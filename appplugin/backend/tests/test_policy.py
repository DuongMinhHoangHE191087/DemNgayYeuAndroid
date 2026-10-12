"""2.1.0 BE2 — `GET /policy`: khoá trước, verdict Integrity (giả) quyết định artifact, fail-closed.

Không bao giờ import `google.auth` (PO9 đếm bằng subprocess).
"""

from __future__ import annotations

import json
import logging
import subprocess
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

KEY_A, KEY_B = "key-a-0123456789", "key-b-0123456789"
POLICY_A = {"version": 3, "gap_s": 45}
BACKEND = Path(__file__).resolve().parents[1]


def _client(tmp_path, mode="fake", verifier=None, policies=None):
    from app.config import Settings
    from app.main import create_app

    pdir = tmp_path / "policies"
    pdir.mkdir(exist_ok=True)
    for name, body in (policies if policies is not None else {"appa": POLICY_A}).items():
        (pdir / f"{name}.json").write_text(json.dumps(body), encoding="utf-8")
    settings = Settings(api_keys=f"appa:{KEY_A},appb:{KEY_B}", sink="jsonl",
                        jsonl_path=str(tmp_path / "e.jsonl"), integrity_mode=mode,
                        policy_dir=str(pdir))
    application = create_app(settings)
    if verifier is not None:
        application.state.integrity = verifier
    return TestClient(application)


def _get(client, key=KEY_A, token=None, **kw):
    headers = {}
    if key is not None:
        headers["X-Api-Key"] = key
    if token is not None:
        headers["X-Integrity-Token"] = token
    return client.get("/policy", headers=headers, **kw)


class _Spy:
    def __init__(self, verdict=None, exc=None):
        self.calls, self._verdict, self._exc = [], verdict, exc

    def verify(self, token, app_id):
        self.calls.append((token, app_id))
        if self._exc:
            raise self._exc
        return self._verdict


def test_po1_policy_without_key_is_401_before_reading_other_headers(tmp_path):
    spy = _Spy()
    client = _client(tmp_path, verifier=spy)
    for key in (None, "wrong-key"):
        resp = _get(client, key=key, token="fake-ok")
        assert resp.status_code == 401
    assert spy.calls == []


def test_po2_registered_app_with_accepting_fake_gets_its_policy(tmp_path):
    resp = _get(_client(tmp_path), token="fake-ok")
    assert resp.status_code == 200
    body = resp.json()
    assert (body["source"], body["app_id"], body["policy"]) == ("app", "appa", POLICY_A)


def test_po3_rejecting_fake_returns_default_with_reason(tmp_path):
    client = _client(tmp_path)
    body = _get(client, token="fake-bad-device").json()
    assert body["source"] == "default" and body["reason"] == "not_accepted"
    assert body["policy"] == {"version": 0}
    assert _get(client, token="whatever").json()["reason"] == "not_accepted"


def test_po4_missing_token_returns_default_without_calling_verifier(tmp_path):
    spy = _Spy()
    body = _get(_client(tmp_path, verifier=spy)).json()
    assert body["source"] == "default" and body["reason"] == "no_token"
    assert spy.calls == []


def test_po5_verifier_error_is_fail_closed(tmp_path):
    client = _client(tmp_path)
    body = _get(client, token="fake-raise").json()
    assert body["source"] == "default" and body["reason"] == "not_accepted"
    assert body["policy"] == {"version": 0}
    # lỗi lạ khác cũng đóng
    client2 = _client(tmp_path, verifier=_Spy(exc=ValueError("boom")))
    assert _get(client2, token="fake-ok").json()["source"] == "default"


def test_po6_app_id_comes_from_key_not_query(tmp_path):
    client = _client(tmp_path, policies={"appa": POLICY_A, "appb": {"version": 9}})
    body = _get(client, key=KEY_B, token="fake-ok", params={"app_id": "appa", "app": "appa"}).json()
    assert body["app_id"] == "appb" and body["policy"] == {"version": 9}
    other = _get(client, key=KEY_A, token="fake-ok", params={"app_id": "appb"}).json()
    assert other["policy"] == POLICY_A


def test_po7_mode_off_never_calls_verifier(tmp_path):
    client = _client(tmp_path, mode="off")
    assert client.app.state.integrity is None
    body = _get(client, token="fake-ok").json()
    assert body["source"] == "default" and body["reason"] == "integrity_off"
    spy = _Spy()
    from app.integrity import build_integrity_verifier

    assert build_integrity_verifier(client.app.state.settings) is None and spy.calls == []


