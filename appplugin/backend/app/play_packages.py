"""`BRAIN_PLAY_PACKAGES` (2.2.0 SECB): app_id -> gói + chứng chỉ ký đã đăng ký cho `/policy` google mode.

Dạng: `app_id:pkg[|pkg2][:certhex,certhex];app_id2:…`. Cert khai bằng hex SHA-256 (như token giấy phép) và
được đổi sang base64url không padding — dạng Play Integrity trả ở `appIntegrity.certificateSha256Digest`
(`googleapis@144.0.0 playintegrity/v1.d.ts`). Lỗi chỉ nêu SỐ THỨ TỰ mục, không nêu nội dung.
"""

from __future__ import annotations

import base64
import re
from dataclasses import dataclass

from .auth import parse_api_keys

_APP_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
_PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
_SHA256_BYTES = 32


@dataclass(frozen=True)
class AppBinding:
    packages: tuple[str, ...]
    certs: frozenset[str]  # base64url không padding; rỗng = không ràng chứng chỉ


def b64url_nopad(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def parse_play_packages(spec: str) -> dict[str, AppBinding]:
    out: dict[str, AppBinding] = {}
    for idx, entry in enumerate((e.strip() for e in spec.split(";")), start=1):
        if not entry:
            continue
        parts = entry.split(":")
        if len(parts) not in (2, 3):
            raise ValueError(f"BRAIN_PLAY_PACKAGES mục {idx} sai dạng: app_id:pkg[|pkg2][:certhex,…]")
        app_id, packages = parts[0].strip(), tuple(p.strip() for p in parts[1].split("|"))
        certs = _parse_certs(parts[2], idx) if len(parts) == 3 else frozenset()
        if not _APP_ID_RE.match(app_id) or app_id in out or not all(_PACKAGE_RE.match(p) for p in packages):
            raise ValueError(f"BRAIN_PLAY_PACKAGES mục {idx} sai: app_id/gói không hợp lệ hoặc trùng")
        out[app_id] = AppBinding(packages, certs)
    return out


def _parse_certs(raw: str, idx: int) -> frozenset[str]:
    certs = set()
    for item in (c.strip().replace(":", "") for c in raw.split(",") if c.strip()):
        try:
            digest = bytes.fromhex(item)
        except ValueError:
            digest = b""
        if len(digest) != _SHA256_BYTES:
            raise ValueError(f"BRAIN_PLAY_PACKAGES mục {idx}: cert phải là SHA-256 hex (64 ký tự)")
        certs.add(b64url_nopad(digest))
    return frozenset(certs)


def check_google_bindings(spec: str, api_keys: str, api_key: str) -> None:
    """Google mode: mọi app có khoá phải có mục gói (thiếu = không khởi động)."""
    bindings = parse_play_packages(spec)
    if not bindings:
        raise ValueError("BRAIN_INTEGRITY_MODE=google cần BRAIN_PLAY_PACKAGES (app_id:pkg;…) khác rỗng")
    missing = [a for a, _ in parse_api_keys(api_keys, api_key) if a not in bindings]
    if missing:
        raise ValueError(f"BRAIN_PLAY_PACKAGES thiếu mục cho {len(missing)} app có khoá API")
