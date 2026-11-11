"use strict";
// Pure entitlement logic shared by callable functions and tests.
// The large timestamp is solely for backward compatibility with older APKs
// which interpret expiresAt as required for every access check.
const LEGACY_LIFETIME_EXPIRES_AT = Date.UTC(2099, 0, 1);
const PACKAGES = Object.freeze({
  android: Object.freeze({priceThb: 490, features: ["android"]}),
  pro: Object.freeze({priceThb: 990, features: ["android", "windows"]})
});
function toMillis(timestamp) {
  return timestamp && typeof timestamp.toMillis === "function" ? timestamp.toMillis() : 0;
}
function validPlan(value) {return Object.prototype.hasOwnProperty.call(PACKAGES, value);}
function effectivePlan(user) {
  return validPlan(user.lifetimePlan) ? user.lifetimePlan : null;
}
function entitlement(user, now) {
  const plan = effectivePlan(user);
  const trialEnds = toMillis(user.trialEndsAt);
  const paidEnds = toMillis(user.licenseEndsAt);
  const legacyEnds = Math.max(trialEnds, paidEnds);
  const lifetime = plan !== null;
  const active = lifetime || legacyEnds > now;
  return {
    active,
    lifetime,
    plan: plan || (paidEnds > now ? "legacy_term" : "trial"),
    features: lifetime ? PACKAGES[plan].features : ["android"],
    trial: !lifetime && paidEnds <= now && trialEnds > now,
    // Never use this compatibility value as a billing or renewal date.
    expiresAt: lifetime ? LEGACY_LIFETIME_EXPIRES_AT : legacyEnds,
    serverNow: now
  };
}
function applyLifetime(user, purchasedPlan) {
  if (!validPlan(purchasedPlan)) throw new Error("Invalid plan");
  const previous = effectivePlan(user);
  // A mobile purchase must not downgrade an existing Pro license.
  user.lifetimePlan = previous === "pro" ? "pro" : purchasedPlan;
  user.schemaVersion = 2;
  return user;
}
module.exports={PACKAGES, validPlan, effectivePlan, entitlement, applyLifetime, toMillis};
