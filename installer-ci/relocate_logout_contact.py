"""Move actual Pro logout UI into Settings, replace floating location with LINE contact.

Source ZIP is extracted and Dashboard-only patch already applied.
Maintain original logout event listener / route / CSRF by moving the same
DOM element, not implementing a new logout action.
"""
from pathlib import Path

web=Path("pc-installer/web")
page=web/"index.html"
jsfile=web/"app.js"
html=page.read_text(encoding="utf-8")
app=jsfile.read_text(encoding="utf-8")
marker="pg-contact-logout-v1"

if marker in html:
    assert "pg-logout-relocator" in html and "pg-settings-logout-area" in html and "pg-logout-direct-in-settings" in app
    print("Contact/logout relocation already installed")
    raise SystemExit(0)

assert html.count('id="page-settings"')==1
assert html.count('class="settings-actions"')==1
assert html.count("</head>")==1
assert html.count("</body>")==1
assert 'https://lin.ee/wEsjx0X' in html, "Verified LINE support missing"

style="""<style id="pg-contact-logout-v1">
/* Same lower-right footprint as the former Pro logout, never over a logout. */
.pg-contact-fixed{position:fixed;right:12px;bottom:12px;z-index:75;
 display:inline-flex;align-items:center;justify-content:center;gap:8px;
 background:#21374b;border:1px solid #456079;border-radius:10px;
 color:#fff;padding:12px 16px;text-decoration:none;font-weight:700;
 font-family:system-ui,'Segoe UI',sans-serif;box-shadow:0 8px 18px #0005}
.pg-contact-fixed:hover{background:#28465f}
.pg-contact-fixed:focus-visible{outline:3px solid #ff9c1a;outline-offset:3px}
#pg-logout-in-settings{position:static!important;inset:auto!important;
 left:auto!important;right:auto!important;top:auto!important;bottom:auto!important;
 transform:none!important;z-index:auto!important;display:inline-flex!important;
 width:auto!important;height:auto!important;margin:10px 0!important;
 opacity:1!important;visibility:visible!important}
.pg-settings-logout-area{padding:12px 0;border-top:1px solid #33465a;margin-top:14px}
.pg-settings-logout-area h3{margin:0 0 4px}
.pg-settings-logout-area p{color:#9eb2c5;margin:0 0 9px}
@media(max-width:600px){.pg-contact-fixed{right:10px;bottom:10px}}
</style>"""
html=html.replace("</head>",style+"\n</head>",1)

contact="""<a id="pg-contact-fixed" class="pg-contact-fixed"
href="https://lin.ee/wEsjx0X" target="_blank" rel="noopener noreferrer"
aria-label="ติดต่อเรา ผ่าน LINE OA">☏ ติดต่อเรา</a>"""
html=html.replace("</body>",contact+"\n</body>",1)
# Inject a dedicated Settings container. Keep existing inline LINE section too.
settings="""<section class="pg-settings-logout-area" id="pg-settings-logout-area"
aria-label="ออกจากระบบ Pro"><h3>บัญชี Pro</h3>
<p>ออกจากระบบเมื่อเลิกใช้งานคอมพิวเตอร์เครื่องนี้</p></section>"""
html=html.replace('<div class="settings-actions">',settings+'\n<div class="settings-actions">',1)

# On PC V1.4.0, Pro authentication can render a logout button after the
# original index HTML has loaded. Observe DOM inserts until detected.
# Text fallback supports both the current Thai label and 'Logout Pro'.
# We intentionally do not synthesize a logout or touch session cookies.
behavior=r"""
(function(){
  "use strict";
  const marker="pg-contact-logout-v1";
  const area=document.getElementById("pg-settings-logout-area");
  if(!area)return;
  const textMatches=(t)=>/ออกจากระบบ\s*(?:Pro)?|(?:Log\s*out|Sign\s*out)\s*(?:Pro)?/i.test(t);
  function relocate(){
    const existing=document.getElementById("pg-logout-in-settings");
    if(existing && area.contains(existing))return true;
    // Auth UI may be server-injected. Keep original node and its event
    // listener; remove only floating placement styles and move under Settings.
    const candidates=Array.from(document.querySelectorAll("button,a,[role=button],input[type=button]"));
    const el=candidates.find(n=>
      n.id!=="pg-contact-fixed" && !area.contains(n) &&
      (textMatches((n.innerText||n.textContent||n.value||"").trim()) ||
       /logout|signout/i.test(n.id||"")) &&
      !n.closest("#page-scanner"));
    if(!el)return false;
    el.id="pg-logout-in-settings";
    el.style.setProperty("position","static","important");
    el.style.setProperty("right","auto","important");
    el.style.setProperty("bottom","auto","important");
    el.style.setProperty("transform","none","important");
    el.style.setProperty("z-index","auto","important");
    el.removeAttribute("hidden");
    area.appendChild(el);
    return true;
  }
  let working=false;
  const observer=new MutationObserver(()=>{
    if(working)return;
    working=true;
    try{relocate();}finally{working=false;}
  });
  // The UI may load before or after the authentication state is checked.
  if(document.readyState==="loading"){
    document.addEventListener("DOMContentLoaded",()=>{
      relocate();
      observer.observe(document.body,{childList:true,subtree:true});
    },{once:true});
  }else{
    relocate();
    observer.observe(document.body,{childList:true,subtree:true});
  }
})();
"""
# Behaviors run after original app JS is included, yet the observer also catches
# dynamic elements injected by the Pro-auth UI.
html=html.replace("</body>",'<script id="pg-logout-relocator">'+behavior+'</script>\n</body>',1)
assert html.count('id="pg-contact-fixed"')==1
assert html.count('id="pg-settings-logout-area"')==1
assert html.count('id="pg-logout-relocator"')==1
page.write_text(html,encoding="utf-8")
# The original app.js builds a Pro logout button inside its authenticated
# startup callback and appends it directly to body with fixed positioning.
# Change the source rather than relying on a DOM observer after render.
old_style="out.style='position:fixed;right:14px;bottom:14px;z-index:1000;padding:10px 15px;background:#243748;color:white;border:1px solid #486075;border-radius:8px;cursor:pointer'"
new_style="out.style='padding:10px 15px;background:#243748;color:white;border:1px solid #486075;border-radius:8px;cursor:pointer'"
assert app.count(old_style)==1, "Source logout fixed positioning changed"
app=app.replace(old_style,new_style,1)
old_append="document.body.appendChild(out);refresh();setInterval("
new_append="(document.getElementById('pg-settings-logout-area')||document.body).appendChild(out);refresh();setInterval("
assert app.count(old_append)==1, "Source logout injection changed"
app=app.replace(old_append,new_append,1)
app="/* pg-logout-direct-in-settings */\n"+app
assert 'position:fixed;right:14px;bottom:14px;z-index:1000' not in app
jsfile.write_text(app,encoding="utf-8")
print("Pro logout source now appends directly under Settings; LINE contact remains at lower right")
