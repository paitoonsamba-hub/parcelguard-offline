"use strict";
const test=require("node:test");
const assert=require("node:assert/strict");
const {PACKAGES,validPlan,entitlement,applyLifetime}=require("../license-model");
const timestamp=ms=>({toMillis:()=>ms});
const now=Date.UTC(2026,10,11);
const base=()=>({createdAt:timestamp(now),trialEndsAt:timestamp(now+7*86400000),licenseEndsAt:null,schemaVersion:1});

test("catalog prices and features",()=>{
  assert.equal(PACKAGES.android.priceThb,490);
  assert.equal(PACKAGES.pro.priceThb,990);
  assert.deepEqual(PACKAGES.pro.features,["android","windows"]);
  assert.equal(validPlan("root"),false);
});
test("trial remains seven days",()=>{
  const t=entitlement(base(),now);
  assert.equal(t.active,true);
  assert.equal(t.trial,true);
  assert.equal(t.lifetime,false);
  assert.equal(t.expiresAt,now+7*86400000);
});
test("legacy 30-day license remains dated after migration",()=>{
  const user=base();
  user.licenseEndsAt=timestamp(now+30*86400000);
  const t=entitlement(user,now);
  assert.equal(t.lifetime,false);
  assert.equal(t.plan,"legacy_term");
  assert.equal(t.expiresAt,now+30*86400000);
});
test("mobile lifetime never expires and does not include PC",()=>{
  const user=applyLifetime(base(),"android");
  const t=entitlement(user,Date.UTC(2038,0,1));
  assert.equal(t.active,true);
  assert.equal(t.lifetime,true);
  assert.equal(t.plan,"android");
  assert.deepEqual(t.features,["android"]);
});
test("pro upgrades mobile and cannot be downgraded",()=>{
  const user=applyLifetime(base(),"android");
  applyLifetime(user,"pro");
  applyLifetime(user,"android");
  const t=entitlement(user,Date.UTC(2040,0,1));
  assert.equal(t.plan,"pro");
  assert.equal(t.lifetime,true);
  assert.deepEqual(t.features,["android","windows"]);
});
test("expired trial fails closed",()=>{
  assert.equal(entitlement(base(),now+8*86400000).active,false);
});
