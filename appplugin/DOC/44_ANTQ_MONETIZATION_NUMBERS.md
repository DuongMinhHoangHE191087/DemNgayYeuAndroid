# 44 — Báo cáo số: bộ máy kiếm tiền của game "AnTQ"

> Đọc không cần biết code. Mọi con số dưới đây **đếm được trực tiếp** từ bộ giải mã
> `/Users/dungdm/ghidra_export` (320.081 hàm đã khôi phục tên).
> Cột "nguồn" chỉ ghi tên khối chức năng, không ghi cú pháp.
>
> ⚠️ **Một loại số KHÔNG có trong bộ này**: các giá trị tinh chỉnh (bao nhiêu giây,
> giá bao nhiêu, mở ở level mấy). Lý do và cách lấy ở **mục 12**. Tôi không đoán chúng.

---

## 1. Bảng số tổng — một trang

| Hạng mục | Số | Ghi chú |
|---|---:|---|
| Định dạng quảng cáo đang dùng | **6** | banner, banner thu gọn, MREC, xen kẽ, có thưởng, mở app |
| Mạng quảng cáo hoạt động | **2** | AdMob + AppLovin MAX (khai báo 3, thiếu 1) |
| Điểm mời xem quảng cáo có thưởng | **7** | tất cả đặt sau một thất bại hoặc thiếu hụt |
| Bề mặt bán hàng thật (IAP) | **4** | NoAds, StarterPack, PackWorldCup, Shop |
| Cách thanh toán cho **một** gói trong Shop | **4** | tiền thật / vàng / xem quảng cáo / test |
| Điểm tiêu vàng trong game | **6** | |
| Cơ chế tạo nhu cầu (đứng ngay trước điểm thu) | **10** | |
| Sự kiện LiveOps | **8** | + hệ Team/Clan chạy trên máy chủ |
| Nhóm cấu hình đổi được từ xa | **14** | đổi không cần cập nhật app |
| Khoá tinh chỉnh đóng gói sẵn trong app | **22** | bộ mặc định |
| Ô cấu hình mạng quảng cáo (đổi từ xa) | **29** | 17 + 12 |
| Màn hình popup trong game | **50** | trong đó **14** phục vụ tiền/quảng cáo |
| Màn hình lớp phủ | **19** | trong đó **7** phục vụ tiền/quảng cáo |
| Popup có **nút đóng ẩn** | **4** | mục 6 |
| Hàm gửi số liệu về máy chủ phân tích | **28** | mục 9 |
| Loại phần thưởng phát được từ xa qua Hộp thư | **11** | **gồm cả NoAds và tim vô hạn** |
| Nhóm người chơi được đối xử khác nhau khi chiếu quảng cáo | **1** | không phân nhóm — mục 8 |
| Dịch vụ máy chủ riêng | **11** | 87 kiểu dữ liệu |

---

## 2. Sáu định dạng quảng cáo — khối lượng kỹ thuật mỗi loại

Số hàm dành riêng cho từng định dạng cho biết **họ đầu tư vào cái nào nhiều nhất**:

| Định dạng | Số hàm | Chiếu ở đâu |
|---|---:|---|
| **Banner thu gọn** (collapsible) | 20 | thường trực, tự làm mới theo chu kỳ, có hạn hiển thị |
| **Banner thường** | 18 | thường trực; **giao diện có 2 vị trí, tự dịch chuyển khi có/không banner** |
| **Mở app** | 16 | lúc mở app **và lúc quay lại app**; có màn loading che lúc chiếu |
| **Xen kẽ** (interstitial) | 15 | có màn hình đệm riêng + lớp phủ đếm ngược "Ads Break" |
| **Có thưởng** (rewarded) | 12 | 7 điểm — bảng mục 3 |
| **MREC** | 11 | có đủ vòng đời nhưng **không tìm thấy màn hình nào gắn vào** |

**Tổng bộ máy quảng cáo: 389 hàm**
- điều phối chung: 130
- bộ nối AdMob: 98
- bộ nối MAX: 82
- lớp trừu tượng dùng chung: 37
- cổng kiểm soát phía game: 42

