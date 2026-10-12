# Doc-Solara_APKPure.xapk — Báo cáo decode

## 1. Danh tính & tính xác thực

| Trường | Giá trị |
|---|---|
| Package | `com.solara.alldocumentreader.officepdfwordexcel` |
| Tên app | All Document Reader |
| versionName / versionCode | `1.5.3-Doc-Solara` / `53` |
| minSdk / targetSdk / compileSdk | 29 / **36** / 37 |
| Build variant (Gradle) | `docSolaraProdRelease` (flavor `docSolara`) |

**Không phải bản repack/mod.** Ba bằng chứng độc lập:
- Chữ ký v3: `CN=Android, OU=Android, O=Google Inc.` → Play App Signing key, SHA-256 `a4394491cafb06a4fc49b77ccb0272443e062cef9b9077e4184678b19ae8ed13`
- Source stamp verified, `com.android.stamp.source = https://play.google.com/store`, type `STAMP_TYPE_DISTRIBUTION_APK`
- Kotlin `@Metadata` trong bytecode ghi module name `base_docSolaraProdRelease` → "Doc-Solara" là product flavor chính hãng của dev, không phải nhãn của bên thứ ba

## 2. Cấu trúc XAPK

```
Doc-Solara_APKPure.xapk (172 MB)
├── com.solara...apk        86 MB   base — 9 classes.dex, resources.arsc, assets
├── config.arm64_v8a.apk    87 MB   20 native .so (chỉ arm64-v8a)
├── config.xxxhdpi.apk     7.5 MB   drawable xxxhdpi
├── manifest.json                   metadata APKPure
└── icon.png
```

Chỉ có split arm64-v8a → **không chạy được trên thiết bị/emulator armeabi-v7a hay x86**.

## 3. Kiến trúc code

Code riêng của app: **`com.roosterx.*`** (241 class giữ tên) + **13.502 class bị R8 obfuscate** đẩy về default package (jadx đặt ở `defpackage/`).

```
com.roosterx.base          BaseApp, config/remote, inapp (billing), receiver, glide, customviews
com.roosterx.featurefirst   splash, language(+v2), intro(v1/v2/v3), uninstall
com.roosterx.featuremain    MainActivity, pdfview, scanner, createpdf, tools, iap, widget, work
```

Application class trong manifest là **`com.artifex.sonui.MainApp`** — app build trên nền **Artifex SmartOffice SDK** (thương mại):
- `libso.so` (11 MB) — engine gốc, JNI `Java_com_artifex_solib_SODoc_*` (đọc/sửa Word/Excel/PowerPoint, annotation, tracked changes)
- `libmupdf.so` (18 MB) — MuPDF
- `com.artifex.sonui.editor.OfficeReaderActivity`, `SlideShowActivity`, `ExplorerActivity`

Song song còn có iText (1.315 class), Apache POI, PDFBox-Android (tom_roush), pdfium — nhiều engine tài liệu trùng lặp, giải thích size 86 MB.

## 4. Surface: 54 activity, 13 service, 19 receiver, 11 provider

## 5. SDK tích hợp (version lấy từ `unknown/*.properties` + manifest meta-data)

**Quảng cáo — 5 mạng cùng lúc:**

| Mạng | Version | Ghi chú |
|---|---|---|
| Google AdMob | `play-services-ads 25.4.0` | App ID `ca-app-pub-3559750835852503~3094039296` |
| ByteDance Pangle | `8.1.0.5` | ~24 activity `TT*`, `libpglarmor.so`, `libtt_ugen_layout.so` (Yoga) |
| Meta Audience Network | — | `assets/audience_network/classes2.dex` (dex nạp động 5 MB) |
| Mintegral (mbridge) | — | 1.793 class |
| Bigo Ads | — | 1.838 class (`sg.bigo.ads`) |

Đây là **5 mạng thực sự có SDK trong APK**. Nhưng `config_ads_banner.banner_refresh_rates_s` khai
báo refresh rate cho **9 mạng** — thêm `iron_source`, `liftoff`, `moloco`, `unity`. Đã grep toàn bộ
APK: không có adapter nào của 4 mạng này (cũng không có `applovin`, `vungle`, `inmobi`,
`chartboost`, `fyber`). ⇒ 4 key đó là **config chết**, khả năng cao là config template dùng chung
cho nhiều app trong portfolio. Không gây lỗi runtime nhưng gây nhầm khi đọc config.

