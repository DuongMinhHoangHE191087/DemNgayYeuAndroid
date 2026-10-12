# Couple dialog redesign (EditCoupleDialog)

## Goal

Change `EditCoupleDialog` (`app/src/main/java/com/example/ui/screens/Dialogs.kt:894`) so the offline tab shows only the partner card and the anniversary card. The popup is narrower (about 15-20% less than the platform default) with even side margins. Fonts and padding shrink a little, and the "Đóng" button stays visible below a scrolling body at 48dp. The partner avatar gets a clear pink camera badge, a "Chạm để tải ảnh" caption, and a tap that opens the gallery. Saving must never overwrite the user's own (partner1) data. Theme, icons, chips and all existing testTags stay. Edits go on top of the uncommitted working tree; nothing is reverted.

## Assumptions

- VERIFIED: the dialog uses a plain `Dialog(onDismissRequest = onDismiss)` with no DialogProperties (Dialogs.kt:991). Dialogs.kt has no `usePlatformDefaultWidth` at all, so the dialog gets the platform default width.
- VERIFIED: `DialogProperties(usePlatformDefaultWidth = false)` is already used in this codebase: MilestoneBadgeTracker.kt:669, AuthScreen.kt:1117, AccountSecurityDialogs.kt:367.
- VERIFIED: "Đóng" (Dialogs.kt:1621-1629, 44dp) is the last child inside the `verticalScroll` Column. That is why it can scroll out of view.
- VERIFIED: no test references `input_boy_name`, "THÔNG TIN CỦA BẠN", `edit_couple_dialog`, `btn_save_couple_offline` or `input_girl_name`. These strings appear only in Dialogs.kt.
- VERIFIED: `saveCoupleProfile` (InLoveViewModel.kt:947) rebuilds partner1 from its arguments (InLoveViewModel.kt:976-985) and falls back to the stored value only for blank strings (962-965). `boyA` has no fallback (984).
- VERIFIED: the ViewModel exposes `boyName`, `boyBirthDate`, `boyAge`, `boyZodiac` and `boyAvatarUrl` as StateFlows (InLoveViewModel.kt:255-267). The current dialog seeds its boy fields from those flows (Dialogs.kt:910-912, 924-926).
- VERIFIED: the Save call passes `boy = currentBoyName`, `boyBirth = currentBoyBirth`, `boyAvatar = currentBoyAvatar`, `boyA = boyAge`, `boyZod = boyZodiac` (Dialogs.kt:1296-1301). The `boyLauncher` is at Dialogs.kt:946.
- VERIFIED: the Robolectric + compose-test pattern already exists in `app/src/test/java/com/example/ui/screens/EditProfileDialogTargetsTest.kt`.
- VERIFIED: `InLoveDatePickerField` has no textStyle parameter (Material3DatePicker.kt:414-425). Its input text uses `LocalTextStyle` and its labels use MaterialTheme typography. A local `MaterialTheme(typography = ...)` wrapper can shrink it without touching the shared component.
- VERIFIED: no other docs/superpowers plan or spec references EditCoupleDialog, so there is no competing plan for this dialog.
- VERIFIED: Gradle runs on this machine (Gradle 9.6.0, AGP 9.4.1). Unit test results were written today.
- UNVERIFIED: whether "Đóng" stays visible with the keyboard open on a short screen. Needs a manual check on an emulator.
- UNVERIFIED: the exact width ratio (0.82f here). Tune visually; if the popup still looks too wide, use 0.80f.

## Save-path decision: Option A (chosen)

- **A (chosen):** the Save button passes the ViewModel's own stored boy values back unchanged: `viewModel.boyName.value`, `viewModel.boyBirthDate.value`, `viewModel.boyAvatarUrl.value`, `viewModel.boyAge.value`, `viewModel.boyZodiac.value`. These are the same flows the old dialog started from (Dialogs.kt:910-912), so a save produces the same result as today when nobody edits the boy card. Reading `.value` inside onClick gets the latest value, so no new collectAsState is needed.
  - It also removes a hidden bug. The old dialog defaulted to "15/10/2004" (Dialogs.kt:925), so saving with no stored birth date wrote a made-up date and age.
  - It touches 1 file.
- **B (rejected):** drop the boy params and rebuild from `existing?.partner1*` inside the ViewModel. That touches InLoveViewModel.kt:947 and 1209, Dialogs.kt:1296 and OnboardingScreen.kt:279 (which passes the boy name). It also changes behavior: Room's partner1 would win over the online-user overlay (InLoveViewModel.kt:612-616) that the UI shows today. More files, more risk, no benefit for this ticket.

## Width and bottom-button approach

