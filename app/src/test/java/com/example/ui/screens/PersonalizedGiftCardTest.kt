package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.example.data.model.GiftIdeaEntity
import com.example.domain.content.GiftCatalog
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.ui.testing.NoNetworkImagesRule
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
class PersonalizedGiftCardTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val noNetwork = NoNetworkImagesRule()

  private fun setCard(onShare: (() -> Unit)? = null) {
    composeTestRule.setContent {
      MyApplicationTheme {
        PersonalizedGiftCard(
          title = "Gift title",
          reason = "R1",
          preparation = "P1",
          priceLabel = null,
          modifier = Modifier.testTag("personalized_gift_card"),
          onShare = onShare
        )
      }
    }
  }

  @Test
  fun reasonAndPreparation_areHidden_untilCardIsTapped() {
    setCard()
    composeTestRule.onNodeWithText("R1", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("P1", useUnmergedTree = true).assertDoesNotExist()

    composeTestRule.onNodeWithTag("personalized_gift_card").performTouchInput { click() }

    composeTestRule.onNodeWithText("R1", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("P1", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun shareButton_sharesOnce_andDoesNotExpandCard() {
    var shares = 0
    setCard(onShare = { shares++ })

    composeTestRule.onNodeWithTag("btn_share_gift_suggestion", useUnmergedTree = true).performTouchInput { click() }

    assertEquals(1, shares)
    composeTestRule.onNodeWithText("R1", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("P1", useUnmergedTree = true).assertDoesNotExist()
  }

  private fun suggestion(remoteId: String, title: String, reason: String) = PersonalizedGiftSuggestion(
    idea = GiftIdeaEntity(
      title = title, category = "c", badgeText = "b", tag = "t", description = "d", imageUrl = "", remoteId = remoteId
    ),
    gift = GiftCatalog.LocalizedGift(
      categoryKey = "c", title = title, badge = "b", tag = "t", description = "d", detailsSnippet = "", actionText = ""
    ),
    reason = reason,
    preparation = "P-$remoteId",
    matchesInterest = true,
    fitsOccasion = false,
    priceLabel = null
  )

  @Test
  fun expansion_staysWithTheSameGift_whenTheListIsReordered() {
    val giftA = suggestion("gift-a", "Gift A", "reason-a")
    val giftB = suggestion("gift-b", "Gift B", "reason-b")
    var suggestions by mutableStateOf(listOf(giftA, giftB))
    composeTestRule.setContent {
      MyApplicationTheme {
        PersonalizedGiftList(suggestions = suggestions, shareLabel = "Share", onShare = {})
      }
    }

    composeTestRule.onNodeWithText("Gift A", useUnmergedTree = true).performTouchInput { click() }
    composeTestRule.onNodeWithText("reason-a", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.runOnUiThread { suggestions = listOf(giftB, giftA) }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("reason-a", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("reason-b", useUnmergedTree = true).assertDoesNotExist()
  }
}
