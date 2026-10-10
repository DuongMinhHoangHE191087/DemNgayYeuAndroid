package com.example.domain

import com.example.data.model.GiftIdeaEntity

/** Xếp hạng gợi ý quà cục bộ theo sở thích chung + dịp; không cần mạng. Hoà điểm giữ nguyên thứ tự gốc. */
object GiftRanker {
  private fun normalize(interests: Set<String>) = interests.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

  private fun targets(g: GiftIdeaEntity) = g.targetInterests.lowercase().split(',').map { it.trim() }

  private fun text(g: GiftIdeaEntity) = "${g.title} ${g.tag} ${g.category} ${g.description}".lowercase()

  /** Dịp trùng tên hoặc quà dùng được cho "bất kỳ dịp nào". */
  private fun occasionFits(g: GiftIdeaEntity, occ: String): Boolean =
    occ.isNotEmpty() && g.suggestedOccasion.lowercase().let { it == occ || it.contains("bất kỳ") || it.contains("any") }

  /** Có ít nhất một khoá sở thích nằm trong nhãn đối tượng hoặc nội dung quà. */
  fun matchesAnyInterest(g: GiftIdeaEntity, interests: Set<String>): Boolean {
    val target = targets(g)
    val text = text(g)
    return normalize(interests).any { it in target || text.contains(it) }
  }

  /** Quà hợp [occasion] theo cùng luật mà [rank] dùng để cộng điểm. */
  fun fitsOccasion(g: GiftIdeaEntity, occasion: String): Boolean = occasionFits(g, occasion.trim().lowercase())

  fun rank(ideas: List<GiftIdeaEntity>, interests: Set<String>, occasion: String = ""): List<GiftIdeaEntity> {
    val keys = normalize(interests)
    val occ = occasion.trim().lowercase()
    fun score(g: GiftIdeaEntity): Int {
      val target = targets(g)
      val text = text(g)
      var s = keys.sumOf { k -> (if (k in target) 3 else 0) + (if (text.contains(k)) 1 else 0) }
      if (occasionFits(g, occ)) s += 2
      if (g.isAiGenerated) s += 1
      return s
    }
    return ideas.sortedByDescending { score(it) } // sortedBy ổn định
  }
}
