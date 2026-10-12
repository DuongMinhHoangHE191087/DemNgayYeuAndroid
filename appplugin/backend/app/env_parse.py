"""Đọc biến môi trường có kiểm (2.2.0 SECB, tách khỏi `config.py` để giữ trần 230 dòng)."""

from __future__ import annotations

import math
import os

_FALSE = ("0", "false", "no", "off")


def env_float(name: str, default: float, minimum: float) -> float:
    """Số hữu hạn >= minimum; `nan`/`inf` làm hỏng hạn mức/TTL (nan so sánh luôn sai) nên bị từ chối."""
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ValueError(f"{name} phải là số, nhận được {raw!r}") from exc
    if not math.isfinite(value):
        raise ValueError(f"{name} phải là số hữu hạn, nhận được {raw!r}")
    if value < minimum:
        raise ValueError(f"{name} phải >= {minimum}, nhận được {value}")
    return value


def env_bool(name: str, default: bool) -> bool:
    """`0|false|no|off` (không phân biệt hoa thường) = tắt; rỗng = mặc định; còn lại = bật."""
    raw = os.getenv(name, "").strip().lower()
    return default if not raw else raw not in _FALSE


def env_int(name: str, default: int, minimum: int, maximum: int) -> int:
    """Số nguyên trong [minimum, maximum]; chuỗi khác (kể cả `1.5`) = không khởi động."""
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} phải là số nguyên {minimum}..{maximum}, nhận được {raw!r}") from exc
    if not minimum <= value <= maximum:
        raise ValueError(f"{name} phải trong {minimum}..{maximum}, nhận được {value}")
    return value
