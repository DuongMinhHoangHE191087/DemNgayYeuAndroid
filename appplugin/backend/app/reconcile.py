"""Đối soát bucket thí nghiệm phía server — quyết định 2 mục B.2 doc 57.

Hàng `experiment_exposure` mang theo `holdout_bucket`, `is_holdout`, `in_rollout`,
`salt_version` — đó là *lời khai* của client về membership. Doc 57 B.2 không tin lời
khai: exposure hàng mang đủ dữ liệu để server TỰ suy lại bucket từ (salt_version,
user_id) bằng đúng hàm băm của SDK (`HoldoutManager.bucketWith`, FNV-1a 32-bit trên
chuỗi `"$namespace|$playerId"`), và "lệch bucket là biết log hỏng".

Ba lớp kiểm tra, lớp sau cần ít thông tin hơn lớp trước:

1. **Tái tính bucket** — fnv1a_bucket(salt, user_id) phải bằng `holdout_bucket` đã log.
   Lệnh một ô là hỏng dữ liệu tuyệt đối: hoặc SDK log sai, hoặc hàng bị đổi trên đường
   đi, hoặc ai đó sửa thuật toán băm mà không đổi muối (làm mọi chu kỳ đo trước đó
   không còn join được).
2. **Đơn điệu ngưỡng** — KHÔNG cần biết fraction. Với mỗi salt_version, cờ is_holdout
   phải là hàm bậc thang của bucket: tồn tại ngưỡng T sao cho bucket < T ⇒ cờ 1,
   bucket ≥ T ⇒ cờ 0. Một hàng cờ 1 nằm trên bucket của hàng cờ 0 là tự mâu thuẫn
   nội tại dù không biết fraction cấu hình là bao nhiêu. Rollout kiểm tra tương tự
   với bucket tái tính từ namespace "$salt|rollout" (SDK không log bucket này).
3. **Cờ chính xác** — chỉ khi người vận hành cấp `holdout_fraction`/`rollout_fraction`:
   đối chiếu từng cờ với `bucket < int(fraction * 10000)` (đúng phép toán Kotlin:
   `(fraction * 10000).toInt()` — IEEE 754 double, cắt về 0).

Hàm `compute()` thuần, chạy trên hàng của JsonlReader hay BigQueryReader như nhau —
cùng một pattern với `metrics.compute()`.
"""

from __future__ import annotations

from typing import Any, Iterable

BUCKET_COUNT = 10_000

# Số ví dụ tối đa mỗi loại trong báo cáo — đủ để điều tra, không phình payload.
_MAX_EXAMPLES = 5


def fnv1a_bucket(namespace: str, player_id: str) -> int:
    """Bản sao Python CHÍNH XÁC của `HoldoutManager.bucketWith` (Kotlin).

    FNV-1a 32-bit trên các code unit UTF-16 của `"$namespace|$playerId"`
    (Kotlin lặp `Char` — code unit, không phải code point; với ký tự ngoài BMP
    phải tách thành cặp surrogate để ra đúng kết quả), mod 10000.

    Đã khoá chéo ngôn ngữ: bộ vector trong `HoldoutParityVectorsTest` (Kotlin)
    và `test_reconcile.py` (Python) cùng trỏ vào một bộ hằng số.
    """
    h = 0x811C9DC5  # FNV offset basis 2166136261 (HoldoutManager viết -0x7ee3623b dạng Int có dấu)
    for ch in f"{namespace}|{player_id}":
        cp = ord(ch)
        if cp > 0xFFFF:
            units = (0xD800 + ((cp - 0x10000) >> 10), 0xDC00 + ((cp - 0x10000) & 0x3FF))
        else:
            units = (cp,)
        for u in units:
            h ^= u
            h = (h * 0x01000193) & 0xFFFFFFFF  # FNV prime 16777619; Kotlin Int tràn = mod 2^32
    return h % BUCKET_COUNT


def _threshold(bucket_flags: list[tuple[int, int]]) -> tuple[int | None, int]:
    """Ngưỡng ngầm định và số hàng vi phạm tính bậc thang, từ (bucket, cờ).

    Trả (implied_threshold, violations). Ngưỡng ngầm định = bucket cờ-1 cao nhất + 1
    (chỉ là cận dưới nếu chưa quan sát đủ bucket quanh ngưỡng); violations = số hàng
    cờ-1 nằm từ bucket cờ-0 thấp nhất trở lên. Dữ liệu sạch (cờ đúng là hàm ngưỡng của
    bucket) luôn cho violations = 0 — chứng minh được trong chính hàm này.
    """
    ones = [b for b, f in bucket_flags if f == 1]
    zeros = [b for b, f in bucket_flags if f == 0]
    if not ones:
        return (0 if zeros else None), 0
    implied = max(ones) + 1
    if not zeros:
        return implied, 0
    return implied, sum(1 for b in ones if b >= min(zeros))


