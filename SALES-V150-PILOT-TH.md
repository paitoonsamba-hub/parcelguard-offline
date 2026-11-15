# ParcelGuard V1.5.0 — Sales & Delivery Pilot

## Status / safety
- **Development only. Not deployed.** Branch `feature/sales-fulfillment-v1.5.0`.
- Baseline: V1.4.0 Windows Pilot. Do not modify V1.2.9 Stable, V1.3.0 PC data, Firebase licensing, or existing admin hosting.
- No online payment gateway, receipt/slip upload, automated bank verification, automated email, or automatic app download.
- Do not publish a live payment instruction or share a customer-facing URL until payment owner details, privacy policy, terms, and independent security checks are ready.

## Existing mechanisms reused
- `admins/{uid}` role=owner + verified Firebase user is required for owner endpoints.
- Original `checkAccess`, `redeemLicense`, `adminIssueLicense` and `manualPayments` are unchanged.
- `licenseKeys/{sha256(rawKey)}` stores hashed keys. Android Mobile 490 THB, Pro Android+PC 990 THB.
- Existing `admin-web` remains the active Firebase Hosting target; the new `sales-web` is isolated.

## New API endpoints (un-deployed)
1. `salesCreateOrder({plan:'android'|'pro'})`: verified signed-in customer only; server-selected THB price; 60-second per-UID anti-spam transaction. Creates `salesOrders/{randomId}` pending payment.
2. `salesListMyOrders()`: only own orders, never keys, payment references or other customer information.
3. `salesAdminListOrders()`: owner-only list of 50 recent orders.
4. `salesAdminFulfillOrder({orderId,method,reference,amountThb,manuallyChecked:true})`: verified owner manually checks bank receipt outside system; one Firestore transaction creates a `manualPayments` record, lifetime `licenseKeys`, `adminAudit`, and marks order fulfilled. Duplicate payment references and double fulfillment are rejected. Full `PG-...` key appears **once in the owner response**; never stored as plaintext and never exposed to buyer API.

## Flow — pilot only
- Buyer opens `sales-web/index.html`, signs up/signs in, confirms email, chooses 490 or 990, and creates an order.
- Buyer contacts seller on an authenticated private channel to receive payment instructions. Pilot UI must not accept slips or expose bank credentials.
- Owner opens the existing `admin-web/index.html` and selects the Orders tab, logs in using verified Firebase owner account, sees orders, verifies funds directly in bank, then confirms exact reference and amount.
- Owner copies the one-time key from the confirmation response and sends it privately to verified customer along with a tested, official installation link.
- Buyer redeems key using the existing ParcelGuard Android application; Pro PC sign-in uses the same account.
- Buyer status shows order fulfilled but **does not expose raw key**. If owner loses one-time key, there is no reset/reissue flow yet. Do NOT sell before a supported recovery policy is built.

## Deployment boundary
- New `sales-web` imports `../admin-web/firebase-config.js`, requiring an appropriate site layout or build before hosting.
- Do not deploy `firebase deploy --only hosting` from this branch; it currently publishes `admin-web`, not the sales portal.
- Do not deploy `firebase deploy --only functions` from this branch without verifying existing exports, secrets and deployment diff. Prefer explicitly selected sales endpoints in a staging Firebase project first.
- Firestore Rules remain deny-all on the client; writes occur only in server callable functions.

## Release gates
1. Node CI unit tests + syntax checks; local Firebase Emulator integration tests for customers, owner, duplicate payment, exact money, key redemption.
2. Human walkthrough on mobile and desktop; login / verification / order creation / owner fulfillment.
3. Delivery link from a non-expiring, verified distribution channel (not a short-lived Actions artifact); signed installer and documented SmartScreen expectations.
4. Privacy disclosure, terms of lifetime license, 1-PC binding, returns and support channel.
5. Controlled staged deploy; verify owner dashboard and existing Android and Windows license access remain unaffected.
6. Beta test with 5–10 shops before promoting to retail.

## V1.5 corrective change
- Eliminated duplicate `sales-web/admin.html` and `sales-web/admin.js`.
- Added Orders tab and one-time fulfillment form to the EXISTING Firebase Owner Admin Dashboard (`admin-web/index.html`, `admin-web/app.js`).
- The failure of the *existing* Cloudflare Pages project comes from its repository-root `functions/` (Firebase functions) being misinterpreted as Pages Functions. Do NOT rename Firebase functions. Build the buyer preview using a SEPARATE Cloudflare Pages project with Root directory `sales-web`, output directory `.`. GitHub code alone cannot change the existing Cloudflare project's root-directory setting.
- CI tests syntax and integrated Orders UI. Cloudflare check remains independent and may remain failed until a Pages project is configured correctly.
