# ParcelGuard V0.6 — Lifetime license plans (design & release checklist)

## แพ็กเกจที่อนุมัติ
| รหัส | ราคา | รูปแบบ | ความสามารถ |
| --- | ---: | --- | --- |
| `android` | ฿490 | ซื้อครั้งเดียว / ตลอดชีพ | ParcelGuard Android, สแกน ถ่ายภาพ/วิดีโอ จัดถุง รายงาน |
| `pro` | ฿990 | ซื้อครั้งเดียว / ตลอดชีพ | Android ทั้งหมด + สิทธิ์ ParcelGuard Windows PC |

ทดลองใช้ Android 7 วันเท่าเดิมก่อนซื้อ ทั้งสองแพ็กเกจใช้ **Firebase Authentication UID** เป็นเจ้าของ License ไม่ใช่หมายเลขโทรศัพท์หรืออีเมลอย่างเดียว ผู้ใช้เปลี่ยนอีเมลในบัญชีเดิมได้โดยไม่เสียสิทธิ์

คำว่า "ตลอดชีพ" หมายถึงไม่มีวันหมดอายุของ License ที่กำหนดจากวันที่ซื้อ ตราบเท่าที่ผลิตภัณฑ์และบริการยืนยันสิทธิ์ยังดำเนินการอยู่ ต้องแจ้งลูกค้าถึงการพึ่งพา Firebase และข้อกำหนดบริการให้ชัดก่อนขาย ห้ามสัญญาว่าเซิร์ฟเวอร์จะออนไลน์ตลอดไปโดยไม่มีค่าใช้จ่าย

## Firestore schema
- `entitlements/{uid}`: `createdAt`, `trialEndsAt` (7 วัน), `licenseEndsAt` (legacy paid term only), `lifetimePlan: "android"|"pro"`, `lifetimeGrantedAt`, `schemaVersion:2`, `lastLicenseHash`
- `licenseKeys/{sha256(UPPERCASE_KEY)}`: `licenseType:"lifetime"`, `plan`, `priceThb` (490/990), `currency:"THB"`, `redeemedBy:null|uid`, `createdAt`, `redeemedAt`, `issuedVia:"admin-cli"`
- Key string `PG-` + 32 hex uppercase characters; unique and shown once. Store only SHA-256 digest in DB. **Do not commit keys**.
- Existing document with only `durationDays` remains a paid term key. Existing 30-day entitlement and `trialEndsAt` remain unchanged after deployment.

## Server semantics
- `checkAccess` returns `active`, `trial`, `lifetime`, `plan`, `features`, `expiresAt`, `serverNow`.
- For lifetime users, `expiresAt` is synthetic **2099-01-01** ONLY for legacy APK compatibility; the source of truth is `lifetime: true`. Never treat 2099 as the real license expiration or as a renewal date.
- `redeemLicense` is transactional. A key can be redeemed by exactly one UID. Redeeming the same key again on the same UID is idempotent.
- Upgrading `android` to `pro` preserves permanent rights; redeeming a Mobile key never downgrades Pro.
- `priceThb` in a key document is informational and **does not collect or validate payment**. Admin must verify transfer before issuing a key. There is no payment gateway.
- Firestore rules deny client direct access to all collections. Only authenticated callable functions with Admin SDK grant entitlements.

## คู่มือออกคีย์ใน Cloud Shell (หลัง Deploy V0.6)
Do **NOT** issue lifetime keys until both functions and the **new Android APK** are ready to test.

```bash
cd ~/parcelguard-deploy
git pull --ff-only
npm --prefix functions install
node --test functions/test/license-model.test.js
# Only after independently confirming payment:
node functions/scripts/issue-license.js android   # Lifetime Android ฿490
node functions/scripts/issue-license.js pro       # Lifetime Android+PC ฿990
```

Output includes one-time license key. Store it confidentially and send only to that paying customer. Do not share screenshots showing the key. The old `node functions/scripts/issue-license.js 30` command intentionally no longer works.

Deploy new backend from project directory after local syntax check and review (does NOT happen automatically from committing GitHub):
```bash
node --check functions/index.js
firebase deploy --only functions --project parcelguard-license
```
The Firestore rules need no new access grants; existing deny-all rules remain appropriate.

## Android UI
- Show `ParcelGuard Mobile / ตลอดชีพ` for `android`
- Show `ParcelGuard Pro / ตลอดชีพ` for `pro`
- Show 7-day trial / remaining date for pre-purchase accounts
- Show old fixed-term expiry date for legacy keys
- Always expose **ตั้งค่า → บัญชีและ License Key**.

## Windows PC: not yet implemented
The backend authorizes a `windows` feature for Pro but **does not yet authenticate the Windows receiver**. Before selling the Pro entitlement as enforced, add PC-side sign-in (or safe QR device linking), short-lived server-authorized session, revoke/relink workflow, and regression tests. A locally paired Wi-Fi receiver alone is **not** proof of Pro entitlement. Avoid embedding admin credentials in the Windows installer.

## Risks & release gates
1. Test new account, same-account reinstall, expired trial, valid lifetime, duplicate redemption, Mobile->Pro and failed Pro->Mobile downgrade.
2. Test Android cold launch and 15-minute revalidation with network loss. Current lease is short: "lifetime" describes entitlement duration, **not perpetual offline access**.
3. App Check is not enforced. Email account creation can restart trial on another UID. Add abuse protection / device policy before public launch.
4. Test backups, reinstall behavior, crashes, existing scanner/recording, ZIP output, PC sync.
5. Commit is to feature branch only. Deploy backend separately with project owner Google account. Merge only after on-device and backend acceptance tests.
