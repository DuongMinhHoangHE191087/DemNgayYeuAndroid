# Design Spec: Button & Spacing Unification (Primary vs. Secondary)

## Overview

InLove's 19 screen/dialog files contain 126 `Button`/`OutlinedButton`/`TextButton`/
`ElevatedButton`/`FilledTonalButton` call sites, almost all styled inline with
ad-hoc `ButtonDefaults.buttonColors(...)`, `shape = RoundedCornerShape(...)`,
and `contentPadding = ...` instead of a shared component. This spec unifies
them into two shared, reusable composables so every "do the main thing" button
looks the same everywhere, every "cancel/secondary/destructive" button looks
the same everywhere, and visual drift stops being possible by construction.

This directly serves the user's request to "phân chia primary button và các
phần còn lại" (separate the primary button from everything else) and unify
button box sizes, spacing, and font app-wide.

## Current State (evidence)

- **A primary gradient button already exists and is barely used.**
  `CommonComponents.kt:422-465` defines `PrimaryGradientButton` — a 50dp
  stadium-shaped button with the `RoseGradientStart/Mid/End` gradient,
  8dp shadow, 20dp/13dp padding, white Bold 14sp text, matching
  `DESIGN.md`'s "Buttons: 14dp-50dp" corner-radius range exactly. It has
  **2 callers in the whole repo**, one of which is a throwaway spike test
  (`spikes/ui-drift/.../DriftGoldenTest.kt`). It is production-ready and
  effectively unused.
- **The same brand color is duplicated as a literal hex in ~15+ places**
  instead of referencing the `Primary` token (`Color.kt`), e.g.
  `EditMyProfileDialog.kt:377,393,569`, `AccountSecurityDialogs.kt:234,347,452`,
  `PairingScreen.kt:326,596,843,1007`, `AuthScreen.kt:1209`,
  `SettingsScreen.kt:485` all write `Color(0xFFE91E63)` by hand. If `Primary`
  ever changes, these sites silently fall out of sync.