def test_po8_logs_never_contain_the_token(tmp_path, caplog):
    secret = "tok-SECRET-9f8e7d6c"
    client = _client(tmp_path, verifier=_Spy(exc=RuntimeError(f"bad {secret}")))
    with caplog.at_level(logging.DEBUG):
        _get(client, token=secret)
        _get(_client(tmp_path), token=secret)
    assert caplog.records
    assert secret not in caplog.text
    assert all(secret not in str(r.args) + r.getMessage() for r in caplog.records)


def test_po9_google_auth_not_imported_in_fake_flow():
    code = (
        "import sys, tempfile\n"
        "from fastapi.testclient import TestClient\n"
        "from app.config import Settings\nfrom app.main import create_app\n"
        "t = tempfile.mkdtemp()\n"
        "c = TestClient(create_app(Settings(api_keys='a:k0123456789abcdef', sink='jsonl',"
        " jsonl_path=t + '/e.jsonl', integrity_mode='fake')))\n"
        "r = c.get('/policy', headers={'X-Api-Key': 'k0123456789abcdef', 'X-Integrity-Token': 'fake-ok'})\n"
        "assert r.status_code == 200, r.text\n"
        "print('GOOGLE_AUTH', 'google.auth' in sys.modules)\n")
    out = subprocess.run([sys.executable, "-c", code], cwd=BACKEND, capture_output=True, text=True,
                         check=True).stdout
    assert "GOOGLE_AUTH False" in out


def test_po10_unknown_mode_does_not_start(tmp_path, monkeypatch):
    from app.config import Settings
    from app.main import create_app

    with pytest.raises(ValueError, match="BRAIN_INTEGRITY_MODE"):
        create_app(Settings(integrity_mode="bogus", jsonl_path=str(tmp_path / "e.jsonl")))
    monkeypatch.setenv("BRAIN_INTEGRITY_MODE", "bogus")
    with pytest.raises(ValueError, match="BRAIN_INTEGRITY_MODE"):
        Settings.from_env()


def test_po11_non_verdict_return_is_fail_closed(tmp_path):
    for bad in (None, "ok", object()):
        resp = _get(_client(tmp_path, verifier=_Spy(verdict=bad)), token="fake-ok")
        assert resp.status_code == 200
        body = resp.json()
        assert (body["source"], body["reason"], body["policy"]) == ("default", "not_accepted", {"version": 0})


def test_po12_google_mode_without_package_does_not_start(tmp_path):
    from app.config import Settings
    from app.main import create_app

    with pytest.raises(ValueError, match="BRAIN_PLAY_PACKAGE"):
        create_app(Settings(integrity_mode="google", jsonl_path=str(tmp_path / "e.jsonl")))


def test_po13_unreadable_or_invalid_policy_file_gives_default(tmp_path):
    client = _client(tmp_path, policies={})
    pfile = tmp_path / "policies" / "appa.json"
    pfile.mkdir()  # là thư mục
    for case in ("dir", "invalid", "deep", "list"):
        if case != "dir":
            pfile.rmdir() if pfile.is_dir() else None
            pfile.write_text({"invalid": "{not json", "deep": "[" * 100000, "list": "[1]"}[case])
        body = _get(client, token="fake-ok")
        assert body.status_code == 200
        assert (body.json()["source"], body.json()["reason"]) == ("default", "no_policy")


# ---------------------------------------------------------------------------------------------
# 2.2.0 SECB: google mode với verifier giả lập đầy đủ. Payload theo googleapis@144.0.0
# `playintegrity/v1.d.ts` ([M-offline]); phiên HTTP giả trả `{"tokenPayloadExternal": ...}` đúng dạng
# thật nên toàn bộ mã `GoogleIntegrityVerifier` chạy, chỉ thiếu mạng và `google.auth`.
# ---------------------------------------------------------------------------------------------
FIXTURES = BACKEND / "tests" / "fixtures" / "integrity"
CERT_A_HEX = "43B114D6E3A5C0F19A27B8D40E5F6C71829AB3C4D5E6F708192A3B4C5D6E7F80"
CERT_A = "Q7EU1uOlwPGaJ7jUDl9scYKas8TV5vcIGSo7TF1uf4A"  # base64url của CERT_A_HEX (tính độc lập)
CERT_B_HEX = "7A1C2E3F405162738495A6B7C8D9E0F10213243546576879A8B9CADBECFD0E1F"
CERT_B = "ehwuP0BRYnOElaa3yNng8QITJDVGV2h5qLnK2-z9Dh8"
PACKAGES = f"appa:com.example.a:{CERT_A_HEX};appb:com.example.b"
T0 = 1_700_000_000.0


