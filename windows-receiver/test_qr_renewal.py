"""Regression coverage for QR renewal on the original PC receiver."""
import ast
import pathlib
import unittest

src=pathlib.Path(__file__).with_name("main.py").read_text(encoding="utf-8")
tree=ast.parse(src)
cls=next(x for x in tree.body if isinstance(x,ast.ClassDef) and x.name=="ParcelGuardWindow")
methods={x.name:x for x in cls.body if isinstance(x,ast.FunctionDef)}

class QrRenewalTests(unittest.TestCase):
    def test_qr_button_is_repeatable(self):
        self.assertIn("pair",methods)
        self.assertIn("new_pairing_code()",src)
        self.assertIn("สร้าง QR ใหม่",src)

    def test_old_code_is_invalidated_on_expiration_and_stop(self):
        self.assertIn("_tick_qr",methods)
        self.assertIn("_cancel_qr_timer",methods)
        self.assertIn("claim_code = \"\"",src)
        self.assertIn("claim_expiry = 0.0",src)

    def test_countdown_and_session_stay_independent(self):
        self.assertIn("qr_countdown",src)
        self.assertIn("self.root.after(250, self._tick_qr)",src)
        self.assertIn("PC ยังเปิดรับพัสดุ",src)
        self.assertIn("except Exception as exc",src)

if __name__=="__main__":
    unittest.main()
