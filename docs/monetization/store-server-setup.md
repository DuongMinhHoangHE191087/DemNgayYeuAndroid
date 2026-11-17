# Thiết lập máy chủ thanh toán (store server) cho InLove

Tài liệu cho chủ dự án. Làm theo đúng thứ tự từ trên xuống. Mỗi bước ghi rõ làm ở đâu:

- **[CONSOLE – Apple]**: App Store Connect, trên trình duyệt.
- **[CONSOLE – Google]**: Google Play Console, trên trình duyệt.
- **[CONSOLE – Google Cloud]**: console.cloud.google.com, trên trình duyệt.
- **[CONSOLE – Firebase]**: console.firebase.google.com, trên trình duyệt.
- **[TERMINAL]**: PowerShell, chạy tại thư mục gốc của repo trừ khi có ghi khác.

Thông tin cố định: Firebase project `demngayyeuandroid`, vùng `asia-southeast1`, Android applicationId `com.aistudio.inlove.kmrv`. Cập nhật lần cuối: 2026-10-15.

## 0. Quy tắc an toàn (đọc trước)

- Không dán khoá bí mật vào chat, commit, issue, ảnh chụp màn hình hay file trong repo. Khoá bí mật gồm file `.p8` của Apple, khoá riêng ký token (`ENTITLEMENT_SIGNING_KEY`) và mọi giá trị secret.
- Chỉ đưa khoá bí mật vào hệ thống bằng `firebase functions:secrets:set`. Không tạo file khoá trong repo, không ghi khoá vào `.env`.
- Không dùng `firebase functions:secrets:access` để kiểm tra: lệnh này in khoá ra màn hình.
- `make-signing-key.js` chỉ chạy trong terminal không lưu lịch sử. Trong PowerShell, chạy `Set-PSReadLineOption -HistorySaveStyle SaveNothing` trước, rồi đóng cửa sổ khi xong.
- Không chạy lại `make-signing-key.js` khi đã có khoá đang dùng. Lệnh này tạo khoá mới và đổi khoá ký của server. Muốn xoay khoá thì làm theo mục 13.
- Không đưa `kid` có tiền tố `test-` vào bản phát hành. Gặp `test-` thì dừng và báo nhóm kỹ thuật.
- Không sửa tay dữ liệu Firestore (`purchases`, `entitlements`, `entitlementAccounts`, `entitlementAudit`). Muốn chuyển giao dịch sang tài khoản khác thì chỉ dùng `rebind-purchase.js` ở mục 12.

## 1. Các hàm và tham số

### 1.1 Sáu hàm

| Hàm | Loại | Việc làm | Bảo vệ |
|---|---|---|---|
| `getAccountToken` | callable | Cấp mã tài khoản (appAccountToken) để app gắn vào giao dịch | App Check, đăng nhập |
| `claimPurchase` | callable | App gửi mã giao dịch, server hỏi cửa hàng rồi quyết định cấp VIP | App Check, đăng nhập |
| `getEntitlement` | callable | App hỏi trạng thái quyền VIP | App Check, đăng nhập |
| `appleNotifications` | HTTP (webhook) | Apple gửi thông báo thay đổi giao dịch (ASSN V2) | Chữ ký JWS của Apple |
| `googlePlayNotifications` | Pub/Sub, topic `play-rtdn` | Google gửi thông báo thay đổi giao dịch (RTDN) | Chỉ tài khoản Google được phép publish vào topic |
| `releaseEntitlementBindings` | Firestore trigger, `accountDeletions/{uid}` | Dọn quyền khi người dùng xoá tài khoản | Nội bộ |

### 1.2 Chín tham số

Có hai loại:
- **secret**: lưu trong Secret Manager, đặt bằng `firebase functions:secrets:set`.
- **string**: đặt trong file `firebase/functions/.env.demngayyeuandroid`, hoặc nhập khi `firebase deploy` hỏi.

