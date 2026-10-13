# Cập nhật từ chủ dự án (vòng /loop, 2026-10-13) — áp dụng cho MỌI phần P1–P8

Chủ dự án yêu cầu thêm so với `_shared-context.md`. Đọc file này rồi áp dụng vào file kế hoạch của bạn (sửa/bổ sung phần đã viết nếu cần). Nếu mâu thuẫn với `_shared-context.md`, file này thắng.

## 1. Tự quyết định, không hỏi lại
- Lời chủ dự án: "nếu có phần nào cần hỏi ý kiến thì bạn tự nghiên cứu, tự đưa ra quyết định". Với MỌI lựa chọn kỹ thuật/sản phẩm: tự nghiên cứu (repo + web) và chốt. Ghi dạng:
  `DECIDED: <lựa chọn> — vì <bằng chứng> — loại bỏ: <phương án khác và lý do>`.
- Mục "Open questions" CHỈ chứa việc chỉ chủ dự án làm được (tài khoản, khoá/chứng chỉ, thao tác trên Apple/Firebase/AdMob console, pháp lý/thuế). Mỗi mục: làm gì, ở đâu, cần xong trước task nào. Không hỏi ý kiến về lựa chọn kỹ thuật.
- Tiêu chí chọn, theo thứ tự: (1) chạy mượt, ổn định; (2) an toàn, không bypass được gói đăng ký; (3) dễ mở rộng/bảo trì; (4) ít việc nhất để đạt (1)-(3). Hoà thì chọn phương án ít phụ thuộc bên thứ ba.
- `DECISION-CONFLICT:` với D1–D11 vẫn dùng được, nhưng phải kèm bằng chứng; master sẽ chốt.

## 2. Task nhỏ, thực thi độc lập
- Không task nào lớn hơn M (≤2 ngày). Task L phải tách thành M/S (id kiểu `U-12a`, `U-12b`).
- Mỗi dòng task tự đứng được để một subagent làm một mình: file sở hữu chính xác, thay đổi (≤3 câu), lệnh/điều kiện nghiệm thu mà THẤT BẠI nếu logic sai, phụ thuộc, model, host. Không viết "xem task trước"/"tương tự".
- Task logic: test viết trước (red) → code → xanh; ghi tên test cụ thể.

## 3. Chất lượng mã & khả năng mở rộng
- commonMain không biết platform; interface đặt ở commonMain; mỗi gateway có bản Fake cho test; không file/class "thần" (> ~600 dòng thì tách); quy ước đặt tên/vị trí cho code mới phải rõ để thêm tính năng sau này không phải sửa lõi.
- Không trừu tượng đầu cơ: chỉ tạo interface khi có ≥2 triển khai hoặc cần cho test/đa nền tảng. Mục bỏ qua ghi `skipped: X, add when Y`.

## 4. Chống bypass gói đăng ký & toàn vẹn (trọng tâm P3, P6; P2, P4, P7 chú ý)
- Quyền VIP chỉ do SERVER xác nhận (Apple App Store Server API + Server Notifications V2; Google Play Developer API/RTDN) rồi Cloud Function ghi vào Firestore; client KHÔNG được tự ghi trường quyền lợi (firestore.rules + test rules bằng emulator).
- Gắn giao dịch với tài khoản: `appAccountToken` ↔ Firebase uid; `originalTransactionId` chỉ gắn một uid; chống dùng chung biên lai. Xử lý hoàn tiền/thu hồi, grace period, billing retry, family sharing, restore.
- Chống chỉnh đồng hồ/offline vô hạn: mốc thời gian server + cửa sổ tin cậy offline có hạn; giao dịch `.unverified` = không có quyền; lắng nghe `Transaction.updates` ngay khi khởi động; `finish()` sau khi cấp quyền.
- Tính năng tốn tiền server (Gemini/Firebase AI, upload media) kiểm quyền ở server (Cloud Function/rules + App Check), không chỉ ẩn nút ở UI.
- App Check iOS (App Attest/DeviceCheck) bật cho Firestore/Functions. `appplugin/backend/app/integrity.py` hiện xác minh Play Integrity: nêu rõ phải mở rộng gì cho iOS hoặc chính sách thay thế.
- Test bắt buộc: rules test (emulator), unit test resolver quyền, test tamper (đồng hồ lùi, giao dịch unverified, giao dịch của uid khác, hoàn tiền).

