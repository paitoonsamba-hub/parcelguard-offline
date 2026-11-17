"""Check the packaged PC contact+logout UI without changing runtime data."""
from pathlib import Path
import re,subprocess,tempfile
h=Path("pc-installer/web/index.html").read_text(encoding="utf-8")
j=Path("pc-installer/web/app.js").read_text(encoding="utf-8")
assert h.count('id="pg-contact-fixed"')==1
assert h.count('id="pg-settings-logout-area"')==1
assert h.count('id="pg-logout-relocator"')==1
assert "https://lin.ee/wEsjx0X" in h
assert '☏ ติดต่อเรา' in h
assert 'pg-contact-logout-v1' in h
assert 'area.appendChild(el)' in h
assert 'class="pg-support-float"' not in h
assert 'data-page="scanner"' not in h
assert 'pg-dashboard-only-nav-removed' in j
assert 'id="page-overview" class="page active"' in h
assert 'pg-support-inline' in h
assert 'id="scan-preview"' in h
match=re.search(r'<script id="pg-logout-relocator">([\s\S]*?)</script>',h)
assert match,"Relocator script missing"
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"relocator.js"
    p.write_text(match.group(1),encoding="utf-8")
    subprocess.run(["node","--check",str(p)],check=True)
print("PASS: Dashboard-only preserved, contact points to LINE, logout moves under settings, JS syntax valid")
