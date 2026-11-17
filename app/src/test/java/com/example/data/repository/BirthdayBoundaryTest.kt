package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineUserEntity
import com.example.ui.util.ProfileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T24: ngày sinh đi vào mô hình qua Birthday ở updateMyProfile và khi nhận lời mời; đường đổi tên giữ nguyên giá trị đã lưu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BirthdayBoundaryTest {

  private fun withRepo(
    storedBirthday: String = "",
    block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit
  ) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(
      db.inLoveDao(), context, scope, useFirestore = false, functions = FakeCloudFunctionsClient().apply { enqueue(emptyMap()) }
    )
    db.inLoveDao().insertOnlineUser(
      OnlineUserEntity(
        uid = "uid_b", displayName = "Be", coupleCode = "BBBB-2222", isCurrentUser = true, isProfileSetup = true,
        birthDate = storedBirthday,
        age = ProfileUtils.calculateAge(storedBirthday),
        zodiac = ProfileUtils.calculateZodiac(storedBirthday).first
      )
    )
    repo.setCurrentUserId("uid_b")
    try {
      block(repo, db)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  private suspend fun storedMe(db: AppDatabase) = db.inLoveDao().getOnlineUserByUidSync("uid_b")!!

  @Test
  fun updateMyProfile_isoBirthday_storesCanonicalWithMatchingAgeAndZodiac() {
    withRepo { repo, db ->
      repo.updateMyProfile("Lan", "1995-05-05", "", "FEMALE", "")
      val me = storedMe(db)
      assertEquals("05/05/1995", me.birthDate)
      assertEquals(ProfileUtils.calculateAge("05/05/1995"), me.age)
      assertTrue(me.age > 0)
      assertEquals(ProfileUtils.calculateZodiac("05/05/1995").first, me.zodiac)
    }
  }

  private fun inviteWithSenderBirthDate(raw: String) = OnlineInviteEntity(
    inviteId = "inv_birth_1", senderUid = "uid_a", senderName = "An", senderCoupleCode = "AAAA-1111",
    senderBirthDate = raw, targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
  )

  private fun senderBirthdayAfterAccept(raw: String): String {
    var result = "?"
    withRepo { repo, db ->
      // Không có hàng người gửi trong Room: hàng được dựng từ lời mời nên đi qua điểm nhận cần kiểm.
      db.inLoveDao().insertOnlineInvite(inviteWithSenderBirthDate(raw))
      val (ok, _) = repo.acceptSetLoveInvite("inv_birth_1", confirmedStartDateMillis = 1_700_000_000_000L)
      assertTrue(ok)
      result = db.inLoveDao().getOnlineUserByUidSync("uid_a")!!.birthDate
    }
    return result
  }

  @Test
  fun inviteIngest_isoBirthday_storesCanonical() {
    assertEquals("05/05/1995", senderBirthdayAfterAccept("1995-05-05"))
  }

  @Test
  fun inviteIngest_invalidRemoteBirthday_storesEmpty() {
    assertEquals("", senderBirthdayAfterAccept("not-a-date"))
  }

  @Test
  fun inviteIngest_ageAndZodiacComeFromTheBirthday_notFromTheInvite() {
    // T15/A-3: số tuổi và cung gửi kèm lời mời không được tin, chỉ ngày sinh đã chuẩn hoá mới là nguồn.
    listOf("1995-05-05", "not-a-date").forEach { raw ->
      withRepo { repo, db ->
        db.inLoveDao().insertOnlineInvite(inviteWithSenderBirthDate(raw).copy(senderAge = 99, senderZodiac = "Bogus"))
        assertTrue(repo.acceptSetLoveInvite("inv_birth_1", confirmedStartDateMillis = 1_700_000_000_000L).first)
        val row = db.inLoveDao().getOnlineUserByUidSync("uid_a")!!
        assertEquals(raw, ProfileUtils.calculateAge(row.birthDate), row.age)
        assertEquals(raw, ProfileUtils.calculateZodiac(row.birthDate).first, row.zodiac)
        assertTrue(raw, row.age != 99 && row.zodiac != "Bogus")
      }
    }
  }

  @Test
  fun rename_storedBlankOrGarbageBirthday_keepsBirthdayAgeAndZodiac() {
    listOf("05/05/1995", "5/5/95").forEach { stored ->
      listOf(stored, "", "  ", "garbage").forEach { sent ->
        withRepo(storedBirthday = stored) { repo, db ->
          val before = storedMe(db)
          repo.updateMyProfile("Lan Moi", sent, "", "FEMALE", "")
          val after = storedMe(db)
          assertEquals("stored=$stored sent=$sent", stored, after.birthDate)
          assertEquals("stored=$stored sent=$sent", before.age, after.age)
          assertEquals("stored=$stored sent=$sent", before.zodiac, after.zodiac)
          assertEquals("Lan Moi", after.displayName)
        }
      }
    }
  }

  @Test
  fun updateMyProfile_clearBirthday_blanksBirthdayAgeZodiacAndCoupleProfile() {
    withRepo(storedBirthday = "05/05/1995") { repo, db ->
      db.inLoveDao().insertCoupleProfile(
        CoupleProfileEntity(
          partner1Name = "Be", partner1Birthday = "05/05/1995", partner1ProfilePicture = "",
          partner2Name = "An", partner2Birthday = "", partner2ProfilePicture = ""
        )
      )
      assertTrue(storedMe(db).age > 0)
      repo.updateMyProfile("Be", "05/05/1995", "", "FEMALE", "", clearBirthday = true)
      val me = storedMe(db)
      assertEquals("", me.birthDate)
      assertEquals(0, me.age)
      assertEquals(ProfileUtils.calculateZodiac("").first, me.zodiac)
      val couple = db.inLoveDao().getCoupleProfileSync()!!
      assertEquals("", couple.partner1Birthday)
      assertEquals(0, couple.partner1Age)
    }
  }
}