class _Clock:
    def __init__(self, now=T0):
        self.now = now

    def __call__(self):
        return self.now


class _Resp:
    def __init__(self, body):
        self._body = body

    def raise_for_status(self):
        return None

    def json(self):
        return self._body


class _SimSession:
    """Giả `AuthorizedSession.post` của decodeIntegrityToken: token = chính payload JSON."""

    def __init__(self):
        self.calls = []

    def post(self, url, json=None, timeout=None):
        self.calls.append(url)
        assert timeout, "mọi lời gọi decode phải có timeout"
        return _Resp(_loads(json["integrity_token"]))


_loads = json.loads


def _google_client(tmp_path, *, packages=PACKAGES, clock=None, session=None, factory=None, **settings_kw):
    from app.config import Settings
    from app.integrity import GoogleIntegrityVerifier
    from app.main import create_app
    from app.nonce_store import NonceStore
    from app.play_packages import parse_play_packages

    clock, session, created = clock or _Clock(), session or _SimSession(), []
    pdir = tmp_path / "policies"
    pdir.mkdir(exist_ok=True)
    for name, body in {"appa": POLICY_A, "appb": {"version": 9}}.items():
        (pdir / f"{name}.json").write_text(json.dumps(body), encoding="utf-8")
    settings = Settings(api_keys=f"appa:{KEY_A},appb:{KEY_B}", sink="jsonl",
                        jsonl_path=str(tmp_path / "e.jsonl"), integrity_mode="google",
                        play_packages=packages, policy_dir=str(pdir), **settings_kw)
    application = create_app(settings)
    nonces = NonceStore(settings.integrity_nonce_ttl_s, clock=clock)

    def created_factory():
        created.append(1)
        return session

    application.state.nonces = nonces
    application.state.integrity = GoogleIntegrityVerifier(
        parse_play_packages(packages), nonces, session_factory=factory or created_factory, clock=clock,
        max_age_s=settings.integrity_max_age_s, require_licensed=settings.integrity_require_licensed)
    return TestClient(application), clock, session, created


def _nonce(client, key=KEY_A):
    resp = client.post("/policy/nonce", headers={"X-Api-Key": key})
    assert resp.status_code == 200, resp.text
    return resp.json()["nonce"]


def _token(name, nonce, clock, *, ts_ms=None, mutate=None):
    ms = int(clock() * 1000) if ts_ms is None else ts_ms
    text = ((FIXTURES / name).read_text(encoding="utf-8").replace("__NONCE__", nonce)
            .replace("__NOW_MS__", str(ms)).replace("__OLD_MS__", str(ms - 600_000))
            .replace("__CERT__", CERT_A).replace("__CERT_OTHER__", CERT_B))
    payload = json.loads(text)
    if mutate:
        mutate(payload)
    return json.dumps(payload)


def _as_package(pkg):
    def mutate(payload):
        body = payload["tokenPayloadExternal"]
        body["requestDetails"]["requestPackageName"] = body["appIntegrity"]["packageName"] = pkg
    return mutate


def _accepted(resp):
    return resp.status_code == 200 and resp.json()["source"] == "app"


def _rejected(resp, reason="not_accepted"):
    body = resp.json()
    return resp.status_code == 200 and (body["source"], body["reason"]) == ("default", reason)


def test_po14_request_package_name_must_belong_to_the_app(tmp_path):
    client, clock, _, _ = _google_client(tmp_path)
    ok = _get(client, token=_token("ok.json", _nonce(client), clock))
    assert _accepted(ok)  # đối chứng: cùng đường đi, gói đúng
    evil = _get(client, token=_token("wrong_package.json", _nonce(client), clock))
    assert _rejected(evil)
    # gói của app B (có đăng ký) dùng với khoá app A vẫn là gói lạ
    other = _token("ok.json", _nonce(client), clock, mutate=_as_package("com.example.b"))
    assert _rejected(_get(client, token=other))
    for section, field in (("appIntegrity", "packageName"), ("requestDetails", "requestPackageName")):
        split = _token("ok.json", _nonce(client), clock, mutate=lambda p, s=section, f=field: p[
            "tokenPayloadExternal"][s].update({f: "com.evil.app"}))  # chỉ MỘT trong hai trường gói là lạ
        assert _rejected(_get(client, token=split)), (section, field)


