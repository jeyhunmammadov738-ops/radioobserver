# Workstream 08 — Integrated Quality Gate and Functional Acceptance Preparation (2026-11-06)

**Scope:** cross-workstream integration QA, privacy/data-integrity regression, final DEBUG build,
honest acceptance evidence. No new features, no redesign, no release, no next roadmap.
Build Mode: ON (DEBUG only). No device/ADB/emulator. **Defects found: NONE.** Evidence-only QA
with a fresh debug verification was the valid outcome.

## 1. Feature dependency graph (implemented contracts)
History/consent (store) -> LiveRowTracker -> HistoryRecord
ObservationUiState -> LiveFilter -> [Liste | Radar | Zaman tuneli]
ObservationUiState -> SessionAlertEngine (foreground, in-memory)
HistoryRecord -> HistoryAnnotations (WS04) ; HistoryRecord -> SessionGroups (WS05)
HistoryRecord + SessionGroups + HistoryAnnotations -> ReportBuilder (WS06) -> SAF export
SignatureClassifier <- UserSignatureRules (WS03B) ; presets/filters (WS02); FieldDiag (debug gate)

## 2. Defects
NONE FOUND in this pass. No source change was required, so the final tree equals the WS06 tree:
the debug APK SHA-256 is identical to the WS06 build (1144a279...bb836c), which independently
confirms that no silent source drift occurred between WS06 and WS08.

## 3. Privacy / consent / data integrity (verified by existing behavioural JVM tests)
- Recording consent default OFF; independent SSID consent; no write path from view/filter/alert/
  group/export screens (they are pure projections; only the tracker writes, gated by consent).
- Retention 7/14/30, 500-record cap, single delete, Clear All: no resurrection through favorites,
  notes, group membership, statistics or exports (WS04/WS05 tests).
- Four distinct identities: session-local rowId, saved-record UUID, group metadata id, physical
  device (never claimed). No cross-session identification or fingerprinting exists structurally.
- Sanitisation holds at every boundary: no MAC/BSSID/payload/undisclosed SSID/location/storage
  path/FieldDiag state can be represented or exported (model-level structural tests).

## 4. Storage resilience
Separate versioned app-private files: history store, filter presets, signature user catalog,
history annotations, session groups. All are atomic, bounded, fail-closed on unsupported schema
(original file is never rewritten destructively) and drop+count corrupt lines. Storage files are
independent; a corrupt one cannot crash startup or destroy another store.

## 5. Radio / lifecycle invariants (code + host tests)
Explicit Start only; separate tap after first permission grant; no scanning on open/navigation/
History/analytics/export; RESUMED-gated, bounded sessions; passive Wi-Fi via getScanResults
(no WifiManager.startScan anywhere in call paths); cached Wi-Fi stays "Eski" and cannot produce a
"new device" alert; alerts OFF by default, bounded, deduplicated, session-limited; Radar/Timeline
membership invariant under filters/favorites/catalog; FieldDiag-04 debug-only, hidden until seven
taps, unpersisted, unreachable from export.

## 6. Startup safety (WS02 hotfix contract)
No Context access in Activity property initialisers. Filter presets, signature catalog, history
+ annotations, session groups and alert engine are constructed inside onCreate AFTER
super.onCreate; no `!!`, no swallowed exceptions, no global Activity references.

## 7. Export / SAF boundaries
CSV/JSON generation is pure and deterministic (UTC, RFC 4180, formula-injection defence,
explicit nulls/unknown, stable ordering, network names and user labels/notes excluded unless
opted in). File writing uses SAF CreateDocument and cannot mutate History, groups, annotations or
consent. **WS06 real SAF picker/provider behaviour and exported bytes remain UNVERIFIED here
(phone-only) and WS06 phone acceptance stays OPEN.**

## 8. Git ancestry observation (read-only)
Linear: bf61647 (WS06) -> 067a200 (license/.kiro) -> fe2ec74 (WS07) -> HEAD. All three are
ancestors of HEAD; no history rewrite, no lost work. The local origin/main tracking ref was stale
at 66eb5c0 because WS06+ pushes used an explicit token URL; the remote itself holds the newer
commits (verified earlier by FETCH_HEAD).

## 9. WS07 status
Research-only. No GPS, map, INTERNET, Remote ID or location-reading code exists. The proposed
optional phone-location card remains NOT authorized and NOT implemented.

## 10. Definition of Done
- Host QA green: core 370/370, app 49/49, compileDebugKotlin, assembleDebug, lint 0 errors/15
  warnings, all on the final tree. PASS.
- No new permission/network/GPS/background work. PASS.
- Version/signing/appId unchanged (com.hjinlabs.radioobserver, 1 / 0.1.0-phase1). PASS.
- WS06 Xiaomi export acceptance: OPEN (developer only).
- WS08 Xiaomi functional acceptance: OPEN (developer only).

## 11. Xiaomi acceptance checklist (UNVERIFIED IN OPENCODE)
1. Install OVER the existing signed debug app (never uninstall / Clear Data). Cold start + reopen.
2. Confirm prior History (hundreds of records), annotations, presets, user catalog, groups survive.
3. BLE + passive Wi-Fi manual Start; permission flow; stop and foreground-loss teardown; no
   automatic scanning when navigating or opening History/analytics/export.
4. Liste/Radar/Zaman tuneli membership + schematic/stale labels unchanged.
5. Filters and presets; built-in + user classification; UNKNOWN/conflict cases.
6. Session-only favorites; alerts default OFF, no false alert from cached "Eski" Wi-Fi.
7. Annotations, retention, single delete on expendable records only; no Clear All on real data.
8. WS05 groups: create/rename/add/remove, charts, comparison, persistence across restart.
9. **WS06 (mandatory): save CSV and JSON via the real picker; check headers, UTF-8/Unicode, UTC
   stamps, record counts vs app, default SSID/label/note exclusion, opt-in inclusion, cancel
   semantics, two-group comparison, and that export screens never start a scan.**
10. FieldDiag-04 hidden by default; seven-tap unlock works.
11. Honesty: if suitable radio signals are unavailable, record the scenario as unverified.
