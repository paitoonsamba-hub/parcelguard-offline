/* ParcelGuard PC Smart Webcam Barcode QA.
   Webcam capture and persistence remain owned by existing app.js.
   This file only decodes previews and optionally calls existing savePcScan().
   No third-party API calls, tracking uploads or remote scripts.
*/
(() => {
  'use strict';
  const $scan = id => document.getElementById(id);
  const preview = $scan('scan-preview');
  const scanStatus = $scan('pg-webcam-scan-status');
  const autoSave = $scan('pg-webcam-auto-save');
  const enabled = $scan('pg-webcam-scan-enabled');
  const last = { value: '', hits: 0, acceptedAt: 0, acceptedValue: '', busy: false };
  let detector = null;
  let zxing = null;
  let engine = 'none';
  let lastWarn = '';
  function status(s) { if (scanStatus) scanStatus.textContent = s; }
  function normalize(raw) { return String(raw || '').trim().replace(/\s+/g, '').toUpperCase(); }
  let framesChecked=0, emptyFrames=0;
  function readDecision(items){
    const carrier=$scan('scan-carrier')?.value || '';
    return ParcelTrackingSelector.choose(items.map(c=>c.raw),carrier);
  }
  async function initEngine() {
    if (typeof BarcodeDetector === 'function') {
      try {
        const formats = await BarcodeDetector.getSupportedFormats();
        const choices = ['code_128','code_39','code_93','itf','ean_13','pdf417','qr_code','data_matrix']
          .filter(f => formats.includes(f));
        if (choices.length && (choices.includes('code_128') || choices.includes('itf'))) {
          detector = new BarcodeDetector({ formats: choices });
          engine = 'native';
        }
      } catch (_) {}
    }
    // Keep ZXing available even when native detection exists: USB webcam
    // frames may decode in one engine but not the other.
    if (typeof ZXingBrowser !== 'undefined' &&
        typeof ZXingBrowser.BrowserMultiFormatReader === 'function') {
      try {
        zxing = new ZXingBrowser.BrowserMultiFormatReader();
        if (engine === 'none') engine = 'zxing';
      } catch (_) {}
    }
    if (engine === 'none') status('เครื่องนี้ยังไม่มีตัวถอดรหัสบาร์โค้ดที่รองรับ กรุณาใช้เครื่องสแกน USB');
    else status('พร้อมตรวจจับบาร์โค้ดจาก Webcam • เลือกขนส่งและถุงก่อนสแกน');
  }
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  async function decodeFrame() {
    let codes=[];
    if (detector) {
      try {
        codes=(await detector.detect(preview)).map(c=>({raw:c.rawValue,format:c.format}));
      } catch (_) { /* Try bundled ZXing below */ }
      if (codes.length) return codes;
    }
    if (zxing && ctx) {
      canvas.width = preview.videoWidth;
      canvas.height = preview.videoHeight;
      ctx.drawImage(preview, 0, 0, canvas.width, canvas.height);
      try {
        const found = zxing.decodeFromCanvas(canvas);
        if(found) return [{raw:found.getText(),format:''}];
      } catch (_) { /* No symbol in this frame is expected */ }
      // Try a central crop for narrow shipping-label Code128 bars.
      const w=canvas.width,h=canvas.height;
      if(w>640 && h>400){
        const x=Math.floor(w*.10),y=Math.floor(h*.13),cw=Math.floor(w*.8),ch=Math.floor(h*.74);
        const crop=document.createElement('canvas');crop.width=cw;crop.height=ch;
        crop.getContext('2d').drawImage(canvas,x,y,cw,ch,0,0,cw,ch);
        try {
          const found=zxing.decodeFromCanvas(crop);
          if(found)return [{raw:found.getText(),format:''}];
        } catch (_) {}
      }
    }
    return codes;
  }
  function onCapture(s) {
    const now = Date.now();
    if (last.acceptedValue === s && now-last.acceptedAt < 12000) return;
    const input = $scan('scan-tracking');
    if (!input) return;
    // Don't overwrite a manually typed or different pending Tracking.
    if (input.value.trim() && normalize(input.value) !== s) {
      status('พบ '+s+' แต่ช่อง Tracking มีเลขอื่นอยู่ • ตรวจสอบก่อนสแกนใหม่');
      return;
    }
    input.value = s;
    last.acceptedAt = now;
    last.acceptedValue = s;
    status('อ่าน Tracking ได้: '+s+' • ตรวจเลขและกดบันทึกหลักฐาน');
    if (!autoSave?.checked) return;
    if (!$scan('scan-bag')?.value.trim()) {
      status('อ่าน Tracking ได้: '+s+' • กรุณาระบุถุงก่อนเปิดบันทึกอัตโนมัติ');
      return;
    }
    // Auto save uses the existing video-first/JPEG and backend workflow.
    // Never launch a second save while the camera is already capturing.
    if (typeof scannerBusy !== 'undefined' && scannerBusy) return;
    if (typeof savePcScan === 'function') {
      status('อ่าน Tracking ได้: '+s+' • กำลังบันทึกหลักฐาน');
      savePcScan();
    }
  }
  async function tick() {
    if (!enabled?.checked || engine==='none' || last.busy) return;
    if (!preview?.srcObject || !preview.videoWidth || preview.readyState < 2) return;
    if (typeof scannerBusy !== 'undefined' && scannerBusy) return;
    if (document.hidden) return;
    last.busy = true;
    try {
      const items = await decodeFrame();
      framesChecked++;
      const decision = readDecision(items);
      const value = decision.autoTracking;
      const carrier = $scan('scan-carrier')?.value || '';
      if (value) {
        emptyFrames = 0;
        last.hits = (last.value === value) ? last.hits+1 : 1;
        last.value = value;
        const needed = ParcelTrackingSelector.fastVerifiedLexFlash(value,carrier)?1:2;
        if (last.hits >= needed) onCapture(value);
        else status('อ่านบาร์โค้ดได้ • กำลังยืนยันรหัส '+value);
      } else {
        last.hits = 0;
        last.value = '';
        if (decision.message) status(decision.message+
            (decision.reviewOptions.length?' • '+decision.reviewOptions.join(' / '):''));
        else if (items.length) status('อ่านบาร์โค้ดได้ แต่ไม่พบ Tracking ที่ยืนยันได้ • ตรวจขนส่ง');
        else if (++emptyFrames % 18 === 0) status('กล้องเปิดอยู่ แต่ยังถอดรหัสบาร์โค้ดไม่ได้ • '+engine+
            ' • ตรวจความคม/ระยะ/แสง ('+framesChecked+' เฟรม)');
      }
    } catch (e) {
      if (lastWarn !== e.name) {
        lastWarn = e.name;
        status('อ่านบาร์โค้ดไม่ได้: '+e.message+' • ตรวจกล้อง/แสง/โฟกัส');
      }
    } finally { last.busy = false; }
  }
  enabled?.addEventListener('change', () => {
    last.value = ''; last.hits = 0;
    status(enabled.checked ? 'เปิดอ่านบาร์โค้ดอัตโนมัติแล้ว' : 'หยุดอ่านบาร์โค้ดจาก Webcam');
  });
  autoSave?.addEventListener('change', () => {
    if (autoSave.checked) {
      status('เปิดบันทึกอัตโนมัติ: ใช้กับพัสดุทดสอบเท่านั้น กรุณาระบุถุงและตรวจขนส่ง');
    } else status('ปิดบันทึกอัตโนมัติ: อ่าน Tracking แล้วกดบันทึกเอง');
  });
  // Avoid camera readback during evidence capture and avoid overlapping decodes.
  initEngine().then(() => setInterval(tick, 360));
})();
