# ParcelGuard QR Sync V0.8.5 (Windows + Android)

## Root issue
Older Windows QR receiver could successfully pair but had no on-screen receive status. Pairing alone does not send files. Furthermore, Android auto-connect branch may overwrite manually QR-paired host/token with Firebase-discovered credentials.

## Changes
- Windows QR-first, 90-second code and renew button retained
- Windows shows events when Android pairs, a ZIP is imported, a duplicate exists, token invalid (401), malformed ZIP (400), license denied (403), or storage write failure (500)
- Windows shows count of parcels actually saved under `%LOCALAPPDATA%\\ParcelGuardPC\\parcels`; button opens storage folder
- Android is rebuilt from QR-only base, so auto-discovery cannot overwrite QR-paired host/token
- Android reports when sync has no new evidence to upload, rather than silently doing nothing
- Android version code 27 to upgrade over experimental v26 if signed with same key

## Check already stored parcel before another scan
1. Windows Explorer: press Win+R, paste `%LOCALAPPDATA%\\ParcelGuardPC\\parcels`, Enter.
2. If folders with `manifest.csv`, `photos` and optionally `videos` exist, ZIP had already reached PC; earlier app just didn't display it.
3. If empty, install compatible V0.8.5 Android APK + Windows EXE. Back up Android evidence ZIP first; do not uninstall app if signatures mismatch.
4. Log in Pro on PC, connect private Wi-Fi, create QR, scan on Android. PC should display **Android จับคู่สำเร็จ แต่ยังไม่ได้รับไฟล์พัสดุ**.
5. On Android settings, enable Wi-Fi Sync, save and sync. The sync may process existing parcel if signature hasn't been marked sent; if already marked sent on Android but file missing on PC, need explicit resend/reconciliation feature (not implemented in this hotfix).
6. Look at PC event + count. Successful receive shows **ได้รับ ZIP หลักฐานใหม่**. Android confirms **PC ยืนยันรับ ZIP แล้ว N รายการ**.
7. If no upload, check Android message and whether Wi-Fi is active. Avoid scanning additional real parcels during diagnosis.

## Security and limitations
- Receiver listens on LAN port 8766 over HTTP; only trust controlled private Wi-Fi, never forward port to Internet.
- Firebase Pro license is still required on PC; QR one-time code expires in 90 seconds; token remains on Android and is accepted only by the paired PC.
- PC stores one evidence folder per tracking/bag, without overwriting; changes to existing tracking/bag in the future are not overwritten and need a separate reconciliation plan.
- No database migrations or Cloud Functions deployment required.
- A success in CI does not prove working physical Wi-Fi; verify both devices and file contents.
