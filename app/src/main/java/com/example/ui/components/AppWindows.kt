package com.example.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AppDensity
import com.example.ui.theme.MinTouchBox

/**
 * A Dialog/AlertDialog/DropdownMenu opens its own window, which re-provides LocalDensity (real,
 * not AppDensity), LocalContext and LocalConfiguration (no in-app locale) from its own view.
 * This re-applies them. Every Dialog, AlertDialog and DropdownMenu must go through the wrappers below.
 */
@Composable
internal fun AppWindowLocals(
  context: Context,
  configuration: Configuration,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalContext provides context,
    LocalConfiguration provides configuration,
  ) {
    AppDensity {
      CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides MinTouchBox) {
        content()
      }
    }
  }
}

@Composable
internal fun AppDialog(
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  content: @Composable () -> Unit,
) {
  val ctx = LocalContext.current
  val cfg = LocalConfiguration.current
  Dialog(onDismissRequest, properties) { AppWindowLocals(ctx, cfg, content) }
}

/**
 * [scrollableText] is required: true wraps the text slot in a verticalScroll Column, false means
 * the slot scrolls itself (nesting two verticalScroll throws an infinite-height exception).
 */
@Composable
internal fun AppAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  scrollableText: Boolean,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
  shape: Shape = AlertDialogDefaults.shape,
  containerColor: Color = AlertDialogDefaults.containerColor,
  iconContentColor: Color = AlertDialogDefaults.iconContentColor,
  titleContentColor: Color = AlertDialogDefaults.titleContentColor,
  textContentColor: Color = AlertDialogDefaults.textContentColor,
  tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
  properties: DialogProperties = DialogProperties(),
) {
  val ctx = LocalContext.current
  val cfg = LocalConfiguration.current
  // ponytail: M3 owns this window, so its frame padding and 280dp minimum width stay at window
  // density; if it looks off, rebuild on BasicAlertDialog.
  AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { AppWindowLocals(ctx, cfg, confirmButton) },
    modifier = modifier,
    dismissButton = dismissButton?.let { slot ->
      @Composable { AppWindowLocals(ctx, cfg, slot) }
    },
    icon = icon?.let { slot ->
      @Composable { AppWindowLocals(ctx, cfg, slot) }
    },
    title = title?.let { slot ->
      @Composable { AppWindowLocals(ctx, cfg, slot) }
    },
    text = text?.let { slot ->
      @Composable {
        AppWindowLocals(ctx, cfg) {
          if (scrollableText) {
            Column(Modifier.verticalScroll(rememberScrollState())) { slot() }
          } else {
            slot()
          }
        }
      }
    },
    shape = shape,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    tonalElevation = tonalElevation,
    properties = properties,
  )
}

@Composable
internal fun AppDropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val ctx = LocalContext.current
  val cfg = LocalConfiguration.current
  DropdownMenu(expanded, onDismissRequest, modifier) {
    val scope = this
    AppWindowLocals(ctx, cfg) { scope.content() }
  }
}
