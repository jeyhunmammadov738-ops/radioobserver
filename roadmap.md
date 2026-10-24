# Radio Observer — Roadmap

Authoritative task list. Phase 1 is complete. Phase 2 is AUTHORIZED (2026-10-19) in the
documented order, with implementation granted per bounded iteration; this iteration
implemented 2.1, 2.2 and 2.5 ONLY. Phase 3 remains NOT AUTHORIZED. Historical evidence is
preserved; entries are never reordered or silently restructured. A task may only be checked
with evidence from the current run recorded inline.

## Phase 1 — Architecture Foundation & Pure JVM Parsers (complete)

- [x] **1.1** Project scaffolding and host-only Gradle compile validation
  - Evidence (2026-10-18 run): two-module Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.4.0 project
    (`:app` minimal Compose entry, `:core:radio` pure JVM). Host command
    `./gradlew :app:compileDebugKotlin --no-daemon` → BUILD SUCCESSFUL (exit 0).
    applicationId `com.example.radioobserver` is PROVISIONAL (needs approval before release).
    No permissions declared; no version bump.

- [x] **1.2** Domain models for BLE advertisements and Wi-Fi access-point observations
  - Evidence: `core/radio/.../model/RadioModels.kt` — canonical `ObservedRadioDevice`
    (single model name used everywhere), `RadioType { BLE, WIFI }`,
    `ObservationConfidence { UNKNOWN, POSSIBLE, CANDIDATE, VALIDATED_FORMAT }`,
    `ManufacturerInfo` / `DeviceTypeCandidate` with mandatory evidence lists, epoch-ms
    observation timestamps, locally-administered address flag, source-specific fields.
    Randomized BLE addresses/BSSIDs explicitly modeled as NOT stable identity.

- [x] **1.3** Pure Kotlin BLE manufacturer/beacon and conservative Wi-Fi classification parsers
  - Evidence: `parser/BleAdvertisementParser.kt` (AD-structure walker; little-endian company
    ids; Android-extracted `ManufacturerData` input consumes the company id verbatim without
    re-parsing) and `parser/BleDeviceClassifier.kt` (0x004C Apple / 0x0075 Samsung;
    iBeacon layout = VALIDATED_FORMAT; Find My separated layout = CANDIDATE only, wrong
    length = POSSIBLE; Samsung never produces a SmartTag claim; unknown vendor fallback).
    `wifi/WifiObservationClassifier.kt` (infrastructure APs only; SSID never proves make/model;
    locally-administered BSSID → vendor stays Unknown; OUI hints caller-supplied only).
    Zero `android.*` imports in the module. Verified by BleDeviceClassifierTest (9 tests) and
    WifiObservationClassifierTest (8 tests), all passing this run.

- [x] **1.4** RSSI signal-bucket / heuristic proximity utilities
  - Evidence: `signal/SignalBuckets.kt` — deterministic IMMEDIATE/NEAR/FAR/UNAVAILABLE
    buckets, configurable `RssiThresholds` (defaults -50/-70 dBm), inclusive boundary
    behavior, inverted-config rejection, implausible/null RSSI → UNAVAILABLE (never
    fabricated). Verified by SignalBucketsTest (5 tests incl. exact threshold edges), passing.

- [x] **1.5** Device-free JVM parser and state-model regression tests
  - Evidence (2026-10-18 run): `./gradlew :core:radio:test --no-daemon` → BUILD SUCCESSFUL.
    Result: 44 tests completed, 44 passed, 0 failed, 0 skipped across 6 test classes
    (BLE parser, BLE classifier, signal buckets, Wi-Fi classifier, observation store,
    observation factory). Fixtures cover 0x004C / 0x0075 / unknown vendor ids, Find My
    candidate + bogus lookalike + length-mismatch cases, malformed/truncated/empty/oversized
    AD frames, RSSI threshold edges and unavailable values, randomized BSSID cases, and
    dedup/stale/out-of-order state behavior.

## Phase 2 — Scanner Abstractions & Permission Flow Models (complete at code/host level; 8/8 done — 2.8 re-verified after lint remediation)

Phase 2 tasks approved by the developer 2026-10-19, in the order of
[docs/phase2-proposal.md](docs/phase2-proposal.md). This iteration implemented
2.1, 2.2, and 2.5 ONLY; 2.3, 2.4, 2.6, 2.7, 2.8 remain unchecked and unimplemented.
Approved policy: BLE `neverForLocation` NOT asserted (coverage prioritized; fine-location
implications disclosed); Wi-Fi receive-only (no `WifiManager.startScan()`); background
operation OFF; no manifest permission additions yet.

- [x] **2.1** Platform and permission capability matrix
  - Evidence (2026-10-19): `docs/permission-capability-matrix.md` written from the official
    Android Developers docs (bt-permissions, wifi-scan, wifi-permissions; inspected this
    run). Covers API 29–30 / 31–32 / 33+ buckets, separate BLE vs Wi-Fi `getScanResults()`
    requirement paths, radio/toggle/hardware/denial states, cached Wi-Fi results,
    user-disclosure obligations, and the approved neverForLocation=NO and background=OFF
    decisions. Real-device enforcement behavior remains explicitly UNVERIFIED.

- [x] **2.2** Android boundary contracts and state models
  - Evidence (2026-10-19): `core/radio/.../boundary/ObservationContracts.kt` — pure
    Kotlin/JVM (zero `android.*` imports, verified), bounded `ObservationSessionSpec` with
    a hard 10-minute ceiling, typed `ObservationError`/`ObservationEvent`/`SessionEndReason`,
    `BleObservationSource` + `WifiObservationSource` contracts with a single-session guard,
    cancellable handles, and deliberately NO scan-trigger/pairing/connect API surface.
    JVM fakes + 14 BoundaryContractsTest tests cover start/stop/cancel, single-session
    refusal, permission/hardware refusals, duration elapsed, stale Wi-Fi delivery with
    unknown-age modeling, and post-failure session end. All passing this run.

