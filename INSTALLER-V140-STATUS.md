# ParcelGuard PC V1.4.0 Installer — pilot handoff

## Current state (2026-11-14)
- Original PC Station V1.4.0 archive inspected locally: 45 entries, 154,777 bytes, app version 1.4.0.
- Local regression tests passed 26/26. These tests DO NOT replace a real Windows installation test.
- Branch `feature/windows-installer-v1.4.0` is isolated from V1.2.9 Stable and V1.3.0 Commercial Pilot.
- Workflow `.github/workflows/build-installer.yml` is configured to accept a **single ZIP** at repo root named `pc-installer-source-v140.zip` and extract it during Windows build.
- Source archive has not yet been uploaded to the repository; current Actions failures are expected source-missing stops. There is NO distributable EXE yet.

## One-file upload to unblock
1. Download the original, verified PC Station source archive (not screenshots or a replacement receiver).
2. On this branch, use Add file > Upload files and commit **one ZIP** named `pc-installer-source-v140.zip` at repository root.
3. The Actions workflow starts automatically, runs source/version assertions and 26 tests, builds EXE with bundled Python via PyInstaller, creates Inno Setup and uploads an installer artifact.

## Data preservation
- Do not edit any V1.2.9 Stable directories or databases.
- Pilot user data: `%LOCALAPPDATA%\\ParcelGuardPC-Pilot-V130`
- Stable user data: `%LOCALAPPDATA%\\ParcelGuardPC`
- Before install/update, installer invokes PowerShell preflight to check ports 8765/8766 and back up Pilot data to `%LOCALAPPDATA%\\ParcelGuardPC-Backups`. Backup must be checked on Windows.
- Do not merge / release to customers without confirming Firebase Pro login, USB scan, QR sync, A4 print, installer signature, and update without data loss.
