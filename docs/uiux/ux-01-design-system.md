# UX-01 — Professional Material 3 Design System Foundation (2026-11-06)

Status: IMPLEMENTED (debug-only) — visual foundation only; no functional contract changed.

## 1. Files

- `ui/theme/DesignTokens.kt` (new, pure Kotlin): CONTROL DECK tokens + WCAG math.
- `ui/theme/Theme.kt` (rewritten): full dark role mapping, tuned typography, shapes.
- `ui/components/DesignSystem.kt` (additive): `Dimensions`, `RadioSourceBadge`,
  `StatusBanner`, `StatisticRow`, `StatBlock`, `SectionHeader(description, trailing)`,
  `ActionRow`, `ActionEmphasis`, `PillKind.WARNING/STALE`, `Spacing.xxl`. Existing
  signatures unchanged; no call site modified.
- `ui/components/DesignSystemShowcase.kt` (new): preview definitions.
- `app/src/test/.../DesignTokensTest.kt`, `PillMappingRegressionTest.kt` (new).

## 2. Token values (dark = product identity)

| Role | Token | Value |
|---|---|---|
| background / onBackground | DARK_BACKGROUND / DARK_ON_BACKGROUND | #10131A / #E9EDF7 |
| surface / onSurface | DARK_SURFACE / DARK_ON_SURFACE | #161A24 / #E9EDF7 |
| surfaceContainerLow | DARK_SURFACE_LOW | #1C2230 |
| surfaceContainer | DARK_SURFACE_CONTAINER | #202836 |
| surfaceContainerHigh | DARK_SURFACE_HIGH | #2A3446 |
| surfaceContainerHighest | DARK_SURFACE_HIGHEST | #303B51 |
| surfaceVariant / onSurfaceVariant | DARK_SURFACE_VARIANT / DARK_ON_SURFACE_VARIANT | #2A3446 / #B6BFD2 |
| primary / onPrimary | PRIMARY / ON_PRIMARY | #AEBFFF / #16224E |
| primaryContainer / on… | #2B3B78 / #DCE1FF |
| secondary (teal) / onSecondary | #47C3BA / #00332F |
| secondaryContainer / on… | #11463F / #A8EDE3 |
| tertiary / onTertiary | #C9C2E8 / #2C2949 |
| error / onError | #FFB4AB / #690005 |
| errorContainer / on… | #5C1B1B / #FFDAD6 |
| outline / outlineVariant | #8C97AE / #3A4356 |
| inverseSurface / inverseOnSurface | #E9EDF7 / #262B36 |
| warning / warningContainer (extension) | #FFC46B / #4A3610 (on: #FFDFA8) |
| stale marker (extension) | #9AA3B5 |

Light scheme: the previously shipped palette, intentionally unchanged (light-theme
acceptance is out of scope by developer decision). No dynamic/wallpaper colour.

## 3. Typography (platform font only)

headlineSmall 22/28 SemiBold · titleLarge 18/24 SemiBold · titleMedium 15/20 Medium
(record titles) · titleSmall 13/18 Medium (sections) · bodyLarge 15/22 · bodyMedium
14/20 · bodySmall 12/16 · labelLarge 14 · labelMedium 12 · labelSmall 11. Numbers:
`DeckNumerals.numeric` (tabular `tnum`) for aligned statistics. Turkish diacritics and
long words verified by font choice (system font) and existing multi-line/wrap rules.

## 4. Shapes, spacing, dimensions

Shapes: 4 / 6 / 10 / 14 / 20 dp (extraSmall…extraLarge). Spacing: 4, 8, 12, 16, 24, 32
(single `Spacing` object; no competing scale). Dimensions: touch target 48, primary
action 48, compact row min 44, badge 22, icons 16/20/24, card padding 12, section gap
16, list gap 8. Compact visuals never shrink the 48dp touch target.

## 5. Semantic states

