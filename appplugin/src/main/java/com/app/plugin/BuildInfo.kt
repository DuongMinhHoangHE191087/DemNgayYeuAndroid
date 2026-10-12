package com.app.plugin

/**
 * 2.1.0 SC1 (§G): dấu vết dựng `appplugin/<phiên bản>/<git short sha>` (`BuildSha.ID`: nguồn Kotlin sinh lúc build bởi `gradle/build-sha.gradle`, không qua BuildConfig).
 * `consumer-rules.pro` giữ lớp và trường để chuỗi này còn nguyên trong dex của app sau R8; `sdk_health` gửi nó ở trường `build`.
 */
internal object BuildInfo {
    @JvmField val ID: String = BuildSha.ID
}
