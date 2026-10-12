package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvatarFilesTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun staleLocalAvatar_returnsOldFileInsideDir() {
    val dir = tmp.newFolder("avatars")
    val old = java.io.File(dir, "user_avatar_1.jpg").apply { writeText("x") }
    val result = ProfileUtils.staleLocalAvatar(old.toURI().toString(), "https://x/y.jpg", dir)
    assertEquals(old.canonicalFile, result?.canonicalFile)
  }

  @Test
  fun staleLocalAvatar_nullWhenSameUrl() {
    val dir = tmp.newFolder("avatars")
    val old = java.io.File(dir, "user_avatar_1.jpg").apply { writeText("x") }
    val url = old.toURI().toString()
    assertNull(ProfileUtils.staleLocalAvatar(url, url, dir))
  }

  @Test
  fun staleLocalAvatar_nullForHttpsUrl() {
    val dir = tmp.newFolder("avatars")
    assertNull(ProfileUtils.staleLocalAvatar("https://x/y.jpg", "", dir))
  }

  @Test
  fun staleLocalAvatar_nullForFileOutsideDir() {
    val dir = tmp.newFolder("avatars")
    val outside = java.io.File(tmp.root, "x.jpg").apply { writeText("x") }
    assertNull(ProfileUtils.staleLocalAvatar("file:" + dir.path + "/../x.jpg", "", dir))
    assertNull(ProfileUtils.staleLocalAvatar(outside.toURI().toString(), "", dir))
  }

  @Test
  fun shareableAvatar_blanksFileScheme() {
    assertEquals("", ProfileUtils.shareableAvatar("file:/data/avatars/a.jpg"))
  }

  @Test
  fun shareableAvatar_keepsHttps() {
    assertEquals("https://x/y.jpg", ProfileUtils.shareableAvatar("https://x/y.jpg"))
  }
}
