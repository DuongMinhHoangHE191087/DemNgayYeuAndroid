# appplugin-max consumer rules — phần AppLovin MAX của consumer-rules.pro gốc (2.0.0 C2b).
# Lớp adsmax/* ở core tham chiếu com.applovin; app game kéo SDK qua artifact này, các adapter MAX tự mang rules riêng.
-dontwarn com.applovin.**
# Adapter applovin kéo OMID bản có attestation tham chiếu com.amazon.privacypass (không có trong classpath).
-dontwarn com.amazon.privacypass.**
# Core lấy impl MAX bằng Class.forName tên cố định (MaxBridge.IMPL_CLASS) — R8 không thấy lời gọi đó.
-keep class com.app.plugin.ads.adsmax.MaxBridgeImpl { <init>(); }
