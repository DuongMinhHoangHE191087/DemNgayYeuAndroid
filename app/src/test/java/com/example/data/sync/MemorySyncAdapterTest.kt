package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import org.junit.Test

class MemorySyncAdapterTest {

  private val base = SharedMemoryEntity(
    title = "Lần đầu hẹn hò",
    dateText = "2024-02-14",
    photoUri = "",
    relationshipId = "rel_1",
    authorId = "uid_a",
    syncId = "sync-abc",
    updatedAt = 1_700_000_000_000L,
  )

  @Test
  fun toFirestoreMap_roundTripsThroughFromFirestoreDoc_viaMap() {
    val original = SharedMemoryEntity(
      id = 5,
      title = "Lần đầu hẹn hò",
      dateText = "2024-02-14",
      note = "Trời mưa nhưng vui",
      photoUri = "https://cdn/photo.jpg",
      relationshipId = "rel_1",
      authorId = "uid_a",
      syncId = "sync-abc",
      updatedAt = 1_700_000_000_000L,
      pendingSync = true
    )

    val map = MemorySyncAdapter.toFirestoreMap(original)

    assert(map["title"] == "Lần đầu hẹn hò")
    assert(map["syncId"] == "sync-abc")
    assert(map["relationshipId"] == "rel_1")
    assert(map["deleted"] == false)
    // pendingSync is a local-only flag and must never be pushed to Firestore
    assert(!map.containsKey("pendingSync"))
  }

  @Test
  fun entityType_isStableIdentifierUsedByTheOutbox() {
    assert(MemorySyncAdapter.entityType == "memory")
  }

  @Test
  fun toFirestoreMap_neverSendsLocalUrisOrLegacyCloudinaryFields() {
    val map = MemorySyncAdapter.toFirestoreMap(
      base.copy(
        photoUri = "file:///data/user/0/a.jpg",
        videoUri = "content://media/video.mp4",
        cloudinaryUrl = "https://res.cloudinary.com/demo/public.jpg",
        isCloudinaryStored = true,
      )
    )

    assert(map["photoUri"] == "")
    assert(map["videoUri"] == "")
    assert(!map.containsKey("cloudinaryUrl")) { "the public link must never leave the device" }
    assert(!map.containsKey("isCloudinaryStored"))
  }

  @Test
  fun toFirestoreMap_blanksPhotoHostedOnCloudinary() {
    val map = MemorySyncAdapter.toFirestoreMap(base.copy(photoUri = "https://res.cloudinary.com/demo/image/upload/a.jpg"))

    assert(map["photoUri"] == "")
  }

  @Test
  fun toFirestoreMap_keepsHttpsPhotoOutsideCloudinary() {
    val map = MemorySyncAdapter.toFirestoreMap(base.copy(photoUri = "https://images.unsplash.com/photo-1.jpg"))

    assert(map["photoUri"] == "https://images.unsplash.com/photo-1.jpg")
  }

  @Test
  fun toFirestoreMap_keepsPublicIdOnlyWhenBoundToThisMemory() {
    val bound = MemorySyncAdapter.toFirestoreMap(base.copy(cloudinaryPublicId = "inlove_mem_sync-abc"))
    val foreign = MemorySyncAdapter.toFirestoreMap(base.copy(cloudinaryPublicId = "inlove_mem_other"))

    assert(bound["cloudinaryPublicId"] == "inlove_mem_sync-abc")
    assert(foreign["cloudinaryPublicId"] == "")
  }

  @Test
  fun toFirestoreMap_tombstoneSendsNoMedia() {
    val map = MemorySyncAdapter.toFirestoreMap(
      base.copy(
        deleted = true,
        photoUri = "https://images.unsplash.com/photo-1.jpg",
        cloudinaryPublicId = "inlove_mem_sync-abc",
      )
    )

    assert(map["deleted"] == true)
    assert(map["photoUri"] == "") { "a tombstone must not keep the photo" }
    assert(map["cloudinaryPublicId"] == "") { "a tombstone must release the cloud asset" }
  }

  @Test
  fun safeSharedPhotoUri_keepsOnlyHttpsUrlsOutsideCloudinary() {
    assert(MemorySyncAdapter.safeSharedPhotoUri(null) == "")
    assert(MemorySyncAdapter.safeSharedPhotoUri("file:///a.jpg") == "")
    assert(MemorySyncAdapter.safeSharedPhotoUri("http://images.example.com/a.jpg") == "")
    assert(MemorySyncAdapter.safeSharedPhotoUri("https://cloudinary.com/a.jpg") == "")
    assert(MemorySyncAdapter.safeSharedPhotoUri("https://res.cloudinary.com/a.jpg") == "")
    assert(MemorySyncAdapter.safeSharedPhotoUri("https://images.example.com/a.jpg") == "https://images.example.com/a.jpg")
  }

  @Test
  fun safePublicId_acceptsOnlyTheMemoryOwnName() {
    assert(MemorySyncAdapter.safePublicId("sync-abc", "inlove_mem_sync-abc") == "inlove_mem_sync-abc")
    assert(MemorySyncAdapter.safePublicId("sync-abc", "inlove_mem_other") == "")
    assert(MemorySyncAdapter.safePublicId("sync-abc", null) == "")
  }
}
