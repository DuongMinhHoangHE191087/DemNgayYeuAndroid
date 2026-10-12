#!/usr/bin/env bash
# Test tools/license_issue.py (2.1.0 SA2): K1–K8. Chạy: bash tools/tests/license_issue_test.sh (exit 0 = xanh). Khoá thử nằm trong thư mục tạm, xoá khi xong.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
LI="python3 $ROOT/tools/license_issue.py"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
total=0; bad=0
check() { total=$((total+1)); if [ "$2" = 0 ]; then echo "ok   $1"; else bad=$((bad+1)); echo "FAIL $1"; echo "${3:-}"; fi; }
CERT=41d06cb4629cf33920d966c51447b270ef78275f6cafb4a9caffa4f665e48bb2

$LI keygen --out "$ROOT/x.alpk" >/dev/null 2>&1; rc=$?
check "K1 keygen trong repo -> rc 2, không tạo file" "$([ $rc -eq 2 ] && [ ! -e "$ROOT/x.alpk" ]; echo $?)" "rc=$rc"
rm -f "$ROOT/x.alpk"

sig=$(cd "$ROOT/tools" && python3 -c 'import license_issue as l; print(l.sign(bytes.fromhex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"), b"").hex())' 2>&1)
want=e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b
check "K2 RFC 8032 §7.1 TEST 1: chữ ký khớp từng byte" "$([ "$sig" = "$want" ]; echo $?)" "$sig"

pub=$($LI keygen --out "$T/k.alpk" 2>/dev/null); rc=$?
mode=$(stat -f %Lp "$T/k.alpk" 2>/dev/null || stat -c %a "$T/k.alpk")
tok=$($LI issue --key "$T/k.alpk" --kid t1 --pkg test.sdk.zd --cert $CERT --days 30 --feat ads,brain 2>/dev/null)
$LI verify --pub "$pub" "$tok" >/dev/null 2>&1; vrc=$?
check "K3 keygen -> issue -> verify rc 0 (pub 64 hex, token ALP1.)" "$([ $rc -eq 0 ] && [ ${#pub} -eq 64 ] && [ $vrc -eq 0 ] && [ "${tok#ALP1.}" != "$tok" ]; echo $?)" "rc=$rc vrc=$vrc"

bad_tok=$(python3 - "$tok" <<'PY'
import sys,base64
h,p,s=sys.argv[1].split(".")
raw=bytearray(base64.urlsafe_b64decode(p+"="*(-len(p)%4))); raw[10]^=1
print(h+"."+base64.urlsafe_b64encode(bytes(raw)).rstrip(b"=").decode()+"."+s)
PY
)
$LI verify --pub "$pub" "$bad_tok" >/dev/null 2>&1; rc=$?
check "K4 verify token sửa payload -> rc 1" "$([ $rc -eq 1 ]; echo $?)" "rc=$rc"

$LI issue --key "$T/k.alpk" --kid t1 --pkg a.b --cert $CERT --days 0 >/dev/null 2>&1; rc0=$?
$LI keygen --out "$T/k.alpk" >/dev/null 2>&1; rce=$?
check "K5 --days 0 -> rc 2; keygen mode 600; file đã có -> rc 2" "$([ $rc0 -eq 2 ] && [ "$mode" = 600 ] && [ $rce -eq 2 ]; echo $?)" "days0=$rc0 mode=$mode exist=$rce"

$LI issue --key "$T/k.alpk" --kid t1 --pkg a.b --days 5 >/dev/null 2>&1; rc=$?
check "K6 thiếu --cert -> rc 2" "$([ $rc -eq 2 ]; echo $?)" "rc=$rc"

out=$({ $LI keygen --out "$T/k2.alpk"; $LI issue --key "$T/k2.alpk" --kid t1 --pkg a.b --cert $CERT --days 5; $LI issue --key "$T/none" --kid t1 --pkg a.b --cert $CERT --days 5; } 2>&1)
check "K7 stdout/stderr không bao giờ chứa ALPK1." "$(printf '%s' "$out" | grep -q 'ALPK1\.'; [ $? -eq 1 ]; echo $?)"

red=$(printf 'key ALPK1.AbC_d-123xyz end\n' | bash -c ". $ROOT/tools/lib/redact.sh; redact_stream")
check "K8 redact.sh che ALPK1.<...>" "$([ "$red" = "key <redacted-license-key> end" ]; echo $?)" "$red"

mut_last=$(python3 - "$tok" <<'PY'
import sys
t=sys.argv[1]; a="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
print(t[:-1]+a[a.index(t[-1])^1])
PY
)
$LI verify --pub "$pub" "$mut_last" >/dev/null 2>&1; rc=$?
check "K9 đổi ký tự cuối chữ ký (bit thừa) -> rc 1" "$([ $rc -eq 1 ]; echo $?)" "rc=$rc"
$LI verify --pub "$pub" "${tok}**" >/dev/null 2>&1; rc=$?
$LI verify --pub "$pub" "${tok}==" >/dev/null 2>&1; rceq=$?
check "K10 nối '**' hoặc '==' vào token -> rc 1" "$([ $rc -eq 1 ] && [ $rceq -eq 1 ]; echo $?)" "star=$rc eq=$rceq"
$LI verify --pub "$pub" --now 4102444800 "$tok" >/dev/null 2>&1; rc=$?
$LI verify --pub "$pub" --now 1 "$tok" >/dev/null 2>&1; rcok=$?
check "K11 verify --now quá hạn -> rc 3; chưa hạn -> rc 0" "$([ $rc -eq 3 ] && [ $rcok -eq 0 ]; echo $?)" "expired=$rc ok=$rcok"

echo "license_issue_test: $((total-bad))/$total"
[ $bad -eq 0 ]
