"""Giới hạn tốc độ token bucket trong process (1.7.0 T9).

Hai tầng: theo `app_id` (lấy từ khoá) và theo `(app_id, user_id)` (từ body — chỉ là rào mềm,
client tự khai user_id). Mỗi request tốn 1 token ở mỗi bucket liên quan, không tính theo số
event: SDK gửi từng event một, và một lô hợp lệ không được thành 429 vĩnh viễn chỉ vì to.

Trong process nghĩa là mỗi instance một bảng: N instance Cloud Run = trần N lần. Đủ để chặn một
client chạy vòng lặp hỏng; không phải hạn ngạch toàn cục (cần kho chung — ngoài phạm vi).
"""

from __future__ import annotations

import ipaddress
import math
import threading
import time
from collections import OrderedDict
from typing import Callable

# Trần số bucket giữ trong bộ nhớ: user_id do client khai nên phải có trần, không thì body
# ngẫu nhiên làm phình RAM. Bucket bị đẩy ra quay về đầy — chỉ nới, không bao giờ chặn nhầm.
DEFAULT_MAX_KEYS = 100_000


class RateLimiter:
    def __init__(self, rate: float, burst: float, *, max_keys: int = DEFAULT_MAX_KEYS,
                 clock: Callable[[], float] = time.monotonic) -> None:
        if rate <= 0 or burst < 1:
            raise ValueError("rate phải > 0 và burst >= 1")
        self._rate = rate
        self._burst = burst
        self._max_keys = max_keys
        self._clock = clock
        self._buckets: OrderedDict[str, tuple[float, float]] = OrderedDict()
        self._lock = threading.Lock()

    def acquire(self, key: str) -> float:
        """Lấy 1 token. Trả 0.0 nếu được đi, ngược lại số giây phải chờ (> 0)."""
        now = self._clock()
        with self._lock:
            tokens, last = self._buckets.pop(key, (self._burst, now))
            tokens = min(self._burst, tokens + (now - last) * self._rate)
            if tokens >= 1.0:
                tokens -= 1.0
                wait = 0.0
            else:
                wait = (1.0 - tokens) / self._rate
            self._buckets[key] = (tokens, now)
            while len(self._buckets) > self._max_keys:
                self._buckets.popitem(last=False)
        return wait


def retry_after_seconds(wait: float) -> int:
    """Giá trị header `Retry-After`: giây nguyên, làm tròn lên, tối thiểu 1."""
    return max(1, math.ceil(wait - 1e-9))


class IngestLimiter:
    """Cặp limiter app + user cho `/push`."""

    def __init__(self, app_rps: float, app_burst: float, user_rps: float, user_burst: float) -> None:
        self.app = RateLimiter(app_rps, app_burst)
        self.user = RateLimiter(user_rps, user_burst)

    def check_app(self, app_id: str) -> float:
        return self.app.acquire(app_id)

    def check_users(self, app_id: str, user_ids: set[str]) -> float:
        """Chờ lâu nhất trong các user của lô; user_id rỗng không có bucket riêng."""
        waits = [self.user.acquire(f"{app_id}\x00{uid}") for uid in sorted(user_ids) if uid]
        return max(waits, default=0.0)


def _canon(raw: str) -> str | None:
    """IP chuẩn hoá làm khoá (IPv6 gộp theo /64, v4-mapped về v4); None nếu không phải IP. Không bao giờ trả chuỗi thô."""
    try:
        ip = ipaddress.ip_address(raw.strip())
        if ip.version == 4:
            return str(ip)
        return str(ip.ipv4_mapped or ipaddress.ip_network(f"{ip}/64", strict=False))
    except ValueError:
        return None


def client_bucket(peer: str, xff: str, hops: int) -> tuple[str, bool]:
    """(khoá client, hợp lệ). `hops` = 0: chỉ peer. Ngược lại hop thứ `hops` TÍNH TỪ PHẢI của `X-Forwarded-For`
    (proxy tin cậy nối thêm vào cuối); thiếu hop/không phải IP -> peer + False (hop bên trái do client khai, bỏ qua)."""
    fallback = _canon(peer) or peer
    if hops <= 0:
        return fallback, True
    parts = xff.rsplit(",", hops)
    key = _canon(parts[len(parts) - hops]) if len(parts) >= hops else None
    return (key, True) if key else (fallback, False)
