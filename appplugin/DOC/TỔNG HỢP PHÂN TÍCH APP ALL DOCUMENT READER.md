TỔNG HỢP PHÂN TÍCH APP "ALL DOCUMENT READER"
=============================================

App:      All Document Reader (com.solara.alldocumentreader.officepdfwordexcel)
Bản phân tích: 1.5.3-Doc-Solara (version code 53)
Nguồn:    file XAPK tải từ APKPure, gốc là bản phát hành chính thức trên Google Play
Ngày:     13/08/2026


────────────────────────────────────────────────────────────────
LƯU Ý QUAN TRỌNG TRƯỚC KHI ĐỌC
────────────────────────────────────────────────────────────────

Tất cả thông số cấu hình trong tài liệu này là GIÁ TRỊ MẶC ĐỊNH được đóng
gói sẵn trong file cài đặt. App dùng Firebase Remote Config, nghĩa là giá trị
thật đang chạy trên máy người dùng do server quyết định và CÓ THỂ KHÁC HOÀN
TOÀN so với những gì đọc được từ file cài.

Giá trị mặc định chỉ được dùng trong 2 trường hợp: lần mở app đầu tiên trước
khi kịp tải config, hoặc khi kết nối tới Firebase thất bại.

=> Việc đầu tiên cần làm là mở Firebase Console và đối chiếu 22 nhóm cấu hình
   với BẢNG GIÁ TRỊ MẶC ĐỊNH Ở MỤC 11. Không thể kết luận "tính năng X đang
   tắt" chỉ từ file cài đặt.

Những phần KHÔNG phụ thuộc server (đọc từ file cài là chắc chắn đúng): kiến
trúc code, danh sách vị trí quảng cáo, thư viện tích hợp, quyền truy cập, và
2 lỗi logic ở mục RỦI RO.


────────────────────────────────────────────────────────────────
1. APP NÀY LÀ GÌ
────────────────────────────────────────────────────────────────

Ứng dụng đọc và xử lý tài liệu (PDF, Word, Excel, PowerPoint, HWP), kèm bộ
công cụ: scan tài liệu, OCR nhận dạng chữ, dịch, gộp/tách/chuyển đổi PDF,
ảnh sang PDF.

Xây trên nền engine thương mại Artifex SmartOffice (bản có license). Đây là
tài sản đáng giá — engine này là thứ khiến app đọc được file Office đúng
định dạng, khác biệt so với các app đối thủ dùng thư viện miễn phí.

Quy mô: file cài 172 MB, khoảng 50.000 class code, 54 màn hình.
Hỗ trợ 60 ngôn ngữ. Target Android API 36 (đang là mức mới nhất Google yêu
cầu — không cần lo về deadline nâng target SDK).


────────────────────────────────────────────────────────────────
2. ĐIỂM QUAN TRỌNG NHẤT: GẦN NHƯ TOÀN BỘ QUẢNG CÁO ĐIỀU KHIỂN
   ĐƯỢC TỪ SERVER, KHÔNG CẦN CẬP NHẬT APP
────────────────────────────────────────────────────────────────

Đây là năng lực mạnh nhất của hệ thống hiện tại và là nền tảng cho mọi kế
hoạch tối ưu doanh thu.

Có 22 nhóm cấu hình trên Firebase điều khiển được:

  - Bật/tắt từng vị trí quảng cáo riêng lẻ (58 vị trí)
  - Đổi ad unit ID của từng vị trí
  - Toàn bộ giao diện quảng cáo native: hơn 90 thuộc tính (màu sắc, bo góc,
    cỡ chữ, khoảng cách, tỉ lệ ảnh, kiểu đếm ngược, style icon, màu nút CTA...)
  - Tần suất hiển thị, khoảng cách giữa 2 lần hiện quảng cáo
  - Thời lượng màn splash, loại quảng cáo hiện ở splash
  - Giá gói IAP và toàn bộ hành vi paywall
  - Chặn quảng cáo theo quốc gia
  - Nội dung và lịch gửi thông báo đẩy hàng ngày

Ý nghĩa với PO: có thể A/B test giao diện quảng cáo, đổi giá, bật/tắt vị trí,
điều chỉnh tần suất — tất cả trong vòng vài phút, không cần dev, không cần
chờ Google review. Đây là thứ nên khai thác trước khi nghĩ đến việc sửa code.


