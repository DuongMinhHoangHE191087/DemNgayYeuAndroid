# 42 — Teardown monetization: game "AnTQ" (Ghidra export libil2cpp)

> Chạy theo khuôn prompt doc 41. **Đọc mục "Giới hạn input" trước khi dùng bất kỳ con số nào.**

```text
DỰ ÁN:      AnTQ — game casual chủ đề kiến/sort thức ăn (package name: [U])
LOẠI INPUT: Ghidra C++ export của libil2cpp.so (Android arm64), IL2CPP metadata đã khôi phục TÊN
ĐƯỜNG DẪN:  /Users/dungdm/ghidra_export
THỂ LOẠI:   casual puzzle + meta sưu tập thẻ + nhiều event LiveOps
THỊ TRƯỜNG: [U] (có cơ chế country code + campaign, không đọc được giá trị)
ĐỘ SÂU:     full (mục 0–13)
```

---

## Giới hạn input (đọc trước — quyết định độ tin cậy toàn báo cáo)

| Có gì | Không có gì |
|---|---|
| `null.h` — **bảng ký hiệu đầy đủ 320.081 method** đã khôi phục tên lớp/method/tham số + VA | **Thân hàm của các method đã đặt tên** — Ghidra export thiếu `null.cpp`; kiểm chứng: VA `0x3ac1a10` (`SDK::AdsManager::SetupInterstitial`) chỉ tồn tại trong `null.h:3175`, không có body ở bất kỳ file nào |
| `GlobalFunctions.cpp` (73MB) — body của ~124k hàm **chưa đặt tên** (`FUN_*`) | **Toàn bộ string literal managed** — nằm trong `global-metadata.dat`, không có trong export. Kiểm chứng: `grep -c 'ca-app-pub' GlobalFunctions.cpp` = 0, `'interstitial'` = 0, `'com.'` = 0 |
| Tên property (getter/setter) → suy ra được **tên field** | **Field public không có accessor** → IL2CPP không sinh method → vô hình. Các lớp chỉ có `::E` (constructor) là data class dạng này: `AdsConfig`, `InterLoseConfig`, `CustomRemoteConfig`, `ShopPackageDataConfig`, `OfferDetails`, `RewardAdsPlacementConfig` |
| | **Giá trị enum** (vd `PackageName`, `AdsType`, `ResourceType`) |

**Hệ quả:** báo cáo này là **teardown cấu trúc (grey-box)**. Mọi *kiến trúc, luồng, cổng chặn, bề mặt điều khiển* đều là `[V]`. Mọi *con số, key remote config, ad unit id, SKU id, giá* đều là `[U]`. Không có một con số nào bịa ra trong tài liệu này.

---

## 0. Danh tính & kiểm kê stack

**Kết luận:** Unity 6.3 IL2CPP, Android arm64, **hai mediation song song (AdMob + AppLovin MAX) chạy dưới một facade tự viết tên namespace `SDK`**, MMP là AppsFlyer, remote config là Firebase + **một kênh HTTP riêng thứ hai**.

| Hạng mục | Giá trị | Nhãn | Bằng chứng |
|---|---|---|---|
| Engine | Unity **6000.3.18f1** (Unity 6.3), IL2CPP | [V] | chuỗi đường dẫn `/Applications/Unity/Hub/Editor/6000.3.18f1/...` trong `GlobalFunctions.cpp` |
| Nền tảng | Android arm64 (`AndroidJavaObject`, `ro.arch`) | [V] | `GlobalFunctions.cpp` |
| Kích thước export | 142 MB / 320.081 method đã đặt tên | [V] | `du -sh`, `grep -c ZN null.h` |
| Package name / version | — | [U] | không có string metadata |

### Bảng SDK

| Vai trò | SDK | Có trong bin? | Bằng chứng |
|---|---|---|---|
| Ad mediation #1 | **Google Mobile Ads (AdMob)** — 1.305 symbol, có `NextGen*AdClient` (SDK đời mới) | [V] Có, đầy đủ | `GoogleMobileAds::Api::*`, `SDK::AdmobMediationController` (`null.h:3045–3146`) |
| Ad mediation #2 | **AppLovin MAX** | [V] Có, đầy đủ | `MaxSdkBase`/`MaxSdkAndroid`/`MaxSdkCallbacks`, `SDK::MaxMediationController` (`null.h:3324–3405`) |
| Ad mediation #3 | **IronSource** | [V] **KHAI nhưng KHÔNG có SDK** | `IronSourceMediationController` chỉ còn 2 symbol (`null.h:574–575`: `GetAdsMediationType`, ctor); `SDK::IronSourceAdSetup` chỉ có ctor (`null.h:701`); `SDKSetup::get_sdk_IronSource` (`null.h:596`) và `SDK::AdsManager::UpdateIronSourceMediation` (`null.h:3195`) vẫn tồn tại → **lệch config↔bin** |
| CMP | **Google UMP** | [V] | `GoogleMobileAds::Ump::Api::*`, `SDK::AdmobMediationController::InitConsent` (`null.h:3047`), `ConsentForm::LoadAndShowConsentFormIfRequired` |
| MMP | **AppsFlyer** (có consent API + ATT wait) | [V] | `AppsFlyerSDK::*` (236 symbol), `SDK::AppsFlyerManager` (`null.h:3027–3044`) |
| MMP khác | Adjust / Singular / Tenjin / Facebook SDK | [V] **KHÔNG có** | `grep AdjustSdk\|Singular\|Tenjin\|Facebook::Unity all_symbols` = 0 (chỉ có nút "Facebook" mở link trong Setting) |
| Analytics | Firebase Analytics | [V] | `SDK::FirebaseAnalyticsManager` (`null.h:703–710`) |
| Crash | Firebase Crashlytics | [V] | `Firebase::Crashlytics::*` (38 symbol) |
| Push | Firebase Messaging | [V] | `Firebase::Messaging::*` (227 symbol), `SDK::FirebaseManager::OnTokenReceived` |
| Remote config | Firebase Remote Config | [V] | `SDK::FirebaseRemoteConfigManager` (`null.h:3443–3451`) |
| Remote config #2 | **Kênh HTTP tự viết `ABILibsCustomEventConfig`** | [V] | `ABILibsCustomEventConfig::FetchAndApplyHttpAsync`, `::StartAutoHttpFetch` (`null.h:526–529`) |
| Billing | **Unity IAP** (Google Play + Apple, 2.838 symbol) | [V] | `UnityEngine::Purchasing::*`, `Purchaser` (`null.h:13604–13639`) |
| Social/Auth | Google Play Games (589 symbol) + backend tự viết | [V] | `GooglePlayGames::*`, `GameBackendModule::Services::AuthService` |
| Backend riêng | REST tự viết: Auth / Player / Clan / Team / Leaderboard / WeeklyContest / Purge | [V] | `GameBackendModule::Services::*` |
| Haptics / UI / anim | Lofelt NiceVibrations, Spine, LitMotion, R3, UniTask, Zenject, MoreMountains Feel, Sirenix | [V] | namespace tương ứng |

**Phát hiện lệch:** IronSource nằm trong enum mediation + có ô config `sdk_IronSource` nhưng **không có adapter/SDK nào trong binary**. Nếu remote config lỡ trỏ mediation sang IronSource → `GetAdsMediationController` trả về controller rỗng → mất toàn bộ ad. Đây là **rủi ro kill-switch ngược**.

---

## 1. Bản đồ bề mặt monetization (placement map)

**Kết luận:** 6 định dạng ad (banner, collapsible banner, MREC, interstitial, rewarded, app-open) + 3 dòng IAP (NoAds, StarterPack, PackWorldCup) + shop pack. Điểm dày nhất là **màn thua level** — nơi 4 loại lời mời tranh nhau qua một bộ trọng tài điểm số.

| Màn hình / Trigger | Loại | Điều kiện hiển thị (theo tên API) | Khóa điều khiển | Tắt từ xa? | Nhãn |
|---|---|---|---|---|---|
| Mở app / resume | **App Open Ad** | `IsActiveAppOpenAds` → `coCheckingShowAppOpenAds` → `DelayShowAppOpenAds` → `ShowResumeAds` (`null.h:3267–3291`) | `UpdateAOARemoteConfig`, `UpdateRemoteConfigResumeAds` (`null.h:3164–3165`) | ✅ | [V] |
| Phiên đầu tiên | **Ad đầu tiên** | `ShowAdsFirstTime` (`null.h:3278`) — có nhánh riêng cho lần đầu | [U] | [I] | [V] cấu trúc |
| Mở lại app | bỏ qua N inter đầu | `InterAdsCappingRemoteConfigData::GetSkipFirstInterCountOnReopen` (`null.h:11636`) | key `InterAdsCapping` | ✅ | [V] |
| Home + In-game | **Banner** / **Collapsible banner** | `UpdateBanner`, `UpdateCollapsibleBanner(float)`, `IsCollapsibleBannerShowingTimeOut` (`null.h:3167–3168, 3242`) | remote | ✅ | [V] |
| [U] màn nào | **MREC** | `SetupMRecAds`, `ShowMRecAds` (`null.h:3256–3265`) | remote | ✅ | [V] |
| Thắng/thua level, chuyển màn | **Interstitial** | `GameManager::CanShowAdsInter`, `CanShowInterLose`; `AdsController::ShowAdsInter(Action,…,string placement)` | `InterLoseConfig`, `LevelShowADSInter` | ✅ | [V] |
| Màn hình đệm "Ads Break" | overlay đếm ngược rồi bung inter | `ActivityAdsBreakContext::UIPresenter::DelayShowAds` → `OnShowAdsSuccess/Failed`; `ActivityNotificationOverlay::ShowAdsBreak` | [U] | [I] | [V] |
| Thua level (lock/hết slot) | **Rewarded — revive** | `Level::ReviveByLockStuck` / `ReviveByOutOfSlot`; `ReviveByAdsCount` vs `ReviveByCoinCount` | `ReviveRemoteConfigData` | ✅ | [V] |
| Cuối level | **Rewarded — x2 coin** | `EndGameRewardRemoteConfigData::CanShowX2Reward`, `GetAdsRewardCoin(LevelDifficulty)`; mở khóa từ level `DefaultX2CoinLevel` | `EndGameReward` | ✅ | [V] |
| Hết tim | **Rewarded — fill heart** | `DefaultGlobalConfig::IsShowAdsFillHeart` (`null.h:2834`), `ModalFillHeart` | `IsHeart`, `DefaultFillHeartTime` | ✅ | [V] |
| Chọn booster | **Rewarded — nhận booster** | `Level::GetBoosterByAdsCount` / `IncrementBoosterByAdsCount`; `BoosterRemoteConfigData::HasAdsForBooster` | `Booster` | ✅ | [V] |
| Home (icon) + auto sau khi thua | **Rewarded/IAP — "Help with Ads → Gold"** | `HelpWithAdsGoldManager::RecordLoseLevel` → `TryShowAutoPopup` / `TryShowHomeIconPopup`; mỗi option `ClaimAdsReward` **hoặc** `TryBuyGoldReward` | `HelpWithAdsGold` (`IsEnabled`) | ✅ | [V] |
| Mở thẻ | **Rewarded — card pack** | `CardCollectionManager::SetPlacementCardCollection(string)` / `GetPlacementCardCollection` | remote config riêng của CardCollection | ✅ | [V] |
| Tăng tốc in-game | IAP/coin | `ScreenInGame::OnClickBtnBuySpeedUp`, `ModalBuySpeedUp` | [U] | [I] | [V] |
| Home / sau khi xem đủ inter | **IAP NoAds** | `NoAdsPopupManager::MarkInterstitialWatched` → `TryShowOnHome`; `UIButtonNoAds` gated bằng `PackConditionGroup` | `NoAdsPopup` (`IsEnabled`), `NumberShowNoAds`, `CountShowNoAdsMax` | ✅ | [V] |
| [U] thời điểm | **IAP StarterPack** | `ModalStarterPackContext::UIPresenter::OnStarterPackSuccess(PackageName)` | [U] | [I] | [V] |
| Event | **IAP PackWorldCup** (có đồng hồ đếm ngược) | `ModalPackWorldCupContext::UIView::TextTime`, `OnViewUpdate` | [U] | [I] | [V] |
| Shop | shop pack (IAP / ads / coin) | `UIShopPack::OnIAPPurchase` / `OnAdsPurchase` / `OnMoneyPurchase` | `ShopGlobalConfig` | [I] | [V] |
| Thua level | **event offer** | `GameManager::ShowEventOnFailed`; `EventOfferManager::DelayShowOffer` → `ShowCurrentOfferAsync` | [U] | [I] | [V] |

