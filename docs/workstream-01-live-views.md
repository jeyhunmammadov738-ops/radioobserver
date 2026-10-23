# Workstream 01 — Live Observation Views (Liste / Radar / Zaman tüneli) — 2026-10-23

Developer-approved Fieldwatch-INSPIRED workstream. NOT a Fieldwatch clone, NOT a new
roadmap phase; the 23 historical tasks (Phase 1 = 5/5, Phase 2 = 8/8, Phase 3 = 10/10)
are untouched. Only D6 of the expansion proposal was exercised (start with 01);
D1–D5 remain UNAPPROVED and out of scope.

## Scope decision (architectural)

All three views are STATELESS renderers over the CURRENT sanitized
ObservationUiState via ONE pure projection source (LiveViewProjection in
core/radio/ui, JVM-first like ObservationFeedProjection). NO new retention, cache,
event store or history write exists: disposal, TTL eviction, the 200-row pipeline
cap and session replacement therefore empty every view automatically (host-tested).
The selected view is short-lived Activity UI state, never persisted, dispatching
nothing. A view switch can never fetch raw radio data, request permission, start or
extend a session, or inject rows.

## Field-to-view matrix (evidence-based, every rendered datum)

| Datum (model field) | Provenance | Liste | Radar | Zaman tüneli | Honesty rule |
|---|---|---|---|---|---|
| rowId | pipeline session-local ordinal (never address-derived) | identity/key | dot identity + tap target | key + stable tie-breaker | no stable cross-session identity |
| name | sanitized safe label (SSID only under separate consent) | row title | adjacent list | row title | "Adı bilinmiyor" when null; no invented names |
| radio | BLE / WIFI source | icon + filter | dot COLOR (primary/tertiary) + SHAPE (circle/rounded square) | icon | color alone never carries meaning |
| signalBucket | verified taxonomy IMMEDIATE/NEAR/FAR/UNAVAILABLE (Çok yakın/Yakın/Uzak/Sinyal bilgisi yok) | detail text | RING index (innermost→outermost) — never meters | — | UNAVAILABLE gets its own outermost zone, never fake proximity |
| freshness (FRESH/STALE/UNKNOWN) | pipeline provenance; STALE = cached Wi-Fi scan | pill | hollow (stroke) vs filled dot + legend | label | cached data is never shown as fresh |
| lastSeenAtEpochMs | pipeline's last receipt wall clock (session-local) | right-aligned clock | adjacent list clock | PRIMARY ordering key (desc, rowId tie-break) | exactly the model value; never restated |
| ageMs | provenance at pipeline receipt | detail only | — | — | shown as honest recorded-time age in detail |
| manufacturerHint/typeHint/confidence | sanitized hints + localized sentinels | detail | — | — | never a verified identity claim |
| firstSeen | DOES NOT EXIST in the model | — | — | deliberately absent; timeline copy states no first-seen is kept | never fabricated |
| rssiDbm | raw value behind the boundary | — | — (bucket only) | — | never meters/distance |

Timestamp bases: all displayed clocks are the pipeline's wall-clock last-seen times
rendered via DetailTime (HH:mm:ss, local). No UTC conversion, no invented receipt
times; unknown freshness stays "Bilinmiyor".

## Radar honesty contract

Abstract signal-category scheme ONLY: rings = SignalBucket taxonomy; dot angle and
radius are deterministic rowId-derived VISUAL decoration (stable across
recompositions, moving only if the rowId itself changes — host-tested) and convey
NO direction or distance. Mandatory copy, always visible and accessible:
"Şematik sinyal görünümü — yön veya mesafe göstermez." The center is labeled as
purely schematic, never the user's position; there is no compass, bearing, map,
arrow, movement prediction or sweep animation. Bounded canvas: at most
RADAR_MAX_DISPLAYED_DOTS = 40 dots; overflow reported honestly by name
(plural) and remains reachable in the adjacent list. All-signal-UNAVAILABLE states
get an explicit "halkalar bir yakınlık anlamı taşımaz" note.

