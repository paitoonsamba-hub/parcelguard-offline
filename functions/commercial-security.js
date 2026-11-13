"use strict";
// ParcelGuard Commercial v1.3 pilot: server-authoritative Pro access and PC seat.
const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {getFirestore,Timestamp}=require("firebase-admin/firestore");
const {entitlement}=require("./license-model");
const db=getFirestore();
const options={region:"asia-southeast1",maxInstances:10,enforceAppCheck:false};
const MAX_PC_SEATS=1;
function assertDevice(v){
  if(typeof v!=="string"||!/^[a-f0-9]{64}$/.test(v))
    throw new HttpsError("invalid-argument","Device hash must be 64 lowercase hex characters.");
  return v;
}
function activePro(ent,key,uid,now){
  const e=entitlement(ent,now);
  return !!(e.active&&e.lifetime&&e.plan==="pro"&&e.features.includes("windows")&&
    !ent.commercialSuspendedAt&&key&&key.licenseType==="lifetime"&&
    key.plan==="pro"&&key.redeemedBy===uid&&!key.revokedAt);
}
exports.commercialCheckPc=onCall(options,async req=>{
  const uid=req.auth?.uid;
  if(!uid)throw new HttpsError("unauthenticated","Sign in first.");
  const device=assertDevice(req.data?.deviceHash);
  const userRef=db.collection("entitlements").doc(uid);
  const seatRef=db.collection("commercialPcSeats").doc(uid);
  return db.runTransaction(async tx=>{
    const [u,seatSnapshot]=await Promise.all([tx.get(userRef),tx.get(seatRef)]);
    if(!u.exists)throw new HttpsError("permission-denied","No Pro purchase on this account.");
    const user=u.data();
    const hash=user.lastLicenseHash;
    if(typeof hash!=="string"||!/^[a-f0-9]{64}$/.test(hash))
      throw new HttpsError("failed-precondition","Legacy Pro key needs owner verification before commercial activation.");
    const keyRef=db.collection("licenseKeys").doc(hash);
    const k=await tx.get(keyRef);
    const now=Date.now();
    if(!activePro(user,k.exists?k.data():null,uid,now))
      throw new HttpsError("permission-denied","Pro license inactive, suspended or revoked.");
    const seat=seatSnapshot.exists?seatSnapshot.data():null;
    if(seat&&seat.blockedDeviceHash===device)
      throw new HttpsError("permission-denied","This PC was released by owner. Contact support.");
    if(seat&&seat.deviceHash&&seat.deviceHash!==device)
      throw new HttpsError("resource-exhausted","PC seat already occupied. Contact support for a transfer.");
    if(!seat)tx.create(seatRef,{deviceHash:device,boundAt:Timestamp.fromMillis(now),lastSeenAt:Timestamp.fromMillis(now)});
    else if(!seat.deviceHash)tx.update(seatRef,{deviceHash:device,boundAt:Timestamp.fromMillis(now),lastSeenAt:Timestamp.fromMillis(now)});
    else tx.update(seatRef,{lastSeenAt:Timestamp.fromMillis(now)});
    return {authorized:true,plan:"pro",lifetime:true,features:["android","windows"],
      maxPcDevices:MAX_PC_SEATS,serverNow:now,recheckAfterSeconds:900};
  });
});
// Only the owner can release a PC seat for a documented hardware transfer.
exports.adminReleasePcSeat=onCall(options,async req=>{
  const adminUid=req.auth?.uid;
  if(!adminUid||req.auth.token?.email_verified!==true)
    throw new HttpsError("permission-denied","Verified owner required.");
  const admin=await db.collection("admins").doc(adminUid).get();
  if(!admin.exists||admin.get("role")!=="owner"||admin.get("disabled")===true)
    throw new HttpsError("permission-denied","Owner required.");
  const customerUid=req.data?.customerUid;
  const reason=String(req.data?.reason||"").trim();
  if(typeof customerUid!=="string"||!/^[A-Za-z0-9_-]{8,128}$/.test(customerUid)||
     reason.length<8||reason.length>300)
    throw new HttpsError("invalid-argument","Customer UID and reason required.");
  const seatRef=db.collection("commercialPcSeats").doc(customerUid);
  await db.runTransaction(async tx=>{
    const old=await tx.get(seatRef);
    if(!old.exists)throw new HttpsError("not-found","No PC seat.");
    if(typeof old.get("deviceHash")!=="string")throw new HttpsError("failed-precondition","PC seat already released.");
    tx.update(seatRef,{deviceHash:null,blockedDeviceHash:old.get("deviceHash"),releasedAt:Timestamp.now(),releasedBy:adminUid});
    tx.create(db.collection("adminAudit").doc(),{
      action:"commercial_pc_seat_release",actorUid:adminUid,customerUid,reason,
      timestamp:Timestamp.now(),previousDeviceHash:old.get("deviceHash")});
  });
  return {released:true};
});
module.exports._test={activePro,assertDevice};
