#!/usr/bin/env bash
# Test không cần máy của cổng thiết bị — vùng iap: --iap + iap_check.py (Z1-Z15).
# Chạy: bash tools/tests/dg_iap_test.sh (hoặc qua runner device_gate_allowlist_test.sh). exit 0 = xanh.
. "$(dirname "$0")/lib/dg_harness.sh" || exit 99

# ── 1.8.0 Task 16 (G12): `device_gate.sh --iap` + tools/iap_check.py (Z1–Z10) ─────────────────────────────────────────────────
# adb giả + curl giả cho 12 script của harness: tools/tests đặt fake_iap.py vào $TMP; mọi lệnh adb/curl của cổng đi qua shim
# ghi $ADB_CALLS rồi exec python. FAKE_IAP_FAULTS (danh sách phẩy) bơm lỗi: dup (2 iap_purchase cùng token), adshow (ad_show sau
# grant), denied_post (POST giữa DENIED và GRANTED), no_server (không có subscription_state server), iam2 / iam_none / msg_on
# (in-app-messages gọi sai foreground / không gọi / gọi khi enabled=false). FAKE_API=26: am start -W --activity-single-top treo.
FAKE_IAP=$TMP/fake_iap.py
cat > "$FAKE_IAP" <<'PYEOF'
import json, os, sys, time
from pathlib import Path

ST = Path(os.environ.get("FAKE_IAP_ST", "/nonexistent"))
FAULTS = set(x for x in os.environ.get("FAKE_IAP_FAULTS", "").split(",") if x)
JSONL = Path(os.environ.get("FAKE_JSONL") or "DOC/testing/local-sink/events.jsonl")
API = os.environ.get("FAKE_API", "34")
PFX = "09-30 12:00:00.000  4242  4242 I AppPlugin: "
FIXTURE = "rtdn-fixture-sub-token"
IAM = "iap harness: in-app-messages call"
LABELS = [("Show Interstitial", 550), ("Show Rewarded", 750), ("Show Rewarded Interstitial", 1550), ("Do not consent", 950), ("Privacy options", 1150), ("Consent", 1350)]


def rd(name, default=""):
    p = ST / name
    return p.read_text() if p.exists() else default


def wr(name, text):
    ST.mkdir(parents=True, exist_ok=True)
    (ST / name).write_text(text)


def app(name, text):
    ST.mkdir(parents=True, exist_ok=True)
    with open(ST / name, "a") as f:
        f.write(text)


def log(line):
    app("log", PFX + line + "\n")


def jsonl():
    """Sink hiện tại: stub local_sink.sh `start --dir D` đổi sang D/events.jsonl (script 12 chạy trên sink mới)."""
    p = ST / "jsonl_path"
    return Path(p.read_text().strip()) if p.exists() else JSONL


def put(r):
    J = jsonl()
    J.parent.mkdir(parents=True, exist_ok=True)
    with open(J, "a") as f:
        f.write(json.dumps(r) + "\n")


def row(ev, direct=False, **kw):
    n = int(rd("seq", "0")) + 1
    wr("seq", str(n))
    r = {"event_name": ev, "event_id": "e%d" % n, "client_ts_ms": 1790000000000 + n}
    r.update(kw)
    if rd("script") == "denied" and rd("consent", "unknown") != "granted" and not direct:
        app("held", json.dumps(r) + "\n")
    else:
        put(r)


def jsonl_lines():
    return len(jsonl().read_text().splitlines()) if jsonl().exists() else 0


def buy(name, token, mask=63, phase="base", paywall=True, product="g12_sub"):
    sid = "ps-1" if paywall else ""
    if paywall:
        log("iap harness: %s step=1 paywall shown variant=g12_v1 decided=true" % name)
        row("paywall_impression", paywall_session_id=sid)
        row("paywall_choice", paywall_session_id=sid, product_id=product)
    log("iap harness: %s step=2 acknowledge token=%s first=true" % (name, token))
    row("iap_result", outcome="success", product_id=product, paywall_session_id=sid)
    if name == "buy_cheap" and "prerow" in FAULTS:  # T0e X2 (M19): hàng remove_ad của rwi TRƯỚC show_from (khởi động/lượt trước) không được tính
        row("ad_show_fail", ad_format="rewarded_interstitial", placement="rwi_default", reason="remove_ad")
    if name == "buy_sub" and "gmspersist" in FAULTS:  # D32-2: GMS persistent chết dưới áp lực bộ nhớ, kéo app chết; hàng lặp làm iap_check đỏ
        log("ActivityManager: Killing 3486:test.sdk.zd (adj 0): depends on provider com.google.android.gms/.fonts.provider.FontsProvider in dying proc com.google.android.gms.persistent")
        row("iap_purchase", purchase_token=token, phase=phase, paywall_session_id=sid, product_id=product)
    for _ in range(2 if "dup" in FAULTS else 1):
        row("iap_purchase", purchase_token=token, phase=phase, paywall_session_id=sid, product_id=product)
    if "foreign" in FAULTS:  # iap_purchase của token không thuộc script (R-16 m1)
        row("iap_purchase", purchase_token="other-token", phase=phase, paywall_session_id=sid, product_id=product)
    log("iap harness: %s step=3 result success product=%s mask=%d" % (name, product, mask))
    log("iap harness: %s done" % name)


def restore(name, owned, mask, token=None):
    if token:
        log("iap harness: %s step=2 acknowledge token=%s first=true" % (name, token))
    log("iap harness: %s step=3 restore owned=%s mask=%d" % (name, owned, mask))
    log("iap harness: %s done" % name)


