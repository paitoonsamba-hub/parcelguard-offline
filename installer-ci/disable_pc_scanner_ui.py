"""Make the verified PC V1.4.0 station a dashboard-only client.

Hide the legacy PC scan page and all navigation to it. Keep the DOM and
original server/database intact for safe rollback and non-destructive upgrade.
The PC camera/auto-scan/bag QA injection steps are omitted by this branch's
installer workflow. Android remains the capture device.
"""
from pathlib import Path

web = Path("pc-installer/web")
page = web / "index.html"
login = web / "login.html"
s = page.read_text(encoding="utf-8")
marker = 'id="pg-dashboard-only-mode"'
if marker in s:
    assert 'pg-dashboard-only-script' in s
    print("Dashboard-only patch already applied")
    raise SystemExit(0)

assert s.count('id="page-overview"') == 1, "Original Dashboard missing"
assert s.count('id="scan-preview"') == 1, "Original PC scanner panel missing"
assert s.count('</head>') == 1 and s.count('</body>') == 1
assert 'src="app.js"' in s, "Original PC app script missing"

css = """<style id="pg-dashboard-only-mode">
/* UI-only feature retirement; never deletes evidence or app data. */
.page.pg-retired-pc-scanner,
[data-pg-scan-retired="true"] { display:none !important; }
</style>"""
js = """<script id="pg-dashboard-only-script">
(function(){
  'use strict';
  const scanPreview=document.getElementById('scan-preview');
  const scanPage=scanPreview && scanPreview.closest('.page');
  const dashboard=document.getElementById('page-overview');
  if (!scanPage || !dashboard || !/^page-/.test(scanPage.id)) {
    console.error('ParcelGuard Dashboard-only: original page contract changed');
    return;
  }
  const scanKey=scanPage.id.slice(5);
  if (scanKey==='overview') return;
  scanPage.classList.add('pg-retired-pc-scanner');
  scanPage.classList.remove('active');

  function concealScannerLinks(){
    document.querySelectorAll('[data-page]').forEach(el=>{
      if(el.getAttribute('data-page')===scanKey){
        el.setAttribute('data-pg-scan-retired','true');
        el.setAttribute('aria-hidden','true');
        el.setAttribute('tabindex','-1');
      }
    });
    document.querySelectorAll('a[href]').forEach(el=>{
      const href=el.getAttribute('href');
      if(href==='#'+scanPage.id){
        el.setAttribute('data-pg-scan-retired','true');
        el.setAttribute('aria-hidden','true');
        el.setAttribute('tabindex','-1');
      }
    });
  }
  concealScannerLinks();
  dashboard.classList.add('active');
  const dashboardNav=document.querySelector('[data-page="overview"]');
  if(dashboardNav){
    dashboardNav.classList.add('active');
    dashboardNav.setAttribute('aria-current','page');
  }
  // Block old shortcuts without touching application routing, receiver or data.
  document.addEventListener('click',e=>{
    const el=e.target.closest('[data-page],a[href]');
    if(!el)return;
    if(el.getAttribute('data-page')===scanKey ||
       el.getAttribute('href')==='#'+scanPage.id){
      e.preventDefault();e.stopImmediatePropagation();
    }
  },true);
  window.addEventListener('hashchange',()=>{
    if(location.hash==='#'+scanPage.id)location.hash='#page-overview';
  });
})();
</script>"""
s = s.replace('</head>',css+'\n</head>',1)
s = s.replace('</body>',js+'\n</body>',1)
page.write_text(s,encoding='utf-8')

# Only user-facing description changes, never the sign-in form/logic.
l = login.read_text(encoding='utf-8')
l2 = l.replace('เพื่อเปิด Dashboard สแกนและรับพัสดุ',
               'เพื่อเปิด Dashboard ตรวจสอบถุงและหลักฐานจาก Android')
if l2 != l: login.write_text(l2,encoding='utf-8')

assert marker in page.read_text(encoding='utf-8')
assert 'pg-dashboard-only-script' in page.read_text(encoding='utf-8')
assert 'id="scan-preview"' in page.read_text(encoding='utf-8'), "Must preserve rollback-friendly page markup"
print("Dashboard-only mode added: legacy scan page/menu hidden; original app/backend untouched")
