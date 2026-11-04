# Workstream 07 — GPS, Remote ID and Maps Feasibility Study (research-only, 2026-11-04)

**Status: RESEARCH COMPLETE — NO IMPLEMENTATION AUTHORIZED.** Build Mode was OFF; no Gradle,
test, APK, device or emulator run occurred. This document is decision support, not an
implementation and not legal advice.

## 1. Executive summary

- The app TODAY holds only sanitized BLE advertisements (pipeline type hints + format
  confidence) and passive Wi-Fi scan results, with **no INTERNET permission**, no location
  API calls, and no GPS/map/Remote ID code (verified by source inspection of HEAD `067a200`).
- **Handset location** (where the PHONE is) is feasible and cheap: `ACCESS_FINE_LOCATION` /
  `ACCESS_COARSE_LOCATION` are ALREADY declared for BLE/Wi-Fi scan eligibility on API 29–35,
  so no new permission would be required to READ the handset position — but any retention,
  display or export of it is a NEW privacy surface and needs explicit opt-in design.
- **Map display** is the expensive part: online SDKs need a new `INTERNET` permission and
  bring billing/ToS/attribution constraints; offline tiles need licensing and APK size.
  Recommendation: **NO-GO for online maps in the current privacy model; CONDITIONAL (deferred)
  for a user-position-only schematic.** Never place pins for nearby devices.
- **Broadcast drone Remote ID** is only realistically observable in narrow variants through
  ordinary Android public APIs, and the normative payload standard (ASTM F3411) is paywalled
  and could not be verified in this run: **RESEARCH BLOCKED** for decoder design, **NO-GO**
  for implementation now. FCC/EASA-style identification data is also third-party personal data.
- Direction finding, ranging, heatmaps and long-term tracking are **NO-GO** under current
  permissions/hardware/privacy model.
- Smallest defensible next slice, if authorized later: an OPTIONAL, session-scoped, never-
  persisted "handset context" card (coarse or precise by user choice) with zero export and
  zero map — effort band S (see §9).

## 2. Evidence-based current capability matrix (verified in this repo)

| Capability | State today | Source of truth |
|---|---|---|
| BLE scan | `BLUETOOTH_SCAN` runtime (A12+), legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` (≤A11); `neverForLocation` deliberately NOT asserted | `app/src/main/AndroidManifest.xml` L9–L21 |
| BLE filtering risk | Without `neverForLocation`, some BLE APIs/filters behave differently; with it, some beacons are filtered by the OS | Android Bluetooth permissions doc (URL §3) |
| Wi-Fi | Passive `SCAN_RESULTS_AVAILABLE_ACTION` + `getScanResults()` only; `startScan()` NEVER called | `app/.../wifi/AndroidWifiResultEngine.kt`, `AndroidWifiResultFetcher.kt` |
| Location permission state | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` declared, used ONLY as scan eligibility, never to read position | Manifest L20–L30; `permissions/ObservationReadinessModel.kt` |
| Handset position | NOT read anywhere (no LocationManager/FusedLocation) | grep over `core/radio`, `app` → zero hits |
| Network | No `INTERNET` permission, no sockets, no uploads | Manifest (no INTERNET), `docs/permission-capability-matrix.md` |
| Maps / Remote ID | No code, no dependencies, no assets | grep → zero hits |
| Sanitized model | `PublicObservation` carries NO MAC/BSSID/SSID-without-consent/payload/coordinates | `pipeline/PublicObservation.kt` |
| Confidence | Format-level `ObservationConfidence` (UNKNOWN/POSSIBLE/CANDIDATE/VALIDATED_FORMAT), never device identity | `signature/*` |
| Freshness | FRESH/STALE/UNKNOWN incl. cached Wi-Fi; STALE never presented as new | `pipeline/ObservationFreshness.kt` |
| Device position | NEVER available. RSSI is a bucket only; Radar is schematic and labeled | `docs/*`, `signal/SignalBuckets.kt` |

**Hard boundary:** a BLE/Wi-Fi RSSI observation proves only that a transmitter was receivable.
It cannot yield direction, distance or coordinates on one handset without calibration and
multi-receiver geometry. Any map pin for a nearby device would be fabricated accuracy.

## 3. Official sources consulted (accessed 2026-11-04)

