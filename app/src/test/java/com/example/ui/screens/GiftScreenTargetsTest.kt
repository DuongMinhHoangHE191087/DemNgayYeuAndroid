package com.example.ui.screens

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.ui.testing.NoNetworkImagesRule
import com.example.ui.testing.TALL_PIXEL8
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GiftScreenTargetsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val noNetwork = NoNetworkImagesRule()

  // Density of the real screen, read above the theme: MyApplicationTheme scales everything below it by 0.85.
  private var screenDensity = 0f

  private fun setThemedContent(content: @Composable () -> Unit) {
    composeTestRule.setContent {
      screenDensity = LocalDensity.current.density
      MyApplicationTheme { content() }
    }
  }

  // assertWidthIsAtLeast reads a node in its own (scaled) density, so it cannot see AppDensity: a .size(48.dp) passes it
  // while rendering 40.8 real dp. Compare real pixels with 48 dp of the screen instead.
  private fun SemanticsNodeInteraction.assertTouchTargetMeets48Dp() {
    val size = fetchSemanticsNode().size
    val minPx = (48 * screenDensity).roundToInt()
    assertTrue("rendered ${size.width}x${size.height} px, need at least $minPx px (48 dp)", size.width >= minPx && size.height >= minPx)
  }

  private fun setChecklistRow() = setThemedContent {
    ChecklistRow(
      item = ChecklistItemEntity(id = 7, text = "Buy flowers"),
      onToggle = {},
      onEdit = {},
      onDelete = {}
    )
  }

  private fun setGiftReminderRow() = setThemedContent {
    GiftReminderRow(
      item = GiftReminderEntity(id = 9, title = "Candle"),
      onToggle = {},
      onEdit = {},
      onDelete = {}
    )
  }

  @Test
  fun giftIdeaHeart_meetsMinTouchTarget() {
    setThemedContent {
      GiftIdeaCard(
        idea = GiftIdeaEntity(
          title = "Gift",
          category = "",
          badgeText = "",
          tag = "",
          description = "",
          imageUrl = ""
        ),
        onFavoriteToggle = {},
        onActionClick = {}
      )
    }

    // No testTag on the heart: find it by its content description.
    composeTestRule.onNodeWithContentDescription("Yêu thích").assertTouchTargetMeets48Dp()
  }

  // The buttons sit inside a clickable row, so they are looked up in the unmerged tree.
  @Test
  fun checklistCheckCircle_meetsMinTouchTarget() {
    setChecklistRow()

    composeTestRule.onNodeWithTag("btn_toggle_checklist_7", useUnmergedTree = true).assertTouchTargetMeets48Dp()
  }

  // Edit and delete only exist once the row is expanded.
  @Test
  fun checklistRowActions_meetMinTouchTarget() {
    setChecklistRow()
    composeTestRule.onNodeWithTag("checklist_item_7").performClick()

    composeTestRule.onNodeWithTag("btn_edit_checklist_7", useUnmergedTree = true).assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("btn_delete_checklist_7", useUnmergedTree = true).assertTouchTargetMeets48Dp()
  }

  @Test
  fun giftReminderToggle_meetsMinTouchTarget() {
    setGiftReminderRow()

    composeTestRule.onNodeWithTag("btn_toggle_gift_reminder_9", useUnmergedTree = true).assertTouchTargetMeets48Dp()
  }

  @Test
  fun giftReminderRowActions_meetMinTouchTarget() {
    setGiftReminderRow()
    composeTestRule.onNodeWithTag("gift_reminder_item_9").performClick()

    composeTestRule.onNodeWithTag("btn_edit_gift_reminder_9", useUnmergedTree = true).assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("btn_delete_gift_reminder_9", useUnmergedTree = true).assertTouchTargetMeets48Dp()
  }

  // The share button of a gift suggestion card must reach the minimum touch target.
  @Test
  fun giftSuggestionShare_meetsMinTouchTarget() {
    setThemedContent {
      PersonalizedGiftCard(
        title = "Gift",
        reason = "Reason",
        preparation = "Prepare",
        priceLabel = null,
        onShare = {},
        shareLabel = "Share"
      )
    }
    composeTestRule.onNodeWithTag("btn_share_gift_suggestion", useUnmergedTree = true).assertTouchTargetMeets48Dp()
  }

  // The region chips must reach the minimum touch target, unselected and selected.
  @Test
  fun partnerRegionChips_meetMinTouchTarget() {
    setThemedContent {
      PartnerPreferencesEditor(
        profileExists = true,
        savedLikes = "",
        savedBudgetVnd = 0L,
        savedRegion = "",
        isEnglish = true,
        onSave = { _, _, _ -> }
      )
    }
    composeTestRule.onNodeWithTag("btn_toggle_partner_preferences").performClick()
    composeTestRule.onNodeWithTag("partner_region_vn").assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("partner_region_intl").assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("partner_region_vn").performClick()
    composeTestRule.onNodeWithTag("partner_region_vn").assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("partner_region_intl").assertTouchTargetMeets48Dp()
    composeTestRule.onNodeWithTag("partner_region_intl").performClick()
    composeTestRule.onNodeWithTag("partner_region_intl").assertTouchTargetMeets48Dp()
  }

  private fun withGiftScreen(language: AppLanguage, block: (InLoveViewModel) -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
    val viewModelStore = ViewModelStore()
    try {
      val viewModel = ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(context))[InLoveViewModel::class.java]
      viewModel.setLanguage(language)
      composeTestRule.setContent { MyApplicationTheme { GiftScreen(viewModel) } }
      block(viewModel)
    } finally {
      viewModelStore.clear()
      injectDatabase(null)
      db.close()
    }
  }

  // InLoveViewModel reaches Room through AppDatabase.getDatabase(context); setting the INSTANCE field is how it gets the in-memory test database.
  // D-42 changes how the DAO is obtained: this is the ONE place to adapt (nothing else here may mention INSTANCE).
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  @Test
  @Config(qualifiers = TALL_PIXEL8, sdk = [34])
  fun vipProposalCard_namesTheVipPlanInVietnamese() {
    withGiftScreen(AppLanguage.VI) { viewModel ->
      composeTestRule.onNodeWithTag("btn_view_vip_proposal").assertTextEquals("Xem gói VIP")
      composeTestRule.onNodeWithTag("btn_view_vip_proposal").performClick()
      assertTrue(viewModel.showVipProposalDetail.value)
    }
  }

  @Test
  @Config(qualifiers = TALL_PIXEL8, sdk = [34])
  fun vipProposalCard_namesTheVipPlanInEnglish() {
    withGiftScreen(AppLanguage.EN) {
      composeTestRule.onNodeWithTag("btn_view_vip_proposal").assertTextEquals("View VIP plan")
    }
  }
}
