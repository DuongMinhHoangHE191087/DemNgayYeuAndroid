#!/usr/bin/env python3
"""Khẳng định từng hàng của §Hợp đồng B (G12, 1.8.0 Task 16): python3 tools/iap_check.py <script> --jsonl J --applog L [--meta M].

J = dòng JSONL sink của script; L = logcat của script (+ buffer crash/events); M = `khoá=số` từ tools/lib/iap_gate.sh (`fg1_lines`,
`denied_at`, `granted_at`, `show_from`, `first_tap`). Exit 0 = PASS, 1 = FAIL. Fail-closed: thiếu file/meta, JSONL 0 dòng, dòng hỏng, tên script lạ,
mọi ngoại lệ = FAIL. Chỉ dùng thư viện chuẩn.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Callable

PKG = "test.sdk.zd"
ALL_MASK, INTERRUPTIVE_MASK = 63, 23  # RemoveAdsMask.ALL (BIT_COUNT 6); INTERRUPTIVE = bit 0|1|2|4
IAM_LINE = "iap harness: in-app-messages call"
FIXTURE_TOKEN = "rtdn-fixture-sub-token"
# Mã `reason` THẬT của ad_show_fail khi remove-ads chặn (Constants.AdShowFailReason, R-17): interstitial phát
# SHOW_FULL_WHEN_REMOVE_ADS, rewarded/app open/native phát REMOVE_AD. Bảng §B/DOC/65 viết "removeads" chỉ là nhãn tắt.
SUPPRESS = ("remove_ad", "show_full_when_remove_ads")
# Placement của hai nút demo (Show Interstitial / Show Rewarded): hàng ad_show_* của lượt chạm được lọc theo placement, không theo
# ad_format (attempt ghi `interstitial`, fail ghi `fullscreen`), và không lẫn các ad_show_fail khởi động (full_intro_*, nt_*: not_contain_placement).
PL_FULL, PL_REWARDED = "full_default", "gift_default"
Row = dict[str, Any]
RE_ACK_FIRST = re.compile(r"acknowledge token=(\S+) first=true")


class CheckFail(Exception):
    """Đầu vào không đủ để kết luận: luôn FAIL."""


def load_rows(path: str) -> list[Row]:
    p = Path(path)
    if not p.is_file():
        raise CheckFail(f"thiếu JSONL {path}")
    rows: list[Row] = []
    for no, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
            raw = item.get("raw_event_data")
            extra = json.loads(raw) if isinstance(raw, str) and raw else {}
        except (ValueError, AttributeError) as exc:
            raise CheckFail(f"{path}:{no}: không parse được: {exc}") from exc
        if not isinstance(item.get("event_name"), str) or not isinstance(extra, dict):
            raise CheckFail(f"{path}:{no}: không phải hàng sink có event_name")
        rows.append({**item, **{k: v for k, v in extra.items() if item.get(k) in (None, "")}})
    if not rows:
        raise CheckFail(f"JSONL 0 dòng ({path})")
    return rows


def load_log(path: str) -> list[str]:
    p = Path(path)
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines() if p.is_file() else []
    if not lines:
        raise CheckFail(f"thiếu hoặc rỗng log app {path}")
    return lines


def load_meta(path: str | None) -> dict[str, int]:
    meta: dict[str, int] = {}
    if path and not Path(path).is_file():
        raise CheckFail(f"thiếu meta {path}")
    for line in (Path(path).read_text(encoding="utf-8").splitlines() if path else []):
        key, sep, val = line.partition("=")
        if line.strip() and (not sep or not val.strip().isdigit()):
            raise CheckFail(f"meta hỏng: '{line}'")
        if line.strip():
            meta[key.strip()] = int(val)
    return meta


class Ctx:
    def __init__(self, name: str, rows: list[Row], log: list[str], meta: dict[str, int]) -> None:
        self.name, self.rows, self.log, self.meta = name, rows, log, meta
        self.errors: list[str] = []

    def need(self, cond: bool, msg: str) -> None:
        if not cond:
            self.errors.append(msg)

    def ev(self, name: str, rows: list[Row] | None = None, **cond: Any) -> list[Row]:
        return [r for r in (self.rows if rows is None else rows)
                if r["event_name"] == name and all(r.get(k) == v for k, v in cond.items())]

    def has(self, text: str) -> bool:
        return any(text in ln for ln in self.log)
    def find(self, pattern: str) -> list[str]:
        return [ln for ln in self.log if re.search(pattern, ln)]

    def acks(self) -> int:
        return len(self.find(RE_ACK_FIRST.pattern))

    def meta_int(self, key: str) -> int:
        if key not in self.meta:
            raise CheckFail(f"thiếu meta '{key}' (script {self.name})")
        return self.meta[key]

    def purchases(self, token: str | None = None) -> list[Row]:
        p = self.ev("iap_purchase")
        return p if token is None else [r for r in p if r.get("purchase_token") == token]

    def crashes(self) -> int:
        java = sum(1 for i, ln in enumerate(self.log) if "FATAL EXCEPTION" in ln and any(f"Process: {PKG}" in x for x in self.log[i:i + 6]))
        return java + sum(1 for ln in self.log if f">>> {PKG} <<<" in ln or ("am_anr" in ln and PKG in ln))


def common(c: Ctx) -> None:
    for line in (f"iap harness: armed script={c.name}", f"iap harness: {c.name} done"):
        c.need(c.has(line), f"thiếu '{line}'")
    c.need(c.crashes() == 0, f"FATAL/native/am_anr = {c.crashes()} (cần 0)")


def bought(c: Ctx, token: str, phase: str) -> None:
    """Lượt mua thành công: iap_result success 1, ack 1, iap_purchase đúng 1 theo token (Z3/M1)."""
    c.need(len(c.ev("iap_result", outcome="success")) == 1, "iap_result success phải đúng 1")
    c.need(c.acks() == 1, f"ack (first=true) = {c.acks()} (cần 1)")
    buys = c.purchases(token)
    c.need(len(buys) == 1, f"iap_purchase token {token} = {len(buys)} dòng (cần đúng 1)")
    c.need(len(c.purchases()) == len(buys), f"iap_purchase token lạ: {[r.get('purchase_token') for r in c.purchases()]}")
    c.need(all(r.get("phase") == phase for r in buys), f"iap_purchase phase phải là {phase}")


def after_taps(c: Ctx) -> list[Row]:
    """Hàng sink SAU cú chạm show đầu tiên (meta `first_tap`; D32-2): ad_show_fail khởi động của demo xả muộn (consent UNKNOWN giữ hàng)
    có thể rơi giữa `show_from` và cú chạm đầu, đó không phải hàng sau grant. Thiếu `first_tap` (bằng chứng cũ) -> `show_from`."""
    return c.rows[c.meta["first_tap"] if "first_tap" in c.meta else c.meta_int("show_from"):]


def shows(c: Ctx, placement: str) -> tuple[list[Row], list[Row], list[Row]]:
    rows = [r for r in after_taps(c) if r.get("placement") == placement]
    return (c.ev("ad_show", rows), c.ev("ad_show_fail", rows), c.ev("ad_show_attempt", rows))


def check_buy_sub(c: Ctx) -> None:
    bought(c, "g12-buy_sub", "base")
    imp = c.ev("paywall_impression")
    c.need(len(imp) == 1 and len(c.ev("paywall_choice")) == 1, f"paywall_impression/choice phải đúng 1/1 (có {len(imp)}/{len(c.ev('paywall_choice'))})")
    sid = imp[0].get("paywall_session_id") if imp else ""
    c.need(bool(sid), "paywall_impression thiếu paywall_session_id")
    c.need(all(r.get("paywall_session_id") == sid for r in c.purchases()), "iap_purchase khác paywall_session_id của impression")
    c.need(any(f"mask={ALL_MASK}" in ln for ln in c.find(r"result success")), f"kết quả mua không có mask={ALL_MASK}")
    n_show = len(c.ev("ad_show", after_taps(c)))
    c.need(n_show == 0, f"ad_show sau grant = {n_show} (cần 0)")
    for pl in (PL_FULL, PL_REWARDED):
        fails = shows(c, pl)[1]
        c.need(len(fails) >= 1, f"{pl}: không có ad_show_fail sau grant (lượt show không tới SDK?)")
        c.need(all(r.get("reason") in SUPPRESS for r in fails), f"{pl}: ad_show_fail reason ngoài {SUPPRESS} (cần 100 % remove-ads): {[r.get('reason') for r in fails]}")


def check_buy_cheap(c: Ctx) -> None:
    bought(c, "g12-buy_cheap", "inapp")
    masks = [int(m) for ln in c.find(r"result success") for m in re.findall(r"mask=(\d+)", ln)]
    c.need(bool(masks) and all(m == INTERRUPTIVE_MASK for m in masks), f"mask INTERRUPTIVE phải == {INTERRUPTIVE_MASK}: {masks}")
    shown, failed, attempted = shows(c, PL_FULL)
    c.need(not shown and any(r.get("reason") in SUPPRESS for r in failed), "interstitial phải bị remove-ads (ad_show_fail), 0 ad_show")
    r_shown, r_failed, r_attempt = shows(c, PL_REWARDED)
    c.need(not any(r.get("reason") in SUPPRESS for r in r_failed), "rewarded gift_ bị remove-ads (mask quá rộng)")
    c.need(len(r_shown) + len(r_attempt) >= 1, "rewarded không có lượt show nào (ad_show/ad_show_attempt)")


def check_cancel(c: Ctx) -> None:
    c.need(len(c.ev("iap_result", outcome="cancel")) == 1, "iap_result cancel phải đúng 1")
    c.need(not c.ev("iap_result", outcome="success") and not c.find(r"result success") and c.acks() == 0, "có success/ack sau cancel (grant?)")
    c.need(not c.purchases(), f"iap_purchase = {len(c.purchases())} sau cancel (cần 0)")


def check_pending(c: Ctx) -> None:
    c.need(len(c.ev("iap_result", outcome="pending")) == 1, "iap_result pending phải đúng 1")
    bought(c, "g12-pending_then_paid", "base")
    c.need(bool(c.find(r"result pending active=\[\]")), "lúc PENDING: quyền lợi phải rỗng (active=[])")
    c.need(not c.ev("subscription_state", to_state="IN_GRACE_PERIOD"), "PENDING bị ánh xạ thành IN_GRACE_PERIOD")


def check_kill(c: Ctx) -> None:
    c.need(c.has("iap harness: restore_unacked done") and c.has("no-callback"), "thiếu 'restore_unacked done' (pha 2) hoặc 'launch … no-callback' (pha 1)")
    c.need(c.acks() == 1, f"ack (first=true) = {c.acks()} (cần 1)")
    n = len(c.purchases("g12-kill_mid_purchase"))
    c.need(n == 1, f"iap_purchase token g12-kill_mid_purchase = {n} (cần đúng 1)")
    c.need(len(c.purchases()) == n, "iap_purchase token lạ")
    restored_all(c)


def check_restore_owned(c: Ctx) -> None:
    restored_all(c)
    c.need(not c.find(r"\blaunch g12"), "restore đi qua luồng mua (có 'launch')")
    c.need(c.acks() == 0, "restore_owned ack lại giao dịch đã ack")
    c.need(len(c.purchases("g12-buy_sub")) == len(c.purchases()) <= 1, "iap_purchase mới: chỉ được 0-1 dòng cùng token buy_sub (D24)")


def check_reopen(c: Ctx) -> None:
    c.need(bool(c.find(rf"reopen step=.*mask={ALL_MASK} active=.*g12_sub")), "mask đã lưu không áp khi mở lại")
    c.need(bool(c.find(r"ent verify ms=\d+")), "thiếu dòng 'ent verify ms=<n>'")
    c.need(not c.ev("ad_show"), "có ad_show sau khi mở lại với mask ALL")


def check_store_error(c: Ctx) -> None:
    c.need(bool(c.find(r"queryOwned \S+ ok=false")) and bool(c.find(rf"restore owned=.*mask={ALL_MASK}")), "store không lỗi (ok=false) hoặc lỗi store làm mất quyền lợi (mask khác ALL)")
    c.need(not c.ev("subscription_state", to_state="LAPSED"), "lỗi store bị coi là LAPSED")


def check_revoke(c: Ctx) -> None:
    c.need(bool(c.find(r"restore owned=\[\] mask=0")), "revoke không về owned=[] mask=0")
    c.need(len(c.ev("subscription_state", to_state="LAPSED")) == 1, "subscription_state → LAPSED phải đúng 1")
    shown, failed, attempted = shows(c, PL_FULL)
    c.need(not any(r.get("reason") in SUPPRESS for r in c.ev("ad_show_fail", after_taps(c))), "quảng cáo vẫn bị remove-ads sau revoke")
    c.need(len(shown) + len(attempted) + len(failed) >= 1, "sau revoke không có lượt show interstitial nào")


def restored_all(c: Ctx) -> None:
    c.need(bool(c.find(rf"restore owned=.*g12_sub.*mask={ALL_MASK}")), "restore không cấp g12_sub/mask ALL")


def server_grace(c: Ctx) -> None:
    c.need(len(c.ev("subscription_state", to_state="IN_GRACE_PERIOD", source="server")) == 1,
           "subscription_state IN_GRACE_PERIOD source=server phải đúng 1")
    c.need(not (c.ev("subscription_state", to_state="LAPSED") or c.ev("subscription_state", to_state="EXPIRED")), "quyền lợi đổi (LAPSED/EXPIRED) khi chỉ có grace")
    n = len(c.purchases(FIXTURE_TOKEN))
    c.need(n == 1 and len(c.purchases()) == 1, f"iap_purchase token fixture = {n} (cần đúng 1)")


def iam_calls(c: Ctx) -> tuple[int, int]:
    """(foreground 1, foreground 2) — mốc `fg1_lines` là số dòng logcat lúc foreground 1 kết thúc."""
    cut = c.meta_int("fg1_lines")
    return (sum(1 for ln in c.log[:cut] if IAM_LINE in ln), sum(1 for ln in c.log[cut:] if IAM_LINE in ln))


def check_grace(c: Ctx) -> None:
    server_grace(c)
    fg1, fg2 = iam_calls(c)
    c.need(fg1 == 1, f"in-app-messages call ở foreground 1 = {fg1} (cần 1)")
    c.need(fg2 == 0, f"in-app-messages call ở foreground 2 (< 24 h) = {fg2} (cần 0)")


def check_messages_off(c: Ctx) -> None:
    server_grace(c)
    c.need(bool(c.find(r"lifecycle applied=true")), "cấu hình vòng đời in_app_messages.enabled=false không được áp")
    n = len(c.find(re.escape(IAM_LINE)))
    c.need(n == 0, f"in-app-messages call = {n} khi enabled=false (cần 0)")


def check_denied(c: Ctx) -> None:
    denied, granted = c.meta_int("denied_at"), c.meta_int("granted_at")
    c.need(0 <= denied <= granted <= len(c.rows), f"mốc DENIED/GRANTED vô lý: {denied}, {granted}, {len(c.rows)} dòng")
    window = c.rows[denied:granted]
    c.need(not window, f"{len(window)} dòng tới sink giữa DENIED và GRANTED (cần 0): {[r['event_name'] for r in window][:8]}")
    c.need(c.has("consent tier=DENIED") and c.has("consent tier=GRANTED"), "thiếu 'consent tier=DENIED' hoặc 'consent tier=GRANTED'")
    c.need(any(f"mask={ALL_MASK}" in ln for ln in c.find(r"result success")), "DENIED: quyền lợi/mask không được cấp")
    after = c.rows[granted:]
    c.need(len(c.purchases("g12-denied")) == 1 and len(c.ev("iap_purchase", after)) == 1, "iap_purchase phải tới sink đúng 1 lần, sau GRANTED")
    c.need(bool(c.ev("iap_result", after, outcome="success")), "iap_result success không được xả sau GRANTED")


CHECKS: dict[str, Callable[[Ctx], None]] = {
    "buy_sub": check_buy_sub, "buy_cheap": check_buy_cheap, "cancel": check_cancel, "pending_then_paid": check_pending,
    "kill_mid_purchase": check_kill, "restore_owned": check_restore_owned, "reopen": check_reopen,
    "restore_store_error": check_store_error, "revoke": check_revoke, "grace": check_grace, "denied": check_denied,
    "messages_off": check_messages_off,
}


def run(name: str, jsonl: str, applog: str, meta: str | None) -> list[str]:
    if name not in CHECKS:
        raise CheckFail(f"script lạ '{name}' (chỉ nhận {', '.join(CHECKS)})")
    c = Ctx(name, load_rows(jsonl), load_log(applog), load_meta(meta))
    common(c)
    CHECKS[name](c)
    return c.errors


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(prog="iap_check.py")
    ap.add_argument("script")
    ap.add_argument("--jsonl", required=True)
    ap.add_argument("--applog", required=True)
    ap.add_argument("--meta")
    try:
        args = ap.parse_args(argv)
        errors = run(args.script, args.jsonl, args.applog, args.meta)
    except SystemExit:  # argparse: đối số sai/thiếu
        return 1
    except Exception as exc:  # noqa: BLE001 — fail-closed: CheckFail và lỗi không lường trước đều là FAIL
        print(f"iap_check {argv[0] if argv else '?'}: FAIL: {exc if isinstance(exc, CheckFail) else f'lỗi nội bộ {type(exc).__name__}: {exc}'}")
        return 1
    if errors:
        print(f"iap_check {args.script}: FAIL")
        for e in errors:
            print(f"  - {e}")
        return 1
    print(f"iap_check {args.script}: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
