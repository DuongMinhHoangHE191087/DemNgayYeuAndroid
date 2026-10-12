"""2.3.0 SECBS — minors của `/policy` (m-6 hạn mức theo client, m-a SessionBackoff, m-b giải mã nhiều gói).

Không mạng, không import `google.auth`; đồng hồ và phiên decode đều giả.
"""

from __future__ import annotations

import logging

import pytest
from fastapi.testclient import TestClient

from test_policy import (CERT_A_HEX, KEY_A, T0, _Clock, _get, _google_client, _HttpError, _nonce, _rejected,
                         _SimSession, _token)

SLOW = {"policy_rate_rps": 0.001}  # bucket gần như không nạp lại trong lúc test chạy


def _app(tmp_path, **kw):
    from app.config import Settings
    from app.main import create_app

    settings = Settings(api_keys=f"appa:{KEY_A}", sink="jsonl", jsonl_path=str(tmp_path / "e.jsonl"),
                        integrity_mode="fake", **kw)
    return TestClient(create_app(settings))


def _as(client, ip, path="/policy"):
    headers = {"X-Api-Key": KEY_A, "X-Forwarded-For": ip}  # một hop = proxy tin cậy nối thêm (N=1)
    return client.get(path, headers=headers) if path == "/policy" else client.post(path, headers=headers)


def test_po37_one_client_exhausting_its_burst_does_not_block_another_client(tmp_path):
    client = _app(tmp_path, trust_proxy_hops=1, policy_rate_burst=1.0, **SLOW)
    assert _as(client, "1.1.1.1").status_code == 200
    limited = _as(client, "1.1.1.1")
    assert limited.status_code == 429 and int(limited.headers["Retry-After"]) >= 1
    assert _as(client, "2.2.2.2").status_code == 200
    assert _as(client, "2.2.2.2", "/policy/nonce").status_code == 429  # nonce dùng chung bucket client


def test_po38_total_across_clients_is_capped_per_app(tmp_path):
    client = _app(tmp_path, trust_proxy_hops=1, policy_app_rate_burst=3.0, policy_app_rate_rps=0.001, **SLOW)
    codes = [_as(client, f"3.3.3.{n}").status_code for n in range(1, 5)]
    assert codes == [200, 200, 200, 429]


def test_po39_forged_forwarded_for_is_ignored_unless_proxy_is_trusted(tmp_path):
    client = _app(tmp_path, policy_rate_burst=2.0, **SLOW)  # trust_proxy_hops mặc định 0 = chỉ peer
    codes = [_as(client, f"4.4.4.{n}").status_code for n in range(1, 4)]
    assert codes == [200, 200, 429]


def test_po40_session_backoff_is_logged_by_its_own_class(tmp_path, caplog):
    def no_credentials():
        raise RuntimeError("no adc")

    client, _, _, _ = _google_client(tmp_path, factory=no_credentials)
    caplog.set_level(logging.DEBUG)
    assert _rejected(_get(client, token="tok"))
    caplog.clear()
    assert _rejected(_get(client, token="tok"))
    assert "verifier_error:SessionBackoff" in caplog.text


def test_po41_all_package_failures_are_merged_into_the_detail(tmp_path, caplog):
    secret = "BODY-SECRET-9f8e"

    class AllFail(_SimSession):
        def post(self, url, json=None, timeout=None):
            self.calls.append(url)
            raise _HttpError(500 if "/com.example.a:" in url else 403, secret)

    packages = f"appa:com.example.a|com.example.a2:{CERT_A_HEX};appb:com.example.b"
    client, _, _, _ = _google_client(tmp_path, packages=packages, session=AllFail())
    caplog.set_level(logging.DEBUG)
    assert _rejected(_get(client, token="tok-xyz"))  # mã ra dây vẫn not_accepted
    assert "decode_failed:com.example.a=http_500,com.example.a2=http_403" in caplog.text
    assert secret not in caplog.text and "tok-xyz" not in caplog.text


def test_po42_decode_over_all_packages_shares_one_five_second_budget(tmp_path, caplog):
    clock = _Clock()

    seen = []  # (timeout, đã trôi)

    class Slow(_SimSession):
        def post(self, url, json=None, timeout=None):
            self.calls.append(url)
            seen.append((timeout, clock.now - T0))
            clock.now += min(timeout, 2.0)  # lỗi sau 2 s hoặc tới hạn của lời gọi
            raise TimeoutError("slow")

    packages = f"appa:com.example.a|com.example.a2|com.example.a3:{CERT_A_HEX};appb:com.example.b"
    session = Slow()
    client, _, _, _ = _google_client(tmp_path, packages=packages, clock=clock, session=session)
    nonce = _nonce(client)
    caplog.set_level(logging.DEBUG)
    assert _rejected(_get(client, token=_token("ok.json", nonce, clock)))
    assert len(seen) == 3 and clock.now - T0 <= 5.0
    assert all(t <= 5.0 - used + 1e-9 for t, used in seen), seen  # mỗi lời gọi chỉ được phần ngân sách còn lại
    assert "decode_failed:com.example.a=TimeoutError" in caplog.text


