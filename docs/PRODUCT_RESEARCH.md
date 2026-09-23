# PRODUCT_RESEARCH.md — InLove: Hành vi người dùng & Nghiên cứu thị trường

> Task E1 trong `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`. Tài liệu sản phẩm — không phải code, dùng làm roadmap sau khi các Task kỹ thuật (Phần A-D) hoàn thành.

## 1. Đối tượng người dùng & hành vi (đã có trong `PRODUCT.md`, xác nhận lại)

InLove nhắm đến các cặp đôi Gen Z và millennials Việt Nam, hành vi lõi là **mở app hằng ngày để xem số ngày yêu tăng dần** — đây là hook giữ chân chính, đã đúng hướng, KHÔNG cần đổi. Nghiên cứu thị trường 2026 cho các app tương tự xác nhận đây vẫn là mô hình đúng: các app "ngày yêu" tồn tại lâu năm và được tải nhiều (Been Together, The Couple — hơn 1 triệu lượt đánh giá) đều xoay quanh chính xác cơ chế này.

## 2. Đối thủ tham khảo

### Thị trường Việt Nam
| App | Điểm mạnh được người dùng nhắc đến |
|---|---|
| **Been Together** | Giao diện đẹp, nhiều theme lãng mạn, tùy chỉnh bố cục/mốc sự kiện linh hoạt |
| **Been Love Memory** | App đầu tiên tại VN hỗ trợ tiếng Việt đầy đủ; đếm thời gian yêu theo từng phút; tích hợp cung hoàng đạo hằng ngày |
| **The Couple** | Phổ biến nhất, đánh giá tích cực cao (>1 triệu lượt) |
| **Lovedays** | Mạnh về lưu & nhắc ngày kỷ niệm, không bỏ lỡ dịp đặc biệt |
| **Day Together** | Do lập trình viên Việt Nam làm, tối ưu cho thói quen người Việt |

### Thị trường quốc tế (2026)
| App | Điểm khác biệt |
|---|---|
| **Amora** | 1 câu hỏi hằng ngày cho cặp đôi, nhật ký chung riêng tư, gói Pro dùng chung 1 subscription cho cả 2 người |
| **Between** | Không gian riêng tư kết nối hằng ngày, đồng bộ lịch chung, không bỏ lỡ sinh nhật/kỷ niệm |
| **Locket** | Widget màn hình khoá/màn hình chính — ảnh cập nhật trực tiếp lên home screen của bạn đời |
| **Agapé, Love Nudge** | Câu hỏi/chương trình do chuyên gia trị liệu thiết kế, theo dõi thói quen quan tâm nhau |
| **Waffle, Flamme, Lasting, Cupla** | Đa dạng hoá: hỏi đáp cặp đôi, theo dõi thói quen, lên kế hoạch hẹn hò |

**Nhận định quan trọng:** khoá bằng sinh trắc học (vân tay/Face ID) vẫn hiếm ngay cả ở các app quốc tế năm 2026 — PIN vẫn là chuẩn phổ biến nhất. InLove hiện dùng PIN 4 số (đúng xu hướng thị trường), nhưng thiếu giới hạn số lần thử sai (đã ghi trong Task bảo mật A-phần, cần khoá tạm sau nhiều lần sai).

## 3. Khoảng trống tính năng của InLove so với thị trường

Đối chiếu với bộ tính năng InLove hiện có (đã khảo sát codebase):

| Tính năng đối thủ có | InLove hiện tại | Đánh giá |
|---|---|---|
| Widget màn hình khoá/chính kiểu Locket | Không có widget Android nào (chỉ có trong app) | **Cơ hội lớn nhất** — đúng đúng hành vi lõi "mở lên xem mỗi ngày", một widget home-screen hiển thị số ngày yêu sẽ giảm ma sát còn 0 lần chạm |
| Câu hỏi/nhật ký hằng ngày cho cặp đôi (Amora, Between) | Có "Sweet Note" và ghi chú kỷ niệm nhưng không có luồng hỏi-đáp hằng ngày có cấu trúc | Cơ hội tăng engagement hằng ngày ngoài việc chỉ xem số ngày |
| Đồng bộ lịch chung thật sự (Between) | `OnlineCoupleRepository` hiện chỉ hoạt động cục bộ trong Room (xem Task B1) — ghép đôi 2 máy thật CHƯA hoạt động | **Bug chặn use-case cốt lõi "Set Love 1-1"** — ưu tiên sửa trước khi làm thêm tính năng mới |
| Theme/giao diện tuỳ chỉnh phong phú (Been Together) | Đã có theme "Dreamy Romantic Rose" nhất quán, wallpaper picker — nhưng wallpaper chọn xong không lưu (Task B6) | Gần đạt chuẩn thị trường, chỉ cần vá lỗi lưu trữ |
| Cung hoàng đạo hằng ngày (Been Love Memory) | Có `zodiac` trong `ProfileUtils` nhưng chưa thấy màn hình hiển thị riêng | Tính năng nhỏ, có thể làm sau |
| Lịch âm Việt Nam tích hợp | Không có (trước Task D1 của kế hoạch này) | Đã bổ sung — xem `VietnameseHolidays.kt` |

## 4. Ưu tiên đề xuất (không lặp lại các Task kỹ thuật đã có trong kế hoạch chính)

1. **Sửa pairing 2 máy thật (Task B1)** trước khi quảng bá tính năng "Set Love 1-1" — đây là lời hứa giá trị cốt lõi của app, hiện không hoạt động đúng như mô tả.
2. **Home-screen widget** (Glance API cho Jetpack Compose, Android 12+) hiển thị số ngày yêu — đầu tư một lần, tận dụng đúng hành vi "mở lên xem mỗi ngày" đã được xác nhận là mô hình thành công qua Been Together/The Couple. Đây là hạng mục MỚI, chưa có trong kế hoạch kỹ thuật A-F — đề xuất thêm thành Task G nếu chủ dự án đồng ý, ước lượng độ phức tạp trung bình (AppWidgetProvider hoặc Glance, không đụng vào luồng chính).
3. Cân nhắc thêm 1 câu hỏi/ngày cho cặp đôi (kiểu Amora/Between) như tính năng VIP để tăng lý do nâng cấp gói trả phí, tận dụng hạ tầng `shared_memories`/Firestore đã có sẵn.

## 5. Nguồn tham khảo

- [Top 10 ứng dụng đếm ngày yêu — CellphoneS](https://cellphones.com.vn/sforum/app-dem-ngay-yeu)
- [App đếm ngày yêu — Điện Thoại Vui](https://dienthoaivui.com.vn/app-ung-dung-dem-ngay-yeu-nhau-online)
- [Top 3 ứng dụng đếm ngày yêu — Mytour](https://mytour.vn/vi/blog/bai-viet/top-3-ung-dung-dem-ngay-yeu-pho-bien-nhat-danh-cho-cac-cap-doi.html)
- [Best Apps for Couples in 2026 — Amora](https://tryamora.app/blog/best-apps-for-couples-2026)
- [Best Relationship Tracker Apps in 2026 — DaterGraph](https://datergraph.me/blog/modern-dating-culture/best-relationship-tracker-apps-2026/)
- [14 Best Relationship Apps for Couples in 2026](https://www.excellentwebworld.com/best-relationship-apps/)