1. Android — Bluetooth permissions. https://developer.android.com/develop/connectivity/bluetooth/bt-permissions —
   `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (A12+), legacy permissions, and the semantic of
   `neverForLocation` (asserting no location derivation filters some BLE beacons). Applicable
   to API 29–35 (our range). Page body was partially truncated by the fetch; claims used here
   match the wording already captured in `docs/permission-capability-matrix.md` (Phase 2).
2. Android — Wi-Fi permissions. https://developer.android.com/develop/connectivity/wifi/wifi-permissions —
   `NEARBY_WIFI_DEVICES` (A13+) with `neverForLocation` for Wi-Fi APIs that do not derive
   location; scan results that can reveal location still require location permission. Do NOT
   assume `NEARBY_WIFI_DEVICES` alone removes the location requirement for `getScanResults()`.
3. Android — Location permissions. https://developer.android.com/develop/sensors-and-location/location/permissions —
   `ACCESS_COARSE_LOCATION` (approximate) vs `ACCESS_FINE_LOCATION` (precise), runtime grants,
   user-revocable, approximate grants do not satisfy precise-only operations.
4. FAA — Remote ID. https://www.faa.gov/uas/getting_started/remote_id — US rule requiring
   Standard Remote ID broadcast (Bluetooth/Wi-Fi transports per the accepted means of
   compliance) or broadcast modules; operator registration implications. US jurisdiction only.
5. EASA — drone regulation / direct Remote ID (Regulation (EU) 2019/947 and 2019/945 as
   amended). https://www.easa.europa.eu/en/domains/civil-drones — EU direct broadcast Remote ID
   framework. EU jurisdiction only.
6. ASTM F3411 (Remote ID standard) — **paywalled; normative payload layout NOT verified in this
   run.** Treated as RESEARCH BLOCKED for decoder design.
7. MapLibre Native — BSD-2-Clause (permissive) renderer; tiles are NOT included.
   https://github.com/maplibre/maplibre-native
8. OpenStreetMap Foundation — Tile usage policy: attribution required, bulk downloading and
   heavy app use prohibited without agreement. https://operations.osmfoundation.org/policies/tiles/
9. Google Maps Platform — Maps SDK Terms: caching/tile extraction and offline use restricted.
   https://developers.google.com/maps/documentation/android-sdk/policies (ToS-level constraint).
10. Mapbox Maps SDK — proprietary, billed; offline packs are a paid entitlement.
    https://www.mapbox.com/pricing

Jurisdiction note: none of the above governs Azerbaijan or all jurisdictions. Regulatory notes
are feasibility context, NOT legal advice.

## 4. Track A — Foreground handset location context

- **What it means:** the PHONE's own position for the CURRENT explicit session. Never a
  transmitter position.
- **APIs:** `FusedLocationProviderClient` (Play Services, adds a dependency) or platform
  `LocationManager` with `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION` (already declared).
- **Feasibility:** GO (conditional) for read-only, session-scoped display; the permission is
  already justified by scan eligibility, so no NEW manifest permission is needed for COARSE.
- **Constraints:** no background location (never `ACCESS_BACKGROUND_LOCATION`), no continuous
  tracking, no geofencing, no cross-session profiles. Battery cost is small for one-shot fixes;
  repeated polling is not justified.
- **Denied/disabled/poor accuracy:** show an explicit "konum yok / yaklaşık / hassas" state;
  never block observation; never infer coordinates.
- **Retention default:** NO retention, NO export, NO history entry, NO share. If ever stored
  (future authorization), it must be opt-in per record and reconcilable on delete/retention.
- **Effort:** S (platform API + one card + states). Testable on JVM only for the state/policy
  layer; real fixes require phone testing.

## 5. Track B — Map display comparison

| Option | Feasible? | Network/permission | Licensing/attribution | APK/battery | Privacy | Verdict |
|---|---|---|---|---|---|---|
| No map (status quo) | Yes | none | none | none | best | **Keep as default** |
| Schematic "you are here" (own-drawn grid, no tiles) | Yes | none | none (own drawing) | tiny | good | **CONDITIONAL, later** |
| Offline tile bundle (e.g. MapLibre + prebuilt tiles) | Technically yes | none (INTERNET not needed) | Tile data licensing varies per provider; OSM tiles may not be bulk-copied | +tens of MB, maintenance | good offline | **NO-GO now** (licensing + size unresolved) |
| Online SDK (Google/Mapbox/MapLibre+online tiles) | Technically yes | requires `INTERNET` + ToS compliance | proprietary/billed/attribution | moderate | network metadata leaves device | **NO-GO** in current privacy model |
| Device pins on a map | NO | — | — | — | fabricated accuracy | **NO-GO permanently** unless a future source provides trustworthy explicit coordinates with provenance |

Rule preserved: the schematic Radar stays labeled schematic; a map must never imply it knows
where the observed transmitter is.

## 6. Track C — Broadcast drone Remote ID (feasibility only)

- **Regulatory framing:** FAA requires Standard Remote ID broadcast (US); EASA defines direct
  Remote ID (EU). Both rely on standardized broadcast messages carried over Bluetooth and/or
  Wi-Fi transports (per the accepted means/standards). Jurisdiction-specific; not universal.
- **Transport reality on an ordinary Android phone:**
  - Ordinary BLE advertisements are visible through `BluetoothLeScanner` (subject to OS filters
    and `neverForLocation` semantics).
  - BLE extended advertising (5.0 Coded PHY / extended PDUs) is only partially exposed to apps
    depending on OS version and chipset; coverage is device/hardware dependent and NOT
    guaranteed.
  - Wi-Fi Beacon/NAN-based variants are NOT observable through public Android app APIs
    (`WifiManager` scan results do not expose arbitrary vendor/NAN payloads). Monitor-mode /
    raw frame capture is unsupported for normal apps.
  - Conclusion: coverage would be narrow and device-dependent; **cannot be promised**.
- **Payload/decoder:** ASTM F3411 normative layout is paywalled and was NOT verified. Treat
  decoder design as **RESEARCH BLOCKED** until the standard text is lawfully obtained; do not
  copy third-party decoder code without license review.
- **Privacy:** Remote ID messages can include operator/pilot identifiers and coordinates —
  third-party personal data. Any future feature must be explicitly opt-in, display-only,
  never exported by default, and must never fuse Remote ID positions with observed BLE/Wi-Fi
  device fingerprints.
- **Fallback rule:** UNKNOWN / UNSUPPORTED when transport support or message integrity is
  missing (spoofing is trivial for broadcast formats).
- **Verdict:** **NO-GO for implementation now; RESEARCH BLOCKED for decoder.**

## 7. Track D — Adjacent advanced features

| Feature | Classification | Reason |
|---|---|---|
| Direction finding (AoA) | NO-GO | Requires multi-antenna/BLE 5.1 CTE hardware access not exposed to normal apps |
| Ranging (BLE 5.1 / Wi-Fi RTT) | CONDITIONAL / separate R&D | Wi-Fi RTT exists in platform APIs but needs AP ranging support, permissions and new scope |
| Signal heatmaps | NO-GO | Would require continuous position+signal fusion = tracking/surveillance |
| Long-term device tracking | NO-GO | Violates core privacy model (no cross-session identity) |

## 8. Privacy, security, non-functional impact

- Keep manual, bounded, foreground-only sessions; receive-only Wi-Fi (never `startScan()`);
  no background services; no cloud; History opt-in with separate SSID consent; no raw
  MAC/BSSID/payload anywhere public; FieldDiag-04 hidden.
- New surfaces (location read, map tiles, Remote ID) each need: threat model, explicit opt-in
  copy in Turkish, off-by-default, deletion story, export exclusion by default, battery note.
- Signing identity, applicationId `com.hjinlabs.radioobserver`, saved History, presets, catalog
  and WS04/05/06 data formats must remain untouched by any future optional module.

## 9. Decision matrix

| Candidate | Grounded use case | Support evidence | Requires SDK/permission | Location permission? | Online? | Privacy risk | License/data | Complexity | Recommendation |
|---|---|---|---|---|---|---|---|---|---|
| Handset location card (session) | "Where was I when I observed this?" (phone only) | Platform APIs; permission already declared | `LocationManager`/Fused | YES (declared) | No | Medium (user position) | none | S | **GO (conditional, later slice)** |
| Schematic you-are-here | orientation aid without tiles | Own drawing | none new | optional | No | Low | none | S | **CONDITIONAL** |
| Offline tiles | usable map without network | Renderer libs exist | dependency + assets | optional | No | Low | provider-dependent | M–L | **NO-GO now** |
| Online map SDK | familiar map UX | provider SDKs | `INTERNET` + billing | optional | Yes | Medium–High | proprietary | M | **NO-GO** |
| Remote ID BLE decode | drone awareness | transport partially available; spec paywalled | none new (BLE already) | no | No | High (3rd-party identity) | standard paywalled | L | **RESEARCH BLOCKED / NO-GO now** |
| Direction/ranging/heatmaps | — | hardware limits | — | — | — | High | — | — | **NO-GO** |

**Recommended next bounded step (needs separate authorization):** the handset-location card —
session-scoped, no persistence, no export, no map, explicit coarse/precise copy and honest
"unknown" states. Effort band S. Acceptance tests: (1) permission denied → explicit state, no
coordinate shown, observation unaffected; (2) fix acquired → one timestamped position shown,
labeled approximate when coarse; (3) regenerate UI/recreate Activity → still session-scoped,
never persisted; (4) History/export contain no coordinates; (5) cold start after process death
shows no stale position. Requires: developer authorization for a new session-location module
and Xiaomi verification of permission dialogs.

## 10. Limitations of this study

- Android/FAA/EASA pages were fetched but partially truncated; claims rely on the captured
  text plus this project's Phase 2 permission matrix, which was written from the same official
  sources.
- ASTM F3411 text is not publicly accessible: payload-level Remote ID analysis is BLOCKED.
- No phone/emulator testing was performed in this run (Build Mode OFF): all device behavior
  claims are documentary and must be verified before any future implementation.
- Map licensing depends on the tile provider chosen; nothing here is a legal opinion.
