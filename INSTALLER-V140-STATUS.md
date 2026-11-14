# ParcelGuard PC V1.4.0 — Installer development branch

## Status
- This is a **separate pilot branch**, based on `feature/commercial-security-v1.3.0`.
- The build workflow is checked in at `.github/workflows/build-installer.yml`.
- **Do not ship the build as-is**. The exact PC Station V1.4.0 source is in a previously generated ZIP, not yet in the GitHub repository.
- CI deliberately fails if `pc-installer/server.py` and the original dashboard/backup/installer files are absent. It will **never silently package** the old `windows-receiver` prototype in place of the full PC Station application.

## Preservation
- Do not merge into the stable branch until end-to-end validation.
- Do not modify Firebase functions, entitlements, or `commercialPcSeats`.
- The source installer is intended to install into `%LOCALAPPDATA%\\Programs\\ParcelGuard PC Commercial`.
- Pilot user data stays in `%LOCALAPPDATA%\\ParcelGuardPC-Pilot-V130`; stable data in `%LOCALAPPDATA%\\ParcelGuardPC` must be left untouched.
- Installer needs to stop when ports 8765/8766 are busy and create a verified backup of pilot data before changes.

## Source required
Extract the previously prepared file `ParcelGuard_PC_V1_4_0_Installer_Build_Package_PILOT.zip` into the repository root on this branch, preserving:
- `pc-installer/server.py`, `pc-installer/run_pc.py`, `pc-installer/web/**`
- `pc-installer/requirements.txt`
- `pc-installer/installer/ParcelGuard.iss`
- `pc-installer/installer/preinstall-backup.ps1`
- `pc-installer/tests/**`

Do not upload sample parcel evidence containing customer data or live credentials. When source is available, the workflow will run the Python tests, build a standalone EXE (bundling Python), package with Inno Setup on a Windows runner, and publish an **unsigned pilot** installer as a GitHub Actions artifact.

## Release gates
1. Verify installer on Windows 10/11 without Python installed.
2. Ensure install/update/uninstall never modifies the stable data folder.
3. Test SQLite + WAL backup and restore with realistic image/video volume.
4. Confirm license/account check, USB barcode scanner, QR sync and A4 print.
5. Sign the installer and scan it before distributing to paying customers.
