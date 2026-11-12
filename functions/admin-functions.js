"use strict";
const crypto = require("node:crypto");
const {onCall,HttpsError} = require("firebase-functions/v2/https");
const {getFirestore,FieldValue} = require("firebase-admin/firestore");
const {getAuth} = require("firebase-admin/auth");
const {PACKAGES,validPlan} = require("./license-model");
const db=getFirestore();
const settings={region:"asia-southeast1",maxInstances:10,enforceAppCheck:false};
const MAX_LIST=50;
const ms=v=>v&&typeof v.toMillis==="function"?v.toMillis():null;
const sha=value=>crypto.createHash("sha256").update(value,"utf8").digest("hex");
const audit=(action,uid,fields={})=>({
  action,actorUid:uid,timestamp:FieldValue.serverTimestamp(),...fields
});
async function owner(req){
  const uid=req.auth?.uid;
  if(!uid)throw new HttpsError("unauthenticated","Please sign in.");
  if(req.auth.token?.email_verified!==true)
    throw new HttpsError("permission-denied","Verify your email first.");
  const doc=await db.collection("admins").doc(uid).get();
  if(!doc.exists||doc.get("role")!=="owner"||doc.get("disabled")===true)
    throw new HttpsError("permission-denied","Owner access required.");
  return uid;
}
function keySummary(doc){
  const x=doc.data();
  return {
    id:doc.id,plan:x.plan||null,licenseType:x.licenseType||"term",
    priceThb:Number.isFinite(x.priceThb)?x.priceThb:null,
    durationDays:Number.isInteger(x.durationDays)?x.durationDays:null,
    status:x.revokedAt?"revoked":x.redeemedBy?"redeemed":"unused",
    redeemedBy:x.redeemedBy||null,createdAt:ms(x.createdAt),
    redeemedAt:ms(x.redeemedAt),revokedAt:ms(x.revokedAt),
    revokedBy:x.revokedBy||null,issuedVia:x.issuedVia||"legacy",
    paymentId:x.paymentId||null,orderReference:x.orderReference||null
  };
}
function userSummary(doc){
  const x=doc.data();
  return {
    uid:doc.id,plan:x.lifetimePlan||null,lifetime:!!x.lifetimePlan,
    createdAt:ms(x.createdAt),trialEndsAt:ms(x.trialEndsAt),
    licenseEndsAt:ms(x.licenseEndsAt),lifetimeGrantedAt:ms(x.lifetimeGrantedAt),
    schemaVersion:x.schemaVersion||1
  };
}
function paySummary(doc){
  const x=doc.data();
  return {id:doc.id,plan:x.plan,amountThb:x.amountThb,method:x.method,
    reference:x.reference,recordedAt:ms(x.recordedAt),recordedBy:x.recordedBy,
    status:x.issuedKeyHash?"key_issued":"manually_confirmed",issuedKeyHash:x.issuedKeyHash||null};
}
exports.adminWhoAmI=onCall(settings,async req=>({authorized:true,uid:await owner(req),plans:PACKAGES}));
exports.adminListKeys=onCall(settings,async req=>{
  await owner(req);
  const docs=await db.collection("licenseKeys").orderBy("createdAt","desc").limit(MAX_LIST).get();
  return {items:docs.docs.map(keySummary),limit:MAX_LIST};
});
exports.adminListCustomers=onCall(settings,async req=>{
  await owner(req);
  const docs=await db.collection("entitlements").orderBy("createdAt","desc").limit(MAX_LIST).get();
  return {items:docs.docs.map(userSummary),limit:MAX_LIST};
});
exports.adminListPayments=onCall(settings,async req=>{
  await owner(req);
  const docs=await db.collection("manualPayments").orderBy("recordedAt","desc").limit(MAX_LIST).get();
  return {items:docs.docs.map(paySummary),limit:MAX_LIST};
});
exports.adminListAudit=onCall(settings,async req=>{
  await owner(req);
  const docs=await db.collection("adminAudit").orderBy("timestamp","desc").limit(MAX_LIST).get();
  return {items:docs.docs.map(d=>{
    const x=d.data();return {id:d.id,action:x.action,actorUid:x.actorUid,
      plan:x.plan||null,amountThb:x.amountThb??x.priceThb??null,
      licenseHash:x.licenseHash||null,paymentId:x.paymentId||null,
      reason:x.reason||null,timestamp:ms(x.timestamp)};
  }),limit:MAX_LIST};
});
// This is a MANUAL bookkeeping confirmation, not a bank transaction verification.
// Only owner may attest a matching transfer in a separately checked bank channel.
exports.adminConfirmPayment=onCall(settings,async req=>{
  const uid=await owner(req);
  const plan=String(req.data?.plan||"");
  if(!validPlan(plan))throw new HttpsError("invalid-argument","Invalid plan");
  if(req.data?.manuallyChecked!==true)
    throw new HttpsError("failed-precondition","Review payment in your bank first.");
  const amount=Number(req.data?.amountThb);
  if(!Number.isInteger(amount)||amount!==PACKAGES[plan].priceThb)
    throw new HttpsError("invalid-argument","Amount does not match the selected package.");
  const method=String(req.data?.method||"").trim();
  if(!["bank_transfer","promptpay","cash"].includes(method))
    throw new HttpsError("invalid-argument","Invalid payment method.");
  const reference=String(req.data?.reference||"").trim().toUpperCase();
  if(!/^[A-Z0-9_./-]{6,80}$/.test(reference))
    throw new HttpsError("invalid-argument","Reference must be 6-80 characters (letters, digits, _ . / -).");
  const id=sha(method+":"+reference);
  const ref=db.collection("manualPayments").doc(id);
  const batch=db.batch();
  batch.create(ref,{plan,amountThb:amount,method,reference,manuallyChecked:true,
    recordedBy:uid,recordedAt:FieldValue.serverTimestamp(),issuedKeyHash:null});
  batch.create(db.collection("adminAudit").doc(),audit("confirm_manual_payment",uid,
    {paymentId:id,plan,amountThb:amount}));
  try{await batch.commit();}
  catch(e){
    if(e.code===6||e.code==="already-exists")
      throw new HttpsError("already-exists","Payment reference already recorded.");
    throw e;
  }
  return {paymentId:id,plan,amountThb:amount,status:"manually_confirmed"};
});
exports.adminIssueLicense=onCall(settings,async req=>{
  const uid=await owner(req);
  const plan=String(req.data?.plan||"");
  if(!validPlan(plan))throw new HttpsError("invalid-argument","Unknown package.");
  const paymentId=String(req.data?.paymentId||"");
  if(!/^[a-f0-9]{64}$/.test(paymentId))
    throw new HttpsError("failed-precondition","Record a manually confirmed payment first.");
  const key="PG-"+crypto.randomBytes(16).toString("hex").toUpperCase();
  const licenseHash=sha(key);
  const payRef=db.collection("manualPayments").doc(paymentId);
  const keyRef=db.collection("licenseKeys").doc(licenseHash);
  const auditRef=db.collection("adminAudit").doc();
  await db.runTransaction(async tx=>{
    const paymentDoc=await tx.get(payRef);
    if(!paymentDoc.exists)throw new HttpsError("not-found","Payment not found.");
    const payment=paymentDoc.data();
    if(payment.issuedKeyHash)
      throw new HttpsError("already-exists","A license has already been issued for this payment.");
    if(payment.plan!==plan||payment.amountThb!==PACKAGES[plan].priceThb||payment.manuallyChecked!==true)
      throw new HttpsError("failed-precondition","Payment package mismatch.");
    tx.create(keyRef,{
      licenseType:"lifetime",plan,priceThb:PACKAGES[plan].priceThb,
      currency:"THB",redeemedBy:null,createdAt:FieldValue.serverTimestamp(),
      issuedVia:"admin-dashboard",issuedBy:uid,paymentId,
      orderReference:payment.reference
    });
    tx.update(payRef,{issuedKeyHash:licenseHash,issuedAt:FieldValue.serverTimestamp(),issuedBy:uid});
    tx.create(auditRef,audit("issue_lifetime_key",uid,
      {licenseHash,paymentId,plan,priceThb:PACKAGES[plan].priceThb}));
  });
  return {key,plan,priceThb:PACKAGES[plan].priceThb,currency:"THB",licenseHash};
});
exports.adminRevokeUnusedKey=onCall(settings,async req=>{
  const uid=await owner(req);
  const hash=String(req.data?.licenseHash||"").toLowerCase();
  const reason=String(req.data?.reason||"").trim();
  if(!/^[a-f0-9]{64}$/.test(hash)||reason.length<8||reason.length>300)
    throw new HttpsError("invalid-argument","Provide exact license hash and a reason (8-300 chars).");
  const keyRef=db.collection("licenseKeys").doc(hash);
  await db.runTransaction(async tx=>{
    const snap=await tx.get(keyRef);
    if(!snap.exists)throw new HttpsError("not-found","License not found.");
    if(snap.get("redeemedBy"))throw new HttpsError("failed-precondition",
      "Redeemed license cannot be revoked here. Manual review required.");
    if(snap.get("revokedAt"))throw new HttpsError("already-exists","License already revoked.");
    tx.update(keyRef,{revokedAt:FieldValue.serverTimestamp(),revokedBy:uid,revocationReason:reason});
    tx.create(db.collection("adminAudit").doc(),audit("revoke_unused_key",uid,{licenseHash:hash,reason}));
  });
  return {revoked:true,licenseHash:hash};
});
exports.adminSearch=onCall(settings,async req=>{
  await owner(req);
  const type=String(req.data?.type||"");
  const query=String(req.data?.query||"").trim();
  if(query.length>150)throw new HttpsError("invalid-argument","Search is too long.");
  if(type==="license"){
    let hash=query.toLowerCase();
    if(/^PG-[a-f0-9]{32}$/i.test(query))hash=sha(query.toUpperCase());
    if(!/^[a-f0-9]{64}$/.test(hash))
      throw new HttpsError("invalid-argument","Enter full 64-character hash or full license key.");
    const doc=await db.collection("licenseKeys").doc(hash).get();
    return {items:doc.exists?[keySummary(doc)]:[]};
  }
  if(type==="customer"){
    let uid=query;
    if(query.includes("@")){
      try{uid=(await getAuth().getUserByEmail(query)).uid;}
      catch(e){if(e.code==="auth/user-not-found")return {items:[]};throw e;}
    }
    if(!/^[A-Za-z0-9_-]{8,128}$/.test(uid))
      throw new HttpsError("invalid-argument","Enter a Firebase UID or account email.");
    const doc=await db.collection("entitlements").doc(uid).get();
    return {items:doc.exists?[userSummary(doc)]:[]};
  }
  throw new HttpsError("invalid-argument","Select license or customer search.");
});