**Mạng quảng cáo:** khai báo 3 (AdMob, MAX, IronSource) nhưng **IronSource chỉ còn 2 hàm sót lại** — ô cấu hình còn nguyên, bộ thư viện đã bị gỡ. Nếu cấu hình từ xa lỡ trỏ sang IronSource thì mất toàn bộ quảng cáo.

---

## 3. Bảy điểm mời xem quảng cáo có thưởng

| # | Người chơi đang ở tình huống nào | Đổi được gì |
|---|---|---|
| 1 | Thua vì bàn bị khoá cứng | chơi tiếp |
| 2 | Thua vì hết ô chứa | chơi tiếp |
| 3 | Vừa thắng, đang xem phần thưởng | **nhân đôi vàng** |
| 4 | Hết tim, không vào chơi được | thêm tim |
| 5 | Đang trong màn, thiếu vật phẩm hỗ trợ | thêm vật phẩm |
| 6 | Vừa thua nhiều lần liên tiếp | vàng / vật phẩm (popup 4 ô) |
| 7 | Mở gói thẻ sưu tập | thẻ |
| + | Trong Shop | đổi lấy nội dung gói |

**Cả 7 đều đặt sau một thất bại hoặc một thiếu hụt.** Không có điểm nào mời xem lúc người chơi đang thoải mái.

Số lần đã xem được đếm riêng cho từng loại: số lần hồi sinh bằng quảng cáo, số lần nhận vật phẩm bằng quảng cáo, số quảng cáo đã xem trong màn.

---

## 4. Bốn bề mặt bán hàng thật

| # | Bề mặt | Xuất hiện ở đâu | Đặc điểm |
|---|---|---|---|
| 1 | **Gói bỏ quảng cáo** | nút cố định trên Trang chủ **+** popup tự bung ở Trang chủ | popup chỉ bung **sau khi người chơi đã xem đủ số quảng cáo xen kẽ**; có hạn mức số lần/ngày |
| 2 | **Gói khởi đầu** | Trang chủ | |
| 3 | **Gói sự kiện World Cup** | popup riêng | **có đồng hồ đếm ngược chạy liên tục**, chứa **nhiều gói trong một màn**, có 2 nút đóng |
| 4 | **Cửa hàng** | popup Shop | danh sách gói, nạp lại nội dung động |

### Một gói bán được bằng 4 đường
```
Gói trong Shop
   ├─ trả bằng TIỀN THẬT
   ├─ trả bằng VÀNG trong game
   ├─ trả bằng XEM QUẢNG CÁO
   └─ chế độ test
```
Đây là điểm bán chéo trung tâm: cùng một món hàng, ai có tiền thì trả tiền, ai không thì trả bằng thời gian xem quảng cáo.

### Phễu gói — điều kiện mở khoá
Có một hệ điều kiện cho gói, và trong toàn bộ app **chỉ cài đặt đúng 1 loại điều kiện**:
> **"Chỉ hiện gói B sau khi đã mua gói A đủ n lần."**

Nút "Bỏ quảng cáo" cũng dùng chính hệ điều kiện này. Giá trị `n` và danh sách gói: chưa đọc được (mục 12).

---

## 5. Sáu điểm tiêu vàng + mười cơ chế tạo nhu cầu

### Tiêu vàng

| # | Điểm | Đặc điểm |
|---|---|---|
| 1 | Hồi sinh trong màn | **giá tăng dần theo số lần hồi sinh trong cùng một màn** |
| 2 | Mua vật phẩm hỗ trợ | giá đổi được từ xa, có cờ bật/tắt giá từ xa riêng |
| 3 | Mua đầy tim | |
| 4 | Mua tăng tốc giữa màn | |
| 5 | Mua thưởng trong popup "quảng cáo đổi vàng" | cùng popup vừa mời xem quảng cáo vừa mời trả vàng |
| 6 | Đổi sao thẻ sưu tập | |

Mọi khoản tiêu đều được ghi lại kèm **nhãn nguồn** (tiêu vào việc gì) — nền tảng cho báo cáo dòng tiền trong game.

### Mười cơ chế tạo nhu cầu