**Analytics / attribution:** Firebase (Analytics 23.2.0, Crashlytics, Performance, Remote Config, Sessions), AppsFlyer 297 class (dev key `Z2ty7rjatUSWqcybH48vGU`), Adjust (+ `libsigner.so`), Facebook SDK, ByteDance APM Insight (`libapminsighta/b.so`), IAB TCF (`com.iab`, 203 class).

**ML Kit:** OCR 5 script (latin/chinese/japanese/korean/devanagari), document-scanner 16.0.0, translate 17.0.3 (`libtranslate_jni.so` 16 MB), language-id 17.0.6.

**Khác:** Play Billing **9.0.0**, Play Integrity 1.3.0, Play Review/App-Update, OpenCV 4 (`libopencv_java4.so` 22 MB), CameraX, Room, WorkManager, Zendesk, Dropbox + Box (cloud storage), UMP 4.0.0 (consent).

## 6. Firebase project (public client identifiers)

```
project_id            ne005---all-document
google_app_id         1:482539265514:android:70265f9f0b874808b26693
api_key               AIzaSyAOY-g9ct31eVpWF1QHZeqGoSezoYPJ7P8
storage_bucket        ne005---all-document.firebasestorage.app
facebook_app_id       1775652913090795
facebook_client_token 4ae6a28f1465f2fbbd50cc8f812a88d0
```

Đây là client-side identifiers (thiết kế để nằm trong APK), không phải secret server. `adjust_app_token` và `applovin_sdk_key` để **rỗng** → hai SDK này khai báo nhưng chưa kích hoạt trong build này.

## 7. Firebase Remote Config — 22 key (defaults ở `res/xml/remote_config_defaults.xml`)

```
config_format_ad_id      config_screen_app_shortcut   config_ads_pip
ad_places_app_open       config_screen_splash          config_ads_squeeze_back
ad_places_banner         config_screen_language        config_countries_disable_ads
ad_places_fullscreen     config_screen_introduction    config_eu_consent
ad_places_native         config_iap_v2                 config_prevent_ad_click
config_ads_app_open      config_ads_banner             config_app
config_ads_native        config_ads_interstitial       config_schedule_daily
config_country_tier
```

15/22 key thuộc nhóm ads. `config_countries_disable_ads` để **rỗng hẳn**, `config_country_tier` = `{}`.

> **Phạm vi kết luận:** mọi giá trị dưới đây là **default đóng gói trong APK**, chỉ dùng khi chưa
> fetch được Firebase hoặc fetch fail. Giá trị thật đang chạy do server quyết định và không đọc
> được từ APK. Không thể kết luận "tính năng X đang tắt" chỉ từ file cài.

Ngoài file XML còn có **lớp default thứ hai: hardcode trong code** (`na2.d`, `qa2.d`, `wa2.d`,
`ta2.d`, `ic2.d`, `jd2.d`, `kc2.b`, `hb2.d`, `ec2.d`), chạy khi JSON parse fail hoặc key thiếu ở
cả hai nơi. Hai lớp **lệch nhau** ở một số điểm đáng chú ý — đáng lưu ý nhất là default IAP sku
(`3days.20` ở XML vs `3days.30` trong code) và timer splash sau khi về background (3600s vs 300s).
Nghĩa là một lỗi cú pháp JSON khi publish config có thể âm thầm đổi giá bán và mật độ quảng cáo.

Chi tiết 2 lớp + bảng lệch đầy đủ: [CONFIG_DEFAULTS_LOCAL.md](CONFIG_DEFAULTS_LOCAL.md).
Bản pretty-print JSON thô: `remote_config_defaults_pretty.txt`

### Ad unit IDs (default, có thể override qua Remote Config)