### Chuỗi dày nhất — khoảnh khắc THUA

```text
LoseLevel / LoseByLockStuck / LoseByOutOfSlot
   │
   ├─ (a) Rewarded revive          Level::ReviveByLockStuck | ReviveByOutOfSlot
   │        giá leo thang: ReviveByAdsCount ↑ , ReviveByCoinCount ↑ (LevelManager::GetReviveCoin)
   ├─ (b) Interstitial             GameManager::CanShowInterLose  (config: InterLoseConfig)
   ├─ (c) HelpWithAdsGold popup    HelpWithAdsGoldManager::RecordLoseLevel → TryShowAutoPopup
   │        mỗi ô: xem ads (ClaimAdsReward) HOẶC mua bằng gold (TryBuyGoldReward)
   └─ (d) Event offer              GameManager::ShowEventOnFailed → EventOfferManager

   Trọng tài: LoseEventPriority::GetPoint  (gắn vào ModalLose, ModalLoseByLock,
              ModalRetryGame, ModalLeaveGame — null.h, mục 4.2)
```

---

## 2. KỊCH BẢN IAA (mục lõi)

### 2.1 Waterfall & tầng giá

**Kết luận:** waterfall thật nằm ở **danh sách ad-unit-id có thứ tự**, không phải ở tầng giá tự viết. AdMob dùng `List<string>` cho từng format; MAX dùng thứ tự do code dựng (`BuildInterstitialOrder`).

```text
SDKSetup  (null.h:592–628)  ← object cấu hình, [I] nạp từ remote config
 ├── sdkKey_MAX : string
 ├── sdk_IronSource : string
 ├── interstitialAdUnitID_MAX        : string          (1 id)
 ├── interstitialAdUnitID_ADMOB      : List<string>    ← WATERFALL nhiều id
 ├── interstitialAdUnitID_IRONSOURCE : string
 ├── rewardedAdUnitID_{MAX|ADMOB(List)|IRONSOURCE}
 ├── bannerAdUnitID_{…}
 ├── collapsibleBannerAdUnitID_{MAX|ADMOB(List)}
 ├── mrecAdUnitID_{MAX|ADMOB(List)}
 └── appOpenAdUnitID_{MAX|ADMOB(List)}
```

- MAX interstitial: `PreloadAllInterstitialAds` → `BuildInterstitialOrder` → `LoadInterstitialFirst` / `DelayLoadInterstitialFirst` → `LoadInterstitialAtIndex(i)` → `StartInterstitialLoadTimeout` / `InterstitialLoadTimeoutAsync(CancellationToken)` / `CancelInterstitialLoadTimeout` → `ShowInterstitialReady` (`null.h:3327–3336`). **[V]** — waterfall theo index + timeout huỷ được từng bậc.
- MAX rewarded: bộ 8 method đối xứng hoàn toàn (`PreloadAllRewardedAds` … `RewardReadyToShow`, `null.h:3345–3357`). **[V]**
- Số bậc, độ trễ timeout, thứ tự: **[U]**.

### 2.2 Ad unit resolution — thứ tự ưu tiên

```text
[1] AdmobAdSetup.<Format>AdUnitID : SDK::AdScheduleUnitID     ← lớp LỊCH (schedule)
        ├─ CurrentPlatformID : List<string>      (null.h:650)
        ├─ ChangeID()                            (null.h:646)  ← đổi id đang dùng
        ├─ Refresh()                             (null.h:647)
        └─ IsActive()                            (null.h:651)  ← id có đang bật không
[2] AdmobAdSetup.<Format>AdUnitIDList : List<string>          (null.h:561–572)
[3] SDKSetup.<format>AdUnitID_ADMOB : List<string>            (null.h:600–627)
[4] SDK::AdUnitID.ID : string                                 (null.h:698–699)
```

→ có **ít nhất 4 lớp** giữa "format" và "id thật gửi lên network", trong đó `AdScheduleUnitID` cho phép **xoay ad unit theo lịch mà không cần release app**. Đây là bề mặt LiveOps ad mạnh nhất của dự án. **[V] cấu trúc / [U] giá trị.**

### 2.3 Cache & preload

| Cơ chế | Bằng chứng | Nhãn |
|---|---|---|
| Preload toàn bộ waterfall cùng lúc | `MaxMediationController::PreloadAllInterstitialAds` / `PreloadAllRewardedAds` (`null.h:3327, 3345`) | [V] |
| Request theo format, tách biệt | `AdsManager::RequestInterstitial` / `RequestRewardVideo` / `RequestBanner` / `RequestCollapsibleBanner` / `RequestAppOpenAds` (`null.h:3196, 3214, 3234, 3243, 3275`) | [V] |
| Refresh collapsible banner theo chu kỳ | `RefreshCollapsibleBanner`, `UpdateCollapsibleBanner(float)`, `IsCollapsibleBannerShowingTimeOut` (`null.h:3168, 3233, 3242`) | [V] |
| Reload lại collapsible sau fail | `AdmobMediationController::ReloadCollapsibleBannerAds` (`null.h:3080`) | [V] |
| **Có share/reuse ad giữa các vị trí không** | Không thấy method kiểu `is_can_reuse_ads`. Nhưng cache là **theo format, không theo placement** (chỉ có 1 `interstitialAdUnitID` cho toàn game, placement chỉ là `string` truyền vào lúc show — `AdsController::ShowAdsInter(..., string)`) → **một instance inter dùng chung cho mọi placement** | [I] |
| Số ad preload, TTL cache | — | [U] |

**Rủi ro của share theo format:** placement chỉ là *nhãn analytics*, không phải *khoá cache*. Nếu về sau muốn giá sàn khác nhau theo placement (vd inter-sau-thua đắt hơn inter-chuyển-màn) thì kiến trúc hiện tại không tách được.

### 2.4 Fail & retry

| Cơ chế | Bằng chứng | Nhãn |
|---|---|---|
| Đánh dấu fail để hạ bậc | `SDK::AdsConfig::MarkReloadFail` (`null.h:655`) | [V] |
| Quyết định có đổi mediation không | `SDK::AdsConfig::IsGoodToChangeAds` (`null.h:656`) + `GetAdsMediation()` (`null.h:657–658`) | [V] |
| Nạp lại theo lịch | `SDK::AdsConfig::RefreshLoadAds` (`null.h:654`) | [V] |
| Cooldown sau load fail | `AdsManager::ResetAdsLoadingCooldown` (`null.h:3203`) | [V] |
| Timeout từng bậc waterfall (MAX) | `InterstitialLoadTimeoutAsync(CancellationToken)` / `RewardedLoadTimeoutAsync` (`null.h:3334, 3352`) | [V] |
| Callback fail đầy đủ mọi format | `OnInterstitialAdFailedToLoad`, `OnInterstitialAdShowFail`, `OnRewardVideoShowFail`, `OnBannerLoadedFail`, `OnCollapsibleBannerLoadedFail`, `OnAppOpenAdLoadFailedEvent`, `OnMRecAdLoadFailedEvent` | [V] |
| **Rewarded fail → chuyển sang interstitial** | `MaxMediationController::FallbackToInterstitial` (`null.h:3366`), nằm ngay sau khối rewarded | [V] tồn tại / [I] ngữ nghĩa |
| Delay retry theo bậc (giây), error code nào retry, disable timestamp per-ad-type | — | [U] |

**`FallbackToInterstitial` cần đọc kỹ khi có body.** Có hai khả năng: (a) chỉ dùng khi rewarded *không load được* và game chủ động hỏi lại người chơi — chấp nhận được; (b) người chơi bấm "xem để nhận thưởng", rewarded fail, game **âm thầm chiếu interstitial rồi vẫn/không trả thưởng** — đây là anti-pattern nặng. Không phân biệt được từ tên method. Xem mục 9.

### 2.5 Frequency cap & pacing

| Biến | Bằng chứng | Nhãn |
|---|---|---|
| Capping interstitial (đồng hồ đếm ngược) | `AdsController::InterCapping` / `RefreshInterCapping` (`null.h:13522–13523, 13540`) | [V] |
| **Capping riêng sau khi xem rewarded** | `AdsController::InterCappingAfterReward` (`null.h:13524–13525`) — tách biệt với capping thường | [V] |
| Cooldown giữa 2 inter | `AdsController::InterCooldown` (`null.h:13526–13527`) | [V] |
| Cờ "vừa chiếu xong" | `AdsController::InterJustShowed` (`null.h:13530–13531`) | [V] |
| Cờ "đã từng chiếu inter lần đầu chưa" | `AdsController::HasShownFirstInterEver` (`null.h:13532–13533`) | [V] |
| Reset đồng hồ capping | `AdsManager::ResetAdsInterstitialCappingTime` (`null.h:3204`) | [V] |
| Cap theo level | `InterAdsCappingRemoteConfigData::GetInterCapping` + lớp lồng `LevelCappingConfig` (`null.h:11633, 11635`) → **cap thay đổi theo level** | [V] |
| Bỏ qua N inter đầu khi mở lại app | `GetSkipFirstInterCountOnReopen` (`null.h:11636`) | [V] |
| Đếm rewarded interrupt | `AdsManager::IsReadyToShowRewardInterrupt`, `ResetRewardInterruptCount` (`null.h:3247, 3255`) | [V] |
| **Tất cả giá trị giây/số lần** | — | **[U]** |

### 2.6 Cổng chặn trước khi show

```text
ShowAdsInter(placement)
  └─ AdsController::CheckCanShowAds                       null.h:13542
       ├─ GameManager::CanShowAdsInter / CanShowInterLose
       ├─ AdsManager::IsReadyToShowInterstitial            null.h:3210
       ├─ AdsManager::IsPassLevelToShowInterstitial        null.h:3211   ← cổng theo LEVEL
       │     (default đóng gói: DefaultGlobalConfig::LevelShowADSInter, null.h:2826)
       ├─ AdsManager::IsInterstitialAdLoaded               null.h:3198
       ├─ AdsManager::get_IsShowingAds / MarkShowingAds    null.h:3202, 3208, 3266
       │     (khoá không cho 2 ad chồng nhau)
       ├─ InterCapping / InterCooldown / InterJustShowed   (2.5)
       └─ RemoveAds entitlement:
            AdsManager::LoadRemoveAds / SetRemoveAds(bool) null.h:3160, 3180
            CheckRemoveAds (delegate)                      classes.txt
```

- **Thời điểm sớm nhất được chiếu ad:** có nhánh riêng `ShowAdsFirstTime` + `coWaitingMarkShowingAdsDone` + chờ Firebase xong (`coWaitForFirebaseInitialization`, `null.h:3169`) → ad **không chiếu trước khi remote config sẵn sàng**. Đây là thiết kế đúng. **[V]**
- **Click-fraud guard:** không tìm thấy method nào đếm click / khoá theo cửa sổ thời gian. **[V] không có.**
- **Visibility check:** `IsBannerShowing`, `IsCollapsibleBannerShowing`, `IsMRecShowing`, `IsCollapsibleBannerExpended` — có. **[V]**
- Giá trị `LevelShowADSInter` thật đang chạy: **[U]**.

### 2.7 Phân tầng theo user

