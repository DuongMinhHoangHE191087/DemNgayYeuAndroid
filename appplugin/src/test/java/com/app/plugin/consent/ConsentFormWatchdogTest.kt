package com.app.plugin.consent

import com.app.plugin.consent.ConsentFormWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentFormWatchdogTest {
    @Test
    fun `a visible form keeps the gate closed however long the user reads it`() {
        repeat(5) { checks ->
            assertEquals(
                Action.KEEP_WAITING,
                ConsentFormWatchdog.onFormCheck(
                    callbackFired = false, activityGone = false, activityHasFocus = false, silentChecks = checks,
                ),
            )
        }
    }

    @Test
    fun `no form and no callback releases first and retries only on the next check`() {
        assertEquals(
            Action.RELEASE_AND_WAIT,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = false, activityGone = false, activityHasFocus = true, silentChecks = 0,
            ),
        )
        assertEquals(
            Action.RETRY,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = false, activityGone = false, activityHasFocus = true, silentChecks = 1,
            ),
        )
    }

    @Test
    fun `a fired callback stops the checks`() {
        assertEquals(
            Action.STOP,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = true, activityGone = true, activityHasFocus = true, silentChecks = 1,
            ),
        )
    }

    @Test
    fun `a destroyed activity releases without retrying`() {
        assertEquals(
            Action.RELEASE,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = false, activityGone = true, activityHasFocus = false, silentChecks = 0,
            ),
        )
    }

    @Test
    fun `a form lost with its activity moves to the recreated activity while consent is still required`() {
        assertEquals(
            Action.SHOW_ON_NEWER_ACTIVITY,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = false, activityGone = true, activityHasFocus = false, silentChecks = 0,
                newerActivityAlive = true, stillRequired = true,
            ),
        )
    }

    @Test
    fun `a lost form is not shown again once consent is no longer required`() {
        assertEquals(
            Action.RELEASE,
            ConsentFormWatchdog.onFormCheck(
                callbackFired = false, activityGone = true, activityHasFocus = false, silentChecks = 0,
                newerActivityAlive = true, stillRequired = false,
            ),
        )
    }

    @Test
    fun `a quick callback with consent still required means the form never showed`() {
        assertTrue(ConsentFormWatchdog.shouldRetryAfterCallback(elapsedMs = 800L, stillRequired = true))
        assertFalse(ConsentFormWatchdog.shouldRetryAfterCallback(elapsedMs = 800L, stillRequired = false))
        assertFalse(
            ConsentFormWatchdog.shouldRetryAfterCallback(
                elapsedMs = ConsentFormWatchdog.FORM_MIN_INTERACTION_MS, stillRequired = true,
            ),
        )
    }
}
