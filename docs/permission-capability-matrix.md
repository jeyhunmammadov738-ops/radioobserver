# Radio Observer — Platform & Permission Capability Matrix (Task 2.1)

**Date:** 2026-10-19
**Status:** Phase 2 task 2.1 deliverable. Auditable decision matrix; verified against the
official Android Developers documentation (URLs at the end, inspected 2026-10-19).
**Project SDK range (from build files):** minSdk 29, targetSdk 35, compileSdk 35.

## Approved policy decisions (developer, 2026-10-19)

1. **BLE `neverForLocation` is NOT asserted.** Rationale: beacon observation coverage is
   prioritized. The official docs warn that asserting `neverForLocation` on
   `BLUETOOTH_SCAN` filters some BLE beacons out of scan results. Consequence (accepted):
   the app may require `ACCESS_FINE_LOCATION`, and radio observations can potentially
   reveal proximity/location of nearby devices. The app must disclose this honestly and
   must NOT derive, retain, or transmit geographic location.
2. **Background operation: OFF.** No background/always-on scanning, auto-start, boot
   receiver, foreground service, or scheduled background work. User-visible, user-initiated,
   time-bounded observation only (scanner adapters arrive in a later, separately authorized
   task: 2.3/2.4).
3. **Wi-Fi is receive-only.** The app listens for `SCAN_RESULTS_AVAILABLE_ACTION`
   broadcasts (which, on Android 10+, fire for scans initiated by the platform or other
   apps) and reads cached results via permission-gated `getScanResults()`.
   `WifiManager.startScan()` is NEVER called by this app.
4. **`NEARBY_WIFI_DEVICES` is NOT adopted** as a substitute for location permission for
   scan results: per the official Wi-Fi permissions guide, several Wi-Fi APIs — explicitly
   including `getScanResults()` — require `ACCESS_FINE_LOCATION` **even when the app targets
   Android 13+**. No manifest permission is added in this iteration.

## Two requirement dimensions (kept separate by design)

- **Device runtime API level** — determines which permissions the OS actually enforces at
  runtime (rows below).
- **App targetSdk (currently 35)** — determines the declaration set the app must carry.
  With targetSdk ≥ 31 the manifest must declare `BLUETOOTH_SCAN` (plus
  `ACCESS_FINE_LOCATION` since `neverForLocation` is not asserted) and should carry legacy
  `BLUETOOTH`/`BLUETOOTH_ADMIN` declarations with `android:maxSdkVersion="30"` for
  compatibility with API 29–30 devices. Actual declarations happen in a later approved
  iteration; nothing is declared yet.

## Matrix by device runtime bucket (enforced behavior)

Legend for permission cells: runtime = dangerous permission requiring user grant at runtime;
normal = install-time permission. "LS on" = system Location services enabled.

### BLE observation (`BluetoothLeScanner` scan results)

| Requirement | API 29–30 (Android 10–11) | API 31–32 (Android 12–12L) | API 33+ (Android 13+) |
|---|---|---|---|
| Permissions needed | `BLUETOOTH` (normal), `BLUETOOTH_ADMIN` (normal), `ACCESS_FINE_LOCATION` (runtime) | `BLUETOOTH_SCAN` (runtime), `ACCESS_FINE_LOCATION` (runtime; kept because `neverForLocation` is NOT asserted) | same as API 31–32 |
| Location services | required (conservative; location-bearing scan results) | required (conservative; scan results are location-derived without `neverForLocation`) | same |
| Bluetooth radio on | required | required | required |
| Hardware absence | `BluetoothAdapter` absent → BLE unsupported | same | same |
| Denied vs permanently denied | user may re-prompt vs must route to settings | same (runtime `BLUETOOTH_SCAN`) | same |

### Wi-Fi system scan-result observation (`getScanResults()`, passive)

| Requirement | API 29–30 | API 31–32 | API 33+ |
|---|---|---|---|
| Permissions needed | `ACCESS_WIFI_STATE` (normal), `ACCESS_FINE_LOCATION` (runtime) | same | same (per official docs `getScanResults()` still requires `ACCESS_FINE_LOCATION` even at targetSdk 33+; `NEARBY_WIFI_DEVICES` alone is NOT sufficient for it) |
| Location services | required | required | required |
| Wi-Fi radio on | required for active interfaces; cached results may exist when off | same | same |
| Hardware absence | no Wi-Fi adapter → unavailable | same | same |
| Update signal | `SCAN_RESULTS_AVAILABLE_ACTION` broadcast fires for platform/other-app scans; boolean extra reports whether results were updated | same | same |
| Staleness | results are "the most recently updated results, which may be from a previous scan" — always treat as possibly cached/stale; record age marker | same | same |

## State model outcomes (consumed by task 2.5)

Capability evaluation distinguishes, per radio and bucket:
`READY` / `MISSING_PERMISSION(denied)` / `MISSING_PERMISSION(permanently denied)` /
`RADIO_DISABLED` / `LOCATION_SERVICES_DISABLED` / `HARDWARE_UNAVAILABLE` — all combinable
(blocked set), and all host-testable without Android.

## Required user disclosures (honesty obligations)

- BLE scan results can potentially reveal proximity/location of nearby devices; observing
  them requires fine-location permission (and, conservatively, enabled Location services).
- Wi-Fi scan results imply coarse location of the observer; reading them requires
  fine-location permission and enabled Location services; results may be cached/stale.
- Observation is user-initiated, time-bounded, and has NO background/always-on mode.
- Vendor signatures are ecosystem hints only; no tracker/threat labels; RSSI is not distance.

## Limitations — explicitly UNVERIFIED

- All rows reflect documented platform behavior, NOT verified runtime behavior of any
  device. Real-device BLE/Wi-Fi reception, permission prompts, and OS gating are
  unverified until a separately authorized device-validation effort.
- The BLE "location services required" rows are the conservative reading; if a device
  returns results with Location off, the model stays conservative (worst case).
- OEM-specific scan throttling and broadcast cadence are outside this matrix's control.

## Sources (official Android Developers, inspected 2026-10-19)

- Bluetooth permissions (incl. `neverForLocation` filtering + `maxSdkVersion=30` legacy set):
  https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Find BLE devices (scanner API): https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices
- Wi-Fi scanning (API-29+ passive broadcast semantics; cached results; throttling):
  https://developer.android.com/develop/connectivity/wifi/wifi-scan
- Wi-Fi permissions (`getScanResults()` requires `ACCESS_FINE_LOCATION` even targeting 13+):
  https://developer.android.com/develop/connectivity/wifi/wifi-permissions