────────────────────────────────────────────────────────────────
3. HỆ THỐNG QUẢNG CÁO ĐANG CÓ
────────────────────────────────────────────────────────────────

5 mạng quảng cáo chạy song song:
  - Google AdMob (chính)
  - ByteDance Pangle
  - Meta Audience Network
  - Mintegral
  - Bigo Ads

58 vị trí quảng cáo, chia 4 loại:

  Loại              Số vị trí   Xuất hiện ở đâu
  ─────────────────────────────────────────────────────────────
  App Open          4           Mở app lần đầu / mở lại / quay lại sau 1 giờ
  Interstitial      22          Sau splash, mỗi lần bấm Back, sau khi hoàn
                                thành tác vụ (gộp/tách/chuyển đổi xong)
  Banner            14          Đáy 12 màn hình công cụ + 1 quảng cáo nổi
  Native            18          Màn chọn ngôn ngữ, intro, home, settings,
                                trang cuối file, sau mỗi interstitial

Điểm đáng chú ý về mật độ: 22 trong 58 vị trí là interstitial (quảng cáo toàn
màn hình), phần lớn gắn vào nút Back và vào thời điểm user vừa hoàn thành
việc. Ngoài ra mỗi interstitial khi đóng lại còn hiện tiếp một quảng cáo
native toàn màn (cấu hình "native_after_inter"). Tức một lần bấm Back có thể
dẫn tới 2 quảng cáo liên tiếp.


────────────────────────────────────────────────────────────────
4. CƠ CHẾ "3 TẦNG GIÁ" (HIGH FLOOR WATERFALL)
────────────────────────────────────────────────────────────────

Đây là phần kỹ thuật đáng giá nhất, giải thích bằng ngôn ngữ kinh doanh:

Mỗi vị trí quảng cáo có thể khai báo 3 ad unit khác nhau thay vì 1:

  Tầng H (High floor)   - đặt giá sàn cao. Ăn nhiều tiền hơn mỗi lượt hiện,
                          nhưng tỉ lệ có quảng cáo (fill rate) thấp.
  Tầng M (Medium floor) - giá sàn trung bình.
  Tầng D (Default)      - không đặt sàn. Luôn có quảng cáo, giá thấp nhất.

Cách app xử lý (hoàn toàn tự động):

  1. Mỗi lần cần quảng cáo, thử tầng H trước.
  2. H không có quảng cáo → app ghi nhớ "tạm khóa H trong N giây" và tự
     chuyển xuống M.
  3. M cũng không có → khóa M, chuyển xuống D.
  4. Hết thời gian khóa → tự động thử lại từ H.

Thời gian khóa cấu hình được: mặc định trong file cài là khóa H 150 giây,
khóa M 75 giây (với App Open); các loại khác dùng giá trị dự phòng 60s/30s.

Lợi ích: ăn được inventory giá cao khi có, mà không mất fill rate vì tầng D
luôn đỡ phía dưới. Không làm chậm app vì không phải thử cả 3 tầng mỗi lần —
app nhớ tầng nào đang khóa.

Hệ thống này ĐÃ ĐƯỢC XÂY DỰNG HOÀN CHỈNH trong code. Việc bật hay không chỉ
là điền ad unit ID tầng H và M vào Firebase Remote Config.

Trong file cài, phần cấu hình này chỉ có tầng D (1 ad unit cho mỗi loại).
CẦN KIỂM TRA trên Firebase xem tầng H và M đã được điền chưa. Nếu chưa, đây
là cơ hội tăng doanh thu lớn nhất và không cần dev.

Ngoài ra app còn có cơ chế chia sẻ quảng cáo giữa các vị trí (một quảng cáo
tải về dùng cho nhiều màn hình) để tiết kiệm số lượt request — ví dụ 9 màn
công cụ đang dùng chung 1 banner, 3 biến thể màn splash dùng chung 1 native.


────────────────────────────────────────────────────────────────
5. PHÂN TẦNG GIÁ THEO QUỐC GIA
────────────────────────────────────────────────────────────────

Code đã map sẵn khoảng 160 quốc gia thành 3 nhóm:

  Tier 1 - Mỹ, Canada, Anh, Úc, Đức, Pháp, Bắc Âu, Nhật, Hàn...
  Tier 2 - Trung Âu, Đông Âu, Nam Mỹ, Trung Đông...
  Tier 3 - Việt Nam, Ấn Độ, Pakistan, Bangladesh, châu Phi...

