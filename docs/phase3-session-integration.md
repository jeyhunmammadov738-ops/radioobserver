# Phase 3.4 — Foreground Observation Session Integration

Status: implemented 2026-10-21 (code/host level). This document maps the production call
path, ownership, session semantics and the honest boundary to Phase 3.5. Real BLE/Wi-Fi
reception, broadcast delivery timing, lifecycle callback delivery on real devices and
the Compose UI remain UNVERIFIED under the device-free protocol.

## 1. Production call path (the only one; no competing coordinator)

```
explicit user tap (future 3.5 Turkish Start/Stop buttons; UI still inert)
  -> :app session/ObservationSessionPresenter      fresh facts at tap time
  -> :core:radio session/ObservationSessionOrchestrator   permission/capability/foreground gates
  -> RadioObservationSupervisor (Phase 2.6, RESUMED-only)  eligibility re-check
  -> AndroidBleObservationSource / AndroidWifiObservationSource (own security gates inside)
  -> ObservationEvent stream -> ObservationPipeline (privacy boundary: 200/15-min TTL)
  -> PublicObservation -> 3.2 mapper -> ObservationUiState -> (3.5 UI, inert today)
```

- Presenter (`ObservationSessionPresenter`, :app): gathers runtime grants via the 3.3
  `AndroidPermissionGateway`, capability via the EXISTING probes, foreground eligibility
  from the supervisor — all at real tap time, never cached. If FINE (or BLUETOOTH_SCAN on
  API 31+ for BLE) is missing, the tap routes ONLY through the 3.3
  `PermissionFlowController` (two-tap rule); observation never starts from that tap.
- Orchestrator (`ObservationSessionOrchestrator`, pure JVM in :core:radio): re-checks
  permission/capability/foreground at the start boundary, then delegates to the
  supervisor (which re-checks eligibility before touching the source). Sources re-gate
  internally once more (triple defense in depth).
- MainActivity: constructs supervisor (2.6), permission controller (3.3), orchestrator and
  presenter (3.4). The presenter's entry points are NEVER invoked by onCreate/onStart/
  onResume, permission callbacks, Settings returns or recreation: the UI remains inert
  until 3.5 wires real Turkish Start/Stop buttons. No temporary buttons were invented.

## 2. Ownership and lifecycle

- One orchestrator and one supervisor-to-Activity lifecycle binding
  (ObservationLifecycleBridge, unchanged 2.6 component); no duplicate observers.
- Foreground loss (RESUMED lost): the supervisor stops BOTH sessions. The two Android
  sources now implement `ForegroundLossAwareSource` (their `notifyForegroundLost()`
  already existed; the interface was previously not declared — a minimal :app change so
  sessions end with the typed `FOREGROUND_LOST` reason instead of a plain cancel).
  For cancel-based sources the orchestrator additionally classifies a cancel received
  while the host is not RESUMED as FOREGROUND_LOST (honest inference, documented).
- On the LAST session end the volatile pipeline is disposed (`closeAndDispose`) and all
  sanitized rows are dropped: session-local identities and raw evidence die with the
  session. A fresh session gets a fresh pipeline.
- Recreation/new process: everything is rebuilt from scratch (no ViewModel retention of
  scan state; no persisted tokens). Old instances cannot resurrect anything (generation
  guard + pipeline disposal).

## 3. Session semantics (per radio, independent)

- At most one BLE session and one Wi-Fi observation session; both may coexist.
- Duplicate Start is idempotent (no second source call, registration or deadline);
  repeated Stop is a safe no-op (returns false). Start while OBSERVING/STOPPING absorbs.
- Stopping one radio never stops the other. Foreground loss stops both. A stopped
  session NEVER restarts without a fresh explicit user Start tap.
- Defaults re-declared for the production path: BLE 20 s, Wi-Fi 30 s, hard maximum
  10 min (unchanged Phase 2 values; `ObservationSessionSpec` enforces the ceiling).
- Terminal mapping: CANCELLED_BY_USER→STOPPED, DURATION_ELAPSED→TIMED_OUT,
  FOREGROUND_LOST→STOPPED(+FOREGROUND_LOST), SOURCE_ERROR→ERROR (typed cause kept from
  the preceding SourceFailed), COMPLETED_NORMALLY→STOPPED.
- Generation guard: late events (post-cancel/timeout/foreground-loss/newer session) are
  dropped and can neither mutate UI state nor revive disposed data.
- Race decisions: `stop()` only marks STOPPING if the synchronous SessionEnded has not
  already settled the session; async adapters settle through the event. SourceFailed
  records the typed cause; the following SessionEnded keeps it. No implicit auto-retry.

## 4. Privacy and passive Wi-Fi guarantees

- Only `PublicObservation` and the 3.2 sanitized models reach presentation consumers;
  raw addresses/BSSIDs/payloads/parse-issue texts are unreachable by construction
  (regression-tested end-to-end through real pipeline snapshots).
- Pipeline policies preserved: newest-wins dedup, 200 entries, 15-min lazy TTL,
  session-local ordinals, conservative confidence, qualitative RSSI, honest Wi-Fi
  freshness (cached/updated=false and unparseable ages never FRESH).
- Wi-Fi observation remains strictly passive: only SCAN_RESULTS broadcasts +
  getScanResults() through the existing adapter; `WifiManager.startScan()`/
  `WifiScanner.startScan()` are never called (structural: no API exists for it).
- BLE `BluetoothLeScanner.startScan()` remains confined to the existing bounded,
  user-started engine (2.3), reached only via this explicit-tap path.
- Empty feed is never presented as proof of absence (3.1 copy keys; 3.5 UI).

## 5. Handoff to 3.5

The 3.5 control screen wires its Turkish Start/Stop buttons to:
`sessionPresenter.onUserStartBleTap() / onUserStopBleTap() /
onUserStartWifiTap() / onUserStopWifiTap()`, collects `ObservationUiState` via
`sessionOrchestrator.uiSnapshot()` (or the `onUiState` callback), and renders the 3.1
copy keys. It must NOT start anything by itself.

## 6. UNVERIFIED (device-free protocol)

Real radio reception, BLE callback delivery, Wi-Fi broadcast timing, OEM permission and
lifecycle behavior, process-death timing, and the entire Compose UI. JVM tests prove the
orchestrator/presenter decision logic and the fakes' contract behavior only.
