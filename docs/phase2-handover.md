# Phase 2 Handover — Scanner Abstractions, Permission Flow, Lifecycle & Privacy

Status: Phase 2 CODE-LEVEL COMPLETE (2026-10-19). Quality gate run fresh on final tree
(HEAD b793503): 143/143 JVM tests, :app:compileDebugKotlin, :app:assembleDebug all
passing. Real-device behavior remains UNVERIFIED (see §6).

## 1. Task-by-task evidence matrix

| Task | Summary | Code-level evidence |
|---|---|---|
| 2.1 | BLE/Wi-Fi permission & capability matrix | `docs/permission-capability-matrix.md`; FINE-location gates on all supported levels; Wi-Fi cached-results disclosure; `neverForLocation` deliberately absent (documented). |
| 2.2 | Bounded, typed, single-session-per-radio contracts | `ObservationContracts.kt`: 10-min hard ceiling (`MAX_SESSION_DURATION_MS`), typed errors, idempotent handles; no pairing/connect/advertise/Wi-Fi-scan-initiation API anywhere. |
| 2.3 | BLE adapter | `BleSessionCoordinator` + `:app/ble/`: 20-s default, permission/foreground fail-closed gates, idempotent stop, late-callback rejection, conservative manufacturer classification (never identity proof). |
| 2.4 | Receive-only Wi-Fi observer | `WifiSessionCoordinator` + `:app/wifi/`: `getScanResults()` only — ZERO `WifiManager.startScan()`/`WifiScanner` calls; RECEIVER_NOT_EXPORTED session receiver, cleanup verified; boot-domain timestamp/age honesty; cached `updated=false` never "new"; executor off main thread; 30-s default. |
| 2.5 | Readiness/permission model | `ObservationReadinessModel`: PERMANENTLY_DENIED only from caller-injected evidence; hardware/radio/location/foreground gates; no fabricated readiness. |
| 2.6 | Foreground lifecycle & battery policy | `RadioObservationSupervisor` (pure JVM): RESUMED-only eligibility default FALSE; loss of RESUMED terminates both sessions; no auto-resume; cross-radio independence; idempotent cleanup; no FGS/WorkManager/boot/wakelock anywhere (source-scanned). Decision record: `docs/phase2-lifecycle-battery-policy.md`. |
| 2.7 | Privacy pipeline | `ObservationPipeline`: 200-entry cap, 15-min lazy TTL, newest-wins dedup, terminal disposal with late-event rejection; `PublicObservation` structurally cannot expose raw addresses/BSSIDs/payloads/issue texts; ordinal IDs not identifier-derived; Wi-Fi freshness only from valid boot-relative age; cached never FRESH. Decision record: `docs/phase2-observation-privacy-policy.md`. |
| 2.8 | Device-free quality gate & handover | This document; fresh gate results in §2; audit greps in §3. |

## 2. Fresh quality gate (final tree, this iteration)

- `timeout 15m ./gradlew :core:radio:test --rerun-tasks --no-daemon` → EXIT 0, **143/143, 0 failed, 0 skipped** (12 suites: parsers 9+9, factory 5, BLE coordinator 18, contracts 14, pipeline 18, readiness 14, store 8, supervisor 14, buckets 5, Wi-Fi classifier 8, Wi-Fi coordinator 21).
- `timeout 15m ./gradlew :app:compileDebugKotlin --no-daemon` → EXIT 0.
- `timeout 15m ./gradlew :app:assembleDebug --no-daemon` → EXIT 0.
- `timeout 15m ./gradlew :app:lintDebug --no-daemon` → EXIT 1: **1 error, 11 warnings (pre-existing, see §5)** — not a regression; fixing it is prohibited in 2.8 scope (would add a permission).

## 3. Cross-layer audit findings

- `:core:radio` retains ZERO `android.*` imports (source-scanned).
- Manifest/APK permission set unchanged: BLUETOOTH_SCAN, BLUETOOTH(≤30), BLUETOOTH_ADMIN(≤30), ACCESS_FINE_LOCATION, ACCESS_WIFI_STATE (+AGP dynamic-receiver permission). No service/receiver/FGS/boot components. applicationId `com.hjinlabs.radioobserver`, versionCode 1, versionName 0.1.0-phase1.
- No file/network/logging/persistence path in `:core:radio` or the scanning/lifecycle/pipeline code (source-scanned); no WorkManager/JobScheduler/alarm/wakelock.
- BLE `BluetoothLeScanner.startScan()` is the ONLY scan initiator (2.3, deliberate); Wi-Fi has NO initiator.

## 4. Deliberate deferred items (Phase 3 scope, NOT implemented)

- No UI, no start button, no runtime permission dialogs: observation cannot be user-started yet; sources, supervisor, pipeline are inert until wired. Activity lifecycle→eligibility IS live (MainActivity), but nothing starts a session.
- No persistence/export/telemetry — by design (local-first).

## 5. Known open items (require developer decision; not fixed in 2.8)

1. **Lint CoarseFineLocation (ERROR)**: manifest declares FINE without COARSE. The 2.1 policy deliberately requests FINE-only; lint/Play policy prefers both (Android 12+ approximate-grant nuance). Adding ACCESS_COARSE_LOCATION is a NEW permission → prohibited in 2.8; decision needed before Play release.
2. Lint warnings (informational): OldTargetApi(35), newer Gradle/AGP/Kotlin/Compose versions available, MissingApplicationIcon, DataExtractionRules, InlinedApi(BLUETOOTH_SCAN constant on min 29 — inlined constant, safe pattern already guarded at runtime).

## 6. UNVERIFIED on-device (honest limits)

Real BLE/Wi-Fi reception quality, scan/broadcast cadence per OEM, permission-dialog UX,
screen-off/multi-window transitions, Android lifecycle callback delivery on specific builds,
and Play publishing requirements are all UNVERIFIED in this device-free protocol. Host
tests validate policy logic and contracts, not OS behavior.

## 7. Minimum developer decision before Phase 3

Approve the Phase 3 plan (UI + explicit user-start + runtime permission flow), and decide
the CoarseFineLocation manifest question (add COARSE vs lint baseline) — recording it in the
permission matrix doc if changed.
