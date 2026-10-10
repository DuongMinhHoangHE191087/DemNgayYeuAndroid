package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetDedupeTest {

  @Test
  fun upsertGiftIdeaByRemoteId_calledTwiceWithSameRemoteId_doesNotDuplicate() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val idea = GiftIdeaEntity(title = "Nến thơm", category = "Quà lãng mạn", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = "doc_1")
    dao.upsertGiftIdeaByRemoteId(idea)
    dao.upsertGiftIdeaByRemoteId(idea.copy(title = "Nến thơm (updated)"))

    val all = dao.getAllGiftIdeas().first()
    assert(all.size == 1) { "same remoteId synced twice must update, not duplicate: got ${all.size} rows" }
    assert(all.first().title == "Nến thơm (updated)")
    db.close()
  }

  @Test
  fun upsertGiftIdeaByRemoteId_twentySeedsSyncedTwice_keepTwentyRows() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val seeds = com.example.data.seed.GiftIdeasSeed.all.map { it.toEntity(com.example.ui.util.AppLanguage.VI) }
    seeds.forEach { dao.upsertGiftIdeaByRemoteId(it) }
    seeds.forEach { dao.upsertGiftIdeaByRemoteId(it.copy(title = it.title + " (again)")) }

    val all = dao.getAllGiftIdeas().first()
    assert(all.size == seeds.size) { "seeds synced twice must stay ${seeds.size} rows: got ${all.size}" }
    db.close()
  }

  @Test
  fun generateAiGiftSuggestions_calledTwice_keepsRowIdentityAndFavorites() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val repository = InLoveRepository(dao, context)

    repository.generateAiGiftSuggestions("An", setOf("coffee"), "Valentine")
    val coffee = dao.getAllGiftIdeas().first().first { it.remoteId.startsWith("tpl_coffee") }
    dao.updateGiftIdea(coffee.copy(isFavorited = true))

    val returned = repository.generateAiGiftSuggestions("An", setOf("coffee"), "Valentine").getOrThrow()
    val after = dao.getAllGiftIdeas().first()
    val coffeeAfter = after.first { it.remoteId == coffee.remoteId }
    assert(after.size == 2) { "regenerating the same suggestions must not add rows: got ${after.size}" }
    assert(coffeeAfter.id == coffee.id) { "a regenerated suggestion keeps its row id: was ${coffee.id}, got ${coffeeAfter.id}" }
    assert(coffeeAfter.isFavorited) { "regenerating must not drop a favorite" }
    assert(returned.first { it.remoteId == coffee.remoteId }.id == coffee.id) { "the returned rows carry their stored ids" }
    db.close()
  }
}
