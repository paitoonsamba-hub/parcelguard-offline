"use strict";
const test=require("node:test");
const assert=require("node:assert/strict");
const crypto=require("node:crypto");
const {DAY_MS,TRIAL_MS,OFFLINE_MS,TrialError,keyForDevice,decideTrial,createReceipt}=require("../trial-core");
const digest="d".repeat(64);
const secret="test-HMAC-"+"s".repeat(48);
const deviceKey=keyForDevice(digest,secret);
test("device HMAC stable and never equals client SHA-256",()=>{
  assert.equal(keyForDevice(digest,secret),deviceKey);
  assert.notEqual(deviceKey,digest);
  assert.throws(()=>keyForDevice("not-a-digest",secret),e=>e.code==="INVALID_DEVICE");
});
test("first registration receives exactly 7 days",()=>{
 const d=decideTrial({account:null,device:null,uid:"user1",deviceKey,now:1234567890000});
 assert.deepEqual(d,{created:true,startedAt:1234567890000,expiresAt:1234567890000+7*DAY_MS});
});
test("reinstall with the same device and account retains original expiry",()=>{
 const first=decideTrial({account:null,device:null,uid:"user1",deviceKey,now:1000});
 const storedAcc={...first,deviceKey}; const storedDev={...first,uid:"user1"};
 const second=decideTrial({account:storedAcc,device:storedDev,uid:"user1",deviceKey,now:1000+5*DAY_MS});
 assert.equal(second.created,false);
 assert.equal(second.expiresAt,first.expiresAt);
});
test("new email cannot reset trial on a device",()=>{
 const first=decideTrial({account:null,device:null,uid:"user1",deviceKey,now:1000});
 assert.throws(()=>decideTrial({account:null,device:{...first,uid:"user1"},uid:"user2",deviceKey,now:2000}),e=>e.code==="TRIAL_ALREADY_USED");
});
test("existing account cannot start new trial on another device",()=>{
 const first=decideTrial({account:null,device:null,uid:"user1",deviceKey,now:1000});
 assert.throws(()=>decideTrial({account:{...first,deviceKey},device:null,uid:"user1",deviceKey:"e".repeat(64),now:2000}),e=>e.code==="ACCOUNT_ALREADY_USED");
});
test("broken partial records are never silently reset",()=>{
 assert.throws(()=>decideTrial({account:null,device:{uid:"user1",startedAt:1000,expiresAt:1000+TRIAL_MS},uid:"user1",deviceKey,now:2000}),e=>e.code==="INCONSISTENT_RECORD");
});
test("server receipt uses valid P256 ECDSA and limits offline period",()=>{
 const {privateKey,publicKey}=crypto.generateKeyPairSync("ec",{namedCurve:"prime256v1"});
 const now=100000;
 const receipt=createReceipt({uid:"user1",deviceDigest:digest,startedAt:now,expiresAt:now+TRIAL_MS,now,signingKey:privateKey});
 const [body,sig]=receipt.split(".");
 assert.equal(receipt.split(".").length,2);
 assert.ok(crypto.verify("sha256",Buffer.from(body),publicKey,Buffer.from(sig,"base64url")));
 const payload=JSON.parse(Buffer.from(body,"base64url"));
 assert.equal(payload.offlineUntil,now+OFFLINE_MS);
 assert.equal(payload.expiresAt,now+7*DAY_MS);
 assert.ok(!crypto.verify("sha256",Buffer.from(body+"x"),publicKey,Buffer.from(sig,"base64url")));
});
test("near expiry a receipt cannot authorize beyond the 7 day limit",()=>{
 const {privateKey}=crypto.generateKeyPairSync("ec",{namedCurve:"prime256v1"});
 const now=1000+TRIAL_MS-1000;
 const [body]=createReceipt({uid:"user1",deviceDigest:digest,startedAt:1000,expiresAt:1000+TRIAL_MS,now,signingKey:privateKey}).split(".");
 assert.equal(JSON.parse(Buffer.from(body,"base64url")).offlineUntil,1000+TRIAL_MS);
});
