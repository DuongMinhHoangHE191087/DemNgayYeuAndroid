"""1.7.0 F2-be2 (integration) — tắt container bigquery-emulator: `/health` trả 503 trong < 15 s (E-BE #16 concern 1).

Chỉ cần `BRAIN_IT_BQ_ENDPOINT` như mọi test integration (chạy dưới khoá `docker`). Tắt/bật emulator mặc định bằng
`docker compose -f backend/docker-compose.yml stop|start bq` (file compose cạnh thư mục test); ghi đè bằng
`BRAIN_IT_BQ_STOP_CMD` / `BRAIN_IT_BQ_START_CMD`. `finally` luôn bật lại `bq` và chờ sẵn sàng (volume `bqdata`
giữ dataset qua stop/start), nên thứ tự chạy trong phiên không quan trọng và bước sau phiên (`/health` 200,
`stack_load`, `report_daily`) vẫn có emulator (R-F2be2 I-1).
"""

from __future__ import annotations

import os
import shlex
import subprocess
import threading
import time
import urllib.request
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from conftest import IT_ENDPOINT_ENV

pytestmark = pytest.mark.integration

STOP_CMD_ENV = "BRAIN_IT_BQ_STOP_CMD"
START_CMD_ENV = "BRAIN_IT_BQ_START_CMD"
_COMPOSE = ["docker", "compose", "-f", str(Path(__file__).resolve().parents[1] / "docker-compose.yml")]
HEALTH_DOWN_LIMIT_S = 15.0
READY_LIMIT_S = 60.0


def _wait_ready(endpoint: str) -> None:
    t_end = time.monotonic() + READY_LIMIT_S
    while time.monotonic() < t_end:
        try:
            urllib.request.urlopen(f"{endpoint}/projects/local-test/datasets", timeout=2)
            return
        except Exception:  # noqa: BLE001 — chưa lên: thử lại
            time.sleep(1)
    pytest.fail(f"emulator không sẵn sàng lại sau {READY_LIMIT_S} s")


def test_health_503_trong_15s_khi_emulator_tat():
    endpoint = os.getenv(IT_ENDPOINT_ENV, "")
    if not endpoint:
        pytest.fail(f"cần {IT_ENDPOINT_ENV} (emulator chạy, khoá docker)")
    stop_cmd = shlex.split(os.getenv(STOP_CMD_ENV, "")) or [*_COMPOSE, "stop", "bq"]
    start_cmd = shlex.split(os.getenv(START_CMD_ENV, "")) or [*_COMPOSE, "start", "bq"]
    api = TestClient(create_app(Settings(sink="bigquery", bq_project="local-test", bq_endpoint=endpoint)),
                     raise_server_exceptions=False)
    assert api.get("/health").status_code == 200  # emulator đang chạy: sẵn sàng
    subprocess.run(stop_cmd, check=True, timeout=120)
    try:
        # Thread daemon: code cũ thử lại ~600 s — assert đỏ sau 15 s thay vì treo phiên test.
        box: list = []
        t0 = time.monotonic()
        th = threading.Thread(target=lambda: box.append(api.get("/health").status_code), daemon=True)
        th.start()
        th.join(HEALTH_DOWN_LIMIT_S)
        elapsed = time.monotonic() - t0
    finally:
        subprocess.run(start_cmd, check=True, timeout=120)  # test/bước sau trong phiên vẫn có emulator
        _wait_ready(endpoint)
    assert box == [503], f"/health không trả 503 trong {HEALTH_DOWN_LIMIT_S} s: {box} ({elapsed:.1f} s)"
    assert elapsed < HEALTH_DOWN_LIMIT_S
