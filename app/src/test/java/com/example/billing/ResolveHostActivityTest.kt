package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveHostActivityTest {

  @Test
  fun explicitActivityWinsOverContext() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val other = ApplicationProvider.getApplicationContext<Context>()
    assertSame(activity, resolveHostActivity(activity, other))
  }

  @Test
  fun activityIsFoundThroughWrappersButNotThroughConfigurationContext() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    assertSame(activity, resolveHostActivity(null, ContextWrapper(ContextWrapper(activity))))
    // createConfigurationContext does not wrap the Activity: only the explicit one can be used
    val cfg = activity.createConfigurationContext(activity.resources.configuration)
    assertNull(resolveHostActivity(null, cfg))
  }
}
