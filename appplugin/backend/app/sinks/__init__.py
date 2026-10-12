"""Nơi ghi bản ghi xuống.

Ràng buộc số một (doc 46 mục C.1): `write()` chỉ được trả về khi dữ liệu đã BỀN. Trả về
sớm rồi xử lý bất đồng bộ là phá vỡ cơ chế của client — SDK sẽ xoá bản ghi khỏi hàng đợi
ngay khi thấy 2xx, và bản ghi đó mất vĩnh viễn.
"""

from __future__ import annotations

from typing import Any, Protocol


class Sink(Protocol):
    def write(self, rows: list[dict[str, Any]]) -> None:
        """Ghi bền. Ném exception nếu KHÔNG chắc chắn đã ghi được."""
        ...

    def ping(self) -> bool:
        """Sẵn sàng nhận ghi (cho `/health`). Không ném: lỗi = False."""
        ...

    def kind(self) -> str:
        ...

    def health(self) -> str:
        ...
