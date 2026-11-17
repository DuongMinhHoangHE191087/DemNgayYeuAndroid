# P4 U-08 spike: Android visual drift, androidx Compose BOM 2024.09.00 vs Compose Multiplatform 1.12.1

Project: `spikes/ui-drift` (standalone Gradle build, own `settings.gradle.kts`, not included by the root build). It holds verbatim copies of the app's `Theme.kt`, `Color.kt`, `Type.kt`, `Motion.kt` (needed by `PressScale.kt`), `PressScale.kt`, `CommonComponents.kt`, `Material3DatePicker.kt` (original `com.example.ui.*` packages kept). The only edit is an appended `internal data class NotificationItem` at the end of `CommonComponents.kt` (the real one lives in a file not copied). 8 Roborazzi tests in `DriftGoldenTest.kt`, Robolectric 4.16.1 `@GraphicsMode(NATIVE)`, Pixel8 qualifiers, sdk 34, Roborazzi 1.59.0, AGP 9.2.1, Kotlin 2.2.10, Gradle 9.6.0, JDK 21.

Comparison is strict: `SimpleImageComparator(maxDistance = 0f)` (any channel difference counts as a changed pixel) and `ThresholdValidator(0.005f)` (fail above 0.5% changed pixels). Goldens are recorded with the baseline and stored in `spikes/ui-drift/app/src/test/snapshots/` (8 PNGs); the cmp run re-renders the same tests and compares.

## Resolved versions (from `gradlew app:dependencies --configuration debugUnitTestRuntimeClasspath`)

| Library | baseline (default) | `-PcomposeVariant=cmp` |
|---|---|---|
| androidx.compose.material3:material3 | 1.3.0 | 1.5.0-alpha22 |
| androidx.compose.ui:ui | 1.7.0 | 1.12.1 |
| androidx.compose.runtime:runtime | 1.7.0 | 1.12.1 |
| androidx.compose.foundation:foundation | 1.7.0 | 1.12.1 |
| androidx.compose.animation:animation | 1.7.0 | 1.12.1 |
| androidx.compose.material:material-icons-extended | 1.7.0 | 1.7.6 (androidx, pulled in transitively) plus org.jetbrains.compose.material 1.7.3 (declared) |
| androidx.compose.ui:ui-test-junit4 | 1.7.0 | 1.12.1 |
| org.jetbrains.compose.{runtime,ui,foundation,animation,ui-test-junit4} | absent | 1.12.1 |
| org.jetbrains.compose.material3:material3 | absent | 1.12.0-alpha03 |
| io.coil-kt:coil-compose | 2.7.0 | 2.7.0 |

Consequence (confirms A25): on Android the CMP `material3:1.12.0-alpha03` coordinate is a redirect to androidx `material3:1.5.0-alpha22`, so the "cmp" render is androidx Material3 1.5.0-alpha22 on Compose UI 1.12.1, versus Material3 1.3.0 on UI 1.7.0 today. The drift measured below is that jump, not a different drawing engine.

Build notes for the cmp variant:
- Compose 1.12.1 AARs declare `minCompileSdk 37`, only android-36 and android-36.1 are installed. `app/build.gradle.kts` disables the `*AarMetadata` check tasks when `-PcomposeVariant=cmp` (compile stays on android-36.1). The tests ran fine under Robolectric sdk 34. A real migration would need compileSdk 37 in the app.
- No duplicate-class conflict appeared: the JetBrains and androidx coordinates resolve to one version each (see table).
- `createComposeRule` and `rememberModalBottomSheetState` compile with deprecation warnings only under cmp (the Material3 1.5 alpha deprecates them); not changed.

## Screens compared (8) and measured difference

Percentages are Roborazzi `diff_percentage` x 100 (share of pixels whose colour differs at all), measured with the validator threshold temporarily set to 0 to get a number for every screen (restored to 0.5% afterwards; the third run, with 0.5%, is the acceptance run).

