# Workstream 02 — Advanced Live Filtering and Saved Filter Presets (2026-10-24)

Developer-approved scope ONLY: advanced live filtering + explicitly user-managed
saved filter presets inside the existing Gözlemler tab. Workstreams 03–08 were NOT
started. Build Mode ON (DEBUG only). Git commit/push performed under the developer's
explicit per-message full authority (the attached prompt's "OFF" is overridden by the
owner's message, which states it is invalid).

## Filter model and semantics
- Pure Kotlin `core/radio/ui/LiveFilter.kt`: `AdvancedCriteria` =
  signalBuckets + freshness + manufacturerKnownOnly + manufacturerValues, plus the
  separate TRANSIENT text search (`query`).
- OR within a category; AND between categories; EMPTY selection = unrestricted for
  that category. UNKNOWN/UNAVAILABLE values are explicit choices and NEVER silently
  match known selections.
- Source filtering stays in the established Tümü/Bluetooth/Wi-Fi chips
  (FeedFilter). The advanced panel does not duplicate it; presets may restore it.
- Manufacturer matching is ONE category (OR): status (known/unknown hint) OR exact
  sanitized hint value (case-folded). Values-only criteria never degenerate to "all".
- Text search: sanitized display names only, deterministic locale-independent
  Turkish fold (İ/I/ı → i). Raw MAC/BSSID/advertising bytes are structurally
  unreachable. Rows without a name never match a non-blank query. NEVER persisted.
- Reset = unrestricted criteria + cleared search (All sources default).

## Allowed sanitized fields (documented mapping)
Filterable: `radio` (BLE/WIFI), `signalBucket` (IMMEDIATE/NEAR/FAR/UNAVAILABLE),
`freshness` (FRESH/STALE/UNKNOWN), `manufacturerHint` (sanitized, hint ≠ identity),
`name` (sanitized display name, search only). Not filterable and never invented:
raw addresses, BSSID, advertising bytes, timestamps-derived geometry, confidence
(no physical-distance meaning), locallyAdministered (identity inference forbidden).
Device category: DEFERRED — the sanitized model exposes only `typeHint`, which is
not a stable classification; nothing was invented.

## UI state ownership and pipeline
- `MainActivity` owns applied state: `advancedCriteria`, `searchQuery`
  (non-persisted UI state), `filterPresetRepository` + `filterPresets` list.
- `ObservationsFeedScreen` renders; `FilterPanel` edits a LOCAL DRAFT committed
  only by explicit Uygula (Apply) / Sıfırla (Reset). Applying a preset resyncs the
  draft (deterministic reconciliation; applying a preset CLEARS temporary search).
- ONE shared projection boundary: `LiveFilter.rowsFor(state, source, criteria, query)`
  feeds Liste, Radar AND Zaman tüneli (JVM-tested `sameMembership`). Radar filters
  BEFORE marker layout/cluster accounting; the invariant singles + Σcluster members
  + overflow == eligible filtered rows still holds (test I/J). Timeline ordering is
  untouched (last-seen, stable rowId tie-break).
- Result accounting: "x / y kayıt gösteriliyor" (x = displayed, y = session rows)
  visible whenever advanced filtering is active; filter-excluded empties show the
  dedicated `filter_no_matches` state, never "no devices found".

## Preset storage format
- `core/radio/ui/FilterPresets.kt`: hand-rolled line codec (same style as the
  history codec). Header `#radioobserver-presets-v1`, one line per preset, 8
  fields: id | name | version | source | buckets csv | freshness csv | K/U/- |
  values csv, with escaping. Preset ids: `fp-<epoch>-<ordinal>` — repository
  bookkeeping, completely unrelated to radio identity.
- Presets store ONLY: id, user-supplied name, version, source filter, categorical
  criteria. Structurally no field exists for MAC/BSSID/adv bytes/session ids/
  snapshots/GPS/fingerprints/search text.
- Bounds/validation: MAX 20 presets, names trimmed/non-blank/≤40 chars, duplicate
  names rejected deterministically (case-folded). Filters are NEVER auto-saved;
  startup default is unrestricted (established UI-state contract: non-persisted).
- Persistence: `app/.../filters/FilterPresetFileStorage.kt` mirrors the atomic
  HistoryFileStorage pattern (app-private filesDir, temp+rename, allowBackup=false
  already in manifest; file `filter_presets_v1.txt`). Restart-surviving (JVM-tested
  via repository reload simulation).
- Corruption safety: malformed lines are dropped+counted; unknown header/version
  fails closed to an empty list WITHOUT rewriting or deleting the stored file; load
  de-duplicates ids and folded names keeping the FIRST. No history store touch.

## Explicit privacy boundaries
No raw identifiers searched/stored; manufacturer hints treated as hints, never
identity; no cross-session device tracking, no co-travel inference, no background
tracking, no GPS, no external APIs/cloud, no new permissions, no active Wi-Fi
scanning. Presets are categorical configuration, never watch rules. Session/lifecycle
contracts untouched: filters dispatch nothing (no Start/Stop, no permission), never
keep stale rows alive, and disposal empties filtered views (test R). History
(consent, deletion, retention, 500-cap, separate SSID consent) and FieldDiag-04
untouched. Manifest/permissions/session orchestrator/scan policy NOT modified.

## Regression test matrix (all JVM/host)
Existing Workstream 01 regressions kept (282 core baseline) and extended +17:
A default unrestricted; B sources; C bucket single/multi; D freshness incl. UNKNOWN
exclusion; E manufacturer known/unknown/values; F Turkish fold + no-name rows;
G OR/AND semantics; H determinism + no source mutation; I identical three-view
membership; J radar accounting invariant under filters; K timeline ordering; L
reset; M preset save/apply/rename/delete; N reload/restart persistence; O bound +
name/duplicate validation; P corrupt/unsupported store handling; Q no search text or
radio identifiers in the store (structural + round-trip); R disposal with active
filter. S (history/FieldDiag): untouched code paths, existing suites stay green.

## Gates (final tree, --no-daemon, tests/lint --rerun-tasks, all exit 0)
`:core:radio:test` → 299/299. `:app:testDebugUnitTest` → 45/45.
`:app:compileDebugKotlin`, `:app:assembleDebug` → success. `:app:lintDebug` →
0 errors / 15 warnings (baseline kept; the two new PluralsCandidate warnings were
fixed by converting the new count strings to plurals resources).
APK: com.hjinlabs.radioobserver 0.1.0-phase1 (versionCode 1), 24,578,558 bytes,
SHA-256 89c2421376bf2a41d9f4dfbf96bf9bb0a1d08ec6eaa477153b99f3a5ba61e43b.
DEBUG only: no release, AAB, signing, version bump, distribution. Install-over is
safe (applicationId unchanged); do NOT uninstall — existing history is preserved.

## Known limitations
- Manufacturer VALUES selection exists in the typed model and codec but the panel
  UI this workstream offers only known/unknown (values UI deferred — no stable
  manufacturer catalog without Fieldwatch license-controlled data).
- Host tests cannot prove on-screen geometry or touch behavior; Xiaomi retest
  required (below). Applied filters/presets do not survive process death except
  presets (by design; established non-persisted UI-state contract).
- Developer-declined checks (2.0x font, light theme, TalkBack) were NOT performed
  and are NOT claimed.

## Xiaomi phone verification steps
1. Gözlemler → Filtreler: badge count appears as criteria are applied
   ("Filtreler (n)"), "x / y kayıt gösteriliyor" line shows real accounting.
2. Sinyal kategorisi multi-select: all three views show the same members.
3. Tazelik: cached Wi-Fi rows stay "Eski"; UNKNOWN only matches when selected.
4. Üretici ipucu: Bilinen/Bilinmeyin filter honestly; hints unchanged on detail.
5. Ara: Turkish case-insensitive (İ/ı/I); nameless rows excluded; panel note says
   it is temporary; after restart the search box is empty.
6. Uygula/Sıfırla: Reset restores everything (chips stay on their chosen source).
7. Kayıtlı ön ayarlar: save → apply (restores chips + criteria, clears search) →
   rename → delete (confirmation dialog); ≤20 bound; duplicate name rejected with
   visible error. Restart the app: presets survive; History untouched (Ayarlardan
   doğrula); FieldDiag-04 still opens with 7 taps on the version label.
