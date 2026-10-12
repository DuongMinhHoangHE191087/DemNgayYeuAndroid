# Ad pipeline — load/show, high floor, waterfall nhiều tầng giá

Tất cả kết luận dưới đây trace từ code thật. Class name bị R8 obfuscate nhưng **Kotlin property metadata và log string không bị xoá**, nên tên nghiệp vụ khôi phục được chính xác.

## 0. Bản đồ class

| Obf | Vai trò | File |
|---|---|---|
| `gb` | **AdmobManager** — toàn bộ load/show/cache orchestration (2012 dòng) | [gb.java](jadx_base/sources/defpackage/gb.java) |
| `bd` | Interface facade mà Activity gọi (`preloadBannerNative`, `showBannerNative`, …) | [bd.java](jadx_base/sources/defpackage/bd.java) |
| `ha` | Merge `AdPlaceModel` (JSON) → `ga` (runtime ad place) | [ha.java](jadx_base/sources/defpackage/ha.java) |
| `e78` | Remote-config repository (parse + cache 22 config block) | [e78.java](jadx_base/sources/defpackage/e78.java) |
| `ga` | Base runtime ad place — **chứa logic chọn tầng giá** | [ga.java](jadx_base/sources/defpackage/ga.java) |
| `nn` | Persisted prefs — **chứa timestamp disable từng tầng** | [nn.java](jadx_base/sources/defpackage/nn.java) |
| `lb` | Callback handler khi load **fail** → ghi timestamp disable | [lb.java](jadx_base/sources/defpackage/lb.java) |
| `p9` | Base ad holder (1 ô cache). Subclass: `sw` banner, `gs6` native, `fh5` inter, `wm7` pip, `xc9` squeeze, `ze8`/`bf8` rewarded | |
| `ka` | Enum ad type: `a`=AppOpen `b`=Banner `d`=Interstitial `e`=Native `g`=Pip `h`=RewardedInter `i`=Rewarded `j`=SqueezeBack `f`=None | [ka.java](jadx_base/sources/defpackage/ka.java) |
| `ma`→`la`/`oa`/`na` | Wrapper ad-unit-id theo tầng: **`la`=v1, `oa`=v2, `na`=v3** | [ma.java](jadx_base/sources/defpackage/ma.java) |
| `ms6` | Enum native template (`full*` / `medium*` / `small*`) | [ms6.java](jadx_base/sources/defpackage/ms6.java) |

---

## 1. Ba tầng giá: v1 / v2 / v3 = D / M / H

Đây **không** phải mediation waterfall của AdMob. Đây là waterfall **tự implement ở tầng app**: 3 ad unit AdMob riêng biệt cho cùng một vị trí, mỗi unit set eCPM floor khác nhau trên AdMob dashboard.