def launch(name, eea=False):
    wr("script", name)
    wr("focus", "app")
    wr("fg", "0")
    wr("consent", "unknown" if name == "denied" else "granted")
    log("iap harness: armed script=%s" % name)
    row("session_start")
    if name == "buy_sub":
        buy(name, "g12-buy_sub")
    elif name == "buy_cheap":
        buy(name, "g12-buy_cheap", mask=23, phase="inapp", paywall=False, product="g12_lite")
    elif name == "cancel":
        row("iap_result", outcome="cancel", product_id="g12_sub")
        log("iap harness: %s step=2 result cancel" % name)
        log("iap harness: %s done" % name)
    elif name == "pending_then_paid":
        row("iap_result", outcome="pending", product_id="g12_sub")
        log("iap harness: %s step=2 result pending active=[]:grace=false" % name)
        buy(name, "g12-pending_then_paid", paywall=False)
    elif name == "kill_mid_purchase":
        log("iap harness: %s step=2 launch g12_sub no-callback" % name)
        log("iap harness: %s done" % name)
    elif name == "restore_unacked":
        restore(name, "['g12_sub']", 63, "g12-kill_mid_purchase")
        if "unavail" in FAULTS:  # D30: Play Billing tạm không có trên emulator, JSONL 0 hàng
            log("IapHelper unavailable: 3 (không thử lại trong process) Billing service unavailable on device")
        else:
            row("iap_purchase", purchase_token="g12-kill_mid_purchase", phase="base", product_id="g12_sub")
    elif name == "restore_owned":
        restore(name, "['g12_sub']", 63)
        row("iap_purchase", purchase_token="g12-buy_sub", phase="base", product_id="g12_sub")
    elif name == "reopen":
        log("iap harness: %s step=1 mask=63 active=[g12_sub]" % name)
        log("AppPlugin: ent verify ms=12 result=OK")
        log("iap harness: %s done" % name)
    elif name == "restore_store_error":
        log("iap harness: %s step=2 queryOwned SUBS ok=false" % name)
        restore(name, "['g12_sub']", 63)
    elif name == "revoke":
        restore(name, "[]", 0)
        row("subscription_state", product_id="g12_sub", from_state="ACTIVE", to_state="LAPSED", source="client")
    elif name == "grace":
        buy(name, FIXTURE)
    elif name == "messages_off":
        log("iap harness: %s step=1 lifecycle applied=true" % name)
        buy(name, FIXTURE)
    # denied: chờ chạm "Do not consent"


def foreground():
    n = int(rd("fg", "0")) + 1
    wr("fg", str(n))
    wr("focus", "app")
    name = rd("script")
    if name not in ("grace", "messages_off"):
        return
    if n == 1 and (ST / "rtdn").exists() and "no_server" not in FAULTS:
        row("subscription_state", product_id="g12_sub", from_state="ACTIVE", to_state="IN_GRACE_PERIOD", source="server")
    if (name == "grace" and ((n == 1 and "iam_none" not in FAULTS) or (n == 2 and "iam2" in FAULTS))) or \
            (name == "messages_off" and n == 1 and "msg_on" in FAULTS):
        log(IAM)


def shows(kind):
    name = rd("script")
    fmt = "fullscreen" if kind == "i" else "rewarded"  # AdEventLog.formatOfPlacement
    pl = "full_default" if kind == "i" else "gift_default"
    if name == "buy_sub":  # ad_show_fail khởi động của demo (placement khác, không phải remove-ads) không được làm kiểm tra đỏ
        row("ad_show_fail", ad_format="fullscreen", placement="full_intro_3", reason="not_contain_placement")
    if name == "buy_sub" and kind == "i" and "latestartup" in FAULTS:  # hàng ngoài remove-ads SAU cú chạm đầu
        row("ad_show_fail", ad_format="fullscreen", placement="full_default", reason="not_contain_placement")
    blocked = name == "buy_sub" or (name == "buy_cheap" and kind == "i")
    if blocked:  # AdsHelper.reportDisabledShow: bị chặn thì KHÔNG có `call show`
        log("ads helper %s show blocked: %s" % ("full_default" if kind == "i" else "gift_default",
                                                  "show_full_when_remove_ads" if kind == "i" else "remove_ad"))
    else:
        log("showInterstitial call show" if kind == "i" else "showRewarded call show")
    if name == "revoke" or (name == "buy_cheap" and kind == "r"):
        row("ad_show_attempt", ad_format=fmt, placement=pl)
        if name == "buy_cheap":
            row("ad_show", ad_format=fmt, placement=pl)
    elif "adshow" in FAULTS:
        row("ad_show", ad_format=fmt, placement=pl)
    else:
        row("ad_show_fail", ad_format=fmt, placement=pl, reason="show_full_when_remove_ads" if kind == "i" else "remove_ad")  # Constants.AdShowFailReason


def rwi_show():
    """2.0.0 Task 0c: buy_cheap chạm rwi; remove-ads chặn (reportDisabledShow, FLAG_FULLSCREEN) -> ad_show_fail reason=remove_ad. norwirow: thiếu hàng."""
    log("ads helper rwi_default show blocked: remove_ad")
    if "norwirow" not in FAULTS:
        row("ad_show_fail", ad_format="rewarded_interstitial", placement="rwi_default", reason="remove_ad")


def tap_label(label):
    if label == "Show Rewarded Interstitial":
        rwi_show()
    elif label == "Show Interstitial":
        shows("i")
    elif label == "Show Rewarded":
        shows("r")
    elif label == "Do not consent":
        wr("consent", "denied")
        log("iap harness: denied step=1 consent tier=DENIED")
        buy("denied", "g12-denied")
        if "denied_post" in FAULTS:
            wr("leak", "1")
    elif label == "Privacy options":
        log("AppPlugin: privacy options form shown")
    elif label == "Consent":
        wr("consent", "granted")
        log("AppPlugin: consent tier=GRANTED")
        for line in rd("held").splitlines():
            put(json.loads(line))
        wr("held", "")


