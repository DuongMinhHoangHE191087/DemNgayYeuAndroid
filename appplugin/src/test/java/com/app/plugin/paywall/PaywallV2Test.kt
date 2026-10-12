package com.app.plugin.paywall

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.iap.PaywallSession
import com.app.plugin.logevent.IapEventLog
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.logevent.ServerEventCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Paywall schema v2 (1.8.0 Task 9, plan "Hợp đồng D"; D6, D14): `schema_version`, holdout riêng mặc định 0 rút trên
 * `user_id` SDK trong không gian `paywall_holdout_v2`, `session_cap` cho placement `proactive`, `paywall_skip` và
 * `paywall_session_id`. W3, W8, W10 đỏ trên mã cũ (`t180-t9-red.log`); còn lại đặc tả (mã cũ bỏ qua `holdout`, không có
 * `session_cap`, không có parser v2).
 */
class PaywallV2Test {
    @get:Rule val license = LicenseTestRule()

    private val v1 = """{"version":"pw_v1","placements":{"shop":{"variants":[{"id":"a","weight":50,"products":["x"]},{"id":"b","weight":50,"products":["y"]}]}}}"""

    private fun v2(fraction: Double = 0.0, cap: Int = 1) = """
        {"schema_version":2,"version":"pw_v2","holdout":{"fraction":$fraction},"session_cap":$cap,"placements":{
          "shop":{"variants":[{"id":"a","weight":100,"products":["coin_small","coin_big"],"highlight":"coin_big"}]},
          "offer":{"decision_point":"CURRENCY_LOW","proactive":true,"variants":[{"id":"o","weight":100,"products":["vip"]}]}}}
    """.trimIndent()

    private lateinit var userIdSource: () -> String

    @Before
    fun setUp() {
        userIdSource = LogEventServerHelper.userIdSource
        PaywallManager.clear()
        BrainPolicy.clear()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
    }

    @After
    fun tearDown() {
        LogEventServerHelper.userIdSource = userIdSource
        PaywallManager.clear()
        BrainPolicy.clear()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
    }

    @Test
    fun `W1 v2 nap du truong schema_version holdout session_cap proactive`() {
        val r = PaywallManager.apply(v2())
        assertTrue(r.report, r.applied)
        assertTrue("bao cao ghi schema: ${r.report}", r.report.contains("schema v2"))
        assertEquals("pw_v2", PaywallManager.activeVersion())
        val pw = PaywallManager.paywallFor("shop", "p1")!!
        assertEquals(listOf("coin_small", "coin_big"), pw.products)
        assertEquals("coin_big", pw.highlight)
        assertEquals("pw_v2", pw.version)
        assertNotNull("placement proactive van tra paywall khi chua cham cap", PaywallManager.paywallFor("offer", "p1"))
    }

    @Test
    fun `W2 schema_version la hoac holdout sai thi tu choi va giu cau hinh dang chay`() {
        assertTrue(PaywallManager.apply(v1).applied)
        for (bad in listOf(v2().replace("\"schema_version\":2", "\"schema_version\":3"), v2().replace("\"schema_version\":2", "\"schema_version\":\"2\""),
                v2(fraction = 1.5), v2().replace("\"session_cap\":1", "\"session_cap\":-1"))) {
            val r = PaywallManager.apply(bad)
            assertFalse("phai tu choi: $bad", r.applied)
            assertTrue(r.report, r.report.contains("giữ nguyên 'pw_v1'"))
        }
        assertEquals("pw_v1", PaywallManager.activeVersion())
        assertNotNull("cau hinh cu phai con", PaywallManager.paywallFor("shop", "p1"))
    }

    @Test
    fun `W3 v1 JSON holdout 0 khong ai bi null`() {
        PaywallManager.apply(v1)
        // user_id SDK đổi theo từng user (holdout v2 rút trên user_id); playerId cũng đổi (mã cũ rút trên playerId).
        val nulls = (1..20000).count {
            LogEventServerHelper.userIdSource = { "user$it" }
            PaywallManager.paywallFor("shop", "user$it") == null
        }
        assertEquals("paywallFor null cho $nulls/20000 user", 0, nulls)
    }