Tên tầng lấy từ chính Kotlin property metadata trong [nn.java:15](jadx_base/sources/defpackage/nn.java#L15):

```
expiredTimeDisableLoadBannerAdsHighFloor      ← v3
expiredTimeDisableLoadBannerAdsMediumFloor    ← v2
expiredTimeDisableLoadNativeAdsHighFloor
expiredTimeDisableLoadNativeAdsMediumFloor
expiredTimeDisableLoadInterstitialAdsHighFloor
expiredTimeDisableLoadInterstitialAdsMediumFloor
expiredTimeDisableLoadRewardedInterstitialAdsHighFloor / MediumFloor
expiredTimeDisableLoadRewardedAdsHighFloor / MediumFloor
expiredTimeDisableLoadAppOpenAdsHighFloor / MediumFloor
```

Và từ log string app tự in ([gb.java:1558](jadx_base/sources/defpackage/gb.java#L1558), verified là `const-string` inline trong smali):

```java
tier = (maVar == ga.b()) ? "H" : (maVar == ga.c()) ? "M" : "D";
Log("AdmobManager", "$AdType $placeName adUnit tier=$tier adId=$adId")
```

| Tầng | Wrapper | Getter | Label log | Ý nghĩa |
|---|---|---|---|---|
| **v3** | `na` | `ga.b()` | `H` | **High floor** — eCPM cao nhất, thử trước |
| **v2** | `oa` | `ga.c()` | `M` | **Medium floor** |
| **v1** | `la` | `ga.a()` | `D` | **Default** — floor thấp/không floor, luôn fill, không bao giờ bị disable |

Log dòng khác cũng xác nhận ([gb.java:288-291](jadx_base/sources/defpackage/gb.java#L288)):
```
"blocked: not valid to load (isEnable=… adType=… adIds=[<v1>, m=<v2>, h=<v3>])"
```

### Hàm chọn tầng — [ga.java](jadx_base/sources/defpackage/ga.java)

```java
public final ma h() {                              // full waterfall
    return !isBlank(b().a()) ? b()                 //   H nếu có
         : !isBlank(c().a()) ? c()                 //   → M
         : a();                                    //   → D
}
public final ma i() {                              // bỏ qua H
    return !isBlank(c().a()) ? c()                 //   M nếu có
         : a();                                    //   → D
}
```

Tầng nào **chuỗi rỗng** thì bị bỏ qua ngay. Nên nếu remote config chỉ set `v1` (như default hiện tại) thì waterfall tự sập về 1 tầng, không cần disable code.

---

## 2. Ad unit id được resolve từ đâu — 4 lớp override

`ha.a(AdPlaceModel)` build ra `ga`. Ad id đi qua 4 lớp, **lớp sau ghi đè lớp trước**:

```
① config_format_ad_id  →  theo FORMAT (dùng chung mọi placement cùng format)
② ad_places_*[].ad_id / ad_id_v2 / ad_id_v3   →  theo PLACEMENT
③ ad_places_*[].t1|t2|t3.ad_id*               →  theo COUNTRY TIER
④ ad_places_*[].ogn.ad_id*                    →  theo user ORGANIC
```

### ① Format → slot ([yb2.java](jadx_base/sources/defpackage/yb2.java), mapping từ [l78.java:290](jadx_base/sources/defpackage/l78.java#L290))

| Điều kiện | Key trong `config_format_ad_id` |
|---|---|
| AppOpen | `o` |
| Banner thường | `b` |
| Banner collapsible | `bc` |
| Interstitial | `i` |
| Native, `native_inter_enable=true` **và** template thuộc họ `full*` | `nfi` |
| Native, template thuộc họ `small*` | `ns` |
| Native, còn lại (họ `medium*`) | `nmf` |
| Pip | `pip` |
| SqueezeBack | `nsb` |
| Rewarded / RewardedInterstitial | `r` / `ri` — **chưa cấu hình trong build này** |

Họ template: `b()` = 4 biến thể `full*`, `c()` = 5 biến thể `small*`, còn lại = `medium*`.

Quan trọng: khi tier override `ad_type` sang type khác, cả 3 ad id bị **reset về rỗng** ([ha.java:892-897](jadx_base/sources/defpackage/ha.java#L892)) rồi resolve lại — tránh dùng ad unit của format cũ cho format mới.

---

## 3. Chọn tầng lúc LOAD — `gb.q(ga)`

[gb.java:1527-1571](jadx_base/sources/defpackage/gb.java#L1527). Chạy **mỗi lần load**, không phải lúc show.

```java
long now = SystemClock-ish();
if      (now > nn.expiredHighFloor)   pick = ga.h();   // H → M → D
else if (now > nn.expiredMediumFloor) pick = ga.i();   //     M → D
else                                  pick = ga.a();   //         D
ga.a = pick;    // ghi lại tầng đang dùng vào chính ad place
```

Timestamp là **per-ad-type**, không per-placement — một lần `interstitial` high floor no-fill thì **mọi** placement interstitial trong app đều nhảy xuống M.

Mapping type → slot prefs (đọc từ [lb.java:457-548](jadx_base/sources/defpackage/lb.java#L457)):

| Ad type | slot H / M | Config holder | field H / M |
|---|---|---|---|
| Banner `ka.b` | `u0[5]` / `u0[6]` | `oa2` ← `config_ads_banner` | `.d` / `.e` |
| Pip `ka.g` | `u0[5]` / `u0[6]` ⚠️ **dùng chung slot với banner** | `xa2` ← `config_ads_pip` | `.g` / `.h` |
| Native `ka.e` | `u0[7]` / `u0[8]` | `ua2` ← `config_ads_native` | `.d` / `.e` |
| SqueezeBack `ka.j` | `u0[7]` / `u0[8]` ⚠️ **dùng chung slot với native** | `eb2` ← `config_ads_squeeze_back` | `.h` / `.i` |
| Interstitial `ka.d` | `u0[9]` / `u0[10]` | `ra2` ← `config_ads_interstitial` | `.i` / `.j` |
| RewardedInter `ka.h` | `u0[11]` / `u0[12]` | `za2` | `.e` / `.f` |
| Rewarded `ka.i` | `u0[13]` / `u0[14]` | `ya2` | `.e` / `.f` |
| AppOpen `ka.a` | `u0[15]` / `u0[16]` | `la2` ← `config_ads_app_open` | `.e` / `.f` |

⚠️ Pip dùng chung slot disable với Banner, SqueezeBack dùng chung với Native. Nên **pip no-fill sẽ hạ tầng banner** và ngược lại. Nếu đây là chủ ý thì fine; nếu không thì là bug — 8 ad type nhưng chỉ có 6 cặp slot.

---

## 4. Hạ tầng khi load FAIL — `lb`

[lb.java:449-548](jadx_base/sources/defpackage/lb.java#L449), chạy trong callback `onAdFailedToLoad`:

```java
if ((ga.a instanceof na) && now > nn.expiredHigh)        // tầng vừa fail là H
    nn.expiredHigh = now + cfg.timeDisableV3;            //   → cấm H trong time_disable_v3 giây
else if ((ga.a instanceof oa) && now > nn.expiredMedium)  // tầng vừa fail là M
    nn.expiredMedium = now + cfg.timeDisableV2;          //   → cấm M trong time_disable_v2 giây
```

`ga.a` chính là tầng mà `gb.q()` đã ghi vào ở bước load. Vòng lặp đóng kín:
**q() chọn tầng → load → fail → lb ghi timestamp → lần load sau q() tự chọn tầng thấp hơn.**

Tầng `D` (v1) không có nhánh disable — luôn là đáy an toàn.

> **Ghi chú về decompile:** jadx dựng 4 nhánh cuối (`ka.d`/`ka.h`/`ka.i`/`ka.a`) thành `if (a instanceof na) { … else if (a instanceof oa) { … } }` — trông như dead code. Đã verify bằng smali (`apktool_smali/smali_classes4/lb.smali`): cả hai nhánh cùng nhảy tới `:cond_2e`, tức `else if` **có** reachable. Đây là artifact của jadx, không phải bug thật. 4 nhánh đầu jadx dựng đúng dạng phẳng.

### Giá trị default hiện tại

| Config block | `time_disable_v3` (cấm H) | `time_disable_v2` (cấm M) |
|---|---|---|
| `config_ads_native` | 600s | 300s |
| `config_ads_interstitial` | 300s | 150s |
| `config_ads_pip` | 300s | 150s |
| `config_ads_squeeze_back` | 300s | 150s |
| `config_ads_app_open` | 150s | 75s |
| `config_ads_banner` | **không set** → fallback hardcode 60s | **không set** → 30s |

Chỉ banner là không khai báo trong XML và rơi về default hardcode trong code
([e78.java:220-223](jadx_base/sources/defpackage/e78.java#L220), `qa2.d`).
Chi tiết 2 lớp default local: xem [CONFIG_DEFAULTS_LOCAL.md](CONFIG_DEFAULTS_LOCAL.md).

---

## 5. Retry sau khi fail — chọn delay theo tầng mới

[gb.java:202-231](jadx_base/sources/defpackage/gb.java#L202). Đây là chỗ tinh vi nhất:

```java
ma failedTier = p9.a().a;
boolean sameTier = q(p9.a()).a() == failedTier.a();   // gọi lại q() SAU khi đã ghi disable
i             = cfg.maxRetryCount;
delays        = sameTier ? cfg.delayTimesS      // S = Same tier
                         : cfg.delayTimesD;     // D = Different tier
if (maxRetryCount <= 0 || !p9.i || delays.isEmpty())  → không retry
if (p9.h in [0, maxRetryCount))                       → retry với delays[attempt]
else                                                   → "retry exceeded count"
```

Xác nhận thứ tự list qua [e78.java:195-219](jadx_base/sources/defpackage/e78.java#L195): `objectRef`(→field `g`) = `delay_times_d`, `objectRef2`(→field `h`) = `delay_times_s`.

Nên tên config nghĩa là:
- **`delay_times_s`** — delay khi retry **vẫn cùng tầng** (tầng chưa bị disable, ví dụ NO_FILL nhưng chưa hết ngưỡng)
- **`delay_times_d`** — delay khi retry **đã tụt tầng** (tầng vừa fail bị cấm, `q()` trả về tầng khác)

Mảng này index theo lần retry, nên `[2, 5, 10]` = retry 1 sau 2s, retry 2 sau 5s, retry 3 sau 10s. Default hiện tại `[2]` cho cả hai.

### Error code nào được retry — [g36.java](jadx_base/sources/defpackage/g36.java)

```java
return i == 0 || i == 2 || i == 3 || i == 9 || i == 10;
```
= INTERNAL_ERROR(0), NETWORK_ERROR(2), NO_FILL(3), MEDIATION_NO_FILL(9), 10. Code khác → `"not retrying fullscreen — error code N not retryable"`, không retry.

Riêng code 2/3 còn map sang enum lý do: `2 → h9.a`, `3 → h9.b`, khác → `h9.d` ([gb.java:177](jadx_base/sources/defpackage/gb.java#L177)).

---

## 6. `level` — KHÔNG phải tầng giá, mà là cache key

Đây là điểm dễ hiểu sai nhất. `level` trong `ad_places_*` là **khoá phân vùng cache** để share/tách ad instance.

`gb.o(ga, subPlaceLevel, orientation)` — [gb.java:1496-1520](jadx_base/sources/defpackage/gb.java#L1496):

```
cacheKey = "{v1_adId};{level};{-levelInOrder};{'nvf;' nếu native full}{subPlaceLevel};{bannerSize}{o<orientation>;}"
```

Rồi `gb.s()` tra `this.q` = `LinkedHashMap<cacheKey, p9 holder>` — [gb.java:1580](jadx_base/sources/defpackage/gb.java#L1580).

Hai hệ quả trực tiếp:

**a) Cache key neo trên `v1` ad id, không phải tầng đang dùng.** Nên cả 3 tầng H/M/D của cùng placement dùng **một ô cache duy nhất** — đúng thiết kế waterfall: một ô, tầng nào fill được thì điền.

**b) Cùng `v1 adId` + cùng `level` → cùng ô cache → share ad.** Đối chiếu config thật:

| Placement | `level` | `ad_id_v1` | Kết quả |
|---|---|---|---|
| `open_native_first`, `open_native`, `open_native_other` | `open_native` | `…/1288157121` | **share 1 native ad** — preload 1 lần ở splash, dùng lại cho mọi biến thể |
| `language_bottom` | `1` | `…/2197914112` | id giống nhau nhưng level khác |
| `language_2_bottom` | `1.1` | `…/2197914112` | → **2 ô cache riêng**, load 2 ad |
| `view_pdf_bottom`, `image_to_pdf_bottom`, `merge_pdf_bottom`, … (9 placement) | `2` | (từ format `bc`) | **share 1 collapsible banner** across toàn bộ tool screen |

Nên `level` vừa dùng để **gộp** (tiết kiệm request) vừa để **tách** (`1` vs `1.1` khi cần 2 ad khác nhau trên cùng flow).

### `is_can_reuse_ads` — share xuyên cả cache key

Ngoài cache key khớp chính xác, `gb.s()` còn scan **toàn bộ** map tìm holder tái dùng được ([gb.java:1589-1655](jadx_base/sources/defpackage/gb.java#L1589)). Điều kiện:

- Cùng loại holder (`sw` banner / `gs6` native)
- Chưa bị tiêu thụ (`!p9.e`)
- Đã có ad load xong (`.o != null` hoặc `.p != null`)
- `ga.l()` == `is_can_reuse_ads` == **true**
- Riêng native: `hs6.r(other)` — chỉ reuse giữa hai native **cùng họ template** (full ↔ full, non-full ↔ non-full), [hs6.java:252](jadx_base/sources/defpackage/hs6.java#L252)

Log phân biệt rõ: `"NativeAd $name holder reused from $otherName"` vs `"holder fresh (no reusable candidate)"`.

Trong config: chỉ `language_bottom`, `language_2_bottom`, `intro_bottom` bật `is_can_reuse_ads=true` — tức ad của màn language có thể được tái dùng cho màn intro.

### `subPlaceLevel` — chiều thứ ba

Số nguyên nằm trong cache key, cho phép **nhiều instance của cùng placement** (ví dụ nhiều native inline trong một list). `bd.c/e(…, subPlace, …)` truyền vào.

`gb.n(name, skipSet, count)` quét `subPlaceLevel` 0..count-1 tìm ad đã load xong:
```
"NativeFullAfterInter found loaded ad at subPlaceLevel=$i"
```

---

## 7. Native-interstitial: preload N+1 ad

[gb.java:754-757](jadx_base/sources/defpackage/gb.java#L754) — khi placement fullscreen là **native** (template họ `full*`):

```java
int n = hs6.s + 1;                                   // hs6.s = native_inter_ad_refresh_count
for (int i = 0; i < n; i++)
    bd.e(this, activity, name, /*subPlace=*/i, 8);   // load n ad riêng biệt
```

Đã verify `hs6.s` = `native_inter_ad_refresh_count` bằng cách trace tham số thứ 18 của constructor `hs6` về [ha.java:569](jadx_base/sources/defpackage/ha.java#L569).

Config thật:
- `open_native_first` / `open_native` / `open_native_other`: `native_inter_ad_refresh_count = 1` → **2 native ad** được load
- `native_after_inter`: `= 0` → **1 native ad**

Lúc show, `gb.X()` gọi `n(name, {}, hs6.s + 1)` lấy ad đầu tiên đã load xong trong 0..N. Ad thứ 2 dùng để **refresh giữa countdown** — user thấy 2 ad khác nhau trong một lần "interstitial".

Kèm theo: `native_inter_countdown_sec=6` (`hs6.p`), `native_inter_delay_after_countdown_sec=0` (`hs6.q`), `native_inter_countdown_style=text_short`.

---

## 8. Ba cổng chặn trước khi load/show

### `gb.z(name)` — banner / native / interstitial / appopen — [gb.java:1851](jadx_base/sources/defpackage/gb.java#L1851)

```
1. country ∈ config_countries_disable_ads        → "blocked: country not allowed"
2. now < nn.k()  (hết hạn chống click-fraud)      → "blocked: too many recent clicks"
3. ga.q(nn.isUserPaidToRemoveAds())               → "blocked: not valid to load"
```

### `gb.A(name)` — rewarded (chỉ khác log prefix `Visibility(rewarded)`, không có check click-fraud)

### `ga.q(isSubscriber)` — [ga.java](jadx_base/sources/defpackage/ga.java)

```java
if (cả 3 ad id đều rỗng || !isEnable || adType == None)  return true;   // skip
return isSubscriber && !isEnableForSubs;                                // subscriber mà place không bật cho subs
```

Mapping flag (verify chéo qua log string và call site):

| `ga` method | Config field | Dùng ở |
|---|---|---|
| `m()` | `is_enable` | `q()`, log `isEnable=` |
| `o()` | `is_enable_for_subs` | `q()` |
| `l()` | `is_can_reuse_ads` | `gb.s()` reuse scan |
| `p()` | `is_ignore_interval` | bỏ qua frequency cap ([gb.java:1043](jadx_base/sources/defpackage/gb.java#L1043), 768, 1275) |
| `k()` | derived: `type ∈ {Banner, Native}` | routing load path |

### Frequency cap

`if (!ga.p()) { check now - lastShown >= interval }` — biến static toàn cục: `nq7.b`/`nq7.c` cho fullscreen, `pq7.b`/`pq7.c` cho squeeze.

`gb.B()` = cổng riêng cho collapsible banner: `now - lastCollapsibleShown > config_ads_banner.banner_collapsible_time_interval`.

Chống click-fraud (`config_prevent_ad_click`): >6 click / 300s → set `nn.k() = now + 1800s`, chặn toàn bộ ad 30 phút.

---

## 9. Facade Activity gọi — [bd.java](jadx_base/sources/defpackage/bd.java)

| Hàm | Tên thật (từ log) | Chuyển tới |
|---|---|---|
| `bd.c(mgr, act, name, subPlace, flags)` | `→ preloadBannerNative` | `gb.D(act, name, show=false, subPlace)` |
| `bd.e(mgr, act, name, subPlace, flags)` | `→ showBannerNative` | `gb.D(act, name, show=true, subPlace)` |
| `bd.d(mgr, act, name, z, i, flags)` | — | `gb.T(...)` show fullscreen |
| `bd.b(mgr, act)` | `hidePipAd pip_home` | ẩn pip |
| `bd.f(mgr, act, name)` | — | re-show toàn bộ squeeze-back |
| `gb.R(act, name, notify)` | `→ preloadFullScreenAd` | load inter / native-inter / rewarded |
| `gb.X(act, name)` | `→ showFullScreenAd` | show |
| `gb.a0(act, name)` | `showPipAd` | pip |
| `gb.S(act, name)` | — | squeeze-back |

**Chi tiết đáng lưu ý trong `bd.c`:** preload bị **skip** cho banner collapsible khi gọi cross-activity —
```
"← preloadBannerNative $name skipped: collapsible (cross-activity)"
```
Vì collapsible banner phải request trong đúng Activity sẽ hiển thị nó.

`gb.X()` với interstitial set `p9.f = config_ads_interstitial.is_wait_load_to_show` — quyết định show có chờ load xong hay bỏ luôn.

---

## 10. Vị trí & thời điểm — placement map

### Splash — chuỗi dày nhất ([SplashActivity.java](jadx_base/sources/com/roosterx/featurefirst/splash/SplashActivity.java))

Placement được chọn động bởi [i99.java:159-162](jadx_base/sources/defpackage/i99.java#L159):

```java
// app_open place
N() ? "open_app_other"                    // hot start (quay lại từ background)
    : isFirstOpen ? "open_app_first"       // lần mở đầu tiên
    : "open_app"

// fullscreen place  — native hay interstitial do config quyết định
N()            ? (config.adType      == Native ? "open_native_other" : "open_other")
: isFirstOpen  ? (config.adTypeFirst == Native ? "open_native_first" : "open_first")
:                (config.adType      == Native ? "open_native"       : "open")
```
`config.adTypeFirst` = `config_screen_splash.ad_type_first_open`, `config.adType` = `.ad_type`. Hiện tại cả hai = `"app_open"` nên nhánh interstitial (`open_first`/`open`) đang active.

Timeline thực tế:

| Dòng | Hành động |
|---|---|
| 303 | `bd.e(… "splash_bottom")` — **show** banner splash |
| 305 | analytics `fo_1_splash_ad_request` |
| 309 | `gb.R(this, H(), true)` — **preload** fullscreen (place từ `i99`) |
| 312 | `bd.c(… "language_bottom", 0, 28)` — **preload** native màn language |
| 314 | `bd.c(… "language_2_bottom", 0, 28)` — preload native language v2 |
| 335 / 345 | `gb.X(this, H())` — **show** fullscreen |
| 428 | `bd.c(… "uninstall_bottom")` — preload |
| 434 | `bd.c(… "main_bottom")` — preload banner home |
| 436 | `bd.c(… "home_item_inline")` — preload native home |
| 440-442 | `→ preloadPiPAd pip_home` |
| 645 | `gb.S(this, "squeeze_view_pdf")` — squeeze-back |
| 688 | `bd.d(… "splash_bottom", false, …)` |

`config_screen_splash` điều tiết nhịp: `progress_time=30` (tối đa 30s), `progress_time_min_to_show_ad=3` (chờ ít nhất 3s trước khi show), `progress_time_min_to_next_screen_when_ads_failed=3`, `time_display_banner_before_show_full_ad=3000`.

### Bảng placement đầy đủ

**App Open** (4) — `ad_id_v1 = …/1020563810`, `app_open_delay_before_show_millis` 200ms (500ms cho reopen)

`open_app_first` · `open_app_other` · `open_app` · `reopen_app`

`reopen_app` trigger từ [an.java:132](jadx_base/sources/defpackage/an.java#L132), [hn.java:79](jadx_base/sources/defpackage/hn.java#L79), [in.java:26](jadx_base/sources/defpackage/in.java#L26) — lifecycle observer, chạy khi app quay lại foreground sau `config_app.timer_in_second_show_splash_after_app_go_to_background` = **3600s**.

**Interstitial** (22) — tất cả có `native_full_after_inter_name: "native_after_inter"` → sau khi đóng interstitial, show tiếp một native full

| Nhóm | Placement |
|---|---|
| Splash | `open_first` `open_other` `open` (cả 3: `is_auto_load_after_dismiss=false`, `is_ignore_interval=true`) |
| Sau paywall | `open_view_pdf_from_iap` `open_item_tool_from_iap` `open_scan2pdf_export_from_iap` |
| Back button | `back_in_view_pdf` `back_in_merge` `back_in_split` `back_in_image_to_pdf` `back_in_create_pdf` `back_in_converter` `back_in_scanner`(`is_enable_ogn=false`) `back_in_ocr` `action_back_from_restore_recycler_bin` |
| Sau khi xong việc | `back_in_merge_success` `back_in_split_success` `back_in_convert_success` `back_in_pdf_to_image_success` `pdf_to_image_success_open_file` `convert_success_open_file` `converter_success_open_file` |

**Banner** (14) — `level=2` cho 9 tool screen (share 1 instance), collapsible bottom, `is_show_only_once=true`, delay 100ms

`splash_bottom`(L0, **disabled**) · `main_bottom`(L1, **disabled**) · `recycler_bin_bottom`(L2) · `view_pdf_bottom` · `image_to_pdf_bottom` · `merge_pdf_bottom` · `split_pdf_bottom` · `scan2pdf_bottom` · `create_pdf_bottom` · `pdf_to_image_bottom` · `converter_bottom` · `edit_pdf_bottom` · `extract_text_bottom`(L3) · `pip_home`(pip, bottom_right)

**Native** (18)

| Placement | level | template | Ghi chú |
|---|---|---|---|
| `open_native_first/_/_other` | `open_native` | `full` | native-interstitial, refresh_count=1 |
| `native_after_inter` | `native_after_inter` | `full` | chạy sau mọi interstitial, `is_enable_ogn=false` |
| `language_bottom` | `1` | `small` | reuse=true |
| `language_2_bottom` | `1.1` | `small` | reuse=true |
| `intro_bottom` | `2` | `small` | reuse=true |
| `home_recent_inline` | `2` | `small_cta_right` | **disabled** |
| `home_item_inline` | `3` | `small_icon_left` | `is_enable_ogn=false` |
| `tools_inline` | `2` | `small` | `is_enable_ogn=false` |
| `setting_inline` | `medium` | `medium_media_left` | `is_enable_ogn=false` |
| `inline_single_success` / `inline_multiple_success` | `medium` | `medium_media_left` | |
| `restore_success_file_bottom` | `medium` | `medium` | |
| `pdf_to_image_success_inline` | `medium` | `medium` | |
| `viewer_last_page` | `medium` | `medium` | `is_enable_ogn=false` |
| `squeeze_view_pdf` / `squeeze_tool_all` | `squeeze` | — | `ad_type=squeeze_back` |

---

## 11. Phân tầng theo user & quốc gia

Mỗi placement override được theo 4 chiều, resolve trong `ha.a()`:

```
base → t1/t2/t3 (country tier) → ogn (organic user)
```

- **Country tier**: `ConfigCountryTierModel` chia ~160 country code thành t1/t2/t3. Tier hiện tại lấy qua `e78.P()` → so country code với 2 list.
- **`ogn`**: `nn.isUserCameFromOrganic` (u0[1]) — user không đến từ ad campaign. `is_enable_ogn=false` ở `native_after_inter`, `home_item_inline`, `tools_inline`, `setting_inline`, `viewer_last_page`, `back_in_scanner` → **những chỗ này chỉ hiện ad cho user paid-acquisition**, organic user không thấy.
- **`level_for_subs`** format `"1|3"` / `"0|2"` — level thay thế khi user là subscriber. Vì `is_enable_ads_for_subscribers=true`, subscriber vẫn thấy ad nhưng ở level (ô cache) khác, và `is_native_convert_to_banner_for_subscribers=true` đổi native→banner.

---

## 12. Tóm tắt luồng hoàn chỉnh

```
Activity gọi bd.c/bd.e/gb.R(placeName)
   │
   ├─ gb.p(name) → e78.z(name) → ha.a(AdPlaceModel)
   │     resolve ad id: format → placement → country tier → organic
   │     → ga { la(v1), oa(v2), na(v3), flags, level, levelInOrder }
   │
   ├─ gb.z(name)  cổng: country ban? click-fraud? isEnable? subscriber?
   │
   ├─ gb.o(ga, subPlace, orient) → cacheKey  (neo trên v1 adId + level)
   ├─ gb.s(...)   tra LinkedHashMap; không có → scan holder reuse được
   │              (cùng type + đã load + is_can_reuse_ads + template tương thích)
   │
   ├─ gb.q(ga)    ★ CHỌN TẦNG
   │     now > expiredHighFloor[type]   → h()  H→M→D
   │     now > expiredMediumFloor[type] → i()      M→D
   │     else                            → a()        D
   │     ga.a = tầng đã chọn;  log "adUnit tier=H/M/D adId=…"
   │
   ├─ AdMob load với ad id của tầng đó
   │
   ├─ FAIL ──► lb: nếu ga.a là na → expiredHigh   = now + time_disable_v3
   │           nếu ga.a là oa → expiredMedium = now + time_disable_v2
   │           │
   │           └─ gb.g(): code retryable (0/2/3/9/10)?
   │                 gọi lại q() → cùng tầng? → delay_times_s
   │                             → khác tầng? → delay_times_d
   │                 attempt < max_retry_count → retry sau delays[attempt]
   │
   └─ OK ───► holder giữ ad; show khi gb.X()/gb.D(show=true)
              frequency cap: !ga.p() → check now - lastShown >= interval
```

---

## 13. Nhận xét

**Thiết kế tốt:**
- Waterfall 3 tầng ở app-level + timestamp disable per-type là cách hiệu quả để ăn high-floor inventory mà không trả giá latency mỗi lần request. Tầng D luôn có sẵn nên fill rate không bị ảnh hưởng.
- Tách `delay_times_s` / `delay_times_d` khá tinh: retry cùng tầng thì có thể chờ lâu hơn, tụt tầng rồi thì retry nhanh vì khả năng fill cao.
- Cache key neo trên v1 adId (không phải tầng hiện tại) là quyết định đúng — nếu neo trên tầng đang dùng thì mỗi lần tụt tầng sẽ tạo ô cache mới và mất ad đã preload.
- Chống click-fraud tự động (6 click/300s → nghỉ 30 phút) bảo vệ tài khoản AdMob khỏi invalid traffic.

**Hai điểm nên xem lại nếu đây là code của bạn:**

1. **Pip dùng chung slot disable với Banner; SqueezeBack dùng chung với Native** (mục 3). 8 ad type nhưng chỉ 6 cặp prefs slot. Hệ quả: `pip_home` no-fill ở high floor sẽ kéo toàn bộ banner xuống medium floor trong 60s, dù banner có thể vẫn fill được high floor bình thường. Nếu không phải chủ ý thì thêm 2 cặp slot nữa vào `nn`.

2. **Timestamp disable là per-ad-type toàn cục, không per-placement.** Một placement interstitial ở màn hình ít traffic no-fill sẽ hạ tầng cho cả 22 placement interstitial còn lại. Đánh đổi này có lý (ít prefs, phản ứng nhanh với tình trạng inventory chung) nhưng nếu các placement có eCPM rất khác nhau thì đang bỏ tiền trên bàn.

**Về config hiện tại — đọc kỹ phạm vi kết luận:**

`config_format_ad_id` trong `res/xml/remote_config_defaults.xml` chỉ set `v1` cho cả 9 format,
và `config_country_tier` = `{}`.

Nhưng đây là **default đóng gói trong APK**, chỉ dùng khi chưa fetch được Firebase hoặc fetch fail.
Giá trị thật đang chạy do server quyết định và **không đọc được từ APK**. Vì vậy **không thể kết luận
waterfall H/M hay phân tier quốc gia đang tắt** — chỉ có thể nói: ở lớp default thì chúng chưa được
cấu hình. Muốn biết trạng thái thật phải mở Firebase Console.

(Bản trước của tài liệu này viết "waterfall H/M đang không hoạt động trong bản này" — đó là kết luận
quá phạm vi dữ liệu, đã sửa.)

---

## 14. Hai bất thường trong config ads

### 14.1. `banner_refresh_rates_s` khai 9 mạng, APK chỉ có 4

`config_ads_banner` đặt refresh rate riêng cho từng mạng:

```jsonc
"banner_refresh_rates_s": { "admob": 30, "meta": 20, "iron_source": 20, "liftoff": 20,
                            "pangle": 20, "bigo": 20, "mintegral": 20, "moloco": 20,
                            "unity": 20 }
```

Đã grep toàn bộ APK (`apktool_base/`) tìm adapter: chỉ có **Pangle, Meta Audience Network,
Mintegral, Bigo** (+ AdMob là mạng chính). Không tìm thấy `ironsource`, `liftoff`, `moloco`,
`unity`, `applovin`, `vungle`, `inmobi`, `chartboost`, `fyber`.

⇒ 4 key `iron_source`, `liftoff`, `moloco`, `unity` là **config chết** — Map entry không ai tra.
Không gây lỗi runtime, nhưng gây nhầm khi đọc config tưởng app mediation 9 mạng.
Khả năng cao là config template dùng chung cho nhiều app trong portfolio của publisher.

Cũng lưu ý: XML đặt `admob=30` còn 8 mạng kia `=20`, trong khi code default (`qa2.d`) đặt
**tất cả =30**.

### 14.2. `b` và `bc` dùng chung ad unit

`config_format_ad_id` trỏ cả hai về `…/5807639021`:

```jsonc
"b":  { "v1": "ca-app-pub-3559750835852503/5807639021" },   // banner thường
"bc": { "v1": "ca-app-pub-3559750835852503/5807639021" },   // banner collapsible
```

Banner anchored và banner collapsible là 2 format khác nhau trên AdMob với eCPM chênh rõ rệt.
Dùng chung unit ⇒ **không tách được doanh thu 2 loại trong báo cáo AdMob**, nên không đo được
collapsible có hiệu quả hơn không. Tách unit chỉ là thay đổi remote config, không cần dev.

Lưu ý thêm: 13/14 placement banner trong `ad_places_banner` bật `banner_collapsible_position:
"bottom"`, tức phần lớn traffic banner thực tế đi qua nhánh `bc`.

---

## 15. Hai lớp default local và chỗ lệch nhau

Precedence: **Firebase server → `res/xml/remote_config_defaults.xml` → hardcode trong code**.
Lớp hardcode chỉ chạy khi JSON parse fail hoặc key thiếu ở cả hai nơi
([l78.java:183](jadx_base/sources/defpackage/l78.java#L183): `if (model == null) return new yb2(0);`).

Class chứa default hardcode (trong `defpackage/`): `na2.d` app_open · `qa2.d` banner ·
`wa2.d` native · `ta2.d` interstitial · `ic2.d` pip · `jd2.d` squeeze · `db2.d`/`bb2.d` rewarded ·
`kc2.b` prevent_ad_click · `hb2.d` config_app · `ec2.d` iap.

Chỗ 2 lớp lệch nhau — tức hành vi khi Firebase chết hoặc config lỗi cú pháp:

| Tham số | XML | Code hardcode |
|---|---|---|
| **IAP sku mặc định** | `3days.20` | **`3days.30`** |
| **splash sau khi về background** | 3600s (1 giờ) | **300s (5 phút)** |
| `prevent_ad_click.time_per_session` | 300s | **120s** |
| `max_retry_count` inter / native / app_open | 3 | **0** |
| `max_retry_count` banner | **-1** (⇒ không retry) | 5 |
| interstitial `time_interval` | 35s | 37s |
| interstitial `time_interval_after_show_open_ad` | 5s | 10s |
| banner `collapsible_time_interval` | 60s | 45s |
| pip `time_interval` | 30s | 15s |
| pip `max_show_per_session` | 10 | 20 |
| squeeze `is_wait_load_to_show` | **true** | false |

Hai dòng đầu đều nghiêng về hướng **hiếu chiến hơn** khi config fail: bán gói đắt nhất và
app-open ad dày gấp 12 lần. Không phải bug, nhưng một lỗi cú pháp JSON khi publish config có
thể âm thầm đổi giá bán và mật độ quảng cáo.

Chi tiết đầy đủ (nội dung JSON từng block, cả 22 key): [CONFIG_DEFAULTS_LOCAL.md](CONFIG_DEFAULTS_LOCAL.md)
