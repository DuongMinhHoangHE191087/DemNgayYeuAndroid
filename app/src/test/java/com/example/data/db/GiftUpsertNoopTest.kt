package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftUpsertNoopTest {

  private fun newDb(): AppDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  private fun row(remoteId: String) =
    GiftIdeaEntity(title = "t", category = "c", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = remoteId)

  private fun writesOfSecondUpsert(first: GiftIdeaEntity, second: GiftIdeaEntity): Int {
    val db = newDb()
    try {
      val dao = db.inLoveDao()
      runBlocking { dao.upsertGiftIdeaByRemoteId(first) }
      val sdb = db.openHelper.writableDatabase
      sdb.execSQL("CREATE TABLE gift_write_log (op TEXT NOT NULL)")
      sdb.execSQL("CREATE TRIGGER gift_log_insert AFTER INSERT ON gift_ideas BEGIN INSERT INTO gift_write_log VALUES ('insert'); END")
      sdb.execSQL("CREATE TRIGGER gift_log_update AFTER UPDATE ON gift_ideas BEGIN INSERT INTO gift_write_log VALUES ('update'); END")
      sdb.execSQL("CREATE TRIGGER gift_log_delete AFTER DELETE ON gift_ideas BEGIN INSERT INTO gift_write_log VALUES ('delete'); END")
      runBlocking { dao.upsertGiftIdeaByRemoteId(second) }
      return db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM gift_write_log")
        .use { c -> c.moveToFirst(); c.getInt(0) }
    } finally {
      db.close()
    }
  }

  @Test
  fun identicalUpsert_writesNothing() {
    assertEquals("identical upsert must not write", 0, writesOfSecondUpsert(row("r1"), row("r1")))
  }

  @Test
  fun changedTitleUpsert_writesExactlyOnce() {
    assertEquals("changed title must write once", 1, writesOfSecondUpsert(row("r1"), row("r1").copy(title = "changed")))
  }

  @Test
  fun flippedFavoriteOnlyUpsert_writesNothing() {
    assertEquals(
      "favorite-only difference must not write",
      0,
      writesOfSecondUpsert(row("r1").copy(isFavorited = true), row("r1"))
    )
  }
}