def compute(
    rows: Iterable[dict[str, Any]],
    holdout_fraction: float | None = None,
    rollout_fraction: float | None = None,
) -> dict[str, Any]:
    """Đối soát toàn bộ hàng exposure. Xem docstring module cho ba lớp kiểm tra."""
    by_salt: dict[str, dict[str, Any]] = {}
    total = 0
    unusable = 0
    flag_mismatch_examples: list[dict[str, Any]] = []
    holdout_flag_mismatches = 0
    rollout_flag_mismatches = 0

    holdout_thr = None if holdout_fraction is None else int(holdout_fraction * BUCKET_COUNT)
    rollout_thr = None if rollout_fraction is None else int(rollout_fraction * BUCKET_COUNT)

    for r in rows:
        if r.get("event_name") != "experiment_exposure":
            continue
        total += 1
        uid = r.get("user_id") or ""
        salt = r.get("salt_version") or ""
        logged_bucket = r.get("holdout_bucket")
        if not uid or not salt or not isinstance(logged_bucket, int):
            unusable += 1  # không có (salt, user_id) thì không tái tính được — để riêng, không kết tội
            continue

        g = by_salt.setdefault(
            salt,
            {
                "rows": 0,
                "users": set(),
                "bucket_mismatch_examples": [],
                "holdout_flags": [],   # (bucket tái tính, is_holdout đã log)
                "rollout_flags": [],   # (bucket rollout tái tính, in_rollout đã log)
            },
        )
        g["rows"] += 1
        g["users"].add(uid)

        recomputed = fnv1a_bucket(salt, uid)
        mismatches = 0  # mỗi salt giữ riêng qua ví dụ; đếm tổng bên dưới
        if recomputed != logged_bucket:
            if len(g["bucket_mismatch_examples"]) < _MAX_EXAMPLES:
                g["bucket_mismatch_examples"].append(
                    {"user_id": uid, "logged_bucket": logged_bucket, "recomputed_bucket": recomputed}
                )
            g["bucket_mismatches"] = g.get("bucket_mismatches", 0) + 1

        is_holdout = int(r.get("is_holdout") or 0)
        in_rollout = int(r.get("in_rollout") or 0)
        g["holdout_flags"].append((recomputed, is_holdout))

        rollout_bucket = fnv1a_bucket(f"{salt}|rollout", uid)
        g["rollout_flags"].append((rollout_bucket, in_rollout))

        # Lớp 3 — chỉ chạy khi người vận hành cấp fraction.
        if holdout_thr is not None and is_holdout != (1 if recomputed < holdout_thr else 0):
            holdout_flag_mismatches += 1
            if len(flag_mismatch_examples) < _MAX_EXAMPLES:
                flag_mismatch_examples.append(
                    {"user_id": uid, "salt_version": salt, "field": "is_holdout",
                     "bucket": recomputed, "logged": is_holdout}
                )
        if rollout_thr is not None and in_rollout != (1 if rollout_bucket < rollout_thr else 0):
            rollout_flag_mismatches += 1
            if len(flag_mismatch_examples) < _MAX_EXAMPLES:
                flag_mismatch_examples.append(
                    {"user_id": uid, "salt_version": salt, "field": "in_rollout",
                     "bucket": rollout_bucket, "logged": in_rollout}
                )

    corrupt = 0
    inconsistent = 0
    for g in by_salt.values():
        g["users"] = len(g["users"])
        g.setdefault("bucket_mismatches", 0)
        corrupt += g["bucket_mismatches"]

        h_thr, h_viol = _threshold(g["holdout_flags"])
        r_thr, r_viol = _threshold(g["rollout_flags"])
        g["holdout"] = {
            "implied_threshold": h_thr,
            "implied_fraction": None if h_thr is None else h_thr / BUCKET_COUNT,
            "monotonicity_violations": h_viol,
        }
        g["rollout"] = {
            "implied_threshold": r_thr,
            "implied_fraction": None if r_thr is None else r_thr / BUCKET_COUNT,
            "monotonicity_violations": r_viol,
        }
        inconsistent += h_viol + r_viol
        # Trường tái tính và cờ: giữ cho debug, xoá cặp bucket-cờ thô cho gọn payload.
        del g["holdout_flags"], g["rollout_flags"]

    flag_checks = None
    if holdout_thr is not None or rollout_thr is not None:
        flag_checks = {
            "holdout_fraction": holdout_fraction,
            "rollout_fraction": rollout_fraction,
            "is_holdout_mismatches": holdout_flag_mismatches,
            "in_rollout_mismatches": rollout_flag_mismatches,
            "examples": flag_mismatch_examples,
        }
        inconsistent += holdout_flag_mismatches + rollout_flag_mismatches

    checkable = total - unusable
    if checkable == 0:
        verdict = "no_data"
    elif corrupt:
        verdict = "corruption_detected"
    elif inconsistent:
        verdict = "inconsistent"
    else:
        verdict = "clean"

    return {
        "total_exposure_rows": total,
        "checkable_rows": checkable,
        "rows_without_required_fields": unusable,
        "by_salt_version": by_salt,
        "flag_checks": flag_checks,
        "verdict": verdict,
    }
