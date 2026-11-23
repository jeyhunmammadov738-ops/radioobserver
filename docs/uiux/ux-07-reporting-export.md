# UX-07 — Reporting and CSV/JSON Export (2026-11-23)

Status: CODE/HOST COMPLETE (debug-only). Xiaomi visual/SAF acceptance OPEN.

Baseline HEAD `940c68e`. Presentation only: the WS06 report builder, CSV/JSON schemas,
byte content, privacy rules, selection semantics and SAF launchers are unchanged.

## Requirement matrix

| # | Requirement | Implementation | Evidence | Status |
|---|---|---|---|---|
| A | Scope, format, count, sensitive fields and Save as understandable steps | Four numbered section headers (1 Kapsam, 2 Biçim, 3 Gizli alanlar, 4 Kaydet); Save is now the single filled primary button at the end of the flow | `ReportExportScreen.kt` | MET |
| B | SSID, labels and notes visibly excluded by default | A status banner states "SSID, etiket ve notlar DAHİL DEĞİL" with NEUTRAL tone; once either switch is enabled it switches to ATTENTION with "Gizli alanlar dahil edilecek". Both switches still default to false | `ExportReview`, `ExportReviewerTest` | MET |
| C | Picker transition and success/error/cancel honest | Pending content is held until the SAF picker returns; cancel sets the honest cancel message, a null stream or throwable sets the failure message, success reports the real record count. All three now render as StatusBanner (ACTIVE / ERROR) instead of bare text | `ReportExportScreen.kt` | MET (device PENDING) |
| D | Numeric consistency with History/analytics; opening never scans | Count is derived from the same saved-record lists the History and analytics screens read (`records`, group membership); no scan, consent or recording call exists in the screen | `ExportReviewerTest`, source audit | MET |
| E | No sharing/import/restore/new formats | Only CSV and JSON remain; no share sheet, import, restore or backup code path added | source audit | MET |

## Numbers
`ExportReviewer.review` is pure: it counts the selected distinct records, the total saved
records, and the number of enabled sensitive field groups (0 = excluded by default, 1 =
names or annotations, 2 = both). Negative inputs clamp to zero, and an unresolved compare
scope is reported as blocked before any other state, so the UI cannot claim a count it does
not have.

## Tests
7 new host tests: default exclusion, single opt-in, both opt-ins, blocked scope precedence,
empty selection, non-negative clamping, selection never exceeding the total.

## Gates (final tree)
Core JVM 370/370, App JVM 102/102, `compileDebugKotlin`, `assembleDebug`, lint 0 errors /
15 warnings (baseline preserved; the four strings replaced by numbered steps were removed
and the count string was reworded so no new warning was introduced).

## Deliberately unchanged
Export engine and schemas, privacy defaults, selection semantics, SAF contract, dialog for
the whole-history scope, navigation. Not implemented: sharing, import, restore, backup, new
formats, new fields.
