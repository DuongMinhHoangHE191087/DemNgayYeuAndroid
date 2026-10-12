package com.app.plugin.brain.policy

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionEngine
import com.app.plugin.brain.GuardrailEngine
import com.app.plugin.license.LicenseGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Modifier

/**
 * 2.4.0 CARRY-POL (Hợp đồng D, D3-240; minors-230 #28, #12, #24): `/policy` chỉ chạy khi `configure { brainEnabled }` + player
 * trong rollout, tối đa một lượt Integrity mỗi 24 h mỗi cài đặt (prefs `policy_last_attempt_ms`, ghi trước lượt mạng), version
 * server lọc trước khi log, trường `GuardrailEngine` (ghi off-main lúc áp policy) volatile.
 */
internal class PolicyBackoffTest : PolicyClientFixture() {
    private val valid = LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2)

    @Test fun `CP1 brainEnabled false du meta thi 0 mang, policy off brain dung 1 dong`() {
        AdsBrain.enabled = false
        install(); install()
        assertEquals(listOf("policy: off (brain)"), logs)
        assertEquals("0 transport", 0, calls.size)
        assertNull("0 ghi prefs back-off", policyPrefs[KEY])
    }

    @Test fun `CP1b ngoai rollout (rollout 0) thi 0 mang, policy off brain`() {
        AdsBrain.rolloutFraction = 0.0
        install()
        assertEquals(listOf("policy: off (brain)"), logs)
        assertEquals(0, calls.size)
    }

    /** D15-240: rollout của artifact đã áp (server/RC/store) không tự khoá client khỏi `/policy` — chỉ rollout phía client. */
    @Test fun `CP1c artifact da ap rollout 0 nhung client rollout 1 thi policy van chay`() {
        assertTrue(DecisionEngine.instance.applyPolicy(P_POLICY).applied)   // artifact rollout_fraction 0, như restore lúc cold start
        assertEquals(0.0, BrainPolicy.rolloutOverride() ?: -1.0, 0.0)
        install()   // D17-240: không đặt lại AdsBrain.rolloutFraction — apply artifact không còn ghi trường phía client
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
    }

    /** R-CARRY-POL I1 (RV1): artifact rollout 0 áp TRONG process, sau `configure`, trước verdict — vẫn không khoá `/policy`. */
    @Test fun `RV1 artifact ap rollout 0 trong process truoc verdict, client van rollout 1 thi policy van chay`() {
        AdsBrain.rolloutFraction = 1.0   // configure { brainRolloutFraction = 1.0 } (client)
        assertTrue(DecisionEngine.instance.applyPolicy(P_POLICY).applied)   // RC/store artifact rollout_fraction 0 áp sau configure
        install()
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
    }

    @Test fun `CP2 luot truoc chua du 24 h thi 0 mang, policy skip backoff so gio con lai`() {
        policyPrefs[KEY] = System.currentTimeMillis() - HOUR - 60_000L
        install()
        assertEquals("0 transport", 0, calls.size)
        assertEquals(listOf("policy: skip (backoff 23h)"), logs)
    }

    /** R-CARRY-POL m1: mốc ở tương lai (đồng hồ máy bị lùi) không chặn — chạy một lượt. */
    @Test fun `CP2c moc o tuong lai (dong ho lui) thi khong chan, chay 1 luot`() {
        policyPrefs[KEY] = System.currentTimeMillis() + 2 * HOUR
        install()
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
    }

    @Test fun `CP2b luot truoc tu 24 h tro len thi chay 1 luot va ghi lai moc`() {
        val old = System.currentTimeMillis() - 24 * HOUR - 60_000L
        policyPrefs[KEY] = old
        install()
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
        assertTrue("moc moi > moc cu", (policyPrefs[KEY] as Long) > old)
    }

    @Test fun `CP3 prefs ghi truoc luot mang, loi giua chung van tinh cho process sau`() {
        val seen = mutableListOf<Any?>()
        val t0 = System.currentTimeMillis()
        PolicyClient.transport = PolicyTransport { _, _, _ -> seen += policyPrefs[KEY]; throw IOException("down") }
        install()
        assertEquals(listOf("policy: error (IOException)"), logs)
        val at = seen.single() as? Long
        assertTrue("moc da ghi TRUOC POST: $seen", at != null && at >= t0 && at <= System.currentTimeMillis())
        arm(valid, freshInstall = false)   // process mới, cùng cài đặt
        install()
        assertEquals("process sau trong 24 h: 0 transport", 0, calls.size)
        assertEquals(listOf("policy: skip (backoff 24h)"), logs)
    }

    @Test fun `CP3b harness token gia khong goi Integrity nen khong bi back-off va khong ghi moc`() {
        val preset = System.currentTimeMillis() - HOUR
        policyPrefs[KEY] = preset
        PolicyClient.runForHarness(ctx, IntegrityTokenSource { _, _ -> "fake-ok" })
        PolicyClient.drainForTest()
        assertEquals(listOf("POST", "GET"), calls.map { it.method })
        assertEquals(listOf("policy: applied version=policy_harness_230 source=app"), logs)
        assertEquals("harness không ghi mốc (R-CARRY-POL m2)", preset, policyPrefs[KEY])
    }

    @Test fun `CP6 version server ngoai A-Za-z0-9 cham gach toi da 64 thi log loc`() {
        for (v in listOf("../../x", "a".repeat(65), "x\npolicy: applied version=evil")) {
            arm(valid)
            policyResp = PolicyResponse(200, """{"source":"app","policy":{"version":${com.google.gson.Gson().toJson(v)}}}""")
            install()
            assertEquals(v.take(20), listOf("policy: applied version=? source=app"), logs)
            assertEquals("?", PolicyClient.serverVersion)
        }
    }

    @Test fun `CP8 truong GuardrailEngine ghi off-main luc ap policy deu volatile`() {
        val fields = GuardrailEngine::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
        assertTrue("so truong var: ${fields.map { it.name }}", fields.size >= 14)
        assertEquals("truong khong volatile", emptyList<String>(), fields.filter { !Modifier.isVolatile(it.modifiers) }.map { it.name })
    }

    @Test fun `CP9 brain bat, thieu INTEGRITY_CLOUD_PROJECT thi dong off y nguyen, 0 ghi moc`() {
        meta = P_FULL_META - PolicyClient.META_PROJECT
        install()
        assertEquals(listOf("policy: off (com.app.plugin.INTEGRITY_CLOUD_PROJECT)"), logs)
        assertEquals(0, calls.size)
        assertNull(policyPrefs[KEY])
    }

    private companion object {
        const val KEY = "policy_last_attempt_ms"
        const val HOUR = 3_600_000L
    }
}
