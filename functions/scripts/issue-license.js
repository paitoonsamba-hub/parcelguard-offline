"use strict";
/**
 * Manual paid-sale fulfillment from trusted Cloud Shell only.
 * Usage: node functions/scripts/issue-license.js android
 *        node functions/scripts/issue-license.js pro
 * Requires Application Default Credentials with Firestore write permissions.
 * Do not issue before separately verifying the customer payment.
 */
const crypto = require("node:crypto");
const {initializeApp, applicationDefault} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {PACKAGES, validPlan} = require("../license-model");
const plan = (process.argv[2] || "").toLowerCase();
if (!validPlan(plan) || process.argv.length !== 3) {
  console.error("Usage: node functions/scripts/issue-license.js android|pro");
  console.error("android = 490 THB (Android lifetime); pro = 990 THB (Android + Windows lifetime)");
  process.exit(1);
}
initializeApp({credential: applicationDefault(), projectId: "parcelguard-license"});
const key = "PG-" + crypto.randomBytes(16).toString("hex").toUpperCase();
const hash = crypto.createHash("sha256").update(key, "utf8").digest("hex");
getFirestore().collection("licenseKeys").doc(hash).create({
  licenseType: "lifetime",
  plan,
  priceThb: PACKAGES[plan].priceThb, // catalog metadata; NOT proof of payment
  currency: "THB",
  createdAt: FieldValue.serverTimestamp(),
  redeemedBy: null,
  issuedVia: "admin-cli"
}).then(() => {
  console.log("Plan:", plan, "| Lifetime | Catalog price:", PACKAGES[plan].priceThb, "THB");
  console.log("New license (show once):", key);
}).catch(error => { console.error(error.message); process.exitCode = 1; });
