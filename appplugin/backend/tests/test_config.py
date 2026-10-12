"""1.8.0 T14 — hồi quy R-9: BRAIN_SINK=bigquery mà project rỗng phải dừng lúc khởi động, không đợi 404."""

from __future__ import annotations

import pytest

from app.config import Settings


def test_sink_bigquery_with_empty_project_raises_at_validate():
    with pytest.raises(ValueError, match="BRAIN_BQ_PROJECT"):
        Settings(sink="bigquery", bq_project="  ").validate()
