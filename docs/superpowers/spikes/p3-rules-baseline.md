# P3 — Baseline bộ rules test (B-10)

Ghi nhận ngày 2026-10-14 trên Windows 11 Pro 10.0.26300.

## 1. Kết quả

- Lệnh acceptance: `cd scripts; npm ci; npm run test:rules` (chạy từ thư mục gốc repo).
- `npm ci`: thành công, added 791 packages.
- `npm run test:rules`: thoát 0.
- Số test: **42 passing, 0 failing, 0 pending** (mocha, 4s).
- Số passing ghi nhận: **42** (ngưỡng B-10: ≥ 34).
- `scripts/package.json` không thay đổi. Emulator chạy được với Java hiện tại nên không cần ghim `firebase-tools` hay `@firebase/rules-unit-testing`.

## 2. Phiên bản môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Node.js | v26.10.0 | npm 11.19.1 |
| Java (JVM chạy emulator) | JDK 27, build 27+35-2325 | `C:\Program Files\Java\jdk-27\bin\java.exe`. firebase-tools gọi `java` từ PATH, không đọc JAVA_HOME |
| JAVA_HOME (không dùng cho emulator) | Temurin 25.0.4.1+1 | `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\` |
| Firestore emulator | cloud-firestore-emulator v1.19.8 | jar trong `%USERPROFILE%\.cache\firebase\emulators\`; project `demngayyeuandroid`, rules là `firestore.rules` ở gốc repo |
| firebase-tools | 13.35.1 | devDependency |
| @firebase/rules-unit-testing | 3.0.4 | |
| firebase (client SDK) | 10.14.1 | |
| mocha | 10.8.2 | |

## 3. Số test theo file

| File | Suite | Passing |
|---|---|---|
| `scripts/rules-tests/media.rules.test.js` | media security rules (docs/architecture/MEDIA_CLOUDINARY.md) | 17 |
| `scripts/rules-tests/pairing.rules.test.js` | pairing security rules (Task 10 design) | 5 |
| `scripts/rules-tests/users-entitlements.rules.test.js` | users/{uid} entitlement protection (T2) | 20 |
| **Tổng** | | **42** |

Có 34 dòng `it(` tĩnh trong 3 file. Số test chạy được là 42 vì `users-entitlements.rules.test.js` dòng 40 lặp qua 3 trường nhạy cảm (`isVip`, `subscriptionTier`, `role`) và sinh 4 test cho mỗi trường.

## 4. Ghi chú khi chạy

- Log `GrpcConnection ... PERMISSION_DENIED` trong đầu ra là bình thường: các test kiểm tra thao tác bị từ chối.
- `npm ci` cảnh báo `EBADENGINE` cho `superstatic@9.2.0` (yêu cầu Node 18/20/22, máy đang dùng Node 26). Đây chỉ là cảnh báo.
- `npm ci` báo 36 lỗ hổng dependency (1 critical, 15 high, 20 moderate). Chưa xử lý, nằm ngoài B-10.
- Các mục sinh ra khi chạy đều đã bị git bỏ qua: `scripts/node_modules/` (`.gitignore` dòng 28) và `scripts/firestore-debug.log` (`.gitignore` dòng 41).

## 5. Vấn đề đã gặp: emulator mồ côi giữ cổng 8080/9150

- Hai lần `npm run test:rules` thoát 0 đều để lại một tiến trình java của emulator. Tiến trình cha đã thoát nhưng java vẫn giữ cổng 8080 và 9150.
- Lần chạy sau báo lỗi `Error: Could not start Firestore Emulator, port taken.`
- Trước mỗi lần chạy lại, tiến trình còn sót đã được dừng thủ công bằng lệnh sau (PowerShell):

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'cloud-firestore-emulator' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

- Nguyên nhân gốc chưa xác định. Không sửa `scripts/package.json` vì điều kiện sửa của B-10 (emulator không chạy với Java hiện tại) không xảy ra.

## 6. Chưa kiểm tra

- Chỉ chạy trên Windows 11 này. Không kiểm tra macOS, Linux hay CI.
- Không đánh giá từng rule. File này chỉ ghi số pass/fail hiện tại.