| # | Cơ chế | Cách hoạt động |
|---|---|---|
| 1 | **Hệ tim** | hết tim ⇒ chờ hồi / mua / xem quảng cáo / xin bạn. Có tim tự hồi theo thời gian, có mốc tim tối đa |
| 2 | **Xin tim từ Team** | vòng xã hội thay cho mua — có thời gian chờ giữa 2 lần xin |
| 3 | **Đếm số lần thua** | thua đủ số lần ⇒ bung popup mời quảng cáo/vàng |
| 4 | **Cảnh báo màn khó** | hiện nhãn "Khó" / "Rất khó" kèm hoạt ảnh riêng, ngay trong màn chơi |
| 5 | **Gợi ý dùng vật phẩm theo chu kỳ** | game chủ động nhắc dùng vật phẩm lặp đi lặp lại trong màn |
| 6 | **Nút "+" thêm vật phẩm** | dẫn thẳng sang mua/xem quảng cáo |
| 7 | **Ép dùng vật phẩm** | có công tắc **bật/tắt từ xa** để ép người chơi tiêu vật phẩm |
| 8 | **Vật phẩm khoá theo level** | giao diện hiện sẵn "mở ở level ..." ⇒ tạo mục tiêu |
| 9 | **Vàng khởi tạo điều chỉnh từ xa** | nắn hành vi tiêu ngay từ phút đầu |
| 10 | **Chuỗi thắng** | thắng liên tiếp mới lên nấc; **thua là mất chuỗi** ⇒ tạo động lực hồi sinh |

---

## 6. Bốn popup có "nút đóng ẩn"

Bốn màn hình này có **hai** nút đóng: một nút thường và một nút được đặt tên là *nút đóng ẩn*.

| Popup | Bán/mời gì |
|---|---|
| Gói bỏ quảng cáo | tiền thật |
| Gói khởi đầu | tiền thật |
| Hết tim | vàng hoặc quảng cáo |
| Điểm danh hằng ngày | phần thưởng |

**Việc tồn tại 2 nút là chắc chắn.** Nút ẩn hiện ra sau bao nhiêu giây thì **chưa đọc được** — cần bước ở mục 12. Đây là hạng mục nên kiểm tra đầu tiên vì liên quan quy định cửa hàng.

---

## 7. Tám sự kiện LiveOps

| Sự kiện | Số hàm | Cơ chế chính | Mở khoá |
|---|---:|---|---|
| **ShipRace** (đua thuyền) | 83 | 3 chặng, đua theo điểm, **đối thủ là bot do máy mô phỏng** (có cấu hình số lượng bot, tốc độ về đích của bot, tên "người" thắng cuộc) | công tắc mở khoá cấu hình từ xa |
| **AntLab** (phòng thí nghiệm kiến) | 69 | nhiệm vụ nhiều loại, chấm điểm **theo độ khó của màn** và **theo việc thắng ngay lần đầu**; có ngày bắt đầu/kết thúc | như trên |
| **SkyJump** | 54 | 3 chặng, có **thời gian nghỉ bắt buộc giữa các chặng**, có thứ hạng | như trên |
| **CardCollection** (sưu tập thẻ) | 54 | **mùa vụ** (có mở mùa mới, reset sang mùa sau), **mở gói thẻ ngẫu nhiên có tỉ lệ thay đổi theo lượt**, rương sao, đổi sao | như trên |
| **StarBlaze** | 50 | tích điểm theo **chuỗi thắng**, có **giai đoạn nhân đôi điểm** | như trên |
| **UfoStreak** | 43 | thắng/thua đẩy tiến độ; **có xử lý riêng cho tình huống thua ở nấc cuối** | như trên |
| **CastleClimb** (leo tháp) | — | leo theo level, có đồng hồ hết sự kiện | như trên |
| **WeeklyContest** (giải tuần) | — | **chạy trên máy chủ thật**, chia nhóm, có phần thưởng chờ nhận | máy chủ |

Ngoài ra: **Điểm danh hằng ngày** (11 hàm) và **hệ Team/Clan** trên máy chủ: trò chuyện, **xin/tặng thẻ**, **xin tim**, tặng quà, xếp hạng, tìm & gợi ý team.

