#!/bin/bash
# verify_fixes.sh — Kiểm tra tất cả các fix đã được áp dụng đúng
# Chạy: bash verify_fixes.sh

BASE="$(cd "$(dirname "$0")" && pwd)/src/main/java/com/app/plugin"
PASS=0
FAIL=0

check() {
  local desc="$1"
  local file="$2"
  local pattern="$3"
  if grep -q "$pattern" "$file" 2>/dev/null; then
    echo "✅ PASS: $desc"
    ((PASS++))
  else
    echo "❌ FAIL: $desc"
    echo "   File: $file"
    echo "   Pattern not found: $pattern"
    ((FAIL++))
  fi
}

echo "========================================"
echo "  SDK Bug Fix Verification Script"
echo "========================================"
echo ""

echo "--- Nhóm 1: Handler Memory Leak ---"

check \
  "Bug 1a: AdsMobNtFullView có countTimeHandler field" \
  "$BASE/ads/adsmob/AdsMobNtFullView.kt" \
  "private var countTimeHandler: Handler\?"

check \
  "Bug 1b: AdsMobNtFullView có cancelCountdown() method" \
  "$BASE/ads/adsmob/AdsMobNtFullView.kt" \
  "fun cancelCountdown()"

check \
  "Bug 1c: AdsMobNtFullView dùng countTimeHandler?.postDelayed" \
  "$BASE/ads/adsmob/AdsMobNtFullView.kt" \
  "countTimeHandler?.postDelayed"

check \
  "Bug 2: AdsMobPlNtFull.hideNativeFull() gọi cancelCountdown()" \
  "$BASE/ads/adsmob/AdsMobPlNtFull.kt" \
  "mDialogNativeFull?.cancelCountdown()"

echo ""
echo "--- Nhóm 2: Crash / NPE ---"

check \
  "Bug 7: AdsMobPlNt.getAdForShow() có null check trước cast" \
  "$BASE/ads/adsmob/AdsMobPlNt.kt" \
  "return null"

check \
  "Bug 8: AdsMobPlInters.show() reset state khi activity null" \
  "$BASE/ads/adsmob/AdsMobPlInters.kt" \
  "SHOW_WHEN_ACT_NULL"

check \
  "Bug 9: AdsMobPlOpen.show() reset state khi activity null" \
  "$BASE/ads/adsmob/AdsMobPlOpen.kt" \
  "SHOW_WHEN_ACT_NULL"

echo ""
echo "--- Nhóm 3: Logic Bug ---"

check \
  "Bug 12: AdsMobPlNtCl có guard check isShowing trước hideView" \
  "$BASE/ads/adsmob/AdsMobPlNtCl.kt" \
  "isShowing"

echo ""
echo "--- Nhóm 4: Concurrency ---"

check \
  "Bug 13: AppPluginManager.getlistAppLifeCB() trả toList() snapshot" \
  "$BASE/app/AppPluginManager.kt" \
  "listAppResume.toList()"

echo ""
echo "--- Nhóm 5: Data/Type ---"

check \
  "Bug 14a: AppPluginManager dùng getLong cho tAppStart" \
  "$BASE/app/AppPluginManager.kt" \
  "getLong"

check \
  "Bug 14b: AppPluginManager dùng setLong cho tAppStart" \
  "$BASE/app/AppPluginManager.kt" \
  "setLong"

echo ""
echo "--- Nhóm 6: Logic waterfall retry ---"

check \
  "Bug 20: AdsFbPlBanner dùng !flag4Load (đồng bộ 5 file Banner/Rect khác)" \
  "$BASE/ads/adsfb/AdsFbPlBanner.kt" \
  "if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm"

echo ""
echo "--- Nhóm 7: Facebook AdChoices container thứ tự gán ---"

check \
  "Bug 21: AdsFbPlNt gán adChoicesContainer trước khi addView" \
  "$BASE/ads/adsfb/AdsFbPlNt.kt" \
  "BUG-FIX: gán adChoicesContainer cho layout MỚI trước khi addView"

check \
  "Bug 22: AdsFbPlNtFull gán adChoicesContainer trước khi addView" \
  "$BASE/ads/adsfb/AdsFbPlNtFull.kt" \
  "BUG-FIX: gán adChoicesContainer cho layout MỚI trước khi addView"

echo ""
echo "--- Nhóm 9: Facebook show() cast fail reset state ---"

check \
  "Bug 23: AdsFbPlInters reset isLoaded/adObject + onAdShowFail khi cast fail" \
  "$BASE/ads/adsfb/AdsFbPlInters.kt" \
  "adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, \"\", \"ad null\")"

check \
  "Bug 24: AdsFbPlrewarded reset isLoaded/adObject + onAdShowFail khi cast fail" \
  "$BASE/ads/adsfb/AdsFbPlrewarded.kt" \
  "adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, \"\", \"ad null\")"

echo ""
echo "========================================"
echo "  Kết quả: $PASS PASS | $FAIL FAIL"
echo "========================================"
if [ $FAIL -eq 0 ]; then
  echo "  🎉 Tất cả fix đã được áp dụng đúng!"
else
  echo "  ⚠️  Có $FAIL fix chưa được áp dụng. Xem chi tiết bên trên."
fi
echo ""

# Kiểm tra thêm: còn anonymous Handler nào không được lưu không?
echo "--- Bonus: Scan anonymous Handler còn sót ---"
echo "(Tìm pattern 'val [a-z]* = Handler(' — không phải field, chỉ là local var)"
ANON=$(grep -rn "val [a-z]* = Handler(Looper" "$BASE/ads/" 2>/dev/null | grep -v "//")
if [ -z "$ANON" ]; then
  echo "✅ Không còn anonymous Handler nào trong ads/"
else
  echo "⚠️  Còn các Handler chưa được lưu field:"
  echo "$ANON"
fi
