package com.app.plugin.ads

import android.os.Bundle
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.EventNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify

/**
 * Hợp đồng event `sdk_health` (Task 16, addendum A7; khoá ở Task 14b cùng `EventNameContractTest`): tên event
 * và param snake_case, tên ≤ 40 ký tự, giá trị chuỗi ≤ 100 ký tự (giới hạn của Firebase Analytics); `kill` là
 * chuỗi phân tách dấu phẩy.
 */
class SdkHealthTest {
    private val snakeCase40 = Regex("[a-z][a-z0-9_]{0,39}")

    @Test fun `ten event va param dung hop dong, du sau param`() {
        val p = SdkHealth.params(
            sdkVersion = "1.4.0",
            consentTier = ConsentTier.PARTIAL,
            gdprApplies = null,
            networks = SdkHealth.networks(maxAvailable = true, meta = true),
            sinceRequestMs = 42L,
            kill = "none",
        )

        assertTrue(SdkHealth.EVENT.matches(snakeCase40))
        assertEquals(
            listOf("sdk_version", "consent_tier", "consent_gdpr", "networks", "since_request_ms", "kill"),
            p.keys.toList()
        )
        p.keys.forEach { assertTrue("param '$it'", it.matches(snakeCase40)) }
        p.values.filterIsInstance<String>().forEach { assertTrue("giá trị '$it'", it.length <= 100) }
        assertEquals("1.4.0", p["sdk_version"])
        assertEquals("PARTIAL", p["consent_tier"])
        assertEquals("unknown", p["consent_gdpr"])   // IABTCF_gdprApplies vắng
        assertEquals("admob,meta,max", p["networks"])
        assertEquals(42L, p["since_request_ms"])
        assertEquals("none", p["kill"])
    }

    @Test fun `ten event nam trong EventNames`() {
        assertEquals(EventNames.SDK_HEALTH, SdkHealth.EVENT)
    }

    /**
     * Dạng của `kill` (Task 14b): `none`, `version`, hoặc các module bị tắt theo thứ tự `ads,iap,logevent`, nối
     * bằng dấu phẩy, không khoảng trắng (ví dụ `ads,iap`). Dashboard tách theo dấu phẩy; đổi dạng là gãy báo cáo.
     */
    @Test fun `kill la chuoi module phan tach dau phay, moi to hop`() {
        val format = Regex("^(none|version|(ads|iap|logevent)(,(ads|iap|logevent))*)$")
        val flags = listOf(false, true)
        val labels = flags.flatMap { ads ->
            flags.flatMap { iap ->
                flags.flatMap { logevent ->
                    listOf("", "9.9.9").map { below -> SdkKillSwitch(ads, iap, logevent, below).label("1.4.0") }
                }
            }
        }
        labels.forEach { label ->
            val kill = SdkHealth.params("1.4.0", ConsentTier.GRANTED, false, "admob,meta,max", 0L, label)["kill"]
            assertTrue("kill '$kill'", kill is String && kill.matches(format) && kill.length <= 100)
        }
        assertEquals("ads,iap", SdkKillSwitch(ads = true, iap = true).label("1.4.0"))
        assertEquals(setOf("none", "version", "ads", "iap", "logevent", "ads,iap", "ads,logevent", "iap,logevent", "ads,iap,logevent"), labels.toSet())
    }

    @Test fun `gdprApplies co gia tri thi consent_gdpr la true hoac false, networks khong co MAX`() {
        fun gdpr(v: Boolean?) = SdkHealth.params("1.4.0", ConsentTier.GRANTED, v, "x", 0L, "none")["consent_gdpr"]
        assertEquals("true", gdpr(true))
        assertEquals("false", gdpr(false))
        assertEquals("admob,meta", SdkHealth.networks(maxAvailable = false, meta = true))
    }

    /** P1 (DOC/66): Meta Audience Network bị bỏ qua vì consent (DENIED trong vùng GDPR) thì không nằm trong `networks`. */
    @Test fun `Meta bi bo qua vi consent thi networks khong co meta`() {
        assertEquals("admob,meta,max", SdkHealth.networks(maxAvailable = true, meta = true))
        assertEquals("admob,max", SdkHealth.networks(maxAvailable = true, meta = false))
        assertEquals("admob,meta", SdkHealth.networks(maxAvailable = false, meta = true))
        assertEquals("admob", SdkHealth.networks(maxAvailable = false, meta = false))
    }

    @Test fun `Bundle mang dung kieu - so la Long, con lai la chuoi`() {
        val p = SdkHealth.params("1.4.0", ConsentTier.DENIED, true, SdkHealth.NETWORKS_KILLED, 250L, "ads")
        mockConstruction(Bundle::class.java).use { bundles ->
            SdkHealth.toBundle(p)

            val b = bundles.constructed().single()
            verify(b).putString("sdk_version", "1.4.0")
            verify(b).putString("consent_tier", "DENIED")
            verify(b).putString("consent_gdpr", "true")
            verify(b).putString("networks", "killed")
            verify(b).putLong("since_request_ms", 250L)
            verify(b).putString("kill", "ads")
        }
    }
}