| Tên | Loại | Ý nghĩa | Lấy ở đâu |
|---|---|---|---|
| `APPLE_IAP_PRIVATE_KEY` | secret | Toàn bộ nội dung file `.p8` của khoá In-App Purchase | Apple, mục 3 |
| `ENTITLEMENT_SIGNING_KEY` | secret | Khoá riêng ký token quyền lợi (gồm `kid` và `pem`) | Tạo bằng `make-signing-key.js`, mục 7 |
| `APPLE_IAP_KEY_ID` | string | Key ID của khoá `.p8` | Apple, mục 3 |
| `APPLE_IAP_ISSUER_ID` | string | Issuer ID của tài khoản App Store Connect | Apple, mục 3 |
| `APPLE_BUNDLE_ID` | string | Bundle ID của app iOS, phải khớp App Store Connect | Apple, mục 3 |
| `APPLE_APP_APPLE_ID` | string | Apple ID dạng số của app | Apple, mục 3 |
| `GOOGLE_PACKAGE_NAME` | string | Package name của app Android | `com.aistudio.inlove.kmrv` |
| `LEGACY_CLAIM_UNTIL_MS` | string | Hạn của cửa sổ khôi phục legacy, tính bằng mili giây Unix. Trống thì đường legacy đóng | Mục 10 |
| `ACCOUNT_TOKEN_CUTOVER_MS` | string | Mốc chuyển đổi sang mã tài khoản, tính bằng mili giây Unix. Trống thì không có bằng chứng legacy nào, đường legacy đóng | Mục 10 |

Đây là đủ 9 tham số trong `firebase/functions/src/entitlement/options.js`.

### 1.3 Thuật ngữ

| Thuật ngữ | Nghĩa đơn giản |
|---|---|
| Secret | Giá trị bí mật lưu trong kho của Google Cloud. Không ai được xem hay chép ra ngoài. |
| `.env` | File văn bản chứa cấu hình không bí mật, đặt cạnh mã hàm. |
| Topic Pub/Sub | Kênh tin nhắn của Google Cloud. Google đẩy thông báo vào kênh, hàm nhận từ đó. |
| Tài khoản dịch vụ (service account) | Tài khoản "robot" mà hàm dùng để gọi API của Google. |
| App Check | Xác minh app là bản chính thức, chặn các client giả. |
| Remote Config | Bảng giá trị cấu hình của Firebase. Đổi được mà không phát hành lại app. |
| Mốc chuyển đổi (cutover) | Từ mốc này, giao dịch mới phải gắn với mã tài khoản của app. |
| Legacy | Đường cấp quyền cũ, cho giao dịch Google mua trước mốc chuyển đổi và chưa có mã tài khoản. Điều kiện chặt, xem mục 12.1. |

## 2. Thứ tự làm và bảng thao tác

Làm đúng thứ tự từ 1 đến 14. Đánh dấu khi xong.

| # | Làm ở đâu | Việc | Mục |
|---|---|---|---|
| 1 | [CONSOLE – Apple] | Tạo khoá In-App Purchase, lưu file `.p8`, ghi Key ID, Issuer ID, Apple ID, Bundle ID | 3 |
| 2 | [TERMINAL] | Tạo topic `play-rtdn` và cấp quyền publisher cho Google | 4 |
| 3 | [CONSOLE – Google] + [CONSOLE – Google Cloud] | Liên kết Google Cloud với Play Console; bật Google Play Android Developer API | 5 |
| 4 | [TERMINAL] | Đặt secret `APPLE_IAP_PRIVATE_KEY` và file `.env` | 6 |
| 5 | [TERMINAL] | Tạo khoá ký `ENTITLEMENT_SIGNING_KEY`; ghi `kid` và khoá công khai | 7 |
| 6 | [TERMINAL] | Deploy hàm; ghi URL của `appleNotifications` | 8 |
| 7 | [CONSOLE – Google Cloud] + [CONSOLE – Google] | Tìm tài khoản dịch vụ chạy hàm; mời vào Play Console | 9.1 |
| 8 | [CONSOLE – Apple] | Đăng ký ASSN V2 cho Production và Sandbox | 9.2 |
| 9 | [CONSOLE – Google] | Nhập topic RTDN `projects/demngayyeuandroid/topics/play-rtdn` | 9.3 |
| 10 | [CONSOLE – Firebase] | Đăng ký App Check; bật Enforce khi số liệu ổn | 9.4 |
| 11 | [CONSOLE – Firebase] | Đặt Remote Config `sdk_kill_ios` = `{"ads":false}` và publish | 9.5 |
| 12 | [CONSOLE – Apple] + [CONSOLE – Google] + thiết bị thật | Thử Sandbox: mua thử, kiểm tra log | 9.6 |
| 13 | [TERMINAL] | Đặt hai mốc chuyển đổi, deploy lại server (server trước) | 10 |
| 14 | [CONSOLE – Google] | Phát hành bản Android cutover (sau bước 13) | 11 |

