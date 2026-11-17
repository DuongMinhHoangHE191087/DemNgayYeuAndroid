package com.example.spike

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow

// Spike F-04 (p0-room-kmp.md). F-15 xoa file nay va thu muc schemas/com.example.spike.SpikeDatabase.

@Entity(tableName = "spike_row")
data class SpikeRow(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val text: String,
)

@Dao
interface SpikeDao {
  @Insert
  suspend fun insert(row: SpikeRow): Long

  @Query("SELECT * FROM spike_row ORDER BY id")
  fun all(): Flow<List<SpikeRow>>
}

@Database(entities = [SpikeRow::class], version = 1, exportSchema = true)
@ConstructedBy(SpikeDatabaseConstructor::class)
abstract class SpikeDatabase : RoomDatabase() {
  abstract fun spikeDao(): SpikeDao
}

/** Driver san xuat cho moi target (Android + iOS). */
fun RoomDatabase.Builder<SpikeDatabase>.withBundledDriver(): RoomDatabase.Builder<SpikeDatabase> =
  setDriver(BundledSQLiteDriver())

// Room KSP sinh `actual` cho tung target.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object SpikeDatabaseConstructor : RoomDatabaseConstructor<SpikeDatabase> {
  override fun initialize(): SpikeDatabase
}