def test_po15_nonce_is_single_use_and_exactly_one_field(tmp_path, caplog):
    client, clock, _, _ = _google_client(tmp_path)
    nonce = _nonce(client)
    token = _token("ok.json", nonce, clock)
    with caplog.at_level(logging.DEBUG):
        assert _accepted(_get(client, token=token))
        assert _rejected(_get(client, token=token))  # phát lại
        assert _rejected(_get(client, token=_token("wrong_nonce.json", "x", clock)))
    assert nonce not in caplog.text and token not in caplog.text
    # standard request: `requestHash` mang nonce của server cũng được; thiếu cả hai = nonce_required
    assert _accepted(_get(client, token=_token("request_hash.json", _nonce(client), clock)))
    assert _rejected(_get(client, token=_token("no_nonce.json", "", clock)), "nonce_required")

    def both(p):
        p["tokenPayloadExternal"]["requestDetails"]["requestHash"] = "dup"

    assert _rejected(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=both)))


def test_po16_timestamp_must_be_fresh_and_not_from_the_future(tmp_path):
    client, clock, _, _ = _google_client(tmp_path)
    assert _rejected(_get(client, token=_token("stale_timestamp.json", _nonce(client), clock)))
    for offset_s, accepted in ((-290, True), (-310, False), (30, True), (600, False)):
        token = _token("ok.json", _nonce(client), clock, ts_ms=int((clock() + offset_s) * 1000))
        assert _accepted(_get(client, token=token)) is accepted, offset_s


def test_po17_rate_limit_per_app_shared_by_policy_and_nonce(tmp_path):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_rps=0.001, policy_rate_burst=3)
    _nonce(client)
    _get(client, token="x")
    _get(client, token="x")
    limited = _get(client, token="x")
    assert limited.status_code == 429 and int(limited.headers["Retry-After"]) >= 1
    assert client.post("/policy/nonce", headers={"X-Api-Key": KEY_A}).status_code == 429
    # không lách được bằng query/header tự khai; app khác có bucket riêng; khoá sai 401 không tốn token app nào
    assert _get(client, token="x", params={"app_id": "appb"}).status_code == 429
    assert client.get("/policy", headers={"X-Api-Key": KEY_A, "X-Forwarded-For": "9.9.9.9"}).status_code == 429
    assert _get(client, key=KEY_B, token="x").status_code == 200
    assert _get(client, key="wrong-key").status_code == 401
    assert _get(client, key=KEY_B, token="x").status_code == 200


def test_po18_auth_off_refuses_policy_and_nonce(tmp_path):
    from app.config import Settings
    from app.main import create_app

    spy = _Spy(verdict=None)
    settings = Settings(sink="jsonl", jsonl_path=str(tmp_path / "e.jsonl"), integrity_mode="fake",
                        policy_dir=str(tmp_path))
    app = create_app(settings)
    app.state.integrity = spy
    client = TestClient(app)
    for resp in (client.get("/policy", headers={"X-Integrity-Token": "fake-ok"}),
                 client.post("/policy/nonce")):
        assert resp.status_code == 503 and resp.json()["error"]["code"] == "auth_off"
    assert spy.calls == []


def test_po19_nonce_expires_after_ttl(tmp_path):
    client, clock, _, _ = _google_client(tmp_path)
    late, early = _nonce(client), _nonce(client)
    clock.now += 119
    assert _accepted(_get(client, token=_token("ok.json", early, clock)))
    clock.now += 2  # late đã 121 s > TTL 120 s
    assert _rejected(_get(client, token=_token("ok.json", late, clock)))


