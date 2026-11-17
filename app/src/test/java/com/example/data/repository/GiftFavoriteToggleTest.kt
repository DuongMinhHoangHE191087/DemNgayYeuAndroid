package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftFavoriteToggleTest {

  private fun newDb(): AppDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  private fun repo(dao: InLoveDao) = InLoveRepository(dao, ApplicationProvider.getApplicationContext<Context>())

  private fun row(remoteId: String) =
    GiftIdeaEntity(title = "t", category = "c", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = remoteId)

  private suspend fun load(dao: InLoveDao, remoteId: String) =
    dao.getAllGiftIdeas().first().first { it.remoteId == remoteId }

  @Test
  fun toggleGiftFavorite_keepsConcurrentTitleChange() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)

    dao.upsertGiftIdeaByRemoteId(row("toggle_1"))
    val stale = load(dao, "toggle_1")
    dao.updateGiftIdea(stale.copy(title = "new title"))

    repository.toggleGiftFavorite(stale)
    val after = load(dao, "toggle_1")
    assert(after.isFavorited) { "toggle must flip the favorite: ${after.isFavorited}" }
    assert(after.title == "new title") { "toggle must keep the concurrent title change: ${after.title}" }
    db.close()
  }

  @Test
  fun toggleGiftFavorite_flipsBackAndForth() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val repository = repo(dao)

    dao.upsertGiftIdeaByRemoteId(row("toggle_2"))
    repository.toggleGiftFavorite(load(dao, "toggle_2"))
    assert(load(dao, "toggle_2").isFavorited) { "first toggle must favorite the idea" }
    repository.toggleGiftFavorite(load(dao, "toggle_2"))
    assert(!load(dao, "toggle_2").isFavorited) { "second toggle must unfavorite the idea" }
    db.close()
  }
}
