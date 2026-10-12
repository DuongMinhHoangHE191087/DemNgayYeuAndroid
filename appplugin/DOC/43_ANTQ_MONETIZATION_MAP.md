# 43 — Bản đồ ăn tiền: game "AnTQ"

> Nguồn duy nhất: `/Users/dungdm/ghidra_export` (Ghidra export `libil2cpp.so`).
> **Quy tắc của tài liệu này: mỗi dòng phải trỏ được về một symbol có thật trong `null.h`.**
> Không có mục nào viết ra từ "thường thì game casual hay làm thế".
> Tính năng KHÔNG có symbol ⇒ ghi vào mục cuối "Không tồn tại trong code", không đoán.

Ký hiệu: `[V]` đọc thẳng từ symbol · `[I]` nối 2+ symbol, có ghi rõ nối cái gì · `[U]` không có trong input.

---

## 1. ĐỊNH DẠNG QUẢNG CÁO: **6**

Đếm bằng số `AdsConfig` mà `AdsManager` phơi ra — mỗi format một config riêng:

| # | Format | Config accessor | Setup | Bằng chứng |
|---|---|---|---|---|
| 1 | Banner | `get_BannerAdsConfig` | `SetupBannerAds(AdsMediationType)` | `null.h:3185, 3177` |
| 2 | Collapsible Banner | `get_CollapsibleBannerAdsConfig` | `SetupCollapsibleBannerAds` | `null.h:3186, 3178` |
| 3 | Interstitial | `get_InterstitialAdsConfig` | `SetupInterstitial` | `null.h:3187, 3175` |
| 4 | Rewarded Video | `get_RewardVideoAdsConfig` | `SetupRewardVideo` | `null.h:3188, 3176` |
| 5 | MREC | `get_MRecAdsConfig` | `SetupMRecAds` | `null.h:3189, 3256` |
| 6 | App Open Ad | `get_AppOpenAdsConfig` | `SetupAppOpenAds` | `null.h:3190, 3179` |

**Format KHÔNG dùng** (có class trong SDK Google nhưng `AdsManager` không có method nào gọi): Native / Native Overlay, Rewarded Interstitial. `[V]`

**Hai mediation chạy song song, chọn được theo từng format:**
`AdsManager::GetSelectedMediation(AdsType)` (`null.h:3174`) + `SDKSetup::GetAdsMediationType(AdsType)` (`null.h:592`) ⇒ **mỗi format có thể chạy mediation khác nhau**. Controller thật: `AdmobMediationController` (102 method) và `MaxMediationController` (82 method). `[V]`

---

## 2. MỖI FORMAT SHOW Ở ĐÂU

### 2.1 App Open Ad — mở app + quay lại app

| Điểm show | Symbol |
|---|---|
| Kiểm tra khi vào app | `AdsManager::coCheckingShowAppOpenAds` `null.h:3267` |
| Show có delay | `DelayShowAppOpenAds` / `coDelayShowAppOpenAds` `null.h:3270–3271` |
| **Show khi quay lại app** (resume) | `ShowResumeAds` `null.h:3286`, gắn `OnApplicationPause` `null.h:3291` |
| Ép show | `ForceShowAppOpenAds` `null.h:3273` |
| Lần đầu tiên trong đời máy | `ShowAdsFirstTime` `null.h:3278` |
| Màn loading che lúc show | `ShowLoadingPanel` / `CloseLoadingPanel` `null.h:3287–3288` |
| Cờ bật/tắt | `IsActiveAppOpenAds` `null.h:3276`; remote: `UpdateAOARemoteConfig`, `UpdateRemoteConfigResumeAds` `null.h:3164–3165` |

### 2.2 Banner & Collapsible Banner — thường trực

| Điểm show | Symbol |
|---|---|
| Bật/tắt theo màn qua event | `AdsController::EventShowBanner` / `EventHideBanner` (System.Action) `null.h:13518–13521` |
| Show/hide | `AdsController::ShowBanner` (2 overload) / `HideBanner` `null.h:13543–13545` |
| Cờ toàn cục | `Core::GameManager::get_ShowBanner` |
| Đẩy UI khi có banner | `NoAdsSwitchPosition { Root, BannerActivePosition, BannerInactivePosition }` — **UI có 2 vị trí, tự đổi theo banner** |
| Collapsible: tự refresh | `AdsManager::RefreshCollapsibleBanner` `null.h:3233`, `UpdateCollapsibleBanner(float)` `null.h:3168` |
| Collapsible: hết hạn hiển thị | `IsCollapsibleBannerShowingTimeOut` `null.h:3242` |
| Collapsible: theo dõi bung/thu | `IsCollapsibleBannerExpended`, `OnCollapsibleBannerExpanded/Collapsed` `null.h:3229, 3238–3239` |

### 2.3 MREC

Có đủ vòng đời (`ShowMRecAds`/`HideMRecAds`/`IsMRecShowing`/`IsMRecLoaded` + 5 callback, `null.h:3256–3265`).
**Màn nào đặt MREC: `[U]`** — không có symbol UI nào tham chiếu MREC.

### 2.4 Interstitial

| Điểm show | Symbol |
|---|---|
| Cổng vào duy nhất phía game | `AdsController::ShowAdsInter(Action, …, **string placement**)` `null.h:13549` — có tham số **placement** |
| Cờ cho phép, theo ngữ cảnh | `GameManager::CanShowAdsInter`, `GameManager::CanShowInterLose` |
| Config riêng cho inter-khi-thua | `DefaultGlobalConfig::get_InterLoseConfig` `null.h:2838` |
| **Màn hình đệm chuyên để chiếu inter** | `ActivityAdsBreakContext::UIPresenter::{DidEnter, DelayShowAds, OnShowAdsSuccess, OnShowAdsFailed}` |
| Overlay đếm ngược "Ads Break" | `ActivityNotificationOverlayContext::UIPresenter::{ShowAdsBreak, OnAdsBreakUpdate, HideAdsBreak, OnHideAdsBreakComplete}` + `UIView::AdsBreakGroup` |
| Cổng theo level | `AdsManager::IsPassLevelToShowInterstitial` `null.h:3211`; default đóng gói `DefaultGlobalConfig::LevelShowADSInter` `null.h:2826` |
| Đếm để bán NoAds | `NoAdsPopupManager::MarkInterstitialWatched` |
| Đếm để báo cáo | `AdsController::TrackLevelAdsView`, `LevelManager::AddLevelAdsCount` |

