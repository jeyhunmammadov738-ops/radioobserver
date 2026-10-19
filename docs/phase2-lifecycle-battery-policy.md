# Phase 2.6 — Foreground Lifecycle, Battery & Background Policy (Decision Record)

Status: APPROVED & IMPLEMENTED (2026-10-19). This document is a decision record, not
permission for background work. Implementation: `RadioObservationSupervisor`
(pure JVM, `:core:radio`), `ObservationLifecycleBridge` (`:app`), `MainActivity` wiring.

## 1. Foreground eligibility decision

**Eligibility = the host Activity is in the RESUMED state. Nothing else.**

- Conservative: a process being alive, the application context, a foreground service, or
  permission grants NEVER substitute for a RESUMED Activity.
- Eligibility starts FALSE on construction. A new process (also after process death)
  begins ineligible; nothing is auto-recreated.
- `onResume` establishes eligibility but NEVER starts anything. A session requires a
  separate explicit caller/user start intent AND the sources' own permission/capability
  gates (fail closed).
- Loss of RESUMED (`onPause`) revokes eligibility immediately and stops both active
  sessions. It is TERMINAL for those sessions: returning to the foreground does NOT
  revive them; only a fresh explicit start can begin a new session.

**Intentional consequence — multi-window trade-off:** a partially visible but paused
Activity (split-screen, picture-in-picture, another window focused) does NOT qualify.
Observation stops in those states. This is deliberate: a "visible-ish" window is not a
reliable proof of user presence, and conservative privacy/battery behavior is preferred
for this project.

## 2. Lifecycle transition table

| Host event | Eligibility after | Active sessions | Notes |
|---|---|---|---|
| `onCreate` | FALSE | none | No auto-start on (re)creation or restoration. |
| `onStart` | FALSE | none | Deliberately NOT eligible: STARTED ≠ RESUMED. |
| `onResume` | TRUE | none started automatically | Eligibility only; explicit start still required. |
| `onPause` | FALSE | BOTH stopped (FOREGROUND_LOST) | Terminal for those sessions. |
| `onStop` | FALSE | cleanup repeated defensively | Idempotent: no duplicate stop/unregister. |
| `onDestroy` | FALSE | cleanup repeated defensively | New Activity ⇒ new supervisor, starts ineligible. |

Callbacks are handled on one thread (the main thread). Atomics provide visibility toward
the sources' gate providers, not a cross-thread start race policy.

## 3. Session, timeout and battery policy

- Hard ceiling: 10 minutes per session (2.2 contract `ObservationSessionSpec`).
- Conservative defaults: BLE 20 s, Wi-Fi 30 s — finite by construction, enforced by a real
  scheduled task (Handler/postDelayed in :app; injectable `SessionScheduler` in core), not
  merely a duration field. Wall-clock changes are not treated as elapsed time; the
  platform handler schedules from now.
- No periodic re-scan loop, no polling, no restart: after timeout/cancel/failure/foreground
  loss a session ends; only an explicit new start (fresh gates) begins another.
- Session independence: at most one BLE and one Wi-Fi session concurrently (per-source
  single-session semantics preserved). Cancelling one does not stop the other.
  Foreground loss stops both when present.
- In-flight races: stop/unregister are idempotent; late BLE `ScanCallback` and Wi-Fi
  broadcast deliveries after a stop are ignored and never revive a session; cleanup
  failures are surfaced (`ScanStopFailed`), not hidden.

## 4. Battery / background restrictions (hard rules)

NEVER in this project, in this phase or later without explicit new authorization:

- No foreground/background service, no `WorkManager`/`JobScheduler` worker, no alarms.
- No boot receiver, no wake locks, no `PendingIntent` BLE scan to keep the app alive.
- No battery-optimization exemption request.
- No background location, no background scanning, no automatic restart.
- No location history store, no network upload, no analytics, no raw-address logging.

**Honest API boundary:** this is an APP-LEVEL policy, not a claim that Android is
technically incapable of background observation (e.g. documented background BLE
alternatives exist at https://developer.android.com/develop/connectivity/bluetooth/ble/background).
Android's restrictions and OEM variations are context, not a license to adopt background
APIs. Foreground-service restrictions are documented at
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start and
https://developer.android.com/develop/background-work/services/fgs/changes.

## 5. Process death

- The system may kill the process without `onStop`/`onDestroy`. In that case NO cleanup
  callback is guaranteed to run — but nothing leaks either: sessions, receivers, scanner
  callbacks, and all observation data live in process memory and die with the process.
- No scan state is persisted; no auto-resume exists. A new process starts with a fresh,
  ineligible supervisor. The only durable effects of a killed session are none.

## 6. What is verified and what is not

- VERIFIED (device-free, this iteration): supervisor policy reactions via JVM fakes;
  exactly-once cleanup; independence of the two sessions; compile/packaging; permission
  set unchanged.
- UNVERIFIED (requires a device): actual Android callback delivery/timing on a specific
  OEM build, real screen-off/multi-window transitions, OEM battery managers interfering
  with broadcasts or scans. The :app bridge (`ObservationLifecycleBridge`) forwards
  `DefaultLifecycleObserver` callbacks and is compile-verified only.

## 7. References

- Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle
- Context-registered receivers & cleanup: https://developer.android.com/develop/background-work/background-tasks/broadcasts
- `BluetoothLeScanner`: https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner
- Background BLE (documentation only — NOT adopted): https://developer.android.com/develop/connectivity/bluetooth/ble/background