## 3. Khoá In-App Purchase của Apple [CONSOLE – Apple]

Cần: tài khoản Apple Developer Program trả phí và người làm có quyền tạo khoá (thường là Account Holder). App iOS phải đã được tạo trong App Store Connect với đúng Bundle ID, vì Apple ID của app (bước 3) chỉ hiện sau khi tạo app.

1. App Store Connect → **Users and Access** (Người dùng và quyền truy cập) → tab **Integrations** → mục **In-App Purchase** → **Generate** (Tạo khoá). Đặt tên, ví dụ `InLove Store Server`.
2. Tải file `.p8`. **Apple chỉ cho tải một lần.** Lưu file ở nơi an toàn, ngoài repo. Không gửi qua chat hay email.
3. Ghi lại:
   - **Key ID**: hiện trong danh sách khoá. Dạng `<KEY_ID>`.
   - **Issuer ID**: hiện ở đầu trang khoá, dạng UUID `<ISSUER_ID>`.
   - **Apple ID** của app: dãy số trong trang **App Information**, dạng `<APPLE_ID_SỐ>`.
   - **Bundle ID** iOS: dạng `<BUNDLE_ID_IOS>`. Kế hoạch hiện tại là `com.aistudio.inlove.kmrv`. Phải trùng với App Store Connect; nếu khác thì dùng giá trị của App Store Connect.

Tên menu có thể đổi, xem mục 15.

## 4. Topic Pub/Sub cho thông báo Google [TERMINAL]

Bắt buộc có trước khi phát hành. Chạy trong PowerShell. Nếu chưa đăng nhập: `gcloud auth login`.

```powershell
gcloud pubsub topics create play-rtdn --project=demngayyeuandroid
gcloud pubsub topics add-iam-policy-binding play-rtdn --project=demngayyeuandroid --member="serviceAccount:google-play-developer-notifications@system.gserviceaccount.com" --role="roles/pubsub.publisher"
```

- Lệnh thứ nhất tạo topic `play-rtdn`. Nếu topic đã có, lệnh báo lỗi "already exists" là bình thường.
- Lệnh thứ hai cho đúng một tài khoản của Google, `google-play-developer-notifications@system.gserviceaccount.com`, quyền `roles/pubsub.publisher` để publish vào topic. Không cấp quyền này cho tài khoản nào khác.
- Kiểm tra: `gcloud pubsub topics describe play-rtdn --project=demngayyeuandroid`.

## 5. Liên kết Google Play và bật API [CONSOLE – Google] [CONSOLE – Google Cloud]

1. [CONSOLE – Google] Play Console → **Cài đặt** (Settings) → **Quyền truy cập API** (API access) → liên kết project Google Cloud `demngayyeuandroid`.
2. Bật Google Play Android Developer API (`androidpublisher.googleapis.com`). Trên terminal:

```powershell
gcloud services enable androidpublisher.googleapis.com --project=demngayyeuandroid
```

Hoặc trên console: Google Cloud → **APIs & Services** → **Library** → tìm "Google Play Android Developer API" → **Enable**.

## 6. Đặt secret Apple và file .env [TERMINAL]

Làm trước khi deploy. Nếu secret chưa có, `firebase deploy` sẽ báo lỗi. Trước đó chạy `firebase login`, và kiểm tra project mặc định là `demngayyeuandroid` (file `.firebaserc`).

### 6.1 Khoá .p8 (secret)

Cách 1: nhập khi CLI hỏi, dán toàn bộ nội dung file `.p8` (gồm dòng đầu và dòng cuối):

```powershell
firebase functions:secrets:set APPLE_IAP_PRIVATE_KEY
```

Cách 2: đọc từ file, đường dẫn đặt ngoài repo:

```powershell
Get-Content -Raw '<đường-dẫn-tới-file-.p8>' | firebase functions:secrets:set APPLE_IAP_PRIVATE_KEY
```

### 6.2 File .env (các tham số chuỗi)

Tạo file `firebase/functions/.env.demngayyeuandroid`, cùng thư mục với `package.json`. Đường dẫn `.env.*` đã nằm trong `.gitignore`, nên file không bị commit. Nội dung (thay mọi `<...>`):