def test_po43_app_rate_env_is_validated_and_read(tmp_path, monkeypatch):
    from app.config import Settings

    monkeypatch.setenv("BRAIN_POLICY_APP_RATE_RPS", "5")
    monkeypatch.setenv("BRAIN_POLICY_APP_RATE_BURST", "50")
    monkeypatch.setenv("BRAIN_TRUST_PROXY_HOPS", "2")
    s = Settings.from_env()
    assert (s.policy_app_rate_rps, s.policy_app_rate_burst, s.trust_proxy_hops) == (5.0, 50.0, 2)
    monkeypatch.delenv("BRAIN_TRUST_PROXY_HOPS")
    assert Settings.from_env().trust_proxy_hops == 0
    for name, bad in (("BRAIN_POLICY_APP_RATE_RPS", "nan"), ("BRAIN_POLICY_APP_RATE_BURST", "inf")):
        monkeypatch.setenv(name, bad)
        with pytest.raises(ValueError):
            Settings.from_env()
        monkeypatch.setenv(name, "20")


def _burst1(tmp_path, hops, **kw):
    return _app(tmp_path, trust_proxy_hops=hops, policy_rate_burst=1.0, **SLOW, **kw)


def _hdr(xff=None):
    return {"X-Api-Key": KEY_A, **({"X-Forwarded-For": xff} if xff is not None else {})}


def test_po44_client_is_the_nth_hop_from_the_right(tmp_path):
    header = "9.9.9.9, 1.1.1.1, 10.0.0.1"  # hop đầu do client tự khai (giả được)
    two = _burst1(tmp_path, 2)
    assert two.get("/policy", headers=_hdr(header)).status_code == 200
    assert two.get("/policy", headers=_hdr("8.8.8.8, 1.1.1.1, 10.0.0.1")).status_code == 429  # hop giả không đổi bucket
    assert two.get("/policy", headers=_hdr("9.9.9.9, 2.2.2.2, 10.0.0.1")).status_code == 200
    one = _burst1(tmp_path, 1)  # N=1: client = hop cuối (proxy), mọi client chung bucket
    assert one.get("/policy", headers=_hdr(header)).status_code == 200
    assert one.get("/policy", headers=_hdr("9.9.9.9, 2.2.2.2, 10.0.0.1")).status_code == 429


@pytest.mark.parametrize("bad", ["not-an-ip", "x" * 4000, "", "1.1.1.1"], ids=["junk", "long", "empty", "short"])
def test_po45_invalid_or_short_xff_falls_back_to_peer_and_logs_a_fixed_code(tmp_path, caplog, bad):
    client = _burst1(tmp_path, 2)  # "1.1.1.1" chỉ 1 hop < N=2 -> cũng không hợp lệ
    caplog.set_level(logging.DEBUG)
    assert client.get("/policy", headers=_hdr()).status_code == 200  # peer
    assert client.get("/policy", headers=_hdr(f"{bad}, {bad}" if bad and bad != "1.1.1.1" else bad)).status_code == 429
    assert "xff_invalid" in caplog.text and "x" * 50 not in caplog.text and "not-an-ip" not in caplog.text


def test_po46_ipv6_clients_are_bucketed_by_their_64_prefix(tmp_path):
    client = _burst1(tmp_path, 1)
    assert client.get("/policy", headers=_hdr("2001:db8:0:1::1")).status_code == 200
    assert client.get("/policy", headers=_hdr("2001:db8:0:1:ffff::2")).status_code == 429  # cùng /64
    assert client.get("/policy", headers=_hdr("2001:db8:0:2::1")).status_code == 200  # /64 khác


def test_po47_limited_client_uses_no_app_token(tmp_path):
    client = _app(tmp_path, trust_proxy_hops=1, policy_rate_burst=1.0, policy_app_rate_burst=3.0,
                  policy_app_rate_rps=0.001, **SLOW)
    assert client.get("/policy", headers=_hdr("1.1.1.1")).status_code == 200
    for _ in range(20):  # client đã hết hạn mức của chính nó: không được tiêu token của app
        assert client.get("/policy", headers=_hdr("1.1.1.1")).status_code == 429
    assert [client.get("/policy", headers=_hdr(f"2.2.2.{n}")).status_code for n in (1, 2, 3)] == [200, 200, 429]


def test_po49_only_decode_failed_detail_reaches_the_log(tmp_path, caplog):
    from test_policy import _client, _Spy

    class Odd(Exception):
        detail = "LEAKY-DETAIL-1234"

    caplog.set_level(logging.DEBUG)
    assert _rejected(_get(_client(tmp_path, verifier=_Spy(exc=Odd("LEAKY-MSG"))), token="t"))
    assert "verifier_error:Odd" in caplog.text and "LEAKY-DETAIL" not in caplog.text and "LEAKY-MSG" not in caplog.text


def test_po48_bad_thresholds_refuse_to_start(tmp_path, monkeypatch):
    from app.config import Settings
    from app.main import create_app

    for kw in ({"policy_app_rate_rps": float("nan")}, {"policy_app_rate_burst": float("inf")},
               {"trust_proxy_hops": 6}, {"trust_proxy_hops": -1}):
        with pytest.raises(ValueError):
            create_app(Settings(api_keys=f"appa:{KEY_A}", sink="jsonl", jsonl_path=str(tmp_path / "e"), **kw))
    for bad in ("abc", "1.5", "6", "-1"):
        monkeypatch.setenv("BRAIN_TRUST_PROXY_HOPS", bad)
        with pytest.raises(ValueError):
            Settings.from_env()
