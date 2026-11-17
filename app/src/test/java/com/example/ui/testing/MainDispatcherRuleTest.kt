package com.example.ui.testing

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainDispatcherRuleTest {

  @Test
  fun mainDispatcher_isReplacedAndRestored() {
    val rule = MainDispatcherRule()
    var ranWhileReplaced = false
    rule.apply(
      object : Statement() {
        override fun evaluate() {
          CoroutineScope(Dispatchers.Main).launch { ranWhileReplaced = true }
        }
      },
      Description.createTestDescription(MainDispatcherRuleTest::class.java, "inner")
    ).evaluate()
    assertTrue("Dispatchers.Main is replaced: the launch runs eagerly", ranWhileReplaced)

    var ranAfterRestore = false
    CoroutineScope(Dispatchers.Main).launch { ranAfterRestore = true }
    assertFalse(
      "Dispatchers.Main is restored: the paused Android main looper has not run the launch yet",
      ranAfterRestore
    )
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("the restored Dispatchers.Main runs the launch once the main looper idles", ranAfterRestore)
  }
}