```text
APPLE_IAP_KEY_ID=<KEY_ID>
APPLE_IAP_ISSUER_ID=<ISSUER_ID>
APPLE_BUNDLE_ID=<BUNDLE_ID_IOS>
APPLE_APP_APPLE_ID=<APPLE_ID_SỐ>
GOOGLE_PACKAGE_NAME=com.aistudio.inlove.kmrv
LEGACY_CLAIM_UNTIL_MS=
ACCOUNT_TOKEN_CUTOVER_MS=
```

- Hai dòng cuối để trống. Trống nghĩa là đường legacy đóng, đây là mặc định an toàn. Chỉ điền ở mục 10.
- Không để sót chữ `<...>` khi deploy.
- Không ghi khoá bí mật vào file này.
- Không muốn tạo file thì `firebase deploy` sẽ hỏi từng giá trị.

## 7. Khoá ký quyền lợi (ENTITLEMENT_SIGNING_KEY) [TERMINAL]

Chỉ chạy một lần, lúc thiết lập lần đầu khi chưa có khoá nào. Chạy trong terminal không lưu lịch sử (xem mục 0):

```powershell
Set-PSReadLineOption -HistorySaveStyle SaveNothing
cd firebase\functions
node scripts/make-signing-key.js | firebase functions:secrets:set ENTITLEMENT_SIGNING_KEY
```

- Khoá riêng đi thẳng vào Secret Manager và không hiện ra màn hình. Màn hình chỉ hiện hai dòng không bí mật:
  - `kid=<16 ký tự hex>`
  - `publicKeySpkiBase64=<chuỗi base64>`
- Ghi lại hai dòng này và gửi cho đội Android để đưa vào `EntitlementPublicKeys` của app trước khi phát hành.
- Kiểm tra `kid`: không được có tiền tố `test-`. Nếu có thì dừng và báo nhóm kỹ thuật.
- Đóng cửa sổ terminal sau khi xong.

## 8. Deploy hàm [TERMINAL]

Chạy từ thư mục gốc của repo (thư mục có file `firebase.json`):

```powershell
firebase deploy --only functions
```

- Sau khi deploy xong, tìm dòng `Function URL (appleNotifications...)` trong kết quả. Ghi lại URL để dán vào App Store Connect ở mục 9.2.
- Lỗi `Secret ... not found`: quay lại mục 6.1 và 7.
- Lỗi thiếu giá trị tham số: điền `.env` (mục 6.2) rồi deploy lại.

## 9. Cấu hình trên console

### 9.1 Tài khoản dịch vụ chạy hàm và quyền trên Play Console

1. Tìm email tài khoản dịch vụ mà các hàm dùng:
   - [CONSOLE – Google Cloud] **Cloud Run** → chọn dịch vụ `googleplaynotifications` (tên viết thường, vùng `asia-southeast1`) → tab **Security** (Bảo mật) → ghi **Service account**.
   - Hoặc [TERMINAL]:

   ```powershell
   gcloud run services describe googleplaynotifications --region=asia-southeast1 --project=demngayyeuandroid --format="value(spec.template.spec.serviceAccountName)"
   ```

   Nếu kết quả trống, hàm dùng tài khoản mặc định có dạng `<số-project>-compute@developer.gserviceaccount.com`. Lấy đúng giá trị trên console, không tự đoán.
2. [CONSOLE – Google] Play Console → **Người dùng và quyền** (Users and permissions) → **Mời người dùng mới** → nhập email tài khoản dịch vụ.
3. Chọn app InLove và quyền tối thiểu: xem đơn hàng và đăng ký, quản lý đơn hàng và đăng ký (để xác nhận giao dịch). Tên quyền trên console có thể khác, đọc kỹ từng quyền trước khi cấp.
4. Quyền có thể mất đến 24 giờ mới có hiệu lực. Nếu `claimPurchase` báo `unavailable` ngay sau khi mời, chờ rồi thử lại (mục 14).

### 9.2 Apple ASSN V2 (App Store Server Notifications V2) [CONSOLE – Apple]

1. App Store Connect → **Apps** → chọn app InLove → **App Information** (Thông tin app) → mục **App Store Server Notifications** (Thông báo máy chủ App Store).
2. **Production Server URL**: dán URL của `appleNotifications` (lấy ở mục 8). Chọn **Version 2 Notifications**.
3. **Sandbox Server URL**: dán cùng URL đó. Chọn **Version 2 Notifications**.
4. Lưu. Nếu có nút gửi thông báo thử, bấm và kiểm tra log hàm (mục 14).

