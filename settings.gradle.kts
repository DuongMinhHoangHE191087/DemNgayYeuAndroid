pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://artifacts.applovin.com/android")
      content { includeGroupByRegex("com\\.applovin.*") }
    }
    // Kept even though the AdMob-side chartboost/mintegral adapters were trimmed (Task 1 of
    // the ads-monetization-sdk-compliance plan): appplugin's AppLovin MAX block (out of scope,
    // untouched) still declares com.applovin.mediation:chartboost-adapter/mintegral-adapter,
    // which pull in these two networks' own SDKs from these same repos. Removing this repo
    // broke `:app:debugRuntimeClasspath` resolution — caught by actually running the build,
    // not just by grepping for source references (there are none; the failure is transitive).
    maven {
      url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/")
      content {
        includeGroup("com.chartboost")
        includeGroup("com.iab.omid.library")
      }
    }
    maven {
      url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
      content { includeGroup("com.mbridge.msdk.oversea") }
    }
  }
}

rootProject.name = "InLove"

include(":app")
includeBuild("appplugin") {
  dependencySubstitution {
    substitute(module("com.app.plugin:appplugin")).using(project(":"))
  }
}

