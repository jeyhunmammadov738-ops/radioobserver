# UX-00 — Component Catalog and Existing-to-Target Mapping

Existing shared components live in `app/src/main/java/com/example/radioobserver/ui/components/DesignSystem.kt`
and `ui/theme/Theme.kt`. Anything not listed as shared is currently a private composable inside its
screen file.

## 1. Existing shared components

| Component | Location | Current behaviour | Target treatment | Owner |
|---|---|---|---|---|
| `Spacing` object | `DesignSystem.kt:33` | xs/sm/md/lg constants | extend to the 2/4/8/12/16/24/32 scale; single source of truth | UX-01 |
| `StatusPill` + `PillKind` | `:43`, `:85` | NEUTRAL/ACTIVE/ATTENTION/ERROR | keep semantics; add size variant `compact`; text always present | UX-01 |
| `SectionHeader` | `:102` | titleMedium heading | titleSmall + optional trailing action slot | UX-01 |
| `CompactInfoRow` | `:117` | label/value line | keep; add valueStyle override for numbers | UX-01 |
| `SourceIcon` | `:145` | BLE/Wi-Fi glyph | keep; enforce 24dp icon box | UX-01 |
| `EmptyState` | `:168` | single text block | add `title` + optional `action` slot; one empty state per screen | UX-01 |
| Theme `DarkScheme` | `Theme.kt:56` | darkColorScheme | migrate to design-contract tokens | UX-01 |

## 2. Screen-local components that should become shared

| Private composable | Location | Target shared component | Workstream |
|---|---|---|---|
| `RadioControlCard` | `ControlScreen.kt:227` | `OperationCard` (status, primary action, secondary row, disclosure slot) | UX-01/UX-03 |
| `SummaryStrip` | `ControlScreen.kt:182` | `StatusStrip` (max 3 segments) | UX-03 |
| `InfoDisclosure` | `ControlScreen.kt:328` | `DisclosureCard` | UX-01 |
| `SessionAlertsPanel` | `ControlScreen.kt:471` | `AlertConfigPanel` with `TriggerChipGrid` | UX-03 |
| `ObservationRow` | `FeedScreens.kt:342` | `ObservationRow` (shared with History row language) | UX-04 |
| `FilterPanel` | `FeedScreens.kt:975` | `FilterSheet` (bottom sheet) shared by Feed/History | UX-04 |
| `HistoryFilterRow` | `HistoryScreens.kt:203` | `SegmentedFilter` | UX-05 |
| `HistoryRecordRow` | `HistoryScreens.kt:245` | `SavedRecordRow` | UX-05 |
| `GroupStatsCard` / `DistributionRows` / `TimeBucketBars` | `SessionGroupScreens.kt:449/502/530` | `StatBlock`, `DistributionBar`, `BucketChart` | UX-06 |
| `RetentionChips` / `ToggleRow` | `SettingsScreen.kt:286/235` | `SettingRow` + `SegmentedChoice` | UX-08 |
| `ExpandableText` | `SettingsScreen.kt:263` | `DisclosureCard` (same as above) | UX-01 |

## 3. New components required by the contract (no feature change)

`ScreenScaffold` (title + actions + insets), `PrimaryActionBar`, `DestructiveZone`,
`MetaLine` (freshness/signal/time), `StarButton` (48dp, toggled semantics), `CountBadge`,
`ConfirmDialog` (unified destructive wording), `ErrorBanner` (typed error → copy),
`ChartLegend`, `TruthNote` (radar/distance disclaimers).

## 4. Rules

Every new shared component carries: one preview, one semantics contract, no business logic, no
string literals (resource keys only), and must not own state beyond visual toggles. Screen files
keep calling the existing callbacks; UX work changes appearance and composition only.