### 9.3 Thông báo thời gian thực của Google (RTDN) [CONSOLE – Google]

1. Play Console → **Thiết lập kiếm tiền** (Monetization setup) → **Thông báo thời gian thực cho nhà phát triển** (Real-time developer notifications). Tên tiếng Việt có thể khác, tìm theo cụm tiếng Anh.
2. Nhập tên topic đầy đủ: `projects/demngayyeuandroid/topics/play-rtdn`. Lưu.
3. Sau khi mua thử ở mục 9.6, sự kiện phải xuất hiện trong log `googlePlayNotifications`.

### 9.4 App Check [CONSOLE – Firebase]

Mục tiêu: chỉ app chính thức mới gọi được hàm.

1. Firebase console → **Build** (Phát triển) → **App Check** → tab **Apps**.
2. App Android `com.aistudio.inlove.kmrv`: chọn nhà cung cấp **Play Integrity**.
3. App iOS: chọn **App Attest**.
4. Tab **APIs**: theo dõi số liệu vài ngày. Khi phần lớn yêu cầu đã được xác minh, chọn **Cloud Functions** → **Enforce** (bắt buộc).
5. Cảnh báo: Enforce có thể ảnh hưởng mọi hàm callable của dự án, không chỉ hàm entitlement. Kiểm tra số liệu và tỉ lệ người dùng đã có bản app mới trước khi bật.
6. Máy phát triển dùng debug token riêng và không đưa vào bản phát hành.

### 9.5 Remote Config: công tắc tắt quảng cáo iOS [CONSOLE – Firebase]

Mục tiêu: tắt quảng cáo iOS khẩn cấp mà không cần phát hành lại app.

1. Firebase console → **Remote Config** → thêm tham số:
   - Tên: `sdk_kill_ios`
   - Giá trị: `{"ads":false}` (bình thường: không tắt quảng cáo)
2. Bấm **Publish changes** (Xuất bản thay đổi).
3. Khẩn cấp: sửa giá trị thành `{"ads":true}`, rồi publish. Quảng cáo iOS bị tắt.
4. Lưu ý theo DEC-192:
   - Trong một phiên đang mở, giá trị mới chỉ có tác dụng TẮT ngay. Đổi về `{"ads":false}` (bật lại) có hiệu lực ở lần mở app kế tiếp.
   - JSON sai (thiếu ngoặc, sai dấu nháy, thiếu trường) thì app coi như không có lệnh tắt. Sau khi publish, kiểm tra lại từng ký tự.

### 9.6 Thử Sandbox [CONSOLE – Apple] [CONSOLE – Google] [thiết bị thật]

1. Apple: App Store Connect → **Users and Access** → **Sandbox** → **Testers**, tạo tài khoản thử. Đăng nhập tài khoản thử trên iPhone theo hướng dẫn của Apple. Không dùng Apple ID thật để mua thử.
2. Google: Play Console → **Cài đặt** (Settings) → **Kiểm thử giấy phép** (License testing), thêm tài khoản Gmail thử. Tài khoản này mua được mà không bị tính tiền.
3. Mua thử trên app (iOS và Android). Kết quả đúng: app hiện VIP và log hàm có sự kiện tương ứng.
4. Xem log:

   ```powershell
   firebase functions:log --only claimPurchase
   firebase functions:log --only appleNotifications
   firebase functions:log --only googlePlayNotifications
   ```

5. Có lỗi: xem mục 14.

## 10. Mốc chuyển đổi và hạn legacy [TERMINAL]

- `ACCOUNT_TOKEN_CUTOVER_MS`: thời điểm bản Android cutover được phát hành.
- `LEGACY_CLAIM_UNTIL_MS`: hạn của cửa sổ legacy, bằng ngày phát hành cutover cộng 90 ngày.
- Để trống cả hai nghĩa là đường legacy đóng.

Tính hai mốc (thay `<YYYY-MM-DDTHH:mm:ss+07:00>` bằng thời điểm phát hành dự kiến, có múi giờ):

```powershell
$cutover = [DateTimeOffset]::Parse('<YYYY-MM-DDTHH:mm:ss+07:00>').ToUnixTimeMilliseconds()
$cutover
$cutover + 7776000000
```

