# ParcelGuard V0.7.0 — Owner Admin Dashboard

## สถานะ
โค้ดเว็บ Admin, owner-only Cloud Functions และ Firebase Hosting config อยู่ใน Branch `feature/admin-dashboard-v0.7` เท่านั้น **ยังไม่ได้ Deploy ไปยัง Firebase จาก GitHub**. รุ่น V0.6 ที่ Deploy และบัญชีลูกค้าเดิมยังไม่เปลี่ยน

## ฟีเจอร์
- Email/Password sign-in via Firebase Auth; ปุ่มยืนยันอีเมล / รีเซ็ตรหัสผ่าน
- เฉพาะ Firebase UID ที่ถูกแต่งตั้งผ่าน Firestore `admins/{uid}` (`role:owner`) และ email_verified=true จึงเรียก Admin Functions ได้
- ออกคีย์ซื้อขาด Mobile ฿490 หรือ Pro ฿990 หลังเจ้าของกดยืนยันตรวจชำระเงินแล้ว
- ส่งคีย์ใหม่กลับหน้าเว็บเพียงครั้งเดียว; ใน DB เก็บ SHA-256 เท่านั้น
- ประวัติคีย์ล่าสุด 50 รายการ (ใช้แล้ว / ยังไม่ใช้) และบัญชีลูกค้าล่าสุด 50 รายการ
- Audit สำหรับการออกคีย์ใน `adminAudit` พร้อม `actorUid`, แฮชคีย์ และเวลาสร้าง
- ไม่ได้เชื่อม Payment Gateway และไม่ได้ยืนยันการโอนเงินจริงอัตโนมัติ
- ฟังก์ชัน Mobile/Pro License ทั่วไปเดิม `checkAccess` / `redeemLicense` ไม่ถูกเปลี่ยน

## ขั้นที่ 1: ลงทะเบียน Firebase Web App
ใน https://console.firebase.google.com/project/parcelguard-license/settings/general
1. เลื่อนหา **Your apps** → เลือกไอคอน **Web** (`</>`) → ตั้งชื่อ `ParcelGuard Admin`
2. ไม่ต้องเลือก Firebase Hosting ในขั้นตอน registration เพราะ Hosting config อยู่ใน GitHub แล้ว
3. คัดลอกค่า `firebaseConfig` (apiKey, authDomain, projectId, appId) มาใส่ใน `admin-web/firebase-config.js`. **ค่า Firebase Web config ไม่ใช่ Admin SDK secret** แต่อย่าส่ง Service Account private key หรือ Google password
4. ควรใช้ API key ของ Web App ไม่ใช่ key Android ที่เป็นค่าเบื้องต้นใน branch; หากจำกัด API key ตามแอป Android เว็บจะล็อกอินไม่ได้
5. Hosting domain `parcelguard-license.web.app` อยู่ใน Authorized domains ของ Authentication ได้ตามปกติ แต่ให้ตรวจจริงใน Authentication → Settings → Authorized domains หลัง Deploy

## ขั้นที่ 2: Deploy Cloud Functions + Hosting
เปิด Google Cloud Shell ในโปรเจกต์ `parcelguard-license`:
```bash
cd ~/parcelguard-deploy
git fetch origin
git switch -c feature/admin-dashboard-v0.7 --track origin/feature/admin-dashboard-v0.7
npm --prefix functions install
node --check functions/index.js
node --check functions/admin-functions.js
node --check functions/scripts/grant-admin.js
node --test functions/test/license-model.test.js
firebase deploy --only functions,hosting --project parcelguard-license
```
ถ้าอยู่บน Branch admin แล้ว ให้ใช้ `git pull --ff-only` แทน `git switch -c`.
Deployment ครั้งนี้ **ไม่ได้ Deploy / แก้ Firestore Rules** (กฎปิดอ่านเขียน Client ทั้งหมดถูกใช้งานอยู่เดิม)
ตรวจว่ามี 4 functions:
- `checkAccess`, `redeemLicense` (เดิม)
- `adminWhoAmI`, `adminIssueLicense`, `adminListKeys`, `adminListCustomers` (รวมเป็น 4 ใหม่; รวมทั้งหมด 6 ตัว)

