# Phase 3.5 — Material 3 Compose Observation Control Screen

Status: implemented 2026-10-21 (code/host level). Real rendering, click delivery,
TalkBack semantics, font scaling, dark mode and OEM behavior are UNVERIFIED without a
device. This document records the screen structure, Turkish string mapping, the
click-to-production path, ownership, the two-tap semantics and the honest limits.

## 1. Screen structure

`RadioObserverControlScreen` (`:app` `ui/ControlScreen.kt`) — a STATELESS Compose
renderer fed with the immutable sanitized `ObservationUiState` (single source of truth)
and four explicit user callbacks. Sections, in order:

1. `control_title` + `home_intro` (3.1 HOME_INTRO) + `scope_note` (radio observation,
   not tracking/identity/distance) + `privacy_body` (PRIVACY_ABOUT_BODY: nothing
   uploaded/persisted/shared; session-local memory only).
2. **Bluetooth gözlemi** card: honest status label, session limit (ONLY from
   authoritative `remainingMs`), typed issue text, `ble_bounded_note` (user-initiated
   bounded scan), Başlat/Durdur buttons.
3. **Wi-Fi gözlemi** card: same structure with `wifi_passive_note`
   (WIFI_PASSIVE_SOURCE_NOTE: passive system results; the app never starts a Wi-Fi scan).
4. Contextual honesty: `precise_location_rationale` while location is not yet granted;
   `approximate_only_hint` (APPROXIMATE_ONLY_HINT) on approximate-only; the sanitized
   observation-count summary (`plurals.observation_count`) or `empty_feed_note`
   (EMPTY_FEED_HONEST_NOTE, shown only after real observation); `background_stop_note`
   (BACKGROUND_STOP_NOTE) at the bottom.

Status/issue text resolution: every `RadioSessionUiStatus` and `SessionIssueKey` maps to
the Turkish table from `docs/phase3-ux-spec.md` section 5, via `res/values/strings.xml`
(the app's standard string-resource mechanism; default locale Turkish, future
translations can add locale folders without new machinery).

## 2. Click-to-production path (only real user clicks)

```
Button onClick (Compose click callback only)
  -> MainActivity setContent wiring: sessionPresenter::onUserStartBleTap / onUserStopBleTap
     / onUserStartWifiTap / onUserStopWifiTap   (3.4 production presenter)
  -> 3.3 permission path (if FINE/BLUETOOTH_SCAN missing) or 3.4 orchestrator gates
  -> RadioObservationSupervisor -> real Android BLE / passive Wi-Fi sources
```

- Enable/disable rules come from the pure-JVM `ControlScreenProjection` (host-tested):
  Start is enabled for every status except OBSERVING/STOPPING (so PERMISSION_REQUIRED and
  BLOCKED stay retryable and the permission flow is always reachable); Stop only while
  active/stopping. Repeated taps cannot double-start.
- No action is dispatched from composition, recomposition, initialization, resume,
  navigation, permission callbacks, Settings return or recreation. Compose itself was
  NOT host-click-tested (no Robolectric/Compose test facility is configured and none
  could be added); single-dispatch-per-click relies on Compose `onClick` semantics and is
  compile-verified only.
- The session limit line shows ONLY the authoritative `remainingMs` written at session
  start (real spec bound). No ticking countdown is fabricated.

## 3. State observation and ownership

- `MainActivity` holds an Activity-scoped `mutableStateOf(ObservationUiState)` — the
  Compose state source. Session statuses arrive ONLY via the 3.4 orchestrator's
  `onUiState` callback; the 3.3 `PermissionFlowController.onStateChange` refreshes ONLY
  the `locationAccess` field (that callback structurally cannot start anything, and a
  grant alone changes no session status: the two-tap rule stands).
- Activity recreation: a new holder, new orchestrator, new permission controller — the
  screen starts from `ObservationUiState.initial()`; nothing persists and nothing
  auto-restarts. The `RequestMultiplePermissions` registration stays unconditional in
  `onCreate` (unchanged 3.3 property).
- Foreground loss flows through the existing supervisor stop (FOREGROUND_LOST state
  shown); the state renders honestly without resurrection.

## 4. Accessibility basics (Task 3.9's full audit is future work)

- All Start/Stop buttons enforce `heightIn(min = 48.dp)` touch targets.
- contentDescription per ux-spec: action + radio name + current status on every control
  button; status rows announce "Durum: ..." via semantics.
- Strings live in resources; `plurals` used for the count; no hardcoded UI text remains
  in the composable (beyond resource keys).

## 5. Privacy invariants preserved

Sanitized state only (no raw addresses/BSSIDs/payloads/parse-issue text reach the UI);
Wi-Fi described strictly as passive; empty feed never implies absence; approximate-only
never satisfies FINE; BLE stays bounded (20 s default) via the existing engine; no new
storage/network/logging. `:core:radio` remains pure JVM (projection has zero android.*).

## 6. Tests and their honest level

- `ControlScreenProjectionTest` (pure JVM, :core:radio, 8 tests): status→enabled rules
  distinct and non-conflated (G); permission flow reachable (D); duplicate-start
  prevention (F); blocked/error never observing (E); session limit only from real state
  (K); sanitized count (I); empty-feed note only after real observation (J/A); per-radio
  independence (C). These cover STATE→PRESENTATION logic only.
- All 199 core JVM tests pass (incl. 191 prior). `:app:testDebugUnitTest` runs 0 tests
  (no app-side host test facility configured; none added).
- NOT host-tested: real Compose click dispatch, recomposition behavior, TalkBack,
  layout/font-scale/dark-mode, permission dialog appearance, radio reception, OEM
  lifecycle. All remain UNVERIFIED without a device.

## 7. Handoff to 3.6

The observation feed/detail screens (3.6) will reuse `ObservationUiState.rows`
(sanitized `ObservationRowUi` list already flowing) and the 3.1 copy keys
(CONFIDENCE_CAUTION, SIGNAL_QUALITATIVE_NOTE, freshness announcements). Nothing in the
control screen prevents or duplicates that scope.
