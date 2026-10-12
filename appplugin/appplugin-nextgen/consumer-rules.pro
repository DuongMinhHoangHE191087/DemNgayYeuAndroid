# appplugin consumer rules — áp vào R8 của app dùng SDK.
#
# Từ 1.2.0 SDK không keep-all nữa. R8 của app thấy mọi lời gọi từ code app vào SDK nên tự giữ
# đúng những API app dùng; phần còn lại của SDK được rút gọn và đổi tên như code của chính app,
# nên APK phát hành không còn lộ tên lớp, tên hàm nội bộ của SDK. File này chỉ giữ những thứ R8
# không nhìn thấy được: truy cập qua reflection và serialize theo tên field.
#
# Trước 1.2.0 file này keep nguyên các package com.app.plugin.* và cả Firebase, AppsFlyer, Meta,
# AppLovin. Bốn SDK đó tự mang consumer rules riêng trong AAR của chúng, nên không cần keep lại
# ở đây. Speaker Clean và Invoice Maker (R8 full mode) đã chạy production với bộ rule hẹp này.

# ── Gson ─────────────────────────────────────────────────────────────────────────────────────
# Gson 2.10.1 không kèm rule R8. SDK parse JSON ra Map qua `object : TypeToken<Map<...>>() {}`:
# Gson đọc kiểu generic từ chữ ký của lớp con ẩn danh, nên phải giữ Signature và lớp con đó.
-keepattributes Signature
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Model duy nhất SDK serialize theo tên field (không có @SerializedName): payload log gửi lên
# backend analytics. Đổi tên field là hỏng JSON mà không có lỗi nào.
-keepclassmembers class com.app.plugin.logevent.EventRequest { <fields>; }
-keepclassmembers class com.app.plugin.logevent.UserProperty { <fields>; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Retrofit 2.9 (LogService) ───────────────────────────────────────────────────────────────
# Rule đi kèm Retrofit 2.9 chưa đủ cho R8 full mode (mặc định từ AGP 8): kiểu trả về generic
# `Call<ResponseBody>` bị xoá chữ ký và Retrofit ném lỗi lúc tạo service.
-keepattributes InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# ── Enum đọc lại theo tên ────────────────────────────────────────────────────────────────────
# SubscriptionLifecycle và PaywallManager dựng lại enum bằng valueOf() từ chuỗi đã lưu hoặc từ
# remote config. Giữ tên hằng để chuỗi đã lưu ở bản trước vẫn đọc được sau khi app cập nhật.
-keepclassmembers enum com.app.plugin.** { <fields>; }

# ── Meta Audience Network ────────────────────────────────────────────────────────────────────
# AN 6.21 tham chiếu annotation chỉ có lúc compile (Nullsafe) mà không kèm dontwarn cho nó.
-dontwarn com.facebook.infer.annotation.**

# ── Meta SDK (facebook-core) ────────────────────────────────────────────────────────────────
# SDK chỉ compileOnly facebook-core (xem MetaSdkStarter). App không tự khai facebook-core thì lớp
# này vắng mặt lúc chạy, và R8 của app không được coi đó là lỗi thiếu lớp. Dấu `*` phủ cả lớp
# `$Companion`: Kotlin gọi AppEventsLogger.activateApp() qua đó (R8 của app demo đã báo thiếu).
-dontwarn com.facebook.FacebookSdk*
-dontwarn com.facebook.appevents.AppEventsLogger*

# ── AppLovin MAX (tuỳ chọn) ───────────────────────────────────────────────────────────────────
# App tiện ích không kèm AppLovin MAX (tuỳ chọn; runtime dò bằng AdsHelper.isMaxSdkAvailable): lớp adsmax/* tham chiếu com.applovin chỉ được gọi khi SDK có mặt. (1.6.4 Task 5)
-dontwarn com.applovin.**
# com.amazon.privacypass không có trong classpath và được tham chiếu ở hai nơi → R8 Missing class: (1) OMID bản kèm adapter applovin khi bật -PbiddingPartners=applovin (2.0.0 T0f D51); (2) ads-mobile-sdk của GMA Next-Gen (2.4.0, đo ở 6 lượt gate NG6b).
-dontwarn com.amazon.privacypass.**
# 2.0.0 C1c (D1): core lấy impl MAX bằng Class.forName tên cố định (MaxBridge.IMPL_CLASS) — R8 không thấy lời gọi đó.
-keep class com.app.plugin.ads.adsmax.MaxBridgeImpl { <init>(); }
# 2.1.0 G1 (F5): keep của GameModule chỉ ở appplugin-game/consumer-rules.pro — giữ ở đây thì mã game theo vào mọi APK utility.

# ── Room / WorkManager (kéo theo bởi play-services-ads 25.4 và unity-ads 4.19) ──────────────
# Room 2.2.5 (từ work-runtime 2.7.0) chỉ keep lớp con RoomDatabase, không keep <init>(). R8 full
# mode xoá constructor của WorkDatabase_Impl, androidx.startup ném lỗi trước màn đầu tiên và bản
# release sập lúc mở (đo 2026-09-24 trên app demo, targetSdk 36). Chỉ ghim WorkManager 2.9.1 không
# đủ; rule này thì đủ. Speaker Clean và Invoice sống nhờ rule riêng của app; app-template thiếu.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# WorkManager dựng InputMerger và Worker từ tên lớp (Invoice: backup không bao giờ chạy).
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# ── Firebase components (firebase-components 18.0.0 đi cùng BoM 33.4.0) ────────────────────────
# Rule của Firebase giữ lớp registrar nhưng không giữ <init>(); ComponentDiscovery tạo registrar
# bằng reflection nên Crashlytics, FCM, Installations chết câm ở bản release (NoSuchMethodException).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# ── kotlinx.serialization: ClassValue (kéo theo bởi chartboost-sdk và vungle-ads) ──────────────
# ClassValueCache/ClassValueParametrizedCache tạo ClassValueReferences, lớp con của java.lang.ClassValue
# (chỉ có từ API 34). Thư viện chỉ dùng chúng khi Class.forName("java.lang.ClassValue") thành công. Nhưng
# R8 gộp ngang chúng vào một lớp dùng lúc khởi động (đo 2026-09-24 trên app demo: cùng lớp với
# LogFileManager của Crashlytics và BannerLoadGuard), nên ART API < 34 xác minh lớp gộp đó lúc
# Crashlytics khởi tạo, resolve ClassValue thất bại và in NoClassDefFoundError (13–16 dòng, API 28).
# Nâng kotlinx.serialization (1.5.1 -> 1.8.1) không hết: cả hai phát hiện ClassValue y như nhau. Giữ
# riêng ba lớp này (vẫn đổi tên, vẫn xoá được nếu không dùng) để R8 không gộp và không inline chúng.
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.ClassValueCache { *; }
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.ClassValueParametrizedCache { *; }
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.ClassValueReferences { *; }
# Keep trên không chặn API outlining: R8 9.2 vẫn tách lời gọi ClassValue.get (API 34) của hai lớp cache
# ra lớp tổng hợp dùng chung AndroidAttribution$$ExternalSyntheticApiModelOutline0 của Unity Ads (có từ
# 1.6.0), nên ART API 28/26 xác minh lớp đó lúc Unity dùng và in ClassNotFoundException java.lang.ClassValue;
# API 28 thêm 147 ms tranh khoá lớp (đo 2026-09-28). R8 không có rule tắt outlining theo lớp. useClassValue
# vốn false dưới API 34; ép false ở mọi API thì nhánh ClassValue là mã chết và ba lớp trên bị xoá. Từ API 34
# thư viện dùng ConcurrentHashMapCache của chính nó (nhánh sẵn có cho mọi máy dưới 34). Ba keep ở trên giữ
# lại làm lớp phòng hờ: nếu rule dưới không còn khớp (kotlinx đổi tên/kiểu trường), chúng vẫn chặn đường
# gộp ngang của 109926d. tools/release_gate.sh FAIL khi mapping còn chữ ClassValue.
-assumevalues class kotlinx.serialization.internal.CachingKt { static boolean useClassValue return false; }

# ── 2.1.0 SC1: dấu vết dựng appplugin/<phiên bản>/<sha> (BuildInfo.ID) còn trong dex để truy nguồn APK. Rule bỏ log SDK là tuỳ chọn
# (docs/integration/appplugin-strip-logs.pro), KHÔNG ở đây: các cổng đọc dòng logD của SDK (D4).
-keep class com.app.plugin.BuildInfo
-keepclassmembers class com.app.plugin.BuildInfo { public static java.lang.String ID; }
