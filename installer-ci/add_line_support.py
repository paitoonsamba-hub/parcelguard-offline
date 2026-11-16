"""Add the verified LINE OA support channel to original ParcelGuard PC web UI.

Runs AFTER verified V1.4.0 PC Station source ZIP extraction, BEFORE packaging.
Idempotent; fails closed when original page structure is not found.
Only touches pc-installer/web/index.html and login.html; never server,
database, license code, installer ID, or runtime data.
"""
from pathlib import Path

URL = "https://lin.ee/wEsjx0X"
WEB = Path("pc-installer/web")

STYLE = """
<style id="pg-support-css">
.pg-support-float{position:fixed;right:20px;bottom:20px;z-index:80;display:inline-flex;
align-items:center;gap:10px;border:1px solid rgba(255,255,255,.16);border-radius:999px;
background:#06c755;color:#fff;padding:12px 18px;text-decoration:none;
font:700 14px/1.3 system-ui,'Segoe UI',sans-serif;
box-shadow:0 10px 28px rgba(0,0,0,.35)}
.pg-support-float:hover{filter:brightness(1.07)}
.pg-support-float:focus-visible,.pg-support-inline:focus-visible{outline:3px solid #ffb14d;outline-offset:3px}
.pg-support-inline{display:inline-flex;align-items:center;gap:8px;color:#fff;
background:#06c755;text-decoration:none;padding:11px 15px;border-radius:10px;
font:700 14px/1.3 system-ui,'Segoe UI',sans-serif;margin:9px 0}
.pg-support-icon{display:inline-flex;align-items:center;justify-content:center;width:27px;height:27px;
border-radius:50%;background:rgba(255,255,255,.18);font-size:18px}
@media(max-width:600px){.pg-support-float{right:12px;bottom:12px;padding:10px 13px;font-size:13px}}
</style>
"""

ICON = '<span class="pg-support-icon" aria-hidden="true">☏</span>'
FLOAT = (
    '<a class="pg-support-float" href="'+URL+'" target="_blank" rel="noopener noreferrer" '
    'aria-label="ติดต่อพนักงานผ่าน LINE OA">'
    + ICON + '<span>ติดต่อพนักงาน</span></a>'
)
INLINE = (
    '<section class="pg-support-section" aria-label="ฝ่ายช่วยเหลือ ParcelGuard">'
    '<h3>ฝ่ายช่วยเหลือ ParcelGuard</h3>'
    '<p>TARONIX 208 • ปัญหา License, การสแกนพัสดุ และการใช้งาน PC</p>'
    '<a class="pg-support-inline" href="'+URL+'" target="_blank" '
    'rel="noopener noreferrer">ติดต่อพนักงานผ่าน LINE OA</a>'
    '<p class="minor">โปรดอย่าส่งรหัสผ่านหรือ License Key เต็มผ่านแชต</p>'
    '</section>'
)

def write_if_needed(path, transform):
    s = path.read_text(encoding="utf-8")
    if "pg-support-css" in s:
        assert URL in s and "ติดต่อพนักงาน" in s, "unexpected prior support edit"
        print("Already patched:", path)
        return
    new = transform(s)
    assert new != s and "pg-support-css" in new and URL in new
    path.write_text(new, encoding="utf-8")
    print("Patched verified PC support UI:", path)

def patch_dashboard(s):
    if s.count("</head>") != 1 or s.count("</body>") != 1:
        raise ValueError("Dashboard HTML structure changed")
    if s.count('<div class="settings-actions">') != 1:
        raise ValueError("Settings insertion point changed")
    s = s.replace("</head>", STYLE + "\n</head>", 1)
    s = s.replace('<div class="settings-actions">', INLINE + '\n<div class="settings-actions">', 1)
    return s.replace("</body>", FLOAT + "\n</body>", 1)

def patch_login(s):
    if s.count("</head>") != 1 or s.count("</body>") != 1:
        raise ValueError("Login HTML structure changed")
    if s.count("</main>") != 1:
        raise ValueError("Login main insertion point changed")
    s = s.replace("</head>", STYLE + "\n</head>", 1)
    return s.replace("</main>", INLINE + "\n</main>", 1)

write_if_needed(WEB / "index.html", patch_dashboard)
write_if_needed(WEB / "login.html", patch_login)

# Assertions run even on a second invocation (idempotence verification).
dashboard=(WEB/"index.html").read_text(encoding="utf-8")
login=(WEB/"login.html").read_text(encoding="utf-8")
assert dashboard.count('class="pg-support-float"') == 1
assert dashboard.count('class="pg-support-inline"') == 1
assert login.count('class="pg-support-inline"') == 1
assert all(URL in x for x in (dashboard,login))
print("Support UI integrity checks passed")
