# UX-04 — Live Observations Experience (2026-11-06)

Status: IMPLEMENTED (debug-only). Presentation only: same sanitized live state, same
filters, same presets, same navigation; no scanning, permission, session or persistence
change.

## Scope
List, schematic Radar, Timeline, source and favorites filters, advanced filters and saved
presets, honest empty/inactive/stale/unavailable states.

## What changed
- The three view modes, the three source filters, the advanced-filter button (with applied
  criteria badge), the session-local favorites chip and the view label are now grouped into
  ONE compact toolbar surface (`surfaceContainerLow`, medium shape, `Spacing.md` inner
  padding, `Spacing.xs` vertical rhythm) instead of stacked full-width blocks. Every chip
  keeps its 48dp minimum target and its existing callback.
- Cached/stale observations now use the UX-01 `PillKind.STALE` tone instead of the generic
  ATTENTION tone, so "Eski" is visually distinct from a warning while the WORD still carries
  the meaning (never colour alone).

## Deliberately unchanged
- Radar geometry and the schematic label (no real positions, no bearings, no distances).
- Timeline bucketing and honest "no observations in this minute" gaps.
- List row content: source, sanitized primary/secondary text, freshness pill, favourite star
  and the same detail navigation.
- Empty-state taxonomy: session-active-with-no-rows, no-session, and no-matches-for-filter
  (favorites-only and advanced-filter variants) keep their exact copy and conditions.
- Filter panel fields, preset apply/save/delete behaviour and the applied-criteria count.

## Contract checks
- Pure feed filter/preset/presentation logic is unchanged and covered by the existing host
  tests (filters, presets, empty-kind projection, row ordering); app suite 81/81.
- No new composable state leaves the screen: toolbar grouping adds no state at all.

## Gates (final tree)
- `:core:radio:test` 370/370 · `:app:testDebugUnitTest` 81/81
- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:lintDebug` 0 errors / 15 warnings
- DEBUG APK only; versionCode 1 / 0.1.0-phase1 unchanged.

## Device-only (NOT VERIFIED here)
Toolbar wrap at 2.0x font, Radar/Timeline render and touch targets, edge-to-edge spacing,
dark-theme contrast of the new STALE pill, and scrolling behaviour on the physical phone.

## Deferred to later UX stages
History (UX-05), analytics/groups (UX-06), reporting (UX-07), settings/alerts/catalog
(UX-08), final integrated visual QA (UX-09).
