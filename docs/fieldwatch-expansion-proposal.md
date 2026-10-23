# Radio Observer — Fieldwatch-Inspired Expansion Proposal (DRAFT, 2026-10-23)

Status: PLANNING DOCUMENT ONLY. Nothing in this file authorizes implementation. No
source, build configuration, roadmap or schema was changed in this iteration. The
original `roadmap.md` (Phase 1 = 5/5, Phase 2 = 8/8, Phase 3 = 10/10 — verified against
the actual file, 23/23 checked tasks) is untouched; historical evidence preserved.

Purpose: evolve Radio Observer into a substantially more capable OFFLINE Android
radio-observation app inspired by the FEATURE DEPTH of Fieldwatch — explicitly NOT a
clone of Fieldwatch, its code, its catalog, or its identity.

---

## 1. Sources and verification status

| Source | URL | Accessed | Status |
|---|---|---|---|
| Repository front page / README | https://github.com/OffGridPete/Fieldwatch | 2026-10-23 | Read (web) |
| CHANGELOG.md (main) | https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/CHANGELOG.md | 2026-10-23 | Read through 1.1.24–1.1.30 (newest entries; older truncated by fetch) |
| NOTICE | https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/NOTICE | 2026-10-23 | Read in full |
| LICENSE (MIT) | repo root | 2026-10-23 | Declared MIT; file itself not fetched verbatim (limitation) |
| docs/ directory listing | https://github.com/OffGridPete/Fieldwatch/tree/main/docs | 2026-10-23 | Listing only: user manual PDF, instruction.txt, language-pack guides, signature-localization-options.md, trusted-network-requirements.md, diagrams |
| Source code (app/, signature catalogs dist/fieldwatch-signatures*.json) | — | — | NOT inspected in this iteration (intentional, to avoid license contamination) |

Verification limitations (stated, not invented): the exact git revision of Fieldwatch
was not pinned (web fetches do not expose the commit hash); the user manual PDF and app
source were not read. Everything marked VERIFIED below comes only from
README/CHANGELOG/NOTICE text fetched on 2026-10-23.

## 2. Fieldwatch feature inventory (evidence-based)

VERIFIED from README + CHANGELOG (release documenting the behavior in parentheses):

1. Live list view with per-radio rows (title/subtitle lines, RSSI bars, frequency,
   first/last seen) — README, (1.1.29).
2. Radar view + Timeline view; per-view "Display dims" configuration (title line,
   subtitle line, live path, RSSI bars, frequency, first/last seen) persisted per
   view — README, (1.1.28, 1.1.29).
3. Live north-up path map over Live ("Show live path"): draws this phone's GPS track
   during a sit; ~12 m bend retention; draggable divider — (1.1.28).
4. Filtering engine: Keep/Hide (signature matches only, watched only, named only,
   Hide Mine), Moving with you (co-travel), Seen at more than one place, New
   detections, selected signature classes, selected signatures, signal/name/MAC/
   vendor filters — (1.1.27).
5. Filter presets: save current filters as a preset, tap to load, long-press to
   delete — (1.1.27).
6. Extensible signature library: stock JSON catalogs (dist/fieldwatch-signatures.json
   for 1.1.11; -v2 for 1.1.12+), user-editable signatures (class, rules, decode maps,
   notes, extra attention), GitHub catalog update path — README, (1.1.27).
7. "Signature candidates": match the whole log against the catalog to propose new
   signatures, with progress dialog — (1.1.27).
8. Manufacturer/device-type hints: offline tables packed from IEEE Registration
   Authority (MA-L/MA-M/MA-S/CID) and Bluetooth SIG assigned numbers (company IDs,
   GAP Appearance, 16-bit service UUIDs); Fast Pair 24-bit model IDs — NOTICE.
9. Named radios + watchlist + "Bookmark to alert" (foreground alerts for signature
   matches / watched radios) — (1.1.27).
10. "Sits": named observation sessions; sit list; per-sit actions — (1.1.27).
11. Places: stable trackers heard at 2+ places within 7 days, return-after-quiet
    detection, latest 8 stops per radio, phone-side location only, address-changed
    radios skipped — (1.1.27, 1.1.28).
