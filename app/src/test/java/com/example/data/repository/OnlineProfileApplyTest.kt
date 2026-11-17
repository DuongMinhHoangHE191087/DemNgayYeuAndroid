package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineUserEntity
import com.example.data.model.UserAccountEntity
import com.example.data.sync.ProfileProjection
import com.example.ui.util.ProfileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P13: applyRemoteProfile áp dụng bản chiếu từ xa lên dòng "tôi" (chỉ trường khác, avatar theo B-02) rồi chép sang account + couple profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineProfileApplyTest {

  private val seedProfile = CoupleProfileEntity(
    partner1Name = "OldMe", partner1Birthday = "01/01/2000", partner1ProfilePicture = "old-me.png",
    partner1Age = 20, partner1Zodiac = "OldZ1",
    partner2Name = "Partner", partner2Birthday = "02/02/2001", partner2ProfilePicture = "partner.png",
    partner2Age = 21, partner2Zodiac = "OldZ2",
    loveTitle = "Title", loveDays = 77, anniversaryDate = "03/03/2020"
  )

  private fun meRow(
    displayName: String = "Bạn",
    isProfileSetup: Boolean = false,
    birthDate: String = "",
    bio: String = "Chào mừng bạn đến với InLove",
    avatarUrl: String = "",
    gender: String = "MALE"
  ) = OnlineUserEntity(
    uid = "uid_b", displayName = displayName, email = "be@example.com", coupleCode = "BBBB-2222",
    isCurrentUser = true, isProfileSetup = isProfileSetup, birthDate = birthDate, bio = bio,
    avatarUrl = avatarUrl, gender = gender,
    age = ProfileUtils.calculateAge(birthDate), zodiac = ProfileUtils.calculateZodiac(birthDate).first
  )

  private fun withRepo(
    me: OnlineUserEntity,
    block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit
  ) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false, functions = null)
    db.inLoveDao().insertOnlineUser(me)
    repo.setCurrentUserId(me.uid)
    try {
      block(repo, db)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  private suspend fun stored(db: AppDatabase) = db.inLoveDao().getOnlineUserByUidSync("uid_b")!!

  private suspend fun apply(repo: OnlineCoupleRepository, p: ProfileProjection, key: String) =
    withTimeout(5_000) { repo.applyRemoteProfile("uid_b", p, key) }

  @Test
  fun appliesFiveFields_recomputesAgeAndZodiac_mirrorsAccountAndCoupleProfile() {
    val me = meRow()
    withRepo(me) { repo, db ->
      db.inLoveDao().insertCoupleProfile(seedProfile)
      db.inLoveDao().insertUserAccount(
        UserAccountEntity(
          uid = "uid_b", email = "be@example.com", passwordHash = "h", salt = "s",
          displayName = "OldName", coupleCode = "BBBB-2222"
        )
      )
      val p = ProfileProjection("Lan", "05/05/1995", "FEMALE", "Xin chao", "coffee,music", null)
      assertTrue(apply(repo, p, ProfileProjection.of(me).key()))
      val row = stored(db)
      assertEquals("Lan", row.displayName)
      assertEquals("05/05/1995", row.birthDate)
      assertEquals("FEMALE", row.gender)
      assertEquals("Xin chao", row.bio)
      assertEquals("coffee,music", row.interestsCsv)
      assertEquals(ProfileUtils.calculateAge("05/05/1995"), row.age)
      assertEquals(ProfileUtils.calculateZodiac("05/05/1995").first, row.zodiac)
      assertTrue(row.isProfileSetup)
      assertEquals("be@example.com", row.email)
      assertEquals("BBBB-2222", row.coupleCode)
      assertEquals("Lan", db.inLoveDao().getUserAccountByUid("uid_b")!!.displayName)
      val couple = db.inLoveDao().getCoupleProfileSync()!!
      assertEquals("Lan", couple.partner1Name)
      assertEquals("05/05/1995", couple.partner1Birthday)
      assertEquals(seedProfile.partner2Name, couple.partner2Name)
      assertEquals("Lan", repo.currentUser.value.displayName)
    }
  }

  @Test
  fun rawLocalBirthday_remoteBlank_keepsRawBirthdayAgeAndZodiac() {
    val me = meRow(displayName = "Be", isProfileSetup = true, birthDate = "5/5/95")
    withRepo(me) { repo, db ->
      val p = ProfileProjection.of(me).copy(displayName = "Lan")
      assertEquals("", p.birthDate)
      assertTrue(apply(repo, p, ProfileProjection.of(me).key()))
      val row = stored(db)
      assertEquals("5/5/95", row.birthDate)
      assertEquals(me.age, row.age)
      assertEquals(me.zodiac, row.zodiac)
      assertEquals("Lan", row.displayName)
    }
  }

  @Test
  fun longLocalBio_remoteIsTruncatedCopy_keepsLongBio() {
    val longBio = "a".repeat(1500)
    val me = meRow(displayName = "Be", isProfileSetup = true, bio = longBio)
    withRepo(me) { repo, db ->
      val p = ProfileProjection.of(me).copy(displayName = "Lan", bio = longBio.take(1000))
      assertTrue(apply(repo, p, ProfileProjection.of(me).key()))
      assertEquals(longBio, stored(db).bio)
    }
  }

  @Test
  fun blankRemoteName_keepsLocalName() {
    val me = meRow(displayName = "Be", isProfileSetup = true)
    withRepo(me) { repo, db ->
      val p = ProfileProjection.of(me).copy(displayName = "")
      assertTrue(apply(repo, p, ProfileProjection.of(me).key()))
      assertEquals("Be", stored(db).displayName)
    }
  }

  @Test
  fun staleExpectedKey_returnsFalse_roomUnchanged() {
    val me = meRow(displayName = "Be", isProfileSetup = true)
    withRepo(me) { repo, db ->
      val before = stored(db)
      val p = ProfileProjection.of(me).copy(displayName = "Lan", bio = "khac")
      assertFalse(apply(repo, p, "not-the-key"))
      assertEquals(before, stored(db))
    }
  }

  @Test
  fun unknownUid_returnsFalse() {
    withRepo(meRow()) { repo, _ ->
      val p = ProfileProjection.of(meRow())
      assertFalse(withTimeout(5_000) { repo.applyRemoteProfile("nobody", p, p.key()) })
    }
  }

  @Test
  fun notCurrentUserRow_returnsFalse_roomUnchanged() {
    withRepo(meRow()) { repo, db ->
      val other = meRow().copy(uid = "uid_other", coupleCode = "OOOO-0000", isCurrentUser = false)
      db.inLoveDao().insertOnlineUser(other)
      val p = ProfileProjection.of(other).copy(displayName = "Lan")
      assertFalse(withTimeout(5_000) { repo.applyRemoteProfile("uid_other", p, ProfileProjection.of(other).key()) })
      assertEquals(other, db.inLoveDao().getOnlineUserByUidSync("uid_other"))
    }
  }

  @Test
  fun localFileAvatar_isKept_whenRemoteAvatarIsNullOrEmptyOrHttps() {
    val me = meRow(displayName = "Be", isProfileSetup = true, avatarUrl = "file:///data/avatar.jpg")
    withRepo(me) { repo, db ->
      listOf(null, "", "https://x/y.jpg").forEach { remote ->
        val p = ProfileProjection.of(stored(db)).copy(displayName = "Lan", avatarUrl = remote)
        assertTrue(apply(repo, p, ProfileProjection.of(stored(db)).key()))
        assertEquals("avatar=$remote", "file:///data/avatar.jpg", stored(db).avatarUrl)
      }
    }
  }

  @Test
  fun httpsAvatar_replacesBlankAndHttps_andEmptyRemoteClearsOnlyHttps() {
    val me = meRow(displayName = "Be", isProfileSetup = true, avatarUrl = "")
    withRepo(me) { repo, db ->
      suspend fun applyAvatar(remote: String?): String {
        val p = ProfileProjection.of(stored(db)).copy(avatarUrl = remote)
        assertTrue(apply(repo, p, ProfileProjection.of(stored(db)).key()))
        return stored(db).avatarUrl
      }
      assertEquals("https://x/a.jpg", applyAvatar("https://x/a.jpg"))
      assertEquals("https://x/b.jpg", applyAvatar("https://x/b.jpg"))
      assertEquals("https://x/b.jpg", applyAvatar(null))
      assertEquals("", applyAvatar(""))
    }
  }
}
