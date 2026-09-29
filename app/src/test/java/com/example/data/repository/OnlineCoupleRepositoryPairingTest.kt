package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.OnlineStatus
import com.example.data.model.RelationshipStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineCoupleRepositoryPairingTest {

  @Test
  fun acceptSetLoveInvite_afterBreakup_createsANewRelationshipId_notTheTerminatedOne() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope)

    // Seed a terminated relationship between A and B from a past pairing.
    val terminatedRelId = "rel_old"
    db.inLoveDao().insertOnlineRelationship(
      com.example.data.model.OnlineRelationshipEntity(
        relationshipId = terminatedRelId, user1 = "uid_a", user2 = "uid_b",
        startDate = 1L, status = RelationshipStatus.TERMINATED
      )
    )
    db.inLoveDao().insertOnlineUser(
      com.example.data.model.OnlineUserEntity(uid = "uid_a", coupleCode = "AAAA-1111", isCurrentUser = false)
    )
    db.inLoveDao().insertOnlineUser(
      com.example.data.model.OnlineUserEntity(uid = "uid_b", coupleCode = "BBBB-2222", isCurrentUser = true)
    )
    repo.setCurrentUserId("uid_b")

    val newInviteId = "inv_new"
    db.inLoveDao().insertOnlineInvite(
      com.example.data.model.OnlineInviteEntity(
        inviteId = newInviteId, senderUid = "uid_a", senderCoupleCode = "AAAA-1111",
        targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
      )
    )

    repo.acceptSetLoveInvite(newInviteId)

    val newRel = db.inLoveDao().getActiveRelationshipForUser("uid_b")
    assert(newRel != null)
    assert(newRel!!.relationshipId == newInviteId) { "relationship id must equal the accepted invite id, per Task 10's rule design" }
    assert(newRel.relationshipId != terminatedRelId) { "must not resurrect the old terminated relationship id" }
    db.close()
  }

  @Test
  fun confirmBreakup_withNoFirestoreConfigured_stillTerminatesLocally() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope)
    db.inLoveDao().insertOnlineUser(com.example.data.model.OnlineUserEntity(uid = "uid_b", coupleCode = "B", isCurrentUser = true, relationshipId = "rel_1", status = OnlineStatus.COUPLED))
    db.inLoveDao().insertOnlineRelationship(
      com.example.data.model.OnlineRelationshipEntity(relationshipId = "rel_1", user1 = "uid_a", user2 = "uid_b", startDate = 1L, status = RelationshipStatus.ACTIVE)
    )
    repo.setCurrentUserId("uid_b")

    val (success, _) = repo.confirmBreakup()

    assert(success)
    db.close()
  }
}