def test_po20_nonce_is_bound_to_the_issuing_app(tmp_path):
    client, clock, _, _ = _google_client(tmp_path)
    nonce_a = _nonce(client, KEY_A)
    token_for_b = _token("ok.json", nonce_a, clock, mutate=_as_package("com.example.b"))
    assert _rejected(_get(client, key=KEY_B, token=token_for_b))
    # bị đốt khi dùng sai app: chủ nonce cũng không dùng lại được
    assert _rejected(_get(client, token=_token("ok.json", nonce_a, clock)))
    own = _token("ok.json", _nonce(client, KEY_B), clock, mutate=_as_package("com.example.b"))
    assert _accepted(_get(client, key=KEY_B, token=own))


def test_po21_certificate_digest_base64url_and_rotation(tmp_path):
    client, clock, _, _ = _google_client(tmp_path)
    assert _accepted(_get(client, token=_token("cert_b64url.json", _nonce(client), clock)))

    def only_other(p):
        p["tokenPayloadExternal"]["appIntegrity"]["certificateSha256Digest"] = [CERT_B]

    def none(p):
        del p["tokenPayloadExternal"]["appIntegrity"]["certificateSha256Digest"]

    def padded(p):
        p["tokenPayloadExternal"]["appIntegrity"]["certificateSha256Digest"] = [CERT_A + "="]

    assert _rejected(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=only_other)))
    assert _rejected(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=none)))
    assert _accepted(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=padded)))
    # xoay chứng chỉ ký: cấu hình khai cả cũ + mới, payload mang cái nào cũng được
    rotated = f"appa:com.example.a:{CERT_B_HEX},{CERT_A_HEX};appb:com.example.b"
    client2, clock2, _, _ = _google_client(tmp_path, packages=rotated)
    assert _accepted(_get(client2, token=_token("ok.json", _nonce(client2), clock2, mutate=only_other)))


def test_po22_google_mode_requires_a_package_entry_per_keyed_app(tmp_path):
    from app.config import Settings
    from app.main import create_app

    base = dict(api_keys=f"appa:{KEY_A},appb:{KEY_B}", jsonl_path=str(tmp_path / "e.jsonl"),
                integrity_mode="google")
    with pytest.raises(ValueError, match="BRAIN_PLAY_PACKAGES"):
        create_app(Settings(play_packages="appa:com.example.a", **base))  # thiếu appb
    for bad in ("appa:com.example.a;appb:", "appa:com.example.a:zz;appb:com.example.b",
                "appa:not a package;appb:com.example.b", "appa:com.example.a;appa:com.example.a;appb:x.y"):
        with pytest.raises(ValueError, match="BRAIN_PLAY_PACKAGES"):
            create_app(Settings(play_packages=bad, **base))
    create_app(Settings(play_packages=PACKAGES, **base))  # đủ mục thì khởi động được


def test_po23_credential_is_created_once_and_decode_has_timeout(tmp_path):
    client, clock, session, created = _google_client(tmp_path)
    for _ in range(2):
        assert _accepted(_get(client, token=_token("ok.json", _nonce(client), clock)))
    assert created == [1] and len(session.calls) == 2
    assert all("/v1/com.example.a:decodeIntegrityToken" in url for url in session.calls)


def test_po24_nonce_endpoint_needs_a_valid_key(tmp_path):
    client, _, _, _ = _google_client(tmp_path)
    for headers in ({}, {"X-Api-Key": "wrong-key"}):
        assert client.post("/policy/nonce", headers=headers).status_code == 401
    first, second = (client.post("/policy/nonce", headers={"X-Api-Key": KEY_A}).json() for _ in range(2))
    assert first["nonce"] != second["nonce"] and len(first["nonce"]) >= 43 and first["expires_in_s"] == 120
    assert client.get("/policy/nonce", headers={"X-Api-Key": KEY_A}).status_code == 405
    # xoay khoá API: khoá cũ hết hiệu lực ngay khi cấu hình mới được nạp
    rotated, _, _, _ = _google_client(tmp_path)
    rotated.app.state.auth = type(rotated.app.state.auth).from_settings(f"appa:{KEY_B},appb:{KEY_A}", "")
    assert rotated.post("/policy/nonce", headers={"X-Api-Key": KEY_A}).status_code == 200  # nay là appb
    assert _nonce(rotated, KEY_B) and rotated.app.state.auth.resolve(KEY_A) == "appb"


def test_po25_non_numeric_timestamp_is_rejected(tmp_path):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    for bad in ("abc", "12.5", "-5", "", " 1700000000000", "١٢٣", 1_700_000_000_000, None, "9" * 40):
        def set_ts(p, v=bad):
            p["tokenPayloadExternal"]["requestDetails"]["timestampMillis"] = v

        assert _rejected(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=set_ts))), bad


