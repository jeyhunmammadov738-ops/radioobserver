# Phase 3.3 — Runtime Permission & Settings Flow (integration contract)

Status: implemented 2026-10-21 (host/code-level). This document defines the decision
matrix, the two-tap rule, lifecycle semantics and the 3.5 handoff. Real dialog
appearance, OEM behavior, Settings navigation success and grant delivery are
**UNVERIFIED** under the device-free protocol: the bridge is compile-gated only.

## 1. Where the logic lives

| Layer | Artifact | Responsibility |
|---|---|---|
| Pure JVM (`:core:radio`, `core.radio.ui`) | `PermissionFlowPolicy.kt` | All decisions: request plans (API-correct), honest location access, denial assessment, two-tap rule, duplicate guard, recreation semantics. Zero `android.*` imports; fully unit-tested. |
| Pure JVM | `ObservationReadinessModel.kt` | `PermissionId.ACCESS_COARSE_LOCATION` added (additive, Phase 3.3): runtime vocabulary for the Android 12+ FINE+COARSE combined request. `requiredPermissionsFor` unchanged (hard FINE requirement preserved). |
| Pure JVM | `ObservationUiContracts.kt` | `LocationAccessUi.DENIED` added (additive, Phase 3.3): an honest post-request denial state; still never a permanent-denial claim. |
| Android (`:app`, `permission/`) | `AndroidPermissionGateway.kt` | Reads real grants via `checkSelfPermission` into `RuntimeGrantSnapshot` (BLUETOOTH_SCAN install-time-true below API 31). |
| Android (`:app`, `permission/`) | `PermissionFlowController.kt` | Lifecycle-safe `RequestMultiplePermissions` launcher registered unconditionally in `onCreate` (recreation-safe, stable order); launches ONLY from an explicit user Start tap; Settings recovery entry points; re-reads FRESH grants after every callback; emits typed `PermissionFlowState`; **never starts observation**. |
| `MainActivity` | narrow 3.3 wiring | Controller registration + inert no-op state listener. No Start button exists yet (3.5); entry point left intentionally unwired. |

## 2. SDK decision matrix (pure planner, tested)

| API | Scope | Missing | Runtime request |
|---|---|---|---|
| 29–30 | BLE / Wi-Fi | FINE | `[ACCESS_FINE_LOCATION]` only. Legacy BLUETOOTH/BLUETOOTH_ADMIN are manifest install-time (maxSdkVersion=30); **never** runtime-requested. |
| 31+ | Wi-Fi | FINE (± COARSE) | `[ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION]` together in ONE request (user may pick approximate-only in the same dialog). |
| 31+ | BLE | FINE (± COARSE), SCAN | `[ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, BLUETOOTH_SCAN]` in ONE coordinated request; never overlapping launches. |
| 31+ | BLE | only SCAN | `[BLUETOOTH_SCAN]`. |
| any | any | none | No dialog (null plan). |

Never runtime-requested: `ACCESS_WIFI_STATE` (manifest-normal), legacy BLUETOOTH/ADMIN.
Not in the vocabulary at all: `NEARBY_WIFI_DEVICES`, `BLUETOOTH_CONNECT`, background
location. The `neverForLocation` absence decision (2.1) is preserved: beacon
observations are not filtered out.

## 3. Two-tap rule (developer-approved)

1. First Start tap: the flow may show the runtime dialog. No observation starts.
2. Dialog result: the controller re-reads fresh grants, updates typed state
   (`PRECISE` / `APPROXIMATE_ONLY` / `DENIED`) and **consumes** the transient Start tap.
   No scan is queued, replayed or auto-completed — not after grant, cancel, resume,
   Settings return or recreation. `PermissionFlowEffect` has NO start-observation member.
3. Second Start tap (a NEW explicit user action): a fresh plan is computed; if everything
   is granted the flow returns `None` — the actual readiness check + session start is
   task 3.4 wiring, still gated behind the user's explicit tap.

Approximate-only (`APPROXIMATE_ONLY`) is never precise: `isPreciseEnoughForScanning`
is true only for `PRECISE`; FINE-required capabilities stay unavailable with the
`PERMISSION_APPROXIMATE_ONLY` typed issue (3.2) presented in Turkish (3.5/3.7).

## 4. Denial handling

`shouldShowRequestPermissionRationale() == false` alone is NEVER classified as
permanently denied (pure `DenialAssessment` has no such output): denial strength stays
`UNCERTAIN` with honest Settings guidance; rationale=true maps to
`RATIONALE_RECOMMENDED`. No automatic re-prompting, no prompt loops; Cancel leaves a
usable non-observing state. "Permanently denied" may only ever be derived from real
prior-request evidence by future wiring, never from the flag alone.

## 5. Lifecycle semantics

- Launcher registered unconditionally in `onCreate`, stable order; launched only from
  `onUserStartTap` (explicit user action).
- Duplicate/overlapping requests blocked by `pendingRequestScope` (in-flight guard).
- Callback: fresh `checkSelfPermission` re-read (handles revocation between dialog and
  delivery); typed state update only.
- Activity recreation: transient Start intent dropped (`ActivityRecreated` event); the
  in-flight marker intentionally survives so a stale late result is consumed safely
  instead of stacking a second dialog.
- Settings return (`onSettingsReturned`): read-only grant refresh; no dialog, no start.
- Settings navigation guarded with `resolveActivity`; no success claims without runtime
  verification.
- No persisted tokens/intents, no services/receivers/wake locks, no identifier logging.

## 6. 3.5 integration handoff

The Phase 3.5 control screen calls:
- `permissionFlowController.onUserStartTap(PermissionFlowScope.BLE / WIFI)` from the
  Turkish Start buttons (`UiCopyKey.BLE_START_BUTTON` / `WIFI_START_BUTTON`).
- `onStateChange` callback for `PermissionFlowState` → maps to 3.2
  `ObservationUiState` (locationAccess; `PERMISSION_REQUIRED` +
  `SessionIssueKey.PERMISSION_APPROXIMATE_ONLY` / `PERMISSION_DENIED`).
- `openAppPermissionSettings()` / `openLocationSettings()` from explicit user recovery
  actions; `onSettingsReturned()` when the user comes back.
- The second tap path (post-grant) hands over to task 3.4 readiness/session wiring —
  still only ever behind a fresh explicit Start tap.

## 7. UNVERIFIED on real devices

Dialog appearance and approximate-only UX, OEM permission behavior, Settings
navigation success, callback delivery timing, process death mid-dialog, and the whole
observation UX. Passing JVM tests and a green compile prove decision logic and
compilation only.
