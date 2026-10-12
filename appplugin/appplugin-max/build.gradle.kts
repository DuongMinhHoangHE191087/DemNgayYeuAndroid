// 2.0.0 C2b (D2, D16 B): artifact chỉ phụ thuộc, 0 mã nguồn; mã adsmax ở module gốc (compileOnly AppLovin).
plugins {
    id("com.android.library")
    id("maven-publish")
}

val sdkVersion = rootProject.extra["sdkVersion"] as String
// 2.1.0 G1 (M32): compileSdk/minSdk/JVM/consumer rules/singleVariant chung ở gradle/module-defaults.gradle.
apply(from = "../gradle/module-defaults.gradle")
android {
    namespace = "com.app.plugin.max"
}
dependencies {
    api(project(":"))
    // AppLovin SDK 13.6.2 + 8 adapter MAX: cùng danh sách với compileOnly của module gốc.
    @Suppress("UNCHECKED_CAST")
    (rootProject.extra["applovinMaxCoords"] as List<String>).forEach { api(it) }
    // Mạng game ra khỏi POM gốc ở 2.0.0 (cùng bản như trước).
    api("com.bigossp:admob-mediation:5.7.0.0")
    api("com.google.ads.mediation:chartboost:9.12.1.1")
    api("com.google.ads.mediation:fyber:8.4.5.1")
    api("com.unity3d.ads:unity-ads:4.19.0")
}
apply(from = "../gradle/publish.gradle")
