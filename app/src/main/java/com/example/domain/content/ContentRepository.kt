package com.example.domain.content

import com.example.ui.util.AppLanguage

/**
 * Single seam for all "editorial content" that the product owner should be able to change
 * from Firebase without shipping a new app release — the gift-idea catalog and the
 * Vietnamese/Western holiday calendar today, more content sets later.
 *
 * Implementations MUST NOT throw and MUST NOT return an empty list when the remote source is
 * unreachable — they should fall back to a bundled local default (see
 * `com.example.data.content.FirebaseContentRepository`) so the app is always usable offline
 * and on first install, before any remote fetch has completed.
 */
interface ContentRepository {

    /**
     * The gift-idea catalog, resolved to a single language. `language` only affects the
     * *bundled fallback* content (which is authored bilingually); documents fetched from the
     * live Firestore `gift_ideas` collection already carry a single `title` string as typed
     * by whoever edited them there, same as today.
     */
    suspend fun getGiftCatalog(language: AppLanguage): List<GiftCatalogItem>

    /**
     * The holiday calendar entries relevant to [language] (Vietnamese calendar for
     * [AppLanguage.VI], Western/US calendar for [AppLanguage.EN]) — additive per language,
     * not merged, matching the existing `VietnameseHolidays`/`WesternHolidays` split.
     */
    suspend fun getHolidayCalendar(language: AppLanguage): List<HolidayCalendarItem>
}
