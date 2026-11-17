package com.example.link

/** Thu tu = chi so tab 0..4. */
enum class DeepLinkTab { Home, Memories, Calendar, Gifts, Settings }

sealed interface DeepLink {
  data class Tab(val tab: DeepLinkTab) : DeepLink

  data class Pair(val code: String) : DeepLink
}
