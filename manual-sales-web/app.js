import {STORE} from "./config.js";
const status=document.getElementById("sales-status");
const toast=document.getElementById("toast");
document.getElementById("year").textContent=String(new Date().getFullYear());
status.textContent=STORE.salesOpen
  ? "สั่งซื้อแบบติดต่อร้าน • ผู้ดูแลตรวจเงินเข้าจริงก่อนออก License"
  : "กำลังเตรียมเปิดขาย • ยังไม่รับชำระเงินผ่านหน้าเว็บนี้";
let timer=null;
function notify(text){
 toast.textContent=text;
 toast.hidden=false;
 clearTimeout(timer);
 timer=setTimeout(()=>{toast.hidden=true;},7500);
}
function purchaseMessage(plan,price){
 return [
  "สวัสดีครับ สนใจ ParcelGuard "+plan+" ราคา "+price+" บาท",
  "รบกวนแจ้งรายละเอียดแพ็กเกจ เงื่อนไข License และช่องทางชำระเงินที่ยืนยันได้",
  "ต้องการทราบวิธีติดตั้งและขั้นตอนรับ License Key ด้วยครับ"
 ].join("\n");
}
for(const btn of document.querySelectorAll(".contact-btn")){
 btn.addEventListener("click",async()=>{
  const message=purchaseMessage(btn.dataset.plan,btn.dataset.price);
  if(!STORE.salesOpen || !STORE.supportUrl){
    try{
      await navigator.clipboard.writeText(message);
      notify("คัดลอกข้อความสอบถามแล้ว • เว็บไซต์ยังไม่เปิดรับออร์เดอร์ กรุณารอประกาศช่องทางติดต่อร้านที่ยืนยันแล้ว");
    }catch(e){
      notify("เว็บไซต์ยังไม่เปิดรับคำสั่งซื้อ และยังไม่ได้ตั้งค่าช่องทางติดต่อร้าน");
    }
    return;
  }
  try{
    const u=new URL(STORE.supportUrl);
    if(u.protocol!=="https:")throw new Error("Support contact must be HTTPS");
    await navigator.clipboard.writeText(message).catch(()=>{});
    window.open(u.href,"_blank","noopener,noreferrer");
    notify("เปิดช่องทางติดต่อร้านแล้ว • ข้อความสอบถามถูกคัดลอกไว้ (หากเบราว์เซอร์อนุญาต)");
  }catch(e){
    notify("ไม่สามารถเปิดช่องทางติดต่อได้ โปรดตรวจข้อมูลติดต่อจากร้านก่อน");
  }
 });
}