## ขั้นที่ 3: ตั้งบัญชี Owner ครั้งแรก (สำคัญมาก)
1. เข้า Firebase Console → Authentication → Users และเลือกบัญชีอีเมลผู้ดูแลที่ต้องการ (แนะนำบัญชีเฉพาะ ไม่ใช่บัญชีลูกค้า)
2. ตรวจสอบว่าอีเมลยืนยันแล้ว (`emailVerified=true`). ถ้ายังไม่ยืนยัน ให้เข้าสู่หน้าเว็บ Admin แล้วกด **ส่งอีเมลยืนยันบัญชี** กดยืนยันจาก Inbox และกด **ตรวจสอบการยืนยันอีเมลอีกครั้ง**
3. คัดลอกค่า **UID** ตรงบัญชี Owner จาก Firebase Console
4. ใน Cloud Shell ที่ auth ด้วยบัญชี Google ซึ่งมีสิทธิ์ Firebase Auth Admin และ Firestore write ให้รัน:
```bash
node functions/scripts/grant-admin.js "YOUR_ADMIN_EMAIL" "EXACT_ADMIN_FIREBASE_UID"
```
สคริปต์ยืนยัน email + UID ตรงกัน และบัญชีไม่ถูกระงับ/ยืนยันอีเมลแล้วก่อนตั้งสิทธิ์ `admins/{uid}.role=owner`.
**ไม่สร้างผู้ดูแลอัตโนมัติ** ไม่ใช้ email เพียงอย่างเดียวในการเช็กสิทธิ์ และ **ห้ามแจกสิทธิ์ Owner ให้ลูกค้า**. หลังออกสิทธิ์จึง Refresh หน้าเว็บได้

## ขั้นที่ 4: ทดสอบระบบ
- เปิดเว็บ Hosting หลัง Deploy (ตัวอย่างโดเมนถ้าถูกสร้าง: https://parcelguard-license.web.app)
- ล็อกอินด้วยบัญชี Owner ที่แต่งตั้ง → เปิด Dashboard
- ล็อกอินด้วยบัญชีลูกค้าธรรมดา → ต้องได้ `permission-denied`
- ออก Mobile 490 → หน้าจอแสดงคีย์เพียงครั้งเดียว พร้อมคัดลอก
- ตรวจ Firestore: `licenseKeys/{hash}` มี `plan:android`, `licenseType:lifetime`, `redeemedBy:null`; audit ต้องมีบันทึก
- กรอกคีย์ที่ APK ทดสอบ → status เปลี่ยนเป็น redeemed และ entitlement เป็น lifetime
- ออก Pro 990 → ทดสอบกับบัญชีอื่น; ตรวจ Mobile → Pro upgrade
- ทดสอบ Refresh / Logout ไม่สามารถเรียกดูคีย์เต็มย้อนหลังได้
- ตรวจความเข้ากันได้ของ Android และ PC; PC-side Pro license enforcement ยังไม่ได้ทำ ห้ามขาย Pro เป็นระบบป้องกันสิทธิ์สมบูรณ์

## คำเตือนด้านความปลอดภัย
- ต้องตรวจสลิป/ยอดโอนจากระบบการเงินจริงก่อนกด checkbox — checkbox ไม่ใช่หลักฐานว่าจ่ายแล้ว
- Firebase Auth + owner role เป็นขอบเขตหลัก; หลังเปิดขายควรบังคับ App Check และเพิ่ม rate limits/2FA สำหรับ Admin
- ไม่มีการเพิกถอนคีย์ที่ใช้แล้วใน UI เวอร์ชันนี้ หากมีข้อพิพาทให้จัดการผ่านกระบวนการดูแลหลังบ้าน
- ไม่ควรเผยแพร่ Admin URL เป็นหน้าให้ลูกค้าใช้งาน การรู้ URL ไม่พอเข้าถึง Admin แต่ไม่ควรอ้างว่า URL เป็นความลับ
- Dashboard รายงานเฉพาะล่าสุด 50 รายการ ไม่ใช่ยอดขายรวมจริง และราคาใน Firestore ไม่ใช่ยอดเงินรับจริง
- Firebase Hosting และ Cloud Functions ใช้ Blaze และอาจเกิดต้นทุน; ตั้งงบ Alert