MỌI nhóm cấu hình đều override được theo tier. Nghĩa là có thể set:
  - Giá IAP khác nhau theo tier (đã có sẵn: 3 gói 20/25/30)
  - Tần suất quảng cáo khác nhau theo tier
  - Ad unit khác nhau theo tier
  - Giao diện quảng cáo khác nhau theo tier

Về IAP, cấu hình mặc định là gói trial 3 ngày với 3 mức giá:
  Tier 1 → gói "3days.30"
  Tier 2 → gói "3days.25"
  Mặc định → gói "3days.20"

Trong file cài, nhóm cấu hình phân tier đang để rỗng. CẦN KIỂM TRA trên
Firebase. Nếu chưa dùng thì đây là đòn bẩy thứ hai: hạ tầng có sẵn, chỉ cần
đẩy cấu hình.


────────────────────────────────────────────────────────────────
6. PHÂN BIỆT USER ORGANIC VÀ USER TỪ QUẢNG CÁO
────────────────────────────────────────────────────────────────

App theo dõi user đến từ đâu (organic hay từ chiến dịch quảng cáo trả tiền)
và có thể áp cấu hình riêng cho mỗi nhóm.

Hiện tại 6 vị trí quảng cáo được đặt TẮT với user organic:
  - Quảng cáo native sau mỗi interstitial
  - Native ở danh sách file trang chủ
  - Native ở màn công cụ
  - Native ở màn settings
  - Native ở trang cuối khi đọc file
  - Interstitial khi thoát màn scan

Logic đằng sau: user organic là user tự tìm đến, giá trị lâu dài cao hơn nên
giữ trải nghiệm nhẹ hơn; user mua từ quảng cáo cần thu hồi chi phí acquisition
nhanh nên chịu mật độ quảng cáo dày hơn. Đây là chiến lược hợp lý và đã
implement xong — PO có thể tinh chỉnh danh sách này qua config.


────────────────────────────────────────────────────────────────
7. HỆ THỐNG GIỮ CHÂN NGƯỜI DÙNG (RETENTION)
────────────────────────────────────────────────────────────────

Đã có sẵn hệ thống thông báo đẩy hàng ngày khá hoàn chỉnh, điều khiển toàn
bộ từ server:

  - Lịch gửi: giờ, phút, khoảng cách ngày, ngày bắt đầu sau khi cài
  - Giới hạn tần suất: số thông báo tối đa, số ngày trong một cửa sổ
  - Không gửi khi user đang active (suppress active hours)
  - Thời gian nghỉ sau khi user bấm vào thông báo (click cooldown)
  - Tự động giãn tần suất nếu user phớt lờ (backoff after ignored)
  - Tự động dừng hẳn nếu bị phớt lờ quá nhiều lần
  - Hỗ trợ A/B test: có experiment_id, variant_id, scenario_id ghi vào
    analytics

Ngoài ra có 6 widget màn hình chính (điều hướng, ảnh sang PDF, công cụ PDF,
scan, tách, gộp) — kênh giữ chân không cần thông báo.

Đây là hệ thống tốt, khuyến nghị kiểm tra xem đang được cấu hình và dùng ở
mức nào.


────────────────────────────────────────────────────────────────
8. RỦI RO VÀ VẤN ĐỀ CẦN XỬ LÝ
────────────────────────────────────────────────────────────────

Phần này KHÔNG phụ thuộc cấu hình server — đọc từ code nên chắc chắn đúng.

--- 8.1. HAI LỖI LOGIC TRONG CƠ CHẾ 3 TẦNG GIÁ (cần dev sửa) ---

Mức độ: trung bình. Ảnh hưởng doanh thu, không gây crash.

Lỗi 1 — Các loại quảng cáo dùng chung bộ nhớ "tầng đang bị khóa":
App có 8 loại quảng cáo nhưng chỉ tạo 6 ô nhớ để lưu trạng thái khóa tầng.
Kết quả:
  - Quảng cáo nổi (pip) dùng chung ô nhớ với Banner
  - Quảng cáo squeeze-back dùng chung ô nhớ với Native

Hệ quả thực tế: khi quảng cáo nổi không có inventory ở tầng H, app sẽ khóa
tầng H cho CẢ banner — dù banner có thể vẫn bán được giá cao bình thường.
Đang mất doanh thu ở đây.