| Chiều | Có? | Bằng chứng | Nhãn |
|---|---|---|---|
| Đã mua NoAds | ✅ | `LoadRemoveAds` / `SetRemoveAds` / `CheckRemoveAds`; `CoreData::ShopData::IsPackPurchased(string)` | [V] |
| Organic vs non-organic | ✅ (có tín hiệu) | `DefaultGlobalConfig::CampaignName` + `CampaignID` (`null.h:2840–2842`), `AppsFlyerManager::TrackGetCampname` (`null.h:3044`) | [V] tín hiệu / [I] có dùng để phân tầng ad hay không |
| Country | ✅ (có tín hiệu) | `PlayerService::UpdateCountryCode(UpdateCountryCodeRequest)` | [V] tín hiệu / [U] có tier giá/tier ad theo country không |
| Phiên đầu vs phiên sau | ✅ | `HasShownFirstInterEver`, `ShowAdsFirstTime`, `GetSkipFirstInterCountOnReopen` | [V] |
| Theo level / độ khó | ✅ | `IsPassLevelToShowInterstitial`, `LevelCappingConfig`, `GetAdsRewardCoin(LevelDifficulty)` | [V] |
| Người đã từng trả tiền (nhưng chưa mua NoAds) | ❓ | chỉ thấy `ShopData::IsPackPurchased` theo từng pack, không thấy cờ tổng "is payer" dùng cho ad | [I] không có |
| Phiên bản OS | ❌ | không tìm thấy | [V] không có |

### 2.8 Rewarded

| Điểm mời xem | Phần thưởng | Bằng chứng |
|---|---|---|
| Thua vì kẹt khoá / hết slot | revive | `Level::ReviveByLockStuck`, `ReviveByOutOfSlot`, `ReviveByAdsCount` |
| Cuối level | x2 coin | `EndGameRewardRemoteConfigData::CanShowX2Reward`, `GetAdsRewardCoin(LevelDifficulty)` |
| Hết tim | tim | `DefaultGlobalConfig::IsShowAdsFillHeart` |
| Chọn booster | booster | `Level::GetBoosterByAdsCount`, `BoosterRemoteConfigData::HasAdsForBooster` |
| Popup "Help with Ads → Gold" | gold/booster, 4 ô | `HelpWithAdsGoldManager::ClaimAdsReward(option, ResourceType)` |
| Card pack | thẻ | `CardCollectionManager::SetPlacementCardCollection` |
| Shop pack | pack | `UIShopPack::OnAdsPurchase` / `OnAdsPurchaseSuccess` |

**Manufactured fail (cố tình tạo thất bại để mời xem ad):**
- **Không tìm thấy** cơ chế cố tình thua. Nhưng có 3 thứ cần đọc body khi có: `Core::GameManager::ForceUseBooster(ResourceType)` + `ForceUseBoosterRemoteConfigData` (bật/tắt từ xa) — **ép người chơi dùng booster**; `AlertLevelDifficult` với `HardLevel` / `SuperHardLevel` (`OnInit(LevelDifficulty)`) — **báo trước "level khó"** ngay trong màn chơi; `LevelManager::CheckShowAlertDifficult`.
- Ba thứ này hợp lại là mô típ *tạo cảm giác khó → bán booster/revive*. Chưa đủ bằng chứng gọi là manufactured fail. **[I], cần body để kết luận.**

### 2.9 Mật độ ad ước tính

**[U] — không tính được.** Mọi tham số đầu vào (`InterCapping`, `InterCooldown`, `LevelShowADSInter`, thời lượng 1 level) đều nằm trong string metadata/remote config không có trong export. Ước tính ở đây sẽ là bịa số. Xem mục 13 để biết cần thêm gì.

### 2.10 Ma trận tệp người chơi × kịch bản (User Segment × Scenario Matrix)

**Kết luận:** N=1 tệp thực sự. Tất cả ngưỡng quảng cáo/mua hàng là hằng số remote dùng chung,
không phân nhánh theo user attributes. Chỉ có 3 công tắc nhị phân không tạo nhánh decisioning ad thực:
RemoveAds (tắt ad toàn bộ), level gates (`LevelShowADSInter`), first-time skip (`GetSkipFirstInterCountOnReopen`).
Tín hiệu CampaignName/CampaignID/country chỉ chảy vào analytics, không dùng cho ad decisioning.

| Chiều | Có phân nhánh? | Bằng chứng | Nhãn |
|---|---|---|---|
| RemoveAds | ✅ Có (white-list) | `CheckRemoveAds` → skip tất cả ad. Code-path riêng nhất. | [V] |
| Level thấp → cấm ad inter | ⚠️ Một phần | `IsPassLevelToShowInterstitial(LevelID)` → cô lập các level sớm. Nhưng threshold là 1 con số `DefaultGlobalConfig::LevelShowADSInter`, không phân tầng user. | [V] structure / [U] giá trị |
| Phiên đầu skip N inter | ⚠️ Một phần | `GetSkipFirstInterCountOnReopen` → delay ad. Nhưng áp cho tất cả first-timer, không phân tầng. | [V] structure / [U] giá trị |
| Organic vs non-organic (campaign) | ❌ Chỉ analytics | `CampaignName`, `CampaignID` → ghi vào event, **không dùng trong `CheckCanShowAds` path**. Waterfall/cap giống hệt. | [I] + [V] |
| Country tier | ❌ Chỉ analytics | `UpdateCountryCode` → tín hiệu có, **không thấy trong ad decisioning logic**. Khác với lớp 2.2 `AdScheduleUnitID` (xoay unit theo lịch). | [I] + [V] |
| Người đã từng trả tiền (payer) | ❌ Không có | Chỉ `IsPackPurchased` per-pack. Không có cờ `is_payer` global dùng cho ad. | [V] |
| OS tier (Android version) | ❌ Không có | Không tìm thấy `Build.VERSION`. | [V] |

**4 câu kiểm tra:**

(a) Tín hiệu ngoài không ảnh hưởng ad decisioning? → **ĐÚ.** CampaignName + CountryCode chỉ là telemetry, không bao giờ xuất hiện trong điều kiện `CheckCanShowAds`, `IsReadyToShowInterstitial`, thresholds. → **Tệp N=1 không bị ngụy trang.**

(b) Có tệp zero-ad white-list? → **CÓ: RemoveAds = 1 tệp có zero ad.** Còn lại chỉ có 1 tệp (default). → **N_thực = 2 tệp: {RemoveAds, Ads}.**

(c) Xung đột giữa tệp? → **KHÔNG.** RemoveAds là `bool` đơn giản, không overlap (một người chơi là subscriber hoặc không, không thể vừa RemoveAds vừa đang mua ads). Không có xung đột. → **Rõ ràng.**

(d) Transition RemoveAds (mua hoặc huỷ) → ad config apply lại ngay? → **[I] Có cơ chế:**
   `AdsManager::SetRemoveAds(bool)` + `CheckRemoveAds(delegate)` → sẽ skip show ads ngay lần tiếp theo
   gọi `ShowAdsInter`. Nhưng chưa xác minh: *khi nào* `SetRemoveAds` được gọi khi người chơi mua pack.
   Nếu là **sau IAP flow, trước khi return về game** → OK. Nếu **delay hoặc gọi từ backend async** 
   → có race condition (người chơi thấy ad 1-2 lần sau khi mua trước khi `SetRemoveAds` chạy). → **[U] timing.**

**Kết luận:** AnTQ có thiết kế tệp đơn giản (N=1 + binary RemoveAds), hiếm khi cần segmentation
high-dimensional. Đây là mô típ game casual có "đơn giản hoá IAA" (chỉ one-size-fits-all ad).
Ngược lại với SDK chuẩn thế giới C.1 (decisioning per-user). Không có machine-learned segmentation.
→ **Có opportunity tăng ARPU bằng data-driven segmentation nếu thêm signals (LTV, engagement, cohort).**

### 2.10 Ma trận tệp người chơi × kịch bản

**Kết luận: N = 1.** Không có segmentation cho ad decisioning. Tất cả người chơi dùng **chung một bộ quy tắc ad**, chung một waterfall, chung một bộ cap. Cái trông giống "tệp" thực ra là **3 công tắc trạng thái nhị phân trên cùng một bộ quy tắc**, không phải bộ quy tắc riêng.

| # | "Tệp" | Định nghĩa (điều kiện code) | % người chơi | Tham số ad RIÊNG? | Nhãn |
|---|---|---|---|---|---|
| 1 | **Mặc định** | mọi người chơi | ~100% | — bộ quy tắc duy nhất | [V] |
| 1a | công tắc *RemoveAds* | `ShopData::IsPackPurchased` → `AdsManager::SetRemoveAds(true)` (`null.h:3180`) | [U] | **không** có tham số riêng; chỉ **tắt** nhánh ad qua `CheckCanShowAds` | [V] |
| 1b | công tắc *cổng level* | `IsPassLevelToShowInterstitial` (`null.h:3211`), `DefaultX2CoinLevel` (`null.h:2806`) | [U] | **không**; chỉ bật/tắt theo ngưỡng level, ngưỡng là hằng số chung | [V] |
| 1c | công tắc *phiên đầu / mở lại* | `HasShownFirstInterEver` (`null.h:13532`), `GetSkipFirstInterCountOnReopen` (`null.h:11636`), `ShowAdsFirstTime` (`null.h:3278`) | [U] | **không**; bỏ qua N inter đầu, N là hằng số chung | [V] |

**Bộ tín hiệu gán nhóm:**

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| Ai gán | **Client, hoàn toàn.** Không có endpoint nào trả về segment/variant/bucket (`GameBackendModule::Models::*` — 90 model, không có model nào tên Segment/Variant/Experiment/Cohort) | [V] |
| Khi nào | mỗi lần đánh giá tại chỗ (`CheckCanShowAds`, `CanShow()`, `IsMet()`), **không có gán một lần rồi lock** | [V] |
| Có lock không | **Không.** Không có trường nào lưu segment id trong `PlayerData` / `CoreData::UserData` | [V] |

#### (a) Ngoài bộ tín hiệu gán tệp, còn tín hiệu nào khác ảnh hưởng ad decisioning không?

**KHÔNG — và đây là điểm cần nói rõ.** Có 2 tín hiệu *tồn tại* nhưng **không chạm vào ad decisioning**:

| Tín hiệu | Có thu thập? | Có ảnh hưởng ad? | Bằng chứng |
|---|---|---|---|
| Campaign (organic vs paid) | ✅ `DefaultGlobalConfig::CampaignName` / `CampaignID` (`null.h:2840–2842`), `AppsFlyerManager::TrackGetCampname` (`null.h:3044`) | ❌ **không** — chảy vào AppsFlyer/analytics, không có lớp nào đọc nó để rẽ nhánh ad | [V] tín hiệu / [I] không dùng |
| Country | ✅ `PlayerService::UpdateCountryCode(...)` | ❌ **không** — gửi lên backend riêng, không có `AdsConfig`/`SDKSetup` nào nhận country | [I] |
| OS tier / thiết bị | ❌ không thu thập cho mục đích này | ❌ | [V] |
| Level / độ khó | ✅ | ✅ **có** — nhưng đây là công tắc 1b, không phải chiều tệp | [V] |

⇒ **N = 1 không phải nguỵ trang.** Nó là con số thật: không có tệp nào bị giấu sau country/campaign. Ngược lại, tín hiệu để phân tệp **đã có sẵn trong tay mà chưa dùng** — đây là đòn bẩy rẻ nhất (mục 12, việc ①).

#### (b) Có tệp nào zero-ad / all-premium không?

**Có đúng một: người mua NoAds** — và **đó là IAP thật, không phải holdout experiment**.

- Không có white-list nào khác cho ad. `SDK::PlayerHelperActive` (`FullPlayerId`, `TryActiveHelper`, `GetIsHelperActive`, `null.h:725–729`) là white-list theo **player id cụ thể**, nhưng [I] đây là công cụ QA/nội bộ, không phải tệp sản phẩm — nó không nằm trên đường `CheckCanShowAds`.
- **Không có holdout.** Không tìm thấy bất kỳ lớp nào giữ nhóm đối chứng (`grep Holdout|Control|Baseline` = 0).
- ⚠️ **"Zero-ad" không thật sự zero:** App Open Ad đi đường riêng, không qua `CheckCanShowAds` (mục 3.8). Người mua NoAds **có khả năng vẫn thấy ad mở app**. Chưa xác minh được, nhưng nếu đúng thì tệp duy nhất của hệ thống cũng bị rò.

#### (c) Có xung đột giữa tệp không (một người match nhiều tệp)?

**Có, thường xuyên — và cơ chế ưu tiên là ngầm, không khai báo.**

Một người chơi hoàn toàn có thể đồng thời: chưa qua `LevelShowADSInter` (1b) **và** đang trong N inter đầu sau khi mở lại app (1c) **và** đã mua NoAds (1a). Ba công tắc này không loại trừ nhau.

