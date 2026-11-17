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
for (const name of ["SPX","LEX-All","490","990","demoVideo","data-request-demo","https://lin.ee/wEsjx0X"]) {
  assert.ok(html.includes(name), "Missing required site content: "+name);
}
for (const anchor of ["problem","features","how-it-works","pricing","faq","demo"]) {
  assert.ok(html.includes('id="'+anchor+'"'),"Missing section: "+anchor);
}
assert.ok(!html.includes("14"+" วัน"),"Old trial duration remains");
// Hero v2 is real responsive markup, not just a static marketing screenshot.
assert.match(html,/<link rel="stylesheet" href="\.\/hero-v2\.css">/);
assert.equal((html.match(/class="solution-card sol-/g)||[]).length,6,
  "Hero must show six connected solution cards");
for (const term of ["สแกนได้หลายขนส่ง","แยกถุง ลดพัสดุปน",
  "พิมพ์สรุป A4 / PDF","หลักฐานครบในที่เดียว",
  "วิดีโอช่วยตรวจน้ำหนัก","ค้นหลักฐานย้อนหลัง",
  "ลงชื่อผู้รับ","ตัวอย่างหน้าจอและข้อมูลจำลอง"]) {
  assert.ok(html.includes(term),"Missing solution messaging: "+term);
}
assert.equal((html.match(/data-request-demo/g)||[]).length,5,"Expected five selected-pilot CTAs");
assert.ok(!html.includes("ทดลองใช้ฟรี 7 วัน") && !html.includes("COMING SOON"),
  "The site must not claim immediate trial availability");
assert.ok(!html.includes("trialDialog") && !html.includes("data-trial"),
  "Legacy Coming Soon modal must be absent");
assert.match(html,/เปิดรับผู้สนใจทดลองแบบคัดเลือก/);
assert.ok(existsSync(new URL("../public/hero-v2.css",import.meta.url)),
  "Missing hero responsive styles");
const heroCss=read("../public/hero-v2.css");
assert.match(heroCss,/@media\(max-width:780px\)/);
assert.match(heroCss,/grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
assert.match(js,/DEMO_VIDEO_URL = ""/);
assert.match(js,/\[data-request-demo\]/);
assert.match(js,/https:\/\/lin\.ee\/wEsjx0X/);
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
