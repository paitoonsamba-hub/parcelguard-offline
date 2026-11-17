/* Static release metadata only. This is NOT an approval/login mechanism.
 * Never publish a link unless signed release, installer test and QA are complete.
 */
(() => {
"use strict";
const productIds=["android","windows"];
const approvedRelease=(url,product)=>{
  try{
    const u=new URL(url);
    if(u.protocol!=="https:"||u.hostname!=="github.com")return false;
    const prefix="/paitoonsamba-hub/parcelguard-offline/releases/download/";
    if(!u.pathname.startsWith(prefix)||u.search||u.hash)return false;
    const rest=u.pathname.slice(prefix.length);
    if(!/^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(rest))return false;
    return product==="android"?/\.apk$/i.test(rest):/\.exe$/i.test(rest);
  }catch{return false;}
};
const hashValid=h=>typeof h==="string" && /^[a-fA-F0-9]{64}$/.test(h);
async function render(){
  let manifest;
  try{
    const response=await fetch("./download-manifest.json",{cache:"no-store"});
    if(!response.ok)throw Error("Manifest not available");
    manifest=await response.json();
    if(manifest.schemaVersion!==1)throw Error("Unsupported manifest");
  }catch{
    for(const product of productIds){
      const status=document.getElementById(product+"-status");
      if(status)status.textContent="ยังไม่สามารถตรวจสอบข้อมูลไฟล์ล่าสุดได้ กรุณาติดต่อ LINE OA";
    }
    return;
  }
  for(const product of productIds){
    const entry=manifest.products?.[product];
    const status=document.getElementById(product+"-status");
    const version=document.getElementById(product+"-version");
    const link=document.getElementById(product+"-link");
    const hash=document.getElementById(product+"-hash");
    if(!entry||!status||!link||!version||!hash)continue;
    const ready=entry.enabled===true &&
      typeof entry.version==="string" && /^[A-Za-z0-9._-]{1,50}$/.test(entry.version) &&
      approvedRelease(entry.downloadUrl,product) &&
      hashValid(entry.sha256);
    if(!ready){
      status.textContent=typeof entry.status==="string"?entry.status:"รอไฟล์ที่ผ่านการตรวจสอบ";
      continue;
    }
    version.textContent="รุ่น "+entry.version;
    status.textContent="ไฟล์รุ่นส่งมอบที่ทีมงานยืนยันแล้ว • โปรดใช้สิทธิ์ที่ได้รับอนุมัติ";
    link.href=entry.downloadUrl;
    link.target="_blank";
    link.rel="noopener noreferrer";
    link.removeAttribute("aria-disabled");
    link.removeAttribute("tabindex");
    link.classList.remove("unavailable");
    link.textContent=product==="android"?"ดาวน์โหลด APK รุ่นที่ยืนยันแล้ว ↗":"ดาวน์โหลด Windows EXE รุ่นที่ยืนยันแล้ว ↗";
    hash.hidden=false;
    hash.textContent="SHA-256: "+entry.sha256.toLowerCase();
  }
}
render();
})();