- [x] **2.3** BLE observation adapter (time-bounded, permission-gated `BluetoothLeScanner`
      callback adapter)
  - Evidence (2026-10-19): pure-JVM `BleSessionCoordinator` (`:core:radio`, zero android.*
    imports) owns ALL session logic: gate order = single-session -> caller-supplied
    foreground eligibility (DEFAULT FALSE — nothing can scan until a later UI task wires a
    real signal) -> real-check readiness snapshot evaluated by the 2.5 model -> engine start.
    Bounded spec (hard 10 min ceiling, conservative 20 s default), idempotent cancel/stop,
    stop on cancel/timeout/failure/foreground-loss, NO restart loop, late callbacks ignored,
    dedup via Phase 1 ObservationStore newest-wins, no raw-address/payload logging.
    Thin Android shell in `:app`: `AndroidBleScanEngine` (BluetoothLeScanner + ScanCallback;
    typed mapping of platform failure codes 1-6, SecurityException -> typed refusal,
    batch/defensive parsing, arrival wall-clock timestamps — timestampNanos never
    misrepresented as epoch), `AndroidBleCapabilityProbe` (REAL checkSelfPermission /
    adapter / Location-mode checks at the boundary; permanent-denial only caller-injected),
    `AndroidBleObservationSource` (foreground default ineligible; scan-only: no
    pair/connect/advertise/GATT/Wi-Fi/network/persistence). Manifest: BLUETOOTH_SCAN (31+),
    legacy BLUETOOTH/BLUETOOTH_ADMIN maxSdk=30, ACCESS_FINE_LOCATION (no neverForLocation —
    coverage priority, honest proximity disclosure); verified in the merged manifest and
    final APK. No runtime permission dialog code. Host evidence: 18 new
    BleSessionCoordinatorTest cases; total 90/90 JVM tests green; :app:compileDebugKotlin
    and :app:assembleDebug successful. Real-device scan reception, permission dialogs,
    OS throttling, screen-off behavior, OEM differences: UNVERIFIED (device-free protocol).
- [x] **2.4** Wi-Fi system scan-result observer (`SCAN_RESULTS_AVAILABLE_ACTION` passive
      listener, permission-gated `getScanResults()`, freshness markers)
  - Evidence (2026-10-19): pure-JVM `WifiSessionCoordinator` (`:core:radio`, zero android.*)
    — STRICTLY receive-only: no method anywhere on the engine/fetcher boundary can trigger a
    scan (source-inspected; `WifiManager.startScan()`/`WifiScanner` never appear). Gates
    identical to BLE (single-session -> foreground default false -> 2.5 readiness -> receiver
    registration) and RE-EVALUATED on every broadcast: permission/foreground loss fails closed
    typed, session ends, no retry. Freshness honesty: platform timestamp (µs since boot)
    preserved verbatim + boot-clock age (negative/future/implausible -> unknown), receipt
    wall-clock kept separate, updated=false/missing delivered as NOT-new; dedup via
    ObservationStore newest-wins. Thin Android shell (`:app` wifi/): context-registered,
    session-scoped receiver with RECEIVER_NOT_EXPORTED (targetSdk 34+ mandatory; blocks
    spoofed non-system sends; protected system broadcast stays deliverable), main thread never
    blocked (single-thread executor, ordering preserved), `AndroidWifiResultFetcher` reads
    ONLY `getScanResults()` (SecurityException -> typed PermissionDenied), capability probe
    with REAL checks (ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION; NEARBY_WIFI_DEVICES not
    adopted). Manifest: ACCESS_WIFI_STATE added only (BLE set intact; no CHANGE_WIFI_STATE/
    INTERNET/NEARBY_WIFI_DEVICES); verified in merged manifest + final APK. Session model
    unchanged: per-radio single-session; one BLE and one Wi-Fi session may coexist. Host
    evidence: 21 new WifiSessionCoordinatorTest cases; total 111/111 JVM tests green;
    :app:compileDebugKotlin and :app:assembleDebug successful. Real-device broadcast delivery,
    OEM cadence, permission UX: UNVERIFIED (device-free protocol).
- [x] **2.6** Lifecycle, battery and background policy (stop/cancel rules, restriction
      matrix, FGS evaluation record)
  - Evidence (2026-10-19): pure-JVM `RadioObservationSupervisor` (`:core:radio`, zero
    android.* imports) — RESUMED-ONLY eligibility (default FALSE; process liveness/permission
    grants never substitute), NO auto-start on resume/recreation, loss of RESUMED is
    TERMINAL for both sessions and stops them (FOREGROUND_LOST via additive
    `ForegroundLossAwareSource`; handle-cancel fallback for sources without it), onStop/
    onDestroy repeat cleanup idempotently, sessions independent (cancel-one spares the
    other; foreground loss stops both; 1 BLE + 1 Wi-Fi coexistence unchanged), existing
    10-min ceiling / 20-s BLE / 30-s Wi-Fi bounds and guard ordering preserved. Decision
    record: `docs/phase2-lifecycle-battery-policy.md` (transition table, battery/background
    hard rules incl. no FGS/boot/wakelock/WorkManager/alarms, multi-window trade-off,
    process-death limits, official Android doc citations). :app bridge:
    `ObservationLifecycleBridge` (DefaultLifecycleObserver, one per Activity, no Activity
    refs outliving it) + MainActivity wiring (sources constructed INERT — no session starts
    anywhere; explicit start awaits Phase 3 UI). Manifest unchanged (verified in final APK:
    permission set identical, no new service/receiver/FGS). Host evidence: 14 new
    RadioObservationSupervisorTest cases (eligibility default false, no auto-start,
    pause-stops-both, idempotent stop/destroy, no revival, independence, timeout race,
    plain-source fallback, recreation starts ineligible); total 125/125 JVM green;
    :app:compileDebugKotlin, :app:assembleDebug successful. Android framework callback
    delivery on a real device, OEM lifecycle behavior: UNVERIFIED (device-free protocol);
    the :app bridge is compile-verified only.

- [x] **2.5** Permission and capability state model
  - Evidence (2026-10-19): `core/radio/.../permissions/ObservationReadinessModel.kt` —
    pure Kotlin/JVM decision engine over immutable `CapabilitySnapshot` (API bucket,
    hardware presence, radio toggle, Location services, permission states). Encodes the
    2.1 matrix: BLE legacy (29–30) needs BLUETOOTH/BLUETOOTH_ADMIN/ACCESS_FINE_LOCATION;
    31+ needs BLUETOOTH_SCAN + ACCESS_FINE_LOCATION (neverForLocation NOT asserted);
    Wi-Fi needs ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION on all buckets (NEARBY_WIFI_DEVICES
    deliberately not adopted for `getScanResults()`). Denied vs permanently-denied are
    distinct blockers; multiple blockers accumulate; honest disclosure set emitted with
    Ready. Table-driven: 14 ObservationReadinessModelTest tests all passing this run.
    No manifest change, no runtime permission request code (out of this iteration).

