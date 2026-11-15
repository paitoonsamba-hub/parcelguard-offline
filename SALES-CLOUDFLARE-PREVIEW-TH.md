# Cloudflare Pages V1.5 Sales Preview — isolated deployment

## Verified failure
The existing Cloudflare Pages project `parcelguard-offline` uses the GitHub repository root as the Pages project root. Since this monorepo has a top-level `functions/` directory for **Firebase**, Cloudflare attempts to compile those files as Cloudflare Pages Functions and fails with `No routes found when building Functions directory`. This failure also occurs on the V1.4 branch; it is unrelated to V1.5 payment endpoints.

## SAFE resolution — new Pages project, not existing production
To avoid affecting the original Landing Page or production deployment:
1. Cloudflare Dashboard > Workers & Pages > Create application > Pages > Connect to Git.
2. Select `paitoonsamba-hub/parcelguard-offline`.
3. Give the new PREVIEW project a distinct name, e.g. `parcelguard-sales-pilot`.
4. Set production branch for THIS NEW project to `feature/sales-fulfillment-v1.5.0`.
5. Framework preset: None.
6. Build command: blank (or `exit 0`).
7. **Root directory: `sales-web`**.
8. Build output directory: `.` (relative to `sales-web`).
9. Deploy and inspect the Preview URL. This only serves a static page. It does NOT deploy Firebase sales Cloud Functions.
10. The buyer and owner API actions will fail until sales callable functions are independently deployed to a staging Firebase project. DO NOT submit live payments or keys to an incomplete Pilot.
11. For security, restrict preview access before sharing, because the page uses the existing Firebase client config to render UI.

Do NOT change settings on the existing `parcelguard-offline` Pages project merely to silence check errors in the development PR; that would change where the existing project deploys from, affecting its production branch too.

## Integrity
- `sales-web` now includes its own `firebase-config.js` (Firebase public client identifier, not a private service credential). Both buyer and owner scripts import `./firebase-config.js` so the directory is standalone.
- No modifications to `functions/`, `admin-web/`, Firestore rules, or Firebase deployment.
- No change to V1.4 Installer or V1.2.9 Stable.

## Merge gate
Passing Cloudflare static Pages deploy does not replace owner/customer authentication, Firestore Emulator authorization tests, bank-side payment review and one-time license delivery tests.
