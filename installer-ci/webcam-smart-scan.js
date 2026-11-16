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
  function candidate(raw, format) {
    const s = normalize(raw);
    // Fails closed on empty/short warehouse sorting barcodes and URLs.
    // Format-specific carrier validation will be expanded using real labels.
    if (!/^[A-Z0-9-]{12,35}$/.test(s)) return '';
    if (/^(HTTP|WWW|BAG-|SORT|ZONE|BIN-)/.test(s)) return '';
    const carrier = $scan('scan-carrier')?.value || '';
    // LEX commonly uses 15-character tracking IDs in this application.
    if (/LEX/i.test(carrier) && s.length !== 15) return '';
    if (format && /^(qr_code|data_matrix|aztec)$/i.test(format) && s.length < 15) return '';
    return s;
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
    if (engine === 'none' && typeof ZXingBrowser !== 'undefined' &&
        typeof ZXingBrowser.BrowserMultiFormatReader === 'function') {
      try {
        zxing = new ZXingBrowser.BrowserMultiFormatReader();
        engine = 'zxing';
      } catch (_) {}
    }
    if (engine === 'none') status('เครื่องนี้ยังไม่มีตัวถอดรหัสบาร์โค้ดที่รองรับ กรุณาใช้เครื่องสแกน USB');
    else status('พร้อมตรวจจับบาร์โค้ดจาก Webcam • เลือกขนส่งและถุงก่อนสแกน');
  }
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  async function decodeFrame() {
    if (detector) {
      const codes = await detector.detect(preview);
      return codes.map(c => ({raw:c.rawValue, format:c.format}));
    }
    if (zxing && ctx) {
      canvas.width = preview.videoWidth;
      canvas.height = preview.videoHeight;
      ctx.drawImage(preview, 0, 0, canvas.width, canvas.height);
      try {
        const found = zxing.decodeFromCanvas(canvas);
        return found ? [{raw:found.getText(),format:''}] : [];
      } catch (_) { return []; } // No symbol in this frame is expected.
    }
    return [];
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
      const matches = [...new Set(items.map(c => candidate(c.raw,c.format)).filter(Boolean))];
      if (matches.length === 1) {
        const value = matches[0];
        last.hits = (last.value === value) ? last.hits+1 : 1;
        last.value = value;
        if (last.hits >= 3) onCapture(value);
      } else {
        last.hits = 0;
        last.value = '';
        if (matches.length>1) status('พบหลายบาร์โค้ด • กรุณาจัดให้ Tracking ที่ต้องการอยู่กลางกล้องเพียงรหัสเดียว');
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
