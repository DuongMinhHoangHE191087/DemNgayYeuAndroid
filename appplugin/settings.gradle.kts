pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.2.1"
        id("com.android.application") version "9.2.1"
        id("com.google.gms.google-services") version "4.4.2"
        id("com.google.firebase.crashlytics") version "3.0.3"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

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

rootProject.name = "appplugin"
include(":app")
include(":appplugin-game")
include(":appplugin-max")
include(":appplugin-nextgen")