    @Test
    fun `W4 holdout 0,1 rut tren user_id SDK trong paywall_holdout_v2 doc lap brain_holdout_v1`() {
        HoldoutManager.holdoutFraction = 0.1
        PaywallManager.apply(v2(fraction = 0.1))
        val users = (1..10000).map { "user$it" }
        // playerId app truyền cố định: holdout phải theo user_id của SDK, không theo playerId.
        val held = users.filter { u ->
            LogEventServerHelper.userIdSource = { u }
            PaywallManager.paywallFor("shop", "app_player") == null
        }
        assertEquals("holdout = bucketWith(paywall_holdout_v2, user_id) < 1000",
            users.filter { HoldoutManager.bucketWith("paywall_holdout_v2", it) < 1000 }, held)
        val brainHeld = users.filter { HoldoutManager.isHoldout(it) }
        val rate = brainHeld.count { it in held.toSet() }.toDouble() / brainHeld.size
        assertTrue("ti le holdout paywall trong nhom brain holdout = $rate (phai doc lap, ~0,1)", rate in 0.06..0.14)
    }

    @Test
    fun `W5 v1 bo qua holdout session_cap proactive`() {
        PaywallManager.apply("""{"version":"v1x","holdout":{"fraction":1.0},"session_cap":0,"placements":{"offer":{"proactive":true,"variants":[{"id":"o","weight":100,"products":["vip"]}]}}}""")
        repeat(3) {
            val pw = PaywallManager.paywallFor("offer", "p$it")
            assertNotNull("v1: khong holdout, khong cap (lan ${it + 1})", pw)
            PaywallManager.onPaywallShown(pw!!)
        }
        assertEquals(0, PaywallManager.holdoutSkipCount())
    }

    @Test
    fun `W6 session_cap 1 placement proactive thu hai bi chan voi paywall_skip session_cap`() {
        PaywallManager.apply(v2(cap = 1))
        ServerEventCapture().use { s ->
            s.autoAck = true
            PaywallManager.onPaywallShown(PaywallManager.paywallFor("offer", "p1")!!)
            assertNull("cham session_cap thi null", PaywallManager.paywallFor("offer", "p1"))
            val e = s.await("paywall_skip", 1)
            assertEquals("paywall_skip=$e", listOf("offer" to "session_cap"), e.map { it.get("placement").asString to it.get("reason").asString })
        }
    }

    @Test
    fun `W7 session_cap khong dem va khong chan placement khong proactive`() {
        PaywallManager.apply(v2(cap = 1))
        repeat(3) { PaywallManager.onPaywallShown(PaywallManager.paywallFor("shop", "p1")!!) }
        val offer = PaywallManager.paywallFor("offer", "p1")
        assertNotNull("paywall nguoi dung mo khong duoc tinh vao cap", offer)
        PaywallManager.onPaywallShown(offer!!)
        assertNull(PaywallManager.paywallFor("offer", "p1"))
        assertNotNull("placement khong proactive khong bao gio bi cap", PaywallManager.paywallFor("shop", "p1"))
    }

    @Test
    fun `W8 paywall_impression co paywall_session_id va schema_version_cfg`() {
        PaywallManager.apply(v1)
        ServerEventCapture().use { s ->
            PaywallManager.onPaywallShown(PaywallManager.paywallFor("shop", "u1")!!)
            val e = s.await("paywall_impression", 1).single()
            assertTrue("paywall_session_id: $e", e.has("paywall_session_id") && e.get("paywall_session_id").asString.isNotEmpty())
            assertEquals("schema_version_cfg: $e", 1, e.get("schema_version_cfg").asInt)
        }
    }

