import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  val envFile = rootProject.file(".env")
  val envExampleFile = rootProject.file(".env.example")
  val envProps = Properties()
  if (envExampleFile.exists()) {
    envExampleFile.inputStream().use { stream -> envProps.load(stream) }
  }
  if (envFile.exists()) {
    envFile.inputStream().use { stream -> envProps.load(stream) }
  }

  fun getEnv(key: String, default: String = ""): String {
    val raw = envProps.getProperty(key, default) ?: default
    return raw.trim().removeSurrounding("\"").removeSurrounding("'")
  }

  defaultConfig {
    applicationId = "com.aistudio.inlove.kmrv"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Owner-controlled HTTPS site hosting /privacy, /terms and /delete-account (see docs/release/PLAY_RELEASE_CHECKLIST.md)
    buildConfigField("String", "LEGAL_BASE_URL", "\"${getEnv("LEGAL_BASE_URL", "https://inloveapp.com")}\"")
  }

  packaging {
    resources {
      excludes += listOf(
        "META-INF/LICENSE.md",
        "META-INF/LICENSE.txt",
        "META-INF/NOTICE.md",
        "META-INF/NOTICE.txt"
      )
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseAppId = project.findProperty("ADMOB_APP_ID_RELEASE")?.toString() ?: ""
      val releaseBannerId = project.findProperty("ADMOB_BANNER_ID_RELEASE")?.toString() ?: ""
      val releaseInterstitialId = project.findProperty("ADMOB_INTERSTITIAL_ID_RELEASE")?.toString() ?: ""
      val releaseAoaId = project.findProperty("ADMOB_AOA_ID_RELEASE")?.toString() ?: ""

      manifestPlaceholders["admobAppId"] = releaseAppId
      buildConfigField("String", "ADMOB_BANNER_ID", "\"$releaseBannerId\"")
      buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$releaseInterstitialId\"")
      buildConfigField("String", "ADMOB_AOA_ID", "\"$releaseAoaId\"")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
      buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
      buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
      buildConfigField("String", "ADMOB_AOA_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true // java.time trên API 24-25
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // Room's MigrationTestHelper reads app/schemas/<db>/<version>.json from the merged assets (Migration12To13Test).
  // AGP's unit-test variant reads the debug merge, not test-source-set assets, so the schemas ride in debug assets only.
  sourceSets {
    getByName("debug") { assets.srcDirs(files("$projectDir/schemas")) }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Room needs to know where to write the exported schema JSON (app/schemas/) — required now
// that AppDatabase turns on exportSchema = true (data-sync-and-real-pairing plan, Task 1).
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.addAll(
    listOf(
      "FIREBASE_APPCHECK_DEBUG_TOKEN",
      // Khóa phía server Cloudinary: không bao giờ vào BuildConfig, kể cả khi còn sót trong .env của máy dev.
      "CLOUDINARY_API_KEY",
      "CLOUDINARY_API_SECRET",
      "CLOUDINARY_TOKEN_KEY",
      "CLOUDINARY_DELIVERY_MODE",
      "CLOUDINARY_CLOUD_NAME",
      "CLOUDINARY_UPLOAD_PRESET",
      "CLOUDINARY_FOLDER",
      // SMTP_* may still exist in a developer .env; never expose them in BuildConfig (empty values break javac).
      "SMTP_HOST",
      "SMTP_PORT",
      "SMTP_SENDER_EMAIL",
      "SMTP_SENDER_PASSWORD",
      "SMTP_SENDER_NAME",
      // scripts/seed_firestore.js dùng riêng, không phải cấu hình app Android — không cần
      // (và không nên) lộ vào BuildConfig của APK. Thiếu dòng này khiến Secrets Gradle Plugin
      // tự sinh field BuildConfig cho các key này; giá trị rỗng trong .env.example sinh ra
      // Java không hợp lệ (`public static final String X = ;`) và làm vỡ compileDebugJavaWithJavac.
      "SEED_TESTER_PRIMARY_PASSWORD",
      "SEED_TESTER_PARTNER_PASSWORD",
      "SEED_TESTER_VIP_PASSWORD",
      "SEED_TESTER_FREE_PASSWORD",
      "SEED_ADMIN_PASSWORD"
    )
  )
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.appplugin)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firestore support
  implementation(libs.firebase.firestore)
  // Crashlytics runtime
  implementation(libs.firebase.crashlytics)
  // Callable Cloud Functions: upload ký số và URL phát Cloudinary (docs/architecture/MEDIA_CLOUDINARY.md)
  implementation(libs.firebase.functions)

  // Firebase Auth and Google Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)

  // Google Mobile Ads SDK (AdMob) & UMP (User Messaging Platform).
  // :appplugin declares play-services-ads as `implementation`, not `api`, so it is NOT exposed
  // transitively to :app despite `implementation(libs.appplugin)` below — confirmed by an
  // actual build: every file here importing com.google.android.gms.ads.* failed to resolve when
  // this line was removed on the (wrong) assumption that the module dependency alone was enough.
  // :app still needs its own direct dependency; what actually fixes the version skew is pinning
  // it to the exact same version appplugin declares (playServicesAds = "25.4.0" in the catalog),
  // not removing it.
  implementation(libs.google.play.services.ads)
  implementation(libs.user.messaging.platform)

  // Google Play Billing Client KTX — Subscriptions & In-App Purchases (version: libs.versions.toml `billingKtx`)
  implementation(libs.google.play.billing.ktx)

  // WorkManager — offline sync outbox (data-sync-and-real-pairing plan, Task 6)
  implementation(libs.androidx.work.runtime.ktx)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.firebase.appcheck.debug)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Chặn đóng gói release thiếu cấu hình: AdMob App ID rỗng làm app crash lúc khởi động, thiếu
// keystore thì AAB không ký được. Chỉ áp cho task bundle/assemble/package Release (lint/test vẫn chạy).
gradle.taskGraph.whenReady {
  val packaging = allTasks.any { it.project == project && Regex("^(bundle|assemble|package)Release$").matches(it.name) }
  if (!packaging) return@whenReady
  val missing = buildList {
    listOf("ADMOB_APP_ID_RELEASE", "ADMOB_BANNER_ID_RELEASE").forEach {
      val v = project.findProperty(it)?.toString()
      if (v.isNullOrBlank()) add("-P$it (gradle property)")
      else if (!Regex("""ca-app-pub-\d{16}[~/]\d{10}""").matches(v)) add("-P$it sai định dạng (ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN)")
    }
    listOf("STORE_PASSWORD", "KEY_PASSWORD").forEach { if (System.getenv(it).isNullOrBlank()) add("env $it") }
    val ks = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    if (!file(ks).exists()) add("keystore file ($ks) — set env KEYSTORE_PATH")
  }
  if (missing.isNotEmpty()) throw GradleException("Release build thiếu cấu hình: ${missing.joinToString("; ")}")
}
