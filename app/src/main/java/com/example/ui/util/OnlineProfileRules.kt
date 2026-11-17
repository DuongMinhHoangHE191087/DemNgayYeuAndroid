package com.example.ui.util

import com.example.data.model.OnlineUserEntity
import com.example.data.repository.OnlineCoupleRepository

/** The signed-out guest and a blank uid both mean the card has no online profile. */
internal fun profileUidOf(uid: String?): String? =
  uid?.takeIf { it.isNotBlank() && it != OnlineCoupleRepository.GUEST_UID }

/** Registration used to persist one stock photo for every account; treat it as "no photo". */
internal fun displayAvatarUrl(url: String?): String =
  url?.takeIf { it.isNotBlank() && !it.contains("photo-1534528741775") }.orEmpty()

/** Room owns a card only when no online profile feeds it (guest, signed out, no partner). */
internal fun roomOwnsCard(onlineUid: String?): Boolean = profileUidOf(onlineUid) == null

/** A different or vanished profile replaces the one a card applied; a first arrival replaces nothing. */
internal fun isProfileChanged(applied: String?, uid: String?): Boolean = applied != null && applied != uid

/** A blank field clears only for the profile already on the card; otherwise the card keeps what it shows. */
internal fun <T> profileFieldAfter(current: T, incoming: T, isBlank: Boolean, sameProfile: Boolean): T =
  if (isBlank && !sameProfile) current else incoming

/** Display only: fills a row's blank name, birthday and avatar from the local card; a set name is never replaced. */
internal fun OnlineUserEntity.withCardFallback(name: String, birth: String, avatar: String): OnlineUserEntity {
  val fillName = effectiveDisplayName.isBlank() && name.isNotBlank()
  return copy(
    displayName = if (fillName) name else displayName,
    isProfileSetup = isProfileSetup || fillName,
    birthDate = birthDate.ifBlank { birth },
    avatarUrl = avatarUrl.ifBlank { avatar },
  )
}

/** Shown for the me-side name when it is unknown. */
internal fun displayMeName(name: String, isEnglish: Boolean): String =
  name.ifBlank { if (isEnglish) "You" else "Bạn" }

/** Shown for the partner-side name when it is unknown. */
internal fun displayPartnerName(name: String, isEnglish: Boolean): String =
  name.ifBlank { if (isEnglish) "Partner" else "Người ấy" }

/** Bio given to a new profile. Older builds appended an emoji; see [isLegacyWelcomeBio]. */
internal const val WELCOME_BIO = "Chào mừng bạn đến với InLove"

/** A bio the app generated itself in an older build (welcome text + emoji), as opposed to one the user typed. */
internal fun isLegacyWelcomeBio(bio: String): Boolean =
  bio.startsWith(WELCOME_BIO) && bio.length > WELCOME_BIO.length && cleanName(bio.removePrefix(WELCOME_BIO)).isEmpty()

/** Love note used when the sender writes none. */
internal const val DEFAULT_LOVE_NOTE = "Cùng anh/em xây dựng hạnh phúc Set Love nhé!"

/** Love title used when the couple has none. */
internal const val DEFAULT_LOVE_TITLE = "Hành Trình Yêu Thương"