12. Reports: Path, Debrief (street tiles), Compare (two sits), Sit export (GPX, KML,
    GeoJSON, ATAK data package), Log Export, AI Export — (1.1.27, 1.1.28), README.
13. GNSS interference detection (raw GPS measurement recording around a hit; armed
    switch; Privacy mode blocks it) — (1.1.26, 1.1.27).
14. Remote ID decoding (aircraft/operator positions, takeoff/pilot/fixed operator,
    UAS ID, EU class) — (1.1.26, 1.1.27).
15. ATAK/TAK integration (per-phone marker, radio spots with retention, GNSS hit
    pin/ellipse marks, data packages) — (1.1.24–1.1.27).
16. GPS co-travel hypothesis ("Moving with you / possible tail") — README disclaimer.
17. Top-bar health icons for Wi-Fi/BT/signatures/GPS — (1.1.24).
18. Privacy mode (screen masking), user-controlled deletion — README, (1.1.24).
19. Crash report screen on next open after a crash — (1.1.29).
20. Diagnostics page (scanning state, Wi-Fi/BT/Location/GPS) — (1.1.27).
21. Multi-language packs (AI-drafted, community files) — (1.1.27) + docs listing.
22. Faster Wi-Fi AP scans via Developer options throttling toggle — (1.1.24).

Radio Observer ideas that are NOT verified Fieldwatch behavior are marked ADAPTATION
in the matrix. Everything else cites the rows above.

## 3. Radio Observer current capability inventory (audited at HEAD e9a3c45)

Architecture (verified by direct source inspection):
- Strict JVM/Android split: :core:radio (pure Kotlin, host-tested packages:
  boundary, factory, history, lifecycle, model, parser, permissions, pipeline,
  session, signal, state, ui, wifi) and :app (ble, wifi, diag, history, lifecycle,
  navigation, permission, session, ui, ui/components, ui/theme). 32 test classes;
  core 266/266 + app 45/45 green at last gate run.
- BLE: bounded, permission-gated BluetoothLeScanner adapter (AndroidBleScanEngine).
- Wi-Fi: strictly PASSIVE SCAN_RESULTS_AVAILABLE_ACTION observer; WifiManager/
  WifiScanner startScan appears ONLY in comments and in a negative host test that
  asserts its absence (ObservationHistoryTest) — contract verified intact.
- Sanitization pipeline: raw radio input never reaches the UI; public observations
  carry safe names, manufacturer HINTS (never raw MAC/BSSID), conservative
  SignalBucket, freshness provenance, session-local rowIds, TTL + 200-row live cap.
- Sessions: independent 20 s BLE / 30 s Wi-Fi sessions, explicit Start, second Start
  after a permission grant, foreground/RESUMED-only, teardown on foreground loss.
- History: optional, local, persistent, deletable; OFF by default; separate
  Wi-Fi-name consent; 7/14/30-day retention; 500-record cap; random UUID ids;
  backup exclusions; the model structurally cannot hold raw identifiers.
- UI: four-tab Compose/M3 (Gözlem, Gözlemler, Geçmiş, Ayarlar) + live/history
  details; Premium V2 design system; honest Turkish copy including localized
  classifier sentinels (Wi-Fi + BLE) via ManufacturerPresentation.
- FieldDiag-04: hidden DEBUG diagnostic panel, seven-version-tap unlock, in-memory
  only; gated by DeveloperDiagGate tests.
- Identity: applicationId com.hjinlabs.radioobserver, versionCode 1,
  versionName 0.1.0-phase1 — unchanged by this proposal.

What already works and must NOT be rewritten: the scanning/permission/lifecycle/
sanitization/history core. Expansion ADDS presentation and analysis layers on top of
PublicObservation-grade data.

## 4. Feature gap matrix

Legend: FW ref = Fieldwatch inventory row. RO = Radio Observer. Cx: L/M/H.
(P) = needs a new privacy review and individual approval. Phase = workstream.

