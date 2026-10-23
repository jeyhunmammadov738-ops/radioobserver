# Professional Material 3 UI/UX Redesign and Hidden Debug Diagnostics (2026-10-23)

Post-roadmap, developer-approved UI iteration. NOT a new roadmap phase; no historic
task or checkbox reopened; no radio/storage/scanning capability change.

## Previous UI shortcomings (from the developer's Xiaomi screenshots)

- Oversized title + several always-visible paragraphs pushed Start controls below the fold.
- BLE/Wi-Fi large monochrome cards with long copy; weak hierarchy.
- A permanently exposed "Tanılama / FieldDiag-04" strip made the app feel like a prototype.
- Ad-hoc "Gözlemler"/"Geçmiş" buttons at the bottom instead of consistent navigation.
- Feed rows sparse; filter chips could crowd the list.
- History mixed browsing with recording/retention/privacy preferences in one long page.
- Details were dense labeled-text dumps without grouping.

## New screen structure (four-tab bottom navigation)

Material 3 `Scaffold` + `NavigationBar` with four tabs (Turkish, core icons already
bundled — NO new dependency):
- Gözlem — compact live control dashboard (heading, subtitle, truthful session
  summary, BLE/Wi-Fi status cards with Start/Stop, recovery actions, collapsed
  "Bilgi ve gizlilik" disclosure).
- Gözlemler — live sanitized feed (chips, consistent rows: safe label + source/
  freshness tonal badges + last-seen; honest empty states).
- Geçmiş — browsing-only: count, source filters, record cards, per-record delete,
  confirmed Clear All, honest notices, empty state with a pointer to Ayarlar.
- Ayarlar — single source of truth for history preferences (recording consent OFF by
  default, separate Wi-Fi-names consent OFF by default, 7/14/30-day retention,
  Clear All entry with confirmation), truthful privacy section, "Uygulama hakkında"
  with version label.

Detail screens (live observation detail, saved-history detail) hide the bottom bar
and keep explicit Back buttons.

## Navigation behavior (pure, JVM-tested in AppNavigationTest)

- Tab selection is deterministic and idempotent; it NEVER reopens a stale detail and
  NEVER starts/stops a session, requests a permission or refreshes scanning state.
- Back: observation detail → Gözlemler tab; history detail → Geçmiş tab; a non-primary
  tab → Gözlem; Gözlem hands Back to the system (default exit behavior).
- The bottom bar shows ONLY on tab screens (details hide it).
- Tab switches in a still-RESUMED activity do not touch radio eligibility or sessions
  (projection of the unchanged foreground contract); stop-on-background is unchanged.

## Theme and layout decisions

- New `ui/theme/Theme.kt`: one deliberate Material 3 system — calm neutral surfaces,
  controlled indigo/violet accent, coherent light and dark schemes (no dynamic color
  so the brand stays stable), consistent 8/12/16dp rhythm, tonal badges instead of
  color-only meaning, no gradients/heavy shadows/flashy animation.
- Typography via Material 3 roles; badges and cards use `shapes.small`/default card
  shape; minimum 48dp touch targets preserved everywhere (chips, switches, buttons).
- Removed four now-unused string resources (home_intro, open_feed_button,
  open_history_button, a11y_open_feed) — lint returns to its exact baseline.

## Settings/history separation

Geçmiş is browse-only. ALL preferences (recording, Wi-Fi-names, retention, Clear All)
live in Ayarlar and drive the SAME existing HistoryController state: no duplicate
settings, no preference resets, no storage change. Default recording OFF, only
future observations saved after explicit opt-in, BSSID never stored, 7/14/30-day
retention (hard cap 30), 500-record deterministic capacity — all preserved.

## FieldDiag visibility policy (pure, JVM-tested in DeveloperDiagGateTest)

- The normally visible top banner/panel is REMOVED from the standard presentation.
- Cold start: diagnostics ALWAYS hidden; nothing persisted (process recreation
  re-locks and re-hides automatically).
- DEBUG-only access: Ayarlar → Uygulama hakkında → tap the version label SEVEN times
  → developer section appears with Tanılamayı göster / Tanılamayı gizle.
- Visibility rule (pure): debuggable build AND unlocked AND explicit user toggle.
- The panel stays the same bounded in-RAM chronology: no disk writes, no history
  writes, no upload, no analytics; release builds can never show it.

## Preserved contracts (unchanged)

Explicit manual Start; two-tap permission rule; foreground-only observation;
stop on foreground loss; BLE/Wi-Fi independence; bounded sessions (20s/30s); passive
Wi-Fi only (no startScan anywhere); sanitized session-local live observations;
optional-history consent/deletion/race guarantees; no raw MAC/BSSID or tracking
identifiers anywhere in UI or storage.

## Test evidence (device-free, this run)

- Core: 266/266 (unchanged suites).
- App: 23/23 (17 new: AppNavigationTest 9 — tab selection, no detail reopening, back
  policy, system-back handoff, bottom-bar visibility; DeveloperDiagGateTest 8 —
  cold-start locked, 6-vs-7 taps, reset semantics, recreation re-lock, visibility
  rule, release-build refusal).
- Commands (all exit 0): `:core:radio:test --rerun-tasks --no-daemon`,
  `:app:testDebugUnitTest --rerun-tasks --no-daemon`, `:app:compileDebugKotlin
  --no-daemon`, `:app:assembleDebug --no-daemon`, `:app:lintDebug --rerun-tasks
  --no-daemon` (0 errors, 16 warnings — exact baseline).
- Debug APK: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1,
  23,696,157 bytes, SHA-256
  44b43fcfc8bab8234fe2f01f1947b0aa0140c426b16a531f860ec720f27b209b.
- NO release/AAB/signing/version tasks; NO device/emulator/ADB use.

## Unverified on-device behavior (required Xiaomi phone checks)

1. Four tabs render; selection states clear; labels legible at 1.3–2.0 font scale.
2. Start a session on Gözlem, switch tabs — session must continue; no auto-start on
   tab navigation; backgrounding still stops sessions.
3. Feed rows/badges/times readable; no chip/row overlap while scrolling; details
   grouped correctly; Back returns to the owning tab.
4. Ayarlar toggles drive the same state Geçmiş shows; consent defaults OFF; retention
   chips respond; Clear All confirmation works from both surfaces.
5. FieldDiag hidden on cold start; seven version taps unlock; Tanılamayı göster shows
   the panel; gizle hides it; recreation re-hides.
6. Light and dark theme sanity; 48dp targets; TalkBack spot check.
