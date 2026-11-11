"use strict";
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const crypto = require("node:crypto");
const {validPlan, entitlement, applyLifetime, toMillis} = require("./license-model");
initializeApp();
const db = getFirestore();
const REGION = "asia-southeast1";
const TRIAL_MS = 7 * 24 * 60 * 60 * 1000;
const options = {region: REGION, enforceAppCheck: false, maxInstances: 20};

function requireUid(req) {
  if (!req.auth || !req.auth.uid) throw new HttpsError("unauthenticated", "Please sign in.");
  return req.auth.uid;
}
function newTrial(now) {
  return {
    createdAt: Timestamp.fromMillis(now),
    trialEndsAt: Timestamp.fromMillis(now + TRIAL_MS),
    licenseEndsAt: null,
    schemaVersion: 2
  };
}
// Trial remains linked to Firebase UID. The client cannot choose the start date.
exports.checkAccess = onCall(options, async req => {
  const uid = requireUid(req);
  const ref = db.collection("entitlements").doc(uid);
  const user = await db.runTransaction(async tx => {
    const existing = await tx.get(ref);
    if (existing.exists) return existing.data();
    const initial = newTrial(Date.now());
    tx.create(ref, initial);
    return initial;
  });
  return entitlement(user, Date.now());
});

exports.redeemLicense = onCall(options, async req => {
  const uid = requireUid(req);
  const key = typeof req.data?.key === "string" ? req.data.key.trim().toUpperCase() : "";
  if (!/^PG-[A-F0-9]{32}$/.test(key))
    throw new HttpsError("invalid-argument", "Invalid license format.");
  const hash = crypto.createHash("sha256").update(key, "utf8").digest("hex");
  const keyRef = db.collection("licenseKeys").doc(hash);
  const userRef = db.collection("entitlements").doc(uid);
  return db.runTransaction(async tx => {
    // Firestore transactions must perform all reads before any writes.
    const [keyDoc, userDoc] = await Promise.all([tx.get(keyRef), tx.get(userRef)]);
    if (!keyDoc.exists) throw new HttpsError("not-found", "License not found.");
    const license = keyDoc.data();
    if (license.redeemedBy && license.redeemedBy !== uid)
      throw new HttpsError("already-exists", "License already redeemed.");
    const now = Date.now();
    const user = userDoc.exists ? userDoc.data() : newTrial(now);
    if (!license.redeemedBy) {
      if (license.licenseType === "lifetime" && validPlan(license.plan)) {
        applyLifetime(user, license.plan);
        user.lifetimeGrantedAt = Timestamp.fromMillis(now);
        user.lastLicenseHash = hash;
      } else if (!license.licenseType && Number.isInteger(license.durationDays) &&
                 license.durationDays >= 1 && license.durationDays <= 3650) {
        // Existing 30/365-day keys are honored; never silently convert them to lifetime.
        const startsAt = Math.max(now, toMillis(user.licenseEndsAt));
        user.licenseEndsAt = Timestamp.fromMillis(startsAt + license.durationDays * 86400000);
      } else {
        throw new HttpsError("failed-precondition", "Invalid license product.");
      }
      tx.update(keyRef, {redeemedBy: uid, redeemedAt: Timestamp.fromMillis(now)});
      tx.set(userRef, user);
    }
    return entitlement(user, now);
  });
});