| # | Feature | FW | RO today | Missing | Sanitized data enough? | Limits | New perms | Privacy/retention | Test strategy | Cx | Phase |
|---|---|---|---|---|---|---|---|---|---|---|---|
| F1 | Per-row display config (dims) | 1,2 | Fixed feed row | Row-element toggles, persisted per view | YES | none | none | display-only | pure projection tests | L | 01 |
| F2 | Timeline view | 2 | Newest-first aggregate feed | Chronological event stream view | YES | none | none | none | projection + ordering tests | M | 01 |
| F3 | Radar visualization | 2 | none | Abstract proximity-ring view of active sources | YES (RSSI bucket only) | NO direction hardware; RSSI is NOT distance — must stay an abstract hint, never direction finding | none | fixed honesty copy (no direction/distance implication) | disclaimers + geometry tests | M | 01 |
| F4 | Advanced filters | 4 | none | Filter DSL (type/signal/name/keep-hide) over public observations | YES | none | none | no raw identifiers in rules | pure filter tests | M | 02 |
| F5 | Filter presets | 5 | none | Named save/load/delete of filter sets | YES | none | none | local, deletable | controller + codec tests | L | 02 |
| F6 | Signature catalog + rule engine | 6,7 | Phase 1 layout candidates + 2 vendor hints only | User-extensible typed rules (name pattern, service-UUID label, payload layout), classes, confidence/provenance labels | PARTIALLY — needs a transient typed-hint enrichment stage | OEM payload variability; scan throttling | none | hints in-memory, session-scoped, not persisted by default | rule-engine JVM suites + boundary regression | H | 03 |
| F7 | Vendor table upgrade | 8 | 2-entry curated table + caller hints | More company-ID/GAP/service labels | YES | NOTICE: IEEE/BT-SIG tables are NOT MIT — RO must source its own data under IEEE/BT-SIG terms | none | hints are not identity claims | table + honest-copy tests | M | 03 |
| F8 | User labels/bookmarks | 9 | none (live rows session-local by design) | Watchlist/labels on observations | NO — RO has no stable cross-session key by contract | none | none | (P) content-keyed labels create a de-facto profile; needs explicit decision | keying tests | M | 04 |
| F9 | Foreground alerts | 9 | none | Notification on signature/bookmark match during a session | YES | Android 13+ runtime notification permission | POST_NOTIFICATIONS (needs approval) | alerts ONLY inside an active foreground session | gate + matcher tests | M | 04 |
| F10 | Named sessions | 10 | Implicit bounded sessions only | User-named session spans + event capture | YES | none | none | local, deletable; new entity → migration-first plan | controller tests | M | 05 |
| F11 | Session analytics | 11 | History list/detail only | Per-session counts/durations/distinct-source estimates | MOSTLY (stats over sanitized data) | none | none | derived stats die with their source records | pure analytics tests | M | 05 |
| F12 | Session comparison | 12 | none | Side-by-side two-session stats | YES | none | none | same as F11 | pure comparison tests | M | 05 |
| F13 | Report generation | 12 | none | Honest local text/HTML session report | YES | none | none | explicit user action only; carries disclaimers | golden-file builder tests | M | 06 |
| F14 | CSV/JSON export | 12 | none | Export via SAF (user-picked destination) | YES | none | none | sanitized schema only | codec + round-trip tests | L | 06 |
| F15 | Import + backup | 12 | none | Import RO-exported files; local backup | YES | none | none | import validates + sanitizes; never merges raw ids | round-trip + malformed-input tests | M | 06 |
| F16 | Places / tracker memory | 11 | none | Cross-visit stable-source memory | NO — needs stable ids + GPS, both forbidden today | — | location | (P) de-facto profiling + movement data | — | H | 07 |
| F17 | GPS path tracking / live map | 3 | none | Phone track + map tiles | NO | — | location | (P) exact movement data; FW README notes logs keep full coordinates even in privacy mode | — | H | 07 |
| F18 | Remote ID decoding | 14 | none | Drone/operator position decoding | NO — raw payload + location semantics | OEM quirks | (P) | location-bearing data | — | H | 07 |
| F19 | TAK/ATAK + GPX/KML/GeoJSON | 15 | none | External feeds/files with coordinates | NO | network for TAK | (P) network transmission + exact locations — contradicts no-network contract | — | H | 07 |
| F20 | GNSS interference detection | 13 | none | Raw GNSS measurement analysis | NO | OEM GNSS quality | (P) location-adjacent raw data | — | H | 07 |
| F21 | First-class diagnostics screen | 20 | Hidden FieldDiag-04 only | User-facing readiness/diagnostics (opt-in) | YES | none | none | never persisted | gate tests | L | 08 |
| F22 | Crash report on next open | 19 | none | Crash breadcrumb capture | YES | none | none | crash logs must never contain radio data | logger unit tests | M | 08 |
| F23 | Health status icons | 17 | Status pills in cards | Compact scan/BT/Wi-Fi health indicators | YES | none | none | none | projection tests | L | 08 |

