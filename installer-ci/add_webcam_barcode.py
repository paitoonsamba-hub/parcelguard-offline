"""Integrate offline ZXing fallback and Smart Barcode Scanner into PC V1.4.0 UI.

Patches only extracted pc-installer/web/index.html + app.js after earlier
camera-picker patch; fails closed if expected original hooks are absent.
No CDN/runtime network dependencies or backend changes.
"""
from pathlib import Path

web=Path("pc-installer/web")
page=web/"index.html"
script=web/"app.js"
html=page.read_text(encoding="utf-8")
js=script.read_text(encoding="utf-8")
marker='id="pg-webcam-scan-enabled"'
if marker in html:
    assert 'Smart Webcam Barcode QA' in js and 'ZXingBrowser' in js
    print("Smart webcam scanner already patched")
    raise SystemExit(0)

hook='<button class="btn btn-secondary" id="scan-camera" style="margin-top:12px">เปิดกล้องเว็บแคม</button>'
assert html.count(hook)==1, "Missing original scanner camera button"

ui="""<div class="pg-webcam-scanner-controls" style="display:grid;gap:8px;margin:12px 0 4px;padding:13px;border:1px solid #374b5e;border-radius:12px;background:#172533">
              <strong style="color:#ff9b23">Smart Barcode Scan • Webcam</strong>
              <label style="display:flex;gap:9px;align-items:center;color:#fff;font-size:14px">
                <input id="pg-webcam-scan-enabled" type="checkbox" checked> อ่าน Tracking อัตโนมัติจากภาพกล้อง
              </label>
              <label style="display:flex;gap:9px;align-items:center;color:#fff;font-size:14px">
                <input id="pg-webcam-auto-save" type="checkbox"> บันทึกภาพและวิดีโออัตโนมัติหลังตรวจพบ (โหมดทดสอบ)
              </label>
              <p id="pg-webcam-scan-status" role="status" aria-live="polite" style="margin:4px 0;color:#a9c0d2;font-size:12px">
                กำลังเตรียมตัวอ่านบาร์โค้ด • ระบุขนส่งและถุงก่อนสแกน
              </p>
              <small style="color:#9fb0c2">ตรวจเลขบนใบปะหน้าให้ตรงก่อนบันทึก • ถ้ามีหลายรหัสให้ใช้โหมดบันทึกเอง</small>
            </div>
            """
html=html.replace(hook,hook+"\n"+ui,1)

vendor=Path("installer-ci/vendor-build/node_modules/@zxing/browser/umd/zxing-browser.min.js")
assert vendor.is_file() and vendor.stat().st_size>10000, "ZXing offline JS bundle missing"
base=(Path("installer-ci/webcam-smart-scan.js")).read_text(encoding="utf-8")
assert "Smart Webcam Barcode QA" in base
assert "savePcScan()" in js, "Original scan save function unavailable"
assert "scannerStream" in js and "scannerBusy" in js
# Browser bundle is appended after the original application code and before
# our own scanner so it sees the existing globals and stays same-origin.
selector=(Path("installer-ci/android-tracking-selector.js")).read_text(encoding="utf-8")
assert "ParcelTrackingSelector" in selector
js+="\n;\n"+vendor.read_text(encoding="utf-8")+"\n;\n"+selector+"\n;\n"+base+"\n"
assert js.count("Smart Webcam Barcode QA")==1
page.write_text(html,encoding="utf-8")
script.write_text(js,encoding="utf-8")
print("Installed self-contained Webcam Smart Scanner + local ZXing fallback. No remote resources.")
