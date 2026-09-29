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
}
