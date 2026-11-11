"use strict";
// Admin-only CLI: GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
// node functions/scripts/issue-license.js 365
// Never bundle this file or service account credentials into Android APK.
const crypto = require("node:crypto");
const {initializeApp, applicationDefault} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const days = Number(process.argv[2]);
if (!Number.isInteger(days) || days < 1 || days > 3650) {
  console.error("Usage: node functions/scripts/issue-license.js <durationDays 1..3650>");
  process.exit(1);
}
initializeApp({credential: applicationDefault(), projectId: "parcelguard-license"});
const key = "PG-" + crypto.randomBytes(16).toString("hex").toUpperCase();
const hash = crypto.createHash("sha256").update(key, "utf8").digest("hex");
getFirestore().collection("licenseKeys").doc(hash).create({
  durationDays: days, createdAt: FieldValue.serverTimestamp(), redeemedBy: null
}).then(() => {
  console.log("New license (show once):", key);
}).catch(error => {console.error(error.message); process.exitCode = 1;});
