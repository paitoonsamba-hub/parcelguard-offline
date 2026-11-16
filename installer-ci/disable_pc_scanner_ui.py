"""Retire PC scanner from *source navigation and routing*, retain evidence/API.

Operates on the extracted original V1.4.0 source after LINE support insertion.
No server/database/auth/Hybrid Sync modifications. The legacy scan DOM stays
unreachable (for existing app.js binding compatibility and rollback).
"""
from pathlib import Path
import re

web=Path("pc-installer/web")
page=web/"index.html"
app=web/"app.js"
login=web/"login.html"
html=page.read_text(encoding="utf-8")
js=app.read_text(encoding="utf-8")
login_html=login.read_text(encoding="utf-8")

if 'id="pg-dashboard-only-source-v2"' in html:
    assert 'pg-dashboard-only-nav-removed' in js
    print("Dashboard-only source patch already applied")
    raise SystemExit(0)

# Critical: remove the sidebar button directly from the packaged HTML, rather
# than hiding it after app.js loads.
nav_pattern=r'^[ \t]*<button class="nav-link" data-page="scanner">[^\n]*</button>\s*\n'
html,nav_count=re.subn(nav_pattern,'',html,count=1,flags=re.M)
assert nav_count==1,"PC scanner sidebar navigation missing or changed"

# Keep scan input elements for legacy app.js event bindings, but make the
# entire page unreachable by CSS and routing (including stale deep links).
page_pattern=r'<div id="page-scanner" class="page">'
html,scan_count=re.subn(page_pattern,
    '<div id="page-scanner" class="page" aria-hidden="true" hidden>',
    html,count=1)
assert scan_count==1,"PC scanner page markup changed"
assert html.count('id="page-overview" class="page active"')==1,"Dashboard is not initial page"
assert html.count('data-page="overview"')>=1
assert html.count('data-page="scanner"')==0

style="""<style id="pg-dashboard-only-source-v2">
/* Hide the retired page even if legacy JS tries to activate it. */
#page-scanner, #page-scanner.active {display:none!important}
</style>"""
assert html.count('</head>')==1
html=html.replace('</head>',style+'\n</head>',1)

# Remove LINE floating action from dashboard (it overlays Pro logout), while
# leaving LINE's existing settings inline section and login support intact.
float_re=r'\s*<a class="pg-support-float"[^>]*>.*?</a>'
html,n_float=re.subn(float_re,'',html,count=1,flags=re.S)
assert n_float==1,"Expected LINE floating button not found"
assert 'class="pg-support-inline"' in html,"Settings LINE contact must remain"
assert 'https://lin.ee/wEsjx0X' in html
assert 'class="pg-support-inline"' in login_html,"Login LINE contact must remain"

# Disable scanner navigation at actual route allowlist, not just presentation.
allow="'scanner','overview','bags','evidence','manifest','devices','settings'"
replacement="'overview','bags','evidence','manifest','devices','settings'"
assert js.count(allow)==1,"Original navigation allowlist changed"
js=js.replace(allow,replacement,1)

# Keep remaining original routes and all scan handlers untouched for rollback.
js='/* pg-dashboard-only-nav-removed: scanner route disabled */\n'+js
assert "'scanner','overview'" not in js
assert 'savePcScan' in js
assert 'id="scan-preview"' in html

page.write_text(html,encoding="utf-8")
app.write_text(js,encoding="utf-8")
print("Removed scanner sidebar menu and disabled scanner route. Dashboard stays initial.")
print("LINE floating button removed; settings/login links retained; backend unchanged.")
