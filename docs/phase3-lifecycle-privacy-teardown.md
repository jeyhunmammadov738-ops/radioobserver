# Phase 3.8 — Lifecycle Ownership, Privacy Teardown & State Restoration

Status: audited and closed 2026-10-21 (code/host level). One proven defect found and
fixed (below). Android runtime callback delivery, OEM behavior and real radios remain
UNVERIFIED (device-free protocol).

## 1. Ownership matrix (verified against source, not memory)

| Resource | Owner | Start edge | Stop/disposal edge | Late-callback guard |
|---|---|---|---|---|
| Activity + Compose state | MainActivity (Activity-local mutableStateOf; NO ViewModel/SavedStateHandle) | onCreate | dies with Activity (recreation = fresh inert state) | n/a |
| Navigation/filter/selection | Activity-local Screen/FeedFilter state | user click only | recreation returns Home | no persistence |
| Permission launcher | PermissionFlowController (single ActivityResult launcher, registered unconditionally in 3.3) | user Start tap | result consumed once; in-flight guard | WeakReference activity; destroyed → skip |
| BLE scan | AndroidBleScanEngine → pure-JVM BleSessionCoordinator | supervisor.startBleSession (RESUMED only) | handle.cancel / notifyForegroundLost / timeout; started flag | `if (!started) return` drops late scan callbacks |
| Wi-Fi receiver | AndroidWifiResultEngine (context-registered, RECEIVE_ONLY) | startObservation | stop() unregisters; idempotent | action guard + `started` flag |
| Session tokens | ObservationSessionOrchestrator generations (ble/wifi) | per Start (++generation) | invalidateGeneration on SessionEnded | events with stale generation dropped (`live=false`) |
| Foreground eligibility | RadioObservationSupervisor (AtomicBoolean; RESUMED-only) | onHostResumed | onPause/onStop/onDestroy all stop BOTH sources idempotently | ineligible start refuses closed |
| Shared pipeline | ObservationPipeline (200 rows / 15-min TTL authoritative, unchanged) | first source Started | closeAndDispose ONLY when the LAST radio ends | other radio's rows preserved while live |
| Public UI rows | sanitized ObservationUiState via orchestrator publish | pipeline publicSnapshot | disposal clears rows (withRowsCleared) | detail resolves current state → unavailable |
| Pending Start intent | PermissionFlowState.pendingStartTap (transient) | Start tap that opened a dialog | consumed on result; ActivityRecreated drops it | never replayed; no start effect exists |

Verified guarantees (existing tests cited): no auto-start on resume/recreation
(Orc-A, Sup "RESUMED alone never auto-starts"); two explicit taps after grant (Orc-D/E);
foreground loss stops both, no revival (Orc-I, Sup "resuming after loss does not
revive"); independent Stop preserves the other radio and its rows (Orc-G, Orc-P);
pipeline disposed once at last end (Orc-P); duplicate stop/start idempotent (Orc-H,
Sup); timeout/late callbacks safe (Orc-K); fresh owner starts from zero (Orc-J);
recreation drops transient Start, keeps in-flight guard (3.8 tests below).

## 2. Proven defect found and fixed (root cause → change → proof)

Defect (honest state restoration, introduced by the 3.7 onResume wiring):
`SettingsReturned` mapped a grantless snapshot to `LocationAccessUi.DENIED` even when
NO runtime request had ever happened — a fresh install's first launch (or process-fresh
Settings return) claimed a denial that never occurred. The resolver ignored the genuine
evidence base (`previouslyRequested`, and previously-held access).

Fix: `LocationAccessResolver.resolve(snapshot, runtimeRequestHappened)` — with nothing
granted, DENIED requires denial EVIDENCE: a genuine past runtime request OR access
that was previously held (revoked-after-grant stays honestly DENIED, verified by the
pre-existing regression test "revoked-after-grant on Settings return"). The reducer
supplies the evidence: PermissionResult → true (a dialog genuinely ran);
SettingsReturned → previouslyRequested.isNotEmpty() || current access != NOT_REQUESTED.

Proof: new `PermissionRestorationTest` (5 tests): fresh-process Settings return →
NOT_REQUESTED; post-request return → DENIED; runtime result consumes pending tap with
no start effect; recreation drops pendingStartTap but keeps the in-flight guard;
resolver never fabricates denial. The revocation regression surfaced and re-passed in
the full suite (223/223).

## 3. Privacy teardown facts (unchanged, re-verified)

No raw MAC/BSSID/payload crosses into Compose (typed sanitized keys only); no
persistent observation history (all state Activity-local; process recreation = no
sessions, no rows, no selection, no pending Start); session-local row IDs unchanged;
200/15-min bounds unchanged; Wi-Fi stays receive-only (no startScan anywhere); detail
selection resolves only current public state (3.6 unavailable behavior). Only
non-sensitive view preference retained: none (filter/selection are Activity-local by
design; nothing persisted by 3.8).

## 4. Tests and honest limits

Total 223/223 host tests (5 new in PermissionRestorationTest; 218 historical
preserved). App JVM tests: 0 (no facility). UNVERIFIED: real Android lifecycle callback
ordering, configuration-change recreation timing, permission dialog races on device,
OEM behavior. 3.9 (accessibility/layout polish) remains not started; separate approval.
