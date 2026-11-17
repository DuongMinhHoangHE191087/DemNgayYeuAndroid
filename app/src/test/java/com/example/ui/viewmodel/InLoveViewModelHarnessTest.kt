package com.example.ui.viewmodel

import android.os.Looper
import com.example.alarm.plan.fakes.FakeReminderScheduler
import com.example.ui.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveViewModelHarnessTest {

  @get:Rule val mainDispatcher = MainDispatcherRule()

  @Test
  fun harness_buildsViewModel_andFlowsEmitDefaults() {
    val viewModel = InLoveViewModelHarness.build()
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("selectedTab starts at 0", 0, viewModel.selectedTab.value)
    assertNull("toastMessage starts null", viewModel.toastMessage.value)
    assertFalse("isVip starts false", viewModel.isVip.value)
  }

  @Test
  fun harness_wiresTheFakeScheduler() {
    val fake = FakeReminderScheduler()
    val viewModel = InLoveViewModelHarness.build(fake)
    viewModel.resyncAllAnniversaryAlarms()
    assertTrue("the ViewModel asked the injected fake to resync", fake.resyncRequests >= 1)
  }

  // Reads the ViewModel's private lazy scheduler, the same trick as androidScheduler() in ReminderWiringSettingsTest;
  // update the field name here if the ViewModel moves it.
  @Test
  fun harness_defaultSchedulerIsAFake() {
    val viewModel = InLoveViewModelHarness.build()
    val field = InLoveViewModel::class.java.getDeclaredField("scheduler\$delegate")
    field.isAccessible = true
    assertTrue(
      "build() without a scheduler injects a fake, never the real alarm scheduler",
      (field.get(viewModel) as Lazy<*>).value is FakeReminderScheduler
    )
  }
}
