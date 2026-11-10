"use strict";
const crypto = require("node:crypto");

const DAY_MS = 24 * 60 * 60 * 1000;
const TRIAL_MS = 7 * DAY_MS;
const OFFLINE_MS = 48 * 60 * 60 * 1000;
const DEVICE_FORMAT = /^[a-f0-9]{64}$/;
class TrialError extends Error {
  constructor(code, message) { super(message); this.code=code; }
}
function keyForDevice(deviceDigest, secret) {
  if (typeof deviceDigest!=="string" || !DEVICE_FORMAT.test(deviceDigest))
    throw new TrialError("INVALID_DEVICE","Invalid device digest");
  if (typeof secret!=="string" || secret.length<32)
    throw new TrialError("NOT_CONFIGURED","Missing server HMAC secret");
  return crypto.createHmac("sha256",secret).update(deviceDigest,"utf8").digest("hex");
}
// Pure and idempotent rules; callers must execute decisions inside a Firestore transaction.
function decideTrial({account,device,uid,deviceKey,now}) {
  if (!uid || typeof uid!=="string") throw new TrialError("INVALID_ACCOUNT","Missing account");
  if (account && account.deviceKey!==deviceKey)
    throw new TrialError("ACCOUNT_ALREADY_USED","This account was used on another device");
  if (device && device.uid!==uid)
    throw new TrialError("TRIAL_ALREADY_USED","This device already received a trial");
  if (Boolean(account)!==Boolean(device))
    throw new TrialError("INCONSISTENT_RECORD","Trial records do not agree");
  if (account && device) {
    if (account.startedAt!==device.startedAt || account.expiresAt!==device.expiresAt)
      throw new TrialError("INCONSISTENT_RECORD","Trial records have mismatched dates");
    return {created:false,startedAt:account.startedAt,expiresAt:account.expiresAt};
  }
  if (!Number.isSafeInteger(now) || now<0)
    throw new TrialError("INVALID_TIME","Invalid trusted server timestamp");
  return {created:true,startedAt:now,expiresAt:now+TRIAL_MS};
}
function createReceipt({uid,deviceDigest,startedAt,expiresAt,now,signingKey}) {
  if (!uid || !DEVICE_FORMAT.test(deviceDigest) || !signingKey)
    throw new TrialError("NOT_CONFIGURED","Invalid receipt signer configuration");
  const payload = {
    version:1,
    uid,
    deviceDigest,
    startedAt,
    expiresAt,
    issuedAt:now,
    offlineUntil:Math.min(expiresAt,now+OFFLINE_MS)
  };
  const part=Buffer.from(JSON.stringify(payload),"utf8").toString("base64url");
  const signature=crypto.sign("sha256",Buffer.from(part,"ascii"),signingKey).toString("base64url");
  return part+"."+signature;
}
module.exports={DAY_MS,TRIAL_MS,OFFLINE_MS,TrialError,keyForDevice,decideTrial,createReceipt};