- [x] **2.7** Observation pipeline and privacy controls
  - Evidence (2026-10-19): pure-JVM pipeline package in `:core:radio` (zero android.*
    imports, `:app` untouched): `ObservationPipeline` (bounded volatile retention — 200
    entries default, deterministic oldest-first eviction, lazy 15-min TTL sweep with NO
    timers/background work; newest-wins dedup via Phase 1 ObservationStore + additive
    remove/keys; terminal `closeAndDispose` — late events fail closed, no reopen, volatile
    identifiers dropped), `ObservationFreshness` (FRESH/STALE/UNKNOWN; Wi-Fi age ONLY
    boot-relative resultAgeMs else UNKNOWN — receipt clock never substitutes data age;
    cached updated=false results can NEVER be FRESH; negative/future/overflow UNKNOWN),
    `PublicObservation` (privacy-safe projection: raw addresses/BSSIDs/payloads/issue
    texts structurally unreachable; maskedAddress helper; per-pipeline ordinal
    observationId with NO address/hash-derived material), `PipelineObservationListener`
    (feeds DeviceObserved/WifiResultsAvailable only; one radio's session end never
    corrupts the other's data; NOT wired to any production flow yet — explicit start UI
    is Phase 3). Additive `ObservationStore.remove/keys` (semantics unchanged). Decision
    record: `docs/phase2-observation-privacy-policy.md` (dataflow, identifiers, masking,
    capacity/TTL, disposal, confidence limits, unverified-device caveats). No new
    permissions; manifest/APK permission set verified unchanged; no file/network/log path
    (source audited). Host evidence: 18 new ObservationPipelineTest cases; total 143/143
    JVM green; :app:compileDebugKotlin and :app:assembleDebug successful (app sources
    untouched). Real radio reception, broadcast cadence and Android runtime: UNVERIFIED
    (device-free protocol).
- [x] **2.8** Device-free quality gate and phase handoff
  - CORRECTION LOG (transparent, records preserved): the 2026-10-19 run prematurely ticked
    2.8 while `:app:lintDebug` exited 1 (`CoarseFineLocation`). Reopened 2026-10-20;
    remediation authorized in the same iteration: `ACCESS_COARSE_LOCATION` declared
    alongside `ACCESS_FINE_LOCATION` (only change; no tools:ignore/baseline/severity
    downgrade; no other permissions added; fail-closed probes unchanged — a COARSE-only
    grant still does NOT satisfy BLE scan / getScanResults()).
  - Fresh full-gate evidence on the remediated tree (2026-10-20):
    `:core:radio:test --rerun-tasks --no-daemon` → 143/143, 0 failed, 0 skipped;
    `:app:compileDebugKotlin --no-daemon` → EXIT 0; `:app:assembleDebug --no-daemon` →
    EXIT 0; `:app:lintDebug --rerun-tasks --no-daemon` → EXIT 0, 0 errors, 11
    informational warnings (2x AndroidGradlePluginVersion, 3x GradleDependency, 2x
    NewerVersionAvailable, 1x InlinedApi [BLUETOOTH_SCAN constant, runtime-guarded],
    1x OldTargetApi, 1x MissingApplicationIcon, 1x DataExtractionRules) — itemized in
    docs/phase2-handover.md §5. APK verified: applicationId com.hjinlabs.radioobserver
    unchanged; permission set now includes ACCESS_COARSE_LOCATION; no new
    services/receivers/FGS. One transient anomaly documented: an intermediate non-rerun
    lint invocation replayed a stale cached failure; the forced fresh run is the evidence.
  - docs/phase2-handover.md updated (§2 gate results, §3 permission set, §5 resolved open
    item). Phase 2 complete AT CODE/HOST-VERIFICATION LEVEL ONLY; real-device reception,
    permission UX (incl. approximate-only grant handling), OEM behavior: UNVERIFIED.
    Phase 3 remains not started.
  - Evidence (2026-10-19): independent audit of 2.1-2.7 against documented acceptance
    criteria (evidence matrix in docs/phase2-handover.md §1): receive-only Wi-Fi confirmed
    (zero WifiManager.startScan/WifiScanner), 10-min ceiling / 20-s BLE / 30-s Wi-Fi
    defaults intact, RESUMED-only lifecycle policy intact, privacy pipeline bounds intact
    (200 entries / 15-min lazy TTL / no raw identifiers in public projection), no new
    permissions/components/persistence/network (source + APK scanned). Fresh quality gate
    on final tree (HEAD b793503): :core:radio:test --rerun-tasks -> 143/143, 0 failed,
    0 skipped; :app:compileDebugKotlin OK; :app:assembleDebug OK; :app:lintDebug -> 1
    pre-existing error (CoarseFineLocation: FINE-only is the deliberate 2.1 policy; adding
    COARSE would be a new permission, prohibited in 2.8 — recorded as an open developer
    decision, docs/phase2-handover.md §5) + 11 informational warnings. Handover document
    created: docs/phase2-handover.md. Phase 2 marked complete AT CODE/HOST-VERIFICATION
    LEVEL ONLY; real-device reception, permission UX, OEM behavior: UNVERIFIED. Phase 3
    remains not started.

Current focus: none — Phases 1-3 complete (5/5, 8/8, 10/10). Any further work requires a new authorized roadmap.

