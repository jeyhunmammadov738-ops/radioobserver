# Radio Observer — Phase 3 Proposal: Friendly Compose UI & Live Observation State

**Prepared:** 2026-10-20  
**Status:** DEVELOPER-AUTHORIZED PLANNING PROPOSAL ONLY — NOT APPROVED FOR IMPLEMENTATION  
**Project:** Radio Observer (`com.hjinlabs.radioobserver`)  
**Historical baseline:** Phase 1 5/5, Phase 2 8/8 *code/host-level*, Phase 3 not started; last reported Phase 2 remediation commit `65ecda1`. Confirm all facts against the repository before any action.  
**Execution environment:** Linux/bash; no device, emulator, adb, instrumentation, external backend, Docker or VPS.

## 1. Objective

Turn the existing **inert** Phase 2 observation foundation into a comprehensible, privacy-preserving, **explicitly user-controlled** Jetpack Compose experience. The app should let a foreground user request permissions in context, intentionally start and stop bounded BLE and passive Wi-Fi observation sessions, view cautiously labeled observations, understand permission/hardware failures, and dispose of transient observation data safely.

**Not release-ready by virtue of a green host build.** Bluetooth reception, Wi-Fi broadcast delivery, permission-dialog details, system lifecycle timing, OEM differences, and UI behavior on a real phone remain **UNVERIFIED** under this protocol.

### Boundaries inherited from Phases 1–2

- Keep `:core:radio` **pure Kotlin/JVM** (`android.*` imports remain absent). Android APIs only in `:app` boundary layers; reuse the existing domain models, coordinators, capability probes, lifecycle bridge, and `ObservationPipeline` instead of duplicating business rules.
- Application ID remains `com.hjinlabs.radioobserver`. No incidental changes to `namespace`, signing, versionCode/versionName, Kotlin/AGP/Gradle versions, or dependencies.
- BLE: **observation-only**, time-bounded, no pairing/connection/GATT/advertising. Historical default session **20 seconds**, strict upper bound **10 minutes**; actual contracts prevail if repository differs.
- Wi-Fi: **system-provided scan-result observer only**; absolutely no `WifiManager.startScan()`, `WifiScanner`, Wi-Fi configuration or connection. Historical default **30 seconds**. New results are not guaranteed; cached/stale/unknown remains honest.
- **RESUMED-only** eligibility. Pausing/losing foreground closes active sessions; returning to foreground does not restart them. No boot, foreground/background service, WorkManager scanning, wake lock, alarm, or battery-optimization bypass.
- **Per-radio single session**; one BLE and one Wi-Fi session can coexist if existing contracts allow. Never silently change concurrency policy.
- Existing `ObservationPipeline`: maximum **200** observations and **15-minute lazy TTL**, session-local ID, bounded in-memory retention and `closeAndDispose`; use *only* sanitized `PublicObservation` in UI presentation. No raw BLE address, Wi-Fi BSSID, raw payload, parse-issue text, files, logs, backend, analytics, or exports.
- Confidence is conservative: Apple/Samsung manufacturer hints do **not** prove AirTag/SmartTag identity; RSSI buckets do **not** measure real distance. Do not use “danger”, “tracker detected”, “verified AirTag” or a fake proximity distance label.

## 2. Proposed app information architecture

| Screen or surface | Purpose | Core states / requirements |
|---|---|---|
| **Home / Observation Control** | Explains the feature; two distinct **Start BLE** and **Observe Wi-Fi** actions, individual Stop actions, time left and radio status | Inactive, checking, permission needed, observing, ending, ended, unavailable, error; no auto-start |
| **Observations** | Combined **ephemeral** feed with BLE/Wi-Fi filter | Empty (not necessarily “nothing nearby”), observing, stale/unknown data, recent results, expired/evicted results; sanitized view only |
| **Observation Details** | Confidence, manufacturer/evidence hints, RSSI category where applicable, freshness, provenance | Explain uncertain identity, signal limitations, age source; never expose raw stable identifiers |
| **Permissions & Readiness** | In-context rationale; platform permission requests; recovery guidance for hardware/location toggles | Not granted, approximate-only, denied, rationale needed, settings guidance, Bluetooth/Wi-Fi/location disabled, unsupported hardware |
| **Privacy & About** | Explain no uploads/storage/background tracking, what a scan does/does not prove, cache limitations | Plain language; link to existing policy/help text; no false safety assurance |

