# UX-06 — Analytics Groups, Charts and Comparison: Targeted Completion (2026-11-22)

Status: CODE/HOST COMPLETE (debug-only). Xiaomi visual acceptance OPEN.

Baseline HEAD `6c8ef83`; final HEAD recorded in the report. Presentation layer only: the
WS05 engine, group persistence, membership rules and History storage are unchanged.

## 1. Requirement matrix

| # | Requirement | Source | Behavior / evidence | Tests | Status |
|---|---|---|---|---|---|
| A | Group list hierarchy | `ui/SessionGroupScreens.kt` `SessionGroupsScreen` | Create is now the only filled primary action, Compare is secondary beside it at equal weight; each group card shows name (titleMedium) then member count, actions flow below | source review | MET |
| B | Group detail presentation | `SessionGroupDetailScreen`, `GroupStatsCard` | Three numeric `StatBlock`s, `StatisticRow` key/values, track charts, honest coverage text | source review | MET |
| C | Explicit membership selection | `AddRecordSection` | Available records only (`id !in group.recordIds`), one explicit add per record, hint text, honest "no available records" state | source review | MET |
| D | Creation | `GroupNameDialog` + `Button` | Primary action opens a bounded name dialog; validation messages readable; save/cancel explicit | existing core group tests | MET |
| E | Renaming | `GroupNameDialog` (rename target) | Same dialog, prefilled with the current name; unchanged callbacks | existing core group tests | MET |
| F | Deletion safety | delete `TextButton` + `AlertDialog` | Delete is error-coloured and visually separated; dialog body now names the group and states that records stay in History; confirm is error-coloured; cancel/dismiss delete nothing | source review | MET |
| G | BLE/Wi-Fi numbers | `GroupStatsCard` | Exact engine counts in blocks and rows; no device counts | core analytics tests | MET |
| H | Signal/freshness charts | `DistributionRows` | Track + proportional fill; text keeps exact `count (%percent)` | `ChartScaleTest` | MET |
| I | Six time buckets | `TimeBucketBars` | Exact count printed above every bar; zero bucket draws only the 4dp baseline while the text says 0 | `ChartScaleTest` | MET |
| J | Two-group comparison | `SessionGroupCompareScreen` | Each group keeps its own identity, picker value and metrics; no merged numbers | source review | MET |
| K | Medium/expanded behavior | `BoxWithConstraints` in compare screen | >=600dp renders both honest cards side by side; compact widths stay stacked and scroll | NOT HOST-TESTABLE (device pending) | DEVICE PENDING |
| L | Missing/deleted/expired records | compare + detail `mapNotNull` resolution | Missing ids are simply absent; nothing is resurrected and no count is invented | core membership tests | MET |
| M | Numeric/chart consistency | `ChartScale` | Bar fraction = count / largest count, 0 for zero, clamped; percentages come from the engine | `ChartScaleTest` (8 cases) | MET |
| N | WS05 + persistence preserved | core modules untouched | No diff in `core/radio`; group ids, names, ordering, membership and retention unchanged | full core suite | MET |

## 2. Chart-to-engine trace

Persisted History -> group `recordIds` -> existing id resolution (`mapNotNull`) ->
`SessionAnalytics.stats` -> `GroupStats` / `CategoryCount` -> presentation.

* Percentages are supplied by the engine: `percent = (count*100 + total/2)/total`, denominator =
  available records in that group, half-up integer rounding. The UI never recomputes them.
* Bar length is display-only: `ChartScale.fraction(count, maxCount)`. It never changes a number,
  never interpolates, and returns exactly 0 for zero counts and for all-zero groups.
* Empty groups report zeros; no bucket or category is fabricated.

## 3. Responsive decision

Stacked cards are kept for compact phones because a full-width chart is more readable there.
Side-by-side comparison is added only at >=600dp using `BoxWithConstraints` (already installed
Compose API); no window-size-class dependency, no tablet navigation architecture, no animation.
Physical validation of the >=600dp path is DEVICE PENDING.

## 4. Gates (final tree)

core 370/370, app 95/95 (8 new), `compileDebugKotlin`, `assembleDebug`, lint 0 errors / 15
warnings, debug APK only, versionCode unchanged.