## 5. Lỗi kinh điển cần xử lý rõ trong phần liên quan (ghi "pitfall → cách chặn → cách kiểm")
- P1: User Script Sandboxing của Xcode phải TẮT cho Run Script `embedAndSignAppleFrameworkForXcode`; Gradle trên Windows bỏ qua target Apple; khớp phiên bản Kotlin/Compose/Xcode; thời gian build lần đầu và cache Konan; cờ linker `-ObjC` khi có Swift package.
- P2: SQLite trong App Group + WAL + app bị đóng băng nền → crash 0xdead10cc (ưu tiên widget đọc JSON snapshot, KHÔNG mở DB); identity hash/schema Room để DB Android cũ không bị xoá; kotlinx-datetime: múi giờ/DST/ngày 29-2/ranh giới ngày; NSUserDefaults khác SharedPreferences (kiểu dữ liệu, đồng bộ hoá).
- P3: chuẩn hoá Unicode NFC trước khi băm/so khớp chuỗi tiếng Việt (đáp án câu hỏi bảo mật, tên...) vì iOS/Android có thể cho NFC/NFD khác nhau; Keychain tồn tại sau khi gỡ app (bộ đếm khoá PIN/trạng thái đăng nhập cũ → cờ "first launch" để dọn); Sign in with Apple chỉ trả tên/email lần đầu, relay email, revoke token khi xoá tài khoản; vòng đời listener Firestore.
- P4: gõ tiếng Việt Telex/VNI (marked text/IME) trong TextField Compose iOS PHẢI đúng — đưa vào spike P0 và test trên thiết bị thật; bàn phím/insets/safe area/Dynamic Island; vuốt-quay-lại iOS vs HorizontalPager; chi phí `blur`/offscreen layer; decode ảnh lớn ngoài main thread; `rememberSaveable`/khôi phục trạng thái; Reduce Motion; VoiceOver.
- P5: giới hạn 64 thông báo local; BGAppRefresh không đảm bảo (đừng dựa vào); trigger lặp qua DST/đổi múi giờ; xin quyền đúng lúc và luồng bị từ chối; ngân sách reload timeline widget (~40–70/ngày), gọi `reloadTimelines` khi dữ liệu đổi; giới hạn bộ nhớ widget ~30MB (thumbnail nhỏ); che nội dung nhạy cảm khi khoá màn hình.
- P6: thứ tự ATT/UMP và va chạm với quảng cáo mở app; banner/native qua UIKitView (chiều cao, cảm ứng, rò rỉ); gọi quảng cáo trên main thread; danh sách SKAdNetworkItems; giấy phép SDK gắn bundle id; kill switch hoạt động khi offline.
- P7: lý do bị từ chối thường gặp (2.1 thiếu tài khoản demo, 4.8, 5.1.1(v), 3.1.2 điều khoản đăng ký, 1.2 nội dung người dùng, privacy manifest); build number tăng đơn điệu; dSYM lên Crashlytics; export compliance; TestFlight external beta cần review.
- Mọi phần: Kotlin↔Swift — exception phải có `@Throws`, tránh retain cycle, huỷ Flow khi view biến mất, không chặn main thread.

## 6. Kiểm chứng chức năng
- Mỗi tính năng người dùng thấy được có kiểm tra tự động hoặc kịch bản thủ công. Ma trận do P8 giữ: `p8-feature-parity-matrix.md` (id `FP-xxx`). Dòng task có thể ghi `FP-xxx` mà task hoàn thiện.
- Hàm thuần (tính ngày yêu, mốc kỷ niệm, ngày lễ/âm lịch, GiftRanker, băm/KDF, định dạng ngày vi/en...) phải có "golden vectors" sinh từ Android và kiểm trên cả hai nền tảng (đặt tại `shared/src/commonTest/resources/golden/`; P2/P7 chốt cách làm).

## 7. Cách làm việc
- Giữ báo cáo cuối ≤10 dòng. Chỉ ghi vào file của mình. Không sửa mã nguồn, không dùng git.
