# Phase 3.9 — Accessibility, Responsive Layout and Polish

Status: audited and closed 2026-10-22 (source level). TalkBack, real font scaling,
layout rendering and dark mode on a device remain UNVERIFIED (device-free protocol).

## 1. Criterion-by-surface audit (what already met the bar — no churn)

Verified as already satisfying (kept unchanged, with evidence): text-bearing
Start/Stop/Back/OpenFeed/Recovery buttons use visible text plus contextual
contentDescription including the radio name and status (no ambiguous "button"
announcements; radios distinguishable); Card(onClick) rows carry native Role.Button
and full sanitized row descriptions; FilterChip selected-state semantics are native;
recovery action a11y ("Kurtarma eylemi: <eylem>, <radyo>, düğme") already distinct;
48dp minimums already on all OutlinedButton/Button; control screen is vertically
scrollable (long privacy/scope copy never hidden); feed list is a LazyColumn with
content padding; honest empty/unavailable states unchanged; filter/label/empty/row/
detail mappings are host-tested (ObservationFeedProjectionTest A-K, existing 223/223);
no raw MAC/BSSID/payload appears in any string or accessibility description
(grep-verified); status is never communicated by color alone (text + issue lines).

## 2. Proven defects found and fixed (root cause -> change -> proof)

A. Dropped session limit in screen-reader semantics (ControlScreen status Row):
   the merged contentDescription announced only "Durum: <status>" while the visible
   row also showed "Oturum sınırı: N sn" — information loss for TalkBack users.
   Fix: the merged description now includes the session limit when present.

B. No heading semantics: control/feed/detail/card titles announced as plain text.
   Fix: heading() added to control, feed and detail screen titles and both radio
   card titles (structure navigation).

C. Filter chips below the guaranteed 48dp touch target and chip row overflow risk
   at large font scales (plain Row). Fix: explicit heightIn(min = 48.dp) on all
   three FilterChips and FlowRow (existing foundation API, no new dependency) so
   chips wrap instead of clipping on narrow/large-type layouts.

D. Turkish polish: "Oturum kaydı (oturuma özel)" was ambiguous ("oturum" twice);
   now "Oturum kaydı (yalnızca bu oturumda geçerli)". strings.xml indentation
   inconsistency fixed. No approved privacy/honesty wording was strengthened.

## 3. Invariants preserved (unchanged, re-verified)

No changes to scan engines, manifest, permissions, foreground policy, dependencies,
SDK/AGP/Kotlin/Compose versions, applicationId/namespace, versionCode/Name, TTL
(15 min), record cap (200), storage, or any 3.3-3.8 behavioral contract. All UI
changes are stateless presentation-only; no navigation/session/permission behavior
touched. Two-tap Start, no auto-start on any UI event, receive-only Wi-Fi, sanitized
PublicObservation only — all intact.

## 4. Tests and honest limits

No pure Kotlin projection was modified, so no new host tests were required; the full
existing behavioral matrix (223/223, including feed/filter/empty/detail/privacy
regressions A-K) passes on the final tree. :app:testDebugUnitTest 0 tests (none
configured). compile/assemble/lint all EXIT 0; lint 0 errors, 15 warnings (baseline
unchanged, no new warnings). APK identity/permissions unchanged. UNVERIFIED: real
TalkBack traversal, measured contrast, runtime font-scale layout, dark-mode render,
click delivery on a device. Phase 3.10 (final quality gate and handover) remains
not started and requires separate approval.
