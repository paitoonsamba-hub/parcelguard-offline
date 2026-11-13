"""ParcelGuard PC V0.8 — Windows GUI.

Windows 10/11, Python 3.11+, stdlib tkinter, Pillow + qrcode.
Start the listener only after Firebase checks an active Pro lifetime license.
"""
from __future__ import annotations
import os
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk
from PIL import ImageTk
import qrcode

from license_client import ProLicense, LicenseError
from receiver import ReceiverState, private_lan_ip, serve, PORT

APP_BG = "#0b1219"
CARD = "#152430"
TEXT = "#f0f6fa"
MUTED = "#a4b8c7"
ORANGE = "#ff981c"


def app_folder() -> Path:
    root = os.environ.get("LOCALAPPDATA") or str(Path.home())
    folder = Path(root) / "ParcelGuardPC"
    folder.mkdir(parents=True, exist_ok=True)
    return folder


class ParcelGuardWindow:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("ParcelGuard PC — Pro License")
        self.root.geometry("740x710")
        self.root.minsize(620, 610)
        self.root.configure(bg=APP_BG)
        self.license = ProLicense()
        self.state = ReceiverState(app_folder(), self.license)
        self.state.on_event = self.handle_event
        self.server = None
        self.thread = None
        self.qr_image = None
        self._busy = False
        self.qr_expires_at = 0.0
        self.qr_timer_id = None
        self.qr_host = None
        self._build()
        self.root.protocol("WM_DELETE_WINDOW", self.close)

    def _label(self, parent, value, size=11, fg=TEXT, bold=False):
        return tk.Label(parent, text=value, font=("Segoe UI", size, "bold" if bold else "normal"),
                        fg=fg, bg=parent.cget("bg"), anchor="w", justify="left", wraplength=600)

    def _button(self, parent, title, command, primary=False):
        button = tk.Button(parent, text=title, command=command, relief="flat",
                           bg=ORANGE if primary else "#293d4d",
                           fg="#111923" if primary else TEXT, font=("Segoe UI", 11, "bold"),
                           activebackground="#ffb34c" if primary else "#395368",
                           padx=14, pady=10, cursor="hand2")
        button.pack(fill="x", pady=(12, 0))
        return button

    def _placeholder(self, entry, hint, password=False):
        """Show a hint only while the input is empty and unfocused."""
        hint_color = "#687d8e"
        normal_color = "#14212a"
        def show_hint():
            if not entry.get():
                entry.configure(foreground=hint_color, show="")
                entry.insert(0, hint)
                entry._pg_hint = True
        def focus_in(_event):
            if getattr(entry, "_pg_hint", False):
                entry.delete(0, "end")
                entry.configure(foreground=normal_color, show="●" if password else "")
                entry._pg_hint = False
        def focus_out(_event):
            if not entry.get():
                show_hint()
        entry._pg_hint = False
        entry.bind("<FocusIn>", focus_in)
        entry.bind("<FocusOut>", focus_out)
        show_hint()

    def _build(self):
        outer = tk.Frame(self.root, bg=APP_BG, padx=28, pady=23)
        outer.pack(fill="both", expand=True)
        heading = tk.Frame(outer, bg=APP_BG)
        heading.pack(fill="x")
        self._label(heading, "▣  ParcelGuard", 25, ORANGE, True).pack(anchor="w")
        self._label(heading, "WINDOWS RECEIVER · ANDROID + PC PRO LICENSE", 10, MUTED).pack(anchor="w")

        self.login_card = tk.Frame(outer, bg=CARD, padx=23, pady=20)
        self.login_card.pack(fill="x", pady=(22, 12))
        self._label(self.login_card, "ลงชื่อเข้าใช้บัญชี Firebase ที่ซื้อ Pro ฿990", 13, TEXT, True).pack(anchor="w")
        self._label(self.login_card, "ต้องเป็นบัญชีเดียวกับที่ Redeem คีย์ Pro แล้ว", 10, MUTED).pack(anchor="w", pady=(5, 12))
        self._label(self.login_card, "อีเมล (Email)", 11, TEXT, True).pack(anchor="w", pady=(0, 5))
        self.email = ttk.Entry(self.login_card, font=("Segoe UI", 11))
        self.email.pack(fill="x", ipady=6, pady=(0, 10))
        self._placeholder(self.email, "example@gmail.com")

        self._label(self.login_card, "รหัสผ่าน (Password)", 11, TEXT, True).pack(anchor="w", pady=(0, 5))
        self.password = ttk.Entry(self.login_card, show="●", font=("Segoe UI", 11))
        self.password.pack(fill="x", ipady=6)
        self._placeholder(self.password, "กรอกรหัสผ่าน Firebase", password=True)
        self.login_button = self._button(self.login_card, "ตรวจสอบ Pro และเปิดรับพัสดุ", self.start, True)

        self._label(outer, "สถานะระบบ", 11, MUTED, True).pack(anchor="w", pady=(12, 5))
        self.status = tk.StringVar(value="ยังไม่ได้เข้าสู่ระบบ — ปิดรับข้อมูลพัสดุ")
        tk.Label(outer, textvariable=self.status, bg=CARD, fg=TEXT, font=("Segoe UI", 11),
                 anchor="w", padx=16, pady=14, wraplength=620, justify="left").pack(fill="x")

        self.qr_frame = tk.Frame(outer, bg=APP_BG)
        self.qr_frame.pack(fill="x", pady=(12, 0))
        self.qr_label = tk.Label(self.qr_frame, bg=APP_BG)
        self.qr_label.pack(side="left")
        self.qr_note = self._label(self.qr_frame, "เมื่อ Pro ผ่าน ให้กดสร้าง QR เพื่อจับคู่ Android", 11, MUTED)
        self.qr_note.pack(side="left", padx=20)

        buttons = tk.Frame(outer, bg=APP_BG)
        buttons.pack(fill="x")
        self.pair_btn = self._button(buttons, "สร้าง QR จับคู่ Android", self.pair)
        self.qr_countdown = tk.StringVar(value="QR ยังไม่ถูกสร้าง")
        tk.Label(buttons, textvariable=self.qr_countdown, bg=APP_BG, fg=MUTED,
                 font=("Segoe UI", 11), anchor="w").pack(fill="x", pady=(6, 0))
        self.pair_btn.configure(state="disabled")
        self.stop_btn = self._button(buttons, "ปิดรับพัสดุและออกจากระบบ", self.stop)
        self.stop_btn.configure(state="disabled")
        self.sync_status = tk.StringVar(value="รอจับคู่ Android และรับไฟล์ ZIP")
        tk.Label(outer, textvariable=self.sync_status, bg=CARD, fg=TEXT,
                 font=("Segoe UI", 10), padx=12, pady=12, anchor="w", justify="left",
                 wraplength=600).pack(fill="x", pady=(12, 0))
        self._button(outer, "เปิดโฟลเดอร์หลักฐานที่ได้รับ", self.open_parcels)
        self._label(outer, "หลักฐานจัดเก็บใน %LOCALAPPDATA%\\ParcelGuardPC\\parcels", 9, MUTED).pack(anchor="w", pady=(14, 0))
        self._label(outer, "ซิงก์ผ่าน Wi-Fi ส่วนตัวเท่านั้น • ห้ามเปิดพอร์ต 8766 สู่อินเทอร์เน็ต", 9, MUTED).pack(anchor="w", pady=(5, 0))

    def handle_event(self, event):
        def update():
            count = self.state.count_parcels()
            self.sync_status.set(event["message"] + " • หลักฐานใน PC " + str(count) + " รายการ")
            if event["kind"] == "error":
                self.status.set(event["message"])
        self.root.after(0, update)

    def open_parcels(self):
        folder = self.state.folder / "parcels"
        folder.mkdir(parents=True, exist_ok=True)
        os.startfile(str(folder))
        self.sync_status.set("โฟลเดอร์หลักฐาน • " + str(self.state.count_parcels()) + " รายการ")

    def notify(self, text):
        self.root.after(0, lambda: self.status.set(text))

    def start(self):
        if self._busy or self.server:
            return
        email = "" if getattr(self.email, "_pg_hint", False) else self.email.get().strip()
        password = "" if getattr(self.password, "_pg_hint", False) else self.password.get()
        if not email or not password:
            messagebox.showwarning("เข้าสู่ระบบ", "กรอกอีเมลและรหัสผ่าน Firebase")
            return
        self._busy = True
        self.login_button.configure(state="disabled")
        self.status.set("กำลังตรวจสอบ License กับ Firebase...")
        self.password.delete(0, "end")

        def worker():
            try:
                self.license.sign_in(email, password)
                server = serve(self.state)
                self.server = server
                self.thread = threading.Thread(target=server.serve_forever, daemon=True)
                self.thread.start()
                self.notify("Pro Lifetime ผ่านการตรวจสอบ • PC เปิดรับ ZIP บน Wi-Fi พอร์ต 8766")
                self.root.after(0, lambda: self.pair_btn.configure(state="normal"))
                self.root.after(0, lambda: self.stop_btn.configure(state="normal"))
            except Exception as exc:
                # Background errors must never leave the UI stuck at "checking".
                if isinstance(exc, LicenseError):
                    message = str(exc)
                elif isinstance(exc, OSError):
                    message = "ตรวจสอบ Wi-Fi / พอร์ต 8766 / Windows Firewall"
                else:
                    message = "ข้อผิดพลาดภายใน (" + type(exc).__name__ + ")"
                self.notify("เปิด Receiver ไม่สำเร็จ: " + message)
                self.license.sign_out()
            finally:
                self._busy = False
                self.root.after(0, lambda: self.login_button.configure(state="normal"))
        threading.Thread(target=worker, daemon=True).start()

    def _cancel_qr_timer(self):
        if self.qr_timer_id is not None:
            self.root.after_cancel(self.qr_timer_id)
            self.qr_timer_id = None

    def _tick_qr(self):
        self.qr_timer_id = None
        if not self.server or not self.qr_expires_at:
            return
        remaining = max(0, int(self.qr_expires_at - time.monotonic() + 0.999))
        if remaining == 0:
            # Invalidate both the visible QR and its one-time server-side code.
            with self.state.lock:
                self.state.claim_code = ""
                self.state.claim_expiry = 0.0
            self.qr_expires_at = 0.0
            self.qr_label.configure(image="")
            self.qr_image = None
            self.qr_countdown.set("QR หมดอายุแล้ว — กดปุ่มสร้าง QR ใหม่ได้ทันที")
            self.qr_note.configure(text="กดปุ่มสร้าง QR ใหม่เพื่อสแกนอีกครั้ง • ไม่ต้องล็อกอินใหม่")
            self.pair_btn.configure(text="สร้าง QR ใหม่")
            self.status.set("QR หมดอายุ • PC ยังเปิดรับพัสดุและ License Pro ยังใช้งานได้")
            return
        self.qr_countdown.set(f"QR ใช้ได้อีก {remaining} วินาที")
        self.qr_timer_id = self.root.after(250, self._tick_qr)

    def pair(self):
        if not self.server or not self.license.approved_now():
            self.status.set("ไม่มีสิทธิ์ Pro ที่ตรวจสอบได้ — สร้าง QR ไม่ได้")
            return
        ip = private_lan_ip()
        if ip.startswith("127."):
            self.status.set("ไม่พบ IP Wi-Fi/LAN ส่วนตัวของ PC")
            return
        # Every press creates a fresh one-time code and invalidates the old QR.
        self._cancel_qr_timer()
        code = self.state.new_pairing_code()
        self.qr_expires_at = time.monotonic() + 90
        pairing_text = f"PGPAIR1|{ip}|{PORT}|{code}"
        img = qrcode.make(pairing_text).convert("RGB").resize((195, 195))
        self.qr_image = ImageTk.PhotoImage(img)
        self.qr_label.configure(image=self.qr_image)
        self.qr_note.configure(text=f"เปิด Android → ตั้งค่า → เชื่อมต่อ PC ด้วย QR\\nIP: {ip}:{PORT}\\nกดสร้างใหม่ได้ทุกเมื่อ")
        self.pair_btn.configure(text="สร้าง QR ใหม่ (รหัสเดิมจะใช้ไม่ได้)")
        self.status.set("QR พร้อมสแกน • กดสร้างใหม่ได้ทันทีเมื่อหมดอายุ")
        self._tick_qr()

    def stop(self):
        self._cancel_qr_timer()
        self.qr_expires_at = 0.0
        with self.state.lock:
            self.state.claim_code = ""
            self.state.claim_expiry = 0.0
        self.qr_countdown.set("QR ยังไม่ถูกสร้าง")
        self.pair_btn.configure(text="สร้าง QR จับคู่ Android")
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None
        self.license.sign_out()
        self.qr_label.configure(image="")
        self.qr_image = None
        self.pair_btn.configure(state="disabled")
        self.stop_btn.configure(state="disabled")
        self.status.set("ปิดการรับพัสดุและออกจากระบบแล้ว")

    def close(self):
        self.stop()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    ParcelGuardWindow().run()