def poll():
    if rd("leak") == "1":
        wr("leak", "0")
        row("consent_choice", direct=True, consent_tier="DENIED")


def xml():
    nodes = "".join('<node text="%s" bounds="[100,%d][900,%d]" />' % (t, y - 50, y + 50) for t, y in LABELS)
    return "<hierarchy>%s</hierarchy>" % nodes


def adb(a):
    if a[0] == "install":
        print("Success")
    elif a[0] == "logcat":
        if "-c" in a and "-b" not in a:
            wr("log", "")
        elif "-d" in a and "-b" not in a:
            poll()
            sys.stdout.write(rd("log"))
            if os.environ.get("FAKE_STRICT_FILE"):  # vi phạm StrictMode trong logcat của script (R-16 I1)
                sys.stdout.write(Path(os.environ["FAKE_STRICT_FILE"]).read_text())
    elif a[0] == "shell":
        s = a[1:]
        j = " ".join(s)
        if j.startswith("pm clear"):
            for f in ("script", "fg", "rtdn", "held", "leak", "consent"):
                wr(f, "")
            wr("consent", "granted")
            print("Success")
        elif j.startswith("am start"):
            if "appplugin_iap_script" in s:
                if API == "26" and "-W" in s and "--activity-single-top" in s:
                    time.sleep(3600)
                launch(s[s.index("appplugin_iap_script") + 1], "force_eea" in j)
                print("Status: ok\nTotalTime: 100\nWaitTime: 110")
            elif "-W" in s:
                wr("focus", "app")  # khởi chạy thử (guest ấm), không harness
                print("Status: ok\nTotalTime: 100\nWaitTime: 110")
            else:
                foreground()
        elif j == "input keyevent KEYCODE_HOME":
            wr("focus", "home")
        elif j.startswith("input tap"):
            y = int(s[3])
            tap_label(min(LABELS, key=lambda t: abs(t[1] - y))[0])
        elif j.startswith("wm size"):
            print("Physical size: 1080x2400")
        elif j.startswith("getprop"):
            print(API)
        elif j.startswith("dumpsys window"):
            if rd("focus", "app") == "app":
                print("  mCurrentFocus=Window{abc u0 test.sdk.zd/com.app.plugin.demo.MainActivity}")
            else:
                print("  mCurrentFocus=Window{abc u0 com.android.launcher/.Launcher}")
        elif j.startswith("cat /sdcard/ui.xml"):
            if "lateflush" in FAULTS and rd("script") == "buy_sub" and not (ST / "lf").exists():  # hàng khởi động xả giữa lúc ui_find dump (sau show_from, sau mốc chạm sớm, trước cú chạm thật)
                wr("lf", "1")
                row("ad_show_fail", ad_format="fullscreen", placement="full_default", reason="not_contain_placement")
            print(xml())
        elif j.startswith("pidof"):
            print("4242")
    return 0


def curl(a):
    out = a[a.index("-o") + 1] if "-o" in a else None
    url = [x for x in a if x.startswith("http")][0]
    if url.endswith("/rtdn"):
        wr("rtdn", "1")
        body = '{"ok": true, "stored": true, "kind": "subscription", "state": "GRACE"}'
    else:
        body = "{}"
    if out:
        Path(out).write_text(body)
    if "-w" in a:
        sys.stdout.write("200")
    return 0


def gen(name, out):
    launch(name, name == "denied")
    meta = {}
    if name == "denied":
        tap_label("Do not consent")
        meta["denied_at"] = jsonl_lines()
        poll()
        meta["granted_at"] = jsonl_lines()
        tap_label("Privacy options")
        tap_label("Consent")
    if name in ("buy_sub", "buy_cheap", "revoke"):
        meta["show_from"] = jsonl_lines()
        if "startupflush" in FAULTS:  # D32-2: hàng khởi động bị giữ (consent UNKNOWN) xả SAU show_from nhưng TRƯỚC cú chạm đầu
            row("ad_show_fail", ad_format="fullscreen", placement="full_default", reason="not_contain_placement")
        meta["first_tap"] = jsonl_lines()
        tap_label("Show Interstitial")
        tap_label("Show Rewarded")
    if name == "kill_mid_purchase":
        launch("restore_unacked")
    if name in ("grace", "messages_off"):
        wr("rtdn", "1")
        foreground()
        meta["fg1_lines"] = len(rd("log").splitlines())
        if name == "grace":
            foreground()
    jsonl().parent.mkdir(parents=True, exist_ok=True)
    jsonl().touch()
    (Path(out) / "app.log").write_text(rd("log"))
    (Path(out) / "app.meta").write_text("".join("%s=%d\n" % kv for kv in meta.items()))


if __name__ == "__main__":
    cmd, rest = sys.argv[1], sys.argv[2:]
    sys.exit({"adb": lambda: adb(rest), "curl": lambda: curl(rest), "gen": lambda: gen(rest[0], rest[1])}[cmd]() or 0)
PYEOF

