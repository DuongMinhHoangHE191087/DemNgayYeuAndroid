package com.example.ui.util

import com.example.data.repository.OnlineCoupleRepository

/** The signed-out guest and a blank uid both mean the card has no online profile. */
internal fun profileUidOf(uid: String?): String? =
  uid?.takeIf { it.isNotBlank() && it != OnlineCoupleRepository.GUEST_UID }

/** A different or vanished profile replaces the one a card applied; a first arrival replaces nothing. */
internal fun isProfileChanged(applied: String?, uid: String?): Boolean = applied != null && applied != uid

/** A blank field clears only for the profile already on the card; otherwise the card keeps what it shows. */
internal fun <T> profileFieldAfter(current: T, incoming: T, isBlank: Boolean, sameProfile: Boolean): T =
  if (isBlank && !sameProfile) current else incoming