**Giá trị `placement` cụ thể: `[U]`** (string literal không có trong export).

### 2.5 Rewarded Video — **7 điểm mời xem, tất cả đều xác minh được**

| # | Điểm | Người chơi đổi được gì | Symbol |
|---|---|---|---|
| 1 | **Thua vì kẹt khoá** | chơi tiếp | `Core::GamePlay::Level::ReviveByLockStuck` |
| 2 | **Thua vì hết slot** | chơi tiếp | `Level::ReviveByOutOfSlot` |
| — | (đếm số lần revive bằng ads) | | `Level::get_ReviveByAdsCount` / `set_ReviveByAdsCount`; `LevelManager::get_CurrentReviveCount` |
| 3 | **Cuối màn thắng** | x2 coin | `EndGameRewardRemoteConfigData::CanShowX2Reward`, `GetAdsRewardCoin(LevelDifficulty)` `null.h:11609–11610`; cổng level `DefaultGlobalConfig::DefaultX2CoinLevel` `null.h:2806` |
| 4 | **Hết tim** | tim | `DefaultGlobalConfig::IsShowAdsFillHeart` `null.h:2834`; `UIManager::ShowModalFillHeart`; `Core::UI::Modals::ModalFillHeart` |
| 5 | **Thiếu booster trong màn** | booster | `Level::GetBoosterByAdsCount` / `IncrementBoosterByAdsCount`; `BoosterRemoteConfigData::{HasAdsForBooster, GetCurrentBoosterAds, GetBoosterAdsConfigEntry}` `null.h:11585–11587` |
| 6 | **Popup "Help with Ads → Gold"** (4 ô) | gold / booster | `HelpWithAdsGoldManager::ClaimAdsReward(HelpWithAdsGoldOption, ResourceType)` `null.h:8796` |
| 7 | **Card pack** | thẻ sưu tập | `CardCollectionManager::SetPlacementCardCollection(string)` / `GetPlacementCardCollection` |
| + | **Shop pack trả bằng ads** | nội dung pack | `UIShopPack::OnAdsPurchase` / `OnAdsPurchaseSuccess` |

Cổng chặn riêng của rewarded: `AdsManager::IsReadyToShowRewardVideo` `null.h:3254`, `IsReadyToShowRewardInterrupt` `null.h:3247`, `ResetRewardInterruptCount` `null.h:3255`.

---

## 3. TOÀN BỘ ĐIỂM ĂN TIỀN

### 3.1 Ăn tiền thật (IAP) — 4 bề mặt

| # | Bề mặt | Show ở đâu | Symbol |
|---|---|---|---|
| 1 | **NoAds** | nút cố định trên **Home** (`ModalHomeContext::UIView::get_ButtonNoAds`, presenter `OnTouchButtonNoAds`) **+** popup tự bung ở Home sau khi xem đủ interstitial (`NoAdsPopupManager::TryShowOnHome`) | `ModalNoAdsContext::UIPresenter::OnBuyNoAdsSuccess(PackageName)`; `UIManager::ShowModalNoAdsCompleted` |
| 2 | **StarterPack** | Home (`ModalHomeContext::UIPresenter::OnTouchButtonStarterPack`) | `ModalStarterPackContext::UIPresenter::OnStarterPackSuccess(PackageName)` |
| 3 | **PackWorldCup** (pack theo event, **có đồng hồ đếm ngược**) | modal riêng | `ModalPackWorldCupContext::UIPresenter::OnBuyPackSuccess(PackageName)`; `UIView::{EventName, TextTime, ShopPackArray, ButtonClose1, ButtonClose2}` |
| 4 | **Shop** (nhiều pack trong 1 màn) | `ModalShop` | `ModalShopContext::UIView::UIShopPacks : List<UIShopPack>`; presenter `OnBuyNoConsumablePack`, `ReInitContent` |

**Một `UIShopPack` bán được bằng 4 đường** — đây là điểm cross-sell trung tâm:

```
UIShopPack::OnPurchase
   ├── OnIAPPurchase      → tiền thật
   ├── OnMoneyPurchase    → gold trong game
   ├── OnAdsPurchase      → xem quảng cáo   → OnAdsPurchaseSuccess
   └── OnTestPurchase     → build test
        ↓
   PurchaseSuccess / PurchaseSuccessAsync / OnPurchaseFailOffer
```
`[V]` — `null.h`, khối `UIShopPack`.

**Gating pack (điều kiện mới cho hiện):**
`PackHandler::ConditionGroups` → `PackConditionGroup::IsMet` → interface `IPackHandlerCondition`.
Cài đặt **duy nhất** có trong build: `PackBuyCount { PackName: PackageName, RequiredBuyCount: int }::IsConditionMet`
⇒ **"chỉ hiện pack B sau khi đã mua pack A đủ n lần"** — chuỗi phễu pack. `[V]`
`UIButtonNoAds` cũng dùng chính cơ chế này (`UIButtonNoAds::ConditionGroups`, `CheckConditions`). `[V]`

**Danh sách SKU đầy đủ + giá: `[U]`** — SKU là enum `PackageName`, giá trị enum không có trong export.

