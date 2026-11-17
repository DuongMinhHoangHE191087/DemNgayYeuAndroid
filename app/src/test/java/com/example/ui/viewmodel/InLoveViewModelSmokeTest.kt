package com.example.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * U-07 spike: InLoveViewModel(application) builds under plain Robolectric with test-side code only.
 * No Firebase init, no WorkManager init and no test Application are needed (see
 * docs/superpowers/spikes/p4-u07-vm-robolectric.md). Two tests on purpose: the second one proves a
 * second VM in the same JVM works despite the AppServiceLocator / AppDatabase singletons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveViewModelSmokeTest {

  private fun newViewModel() =
    InLoveViewModel(ApplicationProvider.getApplicationContext<Application>())

  @Test
  fun `view model constructs and exposes initial selectedTab`() {
    val viewModel = newViewModel()
    assertEquals(0, viewModel.selectedTab.value)
  }

  @Test
  fun `second view model in same JVM reads selectedTab after main looper idles`() {
    val viewModel = newViewModel()
    // Let the init-time viewModelScope.launch blocks (default data seed, alarm scheduling) run.
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(0, runBlocking { viewModel.selectedTab.first() })
  }
}
