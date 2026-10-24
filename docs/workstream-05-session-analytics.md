# Workstream 05 — Advanced Observation Session History, Local Analytics, Timeline Statistics and Session Comparison (2026-10-24)

Session-local analytical groups (user-created selections of ALREADY-SAVED History
records), a pure local analytics engine, deterministic time-bucket statistics and
two-group comparison on top of WS01–04, whose architectures are unchanged. The
prompt file said Git-OFF; the owner's live message explicitly declared that OFF
invalid and granted full Git authority for this iteration (the "new decision" the
prompt itself requires). Build Mode ON (DEBUG ONLY); versionCode NOT bumped.
Developer decision honored: NO visual redesign, NO cosmetic polish — only the
minimum functional Compose interfaces.

## 1. Session feasibility analysis (mandatory, verified in source)
The architecture distinguishes: A. individual BLE observation window · B.
individual passive Wi-Fi observation window · C. live UI observation session ·
D. user-created analytical grouping of saved records · E. a saved History record ·
F. a real physical device. These are NOT conflated: an analytical group (D) is NOT
a scanning session (A/B), NOT the live session (C) and NOT a device (F).
Verified: BLE/Wi-Fi sessions have independent statuses and may overlap
(RadioSessionUiState per radio). Saved HistoryRecords (verified fields: id = random
UUID generated at save time, deliberately NOT address-derived; savedAtEpochMs;
observedAtEpochMs; displayName; manufacturerHint; typeHint; signalBucket;
freshness; ageMs) contain NO trustworthy session identifier. Therefore:
- No session id was fabricated.
- No membership was inferred from nearby timestamps.
- No legacy record was silently assigned to a new group.
- Legacy records stay ungrouped until the user EXPLICITLY selects them.
- The UI states that a group is an analytical selection, never a historically
  verified scanning session (group_list_subtitle).
The trustworthy grouping approach used is exactly this explicit selection with
existing trusted record boundaries and existing recording consent — nothing else
was needed, and nothing was weakened.

## 2. Named analytical groups (core: history/SessionGroups.kt)
- AnalyticalGroup(id, name, recordIds, createdAtEpochMs, updatedAtEpochMs) —
  structural test asserts EXACTLY these five fields: no radio values, no addresses,
  no names-of-records, no fingerprints; ids are app-generated metadata ids
  ("G-0001", ...), never device identifiers.
- Limits: 50 groups (bounded, consistent with the 500-record convention), name
  <= 40 chars trimmed non-blank, schema "ws05-groups-v1".
- Repository ops are all EXPLICIT user actions with honest typed errors
  (NAME_BLANK, NAME_TOO_LONG, GROUP_LIMIT_REACHED, NOT_FOUND, RECORD_NOT_SAVED,
  ALREADY_MEMBER): create, rename, addRecord (rejects ids not in the current
  History snapshot — no fabricated references), removeRecord, delete (metadata
  ONLY: a combined delete-group-and-records action does NOT exist and would need
  separate authorization + confirmation), syncWithRecords (retention/deletion
  reconciliation, returns removed-reference count).
- Empty groups remain as named metadata with an honest empty state; they are
  never auto-deleted and never resurrect records.

## 3. Storage (app: SessionGroupFileStorage.kt)
- Separate versioned app-private file session_groups_v1.txt via the existing
  atomic temp+rename conventions (partial failure discards the temp, original
  preserved). Constructed ONCE in MainActivity.onCreate AFTER super.onCreate
  (WS02 startup hotfix contract; lateinit, no eager property initializer).
- Codec: versioned line format id|name|recordIds(,)|createdAt|updatedAt with
  escaping for '|', ',', '\n', '\r'. Unsupported version -> EMPTY, original
  NEVER rewritten. Corrupt/blank-name/bad-timestamp/duplicate-id/duplicate-ref/
  oversized lines dropped AND counted (lastLoadIssues). Over-cap files keep the
  first 50 by id. Missing file -> empty, no write. COMPLETELY separate from the
  History store, WS02 presets, WS03B catalog and WS04 annotations (tested).

## 4. Pure local analytics engine (core: analytics/SessionAnalytics.kt)
- Pure deterministic object over SAVED sanitized records: recordCount, BLE/Wi-Fi
  counts, source/signal/freshness distributions (exact counts + honest integer
  percents), manufacturer known/unknown counts, earliest/latest OBSERVATION
  receipt times, earliest/latest SAVE times (distinct provenance), and 6
  deterministic time buckets over observedAt.
- HONESTY: a record count is NOT a distinct-physical-device count (structural
  test asserts GroupStats has no device/distance/movement field). Freshness is
  carried VERBATIM from save time: a cached STALE ("Eski") Wi-Fi record stays
  STALE in analytics — never presented as newly discovered at save time. Invalid
  timestamps (<= 0) are EXCLUDED from earliest/latest and bucket arithmetic and
  REPORTED via invalidTimestampCount — never repaired, never interpolated; empty
  buckets stay zero (no fabricated trends). No metric implies distance, direction,
  geographic coverage or device movement; manufacturer hints are not identity.
