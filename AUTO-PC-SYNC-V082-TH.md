# ParcelGuard V0.8.2 — Android ↔ Windows auto-pair (same Firebase UID)

## หลักการ
- Windows sign in with **Pro Lifetime ฿990** account and checks `checkAccess`
- Windows registers LAN address, sessionId, and temporary 256-bit bearer token at callable `registerPcReceiver` for its authenticated Firebase UID
- Every 2 minutes Windows renews listing; listing TTL = 10 minutes and is overwritten if another PC signs in with the same UID
- Android signed into **the same Firebase UID** asks `findPcReceiver` over HTTPS, verifies Pro entitlement, and receives that private LAN endpoint and session token; Wi-Fi syncing then uses the original ZIP protocol
- Android looks for PC every 2 minutes while app is running and Wi-Fi sync is enabled (default enabled); evidence remains local, never uploaded to Firebase
- When PC stops, `unregisterPcReceiver` is best-effort and stale entries expire after 10 minutes
- QR remains available as an optional fallback for pilot deployments

## Important
**This requires a Firebase Cloud Functions deploy and installation of BOTH new Android APK and Windows EXE**; installing only one new app does not enable auto pairing.
Do not use as a production release without testing on actual devices: internet required for discovery, private Wi-Fi required for ZIP transfer. Account login does not automatically ensure LAN reachability if client isolation / different SSIDs / VPN is in use.
- Android app auto sync only when running and enabled, not a background service
- Windows receiver uses HTTP on local LAN, including bearer token headers. Never use untrusted public Wi-Fi or expose port 8766 to the Internet
- Firebase Firestore Rules still deny all client reads; the Cloud Functions verify UID and Pro on every discovery request
- Windows and Android can use the same email/password but actual checks are based on Firebase UID, not the text of the email
- A Pro key grants non-expiring license; directory entry is intentionally temporary for each PC session
- **No separate License Key or new purchase is required** when the account already has Pro.
- This project branch has not been deployed from GitHub into the production Firebase project.
- Windows EXE is a new pilot Receiver, not necessarily the original Windows program.

## Before testing
1. Back up parcel evidence with Android export ZIP; debug APK signing can differ across builds
2. Confirm test account has `entitlements/{uid}.lifetimePlan="pro"`
3. Open Google Cloud Shell in project `parcelguard-license` and deploy code from `feature/auto-pc-pair-v0.8.2` (see command below)
4. Download and open matching Windows EXE from GitHub Actions branch `feature/auto-pc-pair-v0.8.2`
5. Install Android APK from the same branch, **only over the existing app if signatures match**. Never uninstall without evidence backup.
6. Windows: log in with Pro, click **ตรวจสอบ Pro และเปิดรับพัสดุ**. QR is now optional. Windows should show auto-connection status.
7. Android: sign in with the exact same account, connect both to same trusted Wi-Fi, enable Wi-Fi sync from settings if disabled, open the app and scan test parcel. Wait for foreground sync cycle.
8. Check Windows Sync Monitor and click **เปิดโฟลเดอร์หลักฐานที่รับ**; verify photos/MP4 + CSV + checksums.
9. Negative tests: account Mobile (no Pro) cannot discover; different Pro UID cannot discover this PC; PC off / offline or different Wi-Fi cannot sync; duplicate ZIP is idempotent.

## Deploy Cloud Functions (not yet run)
```bash
cd ~/parcelguard-deploy
git fetch origin refs/heads/feature/auto-pc-pair-v0.8.2:refs/remotes/origin/feature/auto-pc-pair-v0.8.2
git switch -c feature/auto-pc-pair-v0.8.2 origin/feature/auto-pc-pair-v0.8.2
node --check functions/index.js
node --check functions/pc-auto-pair.js
firebase deploy --only functions --project parcelguard-license
```
Do not deploy until after reviewing the changes and verifying the CI builds pass.
This deploy updates `checkAccess`, `redeemLicense`, admin functions and introduces `registerPcReceiver`, `findPcReceiver`, `unregisterPcReceiver`. Data records and Firestore rules remain unchanged.

## Troubleshooting
- Android says "ยังไม่พบ PC ที่เปิดใช้งาน Pro ด้วยบัญชีเดียวกัน": PC may not have registered, Pro not accepted, session expired or different UID used
- Android says cannot find PC / HTTP 401: Windows token rotated; keep both apps open and retry sync/discovery
- Android shows sync success but Windows folder empty: inspect `%LOCALAPPDATA%\\ParcelGuardPC\\parcels` and Windows Sync Monitor. Pairing alone never sends parcel data.
- Wi-Fi transfers fail despite online registration: ensure Windows Firewall allows private network TCP 8766, and disable guest SSID client isolation (do not open public port)
- QR expires after 90 seconds: generate new QR. Auto pairing is unaffected by QR expiry.

## Security follow-up before general availability
Use HTTPS/mTLS or an authenticated local tunnel for LAN transfer, enforce PC device/session limit, monitor callable usage and costs, and add automated TTL cleanup of stale `pcReceivers` documents. Desktop auth client still uses a session in memory rather than persisting refresh tokens to disk.
