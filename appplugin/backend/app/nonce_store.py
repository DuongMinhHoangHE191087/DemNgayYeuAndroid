"""Kho nonce một-lần cho `/policy` (2.2.0 SECB): trong process, có trần, gắn `app_id`, hết hạn theo TTL.

Mỗi instance một kho: nonce cấp ở instance này không dùng được ở instance khác (giới hạn đã biết; Cloud Run
`--max-instances=1` hoặc kho chung là việc của 2.3.0). Nonce là 32 byte ngẫu nhiên url-safe; không bao giờ log.
"""

from __future__ import annotations

import secrets
import threading
import time
from collections import OrderedDict
from typing import Callable

DEFAULT_MAX_ENTRIES = 10_000


class NonceStore:
    def __init__(self, ttl_s: float, *, max_entries: int = DEFAULT_MAX_ENTRIES,
                 clock: Callable[[], float] = time.time) -> None:
        self._ttl = ttl_s
        self._max = max_entries
        self._clock = clock
        self._items: OrderedDict[str, tuple[str, float]] = OrderedDict()
        self._lock = threading.Lock()

    @property
    def ttl_s(self) -> float:
        return self._ttl

    def issue(self, app_id: str) -> str:
        nonce = secrets.token_urlsafe(32)
        now = self._clock()
        with self._lock:
            while self._items and next(iter(self._items.values()))[1] <= now:
                self._items.popitem(last=False)  # hạn tăng dần theo thứ tự cấp
            self._items[nonce] = (app_id, now + self._ttl)
            while len(self._items) > self._max:
                self._items.popitem(last=False)
        return nonce

    def consume(self, nonce: str, app_id: str) -> bool:
        """Đốt nonce (kể cả khi sai app/hết hạn: không cho thử lại); True chỉ khi đúng app, chưa hết hạn, chưa dùng."""
        with self._lock:
            owner, expires = self._items.pop(nonce, (None, 0.0))
        return owner == app_id and self._clock() < expires
