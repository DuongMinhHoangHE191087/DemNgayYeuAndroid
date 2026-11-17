package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.UserAccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D1/D2: Room partner1* luôn là "tôi", partner2* là người ấy (không theo giới tính); updateMyProfile là nơi duy nhất ghi "tôi". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineCoupleProfileSlotTest {

  private val seedProfile = CoupleProfileEntity(
    partner1Name = "OldMe", partner1Birthday = "01/01/2000", partner1ProfilePicture = "old-me.png",
    partner1Age = 20, partner1Zodiac = "OldZ1",
    partner2Name = "Partner", partner2Birthday = "02/02/2001", partner2ProfilePicture = "partner.png",
    partner2Age = 21, partner2Zodiac = "OldZ2",
    loveTitle = "Title", loveDays = 77, anniversaryDate = "03/03/2020"
  )

  private fun withRepo(
    me: OnlineUserEntity,
    profile: CoupleProfileEntity?,
    block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit
  ) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false, functions = null)
    db.inLoveDao().insertOnlineUser(me)
    if (profile != null) db.inLoveDao().insertCoupleProfile(profile)
    repo.setCurrentUserId(me.uid)
    try {
      block(repo, db)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  private fun meUser(status: String = OnlineStatus.SINGLE, gender: String = "FEMALE") = OnlineUserEntity(
    uid = "uid_b", displayName = "Be", coupleCode = "BBBB-2222", isCurrentUser = true,
    isProfileSetup = true, gender = gender, status = status
  )

  @Test
  fun acceptInvite_femaleMe_isPartner1_senderIsPartner2() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<android.content.Context>()
      val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
      val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
      val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false, functions = null)
      db.inLoveDao().insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_a", displayName = "An", coupleCode = "AAAA-1111", isProfileSetup = true,
          gender = "MALE", status = OnlineStatus.PENDING_INVITE
        )
      )
      db.inLoveDao().insertOnlineUser(meUser())
      repo.setCurrentUserId("uid_b")
      db.inLoveDao().insertOnlineInvite(
        OnlineInviteEntity(
          inviteId = "inv_slot_1", senderUid = "uid_a", senderCoupleCode = "AAAA-1111",
          targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
        )
      )
      try {
        val (ok, _) = repo.acceptSetLoveInvite("inv_slot_1", confirmedStartDateMillis = 1_700_000_000_000L)
        assertEquals(true, ok)
        val row = db.inLoveDao().getCoupleProfileSync()!!
        assertEquals("Be", row.partner1Name)
        assertEquals("An", row.partner2Name)
      } finally {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
      }
    }
  }

  @Test
  fun updateMyProfile_coupledFemale_changesPartner1Only() {
    withRepo(meUser(OnlineStatus.COUPLED, "FEMALE"), seedProfile) { repo, db ->
      repo.updateMyProfile("Lan", "05/05/1999", "lan.png", "FEMALE", "")
      val row = db.inLoveDao().getCoupleProfileSync()!!
      assertEquals("Lan", row.partner1Name)
      assertEquals("05/05/1999", row.partner1Birthday)
      assertEquals("lan.png", row.partner1ProfilePicture)
      assertEquals(seedProfile.partner2Name, row.partner2Name)
      assertEquals(seedProfile.partner2Birthday, row.partner2Birthday)
      assertEquals(seedProfile.partner2ProfilePicture, row.partner2ProfilePicture)
      assertEquals(seedProfile.partner2Age, row.partner2Age)
      assertEquals(seedProfile.partner2Zodiac, row.partner2Zodiac)
    }
  }

  @Test
  fun updateMyProfile_notCoupled_rowExists_changesPartner1Only() {
    withRepo(meUser(OnlineStatus.SINGLE, "MALE"), seedProfile) { repo, db ->
      repo.updateMyProfile("Minh", "06/06/1998", "minh.png", "MALE", "")
      val row = db.inLoveDao().getCoupleProfileSync()!!
      assertEquals("Minh", row.partner1Name)
      assertEquals("06/06/1998", row.partner1Birthday)
      assertEquals("minh.png", row.partner1ProfilePicture)
      assertEquals(seedProfile.partner2Name, row.partner2Name)
      assertEquals(seedProfile.partner2ProfilePicture, row.partner2ProfilePicture)
      assertEquals(seedProfile.loveTitle, row.loveTitle)
      assertEquals(seedProfile.loveDays, row.loveDays)
      assertEquals(seedProfile.anniversaryDate, row.anniversaryDate)
    }
  }

  @Test
  fun updateMyProfile_noRow_doesNotCreateOne() {
    withRepo(meUser(), profile = null) { repo, db ->
      repo.updateMyProfile("Lan", "05/05/1999", "lan.png", "FEMALE", "")
      assertNull(db.inLoveDao().getCoupleProfileSync())
    }
  }

  @Test
  fun updateMyProfile_blankName_mirrorsEmptyNameIntoCoupleProfile() {
    withRepo(meUser(), seedProfile) { repo, db ->
      repo.updateMyProfile("  ", "05/05/1999", "", "FEMALE", "")
      assertEquals("", db.inLoveDao().getCoupleProfileSync()!!.partner1Name)
    }
  }

  @Test
  fun updateMyProfile_keepsStatusWrittenToRoomAfterCacheLoaded() {
    withRepo(meUser(OnlineStatus.SINGLE), seedProfile) { repo, db ->
      // Lớp đồng bộ ghi trạng thái ghép đôi vào Room sau khi cache của repo đã nạp (vẫn SINGLE).
      db.inLoveDao().updateOnlineUser(
        meUser(OnlineStatus.COUPLED).copy(partnerId = "uid_a", relationshipId = "rel_sync")
      )
      repo.updateMyProfile("Lan", "05/05/1999", "lan.png", "FEMALE", "")
      val row = db.inLoveDao().getOnlineUserByUidSync("uid_b")!!
      assertEquals(OnlineStatus.COUPLED, row.status)
      assertEquals("uid_a", row.partnerId)
      assertEquals("rel_sync", row.relationshipId)
      assertEquals("Lan", row.displayName)
    }
  }

  @Test
  fun updateMyProfile_realName_updatesLoginAccountDisplayName() {
    withRepo(meUser(), seedProfile) { repo, db ->
      db.inLoveDao().insertUserAccount(
        UserAccountEntity(
          uid = "uid_b", email = "be@example.com", passwordHash = "h", salt = "s",
          displayName = "OldName", coupleCode = "BBBB-2222"
        )
      )
      repo.updateMyProfile("Lan", "05/05/1999", "lan.png", "FEMALE", "")
      assertEquals("Lan", db.inLoveDao().getUserAccountByUid("uid_b")!!.displayName)
    }
  }

  @Test
  fun acceptInvite_maleMe_isPartner1_andKeepsProfileFieldsAndGiftPrefs() {
    val me = meUser(gender = "MALE").copy(
      birthDate = "04/04/1997", age = 29, zodiac = "Bạch Dương", avatarUrl = "be.png"
    )
    val existing = seedProfile.copy(likesCsv = "hoa,sách", budgetMaxVnd = 500_000L, occasionRegion = "HN")
    withRepo(me, existing) { repo, db ->
      db.inLoveDao().insertOnlineUser(
        OnlineUserEntity(
          uid = "uid_a", displayName = "An", coupleCode = "AAAA-1111", isProfileSetup = true, gender = "FEMALE",
          status = OnlineStatus.PENDING_INVITE, birthDate = "08/08/1998", age = 28, zodiac = "Sư Tử", avatarUrl = "an.png"
        )
      )
      db.inLoveDao().insertOnlineInvite(
        OnlineInviteEntity(
          inviteId = "inv_slot_2", senderUid = "uid_a", senderCoupleCode = "AAAA-1111",
          targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
        )
      )
      val (ok, _) = repo.acceptSetLoveInvite("inv_slot_2", confirmedStartDateMillis = 1_700_000_000_000L)
      assertEquals(true, ok)
      val row = db.inLoveDao().getCoupleProfileSync()!!
      // partner1* is "me" even though me is MALE
      assertEquals("Be", row.partner1Name)
      assertEquals("04/04/1997", row.partner1Birthday)
      assertEquals(29, row.partner1Age)
      assertEquals("Bạch Dương", row.partner1Zodiac)
      assertEquals("be.png", row.partner1ProfilePicture)
      // partner2* is the sender
      assertEquals("An", row.partner2Name)
      assertEquals("08/08/1998", row.partner2Birthday)
      assertEquals(28, row.partner2Age)
      assertEquals("Sư Tử", row.partner2Zodiac)
      assertEquals("an.png", row.partner2ProfilePicture)
      // the REPLACE-insert keeps what the user typed before pairing
      assertEquals("hoa,sách", row.likesCsv)
      assertEquals(500_000L, row.budgetMaxVnd)
      assertEquals("HN", row.occasionRegion)
      assertEquals(existing.loveTitle, row.loveTitle)
    }
  }
}
