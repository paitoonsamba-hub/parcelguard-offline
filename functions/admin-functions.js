"use strict";
/**
 * ParcelGuard owner-only admin callables. Never authorize using a client-supplied email,
 * price, UID, or a boolean flag. The whitelist is Firestore admins/{auth.uid}
 * populated by a trusted Firebase Admin SDK CLI, not readable/writable by clients.
 */
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const crypto = require("node:crypto");
const {PACKAGES, validPlan} = require("./license-model");
const db = getFirestore();
const settings = {region:"asia-southeast1",maxInstances:10,enforceAppCheck:false};
const MAX_LIST = 50;
async function owner(request) {
  const uid=request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated","Sign in to continue.");
  if (request.auth.token?.email_verified !== true)
    throw new HttpsError("permission-denied","Verify your email address first.");
  const document=await db.collection("admins").doc(uid).get();
  if (!document.exists || document.get("role") !== "owner" || document.get("disabled") === true)
    throw new HttpsError("permission-denied","You are not an authorized ParcelGuard owner.");
  return uid;
}
const ms=v=>v&&typeof v.toMillis==="function"?v.toMillis():null;
function summary(doc) {
  const x=doc.data();
  return {
    id:doc.id,
    plan:x.plan||null,
    licenseType:x.licenseType||"term",
    priceThb:Number.isFinite(x.priceThb)?x.priceThb:null,
    durationDays:Number.isFinite(x.durationDays)?x.durationDays:null,
    status:x.redeemedBy?"redeemed":"unused",
    redeemedBy:x.redeemedBy||null,
    createdAt:ms(x.createdAt),redeemedAt:ms(x.redeemedAt),
    issuedVia:x.issuedVia||"legacy"
  };
}
exports.adminWhoAmI = onCall(settings,async req=>{
  const uid=await owner(req);
  return {authorized:true,uid,plans:PACKAGES};
});
exports.adminIssueLicense = onCall(settings,async req=>{
  const uid=await owner(req);
  const plan=String(req.data?.plan||"");
  if (!validPlan(plan)) throw new HttpsError("invalid-argument","Unknown package.");
  if (req.data?.paymentConfirmed !== true)
    throw new HttpsError("failed-precondition","Confirm payment outside the app first.");
  const orderRef=String(req.data?.orderReference||"").trim();
  if (orderRef.length>80 || !/^[a-zA-Z0-9 _\-.]*$/.test(orderRef))
    throw new HttpsError("invalid-argument","Invalid order reference.");
  const key="PG-"+crypto.randomBytes(16).toString("hex").toUpperCase();
  const hash=crypto.createHash("sha256").update(key,"utf8").digest("hex");
  const batch=db.batch();
  batch.create(db.collection("licenseKeys").doc(hash),{
    licenseType:"lifetime",plan,priceThb:PACKAGES[plan].priceThb,
    currency:"THB",redeemedBy:null,createdAt:FieldValue.serverTimestamp(),
    issuedBy:uid,issuedVia:"admin-dashboard",orderReference:orderRef||null
  });
  batch.create(db.collection("adminAudit").doc(),{
    action:"issue_lifetime_key",actorUid:uid,licenseHash:hash,
    plan,priceThb:PACKAGES[plan].priceThb,orderReference:orderRef||null,
    timestamp:FieldValue.serverTimestamp()
  });
  await batch.commit();
  // Only return key ONCE. Neither Firestore nor audit logs contain the raw key.
  return {key,plan,priceThb:PACKAGES[plan].priceThb,currency:"THB",licenseHash:hash};
});
exports.adminListKeys = onCall(settings,async req=>{
  await owner(req);
  const snapshot=await db.collection("licenseKeys").orderBy("createdAt","desc").limit(MAX_LIST).get();
  return {items:snapshot.docs.map(summary),limit:MAX_LIST};
});
exports.adminListCustomers = onCall(settings,async req=>{
  await owner(req);
  const snapshot=await db.collection("entitlements").orderBy("createdAt","desc").limit(MAX_LIST).get();
  return {items:snapshot.docs.map(doc=>{
    const x=doc.data();
    return {uid:doc.id,plan:x.lifetimePlan||null,lifetime:!!x.lifetimePlan,
      createdAt:ms(x.createdAt),trialEndsAt:ms(x.trialEndsAt),
      licenseEndsAt:ms(x.licenseEndsAt),lifetimeGrantedAt:ms(x.lifetimeGrantedAt),
      schemaVersion:x.schemaVersion||1};
  }),limit:MAX_LIST};
});
