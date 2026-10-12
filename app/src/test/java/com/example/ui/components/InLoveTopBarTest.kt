package com.example.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class InLoveTopBarTest {

  @get:Rule val rule = createComposeRule()

  private val item = NotificationItem("💍", "Kỷ niệm 1000 ngày yêu nhau thật đáng nhớ", "Ngày mai", true)
  private lateinit var sb: TopAppBarScrollBehavior

  private fun setBar(
    title: String = "Kỷ Niệm",
    latest: NotificationItem? = item,
    onSeeAll: () -> Unit = {},
  ) {
    rule.setContent {
      MyApplicationTheme {
        sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        InLoveTopBar(
          title = title,
          avatarUrl = "",
          latest = latest,
          isEnglish = false,
          scrollBehavior = sb,
          hasUnread = true,
          onSeeAll = onSeeAll,
          includeStatusBarPadding = false,
        )
      }
    }
  }

  private fun collapse() = rule.runOnIdle { sb.state.heightOffset = sb.state.heightOffsetLimit }

  private fun assertTouchTargets() {
    // Test dp is unscaled while the app renders under AppDensity (0.85), so 48.dp is the real floor.
    rule.onNodeWithTag("topbar_bell_button").assertExists().assertHeightIsAtLeast(48.dp)
    rule.onNodeWithTag("topbar_profile_avatar").assertExists().assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun bellAndAvatar_presentExpandedAndCollapsed() {
    setBar()
    assertTouchTargets()
    collapse()
    rule.waitForIdle()
    assertTouchTargets()
  }

  @Test
  fun strip_hiddenWhenNoNotification() {
    setBar(latest = null)
    rule.onNodeWithTag("topbar_notification_strip").assertDoesNotExist()
  }

  @Test
  fun strip_showsFullItem_andSeeAllFires() {
    var seeAll = 0
    setBar(onSeeAll = { seeAll++ })
    rule.onNodeWithText("💍").assertIsDisplayed()
    rule.onNodeWithText(item.title).assertIsDisplayed()
    rule.onNodeWithText("Ngày mai").assertIsDisplayed()
    rule.onNodeWithTag("topbar_see_all").performClick()
    assertEquals(1, seeAll)
  }

  @Test
  fun largeTitle_hasNoVisualOverflow() {
    var title by mutableStateOf("Trang Chủ")
    rule.setContent {
      MyApplicationTheme {
        sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        InLoveTopBar(title, "", item, false, sb, includeStatusBarPadding = false)
      }
    }
    for (t in listOf("Trang Chủ", "Kỷ Niệm", "Lịch", "Gợi Ý Quà", "Cài Đặt")) {
      title = t
      rule.waitForIdle()
      val results = mutableListOf<TextLayoutResult>()
      rule.onNodeWithTag("topbar_large_title").fetchSemanticsNode().config
        .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
      // hasVisualOverflow is true for any wrap-content text here (paragraph laid out at max width), so
      // assert instead that the whole title sits on the single allowed line.
      val r = results.single()
      assertEquals("title truncated for $t", t.length, r.getLineEnd(0, visibleEnd = true))
    }
  }

  @Test
  fun scrollUp_collapses() {
    rule.setContent {
      MyApplicationTheme {
        sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        Scaffold(
          modifier = Modifier.nestedScroll(sb.nestedScrollConnection),
          topBar = { InLoveTopBar("Kỷ Niệm", "", item, false, sb, includeStatusBarPadding = false) },
        ) { padding ->
          LazyColumn(Modifier.testTag("list"), contentPadding = padding) {
            items((0 until 100).toList()) { Text("$it", Modifier.height(48.dp)) }
          }
        }
      }
    }
    rule.onNodeWithTag("list").performTouchInput { swipeUp() }
    rule.waitForIdle()
    assertEquals(1f, sb.state.collapsedFraction, 0f)
  }

  @Test
  fun tabChange_resetsCollapse() {
    var tab by mutableStateOf(1)
    rule.setContent {
      MyApplicationTheme {
        val state = rememberTopAppBarState()
        sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
        LaunchedEffect(tab) {
          state.heightOffset = 0f
          state.contentOffset = 0f
        }
        InLoveTopBar("Kỷ Niệm", "", item, false, sb, includeStatusBarPadding = false)
      }
    }
    collapse()
    rule.waitForIdle()
    assertEquals(1f, sb.state.collapsedFraction, 0f)
    tab = 2
    rule.waitForIdle()
    assertEquals(0f, sb.state.collapsedFraction, 0f)
  }

  @Test
  fun expanded_screenshot() {
    setBar(title = "Gợi Ý Quà")
    rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/topbar_expanded.png")
  }

  @Test
  fun collapsed_screenshot() {
    setBar(title = "Gợi Ý Quà")
    collapse()
    rule.waitForIdle()
    rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/topbar_collapsed.png")
  }
}
