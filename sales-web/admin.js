import {initializeApp} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {getAuth,onAuthStateChanged,signInWithEmailAndPassword,signOut} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {getFunctions,httpsCallable} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js";
import {firebaseConfig} from "../admin-web/firebase-config.js";
const app=initializeApp(firebaseConfig),auth=getAuth(app),fn=getFunctions(app,"asia-southeast1");
const $=id=>document.getElementById(id);
const call=(name,data={})=>httpsCallable(fn,name)(data).then(r=>r.data);
const state={authorized:false,orders:[],selected:null};
function say(t,bad=false){$("flash").textContent=t;$("flash").className=bad?"error":"";}
function error(e){return e?.message||"พบข้อผิดพลาด";}
function logoutView(){state.authorized=false;state.orders=[];state.selected=null;
 $("login-panel").classList.remove("hidden");$("dashboard").classList.add("hidden");
 $("handoff").classList.add("hidden");$("key").textContent="";}
async function refresh(){
 const {items}=await call("salesAdminListOrders");state.orders=items||[];
 const root=$("orders");root.replaceChildren();
 if(!state.orders.length){root.textContent="ยังไม่มีคำสั่งซื้อ";return;}
 for(const order of state.orders){const box=document.createElement("div");box.className="order";
 const text=document.createElement("span");
 text.textContent=`${order.id} | ${order.customerEmail||order.customerUid} | ${order.plan==="pro"?"Pro":"Mobile"} ฿${order.amountThb} | ${order.status==="fulfilled"?"ส่งมอบแล้ว":"รอตรวจเงิน"}`;
 box.append(text);
 if(order.status==="pending_payment"){const button=document.createElement("button");button.type="button";button.className="secondary";button.textContent="เลือกตรวจยอด";
 button.addEventListener("click",()=>{state.selected=order;$("order-id").value=order.id;
 $("amount-label").value=`${order.plan} / ฿${order.amountThb}`;
 $("reference").value="";$("checked").checked=false;$("handoff").classList.add("hidden");$("key").textContent="";
 document.getElementById("fulfill").scrollIntoView({behavior:"smooth"});});
 box.append(button);}
 root.append(box);}
}
onAuthStateChanged(auth,async user=>{
 logoutView();if(!user)return;
 try{await user.reload();await user.getIdToken(true);await call("adminWhoAmI");
 state.authorized=true;$("login-panel").classList.add("hidden");$("dashboard").classList.remove("hidden");
 $("owner").textContent=user.email||"Owner";await refresh();
 }catch(e){say("ยังไม่มีสิทธิ์ Owner หรือระบบยังไม่ได้ Deploy: "+error(e),true);}
});
$("login").addEventListener("submit",async ev=>{ev.preventDefault();try{
 await signInWithEmailAndPassword(auth,$("email").value.trim(),$("password").value);
 $("password").value="";say("เข้าสู่ระบบสำเร็จ");
 }catch(e){say(error(e),true);}});
$("logout").addEventListener("click",()=>signOut(auth));
$("reload").addEventListener("click",async()=>{try{await refresh();}catch(e){say(error(e),true);}});
$("fulfill").addEventListener("submit",async ev=>{ev.preventDefault();
 const order=state.selected;if(!order||order.id!==$("order-id").value){say("กรุณาเลือกคำสั่งซื้อจากตาราง",true);return;}
 if(!$("checked").checked){say("ต้องตรวจเงินเข้าจริงก่อน",true);return;}
 const button=$("issue");button.disabled=true;
 try{
  if(!confirm(`ยืนยันว่าได้รับเงินจริง ฿${order.amountThb} สำหรับ ${order.id} และออกคีย์หนึ่งดอก?\n\nการดำเนินการย้อนกลับไม่ได้`))return;
  const r=await call("salesAdminFulfillOrder",{orderId:order.id,method:$("method").value,
    reference:$("reference").value,amountThb:order.amountThb,manuallyChecked:true});
  $("key").textContent=r.key;$("handoff").classList.remove("hidden");
  say("ออกคีย์สำเร็จ — โปรดส่งคีย์ให้ผู้ซื้อทันที เพราะระบบไม่เก็บคีย์ฉบับเต็ม");
  await refresh();state.selected=null;$("order-id").value="";$("amount-label").value="";
 }catch(e){say(error(e),true);}finally{button.disabled=false;}
});
$("copy").addEventListener("click",async()=>{const key=$("key").textContent;if(!key)return;try{
 await navigator.clipboard.writeText(key);say("คัดลอกคีย์แล้ว");
 }catch(e){say("โปรดเลือกและคัดลอกคีย์ด้วยตัวเอง",true);}});