def test_po26_package_match_is_exact_not_prefix_or_parent(tmp_path):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    for fake in ("com.example.a.evil", "com.example", "com.example.", "com.example.aa", "COM.EXAMPLE.A"):
        for section, field in (("requestDetails", "requestPackageName"), ("appIntegrity", "packageName")):
            token = _token("ok.json", _nonce(client), clock, mutate=lambda p, s=section, f=field, v=fake: p[
                "tokenPayloadExternal"][s].update({f: v}))
            assert _rejected(_get(client, token=token)), (section, fake)


def test_po27_missing_app_package_malformed_device_and_license(tmp_path):
    def drop_pkg(p):
        del p["tokenPayloadExternal"]["appIntegrity"]["packageName"]

    def device(v):
        return lambda p: p["tokenPayloadExternal"]["deviceIntegrity"].update(deviceRecognitionVerdict=v)

    def lic(v):
        def mutate(p):
            if v is None:
                del p["tokenPayloadExternal"]["accountDetails"]
            else:
                p["tokenPayloadExternal"]["accountDetails"]["appLicensingVerdict"] = v
        return mutate

    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    cases = (drop_pkg, device("MEETS_DEVICE_INTEGRITY"), device(None), lic("UNLICENSED"), lic("UNEVALUATED"), lic(None))
    for mutate in cases:  # mặc định: tất cả bị từ chối
        assert _rejected(_get(client, token=_token("ok.json", _nonce(client), clock, mutate=mutate)))
    off, clock2, _, _ = _google_client(tmp_path, integrity_require_licensed=False, policy_rate_burst=1000)
    for mutate in (lic("UNLICENSED"), lic(None)):  # cờ tắt: giấy phép không xét
        assert _accepted(_get(off, token=_token("ok.json", _nonce(off), clock2, mutate=mutate)))
    for mutate in (drop_pkg, device("MEETS_DEVICE_INTEGRITY")):  # nhưng gói/thiết bị vẫn fail-closed
        assert _rejected(_get(off, token=_token("ok.json", _nonce(off), clock2, mutate=mutate)))


def _logged(caplog, text):
    return text in caplog.text


def test_po28_app_and_device_verdicts_must_be_play_recognized_and_device_integrity(tmp_path, caplog):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    caplog.set_level(logging.INFO, logger="brain.policy")
    for verdict in ("UNRECOGNIZED_VERSION", "UNEVALUATED", "play_recognized", ["PLAY_RECOGNIZED"]):
        caplog.clear()
        token = _token("ok.json", _nonce(client), clock, mutate=lambda p, v=verdict: p["tokenPayloadExternal"][
            "appIntegrity"].update(appRecognitionVerdict=v))
        assert _rejected(_get(client, token=token)), verdict
        assert _logged(caplog, "app_not_recognized"), verdict
    for devices in (["MEETS_BASIC_INTEGRITY"], ["MEETS_STRONG_INTEGRITY"], []):
        caplog.clear()
        token = _token("ok.json", _nonce(client), clock, mutate=lambda p, d=devices: p["tokenPayloadExternal"][
            "deviceIntegrity"].update(deviceRecognitionVerdict=d))
        assert _rejected(_get(client, token=token)), devices
        assert _logged(caplog, "device_not_ok"), devices


def test_po29_certificate_digest_must_be_a_list(tmp_path, caplog):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    caplog.set_level(logging.INFO, logger="brain.policy")
    for bad in ({CERT_A: 1}, CERT_A, "", 7):
        caplog.clear()
        token = _token("ok.json", _nonce(client), clock, mutate=lambda p, v=bad: p["tokenPayloadExternal"][
            "appIntegrity"].update(certificateSha256Digest=v))
        assert _rejected(_get(client, token=token)), bad
        assert _logged(caplog, "cert_malformed"), bad


def test_po30_malformed_google_payload_is_payload_malformed_not_nonce_required(tmp_path, caplog):
    client, clock, _, _ = _google_client(tmp_path, policy_rate_burst=1000)
    caplog.set_level(logging.INFO, logger="brain.policy")
    for raw in ("[]", "{}", '"text"', "null", '{"tokenPayloadExternal": "x"}', '{"tokenPayloadExternal": []}'):
        caplog.clear()
        assert _rejected(_get(client, token=raw)), raw  # not_accepted trên dây
        assert _logged(caplog, "payload_malformed"), raw


