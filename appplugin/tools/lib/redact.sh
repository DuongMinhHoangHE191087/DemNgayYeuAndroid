# shellcheck shell=bash
# 1.9.0 Task 0c (plan-lint #40, lesson 1.8.0 E5-2): che bí mật trong log thiết bị NGAY LÚC CHỤP, trước khi ghi ra DOC/testing.
# Dùng: adb_b 30 logcat -d | redact_stream > file   (stdin -> stdout; không đổi số dòng, không đổi exit của lệnh đứng trước:
# đọc ${PIPESTATUS[0]}). Sáu mẫu cố định, mỗi mẫu thay đúng phần bí mật (không thay cả dòng):
#   eyJ… (JWT, Firebase Installations)      -> <redacted-fis-token>
#   AIza… (khoá API Google)                 -> <redacted-api-key>
#   ALPK1.… (khoá riêng giấy phép, 2.1.0 SA2) -> <redacted-license-key>
#   ya29.… (OAuth access token)             -> <redacted-oauth-token>
#   Bearer <token>                          -> Bearer <redacted-bearer>
#   Finsky "preferred account via installer for <pkg>: [<base64>]" -> [<redacted-account-hash>] (1.8.0 E5-3)
#   PEM (awk, một dòng một lượt, không đệm, không nhìn trước): chỉ dòng là KHOÁ mới bị che. Dòng BEGIN -> từ BEGIN tới hết dòng
#   thành <redacted-pem> và mở khối; trong khối chỉ dòng END hoặc dòng toàn ký tự base64 (không đặt độ dài tối thiểu: dòng ngắn cuối khối cũng che; X13)
#   bị che, DÒNG KHÁC kết thúc khối và in nguyên (FATAL/am_anr không bao giờ bị che). BEGIN..END cùng dòng: chỉ che đoạn đó;
#   END lẻ: chỉ che dấu END. Thân PEM trong logcat có tiền tố (ngày giờ, pid) nên không khớp base64 trần: chấp nhận, mẫu JWT/khoá
#   vẫn áp. Số dòng giữ nguyên theo cấu tạo.
# BSD sed/awk (macOS) và bash 3.2: chỉ dùng -E, không dùng \b hay \w.
redact_stream() {
  LC_ALL=C sed -E \
    -e 's/eyJ[A-Za-z0-9_-]{20,}(\.[A-Za-z0-9_-]+){0,2}/<redacted-fis-token>/g' \
    -e 's/AIza[0-9A-Za-z_-]{35}/<redacted-api-key>/g' \
    -e 's/ALPK1\.[A-Za-z0-9_-]+/<redacted-license-key>/g' \
    -e 's/ya29\.[0-9A-Za-z_-]+/<redacted-oauth-token>/g' \
    -e 's/(Bearer )[A-Za-z0-9._~+\/=-]+/\1<redacted-bearer>/g' \
    -e 's/(preferred account via installer for [^:]+: \[)[^]]*\]/\1<redacted-account-hash>]/g' |
  LC_ALL=C awk '
    inb { if ($0 ~ /-----END/ || $0 ~ /^[ \t]*[A-Za-z0-9+\/=]+[ \t]*$/) { print "<redacted-pem>"; if ($0 ~ /-----END/) inb = 0; next } inb = 0 }
    /-----BEGIN[^-]*-----.*-----END[^-]*-----/ { gsub(/-----BEGIN[^-]*-----.*-----END[^-]*-----/, "<redacted-pem>"); print; next }
    /-----BEGIN/ { sub(/-----BEGIN.*/, "<redacted-pem>"); inb = 1; print; next }
    /-----END/ { gsub(/-----END[^-]*(-----)?/, "<redacted-pem>") }
    { print }'
}
