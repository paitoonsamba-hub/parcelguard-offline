"""Camera device selector patch for the verified ParcelGuard PC V1.4.0 ZIP.

This runs after extracting the original source archive and after LINE support
injection, before tests and PyInstaller. No server, auth, database or installer
identity modifications. Fails closed if the camera implementation differs.
"""
from pathlib import Path

web = Path("pc-installer/web")
page = web / "index.html"
script = web / "app.js"
html = page.read_text(encoding="utf-8")
js = script.read_text(encoding="utf-8")

if 'id="scan-camera-select"' in html:
    assert "CAMERA_PREF_KEY" in js, "HTML selector present without matching JS"
    print("Camera selector already installed")
    raise SystemExit(0)

button = '<button class="btn btn-secondary" id="scan-camera" style="margin-top:12px">เปิดกล้องเว็บแคม</button>'
assert html.count(button) == 1, "Original camera button changed: refusing patch"
chooser = """<div class="pg-camera-picker" style="margin-top:12px;display:grid;gap:8px">
              <label for="scan-camera-select" style="display:block;font-weight:650;color:#d6e0ed">เลือกอุปกรณ์กล้อง</label>
              <select id="scan-camera-select" aria-label="เลือกอุปกรณ์กล้องบันทึกหลักฐาน" style="display:block;width:100%;min-height:46px;background:#1d2b3a;color:#fff;border:1px solid #41556a;border-radius:10px;padding:10px;font:inherit">
                <option value="">กล้องเริ่มต้นของระบบ</option>
              </select>
              <div id="scan-camera-status" role="status" aria-live="polite" style="font-size:12px;color:#9eb1c6">เลือกกล้องได้หลังอนุญาตสิทธิ์กล้อง</div>
            </div>
            """
html = html.replace(button, chooser + button, 1)

old = """let scannerStream=null, scannerBusy=false;
async function openScannerCamera(){
 try{scannerStream=await navigator.mediaDevices.getUserMedia({video:{width:{ideal:1280},height:{ideal:720}},audio:false});
 $('scan-preview').srcObject=scannerStream;toast('กล้องพร้อมถ่ายหลักฐาน');}
 catch(e){toast('เปิดเว็บแคมไม่ได้: '+e.message)}
}"""
assert js.count(old) == 1, "Original getUserMedia implementation changed: refusing patch"

new = """let scannerStream=null, scannerBusy=false;
const CAMERA_PREF_KEY='parcelguard.pc.selectedCamera.v1';
function cameraPrefGet(){try{return localStorage.getItem(CAMERA_PREF_KEY)||''}catch(_){return ''}}
function cameraPrefSet(id){try{if(id)localStorage.setItem(CAMERA_PREF_KEY,id);else localStorage.removeItem(CAMERA_PREF_KEY)}catch(_){}}
function cameraStatus(value){$('scan-camera-status').textContent=value}
async function refreshScannerCameraList(preferred){
 const select=$('scan-camera-select');
 if(!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices){
  cameraStatus('เบราว์เซอร์ไม่รองรับการแสดงรายชื่อกล้อง');return;
 }
 try{
  const devices=(await navigator.mediaDevices.enumerateDevices()).filter(d=>d.kind==='videoinput');
  const wanted=preferred===undefined?(select.value||cameraPrefGet()):preferred;
  select.replaceChildren(new Option('กล้องเริ่มต้นของระบบ',''));
  devices.forEach((d,i)=>select.add(new Option(d.label||('กล้อง '+(i+1)),d.deviceId)));
  if(wanted && devices.some(d=>d.deviceId===wanted))select.value=wanted;
  else if(wanted){
   cameraPrefSet('');
   cameraStatus('ไม่พบกล้องที่บันทึกไว้ กรุณาเลือกกล้องอีกครั้ง');
  }
  if(!devices.length)cameraStatus('ไม่พบกล้อง โปรดตรวจสาย USB และสิทธิ์ของเบราว์เซอร์');
 }catch(e){cameraStatus('อ่านรายชื่อกล้องไม่ได้: '+e.message)}
}
async function openScannerCamera(){
 if(scannerBusy){toast('กำลังบันทึกหลักฐาน กรุณารอจนเสร็จก่อนเปลี่ยนกล้อง');return}
 if(!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia){
  cameraStatus('เบราว์เซอร์ไม่รองรับการเปิดกล้อง');return;
 }
 const selected=$('scan-camera-select').value||cameraPrefGet();
 const video={width:{ideal:1920},height:{ideal:1080},frameRate:{ideal:30}};
 if(selected)video.deviceId={exact:selected};
 try{
  // Acquire the selected device before stopping the working stream. If this
  // fails, keep the previous preview recording-safe rather than blanking it.
  const next=await navigator.mediaDevices.getUserMedia({video,audio:false});
  const previous=scannerStream;
  scannerStream=next;
  $('scan-preview').srcObject=next;
  if(previous)previous.getTracks().forEach(t=>t.stop());
  const track=next.getVideoTracks()[0], actual=track&&track.getSettings().deviceId||'';
  // Request continuous autofocus only if this webcam/driver advertises support.
  // The video stream stays usable if the optional constraint is rejected.
  if(track && track.getCapabilities && track.applyConstraints){
   try{
    const caps=track.getCapabilities();
    if(Array.isArray(caps.focusMode)&&caps.focusMode.includes('continuous')){
     await track.applyConstraints({advanced:[{focusMode:'continuous'}]});
    }
   }catch(_){ /* Unsupported focus controls must never break scanning */ }
  }
  cameraPrefSet(actual||selected);
  await refreshScannerCameraList(actual||selected);
  cameraStatus('กล้องที่กำลังใช้งาน: '+(track&&track.label||'กล้องเริ่มต้นของระบบ'));
  toast('กล้องพร้อมถ่ายหลักฐาน');
 }catch(e){
  if(e.name==='OverconstrainedError'||e.name==='NotFoundError'){
   cameraPrefSet('');
   await refreshScannerCameraList('');
   cameraStatus('กล้องที่เลือกไม่พร้อมใช้งาน กรุณาเลือกอุปกรณ์ใหม่');
  }else cameraStatus('เปิดกล้องไม่ได้: '+e.message);
  toast('เปิดเว็บแคมไม่ได้: '+e.message);
 }
}"""
js=js.replace(old,new,1)
old_bind="$('scan-camera').onclick=openScannerCamera;"
assert js.count(old_bind)==1,"Camera click binding changed"
new_bind="""$('scan-camera').onclick=openScannerCamera;
$('scan-camera-select').addEventListener('change',async e=>{
 if(scannerBusy){
  cameraStatus('กำลังบันทึกหลักฐาน กรุณารอแล้วเลือกกล้องอีกครั้ง');
  return;
 }
 cameraPrefSet(e.target.value);
 await openScannerCamera();
});
refreshScannerCameraList();
if(navigator.mediaDevices&&navigator.mediaDevices.addEventListener){
 navigator.mediaDevices.addEventListener('devicechange',()=>refreshScannerCameraList());
}"""
js=js.replace(old_bind,new_bind,1)
assert js.count("CAMERA_PREF_KEY")==4 or "CAMERA_PREF_KEY" in js
assert js.count("getUserMedia(")==1
assert js.count("'scan-camera-select'")>=2
page.write_text(html,encoding="utf-8")
script.write_text(js,encoding="utf-8")
print("Camera selector added; original scan/video save and server code untouched")