### 3.2 Ăn tiền qua quảng cáo — xem mục 2 (6 format, 7 điểm rewarded)

### 3.3 Ăn gold trong game (sink) — 6 điểm

| # | Sink | Symbol |
|---|---|---|
| 1 | **Revive bằng coin** (giá leo theo số lần) | `ReviveRemoteConfigData::GetCurrentReviveCoin`; `LevelManager::GetReviveCoin`; `Level::ReviveByCoinCount`; `DefaultGlobalConfig::ReviveCoins`, `ReviveSlot` |
| 2 | **Mua booster** | `BoosterRemoteConfigData::GetBoosterPriceX3`, `IsRemotePriceActive`; `ItemGlobalConfig::GetBoosterPriceX3`; `BoosterSelector::BoosterPriceTxt` |
| 3 | **Mua đầy tim** | `DefaultGlobalConfig::DefaultFullHeartCoin` `null.h:2814` |
| 4 | **Mua tăng tốc trong màn** | `ScreenInGameContext::UIPresenter::OnClickBtnBuySpeedUp`; `ModalBuySpeedUp` |
| 5 | **Mua gold-reward trong popup HelpWithAdsGold** | `HelpWithAdsGoldManager::TryBuyGoldReward(HelpWithAdsGoldOption, ResourceType)` `null.h:8797` |
| 6 | **Đổi sao thẻ** | `AnTQ::CardCollection::ModalCardStarExchange` |

Đo lường sink: `LevelManager::AddLevelCoinSpend` / `AddLevelBoosterSpend` + `get_CurrentLevelCoinSpend` / `get_CurrentLevelBoosterSpend`. `[V]`

### 3.4 Cơ chế tạo nhu cầu (không trực tiếp thu tiền, nhưng đứng ngay trước điểm thu)

| Cơ chế | Nó làm gì | Symbol |
|---|---|---|
| **Hệ tim (energy gate)** | hết tim ⇒ chờ / mua / xem ads / xin bạn | `DefaultGlobalConfig::{IsHeart, DefaultLife, DefaultFillHeartTime, DefaultFullHeartCoin, IsShowAdsFillHeart}` `null.h:2810–2834`; `GameManager::HasLifeOrInfinite`, `get_MaxLife`; `FillHeartTimeBar` |
| **Xin tim từ team** | vòng xã hội thay cho mua | `GameBackendModule::Models::{TeamActiveLivesRequest, TeamLivesStatusResponse, TeamLivesRequestResponse, TeamLivesHelpResponse, TeamLivesCooldown, TeamLivesHelperReward}` |
| **Đếm số lần thua để bung popup** | thua nhiều ⇒ mời ads/gold | `HelpWithAdsGoldManager::RecordLoseLevel`; `CoreData::HelpWithAdsGoldData::{GetLoseCount, IncreaseLoseCount, ResetLoseCount, LevelLoseCount}`; `CoreData::LevelLoseCountData` |
| **Cảnh báo level khó** | báo trước "Hard/SuperHard" | `AlertLevelDifficult::{OnInit(LevelDifficulty), HardLevel, SuperHardLevel, HardAnim, SuperHardAnim}`; `LevelManager::CheckShowAlertDifficult`; `ScreenInGameContext::UIPresenter::CheckShowAlertDifficult` |
| **Gợi ý dùng booster theo chu kỳ** | nhắc liên tục trong màn | `BoosterSelector::{StartSuggestionCycle, SetSuggestion, OnSuggestionUpdate}` |
| **Nút "+" thêm booster** | dẫn sang mua/ads | `BoosterSelector::get_AddMoreBoosterObj`; `ShowModalBooster(ResourceType)` |
| **Ép dùng booster** (bật/tắt từ xa) | tiêu hết booster tồn | `Core::GameManager::ForceUseBooster(ResourceType)`, `ForceUseBoosterCompleted`; `BoosterSelector::AddBoosterToForceUseAmount`; `ForceUseBoosterRemoteConfigData::get_IsEnabled` `null.h:11614` |
| **Booster khoá theo level** | mở dần → tạo mục tiêu | `BoosterSelector::{BoosterUnlockLvTxt, UnlockedObj, UnlockWaitClaimObj, OnUnlockClaim}`; `ItemGlobalConfig::{GetNewBoosterUnlock, IsNeedShowBoosterUnlock, ShowBoosterUnlock}` |
| **Gold khởi tạo điều khiển từ xa** | nắn hành vi tiêu ngay từ đầu | `StartGoldRemoteConfigData`; `RemoteConfigManager::TryApplyStartGoldRemoteConfig` |

---

## 4. GÓI / OFFER SHOW NHƯ THẾ NÀO

### 4.1 NoAds — luồng đầy đủ, xác minh được từng bước

```
Người chơi xem interstitial
   → NoAdsPopupManager::MarkInterstitialWatched
   → CoreData::NoAdsPopupData::RegisterShown        (ghi state)
        state gồm: ShowDate, ShowCountToday, LastShowTime, Version/TargetVersion
   → NoAdsPopupData::EnsureToday()                  (đổi ngày thì reset)
   → NoAdsPopupData::CanShow()                      (kiểm tra cap)
   → NoAdsPopupManager::TryShowOnHome               (CHỈ bung ở Home)
   → ModalNoAds
        UIView: MainView, ShopPack, CloseButton, **CloseButtonHidden**
   → OnBuyNoAdsSuccess(PackageName) → UIManager::ShowModalNoAdsCompleted
   → AdsManager::SetRemoveAds(true) → DestroyBanner / DestroyCollapsibleBanner
```
Cap đóng gói trong build: `DefaultGlobalConfig::NumberShowNoAds` `null.h:2828`, `CountShowNoAdsMax` `null.h:2830`.
Bật/tắt từ xa: `NoAdsPopupRemoteConfigData::get_IsEnabled` `null.h:11644`. `[V]`