# Bằng chứng giả cho một script (không qua cổng): $1 = script, $2 = thư mục; FAKE_IAP_FAULTS bơm lỗi.
gen_iap() { rm -rf "$2"; mkdir -p "$2"; FAKE_IAP_ST="$2/st" FAKE_JSONL="$2/events.jsonl" python3 "$FAKE_IAP" gen "$1" "$2"; }
chk_iap() { python3 "$ROOT/tools/iap_check.py" "$1" --jsonl "$2/events.jsonl" --applog "$2/app.log" --meta "$2/app.meta" 2>&1; }
# Cổng thật --iap trong cây tạm với adb/curl giả; gọi thẳng (không trong $(...)) để giữ GATE_ROOT.
prepare_iap_gate() {
  prepare_gate "$TMP/good.txt"
  cp "$ROOT/tools/lib/iap_gate.sh" "$GATE_ROOT/tools/lib/"; [ ! -f "$ROOT/tools/lib/invalid_classes.sh" ] || cp "$ROOT/tools/lib/invalid_classes.sh" "$GATE_ROOT/tools/lib/"; cp "$ROOT/tools/iap_check.py" "$GATE_ROOT/tools/"
  IAP_ST=$TMP/iap-st-$n_gate; rm -rf "$IAP_ST"; mkdir -p "$IAP_ST"
  cat > "$GATE_ROOT/tools/local_sink.sh" <<'SINKEOF'
#!/usr/bin/env bash
# sink giả của cổng --iap: status up; start --dir D đổi sink sang D/events.jsonl (script 12); ghi lệnh vào $ADB_CALLS.
echo "local_sink $*" >> "$ADB_CALLS"
case "$1" in
  status) echo "sink up"; exit 0;;
  start) while [ $# -gt 0 ]; do [ "$1" != --dir ] || { mkdir -p "$2"; echo "$2/events.jsonl" > "$FAKE_IAP_ST/jsonl_path"; }; shift; done;;
esac
exit 0
SINKEOF
  printf '#!/usr/bin/env bash\necho "$*" >> "$ADB_CALLS"\nexec python3 "$FAKE_IAP" adb "$@"\n' > "$TMP/bin/adb"
  printf '#!/usr/bin/env bash\necho "curl $*" >> "$ADB_CALLS"\nexec python3 "$FAKE_IAP" curl "$@"\n' > "$TMP/bin/curl"
  chmod +x "$TMP/bin/adb" "$TMP/bin/curl"; : > "$TMP/adb-calls"
}
run_iap() {  # đối số thêm (vd --mapping <m>) ghi đè mặc định
  ADB_CALLS=$TMP/adb-calls PATH="$TMP/bin:$PATH" ANDROID_SERIAL=stub-serial MG_NO_SLEEP=1 FAKE_IAP="$FAKE_IAP" FAKE_IAP_ST="$IAP_ST" \
    bash "$GATE_ROOT/tools/device_gate.sh" --iap --apk "$GATE_ROOT/apk/app-release.apk" --mapping "$GATE_ROOT/apk/mapping.txt" "$@" 2>&1
}
# restore_owned thêm hai lần là bước mồi (cùng cài đặt) trước restore_store_error và revoke.
IAP_ORDER="buy_sub buy_cheap cancel pending_then_paid kill_mid_purchase restore_unacked restore_owned reopen restore_owned restore_store_error restore_owned revoke grace denied messages_off"

prepare_iap_gate
z1=$(run_iap); z1rc=$?
z1calls=$(cat "$TMP/adb-calls")
z1seq=$(command grep -o 'appplugin_iap_script [a-z_]*' "$TMP/adb-calls" | awk '{print $2}' | tr '\n' ' ')
check "Z1 --iap chay du 12 script theo thu tu (restore_unacked sau kill_mid_purchase, buoc moi restore_owned, sink moi cho script 12), 12/12 PASS, RESULT PASS" \
  "$([ "$z1rc" -eq 0 ] && [ "$z1seq" = "$IAP_ORDER " ] && has "$z1" 'iap: 12/12 PASS; FAIL: không; invalid: không' && has "$z1" 'sink mới cho script 12' && has "$z1" 'RESULT: PASS'; echo $?)" "rc=$z1rc seq=$z1seq
$z1"

z2=$(python3 "$ROOT/tools/iap_check.py" buy_sub --jsonl "$TMP/khong-co.jsonl" --applog "$TMP/khong-co.log" 2>&1); z2rc=$?
gen_iap buy_sub "$TMP/z2"; rm -f "$TMP/z2/events.jsonl"; z2b=$(chk_iap buy_sub "$TMP/z2"); z2brc=$?
check "Z2 iap_check: thieu JSONL (file khong co, hoac ten script la) → FAIL, khong PASS" \
  "$([ "$z2rc" -eq 1 ] && [ "$z2brc" -eq 1 ] && has "$z2b" 'thiếu JSONL' && hasnt "$z2b" ': PASS'; echo $?)" "$z2
$z2b"

gen_iap buy_sub "$TMP/z3ok"; z3ok=$(chk_iap buy_sub "$TMP/z3ok"); z3okrc=$?
FAKE_IAP_FAULTS=dup gen_iap buy_sub "$TMP/z3"; z3=$(chk_iap buy_sub "$TMP/z3"); z3rc=$?
check "Z3 iap_purchase 2 dong cung token → FAIL (ban dung cung script PASS)" \
  "$([ "$z3okrc" -eq 0 ] && [ "$z3rc" -eq 1 ] && has "$z3" 'token g12-buy_sub = 2 dòng'; echo $?)" "ok: $z3ok
dup: $z3"

FAKE_IAP_FAULTS=adshow gen_iap buy_sub "$TMP/z4"; z4=$(chk_iap buy_sub "$TMP/z4"); z4rc=$?
check "Z4 ad_show sau grant → FAIL (ad_show phai 0, moi ad_show_fail la remove_ad/show_full_when_remove_ads)" \
  "$([ "$z4rc" -eq 1 ] && has "$z4" 'ad_show sau grant = 2'; echo $?)" "$z4"

gen_iap denied "$TMP/z5ok"; z5ok=$(chk_iap denied "$TMP/z5ok"); z5okrc=$?
FAKE_IAP_FAULTS=denied_post gen_iap denied "$TMP/z5"; z5=$(chk_iap denied "$TMP/z5"); z5rc=$?
check "Z5 POST trong khoang DENIED → FAIL (0 dong tu DENIED toi GRANTED; ban dung PASS, iap_purchase sau GRANTED)" \
  "$([ "$z5okrc" -eq 0 ] && [ "$z5rc" -eq 1 ] && has "$z5" 'dòng tới sink giữa DENIED và GRANTED'; echo $?)" "ok: $z5ok
