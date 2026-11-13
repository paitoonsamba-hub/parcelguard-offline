"""ParcelGuard PC LAN receiver compatible with Android HybridSync / PairQrActivity.

Safe ZIP ingestion with path, file-type, size and checksum validation.
Access is gated on the current Windows owner's Firebase Pro license.
"""
from __future__ import annotations
import csv
import hashlib
import hmac
import io
import json
import os
import secrets
import shutil
import socket
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_BODY = 230 * 1024 * 1024
MAX_MEMBER = 120 * 1024 * 1024
PORT = 8766
_ALLOWED = {"manifest.csv", "SHA256SUMS.txt"}
PAIR_TTL = 90


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_id(value: str) -> str:
    if not value or len(value) > 160 or any(ord(c) < 32 for c in value):
        raise ValueError("Invalid parcel tracking or bag")
    return sha(value.encode("utf-8"))[:24]


def private_lan_ip() -> str:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(("192.0.2.1", 9))  # no packet is transmitted
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"


class ReceiverState:
    def __init__(self, folder: Path, license_client) -> None:
        self.folder = Path(folder)
        self.folder.mkdir(parents=True, exist_ok=True)
        self.license_client = license_client
        self.lock = threading.RLock()
        self.claim_code = ""
        self.claim_expiry = 0.0
        self.paired_file = self.folder / "paired-devices.json"
        self.paired = self._load_pairs()
        self.on_event = None
        self.events = []

    def emit(self, kind: str, message: str):
        item = {"kind": kind, "message": message, "time": int(time.time())}
        with self.lock:
            self.events = ([item] + self.events)[:80]
        if self.on_event:
            try:
                self.on_event(item)
            except Exception:
                pass

    def count_parcels(self):
        root = self.folder / "parcels"
        return sum(1 for p in root.glob("*/manifest.csv") if p.is_file()) if root.exists() else 0

    def _load_pairs(self) -> dict:
        try:
            v = json.loads(self.paired_file.read_text("utf-8"))
            if isinstance(v, dict):
                return {k: value for k, value in v.items()
                        if isinstance(k, str) and len(k) == 64 and isinstance(value, dict)}
        except (OSError, ValueError):
            pass
        return {}

    def _save_pairs(self) -> None:
        # Written to the current user's app data, never in the project repository.
        temp = self.paired_file.with_suffix(".tmp")
        temp.write_text(json.dumps(self.paired, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(temp, self.paired_file)

    def new_pairing_code(self) -> str:
        with self.lock:
            self.claim_code = secrets.token_urlsafe(32)
            self.claim_expiry = time.monotonic() + PAIR_TTL
            return self.claim_code

    def claim(self, code: str, name: str) -> dict | None:
        with self.lock:
            if not self.claim_code or time.monotonic() >= self.claim_expiry:
                return None
            if not hmac.compare_digest(self.claim_code, code):
                return None
            self.claim_code = ""
            self.claim_expiry = 0.0
            token = secrets.token_urlsafe(32)
            ident = secrets.token_hex(12)
            self.paired[sha(token.encode())] = {
                "id": ident, "name": name[:80], "createdAt": int(time.time())
            }
            self._save_pairs()
            self.emit("paired", "Android จับคู่สำเร็จ แต่ยังไม่ได้รับไฟล์พัสดุ")
            return {"id": ident, "token": token}

    def valid_token(self, token: str) -> bool:
        if len(token) < 24 or len(token) > 200:
            return False
        digest = sha(token.encode())
        with self.lock:
            return digest in self.paired


def ingest_zip(content: Path, destination: Path) -> str:
    """Validate the entire zip before modifying the evidence directory."""
    with zipfile.ZipFile(content, "r") as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) not in (3, 4) or len(names) != len(set(names)):
            raise ValueError("Archive must contain manifest, checksums, photo and optional video")
        for entry in entries:
            name = entry.filename
            if (entry.is_dir() or name.startswith("/") or "\\" in name or
                ".." in name.split("/") or entry.file_size > MAX_MEMBER or
                entry.compress_size > MAX_BODY):
                raise ValueError("Unsafe archive entry")
            if name in _ALLOWED:
                continue
            if not (name.startswith("photos/") and name.endswith(".jpg") or
                    name.startswith("videos/parcels/") and name.endswith(".mp4")):
                raise ValueError("Unexpected archive member")
        photo_names = [n for n in names if n.startswith("photos/")]
        video_names = [n for n in names if n.startswith("videos/parcels/")]
        if (len(photo_names) != 1 or len(video_names) > 1 or
                "manifest.csv" not in names or "SHA256SUMS.txt" not in names):
            raise ValueError("Missing or duplicate evidence files")
        raw_manifest = archive.read("manifest.csv")
        if len(raw_manifest) > 32768:
            raise ValueError("Manifest too large")
        rows = list(csv.DictReader(io.StringIO(raw_manifest.decode("utf-8-sig"))))
        if len(rows) != 1:
            raise ValueError("Archive must contain one parcel row")
        row = rows[0]
        tracking, bag = row.get("tracking", ""), row.get("bag", "")
        if not tracking or not bag or row.get("photo_name") != photo_names[0]:
            raise ValueError("Invalid tracking/bag or photo name")
        if (row.get("parcel_video_file", "") or "") != (video_names[0] if video_names else ""):
            raise ValueError("Video manifest mismatch")
        checksums = archive.read("SHA256SUMS.txt")
        if len(checksums) > 4096:
            raise ValueError("Checksum list too large")
        expected = {}
        for line in checksums.decode("utf-8").splitlines():
            if "  " not in line:
                raise ValueError("Invalid checksum line")
            digest, filename = line.split("  ", 1)
            if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                raise ValueError("Bad checksum digest")
            expected[filename] = digest
        media = photo_names + video_names
        if set(expected) != set(media):
            raise ValueError("Checksums do not match media list")

        archive_name = safe_id(tracking + "|" + bag)
        target = destination / "parcels" / archive_name
        if target.exists():
            # Idempotent on tracking+bag; do not silently overwrite prior evidence.
            return "already_imported"
        target.parent.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix=".pg-staging-", dir=str(target.parent)))
        try:
            for name in media:
                output = staging / name
                output.parent.mkdir(parents=True, exist_ok=True)
                digest = hashlib.sha256()
                total = 0
                with archive.open(name) as source, output.open("wb") as out:
                    while True:
                        chunk = source.read(65536)
                        if not chunk:
                            break
                        total += len(chunk)
                        if total > MAX_MEMBER:
                            raise ValueError("Evidence member too large")
                        digest.update(chunk)
                        out.write(chunk)
                if not hmac.compare_digest(expected[name], digest.hexdigest()):
                    raise ValueError("Evidence SHA-256 mismatch")
            (staging / "manifest.csv").write_bytes(raw_manifest)
            (staging / "SHA256SUMS.txt").write_bytes(checksums)
            os.rename(staging, target)
        except BaseException:
            shutil.rmtree(staging, ignore_errors=True)
            raise
        return "imported"


