# Phase 3.6 — Sanitized Observation Feed and Detail Presentation

Status: implemented 2026-10-21 (code/host level). Real Compose click delivery,
TalkBack, text scaling, dark mode, Wi-Fi broadcast delivery and radio reception are
UNVERIFIED without a device.

## 1. Destinations and navigation

No navigation dependency was added: MainActivity holds a LOCAL typed destination
(`Screen: Home | Observations | ObservationDetail(rowId)`, `mutableStateOf`) plus a
feed-filter state. The 3.5 control screen gained a "Gözlemler" OutlinedButton
(`onOpenObservations`) — pure navigation, changes NO session/permission/scanning
state. Feed → Home and Detail → Feed return uses both the visible "Geri" button and
`BackHandler` (androidx.activity:compose, already a dependency). Navigation events
never call any scanner Start, permission request or session recreate; destination
state dies with the Activity (recreation → Home).

## 2. Feed (ui/FeedScreens.kt, ObservationsFeedScreen)

- Combined ephemeral read-only BLE + passive Wi-Fi feed over the CURRENT sanitized
  `ObservationUiState.rows`; All / Bluetooth / Wi-Fi `FilterChip` filters; per-row
  source word (Bluetooth/Wi-Fi — words, not color-only).
- Deterministic ordering: the pipeline's newest-first order is preserved verbatim by
  the pure projection; the screen NEVER re-sorts by proximity/identity. Rows render
  in a `LazyColumn` (bounded by the pipeline's authoritative 200-row limit) with
  `key = rowId` (session-local ordinal — never address/hash-derived).
- Last-observed: formatted only from the authoritative `lastSeenAtEpochMs`; no invented
  durations. Freshness shown as the honest word Taze/Eski/Tazelik bilinmiyor — carried
  verbatim from the pipeline; cached Wi-Fi can never render as fresh (no promotion
  path exists in the projection).
- Honest empty states (pure-JVM taxonomy `FeedEmptyKind`): active-session-no-rows,
  no-session-no-rows, filter-no-matches — all with the approved no-absence-proof note.

## 3. Detail (ObservationDetailScreen)

Resolved ONLY via `ObservationFeedProjection.detailFor(state, rowId)` against the
CURRENT sanitized state — no cached raw/stale copy exists. Missing fields render
"Bilinmiyor". Shown fields: source, name, conservative confidence word (incl.
"Doğrulanmış biçim" = format-level only, never identity), qualitative signal category
(+ SIGNAL_QUALITATIVE_NOTE: rough cue, not distance), freshness word, manufacturer/type
hints with CONFIDENCE_CAUTION (possible family, identity not verified), locally-
administered flag (Evet/Hayır), last-seen time, session-local row label. Wi-Fi rows
carry the passive-source note. A disappeared/evicted/TTL-expired/disposed/replaced row
renders the Turkish "detail_unavailable" state with an accessible return action;
nothing restarts a scan to recover it.

## 4. Privacy and freshness invariants

Only sanitized `ObservationRowUi` data exists on the UI surface (structural: no raw
address/BSSID/payload/parse-issue member can reach it); rowIds never travel in route
args to any external store; no raw values in semantics, logs, clipboard or persistence.
The pipeline's 200-row bound and 15-minute lazy TTL remain the ONLY retention
(authoritative source); the feed/detail layer adds no store, timer or polling. The
3.5 bug-risk review found no surviving-selection defect: `rowId` is the per-pipeline
ordinal (not the positional index), stable across snapshot recomputes, and disposal
publishes empty rows so any selection resolves to unavailable.

## 5. Pure-JVM projection (core/radio/ui/ObservationFeedProjection.kt, zero android.*)

`FeedFilter` (ALL/BLUETOOTH/WIFI), `FeedEmptyKind`, `rowsFor` (filter-only, order
preserving), `counts`, `emptyKind` (three-way honest taxonomy), `detailFor`
(current-state-only resolution; blank/unresolvable → null). Host-tested by
ObservationFeedProjectionTest (7 tests): filters (A), no-reorder + session-local keys
with per-radio overlap and new-session id reuse (B), detail from current state and
unavailable-after-disposal (C/D), FRESH/STALE/UNKNOWN distinct, no promotion (E/H),
sanitized-only surface (I), total/side-effect-free (J/K). These are STATE→PRESENTATION
tests, not Compose UI interaction tests.

## 6. Honest limits and 3.7 handoff

Feed/detail entry, filter, select and back dispatch zero Start/Stop/permission
effects; the 3.5 control-screen two-tap and foreground invariants are untouched.
Real Compose runtime, TalkBack, text scaling, dark mode, Wi-Fi broadcasts, BLE
reception, OEM lifecycle: UNVERIFIED (device-free protocol). Task 3.7 (error,
readiness and recovery UI) remains not started and requires separate approval.
