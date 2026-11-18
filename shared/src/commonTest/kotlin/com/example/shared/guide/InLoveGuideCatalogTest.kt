package com.example.shared.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InLoveGuideCatalogTest {

    @Test
    fun catalog_containsFourChaptersWithThreeConciseStepsEach() {
        val chapters = InLoveGuideCatalog.CHAPTERS
        assertEquals(4, chapters.size)
        assertEquals(GuideChapterId.entries.toSet(), chapters.map { it.id }.toSet())
        assertEquals(GuideQuickAction.entries.toSet(), chapters.map { it.quickAction }.toSet())

        val forbiddenTerms = listOf("firestore", "sqlite", "room db", "sharedpreferences", "database", "cơ sở dữ liệu")
        for (chapter in chapters) {
            assertEquals(chapter, InLoveGuideCatalog.chapterFor(chapter.id))
            assertTrue(chapter.shortTabVi.isNotBlank())
            assertTrue(chapter.shortTabEn.isNotBlank())
            assertTrue(chapter.titleVi.isNotBlank())
            assertTrue(chapter.titleEn.isNotBlank())
            assertTrue(chapter.summaryVi.isNotBlank())
            assertTrue(chapter.summaryEn.isNotBlank())
            assertEquals(listOf("01", "02", "03"), chapter.steps.map { it.numberLabel })

            for (step in chapter.steps) {
                val viWords = step.detailVi.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val enWords = step.detailEn.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                assertTrue(viWords.size in 4..14, "${chapter.id} ${step.numberLabel} VI too long: ${viWords.size}")
                assertTrue(enWords.size in 4..14, "${chapter.id} ${step.numberLabel} EN too long: ${enWords.size}")
            }

            val allCopy = listOf(
                chapter.shortTabVi,
                chapter.shortTabEn,
                chapter.titleVi,
                chapter.titleEn,
                chapter.summaryVi,
                chapter.summaryEn,
                chapter.quickActionLabelVi,
                chapter.quickActionLabelEn,
            ) + chapter.steps.flatMap { listOf(it.titleVi, it.titleEn, it.detailVi, it.detailEn) }

            for (text in allCopy) {
                assertFalse(text.contains('!'), "Guide copy should avoid exclamation marks: $text")
                assertFalse(text.any { it.isSurrogate() }, "Guide copy must stay emoji-free: $text")
                val lower = text.lowercase()
                for (term in forbiddenTerms) {
                    assertFalse(lower.contains(term), "Guide copy leaked $term: $text")
                }
            }
        }
    }
}
