"""Khoá API theo app (1.7.0 T9).

- `BRAIN_API_KEYS="app_id:key,…"`: mỗi app một khoá; server gán cột `app_id` TỪ KHOÁ, không
  bao giờ từ body (`package_name` là lời client tự khai).
- Chỉ có `BRAIN_API_KEY=k`: một app `app_id=default` (tương thích `tools/local_sink.sh`).
- Có cả hai: `BRAIN_API_KEYS` thắng, ghi đúng một dòng log (không in khoá).
- Không khoá nào: chế độ dev như cũ (`app_id=dev`, `/health` báo `auth:false`).

So khớp: `hmac.compare_digest` trên digest SHA-256 của từng khoá, chạy qua MỌI khoá kể cả khi
đã khớp (không thoát sớm) — thời gian không lộ khoá nào khớp hay khớp tới đâu; băm trước để độ
dài chuỗi đưa vào so sánh luôn bằng nhau.
"""

from __future__ import annotations

import hashlib
import hmac
import logging
import re
from dataclasses import dataclass

log = logging.getLogger("brain.auth")

DEFAULT_APP_ID = "default"
DEV_APP_ID = "dev"
_APP_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


def _digest(value: str) -> bytes:
    return hashlib.sha256(value.encode("utf-8")).digest()


def parse_api_keys(api_keys: str, api_key: str) -> tuple[tuple[str, str], ...]:
    """(app_id, key) theo thứ tự khai báo. Sai dạng -> ValueError (không khởi động).

    Thông báo lỗi chỉ nêu SỐ THỨ TỰ mục, không bao giờ nêu nội dung (có thể chứa khoá).
    """
    if not api_keys.strip():
        return ((DEFAULT_APP_ID, api_key),) if api_key else ()
    pairs: list[tuple[str, str]] = []
    for idx, entry in enumerate(api_keys.split(","), start=1):
        entry = entry.strip()
        if not entry:
            continue
        app_id, sep, key = entry.partition(":")
        app_id, key = app_id.strip(), key.strip()
        if not sep or not key or not _APP_ID_RE.match(app_id):
            raise ValueError(f"BRAIN_API_KEYS mục {idx} sai dạng: cần app_id:key, "
                             "app_id chỉ gồm [A-Za-z0-9._-] (<= 64 ký tự)")
        pairs.append((app_id, key))
    if not pairs:
        raise ValueError("BRAIN_API_KEYS được đặt nhưng không có mục app_id:key nào")
    if len({a for a, _ in pairs}) != len(pairs):
        raise ValueError("BRAIN_API_KEYS có app_id trùng")
    if len({k for _, k in pairs}) != len(pairs):
        raise ValueError("BRAIN_API_KEYS có hai app dùng chung một khoá")
    return tuple(pairs)


@dataclass(frozen=True)
class ApiKeyAuth:
    """Bảng khoá bất biến; chỉ giữ digest, không giữ khoá gốc."""

    entries: tuple[tuple[str, bytes], ...]

    @classmethod
    def from_settings(cls, api_keys: str, api_key: str) -> "ApiKeyAuth":
        pairs = parse_api_keys(api_keys, api_key)
        if api_keys.strip() and api_key:
            log.warning("BRAIN_API_KEYS và BRAIN_API_KEY cùng được đặt: dùng BRAIN_API_KEYS "
                        "(%d app), bỏ qua BRAIN_API_KEY", len(pairs))
        return cls(tuple((app_id, _digest(key)) for app_id, key in pairs))

    @property
    def enabled(self) -> bool:
        return bool(self.entries)

    @property
    def app_count(self) -> int:
        return len(self.entries)

    def resolve(self, presented: str | None) -> str | None:
        """app_id của khoá khớp, hoặc None. Không bật khoá (dev) -> `DEV_APP_ID`."""
        if not self.entries:
            return DEV_APP_ID
        probe = _digest(presented or "")
        matched: str | None = None
        for app_id, digest in self.entries:
            # Không `break`: số lần so sánh luôn bằng số khoá.
            if hmac.compare_digest(probe, digest) and presented:
                matched = app_id
        return matched
