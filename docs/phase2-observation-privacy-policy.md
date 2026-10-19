# Phase 2.7 — Observation Pipeline & Privacy Policy (Decision Record)

Status: IMPLEMENTED (2026-10-19). Components: `ObservationPipeline`,
`ObservationFreshness`, `PublicObservation`, `PipelineObservationListener`
(all pure JVM, `:core:radio`, zero `android.*` imports).

## 1. Dataflow

Session coordinators (BLE 2.3 / Wi-Fi 2.4) -> `ObservationEvent.DeviceObserved` /
`ObservationEvent.WifiResultsAvailable` -> `PipelineObservationListener` ->
`ObservationPipeline.submit` (newest-wins dedup, bounded retention) ->
`publicSnapshot()` -> `PublicObservation` (privacy-safe projection; Phase 3 UI consumer).

**Activation state (honest):** nothing in production wires a session to the pipeline yet;
an explicit user-start UI is Phase 3 scope. The pipeline exists, is host-tested, and is
inert until that wiring is authorized.

## 2. Identifiers

- Raw BLE addresses / Wi-Fi BSSIDs exist ONLY as volatile `ObservedRadioDevice.address`
  (and session-local dedup keys) inside the pipeline. They are never in the public
  projection: `PublicObservation` cannot structurally expose them.
- `observationId` is a per-pipeline session-local ordinal (`obs-1`, `obs-2`, …),
  deliberately NOT derived from any address and NOT a hash. Ordinals restart in a new
  pipeline and refer to unrelated data, so cross-session correlation via ids is
  impossible by construction. No long-lived, silently linkable hash-derived identifiers.
- Randomized BLE addresses / locally administered BSSIDs are only session-local dedup
  keys; they are never treated as durable identity.
- Parse-issue texts may embed raw identifiers (e.g. malformed BSSIDs); the projection
  exposes only `parseIssueCount`.

## 3. Masking

The public projection carries at most an OUI-prefix mask. Raw addresses are withheld
entirely (no full address in any projection field); malformed/absent addresses are
reported as `(withheld)`/null. Raw advertisement bytes are never part of the evidence
model at all (Phase 1 design) and thus unreachable.

## 4. Capacity, TTL and freshness

- Capacity: hard 200 entries default (`RetentionConfig.maxEntries`, configurable);
  deterministic oldest-first eviction under bursts. No unbounded queues.
- TTL: entries older than 15 minutes (configurable) are swept lazily on access —
  no timers, no background processing.
- Freshness (`FreshnessState` FRESH/STALE/UNKNOWN):
  - Wi-Fi age uses ONLY the boot-relative result age (`resultAgeMs`); if unknown, the
    state is UNKNOWN — the receipt clock is never substituted, so "when the cache was
    read" cannot masquerade as "how old the data is".
  - Cached Wi-Fi results (`resultsUpdatedFlag=false`/missing) can NEVER be FRESH.
  - BLE age is receipt wall-clock; negative/future/overflow values are UNKNOWN.
- The RETENTION TTL (receipt wall-clock base) is a separate concept from RESULT age;
  the two are never conflated.

## 5. Disposal

`ObservationPipeline.closeAndDispose()` is terminal: late events fail closed
(`IgnoredClosed`), all volatile identifiers are dropped, and there is no reopen — a new
observation window requires a new pipeline instance. `clear()`/`remove()` are explicit
deterministic disposal paths. Post-expiry or post-close events cannot revive data.

## 6. Confidence limitations

The pipeline carries confidence and type labels VERBATIM from the Phase 1 evidence
models; it never promotes a candidate to a proven product, person, position, distance,
or threat. Vendor ID, SSID text, OUI, or RSSI NEVER prove an AirTag/SmartTag or any
person's presence. Unknown remains unknown; malformed/empty payloads, null/invalid RSSI
and implausible timestamps fail safely (`UNAVAILABLE` bucket, UNKNOWN freshness,
`Rejected`/`IgnoredStale` outcomes) without false certainty.

## 7. Local-first, no export by default

The pipeline is in-memory volatile only. There is NO file, database, preference, network,
telemetry, analytics, clipboard/share, or logging path in any pipeline class (source
audited), and none may be added without new authorization. No new permissions were
added or needed; the manifest and APK permission set are unchanged.

## 8. What is verified and what is not

- VERIFIED (device-free, this iteration): dedup/newest-wins, bounded capacity, deterministic
  eviction and TTL, freshness honesty incl. cached-result blocking, masking, disposal
  semantics, defensive inputs — 18 new JVM tests (143/143 total green); compile/packaging.
- UNVERIFIED (requires a device): real radio reception quality, actual Wi-Fi broadcast
  cadence, BLE advertisement payloads on real hardware, Android runtime behavior. No
  production data flow is wired yet (Phase 3), so no real observations exist anywhere.