⚠️ `ModalNoAdsContext::UIView` có **hai** nút đóng: `get_CloseButton` **và** `get_CloseButtonHidden`. Cùng cấu trúc ở `ModalStarterPackContext::UIView`. **Ý nghĩa và số giây: `[U]`** — cần body. `[V]` việc tồn tại 2 nút.

### 4.2 Popup "Help with Ads → Gold" — luồng ăn tiền kép

```
Thua level → HelpWithAdsGoldManager::RecordLoseLevel
             HelpWithAdsGoldData::IncreaseLoseCount
   ├── TryShowAutoPopup          (tự bung)
   └── TryShowHomeIconPopup      (qua icon ở Home)
        icon: ButtonHelpWithAdsGold::CheckShowHelpWithAdsGoldIcon
              HelpWithAdsGoldManager::{IsHomeIconActive, RefreshHomeIcon, HomeIconActiveChanged}

   → GetTodayPopupType / GetCurrentPopupType  (HelpWithAdsGoldPopupType)
   → ShowPopup / ShowPopupAndWaitComplete
   → ModalHelpWithAdsGold: 4 ô thưởng
        Reward1..4 GameObject + Reward1..4 Button + Reward1..4 ClaimCompleted
        UIModel giữ: PopupType, Reward1, Reward2  (Core::UI::Modals::HelpWithAdsGoldRewardData)
   → mỗi ô: ClaimAdsReward(option, ResourceType)   ← xem quảng cáo
            HOẶC TryBuyGoldReward(option, ResourceType)  ← trả bằng gold
   → IsOptionClaimed(option) ; GetTotalBoosterAmount
   → reset theo ngày: EnsureDailyState / OnDailyReset / GetTodayString
```
Bật/tắt từ xa: `HelpWithAdsGoldRemoteConfigData::get_IsEnabled` `null.h:11626`. `[V]`

### 4.3 Offer theo event

```
EventOfferManager::Initialize
   → DelayShowOffer                 (trì hoãn rồi mới bung)
   → ShowCurrentOfferAsync
   → ShowCurrentOfferComplete → IsCurrentEventOfferShowComplete
Nút vào: EventOfferButton      Dữ liệu: OfferDetails
Kích hoạt lúc thua: Core::GameManager::ShowEventOnFailed
```
`[V]` — `null.h:4743–4754`.

### 4.4 PackWorldCup — pack có hạn giờ

`ModalPackWorldCupContext::UIView`: `EventName` (string), `TextTime` (đồng hồ), `ShopPackArray` (**nhiều pack trong 1 modal**), `ButtonClose1`, `ButtonClose2`, `OnViewUpdate(R3::Unit)` (tick liên tục). `[V]`

### 4.5 Thứ tự ưu tiên khi nhiều lời mời cùng lúc

```
EventPriority       { EventPriorityData[] }      → GetPoint() / SetActive() / EventObject
LoseEventPriority   { LoseEventPriorityData[] }  → GetPoint() / SetActive() / IsAnyEventCanWarning()
```
`LoseEventPriority` được gắn vào **4 modal của khoảnh khắc thua**: `ModalLose`, `ModalLoseByLock`, `ModalRetryGame`, `ModalLeaveGame` (mỗi cái có property `get_LoseEventPriority`).
StarBlaze có bản riêng: `StarBlazeEventPriorityData`, `StarBlazeLoseEventPriorityData`. `[V]`

---

## 5. LIVE OPS: 8 EVENT — MỞ KHOÁ RA SAO, PHỤC VỤ MỤC ĐÍCH GÌ

### 5.1 Cổng mở khoá

**Mọi event đều có một property `IsEventUnlock`** — đây là cổng vào duy nhất:

| Event | Symbol cổng |
|---|---|
| AntLab | `AntLabManager::get_IsEventUnlock` |
| ShipRace | `ShipRaceManager::get_IsEventUnlock` |
| SkyJump | `SkyJumpManager::get_IsEventUnlock` |
| StarBlaze | `StarBlazeManager::get_IsEventUnlock` |
| UfoStreak | `UfoManager::get_IsEventUnlock` |
| CardCollection | `CardCollectionManager::IsActive` / `IsActiveForReward` |

⚠️ **`IsEventUnlock` mở ở LEVEL BAO NHIÊU: `[U]`.** Đây là property đọc-only, ngưỡng nằm trong body hoặc trong `*RemoteConfig`/`*GlobalConfig` — cả hai đều không có trong export này.
Điều **xác minh được**: các event mở khoá theo **cấu hình từ xa**, không hardcode — vì mỗi event có `*RemoteConfig` riêng (`AntLabRemoteConfigData`, `ShipRaceRemoteConfig`, `SkyJumpRemoteConfig`, `StarBlazeRemoteConfig`, `CastleClimbRemoteConfig`) và `*GlobalConfig` đóng gói (`ShipRaceGlobalConfig`, `SkyJumpGlobalConfig`, `StarBlazeGlobalConfig`, `UfoSreakGlobalConfig`, `WeeklyContestGlobalConfig`, `AntLabQuestGlobalConfig`). `[V]`

Riêng cổng theo level **xác minh được cho 3 thứ khác** (không phải event):
- interstitial: `IsPassLevelToShowInterstitial` ← `DefaultGlobalConfig::LevelShowADSInter` `null.h:2826`
- x2 coin cuối màn: `DefaultGlobalConfig::DefaultX2CoinLevel` `null.h:2806`
- quay về menu: `DefaultGlobalConfig::DefaultBackToMenuLevel` `null.h:2804`
- xin đánh giá: `DefaultGlobalConfig::DefaultlevelShowRate` `null.h:2836`
- booster: `BoosterSelector::BoosterUnlockLvTxt` (UI hiện level mở khoá)
(giá trị số của cả 4: `[U]`)

