"use strict";
const crypto = require("node:crypto");
const {PACKAGES,validPlan} = require("./license-model");

const STATUS = Object.freeze({
  PENDING: "pending_payment",
  FULFILLED: "fulfilled"
});
const METHOD = Object.freeze(["bank_transfer","promptpay","cash"]);
function planPrice(plan) {
  if (!validPlan(plan)) throw new Error("Invalid plan");
  return PACKAGES[plan].priceThb;
}
function reference(value) {
  const v=String(value||"").trim().toUpperCase();
  if (!/^[A-Z0-9_./-]{6,80}$/.test(v)) throw new Error("Invalid payment reference");
  return v;
}
function paymentId(method,ref) {
  if(!METHOD.includes(method))throw new Error("Invalid payment method");
  return crypto.createHash("sha256").update(method+":"+reference(ref),"utf8").digest("hex");
}
function safeOrder(x) {
  return {id:x.id,plan:x.plan,amountThb:x.amountThb,status:x.status,
    createdAt:x.createdAt??null,fulfilledAt:x.fulfilledAt??null,
    // No plaintext license keys or bank details in customer APIs.
    downloadInfo:x.status===STATUS.FULFILLED?"ขอไฟล์ติดตั้งและคีย์จากช่องทางส่งมอบที่ผู้ขายยืนยัน":null};
}
module.exports={STATUS,METHOD,planPrice,reference,paymentId,safeOrder};
