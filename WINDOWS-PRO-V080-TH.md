# ParcelGuard PC V0.8 — Windows Pro License

## สิ่งที่พัฒนา
- โปรแกรม Windows Receiver **ตัวใหม่** (Python/Tkinter → standalone EXE) เพื่อแทนตัวรับ PC รุ่นทดลองที่ไม่มีซอร์สใน Repository นี้
- ล็อกอินบัญชี Firebase Auth ด้วยอีเมลและรหัสผ่านในโปรแกรม PC
- เรียก Cloud Function `checkAccess` และเปิดรับพัสดุเฉพาะเมื่อ `active=true`, `lifetime=true`, `plan=pro`, `features` รวม `windows`
- เช็กสิทธิ์ซ้ำทุก 5 นาทีระหว่างรับไฟล์; หาก Firebase ติดต่อไม่ได้หลังรอบตรวจสอบหมด จะปฏิเสธการรับไฟล์ใหม่
- คู่ Android ด้วย QR `PGPAIR1|<LAN_IP>|8766|<one_time_code>` ใช้ได้ครั้งเดียวภายใน 90 วินาที
- รับ ZIP จาก Android `POST /api/sync/archive`, Bearer token เฉพาะเครื่องที่จับคู่แล้ว, ตรวจชื่อไฟล์, ไฟล์ manifest, SHA256SUMS, ขนาดและ checksum ก่อนเก็บหลักฐาน
- ข้อมูลหลักฐานจัดเก็บใน `%LOCALAPPDATA%\ParcelGuardPC\parcels\`; token จับคู่เก็บเฉพาะ SHA-256 hash ใน `paired-devices.json`
- การอัปโหลดซ้ำ tracking+bag เดิมตอบ `already_imported`, ป้องกันการเขียนทับหลักฐานเดิม
- โปรแกรมไม่เก็บอีเมล/รหัสผ่าน/refresh token บนดิสก์; เปิดโปรแกรมครั้งใหม่ต้องล็อกอินอีกครั้ง

## สถานะและข้อจำกัดสำคัญ
**Prototype / pre-release**: ซอร์สและการ Build EXE ผ่าน CI ไม่เท่ากับทดสอบเครื่องจริง ต้องทดสอบบน Windows 10/11 และ Vivo V29 ก่อนใช้งานกับหลักฐานจริง

1. Android ปัจจุบันมี QR Pairing และ HybridSync อยู่แล้ว จึงไม่ต้องแก้ APK สำหรับการทดสอบโปรโตคอลเบื้องต้น
2. การจับคู่ยืนยันการครอบครอง QR จาก PC ทางกายภาพ แต่ **ยังไม่ได้บังคับว่า Firebase UID ของ Android ต้องเป็นบัญชีเดียวกับ PC**. สิทธิ์ Pro ถูกตรวจจากบัญชีฝั่ง PC เท่านั้น
3. พอร์ต 8766 เป็น **HTTP ไม่เข้ารหัส** ตามโปรโตคอล Android รุ่นเดิม จึงใช้ได้เฉพาะ Wi-Fi ร้านที่เชื่อถือได้ ห้าม port-forward หรือเปิดจากอินเทอร์เน็ต
4. พื้นที่ดิสก์, Windows Defender/Firewall, สิทธิ์ไฟล์, การบันทึกข้อมูลและความสมบูรณ์ของไฟล์ยังต้องทดสอบและทำ backup แยก
5. เป็นโปรแกรมตัวรับข้อมูลผ่าน Wi-Fi และดูหลักฐานได้จากโฟลเดอร์ ไม่ใช่โปรแกรม PC รุ่นเดิมที่มีหน้าดูประวัติ/ฟีเจอร์อื่นทั้งหมด (ยังไม่มีซอร์สเดิม)
6. ไม่มีการออก Pro Key หรือเปลี่ยนสิทธิ์ของลูกค้าใน Firebase จากการสร้างโปรแกรมนี้
7. สิทธิ์ “ตลอดชีพ” หมายถึงไม่มีวันหมดอายุของ License แต่โปรแกรมต้องมีอินเทอร์เน็ตตอนเปิดและตรวจสิทธิ์เป็นระยะ

## ดาวน์โหลดและทดสอบ EXE
เปิด GitHub Actions → workflow **Build ParcelGuard PC V0.8** บน Branch `feature/windows-pro-license-v0.8` → run ที่สถานะ **Success** → Artifacts → `ParcelGuard-PC-V080-Windows` → แตก ZIP → `ParcelGuardPC.exe`.
GitHub build เป็น EXE แบบยังไม่ได้เซ็นดิจิทัล Windows อาจแจ้งเตือน SmartScreen ต้องตรวจว่าดาวน์โหลดจาก Actions ของ Repository ของตนเองเท่านั้น หลีกเลี่ยงการปิด Defender ทั้งระบบ

1. ต่อมือถือ Android และคอมใน Wi-Fi ร้านเดียวกัน และเปิดอินเทอร์เน็ตเพื่อให้ PC ตรวจ License
2. บัญชี Firebase ที่ใช้ล็อกอิน **ต้องมีสิทธิ์ Pro ฿990 แบบตลอดชีพ** (สำหรับ QA แนะนำบัญชีทดสอบเฉพาะที่เปิดสิทธิ์ Pro ผ่านคีย์ที่ผู้ดูแลออกอย่างถูกต้อง)
3. เปิด EXE กรอก Firebase Email/Password → กดตรวจสอบ Pro
4. Windows Firewall อาจถามให้รับ Connection: เลือก **Private networks เท่านั้น** ไม่เปิด Public networks
5. เมื่อเปิดรับข้อมูลสำเร็จ กด “สร้าง QR จับคู่ Android”
6. ใน Android เปิดหน้า QR Pairing → สแกน QR บนจอ PC → ยืนยันคู่
7. ใช้ Android สแกนและถ่ายพัสดุทดสอบ จากนั้นเปิด Wi-Fi Sync
8. ตรวจโฟลเดอร์ `%LOCALAPPDATA%\ParcelGuardPC\parcels` ว่ามี `manifest.csv`, JPG, MP4 และ `SHA256SUMS.txt`
9. ทดสอบส่งซ้ำต้องไม่เกิดไฟล์ซ้ำ; หยุด PC / ตัดเน็ตหลังรอบตรวจสอบผ่านไปแล้วต้องปฏิเสธการรับไฟล์ใหม่
10. ทดสอบบัญชี Mobile ฿490 และบัญชีทดลอง 7 วันบน PC: ต้องถูกปฏิเสธ ไม่เปิดรับไฟล์

## แนวทางต่อไป
- เพิ่มหน้าประวัติ/ค้น tracking และภาพตัวอย่างใน Windows
- ทำ QR แบบผูก Firebase UID เดียวกับ Android (เพิ่ม Callable Function ออกสิทธิ์ Session ให้ PC)
- ปรับโปรโตคอล Sync เป็น HTTPS/mTLS หรือใช้ช่องทางเข้ารหัสสำหรับเครือข่ายที่ไม่ไว้ใจ
- เพิ่มสำรองข้อมูลและตรวจพื้นที่ดิสก์ก่อนรับไฟล์ขนาดใหญ่
- เพิ่มการจัดการอุปกรณ์ที่จับคู่และถอน Token ผ่านหน้าจอ
- เพิ่ม Code Signing และ Release ที่มีหมายเลขเวอร์ชันชัดเจน

## ทดสอบนักพัฒนา
```powershell
py -3.12 -m unittest discover -s windows-receiver -p "test_*.py" -v
py -3.12 -m pip install qrcode==8.2 pillow==11.3.0
py -3.12 windows-receiver/main.py
```
