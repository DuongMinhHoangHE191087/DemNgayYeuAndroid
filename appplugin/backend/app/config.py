from __future__ import annotations

import math
import os
from dataclasses import dataclass, field

from .env_parse import env_bool, env_float as _env_float, env_int
from .play_packages import check_google_bindings

# Project duy nhất được phép đi với `BRAIN_BQ_ENDPOINT` (bigquery-emulator của stack cục bộ).
LOCAL_TEST_PROJECT = "local-test"
MAX_PROXY_HOPS = 5
# Bearer tĩnh của `/rtdn` (1.8.0 T13) ngắn hơn thế này = đoán được: không khởi động.
MIN_RTDN_TOKEN_LEN = 16


@dataclass(frozen=True)
class Settings:
    # Khớp header `X-API-KEY` mà LogEventServer gắn vào mọi request. Một khoá = app `default`.
    api_key: str = ""
    # `app_id:key,…` — khoá theo app; có thì thắng `api_key` (xem `auth.parse_api_keys`).
    api_keys: str = ""
    sink: str = "jsonl"          # jsonl | bigquery
    jsonl_path: str = "data/events.jsonl"
    bq_project: str = ""
    bq_dataset: str = "monetization_brain"
    # Bảng ĐỌC (sau MERGE). Sink ghi vào `bq_staging_table`; MERGE (T10) chuyển sang đây.
    bq_table: str = "events_raw"
    bq_staging_table: str = "events_staging"
    # Chỉ cho bigquery-emulator; bắt buộc đi cùng project `local-test` (xem `validate`).
    bq_endpoint: str = ""
    # Xác thực Play (doc 45 mục E.1): chỉ bật khi đặt ĐỦ cả hai — thiếu package mà có
    # credential thì URL androidpublisher không dựng được, bật nửa vời tệ hơn tắt hẳn.
    play_package: str = ""
    play_credentials: str = ""
    # Token bucket trong process [estimate]: app 200 rps / burst 400, user 20 rps / burst 60.
    rate_app_rps: float = 200.0
    rate_app_burst: float = 400.0
    rate_user_rps: float = 20.0
    rate_user_burst: float = 60.0
    # Git sha của bản đang chạy, `/health` trả về để biết đang nói chuyện với bản nào.
    version: str = "unknown"
    # Bearer tĩnh của `POST /rtdn` (1.8.0 T13, chỉ cục bộ/stack thử); rỗng = `/rtdn` trả 503. Production: Pub/Sub
    # push với token OIDC (H7b). `repr=False`: Settings có in ra log cũng không lộ.
    rtdn_token: str = field(default="", repr=False)
    integrity_mode: str = "off"  # `/policy` (2.1.0): off | fake | google
    policy_dir: str = ""  # `<policy_dir>/<app_id>.json` = policy đã đăng ký
    # 2.2.0 SECB: google mode `app_id:pkg[|pkg2][:certhex,…];…`, TTL nonce, tuổi verdict (s), bucket /policy theo app_id.
    play_packages: str = ""
    integrity_nonce_ttl_s: float = 120.0
    integrity_max_age_s: float = 300.0
    policy_rate_rps: float = 1.0
    policy_rate_burst: float = 10.0
    policy_app_rate_rps: float = 20.0
    policy_app_rate_burst: float = 200.0
    trust_proxy_hops: int = 0  # 0 = chỉ peer; N = hop thứ N từ phải của X-Forwarded-For (proxy tin cậy nối thêm)
    integrity_require_licensed: bool = True  # appLicensingVerdict phải LICENSED (BRAIN_INTEGRITY_REQUIRE_LICENSED=0 tắt)

    @classmethod
    def from_env(cls) -> "Settings":
        """Đọc env LÚC GỌI (không lúc import) rồi kiểm; cấu hình sai = không khởi động."""
        s = cls(
            api_key=os.getenv("BRAIN_API_KEY", ""),
            api_keys=os.getenv("BRAIN_API_KEYS", ""),
            sink=os.getenv("BRAIN_SINK", "jsonl"),
            jsonl_path=os.getenv("BRAIN_JSONL_PATH", "data/events.jsonl"),
            bq_project=os.getenv("BRAIN_BQ_PROJECT", ""),
            bq_dataset=os.getenv("BRAIN_BQ_DATASET", "monetization_brain"),
            bq_table=os.getenv("BRAIN_BQ_TABLE", "events_raw"),
            bq_staging_table=os.getenv("BRAIN_BQ_STAGING_TABLE", "events_staging"),
            bq_endpoint=os.getenv("BRAIN_BQ_ENDPOINT", "").strip(),
            play_package=os.getenv("BRAIN_PLAY_PACKAGE", ""),
            play_credentials=os.getenv("BRAIN_PLAY_CREDENTIALS", ""),
            rate_app_rps=_env_float("BRAIN_RATE_APP_RPS", 200.0, 0.001),
            rate_app_burst=_env_float("BRAIN_RATE_APP_BURST", 400.0, 1.0),
            rate_user_rps=_env_float("BRAIN_RATE_USER_RPS", 20.0, 0.001),
            rate_user_burst=_env_float("BRAIN_RATE_USER_BURST", 60.0, 1.0),
            version=os.getenv("BRAIN_GIT_SHA", "").strip() or "unknown",
            rtdn_token=os.getenv("BRAIN_RTDN_TOKEN", "").strip(),
            integrity_mode=os.getenv("BRAIN_INTEGRITY_MODE", "off").strip().lower() or "off",
            policy_dir=os.getenv("BRAIN_POLICY_DIR", "").strip(),
            play_packages=os.getenv("BRAIN_PLAY_PACKAGES", "").strip(),
            integrity_nonce_ttl_s=_env_float("BRAIN_INTEGRITY_NONCE_TTL_S", 120.0, 1.0),
            integrity_max_age_s=_env_float("BRAIN_INTEGRITY_MAX_AGE_S", 300.0, 1.0),
            policy_rate_rps=_env_float("BRAIN_POLICY_RATE_RPS", 1.0, 0.001),
            policy_rate_burst=_env_float("BRAIN_POLICY_RATE_BURST", 10.0, 1.0),
            policy_app_rate_rps=_env_float("BRAIN_POLICY_APP_RATE_RPS", 20.0, 0.001),
            policy_app_rate_burst=_env_float("BRAIN_POLICY_APP_RATE_BURST", 200.0, 1.0),
            trust_proxy_hops=env_int("BRAIN_TRUST_PROXY_HOPS", 0, 0, MAX_PROXY_HOPS),
            integrity_require_licensed=env_bool("BRAIN_INTEGRITY_REQUIRE_LICENSED", True),
        )
        s.validate()
        return s

    def validate(self) -> None:
        """Chặn cấu hình có thể trỏ nhầm sang BigQuery thật.

        `BRAIN_BQ_ENDPOINT` + credential ẩn danh chỉ có nghĩa với emulator; đi với project khác
        `local-test` thì hoặc là gõ nhầm, hoặc là đang đẩy dữ liệu thử vào một project thật.
        Ngược lại, `local-test` KHÔNG có endpoint sẽ gọi API BigQuery thật bằng credential của
        máy — cũng từ chối.
        """
        if self.integrity_mode not in ("off", "fake", "google"):
            raise ValueError("BRAIN_INTEGRITY_MODE phải là off|fake|google")
        if self.integrity_mode == "google":
            check_google_bindings(self.play_packages, self.api_keys, self.api_key)
        nums = (self.integrity_nonce_ttl_s, self.integrity_max_age_s, self.policy_rate_rps, self.policy_rate_burst,
                self.policy_app_rate_rps, self.policy_app_rate_burst)
        if not all(math.isfinite(v) and v > 0 for v in nums) or min(self.policy_rate_burst, self.policy_app_rate_burst) < 1:
            raise ValueError("BRAIN_POLICY_*RATE_*/BRAIN_INTEGRITY_* phải là số hữu hạn > 0 (burst >= 1)")
        if not 0 <= self.trust_proxy_hops <= MAX_PROXY_HOPS:
            raise ValueError(f"BRAIN_TRUST_PROXY_HOPS phải trong 0..{MAX_PROXY_HOPS}")
        if self.integrity_mode == "fake" and self.sink != "jsonl" and self.bq_project != LOCAL_TEST_PROJECT:
            raise ValueError("BRAIN_INTEGRITY_MODE=fake chỉ dùng với BRAIN_SINK=jsonl hoặc project local-test")
        if self.rtdn_token and len(self.rtdn_token) < MIN_RTDN_TOKEN_LEN:
            raise ValueError(f"BRAIN_RTDN_TOKEN phải dài >= {MIN_RTDN_TOKEN_LEN} ký tự")
        if self.sink == "bigquery" and not self.bq_project.strip():
            # Project rỗng: client chỉ lỗi 404 ở lần gọi đầu — dừng ngay lúc khởi động thì rõ hơn.
            raise ValueError("BRAIN_SINK=bigquery cần BRAIN_BQ_PROJECT khác rỗng")
        if self.sink == "bigquery":
            self._validate_bq_identifiers()
        if self.bq_endpoint and self.bq_project != LOCAL_TEST_PROJECT:
            raise ValueError(
                f"BRAIN_BQ_ENDPOINT chỉ dùng với BRAIN_BQ_PROJECT={LOCAL_TEST_PROJECT} (emulator); "
                "bỏ BRAIN_BQ_ENDPOINT để dùng BigQuery thật")
        if (self.sink == "bigquery" and self.bq_project == LOCAL_TEST_PROJECT
                and not self.bq_endpoint):
            raise ValueError(
                f"BRAIN_BQ_PROJECT={LOCAL_TEST_PROJECT} cần BRAIN_BQ_ENDPOINT (emulator); "
                "không gọi BigQuery thật với project thử")

    def _validate_bq_identifiers(self) -> None:
        """Reader (`SELECT … FROM `{project}.{dataset}.{table}``) và sink ghép các tên này từ env: tên
        không hợp lệ = không khởi động, không bao giờ ghép vào SQL (R-FBE m2)."""
        from .bq_tables import InvalidIdentifier, require_identifier, require_project  # noqa: PLC0415

        checks = (("BRAIN_BQ_PROJECT", self.bq_project, require_project),
                  ("BRAIN_BQ_DATASET", self.bq_dataset, require_identifier),
                  ("BRAIN_BQ_TABLE", self.bq_table, require_identifier),
                  ("BRAIN_BQ_STAGING_TABLE", self.bq_staging_table, require_identifier))
        for env_name, value, check in checks:
            try:
                check(value)
            except InvalidIdentifier as exc:
                raise ValueError(f"{env_name} không hợp lệ: {exc}") from None

    def fx_rates(self) -> dict[str, float]:
        """Tỉ giá cho lớp phân tích, dạng `EUR=1.08,GBP=1.27`.

        Cùng nguyên tắc với `FxRates` phía SDK: chỉ USD là hằng số, mọi mã khác phải nạp vào.
        Không nạp thì doanh thu ở mã đó được báo cáo riêng trong `unconverted`, KHÔNG bị bỏ
        qua âm thầm — bỏ qua sẽ làm ARPDAU thấp giả tạo đúng ở thị trường chưa có tỉ giá.
        """
        raw = os.getenv("BRAIN_FX_RATES", "")
        out: dict[str, float] = {}
        for pair in raw.split(","):
            if "=" not in pair:
                continue
            code, _, value = pair.partition("=")
            try:
                rate = float(value)
            except ValueError:
                continue
            if rate > 0:
                out[code.strip().upper()] = rate
        return out

    def require_auth(self) -> bool:
        """Không đặt khoá nào = chạy chế độ phát triển, không kiểm tra.

        Cố ý KHÔNG sinh một khoá ngẫu nhiên khi thiếu cấu hình: khoá ngẫu nhiên đổi sau mỗi
        lần khởi động lại sẽ làm mọi client bị 401 và bản ghi dồn ứ trong hàng đợi của họ —
        một sự cố im lặng, khó truy. Mở hẳn trong dev và bắt buộc đặt khoá khi deploy là
        ranh giới rõ ràng hơn.
        """
        return bool(self.api_keys.strip() or self.api_key)


