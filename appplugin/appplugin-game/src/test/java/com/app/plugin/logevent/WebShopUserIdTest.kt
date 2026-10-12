package com.app.plugin.logevent

import com.app.plugin.MonetizationSdk
import com.app.plugin.brain.BrainStorage
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.iap.PurchaseRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 2.1.0 G1: test web shop của `LogEventServerHelperTest` (dùng `PurchaseRouter`) dời sang `appplugin-game`, nguyên thân. */
class WebShopUserIdTest {
    private val webShopConfig = """
        {"version": "routing_user_id",
         "web_shop": {"enabled": true, "url_template": "https://shop.example.com/checkout?product={product_id}&user={user_id}", "eligible_countries": ["US"]},
         "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]}
    """.trimIndent()

    @Before fun setUp() { PlayerIdSource.override = null; MonetizationSdk.resetForTest() }
    @After fun tearDown() { PlayerIdSource.override = null; MonetizationSdk.resetForTest(); PurchaseRouter.clear() }

    private fun configurePlayerId(id: String) {
        MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { playerId = id })
    }

    @Test
    fun `url web shop dung cung id voi user_id cua telemetry, khi co va khi khong configure`() {
        val defaultCountry = PurchaseRouter.countryProvider
        try {
            // Unit test không có quốc gia thật; userIdProvider giữ mặc định (đọc LogEventServerHelper).
            PurchaseRouter.countryProvider = { "US" }
            PurchaseRouter.apply(webShopConfig)

            val sdkId = BrainStorage.playerId()
            assertEquals(
                "https://shop.example.com/checkout?product=coin_pack_1&user=$sdkId",
                PurchaseRouter.checkoutUrlFor("coin_pack_1")
            )

            configurePlayerId("acct 7&x")
            assertEquals("acct 7&x", LogEventServerHelper.generateGlobalPropertiesData().userId)
            assertEquals(
                "https://shop.example.com/checkout?product=coin_pack_1&user=acct+7%26x",
                PurchaseRouter.checkoutUrlFor("coin_pack_1")
            )
        } finally {
            PurchaseRouter.countryProvider = defaultCountry
        }
    }
}
