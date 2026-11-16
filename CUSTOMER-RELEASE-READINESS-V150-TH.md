# ParcelGuard — Customer Delivery Readiness (Option A)
Updated 2026-11-16. Staging only; DO NOT interpret this checklist as release authorization.

## Source of truth
- Landing production: `parcelguard-website`, deployed to https://parcelguard-offline.pages.dev/ . Do not overwrite its approved design.
- Verified LINE OA: https://lin.ee/wEsjx0X; inquiries open, payments closed pending release verification.
- Manual fulfillment: verify actual bank deposit; existing Firebase Admin at https://parcelguard-license.web.app issues license after owner confirmation.
- Mobile product: 490 THB one-time; Pro Android + Windows: 990 THB one-time.
- Android app package: `com.parcelguard.offline`. Source in this branch (based on PC V1.4.0 Pilot).
- Preserve Windows Stable V1.2.9 at `%LOCALAPPDATA%\ParcelGuardPC`. Pilot data at `%LOCALAPPDATA%\ParcelGuardPC-Pilot-V130`; backups `%LOCALAPPDATA%\ParcelGuardPC-Backups`.
- Never merge experimental release changes into production without verifying migration and permissions.

## Existing verified artifacts (internal QA only)
- Android APK V0.8.6 build run: https://github.com/paitoonsamba-hub/parcelguard-offline/actions/runs/43382047753 ; artifact `ParcelGuard-Offline-APK`; CI success, **debug** APK (not an authorized public release).
- Windows V1.4.0 installer run: https://github.com/paitoonsamba-hub/parcelguard-offline/actions/runs/43592472916 ; artifact `ParcelGuard-PC-Setup-V1.4.0-PILOT`; CI success, **unsigned pilot** only. Expires around 2026-11-28 UTC.
- CI tests passing / the existence of artifacts does not prove real-world installation, safety, or licensing usability. The Android artifact and PC EXE are not yet permanent public download links.
- No GitHub Releases currently published as of 2026-11-16.

## Gate A — internal Android validation
- [ ] Confirm APK SHA-256, file size, package, versionCode, versionName and build commit.
- [ ] Set up production app-signing key via secure secret manager, separate from source tree and website; design upgrade path from testing debug install. Never commit keys or use the debug APK as a public release.
- [ ] Create signed Android release APK (or Play delivery format if needed), install on a clean supported handset without ADB.
- [ ] Verify Firebase trial 7-day start, server-side expiry, reinstall cannot reset trial, correct timezone and network offline state.
- [ ] Verify Mobile license activates Android only; Pro license activates intended product; revoked keys fail appropriately; do not expose full keys in logs.
- [ ] Verify camera permissions; SPX, LEX-All, Flash, J&T, Other; tracking edge cases; photo after short video; bag counts; cross-bag handling; PDF/A4; persistence after app restart.
- [ ] Verify real device upgrade backup/restore test. Do not force uninstall an existing installed debug build without backing up local data.

## Gate B — internal Windows validation
- [ ] Download/retain signed-off pilot installer in private versioned storage before GitHub artifact expires (do not publish to customers).
- [ ] Confirm EXE SHA-256, exact V1.4.0 package/version, correct setup signature status and origin.
- [ ] Test install on clean Windows PC; check antivirus/SmartScreen treatment; code-sign production executable and installer when ready.
- [ ] Verify PC Pro license login, bound-device restrictions and no login with Mobile-only keys.
- [ ] Test USB barcode scan, phone-to-PC QR LAN pairing and reconnect, accurate photo/video and bag counts.
- [ ] Test A4 combined-bag printing and PDF export, staff signature path where supported.
- [ ] Test backup, reinstall and restore/upgrade on sacrificial test data; do not migrate/touch Stable V1.2.9 storage.
- [ ] Confirm auto-update uses validated official URLs and cannot silently overwrite wrong version.

## Gate C — delivery and seller process
- [ ] Select stable, versioned official distribution URLs with access control; never rely on expiring GitHub Actions artifacts.
- [ ] Publish checksums and release notes; store production signing secrets privately; document Android sideload instructions and safety warnings.
- [ ] Review user-friendly 7-day trial terms, one-time license scope, seats, update window, supported Android versions and support hours.
- [ ] Prepare privacy policy, evidence retention and responsibility limits.
- [ ] TEST order conversation from website and LINE OA, real-bank-payment verification, existing Firebase admin payment receipt, single-use lifetime license issue, and private delivery instructions.
- [ ] Only after all above pass: explicitly authorize owner to switch sales-open wording and publish verified distribution URLs. No automatic checkout or extra dashboards.

## Current decision
As of 2026-11-16: **NOT CUSTOMER-READY**. The website may accept LINE OA inquiries only. Do not claim instant trial downloads or immediate delivery. CI build success is not equivalent to E2E pass.