```text
Thứ tự ưu tiên THẬT = thứ tự các lệnh if trong CheckCanShowAds / IsReadyToShowInterstitial
                      (null.h:13542, 3210)
   → không có bảng ưu tiên, không có enum, không có comment
   → thứ tự nằm trong body → [U]
```

Vì tất cả đều là **cổng chặn (AND)** chứ không phải **bộ chọn (OR/switch)**, kết quả logic là an toàn: chỉ cần một cổng đóng là không chiếu ad. Nhưng hệ quả là **không thể biết cổng nào đã chặn** — không có mã lý do (reason code) khi từ chối chiếu. Đây là lý do trực tiếp khiến mục 5.3 (decision log) trống.

**So sánh với chỗ họ làm ĐÚNG:** ở phía LiveOps, xung đột được xử lý tường minh bằng điểm số (`EventPriority::GetPoint()` / `LoseEventPriority::GetPoint()`, mục 4.2). Cùng một studio, cùng một codebase — nhưng phía ad thì lại dùng chuỗi if ngầm. **Đây là bất đối xứng đáng học ngược:** pattern tốt của họ ở lớp event nên được bê sang lớp ad.

#### (d) Khi transition giữa tệp (vd mua NoAds), config có apply lại ngay không?

| Đường | Apply ngay? | Bằng chứng | Nhãn |
|---|---|---|---|
| Banner / collapsible banner | ✅ **ngay** | `SetRemoveAds(bool)` (`null.h:3180`) đứng liền kề `DestroyBanner` (3181) và `DestroyCollapsibleBanner` (3182) — sắp xếp VA liên tiếp cho thấy setter gọi thẳng destroy | [I] mạnh |
| Interstitial | ✅ ngay ở lần gọi kế tiếp | `CheckCanShowAds` đọc cờ tại thời điểm gọi, không cache | [I] |
| **App Open Ad** | ⚠️ **không rõ** | đường `coCheckingShowAppOpenAds` / `ShowResumeAds` / `ForceShowAppOpenAds` (`null.h:3267–3286`) không đi qua `CheckCanShowAds` | [U] |
| Khôi phục sau khi cài lại app | ⚠️ | `LoadRemoveAds` (`null.h:3160`) gọi ở `Awake`; entitlement lấy từ `ShopData` **local**. Nếu save mất → mất quyền, và **không có server để khôi phục** (mục 3.7). Có `Purchaser::RestorePurchases` + `FetchExistingPurchases` nhưng đó là store restore, phải người dùng chủ động | [V] |

**Race condition đã nhận diện:**

```text
Awake:  AdsManager::LoadRemoveAds()          ← đọc ShopData local
        ‖ song song ‖
        coWaitForFirebaseInitialization()    ← chờ remote config
        ‖
        ShowAdsFirstTime() / coCheckingShowAppOpenAds()

Nếu ShopData đọc từ đĩa chậm hơn nhánh app-open ⇒ người đã mua NoAds
vẫn ăn 1 ad mở app ở lần khởi động đó.  → [U], phải đọc body để xác nhận.
```

Đây chính là biến thể race của rủi ro #2 (mục 9, câu 4) — và là lý do khuyến nghị **hợp nhất một cổng duy nhất cho mọi format**, kể cả app-open.

---

---

## 3. KỊCH BẢN IAP / PAYWALL

### 3.1 Catalog

**Kết luận:** SKU được định danh bằng **enum `PackageName`** (không phải string tự do) → catalog cố định trong build, **không thêm SKU mới bằng remote config được**.

| SKU (theo tên modal) | Loại | Bằng chứng |
|---|---|---|
| NoAds | non-consumable | `ModalNoAdsContext::UIPresenter::OnBuyNoAdsSuccess(PackageName)` |
| StarterPack | [I] non-consumable (một lần) | `ModalStarterPackContext::UIPresenter::OnStarterPackSuccess(PackageName)` |
| PackWorldCup | [I] consumable, giới hạn thời gian | `ModalPackWorldCupContext::UIPresenter::OnBuyPackSuccess(PackageName)` |
| Shop packs (n gói) | hỗn hợp | `ModalShopContext::UIView::UIShopPacks : List<UIShopPack>`, `ModalShopContext::UIPresenter::OnBuyNoConsumablePack` |

- **Giá trị enum `PackageName`, số bậc giá, tỉ lệ giữa các bậc, bậc neo:** **[U]** — enum không sinh method.
- Giá hiển thị lấy từ store, không hardcode: `IAPPackage::LocalizedPriceString` / `LocalizedPrice : decimal` / `CurrencyCode` (`null.h`, khối `IAPPackage`). **[V] — đây là cách đúng.**

### 3.2 Cấu trúc tiền tệ

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| Currency | `CoreData::ResourceType` (enum) — gồm ít nhất: coin/gold, tim (heart/life), nhiều loại booster, card pack, card star | [V] |
| Hard currency riêng (gem) | **Không thấy** — chỉ có 1 dòng "gold/coin" + tài nguyên trực tiếp | [I] |
| Quản lý | `PlayerResourceManager::{Add,Sub,Set,Get,IsEnough}ResourceValue(ResourceType, string)` | [V] |
| **Currency decoupling** | ❌ không có (mua pack ⇒ nhận thẳng resource, không qua đồng trung gian) | [I] |
| Tỉ giá quy đổi | — | [U] |

**Ghi chú tốt:** `AddResourceValue(ResourceType, **string**)` và `SubResourceValue(ResourceType, **string**)` — tham số string thứ hai gần như chắc chắn là **source/sink tag**. Đây là nền tảng đúng cho báo cáo economy (xem mục 7).

### 3.3 Bundle & offer

| Cơ chế | Bằng chứng | Nhãn |
|---|---|---|
| Gating pack theo điều kiện | `PackHandler::ConditionGroups` → `PackConditionGroup::IsMet` → `IPackHandlerCondition` | [V] |
| Điều kiện duy nhất được cài đặt | `PackBuyCount { PackName: PackageName, RequiredBuyCount: int }::IsConditionMet` — *"chỉ hiện pack B sau khi đã mua pack A n lần"* → **chuỗi phễu pack (pack ladder)** | [V] |
| Offer có hạn giờ | `ModalPackWorldCup` có `TextTime` + `OnViewUpdate(R3::Unit)` (tick mỗi frame) | [V] |
| Offer theo event | `EventOfferManager::{Initialize, DelayShowOffer, ShowCurrentOfferAsync, ShowCurrentOfferComplete}`, `EventOfferButton`, `OfferDetails` | [V] |
| Nội dung "tiết kiệm X%", break-even | — | [U] |

### 3.4 Subscription

**N/A — không có subscription.** `SubscriptionInfo`, `SubscriptionPurchase`, `SubscriptionValidationResult` v.v. đều thuộc thư viện `UnityEngine::Purchasing` / `GoogleBilling`, **không có lớp game nào gọi tới**; không có modal/paywall subscription, không có trial, không có dunning/win-back/grace period. **[V]**

### 3.5 Paywall

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| Trigger NoAds | sau khi xem đủ interstitial: `NoAdsPopupManager::MarkInterstitialWatched` → `TryShowOnHome` (hiện ở HOME, không cắt ngang gameplay) | [V] |
| Cap NoAds popup | `CoreData::NoAdsPopupData`: `ShowDate`, `ShowCountToday`, `LastShowTime`, `CanShow()`, `RegisterShown()`, `EnsureToday()` → **cap theo ngày + theo lần + có khoảng cách thời gian** | [V] |
| Default đóng gói của cap | `DefaultGlobalConfig::NumberShowNoAds` (`null.h:2828`), `CountShowNoAdsMax` (`null.h:2830`) | [V] tên / [U] giá trị |
| **Nút đóng bị trễ/ẩn** | `ModalNoAdsContext::UIView` có **`CloseButton` VÀ `CloseButtonHidden`**; `ModalStarterPackContext::UIView` cũng vậy | [V] tồn tại |
| Số giây trễ | — | **[U]** |
| Ép xem trước khi dùng app | Không thấy paywall chặn onboarding | [I] không có |
| Paywall điều khiển từ xa (layout/copy/giá) | ❌ — modal là prefab trong build; chỉ bật/tắt được | [I] |

**`CloseButtonHidden` là điểm phải soi.** Hai cách đọc: (a) một nút đóng thứ hai ở vị trí khác, hiện sau X giây — **dark pattern nếu X lớn**; (b) nút đóng của biến thể layout khác. Không phân biệt được từ tên. Ghi vào mục 9 là *nghi vấn*, không kết luận.

### 3.6 Regional pricing

- Có gửi country code lên server: `PlayerService::UpdateCountryCode` **[V]**.
- Giá lấy từ store theo tier Google/Apple (`LocalizedPrice`) **[V]**.
- **Có map country → tier giá riêng trong app không:** **[U]** — không thấy lớp nào.

### 3.7 Payment rails

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| Kênh thanh toán | **Chỉ Store IAP** (Google Play + Apple qua Unity IAP). Không có web shop / D2C | [V] |
| Validate receipt | **Client-side**: `Purchaser::CreateCrossPlatformValidator` → `CanCrossPlatformValidate` → `ValidatePurchaseIfPossible` / `ValidatePurchase(IOrderInfo)` (Unity `CrossPlatformValidator`) | [V] |
| Validate server-side | **KHÔNG CÓ.** Backend riêng có 11 service (`Auth, Player, Clan, Team, Leaderboard, WeeklyContest, Game, UserPurge…`) và ~90 model — **không một model/endpoint nào liên quan purchase/receipt/entitlement** (`grep 'GameBackendModule.*(Purchase|Receipt|Entitle|Order)'` = 0 kết quả) | [V] |
| Entitlement lưu ở đâu | **Local**: `CoreData::ShopData::IsPackPurchased(string)` / `AddPurchasedPack(string)`; NoAds là `AdsManager::SetRemoveAds(bool)` | [V] |
| Grant thưởng | `IAPRewardDelivery::GrantByProductId(string)` — client tự phát | [V] |

**Rủi ro gian lận (nêu rõ theo yêu cầu doc 41):** entitlement hoàn toàn client-side. `CrossPlatformValidator` chống được receipt giả cơ bản nhưng **không chống được sửa save-file cục bộ** (`ShopData` là JSON local: có `ToJson`/`FromJson`/`Migrate`/`Version`). Ai cũng có thể bật `RemoveAds` và mọi pack bằng cách sửa file save → mất doanh thu IAA lẫn IAP, và không có cách phát hiện phía server.

**Điểm cộng bù lại:** phần đối soát giao dịch dở dang được làm rất kỹ — `StashPendingTransaction` / `PopPendingTransaction` / `TryBeginDeliverTransaction` / `MarkBacklogTransactionAsDelivered` / `ConfirmBacklogOrder` / `MarkBacklogReconciled` / `IsBacklogReconciled` / `RemovePendingOrders` / `RemoveDeferredOrders` / `FetchExistingPurchases` / `RestorePurchases`. Đây là mức chỉn chu hiếm thấy ở game casual. **[V]**

### 3.8 Sau khi mua — người đã trả tiền còn thấy ad không?

**[U] — không kết luận được, và đây là câu hỏi quan trọng nhất chưa trả lời được.**

Những gì biết chắc:
- Có một cờ duy nhất `RemoveAds` (`SetRemoveAds(bool)`), **không** có cờ riêng cho từng format.
- `CheckCanShowAds` nằm ở `AdsController` (lớp game), trong khi **App Open Ad đi đường khác**: `AdsManager::coCheckingShowAppOpenAds` / `ShowResumeAds` / `ForceShowAppOpenAds` (`null.h:3267–3273, 3286`) **không đi qua `AdsController`**.
- ⇒ **Nghi vấn cao**: người mua NoAds vẫn có thể thấy App Open Ad / resume ad. Đây đúng là anti-pattern "hối hận sau mua" kinh điển. Phải xác minh bằng body của `IsActiveAppOpenAds` và `ForceShowAppOpenAds`.
- Rewarded thì đương nhiên vẫn còn (đúng, vì người chơi tự chọn).

---

## 4. LIVE OPS & khả năng điều khiển từ xa

### 4.1 Bề mặt remote config

