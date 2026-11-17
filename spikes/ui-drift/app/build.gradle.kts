plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
}

// -PcomposeVariant=cmp swaps the androidx Compose coordinates for org.jetbrains.compose (Android target).
val cmp = providers.gradleProperty("composeVariant").orNull == "cmp"

android {
  namespace = "spike.drift"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "spike.drift"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures { compose = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// CMP 1.12.1 AARs declare minCompileSdk 37 but only android-36.x is installed here; the spike only needs them to render under
// Robolectric sdk 34, so skip that metadata check for the cmp variant (recorded in the spike doc).
if (cmp) tasks.configureEach { if (name.endsWith("AarMetadata")) enabled = false }

// Golden images live next to the tests so they survive `clean`.
roborazzi { outputDir.set(layout.projectDirectory.dir("src/test/snapshots")) }

dependencies {
  if (cmp) {
    implementation(libs.cmp.runtime)
    implementation(libs.cmp.ui)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.animation)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material.icons.extended)
    testImplementation(libs.cmp.ui.test.junit4)
  } else {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
  }
  implementation(libs.coil.compose)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
}
