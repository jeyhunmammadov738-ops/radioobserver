# UX-09 P1 Visual Fix — Observations (Gözlemler) Control Area (2026-11-23)

Status: CODE/HOST COMPLETE. Xiaomi re-verification OPEN.

Baseline HEAD `3147e6b`. Visual-only correction of the UX-04 Observations screen; no
feature, behaviour, navigation or radio-lifecycle change.

## Confirmed defects and root causes

| ID | Defect | Root cause (source) | Fix |
|---|---|---|---|
| P1-01 | Source, view and filter controls overlapped | All seven controls lived in one shared surface without per-group rows, and the content area below requested `fillMaxSize()` inside the same `Column`, so a view could claim the full screen height and paint over the control area | Controls split into three labelled groups, each with its own `FlowRow`; content wrapped in `Box(Modifier.weight(1f))` so it can only use the space the controls leave |
| P1-02 | Button labels wrapped over neighbouring outlines | Chips had no line policy inside a shared flow | Every chip/button label is `maxLines = 2` inside its own group flow, and groups carry explicit `verticalArrangement` spacing |
| P1-03 | Empty-state sentence rendered twice | Header `Text` fell back to `feed_empty_no_session` when `state.rows` was empty while the state block rendered the same string via `EmptyState` | Header now prints the count only when rows exist; the honest empty explanation is rendered exactly once by the state block |
| P1-04 | No hierarchy between source / view / filters | Single unlabelled surface | Three headings: "Kaynak", "Görünüm", "Süzgeçler" (new strings), each `semantics { heading() }` |

## Preserved
All callbacks and semantics: source filter (Tümü/Bluetooth/Wi-Fi), view kind
(Liste/Radar/Zaman tüneli), advanced filter button with applied-criteria badge, favourites
chip, filter panel content and presets, empty taxonomy, radar and timeline projections,
48dp minimum targets, bottom navigation (untouched, as instructed).

## Gates (fresh, final tree)
core 370/370 · app 109/109 · `compileDebugKotlin` · `assembleDebug` · lint 0 errors / 15
warnings (baseline). Debug APK only; versionCode 1 / 0.1.0-phase1.

## Device-pending
Re-check on the Xiaomi device: three groups visible without overlap at 1.0x and 2.0x font,
chip labels wrapping inside their own outline, a single empty-state sentence, radar/timeline
filling the bounded content area, and scrolling in the list view.