Lỗi 2 — Trạng thái khóa tầng áp dụng toàn cục theo loại, không theo vị trí:
Một vị trí interstitial ở màn hình ít traffic không bán được tầng H sẽ kéo
CẢ 22 vị trí interstitial còn lại xuống tầng thấp hơn.

Đánh đổi này có thể là chủ ý (ít lưu trữ, phản ứng nhanh với tình trạng
inventory chung). Nhưng nếu các vị trí có eCPM chênh lệch nhiều thì đang bỏ
tiền trên bàn. Cần dev đánh giá cùng data thực tế trước khi quyết sửa.

--- 8.2. NGƯỜI ĐÃ TRẢ TIỀN VẪN THẤY QUẢNG CÁO (quyết định business) ---

Cấu hình mặc định bật "hiện quảng cáo cho người đã mua gói", chỉ đổi quảng
cáo native thành banner cho nhẹ hơn. Nút đóng paywall bị delay 3 giây.

CẦN KIỂM TRA giá trị thật trên Firebase. Nếu đúng là đang bật:
  - Đây là rủi ro về đánh giá 1 sao và refund. Người dùng mua gói với kỳ vọng
    hết quảng cáo là kỳ vọng phổ biến nhất.
  - Cần rà soát mô tả gói trên Play Store và trong app: nếu có chữ nào ngụ ý
    "remove ads" mà thực tế vẫn hiện quảng cáo thì đây là rủi ro chính sách
    Google Play, không chỉ là vấn đề UX.
  - Khuyến nghị: quyết định rõ ràng gói bán là gì (unlock tính năng? bỏ
    quảng cáo? cả hai?) rồi làm cho mô tả và hành vi khớp nhau.

--- 8.3. QUYỀN TRUY CẬP TOÀN BỘ BỘ NHỚ (rủi ro chính sách Play) ---

App xin quyền MANAGE_EXTERNAL_STORAGE — quyền truy cập toàn bộ file trên
máy. Đây là quyền Google Play kiểm soát chặt nhất.

Với app quản lý file thì đây là use case được cho phép, nhưng:
  - Phải khai báo và giải trình đúng trong Play Console
  - Google có thể yêu cầu giải trình lại bất kỳ lúc nào
  - Nếu bị đánh giá là không cần thiết, app có thể bị gỡ khỏi Store

Khuyến nghị: xác nhận với dev rằng phần khai báo trong Play Console đang
đầy đủ và cập nhật.

--- 8.4. CẤU HÌNH CHIA SẺ FILE QUÁ RỘNG (cần dev thu hẹp) ---

Mức độ: thấp-trung bình. Không phải lỗ hổng khai thác trực tiếp được từ bên
ngoài, nhưng là bề mặt tấn công không cần thiết.

Cấu hình chia sẻ file của app khai báo phạm vi là toàn bộ thư mục gốc hệ
thống và toàn bộ bộ nhớ ngoài, trong khi thực tế chỉ cần vài thư mục cụ thể.
Nếu sau này có lỗi nào cho phép bên ngoài tác động vào đường dẫn file, mức
ảnh hưởng sẽ rất lớn.

Việc thu hẹp là thay đổi nhỏ, nên gộp vào release gần nhất.

--- 8.5. KÍCH THƯỚC APP (cân nhắc dài hạn) ---

File cài 172 MB là lớn. Nguyên nhân: app đang mang 5 engine xử lý tài liệu
trùng lặp chức năng (Artifex/MuPDF, iText, Apache POI, PDFBox, pdfium).

Ảnh hưởng: tỉ lệ hoàn thành cài đặt thấp hơn, đặc biệt ở thị trường tier 3
nơi mạng chậm và máy ít dung lượng — mà tier 3 chính là nơi có volume lớn
nhất.

Đây là việc lớn, cần dev khảo sát riêng xem engine nào thực sự cần. Không
khuyến nghị làm ngay, nhưng nên đưa vào backlog nếu số liệu install
conversion đang thấp.


────────────────────────────────────────────────────────────────
9. ĐỀ XUẤT KẾ HOẠCH THEO THỨ TỰ ƯU TIÊN
────────────────────────────────────────────────────────────────

