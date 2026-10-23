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

---

# Third Field Iteration — On-Screen FieldDiag-03 Diagnostics (2026-10-23, 15:12)

## Field evidence (OBSERVED; underlying mechanism still UNVERIFIED)

15:12 screenshot (Xiaomi): both cards STILL "Etkin değil" + the same red start-failed
copy; no observing state. New system log (radioobserver_logs(2).txt): 15:12:00 app
data cleared + force-stop; 15:12:03-05 launch displayed; 15:12:07 app-initiated
REQUEST_PERMISSIONS UI; 15:12:09 permission-group selections recorded, app
regained focus, system activityResumed; 15:12:10-27 touch events on the app window.
No package-replacement event before this test: WHICH APK build was actually
installed is NOT established by the log (filenames/versionCode prove nothing).
No RadioObserverDiag lines in this SYSTEM-filtered log (no conclusion from that).
No app-level start decision, scan callback or crash established.

## Deep source audit result (third pass, both radio paths end-to-end)

Full chain re-verified with exact source evidence: Compose onStartBle/onStartWifi
clicks -> MainActivity callbacks -> presenter.onUserStartTap -> permission gateway
(checkSelfPermission, FRESH at every tap) -> LocationAccess/recheck ->
foregroundEligible() (= supervisor.resumed, LIVE read) -> orchestrator gate
(authorization.eligible OR supervisor re-check, LIVE) -> supervisor ->
source activation (sink AtomicBoolean, updated in the SAME supervisor methods in
the same order: onHostResumed sets resumed THEN sink(true); loseForeground sets
resumed=false THEN sink(false) THEN stops) -> UI mapper. The two NOT_ELIGIBLE
producers and the clearing hook behave as reported. Test delta reconciled:
223 + 4 (Q1-Q4) + 4 (R1-R4) = 231; no invented count. NO contradictory cached
eligibility, no replay/memoization divergence, no stale-registration defect was
found in the source. CONCLUSION (explicit): the source audit does NOT establish a
reproducible defect; the decisive missing evidence is what happened INSIDE the app
at tap time on the phone, and which APK build was installed.

## Change: DEBUG-only on-screen diagnostics (no ADB needed)

Per the field-protocol decision rule, since host reasoning cannot settle it:
- FieldDiagnostics now ALSO records a bounded (last 24), in-RAM chronology of the
  SAME typed Logcat events (eligibility_changed, activity_resume, start_tap with
  eligibility/grant booleans/readiness class, start_attempt with typed outcome,
  start_tap_permission_path, runtime_perm_result, settings_returned, recheck_tap).
- New ui/FieldDiagnosticsPanel.kt: a collapsed "Tanılama" disclosure rendered ONLY
  in debug builds (gated by the app debuggable flag), labeled
  "FieldDiag-03 — DEBUG ONLY — gözlem sonucu değildir", showing the build marker,
  LIVE orchestrator eligibility + source (sink) eligibility, current session
  statuses with the VISIBLE issue-key origin, and the bounded event rows with
  elapsed timestamps. No MAC/BSSID/payload/identifiers; nothing persisted or
  transmitted; no control-flow effect (the panel has no callbacks into scanning,
  permissions, retry or recheck). The Row chronology shows whether the FIRST
  (permission-path) and SECOND (real gate) Start taps were received and what each
  gate decided.
- Panel placement: MainActivity Home screen, above the control screen inside a
  Column+Box; release layout and behavior unchanged (panel not rendered).

## Tests and gates (fresh, final tree)

:core:radio:test --rerun-tasks EXIT 0 — 231/231, 0 failed (unchanged);
:app:testDebugUnitTest --rerun-tasks EXIT 0 — 3/3 NEW (FieldDiagnosticsRingTest:
capacity bound + stable marker; typed fact formatting; no identifier-style fields).
The Android Activity/Compose boundary is NOT host-tested (openly stated; no
instrumentation run, no device). :app:compileDebugKotlin EXIT 0; :app:assembleDebug
EXIT 0; :app:lintDebug --rerun-tasks EXIT 0 (0 errors, 15 warnings, baseline).
No manifest/permission/dependency/SDK/version/signing changes.

## APK identity (new build, marker VERIFIED inside the artifact)

com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1 (unchanged BY
DESIGN; these strings alone prove nothing). Absolute path:
/app/radioobserver/app/build/outputs/apk/debug/app-debug.apk. Size 23526952 bytes.
SHA-256 31a2f4f9449cd4266429a88a0308c0cb6eff8d6f09c57344599f84589b73e8cf.
"FieldDiag-03" and "RadioObserverDiag" strings CONFIRMED present in the APK DEX
(classes16/classes17) — the installed build can be verified ON the phone by the
visible "Tanılama ▸ (FieldDiag-03)" disclosure even without a PC.

## Next manual phone test (decisive, NO ADB required)

1. Install the NEW APK (SHA-256 above). OLD APKs share versionCode 1 — before
   testing, uninstall or verify the marker: open the app; the collapsed
   "Tanılama ▸ (FieldDiag-03)" chip MUST be visible. No chip = wrong/old build.
2. Enable Bluetooth, Wi-Fi and Location services. 3. Open the app; FIRST Start
   (grant OS permissions if asked); return. 4. SECOND distinct Start while the app
   stays visible; repeat for both BLE and Wi-Fi. 5. Expand "Tanılama" and
   screenshot the panel (chronology + live booleans) — one screenshot is enough;
   no ADB/log sharing needed. 6. Check "Gözlemler" separately; zero passive Wi-Fi
   results does NOT prove its observer failed.
