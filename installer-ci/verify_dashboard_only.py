"""Contract checks for the packaged dashboard-only PC frontend."""
from pathlib import Path
h=Path("pc-installer/web/index.html").read_text(encoding="utf-8")
j=Path("pc-installer/web/app.js").read_text(encoding="utf-8")
l=Path("pc-installer/web/login.html").read_text(encoding="utf-8")
assert 'data-page="scanner"' not in h, "PC scanner still in navigation"
assert 'class="pg-support-float"' not in h, "LINE floating link still overlaps logout"
assert 'id="pg-dashboard-only-source-v2"' in h
assert 'pg-dashboard-only-nav-removed' in j
assert 'id="page-overview" class="page active"' in h
assert 'id="page-scanner" class="page" aria-hidden="true" hidden' in h
assert "'scanner','overview','bags'" not in j, "Scanner route still enabled"
assert 'class="pg-support-inline"' in h, "LINE link missing in settings"
assert 'class="pg-support-inline"' in l, "LINE link missing in login"
assert 'https://lin.ee/wEsjx0X' in h and 'https://lin.ee/wEsjx0X' in l
assert 'id="scan-preview"' in h, "Original scan DOM removed unexpectedly"
assert 'id="scan-save"' in h, "Original handlers cannot bind"
assert 'src="app.js"' in h
print("DASHBOARD QA PASS: scanner menu and route absent, dashboard active, LINE no overlap, settings/login preserved")
