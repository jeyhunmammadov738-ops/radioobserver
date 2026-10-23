# Post-Handover Field Finding 2 — Start/Permission/Session Transition (2026-10-23)

Second real-phone field iteration after the NOT_ELIGIBLE fix. Post-roadmap defect
diagnosis; no phase/roadmap changes. Device results here remain UNVERIFIED
(no device/emulator in this environment).

## 1. Field evidence

14:52 screenshot: BOTH cards "Etkin değil" (IDLE) + red
"Şu anda başlatılamadı. Uygulama ekranda etkinken Başlat düğmesine tekrar
dokunun." (NOT_ELIGIBLE). Both Start buttons visible; no active session.
System log (filtered, undecodable result codes): 14:49:45-47 APK replaced;
14:50:21 launch; 14:51:33 OS app-permissions Settings (NEARBY_DEVICES, LOCATION
groups); 14:51:53 app data cleared from Settings, process stopped;
14:51:54 new process; 14:51:57 app-initiated REQUEST_PERMISSIONS dialog;
14:51:58-59 selections returned to app; 14:52:01-06 touch input; 14:52 screenshot.
No app-level start decision, scan callback, SecurityException or crash in the log.

## 2. Which conditional produces the red text (exact)

SessionIssueKey.NOT_ELIGIBLE has exactly two producers (proven by grep):
ObservationSessionOrchestrator.start's foreground gate (authorization.eligible OR
supervisor re-check false) and the source-side ForegroundIneligible refusal;
both call publishNotEligibleIfIdle, which publishes IDLE + NOT_ELIGIBLE ONLY for
a radio that was IDLE (never observing). Classification: an ACTUAL REJECTED START
(transient tap-time condition) — not a default error, not a stop, and NOT an
intended post-permission prompt. What the screenshot ADDITIONALLY proves: the
hint PERSISTS as current state while the app is fully foreground.

## 3. Root cause status — honest split

UNPROVEN: WHY eligibility was false at the reported tap time. The wiring is
internally consistent (bridge registered in onCreate on the Activity lifecycle;
single supervisor instance; sink and presenter read the same flag; the gate
re-checks LIVE state at every tap). The filtered system log contains no
app-level lifecycle/eligibility evidence, and no host test can reproduce OEM
lifecycle delivery. Candidate real-device triggers (hypotheses only): tap
delivered while the OS permission dialog/its pause was still active, OEM
overlay/animation windows, split-screen visibility.

PROVEN (the demonstrable defect): the transient rejection LATCHES. After a
genuine foreground return nothing cleared a stale NOT_ELIGIBLE hint — violating
the contract "do NOT leave a stale failed-start latch after a genuine foreground
return; recheck current foreground state". The user-visible outcome on the
phone: a red, error-looking, actionless state that claims a current problem that
(by then) no longer existed.

## 4. Narrow fix (this iteration)

1. Orchestrator.onForegroundEligibilityChanged(eligible) (pure JVM): on a
   GENUINE eligibility (re)establishment, a latched IDLE + NOT_ELIGIBLE state
   clears to plain truthful IDLE. Only that exact state is touched: real terminal
   history (STOPPED/TIMED_OUT/ERROR) and active sessions stay untouched; the
   false transition publishes nothing; nothing ever starts automatically.
2. MainActivity wires the supervisor's existing eligibility sink to this hook
   (single line addition; supervisor semantics unchanged).
3. DEBUG-only, privacy-safe diagnostics (new app/diag/FieldDiagnostics.kt,
   gated by the app debuggable flag — release builds emit NOTHING): typed
   breadcrumbs eligibility_changed, activity_resume, start_tap (radio, eligibility,
   grant booleans, readiness class), start_attempt (typed outcome),
   start_tap_permission_path, recheck_tap, runtime_perm_result, settings_returned.
   No MAC/BSSID/payload/identifiers/location/observation data; no control-flow
   change. Capture filter: `adb logcat -s RadioObserverDiag` (or package filter
   com.hjinlabs.radioobserver). These will settle the unproven trigger on the
   next field run.
4. No change to: two-tap rule, no-auto-start, single launcher, RESUMED-only
   manual start, foreground teardown of real sessions, independence,
   passive Wi-Fi, sanitization/privacy caps, manifest/permissions/dependencies/
   SDK/version/signing.

## 5. Tests and gates (fresh, final tree)

New orchestrator tests R1-R4: genuine foreground return clears latched hints;
real STOPPED+FOREGROUND_LOST history never overwritten; eligibility-lost
transitions publish nothing; clearing touches only IDLE+NOT_ELIGIBLE (active
sessions untouched). Gates (15-min bound, --no-daemon): :core:radio:test
--rerun-tasks EXIT 0 — 231/231 (227 + 4), 0 failed; :app:testDebugUnitTest
--rerun-tasks EXIT 0 (0 tests; no Compose coverage claimed);
:app:compileDebugKotlin EXIT 0; :app:assembleDebug EXIT 0; :app:lintDebug
--rerun-tasks EXIT 0 (0 errors, 15 warnings, baseline unchanged). APK identity
unchanged: com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1.
Debug APK SHA-256 5ae1a1435c3ff552447488a2f4cf935b77c56deedb556e26e9525b05217d78de.

## 6. Manual phone acceptance script (NOT performed here)

1. Install the new debug APK (verify SHA-256 above); enable Bluetooth + Location
   services; enable radios. 2. Open the app; FIRST Start on BLE -> grant the OS
   permissions when asked; the app returns WITHOUT scanning. 3. SECOND explicit
   Start on the visible control screen -> card must show "Gözlemliyor"; Wi-Fi
   Start independently -> "Gözlemliyor" (empty feed is expected until passive
   results arrive — honest note, not an error). 4. If a red "Şu anda başlatılamadı"
   hint ever appears, it must CLEAR on the next genuine foreground return or on a
   successful retry; it must never persist as a permanent dead end. 5. If the
   hint still reproduces, capture `adb logcat -s RadioObserverDiag` around the
   tap and share it — the breadcrumbs will show the exact eligibility and start
   decision at tap time. 6. Background during an active session -> truthful
   "Durdu + Ekrandan ayrıldığınız için durduruldu"; fresh Start works; nothing
   auto-restarts.
