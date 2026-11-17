import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const read=p=>readFileSync(new URL(p,import.meta.url),"utf8");
const html=read("../public/download.html");
const landing=read("../public/index.html");
const script=read("../public/download.js");
const css=read("../public/download.css");
const manifest=JSON.parse(read("../public/download-manifest.json"));
assert.ok(html.includes('<html lang="th">'));
assert.ok(html.includes('id="android-link"') && html.includes('id="windows-link"'));
assert.ok(html.includes('aria-disabled="true"'));
assert.ok(html.includes("https://lin.ee/wEsjx0X"));
assert.ok(landing.includes('href="./download.html"'));
assert.ok(!landing.includes("ทดลองฟรี 7 วัน"),"Do not promise automatic trial");
assert.equal(manifest.schemaVersion,1);
for(const name of ["android","windows"]){
  const entry=manifest.products?.[name];
  assert.ok(entry, "Missing product "+name);
  if(entry.enabled===true){
    assert.match(entry.version,/^[A-Za-z0-9._-]{1,50}$/);
    assert.match(entry.sha256,/^[a-fA-F0-9]{64}$/);
    const url=new URL(entry.downloadUrl);
    assert.equal(url.protocol,"https:");
    assert.equal(url.hostname,"github.com");
    assert.ok(url.pathname.startsWith("/paitoonsamba-hub/parcelguard-offline/releases/download/"));
    assert.match(url.pathname,name==="android"?/\.apk$/:/\.exe$/);
  }else {
    assert.equal(entry.enabled,false);
    assert.equal(entry.downloadUrl,null,"Disabled item must have no download URL");
  }
}
assert.match(script,/entry\.enabled===true/);
assert.match(script,/hashValid\(entry\.sha256\)/);
assert.match(script,/approvedRelease\(entry\.downloadUrl,product\)/);
assert.match(css,/@media\(max-width:750px\)/);
for(const asset of ["download.css","download.js","download-manifest.json"]){
 assert.ok(existsSync(new URL("../public/"+asset,import.meta.url)));
}
console.log("Customer download page checks passed; unavailable files remain disabled.");
