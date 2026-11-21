# UX-05 — Individual Deletion Confirmation Closure (2026-11-21)

Status: IMPLEMENTED (debug-only). Presentation gate only; repository contract untouched.

## Gap
Before this iteration the History row's "Sil" action called `onDeleteOne(record.id)` directly,
so ONE tap deleted a saved record with no confirmation. The error colour and 48dp target were
not a safeguard.

## Entry points traced
- History list row Delete -> now `deleteGate.request(id)` + `pendingDeleteId = id`.
- Record detail screen: has NO record-delete action (only annotation delete, which clears
  label/note/favourite and never removes the record) — verified in source, unchanged.
- No swipe-to-delete and no contextual menu exist in source.
- Clear All keeps its own separate `AlertDialog` and confirmation semantics.

## Implementation
`ui/history/DeleteConfirmationGate.kt` — pure, single-shot gate: request binds one stable
record id; cancel clears; confirm returns the id ONCE and clears. `HistoryScreens` shows the
shared `ConfirmActionDialog` (destructive) with a record-specific message; Cancel, dismissal
and Back clear the pending id and delete nothing; Confirm forwards the same id to the existing
`onDeleteOne`. The gate makes a double tap or recomposition unable to invoke the callback twice
or authorize a different record. Deletion outcome is decided by the repository; no success is
fabricated.

## Answers A-J
A: no longer possible (was possible before this change). B: yes, one dialog for the single
entry point. C: bound to the stable record id, not the row position. D/E: no deletion.
F: exactly once (gate clears on read). G: no double invocation. H: if the record expired or was
removed meanwhile, the repository decides and the UI shows its normal result — no crash, no
fabricated success. I: Clear All untouched and separate. J: opening details, changing filters or
toggling favourites never deletes.

## Evidence
Host tests: `DeleteConfirmationGateTest` (6 cases: no pending, binding, single-shot confirm,
cancel, replacement, confirm without request). Gates: core 370/370, app 87/87, compileDebugKotlin,
assembleDebug, lint 0 errors/15 warnings, debug APK, versionCode unchanged. Compose dialog
rendering and Back handling are device-only and remain NOT VERIFIED here.
