# Radio Observer — Roadmap

Authoritative task list. Phase 1 is authorized; Phases 2–3 are planned placeholders whose
detailed tasks are NOT YET AUTHORIZED. Historical evidence is preserved; entries are never
reordered or silently restructured. A task may only be checked with evidence from the
current run recorded inline.

## Phase 1 — Architecture Foundation & Pure JVM Parsers (current phase)

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

## Phase 2 — Scanner Abstractions & Permission Flow Models
(planned; detailed tasks NOT YET AUTHORIZED — task counts: not defined)

> Proposal only, not approved tasks: see [docs/phase2-proposal.md](docs/phase2-proposal.md).
> Draft tasks 2.1–2.8 remain unchecked proposals until developer approval.

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