- Dòng đầu là `ACCOUNT_TOKEN_CUTOVER_MS`. Dòng thứ hai là `LEGACY_CLAIM_UNTIL_MS` (90 ngày = 7776000000 mili giây).
- Chỉ điền chữ số, không dấu phẩy, không khoảng trắng. Giá trị sai định dạng bị coi như trống, tức là đóng.

Sau đó điền hai giá trị vào `firebase/functions/.env.demngayyeuandroid`, rồi deploy lại:

```powershell
firebase deploy --only functions
```

- Hàm đọc hai tham số này khi instance chạy lần đầu và giữ trong bộ nhớ của instance đó. Đổi xong phải deploy lại.
- Sau deploy, thử mua thử lại (mục 9.6).
- Khẩn cấp đóng đường legacy: để trống `LEGACY_CLAIM_UNTIL_MS`, deploy lại.

## 11. Phát hành bản Android cutover [CONSOLE – Google]

Thứ tự bắt buộc: server trước, app sau.

1. Chỉ làm khi mục 10 đã deploy và đã kiểm tra.
2. Phát hành đúng thời điểm đã dùng để tính `ACCOUNT_TOKEN_CUTOVER_MS`.
3. Bản app phải có `EntitlementPublicKeys` chứa khoá công khai khớp `kid` ở mục 7.
4. Sau khi phát hành, theo dõi log `claimPurchase` và số lỗi `token-required`, `legacy-window-closed` (mục 12 và 14).

## 12. Hỗ trợ khôi phục giao dịch (rebindPurchase)

### 12.1 Khi nào tự động khôi phục

Chỉ áp dụng cho giao dịch Google chưa có mã tài khoản. Giao dịch được tự động gắn lại (đường legacy) khi TẤT CẢ điều kiện sau đúng:

1. Giao dịch được mua TRƯỚC `ACCOUNT_TOKEN_CUTOVER_MS`.
2. Tài khoản Firebase được tạo TRƯỚC `ACCOUNT_TOKEN_CUTOVER_MS`.
3. Tài khoản này đã được cấp quyền theo đường legacy ít hơn 3 lần.
4. Hiện tại vẫn TRƯỚC `LEGACY_CLAIM_UNTIL_MS`.

Điều kiện 1 và 2 không đủ thì server trả `token-required`. Điều kiện 3 và 4 không đủ thì server trả `legacy-window-closed`.

Apple không bao giờ đi đường legacy. Mọi trường hợp còn lại được hỗ trợ viên xử lý thủ công bằng `rebind-purchase.js` (mục 12.2), có bằng chứng và người duyệt.

### 12.2 Quy trình hỗ trợ

1. **Xác minh người dùng.** Lấy email đăng ký và ảnh chụp đơn hàng trong Google Play hoặc App Store. Ghi bằng chứng cụ thể: mã đơn, ngày mua, tên sản phẩm.
2. **Tìm `key` của giao dịch.** Firebase console → **Firestore** → collection `entitlementAudit` → lọc trường `uid` bằng uid của tài khoản mới (tài khoản đang bị từ chối khi nhập giao dịch). Mở tài liệu tên dạng `claim-denied_<key>_<giờ>`, đọc trường `key` (dạng `google_...` hoặc `apple_...`) và `reason`.
   - Chỉ bốn lý do được ghi audit: `already-bound`, `owner-deleted`, `token-required`, `legacy-window-closed`.
   - Không có bản ghi audit thì không tự đoán `key`. Báo nhóm kỹ thuật.
3. **Chạy thử (dry run), chưa ghi gì.** Trong PowerShell:

   ```powershell
   cd firebase\functions
   $env:FIREBASE_PROJECT_ID = 'demngayyeuandroid'
   node scripts/rebind-purchase.js --key <KEY> --to-uid <UID_TÀI_KHOẢN_MỚI> --evidence "<bằng chứng: mã đơn, ngày mua, nguồn đối chiếu>" --operator <tên.người.duyệt>
   ```

   Không có `--apply` nghĩa là chỉ kiểm tra và in kế hoạch.
