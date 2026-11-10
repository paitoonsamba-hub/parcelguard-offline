"use strict";
const {onRequest} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const {TrialError,keyForDevice,decideTrial,createReceipt} = require("./trial-core");

initializeApp();
const deviceHmac = defineSecret("PG_TRIAL_DEVICE_HMAC");
const signingKey = defineSecret("PG_TRIAL_SIGNING_PRIVATE_KEY");

exports.claimTrial = onRequest({
  region:"asia-southeast1",
  cors:false,
  secrets:[deviceHmac,signingKey],
  maxInstances:3,
  timeoutSeconds:20,
  invoker:"public"
}, async(req,res)=>{
  res.set("Cache-Control","no-store");
  res.set("X-Content-Type-Options","nosniff");
  if(req.method!=="POST")return res.status(405).json({error:"METHOD_NOT_ALLOWED"});
  if(!req.is("application/json"))return res.status(415).json({error:"JSON_REQUIRED"});
  try {
    const bearer=/^Bearer\s+(\S+)$/.exec(req.get("Authorization")||"");
    if(!bearer)return res.status(401).json({error:"AUTH_REQUIRED"});
    const userToken=await getAuth().verifyIdToken(bearer[1],true);
    const user=await getAuth().getUser(userToken.uid);
    // Verification ensures one account per deliverable email, but it is not
    // a guarantee of a unique human. The signed-in Android app must reload
    // FirebaseUser and force-refresh its token after email verification.
    if(!user.emailVerified)return res.status(403).json({error:"VERIFY_EMAIL"});
    const digest=req.body && req.body.deviceDigest;
    const deviceKey=keyForDevice(digest,deviceHmac.value());
    const db=getFirestore();
    const accountRef=db.collection("trialAccounts").doc(userToken.uid);
    const deviceRef=db.collection("trialDevices").doc(deviceKey);
    const now=Date.now();
    const trial=await db.runTransaction(async(tx)=>{
      // Firestore transactions must perform all reads before writes.
      const [accountSnapshot,deviceSnapshot] = await Promise.all([
        tx.get(accountRef), tx.get(deviceRef)
      ]);
      const account=accountSnapshot.exists ? accountSnapshot.data() : null;
      const device=deviceSnapshot.exists ? deviceSnapshot.data() : null;
      const decision=decideTrial({
        account,device,uid:userToken.uid,deviceKey,now
      });
      if(decision.created){
        tx.create(accountRef,{
          deviceKey,
          startedAt:decision.startedAt,
          expiresAt:decision.expiresAt,
          createdAt:new Date(now)
        });
        tx.create(deviceRef,{
          uid:userToken.uid,
          startedAt:decision.startedAt,
          expiresAt:decision.expiresAt,
          createdAt:new Date(now)
        });
      }
      return decision;
    });
    const receipt=createReceipt({
      uid:userToken.uid,
      deviceDigest:digest,
      startedAt:trial.startedAt,
      expiresAt:trial.expiresAt,
      now,
      signingKey:signingKey.value().replace(/\\n/g,"\n")
    });
    return res.status(200).json({
      receipt,
      serverTime:now,
      trialEndsAt:trial.expiresAt,
      remainingMs:Math.max(0,trial.expiresAt-now),
      created:trial.created
    });
  }catch(error){
    const code=error instanceof TrialError?error.code:"INTERNAL";
    const known={
      INVALID_DEVICE:400,
      ACCOUNT_ALREADY_USED:409,
      TRIAL_ALREADY_USED:409,
      INCONSISTENT_RECORD:503,
      NOT_CONFIGURED:503
    };
    // Avoid logging token, email, Android ID, secrets or other PII.
    console.error("claimTrial failed",code);
    return res.status(known[code]||500).json({error:code});
  }
});
