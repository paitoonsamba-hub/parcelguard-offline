"""Firebase Auth + Pro entitlement for Windows receiver.

Tokens exist only in memory; credentials are never persisted to disk.
Firebase config apiKey is a public client identifier, NOT a service credential.
"""
from __future__ import annotations
import json
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

API_KEY = "AIzaSyA_6ZpRrMV1iH9dtAxB4pEvuhqkB2dysO8"
AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
REFRESH_URL = "https://securetoken.googleapis.com/v1/token"
CHECK_URL = "https://asia-southeast1-parcelguard-license.cloudfunctions.net/checkAccess"
HTTP_TIMEOUT = 12
CHECK_INTERVAL_SECONDS = 300


class LicenseError(Exception):
    pass


def request_json(url: str, payload: dict, headers: dict | None = None, form: bool = False) -> dict:
    data = (urllib.parse.urlencode(payload).encode("ascii") if form
            else json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    h = {"Content-Type": "application/x-www-form-urlencoded" if form else "application/json"}
    if headers:
        h.update(headers)
    request = urllib.request.Request(url, data=data, headers=h, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT, context=ssl.create_default_context()) as response:
            result = json.load(response)
    except urllib.error.HTTPError as exc:
        # Do not log credentials, request payloads, or the JWT.
        try:
            failure = json.loads(exc.read(8192).decode("utf-8"))
            detail = failure.get("error", {})
            message = detail.get("message", "HTTP " + str(exc.code)) if isinstance(detail, dict) else str(detail)
        except (ValueError, UnicodeError):
            message = "HTTP " + str(exc.code)
        raise LicenseError(str(message)[:180]) from None
    except (OSError, TimeoutError) as exc:
        raise LicenseError("Cannot contact Firebase; internet is required to verify Pro license") from exc
    if not isinstance(result, dict):
        raise LicenseError("Unexpected response from Firebase")
    return result


@dataclass
class ProLicense:
    uid: str = ""
    email: str = ""
    _id_token: str = ""
    _refresh_token: str = ""
    _jwt_expires: float = 0.0
    _verified_at: float = 0.0
    _approved: bool = False

    def sign_in(self, email: str, password: str) -> None:
        self.sign_out()
        result = request_json(AUTH_URL + "?key=" + API_KEY, {
            "email": email.strip(),
            "password": password,
            "returnSecureToken": True
        })
        self.uid = str(result.get("localId", ""))
        self.email = email.strip()
        self._id_token = str(result.get("idToken", ""))
        self._refresh_token = str(result.get("refreshToken", ""))
        self._jwt_expires = time.monotonic() + max(60, int(result.get("expiresIn", 3600))) - 90
        if not self.uid or not self._id_token or not self._refresh_token:
            self.sign_out()
            raise LicenseError("Firebase sign-in returned incomplete credentials")
        try:
            self.verify_pro(force=True)
        except Exception:
            self.sign_out()
            raise

    def sign_out(self) -> None:
        self.uid = self.email = self._id_token = self._refresh_token = ""
        self._jwt_expires = self._verified_at = 0.0
        self._approved = False

    def _refresh(self) -> None:
        if not self._refresh_token:
            raise LicenseError("Sign in with a licensed Pro account")
        result = request_json(REFRESH_URL + "?key=" + API_KEY, {
            "grant_type": "refresh_token", "refresh_token": self._refresh_token
        }, form=True)
        self._id_token = str(result.get("id_token", ""))
        self._refresh_token = str(result.get("refresh_token", self._refresh_token))
        self._jwt_expires = time.monotonic() + max(60, int(result.get("expires_in", 3600))) - 90
        if not self._id_token:
            raise LicenseError("Cannot renew Firebase session")

    def verify_pro(self, force: bool = False) -> bool:
        if not self.uid:
            raise LicenseError("Please sign in before opening the receiver")
        now = time.monotonic()
        if not force and self._approved and 0 < now - self._verified_at < CHECK_INTERVAL_SECONDS:
            return True
        self._approved = False  # fail closed if Firebase is unreachable
        if now >= self._jwt_expires:
            self._refresh()
        response = request_json(CHECK_URL, {"data": {}}, {"Authorization": "Bearer " + self._id_token})
        data = response.get("result")
        if not isinstance(data, dict):
            raise LicenseError("Unexpected license check response")
        if (data.get("active") is not True or data.get("lifetime") is not True or
                data.get("plan") != "pro" or "windows" not in data.get("features", [])):
            raise LicenseError("This Firebase account has no ParcelGuard Pro lifetime license (฿990)")
        self._verified_at = time.monotonic()
        self._approved = True
        return True

    def call_function(self, name: str, payload: dict) -> dict:
        """Authenticated Firebase callable; no Admin SDK credentials on customer PC."""
        if not self.uid:
            raise LicenseError("Sign in first.")
        if time.monotonic() >= self._jwt_expires:
            self._refresh()
        url = "https://asia-southeast1-parcelguard-license.cloudfunctions.net/" + name
        response = request_json(url, {"data": payload},
                                {"Authorization": "Bearer " + self._id_token})
        result = response.get("result")
        if not isinstance(result, dict):
            raise LicenseError("Unexpected Firebase function response")
        return result

    def approved_now(self) -> bool:
        try:
            return self.verify_pro()
        except LicenseError:
            return False
