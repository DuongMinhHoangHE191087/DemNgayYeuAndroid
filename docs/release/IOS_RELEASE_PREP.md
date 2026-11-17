# Chuẩn bị phát hành iOS (App Store) — InLove

Ngày kiểm tra: 26/10/2026. Đây là tài liệu chuẩn bị, chưa phải hướng dẫn nộp bài.
Nhãn nguồn: [WF] = đã mở trang Apple (WebFetch); [R] = đối chiếu repo; [U] = chưa xác minh, owner kiểm tra.
Không ghi Team ID thật, bundle ID thật, khoá, mật khẩu hay email vào tài liệu này.

## 1. Trạng thái hiện tại

- Không có app iOS InLove nào build được. Repo chưa có Xcode project của app InLove [R].
- Sửa nhận định cũ: `.xcodeproj` duy nhất trong repo là `monetization-brain-ios/Integration/MonetizationBrainIntegration.xcodeproj`
  (targets HostApp và StoreKitHostedTests). Đây là harness kiểm thử monetization, không phải app InLove [R].
- Đã có: `iosApp/InLoveWidget/` (Info.plist, entitlements, PrivacyInfo.xcprivacy) và `iosApp/StoreKit/InLove.storekit`
  (giá là placeholder theo PROGRESS M-71) [R].
- Đã có: `scripts/ios/` (mẫu AASA và script kiểm tra; chưa chọn Team ID và bundle ID [U]), `scripts/kmp/ios-release-guard.sh`,
  iOS framework targets trong `shared/build.gradle.kts`, `apple.js` và `appleNotifications.js` phía server [R].
- Chưa có iOS client chạy được trong repo.
- KMP/iOS: lộ trình nằm ở `docs/superpowers/plans/2026-10-13-kmp-ios/PROGRESS.md`; tài liệu này không đổi trạng thái trong file đó.

## 2. Ghi danh Apple Developer Program [WF]

- Cá nhân: Apple Account bật xác thực hai yếu tố; đủ tuổi thành niên (người giám hộ có thể ghi danh nếu người dùng là trẻ vị thành niên); tên pháp lý.
  Địa chỉ không dùng hộp thư bưu điện (P.O. box).
- Tổ chức: pháp nhân, số D-U-N-S, email công việc theo tên miền, website công khai.
- Phí: 99 USD mỗi năm thành viên. Tổ chức phi lợi nhuận, cơ sở giáo dục được công nhận và cơ quan chính phủ có thể xin fee waiver nếu đủ điều kiện; không tự động được miễn phí.
- Mặc định đề xuất: ghi danh cá nhân nếu chưa có pháp nhân. Khi đó tên người bán trên store là tên cá nhân.
- Owner tự ghi danh và thanh toán. Không đưa thông tin đăng nhập Apple vào repo.

## 3. Khai báo quyền riêng tư (App Privacy) [WF]

- Nhóm khai báo trong App Store Connect: Data Linked to You, Data Used to Track You, Data Not Collected.
- Bắt buộc có URL chính sách quyền riêng tư công khai. Đối tác SDK phải được khai báo.
- Dữ liệu cần khai theo cùng bảy nhóm với bản Android (PLAY_RELEASE_CHECKLIST.md, mục 4). Cột ghi chú nêu phần khác trên iOS:

| Nhóm | Nguồn trên Android | Ghi chú cho iOS |
|---|---|---|
| Tài khoản | Firebase Auth (email, uid) | Đăng nhập Facebook: xem mục 4, Guideline 4.8 [R] |
| Hồ sơ, ghép đôi | `users/{uid}`, `relationships`, `invites`; lời mời chứa ngày và lời nhắn, gửi lên server [R] | Có khi đăng nhập và ghép đôi online [R] |
| Chẩn đoán lỗi | Crashlytics | Xác nhận khi có build iOS [U] |
| Analytics, messaging | appplugin: Firebase Analytics, FCM, AppsFlyer | Xác nhận SDK và cấu hình consent trên iOS [U] |
| Quảng cáo | AdMob, UMP; adapter Meta | Nếu bật trên iOS, xét Data Used to Track You và ATT [U] |
| Mua hàng | Google Play Billing, xác minh qua Cloud Functions [R] | Map sang In-App Purchase (mục 5). `apple.js` và `appleNotifications.js` có trong repo [R]; deploy [U] |
| Kỷ niệm, ảnh, video, ghi chú kỷ niệm | Trên máy; Auto Backup của Android [R] | Chưa có client iOS chạy được trong repo [R]; hành vi sao lưu trên iOS [U] |

