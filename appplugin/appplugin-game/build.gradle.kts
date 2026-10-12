// 2.1.0 G1 (D16, Q1 = SPI): mã game (5 lớp + GameModule) sống ở đây; gọi core chỉ qua API public + SPI @AppPluginInternalApi.
plugins {
    id("com.android.library")
    id("maven-publish")
}

val sdkVersion = rootProject.extra["sdkVersion"] as String
kotlin {
    explicitApi()
    // SPI giữa các artifact appplugin (RcSectionRegistry.register, BrainSignalListener, GameHost): thiếu opt-in = lỗi biên dịch.
    compilerOptions { optIn.add("com.app.plugin.AppPluginInternalApi") }
}
// 2.1.0 G1 (M32): compileSdk/minSdk/JVM/consumer rules/singleVariant chung ở gradle/module-defaults.gradle.
apply(from = "../gradle/module-defaults.gradle")
android {
    namespace = "com.app.plugin.game"
}
dependencies {
    api(project(":"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    // D9: tập `implementation` của gốc mà classpath test cần (FirebaseRemoteConfig, Context, Retrofit/OkHttp của
    // ServerEventCapture, ...) — `implementation` của gốc không lộ sang module này. Cùng bản với gốc, resolve offline.
    testImplementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    testImplementation("com.google.firebase:firebase-config")
    testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.androidx.appcompat)
    testImplementation(libs.material)
    testImplementation("com.squareup.retrofit2:retrofit:2.9.0")
    testImplementation("com.squareup.retrofit2:converter-gson:2.9.0")
    testImplementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    testImplementation("androidx.lifecycle:lifecycle-process:2.8.3")
    testImplementation("com.google.android.gms:play-services-ads:25.4.0")
    testImplementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
apply(from = "../gradle/publish.gradle")

// D9: friend-path CHỈ trên biên dịch unit-test (test gọi AdsBrain/HoldoutManager/... internal của gốc). Đường dẫn = classes.jar
// debug của gốc → chỉ testDebugUnitTest được phủ. Biên dịch main/release không có cờ: mã game vẫn chỉ thấy API public + SPI.
val coreDebugClassesJar = rootProject.layout.buildDirectory.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("UnitTest")) {
        compilerOptions.freeCompilerArgs.add(coreDebugClassesJar.map { "-Xfriend-paths=${it.asFile.absolutePath}" })
    }
}
