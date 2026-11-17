package com.example.spike

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpikeDbAndroidTest {
  @Test
  fun insertAndReadBack() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder<SpikeDatabase>(context)
      // BundledSQLiteDriver (withBundledDriver) khong nap duoc sqliteJni tren host Windows (jar Android chi co .so):
      // test dung AndroidSQLiteDriver duoi Robolectric; ma san xuat commonMain van dung BundledSQLiteDriver.
      .setDriver(AndroidSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.Unconfined)
      .build()
    try {
      runBlocking { runSpikeScenario(db) }
    } finally {
      db.close()
    }
  }
}
