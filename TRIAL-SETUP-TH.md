# ParcelGuard V0.5.1 — Legacy trial / license notes

> This document describes the original 7-day trial and term-key testing. **Current lifetime sale plans are in [LIFETIME-LICENSE-TH.md](LIFETIME-LICENSE-TH.md)**. The original functions were already deployed to Firebase; changes on the feature branch still require a new deploy. Do not use the old duration-day key issuance instructions for new purchases.

## Status
Android authentication gate and server code are implemented on the feature branch; **not deployed to Firebase**. No payment processing is included. The 7-day trial cannot work until deployment and testing succeed.

## Architecture
- Android Firebase Auth email/password -> callable `checkAccess` in `asia-southeast1`.
- First `checkAccess` creates an entitlement with 7-day server-clock trial. Android does not send expiration timestamps.
- The Android scanner only opens on a positive server response and receives at most a 15-minute in-process lease. Reverification requires connectivity; offline scanning is **not available beyond the short lease** yet.
- A single-use key is created by a trusted admin script and redeemed through callable `redeemLicense`. Keys are stored as SHA-256 hashes, not in plaintext.
- Firestore client reads and writes are denied in `firestore.rules`; Admin SDK functions bypass the rules.
- Email-account recreation or use of a different email can trigger another trial. Cross-account/device abuse controls are **not implemented** and require a separate solution.
- Production use requires App Check enforcement, monitoring, rate limiting, and an offline licensing strategy. Android anti-tampering is not absolute.

## ออก License Key จาก Google Cloud Shell (ไม่ต้องสร้าง Service Account Key)

วิธีนี้ใช้บัญชี Google ที่ลงชื่อเข้า Cloud Shell อยู่แล้ว โดยบัญชีนั้นต้องมีสิทธิ์เขียน Firestore ในโปรเจกต์ `parcelguard-license`. **ห้ามส่งคีย์ที่สร้างหรือภาพหน้าจอแสดงคีย์ให้ใคร**

```bash
cd ~/parcelguard-deploy
git pull --ff-only
gcloud config get-value project
node functions/scripts/issue-license.js 30
```

ผลสำเร็จคือ `New license (show once): PG-...` ให้คัดลอกคีย์เก็บไว้ในที่ปลอดภัย แล้วกรอกคีย์ที่หน้า **กรอก License Key** ในแอปทดสอบ ค่า `30` คือระยะเวลาสิทธิ์แบบชำระเงิน 30 วัน เปลี่ยนเป็น `365` สำหรับ 365 วันได้ คีย์แต่ละดอกใช้กับบัญชีเดียวเท่านั้น หลังใช้แล้วไม่สามารถโอนให้บัญชีอื่นผ่านแอปได้

ถ้า Cloud Shell แจ้ง `Could not load the default credentials` ให้ตรวจสิทธิ์ของบัญชีและวิธี ADC ตามเอกสาร Google Cloud ก่อน อย่าสร้างหรือส่งไฟล์ service account JSON ในแชต การพิมพ์คีย์ลง Terminal จะอยู่ใน output ของ Terminal และผู้ที่มองเห็นหน้าจออาจเห็นคีย์

ต้องให้ Cloud Functions `redeemLicense` และ `checkAccess` ทำงานจริงก่อนทดสอบ การออกคีย์เพียงอย่างเดียว **ไม่ได้เปิดสิทธิ์ให้บัญชีใดทันที** จนกว่าบัญชีนั้นจะ Redeem คีย์

## Deploy prerequisites
1. Select the Firebase project `parcelguard-license` and confirm billing supports 2nd-gen Cloud Functions (Blaze plan required).
2. On a trusted development machine with Node.js 22 and Firebase CLI, run:
   ```sh
   npm install -g firebase-tools
   firebase login
   cd functions
   npm install
   cd ..
   firebase use parcelguard-license
   firebase deploy --only firestore:rules,functions
   ```
3. Verify `checkAccess` and `redeemLicense` deploy in `asia-southeast1`.
4. Install the Android debug APK from the feature-branch GitHub Actions and sign up with test credentials. Check that the trial cannot be extended by reinstalling for the **same Firebase account**.
5. Only on a trusted admin machine, authenticate with an appropriate Firebase Admin service-account credential stored **outside GitHub**, then issue a key:
   ```sh
   export GOOGLE_APPLICATION_CREDENTIALS=/secure/location/service-account.json
   node functions/scripts/issue-license.js 365
   ```
   Keep the output key confidential, share it only with the paying customer. NEVER commit service accounts or the issued keys.

## Test cases
- New account => 7 days from first server entitlement check.
- Same account after uninstall => original entitlement remains.
- Client cannot write `entitlements` or `licenseKeys` using Firestore SDK.
- Expired account => scanner locked; UI can redeem a valid key.
- Valid key redeemed twice by two accounts => second account rejected.
- Internet unavailable => fail closed at cold start and revalidation.
- Existing scanner/photo/video/manifest flows still need on-device regression tests.

## Safety
This code has not been deployed or tested on real devices. Keep the PR in draft and the original main branch untouched until acceptance testing.