def build_bq_client(settings: Settings):
    """Một client BigQuery cho cả sink, reader và `/health` (import lười).

    Emulator: `ClientOptions(api_endpoint)` + `AnonymousCredentials`, chỉ khi `validate` đã
    xác nhận project là `local-test`. Còn lại: credential mặc định của môi trường chạy.
    """
    from google.cloud import bigquery  # noqa: PLC0415

    settings.validate()
    if settings.bq_endpoint:
        from google.api_core.client_options import ClientOptions  # noqa: PLC0415
        from google.auth.credentials import AnonymousCredentials  # noqa: PLC0415

        return bigquery.Client(
            project=settings.bq_project,
            client_options=ClientOptions(api_endpoint=settings.bq_endpoint),
            credentials=AnonymousCredentials(),
        )
    return bigquery.Client(project=settings.bq_project)


def build_reader(settings: Settings, bq_client=None):
    if settings.sink == "bigquery":
        from .readers import BigQueryReader

        return BigQueryReader(settings.bq_project, settings.bq_dataset, settings.bq_table,
                              client=bq_client)
    from .readers import JsonlReader

    return JsonlReader(settings.jsonl_path)


def build_verifier(settings: Settings):
    """Verifier Play Billing, hoặc None khi chưa cấu hình credential.

    None KHÔNG phải lỗi — dev và giai đoạn chưa deploy chạy hoàn toàn không cần nó
    (các job đối soát thuần dữ liệu vẫn chạy). Endpoint xác thực sẽ tự báo 503.
    """
    if not settings.play_credentials:
        return None
    from .play_verify import HttpPlayVerifier

    return HttpPlayVerifier(settings.play_package, settings.play_credentials)


def build_sink(settings: Settings, bq_client=None):
    if settings.sink == "bigquery":
        from .sinks.bigquery import BigQuerySink

        return BigQuerySink(bq_client, settings.bq_project, settings.bq_dataset,
                            settings.bq_staging_table)
    from .sinks.jsonl import JsonlSink

    return JsonlSink(settings.jsonl_path)
