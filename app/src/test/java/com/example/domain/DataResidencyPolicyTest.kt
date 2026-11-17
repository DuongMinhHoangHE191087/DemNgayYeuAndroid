package com.example.domain

import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.MemoryMediaRepository
import com.example.domain.media.SaveSharedMemoryOutcome
import com.example.domain.media.SaveSharedMemoryUseCase
import com.example.domain.media.UploadMemoryMediaUseCase
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResidencyPolicyTest {
  private class Stored(val shareWithCouple: Boolean, val publicId: String?)

  private class OfflineRepo : MemoryMediaRepository {
    var uploads = 0

    override suspend fun upload(relationshipId: String, memoryId: String, media: MemoryMediaFile): String {
      uploads++
      throw IOException("offline")
    }

    override suspend fun deliveryUrl(relationshipId: String, memoryId: String): String = throw IOException("offline")
  }

  private val photo = MemoryMediaFile("photo.jpg", MediaKind.IMAGE, "image/jpeg", 2_000_000L)

  @Test
  fun production_isLocalOnly_withoutCloudContent_andAvatarsOff() {
    assertEquals(DataResidency.LOCAL_ONLY, DataResidencyPolicy.current)
    assertFalse(DataResidencyPolicy.cloudContent)
    assertFalse(DataResidencyPolicy.avatarsOnR2)
  }

  @Test
  fun profileOnline_isOn_whileAvatarsOff() {
    assertTrue(DataResidencyPolicy.profileOnline)
    assertFalse(DataResidencyPolicy.avatarsOnR2)
  }

  @Test
  fun defaults_signedOutShare_savesLocally_withoutUpload() = runBlocking {
    val repo = OfflineRepo()
    val stored = mutableListOf<Stored>()
    val outcome = SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(repo))(
      signedIn = false, relationshipId = null, wantsShare = true, media = photo,
    ) { _, share, publicId -> stored += Stored(share, publicId) }
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = true), outcome)
    assertEquals(0, repo.uploads)
    assertEquals(1, stored.size)
    assertNull(stored.single().publicId)
  }

  @Test
  fun defaults_pairedShareWithMedia_offline_savesWithoutUpload() = runBlocking {
    val repo = OfflineRepo()
    val stored = mutableListOf<Stored>()
    val outcome = SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(repo))(
      signedIn = true, relationshipId = "rel_1", wantsShare = true, media = photo,
    ) { _, share, publicId -> stored += Stored(share, publicId) }
    assertEquals(SaveSharedMemoryOutcome.Saved(unpaired = false), outcome)
    assertEquals(0, repo.uploads)
    assertEquals(true, stored.single().shareWithCouple)
    assertNull(stored.single().publicId)
  }

  @Test
  fun cloudContentEnabled_signedOutShare_stillRequiresSignIn() = runBlocking {
    val repo = OfflineRepo()
    val outcome = SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(repo), cloudContent = true)(
      signedIn = false, relationshipId = null, wantsShare = true, media = photo,
    ) { _, _, _ -> }
    assertEquals(SaveSharedMemoryOutcome.NotSignedIn, outcome)
    assertEquals(0, repo.uploads)
  }
}
