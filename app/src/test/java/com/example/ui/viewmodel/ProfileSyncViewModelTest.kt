package com.example.ui.viewmodel

// T7: the Room collector must not clobber the online profile / relationship date of a signed-in user,
// updateMyProfile only refreshes the guest flows (the repository mirrors Room), and a paired couple cannot edit the love date.
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RelationshipStatus
import com.example.ui.util.AppLanguage
import com.example.ui.util.DEFAULT_LOVE_TITLE
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSyncViewModelTest {

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
  }

  @After
  fun tearDown() {
    viewModelStore.clear() // cancels viewModelScope BEFORE the database is closed
    injectDatabase(null)
    db.close()
  }

  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  // The VM's session restore runs on IO and ends with setCurrentUserId(""); a sign-in set up before it finished would be wiped.
  // The restore job is the completion signal; wait for it while idling the main looper (a blocking join would stall Robolectric's looper).
  private fun newViewModel(): InLoveViewModel {
    val vm = ViewModelProvider(
      viewModelStore,
      ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>())
    )[InLoveViewModel::class.java]
    vm.setLanguage(AppLanguage.EN)
    await("the session restore") { vm.authRepo.restoreJob.isCompleted }
    shadowOf(Looper.getMainLooper()).idle()
    return vm
  }

  private fun room(title: String = "Title", anniversary: String = "09/09/2020", name: String = "Room Stale") = CoupleProfileEntity(
    partner1Name = name, partner1Birthday = "", partner1ProfilePicture = "",
    partner2Name = "Partner", partner2Birthday = "", partner2ProfilePicture = "",
    loveTitle = title, loveDays = 5, anniversaryDate = anniversary
  )

  private fun signIn(vm: InLoveViewModel) = runBlocking { vm.onlineRepo.setCurrentUserId("uid_me") }

  @Test
  fun guestWithRow_updateMyProfile_updatesRoomAndFlows() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room(name = "Old")) }
    val vm = newViewModel()
    vm.updateMyProfile("Smoke A", "01/01/2000", "", "FEMALE", "")
    await("boyName") { vm.boyName.value == "Smoke A" }
    assertEquals("Smoke A", runBlocking { db.inLoveDao().getCoupleProfileSync() }!!.partner1Name)
    assertTrue(vm.boyAge.value > 0)
  }

  @Test
  fun guestWithoutRow_updateMyProfile_updatesFlowsAndCreatesNoRow() {
    val vm = newViewModel()
    vm.updateMyProfile("Smoke A", "01/01/2000", "", "FEMALE", "")
    await("boyName") { vm.boyName.value == "Smoke A" }
    assertNull(runBlocking { db.inLoveDao().getCoupleProfileSync() })
  }

  @Test
  fun signedIn_staleRoomRow_doesNotClobberOnlineProfile() {
    val vm = newViewModel()
    signIn(vm)
    runBlocking { vm.onlineRepo.updateMyProfile("Online A", "01/01/2000", "", "MALE", "") }
    await("online name on the card") { vm.boyName.value == "Online A" }

    runBlocking { db.inLoveDao().insertCoupleProfile(room(title = "Stale Title")) }
    await("the Room collector to run") { vm.loveTitle.value == "Stale Title" }

    assertEquals("Online A", vm.boyName.value)
  }

  // me + partner + active relationship in Room, then the repository loads them (activeRelationship != null).
  private fun pairedViewModel(): InLoveViewModel {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", partnerId = "uid_p", relationshipId = "rel1",
          status = OnlineStatus.COUPLED, isProfileSetup = true
        )
      )
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true))
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_707_868_800_000L, startDateText = "14/02/2024")
      )
    }
    val vm = newViewModel()
    signIn(vm)
    await("the relationship date on the VM") { vm.activeRelationship.value != null && vm.anniversaryDate.value == "14/02/2024" }
    return vm
  }

  @Test
  fun paired_setAnniversary_isRefused() {
    val vm = pairedViewModel()
    vm.clearToast()
    vm.setAnniversaryAndRecalculateDays("01/01/2025")
    await("a toast") { vm.toastMessage.value != null }
    assertEquals("Your love date syncs from pairing and can't be changed here", vm.toastMessage.value)
    assertEquals("14/02/2024", vm.anniversaryDate.value)
  }

  @Test
  fun paired_roomRow_doesNotOverwriteRelationshipDate() {
    val vm = pairedViewModel()
    runBlocking { db.inLoveDao().insertCoupleProfile(room(title = "Stale Title", anniversary = "09/09/2020")) }
    await("the Room collector to run") { vm.loveTitle.value == "Stale Title" }
    assertEquals("14/02/2024", vm.anniversaryDate.value)
  }

  @Test
  fun signedIn_placeholderName_showsEmptyBoyName() {
    runBlocking {
      db.inLoveDao().insertOnlineUser(
        OnlineUserEntity(uid = "uid_me", displayName = "Vô danh", coupleCode = "MMMM-1111", isProfileSetup = true)
      )
    }
    val vm = newViewModel()
    signIn(vm)
    await("the sign-in") { vm.currentOnlineUser.value.uid == "uid_me" }
    assertEquals("", vm.boyName.value)
  }

  // me (single) + an ex partner + a TERMINATED relationship + the ex's Room couple row, then the repository loads them.
  private fun endedViewModel(): InLoveViewModel {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", isProfileSetup = true))
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true))
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(
          relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_600_000_000_000L,
          startDateText = "09/09/2020", status = RelationshipStatus.TERMINATED
        )
      )
      dao.insertCoupleProfile(room(title = "Title", anniversary = "09/09/2020"))
    }
    val vm = newViewModel()
    signIn(vm)
    await("the Room collector to run") { vm.loveTitle.value == "Title" && vm.currentOnlineUser.value.uid == "uid_me" }
    return vm
  }

  @Test
  fun endedRelationship_hidesExCardAndDate_butKeepsRoomRow() {
    val vm = endedViewModel()
    await("the ex card to be empty") { vm.girlName.value == "" && vm.loveDays.value == 1 }
    assertEquals("Hôm nay", vm.anniversaryDate.value)
    assertEquals("Partner", runBlocking { db.inLoveDao().getCoupleProfileSync() }!!.partner2Name)
  }

  @Test
  fun endedRelationship_samePartnerRepairs_restoresCardAndDate() {
    val vm = endedViewModel()
    await("the love date reset") { vm.anniversaryDate.value == "Hôm nay" }
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel2", user1 = "uid_me", user2 = "uid_p", startDate = 1_707_868_800_000L, startDateText = "14/02/2024")
      )
      dao.insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", partnerId = "uid_p", relationshipId = "rel2",
          status = OnlineStatus.COUPLED, isProfileSetup = true
        )
      )
      vm.onlineRepo.refreshState()
    }
    await("the new relationship date") { vm.anniversaryDate.value == "14/02/2024" }
    await("the partner on the card") { vm.girlName.value == "Pat" }
  }

  @Test
  fun pendingBreakup_withoutActiveRelationship_roomDoesNotFeedLoveDate() {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", isProfileSetup = true))
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(
          relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_707_868_800_000L,
          startDateText = "14/02/2024", status = RelationshipStatus.PENDING_BREAKUP
        )
      )
    }
    val vm = newViewModel()
    signIn(vm)
    runBlocking { db.inLoveDao().insertCoupleProfile(room(title = "Title", anniversary = "09/09/2020")) }
    await("the Room collector to run") { vm.loveTitle.value == "Title" }
    assertNotEquals("09/09/2020", vm.anniversaryDate.value)
  }

  private fun roomRow() = runBlocking { db.inLoveDao().getCoupleProfileSync() }

  @Test
  fun guestOnboarding_createsRowAndOnlineName() {
    val vm = newViewModel()
    vm.completeOnboardingProfile("An", "Binh", "Bên Nhau Trọn Đời")
    await("the Room row") { roomRow() != null && roomRow()!!.partner1Name == "An" }
    val row = roomRow()!!
    assertEquals("An", row.partner1Name)
    assertEquals("Binh", row.partner2Name)
    assertEquals("Bên Nhau Trọn Đời", row.loveTitle)
    assertEquals("", row.partner1Birthday)
    assertEquals("An", vm.currentOnlineUser.value.effectiveDisplayName)
    await("boyName") { vm.boyName.value == "An" }
    await("girlName") { vm.girlName.value == "Binh" }
  }

  @Test
  fun guestOnboarding_partnerOnly_keepsMeBlank() {
    val vm = newViewModel()
    vm.completeOnboardingProfile("", "Binh", "T")
    await("the Room row") { roomRow()?.partner2Name == "Binh" }
    assertEquals("", roomRow()!!.partner1Name)
    assertEquals("", vm.currentOnlineUser.value.effectiveDisplayName)
  }

  @Test
  fun guestOnboarding_existingRow_keepsPreferences() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room(name = "Old").copy(likesCsv = "a,b")) }
    val vm = newViewModel()
    vm.completeOnboardingProfile("An", "Binh", "T")
    await("partner2Name") { roomRow()?.partner2Name == "Binh" }
    await("partner1Name mirrored") { roomRow()?.partner1Name == "An" }
    assertEquals("a,b", roomRow()!!.likesCsv)
  }

  @Test
  fun paired_saveCoupleProfile_changesNothing() {
    val vm = pairedViewModel()
    runBlocking { db.inLoveDao().insertCoupleProfile(room()) }
    vm.clearToast()
    vm.saveCoupleProfile("Other", "", "", 0, "", "New Title", 0, "")
    await("a toast") { vm.toastMessage.value != null }
    assertEquals("You're paired, so the couple profile syncs from pairing and can't be edited here", vm.toastMessage.value)
    val row = roomRow()!!
    assertEquals("Partner", row.partner2Name)
    assertEquals("Title", row.loveTitle)
  }

  @Test
  fun unpaired_saveCoupleProfile_canClearPartnerBirthday() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room().copy(partner2Birthday = "01/01/2000", partner2Zodiac = "Capricorn")) }
    val vm = newViewModel()
    vm.saveCoupleProfile(
      girl = "Partner", girlBirth = "", girlAvatar = "", girlA = 0, girlZod = "",
      title = "Title", days = 5, anniversary = "09/09/2020"
    )
    await("the cleared partner birthday") { roomRow()?.partner2Birthday == "" && roomRow()?.partner2Zodiac == "" }
    await("girlBirthDate") { vm.girlBirthDate.value == "" }
  }

  // A-6: an old stored birthday that no longer parses is kept verbatim (with its age/zodiac) when the user did not change it.
  @Test
  fun unpaired_saveCoupleProfile_unchangedUnparseableBirthday_isKeptVerbatim() {
    runBlocking {
      db.inLoveDao().insertCoupleProfile(room().copy(partner2Birthday = "5/5/95", partner2Age = 31, partner2Zodiac = "Kim Ngưu"))
    }
    val vm = newViewModel()
    vm.saveCoupleProfile(
      girl = "Partner", girlBirth = "5/5/95", girlAvatar = "", girlA = 0, girlZod = "",
      title = "Title2", days = 5, anniversary = "09/09/2020"
    )
    await("the save") { roomRow()?.loveTitle == "Title2" }
    val row = roomRow()!!
    assertEquals("5/5/95", row.partner2Birthday)
    assertEquals(31, row.partner2Age)
    assertEquals("Kim Ngưu", row.partner2Zodiac)
    await("girlBirthDate") { vm.girlBirthDate.value == "5/5/95" }
  }

  @Test
  fun unpaired_saveCoupleProfile_garbageBirthday_isSavedBlank() {
    runBlocking {
      db.inLoveDao().insertCoupleProfile(room().copy(partner2Birthday = "5/5/95", partner2Age = 31, partner2Zodiac = "Kim Ngưu"))
    }
    val vm = newViewModel()
    vm.saveCoupleProfile(
      girl = "Partner", girlBirth = "garbage", girlAvatar = "", girlA = 0, girlZod = "",
      title = "Title2", days = 5, anniversary = "09/09/2020"
    )
    await("the save") { roomRow()?.loveTitle == "Title2" }
    assertEquals("", roomRow()!!.partner2Birthday)
    assertEquals("", roomRow()!!.partner2Zodiac)
  }

  @Test
  fun saveCoupleProfile_blankTitle_usesDefaultTitle() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room()) }
    val vm = newViewModel()
    vm.saveCoupleProfile("Partner", "", "", 0, "", "  ", 0, "")
    await("the default title") { roomRow()?.loveTitle == "Hành Trình Yêu Thương" }
  }

  @Test
  fun saveCoupleProfile_invalidDate_toastsAndWritesNothing() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room()) }
    val vm = newViewModel()
    vm.clearToast()
    vm.saveCoupleProfile("Other", "", "", 0, "", "New Title", 0, "31/02/2020")
    await("a toast") { vm.toastMessage.value != null }
    assertEquals("Invalid date (dd/MM/yyyy, not in the future)", vm.toastMessage.value)
    val row = roomRow()!!
    assertEquals("Partner", row.partner2Name)
    assertEquals("Title", row.loveTitle)
  }

  @Test
  fun guestWithoutRow_saveCoupleProfile_keepsMeCard() {
    val vm = newViewModel()
    vm.updateMyProfile("Smoke A", "01/01/2000", "", "FEMALE", "")
    await("boyName") { vm.boyName.value == "Smoke A" }
    vm.saveCoupleProfile(girl = "Binh", girlBirth = "", girlAvatar = "", girlA = 0, girlZod = "", title = "Title", days = 0)
    await("the Room row") { roomRow()?.partner2Name == "Binh" }
    assertEquals("Smoke A", roomRow()!!.partner1Name)
    await("the collector") { vm.girlName.value == "Binh" }
    assertEquals("Smoke A", vm.boyName.value)
  }

  private fun daysAgo(n: Long): String =
    java.time.LocalDate.now().minusDays(n).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  private val invalidDateToast = "Invalid date (dd/MM/yyyy, not in the future)"

  @Test
  fun guestRow_loveDaysComeFromTheStoredDate() {
    runBlocking { db.inLoveDao().insertCoupleProfile(room(anniversary = daysAgo(10)).copy(loveDays = 3)) }
    val vm = newViewModel()
    await("the love days from the date") { vm.loveDays.value == 11 }
  }

  private fun assertInvalidAnniversaryWritesNothing(input: String) {
    runBlocking { db.inLoveDao().insertCoupleProfile(room()) }
    val vm = newViewModel()
    await("the Room row on the flows") { vm.anniversaryDate.value == "09/09/2020" }
    val daysBefore = vm.loveDays.value
    vm.clearToast()
    vm.setAnniversaryAndRecalculateDays(input)
    await("a toast") { vm.toastMessage.value != null }
    assertEquals(invalidDateToast, vm.toastMessage.value)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("09/09/2020", roomRow()!!.anniversaryDate)
    assertEquals(5, roomRow()!!.loveDays)
    assertEquals("09/09/2020", vm.anniversaryDate.value)
    assertEquals(daysBefore, vm.loveDays.value)
  }

  @Test
  fun setAnniversary_futureDate_toastsAndWritesNothing() =
    assertInvalidAnniversaryWritesNothing(java.time.LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))

  @Test
  fun setAnniversary_invalidDate_toastsAndWritesNothing() = assertInvalidAnniversaryWritesNothing("31/02/2020")

  @Test
  fun setAnniversary_blankDate_toastsAndWritesNothing() = assertInvalidAnniversaryWritesNothing("")

  @Test
  fun setAnniversary_garbageDate_toastsAndWritesNothing() = assertInvalidAnniversaryWritesNothing("abc")

  @Test
  fun setAnniversary_noRow_createsRowWithRealDateAndNoPlaceholder() {
    val vm = newViewModel()
    val date = daysAgo(5)
    vm.clearToast()
    vm.setAnniversaryAndRecalculateDays(date)
    await("the Room row") { roomRow() != null }
    val row = roomRow()!!
    assertEquals(date, row.anniversaryDate)
    assertEquals(6, row.loveDays)
    assertEquals("", row.partner1Name)
    assertEquals("", row.partner2Name)
    await("the date on the flows") { vm.anniversaryDate.value == date && vm.loveDays.value == 6 }
    await("the success toast") { vm.toastMessage.value != null }
    assertEquals("Recalculated: 6 days together since $date! 💕", vm.toastMessage.value)
  }

  @Test
  fun setAnniversary_noRow_keepsTheGuestMeCard() {
    val vm = newViewModel()
    vm.updateMyProfile("Smoke A", "01/01/2000", "", "FEMALE", "")
    await("boyName") { vm.boyName.value == "Smoke A" }
    vm.setAnniversaryAndRecalculateDays(daysAgo(5))
    await("the Room row") { roomRow() != null }
    await("the Room collector pass") { vm.anniversaryDate.value == daysAgo(5) && vm.loveTitle.value == DEFAULT_LOVE_TITLE }
    assertEquals("Smoke A", roomRow()!!.partner1Name)
    assertEquals("Smoke A", vm.boyName.value)
  }

  @Test
  fun updateLoveTitle_noRow_createsRowWithoutPlaceholders() {
    val vm = newViewModel()
    vm.clearToast()
    vm.updateLoveTitle("Our Story")
    await("the Room row") { roomRow() != null }
    val row = roomRow()!!
    assertEquals("Our Story", row.loveTitle)
    assertEquals("", row.anniversaryDate)
    assertEquals(0, row.loveDays)
    await("the title on the flow") { vm.loveTitle.value == "Our Story" }
    await("a toast") { vm.toastMessage.value != null }
    assertTrue(vm.toastMessage.value!!.contains("Our Story"))
  }

  @Test
  fun updateLoveTitle_blank_storesDefaultTitle() {
    val vm = newViewModel()
    vm.updateLoveTitle("   ")
    await("the Room row") { roomRow() != null }
    assertEquals(DEFAULT_LOVE_TITLE, roomRow()!!.loveTitle)
    assertEquals(DEFAULT_LOVE_TITLE, vm.loveTitle.value)
  }

  @Test
  fun updateLoveTitle_whilePaired_isAllowed() {
    val vm = pairedViewModel()
    runBlocking { db.inLoveDao().insertCoupleProfile(room()) }
    vm.updateLoveTitle("Our Story")
    await("the title written") { roomRow()?.loveTitle == "Our Story" }
  }

  @Test
  fun refreshLoveDays_guestRow_keepsTheDateDays() {
    // ShadowSystemClock does not move System.currentTimeMillis(), so the midnight rollover itself is not testable here
    runBlocking { db.inLoveDao().insertCoupleProfile(room(anniversary = daysAgo(10))) }
    val vm = newViewModel()
    await("the love days from the date") { vm.loveDays.value == 11 }
    vm.refreshLoveDays()
    assertEquals(11, vm.loveDays.value)
  }

  @Test
  fun refreshLoveDays_ended_keepsTheHiddenState() {
    val vm = endedViewModel()
    await("the ex card to be empty") { vm.girlName.value == "" && vm.loveDays.value == 1 }
    vm.refreshLoveDays()
    assertEquals(1, vm.loveDays.value)
    assertEquals("Hôm nay", vm.anniversaryDate.value)
  }

  @Test
  fun refreshLoveDays_paired_keepsTheRelationshipDate() {
    val vm = pairedViewModel()
    vm.refreshLoveDays()
    assertEquals("14/02/2024", vm.anniversaryDate.value)
    assertTrue(vm.loveDays.value > 0)
  }

  private fun insertEndedRelationshipAndRow() = runBlocking {
    val dao = db.inLoveDao()
    dao.insertOnlineUser(OnlineUserEntity(uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", isProfileSetup = true))
    dao.insertOnlineUser(OnlineUserEntity(uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true))
    dao.insertOnlineRelationship(
      OnlineRelationshipEntity(
        relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_600_000_000_000L,
        startDateText = "09/09/2020", status = RelationshipStatus.TERMINATED
      )
    )
    dao.insertCoupleProfile(room(title = "Title", anniversary = "09/09/2020"))
  }

  @Test
  fun endedRelationship_roomPassBeforeSignIn_neverShowsExCard() {
    insertEndedRelationshipAndRow()
    val vm = newViewModel() // still a guest: the Room collector runs before the account is restored
    await("the Room collector (still a guest)") { vm.loveTitle.value == "Title" }
    assertEquals("", vm.girlName.value)
    signIn(vm)
    await("the sign-in") { vm.currentOnlineUser.value.uid == "uid_me" }
    assertEquals("", vm.girlName.value)
    assertEquals("Partner", roomRow()!!.partner2Name)
  }

  @Test
  fun activeThenTerminated_clearsTheRoomFedCard() {
    runBlocking {
      db.inLoveDao().insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_600_000_000_000L, startDateText = "09/09/2020")
      )
      db.inLoveDao().insertCoupleProfile(room())
    }
    val vm = newViewModel()
    await("the Room-fed partner") { vm.girlName.value == "Partner" }
    runBlocking {
      db.inLoveDao().insertOnlineRelationship(
        OnlineRelationshipEntity(
          relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_600_000_000_000L,
          startDateText = "09/09/2020", status = RelationshipStatus.TERMINATED
        )
      )
    }
    await("the ex card to clear") { vm.girlName.value == "" }
  }

  @Test
  fun guestThenSignIn_boyCardTakesTheOnlineNameAndKeepsBlankFieldsFromTheGuestCard() {
    runBlocking {
      db.inLoveDao().insertOnlineUser(OnlineUserEntity(uid = "uid_me", displayName = "Online Me", coupleCode = "MMMM-1111", isProfileSetup = true))
      db.inLoveDao().insertCoupleProfile(
        room().copy(partner1Birthday = "05/05/1990", partner1Age = 36, partner1Zodiac = "Kim Ngưu", partner1ProfilePicture = "guest.png")
      )
    }
    val vm = newViewModel() // still a guest: the Room row feeds the boy card
    await("the guest card from Room") { vm.boyName.value == "Room Stale" }
    assertEquals("05/05/1990", vm.boyBirthDate.value)
    signIn(vm)
    await("the online name on the card") { vm.boyName.value == "Online Me" }
    // the online profile has no birthday / age / zodiac / avatar yet: the card keeps what it showed (profileFieldAfter, first application)
    assertEquals("05/05/1990", vm.boyBirthDate.value)
    assertEquals(36, vm.boyAge.value)
    assertEquals("Kim Ngưu", vm.boyZodiac.value)
    assertEquals("guest.png", vm.boyAvatarUrl.value)
    assertEquals("Room Stale", roomRow()!!.partner1Name) // signing in never writes the Room row
  }

  // A real cold start cannot be reordered in a test (the Room pass runs as a guest before the restore ends): the signed-in state first, the row second.
  @Test
  fun signedInNeverPaired_roomRowArrives_girlCardStaysEmptyAndRowIsKept() {
    runBlocking {
      db.inLoveDao().insertOnlineUser(OnlineUserEntity(uid = "uid_me", displayName = "Online Me", coupleCode = "MMMM-1111", isProfileSetup = true))
    }
    val vm = newViewModel()
    signIn(vm)
    await("the online name on the card") { vm.boyName.value == "Online Me" }
    runBlocking { db.inLoveDao().insertCoupleProfile(room(title = "Title", anniversary = "09/09/2020")) }
    await("the Room collector to run") { vm.loveTitle.value == "Title" }
    assertEquals("Online Me", vm.boyName.value) // the stale Room "me" does not clobber the online profile
    assertEquals("", vm.girlName.value) // a signed-in user's partner card never comes from Room
    assertEquals("09/09/2020", vm.anniversaryDate.value) // never paired: Room still feeds the love date
    assertEquals("Partner", roomRow()!!.partner2Name) // the row itself is kept
  }

  @Test
  fun signOutAfterPairing_resetsBothCardsToEmpty() {
    runBlocking {
      val dao = db.inLoveDao()
      dao.insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_me", displayName = "Me", coupleCode = "MMMM-1111", partnerId = "uid_p", relationshipId = "rel1",
          status = OnlineStatus.COUPLED, isProfileSetup = true, birthDate = "01/01/1990", age = 36, zodiac = "Ma Kết", avatarUrl = "me.png"
        )
      )
      dao.insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true,
          birthDate = "02/02/1992", age = 34, zodiac = "Bảo Bình", avatarUrl = "pat.png"
        )
      )
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel1", user1 = "uid_me", user2 = "uid_p", startDate = 1_707_868_800_000L, startDateText = "14/02/2024")
      )
    }
    val vm = newViewModel()
    signIn(vm)
    await("both cards") { vm.boyName.value == "Me" && vm.girlName.value == "Pat" }
    assertEquals(36, vm.boyAge.value)
    assertEquals(34, vm.girlAge.value)
    runBlocking { vm.onlineRepo.setCurrentUserId("") } // sign out
    await("both cards to clear") { vm.boyName.value == "" && vm.girlName.value == "" }
    assertEquals(listOf("", "", ""), listOf(vm.boyBirthDate.value, vm.boyZodiac.value, vm.boyAvatarUrl.value))
    assertEquals(listOf("", "", ""), listOf(vm.girlBirthDate.value, vm.girlZodiac.value, vm.girlAvatarUrl.value))
    assertEquals(0, vm.boyAge.value)
    assertEquals(0, vm.girlAge.value)
  }

  // ---- an ended couple keeps its hidden ex data: offline edits never overwrite it ----

  // loveEnded is set by the milestone collector, a separate coroutine from the Room card collector that endedViewModel() waits for:
  // wait for it (null = ended; the epoch never moves in this class) so the guards under test see the ended state.
  private fun endedViewModelSettled(): InLoveViewModel {
    val vm = endedViewModel()
    await("the milestone collector (ended)") { vm.loveMilestoneArgsIfFresh() == null }
    return vm
  }

  @Test
  fun ended_setAnniversary_isRefusedAndWritesNothing() {
    val vm = endedViewModelSettled()
    vm.clearToast()
    vm.setAnniversaryAndRecalculateDays(daysAgo(5))
    await("a toast") { vm.toastMessage.value != null }
    assertEquals("No active relationship right now.", vm.toastMessage.value)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("09/09/2020", roomRow()!!.anniversaryDate)
    assertEquals(5, roomRow()!!.loveDays)
    assertEquals("Hôm nay", vm.anniversaryDate.value)
  }

  @Test
  fun ended_saveCoupleProfile_writesOnlyTheTitle() {
    val vm = endedViewModelSettled()
    vm.openEditCoupleDialog()
    vm.clearToast()
    // what the dialog sends when the user only renames the journey: the hidden ex card and date show blank
    vm.saveCoupleProfile(girl = "", girlBirth = "", girlAvatar = "", girlA = 0, girlZod = "", title = "New Title", days = 0, anniversary = "")
    await("the title written") { roomRow()?.loveTitle == "New Title" }
    val row = roomRow()!!
    assertEquals("Partner", row.partner2Name) // the hidden ex partner and date stay for a re-pair
    assertEquals("09/09/2020", row.anniversaryDate)
    assertEquals(5, row.loveDays)
    await("the dialog to close") { !vm.showEditCoupleDialog.value }
    assertEquals("", vm.girlName.value)
    assertEquals("Hôm nay", vm.anniversaryDate.value)
  }

  // "Hôm nay" is the ViewModel's stand-in for "no date yet"; normalizeAnniversaryInput refuses it, so an ended Save must not validate the date.
  @Test
  fun ended_saveCoupleProfile_ignoresTheDateText() {
    val vm = endedViewModelSettled()
    vm.openEditCoupleDialog()
    vm.saveCoupleProfile(girl = "", girlBirth = "", girlAvatar = "", girlA = 0, girlZod = "", title = "New Title", days = 0, anniversary = "Hôm nay")
    await("the title written") { roomRow()?.loveTitle == "New Title" }
    await("the dialog to close") { !vm.showEditCoupleDialog.value }
    assertEquals("09/09/2020", roomRow()!!.anniversaryDate)
  }
}