- Comparison reuses the EXACT same stats() for both sides (tested: compare ==
  stats(left) vs stats(right)); it explains only counts/distributions/coverage and
  never correlates by MAC/BSSID/names/manufacturer/signature/timestamps/fingerprints.
  Fewer than two groups -> clear empty state (group_compare_need_two).

## 5. Consent, retention and deletion guarantees
- Creating/renaming/deleting a group or comparing groups dispatches NOTHING: no
  radio Start/Stop, no scan, no permission, no recording change (the UI layer has
  no such callback at all — structural).
- Recording consent stays OFF by default and untouched; Wi-Fi names consent
  independent; group creation NEVER saves live observations (live rows flow only
  through the existing consent-gated HistoryController path).
- Retention (7/14/30 days), the 500-record cap, individual deletion and confirmed
  Clear All all flow through syncWithRecords on every History snapshot change
  (LaunchedEffect in MainActivity, idempotent): dangling references are dropped,
  group statistics are recomputed from the CURRENT record set, deleted records
  are never resurrected (re-adding a deleted id is REJECTED as RECORD_NOT_SAVED).
- WS04 favorites/labels/notes remain fully intact (separate stores; tested L).

## 6. UI (functional-only, Geçmiş area, NO fifth tab)
Entry: "Analiz grupları" OutlinedButton in Geçmiş -> SessionGroupsScreen
(create/rename/delete with confirmations and honest error/notice states),
SessionGroupDetailScreen (per-group stats card, member list with Ayrıntılar to the
existing HistoryDetail, Çıkar, explicit Kayıt ekle section listing non-member
SAVED records), SessionGroupCompareScreen (two group pickers, side-by-side stats
cards, honesty note). Charts are deterministic Compose Boxes only: width bars for
distributions (count/max), height bars for the 6 time buckets — no animation, no
chart library, no new dependency. Textual summaries always accompany visuals.
Back policy: groups -> Geçmiş, detail/compare -> groups (AppNavigation updated).

## 7. Test matrix (JVM; 17 new core tests; 357/357 total)
SessionGroupsTest: A create/rename/delete + 50-cap · B explicit membership ·
C/D legacy ungrouped, no fabricated session (structural field assertion) ·
E/H metadata-only storage · F save/reload + deterministic ids · G corrupt +
unsupported schema fail closed without rewrite · I individual-deletion orphan
cleanup · J retention cleanup to empty-but-preserved group · K Clear All
consistency + re-add rejection · L store separation from annotations.
SessionAnalyticsTest: N distributions · O invalid-timestamp exclusion/report ·
P deterministic bounded buckets + degenerate span · Q cached STALE Wi-Fi stays
old (saved vs observed provenance) · R two-group comparison · S same engine for
detail and comparison (equality assertions) · T structural no-device-count
fields · U pure engine.
App suite 49/49 (navigation back policy updated but existing tests untouched and
passing). Host-unverifiable (explicit limitation): real Activity launch, touch
behavior and chart rendering remain Xiaomi-verifiable only; no ADB/emulator/
instrumentation was used. Developer-declined tests (2.0x font, light theme,
TalkBack) remain excluded per the explicit decision.

## 8. Fresh gates (all --no-daemon, tests/lint --rerun-tasks, exit 0)
:core:radio:test 357/357 (340 + 17) · :app:testDebugUnitTest 49/49 ·
:app:compileDebugKotlin · :app:assembleDebug · :app:lintDebug 0 errors/15
warnings (baseline kept: the two new UnusedResources findings on
group_member_missing/group_time_bucket_format were fixed by REMOVING the two
unused strings — nothing suppressed, nothing wired falsely).

## 9. Xiaomi acceptance checklist
Install over existing app (no uninstall, no data clear) -> cold start + reopen
without crashes -> old History (>200 records), presets, signature rules and
WS04 annotations intact -> Geçmiş: "Analiz grupları" -> create + name a group ->
associate selected existing records (Kayıt ekle) -> reopen/rename -> verify BLE/
Wi-Fi counts, signal/freshness distributions, coverage times and the 6-bucket
chart -> create a second group and compare both (honest note: record counts are
not device counts) -> delete a History record: statistics update and the group
loses the reference -> delete a group with confirmation: unrelated History
records remain -> restart: surviving group metadata persists -> retention and
Clear All never resurrect records -> opening any analytics screen NEVER initiates
scanning -> Liste/Radar/Zaman tüneli/süzgeçler/favoriler/uyarılar/kataloglar and
hidden FieldDiag-04 (gizli + 7 dokunuş) remain unchanged.

## 10. Limitations (honest)
- Analytics describe SAVED RECORDS only — they can never claim distinct physical
  devices, movement or environment size.
- Legacy records have no session link: groups of them are selections, not
  historical scanning sessions (the UI says exactly this).
- Time buckets use observation receipt times with second-level precision; exact
  scan-window provenance for legacy records is unknowable and is not invented.
- Alerts/engine, scanning and radio contracts untouched; no export (WS06), no
  tracking (WS07), no UI/UX redesign — all deferred.