**Kết luận:** trưởng thành ở mức **"đổi được số, không đổi được luồng"**. Rất nhiều tham số kinh tế/ad đổi được từ xa; nhưng UI, catalog SKU, và thứ tự event đều nằm cứng trong build.

**14 nhóm key** (mỗi lớp có `get_KeyRemoteConfig` + `FromJson`/`ToJson`/`LoadData`/`ResetData` — pattern nhất quán, `null.h:11569–11708`):

| # | Nhóm key | Điều khiển cái gì | Bằng chứng (accessor) |
|---|---|---|---|
| 1 | `InterAdsCappingRemoteConfigData` | cap inter theo level, bỏ qua N inter khi mở lại | `GetInterCapping`, `LevelCappingConfig`, `GetSkipFirstInterCountOnReopen` |
| 2 | `NoAdsPopupRemoteConfigData` | bật/tắt popup bán NoAds | `IsEnabled` |
| 3 | `HelpWithAdsGoldRemoteConfigData` | bật/tắt popup ads↔gold | `IsEnabled` |
| 4 | `StartGoldRemoteConfigData` | gold khởi tạo cho user mới | `RemoteConfigManager::TryApplyStartGoldRemoteConfig` |
| 5 | `ReviveRemoteConfigData` | giá revive bằng coin & bằng ads | `GetCurrentReviveCoin`, `GetCurrentReviveAds`, lớp lồng `ReviveConfig` + `AdsReviveConfig` |
| 6 | `EndGameRewardRemoteConfigData` | thưởng cuối màn theo độ khó + x2 by ads | `GetRewardCoin(LevelDifficulty)`, `GetAdsRewardCoin(LevelDifficulty)`, `CanShowX2Reward` |
| 7 | `BoosterRemoteConfigData` | ads-per-booster + **giá booster (x3)** + bật giá remote | `GetCurrentBoosterAds`, `HasAdsForBooster`, `GetBoosterPriceX3`, **`IsRemotePriceActive`** |
| 8 | `ForceUseBoosterRemoteConfigData` | **ép dùng booster** | `IsEnabled` |
| 9 | `AntLabRemoteConfigData` | lịch event AntLab | `AntLabStartDate`, `AntLabEndDate`, `IsBetweenEvent(DateTime)` |
| 10 | `TestLevelRemoteConfigData` | whitelist level test | `IsEnabled`, `ContainsLevel` |
| 11 | `ShipRaceRemoteConfig` | event đua thuyền | `FromJson` |
| 12 | `SkyJumpRemoteConfig` | event SkyJump | `FromJson` |
| 13 | `StarBlazeRemoteConfig` | event StarBlaze | `FromJson` |
| 14 | `CastleClimbRemoteConfig` | event CastleClimb | `FromJson` |

Cộng thêm: cấu hình CardCollection (`CardCollectionManager::GetRemoteConfigDefault`, `GetRemoteConfigSeasonDefault`, `ReloadRemoteConfig`, `OnRemoteConfigReady`), và **`SDKSetup` + `AdmobAdSetup`** (mediation, sdk key, toàn bộ ad unit id — mục 2.2).

**Đổi được KHÔNG cần release:** tần suất ad, cap theo level, ad unit id + mediation, giá revive/booster (soft currency), thưởng cuối màn, gold khởi tạo, bật/tắt từng popup, lịch & tham số 5 event, mùa CardCollection.
**KHÔNG đổi được:** giá IAP thật (Store tier), danh mục SKU (enum `PackageName`), layout/copy paywall, thứ tự & luật ưu tiên event (prefab + component), thêm loại ad mới.

**Kênh thứ hai — cần soi kỹ:** `ABILibsCustomEventConfig::StartAutoHttpFetch` + `FetchAndApplyHttpAsync` (`null.h:526–529`) là một đường **fetch HTTP tự động, độc lập Firebase**, có `AggregateRetryPolicy` / `ExponentialBackOffRetryPolicy` / `RetryRequest` đi kèm. Tên method có `ApplyHttp` ⇒ nó **áp dụng** config chứ không chỉ tải. Endpoint, có TLS pinning không, có ký số không: **[U]**.

### 4.2 Lịch sự kiện theo kiến trúc 5 lớp

| Lớp | Có gì | Bằng chứng | Nhãn |
|---|---|---|---|
| **0 — nền (hằng ngày)** | Daily reward (`DailyRewardManager`: `CheckEndDay`, `ResetDailyReward`, `AddDayToTimeReset`, `IsCanClaimThisReward`); hệ tim/energy (`IsHeart`, `DefaultFillHeartTime`, `DefaultLife`, `ModalFillHeart`, `FillHeartTimeBar`); quest AntLab (`AntLabQuest*`); daily reset của HelpWithAdsGold (`EnsureDailyState`, `OnDailyReset`, `GetTodayPopupType`) | ✅ | [V] |
| **1 — tuần** | `WeeklyContest*` (service backend riêng: `WeeklyContestAddScore`, `WeeklyContestStatus`, `WeeklyContestPendingClaim`, `WeeklyContestWeekSummary`); Team/Clan (chat, gift, xin tim, xin thẻ, ranking); win-streak (`GameManager::IncreaseWinStreak`/`ResetWinStreak`, `PlayerData::WinStreak`) | ✅ | [V] |
| **2 — mùa** | **CardCollection** = battle-pass-thay-thế: `CardSeasonCfModel`, `StartNewSeason`, `ResetDataNextSeason`, `CardSetCfModel`, `StarChestCfModel`, `ModalCompleteCollection/CompleteSet`, `ModalCardStarExchange` | ✅ (1 cái duy nhất — đúng nguyên tắc không chồng 2 mùa) | [V] |
| **3 — đỉnh (event giới hạn)** | 5 event có lịch riêng: **AntLab** (start/end date), **ShipRace** (3 stage), **SkyJump** (3 stage), **StarBlaze**, **CastleClimb**, + **UfoStreak**, + `ModalPackWorldCup` (theo mùa giải bóng đá) | ✅ | [V] |
| **4 — offer gắn theo lớp** | `EventOfferManager` + `EventOfferButton` + `OfferDetails`; `ModalPackWorldCup` gắn event; `PackBuyCount` xâu chuỗi pack | ✅ | [V] |

**Ma trận xung đột tài nguyên — CÓ, và làm bằng điểm số:**

```text
EventPriority        { EventPriorityData[] }      → GetPoint() / SetActive() / EventObject
LoseEventPriority    { LoseEventPriorityData[] }  → GetPoint() / SetActive() / IsAnyEventCanWarning()

Gắn vào:  ModalLose, ModalLoseByLock, ModalRetryGame, ModalLeaveGame  (đều có property LoseEventPriority)
Riêng StarBlaze còn có bản chuyên dụng: StarBlazeEventPriorityData, StarBlazeLoseEventPriorityData
```

Đây là **pattern tốt nhất tìm được trong dự án**: thay vì mỗi event tự gọi `Show()` và giẫm lên nhau, tất cả đăng ký vào một bộ trọng tài chấm điểm, và có một bộ trọng tài **riêng cho khoảnh khắc thua** — đúng chỗ mật độ lời mời cao nhất. Số điểm cụ thể: **[U]**.

Mật độ/tháng, thời lượng mỗi event: **[U]** (nằm trong remote config).

### 4.3 Battle pass / season pass

**Không có battle pass trả phí.** CardCollection đóng vai trò mùa vụ nhưng: nguồn thẻ là card pack (nhận qua gameplay/ads/shop), **không thấy tier trả phí, không thấy premium track**. `ModalCardStarExchange` cho đổi sao lấy thưởng. Ước tính % doanh thu: **[U]**.

### 4.4 Targeting & segmentation trong config

- Có phân nhánh theo **level** (`LevelCappingConfig`, `IsPassLevelToShowInterstitial`) và **độ khó** (`GetRewardCoin(LevelDifficulty)`). **[V]**
- Có tín hiệu **campaign** (`CampaignName`/`CampaignID`) và **country** — nhưng **không thấy lớp nào rẽ nhánh config theo chúng**. **[I]: dùng cho attribution, chưa dùng cho targeting.**
- `SDK::PlayerHelperActive` (`FullPlayerId`, `TryActiveHelper`, `GetIsHelperActive`, `null.h:725–729`) — cơ chế bật tính năng theo **player id cụ thể**. **[I]** đây là whitelist QA/cheat, không phải segmentation sản phẩm.

### 4.5 Cơ chế thí nghiệm

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| A/B test client-side | **KHÔNG có** — không tìm thấy lớp nào chứa `ABTest`/`Variant`/`Experiment`/`Bucket`/`Cohort` | [V] |
| A/B qua Firebase (server gán nhóm) | Có thể — Firebase A/B Testing không cần class client. `BoosterRemoteConfigData::IsRemotePriceActive` + `GetBoosterPriceX3` có mùi biến thể giá | [I] |
| Holdout / incrementality | **KHÔNG có** | [V] |
| Kill switch | Có, ở mức thô: `IsEnabled` từng popup, `DefaultGlobalConfig::IsUseADS`, bật/tắt mediation | [V] |
| Rollback | Firebase RC rollback (ngoài app) | [I] |
| **Config có ký số / verify không** | **Không thấy** bất kỳ signature/verify nào trên `FromJson`. Firebase RC có TLS + tính toàn vẹn của Google; nhưng kênh `ABILibsCustomEventConfig` qua HTTP tự viết thì **[U]** | [I] |

### 4.6 Nhiều lớp default — **KIỂM TRA BẮT BUỘC**

Đếm được **3 lớp**:

```text
Lớp 1 — hardcode trong code:     mỗi *RemoteConfigData có ResetData()   (giá trị: [U])
Lớp 2 — đóng gói trong build:    DefaultGlobalConfig (ScriptableObject, null.h:2802–2844)
                                 + CardCollectionManager::GetRemoteConfigDefault / GetRemoteConfigSeasonDefault
                                 + StreamingAssetsConfigurationLoader::GetConfigAsync
Lớp 3 — remote:                  Firebase RC  ⊕  ABILibsCustomEventConfig (HTTP)
```

**Lớp fallback đáy có hiếu chiến hơn lớp default chính không?**
→ **[U] — không xác minh được, và đây là rủi ro số 1 của báo cáo này.**
`ResetData()` là hàm gán giá trị đáy; giá trị nằm trong body (không có) hoặc trong string metadata (không có). Cấu trúc *cho phép* fail-aggressive: nếu `ResetData()` của `InterAdsCappingRemoteConfigData` đặt cap = 0 giây, thì **mọi lần Firebase fetch fail = ad không giới hạn**. Bắt buộc phải kiểm tra khi có APK.

Điểm sáng: `AdsManager::coWaitForFirebaseInitialization` (`null.h:3169`) cho thấy game **chờ Firebase xong mới chiếu ad** → giảm cửa sổ chạy bằng giá trị đáy. **[V]**

---

## 5. Player intelligence & personalization

### 5.1 Tín hiệu thu thập được (đối chiếu 13 nhóm của doc 04)

| # | Nhóm feature | Có? | Bằng chứng |
|---|---|---|---|
| 1 | Acquisition | ✅ | `CampaignName`/`CampaignID`, `AppsFlyerManager::TrackGetCampname`, `TrackFirstOpen` |
| 2 | Lifecycle | ✅ | `PlayerData`, `GameManager::LastLevelPlayed`, `PlayerCurrentLevel`, daily reset khắp nơi |
| 3 | Engagement | ✅ | `LevelManager::GetPlayTimeToNow`, `IsTrackingLevelSession`, `ResetLevelSessionStats` |
| 4 | **Skill / difficulty response** | ✅ mạnh | `PlayerData::WinStreak`, `FirstTryCount`, `IsFirstTryLevel`; `CoreData::LevelLoseCountData` + `HelpWithAdsGoldData::{GetLoseCount, IncreaseLoseCount, ResetLoseCount}`; `LevelDifficulty` |
| 5 | Progression | ✅ | level, unlock booster/obstacle (`CheckNewUnlockedItem/Obstacle`) |
| 6 | **Economy** | ✅ mạnh | `LevelManager::AddLevelCoinSpend` / `AddLevelBoosterSpend`; `PlayerResourceManager::{Add,Sub}ResourceValue(type, **source-tag**)` |
| 7 | IAP behavior | ⚠️ một phần | `ShopData::IsPackPurchased(string)` — chỉ biết đã mua gì, **không có timestamp/số tiền/số lần trong client state** |
| 8 | **IAA behavior** | ✅ mạnh | `LevelManager::AddLevelAdsCount`; `AdsController::TrackLevelAdsView`; `NoAdsPopupData::ShowCountToday/LastShowTime`; `Level::ReviveByAdsCount`, `GetBoosterByAdsCount` |
| 9 | Offer intent | ⚠️ | `UIShopPack::OnPurchaseFailOffer` (bắt được fail), `EventOfferManager::ShowCurrentOfferComplete`; **không thấy log "đã xem offer nhưng không mua"** |
| 10 | Preference | ⚠️ | `SettingData`, ngôn ngữ (`OnLanguageChanged`), avatar/badge/frame profile |
| 11 | Temporal / sequence | ❌ | không thấy lưu chuỗi hành động |
| 12 | Context tại decision point | ✅ | `LoseEventPriority` chạy đúng lúc thua với đầy đủ state; `HelpWithAdsGoldManager::GetCurrentPopupType` |
| 13 | Latent score | ❌ | không có |

