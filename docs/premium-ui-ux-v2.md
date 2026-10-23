# Premium UI/UX V2 — Design Handoff (2026-10-23)

Post-roadmap, developer-approved refinement of the four-tab Material 3 UI. NOT a new
phase; roadmap tasks/checkmarks/evidence untouched. No radio/permission/scanning
behavior change. DEBUG build only; no release artifacts; no new dependency.

## Before/after inventory (per developer's seven dark-mode screenshots)

1. Gözlem — BEFORE: two repetitive high grey cards, oversized copy, weak status
   priority, inefficient spacing. AFTER: compact branded heading; truthful summary
   strip (Aktif kaynak / Canlı kayıt, or the honest none-active note); refined source
   cards with BLE/Wi-Fi vector icons, a FIRST-CLASS StatusPill in the header row,
   one-line captions, prominent Start/Stop; long copy stays in the collapsed
   Bilgi ve gizlilik disclosure. Session limits shown as fixed bounds (20s/30s),
   never a fabricated countdown.
2. Gözlemler — BEFORE: equally heavy rows, repeated anonymous names, weak timestamp
   priority. AFTER: dense breathable rows (source icon, name 2-line wrap, source word,
   right-aligned last-seen + freshness PILL), tighter rhythm (4/8dp scale), LazyColumn
   keys stay real session-local rowIds; freshness never promoted on display.
3. Geçmiş — BEFORE: oversized cards, blank areas, disproportionate actions. AFTER:
   compact rows (icon, safe name, source·timestamp meta), explicit two-action footer
   (Ayrıntılar / Sil, both 48dp); a row tap can never delete; Clear All still confirmed.
4. Ayarlar — BEFORE: long consent/privacy paragraphs overpowering the controls. AFTER:
   grouped preference cards (Geçmiş kaydı, Gizlilik ve çalışma şekli, Uygulama hakkında,
   Geliştirici) with short truthful summaries next to each toggle and the full
   consent/privacy text one tap away (expandable; nothing essential deleted).
   Defaults preserved: recording OFF, Wi-Fi-names OFF, 7/14/30 days, 500 cap.
5. Gözlem ayrıntısı — BEFORE: uneven key/value alignment. AFTER: shared
   CompactInfoRow system (label secondary / value primary, wrapping, no hard-coded
   heights) with consistent section headers.
6. Geçmiş Ayrıntıları — BEFORE: near-black rectangular sub-panels inside grey cards.
   AFTER: the SAME card + CompactInfoRow language as the live detail; no nested
   panels; record age shown honestly as "yaklaşık 47 dakika" (rounded down, labeled
   as the age AT RECORDING TIME — never the present elapsed time).
7. Bottom navigation — unchanged contract (four tabs, deterministic Back, hidden on
   details); M3 NavigationBar visual states kept; no routing change.

## Design system (ui/components/DesignSystem.kt)

- Spacing: documented 4/8dp scale (xs=4, sm=8, md=12, lg=16, xl=24) used across all
  screens; no ad-hoc paddings in the rewritten surfaces.
- PillKind (NEUTRAL/ACTIVE/ATTENTION/ERROR) + pure PillStyle.kindFor(status) mapping;
  tonal colors derived from the existing indigo/violet scheme; the WORD always carries
  the meaning (never color alone).
- StatusPill, SectionHeader, CompactInfoRow, SourceIcon, EmptyState — one shared
  component language; radio icons are bundled vector drawables (Apache-2.0 Material
  symbol geometry), no new dependency.
- DashboardSummary: pure, tested projection of active-source count / any-active from
  orchestrator state only.

## Preserved guarantees (regression traceability)

- Explicit Start/second-tap permission rule, foreground-only eligibility, teardown on
  foreground loss: untouched (no orchestrator/presenter change).
- Independent BLE/Wi-Fi sessions, 20s/30s limits, passive Wi-Fi only (no startScan
  anywhere in the diff), sanitization pipeline/TTL/200-row cap: untouched.
- Optional local history: same HistoryController/store; consent defaults, deletion
  races, retention, 500 cap, backup exclusions unchanged; Settings drives the SAME
  state as Geçmiş (single controller, verified by unchanged HistoryControllerLogicTest).
- Navigation policy: pure AppNavigation unchanged (AppNavigationTest still passing);
  tab taps never start/stop sessions or request permissions by construction.
- FieldDiag-04: cold start hidden, seven version-tap unlock, DEBUG-only, nothing
  persisted (DeveloperDiagGateTest still passing; panel itself untouched).
- App identity/permissions/manifest/SDK/versions: untouched.

## Test and build evidence (device-free, this run; all exit 0)

- :core:radio:test --rerun-tasks --no-daemon → 266/266 (unchanged suites).
- :app:testDebugUnitTest --rerun-tasks --no-daemon → 35/35: AppNavigationTest 9,
  DeveloperDiagGateTest 8, FieldDiagnosticsRingTest 4, HistoryControllerLogicTest 2,
  plus NEW DesignSystemLogicTest 4 (status→pill), DashboardSummaryTest 3 (active-source
  truthfulness), HistoryAgeFormatTest 5 (human-friendly Turkish age incl. the
  2,838,035 ms → "yaklaşık 47 dakika" case).
- :app:compileDebugKotlin --no-daemon → success.
- :app:assembleDebug --no-daemon → success (DEBUG only).
- :app:lintDebug --rerun-tasks --no-daemon → 0 errors / 15 warnings (baseline was 16;
  the now-orphaned raw-milliseconds age format was removed).
- Debug APK: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1,
  23,705,186 bytes, SHA-256 e9c3936310337a16cfc90460d2d5650768083eab624e0c5a3cd7e8aa0625b1e2.
- Host tests CANNOT cover: real TalkBack output, touch-target geometry, font-scale
  overflow on the Xiaomi device, actual contrast measurement, inset behavior under
  gesture navigation.

## Open phone-retest criteria (Xiaomi, DEBUG APK)

1. Dashboard: summary strip truthful; status pills legible; Start/Stop reachable at
   2.0× font scale; no card content clipped in landscape/short heights.
2. Feed: rows readable, no chip/list overlap while scrolling; freshness pill matches
   the honest freshness (old cached Wi-Fi rows stay "Eski").
3. Geçmiş: compact rows; Sil never triggers from a row tap; Clear All confirmation.
4. Ayarlar: toggles drive the same state Geçmiş shows; long consent text expands.
5. Detail screens: one visual language; history age reads "yaklaşık X dakika/saat/gün".
6. Developer unlock: FieldDiag hidden on cold start; 7 taps; show/hide works.
7. Light and dark modes; TalkBack spot check; gesture-nav insets.
