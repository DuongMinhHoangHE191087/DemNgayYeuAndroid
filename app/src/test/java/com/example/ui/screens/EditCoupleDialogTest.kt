package com.example.ui.screens

// T3 (D9): the "Cặp đôi" dialog content is scaled by AppDensity like the rest of the app, and every control keeps a real >= 48dp touch box.
// Only EditCoupleDialogContent is composed; the Dialog wrapper is never composed in a test (Robolectric never idles with a text field in a Dialog window).
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class EditCoupleDialogTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private var dismissCount = 0 // times the dialog content asked its host to dismiss

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
  }

  @After
  fun tearDown() {
    viewModelStore.clear() // cancels viewModelScope BEFORE the database is closed
    injectDatabase(null)
    db.close()
  }

  // InLoveViewModel reaches Room through AppDatabase.getDatabase(context); setting INSTANCE hands it the in-memory database.
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun newViewModel(): InLoveViewModel =
    ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>()))[InLoveViewModel::class.java]

  // Creates the VM, waits for the guest session restore, optionally signs in; composes nothing yet.
  private fun startViewModel(seed: CoupleProfileEntity? = null, signInAs: String? = null): InLoveViewModel {
    seed?.let { runBlocking { db.inLoveDao().insertCoupleProfile(it) } }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    // The restore ends with setCurrentUserId(""): wait for it like ProfileSyncViewModelTest.newViewModel(), then sign in.
    await("the session restore") { viewModel.authRepo.restoreJob.isCompleted }
    shadowOf(Looper.getMainLooper()).idle()
    signInAs?.let { runBlocking { viewModel.onlineRepo.setCurrentUserId(it) } }
    return viewModel
  }

  private fun show(viewModel: InLoveViewModel) {
    composeTestRule.setContent {
      MyApplicationTheme {
        EditCoupleDialogContent(viewModel) { dismissCount++ }
      }
    }
  }

  private fun launch(seed: CoupleProfileEntity? = null): InLoveViewModel {
    val viewModel = startViewModel(seed)
    if (seed != null) await("girlName") { viewModel.girlName.value == seed.partner2Name }
    show(viewModel)
    return viewModel
  }

  private fun signedInUser(uid: String = "uid_me", coupled: Boolean = false) = OnlineUserEntity(
    uid = uid, displayName = "Me", coupleCode = "MMMM-1111", isProfileSetup = true,
    partnerId = if (coupled) "uid_p" else null,
    relationshipId = if (coupled) "rel1" else null,
    status = if (coupled) OnlineStatus.COUPLED else OnlineStatus.SINGLE
  )

  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  private fun openPairingTab() {
    composeTestRule.onNodeWithText("Pairing").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("btn_close_pairing_tab")).fetchSemanticsNodes().isNotEmpty()
    }
  }

  // The test root runs at the unscaled density while the content is scaled by AppDensity (0.85),
  // so MinTouchBox (57.dp) renders at about 48.45.dp here.
  private fun assertTarget(tag: String) {
    composeTestRule.onNodeWithTag(tag)
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun pairingTab_targetsMeetMinTouchBox() {
    launch()
    openPairingTab()

    listOf("btn_close_edit_couple", "dialog_btn_search", "btn_close_pairing_tab", "dialog_btn_paste")
      .forEach(::assertTarget)

    composeTestRule.onNodeWithTag("dialog_search_input").performTextInput("abc")
    composeTestRule.waitForIdle()
    assertTarget("dialog_btn_clear")
  }

  @Test
  fun pairingTab_searchDisabledWhileQueryBlank() {
    launch()
    openPairingTab()

    composeTestRule.onNodeWithTag("dialog_btn_search").assertIsNotEnabled()
  }

  @Test
  fun pairingTab_searchEnabledAfterTyping() {
    launch()
    openPairingTab()

    composeTestRule.onNodeWithTag("dialog_search_input").performTextInput("abc")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dialog_btn_search").assertIsEnabled()
  }

  @Test
  fun pairingTab_searchHasContentDescription() {
    launch()
    openPairingTab()

    composeTestRule.onNodeWithTag("dialog_btn_search")
      .assert(hasContentDescription("Search").or(hasContentDescription("Tìm")))
  }

  @Test
  fun profileTab_saveMeetsMinTouchBox() {
    launch()

    assertTarget("btn_close_edit_couple")
    composeTestRule.onNodeWithTag("btn_save_couple_offline").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun profileTab_save_doesNotInventBirthdayOrAnniversary() {
    launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "", partner2ProfilePicture = "",
        loveTitle = "Our Title", loveDays = 5, anniversaryDate = ""
      )
    )

    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    await("the save to finish") { runBlocking { db.inLoveDao().getCoupleProfileSync()!!.loveDays } != 5 }

    val row = runBlocking { db.inLoveDao().getCoupleProfileSync()!! }
    assertEquals("", row.partner2Birthday)
    assertEquals("", row.anniversaryDate)
    assertEquals("", row.partner1Zodiac)
    assertEquals("", row.partner2Zodiac)
    assertEquals("Me Real", row.partner1Name)
    assertEquals("Pat Real", row.partner2Name)
  }

  // Audit C3 (L4-5): the partner birthday is picked from the calendar only, so the clear button is the one way to empty a stored birthday.
  // The saved row must be blank, with no stale age or zodiac left behind.
  @Test
  fun profileTab_clearingTheStoredPartnerBirthday_isSavedBlank() {
    launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "01/02/2000", partner2ProfilePicture = "",
        partner2Age = 26, partner2Zodiac = "Bảo Bình",
        loveTitle = "Our Title", loveDays = 5, anniversaryDate = ""
      )
    )
    composeTestRule.onNodeWithTag("input_partner_birth").assertTextContains("01/02/2000")
    // Real pointer path (the field listens to pointer input), not the semantics click.
    composeTestRule.onNodeWithTag("input_partner_birth_clear_btn", useUnmergedTree = true).performScrollTo().performTouchInput { click() }
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("input_partner_birth_clear_btn", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    await("the save to finish") { runBlocking { db.inLoveDao().getCoupleProfileSync()!!.loveDays } != 5 }

    val row = runBlocking { db.inLoveDao().getCoupleProfileSync()!! }
    assertEquals("", row.partner2Birthday)
    assertEquals("", row.partner2Zodiac)
    assertEquals(0, row.partner2Age)
    assertEquals("Pat Real", row.partner2Name)
  }

  // A refused save (here: a stored anniversary that no longer validates) must leave the dialog open so the user sees the toast and can fix it.
  @Test
  fun profileTab_save_refusedAnniversary_keepsTheDialogOpen() {
    val viewModel = launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "", partner2ProfilePicture = "",
        loveTitle = "Our Title", loveDays = 5, anniversaryDate = "31/02/2020"
      )
    )
    await("the anniversary") { viewModel.anniversaryDate.value == "31/02/2020" }
    viewModel.openEditCoupleDialog()

    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, dismissCount)
    assertTrue(viewModel.showEditCoupleDialog.value)
    assertEquals(5, runBlocking { db.inLoveDao().getCoupleProfileSync()!!.loveDays })
  }

  // A successful save closes the dialog through the ViewModel alone; the content must not also ask its host to dismiss.
  @Test
  fun profileTab_save_success_closesThroughTheViewModelOnly() {
    val viewModel = launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "", partner2ProfilePicture = "",
        loveTitle = "Our Title", loveDays = 5, anniversaryDate = ""
      )
    )
    viewModel.openEditCoupleDialog()

    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    await("the dialog to close") { !viewModel.showEditCoupleDialog.value }

    assertEquals(0, dismissCount)
  }

  // "Hôm nay" is the ViewModel's stand-in for "no date yet" (a guest who never saved), not a date the user typed:
  // an untouched date field must not make Save refuse it, and nothing is invented in its place.
  @Test
  fun profileTab_save_untouchedPlaceholderDate_savesABlankAnniversary() {
    val viewModel = launch()
    assertEquals("Hôm nay", viewModel.anniversaryDate.value)
    viewModel.openEditCoupleDialog()

    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    await("the dialog to close") { !viewModel.showEditCoupleDialog.value }

    assertEquals("", runBlocking { db.inLoveDao().getCoupleProfileSync()!!.anniversaryDate })
  }

  // A-6: saving without touching the birthday hands the stored text back verbatim, even when it no longer parses.
  @Test
  fun profileTab_saveWithoutChange_keepsAnUnparseableStoredBirthday() {
    launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "5/5/95", partner2ProfilePicture = "",
        partner2Age = 31, partner2Zodiac = "Kim Ngưu",
        loveTitle = "Our Title", loveDays = 5, anniversaryDate = ""
      )
    )
    composeTestRule.onNodeWithTag("btn_save_couple_offline").performScrollTo().performClick()
    await("the save to finish") { runBlocking { db.inLoveDao().getCoupleProfileSync()!!.loveDays } != 5 }

    val row = runBlocking { db.inLoveDao().getCoupleProfileSync()!! }
    assertEquals("5/5/95", row.partner2Birthday)
    assertEquals(31, row.partner2Age)
    assertEquals("Kim Ngưu", row.partner2Zodiac)
  }

  // The clear button only appears while there is a birthday to clear.
  @Test
  fun profileTab_blankPartnerBirthday_hasNoClearButton() {
    launch(
      CoupleProfileEntity(
        partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Pat Real", partner2Birthday = "", partner2ProfilePicture = ""
      )
    )

    composeTestRule.onNodeWithTag("input_partner_birth").assertExists()
    composeTestRule.onAllNodesWithTag("input_partner_birth_clear_btn", useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun coupled_showsTheReadOnlySummaryAndNoSaveButton() {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(signedInUser(coupled = true))
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true, birthDate = "02/02/1992"))
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_707_868_800_000L, startDateText = "14/02/2024")
      )
    }
    val viewModel = startViewModel(signInAs = "uid_me")
    await("the coupled state") {
      viewModel.activeRelationship.value != null && viewModel.partnerOnlineUser.value != null && viewModel.anniversaryDate.value == "14/02/2024"
    }
    show(viewModel)

    composeTestRule.onNodeWithTag("coupled_profile_summary").assertExists()
    composeTestRule.onAllNodesWithText("Pat", substring = true).onFirst().assertExists()
    composeTestRule.onAllNodesWithText("02/02/1992", substring = true).onFirst().assertExists()
    composeTestRule.onAllNodesWithText("14/02/2024", substring = true).onFirst().assertExists()
    // The header close button is still composed, so the missing Save is not a missing dialog.
    composeTestRule.onNodeWithTag("btn_close_edit_couple").assertExists()
    composeTestRule.onAllNodesWithTag("btn_save_couple_offline").assertCountEquals(0)
  }

  // The bottom "Close" button is gone: the header X is the only way out of the profile tab.
  @Test
  fun profileTab_notCoupled_hasNoBottomCloseText() {
    launch()

    composeTestRule.onNodeWithTag("btn_close_edit_couple").assertExists()
    composeTestRule.onAllNodesWithText("Close").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Đóng").assertCountEquals(0)
  }

  // Presence only: never click Accept / Decline / Cancel request / search here, they reach Firebase.
  @Test
  fun pairingTab_incomingInvite_showsTheRequestAndHidesSearch() {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(signedInUser())
      dao.insertOnlineInvite(
        OnlineInviteEntity(
          inviteId = "inv_in", senderUid = "uid_x", senderName = "Xena", senderCoupleCode = "XXXX-2222",
          targetCoupleCode = "MMMM-1111", targetUid = "uid_me",
          proposedStartDate = 1_707_868_800_000L, proposedStartDateText = "14/02/2024", status = "PENDING"
        )
      )
    }
    val viewModel = startViewModel(signInAs = "uid_me")
    await("the incoming invite") { viewModel.incomingInvite.value?.inviteId == "inv_in" }
    show(viewModel)
    openPairingTab()

    composeTestRule.onNodeWithTag("dialog_incoming_invite_card").assertExists()
    composeTestRule.onAllNodesWithText("Xena", substring = true).onFirst().assertExists()
    composeTestRule.onAllNodesWithTag("dialog_search_input").assertCountEquals(0)
  }

  @Test
  fun pairingTab_outgoingInvite_showsWaitingAndHidesSearch() {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(signedInUser())
      dao.insertOnlineInvite(
        OnlineInviteEntity(
          inviteId = "inv_out", senderUid = "uid_me", senderName = "Me", senderCoupleCode = "MMMM-1111",
          targetCoupleCode = "YYYY-3333", status = "PENDING"
        )
      )
    }
    val viewModel = startViewModel(signInAs = "uid_me")
    await("the outgoing invite") { viewModel.outgoingInvite.value?.inviteId == "inv_out" }
    show(viewModel)
    openPairingTab()

    composeTestRule.onAllNodesWithText("Waiting for response...").onFirst().assertExists()
    composeTestRule.onAllNodesWithText("Cancel request").onFirst().assertExists()
    composeTestRule.onAllNodesWithText("MMMM-1111").onFirst().assertExists() // the "Your Code" card stays
    composeTestRule.onAllNodesWithTag("dialog_search_input").assertCountEquals(0)
  }

  @Test
  fun coupledProfileSummary_showsReadOnlyValues() {
    composeTestRule.setContent {
      MyApplicationTheme {
        CoupledProfileSummary(
          partnerName = "Pat", partnerBirthday = "01/02/2000", partnerAvatarUrl = "",
          anniversary = "14/02/2024", isEnglish = true
        )
      }
    }

    composeTestRule.onNodeWithText("Pat", substring = true).assertExists()
    composeTestRule.onNodeWithText("01/02/2000", substring = true).assertExists()
    composeTestRule.onNodeWithText("14/02/2024", substring = true).assertExists()
    composeTestRule.onNodeWithText("Your partner updates their own info; it syncs automatically.").assertExists()
  }
}
