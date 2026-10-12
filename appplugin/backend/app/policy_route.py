"""Logic của `GET /policy` (2.1.0 BE2): policy theo app, chỉ trả khi verdict Integrity chấp nhận.

Mặc định đóng (fail-closed): thiếu token, mode `off`, verifier từ chối hay ném, file policy hỏng
-> artifact mặc định + `reason` cố định. `app_id` do route truyền vào, lấy từ KHOÁ (không từ query/body).
Không bao giờ log token (chỉ app_id + reason).
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from .errors import ApiError
from .integrity import DecodeFailed, IntegrityVerifier, build_integrity_verifier
from .nonce_store import NonceStore
from .ratelimit import RateLimiter, client_bucket

log = logging.getLogger("brain.policy")

DEFAULT_POLICY: dict[str, Any] = {"version": 0}


def install_policy_state(application: Any, settings: Any) -> None:
    """Trạng thái của `/policy*`: bucket theo (app_id, client) + trần theo app_id, kho nonce, verifier."""
    application.state.policy_limiter = RateLimiter(settings.policy_rate_rps, settings.policy_rate_burst)
    application.state.policy_app_limiter = RateLimiter(settings.policy_app_rate_rps, settings.policy_app_rate_burst)
    application.state.nonces = NonceStore(settings.integrity_nonce_ttl_s)
    application.state.integrity = build_integrity_verifier(settings, application.state.nonces)


def client_key(request: Any) -> str:
    """Khoá client: peer, hoặc hop thứ N từ phải của `X-Forwarded-For` khi `BRAIN_TRUST_PROXY_HOPS` = N > 0."""
    peer = request.client.host if request.client else "unknown"
    key, ok = client_bucket(peer, request.headers.get("x-forwarded-for", ""), request.app.state.settings.trust_proxy_hops)
    if not ok:
        log.info("policy client key fallback code=xff_invalid")  # mã cố định, không bao giờ ghi giá trị header
    return key


def issue_nonce(state: Any, app_id: str) -> dict[str, Any]:
    return {"nonce": state.nonces.issue(app_id), "expires_in_s": state.nonces.ttl_s}


def require_auth_on(state: Any) -> None:
    """Không có khoá API = không phục vụ `/policy*` (503), kể cả khi mode `fake`; không có app `dev`."""
    if not state.auth.enabled:
        raise ApiError(503, "auth_off", "policy cần BRAIN_API_KEYS")


def _artifact(app_id: str, source: str, reason: str, policy: dict[str, Any]) -> dict[str, Any]:
    return {"app_id": app_id, "source": source, "reason": reason, "policy": policy}


def _default(app_id: str, reason: str, detail: str | None = None) -> dict[str, Any]:
    """`reason` ra dây chỉ thuộc {integrity_off, no_token, not_accepted, no_policy, nonce_required}; `detail` chỉ vào log."""
    log.info("policy default app=%s reason=%s detail=%s", app_id, reason, detail or reason)
    return _artifact(app_id, "default", reason, dict(DEFAULT_POLICY))


def _load_registered(policy_dir: str, app_id: str) -> dict[str, Any] | None:
    """Policy đã đăng ký của app, hoặc None (không có/hỏng/không phải object). `app_id` đã qua regex
    của `auth`; vẫn chặn đường dẫn ra ngoài thư mục."""
    if not policy_dir:
        return None
    try:
        root = Path(policy_dir).resolve()
        path = (root / f"{app_id}.json").resolve()
        if path.parent != root or not path.is_file():
            return None
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 — symlink loop, quyền, JSON lồng sâu/hỏng: không bao giờ 500
        log.warning("policy load failed app=%s type=%s", app_id, type(exc).__name__)
        return None
    return data if isinstance(data, dict) else None


def resolve_policy(state: Any, app_id: str, token: str | None) -> dict[str, Any]:
    """`state` = `app.state` (`integrity`: verifier hoặc None; `settings.policy_dir`)."""
    verifier: IntegrityVerifier | None = state.integrity
    if verifier is None:
        return _default(app_id, "integrity_off")
    if not token:
        return _default(app_id, "no_token")
    try:
        verdict = verifier.verify(token, app_id)
        accepted, detail = bool(verdict.accepted), str(verdict.reason)
    except Exception as exc:  # noqa: BLE001 — fail-closed (kể cả trả về không phải Verdict); chỉ ghi tên lớp lỗi
        status = getattr(getattr(exc, "response", None), "status_code", None)  # chỉ mã HTTP, không bao giờ body
        return _default(app_id, "not_accepted", (exc.detail if isinstance(exc, DecodeFailed) else f"verifier_error:{type(exc).__name__}"
                        + (f":http_{status}" if isinstance(status, int) else "")))
    if not accepted:
        return _default(app_id, "nonce_required" if detail == "nonce_required" else "not_accepted", detail)
    registered = _load_registered(state.settings.policy_dir, app_id)
    if registered is None:
        return _default(app_id, "no_policy")
    return _artifact(app_id, "app", "ok", registered)
