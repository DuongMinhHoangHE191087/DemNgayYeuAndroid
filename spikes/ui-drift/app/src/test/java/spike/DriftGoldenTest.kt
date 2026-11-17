package spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.InLoveBottomNav
import com.example.ui.components.InLoveDatePickerDialog
import com.example.ui.components.InLoveDatePickerField
import com.example.ui.components.InLoveTopBar
import com.example.ui.components.NotificationItem
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Surface as AppSurface
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 8 golden screens for the Material3 drift spike. `recordRoborazziDebug` writes the PNGs in src/test/snapshots with the
 * androidx baseline; `verifyRoborazziDebug -PcomposeVariant=cmp` re-renders with Compose Multiplatform and compares.
 * Strict comparator on purpose: maxDistance 0 (any colour difference counts), changeThreshold 0.5% = gate threshold.
 */
@OptIn(ExperimentalMaterial3Api::class, com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class DriftGoldenTest {

  @get:Rule val rule = createComposeRule()

  private val options = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
      imageComparator = SimpleImageComparator(maxDistance = 0f),
      resultValidator = ThresholdValidator(0.005f),
    ),
  )

  private fun show(content: @Composable () -> Unit) {
    rule.setContent { MyApplicationTheme { content() } }
    rule.waitForIdle()
  }

  private fun shot(name: String) =
    rule.onRoot().captureRoboImage(filePath = "src/test/snapshots/$name.png", roborazziOptions = options)

  // Dialog / bottom sheet live in their own window, so capture the whole screen instead of the root node.
  private fun screenShot(name: String) {
    rule.waitForIdle()
    captureScreenRoboImage(filePath = "src/test/snapshots/$name.png", roborazziOptions = options)
  }

  @Test
  fun d1_topBar() {
    show {
      val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
      Box(Modifier.background(AppSurface)) {
        InLoveTopBar(
          title = "Gợi Ý Quà",
          avatarUrl = "",
          latest = NotificationItem("💍", "Kỷ niệm 1000 ngày yêu nhau", "Ngày mai", true),
          isEnglish = false,
          scrollBehavior = sb,
          hasUnread = true,
          includeStatusBarPadding = false,
        )
      }
    }
    shot("d1_top_bar")
  }

  @Test
  fun d2_bottomNav() {
    show { Box(Modifier.background(AppSurface)) { InLoveBottomNav(selectedTab = 1, onTabSelected = {}) } }
    shot("d2_bottom_nav")
  }

  @Test
  fun d3_gradientButton() {
    show {
      Box(Modifier.background(AppSurface).padding(24.dp)) {
        Column {
          PrimaryGradientButton(text = "Lưu kỷ niệm", onClick = {}, icon = Icons.Filled.Favorite)
          Button(onClick = {}, modifier = Modifier.padding(top = 16.dp)) { Text("Button M3") }
        }
      }
    }
    shot("d3_gradient_button")
  }

  @Test
  fun d4_dialogWithTextField() {
    show {
      AlertDialog(
        onDismissRequest = {},
        // platform-default width never reaches idle under Robolectric; same property the app's own date dialog uses
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Đặt tên kỷ niệm") },
        text = { OutlinedTextField(value = "Ngày đầu gặp nhau", onValueChange = {}, label = { Text("Tên") }) },
        confirmButton = { TextButton(onClick = {}) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = {}) { Text("Hủy") } },
      )
    }
    rule.waitForIdle()
    rule.onNode(isDialog()).captureRoboImage(filePath = "src/test/snapshots/d4_dialog_textfield.png", roborazziOptions = options)
  }

  @Test
  fun d5_inputField() {
    show {
      Box(Modifier.background(AppSurface).padding(24.dp)) {
        Column {
          InLoveDatePickerField(
            value = "14/02/2024",
            onValueChange = {},
            label = "Ngày yêu nhau",
            helperText = "Định dạng dd/MM/yyyy",
          )
          OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Ghi chú") },
            placeholder = { Text("Nhập ghi chú") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          )
        }
      }
    }
    shot("d5_input_field")
  }

  @Test
  fun d6_card() {
    show {
      Box(Modifier.background(AppSurface).padding(24.dp)) {
        Column {
          Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
              Text("Kỷ niệm 1000 ngày", style = MaterialTheme.typography.titleMedium)
              Text("Đã bên nhau từ 14/02/2024", style = MaterialTheme.typography.bodyMedium)
            }
          }
          Card(modifier = Modifier.padding(top = 16.dp), elevation = CardDefaults.elevatedCardElevation()) {
            Column(Modifier.padding(16.dp)) { Text("Thẻ mặc định", style = MaterialTheme.typography.bodyLarge) }
          }
        }
      }
    }
    shot("d6_card")
  }

  @Test
  fun d7_datePickerDialog() {
    show {
      InLoveDatePickerDialog(
        title = "Ngày yêu nhau",
        initialDateMillis = 1707868800000L, // 2024-02-14 UTC; fixed so both runs render the same month
        quickPresets = listOf("Hôm qua" to 1707782400000L, "Tuần trước" to 1707264000000L),
        onDateSelected = { _, _ -> },
        onDismiss = {},
      )
    }
    screenShot("d7_date_picker_dialog")
  }

  @Test
  fun d8_modalBottomSheet() {
    show {
      Scaffold { padding ->
        Box(Modifier.padding(padding))
        ModalBottomSheet(
          onDismissRequest = {},
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
          Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Chọn hành động", style = MaterialTheme.typography.titleLarge)
            Text("Thêm kỷ niệm mới hoặc đặt lời nhắc.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {}, modifier = Modifier.padding(top = 16.dp)) { Text("Thêm kỷ niệm") }
          }
        }
      }
    }
    screenShot("d8_modal_bottom_sheet")
  }
}