GIAI ĐOẠN 0 — KIỂM TRA TRẠNG THÁI THẬT (1-2 ngày, không cần dev)

  Mục tiêu: biết chính xác hệ thống đang chạy cấu hình gì, vì file cài không
  cho biết điều này.

  [ ] Mở Firebase Console > Remote Config, xuất toàn bộ 22 nhóm cấu hình
  [ ] Đối chiếu với bảng giá trị mặc định ở MỤC 11 của tài liệu này
  [ ] Trả lời 4 câu hỏi:
      1. Tầng H và M của waterfall đã điền ad unit chưa?
      2. Phân tier quốc gia đã cấu hình chưa?
      3. "Hiện quảng cáo cho người đã mua gói" đang bật hay tắt?
      4. Hệ thống thông báo hàng ngày đang bật ở mức nào?
  [ ] Lấy số liệu AdMob theo từng ad unit: fill rate, eCPM, impression

  Không làm bước này thì mọi kế hoạch phía sau đều là phỏng đoán.


GIAI ĐOẠN 1 — TĂNG DOANH THU KHÔNG CẦN DEV (1-2 tuần)

  Chỉ thực hiện những mục mà Giai đoạn 0 xác nhận là chưa bật.

  [ ] Nếu waterfall chưa bật: tạo ad unit mới trên AdMob với giá sàn cho các
      format chính (interstitial, native, app open, banner), đẩy vào cấu hình
      tầng H và M. Bắt đầu với 1 format để đo, rồi mở rộng.
  [ ] Nếu phân tier chưa bật: cấu hình tier quốc gia, áp giá IAP theo tier
      (3 gói đã có sẵn), áp tần suất quảng cáo theo tier
  [ ] A/B test giao diện quảng cáo native (hơn 90 thuộc tính điều khiển được):
      thử 2-3 biến thể màu nút CTA và cỡ chữ
  [ ] Rà soát lại 6 vị trí đang tắt với user organic — cân nhắc mở/đóng thêm
      dựa trên số liệu retention theo nhóm

  Đây là nơi có tỉ lệ giá trị/công sức cao nhất.


GIAI ĐOẠN 2 — QUYẾT ĐỊNH SẢN PHẨM (song song Giai đoạn 1)

  [ ] Chốt định nghĩa gói IAP: bán cái gì, có bỏ quảng cáo hay không
  [ ] Rà soát mô tả gói trên Play Store và trong app cho khớp thực tế
      (ưu tiên cao nếu Giai đoạn 0 xác nhận subscriber vẫn thấy quảng cáo)
  [ ] Đánh giá lại mật độ quảng cáo: hiện một lần bấm Back có thể ra 2 quảng
      cáo liên tiếp. Đối chiếu với số liệu rating và uninstall rate để quyết
      định có giảm không.


GIAI ĐOẠN 3 — SỬA CODE (đưa vào release tiếp theo)

  [ ] Sửa lỗi 8.1 phần 1: tách ô nhớ trạng thái khóa tầng cho pip và
      squeeze-back (thay đổi nhỏ, ảnh hưởng doanh thu rõ ràng)
  [ ] Thu hẹp cấu hình chia sẻ file (mục 8.4) — thay đổi nhỏ
  [ ] Xác nhận khai báo quyền truy cập bộ nhớ trong Play Console (mục 8.3)
  [ ] Đánh giá lỗi 8.1 phần 2 (trạng thái khóa tầng toàn cục) — cần data
      từ Giai đoạn 0 để quyết định có đáng sửa không


GIAI ĐOẠN 4 — BACKLOG DÀI HẠN

  [ ] Khảo sát giảm kích thước app (mục 8.5) — chỉ làm nếu số liệu install
      conversion cho thấy 172 MB đang là rào cản
  [ ] Cân nhắc thêm định dạng quảng cáo rewarded: hạ tầng ở tầng SDK đã có
      sẵn nhưng chưa thấy điểm gọi từ giao diện, nên sẽ cần dev thêm. Đây là
      format thân thiện với user (user tự chọn xem để đổi lấy tính năng) —
      phù hợp nếu muốn giảm quảng cáo cưỡng chế mà không giảm doanh thu.


────────────────────────────────────────────────────────────────
10. CÂU HỎI CẦN PO QUYẾT
────────────────────────────────────────────────────────────────

1. Gói IAP bán cái gì? Có bao gồm "bỏ quảng cáo" hay không? — quyết định này
   chặn mục 8.2 và Giai đoạn 2.