- `Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false))`.
- The Card gets `Modifier.fillMaxWidth(0.82f).padding(vertical = 24.dp)` and keeps `testTag("edit_couple_dialog")`.
- Inside the Card is a `Column` with two parts:
  - a scrolling body: `Column(Modifier.weight(1f, fill = false).verticalScroll(...).padding(horizontal = 16.dp, top = 16.dp))`, holding the header, tabs and tab content;
  - a fixed footer: "Đóng" as an `OutlinedButton` with `height(48.dp)`, `RoundedCornerShape(14.dp)`, `BorderStroke(1.dp, Color(0xFFE0E0E0))`. It sits outside the scroll, so it is never clipped.

## Tasks

| id | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|
| T1 (wave 1) | Dialogs.kt | Delete the boy card (Dialogs.kt:1088-1162), plus `currentBoy*` (924-926), the boy-derived local values (935-936), `boyLauncher` (946-950), and the now-unused `boyName`/`boyBirthDate`/`boyAvatarUrl` collects (910-912). Change the Save call (1296-1301) to pass `viewModel.boyName.value`, `.boyBirthDate.value`, `.boyAvatarUrl.value`, `.boyAge.value`, `.boyZodiac.value`. Do not change girl, title or anniversary args. | `.\gradlew.bat :app:compileDebugKotlin` | - | sonnet, effort high |
| T2 (wave 2) | Dialogs.kt | (a) Shell: the width and bottom-button approach above. (b) Fonts: title 20 to 17sp, heart icon 26 to 22dp, tab text 13 to 12sp, section labels 12 to 11sp, days text 15 to 14sp, save text 15 to 14sp, save height 50 to 48dp. Wrap the Card content in `MaterialTheme(typography = t.copy(bodyLarge = t.bodyLarge.copy(fontSize = 14.sp)))` so inputs and floating labels shrink. (c) Partner and anniversary cards: padding 14 to 12dp, spacing 10 to 8dp, corner 20 to 18dp. (d) Pull the partner avatar into `@Composable internal fun PartnerAvatarPicker(avatarUrl: String, onPick: () -> Unit, modifier: Modifier = Modifier)` (details below). Call it with `onPick = { girlLauncher.launch("image/*") }` and add a `@Preview`. Keep every existing testTag. | `.\gradlew.bat :app:compileDebugKotlin` | T1 | haiku, effort max |
| T3 (wave 2) | app/src/test/java/com/example/ui/screens/PartnerAvatarPickerTest.kt (new) | Robolectric test copying the EditProfileDialogTargetsTest setup. The tag `partner_avatar_picker` must be at least 48dp wide and tall. Clicking it calls `onPick`. The text "Chạm để tải ảnh" must exist. | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.ui.screens.PartnerAvatarPickerTest"` | T2 signature (fixed above) | haiku, effort max |
| T4 (wave 3) | none | Full unit suite and debug build. Then a manual emulator check: popup margins, "Đóng" visible with the keyboard open, avatar tap opens the gallery. | `.\gradlew.bat :app:testDebugUnitTest`, then `.\gradlew.bat :app:assembleDebug` | T2, T3 | sonnet, effort high |

Details of `PartnerAvatarPicker` (T2 d):
- A `Column(horizontalAlignment = CenterHorizontally)`.
- Inside it, a 56dp `Box` with `.clickable(onClickLabel = "Đổi ảnh", role = Role.Button) { onPick() }` and `testTag("partner_avatar_picker")`. The 56dp box is the tap zone and is at least 48dp.
  - It holds the `AsyncImage`: 56dp, CircleShape, 2dp `0xFFE91E63` border.
  - At `Alignment.BottomEnd` it holds a 24dp `Surface`: CircleShape, `0xFFE91E63`, `BorderStroke(2.dp, Color.White)`, with a white CameraAlt icon at 4dp padding.
- Below the box, after `Spacer(4.dp)`: `Text("Chạm để tải ảnh", fontSize = 10.sp, color = Color(0xFF9E9E9E))`.

Codex model: gpt-6.1-sol.

## Risks

- If the Card is not bounded in height, `weight(1f, fill = false)` has no effect. The dialog window does bound it; T4 checks this on the emulator.
- The `MaterialTheme` typography wrapper also shrinks default-styled text in the online tab. Acceptable, since most online-tab text sets its own size.
- Dialogs.kt has uncommitted user changes. T1 and T2 make targeted edits (no file rewrite, no `git checkout`) and run in order, never in parallel.
- Option A saves the online-overlaid boy values into Room. The current dialog already does this, so it is not a regression.

## Out of scope

The online tab layout, the `saveCoupleProfile` signature, OnboardingScreen, `InLoveDatePickerField`, and any new Roborazzi screenshot (the pure test is enough; add a screenshot only if a visual regression shows up).