Pill/banner kinds: NEUTRAL, ACTIVE, ATTENTION, ERROR, WARNING, STALE. Historical
status→kind mapping is UNCHANGED (regression-tested, incl. TIMED_OUT→ERROR). Cached
Wi-Fi stays "Eski" with the STALE marker; no colour-only semantics; no new domain
states invented.

## 6. Contrast results (WCAG, computed by host test)

| Pair | Ratio | Requirement |
|---|---|---|
| onBackground / background | ~15:1 | ≥4.5 ✓ |
| onSurface / surface, surfaceContainerLow | ~14:1 | ≥4.5 ✓ |
| onSurfaceVariant / surfaceContainer, surfaceContainerHigh | ~7:1 | ≥4.5 ✓ |
| onPrimary / primary | ~8:1 | ≥4.5 ✓ |
| primary / background | ~10:1 | ≥4.5 ✓ |
| onPrimaryContainer / primaryContainer | ~8:1 | ≥4.5 ✓ |
| onErrorContainer / errorContainer | ~9:1 | ≥4.5 ✓ |
| error / surface | ~9:1 | ≥4.5 ✓ |
| onWarningContainer / warningContainer | ~9:1 | ≥4.5 ✓ |
| warning / surface | ~11:1 | ≥4.5 ✓ |
| secondary / background | ~8:1 | ≥4.5 ✓ |
| onSecondaryContainer / secondaryContainer | ~8:1 | ≥4.5 ✓ |
| outline / surface | ≥3 | ≥3 ✓ (non-text) |
| stale / surfaceContainer | ~5:1 | ≥4.5 ✓ |

Ratios are asserted by `DesignTokensTest`; surface steps ascend strictly (no flat noise).

## 7. Component mapping (UX-00 catalog → UX-01 state)

| Target | UX-01 status |
|---|---|
| RadioSourceBadge | implemented (BLE/Wi-Fi, text + tone, a11y label) |
| StatusBanner | implemented (title + detail + optional explicit action) |
| StatisticRow / StatBlock | implemented (tabular numerals) |
| SectionHeader (+description/trailing) | implemented, backward compatible |
| ActionRow / ActionEmphasis | implemented (layout container + emphasis enum) |
| CompactObservationRow | foundation deferred to UX-04 (needs row redesign ownership) |
| ScreenScaffold / insets | deferred to UX-02 (shell ownership) |
| FilterSheet, AlertConfigPanel, SavedRecordRow, ChartBlock, DisclosureCard | deferred to their owning workstreams |

## 8. Previews

`DesignSystemShowcase()` renders theme surfaces, typography hierarchy, action
hierarchy, badges, all banner kinds, statistics, numeric style and empty state with
deterministic synthetic Turkish labels (no real SSIDs/MACs/BSSIDs/records). The project
has no `ui-tooling-preview` dependency and UX-01 is not authorized to add one, so these
are SOURCE-LEVEL preview definitions — no screenshot or on-device rendering is claimed.

## 9. Regression evidence

- core JVM 370/370 · app JVM 60/60 (49 historical + 11 new) · `compileDebugKotlin` ·
  `assembleDebug` · lint 0 errors / 15 warnings (baseline parity; no new warning).
- New tests: contrast matrix, surface hierarchy, spacing scale, touch targets,
  contrast symmetry, historical status→pill mapping.
- Compose interaction semantics (clicks, focus, disabled rendering) cannot run in this
  host environment: NOT VERIFIED here; they remain part of Xiaomi acceptance.

## 10. Xiaomi acceptance (developer)

Dark Control Deck surfaces on all tabs; readable secondary text; status pill tones
(idle/active/permission/error/warning/stale); BLE vs Wi-Fi badges legible; 48dp targets;
record rows still dense; no functional change (start/stop, permissions, History, groups,
export, alerts, FieldDiag hidden + 7 taps); system dark mode both directions.

## 11. Known limitations

Light scheme untouched; dynamic colour absent by design; no screenshot evidence;
CompactObservationRow/ScreenScaffold/other catalog items deliberately deferred; no new
dependency added.
