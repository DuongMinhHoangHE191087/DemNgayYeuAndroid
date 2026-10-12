#!/usr/bin/env bash
# Xoa ban sao xung dot do trinh dong bo thu muc (iCloud/Dropbox) tao ra trong cay build.
#
# Trieu chung: build do voi mot trong cac loi sau, khong lien quan gi toi code vua sua:
#   - "Type ... is defined multiple times: .../X.class, .../X 2.class"
#   - Task :parseDebugLocalResources FAILED
#   - Task :mergeDebugResources FAILED
#
# Nguyen nhan: Gradle ghi song song nhieu file, trinh dong bo thay xung dot va tao ban sao
# ten "... 2.class" / thu muc "intermediates 2". D8/R8 thay hai lop cung ten va dung lai.
#
# Cach dut diem KHONG phai script nay ma la dua repo ra khoi thu muc duoc dong bo,
# hoac loai tru build/ khoi pham vi dong bo. Script chi don hau qua.
set -euo pipefail
cd "$(dirname "$0")/.."

n=$(find build app/build \( -type d -name "* [0-9]" -o -type f -name "* [0-9].*" \) 2>/dev/null | wc -l | tr -d ' ')
if [ "$n" = "0" ]; then
  echo "Khong co ban sao xung dot nao."
  exit 0
fi
echo "Tim thay $n muc. Dang xoa..."
find build app/build -type d -name "* [0-9]" -exec rm -rf {} + 2>/dev/null || true
find build app/build -type f \( -name "* [0-9].*" -o -name "* [0-9]" \) -delete 2>/dev/null || true
echo "Xong. Con lai: $(find build app/build \( -type d -name "* [0-9]" -o -type f -name "* [0-9].*" \) 2>/dev/null | wc -l | tr -d ' ')"