Design direction: professional Material 3, unobtrusive status components, clearly labeled radio icons, readable typography, accessibility labels and large hit targets, single-column phone layout with a simple responsive wide-screen adaptation. No visual polish should conceal the distinction between “scanning”, “observed”, and “confirmed”.

## 3. Phase 3 proposed tasks (ALL DRAFT / UNCHECKED)

### 3.1 UX contract, navigation and copy inventory
- **Deliverable:** `docs/phase3-ux-spec.md` describing screens, navigation flow, all user-facing state/copy tables, dark/light handling and contrast strategy; optional *text* wireframes/previews; define accessibility labels.
- **Host acceptance:** Complete flow map for start/stop, permission denied/approximate, no hardware, no data, Wi-Fi cached data, scan timeout, app pause, navigation and screen recreation. No definitive tracker claims or physical-distance copy.
- **Risks:** UI may imply security assurances or “live devices” when Wi-Fi cache is stale; prevent via copy requirements.
- **Non-goals:** No implementation or new design-system dependency in this task.

### 3.2 UI state contracts and presentation mapper (JVM-first)
- **Deliverable:** Typed immutable `ObservationUiState` and user-intent/event contracts plus mapper from **public/sanitized** models. Separate BLE and Wi-Fi session states; explicit source/freshness/confidence fields; stable list item identity **only for current session**.
- **Host acceptance:** JVM tests cover empty/running/denied/error/stale/unknown/expired, separate radios, no unsupported threat labels, and no hidden raw identifier fields. Recomposition must never cause a scan.
- **Risks:** Mixing raw storage records with UI DTOs; do not expose domain internals or sensitive fields.
- **Non-goals:** No runtime permissions, Activity listeners or scanning side effects.

### 3.3 Runtime permission and settings flow
- **Deliverable:** User-initiated Android permission orchestration via existing platform APIs (Activity Result contracts as appropriate), keeping the actual Phase 2 readiness probe authoritative. In-context rationale and deliberate settings recovery.
- **Host acceptance:** API 29–30 / 31–32 / 33+ decision matrix; Android 12+ request **ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION together** when precise access is needed; `BLUETOOTH_SCAN` requested on API 31+ when needed. Approximate-only must **not** be equated with FINE; permission denial does not silently start or retry scanning. **Do not infer permanently denied solely from `shouldShowRequestPermissionRationale() == false`**; use real prior-request history and conservative fallback, or present noncommittal settings guidance. Reprobe on return from settings.
- **Risks:** In Android 12+ fine→coarse downgrade the process may restart. Request APIs only from user event, not composition/render effects. Validate Bluetooth adapter state API access without expanding to `BLUETOOTH_CONNECT` by assumption; if actually required, stop and seek separately authorized permission decision.
- **Non-goals:** No background location; no unapproved manifest permissions; no automatic start immediately on permission grant absent a still-valid deliberate action (prefer explicit second Start tap for clarity).

### 3.4 Foreground observation session integration
- **Deliverable:** One lifecycle-bound orchestrator/view-model/presenter connects user Start/Stop intents to existing BLE and passive Wi-Fi sources, `RadioObservationSupervisor`, permission probes and `ObservationPipeline`. Wire results only after legal gates. Release all callbacks/handles on stop, pause and close.
- **Host acceptance:** JVM fakes verify no scan before explicit tap; per-radio concurrency rules; state transitions; timeout/cancel/error/foreground loss; late callbacks ignored; reentry/rotation/process recreation cannot silently restart observing or retain old session data. Reuse Phase 2 boundaries rather than creating second coordinators.
- **Risks:** Activity lifecycle bridge currently drives eligibility but sources historically inert; double observers, permission races and leaked references are priority checks.
- **Non-goals:** No background monitoring or auto-resume; no active Wi-Fi scanning; no data export.

### 3.5 Material 3 Compose control screen
- **Deliverable:** Real Home/Control Compose screen replacing the existing placeholder, with BLE and Wi-Fi cards, explicit Start/Stop, session indicators, bounded duration display, persistent privacy disclaimer, readiness states and navigation.
- **Host acceptance:** Compiles, previews render where feasible (previews are not runtime proof); pure UI state previews for inactive/running/blocked; gestures produce only intents into presenter, not framework scanning directly; no invented demo observations in production paths.
- **Risks:** Loading states and countdowns may imply continuous background scanning; show stopping/timeout behavior accurately.
- **Non-goals:** No services, complex theme frameworks or dependency upgrades by default.

