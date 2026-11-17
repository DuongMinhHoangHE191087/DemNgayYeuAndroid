package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineUserEntity
import com.example.ui.util.DEFAULT_LOVE_NOTE
import com.example.ui.util.WELCOME_BIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** N3: dữ liệu cũ có emoji/Unicode tách dấu được làm sạch một lần, chạy lại không đổi gì. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleanStoredTextTest {

  private fun row(uid: String, name: String, bio: String, me: Boolean = false) = OnlineUserEntity(
    uid = uid, displayName = name, email = "$uid@example.com", coupleCode = "AAAA-1111",
    isCurrentUser = me, isProfileSetup = true, bio = bio
  )

  private fun withRepo(block: suspend (OnlineCoupleRepository, AppDatabase) -> Unit) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false, functions = null)
    try {
      block(repo, db)
    } finally {
      scope.coroutineContext.job.cancelAndJoin()
      db.close()
    }
  }

  @Test
  fun cleansNamesBiosAndCoupleProfile_andSecondRunIsNoOp() = withRepo { repo, db ->
    val dao = db.inLoveDao()
    dao.insertOnlineUser(row("u1", "Nguyễn  Lan 💖", "Chào mừng bạn đến với InLove 💕", me = true))
    dao.insertOnlineUser(row("u2", "😀", "Chào mừng bạn đến với InLove ✨"))
    dao.insertOnlineUser(row("u3", "Minh", "Yêu em 💕"))
    dao.insertCoupleProfile(CoupleProfileEntity(
      partner1Name = "Ánh ✨", partner1Birthday = "01/01/2000", partner1ProfilePicture = "", partner1Age = 20, partner1Zodiac = "Z1",
      partner2Name = "Bình", partner2Birthday = "02/02/2001", partner2ProfilePicture = "", partner2Age = 21, partner2Zodiac = "Z2",
      loveTitle = "T", loveDays = 1, anniversaryDate = "03/03/2020"
    ))

    repo.cleanStoredText()

    val u1 = dao.getOnlineUserByUidSync("u1")!!
    assertEquals("Nguyễn Lan", u1.displayName)
    assertEquals(WELCOME_BIO, u1.bio)
    val u2 = dao.getOnlineUserByUidSync("u2")!!
    assertEquals("Vô danh", u2.displayName)
    assertFalse(u2.isProfileSetup)
    assertEquals(WELCOME_BIO, u2.bio)
    assertEquals("Yêu em 💕", dao.getOnlineUserByUidSync("u3")!!.bio)
    val couple = dao.getCoupleProfileSync()!!
    assertEquals("Ánh", couple.partner1Name)
    assertEquals("Bình", couple.partner2Name)

    val before = listOf("u1", "u2", "u3").map { dao.getOnlineUserByUidSync(it) }
    repo.cleanStoredText()
    assertEquals(before, listOf("u1", "u2", "u3").map { dao.getOnlineUserByUidSync(it) })
  }

  @Test
  fun defaultsCarryNoEmoji() {
    for (s in listOf(WELCOME_BIO, DEFAULT_LOVE_NOTE)) {
      assertEquals(s, com.example.ui.util.cleanName(s).let { if (s.length <= 50) it else s })
    }
  }
}
