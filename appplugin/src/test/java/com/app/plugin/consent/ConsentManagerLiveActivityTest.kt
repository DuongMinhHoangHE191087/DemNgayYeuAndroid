package com.app.plugin.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * Fix wave 2, Important 2: `AdSdkStartup` start AppsFlyer trên Activity của luồng consent. Chỉ đưa ra
 * Activity còn sống; Activity đã finish/destroy thì `null` (AppsFlyer rơi về app context và log).
 */
class ConsentManagerLiveActivityTest {
    @Before fun setUp() = ConsentState.resetForTest()
    @After fun tearDown() = ConsentState.resetForTest()

    private fun activity(finishing: Boolean = false, destroyed: Boolean = false): Activity {
        val context = contextWithTcf(gdpr = 1, purposes = "")
        val resources = context.resources   // ngoài chuỗi stub: gọi mock bên trong `when` là stub dở
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        `when`(activity.isFinishing).thenReturn(finishing)
        `when`(activity.isDestroyed).thenReturn(destroyed)
        return activity
    }

    /** App gọi `updateConsentInfo(activity)`; UMP chưa trả lời (không callback). */
    private fun consentFlowFrom(activity: Activity) {
        val ci = mock(ConsentInformation::class.java)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            ConsentManager.updateConsentInfo(activity) {}
            ConsentManager.reset(activity)   // bỏ `info` giả khỏi object dùng chung
        }
    }

    @Test fun `Activity cua luong consent con song thi tra ve chinh no`() {
        val a = activity()
        consentFlowFrom(a)

        assertSame(a, ConsentManager.liveConsentActivity())
    }

    @Test fun `Activity da finish hoac destroy thi null`() {
        consentFlowFrom(activity(finishing = true))
        assertNull(ConsentManager.liveConsentActivity())

        consentFlowFrom(activity(destroyed = true))
        assertNull(ConsentManager.liveConsentActivity())
    }
}
