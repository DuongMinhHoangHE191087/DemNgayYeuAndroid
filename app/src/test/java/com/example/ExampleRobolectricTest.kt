package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.RbacPolicy
import com.example.data.model.SubscriptionTier
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  private lateinit var db: AppDatabase

  @Before
  fun createDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun closeDb() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("InLove", appName)
  }

  @Test
  fun `insert and retrieve couple profile in Room`() = runBlocking {
    val profile = CoupleProfileEntity(
      id = 1,
      partner1Name = "Alex",
      partner1Birthday = "15/10/2004",
      partner1ProfilePicture = "https://example.com/boy.png",
      partner1Age = 20,
      partner1Zodiac = "Thiên Bình",
      partner2Name = "Taylor",
      partner2Birthday = "24/07/2003",
      partner2ProfilePicture = "https://example.com/girl.png",
      partner2Age = 21,
      partner2Zodiac = "Cự Giải",
      loveTitle = "Forever Love",
      loveDays = 100,
      anniversaryDate = "01/01/2026"
    )

    db.inLoveDao().insertCoupleProfile(profile)
    val retrieved = db.inLoveDao().getCoupleProfile().first()

    assertNotNull(retrieved)
    assertEquals("Alex", retrieved?.partner1Name)
    assertEquals("Taylor", retrieved?.partner2Name)
    assertEquals("15/10/2004", retrieved?.partner1Birthday)
    assertEquals("24/07/2003", retrieved?.partner2Birthday)
    assertEquals("https://example.com/boy.png", retrieved?.partner1ProfilePicture)
    assertEquals("https://example.com/girl.png", retrieved?.partner2ProfilePicture)
    assertEquals(100, retrieved?.loveDays)
  }

  @Test
  fun `rbac policy and ad free subscription logic`() {
    // 1. FREE user is not ad-free
    assertFalse(RbacPolicy.isAdFree(UserRole.USER_FREE, SubscriptionTier.FREE))

    // 2. VIP role is ad-free even on free tier
    assertTrue(RbacPolicy.isAdFree(UserRole.USER_VIP, SubscriptionTier.FREE))

    // 3. Paid subscription tier is ad-free
    assertTrue(RbacPolicy.isAdFree(UserRole.USER_FREE, SubscriptionTier.VIP_MONTHLY))
    assertTrue(RbacPolicy.isAdFree(UserRole.USER_FREE, SubscriptionTier.VIP_YEARLY))
    assertTrue(RbacPolicy.isAdFree(UserRole.USER_FREE, SubscriptionTier.LIFETIME))

    // 4. Admin is always ad-free
    assertTrue(RbacPolicy.isAdFree(UserRole.ADMIN, SubscriptionTier.FREE))
  }

  @Test
  fun `online repository pairing and breakup logic with clean fixtures`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Setup in-memory test fixtures for User A and User B
    val userA = OnlineUserEntity(
      uid = com.example.data.repository.OnlineCoupleRepository.USER_A_ID,
      displayName = "Tester A",
      email = "tester_a@inlove.app",
      coupleCode = com.example.data.repository.OnlineCoupleRepository.USER_A_CODE,
      partnerId = com.example.data.repository.OnlineCoupleRepository.USER_B_ID,
      relationshipId = "rel_test_123",
      status = OnlineStatus.COUPLED,
      isCurrentUser = true
    )
    val userB = OnlineUserEntity(
      uid = com.example.data.repository.OnlineCoupleRepository.USER_B_ID,
      displayName = "Tester B",
      email = "tester_b@inlove.app",
      coupleCode = com.example.data.repository.OnlineCoupleRepository.USER_B_CODE,
      partnerId = com.example.data.repository.OnlineCoupleRepository.USER_A_ID,
      relationshipId = "rel_test_123",
      status = OnlineStatus.COUPLED,
      isCurrentUser = false
    )
    val testRel = OnlineRelationshipEntity(
      relationshipId = "rel_test_123",
      user1 = userA.uid,
      user2 = userB.uid,
      startDate = System.currentTimeMillis(),
      startDateText = "01/01/2026",
      status = RelationshipStatus.ACTIVE
    )
    db.inLoveDao().insertOnlineUser(userA)
    db.inLoveDao().insertOnlineUser(userB)
    db.inLoveDao().insertOnlineRelationship(testRel)

    val onlineRepo = com.example.data.repository.OnlineCoupleRepository(db.inLoveDao(), context)
    onlineRepo.setCurrentUserId(userA.uid)
    onlineRepo.refreshState()

    // 1. Breakup flow: User A requests breakup
    val (breakupReqSuccess, _) = onlineRepo.requestBreakup()
    assertTrue(breakupReqSuccess)

    val relPending = onlineRepo.activeRelationship.first()
    assertEquals(RelationshipStatus.PENDING_BREAKUP, relPending?.status)

    // Switch to User B and confirm breakup
    onlineRepo.switchDemoUserSync()
    val (confirmSuccess, _) = onlineRepo.confirmBreakup()
    assertTrue(confirmSuccess)

    // Both should now be SINGLE
    val singleStatus = onlineRepo.relationshipStatus.first()
    assertEquals(OnlineStatus.SINGLE, singleStatus)

    // 2. Pairing flow: User B sends invite to User A
    val (sendSuccess, _) = onlineRepo.sendSetLoveInvite(com.example.data.repository.OnlineCoupleRepository.USER_A_CODE)
    assertTrue(sendSuccess)

    // Switch back to User A
    onlineRepo.switchDemoUserSync()
    val incoming = onlineRepo.incomingInvite.first()
    assertNotNull(incoming)

    // Accept invite
    val (acceptSuccess, _) = onlineRepo.acceptSetLoveInvite(incoming?.inviteId ?: "")
    assertTrue(acceptSuccess)

    // Status should be COUPLED again
    val status = onlineRepo.relationshipStatus.first()
    assertEquals(OnlineStatus.COUPLED, status)
  }
}
