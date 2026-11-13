"""Guard against missing imports and swallowed background failures in GUI startup."""
from __future__ import annotations
import ast
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).with_name("main.py").read_text(encoding="utf-8")
TREE = ast.parse(SOURCE)
IMPORTED = set()
for node in TREE.body:
    if isinstance(node, ast.Import):
        IMPORTED.update(x.name.split(".")[0] for x in node.names)
    elif isinstance(node, ast.ImportFrom) and node.module:
        IMPORTED.add(node.module.split(".")[0])

class LoginStartupTests(unittest.TestCase):
    def test_required_auto_pair_modules_imported(self):
        self.assertTrue({"secrets", "time", "hashlib"}.issubset(IMPORTED))

    def test_worker_catches_unexpected_errors(self):
        startup = next(n for n in TREE.body if isinstance(n, ast.ClassDef) and n.name=="ParcelGuardWindow")
        start = next(n for n in startup.body if isinstance(n, ast.FunctionDef) and n.name=="start")
        worker = next(n for n in start.body if isinstance(n, ast.FunctionDef) and n.name=="worker")
        handlers = [n for n in ast.walk(worker) if isinstance(n, ast.ExceptHandler)]
        self.assertTrue(any(isinstance(h.type, ast.Name) and h.type.id=="Exception" for h in handlers),
                        "An unexpected error must not leave the UI loading indefinitely")
        self.assertIn("ลงทะเบียน Auto Connect กับ Firebase", SOURCE)
        self.assertIn("ข้อผิดพลาดภายในโปรแกรม", SOURCE)

if __name__ == "__main__":
    unittest.main()
