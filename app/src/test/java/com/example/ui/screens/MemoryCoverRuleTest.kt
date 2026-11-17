package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryCoverRuleTest {
  private val preset = "https://res.example.com/presets/cover1.jpg"

  @Test
  fun firstPick_dropsThePresetSoThePickedPhotoIsTheCover() {
    val out = withPickedMedia(listOf(preset), listOf("file:///a.jpg", "file:///b.jpg"))

    assertEquals(listOf("file:///a.jpg", "file:///b.jpg"), out)
    assertEquals("file:///a.jpg", out.first())
  }

  @Test
  fun laterPicks_appendWithoutDuplicatesAndStopAtTen() {
    val first = (1..8).map { "file:///$it.jpg" }

    val out = withPickedMedia(first, listOf("file:///8.jpg", "file:///9.jpg", "file:///10.jpg", "file:///11.jpg"))

    assertEquals((1..10).map { "file:///$it.jpg" }, out)
  }
}