Post-handover field finding (2026-10-23): first real-phone test (MIUI) surfaced a
truthfulness defect — transient foreground-gate rejection of a Start tap rendered a
permanent-looking dead end ("Kullanılamıyor" + "Ekrandan ayrıldığınız için
durduruldu") though nothing was ever observed. Narrow fix: typed
SessionIssueKey.NOT_ELIGIBLE with truthful IDLE status + retry hint; real terminal
history preserved; 4 new JVM tests. Fresh gates: 227/227, lint 0 errors/15
warnings, APK identity unchanged. Details: docs/field-test-blocked-observation-2026-10-23.md.
Roadmap checkboxes NOT reopened; real-device re-test pending.

Second field iteration (2026-10-23, later the same day): a second real-phone
screenshot showed the transient retry hint itself LATCHED as current state
("Etkin değil" + red start-failed copy on both cards) while the app was fully
foreground — a stale failed-start latch after a genuine foreground return.
Narrow fix: orchestrator onForegroundEligibilityChanged clears latched
IDLE+NOT_ELIGIBLE hints on genuine re-eligibility (real history preserved);
DEBUG-only privacy-safe RadioObserverDiag breadcrumbs added for the next field
log. New tests R1-R4; gates 231/231, lint 0 errors/15 warnings. Details:
docs/field-test-start-permission-2026-10-23.md. The underlying tap-time trigger
remains unproven from filtered logs; diagnostics will settle it. Checkboxes
still NOT reopened.

Third field iteration (2026-10-23 evening): the 15:12 Xiaomi retest still showed
both cards IDLE with the red start-failed hint; the system log contains no
package-replacement event, so which APK was actually installed is unestablished.
A third full source audit found no reproducible divergence: both eligibility
flags are live reads updated in the same supervisor methods, in the same order.
Decision per field protocol: DEBUG-only on-screen FieldDiag-03 diagnostics panel
(collapsed "Tanılama" disclosure, bounded in-RAM chronology, live typed booleans,
no ADB needed) so the next retest yields decisive in-app evidence and proves the
installed build identity. 3 new app-layer JVM tests (core 231/231 unchanged).
Fresh gates: app 3/3, compile/assemble OK, lint 0 errors/15 warnings. Details:
docs/field-test-start-permission-2026-10-23.md (third section). On-device
mechanism remains UNVERIFIED pending the panel retest.

Fourth field iteration (2026-10-23, night): the first DECISIVE in-app evidence
arrived via the FieldDiag-03 panel — both Start taps reached the app with all
tap-time facts true (eligible, grants, Ready) yet both attempts returned Blocked
while sessions stayed IDLE. A fourth full audit enumerated every Blocked producer;
the trace cannot name the failing guard because the FieldDiag-03 start_attempt row
omitted the typed issue/stage, so no behavioral fix was applied (no proven defect;
stale-hint workaround NOT repeated). FieldDiag-04 added: typed decision stage
(ORCHESTRATOR_GATE vs SOURCE_REFUSAL) on StartAttempt.Blocked, typed issue + stage
+ sourceCalled/accepted in the start_attempt rows, anonymous per-radio attempt
numbers, activity_recreated timeline marker, 40-row ring with strictly increasing
sequence IDs and seconds-based monotonic elapsed. 3 new orchestrator stage tests
(core 234/234) and updated ring tests (app 4/4); compile/assemble OK; lint 0
errors/15 warnings. Details: docs/field-test-start-permission-2026-10-23.md
(fourth section). Root cause remains UNPROVEN pending the FieldDiag-04 retest.

Fifth field iteration (2026-10-23, night): FieldDiag-04 named the guard —
stage=SOURCE_REFUSAL, issue=NOT_ELIGIBLE with every caller-side fact true. Root
cause PROVEN: Kotlin trailing-lambda binding sent both sources' foreground lambdas
to permanentDenialProvider, leaving foregroundEligibility at its silent { false }
default, so both source re-gates always refused ForegroundIneligible. Fixed with
named arguments; foregroundEligibility made a REQUIRED constructor parameter
(misbinding can no longer compile); 3 new core wiring-regression tests (core
237/237, app 4/4, compile/assemble OK, lint 0 errors/15 warnings). Real-device
repair remains UNVERIFIED until the phone retest shows the radios entering
OBSERVING.

Sixth field iteration (2026-10-23, night): the root-cause-fix build let BOTH
radios enter Observing on the phone (BLE delivered rows; the FieldDiag-04
wiring defect is closed pending no regression in the next retest), but the
30-second passive Wi-Fi session showed zero entries. Proven gap: the
coordinator read getScanResults ONLY on scan-results broadcasts, so zero
results was expected whenever no system/other-app scan occurred in the window.
Fix: ONE guarded cached-result read on explicit gated Wi-Fi Start (API 29+
cached results; never startScan), delivered honestly as NOT-new with per-record
age; empty/failed read never stops the session. Narrow DEBUG-only wifi_evidence
rows (raw count, broadcast flag, receiver registration) added for the next
field check. 4 new coordinator tests, existing tests consciously updated for
the intentional behavior change (core 241/241, app 4/4, compile/assemble OK,
lint 0 errors/15 warnings). Separately, a DESIGN-ONLY privacy proposal for
optional local history was written (docs/optional-local-history-design.md);
nothing persistent was implemented. Details: docs/field-test-start-permission-2026-10-23.md (sixth section).

Seventh field iteration (2026-10-23, night): the phone PROVED the initial cached
read fetched 13 raw results yet the feed showed zero Wi-Fi rows. Root cause: the
read ran synchronously inside startObservation, before the orchestrator
published OBSERVING and created the pipeline, so its live-guard (and the null
pipeline) silently discarded all 13. Fixed by deferring the read through the
session scheduler (main-looper post) so it runs exactly like a broadcast;
best-effort semantics kept (empty/failed read never stops the session; gate
loss still fails closed). New integration test P1 wires the real coordinator +
supervisor + orchestrator and proves cached rows survive into uiState.rows
(core 242/242, app 4/4, compile/assemble OK, lint 0 errors/15 warnings).
Real-device repair UNVERIFIED until the retest shows the cached rows in the
feed. Details: docs/field-test-start-permission-2026-10-23.md (seventh
section).

POST-ROADMAP, SEPARATELY APPROVED FEATURE (2026-10-23): Optional Local Observation
History (docs/optional-local-history-design.md) implemented as a post-roadmap
addition — no phase created, no completed task reopened. Pure-JVM history package
(minimal sanitized HistoryRecord; codec with fail-closed corruption handling;
consent re-checked at execution time; 7/14/30-day retention hard-capped at 30;
deterministic 500-record capacity) + app-private atomic file store (allowBackup
already false; no new dependency) + serialized HistoryController bridge + Turkish
Material 3 History/detail screens (recording OFF by default; separate Wi-Fi-names
consent; filters; per-record delete; confirmed Clear All; count; retention chips).
Host-verified this run: core 266/266 (24 new), app 6/6 (2 new), compile OK,
assembleDebug OK, lintDebug 0 errors/16 warnings. Real-device persistence
UNVERIFIED until retest. Details: docs/optional-local-history-implementation.md.

POST-ROADMAP, SEPARATELY APPROVED UI ITERATION (2026-10-23): Professional Material 3
redesign + four-tab bottom navigation (Gözlem, Gözlemler, Geçmiş, Ayarlar) + hidden
FieldDiag-04 (docs/professional-ui-navigation-redesign.md). No phase created, no task
reopened. New indigo/violet light+dark theme (no new dependency); pure JVM-tested
navigation/back policy (AppNavigationTest 9) and DEBUG-only diag-visibility gate
(DeveloperDiagGateTest 8; seven version-label taps, hidden by default, nothing
persisted); Geçmiş became browse-only with ALL history preferences moved to Ayarlar
(same HistoryController state); dashboard compacted with a collapsed info disclosure;
feed rows grouped with tonal source/freshness badges; detail screens grouped into
sections; four dead string resources removed. Core 266/266, app 23/23, compile OK,
assembleDebug OK, lintDebug 0 errors/16 warnings (exact baseline). Phone retest
required. Details: docs/professional-ui-navigation-redesign.md.

POST-ROADMAP REFINEMENT 2 (2026-10-23): Premium UI/UX V2 (developer-approved;
docs/premium-ui-ux-v2.md). One shared Material 3 design system (DesignSystem.kt:
4/8dp Spacing tokens, StatusPill/SectionHeader/CompactInfoRow/SourceIcon/EmptyState,
pure PillStyle + DashboardSummary with new host tests) applied to all surfaces:
dashboard summary strip + refined status-first source cards; denser honest feed rows
with freshness pills; compact history rows with explicit two-action footer; grouped
Ayarlar with short summaries + expandable full consent copy; unified detail design
language (black sub-panels removed) and human-friendly Turkish record age
("yaklaşık 47 dakika"). No behavior/permission/scanning change; history settings
reuse one controller. Core 266/266, app 35/35, lint 0 errors/15 warnings, DEBUG APK
only. Phone retest required.

POST-ROADMAP POLISH (2026-10-23): Premium UI/UX V2 final polish
(developer-approved; docs/premium-ui-ux-v2.md addendum). (1) Compact history record
rows (tightened 4/8dp rhythm, slimmed but 48dp actions, name-scoped delete/detail
a11y; card tap never deletes). (2) Turkish presentation of the classifier's English
vendor sentinels via the pure ManufacturerPresentation boundary ("Üretici
belirlenemedi" + locally-administered explanation; real OUI hints unchanged; core
classifier untouched) and recorded-time signal semantics on the history detail.
(3) Adaptive FlowRow Start/Stop for 320dp/2.0x. No behavior/permission/manifest
change. Core 266/266, app 39/39, lint 0 errors/15 warnings. DEBUG APK only. Phone
retest required.

POST-ROADMAP MICRO-POLISH (2026-10-23): (A) history rows: meta + Ayrıntılar/Sil
merged into one FlowRow band (48dp targets kept; card tap never deletes). (B) BLE
"Unknown manufacturer (id 0xNNNN)" sentinel localized at the presentation boundary
(Üretici belirlenemedi + separate Üretici kodu context; verified hints unchanged;
core classifier untouched). (C) live-detail last-seen label/value duplication removed
(pure DetailTime) and duplicated signal caveat deduplicated (distinct recorded-time
and privacy warnings preserved). New tests: ManufacturerBleSentinelTest (4),
DetailTimeTest (2). Core 266/266, app 45/45, lint 0 errors/15 warnings. DEBUG APK
only. Phone retest required.

POST-ROADMAP WORKSTREAM 01 (2026-10-23, developer-approved): live observation views
(docs/workstream-01-live-views.md). One pure projection source
(core LiveViewProjection) drives three switchable Gözlemler presentations — Liste
(unchanged feed), Radar (abstract signal-category rings; deterministic rowId-derived
visual placement; mandatory 'yön veya mesafe göstermez' copy; bounded canvas with
honest overflow; adjacent accessible list), Zaman tüneli (session-local last-seen
chronology; no fabricated first-seen; stable rowId tie-break). Same sanitized state,
same empty taxonomy, same detail navigation; view choice is non-persisted UI state
dispatching nothing. 12 new core regressions (278/278 core, 45/45 app, lint 0/15).
DEBUG APK only. Phone retest required.

POST-ROADMAP QA (2026-10-23): Xiaomi radar visibility audit — defect CONFIRMED and
fixed. Root cause: consecutive session ordinals ("obs-1"…) have String hashes
differing by 1, so the linear angle formula stacked every dot at effectively the
same point (11 records, ~3 visible). Fix: golden-ratio angle mix (~132.7° spread for
consecutive ordinals), decorrelated radius jitter, and explicit count-bearing
clusters for residual visual merges (accounting invariant singles + cluster counts +
overflow == source rows, host-tested; members reachable in the adjoining list).
Also fixed: radar projection now honors the CURRENT source filter (was hard-wired
ALL). 4 new core regressions (282/282 core, 45/45 app, lint 0/15). DEBUG APK only.
Phone retest of the 11-row scenario required.

POST-ROADMAP WORKSTREAM 02 (2026-10-24, developer-approved): advanced live filtering
and saved filter presets (docs/workstream-02-advanced-filters.md). One shared pure
boundary (core LiveFilter.rowsFor) feeds Liste/Radar/Zaman tüneli with identical
membership (OR-within-category, AND-across, empty = unrestricted, UNKNOWN never
silent); Radar filters BEFORE layout so the cluster/overflow invariant keeps holding;
result accounting "x / y kayıt gösteriliyor"; filter-excluded empties never claim
"no devices found". Filtreler panel with explicit Uygula/Sıfırla, transient Turkish
-folded name search (never persisted), and user-managed presets (max 20, validated
names, deterministic duplicate rejection) stored atomically app-private
(filter_presets_v1.txt, history store untouched; corrupt entries fail closed
without deleting data; preset ids are repository bookkeeping, never device
identity). +17 core regressions (299/299), app 45/45, lint 0 errors/15 warnings.
DEBUG APK only. Xiaomi retest required (steps in the workstream doc).

POST-ROADMAP WORKSTREAM 02 STARTUP CRASH HOTFIX (2026-10-24): Xiaomi could not
launch the WS02 debug build (Unable to instantiate MainActivity; NPE:
getApplicationContext on a null object reference). PROVEN source cause: the preset
repository/storage was constructed in a Kotlin PROPERTY INITIALIZER, which runs
during Activity instantiation before attachBaseContext (mBase null). Minimal fix:
lateinit repository constructed exactly once in onCreate after super.onCreate
(established HistoryController pattern); preset list state filled there.
docs/workstream-02-startup-crash-hotfix.md holds the QA note, root cause and
evidence. Fresh gates: core 299/299, app 49/49 (+4 startup-boundary regressions),
lint 0 errors/15 warnings; DEBUG APK only (identity/version/signing unchanged,
install-over safe, data preserved). WORKSTREAM 02 DEVICE ACCEPTANCE REMAINS OPEN
until the Xiaomi launch retest passes.

POST-ROADMAP WORKSTREAM 03A (2026-10-24, developer-approved slice): privacy-safe
signature classification — pure Kotlin engine + 5-rule ORIGINAL versioned catalog
(radioobserver-signature-catalog-v1) + honest UI hints. Typed input enforces the
trust boundary structurally (radio + pipeline type hint + format confidence ONLY;
no name/SSID/manufacturer/RSSI/identity fields), so no identity-adjacent data can
enter classification and no fingerprint can be persisted (classification is an
ephemeral display projection; nothing stored). List rows show a concise 'Olası
tür' line for BLE families; detail shows a provenance card (rule id + catalog
version + rationale) or the truthful 'Cihaz türü belirlenemedi' fallback;
conflicts keep uncertainty deterministically. Category filter DEFERRED (would
change the WS02 preset codec schema; no fake filter added). WS01 radar correction
and WS02 filters/presets/startup-hotfix untouched. +9 core regressions (308/308),
app 49/49, lint 0 errors/15 warnings. DEBUG APK only. docs/workstream-03a-
signature-core.md holds the trust-boundary table, catalog provenance and the
Xiaomi checklist. WS03B and later workstreams NOT started. Details: docs/field-test-start-permission-2026-10-23.md (fifth
section).

