package com.example.data.media

import java.io.File
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaPathTest {
  @get:Rule
  val tmp = TemporaryFolder()

  private lateinit var filesDir: File
  private lateinit var mediaDir: File

  @Before
  fun setUp() {
    filesDir = tmp.newFolder("files")
    mediaDir = File(filesDir, "media").also { it.mkdirs() }
  }

  private fun mediaFile(name: String): File = File(mediaDir, name).also { it.writeText("x") }

  private fun otherFile(dir: String, name: String): File {
    val d = File(filesDir, dir).also { it.mkdirs() }
    return File(d, name).also { it.writeText("x") }
  }

  @Test
  fun nullInNullOut() {
    assertNull(MediaPath.toStored(filesDir, null))
    assertNull(MediaPath.resolve(filesDir, null))
    assertNull(MediaPath.ownedFile(filesDir, null))
  }

  @Test
  fun roundTripAbsolutePath() {
    val f = mediaFile("a.jpg")
    assertEquals("media/a.jpg", MediaPath.toStored(filesDir, f.absolutePath))
  }

  @Test
  fun roundTripFileUri() {
    val f = mediaFile("a.jpg")
    assertEquals("media/a.jpg", MediaPath.toStored(filesDir, f.toURI().toString()))
  }

  @Test
  fun roundTripRelative() {
    val f = mediaFile("a.jpg")
    // Đã là tương đối thì giữ nguyên.
    assertEquals("media/a.jpg", MediaPath.toStored(filesDir, "media/a.jpg"))
    val resolved = MediaPath.resolve(filesDir, "media/a.jpg")!!
    assertTrue(resolved.startsWith("file:"))
    assertEquals(f.canonicalFile, MediaPath.ownedFile(filesDir, resolved))
    assertEquals("media/a.jpg", MediaPath.toStored(filesDir, resolved))
  }

  @Test
  fun resolveKeepsLegacyAndRemoteValues() {
    val f = mediaFile("a.jpg")
    val uri = f.toURI().toString()
    assertEquals(f.absolutePath, MediaPath.resolve(filesDir, f.absolutePath))
    assertEquals(uri, MediaPath.resolve(filesDir, uri))
    assertEquals("content://x/1", MediaPath.resolve(filesDir, "content://x/1"))
    assertEquals("https://x.y/a.jpg", MediaPath.resolve(filesDir, "https://x.y/a.jpg"))
    // File không tồn tại trong media vẫn resolve nếu nằm trong thư mục media (ownedFile không cần file có thật).
    assertTrue(MediaPath.resolve(filesDir, "media/missing.jpg")!!.startsWith("file:"))
    // Tương đối nhưng nằm ngoài media thì trả nguyên.
    assertEquals("media/../avatars/x", MediaPath.resolve(filesDir, "media/../avatars/x"))
  }

  @Test
  fun namesWithSpacesAndVietnameseMatch() {
    val name = "ảnh đẹp 1.jpg"
    val f = mediaFile(name)
    assertEquals("media/$name", MediaPath.toStored(filesDir, f.absolutePath))
    assertEquals("media/$name", MediaPath.toStored(filesDir, f.toURI().toString()))
    // Dạng percent-encoded (như Uri.fromFile) cũng phải khớp.
    val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    val uri = mediaDir.toURI().toString() + encoded
    assertEquals("media/$name", MediaPath.toStored(filesDir, uri))
    assertEquals(f.canonicalFile, MediaPath.ownedFile(filesDir, uri))
    val resolved = MediaPath.resolve(filesDir, "media/$name")
    assertEquals("media/$name", MediaPath.toStored(filesDir, resolved))
  }

  @Test
  fun plusInNameIsNotTurnedIntoSpace() {
    val f = mediaFile("a+b.jpg")
    val uri = mediaDir.toURI().toString() + "a%2Bb.jpg"
    assertEquals("media/a+b.jpg", MediaPath.toStored(filesDir, uri))
    assertEquals("media/a+b.jpg", MediaPath.toStored(filesDir, f.toURI().toString()))
  }

  @Test
  fun passThroughValues() {
    val values = listOf(
      "https://x.y/a.jpg",
      "content://media/external/images/1",
      "",
      "   ",
      "../x",
      "media/a.jpg"
    )
    for (v in values) {
      assertEquals(v, MediaPath.toStored(filesDir, v))
    }
  }

  @Test
  fun ownedFileRejectsNonMediaValues() {
    for (v in listOf("https://x.y/a.jpg", "content://media/1", "", "   ", "../x")) {
      assertNull("ownedFile($v)", MediaPath.ownedFile(filesDir, v))
    }
  }

  @Test
  fun ownedFileRejectsOutsideAndMediaDirItself() {
    val avatar = otherFile("avatars", "x")
    val sibling = otherFile("media2", "x")
    assertNull(MediaPath.ownedFile(filesDir, avatar.absolutePath))
    assertNull(MediaPath.ownedFile(filesDir, avatar.toURI().toString()))
    assertNull(MediaPath.ownedFile(filesDir, sibling.absolutePath))
    assertNull(MediaPath.ownedFile(filesDir, sibling.toURI().toString()))
    assertNull(MediaPath.ownedFile(filesDir, mediaDir.absolutePath))
    assertNull(MediaPath.ownedFile(filesDir, mediaDir.toURI().toString()))
    assertNull(MediaPath.ownedFile(filesDir, "media"))
    assertNull(MediaPath.ownedFile(filesDir, "media/../avatars/x"))
    assertNull(MediaPath.ownedFile(filesDir, "avatars/x"))
    // toStored không đổi những giá trị này.
    assertEquals(avatar.absolutePath, MediaPath.toStored(filesDir, avatar.absolutePath))
    assertEquals(sibling.absolutePath, MediaPath.toStored(filesDir, sibling.absolutePath))
    assertEquals(mediaDir.absolutePath, MediaPath.toStored(filesDir, mediaDir.absolutePath))
  }

  @Test
  fun ownedFileAcceptsAllThreeForms() {
    val f = mediaFile("a.jpg")
    assertNotNull(MediaPath.ownedFile(filesDir, "media/a.jpg"))
    assertNotNull(MediaPath.ownedFile(filesDir, f.absolutePath))
    assertNotNull(MediaPath.ownedFile(filesDir, f.toURI().toString()))
  }

  @Test
  fun deleteUnreferencedKeepsLegacyAbsoluteReference() {
    val kept = mediaFile("kept.jpg")
    val gone = mediaFile("gone.mp4")
    val n = MediaPath.deleteUnreferenced(
      filesDir,
      listOf("media/kept.jpg", "media/gone.mp4"),
      listOf(kept.absolutePath, null, "https://x.y/z.jpg")
    )
    assertEquals(1, n)
    assertTrue(kept.exists())
    assertFalse(gone.exists())
  }

  @Test
  fun deleteUnreferencedKeepsFileUriReference() {
    val kept = mediaFile("kept.jpg")
    val n = MediaPath.deleteUnreferenced(filesDir, listOf(kept.absolutePath), listOf(kept.toURI().toString()))
    assertEquals(0, n)
    assertTrue(kept.exists())
  }

  @Test
  fun deleteUnreferencedCountsEachDeletedFile() {
    val a = mediaFile("a.jpg")
    val b = mediaFile("b.jpg")
    val c = mediaFile("c.jpg")
    val n = MediaPath.deleteUnreferenced(
      filesDir,
      listOf("media/a.jpg", b.toURI().toString(), c.absolutePath, "media/a.jpg", null),
      emptyList()
    )
    assertEquals(3, n)
    assertFalse(a.exists())
    assertFalse(b.exists())
    assertFalse(c.exists())
  }

  @Test
  fun deleteUnreferencedDoesNotThrowWhenFileAlreadyGone() {
    val n = MediaPath.deleteUnreferenced(filesDir, listOf("media/missing.jpg"), emptyList())
    assertEquals(0, n)
  }

  @Test
  fun deleteUnreferencedNeverTouchesFilesOutsideMedia() {
    val avatar = otherFile("avatars", "x")
    val sibling = otherFile("media2", "x")
    val n = MediaPath.deleteUnreferenced(
      filesDir,
      listOf(avatar.absolutePath, sibling.toURI().toString(), "media/../avatars/x", "https://x.y/a.jpg", ""),
      emptyList()
    )
    assertEquals(0, n)
    assertTrue(avatar.exists())
    assertTrue(sibling.exists())
  }
}
