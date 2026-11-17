@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.OnlineUserEntity
import com.example.data.sync.ProfileSync.PushOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P11: ProfileSync (kéo 1 lần mỗi phiên, đẩy có debounce, chính sách xung đột §3.2).
 * Robolectric chỉ để có SharedPreferences thật; mọi lambda là giả, thời gian là thời gian ảo của runTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSyncTest {

  private val uid = "u1"
  private val k0 = "0".repeat(64)
  private val welcome = "Chào mừng bạn đến với InLove ✨"
  private lateinit var prefs: SharedPreferences

  @Before
  fun setUp() {
    prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(ProfileSync.PREFS, Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
  }

  private fun row(
    setUp: Boolean = true,
    name: String = "An",
    birth: String = "05/05/1995",
    bio: String = "Xin chào",
    gender: String = "FEMALE",
    interests: String = "coffee,travel"
  ) = OnlineUserEntity(
    uid = uid,
    coupleCode = "AAAA-1111",
    displayName = name,
    birthDate = birth,
    gender = gender,
    bio = bio,
    interestsCsv = interests,
    isProfileSetup = setUp,
    isCurrentUser = true
  )

  /** Hồ sơ thật khác hẳn row() mặc định. */
  private fun otherRow() = row(name = "Binh", birth = "01/01/1990", bio = "Hi", gender = "MALE", interests = "music")

  private fun projOf(r: OnlineUserEntity) = ProfileProjection.of(r)
  private fun keyOf(r: OnlineUserEntity) = projOf(r).key()
  private fun remoteOf(r: OnlineUserEntity): Map<String, Any?> = projOf(r).toRemoteMap()
  private fun legacyOf(r: OnlineUserEntity): Map<String, Any?> = remoteOf(r) - "schemaVersion"
  private fun synced(): String? = prefs.getString(ProfileSync.syncedKey(uid), null)

  /** Chạy phần đang chờ, đi qua debounce rồi chạy tiếp. */
  private fun TestScope.settle() {
    runCurrent()
    advanceTimeBy(ProfileSync.DEBOUNCE_MS + 1)
    runCurrent()
  }

  private inner class Rig(scope: CoroutineScope, initial: OnlineUserEntity?) {
    val rowFlow = MutableStateFlow(initial)
    var signed: String? = uid
    var fetchResult: suspend () -> Map<String, Any?>? = { null }
    var applyOk = true
    val fetched = mutableListOf<String>()
    val applied = mutableListOf<Triple<String, ProfileProjection, String>>()
    val pushes = mutableListOf<Map<String, Any?>>()
    val dones = mutableListOf<(PushOutcome) -> Unit>()

    val sync = ProfileSync(
      observeRow = { rowFlow },
      prefs = prefs,
      scope = scope,
      fetch = { u ->
        fetched.add(u)
        fetchResult()
      },
      push = { _, data, onDone ->
        pushes.add(data)
        dones.add(onDone)
      },
      signedInUid = { signed }
    )

    val applier: suspend (String, ProfileProjection, String) -> Boolean = { u, p, k ->
      applied.add(Triple(u, p, k))
      if (applyOk) {
        rowFlow.update {
          it?.copy(displayName = p.displayName, birthDate = p.birthDate, gender = p.gender, bio = p.bio, interestsCsv = p.interestsCsv)
        }
      }
      applyOk
    }

    fun start(u: String = uid) = sync.start(u, applier)
    fun edit(name: String) = rowFlow.update { it?.copy(displayName = name) }
  }

  // (1) máy mới: dòng hạt giống + Valid
  @Test
  fun newDeviceSeedRowAppliesRemoteAndPushesNothing() = runTest {
    val seed = row(setUp = false, name = "Vô danh", birth = "", bio = "")
    val rig = Rig(backgroundScope, seed)
    val remote = remoteOf(otherRow())
    rig.fetchResult = { remote }
    rig.start()
    settle()
    assertEquals(1, rig.applied.size)
    assertEquals(keyOf(seed), rig.applied[0].third)
    assertEquals(projOf(otherRow()), rig.applied[0].second)
    assertEquals(0, rig.pushes.size)
    assertEquals(keyOf(otherRow()), synced())
  }

  // (2) Absent + dòng đã thiết lập
  @Test
  fun absentWithSetUpRowPushesAndAckRecordsKey() = runTest {
    val r = row()
    val rig = Rig(backgroundScope, r)
    rig.start()
    settle()
    assertEquals(1, rig.pushes.size)
    assertEquals(remoteOf(r), rig.pushes[0])
    assertEquals("", synced())
    rig.dones[0](PushOutcome.ACKED)
    assertEquals(keyOf(r), synced())
  }

  // (3) Legacy + cục bộ nguyên sơ
  @Test
  fun legacyWithPristineLocalAppliesThenPushesSchemaOne() = runTest {
    val rig = Rig(backgroundScope, row(birth = "", bio = ""))
    val legacy = legacyOf(otherRow())
    rig.fetchResult = { legacy }
    rig.start()
    settle()
    assertEquals(1, rig.applied.size)
    assertEquals(1, rig.pushes.size)
    assertEquals(PROFILE_SCHEMA_VERSION, rig.pushes[0]["schemaVersion"])
    assertEquals(remoteOf(otherRow()), rig.pushes[0])
  }

  // (3b) Legacy + cục bộ không nguyên sơ
  @Test
  fun legacyWithNonPristineLocalPushesLocal() = runTest {
    val r = row()
    val rig = Rig(backgroundScope, r)
    val legacy = legacyOf(otherRow())
    rig.fetchResult = { legacy }
    rig.start()
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(1, rig.pushes.size)
    assertEquals(remoteOf(r), rig.pushes[0])
  }

  // (4) schemaVersion = 2
  @Test
  fun schemaVersionTwoIsIgnored() = runTest {
    val rig = Rig(backgroundScope, row())
    val v2 = remoteOf(otherRow()) + ("schemaVersion" to 2L)
    rig.fetchResult = { v2 }
    rig.start()
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    assertNull(synced())
  }

  // (5) fetch lỗi, đã từng đồng bộ
  @Test
  fun fetchFailureWithSyncedHistoryPushesOnlyLaterEdits() = runTest {
    prefs.edit().putString(ProfileSync.syncedKey(uid), k0).commit()
    val rig = Rig(backgroundScope, row())
    rig.fetchResult = { throw IllegalStateException("offline") }
    rig.start()
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    rig.edit("Binh")
    settle()
    assertEquals(1, rig.pushes.size)
    assertEquals("Binh", rig.pushes[0]["displayName"])
  }

  // (5b) fetch lỗi, chưa từng đồng bộ
  @Test
  fun fetchFailureWithoutSyncedHistoryNeverPushes() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.fetchResult = { throw IllegalStateException("offline") }
    rig.start()
    settle()
    rig.edit("Binh")
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    assertNull(synced())
  }

  // fetch treo: hết FETCH_TIMEOUT_MS thì xử lý như lỗi
  @Test
  fun fetchTimeoutBehavesLikeFailure() = runTest {
    prefs.edit().putString(ProfileSync.syncedKey(uid), k0).commit()
    val rig = Rig(backgroundScope, row())
    rig.fetchResult = { awaitCancellation() }
    rig.start()
    runCurrent()
    advanceTimeBy(ProfileSync.FETCH_TIMEOUT_MS + 1)
    runCurrent()
    rig.edit("Binh")
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(1, rig.pushes.size)
    assertEquals("Binh", rig.pushes[0]["displayName"])
  }

  // (6) bẩn: synced khác key cục bộ
  @Test
  fun dirtyLocalWinsOverRemote() = runTest {
    prefs.edit().putString(ProfileSync.syncedKey(uid), k0).commit()
    val r = row()
    val rig = Rig(backgroundScope, r)
    val remote = remoteOf(otherRow())
    rig.fetchResult = { remote }
    rig.start()
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(1, rig.pushes.size)
    assertEquals(remoteOf(r), rig.pushes[0])
    assertEquals("", synced())
  }

  // (7) apply đổi dòng thành bản từ xa: không đẩy lại
  @Test
  fun appliedRemoteIsNotEchoedBack() = runTest {
    val rig = Rig(backgroundScope, row())
    val remote = remoteOf(otherRow())
    rig.fetchResult = { remote }
    rig.start()
    settle()
    assertEquals(1, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    assertEquals(keyOf(otherRow()), synced())
  }

  // (8) stop() trước khi fetch trả
  @Test
  fun stopBeforeFetchReturnsDoesNothing() = runTest {
    val rig = Rig(backgroundScope, row())
    val gate = CompletableDeferred<Map<String, Any?>?>()
    rig.fetchResult = { gate.await() }
    rig.start()
    runCurrent()
    assertEquals(1, rig.fetched.size)
    rig.sync.stop()
    gate.complete(remoteOf(otherRow()))
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(0, rig.pushes.size)
  }

  // (9) đổi tài khoản đăng nhập giữa chừng
  @Test
  fun signedInUidChangeBlocksPush() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.start()
    runCurrent()
    rig.signed = "someone-else"
    advanceTimeBy(ProfileSync.DEBOUNCE_MS + 1)
    runCurrent()
    assertEquals(0, rig.pushes.size)
  }

  // (10) DENIED
  @Test
  fun deniedStopsPushingUntilNextSession() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.start()
    settle()
    assertEquals(1, rig.pushes.size)
    rig.dones[0](PushOutcome.DENIED)
    assertEquals("", synced())
    rig.edit("Binh")
    settle()
    assertEquals(1, rig.pushes.size)
    rig.sync.stop()
    rig.start()
    settle()
    assertEquals(2, rig.pushes.size)
  }

  // (11) khởi động lại khi chưa ack (B-03)
  private fun TestScope.restartWithoutAck(initialSynced: String?) {
    if (initialSynced != null) prefs.edit().putString(ProfileSync.syncedKey(uid), initialSynced).commit()
    val r = row()
    val first = Rig(backgroundScope, r)
    first.start()
    settle()
    assertEquals(1, first.pushes.size)
    assertEquals("", synced())
    first.sync.stop()

    val second = Rig(backgroundScope, r)
    val older = remoteOf(otherRow())
    second.fetchResult = { older }
    second.start()
    settle()
    assertEquals(0, second.applied.size)
    assertEquals(1, second.pushes.size)
    assertEquals(remoteOf(r), second.pushes[0])
  }

  @Test
  fun restartWithoutAckAfterKnownSyncKeepsLocal() = runTest { restartWithoutAck(k0) }

  @Test
  fun restartWithoutAckOnFirstSyncKeepsLocal() = runTest { restartWithoutAck(null) }

  // (12) dòng không xuất hiện trong ROW_WAIT_MS
  @Test
  fun missingRowNeverFetches() = runTest {
    val rig = Rig(backgroundScope, null)
    rig.start()
    runCurrent()
    advanceTimeBy(ProfileSync.ROW_WAIT_MS + 1)
    runCurrent()
    rig.rowFlow.value = row()
    settle()
    assertEquals(0, rig.fetched.size)
    assertEquals(0, rig.pushes.size)
  }

  // (13) dòng tới sau 2 s: startKey tính từ dòng vừa tới
  @Test
  fun rowArrivingLaterIsFetchedOnceWithItsOwnKey() = runTest {
    val rig = Rig(backgroundScope, null)
    val arrived = row()
    rig.fetchResult = { remoteOf(arrived) }
    rig.start()
    runCurrent()
    advanceTimeBy(2_000)
    runCurrent()
    assertEquals(0, rig.fetched.size)
    rig.rowFlow.value = arrived
    settle()
    assertEquals(1, rig.fetched.size)
    assertEquals(0, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    assertEquals(keyOf(arrived), synced())
  }

  // (14) Absent khi synced == startKey
  @Test
  fun absentWhenSyncedEqualsLocalKeyPushes() = runTest {
    val r = row()
    prefs.edit().putString(ProfileSync.syncedKey(uid), keyOf(r)).commit()
    val rig = Rig(backgroundScope, r)
    rig.start()
    settle()
    assertEquals(1, rig.pushes.size)
    assertEquals("", synced())
  }

  // (15) ACKED của key cũ khi đã có key mới
  @Test
  fun staleAckKeepsDirtyMarker() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.start()
    settle()
    rig.edit("Binh")
    settle()
    assertEquals(2, rig.pushes.size)
    rig.dones[0](PushOutcome.ACKED)
    assertEquals("", synced())
    rig.dones[1](PushOutcome.ACKED)
    assertEquals(keyOf(row(name = "Binh")), synced())
  }

  // (16) ACKED tới sau stop()
  @Test
  fun ackAfterStopStillRecordsKey() = runTest {
    val r = row()
    val rig = Rig(backgroundScope, r)
    rig.start()
    settle()
    rig.sync.stop()
    rig.dones[0](PushOutcome.ACKED)
    assertEquals(keyOf(r), synced())
  }

  // (17) F2: dòng hạt giống + Absent
  @Test
  fun seedRowWithAbsentRemoteDoesNotPushUntilProfileIsSetUp() = runTest {
    val rig = Rig(backgroundScope, row(setUp = false, name = "Vô danh", birth = "", bio = ""))
    rig.start()
    settle()
    assertEquals(0, rig.pushes.size)
    assertNull(synced())
    rig.rowFlow.update { it?.copy(displayName = "An", isProfileSetup = true) }
    settle()
    assertEquals(1, rig.pushes.size)
    assertEquals("An", rig.pushes[0]["displayName"])
  }

  // (18) F2: apply trả false (đua)
  @Test
  fun applyReturningFalseRestoresSyncedAndBlocksPush() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.applyOk = false
    val remote = remoteOf(otherRow())
    rig.fetchResult = { remote }
    rig.start()
    settle()
    assertEquals(1, rig.applied.size)
    assertEquals(0, rig.pushes.size)
    assertNull(synced())
    assertTrue(!prefs.contains(ProfileSync.syncedKey(uid)))
  }

  // (19) F3: cục bộ thật, remote kiểu hạt giống
  @Test
  fun seedLikeRemoteDoesNotOverwriteRealLocalProfile() = runTest {
    val r = row()
    val rig = Rig(backgroundScope, r)
    val seedLike = remoteOf(row(birth = "", bio = welcome))
    rig.fetchResult = { seedLike }
    rig.start()
    settle()
    assertEquals(0, rig.applied.size)
    assertEquals(1, rig.pushes.size)
    assertEquals(remoteOf(r), rig.pushes[0])
  }

  @Test
  fun realRemoteStillWinsOverRealLocalOnFirstSync() = runTest {
    val rig = Rig(backgroundScope, row())
    val real = remoteOf(otherRow())
    rig.fetchResult = { real }
    rig.start()
    settle()
    assertEquals(1, rig.applied.size)
    assertEquals(0, rig.pushes.size)
  }

  // (20) F5: start idempotent
  @Test
  fun startIsIdempotentPerUid() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.start()
    rig.start()
    settle()
    assertEquals(1, rig.fetched.size)
    rig.sync.stop()
    rig.start()
    settle()
    assertEquals(2, rig.fetched.size)
    rig.signed = "u2"
    rig.start("u2")
    settle()
    assertEquals(listOf(uid, uid, "u2"), rig.fetched)
  }

  // (21) F16: sửa dòng sau stop() không đẩy
  @Test
  fun editAfterStopDoesNotPush() = runTest {
    val rig = Rig(backgroundScope, row())
    rig.start()
    settle()
    assertEquals(1, rig.pushes.size)
    rig.sync.stop()
    rig.edit("Binh")
    settle()
    assertEquals(1, rig.pushes.size)
  }
}
