package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryScopeLifecycleTest {

  @Test
  fun onlineCoupleRepository_stopsWork_whenCallerScopeIsCancelled() {
    val db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), AppDatabase::class.java
    ).allowMainThreadQueries().build()
    val job = SupervisorJob()
    val scope = CoroutineScope(job + UnconfinedTestDispatcher())

    val repo = OnlineCoupleRepository(db.inLoveDao(), ApplicationProvider.getApplicationContext(), scope)
    repo.switchDemoUser() // launches on the injected scope

    job.cancel()
    assert(!scope.isActive) { "cancelling the caller's scope must stop the repository's own coroutines" }
    db.close()
  }
}