    @Test
    fun `W9 onPaywallShown mo PaywallSession, paywall_choice cung paywall_session_id voi impression`() {
        PaywallManager.apply(v2())
        ServerEventCapture().use { s ->
            s.autoAck = true
            val pw = PaywallManager.paywallFor("offer", "u1")!!
            PaywallManager.onPaywallShown(pw)
            val session = PaywallSession.current()!!
            assertEquals("offer" to "o", session.placement to session.variantId)
            assertTrue(IapEventLog.onPurchaseLaunched("vip"))
            val impression = s.await("paywall_impression", 1).single()
            val choice = s.await("paywall_choice", 1).single()
            assertEquals(2, impression.get("schema_version_cfg").asInt)
            assertEquals(session.sessionId, impression.get("paywall_session_id").asString)
            assertEquals(session.sessionId, choice.get("paywall_session_id").asString)
            assertEquals("vip", choice.get("product_id").asString)
        }
    }

    @Test
    fun `W10 paywall_skip reason no_config va brain`() {
        ServerEventCapture().use { s ->
            s.autoAck = true
            assertNull(PaywallManager.paywallFor("khong_co", "u1"))
            PaywallManager.apply(v2())
            AdsBrain.enabled = true
            AdsBrain.onSessionStart("p_w10_${System.nanoTime()}")
            repeat(3) { AdsBrain.onPurchase(4.99) }   // chạm IAP_VELOCITY_BREAKER: brain từ chối chào bán
            assertNull(PaywallManager.paywallFor("shop", "u1"))
            val e = s.await("paywall_skip", 2).map { it.get("placement").asString to it.get("reason").asString }.sortedBy { it.second }
            assertEquals("paywall_skip=$e", listOf("shop" to "brain", "khong_co" to "no_config"), e)
        }
    }

    @Test
    fun `W11 session_cap dem luc hien, khong luc hoi`() {
        // QA-PRE M4 (D25): gọi paywallFor mà chưa hiện thì chưa tốn cap; chỉ onPaywallShown mới đếm.
        PaywallManager.apply(v2(cap = 1))
        ServerEventCapture().use { s ->
            s.autoAck = true
            val first = PaywallManager.paywallFor("offer", "p1")
            assertNotNull("hoi lan 1 chua hien", first)
            assertNotNull("hoi lan 2 van chua hien: chua cham cap", PaywallManager.paywallFor("offer", "p1"))
            PaywallManager.onPaywallShown(first!!)
            assertNull("da hien 1 lan: cham session_cap", PaywallManager.paywallFor("offer", "p1"))
            val e = s.await("paywall_skip", 1)
            assertEquals("paywall_skip=$e", listOf("offer" to "session_cap"), e.map { it.get("placement").asString to it.get("reason").asString })
        }
    }

    @Test
    fun `W12 holdout chi phat paywall_holdout_skip co paywall_session_id, khong phat paywall_skip`() {
        // R-9 m1: nhánh holdout không được đếm hai lần (mẫu số phễu).
        PaywallManager.apply(v2(fraction = 1.0))
        ServerEventCapture().use { s ->
            s.autoAck = true
            assertNull(PaywallManager.paywallFor("shop", "p1"))
            val held = s.await("paywall_holdout_skip", 1)
            assertEquals("paywall_holdout_skip=$held", 1, held.size)
            assertTrue("paywall_session_id: ${held.single()}", held.single().get("paywall_session_id").asString.isNotEmpty())
            assertEquals("paywall_skip trong nhanh holdout", emptyList<Any>(), s.await("paywall_skip", 0))
        }
    }

    @Test
    fun `W13 user_id SDK trong thi holdout rut tren playerId`() {
        // R-9 m2 (D25): user_id rỗng không được dồn mọi người vào một bucket.
        LogEventServerHelper.userIdSource = { "" }
        PaywallManager.apply(v2(fraction = 0.1))
        val players = (1..10000).map { "p$it" }
        val held = players.filter { PaywallManager.paywallFor("shop", it) == null }
        assertEquals("holdout = bucketWith(paywall_holdout_v2, playerId) < 1000",
            players.filter { HoldoutManager.bucketWith("paywall_holdout_v2", it) < 1000 }, held)
    }
}
