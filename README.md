# ParcelGuard Landing Page — Dark Tech (Pre-launch)

Single-page responsive Thai-language landing page for ParcelGuard. **No purchase flow is live.** Everything in this branch is a static site and does not change the Android application's main branch or database.

## Where the files live

This site currently lives in the connected GitHub repository **paitoonsamba-hub/parcelguard-offline**, dedicated branch **parcelguard-website**, because repository-creation permission is not exposed in the current connection. The branch contains only website files; Android development continues on **main**. Moving to a standalone **parcelguard-website** repository later is straightforward.

- \`public/index.html\` — main page (Dark Tech / Thai)
- \`public/styles.css\` — responsive design and animations
- \`public/app.js\` — mobile nav, trial status modal, demo player config
- \`public/favicon.svg\` — original ParcelGuard box mark
- \`public/_headers\` — production security headers
- \`public/robots.txt\` — intentionally blocks indexing until launch
- \`tests/smoke.mjs\` — no-dependency static checks

## Deploy on Cloudflare Pages (not yet deployed)

1. Sign into https://dash.cloudflare.com
2. Workers & Pages → Create application → Pages → **Connect to Git**
3. Authorize GitHub and select \`paitoonsamba-hub/parcelguard-offline\`.
4. Select production branch: **\`parcelguard-website\`** (do not select Android's \`main\` branch).
5. Framework preset: **None**
6. Root directory (advanced): **leave blank** (repository root)
7. Build command: **\`exit 0\`**
8. Build output directory: **\`public\`**
9. Deploy; Cloudflare supplies a \`*.pages.dev\` URL.
10. Open the site on desktop and phone to verify.

If the Cloudflare build form prompts to install dependencies, there are none; no package.json or npm build is necessary. The website contains only static files.

Official docs: https://developers.cloudflare.com/pages/framework-guides/deploy-anything/

## Hero v2 feature map

The hero is deliberately built from editable HTML/CSS (`public/hero-v2.css`):
a center phone mockup + six solution cards covering carrier selection,
per-bag tracking, video/photo evidence, optional scale visibility, case search,
and printable A4/PDF manifests for physical handover. Small screens place
cards *below* the phone in a responsive grid. All demo codes and statistics
are illustrative, not real customer records.

**Claim safeguards:** The video may help inspect weight only when the
physical scale is visible and readable; the scale OCR is an experiment.
Printing an A4 list can support a handwritten signature on paper but does
not itself prove carrier acceptance or hub scanning.

## Enable real demo later

1. Record a 30–60 second demo and blur all customer names, addresses, phone numbers and real order details.
2. Upload an H.264 MP4 to \`public/assets/parcelguard-demo.mp4\` (create assets folder).
3. Change \`DEMO_VIDEO_URL = ""\` to \`DEMO_VIDEO_URL = "./assets/parcelguard-demo.mp4"\` in \`public/app.js\`.
4. The placeholder swaps to an HTML video player with native controls. Avoid external iframe trackers.

## Important commercial launch checklist

- Complete and test trial-7-day backend, account recovery and server-signed offline-capable licenses.
- Complete independent signed Android release + Windows installer, backup/recovery and support process.
- Add real privacy notice, terms and business/contact information before taking signups/payments.
- Add payment provider server-side integration and verified webhook/slip reconciliation.
- Make buyer counts, testimonials and shipping claims only with real evidence and consent.
- Review prices/plan rights, licensing device counts and update/support terms.
- **Only after the site is ready for public search**, remove the \`noindex\` meta tag from \`public/index.html\` and change \`public/robots.txt\` to permit indexing; add canonical URL, social share image and sitemap on the real domain.
- Add Meta Pixel/conversion tracking only with an appropriate privacy/consent approach.

## Local check

\`\`\`bash
node tests/smoke.mjs
python3 -m http.server 8000 --directory public
\`\`\`

Browse http://localhost:8000. The form-free trial CTA opens an honest pre-launch modal. It does not collect emails, generate real trial licenses, send APKs, or collect payment.

## Notes

The app/dashboard graphics and manifest shown on this site are synthetic UI illustrations, clearly labeled as such, not product screenshots or shipping acceptance proof. All external brand mentions are descriptive only. Every feature and pricing promise should be retested before public launch.

© ParcelGuard. Pre-launch website draft.
