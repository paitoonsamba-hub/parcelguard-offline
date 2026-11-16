# ParcelGuard PC Webcam Smart Barcode Scanner — Internal QA (2026-11-16)

**State:** Build CI passes. Not yet tested on real webcam/printed labels/production Windows. Do not use as a customer release.

## Implementation
- Based on preserved verified PC V1.4.0 Pilot source in `pc-installer-source-v140.zip`.
- Builds on camera chooser + LINE OA support in the preceding QA branch.
- Adds toggle **อ่าน Tracking อัตโนมัติจากภาพกล้อง** (default ON) and a separate toggle **บันทึกภาพและวิดีโออัตโนมัติหลังตรวจพบ (โหมดทดสอบ)** (default OFF).
- Uses browser BarcodeDetector when supported; local bundled `@zxing/browser@0.2.1` decoder fallback, no runtime CDN requirement. BarcodeDetector is experimental and not available across all browser platforms.
- Reads 1D/2D candidate values from the existing Webcam preview, rejects URLs, short codes and simple sort/bag prefixes. LEX selected in UI requires a 15-character tracking candidate. Other carriers currently use general 12–35-character format pending real label validation.
- Requires the **same single candidate across 3 frames** before populating Tracking. If multiple plausible codes appear, asks the operator to isolate the correct code.
- Does not overwrite a different pending Tracking typed by hand. Suppresses re-accepting the same code for 12 seconds.
- Optional auto capture reuses original `savePcScan()` for 3-second WebM video + JPEG then existing local PC API; requires non-empty bag and blocks parallel saves.
- Keeps USB keyboard-wedge reader support and manual Tracking input in place.
- No Firebase, License, PC database, server logic, installer registration or customer website changes.

## Build evidence
- Branch `feature/pc-webcam-smart-barcode-qa`.
- Windows GitHub Actions (SUCCESS): https://github.com/paitoonsamba-hub/parcelguard-offline/actions/runs/43831885053
- Artifact: `ParcelGuard-PC-Setup-V1.4.0-WEBCAM-SCAN-QA`.
- All original regression tests, Node JavaScript syntax check and Windows installer compilation passed. Does **not** prove physical camera decoding or auto-save correctness.

## Real hardware acceptance before customer release
1. Install **only** on clean Windows VM/test machine. Existing PC Stable V1.2.9 and V1.4.0 Pilot must remain untouched; installer identity is still V1.4.0.
2. Test USB Webcam and laptop integrated camera, plus switching while not recording.
3. Test an actual SPX shipping label, LEX All 15-character label, Flash, J&T and other allowed carriers; confirm Tracking is **exactly** right. Especially validate LEX/Flash multi-barcode labels.
4. Enable automatic detection (leave auto-save OFF). Point at barcode steadily; require exact Tracking after ~3 matching frames, no incorrect sorting/route codes.
5. Show multiple eligible barcodes simultaneously. Confirm warning and no auto capture. Show clearly invalid codes and QR URLs; confirm no matching.
6. Test USB barcode scanner as before; operator manual keyboard entry still works.
7. Test opt-in auto-save with a throwaway Bag ID, tracking, and camera: one short video then JPEG attached to the correct tracking; verify no duplicates even if label remains in frame. Test with no Bag ID and no camera, and while another capture is in progress.
8. Disconnect camera mid-operation and repeat. Recover without overwriting previous evidence.
9. Restart PC/web page; camera selected in previous QA persists, and LINE OA support link works.
10. Test backup/restore, clean install, and upgrade rollback without touching existing `%LOCALAPPDATA%\\ParcelGuardPC` Stable and `%LOCALAPPDATA%\\ParcelGuardPC-Pilot-V130` Pilot data.

## Known limitations
- Some labels contain multiple Code128/QR/route sort codes. The generic heuristic is not yet equivalent to Android's carrier-specific selector; avoid customer-facing claims until testing real labels and matching format-by-carrier policy.
- Lighting/focus, shutter blur, glossy labels and webcam autofocus determine detection performance.
- ZXing decoding in the page is currently on main JS thread. Performance and latency require real hardware tests.
- UI still reports version 1.4.0 and the unsigned pilot installer may target an existing install. Do **not** install over primary PC.
- Action artifacts expire; never use them as permanent download URLs or release files.
