package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountIsolationTest {

  private lateinit var db: AppDatabase
  private lateinit var vault: AccountDataVault
  private val dao get() = db.inLoveDao()

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit().clear().commit()
    vault = AccountDataVault(context, db)
    runBlocking { vault.switchTo(AccountDataVault.GUEST) }
  }

  @After
  fun tearDown() = db.close()

  private fun memory(title: String) = SharedMemoryEntity(title = title, dateText = "2026-01-01", photoUri = "")

  private fun milestone(title: String, userCreated: Boolean) = MilestoneEntity(
    title = title, dateText = "", subtitle = "", categoryTag = "", secondaryTag = "",
    imageUrl = "", daysRemaining = 0, isUserCreated = userCreated
  )

  private suspend fun memories() = dao.getAllSharedMemories().first().map { it.title }.sorted()
  private suspend fun milestones() = dao.getAllMilestones().first().map { it.title }.sorted()

  @Test
  fun accountsDoNotSeeEachOthersData_andGetThemBackOnReturn() = runBlocking {
    val a = AccountDataVault.scopeOf("A@x.com")
    val b = AccountDataVault.scopeOf("b@x.com")
    dao.insertMilestone(milestone("catalog preset", userCreated = false))

    // guest data
    dao.insertSharedMemory(memory("guest memory"))
    dao.insertMilestone(milestone("guest milestone", userCreated = true))

    vault.switchTo(a)
    assertEquals(emptyList<String>(), memories())
    assertEquals(listOf("catalog preset"), milestones())
    dao.insertSharedMemory(memory("A memory"))
    dao.insertGiftReminder(GiftReminderEntity(title = "A gift"))
    dao.insertMilestone(milestone("A milestone", userCreated = true))

    vault.switchTo(b)
    assertEquals(emptyList<String>(), memories())
    assertEquals(emptyList<String>(), dao.getAllGiftReminders().first().map { it.title })
    dao.insertSharedMemory(memory("B memory"))

    vault.switchTo(AccountDataVault.GUEST)
    assertEquals(listOf("guest memory"), memories())
    assertEquals(listOf("catalog preset", "guest milestone"), milestones())

    vault.switchTo(a)
    assertEquals(listOf("A memory"), memories())
    assertEquals(listOf("A gift"), dao.getAllGiftReminders().first().map { it.title })
    assertEquals(listOf("A milestone", "catalog preset"), milestones())

    vault.switchTo(b)
    assertEquals(listOf("B memory"), memories())
  }

  @Test
  fun adoptKeepsGuestDataForNewAccount() = runBlocking {
    dao.insertSharedMemory(memory("guest memory"))
    vault.adoptCurrentInto(AccountDataVault.scopeOf("new@x.com"))
    vault.switchTo(AccountDataVault.scopeOf("new@x.com")) // no-op
    assertEquals(listOf("guest memory"), memories())

    vault.switchTo(AccountDataVault.GUEST)
    assertEquals(emptyList<String>(), memories())
  }

  @Test
  fun discardCurrentDropsAccountDataInsteadOfLeakingToGuest() = runBlocking {
    val a = AccountDataVault.scopeOf("a@x.com")
    dao.insertSharedMemory(memory("guest memory"))
    vault.switchTo(a)
    dao.insertSharedMemory(memory("A memory"))

    vault.switchTo(AccountDataVault.GUEST, discardCurrent = true)
    assertEquals(listOf("guest memory"), memories())

    vault.switchTo(a)
    assertEquals(emptyList<String>(), memories())
  }

  @Test
  fun firstCallAdoptsExistingRowsForThatScope_notGuest() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit().clear().commit()
    val fresh = AccountDataVault(context, db) // simulates an upgrade: rows exist, no scope recorded
    dao.insertSharedMemory(memory("legacy memory"))
    val a = AccountDataVault.scopeOf("a@x.com")

    fresh.switchTo(a)
    assertEquals(listOf("legacy memory"), memories())
    fresh.switchTo(AccountDataVault.GUEST)
    assertEquals(emptyList<String>(), memories())
  }

  @Test
  fun interruptedSwitchIsFinishedOnNextCall_withoutLosingData() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val a = AccountDataVault.scopeOf("a@x.com")
    dao.insertSharedMemory(memory("guest memory"))
    // Process died right after the pending marker was written, before anything moved.
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("pending", "${AccountDataVault.GUEST}|$a|false").commit()

    vault.switchTo(a) // recovery completes guest -> a first
    assertEquals(emptyList<String>(), memories())
    vault.switchTo(AccountDataVault.GUEST)
    assertEquals(listOf("guest memory"), memories())
  }

  private suspend fun cadenceEnabled(key: String) = dao.getAllReminderCadences().first().single { it.key == key }.isEnabled

  private suspend fun favorited(remoteId: String) = dao.getGiftIdeaByRemoteId(remoteId)!!.isFavorited

  @Test
  fun cadenceSwitchesFollowTheAccount() = runBlocking {
    val a = AccountDataVault.scopeOf("a@x.com")
    dao.insertReminderCadences(listOf(ReminderCadenceEntity(key = "7_days", label = "7 ngày", isEnabled = true)))
    dao.updateReminderCadence(ReminderCadenceEntity(key = "7_days", label = "7 ngày", isEnabled = false)) // guest turns it off

    vault.switchTo(a)
    assertEquals(true, cadenceEnabled("7_days"))
    vault.switchTo(AccountDataVault.GUEST)
    assertEquals(false, cadenceEnabled("7_days"))
  }

  @Test
  fun favoritesOnCatalogRowsFollowTheAccount() = runBlocking {
    val a = AccountDataVault.scopeOf("a@x.com")
    dao.insertGiftIdeas(
      listOf(GiftIdeaEntity(title = "catalog gift", category = "", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = "catalog_rose"))
    )
    dao.updateGiftIdea(dao.getGiftIdeaByRemoteId("catalog_rose")!!.copy(isFavorited = true)) // guest favorites it

    vault.switchTo(a)
    assertEquals(false, favorited("catalog_rose"))
    dao.updateGiftIdea(dao.getGiftIdeaByRemoteId("catalog_rose")!!.copy(isFavorited = true))

    vault.switchTo(AccountDataVault.GUEST)
    assertEquals(true, favorited("catalog_rose"))
    vault.switchTo(a)
    assertEquals(true, favorited("catalog_rose"))
  }
}