### 3.6 Sanitized observation feed and detail presentation
- **Deliverable:** Read-only list with source filter, last-observed marker, confidence and freshness, meaningful empty/error states; accessible details with evidence caveats; bounded list compatible with existing TTL/capacity.
- **Host acceptance:** Presentation tests prove raw address/BSSID/payload/parse-issues cannot reach screen DTOs or semantics; unknown or cached Wi-Fi never shown as confirmed fresh; RSSI labeled qualitative not meters; no AirTag/SmartTag certainty.
- **Risks:** Accidental stable identity via row keys or masked identifiers; use session-local IDs only. No sorting that corrupts newest-wins semantics.
- **Non-goals:** History, account, CSV, cloud, map, track-person or threat alarm.

### 3.7 Clear recovery, errors and honest status UI
- **Deliverable:** Typed explanations/actions for no hardware, radio/location disabled, denied vs unknown restriction, SecurityException, scan throttling, timeout, empty Wi-Fi cache, stale results and permission revoke. User-initiated Settings link where appropriate.
- **Host acceptance:** Table-driven JVM mapping tests across supported API groups, denied/approximate-only, paused, cached and empty; no generic “no trackers found” on zero observations; no repeat-request loops.
- **Risks:** `shouldShowRequestPermissionRationale` does not alone prove permanent denial; do not claim it does.
- **Non-goals:** No attempts to defeat Android background, throttling or privacy protections.

### 3.8 Lifecycle ownership, privacy teardown and state restoration
- **Deliverable:** Review actual ownership across Activity, lifecycle bridge, state holder, adapters, pipeline and Compose destination; ensure no duplicate source registration and that all transient private data are disposed appropriately. Save only non-sensitive view preferences if needed.
- **Host acceptance:** JVM tests for pause-before-permission-result, app navigation, repeated stop, timeout race, Activity recreation and new-process default inert state; never restore an active scan or retained raw results automatically.
- **Risks:** A `ViewModel` can outlive Activity recreation; do not mistake ViewModel survival for foreground permission to continue or reuse a closed session.
- **Non-goals:** Persistent observation history or background process recovery.

### 3.9 UX quality, accessibility, layout and localization
- **Deliverable:** Material 3 polish, small/large screen arrangements, adequate touch target and status announcements, content descriptions, readable contrast; one coherent default language, with string resources prepared for localization if within actual project conventions.
- **Host acceptance:** Reviewable composable previews/static resources and host-testable UI copy/state rules; configured lint passes. Do **not** mark touch behavior, TalkBack, visual fidelity or OEM layout as device-verified under this protocol.
- **Risks:** Instrumentation Compose UI tests may require a device/emulator; they are **not allowed**. Host-only tests are acceptable if already configured; do not add new test infrastructure without authorization.
- **Non-goals:** Device testing, ad SDK, subscriptions, analytics, background notifications or scope creep.

### 3.10 Device-free final quality gate and Phase 3 handover
- **Deliverable:** Fresh final-tree `:core:radio` JVM tests, any applicable app host unit tests, Android compile and debug APK assembly, configured lint/static analysis, merged manifest/permission inspection, and `docs/phase3-handover.md` with criterion-by-criterion evidence. Actual tasks must be discovered on disk.
- **Host acceptance:** Required checks exit 0 (including configured error-level lint); all Phase 3 DoD proven at **code/host level**; explicit UNVERIFIED register for permission dialog, live BLE/Wi-Fi reception, OEM broadcasts, physical pause behavior, Compose runtime usability. `roadmap.md` evidence and counts consistent before declaring phase complete.
- **Risks:** Green compilation cannot establish real-device UI, background behavior or runtime Bluetooth reliability. Any failed required gate is blocking, not “warnings-only PASS”.
- **Non-goals:** Shipping/publishing, version bump, Play Console release or new phases.

## 4. Recommended order and checkpoints

**Order:** `3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6 → 3.7 → 3.8 → 3.9 → 3.10`.

- **Checkpoint A (3.1–3.2):** UX and data/state semantics frozen before touching permission or scanner code.
- **Checkpoint B (3.3–3.4):** Gate, permission and session integration verified with host fakes before connecting buttons.
- **Checkpoint C (3.5–3.7):** Compose surfaces and recovery states complete without inventing device results.
- **Checkpoint D (3.8–3.10):** Resource ownership, accessibility and final code-level delivery.

Implementation tasks are **not authorized** by this planning proposal. Each implementation tranche requires separate explicit developer approval and Build/Git mode decisions.

## 5. Suggested privacy-safe UI copy (proposal, not source code)