leak: $z5"

gen_iap grace "$TMP/z6ok"; z6ok=$(chk_iap grace "$TMP/z6ok"); z6okrc=$?
FAKE_IAP_FAULTS=no_server gen_iap grace "$TMP/z6"; z6=$(chk_iap grace "$TMP/z6"); z6rc=$?
check "Z6 script 10 khong co subscription_state source=server → FAIL (ban dung PASS)" \
  "$([ "$z6okrc" -eq 0 ] && [ "$z6rc" -eq 1 ] && has "$z6" 'IN_GRACE_PERIOD source=server phải đúng 1'; echo $?)" "ok: $z6ok
$z6"

prepare_iap_gate
t0=$SECONDS; z7=$(FAKE_API=26 run_iap); z7rc=$?; z7s=$((SECONDS - t0))
check "Z7 FAKE_API=26: khong treo (am start -W --activity-single-top cua API 26 khong bi goi, RESULT PASS, khong 'adb_b timeout', < 100 s)" \
  "$([ "$z7rc" -eq 0 ] && has "$z7" 'RESULT: PASS' && hasnt "$z7" 'adb_b timeout' && [ "$z7s" -lt 100 ]; echo $?)" "rc=$z7rc s=$z7s
$z7"

# 1.9.0 Task 7: API 33 (AVD api33, cong 5590) di dung duong chung nhu 34, khong nhanh rieng cua API 26.
prepare_iap_gate
t0=$SECONDS; t7=$(FAKE_API=33 run_iap); t7rc=$?; t7s=$((SECONDS - t0))
check "T7 FAKE_API=33: cong nhan API 33 (RESULT PASS, rc 0, khong 'adb_b timeout', < 100 s)" \
  "$([ "$t7rc" -eq 0 ] && has "$t7" 'RESULT: PASS' && hasnt "$t7" 'adb_b timeout' && [ "$t7s" -lt 100 ]; echo $?)" "rc=$t7rc s=$t7s
$t7"

z8=$(command grep -nE '(^|[^_a-z])adb |"?\$\{?ADB\}?"? ' "$ROOT/tools/lib/iap_gate.sh" | command grep -v adb_b)
z8n=$(command grep -c 'adb_b ' "$ROOT/tools/lib/iap_gate.sh")
check "Z8 nguon: mot adb tran trong iap_gate.sh = 0 (moi lenh qua adb_b, co >= 10 lenh adb_b)" \
  "$([ -z "$z8" ] && [ "$z8n" -ge 10 ]; echo $?)" "bare=[$z8] adb_b lines=$z8n"

FAKE_IAP_FAULTS=iam2 gen_iap grace "$TMP/z9a"; z9a=$(chk_iap grace "$TMP/z9a"); z9arc=$?
FAKE_IAP_FAULTS=iam_none gen_iap grace "$TMP/z9b"; z9b=$(chk_iap grace "$TMP/z9b"); z9brc=$?
check "Z9 script 10: showInAppMessages 1 lan o foreground 1, 0 o foreground 2 (< 24 h); goi lan hai hoac khong goi → FAIL (ban dung PASS)" \
  "$([ "$z6okrc" -eq 0 ] && [ "$z9arc" -eq 1 ] && [ "$z9brc" -eq 1 ] && has "$z9a" 'foreground 2 (< 24 h) = 1' && has "$z9b" 'foreground 1 = 0'; echo $?)" "ok: $z6ok
iam2: $z9a
none: $z9b"

gen_iap messages_off "$TMP/z10ok"; z10ok=$(chk_iap messages_off "$TMP/z10ok"); z10okrc=$?
FAKE_IAP_FAULTS=msg_on gen_iap messages_off "$TMP/z10"; z10=$(chk_iap messages_off "$TMP/z10"); z10rc=$?
check "Z10 script 12: in_app_messages.enabled=false → 0 loi goi (1 loi goi → FAIL; ban dung PASS)" \
  "$([ "$z10okrc" -eq 0 ] && [ "$z10rc" -eq 1 ] && has "$z10" 'in-app-messages call = 1 khi enabled=false'; echo $?)" "ok: $z10ok
$z10"

# Z11 (R-16 I1): StrictMode by-sdk 0 mỗi script. Log có vi phạm mà khung đầu của app (sau retrace) là lớp SDK → mọi script FAIL;
# log chỉ có vi phạm của super.onCreate() và app demo (không do SDK) → PASS.
prepare_iap_gate
z11a=$(FAKE_STRICT_FILE="$STRICT_LOG" JAVA="$FAKE_JAVA" R8_JAR="$FAKE_R8_JAR" run_iap --mapping "$STRICT_MAP"); z11arc=$?
prepare_iap_gate
z11b=$(FAKE_STRICT_FILE="$STRICT_LOG_CLEAN" JAVA="$FAKE_JAVA" R8_JAR="$FAKE_R8_JAR" run_iap --mapping "$STRICT_MAP"); z11brc=$?
check "Z11 --iap: StrictMode by-sdk > 0 (frame com.app.plugin.* sau retrace) → FAIL moi script; khong do SDK → PASS 12/12" \
  "$([ "$z11arc" -eq 1 ] && has "$z11a" 'strictmode gate iap buy_sub: by sdk=1' && has "$z11a" 'iap: 0/12 PASS' && [ "$z11brc" -eq 0 ] && has "$z11b" 'iap: 12/12 PASS' && has "$z11b" 'by sdk=0'; echo $?)" "rc=$z11arc
$z11a
rc=$z11brc
$z11b"

