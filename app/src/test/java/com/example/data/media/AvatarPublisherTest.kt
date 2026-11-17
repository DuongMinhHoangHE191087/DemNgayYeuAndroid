package com.example.data.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.data.db.AppDatabase
import com.example.data.media.fakes.FakeMediaUploader
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineUserEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvatarPublisherTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var avatarsDir: File
  private lateinit var cacheDir: File
  private val dao get() = db.inLoveDao()
  private val functions = FakeCloudFunctionsClient()
  private val media = FakeMediaUploader()

  private val publicUrl = "https://cdn.example.com/inlove_avt_uidA?v=1700000000000"
  private val copyDownscale: (File, File) -> Boolean = { s, d -> s.copyTo(d, overwrite = true); true }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
    cacheDir = File(context.cacheDir, "avatar_publisher_test").apply { deleteRecursively(); mkdirs() }
  }

  @After
  fun tearDown() {
    db.close()
    avatarsDir.deleteRecursively()
    cacheDir.deleteRecursively()
  }

  private fun publisher(
    enabled: Boolean = true,
    uid: String? = "uidA",
    downscale: (File, File) -> Boolean = copyDownscale,
  ) = AvatarPublisher(
    dao = dao,
    uploader = AvatarUploader(functions, media),
    avatarsDir = avatarsDir,
    cacheDir = cacheDir,
    enabled = { enabled },
    signedInUid = { uid },
    downscale = downscale,
  )

  private fun localFile(dir: File, name: String): File = File(dir, name).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }

  private fun urlOf(file: File): String = file.toURI().toString()

  private suspend fun user(uid: String, avatar: String) {
    dao.insertOnlineUser(OnlineUserEntity(uid = uid, coupleCode = "C-$uid", avatarUrl = avatar, isCurrentUser = true))
  }

  private suspend fun avatarOf(uid: String): String? = dao.getOnlineUserByUidSync(uid)?.avatarUrl

  private fun enqueueSigned() {
    functions.enqueue(
      mapOf(
        "uploadUrl" to "https://acct.r2.cloudflarestorage.com/avatars/inlove_avt_uidA?X-Amz-Signature=abc",
        "headers" to mapOf("content-type" to "image/jpeg", "content-length" to "4", "if-none-match" to "*"),
        "publicId" to "inlove_avt_uidA",
        "publicUrl" to publicUrl,
      ),
    )
  }

  @Test
  fun flagOff_doesNothingAtAll() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))

    assertEquals(AvatarPublisher.Outcome.NOTHING_TO_DO, publisher(enabled = false).publishPending())

    assertTrue(functions.calls.isEmpty())
    assertEquals(urlOf(file), avatarOf("uidA"))
  }

  @Test
  fun notSignedIn_isUnauthenticatedAndUploadsNothing() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))

    assertEquals(AvatarPublisher.Outcome.UNAUTHENTICATED, publisher(uid = null).publishPending())

    assertTrue(functions.calls.isEmpty())
    assertEquals(urlOf(file), avatarOf("uidA"))
  }

  @Test
  fun guestAndAccountRowsBothCurrent_onlyTheSignedInAccountRowIsPublished() = runTest {
    val guestFile = localFile(avatarsDir, "guest.jpg")
    val accountFile = localFile(avatarsDir, "account.jpg")
    user("guest", urlOf(guestFile))
    user("uidA", urlOf(accountFile))
    enqueueSigned()

    assertEquals(AvatarPublisher.Outcome.PUBLISHED, publisher().publishPending())

    assertEquals(publicUrl, avatarOf("uidA"))
    assertEquals(urlOf(guestFile), avatarOf("guest"))
    assertTrue(guestFile.exists())
    assertFalse(accountFile.exists())
  }

  @Test
  fun happyPath_swapsRowMirrorsCoupleCardDeletesOldAndTempFiles() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    dao.insertCoupleProfile(
      CoupleProfileEntity(
        partner1Name = "A", partner1Birthday = "", partner1ProfilePicture = urlOf(file),
        partner2Name = "B", partner2Birthday = "", partner2ProfilePicture = "",
      ),
    )
    enqueueSigned()

    assertEquals(AvatarPublisher.Outcome.PUBLISHED, publisher().publishPending())

    assertEquals(publicUrl, avatarOf("uidA"))
    assertEquals(publicUrl, dao.getCoupleProfileSync()?.partner1ProfilePicture)
    assertFalse(file.exists())
    assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    assertEquals(1, media.calls.size)
  }

  @Test
  fun uidChangesDuringUpload_isUnauthenticatedAndRowUntouched() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    enqueueSigned()
    var current: String? = "uidA"
    media.onUpload = { current = "uidB" }
    val p = AvatarPublisher(
      dao = dao,
      uploader = AvatarUploader(functions, media),
      avatarsDir = avatarsDir,
      cacheDir = cacheDir,
      enabled = { true },
      signedInUid = { current },
      downscale = copyDownscale,
    )

    assertEquals(AvatarPublisher.Outcome.UNAUTHENTICATED, p.publishPending())

    assertEquals(urlOf(file), avatarOf("uidA"))
    assertTrue(file.exists())
  }

  @Test
  fun oldFileStillExistsWhileUploading() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    enqueueSigned()
    var existedDuringUpload = false
    media.onUpload = { existedDuringUpload = file.exists() }

    publisher().publishPending()

    assertTrue(existedDuringUpload)
    assertFalse(file.exists())
  }

  @Test
  fun lostRace_keepsFileAndTheNewerValue() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    enqueueSigned()
    media.onUpload = { runBlocking { dao.replaceOnlineUserAvatar("uidA", urlOf(file), "https://other.example.com/x.jpg") } }

    assertEquals(AvatarPublisher.Outcome.LOST_RACE, publisher().publishPending())

    assertTrue(file.exists())
    assertEquals("https://other.example.com/x.jpg", avatarOf("uidA"))
  }

  @Test
  fun missingFile_clearsRowAndCoupleCardWithoutCallingTheServer() = runTest {
    val file = File(avatarsDir, "gone.jpg")
    user("uidA", urlOf(file))
    dao.insertCoupleProfile(
      CoupleProfileEntity(
        partner1Name = "A", partner1Birthday = "", partner1ProfilePicture = urlOf(file),
        partner2Name = "B", partner2Birthday = "", partner2ProfilePicture = "",
      ),
    )

    assertEquals(AvatarPublisher.Outcome.NOTHING_TO_DO, publisher().publishPending())

    assertEquals("", avatarOf("uidA"))
    assertEquals("", dao.getCoupleProfileSync()?.partner1ProfilePicture)
    assertTrue(functions.calls.isEmpty())
  }

  @Test
  fun uploadFailure_propagatesAndLeavesRowAndFileUntouched() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    enqueueSigned()
    media.failure = IOException("mất mạng")

    val e = runCatching { publisher().publishPending() }.exceptionOrNull()

    assertTrue(e is IOException)
    assertEquals(urlOf(file), avatarOf("uidA"))
    assertTrue(file.exists())
    assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun avatarsNotConfigured_isNotConfiguredAndKeepsEverything() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    functions.enqueueError(CloudFunctionsException(CloudFunctionsCode.FAILED_PRECONDITION, "avatars-not-configured"))

    assertEquals(AvatarPublisher.Outcome.NOT_CONFIGURED, publisher().publishPending())

    assertEquals(urlOf(file), avatarOf("uidA"))
    assertTrue(file.exists())
  }

  @Test
  fun unauthenticatedCallableError_isUnauthenticatedAndKeepsEverything() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    functions.enqueueError(CloudFunctionsException(CloudFunctionsCode.UNAUTHENTICATED, null))

    assertEquals(AvatarPublisher.Outcome.UNAUTHENTICATED, publisher().publishPending())

    assertEquals(urlOf(file), avatarOf("uidA"))
    assertTrue(file.exists())
  }

  @Test
  fun otherCallableError_isRethrown() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))
    functions.enqueueError(CloudFunctionsException(CloudFunctionsCode.RESOURCE_EXHAUSTED, null))

    val e = runCatching { publisher().publishPending() }.exceptionOrNull()

    assertEquals(CloudFunctionsCode.RESOURCE_EXHAUSTED, (e as CloudFunctionsException).code)
  }

  @Test
  fun fileOutsideAvatarsDir_isIgnoredAndLeftAlone() = runTest {
    val outside = localFile(File(context.filesDir, "other"), "x.jpg")
    user("uidA", urlOf(outside))

    assertEquals(AvatarPublisher.Outcome.NOTHING_TO_DO, publisher().publishPending())

    assertTrue(outside.exists())
    assertEquals(urlOf(outside), avatarOf("uidA"))
    assertTrue(functions.calls.isEmpty())
    outside.parentFile?.deleteRecursively()
  }

  @Test
  fun downscaleFails_nothingIsUploaded() = runTest {
    val file = localFile(avatarsDir, "a.jpg")
    user("uidA", urlOf(file))

    assertEquals(AvatarPublisher.Outcome.NOTHING_TO_DO, publisher(downscale = { _, _ -> false }).publishPending())

    assertTrue(functions.calls.isEmpty())
    assertEquals(urlOf(file), avatarOf("uidA"))
    assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
  }
}
