package com.app.plugin.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostAdScreenCooldownTest {
    @Test
    fun `disabled by default so ad cadence is unchanged`() {
        val cooldown = PostAdScreenCooldown()

        assertFalse(cooldown.onAutoAdShown())
        assertFalse(cooldown.isActive)
        assertFalse(cooldown.onScreenChange())
    }

    @Test
    fun `an ad blocks the next auto ad until the user moves through the required screens`() {
        val cooldown = PostAdScreenCooldown(requiredScreens = 2)

        assertTrue(cooldown.onAutoAdShown())
        assertTrue(cooldown.isActive)

        assertFalse(cooldown.onScreenChange())
        assertTrue(cooldown.isActive)

        assertTrue(cooldown.onScreenChange())
        assertFalse(cooldown.isActive)
    }

    @Test
    fun `screen changes after the cooldown cleared report nothing`() {
        val cooldown = PostAdScreenCooldown(requiredScreens = 1)
        cooldown.onAutoAdShown()
        cooldown.onScreenChange()

        assertFalse(cooldown.onScreenChange())
    }

    @Test
    fun `a new ad restarts the count`() {
        val cooldown = PostAdScreenCooldown(requiredScreens = 2)
        cooldown.onAutoAdShown()
        cooldown.onScreenChange()

        cooldown.onAutoAdShown()
        cooldown.onScreenChange()

        assertTrue(cooldown.isActive)
    }

    @Test
    fun `turning the feature off lifts an active cooldown`() {
        val cooldown = PostAdScreenCooldown(requiredScreens = 2)
        cooldown.onAutoAdShown()

        cooldown.requiredScreens = 0

        assertFalse(cooldown.isActive)
    }

    @Test
    fun `negative screen counts are treated as off`() {
        val cooldown = PostAdScreenCooldown(requiredScreens = -3)

        assertFalse(cooldown.onAutoAdShown())
    }
}
