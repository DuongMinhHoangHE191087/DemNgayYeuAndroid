# CROSS-PART routing brief (one TARGET part per run, no Codex needed)

You are a Sonnet subagent. Your prompt gave you TARGET (a part file name without `.md`, e.g. `p4-ui-compose`, short tag `pN`). Your job: apply to TARGET every `CROSS-PART` request that OTHER parts raised against it.
Paths are relative to the repo root `F:\Github_Project\DemNgayYeuAndroid` (Windows; use Read/Grep/Edit/Write or PowerShell; forward slashes are fine). No git write commands, no source code, no other plan file.

## Collect your items yourself (do not rely on a summary)
1. `Grep -n "CROSS-PART"` over `docs/superpowers/plans/2026-10-13-kmp-ios/p*.md` EXCLUDING TARGET itself (those are TARGET's own outgoing notes), and over `docs/superpowers/reviews/2026-10-13-kmp-ios/log-fragments/*.md` (rows of other parts' triage; skip `t1-<TARGET tag>*` and `x1-<TARGET tag>*`). Also read `DECISIONS.md` lines tagged `DECISION-CONFLICT` that name TARGET.
   Also Grep `CROSS-PART` in `00-master.md` (section 12, written by the master pass) and in `DECISIONS.md` (sections 2 and 3). The master pass records the resolved direction (for example which golden-vector ids and file names are official); where a part file or fragment disagrees with master, master wins.
2. Keep a hit only if the text after `CROSS-PART` names your TARGET tag (`pN`, `PN`, or the part file name), alone or in a list such as `p2/p7`. A hit that names several parts is yours too; the other parts get their own run.
3. A hit may be a pointer only (for example "see P5 triage"). Then open the cited task row or paragraph in the raising part (grep its id) to read the full request.
4. De-duplicate (the same request often appears in the raising part and in its log fragment). Write the final numbered list into your head or a scratch note, not into the repo.
5. Read the section for your TARGET tag in `_tools/route-items.md` (items the grep cannot find: dangling-id fixes, golden-vector renames from P8 section 4.1, hints from the cut-off Codex run). Merge it with your prompt's ITEMS list, if any. Every entry there is evidence to check, not truth.

## Inputs (read in slices; a part file is 15-60 KB, never read one whole unless it is under ~300 lines)
1. `docs/superpowers/plans/2026-10-13-kmp-ios/_tools/rules.md` (short, read fully).
2. The TARGET part: grep `^## ` for the outline, then Read by offset/limit only the sections an item touches.
3. `DECISIONS.md` and `_addendum-owner-update.md` only when an item is about a decision, anti-bypass or the task format.

## For EVERY item
1. Verify it against the real repo and the TARGET text (paths, symbols, ids). ITEMS come from other model runs: evidence to check, not truth. Apple / Google / Firebase / JetBrains claims: check the official docs (WebSearch/WebFetch).
2. Decide:
   - **accept**: edit TARGET at the exact place (task row, design text, Definition of Done, risk). Keep every task <= M with a concrete test that fails if the logic is wrong; new tasks take the next free id of this part's prefix.
   - **already**: TARGET already does it (quote the line). Log it as `accept: already in <task id>`.
   - **partial**: say what you took and what you left.
   - **reject**: only with concrete counter-evidence (file:line, official doc URL, or a logical argument). "Too much work" is not a reason when the owner asked for full parity.
   - An item that really belongs to a THIRD part: do not edit; put `CROSS-PART: <pN> <change>` in the action cell and in the report.
3. Anti-bypass (subscription/entitlement) and classic-pitfall items are never "minor".

## Edit rules
- Touch only `TARGET.md` and your log fragment.
- After editing run `node docs/superpowers/plans/2026-10-13-kmp-ios/_tools/plan-tools.mjs lint`; fix every ERROR that points to your ids or file. "depends on unknown <id>" for another part's id is acceptable; list it in the report. Table header stays exactly: `| id | phase | files (owned) | change | test or command | depends on | model | host (win/mac) | size (S<=0.5d, M<=2d) |`.
- Append ` · routed <YYYY-MM-DD>` to the part's first "Trạng thái" line. Keep the prose language (Vietnamese; identifiers, paths and code unchanged).

## Log fragment
File: `docs/superpowers/reviews/2026-10-13-kmp-ios/log-fragments/x1-<TARGET>.md`, created with the Write tool. One row per item, exactly:

`| 1x | plan | [<pN>] [MAJOR] <task id or section> <summary, <=12 words> (raised by <pM>) | accept|partial|reject: <evidence or reason, <=40 words> | <where edited: section, task ids, or none> |`

The first cell must be `1x` (the log tool groups these rows as "r1x <part>", apart from Codex rows). Start the verdict cell with accept, partial, or reject. No header row.

## Final report (<=10 lines)
`STATUS: DONE|PARTIAL|BLOCKED` · items N: accept A / partial P / reject R · tasks added X, split Y · lint result for your ids · new CROSS-PART items for a third part (one line each) · anything only the owner can decide · blockers.
