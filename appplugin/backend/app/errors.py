"""Lỗi có cấu trúc + request id + log JSON một dòng mỗi request (1.7.0 T9).

Mọi lỗi (400/401/413/429/5xx…) có cùng một hình:
`{"ok": false, "error": {"code", "message"}, "request_id", "detail"}` — `detail` = `message`,
giữ cho client/test đời trước đọc `detail` của FastAPI. `X-Request-ID` có trên mọi response.

Log request: một dòng JSON (`severity`, `request_id`, `method`, `path`, `status`, `app_id`,
`accepted`, `rejected`, `latency_ms`) — Cloud Logging đọc thẳng. KHÔNG BAO GIỜ ghi header, body
hay khoá: chỉ các trường trên, lấy từ `request.state` do handler đặt.
"""

from __future__ import annotations

import json
import logging
import sys
import time
import uuid
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

request_log = logging.getLogger("brain.request")
_error_log = logging.getLogger("brain.errors")

_CODES = {
    400: "bad_request", 401: "unauthorized", 403: "forbidden", 404: "not_found",
    405: "method_not_allowed", 413: "payload_too_large", 422: "invalid_request",
    429: "rate_limited", 503: "unavailable",
}


class ApiError(Exception):
    """Lỗi có chủ đích, trả về client đúng status/code/message (message không chứa dữ liệu nhạy cảm)."""

    def __init__(self, status: int, code: str, message: str,
                 headers: dict[str, str] | None = None) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.headers = headers or {}


def request_id_of(request: Request) -> str:
    rid = getattr(request.state, "request_id", None)
    return rid or uuid.uuid4().hex


def error_response(request: Request, status: int, code: str, message: str,
                   headers: dict[str, str] | None = None) -> JSONResponse:
    rid = request_id_of(request)
    body = {"ok": False, "error": {"code": code, "message": message}, "request_id": rid,
            "detail": message}
    return JSONResponse(body, status_code=status, headers={**(headers or {}), "X-Request-ID": rid})


def _code_for(status: int) -> str:
    return _CODES.get(status, "internal" if status >= 500 else "error")


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _api_error(request: Request, exc: ApiError) -> JSONResponse:
        return error_response(request, exc.status, exc.code, exc.message, exc.headers)

    @app.exception_handler(StarletteHTTPException)
    async def _http_error(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        message = exc.detail if isinstance(exc.detail, str) else _code_for(exc.status_code)
        return error_response(request, exc.status_code, _code_for(exc.status_code), message,
                              dict(exc.headers or {}))

    @app.exception_handler(RequestValidationError)
    async def _validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
        # Chỉ vị trí lỗi, không lặp lại giá trị đầu vào.
        where = ", ".join(".".join(str(p) for p in e.get("loc", ())) for e in exc.errors())
        return error_response(request, 422, "invalid_request", f"tham số không hợp lệ: {where}")

    @app.exception_handler(Exception)
    async def _unhandled(request: Request, exc: Exception) -> JSONResponse:
        _error_log.error("loi khong xu ly request_id=%s: %s", request_id_of(request),
                         type(exc).__name__, exc_info=exc)
        return error_response(request, 500, "internal", "lỗi máy chủ, gửi lại sau")


def configure_request_logging() -> None:
    """Một handler stdout in nguyên message (đã là JSON). Idempotent qua các lần `create_app`."""
    if not any(getattr(h, "_brain_request", False) for h in request_log.handlers):
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter("%(message)s"))
        handler._brain_request = True  # type: ignore[attr-defined]
        request_log.addHandler(handler)
    request_log.setLevel(logging.INFO)


class RequestContextMiddleware:
    """ASGI thuần: gắn request id, thêm `X-Request-ID`, ghi một dòng log JSON khi xong."""

    def __init__(self, app: Any) -> None:
        self.app = app

    async def __call__(self, scope: dict, receive: Any, send: Any) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        state = scope.setdefault("state", {})
        rid = uuid.uuid4().hex
        state["request_id"] = rid
        started = time.perf_counter()
        status_holder = {"status": 500}

        async def send_wrapper(message: dict) -> None:
            if message["type"] == "http.response.start":
                status_holder["status"] = message["status"]
                headers = [(k, v) for k, v in message.get("headers", []) if k.lower() != b"x-request-id"]
                headers.append((b"x-request-id", rid.encode("ascii")))
                message = {**message, "headers": headers}
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            _log_request(scope, state, status_holder["status"], started)


def _log_request(scope: dict, state: dict, status: int, started: float) -> None:
    severity = "ERROR" if status >= 500 else "WARNING" if status >= 400 else "INFO"
    doc = {
        "severity": severity,
        "message": "request",
        "request_id": state.get("request_id"),
        "method": scope.get("method"),
        "path": scope.get("path"),
        "status": status,
        "app_id": state.get("app_id"),
        "accepted": state.get("accepted"),
        "rejected": state.get("rejected"),
        "latency_ms": round((time.perf_counter() - started) * 1000, 2),
    }
    level = logging.ERROR if status >= 500 else logging.WARNING if status >= 400 else logging.INFO
    request_log.log(level, json.dumps(doc, ensure_ascii=False))
