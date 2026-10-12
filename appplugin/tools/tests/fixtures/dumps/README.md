Dump `dumpsys activity top` thật (APK utility v1.9.0, sha256 8ad41174047b..., mapping f9efc990...), chạy ngày 2026-10-01 (E-banners, Task R 1.9.0 #10).
Thiết bị: emulator API 37 (5570), 33 (5590), 28 (5580), 26 (5586) và Redmi 65c9b673 (file ghi tên thiết bị trong tên file); top1 = sau mở banner, top2 = sau HOME/resume.
Dùng cho B1-B5 của tools/tests/dg_banners_test.sh (bn_adview đếm đúng 1 AdView, tên 'i4' lấy từ mapping-adview.txt = dòng thật của mapping).
Che lúc chép: quét #40 và email = 0 trên cả 15 file; hai file Redmi bị thay nhãn ứng dụng của launcher `}(<nhãn>)` bằng `}(<app>)` (riêng tư của chủ máy; số dòng giữ nguyên).
Còn lại cố ý: tên gói `com.speaker.clean.wave` (app của chủ dự án, khối TASK khác task test, bị loại khỏi phép đếm).
FIX-GATE 2.4.0: device-gate-R5CX10P9EKZ-20261006-ng-banners-top1.txt = khối TASK test.sdk.zd cắt từ `dumpsys activity top` thật (Samsung A-series API 36, 3 nút, APK nextgen 2.4.0): InsetsSource tappableElement đáy [0,2205][1080,2340] + view banner Next-Gen ads_mobile_sdk.o53>h41>r31>zn3. Dùng cho N1 (dg_banners_test.sh) và NAV1 (dg_measure_test.sh).
