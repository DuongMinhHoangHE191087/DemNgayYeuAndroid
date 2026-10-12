"""Verdict Play Integrity cho `GET /policy` (2.1.0 BE2).

`IntegrityVerifier.verify(token, app_id) -> Verdict`. Mode (`BRAIN_INTEGRITY_MODE`):
`off` (mặc định) không có verifier -> mọi app nhận artifact mặc định; `fake` = `FakeVerifier` (test/stack
cục bộ, không mạng); `google` = `GoogleIntegrityVerifier`, import lười, owner-gated (O3): KHÔNG chạy trong
test hay lane. Không verifier nào được in/ghi token; lý do (`reason`) luôn là mã cố định.
"""

from __future__ import annotations

import re
import threading
import time
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from typing import Any, Protocol

from .nonce_store import NonceStore
from .play_packages import AppBinding, parse_play_packages

MODES = ("off", "fake", "google")

FAKE_OK = "fake-ok"
FAKE_BAD_DEVICE = "fake-bad-device"
FAKE_RAISE = "fake-raise"
_DECODE_URL = "https://playintegrity.googleapis.com/v1/{package}:decodeIntegrityToken"
_SCOPE = "https://www.googleapis.com/auth/playintegrity"
_HTTP_TIMEOUT_S = 5.0
_MILLIS_RE = re.compile(r"[0-9]{1,16}")  # `timestampMillis` là CHUỖI số (v1.d.ts); ASCII, không dấu
_MAX_FUTURE_SKEW_S = 60.0
_SESSION_BACKOFF_S = 30.0  # sau khi tạo credential lỗi (không có ADC): không thử lại mỗi request


@dataclass(frozen=True)
class Verdict:
    app_recognized: bool
    device_ok: bool
    reason: str

    @property
    def accepted(self) -> bool:
        return self.app_recognized and self.device_ok


class IntegrityVerifier(Protocol):
    def verify(self, token: str, app_id: str) -> Verdict: ...


class FakeVerifier:
    """Quyết định theo giá trị token cố định; token lạ = app không được nhận diện. Không mạng."""

    def verify(self, token: str, app_id: str) -> Verdict:
        if token == FAKE_OK:
            return Verdict(True, True, "ok")
        if token == FAKE_BAD_DEVICE:
            return Verdict(True, False, "device_not_ok")
        if token == FAKE_RAISE:
            raise RuntimeError("fake verifier failure")
        return Verdict(False, False, "app_not_recognized")


class SessionBackoff(RuntimeError): ...  # chờ sau khi tạo credential lỗi (log `verifier_error:SessionBackoff`)


class DecodeFailed(RuntimeError):  # mọi gói lỗi; `detail` = `decode_failed:<pkg>=<mã>,…` chỉ vào log
    detail = property(lambda self: str(self))