# Z12 (1.8.0 TF-1): node-picker bỏ node bounds [0,0][0,0] (hierarchy UMP có node "Consent" ẩn đứng trước): chạm node hiển thị đầu
# tiên; chỉ có node bounds 0 → invalid (ui), không chạm (0,0). Lib nạp trực tiếp, adb_b giả trả dump từ $Z12_XML.
Z12_XML=$TMP/z12.xml; Z12_CALLS=$TMP/z12.calls
z12_run() { # $1 = nội dung dump, $2 = nhãn (mặc định Consent); in các lệnh input tap + exit của iap_tap_form
  printf '%s' "$1" > "$Z12_XML"; : > "$Z12_CALLS"
  ( . "$ROOT/tools/lib/adb_b.sh"; . "$ROOT/tools/lib/measure_gate.sh"; . "$ROOT/tools/lib/iap_gate.sh"
    adb_b() { shift; echo "$*" >> "$Z12_CALLS"; case "$*" in "shell cat /sdcard/ui.xml") cat "$Z12_XML";; esac; }
    mg_say() { echo "$*"; }; mg_sleep() { :; }
    IAP_DIR=$TMP/z12dir; mkdir -p "$IAP_DIR"
    iap_tap_form "${2:-Consent}"; echo "rc=$?" ) 2>&1
}
z12a=$(z12_run '<hierarchy><node text="Consent" bounds="[0,0][0,0]" /><node text="Consent" bounds="[100,1300][900,1400]" /></hierarchy>')
z12acalls=$(cat "$Z12_CALLS")
z12b=$(z12_run '<hierarchy><node text="Do not consent" bounds="[0,0][0,0]" /><node content-desc="Do not consent" bounds="[5,5][5,90]" /></hierarchy>' "Do not consent")
z12bcalls=$(cat "$Z12_CALLS")
check "Z12 iap_form_xy: node bounds 0 dau tien bi bo, tap tam node thu hai (500 1350); chi co node bounds 0 → invalid (ui), khong tap" \
  "$(has "$z12a" 'rc=0' && has "$z12acalls" 'shell input tap 500 1350' && has "$z12b" 'rc=4' && has "$z12b" 'invalid (ui)' && ! has "$z12bcalls" 'input tap'; echo $?)" "a: $z12a
$z12acalls
b: $z12b
$z12bcalls"

# Z15 (1.8.0 TF-2): API 28/26, form UMP chỉ có node bounds 0 → "Consent" chạm toạ độ tương đối (0.50 W, 0.70 H) của `wm size` 1080x2400 =
# `input tap 540 1680`, kiểm bằng logcat `consent tier=GRANTED` mới: có dòng → rc=0; không có → invalid (ui), không PASS giả.
Z15_CALLS=$TMP/z15.calls; Z15_GRANT=$TMP/z15.grant
z15_run() { # $1 = 1 nếu logcat giả có dòng GRANTED sau lần chạm
  z15g=$1; : > "$Z15_CALLS"; rm -f "$Z15_GRANT"
  ( . "$ROOT/tools/lib/adb_b.sh"; . "$ROOT/tools/lib/measure_gate.sh"; . "$ROOT/tools/lib/iap_gate.sh"
    adb_b() { shift; echo "$*" >> "$Z15_CALLS"; case "$*" in
      "shell cat /sdcard/ui.xml") echo '<hierarchy><node text="Consent" bounds="[0,0][0,0]" /><node text="Do not consent" bounds="[0,0][2,2]" /></hierarchy>';;
      "shell wm size") echo "Physical size: 1080x2400";;
      "shell input tap "*) [ "$z15g" != 1 ] || touch "$Z15_GRANT";;
      "logcat -d") [ ! -f "$Z15_GRANT" ] || echo "I AppPlugin: consent tier=GRANTED";; esac; }
    mg_say() { echo "$*"; }; mg_sleep() { :; }
    IAP_DIR=$TMP/z15dir; mkdir -p "$IAP_DIR"
    iap_tap_form "Consent"; echo "rc=$?" ) 2>&1
}
z15a=$(z15_run 1); z15acalls=$(cat "$Z15_CALLS")
z15b=$(z15_run 0); z15bcalls=$(cat "$Z15_CALLS")
check "Z15 iap_tap_form Consent: chi node bounds 0 + wm size 1080x2400 → input tap 540 1680 + log 'consent tap fallback 540,1680 (no visible node)'; co GRANTED → rc=0, khong co → rc=4 invalid (ui)" \
  "$(has "$z15a" 'rc=0' && has "$z15a" 'consent tap fallback 540,1680 (no visible node)' && has "$z15acalls" 'shell input tap 540 1680' && has "$z15b" 'rc=4' && has "$z15b" 'invalid (ui)' && has "$z15bcalls" 'shell input tap 540 1680'; echo $?)" "a: $z15a
$z15acalls
b: $z15b
$z15bcalls"

# Z13 (1.8.0 TF-1): precheck docker= chỉ đếm tiến trình của Docker Desktop (/Applications/Docker.app/Contents): một dòng lệnh khác
# chỉ chứa chữ "Docker.app" (vd shell của phiên) không tính. pgrep giả có bảng tiến trình, khớp -f bằng ERE như pgrep thật.
z13_pgrep() {
  cat > "$TMP/bin/pgrep" <<'PGEOF'
#!/usr/bin/env bash
[ "$1" = -f ] || exit 1
{ echo "bash -c cat ~/notes | grep Docker.app"; [ "${FAKE_DOCKER:-0}" != 1 ] || echo "/Applications/Docker.app/Contents/MacOS/com.docker.backend"; } | grep -E -- "$2" | sed 's/.*/4321/'
exit 0
PGEOF
  chmod +x "$TMP/bin/pgrep"
}
prepare_gate "$TMP/good.txt"; z13_pgrep
z13a=$(run_gate); z13arc=$?
prepare_gate "$TMP/good.txt"; z13_pgrep
z13b=$(FAKE_DOCKER=1 run_gate); z13brc=$?
z13src=$(command grep -c "pgrep -f '^/Applications/Docker.app/Contents/'" "$ROOT/tools/device_gate.sh" "$ROOT/tools/daily_session.sh" | tr '\n' ' ')
check "Z13 precheck: dong lenh chi chua 'Docker.app' → docker=0 (khong FAIL: Docker); Docker Desktop that → docker=1 va FAIL; nguon device_gate + daily_session dung mau /Applications/Docker.app/Contents" \
  "$(has "$z13a" 'precheck docker=0 sink=up' && ! has "$z13a" 'FAIL: Docker' && has "$z13b" 'precheck docker=1' && [ "$z13brc" -eq 1 ] && [ "$z13src" = "$ROOT/tools/device_gate.sh:1 $ROOT/tools/daily_session.sh:1 " ]; echo $?)" "a rc=$z13arc
