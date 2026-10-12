package com.app.plugin.until

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.9.0 Task 1 (D2, B14): 5 adapter GMA tuỳ chọn là `compileOnly` ở SDK (không vào classpath chạy của app nào nữa);
 * demo khai lại đúng toạ độ đó cho flavor `game` (game không đổi), và pangle có trên classpath test (B11).
 */
class OptionalAdaptersCompileOnlyTest {
    private val partners = listOf("pangle", "mintegral", "unity", "vungle", "applovin")

    // B14
    @Test
    fun `5 adapter tuy chon la compileOnly o SDK va cung toa do o demo`() {
        val sdk = File("build.gradle.kts").readText()
        val app = File("app/build.gradle.kts").readText()
        for (p in partners) {
            val m = Regex("""(?m)^\s*compileOnly\("com\.google\.ads\.mediation:$p:([^"]+)"\)""").find(sdk)
            assertNotNull("build.gradle.kts phải khai compileOnly cho adapter $p", m)
            assertFalse(
                "build.gradle.kts còn implementation cho adapter $p",
                Regex("""(?m)^\s*implementation\("com\.google\.ads\.mediation:$p:""").containsMatchIn(sdk),
            )
            val coordinate = "com.google.ads.mediation:$p:${m!!.groupValues[1]}"
            assertTrue("app/build.gradle.kts phải khai $coordinate (game không đổi)", app.contains("\"$coordinate\""))
        }
        assertTrue(
            "pangle phải có trên classpath test (B11)",
            Regex("""(?m)^\s*testImplementation\("com\.google\.ads\.mediation:pangle:""").containsMatchIn(sdk),
        )
    }
}
