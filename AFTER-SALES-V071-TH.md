# ParcelGuard V0.7.1 — คู่มือจัดการ License หลังการขาย

## สถานะ
พัฒนาใน Branch `feature/license-after-sales-v0.7.1` แยกจาก V0.7.0 ที่เปิดใช้อยู่ **ยังไม่ได้ Deploy จากการ commit GitHub**. อย่าสั่ง deploy จาก branch `feature/admin-dashboard-v0.7` เพราะจะไม่ได้ฟีเจอร์ V0.7.1

## หน้าจอใหม่
- **รับเงิน**: เลือกแพ็กเกจ Mobile ฿490 / Pro ฿990 ระบุช่องทางและเลขอ้างอิงธุรกรรมไม่ซ้ำ ตรวจเงินเข้าจริงจากบัญชีธนาคาร/PromptPay/เงินสดด้วยตัวเอง แล้วกดยืนยัน
- **จัดการ License**: เลือกเฉพาะรายการรับเงินที่ตรวจแล้วและยังไม่มีคีย์ → ออกคีย์ 1 ดอกต่อ 1 รายการรับเงิน (ป้องกันการกดซ้ำด้วย Firestore Transaction)
- **ค้นหา License**: ใช้คีย์เต็มรูปแบบ `PG-...` หรือ SHA-256 64 ตัว หรือเลือกคีย์ที่ยังไม่ใช้จาก 50 รายการล่าสุด
- **ยกเลิกคีย์**: เฉพาะคีย์ที่ `redeemedBy` ยังว่าง; ต้องใส่เหตุผล 8–300 ตัวอักษร; คีย์ที่ยกเลิกจะใช้ Redeem ไม่ได้ **ไม่ตัดสิทธิ์ผู้ใช้ที่เคย Redeem แล้ว**
- **ค้นหาลูกค้า**: ค้นด้วย Firebase UID หรืออีเมลที่ตรงกับ Firebase Auth (ต้องมีสิทธิ์ Auth Admin ฝั่ง Cloud Functions)
- **ประวัติ Admin**: แสดง 50 รายการล่าสุดของการยืนยันรับเงิน ออกคีย์ และยกเลิกคีย์

**สำคัญมาก:** ไม่มีการเชื่อมธนาคารหรือระบบตรวจสลิปอัตโนมัติ การติ๊ก `ตรวจยอดแล้ว` เป็นการรับรองโดยผู้ดูแลเท่านั้น ไม่ใช่การพิสูจน์ว่ามีเงินเข้า; อย่าป้อนหรือเก็บเลขบัญชีเต็ม หมายเลขบัตร ข้อมูลส่วนตัวในเลขอ้างอิง

## Firestore schema เพิ่ม (Admin SDK เท่านั้น)
- `manualPayments/{sha256(method + ":" + uppercaseReference)}`: `plan`, `amountThb`, `method`, `reference`, `recordedBy`, `recordedAt`, `manuallyChecked`, `issuedKeyHash`, `issuedAt`, `issuedBy`.
- `adminAudit/{randomId}`: `action`, `actorUid`, `timestamp` และรายละเอียดแบบไม่รวม raw key
- `licenseKeys/{sha256(rawKey)}`: เพิ่ม `paymentId`, `revokedAt`, `revokedBy`, `revocationReason` ตามกรณี
- ไฟล์ `firestore.rules` ยังคงปฏิเสธ client read/write ทั้งหมด ไม่มีการเปิดสิทธิ์เพิ่มเติม

## Deploy (Google Cloud Shell)
ในโฟลเดอร์ Clone แบบ single-branch:
```bash
cd ~/parcelguard-deploy
git fetch origin refs/heads/feature/license-after-sales-v0.7.1:refs/remotes/origin/feature/license-after-sales-v0.7.1
git switch -c feature/license-after-sales-v0.7.1 origin/feature/license-after-sales-v0.7.1
node --check functions/index.js
node --check functions/admin-functions.js
node --check admin-web/app.js
node scripts/verify-admin-ui.js
node --test functions/test/license-model.test.js
npm --prefix functions install
firebase deploy --only functions,hosting --project parcelguard-license
```
หาก branch ถูกสร้างไว้แล้ว ให้ `git switch feature/license-after-sales-v0.7.1` และ `git pull --ff-only` แทนคำสั่ง `switch -c`.
Deployment จะเปลี่ยนทั้ง Functions และ Hosting ที่เปิดอยู่ มีผลกับการออกคีย์ แต่คีย์เก่ากับบัญชีลูกค้าไม่ถูกลบ

## Acceptance tests
1. บัญชี Owner เข้าและดูตารางเดิมทั้ง 2+ คีย์ได้
2. บัญชีลูกค้าธรรมดาเรียก `adminListPayments`, `adminSearch`, `adminIssueLicense`, `adminRevokeUnusedKey` ไม่ได้
3. บันทึกรับเงินทดสอบด้วย reference `TEST-20261112-001`, แพ็กเกจ mobile, 490 บาท (เป็นรายการทดสอบ ไม่ใช่เงินรับจริง) และห้ามใช้ออกคีย์ขายให้ลูกค้า
4. reference ซ้ำต้องถูกปฏิเสธ
5. สร้างคีย์จาก payment ID เดียวได้ครั้งเดียว; ลองอีกครั้งต้องล้มเหลว
6. ค้นหา full license hash; ยกเลิกคีย์ที่ยังไม่ Redeem; คีย์ถูกยกเลิกแล้วต้อง Redeem ไม่ผ่าน
7. คีย์ที่ Redeem แล้วต้องไม่สามารถยกเลิกผ่าน Admin Dashboard นี้ได้
8. ค้นหาลูกค้าด้วย UID/อีเมล แล้วตรวจว่าได้บัญชีเดียวตรงกัน
9. ประวัติ Audit ต้องบันทึก 3 เหตุการณ์ข้างต้น
10. ตรวจการทำงานของ `checkAccess` และ `redeemLicense` รุ่นเดิมใน APK

## ขอบเขตและข้อจำกัด
- 50 รายการล่าสุดไม่ใช่รายการทั้งหมด; ช่องค้นหาใช้คีย์เต็มหรือแฮชเต็ม/UID/อีเมล
- คีย์เก่าที่ออกใน V0.7.0 ไม่มี `paymentId`; ยังคงใช้ได้และสถานะยังอ่านได้
- คีย์ที่เคยใช้ไม่รองรับการเพิกถอนสิทธิ์ลูกค้าอัตโนมัติ ต้องมีขั้นตอนตรวจสอบและระบบคืนเงิน/โอนสิทธิ์แยกในรุ่นถัดไป
- การยกเลิกคีย์ไม่คืนเงินจริงโดยอัตโนมัติ; หากผู้ดูแลไม่ได้เก็บ raw key ที่แสดงครั้งเดียว ก็ไม่สามารถดึงกลับมาได้
- Payment ที่ออกคีย์แล้วห้ามนำมาออกคีย์ซ้ำ แม้จะยกเลิกคีย์ในภายหลัง ต้องให้ Owner ตรวจสอบกรณีพิเศษ
- ยังไม่มี App Check enforcement / rate limits / 2FA และยังไม่บังคับ Pro บน Windows; ห้ามโฆษณาว่าป้องกันการละเมิด 100%
