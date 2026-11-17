# ParcelGuard — Customer Pilot Download Center

**Status (2026-11-17): Staging only. No customer-installable release is published.**

## Purpose

Website entry point: `/download.html` (once Cloudflare deploys the `parcelguard-website` branch).

- Selected-pilot inquiries go through verified LINE OA: https://lin.ee/wEsjx0X
- The public page provides separate Android APK / Windows EXE sections and installation guidance.
- While no approved release is present, both download buttons remain **disabled**.
- The public URL is **not** an access-control gate. Authorized use depends on account/license entitlement.
- No direct customer payments, automatic registration, 7-day self-service trial, or license issuance is implemented here.

## Current artifact assessment

- Android GitHub Actions build on `feature/android-line-support-v1.5` currently uses `assembleDebug` and uploads `app-debug.apk`. **This is not a signed distribution-grade release.**
- PC latest build on `feature/pc-contact-replace-logout-v1.4.0-qa` is an Actions QA artifact, not a tested published release.
- No public GitHub Releases assets were found at implementation time. GitHub Actions artifacts expire; do not link them from the customer page.

## Before enabling customer downloads

1. Test with new customers: install Android APK, sign in, issue trial entitlement according to existing Admin tool, open/close a bag, scan SPX/LEX-ALL, save video+photo, and search evidence.
2. Pair Android with PC and verify 20–30 parcel records, image/video playback, tracking search, bag reconciliation, A4 printing, and sync recovery after restart.
3. Build signed Android **release APK** using secured signing credentials. Install the release on a clean phone not used for development, verify upgrade semantics, permissions and SHA-256. Never publish the existing debug APK as the commercial release.
4. Test the PC Setup EXE on a clean Windows machine and on an existing installation with backed-up data. Confirm login, logout from Settings, LINE contact, no PC scanner menu, and no data loss.
5. Run basic malware/security checks, record exact version/build SHA, and generate a SHA-256 hash for each file.
6. Use a protected GitHub Release tag after approval (example tag format `pilot-v1.0.0`; the actual tag/version must match the verified build). Upload verified `.apk` and `.exe` files as **release assets**.
7. Only after assets are publicly reachable, update `public/download-manifest.json`:

```json
{
  "schemaVersion": 1,
  "products": {
    "android": {
      "enabled": true,
      "version": "VERIFIED_VERSION",
      "downloadUrl": "https://github.com/paitoonsamba-hub/parcelguard-offline/releases/download/VERIFIED_TAG/VERIFIED_FILE.apk",
      "sha256": "64_LOWERCASE_HEX_DIGITS",
      "status": "พร้อมดาวน์โหลด"
    },
    "windows": {
      "enabled": false,
      "version": null,
      "downloadUrl": null,
      "sha256": null,
      "status": "รอตรวจสอบ"
    }
  }
}
```

Do not paste these placeholders into the live manifest! The website validates the URL host/path, file extension, explicit enabled flag, and SHA-256 format. It **does not** verify the remote file's hash on behalf of the customer. Verify that the uploaded asset hash matches the recorded hash before publishing.

8. Publish/deploy and verify the website on Android and Windows, in mobile/desktop browsers, and in an incognito browser. Ensure the selected-pilot page URL works and both download links go to the intended verified release. Add user guides once actual screenshots and QA are validated.
9. Keep records of which approved account/license was supplied to each pilot tester privately. Never put customer personal data or keys into public GitHub issues/files.

## Sending a message to an approved tester via LINE

> ParcelGuard เปิดรับทดสอบแบบคัดเลือกแล้วครับ
> 
> ขอบคุณที่สนใจ รบกวนแจ้งว่าต้องการทดลอง Android อย่างเดียว หรือ Android + Windows PC และรุ่นมือถือที่ใช้งาน
>
> หลังตรวจความพร้อมแล้ว ทีมงานจะยืนยันสิทธิ์ทดลองและส่งขั้นตอนติดตั้ง พร้อมลิงก์หน้าดาวน์โหลด ParcelGuard ให้ครับ
>
> กรุณาอย่าส่งรหัสผ่านหรือ License Key เต็มผ่านแชต

**Do not send a download link while its corresponding button is pending.** Do not promise a 7-day term without a verified entitlement workflow.

## Deployment (static Cloudflare Pages)

Production branch is `parcelguard-website`, root is repository root, build output is `public`. Do not alter the Android project's `main` branch or Firebase configuration. Confirm the Cloudflare project and production deployment target before merging. Tests:

```sh
node tests/smoke.mjs
node tests/download-smoke.mjs
node --check public/app.js
node --check public/download.js
```

No binary, secret, user record, license key or payment credential is committed by this change.
