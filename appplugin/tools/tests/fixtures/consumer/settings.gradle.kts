// 2.4.0 NGPUB (D5-240): dự án tiêu thụ tối thiểu, chỉ để chạy `dependencies` trên repo cục bộ build/repo (tools/consumer_check.sh).
// Phiên bản AGP = gốc (settings.gradle.kts của SDK); repo bên thứ ba như gốc để Gradle --offline dùng lại cache metadata.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { id("com.android.library") version "9.2.1" }
}
val localRepo = providers.gradleProperty("repo").get()
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(localRepo); content { includeGroup("com.app.plugin") } }
        google()
        mavenCentral()
        maven { url = uri("https://artifact.bytedance.com/repository/pangle"); content { includeGroup("com.pangle.global") } }
        maven { url = uri("https://artifacts.applovin.com/android"); content { includeGroupByRegex("com\\.applovin.*") } }
        maven { url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/"); content { includeGroup("com.chartboost"); includeGroup("com.iab.omid.library") } }
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea"); content { includeGroup("com.mbridge.msdk.oversea") } }
    }
}
rootProject.name = "consumer"