### 5.2 Mức độ cá nhân hóa

```text
rule tĩnh ──▶ segment thủ công ──▶ propensity model ──▶ bandit ──▶ RL
   ▲
   │
 ĐANG Ở ĐÂY (có nhích sang phải một chút)
```

**Chấm: rule tĩnh + tham số hoá remote.** Bằng chứng: mọi quyết định đều là if/else trên ngưỡng (`IsPassLevelToShowInterstitial`, `IsReadyToShowInterstitial`, `CanShow()`, `IsMet()`, `GetPoint()`), và các ngưỡng đó đọc từ remote config. **Không có model, không có scoring, không có xác suất.** Thứ gần "quyết định" nhất là `LoseEventPriority::GetPoint()` — nhưng điểm là **hằng số cấu hình**, không phải hàm của người chơi. **[V]**

### 5.3 Decision log

**KHÔNG CÓ. [V]**

Có `TrackAdImpression`, `TrackInterstitial_Displayed`, `TrackRewarded_ShowCount`… — tức là **log kết quả**, nhưng không có bất kỳ chỗ nào ghi lại: state lúc quyết định, các action ứng viên đã bị loại, propensity của action được chọn. `AdsController::ShowAdsInter(..., string placement)` có nhận nhãn placement — đó là tất cả.

**Hệ quả (nói thẳng theo doc 41):** không thể học off-policy từ dữ liệu của game này. Muốn gắn bandit/uplift sau này thì **phải thêm decision log trước**, không có đường tắt.

### 5.4 Cold start

- Có `StartGoldRemoteConfigData` + `TryApplyStartGoldRemoteConfig` → gold khởi tạo điều chỉnh từ xa. **[V]**
- Có `DefaultGlobalConfig::PlayGameAtStart`, `DefaultLevelPlaying`, `DefaultBackToMenuLevel`, `LevelShowADSInter`, `DefaultX2CoinLevel` → **các cổng theo level chính là cold-start policy**: user mới không thấy inter/x2 cho tới khi qua ngưỡng level. **[V] cấu trúc / [U] ngưỡng.**
- Tutorial: `TutorialManager`, `ActivityFirstTut`, `ModalOpenFirstCardPack`, `TutorialOpenCollectionCtrl`, `ActivityLoadingFirstGame`. **[V]**

---

## 6. Measurement & attribution

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| MMP | AppsFlyer (duy nhất) | [V] |
| **Ad revenue impression-level** | ✅ **có, đủ 3 tầng**: `AdmobMediationController::OnAdBannerPaid/OnAdRewardedAdPaid/OnAppOpenAppPaidEvent/MRECAdsOnOnAdPaid` → `HandleAdPaidEvent(string, AdValue, ResponseInfo)`; `MaxMediationController::OnAdRevenuePaidEvent(AdsType, string, AdInfo)`; hội tụ về `AdsManager::OnAdRevenuePaidEvent(ImpressionData)` → `AppsFlyerManager::TrackAppsflyerAdRevenue(ImpressionData)` + `SDK::Analytics::AnalyticsManager::TrackAdImpression(ImpressionData)` | [V] |
| Event taxonomy (tên method, không phải tên event) | `TrackFirstOpen`, `TrackLevelStart`, `TrackLevelAchieved`, `TrackCompleteLevel`, `TrackInterstitial_{ClickShowButton, LoadedSuccess, Displayed, ShowCount}`, `TrackRewarded_{ClickShowButton, LoadedSuccess, Displayed, ShowCount}`, `TrackAdImpression`, `TrackAppsFlyerPurchase(string, decimal)`, `TrackGetCampname` (`null.h:3027–3044`) | [V] |
| Chuỗi ad funnel | **đầy đủ**: click nút → load xong → hiển thị → đếm. Cho phép tính fill-rate & show-rate đúng cách | [V] |
| Firebase events | `FirebaseAnalyticsManager::LogEvent` (4 overload), `SetUserProperty`, **`LogPurchase`** | [V] |
| Analytics của meta | `CardCollectionAnalytics::{PackEarn, CardEarn, ChestEarn, CardSetComplete}` | [V] |
| Purchase event gửi server | ❌ chỉ gửi AppsFlyer + Firebase, **không gửi backend riêng** | [V] |
| SKAN / AAK conversion value schema | — | [U] (nằm ở plist/manifest) |
| Deep link / web-to-app | `AppsFlyerAndroid::enableFacebookDeferredApplinks` tồn tại trong SDK; game có gọi hay không: [U] | [U] |

**Nhận xét:** tầng đo lường ad là phần **làm tốt nhất** của dự án. Impression-level revenue có ở cả 2 mediation và chảy vào cả MMP lẫn analytics — đủ để tính ARPDAU theo cohort/campaign một cách chính xác.

---

## 7. Kinh tế trong game

**Kết luận:** kinh tế một-đồng-tiền (gold) + hệ tim + booster; gacha nằm ở CardCollection.

### Source / Sink

| Resource | Source | Sink |
|---|---|---|
| Gold/coin | `StartGold` (remote), `WinGameReward`, `EndGameReward` theo `LevelDifficulty`, x2 by ads, HelpWithAdsGold, daily reward, event reward, shop pack (IAP) | revive (`GetReviveCoin`, giá leo theo `CurrentReviveCount`), mua booster (`GetBoosterPriceX3`), `DefaultFullHeartCoin` (mua đầy tim), `ModalBuySpeedUp` |
| Tim/life | hồi theo thời gian (`DefaultFillHeartTime`), mua bằng coin, xem ads (`IsShowAdsFillHeart`), **xin từ team** (`TeamActiveLivesRequest`, `TeamLivesHelpResponse`, `TeamLivesCooldown`) | thua level |
| Booster | `CheckClaimBooster`, ads (`GetBoosterByAdsCount`), shop | dùng trong level (`AddLevelBoosterSpend`), **`ForceUseBooster`** |
| Card / card star | card pack (gameplay/ads/shop), `StarChest` | `ModalCardStarExchange`, hoàn thành set/collection |

- **Soft gate ở đâu:** hệ tim (`IsHeart` bật/tắt được từ xa) + `DefaultBackToMenuLevel` + các cổng level. **[V]**
- **Tốc độ lạm phát, đường cong độ khó:** **[U]**.
- **DDA:** không tìm thấy cơ chế điều chỉnh độ khó theo người chơi. Có `LevelDifficulty` nhưng là **thuộc tính tĩnh của level** (dùng để tính thưởng và hiện `AlertLevelDifficult`), không phải hàm của kỹ năng người chơi. **[I] không có DDA.**
- **Gacha / loot box:** ✅ **có**, ở card pack: `CardCollectionManager::RandomCardItem(CardPack)`, `ClaimCardPack`, `UpdateDictCardByStar`, `ChangeCardStar` + lớp **`RateDropCardByIndex`** (`FromJson`/`ToJson` với `JsonModel`).
  - Tên `RateDropCardByIndex` cho thấy **tỉ lệ rơi phụ thuộc vị trí/lượt** → đây là hình thái **pity/soft-pity** (tỉ lệ thay đổi theo index). **[I]**
  - Tỉ lệ công bố ở đâu, có hard pity không, kỳ vọng số lần quay & chi phí: **[U]** — con số nằm trong remote config JSON.
  - **Không có** cách mua trực tiếp card pack bằng tiền thật ⇒ mức rủi ro loot box thấp hơn gacha thương mại. Cần xác minh shop có bán card pack không (`UIShopPack` bán gì: [U]).

---

## 8. Privacy & compliance

| Hạng mục | Kết quả | Nhãn |
|---|---|---|
| CMP | **Google UMP** đầy đủ: `ConsentInformation::Update`, `ConsentForm::Load`, `LoadAndShowConsentFormIfRequired`, `ShowPrivacyOptionsForm`; gọi từ `AdmobMediationController::InitConsent` → `OnConsentInfoUpdated(FormError)` (`null.h:3047–3048`) | [V] |
| **Consent khi chạy MAX** | ⚠️ `InitConsent` **chỉ có ở `AdmobMediationController`**. `MaxMediationController` không có method consent nào. Nếu remote config đẩy mediation sang MAX-only, **[I] luồng consent có thể không chạy** | [I] |
| GDPR → AppsFlyer | API có sẵn: `AppsFlyerConsent::{ForGDPRUser, ForNonGDPRUser, isUserSubjectToGDPR, hasConsentForDataUsage, hasConsentForAdsPersonalization, hasConsentForAdStorage}`, `AppsFlyer::setConsentData` | [V] API tồn tại / [U] game có gọi không |
| Consent → Firebase | `FirebaseAnalytics::SetConsent(IDictionary<ConsentType, ConsentStatus>)` tồn tại | [V] API / [U] có gọi không |
| ATT (iOS) | `AppsFlyer::waitForATTUserAuthorizationWithTimeoutInterval` tồn tại; thời điểm prompt: [U] | [V] API / [U] timing |
| COPPA / age gate | **Không tìm thấy** | [V] không có |
| **Xoá dữ liệu người dùng (GDPR/CCPA)** | ✅ có: `GameBackendModule::Services::UserPurgeService`, `PurgeUserByUidRequest/Response`, `DeletePlayerResponse` | [V] |
| Quyền trong manifest | — | [U] (không có manifest trong export) |
| Dữ liệu gửi đi đâu | Firebase (Google), AppsFlyer, AdMob, AppLovin, backend riêng, **+ endpoint HTTP của `ABILibsCustomEventConfig` chưa xác định** | [V] / [U] endpoint |
| Huỷ subscription | N/A — không có subscription | [V] |
| FTC click-to-cancel | N/A | — |
| Loot box disclosure | **Không tìm thấy** UI công bố tỉ lệ card pack | [I] |
| Quảng cáo cho trẻ em | không có age gate ⇒ không có gating ad theo tuổi | [I] |

---

## 9. Audit đạo đức & Comfort (rubric 5 câu — doc 29)

| # | Câu hỏi | Trả lời | Bằng chứng |
|---|---|---|---|
| 1 | Người chơi có hiểu rõ mình đang mua gì và với xác suất nào không? | **Một phần KHÔNG.** IAP thì rõ (pack nội dung cố định, giá store). Card pack là gacha có `RateDropCardByIndex` mà **không tìm thấy UI công bố tỉ lệ**. Giảm nhẹ: card pack không bán trực tiếp bằng tiền thật ([I]) | `RateDropCardByIndex`, không có class disclosure |
| 2 | Thiết kế có khai thác riêng nhóm dễ tổn thương không? | **KHÔNG tìm thấy.** Không có whale detection, không có segment theo mức chi, không có model. `PlayerHelperActive` là whitelist theo id nhưng [I] là công cụ QA | mục 5.2, 5.3 |
| 3 | Áp lực có được tạo ra một cách nhân tạo không? | **CÓ, mức trung bình.** (a) `ModalPackWorldCup` có đếm ngược — cần xác minh là hạn thật hay giả; (b) **ShipRace đua với BOT mô phỏng** — `CreateBots`, `SimulateBot`, `GetBotFinishSeconds`, `GetBotPoint`, `WinnerName`, `PlayerPlace`, `ShipRaceStageConfig::BotCount` → thi đấu trình bày như với người thật; (c) `AlertLevelDifficult` cảnh báo "Hard/SuperHard" ngay trước/trong màn chơi; (d) `ForceUseBoosterRemoteConfigData` bật/tắt từ xa việc **ép dùng booster** | `null.h` ShipRaceManager block; `ForceUseBooster*`; `AlertLevelDifficult` |
| 4 | Người đã trả tiền có được đối xử tốt hơn không? | **CHƯA XÁC MINH ĐƯỢC — nghi vấn KHÔNG.** Một cờ `RemoveAds` duy nhất, nhưng App Open Ad đi đường riêng không qua `AdsController::CheckCanShowAds`. Xem 3.8 | `null.h:3160,3180,3267–3286,13542` |
| 5 | Nếu công khai toàn bộ cơ chế, người chơi có thấy bị lừa không? | **Đa số KHÔNG.** Trừ 2 điểm: bot trong ShipRace, và (nếu đúng) người mua NoAds vẫn thấy app-open ad | trên |

