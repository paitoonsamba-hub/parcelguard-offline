"use strict";
const test=require("node:test");
const assert=require("node:assert/strict");
const {PACKAGES}=require("../license-model");
const {STATUS,METHOD,planPrice,reference,paymentId,safeOrder}=require("../sales-model");
test("seller-side fixed price: 490 and 990 only",()=>{
 assert.equal(planPrice("android"),490);
 assert.equal(planPrice("pro"),990);
 assert.deepEqual(PACKAGES.pro.features,["android","windows"]);
 assert.throws(()=>planPrice("free"),/Invalid plan/);
});
test("payment ref normalized and de-duplicated across casing",()=>{
 assert.equal(reference("  bank-20261115-01 "),"BANK-20261115-01");
 assert.equal(paymentId("promptpay"," bank-20261115-01 "),paymentId("promptpay","BANK-20261115-01"));
 assert.notEqual(paymentId("promptpay","BANK-20261115-01"),paymentId("cash","BANK-20261115-01"));
 assert.throws(()=>reference("<img src=x>"));
 assert.throws(()=>paymentId("unknown","BANK-20261115-01"));
});
test("customer views never expose raw license and payment details",()=>{
 const dto=safeOrder({id:"abc",plan:"pro",amountThb:990,status:STATUS.FULFILLED,
  key:"PG-SECRET",reference:"BANK-ABC",customerEmail:"private@example.com"});
 assert.equal(JSON.stringify(dto).includes("PG-SECRET"),false);
 assert.equal(JSON.stringify(dto).includes("BANK-ABC"),false);
 assert.equal(JSON.stringify(dto).includes("private@example.com"),false);
 assert.equal(dto.status,"fulfilled");
});
test("status and allowed payment methods are explicit",()=>{
 assert.equal(STATUS.PENDING,"pending_payment");
 assert.deepEqual(METHOD,["bank_transfer","promptpay","cash"]);
});