### ⚠️ Về câu hỏi "sự kiện mở ở level bao nhiêu"

Cả 8 sự kiện đều mở qua **một công tắc mở khoá**, và **ngưỡng nằm trong cấu hình từ xa, không nằm trong app**. Con số đó không có trong bộ giải mã này — tôi không đoán.

Điều **đọc được chắc chắn**: có 4 mốc level được đóng gói sẵn trong app, điều khiển 4 việc khác:

| Mốc level điều khiển | Có tên khoá | Có giá trị |
|---|---|---|
| Bắt đầu chiếu quảng cáo xen kẽ | ✅ | ❌ |
| Mở tính năng nhân đôi vàng cuối màn | ✅ | ❌ |
| Bắt đầu cho quay về menu chính | ✅ | ❌ |
| Bắt đầu xin đánh giá cửa hàng | ✅ | ❌ |

---

## 8. Phân nhóm người chơi: **1 nhóm**

Đã rà toàn bộ 320.081 hàm: **không có bất kỳ thành phần nào** mang nghĩa "nhóm người chơi", "biến thể", "thử nghiệm A/B", "nhóm đối chứng". Máy chủ riêng cũng không có kiểu dữ liệu nào như vậy trong 87 kiểu.

Thay vào đó là **4 công tắc trạng thái** trên cùng một bộ quy tắc:

| Công tắc | Tác dụng |
|---|---|
| Đã mua gói bỏ quảng cáo | tắt nhánh quảng cáo |
| Đã vượt mốc level | mở quảng cáo xen kẽ |
| Đã từng xem quảng cáo xen kẽ lần đầu chưa | có nhánh xử lý riêng cho lần đầu |
| Vừa mở lại app | bỏ qua N quảng cáo đầu |

Cộng thêm: **hạn mức quảng cáo thay đổi theo level** (không theo người chơi).

### Nhưng CÓ phân theo QUỐC GIA — ở tầng báo cáo doanh thu

Có một khối riêng của studio (50 hàm) chuyên bắn **tín hiệu doanh thu cho thuật toán đấu giá quảng cáo mua người dùng**. Khối này:

- **lấy mã quốc gia của máy và chuẩn hoá nó, ngay bên trong khối cấu hình**
- có **bảng tỉ giá riêng** để quy mọi doanh thu về USD
- có **hai bộ ngưỡng chạy song song** (hai kiểu sự kiện khác nhau)
- **tách riêng ngưỡng cho quảng cáo có thưởng** khỏi quảng cáo thường
- có một bộ ngưỡng riêng cho **doanh thu bán hàng thật**
- nạp cấu hình qua **hai kênh song song**: một kênh chính thống + **một kênh HTTP tự viết chạy tự động nền**

⇒ Họ **không** cá nhân hoá trải nghiệm theo quốc gia, nhưng **có** tinh chỉnh tín hiệu mua người dùng theo quốc gia. Đây là phần tinh vi nhất trong toàn bộ hệ thống kiếm tiền của họ.

---

## 9. Đo lường: 28 hàm gửi số liệu

| Nhóm | Số hàm | Nội dung |
|---|---:|---|
| **Phễu quảng cáo** | 8 | bấm nút → tải xong → hiện ra → đếm lượt, **làm riêng cho xen kẽ và có thưởng** |
| **Kinh tế trong màn** | 20 | vàng nhận/tiêu, vật phẩm nhận/tiêu, tài nguyên nhận/tiêu — **tất cả kèm nhãn nguồn** |
| | | bắt đầu màn, hoàn thành màn, thất bại màn, mốc kiểm tra, hộp bắt đầu/hoàn thành/thất bại |
| **Doanh thu quảng cáo từng lượt hiển thị** | — | cả 2 mạng quảng cáo đều báo, gộp về một điểm rồi tỏa sang 2 nơi nhận |
| **Doanh thu bán hàng** | — | gửi tới hệ đo lường + hệ phân tích; **không gửi về máy chủ riêng của game** |

Phễu 4 nấc cho phép tách bạch: bao nhiêu người **muốn** xem / bao nhiêu lần **có** quảng cáo sẵn / bao nhiêu lần **thực sự hiện ra**. Đây là mức đo chuẩn, không phải chỉ đếm lượt hiển thị.

