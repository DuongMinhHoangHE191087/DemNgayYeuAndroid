// AGP 9 biên dịch Kotlin sẵn (built-in Kotlin), không còn plugin org.jetbrains.kotlin.android.
// Cùng AGP với Speaker Clean và Invoice Maker (9.2.1), để app dựng được SDK bằng composite build.
plugins {
    id("com.android.library")
    id("maven-publish")
}

// Nguồn duy nhất của số phiên bản: bản publish lên Maven và MonetizationSdk.VERSION (qua
// BuildConfig) đọc cùng một giá trị, nên không thể lệch nhau.
val sdkVersion = "2.5.1"
// Module con (appplugin-game, ...) đọc cùng giá trị qua rootProject.extra (2.0.0 C2a).
extra["sdkVersion"] = sdkVersion
// 2.1.0 SC1 (I1): sha của BuildInfo.ID: -PbuildSha → env APPPLUGIN_BUILD_SHA (dựng từ git archive, không có .git) → git rev-parse → 0000000.
val gitShortSha = providers.gradleProperty("buildSha").orElse(providers.environmentVariable("APPPLUGIN_BUILD_SHA")).orElse(
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD"); isIgnoreExitValue = true }.standardOutput.asText.map { it.trim().ifEmpty { "0000000" } }).orElse("0000000")
extra["gitShortSha"] = gitShortSha
// 2.0.0 C2b (D2): AppLovin SDK + 8 adapter MAX — compileOnly/test ở core, `api` ở appplugin-max (cùng danh sách, cùng bản).
extra["applovinMaxCoords"] = listOf(
    "com.applovin:applovin-sdk:13.6.2",
    "com.applovin.mediation:bigoads-adapter:5.7.0.0",
    "com.applovin.mediation:chartboost-adapter:9.12.1.0",
    "com.applovin.mediation:google-adapter:25.4.0.0",
    "com.applovin.mediation:vungle-adapter:7.7.4.0",
    "com.applovin.mediation:facebook-adapter:6.21.0.0",
    "com.applovin.mediation:mintegral-adapter:17.1.61.0",
    "com.applovin.mediation:unityads-adapter:4.19.0.0",
    "com.applovin.mediation:google-ad-manager-adapter:25.4.0.0",
)

// 2.0.0 C3a (D4): API công khai khai báo tường minh (appplugin-game khai lại cho mã của nó, 2.1.0 G1).
// 2.1.0 G1 (D9): gốc dùng chính SPI của mình (RcSectionRegistry.resetForTest đặt BrainSignalListener.installed) → phải opt-in.
kotlin {
    explicitApi()
    compilerOptions { optIn.add("com.app.plugin.AppPluginInternalApi") }
}

android {
    namespace = "com.app.plugin"
    compileSdk = 36
    // 2.0.0 C3a (D4): mọi resource SDK khai bắt đầu bằng appplugin_; khoá app-contract (admob_*, ...) do app khai nên không bị ảnh hưởng.
    resourcePrefix = "appplugin_"

    defaultConfig {
        multiDexEnabled = true
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
    }

    buildTypes {
        release {
            // Không minify AAR của thư viện. R8 chạy riêng trên thư viện không biết app sẽ gọi gì,
            // nên chỉ giữ được những package liệt kê tay: bản 1.1.0 build ra AAR đã mất hẳn
            // crm/, offerwall/, appsflyer/ và AdsConditionOb. R8 của app mới là nơi rút gọn và
            // đổi tên SDK, với consumer-rules.pro.
            isMinifyEnabled = false
        }
    }
    // Kotlin lấy jvmTarget theo targetCompatibility. Giữ 1.8: bytecode 11 chặn app nào còn
    // target 1.8 inline hàm của SDK.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = true
    }

    // 2.0.0 C3a (D4): ResourceName là lỗi (resourcePrefix ở trên). Lint không thấy font: ResourcePrefixTest RP1 kiểm chúng.
    lint {
        error += "ResourceName"
    }

    testOptions {
        // Cho phép unit test JVM chạy mà không cần Robolectric: các API Android chưa mock
        // sẽ trả về giá trị mặc định thay vì ném "not mocked". Cần thiết vì BrainStorage
        // đi qua PreferenceHelper (SharedPreferences) — trong unit test nó tự thoái hoá
        // thành no-op, đúng như thiết kế fail-safe.
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release")
    }
}

// 2.1.0 SC1-2 (§G): BuildInfo.ID ← BuildSha.ID (Kotlin nguồn sinh lúc build), không dùng BuildConfig.
apply(from = "gradle/build-sha.gradle")