## Phase 3 — Friendly Compose UI & Live Observation State (COMPLETE: 10/10 done)

Detailed proposal: `docs/phase3-proposal.md`. Developer authorization (2026-10-20): the
ten tasks below are recorded in the established order; tasks 3.1 and 3.2 were authorized
for implementation in that iteration. Developer-approved product decisions: default UI
language Turkish; after permissions are granted observation NEVER starts automatically
(a NEW explicit Start tap is required); public observations stay masked with no
raw-address/BSSID reveal control.

- [x] **3.1** UX contract, navigation and copy inventory
  - Evidence (2026-10-20): `docs/phase3-ux-spec.md` — five surfaces (Home/Observation
    Control, Observations, Observation Details, Permissions & Readiness, Privacy &
    About), navigation flow, per-radio state-transition diagram (IDLE/CHECKING/
    PERMISSION_REQUIRED/BLOCKED/OBSERVING/STOPPING/STOPPED/TIMED_OUT/ERROR), separate
    BLE/Wi-Fi Start/Stop controls, full Turkish copy inventory (UiCopyKey and
    SessionIssueKey mapping), exact two-tap permission contract (grant never starts;
    second Start tap requires fresh readiness+foreground checks; same rule for
    composition/resume/recreation/settings return), honesty rules (no AirTag/SmartTag
    identity claim, no meter distance from RSSI, no safety guarantee, stale/unknown
    Wi-Fi never shown fresh, empty list never proof, no raw-address reveal), Material 3
    light/dark + WCAG AA contrast + ≥48dp targets + responsive layout + accessibility
    labels, edge-case scenario table. Compose screens deliberately NOT implemented here.