Unsupported/speculative, clearly marked: F3 as direction finding (no phone AoA
hardware); F16–F20 conflict with current privacy contracts and are NOT in the
default plan (workstream 07 is a documentation-only feasibility review).

## 5. Proposed architecture (draft, additive only)

- core/radio/signature — pure typed signature rule engine (rule schema, matcher,
  confidence + provenance labels, catalog codec). No Android dependency; host-tested
  like the Phase 1 parsers.
- core/radio/analytics — pure session-event and statistics projections (F10–F13).
- core/radio/filtering — pure filter DSL + preset codec (F4/F5).
- app pipeline extension — an optional, bounded TRANSIENT enrichment stage after
  sanitization: typed hints (service-UUID label, payload-layout class, signature
  match) delivered to the LIVE pipeline only; raw addresses/payloads continue to
  stop at the existing boundary; history persistence unchanged by default.
- app/reporting — export/import/report builders over SAF (user-chosen destination).
- app/alerts — foreground-session-gated notification glue.
- UI — timeline and radar-ring as view modes behind the existing Gözlemler tab;
  filters/reports follow the existing M3 design system.

Data-flow/privacy boundary (proposed):
raw radios → adapters → existing sanitization → PublicObservation
  → optional transient enrichment (in-memory, session-scoped, not persisted)
    → live UI (feed / timeline / radar ring) → explicit user export only
  → history (UNCHANGED schema)
Signatures, presets, labels and named sessions are user-authored local data:
deletable, never auto-transmitted. The app stays 100% offline: no backend, no
accounts, no automatic network transmission.

## 6. License and attribution findings

- Fieldwatch app code: MIT (declared). Even so, this proposal plans ORIGINAL Radio
  Observer implementations; no Fieldwatch source is proposed for copying. If any code
  reuse is later explicitly selected, MIT notice + attribution must be preserved and
  documented.
- Fieldwatch's packed assigned-number tables (IEEE MA-L/MA-M/MA-S/CID, Bluetooth SIG
  assigned numbers) are EXPLICITLY NOT MIT per Fieldwatch's NOTICE and are subject to
  IEEE and Bluetooth SIG terms. Radio Observer must NOT copy radiodb.bin,
  ApVendorOuis.kt, or Fieldwatch signature catalogs. F7 must use either RO's own small
  curated table (status quo) or data obtained directly from IEEE/BT-SIG under their
  published terms.
- Fast Pair model-name listings are community-compiled and not MIT-covered — same rule.
- No Fieldwatch branding, logos, application id (app.fieldwatch) or manual content may
  be copied. Radio Observer keeps its own identity and original honest copy.

## 7. Draft phased implementation plan (NOT started; separate from roadmap.md)

Workstreams (draft). Each needs explicit developer approval before any code.

00 Architecture and feature audit — partially done by this document. Remaining:
   D1–D6 decisions below. No code.
