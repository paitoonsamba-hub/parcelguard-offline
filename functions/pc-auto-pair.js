"use strict";
const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {getFirestore,Timestamp}=require("firebase-admin/firestore");
const {entitlement}=require("./license-model");
const db=getFirestore();
const settings={region:"asia-southeast1",maxInstances:12,enforceAppCheck:false};
const SESSION_MS=10*60*1000;
const privateIp=(ip)=>{
  if(typeof ip!=="string"||!/^\d{1,3}(?:\.\d{1,3}){3}$/.test(ip))return false;
  const p=ip.split(".").map(Number);
  if(p.some(n=>n>255))return false;
  return p[0]===10||(p[0]===172&&p[1]>=16&&p[1]<=31)||(p[0]===192&&p[1]===168);
};
async function proUid(req){
  const uid=req.auth?.uid;
  if(!uid)throw new HttpsError("unauthenticated","Sign in first.");
  const snap=await db.collection("entitlements").doc(uid).get();
  if(!snap.exists)throw new HttpsError("permission-denied","Pro lifetime license required.");
  const e=entitlement(snap.data(),Date.now());
  if(!e.active||!e.lifetime||e.plan!=="pro"||!e.features.includes("windows"))
    throw new HttpsError("permission-denied","Pro lifetime license required.");
  return uid;
}
exports.registerPcReceiver=onCall(settings,async req=>{
  const uid=await proUid(req);
  const host=req.data?.host,token=req.data?.token,sessionId=req.data?.sessionId;
  if(!privateIp(host)||typeof token!=="string"||!/^[A-Za-z0-9_-]{32,128}$/.test(token)||
     typeof sessionId!=="string"||!/^[A-Fa-f0-9]{32}$/.test(sessionId))
    throw new HttpsError("invalid-argument","Invalid private LAN address, session or token");
  const ref=db.collection("pcReceivers").doc(uid);
  // PC owns an ephemeral session; a different PC may supersede it when same UID signs in.
  await ref.set({host,port:8766,token,sessionId,updatedAt:Timestamp.now(),
    expiresAt:Timestamp.fromMillis(Date.now()+SESSION_MS)});
  return {registered:true,expiresInSeconds:SESSION_MS/1000};
});
exports.findPcReceiver=onCall(settings,async req=>{
  const uid=await proUid(req);
  const snap=await db.collection("pcReceivers").doc(uid).get();
  if(!snap.exists)return {available:false};
  const x=snap.data();
  if(!privateIp(x.host)||x.port!==8766||typeof x.expiresAt?.toMillis!=="function"||x.expiresAt.toMillis()<=Date.now()||
     typeof x.token!=="string"||!/^[A-Za-z0-9_-]{32,128}$/.test(x.token))
    return {available:false};
  return {available:true,host:x.host,port:8766,token:x.token,
    sessionId:x.sessionId,expiresAt:x.expiresAt.toMillis()};
});
exports.unregisterPcReceiver=onCall(settings,async req=>{
  const uid=await proUid(req);
  const sessionId=req.data?.sessionId;
  if(typeof sessionId!=="string"||!/^[A-Fa-f0-9]{32}$/.test(sessionId))
    throw new HttpsError("invalid-argument","Invalid session");
  const ref=db.collection("pcReceivers").doc(uid);
  await db.runTransaction(async tx=>{
    const doc=await tx.get(ref);
    if(doc.exists&&doc.get("sessionId")===sessionId)tx.delete(ref);
  });
  return {ok:true};
});
