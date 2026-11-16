# ParcelGuard PC — LINE OA Support (QA Pilot)

Status: **CI passed / not installed or exercised on a real Windows user device** (2026-11-16).

## New UI
- Dashboard: floating green button **ติดต่อพนักงาน** at bottom-right on every page; fixed positioning.
- Settings panel: **ฝ่ายช่วยเหลือ ParcelGuard** card with LINE OA button and privacy reminder.
- Pro login page: LINE OA support displayed without requiring authentication.
- Contact goes to verified https://lin.ee/wEsjx0X , opens a new tab with noopener/noreferrer.
- No login credentials, License Keys, or parcel data are passed in the URL.

## Source and build
- Retains the **original verified** `pc-installer-source-v140.zip` from V1.4.0 Pilot.
- `installer-ci/add_line_support.py` patches only extracted `pc-installer/web/index.html` and `login.html`, then asserts exactly one support link in each required place.
- Does not change server.py, Firebase, databases, versions, app registration, update logic, or backup logic.
- QA GitHub Actions run https://github.com/paitoonsamba-hub/parcelguard-offline/actions/runs/43812027831 succeeded, including original regression suite and Inno Setup compilation.
- Artifact `ParcelGuard-PC-Setup-V1.4.0-SUPPORT-QA` contains unsigned Pilot installer. GitHub artifact expiration: 2026-11-30 (UTC).

## Restrictions
- This installer still bears **V1.4.0** internally and may target the **same installation identity as existing Pilot**. It is *not* a separately isolated product installation. **Do not run it on the primary PC or overwrite V1.2.9 Stable / V1.4.0 Pilot** until a data-safe upgrade path has been tested. Use clean Windows VM / expendable test machine first.
- Do not market as customer release or create GitHub Releases yet. Verify signed official installer, clean installation, support URL with/without License, Firebase Pro login, scan, QR LAN sync, printing, backup/restore, version/app identity, upgrade from previous versions, SmartScreen.
- Stable user data `%LOCALAPPDATA%\\ParcelGuardPC`; Pilot data `%LOCALAPPDATA%\\ParcelGuardPC-Pilot-V130`; backup folder `%LOCALAPPDATA%\\ParcelGuardPC-Backups`.
