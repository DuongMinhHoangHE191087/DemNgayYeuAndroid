import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
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
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${getEnv("CLOUDINARY_CLOUD_NAME", "dt6p7wm6i")}\"")
    buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${getEnv("CLOUDINARY_UPLOAD_PRESET", "inlove_unsigned")}\"")
    buildConfigField("String", "CLOUDINARY_FOLDER", "\"${getEnv("CLOUDINARY_FOLDER", "inlove_memories")}\"")
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
      isMinifyEnabled = false
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
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.addAll(
    listOf(
      "FIREBASE_APPCHECK_DEBUG_TOKEN",
      "CLOUDINARY_CLOUD_NAME",
      "CLOUDINARY_UPLOAD_PRESET",
      "CLOUDINARY_FOLDER",
      "SMTP_HOST",
      "SMTP_PORT",
      "SMTP_SENDER_EMAIL",
      "SMTP_SENDER_PASSWORD",
      "SMTP_SENDER_NAME"
    )
  )
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(project(":appplugin"))
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

  // Google Mobile Ads SDK (AdMob) & UMP (User Messaging Platform)
  implementation(libs.google.play.services.ads)
  implementation(libs.user.messaging.platform)

  // Google Play Billing Client KTX v7 — Subscriptions & In-App Purchases
  implementation(libs.google.play.billing.ktx)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
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
