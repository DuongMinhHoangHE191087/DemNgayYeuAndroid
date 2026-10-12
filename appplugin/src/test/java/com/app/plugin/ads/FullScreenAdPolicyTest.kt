package com.app.plugin.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenAdPolicyTest {
    @Test
    fun `ad click suppresses automatic fullscreen until cooldown expires`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        policy.recordAdClick(nowMillis = 10_000L)

        assertTrue(policy.shouldSuppressAutomaticFullscreen(nowMillis = 14_999L))
        assertFalse(policy.shouldSuppressAutomaticFullscreen(nowMillis = 15_000L))
    }

    @Test
    fun `older click cannot shorten an active cooldown`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        policy.recordAdClick(nowMillis = 20_000L)
        policy.recordAdClick(nowMillis = 18_000L)

        assertTrue(policy.shouldSuppressAutomaticFullscreen(nowMillis = 24_999L))
    }

    @Test
    fun `interstitial dismissal chains full2 when remote config enables interstitial chain`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        assertTrue(
            policy.shouldChainFull2AfterInterstitial(
                full2FlagShow = 1,
                originPlacement = PlacementDefault.PLFullDefault,
            )
        )
        assertTrue(
            policy.shouldChainFull2AfterInterstitial(
                full2FlagShow = 3,
                originPlacement = PlacementDefault.PLFullDefault,
            )
        )
    }

    @Test
    fun `interstitial dismissal does not chain full2 when remote config disables it`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        assertFalse(
            policy.shouldChainFull2AfterInterstitial(
                full2FlagShow = 0,
                originPlacement = PlacementDefault.PLFullDefault,
            )
        )
        assertFalse(
            policy.shouldChainFull2AfterInterstitial(
                full2FlagShow = 2,
                originPlacement = PlacementDefault.PLFullDefault,
            )
        )
    }

    @Test
    fun `interstitial dismissal never chains full2 on splash or intro placements`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        listOf(
            PlacementDefault.PLFullSplash,
            PlacementDefault.PLFull2Splash,
            PlacementDefault.PLFullIntro3,
        ).forEach { placement ->
            assertFalse(
                policy.shouldChainFull2AfterInterstitial(
                    full2FlagShow = 3,
                    originPlacement = placement,
                )
            )
        }
    }

    @Test
    fun `rewarded dismissal chains full2 when remote config enables rewarded chain`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        assertTrue(policy.shouldChainFull2AfterRewarded(full2FlagShow = 2))
        assertTrue(policy.shouldChainFull2AfterRewarded(full2FlagShow = 3))
    }

    @Test
    fun `rewarded dismissal does not chain full2 when remote config disables it`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        assertFalse(policy.shouldChainFull2AfterRewarded(full2FlagShow = 0))
        assertFalse(policy.shouldChainFull2AfterRewarded(full2FlagShow = 1))
    }

    @Test
    fun `full2 waits only until the host activity resumes`() {
        val policy = FullScreenAdPolicy(clickReturnCooldownMillis = 5_000L)

        assertTrue(policy.shouldDeferFull2UntilHostResumed())
        assertFalse(policy.isHostReadyForFullscreen())

        policy.onHostActivityResumed()
        assertFalse(policy.shouldDeferFull2UntilHostResumed())
        assertTrue(policy.isHostReadyForFullscreen())

        policy.onHostActivityPaused()
        assertTrue(policy.shouldDeferFull2UntilHostResumed())
        assertFalse(policy.isHostReadyForFullscreen())
    }
}
