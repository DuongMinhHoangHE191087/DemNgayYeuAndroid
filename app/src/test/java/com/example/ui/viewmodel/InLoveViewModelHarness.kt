package com.example.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.plan.ReminderScheduler
import com.example.alarm.plan.fakes.FakeReminderScheduler

// Builds an InLoveViewModel with an injected scheduler. The caller must run under Robolectric
// (@RunWith(RobolectricTestRunner::class), @Config(sdk = [34])); see
// docs/superpowers/spikes/p4-u07-vm-robolectric.md for why no Application or Firebase init is needed.
// Tests that switch accounts must first forget the stored scope and wait for the ViewModel's restoreSession() (it runs
// on IO and can overtake the switch): see forgetScope() and awaitSessionRestored() in ReminderWiringSettingsTest.
object InLoveViewModelHarness {

  fun build(reminderScheduler: ReminderScheduler = FakeReminderScheduler()): InLoveViewModel =
    InLoveViewModel(ApplicationProvider.getApplicationContext<Application>(), reminderScheduler)
}
