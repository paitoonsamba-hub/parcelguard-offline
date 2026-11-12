"use strict";
const fs=require("node:fs");
const assert=require("node:assert/strict");
const html=fs.readFileSync("admin-web/index.html","utf8");
const ui=fs.readFileSync("admin-web/app.js","utf8");
const server=fs.readFileSync("functions/admin-functions.js","utf8");
const lic=fs.readFileSync("functions/index.js","utf8");
const ids=new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]));
const staticCalls=[...ui.matchAll(/\$\("([^"]+)"\)/g)].map(m=>m[1]);
const missing=[...new Set(staticCalls)].filter(id=>!ids.has(id));
assert.deepEqual(missing,[],"All explicitly-referenced DOM IDs must exist");
for(const id of ["payments-view","audit-view","revoke-form","issue-payment","payment-form",
  "search-key-form","search-customer-form","audit-table","payments-table"]){
  assert.ok(ids.has(id),"Missing V0.7.1 control: "+id);
}
for(const name of ["adminConfirmPayment","adminIssueLicense","adminRevokeUnusedKey",
  "adminSearch","adminListAudit","adminListPayments"]){
  assert.ok(server.includes("exports."+name+"="),"Missing admin endpoint: "+name);
}
assert.ok(lic.includes("if (license.revokedAt)"),"Redeem must reject revoked keys");
assert.ok(server.includes("tx.update(payRef"),"Issuance must atomically consume payment");
assert.ok(server.includes("if(snap.get(\"redeemedBy\"))"),"Redeemed key must not be silently revoked");
console.log("Admin UI and server after-sales static contract checks passed ("+staticCalls.length+" controls).");