- **Destructive actions use three different reds** with no shared definition:
  `Color(0xFFD32F2F)` (`EditMyProfileDialog.kt:410`, `SettingsScreen.kt:693`,
  `AnniversaryMemoriesWidget.kt:1308`, `VerifyInviteDialog.kt:366`,
  `PairingScreen.kt:625`), `Color(0xFFC62828)` (`SettingsScreen.kt:620,752`,
  `PairingScreen.kt:630`), and `Color(0xFFFFEBEE)` as a light destructive fill
  (`EditMyProfileDialog.kt:410`). None reference Material 3's built-in
  `colorScheme.error` (already available for free: `Theme.kt`'s
  `lightColorScheme(...)` call never overrides `error`/`onError`/
  `errorContainer`, so M3's baseline `#B3261E` sits there unused).
- **`PaywallScreen.kt:525-549` reimplements a gradient CTA from scratch**:
  a transparent `Button` wrapping a manually gradient-filled `Box`, using
  the same two gradient colors as `PrimaryGradientButton`
  (`0xFFFF2D75`, `0xFFE91E63`) but a 16dp rounded-rect instead of the 50dp
  stadium shape, and its own padding/height logic. This is the exact
  "reinventing a few files over" pattern the existing component should
  replace.

## Design

### 1. `PrimaryGradientButton` — adopt as-is, zero code changes

No changes to its implementation or public signature. It becomes the single
primary CTA across all 19 files. Callers needing full width compose
`modifier = Modifier.fillMaxWidth()` (and `.heightIn(min = MinTouchBox)` where
the current call site already enforces that minimum, e.g. `PaywallScreen`'s
CTA) — the component's own 13dp vertical padding already clears the 48dp
touch target for its existing content-hugging callers, so this is only
needed where a caller currently sets an explicit height.

### 2. `SecondaryButton` — one new composable, added next to it in `CommonComponents.kt`

```kotlin
@Composable
fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  isDestructive: Boolean = false,
  filled: Boolean = false,
  testTag: String = "secondary_btn"
)
```

Same shape, padding, icon size/spacing, and font weight/size as
`PrimaryGradientButton` (50dp stadium, 20dp/13dp padding, 18dp icon, Bold
14sp) so the two read as one matched family — this is the actual "unify
button box sizing, spacing, font" fix. Three visual states, chosen by the
two booleans:

| `isDestructive` | `filled` | Look | Colors |
|---|---|---|---|
| `false` | — | Outline, neutral | `CardBorder` 1.2dp border (matches card-border convention in `DESIGN.md`), white fill, `TextDarkPlum` text |
| `true` | `false` | Outline, red | `colorScheme.error` border + text, white fill |
| `true` | `true` | Solid, red | `colorScheme.error` fill, `colorScheme.onError` text |

`filled = true` with `isDestructive = false` is not a meaningful combination
(a filled non-destructive secondary button would just be a primary button);
the implementation treats `filled` as a no-op unless `isDestructive` is also
`true`, so passing it alone is harmless rather than an error.

### 3. Plain text links — standardize via guidance, not a third composable

Links like "Quên mật khẩu?" stay `TextButton`, but all call sites move to
`ButtonDefaults.textButtonColors(contentColor = Primary)` with Medium 13sp —
matching `DESIGN.md`'s "Card Subtitles & Hints" weight so links read as
links, not as buttons. No new component; this keeps the scope at exactly two
shared button composables, as approved.

### Classification heuristic (for each of the 126 call sites)

1. The one action that completes or advances the screen/dialog's main
   purpose → `PrimaryGradientButton`.
2. Cancel / dismiss / back / a non-destructive alternative choice →
   `SecondaryButton()` (outline, neutral).
3. A destructive action (delete, unpair, sign out, revoke) offered
   **alongside** a safer alternative of equal or greater visual weight →
   `SecondaryButton(isDestructive = true)` (outline, red).
4. A destructive action that **is** the dialog's main/only confirm action
   (e.g., a dedicated "confirm delete" dialog) → `SecondaryButton(isDestructive
   = true, filled = true)` (solid red) — this reads as a serious primary
   action without borrowing the romantic rose-gradient brand, which should
   never signal "delete this."
5. A bare inline text link with no fill or border → `TextButton` per the
   guidance above.

`PaywallScreen.kt`'s CTA (rule 1) is a visible, deliberate change: its shape
moves from a 16dp rounded-rect to the 50dp stadium shared by every other
primary CTA in the app. This is the point of the unification, but because
it's the paywall it gets an explicit before/after screenshot in the final
report rather than being folded silently into a batch.

## Rollout Plan

Three batches, grouped by blast radius, each independently buildable and
testable:

- **Batch 1 — low risk (20 sites):** `AppPinLockScreen.kt`, `OnboardingScreen.kt`
  (styling only — content/copy is owned by the onboarding-audit workstream
  running in parallel; do not touch any text in this file, only
  `Button`/`OutlinedButton` call sites), `VerifyInviteDialog.kt`,
  `Material3DatePicker.kt`, `MilestoneBadgeTracker.kt`,
  `LoveDaysCalculatorWidget.kt`.
- **Batch 2 — feature screens (53 sites):** `AuthScreen.kt`, `PairingScreen.kt`,
  `GiftScreen.kt`, `PaywallScreen.kt`, `AnniversaryMemoriesWidget.kt`,
  `LoveHomeScreen.kt`, `MemoriesGridScreen.kt`.
- **Batch 3 — shared dialogs, highest call-site density (53 sites):**
  `Dialogs.kt` (28 sites alone), `AppModals.kt`, `AccountSecurityDialogs.kt`,
  `EditMyProfileDialog.kt`, `SettingsScreen.kt`, plus re-verifying
  `CommonComponents.kt`'s own existing (non-spike) `PrimaryGradientButton`
  caller for regressions.

Each batch: swap call sites per the classification heuristic, run that
batch's existing unit/Robolectric/Roborazzi tests, update golden images only
where the diff is an intended shape/color change. Full suite + lint run once
after all three batches land.

## Error Handling

No new error paths — these are pure UI composables with no I/O. `onClick`
lambdas are passed through unchanged from each call site's existing logic.

## Testing

- Roborazzi screenshot tests for every touched screen, comparing against
  updated golden images (intended diffs only: shape/color changes called out
  above).
- Any existing test asserting a specific hardcoded color/shape at a touched
  call site is updated to match the new shared styling.
- `PaywallScreen`'s CTA gets an explicit before/after screenshot in the final
  report (see Design, rule 1 note).
- Full suite (`./gradlew test`) and `:app:lintDebug` run once after all three
  batches, per this repo's standing rule that any res/Gradle-adjacent change
  gets a lint pass before being called done.

## Global Constraints

- No new color tokens. Both composables reuse existing `Primary`, `CardBorder`,
  `TextDarkPlum`, and M3's built-in `error`/`onError` roles.
- No change to `PrimaryGradientButton`'s existing public signature.
- `spikes/ui-drift/app/src/main/java/spike/CommonComponents.kt` and its
  `Theme.kt` twin are throwaway KMP-spike scaffolding — out of scope,
  never edited by this rollout.
- Exactly two shared button composables (`PrimaryGradientButton`,
  `SecondaryButton`). No third composable for text links.
- No commit, push, or PR — work stays uncommitted on `main` per standing
  project convention until the user asks otherwise.
