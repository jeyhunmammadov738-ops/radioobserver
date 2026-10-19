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

## Phase 2 — Scanner Abstractions & Permission Flow Models (current phase; 3/8 done)

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

- [ ] **2.3** BLE observation adapter (time-bounded, permission-gated `BluetoothLeScanner`
      callback adapter) — NOT implemented; next authorized task.
- [ ] **2.4** Wi-Fi system scan-result observer (`SCAN_RESULTS_AVAILABLE_ACTION` passive
      listener, permission-gated `getScanResults()`, freshness markers) — NOT implemented.
- [ ] **2.6** Lifecycle, battery and background policy (stop/cancel rules, restriction
      matrix, FGS evaluation record) — NOT implemented.

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

- [ ] **2.7** Observation pipeline and privacy controls — NOT implemented.
- [ ] **2.8** Device-free quality gate and phase handoff — NOT implemented (this iteration
      ran the full gate early: 72/72 JVM tests green, `:app:assembleDebug` successful).

Current focus: Phase 2, next task **2.3** (BLE observation adapter) — not started.

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