## Timeline honesty contract

Session-local chronology over last-seen wall clocks ONLY (the model has no
first-seen field; none is fabricated — the entry type structurally has none).
Ordering: lastSeenAtEpochMs descending, stable rowId tie-break (host-tested). The
visible note states: session-local only, ordered by last-seen, no first-seen kept,
empties with the session. STALE cached Wi-Fi keeps the "Eski" label — never a new
sighting.

## Changes (all files)

- NEW core/radio/.../ui/LiveViewProjection.kt — pure projections (LiveViewKind,
  radarProjection, timelineProjection, sameMembership; deterministic geometry).
- NEW core test .../ui/LiveViewProjectionTest.kt — 12 regressions (A membership,
  B determinism/stability/bounded overflow, C ring taxonomy + all-UNAVAILABLE,
  D stale/unknown provenance in both views, E ordering/ties/no-first-seen,
  F disposal + no-mutation, G/J pure switching + honest empties).
- app/.../ui/FeedScreens.kt — view switcher (Görünüm FilterChips, 48dp, LIST default),
  RadarView (honesty banner, Canvas rings, shape+color dots, tap hit-test, legend,
  adjacent accessible list incl. overflow), TimelineView (honesty note, clock-led
  rows), shared honest empty taxonomy for all views.
- app/.../MainActivity.kt — feedView Activity-state wiring (never persisted).
- res/values/strings.xml — 12 new strings + 1 plural (Turkish).
- Verified NOT changed: scanning engines, permissions/manifest, lifecycle, history
  (controller/store/consent/deletion), FieldDiag-04 (DeveloperDiagGateTest green),
  applicationId/version (com.hjinlabs.radioobserver 0.1.0-phase1), bottom tabs.

## Gate evidence (final tree, all exit 0, --no-daemon, tests/lint --rerun-tasks)

- :core:radio:test → 278/278 (266 previous + 12 new).
- :app:testDebugUnitTest → 45/45 (unchanged suites green).
- :app:compileDebugKotlin → success.
- :app:assembleDebug → success (DEBUG only).
- :app:lintDebug → 0 errors / 15 warnings (baseline; the new overflow string was
  converted to a proper plural, keeping the baseline).
- Debug APK: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1,
  23,777,690 bytes, SHA-256 082c456e103c797716020a4f97bc4440febcd7e63faab8717b998b9ef356f464.

NOT verifiable host-side: actual Compose rendering, touch geometry on dots, OEM
behavior — Xiaomi retest required. Declined checks (2.0x font, light theme,
TalkBack) are not part of acceptance; existing accessibility semantics were kept
(headings, contentDescriptions, 48dp targets) but not manually tested.

## Xiaomi manual test steps (developer)

1. Gözlemler: switch Liste→Radar→Zaman tüneli with a session ACTIVE; rows identical
   across views; switch dispatches nothing (no Start/Stop, no permission dialog).
2. Radar: honesty copy visible; dots by source color+shape; Eski cached Wi-Fi hollow;
   tap a dot → correct detail; return → view still selected within the session.
3. Timeline: rows ordered newest first by time; cached Wi-Fi still "Eski".
4. Filters (Tümü/Bluetooth/Wi-Fi) apply identically in all three views.
5. End the session → all views show the honest empty state; nothing lingers.
6. Geçmiş unchanged (records, delete, Clear All, consent); FieldDiag still hidden on
   cold start, seven taps.

## QA follow-up (2026-10-23): Xiaomi radar visibility discrepancy — CONFIRMED DEFECT, FIXED

Observation: Gözlemler showed 11 session observations (Tümü), but only ~3 radar
markers were distinguishable (two hollow Wi-Fi squares, one filled BLE dot).

