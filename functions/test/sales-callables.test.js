"use strict";
/**
 * Sandboxed callable integration tests: execute the actual sales-functions.js,
 * replacing ONLY Firebase SDK transport with an in-memory Firestore emulator.
 * Never connect to the real project or write production data.
 */
const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const path=require("node:path");
const crypto=require("node:crypto");
const records=new Map();
let counter=0;
const now=()=>({toMillis:()=>Date.now()});
function clone(x){return structuredClone(x);}
class HttpsError extends Error{constructor(code,message){super(message);this.code=code;}}
class Ref{
 constructor(collection,id){this.collection=collection;this.id=id;this.path=collection+"/"+id;}
 get(){const v=records.get(this.path);return Promise.resolve(new Snapshot(this,v));}
}
class Snapshot{
 constructor(ref,data){this.ref=ref;this.id=ref.id;this.exists=!!data;this._data=data;}
 data(){return this._data?clone(this._data):undefined;}
 get(key){return this._data?.[key];}
}
class Collection{
 constructor(name){this.name=name;}
 doc(id){return new Ref(this.name,id||"TESTORDER"+String(++counter).padStart(12,"0"));}
 where(field,op,value){return new Query(this.name,x=>x[field]===value);}
 orderBy(){return new Query(this.name,()=>true);}
}
class Query {
 constructor(collection,predicate){this.collection=collection;this.predicate=predicate;}
 limit(){return this;}
 async get(){return {docs:[...records.entries()].filter(([k,v])=>k.startsWith(this.collection+"/")&&this.predicate(v))
  .map(([k,v])=>new Snapshot(new Ref(this.collection,k.split("/")[1]),v))};}
}
function txFactory(){
 const mutations=[];
 return {
   async get(ref){return new Snapshot(ref,records.get(ref.path));},
   create(ref,data){mutations.push({op:"create",ref,data});},
   update(ref,data){mutations.push({op:"update",ref,data});},
   set(ref,data){mutations.push({op:"set",ref,data});},
   commit(){for(const m of mutations){
    if(m.op==="create"&&records.has(m.ref.path))throw Object.assign(new Error("Already exists"),{code:6});
    if(m.op==="update"&&!records.has(m.ref.path))throw new Error("Missing document");
    records.set(m.ref.path,m.op==="update"?{...records.get(m.ref.path),...m.data}:clone(m.data));
   }}
 };
}
const db={collection:n=>new Collection(n),async runTransaction(fn){
 const tx=txFactory();const r=await fn(tx);tx.commit();return r;
}};
function load(){
 const source=fs.readFileSync(path.join(__dirname,"../sales-functions.js"),"utf8");
 const exported={};
 const requireStub=name=>{
  if(name==="node:crypto")return crypto;
  if(name==="firebase-functions/v2/https")return {onCall:(_options,fn)=>fn,HttpsError};
  if(name==="firebase-admin/firestore")return {getFirestore:()=>db,FieldValue:{serverTimestamp:now}};
  if(name==="./license-model"||name==="./sales-model")return require(path.join(__dirname,"..",name.slice(2)));
  throw new Error("Unexpected import "+name);
 };
 vm.runInNewContext("(function(require,exports){"+source+"\n})",{},{filename:"sales-functions.js"})(requireStub,exported);
 return exported;
}
const api=load();
const owner={auth:{uid:"owner123456",token:{email_verified:true,email:"owner@example.invalid"}}};
const buyer={auth:{uid:"buyer123456",token:{email_verified:true,email:"buyer@example.invalid"}}};
const other={auth:{uid:"other123456",token:{email_verified:true,email:"other@example.invalid"}}};
const anonymous={auth:null};
const unverified={auth:{uid:"buyer123456",token:{email_verified:false,email:"buyer@example.invalid"}}};
function req(auth,data={}){return {...auth,data};}
function reset(){records.clear();counter=0;records.set("admins/owner123456",{role:"owner",disabled:false});}
test.beforeEach(reset);
test("anonymous and unverified users cannot create orders",async()=>{
 await assert.rejects(api.salesCreateOrder(req(anonymous,{plan:"android"})),e=>e.code==="unauthenticated");
 await assert.rejects(api.salesCreateOrder(req(unverified,{plan:"android"})),e=>e.code==="permission-denied");
 assert.equal([...records.keys()].filter(k=>k.startsWith("salesOrders/")).length,0);
});
test("server fixes prices despite injected amount or customer UID",async()=>{
 const result=await api.salesCreateOrder(req(buyer,{plan:"android",amountThb:1,customerUid:"other123456",status:"fulfilled"}));
 assert.equal(result.amountThb,490);
 const stored=records.get("salesOrders/"+result.orderId);
 assert.equal(stored.customerUid,"buyer123456");
 assert.equal(stored.amountThb,490);
 assert.equal(stored.status,"pending_payment");
 await assert.rejects(api.salesCreateOrder(req(other,{plan:"invalid"})),e=>e.code==="invalid-argument");
});
test("buyers only see own orders, never plaintext keys or email of another buyer",async()=>{
 const a=await api.salesCreateOrder(req(buyer,{plan:"pro"}));
 await api.salesCreateOrder(req(other,{plan:"android"}));
 const own=await api.salesListMyOrders(req(buyer));
 assert.equal(own.items.length,1);
 assert.equal(own.items[0].id,a.orderId);
 assert.equal(JSON.stringify(own).includes("other@example.invalid"),false);
 assert.equal(JSON.stringify(own).includes("buyer@example.invalid"),false);
});
test("non-owner is blocked from all owner APIs",async()=>{
 await api.salesCreateOrder(req(buyer,{plan:"android"}));
 await assert.rejects(api.salesAdminListOrders(req(buyer)),e=>e.code==="permission-denied");
 await assert.rejects(api.salesAdminFulfillOrder(req(buyer,{orderId:"TESTORDER000000000001",manuallyChecked:true,
  method:"promptpay",reference:"DEMO-123456",amountThb:490})),e=>e.code==="permission-denied");
});
test("owner must check money; rejects wrong amount, duplicate reference and repeat fulfillment",async()=>{
 const a=await api.salesCreateOrder(req(buyer,{plan:"pro"}));
 const payment={orderId:a.orderId,method:"promptpay",reference:"DEMO-20261115-001",amountThb:990,manuallyChecked:true};
 await assert.rejects(api.salesAdminFulfillOrder(req(owner,{...payment,manuallyChecked:false})),e=>e.code==="failed-precondition");
 await assert.rejects(api.salesAdminFulfillOrder(req(owner,{...payment,amountThb:490})),e=>e.code==="failed-precondition");
 assert.equal([...records.keys()].filter(k=>k.startsWith("licenseKeys/")).length,0);
 const first=await api.salesAdminFulfillOrder(req(owner,payment));
 assert.match(first.key,/^PG-[A-F0-9]{32}$/);
 assert.equal(records.get("salesOrders/"+a.orderId).status,"fulfilled");
 assert.equal(records.has("licenseKeys/"+first.licenseHash),true);
 assert.equal(JSON.stringify([...records.values()]).includes(first.key),false,"No plaintext key in Firestore");
 await assert.rejects(api.salesAdminFulfillOrder(req(owner,payment)),e=>e.code==="already-exists");
 const b=await api.salesCreateOrder(req(other,{plan:"pro"}));
 await assert.rejects(api.salesAdminFulfillOrder(req(owner,{...payment,orderId:b.orderId})),e=>e.code==="already-exists");
 assert.equal([...records.keys()].filter(k=>k.startsWith("licenseKeys/")).length,1);
});
test("success returns owner-only plaintext once, customer list never returns key",async()=>{
 const a=await api.salesCreateOrder(req(buyer,{plan:"android"}));
 const issued=await api.salesAdminFulfillOrder(req(owner,{orderId:a.orderId,method:"cash",
  reference:"DEMO-20261115-002",amountThb:490,manuallyChecked:true}));
 const orders=await api.salesListMyOrders(req(buyer));
 assert.equal(orders.items[0].status,"fulfilled");
 assert.equal(JSON.stringify(orders).includes(issued.key),false);
 assert.equal(records.get("licenseKeys/"+issued.licenseHash).plan,"android");
 assert.equal(records.get("licenseKeys/"+issued.licenseHash).licenseType,"lifetime");
});