def create_handler(state: ReceiverState):
    class Handler(BaseHTTPRequestHandler):
        server_version = "ParcelGuardPC/0.8"

        def log_message(self, format, *args):
            # Do not log pair codes, bearer tokens, or raw evidence.
            return

        def respond(self, code: int, result: dict):
            body = json.dumps(result, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            if self.path not in ("/api/pair/claim", "/api/sync/archive"):
                return self.respond(404, {"error": "not_found"})
            # Windows receiver must never operate without server-verified Pro access.
            if not state.license_client.approved_now():
                state.emit("error", "Pro License ไม่ผ่าน (HTTP 403)")
                return self.respond(403, {"error": "pro_license_required"})
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                return self.respond(400, {"error": "invalid_content_length"})
            limit = 4096 if self.path == "/api/pair/claim" else MAX_BODY
            if size <= 0 or size > limit:
                return self.respond(413, {"error": "invalid_upload_size"})
            if self.path == "/api/pair/claim":
                if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                    return self.respond(415, {"error": "invalid_type"})
                try:
                    request = json.loads(self.rfile.read(size))
                    code = request.get("code", "")
                    name = request.get("name", "Android device")
                    if not isinstance(code, str) or not isinstance(name, str):
                        raise ValueError("Bad QR code")
                    result = state.claim(code, name)
                except (ValueError, UnicodeError):
                    return self.respond(400, {"error": "invalid_request"})
                return self.respond(200, result) if result else self.respond(403, {"error": "expired_or_invalid_qr"})
            auth = self.headers.get("Authorization", "")
            if not auth.startswith("Bearer ") or not state.valid_token(auth[7:]):
                state.emit("error", "Token ไม่ตรงหรือไม่ได้จับคู่ (HTTP 401)")
                return self.respond(401, {"error": "unpaired_device"})
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/zip":
                return self.respond(415, {"error": "expected_zip"})
            tmp = None
            try:
                with tempfile.NamedTemporaryFile(prefix="pg-upload-", suffix=".zip",
                                                 dir=state.folder, delete=False) as out:
                    tmp = Path(out.name)
                    remaining = size
                    while remaining:
                        part = self.rfile.read(min(65536, remaining))
                        if not part:
                            raise ValueError("Incomplete upload")
                        out.write(part)
                        remaining -= len(part)
                with state.lock:
                    result = ingest_zip(tmp, state.folder)
                state.emit("sync", "ได้รับ ZIP หลักฐานใหม่" if result == "imported" else "ไฟล์พัสดุมีอยู่ใน PC แล้ว")
                return self.respond(200, {"status": result})
            except (ValueError, zipfile.BadZipFile, UnicodeError, KeyError):
                state.emit("error", "ZIP ไม่ถูกต้อง (HTTP 400)")
                return self.respond(400, {"error": "invalid_archive"})
            except OSError:
                state.emit("error", "บันทึก ZIP ลงดิสก์ไม่สำเร็จ (HTTP 500)")
                return self.respond(500, {"error": "storage_error"})
            finally:
                if tmp:
                    tmp.unlink(missing_ok=True)
    return Handler


def serve(state: ReceiverState, host="0.0.0.0", port=PORT) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer((host, port), create_handler(state))
    server.daemon_threads = True
    return server