### Kết luận vùng

> **VÙNG XÁM — nghiêng về phía chấp nhận được**, với **2 điểm phải xác minh trước khi chốt**:
> ① người mua NoAds có còn thấy App Open Ad không (3.8) — nếu CÓ thì tụt xuống *vượt lằn ranh* ở câu 4;
> ② `ResetData()` fallback có hiếu chiến hơn default không (4.6).
>
> Lý do nghiêng tích cực: có `LoseEventPriority` để **giảm** chồng lấn lời mời; có cap NoAds popup theo ngày; có cổng level chặn ad cho user mới; chờ Firebase xong mới chiếu ad; không có model khai thác whale; không có subscription bẫy; đo lường minh bạch.

### Anti-pattern kinh điển — kết quả rà soát

| Anti-pattern | Kết quả | Bằng chứng |
|---|---|---|
| Near-miss (suýt trúng giả) | **Không tìm thấy** | — |
| Manufactured fail (cố tình cho thua để bán) | **Không tìm thấy trực tiếp**; nghi vấn quanh `ForceUseBooster` + `AlertLevelDifficult` | [I] |
| Fallback hiếu chiến hơn default | **[U] — chưa xác minh, rủi ro số 1** | `ResetData()` × 14 lớp |
| Share state giữa 2 loại action không liên quan | **CÓ, mức nhẹ**: interstitial cache dùng chung cho mọi placement (placement chỉ là string nhãn) | mục 2.3 |
| Rewarded fail → âm thầm chiếu interstitial | **NGHI VẤN** | `MaxMediationController::FallbackToInterstitial` (`null.h:3366`) |
| Subscriber/payer vẫn thấy ad | **NGHI VẤN CAO** (App Open Ad) | mục 3.8 |
| Nút đóng paywall trễ/ẩn | **NGHI VẤN** | `CloseButtonHidden` ở `ModalNoAds` và `ModalStarterPack` |
| Ad ngay giây đầu phiên đầu | **KHÔNG** — có `IsPassLevelToShowInterstitial` + chờ Firebase + `GetSkipFirstInterCountOnReopen` | [V] |
| Đối thủ giả trong bảng xếp hạng | **CÓ** (ShipRace bots) — nhưng WeeklyContest/Leaderboard/Team thì dùng backend thật | [V] |

---

## 10. Bảng đối chiếu 6 lớp tính năng chuẩn thế giới (doc 23 mục C)

| Lớp | Đánh giá | Bằng chứng | Khoảng cách so với chuẩn |
|---|---|---|---|
| **C.1 Ad monetization** (execution + decisioning per-user) | **Một phần** — execution 9/10, decisioning 1/10 | 6 format, 2 mediation, waterfall theo index, lịch xoay ad unit, cap nhiều tầng, impression-level revenue | Không có quyết định theo từng người: mọi ngưỡng là hằng số remote, chung cho toàn bộ user (trừ level). Thiếu decision log ⇒ không thể học |
| **C.2 Payment routing đa kênh** | **Không** | Chỉ Store IAP; entitlement client-side; backend không có endpoint purchase | Thiếu toàn bộ: web shop/D2C, entitlement server-side, receipt validation server, chống sửa save |
| **C.3 Paywall & subscription** | **Một phần yếu** | Có modal NoAds/StarterPack/PackWorldCup + cap hiển thị theo ngày; giá lấy từ store | Không có subscription, không có trial/dunning/win-back, paywall là prefab (không remote layout, không A/B copy/giá) |
| **C.4 Offerwall / loyalty / engagement** | **Có, mạnh** | Daily reward, quest, win-streak, 6 event mùa/đỉnh, CardCollection season, Team/Clan (chat, gift, xin tim, xin thẻ), WeeklyContest server-side, `EventPriority` arbitration | Không có battle pass trả phí; không có CRM/push segmentation (chỉ có FCM thô); không có offerwall bên thứ ba |
| **C.5 Measurement & attribution 2026** | **Một phần** | AppsFlyer + impression-level ad revenue 2 mediation + funnel ad đầy đủ + Firebase LogPurchase | **Không có decision log**, không có holdout/incrementality; SKAN/AAK schema [U] |
| **C.6 Decision brain** (bandit/uplift, guardrail, cross-modal) | **Không** | — | Thiếu hoàn toàn. Thứ duy nhất gần: `LoseEventPriority::GetPoint()` (điểm tĩnh) và cross-modal thủ công `UIShopPack::OnAdsPurchase/OnMoneyPurchase/OnIAPPurchase` + `HelpWithAdsGold` (ads HOẶC gold) — đúng ý tưởng, sai cách thực thi (rule cứng thay vì học) |

---

## 11. Chắt lọc DNA

**Tiêu chí lọc (theo doc 41):** chỉ đưa vào nếu (a) chạy production thật ở quy mô thật, và (b) verify được trực tiếp từ symbol table. Vì không có body, mục 11 chỉ nhận **pattern kiến trúc**, không nhận con số nào.

### 11.1 PATTERN TỐT ĐÁNG HỌC

| # | Pattern | Bằng chứng production | Vì sao tốt | Áp vào SDK của ta ở đâu | Copy được số? |
|---|---|---|---|---|---|
| 1 | **Trọng tài điểm số cho lời mời, có bản riêng cho khoảnh khắc thua** | `EventPriority`/`LoseEventPriority` + `EventPriorityData::GetPoint()`, gắn vào 4 modal thua | Gom mọi "ai được hiện" về một chỗ ⇒ không giẫm chân, và có thể thay điểm tĩnh bằng score động về sau mà không sửa call-site | Điểm chèn tự nhiên cho decision brain (doc 05/24): giữ interface `GetPoint()`, thay implementation bằng propensity | ❌ chỉ lấy làm kiến trúc |
| 2 | **AdScheduleUnitID — xoay ad unit theo lịch từ xa** | `SDK::AdScheduleUnitID::{ChangeID, Refresh, IsActive, CurrentPlatformID: List<string>}` | Đổi giá sàn/unit không cần release; tách "format" khỏi "id thật" | Tầng ad-unit resolution (doc 25) | ❌ |
| 3 | **Waterfall theo index + timeout huỷ được từng bậc** | `BuildInterstitialOrder` → `LoadInterstitialAtIndex(i)` → `StartInterstitialLoadTimeout` / `InterstitialLoadTimeoutAsync(CancellationToken)` / `Cancel…` (×2 cho inter & rewarded, đối xứng) | Không để một bậc chậm nuốt cả waterfall; huỷ sạch bằng CancellationToken thay vì cờ boolean | Ad loader | ❌ dùng làm prior để A/B |
| 4 | **Frequency cap là object có state ngày, không phải biến rời** | `CoreData::NoAdsPopupData::{ShowDate, ShowCountToday, LastShowTime, EnsureToday(), CanShow(), RegisterShown()}` + `Version`/`TargetVersion`/`Migrate()` | Cap được **serialize, versioned, migrate được**; `EnsureToday()` xử lý đổi ngày tại một chỗ | Mọi cap trong SDK ta nên theo khuôn này | ❌ |
| 5 | **Capping riêng sau rewarded** | `AdsController::InterCappingAfterReward` tách khỏi `InterCapping` | Người vừa tự nguyện xem rewarded thì không nên bị inter đè ngay ⇒ bảo vệ thiện chí | Guardrail của policy | ❌ prior để A/B |
| 6 | **Ad không chiếu trước khi remote config sẵn sàng** | `AdsManager::coWaitForFirebaseInitialization` (`null.h:3169`) | Thu hẹp cửa sổ chạy bằng giá trị fallback | Bắt buộc trong SDK ta | ✅ (đây là quy tắc, không phải số) |
| 7 | **Impression-level ad revenue hội tụ 1 điểm rồi fan-out** | mọi `OnAd*Paid` → `HandleAdPaidEvent` → `AdsManager::OnAdRevenuePaidEvent(ImpressionData)` → AppsFlyer + Analytics | Một điểm nối duy nhất; thêm sink mới không sửa 6 chỗ | Tầng measurement (doc 21) | ✅ kiến trúc |
| 8 | **Funnel ad 4 nấc, không chỉ đếm impression** | `TrackX_ClickShowButton` → `_LoadedSuccess` → `_Displayed` → `_ShowCount` | Tách được "người muốn xem" khỏi "ad có sẵn" khỏi "ad hiện ra" ⇒ đo đúng chỗ rò | Event taxonomy | ✅ |
| 9 | **Đối soát giao dịch dở dang đầy đủ** | `StashPendingTransaction`/`PopPendingTransaction`/`TryBeginDeliverTransaction`/`MarkBacklogTransactionAsDelivered`/`ConfirmBacklogOrder`/`MarkBacklogReconciled`/`IsBacklogReconciled` | Không mất hàng khi app chết giữa lúc mua — hiếm thấy ở casual | Billing layer | ✅ |
| 10 | **Source tag trên mọi thay đổi tài nguyên** | `PlayerResourceManager::AddResourceValue(ResourceType, string)` / `SubResourceValue(ResourceType, string)` | Có source/sink report ngay từ ngày 1, không phải bolt-on | Economy telemetry (doc 30) | ✅ |
| 11 | **Pack ladder có điều kiện khai báo được** | `PackHandler::ConditionGroups` → `PackConditionGroup::IsMet` → `IPackHandlerCondition` (impl: `PackBuyCount`) | Interface mở: thêm điều kiện mới (level, ngày, số ad đã xem, LTV) không sửa `PackHandler` | Offer eligibility engine | ✅ kiến trúc |
| 12 | **Cross-modal IAA↔IAP ngay trong một popup** | `HelpWithAdsGoldManager::ClaimAdsReward(...)` vs `TryBuyGoldReward(...)`; `UIShopPack::OnAdsPurchase`/`OnMoneyPurchase`/`OnIAPPurchase` | Đặt hai con đường cạnh nhau ở đúng khoảnh khắc thiếu tài nguyên | Đây chính là bài toán cross-modal của doc 05 | ❌ |
| 13 | **Đường IAA→IAP có cap: xem đủ inter mới mời mua NoAds** | `NoAdsPopupManager::MarkInterstitialWatched` → `TryShowOnHome` (hiện ở HOME, không cắt gameplay) | Chuyển hoá người mệt vì ad thành người trả tiền, mà không phá phiên chơi | Paywall trigger | ❌ prior |
| 14 | **Có endpoint xoá dữ liệu người dùng** | `UserPurgeService`, `PurgeUserByUidRequest` | GDPR/CCPA làm được thật, không chỉ ghi trong policy | Backend ta | ✅ |

### 11.2 ANTI-PATTERN PHẢI TRÁNH