| Context | Suggested copy |
|---|---|
| Home introduction | “Observe nearby Bluetooth signals and available system Wi-Fi results while this screen is active.” |
| BLE start | “Start Bluetooth observation” |
| Wi-Fi start | “Observe available Wi-Fi results” |
| Wi-Fi source note | “Wi-Fi results come from the system and may be old or unavailable. This app does not start Wi-Fi scans.” |
| Precise location rationale | “Android requires precise location permission for these scan results. We don't show your geographic position or upload results.” |
| Approximate only | “Approximate location is enabled, but this observation feature requires precise access. You can change your choice in settings.” |
| No observations | “No observations to show in this session. This does not prove that no devices are nearby.” |
| Confidence | “Possible device family based on a signal pattern; identity is not verified.” |
| Signal strength | “Signal category is a rough radio hint, not an accurate distance.” |
| Background behavior | “Observation stops when you leave the active screen. It won't restart on its own.” |

Actual localization and phrasing should be reviewed with the developer during Task 3.1.

## 6. Planning-stage acceptance and authority

A Phase 3 planning iteration is complete when an OpenCode report shows that it:
1. Read the **actual** `roadmap.md` and Phase 2 handover before editing.
2. Captured read-only Git and staged-index baselines and verified current applicationId and source boundaries.
3. Created `docs/phase3-proposal.md` (or an established equivalent) preserving the 10 proposed task IDs, names, order, scope, risks, non-goals and code/host acceptance.
4. Preserved Phase 1 and Phase 2 checkboxes and evidence, left Phase 3 **NOT STARTED / NOT IMPLEMENTATION AUTHORIZED**, and at most added a non-task document link in `roadmap.md`.
5. Changed no source, manifest, dependency, version, signing or runtime behavior; ran no build because documentation-only and final code tree unchanged.
6. Performed **NO** Git commit/push (unless the developer supplies a separate explicit, current-iteration Git authorization).
7. Returned a **lossless** roadmap snapshot and changed-file report, along with unresolved decisions before coding.

### Developer decisions required after planning (not assumed by OpenCode)

- Explicit approval of Phase 3 task list and which first implementation tranche to authorize.
- Confirm the default in-app language and exact UI wording; propose English as draft text if no preference exists.
- Confirm whether returning from the runtime permission dialog requires a second Start press (recommended) to prevent implicit scanning.
- Confirm whether app's public results remain **fully masked** with no “reveal raw address” control (recommended).

## 7. Official Android references checked 2026-10-20

- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Runtime location and approximate access: https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- Wi-Fi `getScanResults()` gates: https://developer.android.com/develop/connectivity/wifi/wifi-scan
- Request app permissions: https://developer.android.com/training/permissions/requesting
- Activity Result contracts in Compose: https://developer.android.com/develop/ui/compose/libraries
- State ownership in Compose: https://developer.android.com/develop/ui/compose/state-hoisting
- Lifecycle cleanup in Compose: https://developer.android.com/develop/ui/compose/side-effects
- Compose testing and its device limitations: https://developer.android.com/develop/ui/compose/testing

---

## Appendix: repository verification of this proposal (2026-10-20 planning run)

All historical facts cited above were verified against the actual repository before this
document was placed at `docs/phase3-proposal.md`:

- HEAD `65ecda1` (`main`), working tree and index clean at the start of this run.
- `applicationId com.hjinlabs.radioobserver`; versionCode 1 / versionName 0.1.0-phase1.
- BLE default session 20 s (`BleSessionCoordinator.bleDefault`), Wi-Fi default 30 s
  (`WifiSessionCoordinator.wifiDefault`), strict session ceiling 10 min
  (`ObservationContracts.MAX_SESSION_DURATION_MS`).
- `ObservationPipeline`: 200-entry capacity, 15-minute lazy TTL (verified constants).
- Phase 1 5/5 and Phase 2 8/8 code/host-level per on-disk `roadmap.md`; Phase 3 section
  is a placeholder ("detailed tasks NOT YET AUTHORIZED"); `docs/phase2-handover.md` exists.
- `MainActivity` remains functionally inert for observation start/stop and runtime
  permissions (no permission launchers or session-start references).
- No source, manifest, dependency, version or signing file was modified in this planning
  iteration; no build/test/lint run was performed (Build Mode OFF, code tree unchanged).

This planning iteration performed no implementation: all ten tasks remain DRAFT,
unchecked, and NOT APPROVED FOR IMPLEMENTATION.
