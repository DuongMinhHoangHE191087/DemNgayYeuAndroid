package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class ThemeTokensTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun minTouchBox_scalesToAtLeast48dp() {
    assertEquals(57.dp, MinTouchBox)
    assertTrue(MinTouchBox.value * 0.85f >= 48f)
  }

  @Test
  fun appShapes_areRaised() {
    lateinit var shapes: Shapes
    composeTestRule.setContent {
      MyApplicationTheme {
        shapes = MaterialTheme.shapes
      }
    }
    assertEquals(RoundedCornerShape(20.dp), shapes.medium)
    assertEquals(RoundedCornerShape(36.dp), shapes.extraLarge)
  }
}