---

## 10. Hộp thư — kênh phát thưởng từ xa (11 loại)

Có một hệ Hộp thư **tải nội dung từ máy chủ**, **nhắm theo mốc level**, phát được 11 loại:

| Nhóm | Phát được gì |
|---|---|
| Tiền tệ | vàng |
| Tim | tim thường · **tim vô hạn có thời hạn** |
| Vật phẩm | máy hút · chong chóng · xáo bàn · ô chứa thêm |
| **Quyền lợi trả phí** | **gói bỏ quảng cáo** |
| Trang trí | khung · ảnh đại diện · huy hiệu |

**Ý nghĩa kinh doanh:** họ có thể **tặng miễn phí gói bỏ quảng cáo và tim vô hạn cho bất kỳ ai, từ xa, nhắm theo level** — dùng để đền bù sự cố, kéo người bỏ game quay lại, hoặc thưởng sự kiện, mà không cần cập nhật app.

Tim vô hạn **không thấy bán trực tiếp** ở bất kỳ bề mặt nào trong app; nó chỉ đến từ Hộp thư và từ nhóm tài nguyên phần thưởng.

---

## 11. Bốn mươi ba ô điều khiển từ xa

### 14 nhóm cấu hình (đổi không cần cập nhật app)

| Nhóm | Điều khiển |
|---|---|
| Hạn mức quảng cáo xen kẽ | hạn mức **theo từng level**; số quảng cáo bỏ qua khi mở lại app |
| Popup bán gói bỏ quảng cáo | bật/tắt |
| Popup quảng cáo đổi vàng | bật/tắt |
| Vàng khởi tạo | số vàng người chơi mới nhận |
| Hồi sinh | **giá hồi sinh bằng vàng** và **số lần hồi sinh bằng quảng cáo** |
| Thưởng cuối màn | thưởng theo độ khó; bật/tắt nhân đôi bằng quảng cáo |
| Vật phẩm hỗ trợ | số quảng cáo đổi 1 vật phẩm; **giá vật phẩm**; công tắc bật giá từ xa |
| Ép dùng vật phẩm | bật/tắt |
| AntLab | lịch chạy sự kiện |
| Level test | danh sách level thử nghiệm |
| ShipRace · SkyJump · StarBlaze · CastleClimb | tham số 4 sự kiện |

### 29 ô cấu hình mạng quảng cáo

- **17 ô** ở khối cấu hình chung: khoá SDK, **toàn bộ mã đơn vị quảng cáo của cả 3 mạng**, chọn mạng cho từng định dạng
- **12 ô** ở khối AdMob: 6 định dạng × 2 kiểu — một kiểu là **lịch xoay vòng mã quảng cáo**, một kiểu là danh sách dự phòng

⇒ **Họ đổi được mã đơn vị quảng cáo và giá sàn theo lịch, từ xa, không cần cập nhật app.**

### 22 khoá tinh chỉnh đóng gói sẵn trong app

Bộ mặc định gồm 22 khoá, trong đó liên quan tiền/quảng cáo: mốc level bắt đầu chiếu quảng cáo · số lần hiện popup bỏ quảng cáo · hạn mức popup bỏ quảng cáo · công tắc dùng quảng cáo · công tắc hệ tim · thời gian hồi tim · số tim tối đa · giá mua đầy tim · vàng thưởng thắng màn · vàng hồi sinh · số ô hồi sinh · mốc level nhân đôi vàng · độ trễ hiện màn thua · công tắc xem quảng cáo để hồi tim · mốc level xin đánh giá · tên chiến dịch · mã chiến dịch.

**Tên 22 khoá đọc được. Giá trị thì chưa** (mục 12).

---

## 12. Vì sao chưa có các con số tinh chỉnh, và cách lấy

### Lý do (đã kiểm chứng, không phải phỏng đoán)

Bộ `ghidra_export` này chứa **danh mục đầy đủ mọi chức năng** nhưng **thiếu 2 thứ**:

1. **Phần thân của các hàm đã có tên** — bộ xuất bị thiếu một tệp. Kiểm chứng: địa chỉ của hàm "cài đặt quảng cáo xen kẽ" chỉ xuất hiện ở danh mục, không có ở bất kỳ tệp mã nào.
2. **Toàn bộ chuỗi văn bản** (tên khoá cấu hình, mã đơn vị quảng cáo, mã sản phẩm, giá) — chúng nằm trong một tệp dữ liệu riêng của Unity **không có trong bộ xuất này**. Kiểm chứng: tìm chuỗi `ca-app-pub` (tiền tố mọi mã quảng cáo AdMob) trong toàn bộ 73 MB mã ⇒ **0 kết quả**.

### Danh sách con số đang thiếu

| Thiếu gì | Bao nhiêu mục |
|---|---:|
| Level mở khoá của từng sự kiện | 8 |
| 22 giá trị của bộ khoá tinh chỉnh | 22 |
| Hạn mức & thời gian chờ giữa 2 quảng cáo (giây) | ~6 |
| Mã đơn vị quảng cáo + khoá SDK | 29 ô |
| Danh sách mã sản phẩm và giá từng gói | ~10 |
| Nội dung từng gói trong Shop | ~10 |
| Tỉ lệ rơi thẻ sưu tập + cơ chế bảo hiểm | 1 bảng |
| Ngưỡng doanh thu theo quốc gia | 1 bảng |
| Số giây của nút đóng ẩn | 4 |
| Số lần thua để bung popup | ~3 |

### Cách lấy — một bước, khoảng 15 phút

```
Cần: tệp APK / XAPK gốc của game
Chạy: Il2CppDumper  (công cụ miễn phí, nguồn mở)
      đầu vào  = global-metadata.dat  +  libil2cpp.so   (đều nằm trong APK)
      đầu ra   = dump.cs  +  stringliteral.json
Kết quả: TOÀN BỘ bảng trên chuyển từ "chưa có" sang "có số cụ thể"
```

Ngoài ra, tệp `res/xml/` và `AndroidManifest.xml` trong APK cho thêm: giá trị mặc định của cấu hình từ xa, mã ứng dụng AdMob, danh sách quyền.

**Anh gửi APK, tôi điền số vào đúng những ô đang trống ở trên và trả lại bản này có đủ số.**

---

## 13. Bảy điểm đáng chú ý nhất về mặt kinh doanh

| # | Phát hiện | Vì sao đáng chú ý |
|---|---|---|
| 1 | **Toàn bộ 7 điểm quảng cáo có thưởng đều đặt sau thất bại/thiếu hụt** | doanh thu quảng cáo gắn chặt vào độ khó — chỉnh độ khó là chỉnh doanh thu |
| 2 | **Một gói bán được bằng 4 đường** (tiền/vàng/quảng cáo/test) | không bỏ sót người không trả tiền |
| 3 | **Phễu gói: mua A đủ n lần mới hiện B** | và đây là **loại điều kiện duy nhất** họ cài — dư địa mở rộng còn rất lớn |
| 4 | **Hộp thư phát được cả gói bỏ quảng cáo lẫn tim vô hạn, từ xa, nhắm theo level** | công cụ kéo người quay lại mạnh nhất trong hệ thống |
| 5 | **Xoay mã đơn vị quảng cáo theo lịch, từ xa** | chỉnh giá sàn không cần cập nhật app |
| 6 | **Tầng tín hiệu doanh thu theo quốc gia, có bảng tỉ giá riêng, 2 bộ ngưỡng song song, tách riêng quảng cáo có thưởng** | đây là chỗ họ đầu tư tinh vi nhất — phục vụ đấu giá mua người dùng |
| 7 | **Không phân nhóm người chơi khi chiếu quảng cáo** (1 nhóm duy nhất) | mọi người chơi ăn cùng một mật độ quảng cáo; đồng thời là dư địa cải thiện lớn nhất |

---

*Nguồn: `/Users/dungdm/ghidra_export` · 320.081 hàm đã khôi phục tên · Unity 6.3, Android.
Chi tiết kỹ thuật kèm trích dẫn dòng: doc 42 và doc 43.*
