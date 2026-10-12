package com.app.plugin.firbase

import com.app.plugin.MonetizationSdk
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.iap.SubState
import com.app.plugin.iap.SubscriptionLifecycle
import com.app.plugin.iap.SubscriptionServerState
import com.app.plugin.logevent.LogEventServerHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 2.1.0 G1 (hợp đồng B): [GameHost] là SPI cho `appplugin-game` thay cho 3 lời gọi `internal` cũ — chỉ ủy quyền, cùng
 * nguồn với core (`PurchaseRouter` routing theo đúng country/user_id của event; `RecurringGrants` sở hữu theo nhãn client).
 */
class GameHostTest {
    @Before fun setUp() { PlayerIdSource.override = null; MonetizationSdk.resetForTest(); SubscriptionServerState.clear() }
    @After fun tearDown() {
        LogEventServerHelper.onCountryKnown("")
        PlayerIdSource.override = null
        MonetizationSdk.resetForTest()
        SubscriptionServerState.clear()
    }

    @Test
    fun `SPI1 GameHost uy quyen country, userId, subscriptionState cua core`() {
        LogEventServerHelper.onCountryKnown("US")
        assertEquals("US", LogEventServerHelper.currentCountry())
        assertEquals(LogEventServerHelper.currentCountry(), GameHost.country())

        MonetizationSdk.applyForTest(MonetizationSdk.Config().apply { playerId = "acct-spi1" })
        assertEquals("acct-spi1", GameHost.userId())
        assertEquals(LogEventServerHelper.currentUserId(), GameHost.userId())

        assertEquals(SubState.NONE, GameHost.subscriptionState("vip_spi1"))
        SubscriptionServerState.onClientObserved("vip_spi1", SubState.ACTIVE)
        assertEquals(SubscriptionLifecycle.clientStateFor("vip_spi1"), GameHost.subscriptionState("vip_spi1"))
        assertEquals(SubState.ACTIVE, GameHost.subscriptionState("vip_spi1"))
    }
}
