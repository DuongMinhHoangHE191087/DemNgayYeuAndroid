# Hướng Dẫn Sử Dụng Script Làm Giàu Dữ Liệu Firebase (InLove Seeder)

Script `seed_firestore.js` được thiết kế để làm giàu (enrich) cơ sở dữ liệu Firebase Cloud Firestore với dữ liệu kiểm thử, gợi ý quà tặng, huy hiệu, mốc kỷ niệm, và bộ ảnh mẫu chọn lọc.

## 🛡️ Lợi Ích Chống Dịch Ngược (Anti-Decompilation)
- **Trước đây**: Các câu chuyện tình yêu mẫu, ghi chú cá nhân, mốc ngày tháng, tài khoản test, URL ảnh Unsplash được ghi thẳng vào mã nguồn Kotlin (`InLoveRepository.kt`, `MemoriesGridScreen.kt`, `EditMyProfileDialog.kt`). Khi hacker/người dùng dịch ngược file `.apk`, toàn bộ dữ liệu này bị phơi bày hoàn toàn.
- **Hiện tại**: Toàn bộ dữ liệu được chuyển lên đám mây Firestore trong các collections an toàn (`test_fixtures`, `gift_ideas`, `milestone_presets`, `badge_definitions`, `preset_assets`). Ứng dụng Android chỉ cần gọi API đồng bộ về Room database cục bộ, mã nguồn APK hoàn toàn "sạch bóng" không chứa bất kỳ dữ liệu nhạy cảm hay thông tin nội bộ nào.

---

## 🚀 Cách Chạy Script

### Cách 1: Chạy trực tiếp qua Node.js (Mặc định REST Mode)
Không cần tải file key, chạy trực tiếp:
```bash
cd scripts
node seed_firestore.js
```

### Cách 2: Chạy với Quyền Quản Trị Viên (Firebase Admin SDK)
1. Truy cập [Firebase Console](https://console.firebase.google.com/) -> Dự án `demngayyeuandroid`.
2. Vào **Project settings** -> **Service accounts** -> Bấm **Generate new private key**.
3. Lưu file tải về vào thư mục `scripts/` với tên `serviceAccountKey.json`.
4. Cài đặt dependencies và chạy:
```bash
cd scripts
npm install
npm run seed
```

---

## 📦 Các Collections Được Tạo Trên Firestore

| Collection | Mục Đích | Số lượng tài liệu |
| :--- | :--- | :--- |
| `test_fixtures` | Tài khoản kiểm thử cho QA/Tester (Tester A, Tester B, VIP, Admin) | 5 tài liệu |
| `gift_ideas` | 18+ gợi ý quà tặng lãng mạn đầy đủ 6 danh mục, hình ảnh và phân loại giá | 18 tài liệu |
| `milestone_presets` | Các mốc kỷ niệm tiêu chuẩn (7 ngày, 30 ngày, 100 ngày, 1 năm...) | 8 tài liệu |
| `badge_definitions` | 11 huy hiệu tình yêu (Bronze, Silver, Gold, Ruby, Diamond, Cosmic) | 11 tài liệu |
| `preset_assets` | Danh sách URL ảnh kỷ niệm mẫu & avatar mẫu phân giải cao | 2 tài liệu |
| `checklist_templates` | Mẫu danh sách việc cần chuẩn bị cho ngày kỷ niệm/hẹn hò | 5 tài liệu |

---

## 🛠️ Bộ Script Tiện Ích Thao Tác Nhanh (Windows Batch Scripts)

Thư mục `scripts/` cung cấp sẵn 3 công cụ batch script giúp lập trình viên và QA kiểm thử ứng dụng nhanh chóng, trực quan:

### 1. `launch_emulator.bat` (Mở máy ảo có GUI tương tác)
- **Công dụng**: Khởi chạy máy ảo Android `medium_phone` hiển thị đầy đủ cửa sổ điện thoại nổi trên màn hình Windows.
- **Cách dùng**: Nhấp đúp chuột vào file `scripts\launch_emulator.bat` hoặc gõ trong terminal:
  ```cmd
  scripts\launch_emulator.bat
  ```
- Cho phép người dùng chạm, vuốt, gõ văn bản và thao tác trực tiếp trên màn hình máy ảo.

### 2. `monitor_app.bat` (Giám sát Debug, Chống Crash & Rò Rỉ Bộ Nhớ Thời Gian Thực)
- **Công dụng**: Theo dõi trực tiếp dòng dữ liệu Logcat với bộ lọc chuyên sâu:
  - `Ads`: Vòng đời quảng cáo Banner, Interstitial (Interval Capping 60s), App Open Ads.
  - `BillingManager`: Kết nối Google Play Billing v7, truy vấn gói, trạng thái VIP StateFlow.
  - `AndroidRuntime` & `FATAL`: Bắt lập tức mọi lỗi Crash (Uncaught Exceptions), ANR, OutOfMemory.
- **Cách dùng**: Nhấp đúp chuột vào file `scripts\monitor_app.bat` để mở cửa sổ console debug song song khi đang test app.

### 3. `run_app.bat` (Đóng gói & Cài đặt APK Một Bước)
- **Công dụng**: Tự động gọi `./gradlew assembleDebug`, đợi máy ảo kết nối, cài đặt file APK mới nhất và mở app `InLove` lên màn hình.
- **Cách dùng**: Nhấp đúp chuột vào file `scripts\run_app.bat`.