dependencies {

    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    // Cần cho lớp `ads/`: `AdsPlBase` nhận `Context` nên không dựng được thuần JVM. Phần
    // brain/iap/consent không dùng mock — chúng được thiết kế để test được mà không cần.
    testImplementation("org.mockito:mockito-core:5.23.0")
    // MetaSdkStarter gọi thẳng FacebookSdk (compileOnly ở dưới): test cần lớp đó để mockStatic ba setter cờ
    // auto-init (review N1). Chỉ classpath test; app vẫn tự khai facebook-core.
    testImplementation("com.facebook.android:facebook-core:17.0.2")
    // 1.9.0 T1 (B11): adapter pangle trên classpath test, để BiddingAdMobPathTest thấy registry "pangle present".
    testImplementation("com.google.ads.mediation:pangle:8.1.0.3.1")
    // 2.0.0 C2b: bigo GMA adapter ra khỏi POM gốc (sang appplugin-max); MediationAvailabilityTest vẫn cần nó trên classpath test.
    testImplementation("com.bigossp:admob-mediation:5.7.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    api("androidx.multidex:multidex:2.0.1")
    // api
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")

    implementation("com.appsflyer:af-android-sdk:6.16.2")
    implementation("com.android.installreferrer:installreferrer:2.2")

    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")

    // Play Billing: `implementation` chứ không `api` — API công khai của SDK chỉ dùng model
    // riêng (com.app.plugin.iap.IapProduct/IapPurchase), không để lộ kiểu của Google ra
    // ngoài. Nhờ vậy nâng cấp billing bên trong không bắt app tích hợp phải sửa theo.
    implementation("com.android.billingclient:billing:9.0.0")
    // PlayIntegrityGuard. 1.6.0 cần play-services-basement 18.9.0, đúng bản GMA 25.4 đã kéo về,
    // nên không nâng thêm gì trong graph của app.
    implementation("com.google.android.play:integrity:1.6.0")

    implementation("com.google.android.gms:play-services-ads:25.4.0")

    // UMP (consent). Đã có sẵn bắc cầu qua play-services-ads, nhưng khai báo tường minh:
    // code của ta compile trực tiếp trên API này, và phụ thuộc ngầm vào một dependency bắc
    // cầu nghĩa là bản nâng cấp AdMob nào đó gỡ nó ra sẽ làm ta vỡ mà không ai lường trước.
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // GMA 25.4.0 set: each AdMob adapter is the first release Google built and tested on
    // 25.4.0 (its POM declares play-services-ads:25.4.0). GMA 25 removed APIs the 24.x
    // adapters still call (mediation VersionInfo, onFailure(String), ...), so bump GMA and
    // every adapter together, never one alone. Meta 6.21.0.1 on GMA 24.9 logged "Adapter
    // failed to render native ad. Method is not found" for every native fill: it predates
    // the NativeAdMapper API that 6.21.0.3+ implements (seen in speakerclean, 2026-09-23).
    // Keep direct network SDKs and MAX adapters on the same network versions to prevent
    // duplicate runtime implementations.
    // 1.9.0 T1 (D2): 5 adapter tuỳ chọn (pangle, mintegral, unity, vungle, applovin) là compileOnly: SDK không tham chiếu
    // kiểu nào của chúng (BiddingPartners dò bằng tên lớp), app bật partner nào tự khai adapter đó (CHANGELOG 1.9.0).
    compileOnly("com.google.ads.mediation:pangle:8.1.0.3.1")
    // Bigo publishes no GMA version in its POM. Every release up to 6.1.0.0 still calls
    // MediationAdLoadCallback.onFailure(String), which GMA 25 removed - but only from the
    // rewarded loader when the Bigo slot id is empty, i.e. a mediation misconfiguration.
    // Kept on the version MAX's bigoads-adapter below matches.

    // mediation admob
    // 2.0.0 C2b (D2): bigossp admob-mediation 5.7.0.0, GMA chartboost 9.12.1.1, fyber 8.4.5.1 và unity-ads 4.19.0 ra khỏi POM gốc, sang appplugin-max.
    compileOnly("com.google.ads.mediation:applovin:13.6.2.1")
    compileOnly("com.google.ads.mediation:vungle:7.7.4.2")
    // 6.21.0.4 declares kotlin-stdlib 2.3.0 (6.21.0.3 declares 2.1.10), which lifts every consuming
    // app to 2.3.0. An app on Dagger/Hilt < 2.60 with kapt then fails kaptDebugKotlin: "Provided
    // Metadata instance has version 2.3.0, while maximum supported version is 2.2.0"; Hilt 2.60's
    // Gradle plugin needs AGP 9. Such an app pins this adapter to 6.21.0.3 (it already has the
    // NativeAdMapper native fix) until it moves to AGP 9 + Hilt 2.60 - see DOC/62 mục 3.A.
    implementation("com.google.ads.mediation:facebook:6.21.0.4")
    compileOnly("com.google.ads.mediation:mintegral:17.1.61.1")
    compileOnly("com.google.ads.mediation:unity:4.19.0.0")
//    implementation ("com.google.ads.mediation:tapjoy:13.2.1.0")

    // 1.8.1 is what the classpath already resolves to; 1.0.0 was flagged as outdated by its
    // author in Google's SDK Index, which Play Console surfaces as a warning.
    implementation("androidx.annotation:annotation:1.8.1")
    implementation("com.facebook.android:audience-network-sdk:6.21.0")
    // Meta SDK (app events, attribution cho chiến dịch Meta): chỉ compileOnly. App nào đo chiến
    // dịch Meta tự khai facebook-core và gọi AppPluginBase.initMetaSdkAfterConsent(); app khác
    // không phải mang FacebookInitProvider. Cùng bản với Speaker Clean (facebook-android-sdk 17.0.2).
    compileOnly("com.facebook.android:facebook-core:17.0.2")


    // 2.0.0 C2b (D2): AppLovin SDK + 8 adapter MAX chỉ compileOnly/test ở core (mã adsmax ở lại, dò bằng AdsHelper.isMaxSdkAvailable);
    // app game kéo chúng qua artifact appplugin-max (cùng bản, cùng danh sách ở appplugin-max/build.gradle.kts).
    for (coord in rootProject.extra["applovinMaxCoords"] as Iterable<*>) { compileOnly(coord as String); testImplementation(coord) }
}

apply(from = "gradle/publish.gradle")
