# Radio Observer — Phase 2 Proposal (NOT YET APPROVED FOR IMPLEMENTATION)

**Date:** 2026-10-19
**Repo:** `ceyhunmammadov9599-tech/radioobserver` (private, branch `main`)
**Developer-approved applicationId:** `com.hjinlabs.radioobserver`
**Status:** Planning proposal only. No Phase 2 code, permission, or manifest change is
authorized by this document. Every task below is an UNCHECKED DRAFT pending explicit
developer approval.

## Scope and non-goals

**Goal:** Design safe Android-specific BLE observation and system-provided Wi-Fi
scan-result adapters, permission/capability states, lifecycle boundaries, and device-free
acceptance tests while preserving Phase 1's pure Kotlin/JVM core (`:core:radio`).

**Non-goals (binding):** No device/emulator/adb testing; no tracker-threat declarations;
no pairing, connecting, advertising, GATT access, or transmitting custom packets; no
app-initiated Wi-Fi scan (`WifiManager.startScan()`); no perpetual/background tracking
promise; no Supabase, uploads, analytics, raw-identifier export, or backend; no UI feature
development (Phase 3); no foreground service or boot receiver without separate approval.

## Design principles

1. `:core:radio` remains free of `android.*` imports. All Android types stay behind the
   boundary layer. The canonical `ObservedRadioDevice` and evidence/confidence model is
   reused; no second device model is introduced.
2. BLE scanning is **observation-only at the application level** — this is NOT a claim that
   the device radio never transmits. Scans are time-bounded, cancellable, user-initiated by
   default, and never run in a tight loop.
3. Wi-Fi is **system scan-result observation only**: listen for
   `SCAN_RESULTS_AVAILABLE_ACTION` and read results only when permissioned. A broadcast on
   Android 10+ may reflect a scan performed by the platform or another app; results can be
   cached/stale and delivery is never periodic or guaranteed. `WifiManager.startScan()` is
   never called under the receive-only design.
4. Permission requirements are validated against the **specific API being called**, not
   inferred from a permission family name. On Android 13+, `NEARBY_WIFI_DEVICES` does NOT by
   itself replace `ACCESS_FINE_LOCATION` for `WifiManager.getScanResults()`; location
   services being enabled can also be required depending on API level.
5. The BLE `neverForLocation` assertion is **NOT automatic**: it can cause some BLE beacons
   to be filtered out of scan results, and it must only be asserted truthfully. The
   privacy-versus-observation-coverage trade-off is an explicit, unresolved policy gate
   requiring developer decision (see Open questions).
6. BLE `BLUETOOTH_SCAN` is a runtime permission on Android 12+; behavior on lower API
   levels differs. `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` are not requested unless a
   concrete need is approved.
7. Background scans, foreground services, and always-on operation are **not preauthorized**.
   Android 12+ background-start restrictions, Android 14+ FGS type/permission requirements,
   and current Android 15/16 limitations must be evaluated separately before any such
   component is proposed. Default assumption for this plan: **background operation is OUT
   of scope.**
8. Device identity stays evidence-based and conservative: company ID 0x004C is not AirTag
   proof, 0x0075 is not SmartTag proof, RSSI is not physical distance, and unknown stays
   unknown. No device is labeled a threat from vendor signatures alone.
9. Strict local-first privacy: no uploads, no persistent collection by default, no
   credentials, no unconsented location history, no raw SSID/BSSID/BLE-address logs.
   Retention, masking, and access scopes must be explicit in every task.

## Proposed tasks 2.1–2.8 (ALL UNCHECKED; subject to developer approval)

### 2.1 Platform and permission capability matrix
- **Scope:** Document the actual minSdk/targetSdk (currently 29/35 per build files) and
  API-specific BLE scan vs Wi-Fi `getScanResults()` permission and availability requirements
  for every supported Android version bucket.
- **Suggested technical boundary:** A written, auditable decision matrix (docs), later
  encoded as pure data consumed by the permission state machine. Separate rows for: app
  process state, hardware absence, device radio toggles (Bluetooth/Wi-Fi/location services),
  permission denied vs permanently denied, Wi-Fi result staleness.
