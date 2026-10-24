# Workstream 03A — Privacy-Safe Signature Classification (2026-10-24)

First approved slice of Workstream 03: a local, deterministic, privacy-preserving
device-FAMILY hint engine + small original catalog + honest UI presentation.
WS03B (richer evidence, catalog growth) NOT started; category filter DEFERRED
(see below). Build Mode ON (DEBUG only). Git commit/push was performed at the
owner's explicit per-message instruction (the prompt's Git-OFF is overridden by
the owner's live message, same as Workstreams 02/02-hotfix).

## 1. Trust-boundary / feasibility table (established BEFORE editing)
| Candidate input | Sanitized source | Provenance | Null/unknown behavior | Safe for family hint? |
|---|---|---|---|---|
| radio (BLE/WIFI) | ObservationRowUi.radio | pipeline (session) | always present | YES — structural |
| typeHint | pipeline's own DeviceTypeCandidate.label (public formats: iBeacon layout, Find My separated layout, both validated in Phase 1 by this project's parser) | BleDeviceClassifier | null → no rule matches → UNKNOWN | YES — format-derived family |
| confidence | DeviceTypeCandidate.confidence enum | parser | UNKNOWN | YES — gates rule strength |
| name / SSID | sanitized display name | user-configurable, spoofable | null common | NO — spec: a name/SSID never establishes a make/model; not even a field of the typed input |
| manufacturerHint | Bluetooth SIG company id → ecosystem name (Apple/Samsung) | parser lookup | null / unknown-id sentinel | NO as a family source (ecosystem membership is not a device family); stays display-only |
| rssiDbm / signalBucket | qualitative signal | radio level | null | NO — never a type signal |
| freshness / ageMs / lastSeen | pipeline | computed | UNKNOWN | NO — receipt timing is not a type |
| rowId / locallyAdministered | session-local ordinal / flag | pipeline | — | NO — identity-adjacent; excluded from input |

Conclusion: only radio + typeHint + confidence can honestly support broad family
hints. The typed input (SignatureInput) enforces this STRUCTURALLY: it has no
field for a name, manufacturer, RSSI, freshness or id, so no caller can smuggle
identity-adjacent data into classification. No raw MAC/BSSID/advertising bytes,
no new collection (the previously unapproved D1 enrichment was NOT touched).

## 2. Engine and catalog (pure Kotlin, :core:radio, Android-free)
Package com.example.radioobserver.core.radio.signature:
- SignatureClassifier.classify(input, catalog): single bounded pass over a finite
  rule list; exact Unicode/Turkish-folded label equality (reuses LiveFilter.fold);
  no regex, no recursion, no network; deterministic ordering (specificity, then id).
- SignatureRule: id, category, radio?, typeHintEquals?, requiresConfidence?, strong,
  specificity, rationale. Duplicate entries are deduped in output (bounded rationale).
- SignatureHint: category? + status (SUPPORTED / AMBIGUOUS / CONFLICT / UNKNOWN) +
  matchedRuleIds + catalogVersion + non-sensitive rationale. UNKNOWN and CONFLICT
  carry category = null — uncertainty is never silently upgraded. "strong" rules
  (validated format or structural fact) yield SUPPORTED; prefix/length-mismatch
  rules stay AMBIGUOUS.
- SignatureCatalog.VERSION = "radioobserver-signature-catalog-v1", 5 ORIGINAL
  app-authored rules: R-WIFI-AP-01 (Wi-Fi rows are AP observations by construction),
  R-BLE-IBEACON-01 (validated iBeacon layout → iBeacon family), R-BLE-IBEACON-02
  (iBeacon-like prefix, length mismatch → ambiguous family), R-BLE-FINDMY-01/02
  (publicly documented Find My separated layout → ambiguous family; NEVER an
  AirTag/owner/following claim). No IEEE OUI export, BT-SIG dataset, Fast Pair
  data, Fieldwatch catalog or any third-party content is copied; every label the
  rules match is produced by Radio Observer's own Phase-1 parser from public
  format documentation. License-safe by construction (original work).

