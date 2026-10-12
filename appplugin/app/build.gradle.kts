import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val DEMO_LICENSE_TOKEN = "ALP1.eyJ2IjoxLCJraWQiOiJkZXYxIiwicGtnIjoidGVzdC5zZGsuemQiLCJjZXJ0cyI6WyI0MWQwNmNiNDYyOWNmMzM5MjBkOTY2YzUxNDQ3YjI3MGVmNzgyNzVmNmNhZmI0YTljYWZmYTRmNjY1ZTQ4YmIyIl0sImV4cCI6MTgyMjQ5OTI1NiwiZmVhdCI6WyJhZHMiLCJicmFpbiJdfQ.Y1eVa1odgcucGzmQfxuEk6heNyjrBBFYTiiYkkwwi0N1JEwv0pJVfmRqy_DKvdKlMMTL1PdlWSBbGInHhLsGAQ"

android {
    namespace = "com.app.plugin.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "test.sdk.zd"
        minSdk = 24
        // Điều 20.1: từ 31/08/2026 app trên Play phải target 36. Demo target đúng như app thật.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // 2.1.0 SA1 (§C): token giấy phép SDK → meta-data com.app.plugin.LICENSE; -PlicenseToken=<token> thay (biến thể licbad). Mặc định:
        // token demo `dev1` cho test.sdk.zd + cert debug cục bộ (SA2) — APK ký bằng keystore khác ra INVALID (cert).
        manifestPlaceholders["appPluginLicense"] = (findProperty("licenseToken") as String?) ?: DEMO_LICENSE_TOKEN
    }

    buildTypes {
        release {
            // Điều 5.2 / 8: bản release là sản phẩm thật. R8 full mode + shrinkResources bật để mọi
            // lỗi chỉ-release của SDK (keep rule, keep.xml) lộ ra ở đây, không phải ở app thật.
            isMinifyEnabled = true
            isShrinkResources = true
            // Ký debug: demo không phát hành; CI và emulator cài trực tiếp bản release.
            signingConfig = signingConfigs.getByName("debug")
            // Demo không phát hành: không đẩy 120 MB mapping lên Firebase mỗi lần build.
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 2.1.0 SC1 (D4): -PstripSdkLogs thêm rule bỏ logD/logW của SDK — chỉ biến thể đo, không bao giờ APK cổng thiết bị.
            if (findProperty("stripSdkLogs").let { it != null && it != "false" }) proguardFiles(rootProject.file("docs/integration/appplugin-strip-logs.pro"))
        }
    }
    // 1.6.4 Task 5 (DOC/67 H2): app tiện ích chỉ dùng AdMob + Meta. `utility` bỏ AppLovin MAX và mọi adapter game khỏi
    // classpath (danh sách ở cuối file); `game` giữ đủ như trước 1.6.4. Mọi cổng thiết bị chạy `utility`.
    flavorDimensions += "stack"
    productFlavors {
        create("utility") { dimension = "stack" }   // app tiện ích: AdMob + Meta, 0 adapter game (DOC/67 H2)
        create("game") { dimension = "stack" }      // cấu hình đủ adapter như trước 1.6.4
        create("nextgen") { dimension = "stack" }   // 2.4.0 NG6 (Hợp đồng C): AdMob Next-Gen (:appplugin-nextgen) thay Legacy; A/B với utility (tools/ab_compare.py)
    }
    sourceSets.getByName("nextgen").res.directories.add("src/utility/res")   // 2.4.0 NG6: manifest nextgen = chép utility → dùng chung network_security_config_utility
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // 2.4.0 NG6: gốc (Legacy GMA) chỉ ở utility/game; nextgen dùng module Next-Gen (cùng nguồn chung, AdMob Next-Gen) — tên biến thể utility/game giữ nguyên.
    add("utilityImplementation", project(":"))
    add("gameImplementation", project(":"))
    add("nextgenImplementation", project(":appplugin-nextgen"))
    // 2.0.0 C2b: flavor game thêm hai artifact chỉ-phụ-thuộc (AppLovin MAX + mạng game; GameModule); utility chỉ có gốc.
    add("gameImplementation", project(":appplugin-max"))
    add("gameImplementation", project(":appplugin-game"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Needed at compile time in this module to call AdMob classes directly for the test ad below. 2.4.0 NG6: chỉ utility/game (nextgen 0 Legacy, [S4]).
    listOf("utility", "game").forEach { add("${it}Implementation", "com.google.android.gms:play-services-ads:25.4.0") }
}
// 2.4.0 NG6 (S4): mọi cấu hình nextgen* loại Legacy GMA khỏi classpath (Task R: dependencies nextgenReleaseRuntimeClasspath, 0 dòng play-services-ads(-lite)).
configurations.matching { it.name.startsWith("nextgen") }.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads"); exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

// 1.9.0 T1 (D2, D22): 5 adapter GMA tuỳ chọn compileOnly ở SDK. game khai lại cả 5 (classpath không đổi); utility chỉ khai partner
// trong -PbiddingPartners=<csv> (⊆ khoá dưới, mặc định rỗng, từ khác → GradleException) (2.0.0 C2b: không còn khối exclude — gốc không kéo nhóm game).
// Toạ độ = bản SDK khai compileOnly (OptionalAdaptersCompileOnlyTest).
val optionalGmaAdapters = mapOf("pangle" to ("com.google.ads.mediation:pangle:8.1.0.3.1" to listOf("com.pangle.global")),
    "mintegral" to ("com.google.ads.mediation:mintegral:17.1.61.1" to listOf("com.mbridge.msdk.oversea")),
    // 2.0.0 T0f (D51): unity-ads 4.19 tham chiếu com.unity3d.coherence → partner kéo theo CẢ nhóm phụ này; applovin chỉ cần dontwarn privacypass (consumer-rules.pro).
    "unity" to ("com.google.ads.mediation:unity:4.19.0.0" to listOf("com.unity3d.ads", "com.unity3d.coherence")),
    "vungle" to ("com.google.ads.mediation:vungle:7.7.4.2" to listOf("com.vungle")), "applovin" to ("com.google.ads.mediation:applovin:13.6.2.1" to listOf("com.applovin")))
val biddingPartners = (findProperty("biddingPartners") as String?).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
if (biddingPartners.any { it !in optionalGmaAdapters } || biddingPartners.distinct() != biddingPartners) throw GradleException("-PbiddingPartners=$biddingPartners: chỉ nhận tên không trùng trong ${optionalGmaAdapters.keys}")
// 2.0.0 C2b: unity-ads ra khỏi POM gốc (sang appplugin-max) mà adapter GMA unity không kéo SDK của nó → partner unity ở utility khai thêm unity-ads (game có qua -max).
val partnerSdkDeps = mapOf("unity" to listOf("com.unity3d.ads:unity-ads:4.19.0"))
dependencies { optionalGmaAdapters.values.forEach { add("gameImplementation", it.first) }; biddingPartners.forEach { add("utilityImplementation", optionalGmaAdapters.getValue(it).first); partnerSdkDeps[it].orEmpty().forEach { c -> add("utilityImplementation", c) } } }
// 2.0.0 T0f (D50): resource app ads_bidding_partners CHỈ khi -PbiddingPartners khác rỗng. resValue kiểu array bị AGP escape thành mảng size=0 → sinh values/*.xml thật từ TASK (sống qua clean, an toàn configuration cache).
abstract class BiddingRes : DefaultTask() { @get:Input abstract val partners: ListProperty<String>; @get:OutputDirectory abstract val out: DirectoryProperty
    @TaskAction fun gen() = out.file("values/ads_bidding_partners.xml").get().asFile.run { parentFile.mkdirs(); writeText("<resources><string-array name=\"ads_bidding_partners\">" + partners.get().joinToString("") { "<item>$it</item>" } + "</string-array></resources>") } }
if (biddingPartners.isNotEmpty()) androidComponents.onVariants { v -> if (v.flavorName == "utility") v.sources.res?.addGeneratedSourceDirectory(
    tasks.register<BiddingRes>("gen${v.name.replaceFirstChar(Char::uppercase)}BiddingRes") { partners.set(biddingPartners) }, BiddingRes::out) }