| # | Anti-pattern | Tác hại | Ta đã tránh chưa | Sửa ở đâu | Test để không tái diễn |
|---|---|---|---|---|---|
| 1 | **Entitlement 100% client-side**, không có validate server, save là JSON local (`ShopData::AddPurchasedPack`) | Sửa file save là có mọi pack + NoAds; mất cả IAA lẫn IAP; không phát hiện được | ❓ cần rà | Thêm bảng entitlement server + xác thực receipt phía server; client chỉ cache | Test: sửa save local → server vẫn từ chối quyền; test replay receipt |
| 2 | **Không có decision log** | Không thể học off-policy, không thể chạy uplift, không thể giải thích tại sao chiếu ad | ❓ | Bổ sung ngay ở tầng `ShowAdsInter`/`GetPoint`: ghi {state, action chọn, action ứng viên, propensity, outcome} | Test: mọi lần show ad phải sinh đúng 1 bản ghi decision có propensity ≠ null |
| 3 | **Cache ad theo format, placement chỉ là nhãn string** | Không thể đặt giá sàn/chính sách khác nhau theo placement; một ad "rẻ" bị tiêu ở placement giá trị cao | ❓ | Khoá cache = (format, placement-class), không chỉ format | Test: 2 placement khác class không được dùng chung 1 instance |
| 4 | **Fallback `ResetData()` không kiểm chứng được** — có thể hiếu chiến hơn default | Firebase fetch fail = ad không giới hạn | ❓ | Nguyên tắc **fail-safe**: giá trị đáy phải BẢO THỦ HƠN default | Test bắt buộc: assert `fallback.interCap >= default.interCap` cho MỌI key; CI fail nếu vi phạm |
| 5 | **Rewarded fail → interstitial** (`FallbackToInterstitial`) | Nếu người chơi bấm "xem để nhận thưởng" mà nhận về inter (không thưởng) ⇒ phản bội kỳ vọng, giết retention | ❓ | Nếu rewarded fail: **nói thật + không chiếu ad khác**, hoặc chiếu và VẪN trả thưởng | Test: rewarded fail ⇒ hoặc (no ad shown) hoặc (ad shown AND reward granted). Không có nhánh thứ ba |
| 6 | **App Open Ad không đi qua cổng chung `CheckCanShowAds`** | Người mua NoAds vẫn ăn ad ⇒ hối hận sau mua | ❓ | **Một cổng duy nhất** cho mọi format, kể cả app-open/resume | Test: bật `RemoveAds` ⇒ mọi `Show*` của MỌI format đều trả false. Duyệt bằng reflection qua tất cả format |
| 7 | **Nút đóng ẩn/trễ trên paywall** (`CloseButtonHidden`) | Ép nhìn; vi phạm hướng dẫn store; hại retention | ❓ | Nếu cần trễ, giới hạn cứng và hiện đếm ngược | Test: mọi modal bán hàng phải có nút đóng khả dụng ≤ N giây, N là hằng số duy nhất trong code |
| 8 | **SDK khai trong config nhưng không có trong bin** (IronSource) | Remote config trỏ nhầm ⇒ mất toàn bộ ad, không ai biết | ❓ | Validate config lúc nạp: mediation phải có controller thật | Test: với mọi giá trị của enum mediation, `GetAdsMediationController` trả về non-null hoặc log fatal + fallback |
| 9 | **Consent chỉ khởi tạo trong nhánh AdMob** | Chạy MAX-only ⇒ có thể không xin consent ⇒ rủi ro GDPR | ❓ | Consent phải nằm ở tầng `AdsManager`, trên cả 2 mediation | Test: init với mỗi mediation ⇒ consent flow đều được gọi đúng 1 lần |
| 10 | **Gacha không công bố tỉ lệ** (`RateDropCardByIndex`) | Rủi ro pháp lý (EU/BR/JP/KR) + niềm tin | ❓ | Màn hình công bố tỉ lệ + pity, sinh tự động từ chính config | Test: config drop-rate thay đổi ⇒ UI công bố thay đổi theo (không hardcode) |
| 11 | **Bot trình bày như người thật** (ShipRace) | Nếu bị phát hiện: mất niềm tin; nếu gắn với offer: nguy hiểm hơn | ❓ | Hoặc ghi rõ là AI/luyện tập, hoặc dùng ghost data thật của người chơi khác | Test: không có offer/pack nào được trigger bởi kết quả thua bot |
| 12 | **Kênh remote config thứ hai qua HTTP tự viết, không rõ xác thực** (`ABILibsCustomEventConfig`) | Nếu không có TLS pinning/ký số: MITM đổi được giá, tần suất ad, thưởng | ❓ | Ký số payload config + verify client; hoặc bỏ, dùng 1 kênh | Test: config có chữ ký sai ⇒ bị từ chối, giữ nguyên giá trị cũ |
| 13 | **`ForceUseBooster` bật/tắt từ xa** | Ép tiêu tài nguyên của người chơi = tạo nhu cầu nhân tạo | ❓ | Chỉ dùng trong tutorial, không bao giờ ngoài tutorial | Test: `ForceUseBooster` chỉ gọi được khi `TutorialManager.IsInTutorial` |

---

## 12. Ước tính hiệu quả kinh tế

**Không ước tính. [U] toàn mục.**

Theo nguyên tắc 2 của doc 41 (không bịa số): mọi đầu vào để tính ARPDAU/ad density/tỉ trọng IAA:IAP — `InterCapping` (giây), `InterCooldown`, `LevelShowADSInter`, thời lượng level, giá SKU, mật độ event — **đều nằm trong string metadata và remote config, không có trong export này**. Đưa ra con số ở đây sẽ là bịa.

Ba nhận định **định tính** (không phải số), gắn bằng chứng:

1. **Tỉ trọng nghiêng mạnh về IAA.** 6 định dạng ad × 2 mediation × waterfall × lịch xoay unit, so với **3 dòng SKU + shop pack không có subscription**. Toàn bộ hạ tầng nặng nằm ở phía ad. `[I]`
2. **Chỗ rò lớn nhất là entitlement client-side** (mục 3.7): mất doanh thu ở cả hai đầu và không đo được. `[I]`
3. **Đòn bẩy cao nhất nếu đây là dự án của ta**, xếp theo tỉ lệ (giá trị / công sức):
   - ① **Đưa quyết định chiếu ad ra khỏi hằng số**: đã có sẵn `LoseEventPriority::GetPoint()` làm điểm chèn và đã có đủ feature (win-streak, lose-count, level ads count, coin spend). Chỉ thiếu decision log.
   - ② **Validate purchase phía server** + entitlement server-side: backend đã có sẵn auth + player service, chỉ thêm 1 bảng + 1 endpoint.
   - ③ **Hợp nhất cổng ad** (mọi format qua `CheckCanShowAds`) và **kiểm tra fallback fail-safe**: rẻ nhất, và bịt đúng 2 rủi ro nặng nhất của mục 9.

---

## 13. Việc tiếp theo

| # | Việc | Ở đâu | Phụ thuộc | Nghiệm thu |
|---|---|---|---|---|
| 1 | **Lấy APK/XAPK gốc** và giải nén | — | — | có `global-metadata.dat`, `AndroidManifest.xml`, `res/xml/`, `assets/` |
| 2 | Chạy **Il2CppDumper** (metadata + libil2cpp.so) | APK | #1 | có `dump.cs` + `stringliteral.json` ⇒ **mọi `[U]` về key/số/id trong báo cáo này chuyển thành `[V]`** |
| 3 | Trích **toàn bộ 14 key remote config + giá trị default** | `dump.cs`, `res/xml/remote_config_defaults.xml` | #2 | bảng key → default, đối chiếu với mục 4.1 |
| 4 | **Kiểm tra fail-safe** (rủi ro #1) | body của `ResetData()` × 14 lớp | #2 | assert: mọi giá trị fallback ≤ mức hiếu chiến của default. Viết thành unit test trong SDK ta |
| 5 | **Trả lời 3.8**: người mua NoAds có còn thấy App Open Ad? | body `IsActiveAppOpenAds`, `ForceShowAppOpenAds`, `coCheckingShowAppOpenAds` | #2 | có/không + đường code cụ thể |
| 6 | **Giải nghĩa `FallbackToInterstitial`** | body `MaxMediationController::FallbackToInterstitial` (VA `0x3ac…`) | #2 | xác định có trả thưởng hay không |
| 7 | **Đo delay của `CloseButtonHidden`** | body `ModalNoAdsContext::UIPresenter::Initialize`, `ModalStarterPack…` | #2 | số giây thật |
| 8 | **Tìm endpoint + cơ chế xác thực của `ABILibsCustomEventConfig`** | body `FetchAndApplyHttpAsync` + string literal | #2 | URL, có ký số/pinning không |
| 9 | Trích **enum `PackageName`, `AdsType`, `ResourceType`, `HelpWithAdsGoldPopupType`** | `dump.cs` | #2 | danh mục SKU thật + bản đồ tài nguyên |
| 10 | Trích **tỉ lệ gacha** `RateDropCardByIndex` | remote config JSON | #3 | bảng tỉ lệ + có/không hard pity + kỳ vọng số lần mở |
| 11 | Viết **doc DNA riêng** từ mục 11 theo khuôn `27_SOLARA_TEARDOWN_DISTILLED_DNA.md` | `DOC/43_ANTQ_DISTILLED_DNA.md` | báo cáo này | pattern đã code hoá / anti-pattern đã ngăn / cập nhật bảng DNA doc 26 |
| 12 | Chuyển 13 anti-pattern ở 11.2 thành **13 test thật** trong SDK ta | repo SDK | #11 | CI đỏ khi vi phạm |

---

## Không xác minh được (BẮT BUỘC — liệt kê thẳng)

| Không biết | Cần thêm gì để biết |
|---|---|
| Package name, version, tên app trên store | AndroidManifest.xml trong APK |
| **Mọi tên key remote config** (chỉ biết 14 *nhóm*, không biết chuỗi key) | `global-metadata.dat` → Il2CppDumper |
| **Mọi giá trị số**: InterCapping, InterCooldown, LevelShowADSInter, NumberShowNoAds, CountShowNoAdsMax, DefaultFillHeartTime, DefaultLife, ReviveCoins, WinGameReward, DefaultX2CoinLevel, TimeDelayLose, GetSkipFirstInterCountOnReopen | như trên + `res/xml/remote_config_defaults.xml` |
| **Mọi ad unit id**, sdk key MAX, AdMob app id | metadata + AndroidManifest meta-data |
| Số bậc waterfall AdMob (độ dài `List<string>`), thứ tự, timeout mỗi bậc | body + remote config |
| **Giá trị `ResetData()` — fallback có hiếu chiến hơn default không** (rủi ro số 1) | body của 14 lớp `*RemoteConfigData` |
| **Người mua NoAds có còn thấy App Open Ad không** (rủi ro số 2) | body `IsActiveAppOpenAds` / `ForceShowAppOpenAds` |
| Ngữ nghĩa `FallbackToInterstitial` (có trả thưởng không) | body |
| Số giây trễ của `CloseButtonHidden` | body |
| Giá trị enum `PackageName` ⇒ **danh mục SKU thật, số bậc giá, bậc neo** | metadata |
| Giá trị enum `ResourceType`, `AdsType`, `HelpWithAdsGoldPopupType`, `AntLabQuestType` | metadata |
| **Tỉ lệ rơi thẻ** trong `RateDropCardByIndex`, có hard pity không | remote config JSON |
| Endpoint + xác thực của kênh config HTTP `ABILibsCustomEventConfig` | body + string literal + capture traffic |
| Có map country → tier giá riêng không; country tier ảnh hưởng gì ngoài giá | remote config |
| Game có thực sự gọi `AppsFlyerConsent.ForGDPRUser` / `FirebaseAnalytics.SetConsent` không | body |
| Thời điểm prompt ATT; SKAN/AAK conversion value schema | Info.plist (bản iOS) |
| Có A/B test qua Firebase không, ai gán nhóm, có holdout không | Firebase console (không nằm trong client) |
| Fields của các data class chỉ có ctor: `AdsConfig`, `InterLoseConfig`, `CustomRemoteConfig`, `ShopPackageDataConfig`, `OfferDetails`, `RewardAdsPlacementConfig`, `ShipRaceStageConfig` | metadata (IL2CPP không sinh method cho public field) |
| Mật độ ad/phiên, ARPDAU, tỉ trọng IAA:IAP | tất cả những mục trên |

---

*Nguồn dữ liệu: `/Users/dungdm/ghidra_export/null.h` (320.081 symbol) + `GlobalFunctions.cpp`. Bảng ký hiệu đã giải mã đầy đủ tại `<scratchpad>/all_symbols.txt` (định dạng: `dòng-trong-null.h ⟶ Lớp::Method::ThamSố ⟶ VA`).*
