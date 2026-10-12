// 2.4.0 NG0 (Hợp đồng A, Q1 = module con): artifact com.app.plugin:appplugin-nextgen (GMA Next-Gen) dùng CHUNG nguồn với gốc.
// Nguồn = ../src/main/{java,res} + ../src/test/java chép vào build/generated/appplugin-shared/{java,res,test} (task SharedSrc),
// "override thắng": file cùng đường dẫn tương đối ở appplugin-nextgen/src/... thay bản gốc (liệt kê ở OVERRIDES.txt, NgOverridesTest).
// Cấu hình Android = gốc (build.gradle.kts:32-88), cùng sdkVersion; artifactId = tên project (gradle/publish.gradle).
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("com.android.library")
    id("maven-publish")
}

val sdkVersion = rootProject.extra["sdkVersion"] as String
kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("com.app.plugin.AppPluginInternalApi")
        // D7-240: tên mangle thành viên internal `…$appplugin` như gốc → 0 override thì dump giống hệt api/appplugin.api (NX2).
        moduleName.set("appplugin")
    }
}

android {
    // Bắt buộc như gốc: mã chung dùng com.app.plugin.R / com.app.plugin.BuildConfig.
    namespace = "com.app.plugin"
    compileSdk = 36
    resourcePrefix = "appplugin_"
    defaultConfig {
        multiDexEnabled = true
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
    }
    // Như gốc: không minify AAR thư viện; R8 của app rút gọn với consumer-rules.pro.
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures { buildConfig = true }
    lint { error += "ResourceName" }
    testOptions { unitTests.isReturnDefaultValues = true }
    publishing { singleVariant("release") }
}

apply(from = "../gradle/build-sha.gradle")

// Chép cây nguồn chung, bỏ mọi đường dẫn tương đối có trong thư mục override và trong danh sách loại (phần trước '#').
abstract class SharedSrc : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val shared: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val overrides: ConfigurableFileCollection
    @get:Input abstract val excluded: ListProperty<String>
    @get:OutputDirectory abstract val out: DirectoryProperty

    @TaskAction fun copy() {
        val skip = excluded.get().toMutableSet()
        overrides.asFileTree.visit { if (!isDirectory) skip += relativePath.pathString }
        val src = shared.get().asFile
        val dst = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        src.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(src).invariantSeparatorsPath
            if (rel !in skip) f.copyTo(File(dst, rel))
        }
    }
}

val testExclude = providers.fileContents(layout.projectDirectory.file("shared-test-exclude.txt")).asText
    .map { t -> t.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() } }
fun sharedTask(name: String, dir: String, exclude: Provider<List<String>>) = tasks.register<SharedSrc>(name) {
    shared.set(rootProject.layout.projectDirectory.dir(dir))
    overrides.from(layout.projectDirectory.dir(dir))
    excluded.set(exclude)
    out.set(layout.buildDirectory.dir("generated/appplugin-shared/${name.removePrefix("shared").lowercase()}"))
}
val sharedJava = sharedTask("sharedJava", "src/main/java", provider { emptyList() })
val sharedRes = sharedTask("sharedRes", "src/main/res", provider { emptyList() })
val sharedTest = sharedTask("sharedTest", "src/test/java", testExclude)
androidComponents.onVariants { v ->
    v.sources.kotlin?.addGeneratedSourceDirectory(sharedJava, SharedSrc::out)
    v.sources.res?.addGeneratedSourceDirectory(sharedRes, SharedSrc::out)
    // AGP 9.2.1: nguồn test sinh qua hostTests[UNIT_TEST_TYPE] (v.unitTest không biên dịch trong script; D13-240).
    (v as? HasHostTests)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.sources?.kotlin?.addGeneratedSourceDirectory(sharedTest, SharedSrc::out)
}

// NgOverridesTest/NgLegacyCensusTest đọc file (không qua classpath): khai input để Gradle không coi test là up-to-date khi
// OVERRIDES.txt, legacy-remaining.txt, override hay nguồn gốc đổi.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(layout.projectDirectory) { exclude("build/**") }).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("ngModuleTree")
    inputs.files(sharedJava, rootProject.layout.projectDirectory.dir("src/main"), rootProject.layout.projectDirectory.dir("src/test/java"))
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("ngSharedTree")
}

// Phụ thuộc = gốc (build.gradle.kts:93-192) − play-services-ads − facebook:6.21.0.4 + Next-Gen (Hợp đồng A).
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("com.facebook.android:facebook-core:17.0.2")
    testImplementation("com.google.ads.mediation:pangle:8.1.0.3.1")
    testImplementation("com.bigossp:admob-mediation:5.7.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    api("androidx.multidex:multidex:2.0.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("com.appsflyer:af-android-sdk:6.16.2")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.android.billingclient:billing:9.0.0")
    implementation("com.google.android.play:integrity:1.6.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // GMA Next-Gen 1.5.0 + adapter Meta 6.22.0.1 (kéo play-services-ads:25.4.0 → exclusion trong POM, NX6). NG5b: hết chuyển
    // tiếp (0 compileOnly/testImplementation Legacy); play-services-ads(-lite) loại trên cấu hình consumer-visible (dưới, D11).
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")
    implementation("com.google.ads.mediation:facebook:6.22.0.1") { exclude(group = "com.google.android.gms", module = "play-services-ads") }
    // R-NG6 (Opus delta): app biên dịch trên nextgen mà không ghim thì nhận coroutines 1.6.4 qua androidx lifecycle; ghim sàn 1.9.0 (POM).
    constraints { api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") }

    compileOnly("com.google.ads.mediation:pangle:8.1.0.3.1")
    compileOnly("com.google.ads.mediation:applovin:13.6.2.1")
    compileOnly("com.google.ads.mediation:vungle:7.7.4.2")
    compileOnly("com.google.ads.mediation:mintegral:17.1.61.1")
    compileOnly("com.google.ads.mediation:unity:4.19.0.0")
    implementation("androidx.annotation:annotation:1.8.1")
    implementation("com.facebook.android:audience-network-sdk:6.21.0")
    compileOnly("com.facebook.android:facebook-core:17.0.2")
    for (coord in rootProject.extra["applovinMaxCoords"] as Iterable<*>) { compileOnly(coord as String); testImplementation(coord) }
}

// 2.4.0 NG5b (Hợp đồng A "NG5b cắt", D11-240, [S4]): Next-Gen không dùng chung với GMA Legacy → loại play-services-ads và -lite
// CHỈ trên cấu hình consumer-visible (api, implementation, runtimeOnly), không trên testImplementation (pangle/bigossp/MAX ở test).
for (name in listOf("api", "implementation", "runtimeOnly")) {
    configurations.named(name) {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    }
}

apply(from = "../gradle/publish.gradle")
