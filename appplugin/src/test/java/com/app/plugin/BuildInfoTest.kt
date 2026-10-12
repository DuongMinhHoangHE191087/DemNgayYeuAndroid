package com.app.plugin

import com.app.plugin.ads.SdkHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 SC1 (§G): dấu vết dựng `appplugin/<phiên bản>/<git short sha>` và trường `build` của `sdk_health`. */
class BuildInfoTest {
    @Test fun `W1u BuildInfo ID dang appplugin phien-ban sha, khop SDK_VERSION, sdk_health mang build`() {
        assertTrue("ID=${BuildInfo.ID}", BuildInfo.ID.matches(Regex("^appplugin/\\d+\\.\\d+\\.\\d+/[0-9a-f]{7,}$")))
        assertTrue(BuildInfo.ID.startsWith("appplugin/${BuildConfig.SDK_VERSION}/"))
        assertEquals(BuildInfo.ID, SdkHealth.withBuild(mapOf("kill" to "none"))["build"])
    }
}
