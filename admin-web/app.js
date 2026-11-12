import {initializeApp} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {getAuth,onAuthStateChanged,signInWithEmailAndPassword,signOut,sendPasswordResetEmail,sendEmailVerification}
  from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {getFunctions,httpsCallable} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js";
import {firebaseConfig} from "./firebase-config.js";

const app=initializeApp(firebaseConfig);
const auth=getAuth(app);
const functions=getFunctions(app,"asia-southeast1");
const call=name=>httpsCallable(functions,name);
const $=id=>document.getElementById(id);
const el=(tag,cls="",txt=null)=>{
  const item=document.createElement(tag);
  if(cls)item.className=cls;
  if(txt!==null)item.textContent=String(txt);
  return item;
};
const date=v=>v?new Date(v).toLocaleString("th-TH",{dateStyle:"short",timeStyle:"short"}):"—";
const short=v=>v?v.slice(0,10)+"…"+v.slice(-5):"—";
const state={keys:[],customers:[],authorized:false,busy:false};
let flashTimer;
function flash(message,error=false) {
  const node=$("flash");
  node.textContent=message;
  node.classList.toggle("error",error);
  node.classList.remove("hidden");
  clearTimeout(flashTimer);
  flashTimer=setTimeout(()=>node.classList.add("hidden"),8500);
}
function err(e){
  const code=e?.code||"";
  if(code==="functions/permission-denied")return "ไม่มีสิทธิ์ Owner หรือยังไม่ได้ยืนยันอีเมล";
  if(code==="auth/invalid-credential")return "อีเมลหรือรหัสผ่านไม่ถูกต้อง";
  if(code==="functions/unauthenticated")return "กรุณาเข้าสู่ระบบอีกครั้ง";
  return e?.message||"เกิดข้อผิดพลาด";
}
function authView(authorized){
  state.authorized=authorized;
  $("login-screen").classList.toggle("hidden",authorized);
  $("dashboard").classList.toggle("hidden",!authorized);
  $("logout-btn").classList.toggle("hidden",!auth.currentUser);
  $("user-label").textContent=auth.currentUser?.email||"ยังไม่เข้าสู่ระบบ";
  $("connection-label").textContent=authorized?"Owner authenticated":"ต้องยืนยันสิทธิ์ Owner";
}
function setView(view){
  for(const id of ["overview","keys","customers"]){
    $(id+"-view").classList.toggle("hidden",id!==view);
  }
  const titles={overview:"ภาพรวม",keys:"จัดการ License",customers:"บัญชีลูกค้า"};
  $("page-title").textContent=titles[view]||titles.overview;
  document.querySelectorAll("[data-view]").forEach(b=>b.classList.toggle("active",b.dataset.view===view));
}
async function loginState(user){
  authView(false);
  if(!user)return;
  try{
    await user.reload();
    await user.getIdToken(true);
    await call("adminWhoAmI")({});
    authView(true);
    $("verify-btn").classList.add("hidden");
    $("reload-user-btn").classList.add("hidden");
    await loadAll();
  }catch(e){
    flash("เข้าสู่ระบบได้ แต่ยังเข้า Admin ไม่ได้: "+err(e),true);
    if(user.emailVerified===false){
      $("verify-btn").classList.remove("hidden");
      $("reload-user-btn").classList.remove("hidden");
    }
  }
}
onAuthStateChanged(auth,loginState);
$("login-form").addEventListener("submit",async ev=>{
  ev.preventDefault();
  const btn=$("login-btn");
  btn.disabled=true;
  try {
    await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);
    $("password").value="";
  } catch(e) {flash(err(e),true);}
  finally {btn.disabled=false;}
});
$("reset-btn").addEventListener("click",async ()=>{
  const email=$("email").value.trim();
  if(!email){flash("กรุณากรอกอีเมลก่อน",true);return;}
  try {await sendPasswordResetEmail(auth,email);flash("หากอีเมลนี้อยู่ในระบบ จะได้รับลิงก์รีเซ็ตรหัสผ่าน");}
  catch(e){flash(err(e),true);}
});
$("verify-btn").addEventListener("click",async ()=>{
  if(!auth.currentUser)return;
  try{await sendEmailVerification(auth.currentUser);flash("ส่งอีเมลยืนยันแล้ว โปรดตรวจ Inbox/Spam");}
  catch(e){flash(err(e),true);}
});
$("reload-user-btn").addEventListener("click",async()=>{if(auth.currentUser)await loginState(auth.currentUser);});
$("logout-btn").addEventListener("click",async ()=>{
  $("issued-key").textContent="";
  $("issued-panel").classList.add("hidden");
  state.keys=[];
  state.customers=[];
  await signOut(auth);
});
document.querySelectorAll("[data-view]").forEach(btn=>btn.addEventListener("click",()=>setView(btn.dataset.view)));
function table(target,headers,rows){
  const node=$(target);node.replaceChildren();
  if(!rows.length){node.append(el("p","empty","ยังไม่มีข้อมูลในรายการล่าสุด"));return;}
  const t=el("table","data-table");const head=el("thead");const hr=el("tr");
  for(const h of headers)hr.append(el("th","",h));
  head.append(hr);t.append(head);
  const body=el("tbody");
  for(const cells of rows){
    const tr=el("tr");
    for(const cell of cells){
      const td=el("td",cell.cls||"");
      if(cell.pill){
        const badge=el("span","pill "+(cell.color||""),cell.text);
        td.append(badge);
      } else td.textContent=String(cell.text??"—");
      tr.append(td);
    }
    body.append(tr);
  }
  t.append(body);node.append(t);
}
const cell=(text,cls="")=>({text,cls});
function keyRows(keys){
  return keys.map(k=>[
    cell(short(k.id),"mono"),
    cell(k.licenseType==="lifetime"?(k.plan==="pro"?"Pro":"Mobile"):"ระยะเวลา"),
    cell(k.priceThb!=null?"฿"+k.priceThb:(k.durationDays?k.durationDays+" วัน":"—")),
    {pill:true,text:k.status==="redeemed"?"ใช้แล้ว":"ยังไม่ใช้",color:k.status==="redeemed"?"gray":"amber"},
    cell(short(k.redeemedBy),"mono"),
    cell(date(k.createdAt))
  ]);
}
function renderKeys(){
  const headers=["แฮชคีย์","แพ็กเกจ","ราคา/อายุ","สถานะ","ผู้ใช้งาน","วันที่ออก"];
  table("recent-keys",headers,keyRows(state.keys.slice(0,7)));
  table("keys-table",headers,keyRows(state.keys));
  $("metric-total").textContent=state.keys.length;
  $("metric-unused").textContent=state.keys.filter(x=>x.status==="unused").length;
  $("metric-used").textContent=state.keys.filter(x=>x.status==="redeemed").length;
}
function renderCustomers(){
  const rows=state.customers.map(c=>[
    cell(short(c.uid),"mono"),
    {pill:true,text:c.lifetime?(c.plan==="pro"?"Pro ตลอดชีพ":"Mobile ตลอดชีพ"):(c.licenseEndsAt?"กำหนดระยะเวลา":"ทดลอง"),color:c.lifetime?"":"gray"},
    cell(c.lifetime?"ไม่หมดอายุ":date(c.licenseEndsAt||c.trialEndsAt)),
    cell(date(c.createdAt))
  ]);
  table("customers-table",["UID","สิทธิ์","หมดอายุ","เริ่มใช้งาน"],rows);
  $("metric-users").textContent=state.customers.length;
}
async function loadKeys(){
  const result=await call("adminListKeys")({});
  state.keys=result.data.items||[];
  renderKeys();
}
async function loadCustomers(){
  const result=await call("adminListCustomers")({});
  state.customers=result.data.items||[];
  renderCustomers();
}
async function loadAll(){
  try{await Promise.all([loadKeys(),loadCustomers()]);}
  catch(e){flash("โหลดข้อมูลไม่สำเร็จ: "+err(e),true);}
}
async function refresh(method){
  try{await method();flash("อัปเดตข้อมูลแล้ว");}
  catch(e){flash(err(e),true);}
}
$("refresh-overview").addEventListener("click",()=>refresh(loadAll));
$("refresh-keys").addEventListener("click",()=>refresh(loadKeys));
$("refresh-customers").addEventListener("click",()=>refresh(loadCustomers));
$("issue-form").addEventListener("submit",async ev=>{
  ev.preventDefault();
  if(state.busy)return;
  const plan=$("plan").value;
  if(!["android","pro"].includes(plan)||!$("payment-checked").checked){
    flash("ต้องยืนยันการชำระเงินก่อนสร้าง License",true);return;
  }
  if(!confirm("ยืนยันว่าตรวจรับเงินและต้องการออกคีย์ "+(plan==="pro"?"Pro ฿990":"Mobile ฿490")+" จริง?"))return;
  const btn=$("issue-btn");
  btn.disabled=true;state.busy=true;
  // Immediately hide prior raw key before a new generation attempt.
  $("issued-key").textContent="";
  $("issued-panel").classList.add("hidden");
  try{
    const response=await call("adminIssueLicense")({plan,paymentConfirmed:true,orderReference:$("order-reference").value.trim()});
    const data=response.data;
    $("issued-key").textContent=data.key;
    $("issued-meta").textContent="คีย์สำหรับ "+(plan==="pro"?"Pro":"Mobile")+" • ฿"+data.priceThb+
      " • เก็บคีย์ไว้ก่อนออกจากหน้านี้ (ไม่สามารถเรียกดูย้อนหลัง)";
    $("issued-panel").classList.remove("hidden");
    $("payment-checked").checked=false;
    $("order-reference").value="";
    flash("สร้างคีย์สำเร็จ — กรุณาคัดลอกและเก็บไว้ทันที");
    await loadKeys();
  }catch(e){flash("ออกคีย์ไม่สำเร็จ: "+err(e),true);}
  finally{btn.disabled=false;state.busy=false;}
});
$("copy-key").addEventListener("click",async()=>{
  const key=$("issued-key").textContent;
  if(!key)return;
  try{await navigator.clipboard.writeText(key);flash("คัดลอกคีย์แล้ว โปรดส่งให้ลูกค้าผ่านช่องทางส่วนตัว");}
  catch(e){flash("คัดลอกอัตโนมัติไม่ได้ กรุณาเลือกข้อความคีย์เอง",true);}
});
$("clear-key").addEventListener("click",()=>{
  $("issued-key").textContent="";
  $("issued-panel").classList.add("hidden");
});
