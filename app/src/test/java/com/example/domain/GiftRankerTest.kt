package com.example.domain

import com.example.data.model.GiftIdeaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftRankerTest {
  private fun g(title: String, target: String = "") = GiftIdeaEntity(
    title = title, category = "", badgeText = "", tag = "", description = "", imageUrl = "", targetInterests = target
  )

  @Test fun `interest match ranks first, ties keep order`() {
    val a = g("A"); val b = g("Cà phê đôi", "coffee"); val c = g("C")
    assertEquals(listOf("Cà phê đôi", "A", "C"), GiftRanker.rank(listOf(a, b, c), setOf("coffee")).map { it.title })
    assertEquals(listOf("A", "Cà phê đôi", "C"), GiftRanker.rank(listOf(a, b, c), emptySet()).map { it.title })
  }

  @Test fun `ai flag gives no ranking bonus`() {
    val plain = g("Plain")
    val ai = g("Plain").copy(isAiGenerated = true)
    assertEquals(listOf(false, true), GiftRanker.rank(listOf(plain, ai), emptySet()).map { it.isAiGenerated })
  }

  @Test fun `first anniversary also fits gifts tagged for the anniversary`() {
    assertTrue(GiftRanker.fitsOccasion(g("A").copy(suggestedOccasion = "Kỷ niệm ngày yêu"), "Kỷ niệm 1 năm"))
  }

  @Test fun `first anniversary ranks the anniversary tagged gift first`() {
    val plain = g("plain")
    val anniv = g("anniv").copy(suggestedOccasion = "Kỷ niệm ngày yêu")
    assertEquals(listOf("anniv", "plain"), GiftRanker.rank(listOf(plain, anniv), emptySet(), "Kỷ niệm 1 năm").map { it.title })
  }

  @Test fun `anniversary does not fit gifts tagged for the first year`() {
    assertFalse(GiftRanker.fitsOccasion(g("A").copy(suggestedOccasion = "Kỷ niệm 1 năm"), "Kỷ niệm ngày yêu"))
  }

  @Test fun `first anniversary does not fit a birthday gift`() {
    assertFalse(GiftRanker.fitsOccasion(g("A").copy(suggestedOccasion = "Sinh nhật"), "Kỷ niệm 1 năm"))
  }
}