class GoogleIntegrityVerifier:
    """`decodeIntegrityToken` + ràng buộc server (2.2.0 SECB): app/thiết bị được nhận diện, gói và cert khớp đăng ký,
    nonce của server còn hạn/chưa dùng/đúng app, timestamp đủ mới. Tên trường theo `googleapis@144.0.0 playintegrity/v1.d.ts`.
    Session tạo MỘT lần (import lười `google.auth`); mọi lời gọi có timeout; `reason` là hằng, không log token/nonce."""

    def __init__(self, bindings: Mapping[str, AppBinding], nonces: NonceStore, *,
                 session_factory: Callable[[], Any] | None = None, clock: Callable[[], float] = time.time,
                 max_age_s: float = 300.0, require_licensed: bool = True) -> None:
        self._require_licensed = require_licensed
        self._bindings = bindings
        self._nonces = nonces
        self._factory = session_factory or _default_session
        self._clock = clock
        self._max_age_s = max_age_s
        self._session: Any = None
        self._retry_at = 0.0
        self._lock = threading.Lock()

    def _session_once(self) -> Any:
        with self._lock:
            if self._session is None:
                if self._clock() < self._retry_at:
                    raise SessionBackoff("session_backoff")
                try:
                    self._session = self._factory()
                except Exception:
                    self._retry_at = self._clock() + _SESSION_BACKOFF_S
                    raise
            return self._session

    def verify(self, token: str, app_id: str) -> Verdict:
        binding = self._bindings.get(app_id)
        if binding is None:
            return Verdict(False, False, "app_not_registered")
        payload = self._decode(token, binding.packages)
        body = payload.get("tokenPayloadExternal") if isinstance(payload, dict) else None
        if not isinstance(body, dict):
            return Verdict(False, False, "payload_malformed")
        base, reason = _verdict_from_payload(payload), self._binding_failure(body, app_id, binding)
        return Verdict(False, base.device_ok, reason) if reason else base

    def _decode(self, token: str, packages: tuple[str, ...]) -> Any:
        """Từng gói trong MỘT hạn chung `_HTTP_TIMEOUT_S`; hết gói = ném: 1 lỗi ném nguyên, nhiều lỗi gộp `DecodeFailed`."""
        session = self._session_once()  # lỗi credential/back-off áp cho mọi gói: ném ngay
        deadline = self._clock() + _HTTP_TIMEOUT_S
        failures, last = [], None
        for package in packages:
            if (budget := deadline - self._clock()) <= 0:
                failures.append(f"{package}=deadline")
                break
            try:
                resp = session.post(_DECODE_URL.format(package=package), json={"integrity_token": token}, timeout=min(budget, _HTTP_TIMEOUT_S))
                resp.raise_for_status()
                return resp.json()
            except Exception as exc:  # noqa: BLE001 — thử gói kế
                code = getattr(getattr(exc, "response", None), "status_code", None)
                failures.append(f"{package}={f'http_{code}' if isinstance(code, int) else type(exc).__name__}")
                last = exc
        if len(failures) == 1 and last is not None:
            raise last
        raise DecodeFailed("decode_failed:" + ",".join(failures))

    def _binding_failure(self, body: dict[str, Any], app_id: str, binding: AppBinding) -> str | None:
        req = _obj(body.get("requestDetails"))
        app = _obj(body.get("appIntegrity"))
        sent = [v for v in (req.get("nonce"), req.get("requestHash")) if isinstance(v, str) and v]
        if not sent:
            return "nonce_required"
        if len(sent) != 1:
            return "nonce_ambiguous"
        if not self._nonces.consume(sent[0], app_id):  # đốt trước mọi kiểm tra khác
            return "nonce_invalid"
        if req.get("requestPackageName") not in binding.packages:
            return "package_mismatch"
        if app.get("packageName") not in binding.packages:  # thiếu cũng từ chối (token thật luôn có)
            return "package_mismatch"
        if not self._fresh(req.get("timestampMillis")):
            return "timestamp_invalid"
        digests = app.get("certificateSha256Digest")
        if binding.certs and not isinstance(digests, list):  # dict/chuỗi sẽ lặp theo khoá/ký tự
            return "cert_malformed"
        certs = {d.rstrip("=") for d in digests or [] if isinstance(d, str)}
        if binding.certs and not certs & binding.certs:
            return "cert_mismatch"
        licensed = _obj(body.get("accountDetails")).get("appLicensingVerdict")
        if self._require_licensed and licensed != "LICENSED":  # UNLICENSED/UNEVALUATED/thiếu
            return "not_licensed"
        return None

    def _fresh(self, raw: Any) -> bool:
        if not isinstance(raw, str) or not _MILLIS_RE.fullmatch(raw):
            return False
        age_s = self._clock() - int(raw) / 1000.0
        return -_MAX_FUTURE_SKEW_S <= age_s <= self._max_age_s


def _obj(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _default_session() -> Any:
    import google.auth  # noqa: PLC0415
    from google.auth.transport.requests import AuthorizedSession  # noqa: PLC0415

    creds, _ = google.auth.default(scopes=[_SCOPE])
    return AuthorizedSession(creds, refresh_timeout=_HTTP_TIMEOUT_S)


def _verdict_from_payload(payload: Any) -> Verdict:
    body = payload.get("tokenPayloadExternal") if isinstance(payload, dict) else None
    body = _obj(body)
    recognized = _obj(body.get("appIntegrity")).get("appRecognitionVerdict") == "PLAY_RECOGNIZED"
    devices = _obj(body.get("deviceIntegrity")).get("deviceRecognitionVerdict")
    ok = isinstance(devices, list) and "MEETS_DEVICE_INTEGRITY" in devices  # chuỗi sẽ khớp substring
    if not recognized:
        reason = "app_not_recognized"
    else:
        reason = "ok" if ok else ("device_not_ok" if isinstance(devices, list) else "device_malformed")
    return Verdict(recognized, ok, reason)


def build_integrity_verifier(settings: Any, nonces: NonceStore | None = None) -> IntegrityVerifier | None:
    """None khi mode `off`. Mode lạ đã bị `Settings.validate` chặn; ở đây vẫn fail-closed."""
    mode = settings.integrity_mode
    if mode == "off":
        return None
    if mode == "fake":
        return FakeVerifier()
    if mode == "google":
        return GoogleIntegrityVerifier(parse_play_packages(settings.play_packages),
                                       nonces or NonceStore(settings.integrity_nonce_ttl_s),
                                       max_age_s=settings.integrity_max_age_s,
                                       require_licensed=settings.integrity_require_licensed)
    raise ValueError(f"BRAIN_INTEGRITY_MODE phải là một trong {'|'.join(MODES)}")
