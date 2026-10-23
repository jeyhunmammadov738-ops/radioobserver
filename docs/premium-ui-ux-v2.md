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

## Final polish addendum (2026-10-23, developer-approved)

Narrow post-V2 iteration; three items from the newest Xiaomi screenshots.

### 1. Compact history record cards
Before: saved-record cards still tall with excess empty space. After
(HistoryRecordRow in ui/HistoryScreens.kt): card padding 12dp → horizontal 12 /
vertical 8; internal gap 4dp; action buttons slimmed via `contentPadding`
(horizontal 12 / vertical 4) while keeping the enforced 48dp minimum target
(`heightIn(min = 48.dp)`). Card tap still does NOTHING; only the explicit
Ayrıntılar/Sil buttons act, and both now carry name-scoped TalkBack descriptions
("X kaydını sil" / "X kaydının ayrıntılarını aç"). Filtering, sort order,
single-delete, confirmation and the HistoryController contract untouched.

### 2. Natural Turkish technical descriptions
Root cause: the core Wi-Fi classifier emits honest ENGLISH sentinel company names
("Unknown vendor (locally administered BSSID)"); sanitization passes them verbatim,
so the UI printed English on the device. Fix at the PRESENTATION boundary only:
new pure `ui/ManufacturerPresentation.kt` recognizes exactly those two sentinels;
the UI renders "Üretici belirlenemedi" and, for the locally-administered sentinel,
adds "Yerel olarak atanmış Wi-Fi adresinden güvenilir üretici bilgisi
çıkarılamıyor." Real OUI-derived vendor hints pass through UNCHANGED. The
classifier, its detection logic, its typed models and its JVM tests (which assert
the English sentinels) are untouched; no BSSID is ever exposed and no vendor is
invented. Applied to BOTH the live detail (with the row's locallyAdministered
flag) and the history detail (sentinel-driven).
Signal semantics clarified on the history detail signal card: the existing
"Sinyal kategorisi kaba bir radyo ipucudur, kesin mesafe değildir." plus the new
"…kayıt anındaki durumu yansıtır; cihazın şu anda yakın olduğunun kanıtı değildir."
The human-readable record age ("yaklaşık 47 dakika", labeled as the age AT
RECORDING TIME) is preserved; new negative-value hardening keeps corrupt ages from
rendering negative durations.

### 3. Responsive layout / typography at large font scales
The dashboard Start/Stop pair moved from a fixed Row to an adaptive FlowRow:
side-by-side at normal widths, reflowing to full lines at ~320dp / 2.0x font scale
so labels are never clipped. All text-bearing surfaces remain height-free
(wrapping Text, weight-based splits); Gözlem/Gözlemler/Geçmiş/Ayarlar and both
details remain scrollable columns; the Scaffold innerPadding still keeps the
bottom bar off content. Four tabs, deterministic Back, FieldDiag-04 cold-start
hidden state, seven-tap DEBUG unlock: all untouched.

### Test evidence (final tree; all exit 0)
- :core:radio:test --rerun-tasks --no-daemon → 266/266.
- :app:testDebugUnitTest --rerun-tasks --no-daemon → 39/39: previous 35 plus NEW
  ManufacturerPresentationTest (3: sentinel recognition, locally-administered
  distinction, real-hint/null/never-prefix cases) and a new negative-age case in
  HistoryAgeFormatTest.
- :app:compileDebugKotlin --no-daemon → success.
- :app:assembleDebug --no-daemon → success (DEBUG only).
- :app:lintDebug --rerun-tasks --no-daemon → 0 errors / 15 warnings (unchanged count).
- Debug APK: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1,
  23,711,886 bytes, SHA-256
  76a400f38ce33d5371c42152db6437e69061879912d18406731ac8dd1e922192.
- Host checks cannot certify: real 2.0x rendering, TalkBack output, touch geometry,
  insets on the Xiaomi device — manual retest required.

### Remaining Xiaomi manual test checklist
1. Geçmiş rows compact; Ayrıntılar/Sil easy to hit; a card tap never deletes.
2. History detail: "Üretici belirlenemedi" + locally-administered explanation;
   signal card shows the recorded-time note; age reads "yaklaşık X dakika".
3. Gözlem at 2.0x / narrow: Başlat/Durdur wrap to separate lines, nothing clipped.
4. Ayarlar scrolls fully at 2.0x; bottom bar never covers content.
5. FieldDiag-04: cold start hidden; seven version taps; show/hide works.
