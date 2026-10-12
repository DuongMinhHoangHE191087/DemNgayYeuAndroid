"""Bộ nhớ đệm trong tiến trình cho `signals` của `/push` (1.8.0 Task 13b, đóng R-13 m1).

Khoá `(app_id, user_id)`, sống `SIGNALS_CACHE_TTL_S` giây, tối đa `SIGNALS_CACHE_MAX_ENTRIES` mục (đầy thì bỏ mục
cũ nhất). Một `/rtdn` ghi cho user nào thì mục của user đó bị xoá để signal mới ra ở `/push` kế tiếp. Mỗi tiến trình
một bản: nhiều instance Cloud Run có thể chậm tối đa một TTL với RTDN nhận ở instance khác (RUNBOOK §5).
"""

from __future__ import annotations

import threading
import time
from collections import OrderedDict
from typing import Any

SIGNALS_CACHE_TTL_S = 60
SIGNALS_CACHE_MAX_ENTRIES = 10_000
_MISS = object()


class SignalsCache:
    def __init__(self) -> None:
        self._data: OrderedDict[tuple[str, str], tuple[float, list[dict[str, Any]]]] = OrderedDict()
        self._lock = threading.Lock()

    def get(self, app_id: str, user_id: str) -> Any:
        """Danh sách đã lưu (có thể `[]`) hoặc `MISS`."""
        key = (app_id, user_id)
        with self._lock:
            hit = self._data.get(key)
            if hit is None:
                return MISS
            if time.monotonic() - hit[0] >= SIGNALS_CACHE_TTL_S:
                del self._data[key]
                return MISS
            self._data.move_to_end(key)
            return list(hit[1])

    def put(self, app_id: str, user_id: str, signals: list[dict[str, Any]]) -> None:
        key = (app_id, user_id)
        with self._lock:
            self._data[key] = (time.monotonic(), list(signals))
            self._data.move_to_end(key)
            while len(self._data) > SIGNALS_CACHE_MAX_ENTRIES:
                self._data.popitem(last=False)

    def invalidate(self, app_id: str | None, user_id: str | None) -> None:
        if not app_id or not user_id:
            return
        with self._lock:
            self._data.pop((app_id, user_id), None)

    def __len__(self) -> int:
        return len(self._data)


MISS = _MISS
