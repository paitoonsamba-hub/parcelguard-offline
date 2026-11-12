"use strict";
/**
 * Bootstrap a single authorized owner on a trusted Cloud Shell.
 * Requires Firebase Auth admin and Firestore write permissions.
 * node functions/scripts/grant-admin.js owner@example.com EXPECTED_FIREBASE_UID
 */
const {initializeApp,applicationDefault}=require("firebase-admin/app");
const {getAuth}=require("firebase-admin/auth");
const {getFirestore,FieldValue}=require("firebase-admin/firestore");
const email=process.argv[2],expectedUid=process.argv[3];
if (!email || !/^\S+@\S+\.\S+$/.test(email) || !expectedUid || process.argv.length!==4) {
 console.error("Usage: node functions/scripts/grant-admin.js <admin-email> <expected-Firebase-UID>");
 process.exit(1);
}
initializeApp({credential:applicationDefault(),projectId:"parcelguard-license"});
(async()=>{
 const user=await getAuth().getUserByEmail(email);
 if (user.uid!==expectedUid) throw new Error("UID mismatch: refusing to grant owner access");
 if (user.disabled || !user.emailVerified) throw new Error("Account must be enabled and email verified");
 const ref=getFirestore().collection("admins").doc(user.uid);
 const existing=await ref.get();
 if (existing.exists && existing.get("role")==="owner" && existing.get("disabled")!==true) {
   console.log("Admin owner already exists for matching UID.");return;
 }
 await ref.set({role:"owner",disabled:false,createdAt:FieldValue.serverTimestamp(),
   emailAtGrant:user.email,grantedVia:"trusted-cloud-shell"}, {merge:true});
 console.log("Owner role granted to UID:",user.uid);
})().catch(e=>{console.error("Admin grant failed:",e.message);process.exitCode=1;});