4. **Đọc kết quả.** Nếu `"ok":true` và `"dryRun":true`, kiểm tra `fromUid` (chủ cũ) và `toUid` (chủ mới). Nếu `"ok":false`, đọc `reason`:
   - `invalid-input`: `key` hoặc `uid` sai định dạng (ví dụ có ký tự `/`), hoặc bằng chứng và người thao tác để trống. Nếu CLI in `Dừng: ...` (thiếu hoặc lạ tham số, chưa đặt `FIREBASE_PROJECT_ID`), sửa tham số rồi chạy lại từ bước 3.
   - `unknown-purchase`: không có giao dịch với `key` này.
   - `sandbox-refused`: giao dịch Sandbox. Chỉ chuyển giao dịch Production.
   - `target-account-missing`: tài khoản đích không tồn tại. Kiểm tra lại uid.
   - `too-many-active`: tài khoản đích đã vượt giới hạn giao dịch đang hoạt động. Không ép chuyển.
   - `"ignored":"same-owner"`: giao dịch đã thuộc tài khoản đó, không cần làm gì.
5. **Duyệt.** Chủ dự án duyệt bằng văn bản (email hoặc tin nhắn, lưu lại). Chưa có duyệt thì không làm bước 6.
6. **Ghi thật.** Chạy lại đúng lệnh ở bước 3, thêm `--apply` ở cuối.
7. **Lưu sổ.** Ghi lại `key`, uid cũ, uid mới, bằng chứng, người duyệt, người thao tác và thời điểm. Lệnh tự ghi bản `rebind_<key>_<thời điểm>` vào `entitlementAudit`.

Quyền chạy lệnh: đặt `GOOGLE_APPLICATION_CREDENTIALS` trỏ tới file JSON của tài khoản dịch vụ có quyền Firestore (file đặt ngoài repo), hoặc chạy `gcloud auth application-default login`. Không dán nội dung file vào chat.

### 12.3 Không làm

- Không sửa tay trong Firestore.
- Không chạy `--apply` khi chưa có duyệt.
- Không chuyển giao dịch khi bằng chứng không khớp.

## 13. Xoay khoá ký (chưa thực hiện)

Chỉ làm khi có sự cố lộ khoá hoặc chủ dự án quyết định. Quy tắc theo kế hoạch p6 #15:

1. Phát hành bản app mang khoá công khai MỚI, và vẫn giữ khoá cũ. Chờ phần lớn người dùng cập nhật.
2. Chuyển server sang khoá mới: đặt lại secret `ENTITLEMENT_SIGNING_KEY`, deploy lại.
3. Giữ khoá công khai CŨ trong app thêm 96 giờ sau bước 2. Lý do: 72 giờ là hiệu lực tối đa của token (`tokenTtlMs`), cộng 24 giờ biên an toàn. Con số 96 giờ cũng khớp `maxStaleMs` của server.
4. Sau 96 giờ, bản app kế tiếp bỏ khoá cũ.

**Cảnh báo:** cách chuyển khoá CHƯA được xác nhận. `make-signing-key.js` chỉ ghi khoá riêng thẳng lên Secret Manager. Đặt secret mới có thể làm instance mới dùng khoá mới ngay, trước khi bản app mang khoá mới đến người dùng. Trước lần xoay đầu tiên, nhóm kỹ thuật phải xác nhận quy trình (mục 15). Không chạy lại mục 7 để xoay khoá.

## 14. Xử lý sự cố

Các lý do từ chối (ví dụ `not-ours`, `token-required`) đều trả về mã lỗi `permission-denied`, tên lý do nằm trong thông báo lỗi. Dòng nào ghi mã khác thì mã đó là mã riêng, không phải `permission-denied`.

