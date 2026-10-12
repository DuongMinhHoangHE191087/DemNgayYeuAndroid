#!/usr/bin/env python3
"""Load test `/push` (G13): tải mở (open-loop) N rps × S giây, in p50/p95/p99/max + lỗi.

    backend/.venv/bin/python backend/tools/loadtest.py --rps 50 --seconds 60 \
        --url http://127.0.0.1:8089/push   # khoá: env BRAIN_LOADTEST_KEY (mặc định khoá dev của compose)

Mỗi request một event v2 (`ad_show_attempt`) với `event_id` riêng; user xoay vòng qua `--users` người để
không chạm hạn mức theo user (20 rps). Chỉ nhắm localhost (stack cục bộ); lỗi = mã khác 2xx hoặc ngoại lệ.
Thoát 1 nếu có lỗi hoặc p95 > `--max-p95-ms` (khi đặt). Ngưỡng G13 (p95 < 200 ms) đo ở Task R (E-BE).
"""

from __future__ import annotations

import argparse
import asyncio
import math
import os
import sys
import time
import uuid
from collections import Counter
from urllib.parse import urlparse

import httpx

_LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1"}


def percentile(sorted_ms: list[float], p: float) -> float:
    """Nearest-rank trên danh sách đã sắp xếp."""
    if not sorted_ms:
        return float("nan")
    rank = max(1, math.ceil(p / 100 * len(sorted_ms)))
    return sorted_ms[rank - 1]


def event(i: int, users: int) -> dict:
    now = int(time.time() * 1000)
    return {"sequenceId": i, "eventName": "ad_show_attempt", "ts": now,
            "eventData": {"ad_format": "interstitial", "placement": "full_loadtest", "ready": 1,
                          "screen_name": "LoadTest"},
            "userProperty": {"user_id": f"lt{i % users}", "country": "US", "platform": "android"},
            "event_id": str(uuid.uuid4()), "schema_version": 2, "sdk_version": "loadtest",
            "package_name": "loadtest", "consent_tier": "GRANTED"}


async def _send(client: httpx.AsyncClient, url: str, key: str, body: dict, out: list) -> None:
    t0 = time.perf_counter()
    try:
        r = await client.post(url, json=body, headers={"X-API-KEY": key})
        out.append(((time.perf_counter() - t0) * 1000, str(r.status_code)))
    except httpx.HTTPError as exc:
        out.append(((time.perf_counter() - t0) * 1000, type(exc).__name__))


async def run(url: str, key: str, rps: float, seconds: float, users: int) -> list:
    results: list = []
    total = int(rps * seconds)
    async with httpx.AsyncClient(timeout=10.0) as client:
        start = time.perf_counter()
        tasks = []
        for i in range(total):
            delay = start + i / rps - time.perf_counter()
            if delay > 0:
                await asyncio.sleep(delay)
            tasks.append(asyncio.create_task(_send(client, url, key, event(i, users), results)))
        await asyncio.gather(*tasks)
    return results


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Load test /push (chỉ localhost)")
    ap.add_argument("--url", default="http://127.0.0.1:8089/push")
    ap.add_argument("--rps", type=float, default=50)
    ap.add_argument("--seconds", type=float, default=60)
    ap.add_argument("--users", type=int, default=500)
    ap.add_argument("--max-p95-ms", type=float, default=None)
    args = ap.parse_args(argv)
    if urlparse(args.url).hostname not in _LOCAL_HOSTS:
        ap.error("loadtest chỉ nhắm stack cục bộ (127.0.0.1/localhost)")
    key = os.getenv("BRAIN_LOADTEST_KEY", "local-dev-key")
    results = asyncio.run(run(args.url, key, args.rps, args.seconds, args.users))
    lat = sorted(ms for ms, _ in results)
    codes = Counter(code for _, code in results)
    errors = sum(n for code, n in codes.items() if not code.startswith("2"))
    p95 = percentile(lat, 95)
    print(f"loadtest: requests={len(results)} rps={args.rps:g} seconds={args.seconds:g} errors={errors} "
          f"codes={dict(sorted(codes.items()))}")
    print(f"loadtest: p50={percentile(lat, 50):.1f}ms p95={p95:.1f}ms p99={percentile(lat, 99):.1f}ms "
          f"max={lat[-1] if lat else float('nan'):.1f}ms")
    if errors or (args.max_p95_ms is not None and p95 > args.max_p95_ms):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
