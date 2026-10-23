# Optional Local Observation History — Implementation Record (2026-10-23)

Post-roadmap, separately developer-approved feature. NOT a new roadmap phase; no
completed task was reopened. Design source: docs/optional-local-history-design.md
(developer-approved). Status: implemented and host-verified; REAL-DEVICE
PERSISTENCE UNVERIFIED until the developer retests the APK on the phone.

## Architecture decisions

- Strict JVM/Android separation preserved. ALL policy logic is pure JVM in
  `core/radio/.../history/` (no android.* imports): HistoryModels, HistoryCodec,
  HistoryPersistence (seam), LiveRowTracker, ObservationHistory.
- App layer (`app/.../history/`): HistoryFileStorage (app-private file, atomic
  tmp+rename writes, IOException/SecurityException swallowed best-effort) and
  HistoryController (single-thread executor serializes every operation;
  main-handler publishes immutable UiState to Compose).
- Storage choice: existing mechanisms only — plain java.io in the app's private
  filesDir (`radio_history_v1.txt`). NO new dependency (protocol: no new database
  library without separate authorization). allowBackup="false" (pre-existing
  manifest) already excludes the store from Android/cloud backups.

## Data minimization — exact persisted fields

Per record: random UUID (generated at save time, never address-derived), radio
type, saved-at wall clock, observed-at wall clock, safe display label, manufacturer
hint, type hint, conservative SignalBucket category, freshness provenance (verbatim
at save time — a saved row can never be shown as "fresh"), age provenance.
NEVER persisted: BLE MAC addresses, Wi-Fi BSSIDs, advertising payloads, raw
ScanResults, exact device-location info, stable cross-session identifiers, debug
diagnostics, tracking fingerprints, private exception details, raw RSSI (only the
bucket; RSSI is never presented as a distance).
Wi-Fi SSIDs reveal location context → stored ONLY under a SEPARATE opt-in
(wifiNamesEnabled, OFF by default); BSSID is never stored under any option.

## Consent behavior

- Recording OFF by default; fresh install persists nothing.
- Enabling requires an explicit user toggle on the History screen with a clear
  Turkish consent explanation (see strings: history_consent_body).
- Enabling starts nothing: no scanning, no permissions, no lifecycle eligibility
  change, no backfill (the tracker SEEDS current live rows at enable time — only
  post-consent observations are saved).
- Disabling stops future writes immediately (consent re-checked at EXECUTION time
  for every queued write) and deletes NOTHING: old records remain for explicit
  deletion or automatic expiry — explained in the UI copy.

## Retention, expiry and capacity

- Default 7 days, user-configurable to 14 or 30 (hard maximum 30). Retention is
  pruned deterministically on every load, save AND read — NO background scheduling.
- Capacity hard limit 500: oldest-saved evicted first (deterministic).
- Whole-store rewrite per mutation (tiny store, atomic, race-free under the single
  executor).

## Deletion & races

- Per-record delete, and Clear All behind an explicit confirmation dialog.
- Deletion is inert for live observation: an active BLE/Wi-Fi session is never stopped.
- No queued-write resurrection: the single executor serializes write/clear; the
  tracker keeps already-saved live rows seen after Clear All; consent is re-read at
  execution time, so a queued write from the enabled era is dropped once recording
  is disabled.
- Corrupt individual lines: dropped and honestly reported (one-time UI notice);
  a fully unreadable store fails CLOSED to privacy (everything dropped, consent
  reset to safe defaults, store rewritten clean).
- Process termination: atomic tmp+rename writes; codec fails closed; consent and
  records survive app restarts via the store (JVM-proven round-trip).

## History UI (Turkish, Material 3, a11y)

History entry button on the control screen → History screen: recording ON/OFF
switch with consent copy, separate Wi-Fi-names switch, retention chips (7/14/30
gün), saved-record count, ALL/Bluetooth/Wi-Fi filter chips, list with saved
date-time, per-record delete + detail, Clear All with confirmation, empty state,
48dp touch targets, heading semantics, scrollable, corrupt-store notices.
History detail screen shows the exact stored fields. Works with NO active session.

## Privacy copy

privacy_body updated to distinguish (A) temporary in-memory live observations from
(B) optional locally persisted history, stating: OFF by default, consent required,
stays on this device, no upload/sharing/backup, deletable, disabling ≠ deleting.
DEBUG diagnostics (FieldDiag) remain entirely out of persistent history.

## Guarantees preserved (verified by the existing suites)

Explicit manual Start, two-tap permission policy, foreground-only observation, no
automatic scanning, BLE/Wi-Fi independence, bounded sessions, passive Wi-Fi (no
startScan anywhere), existing pipeline retention, transient session cleanup.
The history path holds NO reference to any coordinator/supervisor/pipeline API.

## Test evidence (device-free, this run)

- Core: 266/266 (24 new: HistoryCodecTest 6, LiveRowTrackerTest 5,
  ObservationHistoryTest 13) covering A (default OFF, zero writes), B (future-only),
  C (disable keeps old records), D (Wi-Fi-name separate consent), E (no raw
  identifiers), F (duplicate callbacks once), G (survives recreation), H (no
  live-injection / no auto-restore), I (7-day + 30-day cap), J (deterministic 500),
  K (single delete), L (clear all), M (no resurrection after clear), N (corruption
  fail-closed), O (consent-only inertness).
- App: 6/6 (2 new: full live→history bridge path, policy boundaries).
- Commands: :core:radio:test, :app:testDebugUnitTest (both --rerun-tasks
  --no-daemon), :app:compileDebugKotlin, :app:assembleDebug, :app:lintDebug
  (--rerun-tasks --no-daemon; 0 errors, 16 warnings — unchanged baseline count).
- APK: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1,
  23622347 bytes, SHA-256
  a3f0f14a5df0bd740f786db1c177acb18c53e208acc1661e3822bdd8cf92fa74.
  history strings + store filename confirmed inside the DEX.

## Remaining real-device verification (Xiaomi manual test)

1. Install the new APK (remove the old one first).
2. Open Geçmiş from the home screen: recording must be OFF, list empty, count 0.
3. Run a BLE + Wi-Fi session WITHOUT enabling history → restart the app → list
   still empty (default OFF on a real device).
4. Enable recording → start Wi-Fi → cached access points must appear in Geçmiş
   as saved records (only rows appearing AFTER enable).
5. Kill the app from Recents → reopen Geçmiş → records must still be there.
6. Toggle recording OFF → observe more rows → no NEW records; old ones remain.
7. Enable Wi-Fi-names opt-in → new Wi-Fi rows must show names; earlier Wi-Fi
   rows stay name-less. Verify a saved record's detail never contains a BSSID/MAC.
8. Per-record delete, then Clear All with confirmation → count 0; active session
   unaffected. Set retention 14/30 days; verify the chips respond.
9. Android settings → verify the store file is NOT in any cloud backup.
