package com.example.domain

import com.example.data.model.GiftIdeaEntity
import org.junit.Assert.assertEquals
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
}
