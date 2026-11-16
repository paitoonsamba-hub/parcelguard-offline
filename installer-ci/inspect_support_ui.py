"""Read-only diagnostic for the original PC UI in the verified source ZIP.

Prints only HTML tag lines around support/navigation/form controls. This script
does not print Python app secrets or configuration values.
"""
from pathlib import Path
import re
for name in ("index.html", "login.html"):
    p=Path("pc-installer/web")/name
    s=p.read_text(encoding="utf-8")
    print(f"=== {name}: {len(s)} characters ===")
    print("html id attributes (first 100):", re.findall(r'\\bid=["\\\']([^"\\\']+)',s)[:100])
    lines=s.splitlines()
    matches=[]
    for i,line in enumerate(lines):
        low=line.lower()
        if any(word in low for word in ("support", "help", "footer", "settings", "license", "login", "logout", "navbar", "<nav", "ติดต่อ", "ช่วยเหลือ", "ตั้งค่า", "</body>", "sidebar")):
            if "<script" in low or "fetch(" in low or "token" in low or "password" in low: continue
            cleaned = re.sub(r'<[^>]*>', ' ', line).strip()
            if cleaned and len(cleaned)<220:matches.append((i+1,cleaned[:160]))
    print("redacted selected UI text lines:", matches[:65])
    for token in ("</body>", "</main>", "</footer>", "</nav>"):
        print("has",token,token in s.lower())
