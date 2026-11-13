"""Offline regression tests; never touch real Firebase data."""
import csv
import hashlib
import io
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from unittest.mock import patch

from license_client import ProLicense, LicenseError
from receiver import ReceiverState, ingest_zip, serve, sha

class FakeLicense:
    approved = False
    def approved_now(self):
        return self.approved

def archive(photo=b"JPEG_TEST", tracking="SPX123456789", bag="BAG-01", tamper=False):
    buf=io.BytesIO()
    path="photos/"+tracking+".jpg"
    manifest="tracking,bag,photo_name,scanned_at,video_offset_ms,parcel_video_file,video_status,carrier\n"
    manifest+=f'"{tracking}","{bag}","{path}",123,0,"",0,"SPX"\n'
    checksum=hashlib.sha256(photo).hexdigest()
    if tamper:
        checksum="0"*64
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zipf:
        zipf.writestr(path,photo)
        zipf.writestr("manifest.csv",manifest)
        zipf.writestr("SHA256SUMS.txt",checksum+"  "+path+"\n")
    return buf.getvalue()

class ProVerificationTests(unittest.TestCase):
    @patch("license_client.request_json")
    def test_pro_is_approved_only_when_windows_feature_present(self, call):
        client=ProLicense(uid="test",_id_token="test-jwt",_refresh_token="refresh",
                          _jwt_expires=10**20)
        call.return_value={"result":{"active":True,"lifetime":True,
                                      "plan":"pro","features":["android","windows"]}}
        self.assertTrue(client.verify_pro(force=True))
        call.return_value={"result":{"active":True,"lifetime":True,
                                      "plan":"android","features":["android"]}}
        with self.assertRaises(LicenseError):
            client.verify_pro(force=True)
        self.assertFalse(client._approved)

    @patch("license_client.request_json")
    def test_network_failure_revokes_cached_approval_on_recheck(self, call):
        client=ProLicense(uid="test",_id_token="test",_refresh_token="refresh",
                          _jwt_expires=10**20,_approved=True,_verified_at=1)
        call.side_effect=LicenseError("offline")
        with self.assertRaises(LicenseError):
            client.verify_pro(force=True)
        self.assertFalse(client._approved)

class ReceiverTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        self.license=FakeLicense()
        self.state=ReceiverState(self.root,self.license)
        self.server=serve(self.state,host="127.0.0.1",port=0)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True)
        self.thread.start()
        self.url=f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        self.tmp.cleanup()

    def post(self,path,data,content_type="application/json",token=None):
        headers={"Content-Type":content_type}
        if token:
            headers["Authorization"]="Bearer "+token
        req=urllib.request.Request(self.url+path,data=data,headers=headers,method="POST")
        try:
            with urllib.request.urlopen(req,timeout=4) as res:
                return res.status,json.load(res)
        except urllib.error.HTTPError as exc:
            return exc.code,json.load(exc)

    def test_mobile_only_cannot_pair_even_with_valid_qr(self):
        code=self.state.new_pairing_code()
        status,_=self.post("/api/pair/claim",json.dumps({"code":code}).encode())
        self.assertEqual(status,403)

    def test_one_time_qr_pairing_and_zip_import(self):
        self.license.approved=True
        code=self.state.new_pairing_code()
        status,reply=self.post("/api/pair/claim",json.dumps({"code":code,"name":"phone"}).encode())
        self.assertEqual(status,200)
        self.assertGreaterEqual(len(reply["token"]),24)
        status,_=self.post("/api/pair/claim",json.dumps({"code":code,"name":"phone"}).encode())
        self.assertEqual(status,403)
        data=archive()
        status,reply2=self.post("/api/sync/archive",data,"application/zip",reply["token"])
        self.assertEqual((status,reply2["status"]),(200,"imported"))
        status,reply3=self.post("/api/sync/archive",data,"application/zip",reply["token"])
        self.assertEqual((status,reply3["status"]),(200,"already_imported"))
        self.assertEqual(len(list((self.root/"parcels").iterdir())),1)

    def test_upload_without_paired_token_rejected(self):
        self.license.approved=True
        status,_=self.post("/api/sync/archive",archive(),"application/zip","not-a-token")
        self.assertEqual(status,401)

    def test_checksum_tampering_rejected_without_storage(self):
        self.license.approved=True
        self.state.paired[sha(b"test-token-that-is-long-enough")]={"id":"abc"}
        status,_=self.post("/api/sync/archive",archive(tamper=True),"application/zip",
                           "test-token-that-is-long-enough")
        self.assertEqual(status,400)
        self.assertFalse((self.root/"parcels").exists() and any((self.root/"parcels").iterdir()))

if __name__=="__main__":
    unittest.main()