2. Mức độ chấp nhận về mật độ quảng cáo? Hiện tại đang rất dày (22 vị trí
   interstitial, mỗi cái kèm 1 native sau đó). Cần cân bằng giữa doanh thu
   ngắn hạn và rating / retention dài hạn.

3. Ưu tiên thị trường nào? Nếu tập trung tier 3 (volume lớn) thì kích thước
   app 172 MB và mật độ quảng cáo là hai vấn đề cần ưu tiên hơn.

4. Có ngân sách dev cho release sửa lỗi không, hay dồn toàn bộ vào Giai đoạn 1
   (chỉ config)? Giai đoạn 1 không cần dev nên có thể chạy trước ngay.


────────────────────────────────────────────────────────────────
11. BẢNG GIÁ TRỊ MẶC ĐỊNH ĐANG ĐÓNG GÓI TRONG APP
────────────────────────────────────────────────────────────────

Dùng mục này để đối chiếu với Firebase Console ở GIAI ĐOẠN 0.

--- 11.1. CÓ 2 LỚP GIÁ TRỊ MẶC ĐỊNH, KHÔNG PHẢI 1 ---

App lấy cấu hình theo thứ tự ưu tiên như sau:

  1. Firebase Remote Config (server)          <- giá trị thật đang chạy
        | nếu tải thất bại hoặc chưa kịp tải
  2. File cấu hình mặc định trong app          <- LỚP A
        | nếu thiếu hẳn mục đó, hoặc mục đó viết sai cú pháp
  3. Giá trị viết cứng trong code              <- LỚP B, đáy cuối

Vì sao PO cần biết: hai lớp A và B KHÔNG GIỐNG NHAU ở một số điểm quan
trọng. Nghĩa là nếu ai đó đẩy một cấu hình lỗi cú pháp lên Firebase, app
sẽ không dùng lớp A mà tụt xuống lớp B — và hành vi sẽ khác hẳn. Chi tiết
ở mục 11.6.


--- 11.2. TẦN SUẤT QUẢNG CÁO ---

Đây là những con số quyết định app "nặng" hay "nhẹ" quảng cáo.

  Thông số                                    Mặc định (lớp A)
  ──────────────────────────────────────────────────────────────
  Quảng cáo toàn màn hình (interstitial)
    Tối đa mỗi phiên                          10 lần
    Độ dài 1 phiên                            600 giây (10 phút)
    Cách nhau tối thiểu giữa 2 lần             35 giây
    Nghỉ sau khi vừa hiện app-open ad           5 giây

  Quảng cáo mở app (app open)
    Cách nhau tối thiểu                        5 giây
    Chờ trước khi hiện                       200 mili giây
    Quay lại app sau bao lâu thì hiện lại    3600 giây (1 giờ)

  Banner
    Tự làm mới                                bật, mỗi 30 giây
    Banner co giãn (collapsible), giãn cách    60 giây
    Chỉ hiện 1 lần mỗi màn                    bật

  Quảng cáo nổi (pip)
    Tối đa mỗi phiên                          10 lần
    Cách nhau tối thiểu                        30 giây

  Quảng cáo khi bấm Back (squeeze)
    Tối đa mỗi phiên                          10 lần
    Cách nhau tối thiểu                        30 giây
    Tự hiện lại sau khi user đóng             bật

  Chống click ảo (bảo vệ tài khoản AdMob)
    Quá bao nhiêu click thì khoá               6 click
    Trong khoảng                              300 giây
    Khoá toàn bộ quảng cáo                   1800 giây (30 phút)

Lưu ý: 3 vị trí interstitial ở màn splash được đặt "bỏ qua giãn cách",
nghĩa là chúng hiện bất chấp con số 35 giây ở trên.


--- 11.3. AD UNIT ID ĐANG ĐÓNG GÓI ---

App gom quảng cáo thành 9 "khuôn" (format). Mỗi khuôn 1 ad unit:

  Khuôn                            Ad unit (đuôi)   Ghi chú
  ──────────────────────────────────────────────────────────────
  Interstitial                     .../8301940428
  App open                         .../7394400472
  Banner thường                    .../5807639021
  Banner co giãn                   .../5807639021   TRÙNG banner thường
  Native nhỏ                       .../3590886832
  Native vừa/lớn                   .../9532289282
  Native toàn màn                  .../8308861045
  Native khi bấm Back              .../4140931306
  Quảng cáo nổi (pip)              .../9956364767

