# Review log: birthday clear button and dialog condense (2026-10-23)

Plan: `docs/superpowers/plans/2026-10-23-birthday-clear-and-dialog-condense.md`. Codex is waived by the owner, so the reviewer is Claude (main session), and every finding is checked against the code before the plan changes. Plan author: Opus subagent (effort high).

## Plan review, round 1 (Claude, against the code)

| round | stage | finding | verdict | action |
|---|---|---|---|---|
| 1 | plan | T4 writes `remember { birthDate.value.isNotBlank() }`, but `birthDate` is a `by`-delegated String (EditMyProfileDialog.kt:178) | accept | `remember { birthDate.isNotBlank() }`, unkeyed; both `onSave` signatures (:117, :174) change |
| 1 | plan | "X does not open the picker" test written with `performClick()` passes vacuously (every old test uses the semantics path, not `PressInteraction.Release`) | accept | T1 must use `performTouchInput { click() }`; assert `changes == listOf("")` and `btn_select_year_month` absent |
| 1 | plan | X contentDescription hard-codes "Xóa ngày sinh" inside a shared component | accept | `if (isEnglish) "Clear date" else "Xóa ngày"`, same pattern as the calendar button (:1101) |
| 1 | plan | After "Đóng" is removed the button Row is empty when coupled (Row held "Đóng" + `if (!isCoupled) Button`) | accept | T3 deletes the whole Row; `if (!isCoupled) Button` sits directly in the Column |
| 1 | plan | Removing UI orphans a string resource and adds a lint `UnusedResources` warning (lint is at the 128 ceiling) | reject | grep: both dialogs use inline `if (isEnglish)` strings, 0 `stringResource` |
| 1 | plan | Relabelling "Bắt đầu yêu" breaks a test that finds the field by label | reject | grep over `app/src/test`: no test finds it by that text; "Xóa ngày sinh" exists only at Dialogs.kt:1401 |
| 1 | plan | `test-inventory.mjs --check` fails because of new tests | reject | `checkInventory` only fails on recorded tests that are missing or renamed; new tests are always fine |

Round 1 result: 7 findings, 4 accepted, 3 rejected, none open. Round 2 is not needed. Next gate: the owner's approval (no product code before it).