| Thông báo hoặc triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `store unavailable` (mã `unavailable`) | Tài khoản dịch vụ chưa được mời vào Play Console, Google Play API chưa bật, khoá Apple sai, hoặc store tạm thời lỗi (mạng, quá tải) | Kiểm tra mục 5 và 9.1. Chờ tới 24 giờ sau khi mời. Nếu là lỗi tạm thời thì thử lại sau vài phút. Kiểm tra `APPLE_IAP_KEY_ID`, `APPLE_IAP_ISSUER_ID` và secret `APPLE_IAP_PRIVATE_KEY` |
| `not-ours` | Không tìm thấy giao dịch, hoặc giao dịch không thuộc app này | Kiểm tra `GOOGLE_PACKAGE_NAME`, `APPLE_BUNDLE_ID` và mã giao dịch |
| `already-bound` | Giao dịch đã gắn với tài khoản khác | Mục 12 |
| `token-required` | Giao dịch không khớp mã tài khoản của app (ví dụ app chưa gọi `getAccountToken` trước khi mua, hoặc giao dịch Apple thiếu mã), hoặc giao dịch Google cũ không đủ điều kiện legacy (mục 12.1), hoặc chưa đặt `ACCOUNT_TOKEN_CUTOVER_MS` | Kiểm tra app có gọi `getAccountToken` trước khi mua. Kiểm tra `ACCOUNT_TOKEN_CUTOVER_MS` (mục 10). Với giao dịch Google cũ, làm theo mục 12.2 |
| `legacy-window-closed` | Hết hạn `LEGACY_CLAIM_UNTIL_MS`, chưa đặt hạn, hoặc tài khoản đã dùng legacy đủ 3 lần | Kiểm tra mục 10. Còn lại xử lý ở mục 12 |
| `owner-deleted` | Chủ giao dịch đã xoá tài khoản | Mục 12 |
| `too-many-active` | Tài khoản đã vượt giới hạn giao dịch đang hoạt động. Lý do này không ghi audit | Không ép chuyển và không sửa Firestore. Báo nhóm kỹ thuật kèm uid và mã giao dịch |
| `denied` | Lý do nội bộ khác (ví dụ `invalid-input`) đều hiện ra là `denied`. Không ghi audit nên không có `key` để tra | Báo nhóm kỹ thuật kèm uid, mã giao dịch và thời điểm. Không tự sửa Firestore |
| `invalid-argument` | App gửi sai dữ liệu: `store` không phải `apple` hoặc `google`, hoặc mã giao dịch rỗng hoặc quá 4096 ký tự | Báo nhóm Android |
| `internal` | Lỗi server không phân loại, ví dụ thiếu hoặc sai secret, tham số trong `.env`, hoặc lỗi Firestore | Xem `firebase functions:log --only claimPurchase`, rồi báo nhóm kỹ thuật |
| `family-shared-unsupported` | Giao dịch chia sẻ gia đình chưa được hỗ trợ | Báo người dùng. Không chuyển đổi |
| `unauthenticated` | Người dùng chưa đăng nhập | Đăng nhập lại rồi thử |
| `resource-exhausted` | Gọi hàm quá nhiều lần | Chờ vài phút |
| Hàm từ chối vì App Check | App chưa đăng ký App Check, hoặc bản app cũ | Mục 9.4 |
| `appleNotifications` trả lỗi 500 liên tục | Lỗi phía server | Xem log `firebase functions:log --only appleNotifications`. Apple sẽ gửi lại |

## 15. Việc còn mở

Các điểm sau chưa được xác nhận. Trước khi làm thật, chủ dự án hoặc nhóm kỹ thuật phải chốt:

- **Ngày phát hành bản Android cutover**: chưa có. Hai mốc ở mục 10 để trống cho đến khi có ngày.
- **Cách xoay khoá ký** (mục 13): cần xác nhận cơ chế chuyển secret trước lần xoay đầu tiên.
- **Vị trí `EntitlementPublicKeys` trong app**: chưa có trong mã. Đội Android cần chọn tên lớp hoặc file và cách nạp khoá công khai (`kid` và `publicKeySpkiBase64` ở mục 7).
- **Bundle ID iOS**: kế hoạch ghi `com.aistudio.inlove.kmrv`, chưa đối chiếu với App Store Connect.
- **Tên menu trên các console** (App Store Connect, Play Console, Google Cloud, Firebase): viết theo kinh nghiệm, chưa kiểm chứng. Nếu tên khác thì tìm theo cụm tiếng Anh trong ngoặc.
- **Lệnh `firebase` và `gcloud`** trong tài liệu chưa được chạy thử, vì máy làm tài liệu chưa cài hai công cụ này. Kiểm tra cú pháp ở lần chạy đầu.
- **Quyền tối thiểu của tài khoản dịch vụ trên Play Console** (mục 9.1): xác nhận tên quyền trên console.

## Ghi chú cho người kỹ thuật

- Tên tham số, tên hàm và mã lý do lấy từ `firebase/functions/src/entitlement/`. Đổi tên trong code thì sửa tài liệu này; `test/entitlement/runbook.test.js` sẽ báo đỏ nếu thiếu tên tham số hoặc từ khoá bắt buộc.
