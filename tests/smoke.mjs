import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
const read = (p) => readFileSync(new URL(p, import.meta.url), "utf8");
const html = read("../public/index.html");
const css = read("../public/styles.css");
const js = read("../public/app.js");
const headers = read("../public/_headers");
const robots = read("../public/robots.txt");
assert.match(html, /<html lang="th">/);
assert.match(html, /<meta name="viewport"/);
assert.match(html, /<meta name="robots" content="noindex,nofollow">/);
for (const name of ["SPX","LEX-All","490","990","14 วัน","demoVideo","trialDialog"]) {
  assert.ok(html.includes(name), "Missing required site content: "+name);
}
for (const anchor of ["problem","features","how-it-works","pricing","faq","demo"]) {
  assert.ok(html.includes('id="'+anchor+'"'),"Missing section: "+anchor);
}
assert.match(js,/DEMO_VIDEO_URL = ""/);
assert.match(js,/\.showModal\(\)/);
assert.ok(!html.includes('action="https://'),"Unexpected live registration form");
assert.ok(!html.includes("ซื้อเลย"),"Unexpected active checkout text");
assert.match(css,/@media \(max-width:650px\)/);
assert.match(css,/@media \(prefers-reduced-motion:reduce\)/);
assert.match(headers,/Content-Security-Policy/);
assert.match(robots,/Disallow: \//);
for (const asset of ["favicon.svg","styles.css","app.js","404.html"]) {
  assert.ok(existsSync(new URL("../public/"+asset,import.meta.url)),"Missing static asset: "+asset);
}
console.log("ParcelGuard landing page smoke checks passed.");
