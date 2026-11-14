# UX-05 — Targeted Completion (2026-11-08)

Status: IMPLEMENTED (debug-only). Presentation only; stored records, annotation schema and
deletion contract unchanged. Toolbar work from the previous iteration was NOT rebuilt.

## Requirement matrix (A-L)

| Req | Actual source/symbol | Current behaviour | Evidence | Status |
|---|---|---|---|---|
| A Compact toolbar | HistoryScreen toolbar Surface (UX-05 iteration 1) | one surface: filters + favourites + report/groups | source + screen review | MET (device pending) |
| B Source/favourites filtering | HistoryFilterRow, favouritesOnly chip, visible filter | three source filters, favourites-only, honest filtered-empty | HistoryFilter/empty tests | MET |
| C Compact rows | HistoryRecordRow | one band: icon+name, meta+3 actions, 48dp each | source | MET this iteration |
| D Detail hierarchy | HistoryDetailScreen | sections: identity, source, saved time, sanitized metadata, classification, favourite, label/note | source | MET |
| E Favourite presentation | row star (state from HistoryAnnotation.favorite) | on/off glyph, 48dp, label carried | annotation tests | MET |
| F Label/note editor | HistoryAnnotationEditor path | validated 40/200 chars, save/clear | annotation limit tests | MET |
| G Row support metadata | HistoryRecordRow annotation line | label text and "Not" marker ONLY when present | NEW this iteration | MET this iteration |
| H Destructive confirmation | clear-all dialog (unchanged), per-row delete | per-row delete now error-coloured and separated from Ayrintilar | source | MET this iteration |
| I Empty/filtered/missing states | FeedEmptyKind, history empty strings, missing-record detail | honest copy, no fabrication | app tests | MET |
| J Navigation/editor retention | AppNavigation HistoryDetail, editor state | back returns to list; editor keeps unsaved text locally | source | MET |
| K Privacy/persistence | HistoryRepository calls unchanged | no schema/write-path change; no address field | 370 core tests | MET |
| L Regression coverage | core 370 + app 81 | all green this tree | gates below | MET |

## Changes made
1. `HistoryRecordRow` now receives the record's `HistoryAnnotation` (single source of truth) instead of a
   derived boolean, and renders the saved label and a "Not" marker in the existing meta line only when they
   exist. No new row height on records without annotations; no invented text.
2. The per-record delete action is now rendered in the error colour, visually separated from "Ayrintilar",
   while keeping its 48dp target and its accessibility description. Deleting still requires the explicit
   button; a row tap still does not delete.

## Not changed (deliberately)
Record schema, annotation schema/limits, repository calls, retention, bulk-clear confirmation, navigation,
favourites semantics, History access to export/groups.

## Gates (final tree)
core 370/370 · app 81/81 · compileDebugKotlin · assembleDebug · lint 0 errors / 15 warnings · DEBUG APK only,
versionCode 1 / 0.1.0-phase1.

## Device pending
Row wrap at 2.0x font, label+note marker readability, star contrast, dialog readability.
