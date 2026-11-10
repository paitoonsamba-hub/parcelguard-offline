# ParcelGuard V0.5.0 — Trial Engine (feature branch)

**Status: code-ready integration preview, NOT an activated customer licensing system.**

The ParcelGuard Android code lives on \`main\`. This work is isolated in
\`feature/trial-v050\`; it does **not** alter current Android users, their
SQLite bags, photos, MP4 evidence, or the Cloudflare landing page.

## Scope delivered

- **7 × 24 hours** from the timestamp written by Firebase Cloud Functions
  at first verified-email registration.
- **One trial per account and per Android device fingerprint**. A different
  email on the same device does not reset the trial. Reinstalling the APK
  removes its local state, but the same phone reclaims the old trial record
  on the Firebase server, not seven extra days.
- Server uses **Firestore transactions** to atomically bind both account and
  device at the very first claim; strict checks reject mismatched/corrupt
  partial state.
- Android receives a **P-256 ECDSA-signed receipt** with the original
  expiry and an offline-until timestamp no longer than 48 hours from server
  confirmation.
- The client verifies the signed receipt against the pinned public key and
  the local device digest; uses elapsedRealtime and a persisted high-water
  time to detect common clock rollback.
- When trial is unverified or expired, recording/creating new bags is gated.
  **History, reports, A4/PDF export and access to existing media remain**.
- **No photo, MP4, recipient information or shipping history** is uploaded
  to the trial server.
- UI supports email/password signup, email verification, sign-in, refresh,
  and a prelaunch link to plans (currently NO payment or License activation).
- Backend unit tests for idempotent re-install, new email same device, account
  switched to new device, exactly seven days, receipt signature and offline
  period; Android time-policy smoke tests.
- Android package and SQLite schema unchanged.

## IMPORTANT security and rollout caveats

1. **No Firebase account/project has been configured or deployed by this branch.**
   Therefore the default debug APK is an internal compatibility preview:
   \`PG_TRIAL_ENABLED=false\`. It continues to work like the old app without
   a trial gate so a broken prototype cannot block active packers.
2. A customer APK MUST be built with **PG_TRIAL_ENABLED=true** and all five
   correct public configuration fields. Gradle fails if any are missing.
   Never release an unrestricted debug APK for paying customers.
3. A device fingerprint derived from Android ID is **not tamperproof**. A
   modified/rooted device, app patch or factory reset may bypass it. An
   account alone also cannot prove unique human identity. For commercial
   use, add Firebase App Check/Play Integrity attestation, rate limiting,
   abuse monitoring, and a support/admin device-recovery process.
4. These Cloud Functions are for initial backend integration. Before taking
   money, add proper production monitoring, payment/license logic, admin
   support, privacy/terms, signed RELEASE APK, and backup/recovery testing.
5. The Firebase functions project needs Blaze billing for deployment. Set
   budget alerts/controls first. Firestore direct client access is denied.
6. Signature changes between debug and release APK may change Android ID
   on Android 8+, and APKs signed by different certificates cannot normally
   install over one another. Back up older ParcelGuard data/MP4 before
   migrating; keep the production signing key stable.
7. The demo packaging and manual-time checks are deterrents, not DRM-proof.
   Trial expiration must be verified on real devices and after reboot.

## Firebase project checklist (manual authorization required)

1. Create a new Firebase project for ParcelGuard in
   https://console.firebase.google.com/ (enable a Blaze budget alert).
2. Add an Android app with the **exact package** \`com.parcelguard.offline\`.
   In Project Settings, locate **API key**, **Project ID** and
   **Mobile SDK App ID** \`1:...:android:...\`; the latter is *not*
   the Java applicationId. The code initializes FirebaseOptions explicitly,
   so no \`google-services.json\` is required in the repository.
3. Enable Authentication -> Sign-in method -> **Email/Password**.
   Configure the email-verification sender/domain correctly.
4. Create Firestore in production mode. Deploy the included strict rules.
5. Generate a local P-256 signing key pair on a trusted PC:
   \`cd firebase && node scripts/create-signing-key.mjs\`.
   This prints the **public** key Base64 and saves a **private**
   \`trial-signing-private.pem\` file locally. **Never upload the private
   key to GitHub or ChatGPT.** If compromised, rotate it and publish a
   client with updated public key, with an explicit upgrade plan.
6. Set secret values *from your own computer*, not via a public chat:
   \`firebase functions:secrets:set PG_TRIAL_SIGNING_PRIVATE_KEY --data-file trial-signing-private.pem\`
   and \`firebase functions:secrets:set PG_TRIAL_DEVICE_HMAC\` with a random
   32+ character value. Save HMAC securely; changing it will make existing
   devices look new and can reset their registration records.
7. \`cd firebase && npm install --prefix functions && node --test functions/test/*.test.js\`.
8. From \`firebase/\`, sign into Firebase CLI and select the correct project:
   \`firebase login\`; \`firebase use --add\`; then
   \`firebase deploy --only firestore:rules,functions:trial\`.
   Note the HTTPS URL of \`claimTrial\` in \`asia-southeast1\`.
   **Do not copy an assumed URL**; use the actual deployed URL.
9. Build a **separate test APK** from \`feature/trial-v050\`, setting
   environment variables below. Do not deploy to active warehouse phones.

   \`\`\`shell
   PG_TRIAL_ENABLED=true
   PG_TRIAL_FIREBASE_APP_ID=1:1234567890:android:real-app-id
   PG_TRIAL_FIREBASE_API_KEY=AIza...real-web-api-key
   PG_TRIAL_FIREBASE_PROJECT_ID=your-real-project-id
   PG_TRIAL_ENDPOINT=https://...actual-cloud-functions-url
   PG_TRIAL_VERIFY_SPKI=...Base64-of-SPKI-public-key
   \`\`\`

   These Android client configuration items are public identifiers, **not
   server credentials**; private signing key/HMAC stay only in Firebase
   Secret Manager. Protect backend APIs despite client identifiers being
   visible in the APK.
10. Test first-registration, email verification, re-login, offline token
    expiry, all new-write gates, history exports, reinstall, same-phone new
    account, phone reboot/time rollback, and change of signing certificate.

### Live test matrix (must run on an actual Android phone)

| Case | Expected |
| --- | --- |
| Sign up on verified email | Trial starts once on server |
| Offline for 24 hours | Packing works on trusted receipt |
| Offline for >48 hours | Requires online refresh, history still available |
| Day 8 | Cannot scan/create bag; can export old files |
| Reinstall after day 3 | Same Firebase device record, 4 days remain |
| Sign up fresh email on same phone | Rejected; no free reset |
| Same email on a different phone | Rejected until future paid transfer policy |
| Manually roll clock back | Local receipt rejects unsafe time |
| Server unavailable / no Firebase setup | Commercial build cannot grant new trial |
| Developer preview with Trial disabled | Old packing functions unchanged |
| Broken/lost device / factory reset | Admin support case, not an automatic free reset |

## Current status and next version

- **V0.5.0** = trial and identity infrastructure.
- **V0.5.1** = purchase/license activation (Mobile ฿490 / Hybrid ฿990),
  optional Windows seat registration, fair device transfer and recovery.
- **V0.5.2** = payment verification + automatic issue from web.
- **Do NOT upload this trial backend as a production sales product yet.**

Official references:
- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/auth/android/start
- https://firebase.google.com/docs/functions/config-env
