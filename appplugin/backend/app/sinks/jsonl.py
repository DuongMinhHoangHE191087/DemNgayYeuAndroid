"""Sink ghi file JSONL — dùng cho phát triển, test và làm lưới an toàn.

Không phải đồ chơi: đây cũng là đường lùi khi BigQuery lỗi. Thà ghi xuống đĩa local rồi
nạp lại sau còn hơn trả lỗi cho client và để bản ghi nằm trong hàng đợi của hàng triệu máy.
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
import threading
from pathlib import Path
from typing import Any

log = logging.getLogger("brain.sink.jsonl")


class JsonlSink:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def write(self, rows: list[dict[str, Any]]) -> None:
        if not rows:
            return
        payload = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
        with self._lock:
            with open(self.path, "a", encoding="utf-8") as f:
                f.write(payload)
                # flush + fsync là toàn bộ lý do sink này đáng tin. Thiếu fsync thì dữ liệu
                # nằm trong page cache của OS, và ta đã trả 2xx cho một thứ chưa chắc tồn tại
                # sau khi mất điện.
                f.flush()
                os.fsync(f.fileno())

    def ping(self) -> bool:
        """Sẵn sàng = ghi được thật: tạo rồi xoá một file tạm cạnh file log.

        `os.access` không đủ (FS chỉ đọc, đĩa đầy, quyền ACL) — thử ghi là cách duy nhất biết
        chắc `/push` kế tiếp sẽ không 5xx. File log đã có mà không ghi được cũng là chưa sẵn sàng.
        """
        try:
            with tempfile.NamedTemporaryFile(dir=self.path.parent, prefix=".health-"):
                pass
            if self.path.exists() and not os.access(self.path, os.W_OK):
                return False
        except OSError as exc:
            log.warning("jsonl ping that bai: %s", type(exc).__name__)
            return False
        return True

    def kind(self) -> str:
        return "jsonl"

    def health(self) -> str:
        return f"jsonl:{self.path}"