- [x] **3.2** UI state contracts and presentation mapper (JVM-first)
  - Evidence (2026-10-20): pure-JVM package `:core:radio` `...core.radio.ui` (zero
    android.* imports, no Android APIs, no side effects, no new dependencies):
    `ObservationUiContracts.kt` (RadioSessionUiStatus, SessionIssueKey typed reasons,
    LocationAccessUi with APPROXIMATE_ONLY never satisfying FINE-required scanning,
    inert sealed ObservationUserIntent data contracts, typed UiCopyKey, RadioSessionUiState,
    sanitized ObservationRowUi with session-local ordinal identity, ObservationUiState)
    and `ObservationUiMapper.kt` (PublicObservation-only mapping; freshness carried
    verbatim; per-radio structural independence; withLocationAccess deliberately
    session-neutral — a PRECISE grant never auto-starts; withForegroundLost terminates
    both radios; defensive mapping of blank/negative values). Privacy boundary: rows are
    built from real pipeline snapshots; raw addresses/BSSIDs/payloads/parse-issue texts
    are unreachable by construction. 18 new JVM tests (ObservationUiStateTest 10,
    ObservationUiMapperTest 8): initial inert state, per-radio independence, grant-
    never-starts, approximate-only semantics, distinct BLOCKED/ERROR/TIMED_OUT/STOPPED,
    foreground loss, inert intents, defensive validation, no-raw-identifier leak (real
    end-to-end snapshot), session-local row identity, cached Wi-Fi never fresh,
    FRESH/STALE/UNKNOWN distinction, conservative confidence/qualitative signal.
    Total 161/161 JVM green; :app:compileDebugKotlin, :app:assembleDebug and
    :app:lintDebug (0 errors, 11 known warnings) all pass. Two test-fixture defects were
    found and fixed during iteration (shared dedup key; "600_000" string underscore not
    parseable) — production code unchanged by them. Android runtime UI/permission dialog
    behavior: UNVERIFIED (device-free protocol).
- [x] **3.3** Runtime permission and settings flow
  - Evidence (2026-10-21): pure-JVM decision policy `:core:radio` `core.radio.ui`
    `PermissionFlowPolicy.kt` (zero android.* imports): API 29-30 FINE-only plan; API 31+
    FINE+COARSE together in ONE coordinated request (+BLUETOOTH_SCAN for BLE when
    missing, never overlapping launches); ACCESS_WIFI_STATE/legacy Bluetooth never
    runtime-requestable; NEARBY_WIFI_DEVICES absent from vocabulary by construction;
    LocationAccessResolver (PRECISE / APPROXIMATE_ONLY never precise / DENIED);
    DenialAssessment with NO permanently-denied output (rationale==false alone never
    proves it); PermissionFlowReducer with in-flight duplicate guard, Settings
    read-only refresh, recreation dropping the transient Start tap, and an effect type
    with NO start-observation member (two-tap rule structurally enforced). Additive
    changes (justified, tested): PermissionId.ACCESS_COARSE_LOCATION (runtime vocabulary
    only; requiredPermissionsFor/FINE semantics unchanged) and LocationAccessUi.DENIED.
    Thin Android bridge `:app` `permission/`: AndroidPermissionGateway (fresh
    checkSelfPermission reads; BLUETOOTH_SCAN install-time-true < API 31) and
    PermissionFlowController (RequestMultiplePermissions registered unconditionally in
    onCreate, recreation-safe; launched ONLY from explicit user Start tap; fresh grant
    re-read after every callback; transient Start consumed, never replayed; user-only
    app/Location Settings recovery with resolveActivity guards; no scan, no services,
    no persistence). MainActivity: narrow registration-only wiring, entry point
    deliberately inert until 3.5. docs/phase3-permission-flow.md (matrix, two-tap rule,
    lifecycle semantics, 3.5 handoff, UNVERIFIED register). 17 new JVM tests
    (PermissionFlowPolicyTest): total 178/178 green; :app:testDebugUnitTest EXIT 0
    (0 tests configured); :app:compileDebugKotlin EXIT 0; :app:assembleDebug EXIT 0;
    :app:lintDebug --rerun-tasks EXIT 0 (0 errors; 14 informational warnings: 11
    pre-existing + 2x InlinedApi BLUETOOTH_SCAN constant, runtime-guarded + 2x
    QueryPermissionsNeeded on Settings intents; not suppressed, documented in
    docs/phase3-permission-flow.md). APK verified unchanged: applicationId
    com.hjinlabs.radioobserver, permission set identical to 2.8 state (incl.
    ACCESS_COARSE_LOCATION; no new permissions). Real dialog/OEM/Settings behavior:
    UNVERIFIED (device-free protocol).
