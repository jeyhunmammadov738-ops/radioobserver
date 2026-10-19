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

## Phase 2 — Scanner Abstractions & Permission Flow Models (complete at code/host level; 8/8 done)

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

Current focus: Phase 2 complete at code/host level; Phase 3 (UI + explicit user start) awaits developer authorization — not started.

## Phase 3 — Friendly Compose UI & Live Observation State
(planned; detailed tasks NOT YET AUTHORIZED — task counts: not defined)

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
