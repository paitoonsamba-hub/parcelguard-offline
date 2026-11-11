"use strict";
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const crypto = require("node:crypto");
initializeApp();
const db = getFirestore();
const REGION = "asia-southeast1";
const TRIAL_MS = 7 * 24 * 60 * 60 * 1000;
const options = {region: REGION, enforceAppCheck: false, maxInstances: 20};

function requireUid(req) {
  if (!req.auth || !req.auth.uid) throw new HttpsError("unauthenticated", "Please sign in.");
  return req.auth.uid;
}
function toMillis(value) {
  return value && typeof value.toMillis === "function" ? value.toMillis() : 0;
}
function entitlement(data, now) {
  const expiresAt = Math.max(toMillis(data.trialEndsAt), toMillis(data.licenseEndsAt));
  return {
    active: expiresAt > now,
    trial: toMillis(data.licenseEndsAt) <= now && toMillis(data.trialEndsAt) > now,
    expiresAt,
    serverNow: now
  };
}
// Always compute access on the server. Never accept trial dates from clients.
exports.checkAccess = onCall(options, async (req) => {
  const uid = requireUid(req);
  const ref = db.collection("entitlements").doc(uid);
  const current = await db.runTransaction(async tx => {
    const snapshot = await tx.get(ref);
    if (snapshot.exists) return snapshot.data();
    const now = Date.now();
    const initial = {
      createdAt: Timestamp.fromMillis(now),
      trialEndsAt: Timestamp.fromMillis(now + TRIAL_MS),
      licenseEndsAt: null,
      schemaVersion: 1
    };
    tx.create(ref, initial);
    return initial;
  });
  return entitlement(current, Date.now());
});

exports.redeemLicense = onCall(options, async (req) => {
  const uid = requireUid(req);
  const key = typeof req.data?.key === "string" ? req.data.key.trim().toUpperCase() : "";
  if (!/^PG-[A-F0-9]{32}$/.test(key)) throw new HttpsError("invalid-argument", "Invalid license format.");
  const hash = crypto.createHash("sha256").update(key, "utf8").digest("hex");
  const keyRef = db.collection("licenseKeys").doc(hash);
  const userRef = db.collection("entitlements").doc(uid);
  const result = await db.runTransaction(async tx => {
    const [keyDoc, userDoc] = await Promise.all([tx.get(keyRef), tx.get(userRef)]);
    if (!keyDoc.exists) throw new HttpsError("not-found", "License not found.");
    const record = keyDoc.data();
    if (record.redeemedBy && record.redeemedBy !== uid)
      throw new HttpsError("already-exists", "License already redeemed.");
    const durationDays = record.durationDays;
    if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650)
      throw new HttpsError("failed-precondition", "Invalid license duration.");
    const now = Date.now();
    const user = userDoc.exists ? userDoc.data() : {
      createdAt: Timestamp.fromMillis(now),
      trialEndsAt: Timestamp.fromMillis(now + TRIAL_MS),
      licenseEndsAt: null,
      schemaVersion: 1
    };
    if (!record.redeemedBy) {
      const beginsAt = Math.max(now, toMillis(user.licenseEndsAt));
      user.licenseEndsAt = Timestamp.fromMillis(beginsAt + durationDays * 86400000);
      tx.update(keyRef, {redeemedBy: uid, redeemedAt: Timestamp.fromMillis(now)});
      tx.set(userRef, user);
    }
    return entitlement(user, now);
  });
  return result;
});