- [x] **3.4** Foreground observation session integration
  - Evidence (2026-10-21): production call path implemented end-to-end:
    pure-JVM `:core:radio` `session/ObservationSessionOrchestrator.kt` (foreground/
    permission/capability re-checks at the start boundary; per-radio independence;
    idempotent duplicate Start and repeated Stop; foreground loss stops both; generation
    guard drops late callbacks; typed terminal mapping incl. TIMED_OUT/FOREGROUND_LOST/
    SOURCE_ERROR with preserved cause; pipeline disposed on LAST session end —
    radio independence preserved while the other session is live; fresh pipeline per
    session with session-local identities; strictly passive Wi-Fi: no scan API exists) +
    `:app` `session/ObservationSessionPresenter.kt` (fresh grants via 3.3 gateway,
    capability via existing probes, foreground from supervisor; missing FINE/SCAN routes
    ONLY through the 3.3 permission controller — two-tap rule; never auto-starts) +
    MainActivity wiring (registered, deliberately inert: no temporary buttons, nothing
    invoked on create/resume/callbacks/recreation). Android sources now declare the
    existing `notifyForegroundLost()` as `ForegroundLossAwareSource` (minimal :app change;
    foreground loss now ends sessions with the typed FOREGROUND_LOST reason).
    docs/phase3-session-integration.md (call path, ownership, session semantics, privacy,
    3.5 handoff, UNVERIFIED register). 13 new JVM tests (ObservationSessionOrchestratorTest
    A–P: no cold-start/resume scan; single authorized start; permission-need not scan;
    grant-never-starts + second-tap; approximate-only fails closed; simultaneous
    independence; idempotency; foreground loss both + no auto-restart; recreation from
    zero; timeout/failure/late-callback safety; sanitized rows without raw leaks; honest
    Wi-Fi freshness FRESH/STALE/cached-never-fresh/UNKNOWN; passive Wi-Fi; disposal +
    session-local identity reset). Totals: :core:radio:test --rerun-tasks 191/191;
    :app:testDebugUnitTest --rerun-tasks EXIT 0 (0 tests configured);
    :app:compileDebugKotlin EXIT 0; :app:assembleDebug EXIT 0; :app:lintDebug
    --rerun-tasks EXIT 0 (0 errors; 14 warnings, all pre-existing informational).
    APK/manifest unchanged: applicationId com.hjinlabs.radioobserver; permission set
    identical; no new services/receivers/components; BLE startScan confined to the
    existing bounded engine; no WifiManager.startScan()/WifiScanner. Real radio
    reception/broadcast/lifecycle/OEM/Compose UI: UNVERIFIED (device-free protocol).
- [x] **3.5** Material 3 Compose observation control screen
  - Evidence (2026-10-21): real Turkish Material 3 control screen replacing the inert
    placeholder. Stateless renderer `:app` `ui/ControlScreen.kt` (RadioObserverControlScreen):
    title/scope/privacy honesty block; two independent cards (Bluetooth gözlemi / Wi-Fi
    gözlemi) with Başlat/Durdur, honest status + typed issue + session-limit-from-real-state;
    passive Wi-Fi note; approximate-only/precise rationale hints; sanitized count summary
    or empty-feed honesty note; background-stop note. Turkish copy in
    res/values/strings.xml mapped 1:1 to the 3.1 UiCopyKey inventory + ux-spec status/
    issue tables (plurals for counts; future locale folders supported by the standard
    mechanism). Pure-JVM projection `:core:radio` `ui/ControlScreenProjection.kt`
    (host-tested enable rules, session-limit-from-authoritative-state, honest empty-feed
    placement, sanitized count): Start enabled except OBSERVING/STOPPING (permission flow
    always reachable; duplicate-start impossible), Stop only while active. MainActivity:
    Activity-scoped mutableStateOf holder as the single Compose state source (statuses
    only from the 3.4 orchestrator callback; 3.3 onStateChange refreshes ONLY
    locationAccess — structurally cannot start anything, two-tap rule intact); setContent
    wires ONLY real click callbacks to sessionPresenter::onUserStart*/Stop*; recreation
    starts from ObservationUiState.initial(); 3.3 launcher registration unchanged.
    A11y: 48dp touch targets, action+radio+status contentDescriptions, status semantics.
    docs/phase3-control-screen.md (structure, strings, click-to-source path, ownership,
    tests + UNVERIFIED register). 8 new JVM tests (ControlScreenProjectionTest G/D/F/E/K/
    I/J-A/C); totals 199/199; :app:testDebugUnitTest 0 tests (none configured); compile/
    assemble/lint all EXIT 0; lint 0 errors, 14 warnings (4 new-in-iteration string
    warnings FIXED in-iteration: plurals + genuinely-used a11y/rationale strings; final
    count equals the historical 14). APK: applicationId/permissions/version unchanged.
    UNVERIFIED: real Compose click delivery, TalkBack, layout/font-scale/dark mode,
    permission dialogs, radio reception, OEM lifecycle (no device).
- [x] **3.6** Sanitized observation feed and detail UI
  - Evidence (2026-10-21): real user-accessible read-only feed + detail, no navigation
    dependency (MainActivity local typed Screen state + BackHandler; 3.5 control screen
    gained a pure-navigation "Gözlemler" button). Feed (ui/FeedScreens.kt
    ObservationsFeedScreen): combined ephemeral BLE + passive Wi-Fi rows over CURRENT
    sanitized ObservationUiState; All/Bluetooth/Wi-Fi FilterChips; per-row source word,
    freshness word (Taze/Eski/Tazelik bilinmiyor — verbatim, cached Wi-Fi never fresh),
    honest last-seen from authoritative epoch only; LazyColumn keyed by session-local
    rowId (pipeline ordinal, never address/hash) under the pipeline's authoritative
    200-row bound; newest-first pipeline order preserved (no proximity re-sort).
    Honest empty taxonomy (active/no-session/filter) with no-absence-proof note.
    Detail (ObservationDetailScreen): resolved ONLY via pure-JVM
    core/radio/ui/ObservationFeedProjection.detailFor(state, rowId) against current
    state; sanitized fields only (source, name, conservative confidence word, signal
    category + not-distance note, freshness, manufacturer/type hints + identity-not-
    verified caution, locally-administered Evet/Hayır, last-seen, session-local row
    label); Wi-Fi passive-source note; missing row → Turkish "no longer available"
    state with return action, never a scan restart. No second retention/store/timer;
    TTL/eviction/disposal stay authoritative at the pipeline. Turkish strings in
    strings.xml (plurals conventions; no hardcoded UI text). Bug-risk review: rowId
    stability verified — pipeline ordinal, not positional index; disposal → empty rows
    → selection resolves unavailable; no defect found. 7 new JVM tests
    (ObservationFeedProjectionTest A/B/C-D/E-F/H/I/J-K); totals 206/206;
    :app:testDebugUnitTest 0 tests (none configured); compile/assemble/lint all EXIT 0;
    lint 0 errors, 15 warnings (14 historical + 1 new: Typos false positive on the
    Turkish word "adres" — documented, not suppressed). APK: applicationId/permissions/
    version unchanged. UNVERIFIED: Compose click/navigation runtime, TalkBack, text
    scaling, dark mode, Wi-Fi broadcasts, BLE reception, OEM lifecycle (no device).
