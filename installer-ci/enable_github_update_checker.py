"""Switch the V1.4 Pilot update checker from an unpublished Hosting URL to a
public GitHub release manifest. Changes only the extracted CI working copy,
not the uploaded source archive or V1.2.9 / V1.3.0 deployments.
"""
import json
from pathlib import Path

server = Path("pc-installer/server.py")
original = server.read_text(encoding="utf-8")
before_url = "UPDATE_MANIFEST_URL = 'https://parcelguard-license.web.app/pc-version.json'"
after_url = "UPDATE_MANIFEST_URL = 'https://raw.githubusercontent.com/paitoonsamba-hub/parcelguard-offline/feature/windows-installer-v1.4.0/updates/pc/pilot.json'"
if original.count(before_url) != 1:
    raise RuntimeError("Unexpected update URL source: abort without changing the program")

before_check = """                        latest=str(manifest.get('version',''))
                        if not re.fullmatch(r'\\d+\\.\\d+\\.\\d+',latest):
                            raise ValueError('ข้อมูลเวอร์ชันไม่ถูกต้อง')
"""
after_check = """                        latest=str(manifest.get('version',''))
                        # Only the explicit pilot channel can update the pilot UI.
                        # Neither download links nor executable code are accepted.
                        if not isinstance(manifest,dict) or manifest.get('channel')!='pilot' or manifest.get('automaticInstall') is not False:
                            raise ValueError('ไฟล์ประกาศเวอร์ชันไม่ใช่ช่อง Pilot แบบตรวจอย่างเดียว')
                        if not re.fullmatch(r'\\d+\\.\\d+\\.\\d+',latest):
                            raise ValueError('ข้อมูลเวอร์ชันไม่ถูกต้อง')
"""
if original.count(before_check) != 1:
    raise RuntimeError("Unexpected manifest validation code: abort safely")
updated = original.replace(before_url,after_url,1).replace(before_check,after_check,1)
server.write_text(updated,encoding="utf-8")

test = Path("pc-installer/tests/test_installer_backup.py")
t = test.read_text(encoding="utf-8")
old = "self.assertTrue(UPDATE_MANIFEST_URL.startswith('https://parcelguard-license.web.app/'))"
new = "self.assertEqual(UPDATE_MANIFEST_URL, 'https://raw.githubusercontent.com/paitoonsamba-hub/parcelguard-offline/feature/windows-installer-v1.4.0/updates/pc/pilot.json')"
if t.count(old)!=1:
    raise RuntimeError("Unexpected update manifest assertion")
test.write_text(t.replace(old,new,1),encoding="utf-8")

manifest = json.loads(Path("updates/pc/pilot.json").read_text(encoding="utf-8"))
assert manifest["version"] == "1.4.0"
assert manifest["channel"] == "pilot"
assert manifest["automaticInstall"] is False
print("GitHub Pilot Update Checker enabled: manual/read-only; no automatic install")
