// -Pcombo=1 legacy (appplugin + game + max, như 2.3.0); 2 nextgen + game + luật thay thế (mục 17 INTEGRATION.md); 3 như 2 NHƯNG không có luật thay thế (phải trùng).
plugins { id("com.android.library") }
val ver = providers.gradleProperty("ver").get()
val combo = providers.gradleProperty("combo").get()
android {
    namespace = "consumer.check"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
if (combo != "1") {
    configurations.all {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
        if (combo == "2") resolutionStrategy.dependencySubstitution {
            substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:$ver"))
        }
    }
}
dependencies {
    if (combo == "1") {
        implementation("com.app.plugin:appplugin:$ver")
        implementation("com.app.plugin:appplugin-max:$ver")
    } else {
        implementation("com.app.plugin:appplugin-nextgen:$ver")
    }
    implementation("com.app.plugin:appplugin-game:$ver")
}