$z13a
b rc=$z13brc
$z13b
src=$z13src"

# Z14 (1.8.0 TF-1): hai lượt --iap liền nhau cùng --sink-dir: mỗi lượt dựng sink ở thư mục riêng <sink-dir>/g12-<serial>-<giờ>,
# khác nhau, dựng trước script đầu tiên, nên khử trùng messageId/token của lượt trước không làm script 10 nhận stored:false.
Z14_SINK=$TMP/z14sink; rm -rf "$Z14_SINK"; mkdir -p "$Z14_SINK"
prepare_iap_gate
z14a=$(run_iap --sink-dir "$Z14_SINK"); z14arc=$?
z14acalls=$(cat "$TMP/adb-calls")
prepare_iap_gate
z14b=$(run_iap --sink-dir "$Z14_SINK"); z14brc=$?
z14bcalls=$(cat "$TMP/adb-calls")
z14d1=$(command grep -o "local_sink start --port 8089 --dir $Z14_SINK/g12-stub-serial-[^ ]*" <<< "$z14acalls" | head -1 | awk '{print $NF}')
z14d2=$(command grep -o "local_sink start --port 8089 --dir $Z14_SINK/g12-stub-serial-[^ ]*" <<< "$z14bcalls" | head -1 | awk '{print $NF}')
z14first=$(command grep -n -m1 'local_sink start\|appplugin_iap_script' <<< "$z14acalls" | head -1)
check "Z14 --iap 2 luot cung --sink-dir: 2 thu muc g12-<serial>-<gio> khac nhau duoi sink-dir, dung truoc script dau, script 10 co hang trong thu muc rieng, 12/12 PASS" \
  "$([ "$z14arc" -eq 0 ] && [ "$z14brc" -eq 0 ] && [ -n "$z14d1" ] && [ -n "$z14d2" ] && [ "$z14d1" != "$z14d2" ] && [ -s "$z14d1/events.jsonl" ] && [ -s "$z14d2/events.jsonl" ] \
    && has "$z14first" 'local_sink start' && has "$z14a" 'iap: 12/12 PASS'; echo $?)" "rc=$z14arc/$z14brc d1=$z14d1 d2=$z14d2 first=$z14first
$z14a"

# m1 (R-16): iap_purchase của token không thuộc script → FAIL (quy tắc "không token lạ").
FAKE_IAP_FAULTS=foreign gen_iap buy_sub "$TMP/m1"; m1=$(chk_iap buy_sub "$TMP/m1"); m1rc=$?
check "m1 iap_check: iap_purchase co token la (khong thuoc script) → FAIL" \
  "$([ "$m1rc" -eq 1 ] && has "$m1" 'token lạ'; echo $?)" "$m1"

# m2 (R-16): lich pm clear cua Z1: moi script pm clear truoc, TRU reopen (noi tiep restore_owned) va restore_unacked (cung cai dat
# voi kill_mid_purchase); restore_store_error/revoke: pm clear + buoc moi restore_owned; denied: pm clear cua guest am roi pm clear.
m2=$(command grep -E '^shell pm clear|appplugin_iap_script' <<< "$z1calls" | sed -E 's/^shell pm clear.*/C/; s/.*appplugin_iap_script ([a-z_]+).*/\1/' | tr '\n' ' ')
m2want="C buy_sub C buy_cheap C cancel C pending_then_paid C kill_mid_purchase restore_unacked C restore_owned reopen C restore_owned restore_store_error C restore_owned revoke C grace C C denied C messages_off "
check "m2 lich pm clear (Z1): khong pm clear truoc reopen va restore_unacked, co truoc moi script con lai (8/9 kem buoc moi)" \
  "$([ "$m2" = "$m2want" ]; echo $?)" "got:  $m2
want: $m2want"


# ── 2.0.0 Task 0c (D53): buy_cheap chạm "Show Rewarded Interstitial" -> ad_show_fail reason=remove_ad placement=rwi_default đúng 1 ──
rwi_taps=$(command grep -c '^shell input tap 500 1550$' <<< "$z1calls")
rwi_after=$(command grep -E '^shell input tap 500 (750|1550)$|appplugin_iap_script cancel' <<< "$z1calls" | sed -n '/appplugin_iap_script cancel/q;p' | tr '\n' ' ' | sed -E 's/shell input tap //g')
check "R5 buy_cheap chạm thêm Show Rewarded Interstitial (adb giả ghi toạ độ 500 1550, sau Rewarded 500 750), 1 lần cả lượt 12 script; hàng remove_ad rwi_default = 1; 12/12 PASS" \
  "$([ "$rwi_taps" -eq 1 ] && has "$z1" 'buy_cheap rwi: ad_show_fail reason=remove_ad placement=rwi_default = 1' && has "$z1" 'iap: 12/12 PASS' && has "$rwi_after" '500 750 500 1550'; echo $?)" "rwi taps=$rwi_taps after=$rwi_after
