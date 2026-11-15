import {initializeApp} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {getAuth,onAuthStateChanged,createUserWithEmailAndPassword,signInWithEmailAndPassword,signOut,sendEmailVerification} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {getFunctions,httpsCallable} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js";
import {firebaseConfig} from "./firebase-config.js";
const app=initializeApp(firebaseConfig),auth=getAuth(app),fn=getFunctions(app,"asia-southeast1");
const $=id=>document.getElementById(id);
const call=(name,data={})=>httpsCallable(fn,name)(data).then(x=>x.data);
function say(s,err=false){$("flash").textContent=s;$("flash").className=err?"error":"";}
function msg(e){return e?.message||"การเชื่อมต่อล้มเหลว";}
function render(items){const parent=$("orders");parent.replaceChildren();
 if(!items.length){parent.textContent="ยังไม่มีคำสั่งซื้อ";return;}
 for(const it of items){const row=document.createElement("div");row.className="order";
  const when=it.createdAt?new Date(it.createdAt).toLocaleString("th-TH"):"รอบันทึก";
  const status=it.status==="fulfilled"?"ออกคีย์แล้ว — รอรับคีย์จากผู้ขาย":"รอตรวจรับเงิน";
  row.textContent=`${it.id} • ${it.plan==="pro"?"Pro":"Mobile"} • ฿${it.amountThb} • ${status} • ${when}`;
  parent.append(row);
 }}
async function reload(){try{const r=await call("salesListMyOrders");render(r.items||[]);}catch(e){say(msg(e),true);}}
onAuthStateChanged(auth,async user=>{const ready=!!(user&&user.emailVerified);
 $("auth-panel").classList.toggle("hidden",ready);
 $("order-panel").classList.toggle("hidden",!ready);
 if(user&&!ready)say("กรุณายืนยันอีเมลก่อนสร้างคำสั่งซื้อ");
 if(ready){$("account").textContent=user.email||"บัญชีลูกค้า";await reload();}
});
$("login-form").addEventListener("submit",async e=>{e.preventDefault();try{
 await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);
 $("password").value="";await auth.currentUser?.getIdToken(true);
 say("เข้าสู่ระบบแล้ว");
 }catch(err){say(msg(err),true);}});
$("signup").addEventListener("click",async()=>{try{
 const user=(await createUserWithEmailAndPassword(auth,$("email").value.trim(),$("password").value)).user;
 await sendEmailVerification(user);$("password").value="";
 say("สมัครแล้ว กรุณายืนยันอีเมลจาก Inbox ก่อนสั่งซื้อ");
 }catch(e){say(msg(e),true);}});
$("verify").addEventListener("click",async()=>{try{
 if(!auth.currentUser)throw Error("กรุณาเข้าสู่ระบบก่อน");
 await sendEmailVerification(auth.currentUser);say("ส่งอีเมลยืนยันแล้ว");
 }catch(e){say(msg(e),true);}});
$("refresh").addEventListener("click",async()=>{try{
 if(!auth.currentUser)throw Error("กรุณาเข้าสู่ระบบก่อน");
 await auth.currentUser.reload();await auth.currentUser.getIdToken(true);
 if(auth.currentUser.emailVerified){$("auth-panel").classList.add("hidden");$("order-panel").classList.remove("hidden");await reload();}
 else say("ยังไม่ยืนยันอีเมล");
 }catch(e){say(msg(e),true);}});
$("logout").addEventListener("click",()=>signOut(auth));
$("reload-orders").addEventListener("click",reload);
$("create-order").addEventListener("click",async()=>{const button=$("create-order");button.disabled=true;try{
 const plan=document.querySelector("input[name=plan]:checked")?.value;
 if(!confirm("ยืนยันสร้างคำสั่งซื้อ "+(plan==="pro"?"Pro ฿990":"Mobile ฿490")+" ? ยังไม่มีการเรียกเก็บเงินออนไลน์"))return;
 const r=await call("salesCreateOrder",{plan});
 say("สร้างคำสั่งซื้อสำเร็จ: "+r.orderId+" ติดต่อผู้ขายผ่านช่องทางที่ยืนยันแล้วเพื่อรับข้อมูลชำระเงิน");
 await reload();
 }catch(e){say(msg(e),true);}finally{button.disabled=false;}});
