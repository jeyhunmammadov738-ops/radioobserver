# UX-03 — Observation Dashboard (2026-11-06)

Status: IMPLEMENTED (debug-only). Presentation only; no radio/lifecycle/persistence change.

## Baseline defects mapped to source
| Developer-reported defect | Source component | Resolution |
|---|---|---|
| Status vs action distinction weak | `ControlScreen.kt` `SummaryStrip` (single grey line) | Replaced with a compact status header: real per-source `StatusPill` (text + tone) + one-line summary |
| Seven alert triggers = enormous stack | `SessionAlertsPanel` `FlowRow` always visible | Triggers moved behind a disclosure (`TextButton`, collapsed by default) showing the selected count |
| Oversized cards / spacing | `RadioControlCard` | Already `Spacing.md` cards, one caption, 48dp targets — unchanged by UX-03 |
| Two competing primary buttons | `RadioControlCard` actions | Already conditional: Start only when startable, Stop only when stoppable (`ControlScreenProjection`), host-tested |
| Long privacy text | `InfoDisclosure` | Remains a collapsed disclosure |

## Contracts preserved (verified by existing host tests)
No new permission; no `startScan`; BLE 20s / Wi-Fi 30s bounds; separate user action after permission grant;
foreground RESUMED gating; no fabricated combined-session state; unknown stays unknown; cached Wi-Fi stays "Eski";
in-app alerts remain session-local and OFF by default; no countdown or scanning progress invented.

## Gates
core 370/370 · app 81/81 · compileDebugKotlin · assembleDebug · lint 0 errors / 15 warnings (baseline restored by
removing one unused string and rewording one count label; no suppression). DEBUG APK only.

## Device-only (NOT VERIFIED on host)
`StatusPill` row wrapping at 2.0x font, disclosure height, edge-to-edge spacing, alert panel on Xiaomi.

## Deferred to other workstreams
Feed/Radar/Timeline/filters (UX-04), History (UX-05), analytics (UX-06), export UX (UX-07), Settings/alerts config (UX-08).
