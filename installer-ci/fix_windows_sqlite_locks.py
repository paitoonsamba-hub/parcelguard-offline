"""Fix SQLite backup connection lifetime on Windows after expanding immutable v1.4 source ZIP.

Temporary isolated CI overlay. The original user-uploaded ZIP is not changed.
Any unexpected upstream source mismatch aborts the build.
"""
from pathlib import Path

changes = {
    "pc-installer/server.py": [
        ("import sqlite3\n", "import sqlite3\nfrom contextlib import closing\n"),
        ("with sqlite3.connect(dest / 'parcelguard-pc.sqlite3') as copied:", "with closing(sqlite3.connect(dest / 'parcelguard-pc.sqlite3')) as copied:"),
        ("with sqlite3.connect(dest / 'parcelguard-pc.sqlite3') as check:", "with closing(sqlite3.connect(dest / 'parcelguard-pc.sqlite3')) as check:"),
    ],
    "pc-installer/tests/test_installer_backup.py": [
        ("import sys, tempfile, sqlite3, pathlib, unittest", "import sys, tempfile, sqlite3, pathlib, unittest\nfrom contextlib import closing"),
        ("with sqlite3.connect(out/'parcelguard-pc.sqlite3') as saved:", "with closing(sqlite3.connect(out/'parcelguard-pc.sqlite3')) as saved:"),
    ],
}
for name, replacements in changes.items():
    path = Path(name)
    original = path.read_text(encoding="utf-8")
    updated = original
    for before, after in replacements:
        count = updated.count(before)
        if count != 1:
            raise RuntimeError(f"{name}: expected unique source snippet {before!r}, got {count}; refusing unsafe patch")
        updated = updated.replace(before, after, 1)
    path.write_text(updated, encoding="utf-8")
    print(f"Fixed Windows SQLite connection cleanup: {name}")