- **Host-verifiable acceptance:** Matrix exists with SEPARATE Wi-Fi `getScanResults()` and
  BLE scan paths; an explicit `neverForLocation` decision gate (unresolved by default); no
  incorrect `NEARBY_WIFI_DEVICES` substitution claims.
- **Risks:** Android permission semantics drift across API levels; mis-documented
  requirements would propagate into the state machine.
- **Non-goals:** No permission declarations in the manifest yet; no runtime requests.

### 2.2 Android boundary contracts and state models
- **Scope:** Interfaces for a BLE observation source, a passive system Wi-Fi result source,
  a capability/permission checker, and an observation-session lifecycle; typed outcomes and
  error states compatible with Phase 1 domain models.
- **Suggested technical boundary:** Pure Kotlin interfaces + sealed result types in a new
  `:core:radio`-adjacent module or `:app` boundary package (no Android types in the
  interfaces themselves where avoidable; Android types wrapped at the adapter edge).
- **Host-verifiable acceptance:** Fake implementations (JVM fakes) exercise start/stop,
  unavailable hardware, cancelled sessions, error paths, and observability with no Android
  framework dependency leaking into `:core:radio`.
- **Risks:** Interface over-design before adapter implementation reveals real needs.
- **Non-goals:** No scanner implementation, no UI.

### 2.5 Permission and capability flow model
- **Scope:** User-facing permission decisions as PURE state transitions with Android
  API-bucket adaptations: required / denied / permanently denied / radio disabled /
  location services disabled / hardware unavailable; `SecurityException` mapping; precise,
  honest user disclosures.
- **Suggested technical boundary:** Pure state machine in JVM code; table-driven; every
  transition testable without Android.
- **Host-verifiable acceptance:** Table-driven host tests across API buckets and permission
  outcomes. Any manifest permission addition must be separately approved in the
  implementation step and documented with its exact rationale.
- **Risks:** Honest disclosure wording must match actual data use (e.g., Wi-Fi results
  imply coarse location of the observer).
- **Non-goals:** No hidden permission requests, no preemption of the user's decision, no
  permission declared in this proposal.

### 2.3 BLE observation adapter design (scoped foreground-session behavior)
- **Scope:** DESIGN for a permission-gated, time-bounded `BluetoothLeScanner` callback
  adapter: single-session guards, clean stop/unregister, error mapping, defensive parsing
  at the boundary feeding Phase 1 pure parsers.
- **Suggested technical boundary:** One adapter class behind the 2.2 interface; converts
  Android scan records to the Phase 1 `BleParseInput` shapes; auto-stop timer; explicit
  cancel.
- **Host-verifiable acceptance:** Host-side fake/callback tests verify start/stop/cancel/
  error handling and confirm absence of pair/connect/advertise/GATT APIs. Real-device
  reception remains explicitly UNVERIFIED.
- **Risks:** OEM scan throttling and battery policies vary; callback threading pitfalls.
- **Non-goals:** No pairing, connecting, advertising, GATT, background guarantees,
  continuous loops.

### 2.4 Wi-Fi system scan-result observer design (strict no-app-trigger)
- **Scope:** DESIGN for listening to OS/other-app scan completion updates
  (`SCAN_RESULTS_AVAILABLE_ACTION`) and reading permission-gated cached scan results with
  documented source/freshness markers. `WifiManager.startScan()` is NEVER invoked.
- **Suggested technical boundary:** BroadcastReceiver adapter behind the 2.2 interface;
  result timestamps and `getScanResults()` permission checks mapped to typed outcomes;
  explicit updated-vs-stale/cached marker.
- **Host-verifiable acceptance:** Fake broadcast/result scenarios cover updated/failed/
  stale results and permission failures; assert no active scanning API is called. No claim
  of continuous delivery.
- **Risks:** Stale cached results being mistaken for fresh observations; OEM behavior
  differences in broadcast cadence.
- **Non-goals:** No app-triggered scans, no periodic-delivery promises, no monitor mode.

### 2.6 Lifecycle, battery, and background policy
- **Scope:** Foreground lifecycle shutdown/cancellation plan, bounded scan windows,
  app-background transition behavior, process-death assumptions, and a decision RECORD
  evaluating (not adopting) foreground services.