### 5.2 Từng event làm gì

| Event | Cơ chế (đọc từ method) | Phục vụ mục đích gì (suy ra từ chính method, đánh dấu rõ) |
|---|---|---|
| **AntLab** | quest nhiều loại (`Notify(AntLabQuestType)`, `NotifyHighest`, `NotifyWinLevel(LevelDifficulty)`, `NotifyFirstTry`, `NotifyTreeTopMission`), stage (`CurrentStage`, `TryCompleteStage`, `LastStageId`), thưởng (`SyncRewardData`, `GetRewardData`), có `AntLabStartDate`/`AntLabEndDate`/`IsBetweenEvent` | `[I]` **ép tần suất chơi + ép chơi level khó**: quest tính điểm theo `LevelDifficulty` và `FirstTry` ⇒ thưởng cho người chơi nhiều và chơi giỏi. Nối từ: `NotifyWinLevel(LevelDifficulty)` + `NotifyFirstTry` + `AntLabQuestTarget` |
| **ShipRace** | đua theo stage (`CurrentStage`, `CurrentTarget`, `PlayerPoint`, `PlayerPlace`), **đối thủ là bot** (`CreateBots(ShipRaceStageConfig)`, `SimulateBot`, `GetBotPoint`, `GetBotFinishSeconds`, `CacheBotPoints`, `HasBotPointChanged`, `ShipRaceStageConfig::get_BotCount`), thắng/thua (`IsPlayerWin`, `WinnerName`, `NeedShowWin`, `NeedShowLose`), 3 modal join theo stage | `[V]` **thi đua với bot**. `[I]` mục đích: tạo áp lực chơi tiếp trong khung thời gian (`RunEndTimeUtc`, `RunTimeRemaining`, `OpenNewRun`) |
| **SkyJump** | 3 stage (`ModalSkyJumpStage1/2/3Info`), trạng thái người chơi (`CurPlayerState: PlayerJumpState`), **cooldown** (`CoolDownEndTimeUtc`), thất bại stage (`HandleStageFail`), thứ hạng (`SkyJumpPlace`, `SkyJumpAvatar`) | `[I]` **nhịp chơi theo cửa sổ thời gian**: có `StageEndTimeUtc` + `CoolDownEndTimeUtc` ⇒ chơi theo đợt, nghỉ, quay lại |
| **StarBlaze** | tích điểm theo **chuỗi thắng** (`AddProgressAndWinStreak`, `CurrentStreak`, `LastStreak`, `IncreaseWinStreak`, `ResetWinStreak`), stage (`CheckPassStage`, `GetCurrentPassedStep`, `HasReachedFinalStage`), **giai đoạn nhân đôi điểm** (`LastStartDoublePointTime`), nhận thưởng (`TryClaimReward`, `TryGetClaimGameResourceGroup`) | `[V]` cơ chế win-streak. `[I]` mục đích: **thưởng cho phiên chơi dài liên tục** (streak vỡ khi thua ⇒ tạo động lực dùng revive/booster để giữ streak) |
| **UfoStreak** | thắng/thua level đẩy tiến độ (`WinLevel`, `LoseLevel`), hiện UI theo level (`LastShowUILevel`), **xử lý thua ở nấc cuối** (`HandleFailOnLastProgress`), nhận thưởng (`TryClaimReward`) | `[I]` **áp lực ở nấc cuối**: có method riêng cho tình huống thua khi sắp về đích |
| **CastleClimb** | leo theo level (`ModalCastleClimbContext::UIModel::get_Level`, `ModalState`), có đồng hồ hết event (`TxtTimeEndEvent`), thưởng (`ActivityShowRewardCastleClimb`, `ActivityCastleClimbInfo`) | `[I]` progression phụ theo mùa |
| **CardCollection** (meta mùa) | mùa (`CardSeasonCfModel`, `StartNewSeason`, `ResetDataNextSeason`), set/collection (`CardSetCfModel`, `CheckCompleteCardSetOrCollection`), **gacha** (`RandomCardItem(CardPack)`, `RateDropCardByIndex`), rương sao (`StarChestCfModel`, `ModalOpenChestStar`), đổi sao (`ModalCardStarExchange`), reset theo ngày (`IEResetDataNewDay`), tutorial riêng (`CheckPlayTutorialOpenCollection`) | `[V]` gacha sưu tập theo mùa. `[I]` mục đích: **giữ chân dài hạn + tạo điểm dùng rewarded** (`SetPlacementCardCollection`) |
| **WeeklyContest** | **chạy trên server thật**: `WeeklyContestService` + models `AddScore`, `Status`, `PendingClaim`, `WeekSummary`, `GroupSummary`, `LeaderboardEntry`, `ClaimResponse`, `DevEndWeek` | `[V]` giải tuần server-side, có nhóm (`GroupSummary`) |

**Vòng xã hội (Team/Clan)** — không phải event nhưng là hạ tầng giữ chân:
`TeamService` + models: chat (`SendTeamChatRequest`, `TeamChatMessage`, `TeamChatEventData`), **xin/tặng thẻ** (`AskCardRequest`, `GiveCardRequest`, `TeamActiveCardRequest`, `TeamCardCooldown`), **xin tim** (mục 3.4), quà (`SendGiftRequest`, `ClaimGiftResponse`, `TeamGiftData`), quản trị (join/leave/kick/promote/transfer leadership), xếp hạng (`TeamRankingResponse`), tìm & gợi ý team (`TeamSearchResponse`, `TeamSuggestionsResponse`). `[V]`

**Lớp nền hằng ngày:** `DailyRewardManager` (`CheckEndDay`, `ResetDailyReward`, `AddDayToTimeReset`, `IsCanClaimThisReward`, `ClaimReward`, `IsClaimed`, `IsCurrentReward`) + `ModalDailyReward` + `SlotDaily` + `UIDailyRewardNotification`; nút vào ở Home (`ModalHomeContext::UIView::get_BtnDailyReward`). `[V]`

