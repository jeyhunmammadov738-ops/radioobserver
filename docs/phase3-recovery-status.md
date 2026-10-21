# Phase 3.7 — Clear Recovery, Errors & Honest Status UI

Status: implemented 2026-10-21 (code/host level). Real Settings launches, permission
dialogs, TalkBack and Compose runtime are UNVERIFIED without a device.

## 1. State-to-action matrix (host-tested, pure RecoveryStatusProjection)

| Sanitized state (per radio) | Turkish explanation | Single action | Rationale |
|---|---|---|---|
| PERMISSION_REQUIRED/BLOCKED + PERMISSION_DENIED | recovery_permission_needs_setting | İzin ayarlarını aç | Honest: settings is the supported path; grant never auto-starts |
| + PERMISSION_APPROXIMATE_ONLY | recovery_approximate_only_needs_setting | İzin ayarlarını aç | Approximate-only never satisfies FINE |
| BLOCKED + LOCATION_SERVICES_OFF | recovery_location_services_need_setting | Konum ayarlarını aç | A Settings problem, not a permission grant problem |
| BLOCKED + BLUETOOTH_OFF / WIFI_OFF | recovery_radio_off_recheck | Yeniden kontrol et | App never toggles radios; user changes system state, app re-reads |
| BLOCKED + SCAN_THROTTLED / SECURITY_EXCEPTION | recovery_system_limit_recheck | Yeniden kontrol et | Re-read current facts; never a scan |
| BLOCKED + HARDWARE_UNSUPPORTED, FOREGROUND_LOST, INTERNAL_ERROR; IDLE/CHECKING/STOPPED/TIMED_OUT/ERROR; OBSERVING/STOPPING | — (issue/status text only) | none | No supported platform action; honest inaction |
| NOT_REQUESTED (no session yet) | (3.5 rationale text) | none | Start tap routes through the 3.3 two-tap flow; a banner here would invite implicit prompting |

No "permanently denied" concept exists anywhere (rationale=false proves nothing);
denial strength never overstates. One action max per state; never a color-only cue;
48dp targets; a11y contentDescription "Kurtarma eylemi: <eylem>, <radyo>, düğme".

## 2. Call paths (all genuinely reachable)

- "İzin ayarlarını aç" → ControlScreen RecoverySection onClick → MainActivity lambda →
  PermissionFlowController.openAppPermissionSettings() (existing guarded
  ACTION_APPLICATION_DETAILS_SETTINGS; resolveActivity guard; returns Boolean, no
  success claim).
- "Konum ayarlarını aç" → same → openLocationSettings() (existing guarded
  ACTION_LOCATION_SOURCE_SETTINGS).
- "Yeniden kontrol et" → per-radio lambda → ObservationSessionPresenter
  onUserRecheckBleTap/onUserRecheckWifiTap → fresh AndroidPermissionGateway snapshot +
  capability probe evaluation → ObservationSessionOrchestrator.refreshReadinessStatus()
  (NEW, narrow): publishes the derived non-running status; no-op while live or
  unchanged; require() forbids fabricating OBSERVING/STOPPING.

## 3. Two-tap and no-auto-start invariants

Recheck NEVER prompts a dialog, never starts/stops a session (ObservationRecheckTest).
Recovery buttons never trigger Start; Start/Stop controls unchanged (3.5 projection
rules intact). Permission grant / Settings return / resume: MainActivity.onResume now
calls the existing PermissionFlowController.onSettingsReturned() (read-only
locationAccess refresh; identified integration gap — previously never invoked) and
onCreate-on-recreation calls onActivityRecreated() (transient Start dropped). Neither
starts anything; a NEW explicit Start with fresh gates remains the only start path.
No new launcher, no conditional registration, no Composable side effect.

## 4. Independence, freshness and privacy

Per-radio projection (host-tested: live BLE + blocked Wi-Fi and inverse); one radio's
failure never marks the other. Errors typed from SessionIssueKey only; no raw
identifiers/parser text anywhere in messages or semantics (projection carries semantic
keys; Turkish text in :app resources). Pipeline bounds/identity/invalidation untouched
(3.6 behavior preserved). No active Wi-Fi scan APIs, no new services, no persistence.

## 5. Tests and honest limits

New JVM tests (12): RecoveryStatusProjectionTest (7: A denied/approximate→settings;
B no permanent-denial vocabulary + rationale-independent mapping; C/D radio-off recheck
vs location-settings; E independence; F terminal/idle/live→no banner; G distinct
actions; L no tracker/distance vocabulary) and ObservationRecheckTest (5: refresh
without source start; blocked→ready→IDLE without touching the other radio; no-op while
OBSERVING; idempotent repeated clicks; cannot fabricate activity). Total 218/218.
App JVM tests: 0 (no facility). UNVERIFIED: real Settings launches, dialogs, TalkBack,
text scaling, dark mode, radio reception, OEM lifecycle. Presenter/MainActivity wiring
is compile-verified only.

## 6. 3.8 handoff

Lifecycle ownership/disposal rework remains task 3.8, not started, requiring separate
approval.
