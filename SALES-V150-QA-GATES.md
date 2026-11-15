# ParcelGuard V1.5.0 — Sales QA / staging release checklist

Updated 2026-11-15. Development branch only. DO NOT merge or deploy against production until staged integration checks are complete.

## Verified in GitHub Actions
Workflow: `.github/workflows/test-sales-v150.yml`.
- Syntax of callable server and buyer/owner UI.
- Customer API requires Firebase verified email; no anonymous ordering.
- Server selects price and customer UID (never trusts client supplied amount/UID/status).
- Customers only list their own orders, with no other customer's email or plaintext key.
- Non-owner cannot list or fulfill orders through owner callables.
- Manual bank review flag required; amount must match plan.
- Duplicate order fulfillment and duplicate payment references are rejected in mocked Firestore sequential transactions.
- A license key is returned once to owner and only a hash is stored.
- Self-contained buyer static preview packaged as a GitHub Actions artifact (no Firebase deployment).

These tests run the real sales callable handlers against **mocked** Firebase / Firestore. They do **not** prove production Firestore transaction concurrency, payment settlement, delivery, Cloudflare hosting or authenticated browser behavior.

## Must test before live deployment
1. Firebase Emulator Suite test with **two concurrent owner fulfillment calls** for same order and same payment reference; exactly one may commit.
2. Emulator negative paths: role=customer, admin disabled, email unverified, client-supplied amount/plan/UID.
3. Create two buyer accounts; verify one cannot read the other's order or get the raw key.
4. Real browser UI on Windows and Android; preserve existing Admin pages.
5. Resolve one-time key loss/recovery securely (current raw key is returned only once to owner, and is not recoverable from hashed storage).
6. Define a verified buyer identity and a private channel for key + download delivery; do not put license keys into publicly readable order status.
7. Create a **separate** Cloudflare Pages preview project with Root directory `sales-web`, output `.`; do not repoint existing production Pages root. The original Pages check on the monorepo will still fail unless its settings are separately updated.
8. Stage only the new `sales*` callable functions in a separate Firebase project, then validate price/identity with Emulator and controlled test users.
9. Document refund policy, support, 1-PC binding and lifetime service terms before accepting money.

## Preservation
- Stable V1.2.9 and Pilot V1.4.0 unmodified.
- Existing `admin-web` changes on this branch only; no Production Hosting deploy.
- Existing `checkAccess`, `redeemLicense`, manual payments, and live Firebase collections unaffected.
