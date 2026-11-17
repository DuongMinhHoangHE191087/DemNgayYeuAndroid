package com.example.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidShareGatewayTest {

  private val app: Application = ApplicationProvider.getApplicationContext()

  private fun innerOf(chooser: Intent): Intent =
    assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))

  @Test
  fun shareStartsChooserWithPlainTextExtra() {
    AndroidShareGateway(app).share("hello love")
    val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
    val inner = innerOf(chooser)
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertEquals(Intent.ACTION_SEND, inner.action)
    assertEquals("text/plain", inner.type)
    assertEquals("hello love", inner.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun shareFromApplicationContextAddsNewTaskFlag() {
    AndroidShareGateway(app).share("hello love")
    val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
    val inner = innerOf(chooser)
    assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    assertEquals(0, inner.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  @Test
  fun chooserTitleIsPassedThrough() {
    AndroidShareGateway(app).share("x", "Share via")
    val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
    assertEquals("Share via", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
  }

  @Test
  fun shareFromActivityDoesNotAddNewTaskFlag() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    AndroidShareGateway(activity).share("hello love")
    val chooser = assertNotNull(shadowOf(activity).nextStartedActivity)
    val inner = innerOf(chooser)
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertEquals(0, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    assertEquals(Intent.ACTION_SEND, inner.action)
    assertEquals("hello love", inner.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun nullChooserTitleOmitsTitleExtra() {
    AndroidShareGateway(app).share("x")
    val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
    assertFalse(chooser.hasExtra(Intent.EXTRA_TITLE))
  }
}