| Slot | Ad unit |
|---|---|
| `i` interstitial | `ca-app-pub-3559750835852503/8301940428` |
| `o` app open | `ca-app-pub-3559750835852503/7394400472` |
| `b` / `bc` banner + collapsible | `ca-app-pub-3559750835852503/5807639021` ⚠️ **dùng chung 1 unit** |
| `ns` native small | `ca-app-pub-3559750835852503/3590886832` |
| `nmf` native media full | `ca-app-pub-3559750835852503/9532289282` |
| `nfi` native full intro | `ca-app-pub-3559750835852503/8308861045` |
| `nsb` native small banner | `ca-app-pub-3559750835852503/4140931306` |
| `pip` picture-in-picture | `ca-app-pub-3559750835852503/9956364767` |

Mỗi slot hỗ trợ 3 biến thể `v1`/`v2`/`v3` = 3 tầng giá sàn (`D`/`M`/`H` — Default / Medium floor /
High floor). Khi tầng cao no-fill, app khoá tầng đó một khoảng thời gian rồi tự tụt xuống tầng thấp
hơn; tầng `D` luôn là đáy nên fill rate không bị ảnh hưởng. Thời gian khoá khai báo riêng theo loại:

| Config block | khoá `H` (`time_disable_v3`) | khoá `M` (`time_disable_v2`) |
|---|---|---|
| `config_ads_native` | 600s | 300s |
| `config_ads_interstitial` / `_pip` / `_squeeze_back` | 300s | 150s |
| `config_ads_app_open` | 150s | 75s |
| `config_ads_banner` | không khai báo → 60s (hardcode) | không khai báo → 30s |

Ở lớp default trong APK, **cả 9 slot chỉ set `v1`** (không có `v2`/`v3`), và không có slot `r`/`ri`
cho rewarded. Trạng thái thật phải kiểm tra trên Firebase Console — xem ghi chú phạm vi ở trên.

Cơ chế đầy đủ (chọn tầng lúc load, hạ tầng khi fail, retry, cache pooling):
[ADS_REPORT.md](ADS_REPORT.md).

### Phân tầng giá theo quốc gia

`ConfigCountryTierModel` chia ~160 country code thành `t1`/`t2`/`t3`. Tier 1 = US/CA/GB/AU/DE/FR/nordics…, tier 3 = VN, IN, PK, BD, các nước châu Phi… **Mọi** config block (ads, IAP, screen) đều override được theo tier.

### IAP (`config_iap_v2`)

```json
{ "sku": "solara.document.3days.20",
  "sku_list": ["solara.document.3days.30","solara.document.3days.25","solara.document.3days.20"],
  "t1": {"sku":"solara.document.3days.30"},  // tier 1 → $30
  "t2": {"sku":"solara.document.3days.25"},  // tier 2 → $25
  "is_enable_ads_for_subscribers": true,
  "is_native_convert_to_banner_for_subscribers": true,
  "is_show_on_start": true,
  "is_show_in_first_open": true,
  "is_show_before_request_permission": true,
  "time_delay_to_show_icon_close": 3000 }
```

Trial 3 ngày, giá phân tầng 20/25/30 theo quốc gia. Đáng lưu ý:
`is_enable_ads_for_subscribers = true` → **người trả tiền vẫn thấy quảng cáo**, native chỉ bị đổi thành banner.
Nút đóng paywall bị delay 3 giây.

### `config_ads` / `config_prevent_ad_click`

```json
{ "max_ad_click_per_session": 6, "time_per_session": 300,
  "time_disable_ads_when_reached_max_ad_click": 1800 }
```

Tự chống click-fraud: >6 click ads / 300s → tắt ads 1800s (bảo vệ tài khoản AdMob khỏi invalid traffic).

### `config_screen_splash`

`progress_time: 30` (splash tối đa 30s), `ad_type_first_open: "app_open"`, min 3s trước khi cho qua nếu ad fail.

### `config_app`

`is_hide_navigation_bar: true`, `is_show_splash_after_app_go_to_background: true` với `timer... = 3600` → quay lại app sau 1 giờ thì chạy lại splash (kèm app-open ad).

### `AdPlaceModel` — 90+ field

Toàn bộ UI của native ad (màu, bo góc, size chữ, margin, aspect ratio, countdown, style icon…) đều điều khiển từ server. Không cần update app để đổi giao diện quảng cáo.

## 8. Quyền & điểm cần lưu ý bảo mật

19 permission. Đáng chú ý:

