"""Patch the verified V1.4.0 PC scan screen with an explicit per-PC bag workflow.

Existing /api/pc/scan remains authoritative for parcels/evidence; this UI only
tracks the currently open bag and blocks accidental scanning into a closed bag.
Closing here is not a server-side carrier handover, syncing, or A4 print.
"""
from pathlib import Path

web=Path("pc-installer/web")
page=web/"index.html"
script=web/"app.js"
html=page.read_text(encoding="utf-8")
js=script.read_text(encoding="utf-8")
if 'id="pg-bag-open"' in html:
    assert 'PG_PC_BAG_V1' in js
    print("PC bag workflow already patched")
    raise SystemExit(0)

anchor='<button class="btn btn-secondary" id="scan-camera" style="margin-top:12px">เปิดกล้องเว็บแคม</button>'
assert html.count(anchor)==1, "Expected original PC camera button missing"
panel='''<div id="pg-bag-lifecycle" style="margin:12px 0;padding:14px;background:#142534;border:1px solid #3e596c;border-radius:12px;display:grid;gap:9px">
  <strong style="color:#ffac35">จัดการถุง — PC</strong>
  <div id="pg-bag-state" role="status" aria-live="polite" style="color:#e5eff9">ยังไม่ได้เปิดถุง</div>
  <div style="display:flex;gap:9px;flex-wrap:wrap">
    <button class="btn btn-primary" id="pg-bag-open" type="button">เปิดถุง</button>
    <button class="btn btn-secondary" id="pg-bag-close" type="button" disabled>ปิดถุง</button>
    <button class="btn btn-secondary" id="pg-bag-view" type="button">ดูรายการถุง</button>
  </div>
  <small style="color:#aab9ca">เมื่อปิดถุงจะหยุดรับพัสดุเข้าถุงนี้บน PC เครื่องนี้ • ใช้หน้ารายการถุงเดิมสำหรับตรวจสอบและพิมพ์เอกสาร</small>
</div>'''
html=html.replace(anchor,panel+'\n'+anchor,1)

start="async function savePcScan(){"
assert js.count(start)==1, "Original scan save hook missing"
guard="""async function savePcScan(){
 if(!pgBagCanSave()){toast('กรุณาเปิดถุงก่อนสแกนรับพัสดุ');return;}
"""
js=js.replace(start,guard,1)
success="  $('scan-tracking').value='';$('scan-tracking').focus();await refresh();"
assert js.count(success)==1, "Original scan success hook changed"
js=js.replace(success,"  pgBagSaved(result);\n"+success,1)

init="""
// Explicit PC bag lifecycle (local operator state, never alters PC database).
const PG_PC_BAG_V1='parcelguard.pc.activeBag.v1';
let pgPcBag=null;
function pgBagRestore(){
 try{
  const data=JSON.parse(localStorage.getItem(PG_PC_BAG_V1)||'null');
  if(data&&typeof data.bag==='string'&&typeof data.carrier==='string'&&
     typeof data.count==='number'&&typeof data.open==='boolean')pgPcBag=data;
 }catch(_){pgPcBag=null}
 pgBagRender();
}
function pgBagPersist(){try{localStorage.setItem(PG_PC_BAG_V1,JSON.stringify(pgPcBag))}catch(_){}}
function pgBagRender(){
 const status=$('pg-bag-state'),open=$('pg-bag-open'),close=$('pg-bag-close');
 if(!status)return;
 if(pgPcBag){
  status.textContent=(pgPcBag.open?'เปิดถุง: ':'ปิดถุงแล้ว: ')+
    pgPcBag.bag+' • '+pgPcBag.carrier+' • รับสำเร็จบน PC นี้ '+pgPcBag.count+' ชิ้น';
 }else status.textContent='ยังไม่ได้เปิดถุง';
 close.disabled=!pgPcBag||!pgPcBag.open||scannerBusy;
 open.disabled=!!pgPcBag&&pgPcBag.open||scannerBusy;
 if(pgPcBag&&pgPcBag.open){
  $('scan-bag').value=pgPcBag.bag;
  $('scan-carrier').value=pgPcBag.carrier;
 }
 $('scan-bag').readOnly=!!pgPcBag&&pgPcBag.open;
 $('scan-carrier').disabled=!!pgPcBag&&pgPcBag.open;
}
function pgBagCanSave(){
 return !!pgPcBag&&pgPcBag.open&&
   $('scan-bag').value.trim()===pgPcBag.bag&&
   $('scan-carrier').value===pgPcBag.carrier;
}
function pgBagSaved(result){
 if(!pgBagCanSave())return;
 if(!result||!result.tracking)return;
 pgPcBag.count++;
 pgBagPersist();pgBagRender();
}
$('pg-bag-open').addEventListener('click',()=>{
 if(scannerBusy)return;
 const bag=$('scan-bag').value.trim(), carrier=$('scan-carrier').value;
 if(!bag||!carrier){toast('กรุณาเลือกขนส่งและกรอกรหัสถุงก่อนเปิดถุง');return}
 if(pgPcBag&&pgPcBag.open){toast('กรุณาปิดถุงเดิมก่อน');return}
 pgPcBag={bag,carrier,count:0,open:true};
 pgBagPersist();pgBagRender();toast('เปิดถุง '+bag+' แล้ว');
});
$('pg-bag-close').addEventListener('click',()=>{
 if(scannerBusy||!pgPcBag||!pgPcBag.open)return;
 if(!confirm('ยืนยันปิดถุง '+pgPcBag.bag+'? จะไม่รับเพิ่มเข้าถุงนี้บน PC เครื่องนี้'))return;
 pgPcBag.open=false;pgBagPersist();pgBagRender();
 $('scanner-feedback').textContent='ปิดถุง '+pgPcBag.bag+' แล้ว • ตรวจรายการถุงก่อนส่งมอบ';
});
$('pg-bag-view').addEventListener('click',()=>{
 const link=document.querySelector('[data-page="bags"]');
 if(link)link.click();else toast('กรุณาเปิดหน้ารายการถุงจากเมนูหลัก');
});
pgBagRestore();
"""
assert js.count("$('scan-tracking').addEventListener('keydown'")==1
js += "\n"+init
page.write_text(html,encoding="utf-8")
script.write_text(js,encoding="utf-8")
print("Explicit PC local bag open/close added; existing scan API and evidence preserved")