Tất cả thuộc tài khoản AdMob 3559750835852503.

QUAN TRỌNG: mỗi khuôn hiện chỉ có 1 ad unit, tức chỉ có TẦNG D (tầng
thấp nhất) của cơ chế 3 tầng giá ở mục 4. Không có ad unit nào cho tầng
H và tầng M trong file cài.

Đây là căn cứ cho câu hỏi số 1 ở Giai đoạn 0: cần kiểm tra Firebase xem
tầng H và M đã được điền chưa. Nếu Firebase cũng chưa có thì cơ chế 3
tầng giá thực sự chưa hoạt động và đây là cơ hội doanh thu lớn nhất.


--- 11.4. THỜI GIAN KHOÁ TẦNG (cơ chế 3 tầng giá) ---

Khi tầng H hoặc M không có quảng cáo, app khoá tầng đó trong bao lâu:

  Loại quảng cáo        Khoá tầng H     Khoá tầng M
  ──────────────────────────────────────────────────────────
  Native                600 giây        300 giây
  Interstitial          300 giây        150 giây
  Quảng cáo nổi (pip)   300 giây        150 giây
  Squeeze (bấm Back)    300 giây        150 giây
  App open              150 giây         75 giây
  Banner                KHÔNG KHAI BÁO -> tụt về 60 giây / 30 giây

Banner là loại duy nhất không được khai báo trong file cấu hình, nên phải
dùng giá trị viết cứng trong code. Nên bổ sung cho đủ bộ để dễ quản lý,
dù hiện tại không gây lỗi.

Logic đọc bảng này: số càng lớn thì sau một lần không có quảng cáo giá
cao, app càng lâu mới thử lại tầng giá cao. Native để 600 giây là khá
lâu — nếu inventory tầng H thực ra có sẵn thì đang bỏ mất doanh thu.
Đây là con số nên A/B test.


--- 11.5. GÓI IAP VÀ PAYWALL ---

  Thông số                                        Mặc định
  ──────────────────────────────────────────────────────────────
  Gói mặc định                          gói dùng thử 3 ngày, mức 20
  Gói theo tier quốc gia                Tier 1 -> mức 30
                                        Tier 2 -> mức 25
                                        Còn lại -> mức 20
  Hiện paywall khi mở app                          bật
  Hiện paywall lần mở đầu tiên                     bật
  Hiện paywall trước khi xin quyền                 bật
  Hiện paywall khi mở file                         bật
  Hiện paywall khi mở công cụ                      bật
  Delay trước khi cho bấm nút đóng            3000 mili giây (3 giây)

  Người đã mua gói vẫn thấy quảng cáo              BẬT
  Đổi native thành banner cho người đã mua         bật
  Ẩn thanh điều hướng cho người đã mua             tắt

Hai dòng cuối in đậm là nội dung của RỦI RO 8.2. Cần kiểm tra Firebase
xem giá trị thật có đúng như vậy không trước khi kết luận.


--- 11.6. RỦI RO: KHI CẤU HÌNH LỖI, APP CHẠY "HIẾU CHIẾN" HƠN ---

So sánh lớp A (file cấu hình) với lớp B (code viết cứng). Cột lớp B là
hành vi thực tế khi Firebase chết hoặc cấu hình bị lỗi cú pháp:

  Thông số                          Lớp A        Lớp B
  ──────────────────────────────────────────────────────────────
  Gói IAP mặc định                  mức 20       mức 30
  Quay lại app sau bao lâu          3600 giây     300 giây
    thì hiện app-open ad             (1 giờ)      (5 phút)
  Cửa sổ đếm click ảo                300 giây     120 giây
  Số lần thử lại khi load fail
    (interstitial / native / open)        3            0
  Số lần thử lại banner            -1 (không)          5
  Giãn cách banner co giãn            60 giây      45 giây
  Giãn cách quảng cáo nổi             30 giây      15 giây
  Tối đa quảng cáo nổi mỗi phiên          10           20

Hai dòng đầu là đáng lo nhất:

  - Nếu cấu hình lỗi, người dùng sẽ được chào bán gói ĐẮT NHẤT (mức 30)
    thay vì mức 20, kể cả ở thị trường tier 3.
  - Nếu cấu hình lỗi, quảng cáo mở app hiện dày gấp 12 lần (5 phút thay
    vì 1 giờ).

