# Couple dialog redesign: review log

Plan: `docs/superpowers/plans/2026-10-12-couple-dialog-redesign.md`

Round 1 rows are a Claude self-check against the code, not Codex. Codex review was skipped at the user's request. The lens and number refer to the self-check output, in its order.

| round | stage (plan/review) | finding | Claude verdict (accept/reject + reason) | action taken |
|---|---|---|---|---|
| 0 | plan | Codex plan review | skipped: user asked to skip Codex review ("bỏ qua codex review") | none; plan goes to the gate as-is |
| 1 | plan | Self-check (not Codex) / fact-dialog #1: WRONG, plan line 25: "a save produces the same result as today" | accept: Option A is replaced, and the claim does not hold | Option A removed; Save path C chosen; partner2 age and zodiac derived from the birth date at save time via ProfileUtils |
| 1 | plan | Self-check / fact-dialog #2: WRONG, plan line 49 and T3 line 44: a 56dp tap box is 47.6dp physical when scaled | accept | tap zone is the outer Column (avatar plus caption) with clickable and testTag; Đóng and Save use MinTouchBox |
| 1 | plan | Self-check / fact-dialog #3: MISSING, Goal line 5 and T1 line 42: removing the boy card removes the only offline partner1 editor, not stated | accept; becomes decision D3 | D3 added; guest photo rights and offline couple-row sync listed as follow-ups |
| 1 | plan | Self-check / fact-dialog #4: WRONG, Goal line 5 vs Risk line 61: Option A writes overlay values into Room partner1 | accept | Goal reworded; C never writes partner1 when a row exists |
| 1 | plan | Self-check / fact-dialog #5: WRONG, Goal line 5 ("all existing testTags stay") and T2 line 43 | accept | removed tags listed: input_boy_name; boy birth picker default date_picker_field (string still used by girl picker); boy chips have no tags |
| 1 | plan | Self-check / fact-dialog #6: WRONG, line 11: Đóng is 1621–1630, not 1621–1629 | accept | range corrected |
| 1 | plan | Self-check / fact-dialog #7: MISSING, lines 32, 49 and T2 line 43: imports not listed | accept | import list added (DialogProperties, Role, Preview, MyApplicationTheme) |
| 1 | plan | Self-check / fact-dialog #8: MISSING, lines 35–36: footer design and Save placement not stated | accept | Save stays in the scroll body; only Đóng is in the fixed footer, shared by both tabs |
| 1 | plan | Self-check / fact-dialog #9: MISSING, T2 line 43 signature and lines 49–50 | accept | PartnerAvatarPicker signature fixed; Unsplash fallback kept at call site; T3 passes avatarUrl = "" |
| 1 | plan | Self-check / fact-dialog #10: MISSING, line 25 and T1 line 42: girl defaults and "Hôm nay" not stated | accept | kept as pre-existing behaviour and disclosed in Risks |
| 1 | plan | Self-check / fact-dialog #11: UNVERIFIABLE, line 17: typography effect on InLoveDatePickerField | accept | marked UNVERIFIABLE; checked at T4b |
| 1 | plan | Self-check / fact-dialog #12: UNVERIFIABLE, lines 5, 9, 21, 33: width and layout | accept | marked UNVERIFIABLE; width measured at T0 and T4b |
| 1 | plan | Self-check / fact-vm-app #1: WRONG (BLOCKER), Goal line 5 and Option A lines 25–27: Option A writes the signed-in user's overlay into partner1; for a female user that is her own profile in the male slot | accept; partner2 consequence becomes decision D2 | Option A removed; D2 added |
| 1 | plan | Self-check / fact-vm-app #2: WRONG, line 13: blank fallbacks read Room | accept: they read the in-memory flows (VM 962–970) | corrected in Save path |
| 1 | plan | Self-check / fact-vm-app #3: WRONG, line 25: "same result as today" | accept as duplicate of fact-dialog #1 | see fact-dialog #1 |
| 1 | plan | Self-check / fact-vm-app #4: MISSING, line 26 "hidden bug" and line 65 out-of-scope | accept | "hidden bug" framing removed; out-of-scope list rewritten |
| 1 | plan | Self-check / fact-vm-app #5: MISSING, Goal line 5, T2(d) line 43, lines 49–51: badge design | accept | badge spec complete: 24dp Surface, white border, CameraAlt; supersedes TASK_CHECKLIST.md:46 (22dp badge, 48dp tap zone) |
| 1 | plan | Self-check / fact-vm-app #6: WRONG, line 28: Option B cites InLoveViewModel.kt:1209, which is dead code | accept | citation dropped; B rejected because it changes saveCoupleProfile semantics (OnboardingScreen.kt:279–293) |
| 1 | plan | Self-check / fact-vm-app #7: UNVERIFIABLE, line 17: LocalTextStyle claim | accept | marked UNVERIFIED |
| 1 | plan | Self-check / data-safety #1: WRONG, Goal vs Option A: "never overwrite partner1" conflicts with Option A | accept | Goal reworded; C chosen |
| 1 | plan | Self-check / data-safety #2: RISK, Risks line 61: "Option A is not a regression" | accept: Option A writes overlay values into Room partner1 | risk restated; C never writes partner1 when a row exists |
| 1 | plan | Self-check / data-safety #3: RISK, line 26: "removes a hidden bug, made-up date and age" | accept: Option A keeps the stored age, so the claim is wrong | claim dropped |
| 1 | plan | Self-check / data-safety #4: RISK, C was mentioned but not specified | accept | C specified, including the no-row branch (literal partner1 defaults; never _boy*) |
| 1 | plan | Self-check / data-safety #5: RISK, Option A with no Room row | accept as a note | C's no-row branch writes "Bạn" and empty partner1 fields |
| 1 | plan | Self-check / data-safety #6: MISSING, other callers | accept | OnboardingScreen.kt:279–293 (boy name) and dead updateCoupleInfo added to out of scope |
| 1 | plan | Self-check / data-safety #7: RISK, line 25: `.value` read inside onClick | accept as harmless | no change |
| 1 | plan | Self-check / compose #1: WRONG, line 35: padding(horizontal = 16.dp, top = 16.dp) does not compile | accept | padding(start = 16.dp, top = 16.dp, end = 16.dp) |
| 1 | plan | Self-check / compose #2: MISSING, T2(a), T2(d), T3: 56dp tap zone, 48dp Đóng, density | accept | MinTouchBox for Đóng and Save; tap zone is the outer Column; density section added |
| 1 | plan | Self-check / compose #3: UNVERIFIABLE, Assumptions and T2(a): 15–20% narrower than platform default | accept | width rule added; measured at T0 (Robolectric) and T4b (device) |
| 1 | plan | Self-check / compose #4: MISSING, T2(a)/(d) and T3: imports | accept as duplicate of fact-dialog #7 | import list added |
| 1 | plan | Self-check / compose #5: RISK, T2(b): typography wrapper | accept | kept for Branch B only; copy(fontSize = 14.sp, lineHeight = 20.sp); colorScheme and shapes passed too |
| 1 | plan | Self-check / compose #6: RISK, PartnerAvatarPicker details | accept | clickable, testTag and clip on the outer Column |
| 1 | plan | Self-check / compose #7: RISK, T3 test plan | accept | test passes avatarUrl = "" |
| 1 | plan | Self-check / compose #8: UNVERIFIABLE, Risks: weight(1f, fill = false) and Card/Column structure | accept | marked UNVERIFIED; checked at T4b |
| 1 | plan | Self-check / scope-rules #1: RISK, Goal and T1 (delete boy card 1088–1162) | accept; becomes decision D3 | D3 added |
| 1 | plan | Self-check / scope-rules #2: RISK, Goal and T2(d): "keep every testTag" | accept | removed tags listed by name |
| 1 | plan | Self-check / scope-rules #3: WRONG, T3 48dp assertion and 56dp zone | accept as duplicate of compose #2 | see compose #2 |
| 1 | plan | Self-check / scope-rules #4: MISSING, width and bottom-button approach, T2(b)/(c) sizing, Goal "15–20% less" | accept as duplicate of compose #2 and #3 | see compose #2 and #3 |
| 1 | plan | Self-check / scope-rules #5: WRONG, caption colour #9E9E9E at 10sp | accept | caption #6B6B6B at 11sp; #4A1934 is the alternative (non-blocking) |
| 1 | plan | Self-check / scope-rules #6: RISK, Out of scope "no new Roborazzi screenshot" | accept, flagged | Roborazzi is in the build (build.gradle.kts:245–247); decision D5 |
| 1 | plan | Self-check / scope-rules #7: RISK, T2(d) PartnerAvatarPicker, T3, typography wrapper | partly accept | badge, caption and tap-target fix kept; new test file kept (AGENTS.md expects a test); typography wrapper kept only as Branch B fallback |
| 1 | plan | Self-check / scope-rules #8: RISK, AGENTS.md rules (no Room in UI, StateFlow from ViewModel) | accept; no change needed | the picker is UI only; state and callbacks come from the dialog parameters and the VM |
| 1 | plan | Self-check / tasks-routing #1: WRONG, T3 in wave 2 and 56dp Box | accept as duplicate of compose #2 | see compose #2 |
| 1 | plan | Self-check / tasks-routing #2: WRONG, T2 and T3 both in wave 2 | accept | T3 after T2; T4 after T3; one Gradle run at a time |
| 1 | plan | Self-check / tasks-routing #3: RISK, T1 acceptance: compileDebugKotlin only | accept | compile does not prove Save arguments; named args; pure merge unit test; grep check |
| 1 | plan | Self-check / tasks-routing #4: UNVERIFIABLE, T1 "wrong-argument test" | accept | no VM construction in tests; test the pure merge function |
| 1 | plan | Self-check / tasks-routing #5: WRONG, T2 model haiku max | accept | T2 sonnet high; T3 haiku max; T4a haiku max; T4b main session |
| 1 | plan | Self-check / tasks-routing #6: RISK, T2(a) and T2(d) imports | accept as duplicate of fact-dialog #7 | see fact-dialog #7 |
| 1 | plan | Self-check / tasks-routing #7: RISK, T1 uses line numbers | accept | edits anchored on code strings |
| 1 | plan | Self-check / tasks-routing #8: WRONG, T4 manual emulator check | accept | split: T4a Gradle; T4b physical SM-S908N (adb serial emulator-5554 is misleading) |
| 1 | plan | Self-check / tasks-routing #9: RISK, commands for all tasks | accept | run .\gradlew.bat from PowerShell; also run the ui.screens package |