- Nếu quảng cáo cá nhân hoá trên iOS, phải xét App Tracking Transparency trước khi theo dõi người dùng [U].
- PrivacyInfo.xcprivacy hiện chỉ có cho widget. App target và từng SDK cần khai báo riêng khi có build [U].

## 4. Quy định Apple áp dụng cho InLove [WF]

- 3.1.1: mở khoá tính năng số (VIP, không quảng cáo) phải dùng In-App Purchase. Đây là nguyên tắc tương đương Play Billing.
- 4.8: app có đăng nhập bên thứ ba hoặc mạng xã hội (Android hiện có Facebook) phải có một lựa chọn đăng nhập tương đương.
  Trang guideline không nêu tên cụ thể; cách phổ biến là Sign in with Apple. Quyết định là việc của owner.
- 5.1.1(v): app có tạo tài khoản phải cho xoá tài khoản ngay trong app. Android đã có luồng này [R], nhưng luồng phụ thuộc Cloud Functions; trạng thái deploy chưa xác nhận [U].
- Mốc đang hiệu lực: privacy manifest và required-reason API từ 1/5/2024; Xcode 26 và iOS 26 SDK từ 28/4/2026;
  câu hỏi độ tuổi cập nhật từ 31/1/2026.

## 5. Việc còn thiếu trước khi viết bước nộp

- Chưa có app target iOS trong repo [R]. Team ID và bundle ID chưa được chọn [U]. Bản ghi app trong App Store Connect chưa xác nhận [U].
- Chưa ghi danh Apple Developer Program [U].
- Trạng thái In-App Purchase trên App Store Connect chưa xác nhận [U]. Cần map `vip_monthly`, `vip_yearly`, `vip_lifetime` sang sản phẩm iOS;
  `InLove.storekit` hiện chỉ là placeholder [R].
- Chưa quyết định cách đáp ứng 4.8 [U].
- Chưa có URL chính sách quyền riêng tư và khai báo App Privacy cho iOS [U].
- Chưa xác nhận có máy Mac có Xcode để build và ký (repo không cho biết) [U]. Xcode chỉ chạy trên macOS.
- Chưa có bằng chứng trong lượt kiểm tra này rằng shared framework cho iOS build được [U].

## 6. Thứ tự làm khi bắt đầu iOS

1. Owner ghi danh Apple Developer Program và chốt cá nhân hay tổ chức.
2. Chọn bundle ID và Team ID, điền vào `scripts/ios` (giá trị không ghi vào tài liệu này).
3. Tạo app target, gắn widget và PrivacyInfo.xcprivacy vào đúng target, rồi dùng `scripts/kmp/ios-release-guard.sh` kiểm tra cấu hình release.
4. Tạo In-App Purchase trên App Store Connect, map `vip_*` và cập nhật `InLove.storekit` cho khớp.
5. Chốt cách đáp ứng 4.8 và điền App Privacy.
6. Viết bước nộp App Store (metadata, ảnh chụp, thông tin review) khi các bước trên đã xong.

## 7. Copy và ảnh chụp

- Copy App Store nằm trong STORE_LISTING_AND_MEDIA_GUIDE.md, mục 1.2. Ảnh chụp và video nằm trong STORE_LISTING_AND_MEDIA_GUIDE.md, mục 2, dùng chung với bản Play (PLAY_RELEASE_CHECKLIST.md, mục 7.2).
- Các câu về đồng bộ đám mây và "chia sẻ" trong copy phải sửa theo trạng thái LOCAL_ONLY trước khi nộp, như bản Play (PLAY_RELEASE_CHECKLIST.md, mục 7.4).