Đây không phải bug — code vẫn chạy đúng. Nhưng nghĩa là một lỗi cú pháp
JSON khi đẩy cấu hình có thể âm thầm đổi giá bán và mật độ quảng cáo mà
không ai nhận ra ngay. Khuyến nghị:

  [ ] Thêm bước kiểm tra cú pháp JSON trước khi publish cấu hình
  [ ] Đặt cảnh báo trên dashboard nếu tỉ lệ hiện app-open ad tăng bất
      thường
  [ ] Cân nhắc yêu cầu dev đổi giá trị viết cứng cho khớp với file cấu
      hình, để "chế độ dự phòng" không hiếu chiến hơn chế độ bình thường


--- 11.7. HAI ĐIỂM BẤT THƯỜNG NÊN RÀ ---

1. Cấu hình khai báo 9 mạng quảng cáo, app chỉ có 4.

   File cấu hình đặt tốc độ làm mới banner riêng cho 9 mạng:
   admob, meta, iron_source, liftoff, pangle, bigo, mintegral, moloco,
   unity.

   Nhưng kiểm tra file cài thì chỉ có 4 mạng phụ thực sự được tích hợp:
   Pangle, Meta Audience Network, Mintegral, Bigo (cộng AdMob là chính).

   4 mục iron_source, liftoff, moloco, unity là cấu hình chết. Không gây
   lỗi, nhưng dễ khiến người đọc cấu hình tưởng app có 9 mạng mediation.
   Khả năng cao đây là cấu hình mẫu dùng chung cho nhiều app trong
   portfolio.

   Việc cần làm: xác nhận với dev/ad-ops thực tế đang mediation mấy mạng,
   rồi dọn cấu hình cho khớp. Nếu muốn thêm 4 mạng kia thì phải tích hợp
   SDK, tức cần dev và release mới.

2. Banner thường và banner co giãn dùng chung 1 ad unit.

   Cả hai đang trỏ về .../5807639021. Đây là 2 định dạng khác nhau trên
   AdMob và eCPM thường chênh nhau rõ rệt.

   Hệ quả: hiện KHÔNG tách được doanh thu của banner co giãn khỏi banner
   thường trong báo cáo AdMob, nên không đo được banner co giãn có hiệu
   quả hơn hay không.

   Việc cần làm: tạo ad unit riêng cho banner co giãn, đẩy qua Firebase.
   Chỉ là thay đổi cấu hình, không cần dev.


--- 11.8. HAI MỤC CẤU HÌNH ĐANG ĐỂ TRỐNG ---

  - Danh sách quốc gia chặn quảng cáo: TRỐNG HẲN
    Nghĩa là mặc định không chặn quảng cáo ở quốc gia nào.

  - Phân nhóm quốc gia (tier): TRỐNG (dấu ngoặc rỗng)
    Nghĩa là mặc định mọi quốc gia rơi vào nhóm mặc định, không áp giá
    và tần suất riêng theo tier.

Đây là căn cứ cho câu hỏi số 2 ở Giai đoạn 0. Code đã map sẵn khoảng 160
quốc gia thành 3 nhóm, nhưng phần cấu hình bật nó lên thì đang trống
trong file cài. Cần kiểm tra Firebase.


────────────────────────────────────────────────────────────────
12. TÀI LIỆU KỸ THUẬT KÈM THEO (cho dev)
────────────────────────────────────────────────────────────────

  ADS_REPORT.md                     Phân tích chi tiết cơ chế quảng cáo:
                                    waterfall 3 tầng, cache/chia sẻ quảng cáo,
                                    logic retry, 58 vị trí và điều kiện trigger.
                                    Có tham chiếu tới từng dòng code.

  REPORT.md                         Phân tích tổng quan: kiến trúc, thư viện
                                    tích hợp, quyền, đánh giá bảo mật.

  CONFIG_DEFAULTS_LOCAL.md          Bản kỹ thuật của MỤC 11: 2 lớp default,
                                    JSON đầy đủ từng nhóm, bảng lệch giữa 2 lớp.

  remote_config_defaults_pretty.txt  Toàn bộ 22 nhóm cấu hình mặc định dạng
                                    JSON thô đã format. Dùng để copy đối chiếu
                                    trực tiếp với Firebase Console.
