# Optional Local Observation History — Privacy-First Design Proposal (DESIGN ONLY)

Status: PROPOSAL. Nothing in this document is implemented. Persistent history
requires explicit fresh developer approval and a separately authorized
implementation iteration. The current behavior is UNCHANGED: observations are
session-local and transient, and everything is disposed when the session ends.

## 1. Current contract (unchanged by this proposal)

- Live observations live only inside the volatile session pipeline (bounded
  capacity, session-local sanitized identities, lazy TTL).
- When the last session ends, everything is disposed: sanitized rows and raw
  session-local evidence die with the session. No retrospective recovery.
- No MAC/BSSID/SSID/address payload is ever persisted; sanitization happens
  before any UI surface.
- UI today truthfully claims: nothing is written to disk, the feed empties
  when sessions end.

## 2. Proposed semantics (default OFF, opt-in only)

- History is OFF by default. The user must explicitly enable "Save future
  observation summaries". Enabling it NEVER recovers already-cleared data and
  never backfills: only future sessions start being summarized.
- Each history item is a SNAPSHOT of sanitized, session-local summary facts
  produced at save time; the live session keeps its existing ownership and
  remains fully transient. History never keeps a scanner alive and never
  extends a session.
- Scope per item (minimal fields):
  - item ID: random (UUID), generated per saved item — NOT derived from any
    physical device identifier; IDs are local-only and never leave the device.
  - source type: BLE or Wi-Fi.
  - user-facing sanitized hint (the existing display name category).
  - coarse signal category (e.g. near/far), if the session had one.
  - capture time (device-local wall clock).
  - explicit uncertainty marker (e.g. "cached, not newly detected" for Wi-Fi
    results with the platform updated=false flag; unknown ages stay unknown).
- Explicitly NOT stored: SSID, BSSID, MAC, raw advertisements, coordinates,
  location payloads, cross-session device identities, or anything that
  re-identifies a physical device across sessions.

## 3. User controls

- Enable/disable at any time; disabling stops future saving (existing items
  remain until deleted by the user or by retention).
- Delete one item; delete a whole session's items; delete all.
- Retention: default proposal 7 days, hard maximum 30 days, max 500 items
  (ring eviction: oldest dropped first). These are proposals for discussion,
  not silently enforced product requirements.
- Visible notice in settings and near the toggle explaining exactly what is
  retained, for how long, and that clearing app data/uninstalling removes it.

## 4. Storage, deletion and migration

- Local-only: Android app-private storage (app sandbox). No cloud, no network
  export; excluding the history file from OS backup is recommended at
  implementation time.
- Retention enforcement: prune on every app start and on every save; delete-all
  removes the store file(s).
- Honest limitations: local storage deletion is immediate at the file level the
  platform guarantees; no claim beyond that (no cryptographic-erase claims).
  Uninstalling or clearing app data deletes history (stated plainly).
- Migration failures: if a stored schema version is unreadable, drop the store
  and disable history (fail closed to privacy), with a one-time notice. No
  silent partial recovery.
- No new dependency is proposed: use storage already configured in the project
  at implementation time. Any new dependency requires explicit approval.

## 5. Risk analysis

- Even coarse facts leak habits: a timestamp plus a manufacturer hint plus a
  signal category can imply where the user was and when. Mitigations:
  coarsest useful categories, short default retention, visible notice,
  local-only storage, no auto-enable.
- The history toggle must never interact with scanning policy: enabling history
  does not start, extend, or schedule any session.
- UI copy update (only in the authorized implementation): the current
  "nothing is written to disk" claim must change to accurately describe the
  opt-in history versus transient live observations, and only when history
  ships.

## 6. Acceptance criteria (for a future authorized implementation)

1. Fresh install: history OFF, zero files written, existing feed/privacy
   behavior byte-identical.
2. Opt-in: future sessions produce bounded summary items only; the live
   pipeline remains transient and fully disposed on session end.
3. No MAC/BSSID/SSID/coordinates/cross-session identities in the store
   (verified by store-content tests).
4. Disable/enable/delete-one/delete-session/delete all behave as specified;
   retention prune proven by tests with fake clocks.
5. No scan/session behavior change from any history interaction (regression
   tests on the existing start/stop/lifecycle suite).
6. Host/JVM tests cover store schema, retention, deletion and the fail-closed
   migration path; UI wiring tests cover toggle + notices.

## 7. Test strategy sketch

- Pure-JVM store tests (schema, retention, eviction, migration fail-closed).
- Mapper tests: sanitized item fields, uncertainty markers, cached labels.
- Existing full lifecycle suite must stay green with history code present but
  disabled (proof of zero behavioral coupling).