**Xin đánh giá:** `AppReviewManager` (`RequestFlowClick`, `LaunchFlowClick`, `AllInOneFlowClick`, `ResetDisplay(string)`) + `ModalRate` + `UIManager::ShowModalRating` + `GameManager::CanShowRate` + `DefaultGlobalConfig::DefaultlevelShowRate`. `[V]`

---

## 6. HỌ SEGMENT USER NHƯ THẾ NÀO

### 6.1 Cho việc CHIẾU QUẢNG CÁO: **không segment** — N = 1

Không tồn tại trong code: bất kỳ class nào có `Segment` / `Variant` / `Experiment` / `Cohort` / `Bucket` / `Holdout` / `ABTest` trong tên (đã grep toàn bộ 320.081 symbol). Backend cũng không có model nào như vậy trong ~90 model của `GameBackendModule::Models`. `[V]`

Cái thay thế: **4 công tắc trạng thái**, cùng một bộ quy tắc:

| Công tắc | Symbol | Ảnh hưởng |
|---|---|---|
| Đã mua NoAds | `AdsManager::LoadRemoveAds` / `SetRemoveAds(bool)` `null.h:3160, 3180`; `CheckRemoveAds` (delegate); `CoreData::ShopData::IsPackPurchased(string)` | tắt nhánh ad |
| Đã qua level ngưỡng | `AdsManager::IsPassLevelToShowInterstitial` `null.h:3211` | mở interstitial |
| Đã từng xem inter lần đầu chưa | `AdsController::HasShownFirstInterEver` `null.h:13532`; `AdsManager::ShowAdsFirstTime` `null.h:3278` | nhánh riêng cho lần đầu |
| Vừa mở lại app | `InterAdsCappingRemoteConfigData::GetSkipFirstInterCountOnReopen` `null.h:11636` | bỏ qua N inter đầu |

Cộng thêm cap thay đổi **theo level** (không phải theo người): `InterAdsCappingRemoteConfigData::GetInterCapping` + lớp lồng `LevelCappingConfig` `null.h:11633, 11635`. `[V]`

### 6.2 Cho việc BÁO CÁO DOANH THU / UA: **CÓ segment theo QUỐC GIA**

Đây là phần tôi tìm thấy trong `ABILibsSDK` — namespace riêng của studio:

```
ABILibsSDK::ABILibsCustomEventConfig                      null.h:514–525
   ├── TryGetDeviceRegionCountryCode()      ← lấy quốc gia của máy
   ├── NormalizeCountryCode(string)         ← chuẩn hoá mã quốc gia
   ├── FetchAndApplyRemoteConfigAsync(TimeSpan?)   ← nạp từ Firebase RC
   ├── ApplyFromActivatedRemoteConfig()
   ├── FetchAndApplyHttpAsync(string, int)  ← nạp từ HTTP riêng
   ├── AutoFetchHttpConfigOnLoad() / StartAutoHttpFetch()
   ├── ApplyFromJsonString(string) / ExportToJson() / ApplyFromJsonInClipboard()
   └── (dữ liệu) FloatArrayWrapper · IntArrayWrapper · TROASPurchaseEventArrayWrapper
```
`[V]` — có hàm lấy + chuẩn hoá country code **ngay trong class config**.
`[I]` — nối `TryGetDeviceRegionCountryCode` + `NormalizeCountryCode` + `FloatArrayWrapper`: **ngưỡng doanh thu được cấu hình theo từng quốc gia**. Không đọc được giá trị (`[U]`).

Ngoài ra country còn được gửi lên backend: `PlayerService::UpdateCountryCode(UpdateCountryCodeRequest)`. `[V]`

### 6.3 Tín hiệu campaign (organic vs paid)

`DefaultGlobalConfig::get_CampaignName` / `get_CampaignID` `null.h:2840–2842` + `SDK::AppsFlyerManager::TrackGetCampname` `null.h:3044`. `[V]` tồn tại.
**Có class nào đọc CampaignName để đổi cách chiếu ad không: không tìm thấy. `[V]`**

---

## 7. TẦNG tROAS — điểm ăn tiền gián tiếp mà tôi đánh giá là quan trọng nhất

`ABILibsSDK::ABILibsCustomEvent` (`null.h:476–507`) bắn **sự kiện doanh thu tuỳ biến** để nuôi thuật toán đấu giá UA (tROAS = target Return On Ad Spend):

| Method | Tham số |
|---|---|
| `TROASEvent(string, MaxSdkBase::AdInfo)` · `TROASEvent(string)` · `TROASEvent(string, …)` | 3 overload |
| `TROASEvent2(...)` | 3 overload — **bộ ngưỡng thứ hai chạy song song** |
| `BambooAdEvent(string, MaxSdkBase::AdInfo)` · `BambooAdEvent(string)` | 3 overload |
| `BambooRewardedEvent(string, MaxSdkBase::AdInfo)` · `BambooRewardedEvent(string)` | 3 overload — **tách riêng rewarded** |
| `TROASPurchaseEvent(string)` | phía IAP |
| `ConvertAdMobRevenueToUsd(string)` · `ConvertToUsd(string)` · `GetExchangeRate(string)` · `ParseExchangeRatesJson(string)` | **quy đổi tiền tệ có bảng tỉ giá riêng** |
| `LogEvent(string, Dictionary<string, …>)` | đích đến |
| `*OnMainThread` cho mọi loại + `MainThreadDispatcher` | đảm bảo bắn đúng thread |