## 3. UI integration (minimal, display-only)
- List row: one concise "Olası tür: …" line, only for BLE family hints
  (iBeacon/Find My). Wi-Fi rows already carry their structural context via the
  source chip, so no redundant hint line is added.
- Detail: new "Sınıflandırma ipucu" card showing category, status word, rationale
  lines, rule id + catalog version, and the non-identity caution; honest fallback
  "Cihaz türü belirlenemedi" + explanation when nothing supportable can be inferred;
  a deterministic CONFLICT state lists the conflicting rule ids and makes no claim.
- Classification is a remember(row)-keyed pure projection inside existing Compose
  renderers: no new state in MainActivity, NO new Activity property initializer
  (the WS02 startup-crash pattern cannot regress), no new repository, nothing
  persisted, no navigation change. It never changes row membership, Radar
  markers/ring semantics/golden-angle layout, Timeline ordering, or session state;
  it dispatches nothing (no Start/Stop/permission/scan).
- Category filter in LiveFilter: DEFERRED — adding a category criterion would
  change the WS02 preset codec schema (v1) in a workstream that forbids unsafe
  preset-format changes. No fake category filter was added.

## 4. Test matrix (host/JVM; 9 new core tests, SignatureClassifierTest)
A determinism (same input → same hint, versioned catalog ids); B supportable
families (Wi-Fi AP SUPPORTED, validated iBeacon SUPPORTED, Find My AMBIGUOUS) and
honest UNKNOWN (bare BLE row; Samsung ecosystem alone); C arbitrary names/SSID and
manufacturer never establish a family (structural: not fields of the typed input);
D deterministic conflict handling (equal specificity → CONFLICT with sorted ids,
no category; strictly higher specificity wins); E Turkish/Unicode folds (İBEACON
matches), blank/whitespace and malformed inputs safe; F bounded evaluation with a
triplicated catalog (deduped output, finite rule set); G no mutation of rows
(pure projection); H+I identical three-view membership + radar accounting
invariant with classified rows + advanced filters; J disposal empties all views,
stale Wi-Fi freshness verbatim, structural purity (no session side effects).
K = existing suites all still green (299→308 core, 49 app incl. startup-boundary
regressions). Limitation stated honestly: no Activity-lifecycle harness exists
(none added); real-device cold start is only provable on the Xiaomi retest.

## 5. Fresh gates (all --no-daemon, tests/lint --rerun-tasks, exit 0)
:core:radio:test 308/308 · :app:testDebugUnitTest 49/49 · :app:compileDebugKotlin ·
:app:assembleDebug · :app:lintDebug 0 errors/15 warnings (baseline kept; the one
new UnusedResources finding was resolved by actually using the conflict status
string in the conflict branch). DEBUG APK only: com.hjinlabs.radioobserver
0.1.0-phase1 (versionCode 1 — NOT bumped), 24,121,561 bytes, SHA-256
b653240444a491986de04680e73781910c7232e175575aaddf4faabe007864c6.
Install-over safe; NEVER uninstall or clear data.

## 6. Non-negotiables preserved
applicationId/install-over identity; manual Start (incl. second Start after
permission grant); foreground/bounded sessions; passive receive-only Wi-Fi (no
startScan); sanitized session-local IDs, capacity/TTL, cached "Eski" labels;
optional local History + SSID consent + deletion/retention; DEBUG-only hidden
FieldDiag-04 (7-tap unlock); WS01 radar correction; WS02 advanced filters +
presets (file format untouched, fail-closed behavior untouched); WS02 startup
hotfix (no Context-dependent initializer added). No new permissions,
dependencies, manifest changes, background work, network/cloud I/O, GPS,
cross-session identifiers or fingerprints. Classification output is ephemeral
and never stored, exported or compared across sessions.

## 7. Open limitations / Xiaomi checks
Unknown BLE devices stay UNKNOWN by design (no invented families). Manufacturer
hints remain ecosystem hints. The catalog is intentionally tiny and honest. Xiaomi
retest: cold start/reopen, History + presets intact, realistic iBeacon/Find My
rows show "Olası tür" + detail provenance, unknown rows show the truthful
fallback, three-view membership identical, cached Wi-Fi still "Eski", no
auto-scan, FieldDiag still hidden/unlockable.
