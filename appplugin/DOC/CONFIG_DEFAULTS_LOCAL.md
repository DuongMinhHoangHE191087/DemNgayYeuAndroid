# Config ads default ở local — 2 lớp

App có **2 lớp default nằm trong file cài**, không phải một. Thứ tự ưu tiên khi lấy giá trị:

```
① Firebase Remote Config (server, fetch lúc runtime)
        ↓ không có / fetch fail
② res/xml/remote_config_defaults.xml        ← lớp default 1 (đăng ký qua setDefaultsAsync)
        ↓ key thiếu hoặc JSON parse fail
③ hardcode trong code (static final object)  ← lớp default 2, đáy cuối
```

Lớp ③ chỉ chạy khi cả server và XML đều không cho được giá trị hợp lệ — ví dụ JSON sai cú pháp,
hoặc key không tồn tại ở cả hai nơi. Xem [l78.java:183](jadx_base/sources/defpackage/l78.java#L183):
`if (model == null) return new yb2(0);`

> **Phạm vi kết luận:** tài liệu này chỉ mô tả lớp ② và ③ — những gì đóng gói trong APK.
> Lớp ① (giá trị thật đang chạy trên máy user) do Firebase quyết định và **không đọc được từ APK**.
> Vì vậy không thể dùng tài liệu này để kết luận "tính năng X đang bật/tắt" — chỉ có thể nói
> ở lớp default thì nó được set thế nào. Muốn biết trạng thái thật phải mở Firebase Console.

---

## LỚP ①  `res/xml/remote_config_defaults.xml`

- Đường dẫn: `apktool_base/res/xml/remote_config_defaults.xml`
- Kích thước: 21.817 bytes, **22 entry**
- Định dạng: JSON nhúng trong XML, escape bằng HTML entity (`&quot;`), indent lộn xộn (nối chuỗi từ build script)

```xml
<?xml version="1.0" encoding="utf-8"?>
<defaults>
    <entry>
        <key>config_format_ad_id</key>
        <value> {
      "i": {
      "v1": "ca-app-pub-3559750835852503/8301940428"
      },
      ...
```

Trong 22 key có **15 key thuộc nhóm ads**:

| Key | Nội dung |
|---|---|
| `config_format_ad_id` | ad unit ID theo format (9 slot) |
| `ad_places_app_open` | 4 placement |
| `ad_places_banner` | 14 placement |
| `ad_places_fullscreen` | 22 placement |
| `ad_places_native` | 18 placement |
| `config_ads_app_open` | tần suất + retry + disable tier |
| `config_ads_banner` | ↑ + refresh |
| `config_ads_native` | ↑ |
| `config_ads_interstitial` | ↑ + session cap |
| `config_ads_pip` | ↑ |
| `config_ads_squeeze_back` | ↑ |
| `config_countries_disable_ads` | **RỖNG** (thẻ `<value>` không có nội dung) |
| `config_country_tier` | `{}` |
| `config_prevent_ad_click` | chống click-fraud |
| `config_eu_consent` | GDPR |

7 key còn lại không thuộc ads: `config_screen_app_shortcut`, `config_screen_splash`,
`config_screen_language`, `config_screen_introduction`, `config_iap_v2`, `config_app`,
`config_schedule_daily`.

### Nội dung đầy đủ 6 block `config_ads_*`

```jsonc
// config_ads_app_open
{ "is_wait_load_to_show": false, "time_interval": 5,
  "time_millis_delay_before_show": 200, "max_retry_count": 3,
  "delay_times_d": [2], "delay_times_s": [2],
  "time_disable_v3": 150, "time_disable_v2": 75 }

// config_ads_banner   ← KHÔNG có time_disable_v2/v3
{ "banner_refresh_enable": true, "banner_refresh_default_rate_s": 30,
  "banner_refresh_buffer_size": 1,
  "banner_refresh_rates_s": { "admob": 30, "meta": 20, "iron_source": 20,
      "liftoff": 20, "pangle": 20, "bigo": 20, "mintegral": 20,
      "moloco": 20, "unity": 20 },
  "banner_enable_large_anchored": false,
  "max_retry_count": -1,                       // <= 0  =>  KHÔNG retry
  "time_millis_delay_before_show": 0,
  "banner_collapsible_time_interval": 60 }

// config_ads_native
{ "max_retry_count": 3, "delay_times_d": [2], "delay_times_s": [2],
  "time_disable_v3": 600, "time_disable_v2": 300 }

// config_ads_interstitial
{ "is_wait_load_to_show": false, "max_show_per_session": 10,
  "time_per_session": 600, "time_interval": 35,
  "time_interval_after_show_open_ad": 5, "max_retry_count": 3,
  "delay_times_d": [3], "delay_times_s": [3],
  "time_disable_v3": 300, "time_disable_v2": 150 }

// config_ads_pip
{ "max_show_per_session": 10, "time_per_session": 600, "time_interval": 30,
  "max_retry_count": 3, "delay_times_d": [3], "delay_times_s": [3],
  "time_disable_v3": 300, "time_disable_v2": 150 }

// config_ads_squeeze_back
{ "is_wait_load_to_show": true, "max_show_per_session": 10,
  "time_per_session": 600, "time_interval": 30, "max_retry_count": 3,
  "is_auto_reshow_after_dismiss": true,
  "delay_times_d": [3], "delay_times_s": [3],
  "time_disable_v3": 300, "time_disable_v2": 150 }
```

### `config_format_ad_id` — chỉ có tầng v1

```jsonc
{ "i":   { "v1": "ca-app-pub-3559750835852503/8301940428" },   // interstitial
  "o":   { "v1": "ca-app-pub-3559750835852503/7394400472" },   // app open
  "b":   { "v1": "ca-app-pub-3559750835852503/5807639021" },   // banner
  "bc":  { "v1": "ca-app-pub-3559750835852503/5807639021" },   // banner collapsible (trùng b)
  "ns":  { "v1": "ca-app-pub-3559750835852503/3590886832" },   // native small
  "nmf": { "v1": "ca-app-pub-3559750835852503/9532289282" },   // native medium/full
  "nfi": { "v1": "ca-app-pub-3559750835852503/8308861045" },   // native full interstitial
  "nsb": { "v1": "ca-app-pub-3559750835852503/4140931306" },   // native squeeze back
  "pip": { "v1": "ca-app-pub-3559750835852503/9956364767" } }  // picture in picture
```

Không có `v2`/`v3` ⇒ ở lớp default này waterfall sập về 1 tầng.
Không có key `r`/`ri` ⇒ rewarded không được cấu hình.

File đã format sẵn để đối chiếu: [remote_config_defaults_pretty.txt](remote_config_defaults_pretty.txt)

---

## LỚP ②  hardcode trong code

Mỗi loại config có một object `static final` làm đáy cuối. Vị trí trong `jadx_base/sources/defpackage/`:

| Loại config | Class chứa default | Giá trị |
|---|---|---|
| app_open | `na2.d` | interval=1s, maxRetry=**0**, delayD=[], delayS=[], disable **60/30** |
| banner | `qa2.d` | maxRetry=**5**, disable **60/30**, collapsibleInterval=**45**s, refresh=true rate=30, refreshRates tất cả **=30** |
| native | `wa2.d` | maxRetry=**0**, delay=[], disable **60/30** |
| interstitial | `ta2.d` | waitLoad=false, cap=10/600s, interval=**37**s, afterOpenAd=**10**s, maxRetry=**0**, disable **60/30** |
| pip | `ic2.d` | cap=**20**/600s, interval=**15**s, delay=[3], disable **60/30** |
| squeeze_back | `jd2.d` | waitLoad=**false**, cap=10/600s, interval=30s, maxRetry=3, disable **60/30** |
| rewarded | `db2.d` | maxRetry=0, disable 60/30 |
| rewarded_inter | `bb2.d` | maxRetry=0, disable 60/30 |
| prevent_ad_click | `kc2.b` | maxClick=6, perSession=**120**s, disable=1800s |
| config_app | `hb2.d` | hideNav=true, splashAfterBg timer=**300**s |
| iap | `ec2.d` | delayClose=3000ms, sku=**"solara.document.3days.30"** |
| ad places (list) | `ma2`/`sa2`/`ab2`/`gb2`… | list rỗng |

Ngoài ra `e78.java` còn có fallback inline ngay tại chỗ đọc, ví dụ
[e78.java:220-223](jadx_base/sources/defpackage/e78.java#L220):

```java
long disableV3 = model.getTimeDisableV3() != null ? … : 60L;
long disableV2 = model.getTimeDisableV2() != null ? … : 30L;
```

Đây là lý do banner (không khai báo trong XML) chạy với **60s/30s**.

---

## Chỗ lớp ① và lớp ② LỆCH NHAU

Quan trọng vì đây chính là hành vi app khi Firebase chết hoặc JSON config bị lỗi cú pháp.

| Tham số | XML (lớp ①) | Code (lớp ②) | Ghi chú |
|---|---|---|---|
| **IAP sku mặc định** | `3days.20` | **`3days.30`** | Config lỗi ⇒ user thấy giá cao nhất |
| **splash sau khi về background** | 3600s (1 giờ) | **300s (5 phút)** | Config lỗi ⇒ quảng cáo app-open dày gấp 12 lần |
| prevent_ad_click `time_per_session` | 300s | **120s** | Cửa sổ đếm click ngắn hơn ⇒ dễ chạm ngưỡng khoá 30 phút |
| interstitial `time_interval` | 35s | 37s | không đáng kể |
| interstitial `time_interval_after_show_open_ad` | 5s | 10s | |
| interstitial `max_retry_count` | 3 | **0** | Config lỗi ⇒ không retry |
| native `max_retry_count` | 3 | **0** | |
| app_open `max_retry_count` | 3 | **0** | |
| banner `max_retry_count` | **-1** (không retry) | 5 | Ngược nhau |
| banner `collapsible_time_interval` | 60s | 45s | |
| pip `time_interval` | 30s | 15s | |
| pip `max_show_per_session` | 10 | 20 | |
| squeeze_back `is_wait_load_to_show` | **true** | false | |

Hai dòng đầu là đáng lưu ý nhất — đều nghiêng về hướng **hiếu chiến hơn** khi config fail:
bán gói đắt hơn và hiện app-open ad dày hơn.

---

## Hai điểm bất thường trong config ads

**1. `banner_refresh_rates_s` khai 9 mạng, APK chỉ có 4.**

XML liệt kê `admob, meta, iron_source, liftoff, pangle, bigo, mintegral, moloco, unity`.
Đã kiểm tra trong file cài: chỉ có adapter của **Pangle, Meta Audience Network, Mintegral, Bigo**
(cộng AdMob là mạng chính). Không tìm thấy iron_source / liftoff / moloco / unity /
applovin / vungle / inmobi.

⇒ 4 key `iron_source`, `liftoff`, `moloco`, `unity` là **config chết** trong build này.
Khả năng cao đây là config template dùng chung cho nhiều app trong portfolio của publisher.
Không gây lỗi (chỉ là entry Map không ai tra), nhưng dễ gây nhầm khi đọc config tưởng
app có 9 mạng mediation.

Cũng lưu ý XML đặt admob=30 còn 8 mạng kia=20, trong khi code default đặt **tất cả =30**.

**2. `banner_collapsible_time_interval` nằm trong `config_ads_banner` nhưng `bc` dùng chung ad unit với `b`.**

`config_format_ad_id` đặt `b` và `bc` cùng ad unit `…/5807639021`. Banner thường và banner
collapsible là 2 định dạng khác nhau trên AdMob và thường nên tách unit để đo riêng
(collapsible có eCPM khác rõ rệt). Hiện đang không phân biệt được doanh thu 2 loại này
trong báo cáo AdMob.

---

## Lệnh dựng lại các file này

```bash
# lớp 1 (XML defaults)
cat apktool_base/res/xml/remote_config_defaults.xml
```

```bash
java -jar apktool.jar d -s -f -o apktool_base com.solara.alldocumentreader.officepdfwordexcel.apk
```