`[V]` toàn bộ. `[I]` cách hoạt động: tích luỹ doanh thu ad → vượt ngưỡng trong `FloatArrayWrapper` (theo quốc gia) → bắn event có tên chứa ngưỡng → Firebase/AppsFlyer → Google/Meta dùng làm tín hiệu đấu giá. Nối từ: `FloatArrayWrapper` + `TryGetDeviceRegionCountryCode` + `ConvertToUsd` + `LogEvent`.

**Tên event thật + giá trị ngưỡng: `[U]`.**

Kèm theo, tầng đo doanh thu ad ở mức từng lượt hiển thị:
```
AdMob:  OnAdBannerPaid · OnAdCollapsibleBannerPaid · OnAdRewardedAdPaid ·
        OnAppOpenAppPaidEvent · MRECAdsOnOnAdPaid
            → HandleAdPaidEvent(string, AdValue, ResponseInfo)     null.h:3121
MAX:    OnAdRevenuePaidEvent(AdsType, string, AdInfo)              null.h:3325
            ↓
        AdsManager::OnAdRevenuePaidEvent(ImpressionData)           null.h:3289
            ├→ AppsFlyerManager::TrackAppsflyerAdRevenue(ImpressionData)   null.h:3042
            └→ SDK::Analytics::AnalyticsManager::TrackAdImpression(ImpressionData) null.h:3290
```
`[V]`

Phía IAP: `AppsFlyerManager::TrackAppsFlyerPurchase(string, decimal)` `null.h:3041`; `FirebaseAnalyticsManager::LogPurchase(string)` `null.h:709`; và **AppsFlyer Purchase Connector** có trong bin (`AppsFlyerPurchaseConnector::{init, build, startObservingTransactions, setAutoLogPurchaseRevenue, setPurchaseRevenueValidationListeners, logConsumableTransaction, setIsSandbox}`) `[V]` — game có gọi hay không: `[U]`.

Funnel ad 4 nấc (`null.h:3029–3040`):
`TrackInterstitial_ClickShowButton` → `_LoadedSuccess` → `_Displayed` → `_ShowCount`; y hệt cho `TrackRewarded_*`. `[V]`

---

## 8. CÁI GÌ BẬT/TẮT ĐƯỢC TỪ XA (không cần cập nhật app)

14 nhóm key, mỗi nhóm là một class có `get_KeyRemoteConfig` + `FromJson`/`ToJson`/`LoadData`/`ResetData` (`null.h:11569–11708`):

| Nhóm | Điều khiển | Symbol đặc trưng |
|---|---|---|
| `InterAdsCapping` | cap interstitial **theo level**, bỏ qua N inter khi mở lại | `GetInterCapping`, `LevelCappingConfig`, `GetSkipFirstInterCountOnReopen` |
| `NoAdsPopup` | bật/tắt popup bán NoAds | `get_IsEnabled` |
| `HelpWithAdsGold` | bật/tắt popup ads↔gold | `get_IsEnabled` |
| `StartGold` | gold khởi tạo | `TryApplyStartGoldRemoteConfig` |
| `Revive` | **giá revive** bằng coin và bằng ads | `GetCurrentReviveCoin`, `GetCurrentReviveAds`, `ReviveConfig`, `AdsReviveConfig` |
| `EndGameReward` | thưởng cuối màn theo độ khó, bật x2-by-ads | `GetRewardCoin(LevelDifficulty)`, `GetAdsRewardCoin`, `CanShowX2Reward` |
| `Booster` | số ads/booster, **giá booster**, bật giá remote | `GetCurrentBoosterAds`, `GetBoosterPriceX3`, **`IsRemotePriceActive`** |
| `ForceUseBooster` | bật/tắt ép dùng booster | `get_IsEnabled` |
| `AntLab` | lịch event | `AntLabStartDate`, `AntLabEndDate`, `IsBetweenEvent` |
| `TestLevel` | whitelist level | `ContainsLevel`, `get_IsEnabled` |
| `ShipRace` / `SkyJump` / `StarBlaze` / `CastleClimb` | tham số 4 event | `FromJson` |

Cộng thêm, **không nằm trong nhóm trên**:
- `SDKSetup` (`null.h:592–628`): sdk key MAX, **toàn bộ ad unit id của cả 3 mediation**, chọn mediation theo từng format
- `AdmobAdSetup` (`null.h:549–572`): 6 format × (`AdScheduleUnitID` + `List<string>`) — **xoay ad unit theo lịch**
- `AnTQ::CardCollection`: `GetRemoteConfigDefault`, `GetRemoteConfigSeasonDefault`, `ReloadRemoteConfig`, `OnRemoteConfigReady`
- `ItemGlobalConfig::FetchRemote` — cấu hình item/booster nạp từ xa
- `ABILibsCustomEventConfig` — ngưỡng tROAS theo quốc gia (mục 7)

**Hai kênh nạp config song song** `[V]`:
```
Kênh 1: Firebase Remote Config
   SDK::FirebaseRemoteConfigManager::{Initialize, FetchDataAsync, FetchComplete, OnFirebaseRemoteConfigReady}
   SDK::FirebaseManager::{GetConfigValue, GetConfigString, GetConfigDouble, GetConfigBool}
Kênh 2: HTTP tự viết
   ABILibsCustomEventConfig::{StartAutoHttpFetch, FetchAndApplyHttpAsync, AutoFetchHttpConfigOnLoad}
   (kèm AggregateRetryPolicy · ExponentialBackOffRetryPolicy · RetryRequest)
```
Endpoint kênh 2 + có ký số hay không: `[U]`.

---

## 9. KHÔNG TỒN TẠI TRONG CODE (đã grep toàn bộ, không phải "chưa tìm thấy")

Liệt kê để không ai kỳ vọng nhầm khi đọc báo cáo này:

| Thứ | Kết quả grep |
|---|---|
| **Subscription / gói thuê bao** | Có class `SubscriptionInfo`, `SubscriptionPurchase`, `SubscriptionValidationResult` — **nhưng tất cả thuộc thư viện `UnityEngine::Purchasing` / `GoogleBilling`**. Không class game nào gọi. Không modal subscription, không trial, không win-back |
| **Battle pass / season pass trả phí** | không có class nào tên `BattlePass`/`SeasonPass`/`PremiumTrack` |
| **A/B test phía client** | 0 kết quả cho `ABTest`, `Variant`, `Experiment`, `Bucket`, `Cohort` |
| **Holdout / nhóm đối chứng** | 0 kết quả cho `Holdout`, `Control group`, `Baseline` |
| **Offerwall bên thứ ba** | không có SDK offerwall nào |
| **Gacha bán trực tiếp bằng tiền thật** | `CardPack` chỉ nhận qua `ClaimCardPack` / `RandomCardItem`; không có `PackageName` nào nối vào card pack trong symbol |
| **MMP khác AppsFlyer** | 0 kết quả cho `AdjustSdk`, `Singular`, `Tenjin`, `GameAnalytics`, `Facebook::Unity` |
| **IronSource thật** | `IronSourceMediationController` chỉ còn 2 symbol (`GetAdsMediationType` + ctor) — **enum và ô config còn, SDK không còn** `null.h:574–575, 596, 701, 3195` |
| **Endpoint mua hàng phía backend riêng** | `GameBackendModule::Models::*` có ~90 model — **không model nào** tên chứa Purchase/Receipt/Entitlement/Order/Shop |
| **Age gate / COPPA** | không có |
| **UI công bố tỉ lệ gacha** | không có class disclosure nào |
| **DDA (độ khó thích ứng)** | `LevelDifficulty` chỉ là thuộc tính tra cứu (`ItemGlobalConfig::GetLevelDifficultyData`), không có class nào điều chỉnh theo kỹ năng người chơi |

---

## 10. KHÔNG ĐỌC ĐƯỢC TỪ INPUT NÀY (cần APK gốc)

Ba giới hạn cứng, đã kiểm chứng:
1. **Thiếu thân hàm** của method đã đặt tên (export thiếu `null.cpp`) — VA `0x3ac1a10` của `AdsManager::SetupInterstitial` chỉ có ở `null.h:3175`, không có body ở file nào
2. **Thiếu toàn bộ string literal managed** (nằm trong `global-metadata.dat`) — `grep 'ca-app-pub'` = 0, `'interstitial'` = 0, `'com.'` = 0
3. **Field public không có accessor thì vô hình** (IL2CPP không sinh method) — nên `AdsConfig`, `InterLoseConfig`, `ShopPackageDataConfig`, `OfferDetails`, `ShipRaceStageConfig`, `RewardAdsPlacementConfig` chỉ thấy được constructor

Do đó `[U]`:

| Không biết | Lấy ở đâu |
|---|---|
| **Level mở khoá từng event** (`IsEventUnlock` bằng bao nhiêu) | body + `*RemoteConfig` |
| `LevelShowADSInter`, `DefaultX2CoinLevel`, `DefaultBackToMenuLevel`, `DefaultlevelShowRate` | metadata |
| Mọi giá trị cap/cooldown ad (giây, số lần) | metadata + remote config |
| Mọi ad unit id, sdk key MAX, AdMob app id | metadata + manifest |
| **Danh sách SKU** (giá trị enum `PackageName`) và giá từng gói | metadata |
| Tên `placement` truyền vào `ShowAdsInter` / `ShowAdsReward` | metadata |
| Nội dung từng `UIShopPack` (bán gì, bao nhiêu) | `ShopPackageDataConfig` — chỉ có ctor |
| **Tỉ lệ rơi thẻ** trong `RateDropCardByIndex`, có hard pity không | remote config JSON |
| Ngưỡng tROAS + tên event tROAS theo quốc gia | `FloatArrayWrapper` + metadata |
| Số giây của `CloseButtonHidden` | body |
| Endpoint HTTP của `ABILibsCustomEventConfig` | body + metadata |
| MREC đặt ở màn nào | body |
| Giá trị enum `ResourceType` (danh sách booster/tiền tệ đầy đủ) | metadata |

**Cách lấy hết trong một bước:** APK gốc → `Il2CppDumper` (`global-metadata.dat` + `libil2cpp.so`) ⇒ có `dump.cs` + `stringliteral.json` ⇒ toàn bộ bảng trên chuyển thành `[V]`.

---

## Tóm tắt một trang

- **6 định dạng ad**, 2 mediation chạy song song chọn được theo từng format, ad unit **xoay theo lịch từ xa**
- **7 điểm mời xem rewarded**, tất cả gắn vào lúc người chơi *thiếu* thứ gì đó (thua, hết tim, hết booster, muốn x2)
- **4 bề mặt IAP**: NoAds (Home + popup theo số inter đã xem), StarterPack (Home), PackWorldCup (có đếm ngược), Shop; **1 pack bán được bằng 4 đường**: tiền thật / gold / xem ads / test
- **Phễu pack**: `PackBuyCount` — mua pack A đủ n lần mới hiện pack B
- **6 sink gold**, tất cả đều nằm ngay sau một thất bại hoặc một thiếu hụt
- **8 event LiveOps** + Team/Clan server-side + daily reward; mọi event mở bằng `IsEventUnlock` cấu hình từ xa (**ngưỡng level: `[U]`**)
- **Không segment người chơi cho việc chiếu ad** (N=1). Chỉ có 4 công tắc trạng thái + cap theo level
- **CÓ segment theo quốc gia** — nhưng cho tầng **tROAS** (`ABILibsSDK`), tức để nuôi đấu giá UA, không phải để đổi cách chiếu ad
- **Không có**: subscription, battle pass trả phí, A/B client, holdout, offerwall, gacha bán bằng tiền thật
