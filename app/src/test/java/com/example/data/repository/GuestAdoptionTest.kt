package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.MilestoneEntity
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Đăng ký từ chế độ khách: hỏi giữ hay bỏ dữ liệu khách (D4 = ask-once). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestAdoptionTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var scope: CoroutineScope
  private lateinit var repo: AuthRepository
  private val dao get() = db.inLoveDao()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    listOf("inlove_account_scope", "inlove_auth_prefs", "inlove_unlock_attempts").forEach {
      context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val vault = AccountDataVault(context, db)
    runBlocking { vault.switchTo(AccountDataVault.GUEST) }
    val online = OnlineCoupleRepository(dao, context, scope, useFirestore = false)
    repo = AuthRepository(dao, online, context, scope, isTestMode = true, vault = vault)
  }

  @After
  fun tearDown() {
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    db.close()
  }

  private suspend fun register(email: String, keep: Boolean) =
    repo.register("Tester", email, "Passw0rd!x", "Passw0rd!x", keepGuestData = keep)

  private suspend fun memories() = dao.getAllSharedMemories().first().map { it.title }

  private fun memory(title: String) = SharedMemoryEntity(title = title, dateText = "2026-01-01", photoUri = "")

  private fun milestone(userCreated: Boolean) = MilestoneEntity(
    title = "m", dateText = "", subtitle = "", categoryTag = "", secondaryTag = "",
    imageUrl = "", daysRemaining = 0, isUserCreated = userCreated
  )

  @Test
  fun guestHasData_falseWhenOnlyCatalogRows() = runBlocking {
    dao.insertMilestone(milestone(userCreated = false))
    assertFalse(repo.guestHasData())
  }

  @Test
  fun guestHasData_trueWithUserCreatedRow() = runBlocking {
    dao.insertSharedMemory(memory("guest memory"))
    assertTrue(repo.guestHasData())
  }

  @Test
  fun registerKeep_adoptsGuestDataIntoNewAccount() = runBlocking {
    dao.insertSharedMemory(memory("guest memory"))
    val (ok, msg) = register("keep@x.com", keep = true)
    assertTrue(msg, ok)
    assertEquals(listOf("guest memory"), memories())

    // Dữ liệu đã chuyển sang tài khoản: quay lại chế độ khách thì phạm vi khách phải trống.
    repo.logout()
    assertEquals(emptyList<String>(), memories())
  }

  @Test
  fun registerFresh_startsEmpty_andGuestDataStaysInGuestScope() = runBlocking {
    dao.insertSharedMemory(memory("guest memory"))
    val (ok, msg) = register("fresh@x.com", keep = false)
    assertTrue(msg, ok)
    assertEquals(emptyList<String>(), memories())

    repo.logout()
    assertEquals(listOf("guest memory"), memories())
  }
}
