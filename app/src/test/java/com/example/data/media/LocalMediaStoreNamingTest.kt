package com.example.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaStoreNamingTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun fiftyConsecutiveCallsGiveFiftyDistinctNamesInsideDir() {
    val dir = tmp.newFolder("media")
    val files = (1..50).map { LocalMediaStore.newMediaFile(dir, "picked_", ".jpg") }
    assertEquals(50, files.map { it.name }.toSet().size)
    for (f in files) {
      assertTrue(f.name, f.name.startsWith("picked_"))
      assertTrue(f.name, f.name.endsWith(".jpg"))
      assertEquals(dir, f.parentFile)
    }
  }
}
