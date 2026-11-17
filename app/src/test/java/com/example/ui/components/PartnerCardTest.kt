package com.example.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class PartnerCardTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val tag = "partner_offline_profile_col"

  private fun setCard(
    age: Int = 22,
    zodiac: String = "Thiên Bình",
    birthDate: String = "05/10/2002",
    showHeartBadge: Boolean = false,
    onClick: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      MyApplicationTheme {
        PartnerCard(
          avatarUrl = "",
          name = "Lan",
          age = age,
          zodiac = zodiac,
          birthDate = birthDate,
          showHeartBadge = showHeartBadge,
          avatarOuterSize = 64.dp,
          avatarInnerSize = 56.dp,
          onClick = onClick,
          testTag = tag
        )
      }
    }
  }

  // assertTextEquals compares the whole text list in order, so an extra or empty chip would fail too.
  @Test
  fun showsNameAgeZodiacAndBirthDate_andTapCallsBack() {
    var clicks = 0
    setCard(onClick = { clicks++ })

    composeTestRule.onNodeWithTag(tag)
      .assertTextEquals("Lan", "22t", "Thiên Bình", "05/10/2002")
      .assertHeightIsAtLeast(48.dp)
      .performClick()
    assertEquals(1, clicks)
    composeTestRule.onAllNodesWithText("MÁY").assertCountEquals(0)
  }

  @Test
  fun zeroAge_hidesTheAgeChip() {
    setCard(age = 0)
    composeTestRule.onNodeWithTag(tag).assertTextEquals("Lan", "Thiên Bình", "05/10/2002")
  }

  @Test
  fun blankZodiac_hidesTheZodiacChip() {
    setCard(zodiac = "")
    composeTestRule.onNodeWithTag(tag).assertTextEquals("Lan", "22t", "05/10/2002")
  }

  @Test
  fun blankBirthDate_hidesTheDateRow() {
    setCard(birthDate = "")
    composeTestRule.onNodeWithTag(tag).assertTextEquals("Lan", "22t", "Thiên Bình")
  }

  @Test
  fun heartBadge_isShownOnlyWhenAsked() {
    setCard(showHeartBadge = true)
    // Unmerged tree: the clickable card merges its children, and a tag on a child is dropped there.
    composeTestRule.onNodeWithTag("${tag}_heart", useUnmergedTree = true).assertExists()
  }

  @Test
  fun noHeartBadge_byDefault() {
    setCard(showHeartBadge = false)
    composeTestRule.onNodeWithTag("${tag}_heart", useUnmergedTree = true).assertDoesNotExist()
  }
}
