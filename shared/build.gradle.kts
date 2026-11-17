// shared/build.gradle.kts - F-02 tao, F-04 va F-05 bo sung, F-15 don spike, F-24 dua Compose sang commonMain
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.jetbrains.compose)
}

kotlin {
  android {
    namespace = "com.example.shared"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    minSdk = 24
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    withHostTest { isIncludeAndroidResources = true }
  }
  listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework { baseName = "Shared"; isStatic = true } }
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.androidx.room.runtime)
      implementation(libs.androidx.sqlite.bundled)
    }
    // F-05: Compose CHI o iosMain (F-24 se dua sang commonMain). Android classpath khong duoc co "compose".
    iosMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.material3.multiplatform)
    }
    commonTest.dependencies { implementation(kotlin("test")); implementation(libs.kotlinx.coroutines.test) }
    getByName("androidHostTest").dependencies {
      implementation(libs.junit)
      implementation(libs.androidx.junit)
      implementation(libs.robolectric)
    }
  }
}
// F-05: plugin kotlin.compose gan vao MOI compilation; compilation Android khong co compose runtime -> crash
// IncompatibleComposeRuntimeVersionException. Bo compiler plugin khoi classpath Android (iOS giu nguyen).
configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspathAndroid") }.configureEach {
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-compose-compiler-plugin-embeddable")
}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  // iOS: cau hinh co the khong ton tai (target bi tat); matching + configureEach bo qua neu thieu.
  listOf("kspIosArm64", "kspIosSimulatorArm64").forEach { name ->
    configurations.matching { it.name == name }.configureEach { project.dependencies.add(name, libs.androidx.room.compiler) }
  }
}
