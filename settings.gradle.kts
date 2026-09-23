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
      url = uri("https://artifact.bytedance.com/repository/pangle")
      content { includeGroup("com.pangle.global") }
    }
    maven {
      url = uri("https://artifacts.applovin.com/android")
      content { includeGroupByRegex("com\\.applovin.*") }
    }
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
include(":appplugin")