$z1"
prepare_iap_gate
r6=$(FAKE_IAP_FAULTS=norwirow run_iap); r6rc=$?
check "R6 buy_cheap: thiếu hàng ad_show_fail remove_ad của rwi_default -> FAIL nêu = 0, RESULT: FAIL, không 12/12 (bản đúng R5 PASS)" \
  "$([ "$r6rc" -eq 1 ] && has "$r6" 'FAIL: buy_cheap: ad_show_fail reason=remove_ad placement=rwi_default = 0' && has "$r6" 'RESULT: FAIL' && hasnt "$r6" 'iap: 12/12 PASS' && hasnt "$r6" 'RESULT: PASS'; echo $?)" "rc=$r6rc
$r6"

# ── 2.1.0 Task T0e (M19, R-T0c m1): phạm vi cửa sổ remove_ad. Đặc tả (xanh trên mã cũ); đột biến `from=0` (đếm cả file) đỏ. ──
prepare_iap_gate
x2=$(FAKE_IAP_FAULTS=prerow run_iap); x2rc=$?
check "T0e X2 hàng remove_ad rwi_default có TRƯỚC show_from (1 hàng) + 1 hàng sau -> vẫn đếm đúng 1, 12/12 PASS (đột biến đếm cả file = 2 -> FAIL)" \
  "$([ "$x2rc" -eq 0 ] && has "$x2" 'buy_cheap rwi: ad_show_fail reason=remove_ad placement=rwi_default = 1' && has "$x2" 'iap: 12/12 PASS'; echo $?)" "rc=$x2rc
$x2"

# ── 2.1.0 Task 0d (D30, D32-2, §E): lớp INVALID môi trường, cửa sổ iap từ cú chạm đầu (fixture dựng từ dòng thật của bằng chứng 2.0.0) ──
prepare_iap_gate
i1=$(FAKE_IAP_FAULTS=unavail run_iap); i1rc=$?
i1meta=$(cat "$GATE_ROOT"/DOC/testing/device-gate-stub-serial-*-iap/01-buy_sub.meta 2>/dev/null | tr '\n' ' ')
check "T0d I1 IapHelper unavailable: 3 trong script kill_mid_purchase -> INVALID (billing unavailable), rc 4, 11 script khác PASS, không 12/12 (cũ: FAIL rc 1)" \
  "$([ "$i1rc" -eq 4 ] && has "$i1" 'invalid (billing unavailable)' && has "$i1" 'RESULT: INVALID (billing unavailable)' && hasnt "$i1" 'RESULT: FAIL' && has "$i1" 'iap: 11/12 PASS; FAIL: không'; echo $?)" "rc=$i1rc
$i1"
prepare_iap_gate
i2=$(FAKE_IAP_FAULTS=gmspersist run_iap); i2rc=$?
check "T0d I2 'Killing 3486:test.sdk.zd ... dying proc com.google.android.gms.persistent' + buy_sub hỏng -> INVALID (gms persistent), rc 4, không FAIL (cũ: FAIL rc 1)" \
  "$([ "$i2rc" -eq 4 ] && has "$i2" 'invalid (gms persistent)' && has "$i2" 'RESULT: INVALID (gms persistent)' && hasnt "$i2" 'RESULT: FAIL'; echo $?)" "rc=$i2rc
$i2"
prepare_iap_gate
i3g=$(FAKE_IAP_FAULTS=lateflush run_iap); i3grc=$?
i3dir=$(ls -d "$GATE_ROOT"/DOC/testing/device-gate-stub-serial-*-iap | head -1)
i3m=$(cat "$i3dir/01-buy_sub.meta" 2>/dev/null | tr '\n' ' ')
FAKE_IAP_FAULTS=startupflush gen_iap buy_sub "$TMP/i3"; i3=$(chk_iap buy_sub "$TMP/i3"); i3rc=$?
i3from=$(sed -n 's/^show_from=//p' "$TMP/i3/app.meta"); i3tap=$(sed -n 's/^first_tap=//p' "$TMP/i3/app.meta")
check "T0d I3 hàng not_contain_placement xả giữa lúc ui_find (sau show_from, TRƯỚC cú chạm thật) -> first_tap ghi ở cú chạm thật, 12/12 PASS; checker: hàng xả sau show_from trước first_tap -> PASS (cũ: FAIL)" \
  "$([ "$i3grc" -eq 0 ] && has "$i3g" 'iap: 12/12 PASS' && has "$i3m" 'first_tap=' && [ "$i3rc" -eq 0 ] && [ "${i3tap:-0}" -gt "${i3from:-0}" ]; echo $?)" "rc=$i3grc meta: $i3m
$i3g
checker: $i3 from=$i3from tap=$i3tap"
FAKE_IAP_FAULTS=startupflush,latestartup gen_iap buy_sub "$TMP/i4"; i4=$(chk_iap buy_sub "$TMP/i4"); i4rc=$?
check "T0d I4 sau cú chạm đầu có ad_show_fail reason ngoài remove-ads -> FAIL (cửa sổ không rộng hơn first_tap)" \
  "$([ "$i4rc" -eq 1 ] && has "$i4" 'ad_show_fail reason ngoài'; echo $?)" "$i4"
i5n=""; for m in 01-buy_sub 02-buy_cheap 09-revoke; do i5n="$i5n $(command grep -c '^first_tap=' "$i3dir/$m.meta" 2>/dev/null)"; done
check "T0d I5 hook mg_tap_verified: first_tap ghi đúng 1 lần, chỉ ở cú chạm đầu (buy_sub 2 chạm, buy_cheap 3, revoke 2 -> mỗi meta đúng 1 dòng first_tap=)" \
  "$([ "$(echo $i5n)" = "1 1 1" ]; echo $?)" "first_tap counts:$i5n"

dg_finish