- **Suggested technical boundary:** Written policy + pure lifecycle-state tests. Evaluation
  must cover Android 12+ background-start restrictions and Android 14+ FGS type/permission
  requirements; conclusion defaults to NO FGS.
- **Host-verifiable acceptance:** State/lifecycle tests and a written restriction matrix.
  No auto-start on boot, no FGS, no endless scanning without separate approval.
- **Risks:** Under-scanning frustrates users; over-scanning betrays the bounded-scan
  promise and battery expectations.
- **Non-goals:** No FGS implementation, no boot receiver, no WorkManager scheduling.

### 2.7 Observation pipeline and privacy controls
- **Scope:** Map platform observations into the existing Phase 1 evidence model with dedup
  (newest-wins), staleness/expiry, bounded IN-MEMORY retention, masking for display,
  unknown/unavailable states, and no "threat" label from vendor hints alone.
- **Suggested technical boundary:** Reuse `ObservationStore` semantics from Phase 1 with
  session-scoped stores; extend only as justified by tests.
- **Host-verifiable acceptance:** Fakes verify dedup, late arrivals, expiry, null/invalid
  RSSI, no-export defaults, and confidence preservation.
- **Risks:** Retention creep (session store accidentally persisting); raw identifiers
  leaking into logs.
- **Non-goals:** No persistence, no export, no uploads, no threat assertions.

### 2.8 Device-free quality gate and phase handoff
- **Scope:** Run the existing `:core:radio:test` regression suite (historical baseline:
  44 tests), add narrowly scoped JVM host tests for 2.2–2.7 units, and run an appropriate
  Android compile/package check (e.g., `:app:assembleDebug`), all device-free.
- **Suggested technical boundary:** Explicit command list with timeouts (`timeout 15m
  ./gradlew ... --no-daemon`); per-task DoD/evidence recorded in `roadmap.md` only after
  verification.
- **Host-verifiable acceptance:** Green final-tree checks with exact commands/results;
  roadmap progress/evidence updated; no Android instrumentation; no device-validation
  claims. Phase 3 stays unauthorized.
- **Risks:** Treating host-test success as device-behavior proof — explicitly forbidden.
- **Non-goals:** No emulator/device/adb validation, no store-readiness claims.

## Recommended execution order

`2.1 -> 2.2 -> 2.5 -> 2.3 -> 2.4 -> 2.6 -> 2.7 -> 2.8`

The order resolves capability and permission uncertainty before any platform adapter is
implemented.

## Gate before implementation (developer decisions required)

1. Explicit approval of the Phase 2 task list and implementation scope.
2. Confirm SDK range from current build files (currently minSdk 29 / target 35).
3. Decide the BLE `neverForLocation` policy after reviewing its beacon-filtering
   implication (coverage vs location-permission disclosure).
4. Confirm Wi-Fi permission disclosure wording and that reading local Wi-Fi scan results
   fits the privacy requirements (scan results imply observer location).
5. Confirm whether ANY background operation is in scope (default: **NO**).
6. Approve or reject each proposed manifest permission addition individually, with
   rationale recorded.

## Open questions (blocking implementation start)

- `neverForLocation`: prefer maximum beacon coverage (omit the flag, keep location-based
  disclosure) or minimal permission footprint (assert the flag, accept filtered beacons)?
- Wi-Fi observation disclosure: acceptable to state that scan results depend on location
  services and can reveal nearby infrastructure, and that results may be cached/stale?
- Background: confirm the default NO for Phase 2; if YES for a future phase, FGS type and
  Android 14+/15/16 constraints must be re-evaluated first.

## Source references (Android Developers; inspected 2026-10-19)

- BLE scanning: https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices
- Bluetooth permissions and `neverForLocation`: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Wi-Fi scan restrictions and broadcasts: https://developer.android.com/develop/connectivity/wifi/wifi-scan
- Wi-Fi API-specific permissions: https://developer.android.com/develop/connectivity/wifi/wifi-permissions
- Background Bluetooth: https://developer.android.com/develop/connectivity/bluetooth/ble/background
- Foreground-service changes: https://developer.android.com/develop/background-work/services/fgs/changes
- App ID versus namespace: https://developer.android.com/build/configure-app-module
