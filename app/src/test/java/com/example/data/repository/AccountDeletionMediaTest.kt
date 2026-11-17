package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.fakes.FakeAuthGateway
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.media.MediaPath
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Xoá tài khoản cũng xoá file ảnh/video kỷ niệm, manifest sao lưu và avatar local (kế hoạch 4b, T8b). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountDeletionMediaTest {

  private class Cleanup(private val cloudError: Exception? = null) : RemoteAccountCleanup {
    override suspend fun deleteCloudData(uid: String, coupleCode: String) {
      cloudError?.let { throw it }
    }

    override suspend fun deleteAuthIdentity() {}
  }

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var scope: CoroutineScope
  private lateinit var vault: AccountDataVault
  private val dao get() = db.inLoveDao()
  private val mediaDir get() = File(context.filesDir, MediaPath.DIR)
  private val avatarsDir get() = File(context.filesDir, "avatars")

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    listOf("inlove_account_scope", "inlove_auth_prefs", "inlove_unlock_attempts").forEach {
      context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    vault = AccountDataVault(context, db)
    runBlocking { vault.switchTo(AccountDataVault.GUEST) }
    mediaDir.mkdirs()
    avatarsDir.mkdirs()
  }

  @After
  fun tearDown() {
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    db.close()
    mediaDir.deleteRecursively()
    avatarsDir.deleteRecursively()
  }

  private fun repoWith(cleanup: RemoteAccountCleanup): AuthRepository {
    val online = OnlineCoupleRepository(dao, context, scope, useFirestore = false)
    return AuthRepository(dao, online, context, scope, FakeAuthGateway(), RecordingSessionHooks(), remoteCleanup = cleanup, vault = vault)
  }

  private val email = "media-test@inlove.app"

  private suspend fun AuthRepository.registerFresh() {
    val (ok, msg) = register("Tester", email, "Passw0rd!x", "Passw0rd!x", keepGuestData = false)
    assertTrue("Đăng ký phải thành công: $msg", ok)
  }

  // Sau đăng ký có thể còn hàng khách isCurrentUser = 1 (OnlineCoupleRepository.init tạo nó), nên
  // getCurrentOnlineUserSync() có thể trả hàng khách: tìm hàng của tài khoản theo email cho chắc.
  private suspend fun setMyAvatar(url: String) {
    val me = dao.getAllOnlineUsersListSync().single { it.email == email }
    dao.insertOnlineUser(me.copy(avatarUrl = url))
  }

  private fun file(dir: File, name: String) = File(dir, name).apply { writeText("x") }

  private fun memory(photo: String, video: String? = null) =
    SharedMemoryEntity(title = "m", dateText = "2026-01-01", photoUri = photo, videoUri = video)

  @Test
  fun deleteAccount_removesPhotoAndLegacyVideo_keepsUnrelatedFiles() = runBlocking {
    val photo = file(mediaDir, "a.jpg")
    val video = file(mediaDir, "b.mp4")
    val wallpaper = file(mediaDir, "wallpaper_stand_in.jpg")
    val avatar = file(avatarsDir, "x.jpg")
    val repo = repoWith(Cleanup())
    repo.registerFresh()
    dao.insertSharedMemory(memory("media/a.jpg", Uri.fromFile(video).toString()))

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertFalse(photo.exists())
    assertFalse(video.exists())
    assertTrue(wallpaper.exists())
    assertTrue(avatar.exists())
  }

  @Test
  fun deleteAccount_keepsFileThatRestoredGuestRowStillUses() = runBlocking {
    val shared = file(mediaDir, "shared.jpg")
    val own = file(mediaDir, "own.jpg")
    dao.insertSharedMemory(memory("media/shared.jpg"))
    val repo = repoWith(Cleanup())
    repo.registerFresh() // keepGuestData = false cất hàng khách vào kho
    dao.insertSharedMemory(memory("media/shared.jpg"))
    dao.insertSharedMemory(memory("media/own.jpg"))

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertTrue("Hàng khách được khôi phục vẫn dùng file này", shared.exists())
    assertFalse(own.exists())
  }

  @Test
  fun deleteAccount_removesBackupManifestAndLocalAvatar_keepsOtherFile() = runBlocking {
    val manifest = file(mediaDir, MediaPath.BACKUP_MANIFEST)
    val avatar = file(avatarsDir, "me.jpg")
    val other = file(avatarsDir, "other.jpg")
    val repo = repoWith(Cleanup())
    repo.registerFresh()
    setMyAvatar(avatar.toURI().toString())

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertFalse(manifest.exists())
    assertFalse(avatar.exists())
    assertTrue(other.exists())
  }

  @Test
  fun deleteAccount_cloudFailure_keepsEveryFile() = runBlocking {
    val photo = file(mediaDir, "a.jpg")
    val manifest = file(mediaDir, MediaPath.BACKUP_MANIFEST)
    val avatar = file(avatarsDir, "me.jpg")
    val repo = repoWith(Cleanup(cloudError = IllegalStateException("PERMISSION_DENIED")))
    repo.registerFresh()
    dao.insertSharedMemory(memory("media/a.jpg"))
    setMyAvatar(avatar.toURI().toString())

    val result = repo.deleteCurrentAccount()

    assertTrue(result.isFailure)
    assertTrue(photo.exists())
    assertTrue(manifest.exists())
    assertTrue(avatar.exists())
  }
}