class _HttpError(Exception):
    def __init__(self, status, body):
        super().__init__(f"{status} {body}")
        self.response = type("R", (), {"status_code": status, "text": body})()


def test_po31_http_error_logs_status_code_never_body(tmp_path, caplog):
    secret = "BODY-SECRET-1a2b3c"

    class Failing:
        def post(self, url, json=None, timeout=None):
            raise _HttpError(403, secret)

    client, _, _, _ = _google_client(tmp_path, session=Failing())
    caplog.set_level(logging.DEBUG)
    assert _rejected(_get(client, token="tok-xyz"))
    assert _logged(caplog, "verifier_error:_HttpError:http_403")
    assert secret not in caplog.text and "tok-xyz" not in caplog.text


def test_po32_every_registered_package_is_tried_in_order(tmp_path):
    packages = f"appa:com.example.a|com.example.a2:{CERT_A_HEX};appb:com.example.b"

    class PerPackage(_SimSession):
        def post(self, url, json=None, timeout=None):
            if "/com.example.a:" in url:
                self.calls.append(url)
                raise _HttpError(400, "wrong package")
            return super().post(url, json=json, timeout=timeout)

    session = PerPackage()
    client, clock, _, _ = _google_client(tmp_path, packages=packages, session=session)
    token = _token("ok.json", _nonce(client), clock, mutate=_as_package("com.example.a2"))
    assert _accepted(_get(client, token=token))
    assert [u.split("/v1/")[1].split(":")[0] for u in session.calls] == ["com.example.a", "com.example.a2"]
    # fail-closed sau gói cuối: gói thứ ba không đăng ký -> mọi lần decode lỗi hoặc gói lạ -> mặc định
    assert _rejected(_get(client, token=_token("wrong_package.json", _nonce(client), clock)))


def test_po33_non_finite_numbers_refuse_to_start(monkeypatch):
    from app.config import Settings

    for name in ("BRAIN_POLICY_RATE_RPS", "BRAIN_INTEGRITY_NONCE_TTL_S", "BRAIN_INTEGRITY_MAX_AGE_S",
                 "BRAIN_RATE_APP_RPS", "BRAIN_POLICY_RATE_BURST"):
        for bad in ("nan", "inf", "-inf"):
            monkeypatch.setenv(name, bad)
            with pytest.raises(ValueError, match=name):
                Settings.from_env()
        monkeypatch.delenv(name)
    for field in ("policy_rate_rps", "integrity_nonce_ttl_s", "integrity_max_age_s", "policy_rate_burst"):
        for bad in (float("nan"), float("inf")):
            with pytest.raises(ValueError, match="hữu hạn"):
                Settings(**{field: bad}).validate()
    Settings.from_env()  # giá trị mặc định vẫn khởi động được


def test_po34_fake_mode_refuses_a_real_bigquery_sink():
    from app.config import Settings

    with pytest.raises(ValueError, match="fake"):
        Settings(integrity_mode="fake", sink="bigquery", bq_project="prod-project").validate()
    Settings(integrity_mode="fake", sink="jsonl").validate()
    Settings(integrity_mode="fake", sink="bigquery", bq_project="local-test", bq_endpoint="http://127.0.0.1:1").validate()
    Settings(integrity_mode="off", sink="bigquery", bq_project="prod-project").validate()


def test_po35_failed_credential_creation_is_backed_off(tmp_path):
    calls = []

    def broken():
        calls.append(1)
        raise RuntimeError("no ADC")

    client, clock, _, _ = _google_client(tmp_path, factory=broken, policy_rate_burst=1000)
    for _ in range(5):
        assert _rejected(_get(client, token="{}"))
    assert len(calls) == 1
    clock.now += 31
    assert _rejected(_get(client, token="{}"))
    assert len(calls) == 2


def test_po36_licensed_flag_parses_case_insensitively(monkeypatch):
    from app.config import Settings

    for raw, expected in (("0", False), ("false", False), ("False", False), ("NO", False), ("Off", False),
                          ("1", True), ("true", True), ("YES", True), ("", True)):
        monkeypatch.setenv("BRAIN_INTEGRITY_REQUIRE_LICENSED", raw)
        assert Settings.from_env().integrity_require_licensed is expected, raw