- **`MANAGE_EXTERNAL_STORAGE`** — quyền truy cập toàn bộ storage, Google Play hạn chế rất chặt; hợp lý với file manager nhưng là quyền rộng nhất trên Android.
- `CAMERA` — scanner tài liệu.
- Trọn bộ `ACCESS_ADSERVICES_*` (AD_ID, ATTRIBUTION, TOPICS, CUSTOM_AUDIENCE) + `AD_ID` → Privacy Sandbox attribution đầy đủ.
- `RECEIVE_BOOT_COMPLETED` qua `BootReceiver` + `ReminderReceiver` → notification định kỳ (`config_schedule_daily` có engagement rules + frequency cap).

**`res/xml/provider_paths.xml` — điểm đáng chú ý nhất:**

```xml
<root-path name="root" path="." />
<external-path name="share" path="/" />
```

FileProvider expose `root-path "."` = toàn bộ filesystem root, và `external-path "/"` = toàn bộ external storage. Bất kỳ app nào nhận được URI từ provider này có thể được cấp quyền đọc file ở đường dẫn tùy ý trong phạm vi đó. Đây là cấu hình quá rộng so với thực tế cần (thường chỉ cần `files-path`/`cache-path`). Không phải lỗ hổng trực tiếp khai thác được từ ngoài (provider không exported public và cần grant URI permission), nhưng là attack surface không cần thiết — nếu có bug nào cho phép ép app tạo URI theo input bên ngoài thì mức độ ảnh hưởng sẽ rất lớn.

`network_security_config.xml` chỉ mở cleartext cho `127.0.0.1` — cấu hình đúng.

## 9. Những thứ nhìn "đáng nghi" nhưng thực chất bình thường

| Thứ | Thực chất |
|---|---|
| `assets/tflite_langid.tflite.jpg` | Model TFLite thật (magic bytes `TFL3`), đặt đuôi `.jpg` để build tool không nén — thủ pháp chuẩn của ML Kit |
| `assets/audience_network/classes2.dex` | Dex của Meta Audience Network SDK, nạp động — cơ chế chính thức của SDK |
| `libpglarmor.so`, `libnms.so`, `libsigner.so` | Lần lượt: Pangle crypto (`PglCryptUtils`), lib phụ, Adjust signature (`com_adjust_sdk_sig_NativeLibHelper_nSign`) |
| `assets/hwp_bridge/*.js` | Hỗ trợ đọc file HWP (Hancom, định dạng văn bản Hàn Quốc) qua WebView + MathJax + html2pdf |
| `sus_url` trong `config_app` | Đã truy vết: parse ở `defpackage/e78.java:1383` → lưu vào field `c` của holder `fb2` → **không có chỗ nào đọc lại**. Field chết trong build này (không có default value). Tên chỉ là viết tắt, không phải "suspicious" |

## 10. Kết luận

App hợp pháp, phân phối chính thức qua Play Store, không có dấu hiệu mã độc hay repack. Đặc điểm nổi bật là **mật độ monetization rất cao**: 5 mạng quảng cáo chạy song song, ~90 tham số UI quảng cáo điều khiển từ server, giá IAP phân tầng theo GDP quốc gia, và vẫn hiện quảng cáo cho người đã trả tiền.

Hai điểm kỹ thuật nên lưu ý nếu đây là codebase của bạn:
1. `provider_paths.xml` expose quá rộng (mục 8) — nên thu hẹp về đúng thư mục cần share.
2. 4 engine tài liệu trùng lặp (Artifex/MuPDF + iText + POI + PDFBox + pdfium) đẩy size lên 86 MB base.

## Output

```
work/
├── REPORT.md                              báo cáo này
├── remote_config_defaults_pretty.txt      22 config block đã pretty-print
├── apktool_base/     12.506 file,  98 MB  AndroidManifest.xml đã decode + res/ + assets/
├── jadx_base/        49.989 file, 287 MB  source Java (sources/) + resources/
└── abi_arm64/            22 file,  84 MB  native .so
```

Mở jadx-gui để browse có index và cross-reference:

    C:\Users\XGame\Desktop\D_folder\jadx-1.5.5\bin\jadx-gui.bat work\com.solara.alldocumentreader.officepdfwordexcel.apk