| # | Test | Golden | Changed pixels |
|---|---|---|---|
| 1 | d1_topBar (`InLoveTopBar`) | d1_top_bar.png | 0 (identical) |
| 2 | d2_bottomNav (`InLoveBottomNav`) | d2_bottom_nav.png | 0.2865% |
| 3 | d3_gradientButton (`PrimaryGradientButton` + M3 `Button`) | d3_gradient_button.png | 0 (identical) |
| 4 | d4_dialogWithTextField (M3 `AlertDialog` + `OutlinedTextField`) | d4_dialog_textfield.png | 0.4032% |
| 5 | d5_inputField (`InLoveDatePickerField` + `OutlinedTextField`) | d5_input_field.png | 0 (identical) |
| 6 | d6_card (two `Card`s) | d6_card.png | 0.1454% |
| 7 | d7_datePickerDialog (`InLoveDatePickerDialog`, custom calendar) | d7_date_picker_dialog.png | 3.3758% |
| 8 | d8_modalBottomSheet (`ModalBottomSheet`) | d8_modal_bottom_sheet.png | 0 (identical) |

Baseline re-verified against its own goldens: all 8 pass (the renders are deterministic). Acceptance run of `verifyRoborazziDebug -PcomposeVariant=cmp` at the 0.5% threshold: 7 pass, `d7_datePickerDialog` fails (3.3758% > 0.5%). Max = 3.3758%.

## Every visual difference seen (from the Roborazzi `*_compare.png` images)

1. Elevation shadow of rounded surfaces is drawn differently: d7 (dialog surface, wide red bands along the left and right edges and at the corners, this is nearly all of the 3.38%), d6 (arcs at the rounded ends of the elevated card), d2 (arcs at both ends of the pill-shaped bottom nav). Shape, size and position of the surfaces are unchanged.
2. d7 `Hủy` OutlinedButton: border colour changes from a dark magenta/outline tone (baseline) to a pale pink (cmp). The label and the "Xác nhận" gradient button are pixel-identical. Cause not isolated (either the Material3 1.5 `OutlinedButton` default border/`outline` handling or the way `InLoveDatePickerDialog` sets it; the source was not changed to find out).
3. d4 `OutlinedTextField`: baseline renders unfocused (thin outline, label in neutral colour); cmp renders it focused (thick pink border, pink label). So under Material3 1.5.0-alpha22 the text field in a dialog takes focus by itself, or the focused style differs. Text position and size are the same. This is a behaviour change as well as a colour one (a focused field would raise the IME on a device); UNVERIFIED on device.
4. d7 tiny single-pixel differences on the calendar icon, the dropdown arrow, the prev/next chevrons and the heart icon (anti-aliasing).
5. Not different: d1 top bar, d3 buttons, d5 fields, d8 bottom sheet are pixel-identical; text metrics, layout and colours of all 8 screens are the same apart from items 1 to 4.

## Verdict

The 0.5% limit is exceeded by one screen (d7, 3.3758%). The requirement table allows `FALLBACK:cmp-1.9` for `max_pixel_diff_pct` only. `screens_compared` = 8 meets its own threshold. The fallback is used as the plan's R1 decision defines it (CMP 1.9.0 material3, last stable) but the 1.9 build itself was not measured here.

## Not verified
- iOS rendering: this spike is Android only (androidx M3 vs CMP M3 on the Android target); nothing about Skia/iOS output is claimed.
- `cmp-1.9` (material3 1.9.0): never built or compared; whether it would be under 0.5% on d7 is unknown.
- Real device or emulator: only Robolectric NATIVE graphics (sdk 34, Pixel8 qualifiers). Shadow rasterisation on a real GPU can differ from the Robolectric one, so the d6/d2/d7 shadow findings and the d4 focus finding are UNVERIFIED on hardware.
- Cause of difference 2 and 3 not isolated.
- Coil avatar: the manifest has no INTERNET permission, so `AsyncImage` in the top bar never loads; real avatar rendering is not compared.
- "Today" highlight in the date picker depends on the run date: d7 uses a fixed 2024-02-14 selected date, but the day-of-today marker for 2026-10-14 is outside the shown month, so it does not appear in the image.
- Only one `maxDistance = 0` setting was measured; a perceptual tolerance would lower all numbers and was not tried.
- compileSdk 37 not tested (platform not installed; AAR metadata check disabled for cmp).

RESULT: FALLBACK:cmp-1.9
METRIC max_pixel_diff_pct 3.3758 <= 0.5
METRIC screens_compared 8 >= 8
