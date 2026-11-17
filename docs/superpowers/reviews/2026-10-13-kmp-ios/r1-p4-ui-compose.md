[BLOCKER] U-00a  
Problem: The specified spike gate can report success for failed or incomplete evidence.  
Evidence: `p4-ui-compose.md:354` rejects missing `RESULT`, violated metrics without a fallback, and `UNVERIFIED`, but never explicitly rejects `RESULT: FAIL` or requires named metrics; therefore `RESULT: FAIL` with no metrics satisfies its stated checks. U-120, U-122, and U-123 rely on this gate at lines 557–560.  
Fix: Reject FAIL and unknown results, require each caller’s exact metric names and fixed thresholds, and accept only explicitly supported fallbacks; add negative tests for FAIL-only, PASS-without-metrics, missing metrics, and altered thresholds.

[BLOCKER] U-13  
Problem: The proposed import-copying algorithm drops imports required by delegated Compose state and breaks the Android split tasks.  
Evidence: `p4-ui-compose.md:373` copies an import only when its final name appears as a word in the moved declaration. `app/src/main/java/com/example/ui/components/MilestoneBadgeTracker.kt:90` and `:93` import `getValue`/`setValue`, while the declaration moved by U-21 uses `by` at `:1120` and `:1131`, without spelling those operator names. Compose’s [state documentation](https://developer.android.com/develop/ui/compose/state) confirms those imports are required.  
Fix: Preserve all source imports during mechanical splitting, or explicitly handle implicit operators and aliases; add a fixture that compiles a moved composable containing delegated state.

[BLOCKER] U-74  
Problem: The wave baseline is not a valid input to the pure-move verifier, so the planned migration gates cannot succeed.  
Evidence: `p4-ui-compose.md:494` writes `git ls-files -s` path/blob listings into `.git/ui-wave-N.base`; U-80 passes the complete listing as `<base-ref>` at `:519`. `p1-foundation-build.md:562` specifies `git read-tree <base-ref>`, which requires a tree/reference rather than index-entry text. U-66 and U-80 also combine rename verification with new files or other edits.  
Fix: Capture the complete prepared worktree as a tree object using a temporary index, save only its tree ID, and run rename verification immediately after the move, before unrelated additions or edits.

[BLOCKER] U-66d  
Problem: Deleting `LocalizedStrings` leaves retained tests referencing removed APIs and breaks “Android always green.”  
Evidence: `p4-ui-compose.md:469` creates `AppStringsLoaderTest` against `LocalizedStrings.fromContext`; `:470` creates `AppStringsBundleSelectionTest` using `LocalizedStrings.get`. U-66d deletes that object at `:471` but owns neither test. Additionally, the final DoD at `:579` still runs `strings_to_cmp.py` and `StringSourceReconcileTest`, which U-66c/U-66d delete.  
Fix: Add both retained tests to U-66d’s ownership and migrate them to the frozen Android golden/Compose Resources oracle; replace the final DoD with surviving checks and correct the stale test names.

[MAJOR] U-27b  
Problem: The date-picker port targets an obsolete implementation and leaves substantial JVM-only calendar logic plus an unsplit giant file.  
Evidence: `p4-ui-compose.md:213` describes a 506-line Material DatePicker wrapper, and `:395` replaces only listed `DatePickerUtils` functions. The current `app/src/main/java/com/example/ui/components/Material3DatePicker.kt` has 1,042 lines: `DatePickerPresets` starts at `:202`, `computeMonthMatrix` returns `List<List<Calendar>>` at `:396`, and the custom dialog retains Calendar state at `:467`. No task splits these sections.  
Fix: Refresh this inventory, split presets/month-grid/dialog/field before migration, port every Calendar operation, and add Android-derived preset/grid vectors plus vi/en interaction checks.

[MAJOR] U-32  
Problem: The current photo-reordering component is absent from the API-replacement ownership and verification plan.  
Evidence: `app/src/main/java/com/example/ui/components/MemoryPhotoReorderStrip.kt:71` imports Coil 2, `:230` uses drag gestures, and `:468` starts an infinite shimmer; `MemoriesGridScreen.kt:797` renders it. U-32’s exhaustive file list at `p4-ui-compose.md:400` omits it, as does U-69 at `:498`, although both global guards inspect its APIs. The “no pointerInput” assertion at `:24` is consequently false.  
Fix: Add this file to Coil, Reduce Motion, gesture/accessibility, and migration ownership; add explicit FP/MQ coverage for reorder, removal, add-more, cancellation, and VoiceOver alternatives.

[MAJOR] U-102a  
Problem: The tasks preserve live input transformations without implementing the promised commit-only filtering, leaving the Telex/VNI failure path open.  
Evidence: `p4-ui-compose.md:307` promises to move seven filters to commit time, but U-79a/U-79b/U-79c explicitly preserve them at `:504`–`:506`; U-102a at `:551` defers only NFC normalization. A current example is `app/src/main/java/com/example/ui/screens/AuthScreen.kt:1165`, which trims every `onValueChange`.  
Fix: Define how marked text bypasses application transformations until commit, implement that contract in the native wrapper/callers, and test trim, uppercase, truncation, cursor selection, and password visibility during composition; run MQ-301 on the production fields.

[MAJOR] U-104  
Problem: The PIN lifecycle plan enables a privacy cover but never connects the policy that relocks the app after backgrounding.  
Evidence: U-104 at `p4-ui-compose.md:508` only calls `PrivacyCoverController.bind`; `p5-native-features.md:672` defines that controller as cover/resync behavior. `p3-backend-auth-media.md:437` implements only the pure `AppLockPolicy`, and `p4-ui-compose.md:640` explicitly acknowledges that its UIScene caller has no owner. U-123 nevertheless requires MQ-403 relocking.  
Fix: Add a mac-owned lifecycle task that records background time, invokes the policy on foreground, and calls the auth lock operation when PIN is enabled; test immediate relock, disabled PIN, clock rewind, and notification deep links.

[MAJOR] U-101b  
Problem: The planned video player has no route to refresh an expired signed URL or stop playback when the app backgrounds.  
Evidence: `p4-ui-compose.md:548` handles item failure through an internal callback and releases on removal, but exposes only `VideoPlayer(url, modifier)`. `app/src/main/java/com/example/ui/components/MemoryMediaModel.kt:18` resolves once per unchanged memory/source key. `p8-feature-parity-matrix.md:219` requires refreshing after 15 minutes and stopping in the background.  
Fix: Own the viewer/model integration, expose a refreshable media source or retry callback, reconnect failures to the resolver, and pause on lifecycle stop; add expired-URL retry and background-playback tests before MQ-306.

[MAJOR] U-08  
Problem: The Android drift spike does not gate the dependency choice or any production UI migration task.  
Evidence: `p4-ui-compose.md:250` requires U-08 before migration and specifies a CMP 1.9 fallback, but U-63 at `:465` depends only on F-24, U-32, B-30, and U-04. Checking the task dependency columns found no consumer of U-08; it is not an ancestor of U-80 or U-120.  
Fix: Add U-08 to the production dependency-switch gate, define one recorded version decision shared with P1, and make the switch task implement and verify the selected fallback before subsequent tasks run.

[MAJOR] U-67  
Problem: Several Windows tasks own Apple source without satisfying the required macOS verification boundary.  
Evidence: `p4-ui-compose.md:487`–`:490` assign `AppLocale.ios.kt`, `ReduceMotion.ios.kt`, `VideoPlayer.ios.kt`, and `ClipEntryFactory.ios.kt` to host=win; U-78 at `:503` also owns `InLoveTextField.ios.kt` on Windows. Their commands compile only Android/common metadata, while the owner rule requires tasks touching `iosMain` to say host=mac.  
Fix: Split each common/Android seam from its mac-owned actual, including minimal stubs where needed, and sequence Apple consumers after an actual iOS compile/link check.

[MAJOR] U-64  
Problem: The converter’s required output count contradicts its instruction to omit `app_name`.  
Evidence: Both `app/src/main/res/values/strings.xml:3` and `app/src/main/res/values-vi/strings.xml:3` contain `app_name`; independent counts find exactly 232 `<string>` entries in each. `p4-ui-compose.md:467` says to exclude that key but requires `test_keyCount` to find 232 output strings plus one plural. Correct conversion produces 231 strings plus one plural.  
Fix: Require the output key set to equal the input set minus `app_name`, yielding 231 strings at this stage, and update subsequent key-count assertions accordingly.

[MAJOR] U-15  
Problem: The initial golden harness relies on a nonexistent theme symbol and a file-private component it cannot call.  
Evidence: `p4-ui-compose.md:375` instructs `GoldenHost` to wrap `InLoveTheme` and directly capture `NotificationStrip`, while owning only test files and PNGs. The repo defines `MyApplicationTheme` at `app/src/main/java/com/example/ui/theme/Theme.kt:79`; `NotificationStrip` is private at `app/src/main/java/com/example/ui/components/CommonComponents.kt:190`.  
Fix: Use `MyApplicationTheme` and capture the strip through its existing public parent, or add a narrowly scoped visibility change with explicit source ownership and an Android build check.

[MAJOR] GENERAL  
Problem: The lifecycle design promises suspended collectors while deliberately leaving collectors active in a retained composition.  
Evidence: `p4-ui-compose.md:163` claims all UI flows use lifecycle-aware collection, but `:620` explicitly skips that migration; current `MainActivity.kt:209` uses `collectAsState`. The documented [CMP lifecycle mapping](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html) sends backgrounding to `ON_STOP`, not destruction, so composition disposal does not provide the promised cancellation.  
Fix: Add lifecycle-aware collection for the root/controllers and visibility gating for continuous animations, with a test that backgrounding stops upstream collection and foregrounding resumes exactly once.

[MAJOR] GENERAL  
Problem: Removing iOS camera capture contradicts the requested full parity with an existing Android feature.  
Evidence: `app/src/main/java/com/example/ui/screens/EditMyProfileDialog.kt:204` implements `TakePicture`, and `:381` exposes it through `btn_photo_camera`. `p4-ui-compose.md:481` and U-101c at `:550` intentionally return no camera launcher; `DECISIONS.md:147` justifies that omission solely by reduced implementation/QA work, without an owner waiver of full parity.  
Fix: Restore camera capture to the required scope through a mac-owned native implementation, localized usage text, denial/cancellation handling, and real-device verification; reconcile DEC-130 and FP-907.

## Missing steps

- Assign startup integration for `OrphanMediaSweeper.sweep`, including protected references and in-progress uploads, a failure-detecting integration test, and MQ-202/MQ-206 verification; `p4-ui-compose.md:640` still leaves this ownerless.
- Replace U-65’s incomplete `ui_decls.py check <file>` command with the specified snapshot/check argument pair, and align every DoD test name with the actual task that creates it.
- Reconcile U-100b’s `InLoveViewModel(deps)` construction with U-60’s constructor accepting `(CoroutineScope) -> InLoveAppDependencies`, and verify the scope belongs to the ViewModel.
- Keep the file-size and UI-boundary guards active over both `app` and `shared` after migration, remove the unbounded ViewModel exemption, and list the exact remaining split tasks before U-62.
- Add production-flow performance evidence for the actual memory grid, photo reorder, tab transitions, and dialog opening/closing at 60/120 Hz, including MQ-606; the isolated avatar spike and idle-Home measurement do not cover those paths.

VERDICT: FAIL

