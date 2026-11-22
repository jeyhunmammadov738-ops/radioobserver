# UX-06 — Analytics Groups, Charts and Comparison (2026-11-22)

Status: IMPLEMENTED (debug-only). Presentation only; the WS05 pure analytics engine,
group membership, record storage and retention are unchanged.

## Changes
1. Group statistics card now opens with three REAL numeric blocks (records / Bluetooth /
   Wi-Fi) using the shared tabular-numeral `StatBlock`, so the headline numbers read as a
   dashboard instead of a list of rows.
2. Key/value statistics use the shared `StatisticRow`, giving consistent label/value
   alignment across analytics and History detail.
3. Distribution charts keep the exact `count (percent)` text and now render a track plus
   a proportional fill (120dp track, `surfaceVariant` + `primary`), so an empty or tiny
   category is visually obvious instead of an invisible bar.
4. Time-bucket bars print their exact count above each bar; zero buckets still render as
   the 4dp baseline, and the six-bucket shape is unchanged.
5. Two-group comparison keeps both full statistics cards stacked (no merged or invented
   metric), so compact phones scroll instead of clipping.

## Honesty rules preserved
- Every displayed number comes from `SessionAnalytics.stats`; no count is recomputed,
  estimated or extrapolated in the UI.
- Missing records are excluded by the existing membership resolution; no false
  physical-device claim is added.
- Empty groups keep their honest empty state and the existing coverage text.

## Evidence
- Gates: core 370/370, app 87/87, compileDebugKotlin, assembleDebug,
  lint 0 errors/15 warnings, DEBUG APK only, versionCode 1 / 0.1.0-phase1 unchanged.
- Device-only (NOT VERIFIED here): chart legibility at 2.0x font, bar rendering under
  gesture navigation, comparison scrolling on the physical Xiaomi phone.

## Deferred
Adaptive medium/expanded two-column comparison and animated charts were not added; they
need window-size-class work that belongs to a later authorized stage.