### Root cause (proven, not assumed)
Real session rowIds are consecutive ordinals ("obs-1", "obs-2", …) whose String
hashes differ by EXACTLY 1. The original angle formula `(h % 36000) / 100` turned a
hash delta of 1 into a 0.01° angle delta, and the radius jitter `(h / 36000) % 1000`
was perfectly correlated with the same degenerate quantization. Exact simulation of
the real geometry: obs-1…obs-9 all landed at 263.1°, obs-10/obs-11 at 236.0°; every
same-ring dot sat at the SAME pixel position (e.g. obs-3…obs-9 all at 263.1° /
radius 0.984). So 11 records WERE projected and drawn — no data loss, no filtering,
no clipping — but drawn as fully overdrawn, uncounted super-impositions. The
screenshot was therefore a faithful rendering of a degenerate projection.

### Second defect found during this audit
RadarView hard-wired FeedFilter.ALL into its projection while the adjoining list used
the live filter: with Bluetooth/Wi-Fi selected, radar and list would disagree. Fixed:
the radar projection now honors the CURRENT filter in all three views.

### Minimal safe correction (presentation-only, deterministic)
- Angle: golden-ratio mix `((h * 0x9E3779B9) and 0xFFFFFFFF) * 360 / 2^32` —
  consecutive ordinals now spread ~132.7° apart (host-proven). Jitter decorrelated
  via an independent avalanche constant (0x85EBCA6B).
- Explicit cluster accounting: dots whose base geometry visually merges (chord
  distance < 0.12 of the canvas radius — two ~20dp markers on a ~170dp canvas)
  are grouped into count-bearing cluster markers (union-find; representative = first
  member in authoritative order). Nothing is silently dropped or overdrawn: the
  accounting invariant singles + Σcluster counts + documented overflow == source
  rows is host-tested. Cluster members stay individually reachable in the adjoining
  list; cluster hollow styling only when EVERY member is stale (mixed clusters stay
  filled — honest).
- Markers are now real Compose elements (clickable, announced via contentDescription)
  instead of Canvas-only pixels; rings remain Canvas. Honesty copy unchanged.

### Count reconciliation for the 11-row scenario (real ordinals, 2 BLE + 9 Wi-Fi
with mixed buckets incl. stale and unknown)
Before fix: (a)=11 source, (b)=11 post-filter, (c)=11 projected, (d)≈3 visually
distinguishable, (e)=0 explicitly counted clusters (overdraws uncounted),
(f)=0 cap exclusions → d+e+f ≠ 11 (defect).
After fix: (a)=11, (b)=11, (c)=11, (d)=11 individually distinguishable markers
(golden-angle spread: 19.8°…327.3°, no collisions in this fixture), (e)=0 clusters,
(f)=0 excluded → 11 = 11 ✓. Residual collisions in other sessions would be
explicit clusters with exact counts, still accounted.

### Regression evidence (final tree; all exit 0, --no-daemon, tests/lint --rerun-tasks)
- :core:radio:test → 282/282 (278 previous + 4 new: 11-row ordinals scenario with
  accounting invariant and collapse regression guard; synthetic residual-collision
  cluster grouping with representative/stable member order; mixed-freshness cluster
  honesty; golden-angle decorrelation proof).
- :app:testDebugUnitTest → 45/45. :app:compileDebugKotlin, :app:assembleDebug → success.
- :app:lintDebug → 0 errors / 15 warnings (baseline kept).
- Debug APK: com.hjinlabs.radioobserver 0.1.0-phase1, 23,878,986 bytes, SHA-256
  fe1c16b80f310315f8c0ba91b61216f040e8e3937f4c37aa7bb051cfe533d045.
- Unchanged: scanning engines, permissions/manifest, lifecycle, History (consent,
  deletion, retention), FieldDiag-04, applicationId, bottom tabs. Host tests cannot
  prove on-screen pixel geometry: Xiaomi retest of the 11-row scenario required.