- [x] **3.7** Error, readiness and recovery UI
  - Evidence (2026-10-21): honest per-radio recovery/status UI on the 3.5 control
    cards. Pure-JVM core/radio/ui/RecoveryStatusProjection.kt (zero android.*, no
    competing state machine, NO permanently-denied concept): maps CURRENT sanitized
    session state to at most ONE context-appropriate action — İzin ayarlarını aç
    (denied/approximate-only), Konum ayarlarını aç (location services off),
    Yeniden kontrol et (radio off / throttled / security); unsupported-hardware,
    terminal, idle and live states map to honest NO action. New narrow orchestrator
    method refreshReadinessStatus (never start/stop/generation; no-op while live or
    unchanged; require() forbids fabricated activity) + presenter recheck entries
    (fresh gateway+probe read; NEVER prompts dialog or starts; two-tap intact).
    MainActivity: recovery buttons wired to EXISTING guarded controller methods
    (openAppPermissionSettings/openLocationSettings — no new launcher); identified
    integration gap fixed: onResume now invokes onSettingsReturned (read-only
    permission refresh after Settings/dialog/app-switch returns) and recreation invokes
    onActivityRecreated (transient Start dropped). Turkish recovery copy + a11y labels
    in strings.xml (48dp targets; action+radio contentDescriptions). 12 new JVM tests
    (RecoveryStatusProjectionTest 7 + ObservationRecheckTest 5: A/B/C/D/E/F/G/L +
    no-start refresh, live no-op, idempotency, no fabricated activity); totals 218/218;
    :app:testDebugUnitTest 0 tests (none configured); compile/assemble/lint all EXIT 0;
    lint 0 errors, 15 warnings (baseline unchanged, no new warnings). APK:
    applicationId/permissions/version unchanged. UNVERIFIED: real Settings launches,
    permission dialogs, TalkBack, text scaling, dark mode, radio reception, OEM
    lifecycle (no device).
- [x] **3.8** Lifecycle ownership, privacy teardown and state restoration
  - Evidence (2026-10-21): full ownership audit (docs/phase3-lifecycle-privacy-teardown.md
    matrix: Activity/launcher/BLE scanner/Wi-Fi receiver/generations/supervisor/pipeline/
    selection/pending-Start) — existing guarantees verified, no churn. ONE proven defect
    fixed: the 3.7 onResume Settings-refresh mapped a grantless state to DENIED even when
    no runtime request ever happened (fresh install falsely claimed denial).
    LocationAccessResolver.resolve now requires denial EVIDENCE (genuine past runtime
    request OR previously-held access, so revoked-after-grant stays honestly DENIED);
    reducer supplies the evidence per event. 5 new JVM tests (PermissionRestorationTest:
    fresh-process NOT_REQUESTED, post-request DENIED, pending-tap consumption with no
    start effect, recreation drops transient Start but keeps in-flight guard, resolver
    never fabricates denial). Totals 223/223; :app:testDebugUnitTest 0 tests (none
    configured); compile/assemble/lint all EXIT 0; lint 0 errors, 15 warnings (baseline
    unchanged). APK: applicationId/permissions/version unchanged. Privacy invariants
    re-verified: no raw identifiers in UI, no persistent history, session-local rows,
    200/15-min bounds unchanged, Wi-Fi receive-only. UNVERIFIED: real Android lifecycle
    delivery, configuration-change timing, on-device permission races, OEM behavior.
- [x] **3.9** Accessibility, responsive layout and polish
  - Evidence (2026-10-22): source-level audit of every Phase 3 surface
    (docs/phase3-accessibility-responsive-polish.md) — met criteria documented, no
    churn. 4 proven defects fixed: (A) screen-reader description dropped the visible
    session-limit text on the control card status row (merged description now carries
    it); (B) no heading semantics on control/feed/detail/card titles (heading()
    added); (C) filter chips lacked explicit 48dp touch targets and the chip Row
    could overflow at large font scales (heightIn(min=48dp) + FlowRow wrap, existing
    foundation API, no new dependency); (D) Turkish polish: ambiguous "Oturum kaydı
    (oturuma özel)" label now "Oturum kaydı (yalnızca bu oturumda geçerli)" +
    strings.xml indentation fix; no honesty/privacy wording strengthened. No pure
    projection changed → no new host tests required; existing behavioral matrix
    passes: 223/223; :app:testDebugUnitTest 0 tests (none configured);
    compile/assemble/lint all EXIT 0; lint 0 errors, 15 warnings (baseline
    unchanged). APK identity/permissions unchanged. All 3.3-3.8 invariants intact
    (two-tap Start, no auto-start, receive-only Wi-Fi, sanitized data only).
    UNVERIFIED: real TalkBack, measured contrast, runtime font-scale layout,
    dark-mode render, on-device clicks.
- [x] **3.10** Device-free quality gate and phase handover
  - Evidence (2026-10-22): fresh final-tree gate on clean tree @ 38aa7ca:
    :core:radio:test --rerun-tasks EXIT 0 (21 suites, 223/223, 0 skipped, recalculated
    from fresh JUnit XML); :app:testDebugUnitTest --rerun-tasks EXIT 0 (0 tests, none
    configured); :app:assembleDebug EXIT 0 (APK identity com.hjinlabs.radioobserver,
    versionCode 1 / versionName 0.1.0-phase1, permissions unchanged, signing
    non-mutated); :app:lintDebug --rerun-tasks EXIT 0 (0 errors, 15 warnings — same
    baseline set, no new findings). Contract matrix A-J and privacy review mapped to
    source + host tests in docs/phase3-quality-gate-handover.md (explicit-user-only
    Start, two-tap rule, independent sessions, foreground-loss teardown, no
    resurrection, sanitized-only UI, session-local row IDs, 200/15-min bounds,
    passive Wi-Fi, qualitative RSSI). 3.1-3.9 evidence is historical and was not
    rerun individually. Real-device matrix explicitly UNVERIFIED (permission
    dialogs, Settings Intents, BLE/Wi-Fi reception, OEM lifecycle, Compose clicks,
    TalkBack, font scaling, dark mode). Status: device-free code/host gate PASSED —
    NOT release certification, field validation or Play Store readiness.

## Binding scope and honesty rules (all phases)

- Live BLE/Wi-Fi reception, permission UX, and real-device accuracy are UNVERIFIED under
  host-only verification; tests prove parsing/state logic only.
- Manufacturer/company-id matches are ecosystem hints, never tracker or product proof;
  RSSI is never distance proof; unknown remains unknown.
- Local-first: no uploads of BLE addresses, BSSIDs, SSIDs, locations, or observation logs.
- Phase 1 performs no scanning and declares no Android permissions.

## Future Phase 2+ design notes (documented, NOT implemented)

- Android 10+ can listen to system Wi-Fi scan-result broadcasts without the app initiating
  a scan; `WifiManager.startScan()` initiated by the app is different and must not be
  presented as strict receive-only behavior.
- "Observation-only" for BLE means no deliberate pairing, connection, injection, or active
  interrogation — not a guarantee that no radio packets are ever transmitted.
- No device may be labeled a threat solely from a vendor signature.