01 Enhanced Live UI, Radar and Timeline (F1, F2, F3).
   Scope: view switch in the feed; persisted per-view display toggles; abstract
   proximity-ring radar with fixed honesty copy (never direction/distance claims);
   timeline view. Exclusions: no direction finding, no GPS/map.
   Acceptance: all existing host gates green + new pure projection tests; phone
   check: views switchable, honest copy visible. Stop condition: any required change
   to the scanning/permission contracts.
02 Advanced filters and saved presets (F4, F5).
   Scope: pure filter DSL + locally persisted presets. Acceptance: filter host tests
   (include/exclude combinations); preset save/load/delete on phone. Stop: any need
   for raw identifiers in rules.
03 Signature classification and catalog (F6, F7).
   Scope: typed rule engine; RO-format user-editable catalog JSON; candidates
   matcher; expanded curated company-id table (RO-sourced). Exclusions: NO import of
   Fieldwatch catalogs; no raw payload persistence; transient enrichment in-memory
   only. Depends: D1. Acceptance: rule-engine suites with confidence/provenance
   labels; the existing sanitization-boundary regression suites stay green (proof of
   no raw-data leak); phone check with a real advertisement of known layout.
04 Labels, bookmarks, foreground alerts (F8, F9). Depends: D2 + D3. Alerts only
   during an active foreground session; notification permission follows the existing
   explicit two-tap pattern. Acceptance: gate tests (no alerts outside sessions).
05 Named sessions, analytics, comparison (F10, F11, F12). Pure JVM analytics;
   HistoryRecord schema unchanged; a NEW local entity links sessions to records;
   migration plan documented BEFORE any schema change; install-over upgrades only.
06 Reports, export/import, backups (F13, F14, F15). SAF destinations; CSV/JSON
   codecs with round-trip + malformed-input tests; reports carry honesty disclaimers.
   Exclusions: no automatic sharing, no network.
07 Advanced feasibility review (F16–F20: Places, GPS path, Remote ID, TAK, GNSS
   interference). DOCUMENTATION ONLY until a per-feature privacy review and explicit
   developer approval exists. These conflict with current contracts (no stable ids,
   no location, no network) and are NOT in the default plan.
08 Integration QA and phone acceptance (F21, F22, F23 + gates). Crash breadcrumbs
   never contain radio data. DoD per workstream: all host gates green (core + app,
   --rerun-tasks), lint 0 errors (warnings ≤ baseline), DEBUG-only builds, phone
   acceptance checklist executed by the developer on the Xiaomi device, roadmap
   updated only in its established post-roadmap-note convention.

Required host regression tests kept green through all work: the existing 266 core +
45 app suites (sanitization, permissions, lifecycle, navigation, history controller,
FieldDiag gate) plus each workstream's new pure tests. Declined checks (2.0x font,
light theme, TalkBack) are NOT added as required acceptance gates per the developer's
explicit decision; they remain optional observations only.

## 8. Issues requiring developer decisions (blocking, in order)

D1. F6 enrichment: approve a transient (session-scoped, non-persisted) typed-hint
    stage, or restrict workstream 03 to data already present in PublicObservation?
D2. F8 keying: labels/bookmarks need a stable user-visible key. Approve content-
    keyed labels (privacy consequence: a de-facto profile), or drop F8/F9?
D3. POST_NOTIFICATIONS: approve adding one new runtime permission for F9 alerts?
D4. F7 vendor table: keep the small curated list, or source IEEE/BT-SIG data
    directly under their published terms (larger asset)?
D5. History schema: named sessions need a new local entity + migration. Approve a
    migration-first plan (install-over upgrades only, never uninstall)?
D6. Which workstream to start after approval? (Recommendation: 01.)

## 9. Protection of existing user data (applies to ALL future work)

applicationId unchanged; existing local history remains readable; consent preferences
and saved records intact; FieldDiag-04 untouched; navigation operational; upgrades are
install-over — uninstalling is never a normal upgrade method; migrations are planned
and documented before any persistent schema change; testing exports must never
require clearing app data.

---

Prepared 2026-10-23 at repository HEAD e9a3c45. This document contains no
implementation, no code, and no roadmap modification. Workstream 01 must not begin
until the developer reviews this proposal and resolves D1–D6.
