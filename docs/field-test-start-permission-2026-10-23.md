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

---

# Fourth Field Iteration — FieldDiag-04 Guard-Provenance Instrumentation (2026-10-23)

## Authoritative FieldDiag-03 trace (first DECISIVE in-app evidence)

Xiaomi, FieldDiag-03 APK: panel shows both taps REACHED the app and BOTH attempts
returned Blocked while the tap-time facts were eligible=true, fineLocation=true,
coarseLocation=true, btScan=true, readiness=Ready, and sessions stayed IDLE.
Trace: +76ms settings_returned (all grants true) -> +77ms activity_resume ->
+78ms eligibility_changed=true -> +8.893s start_tap BLE (all true, Ready) ->
+8.893s start_attempt BLE outcome=Blocked -> +9.215s start_tap WIFI (all true,
Ready) -> +9.215s start_attempt WIFI outcome=Blocked -> +11.08s/+12.204s
eligibility_changed=false (user left to screenshot: pause+stop) -> ~+72.3s
settings_returned -> activity_resume -> eligibility_changed=true (return; the
IDLE+NOT_ELIGIBLE hint, if any, clears via the R1 hook).

Audit of the anomaly: the "+7232ms" pair after "+12204ms" is a transcription
digit-loss of ~+72.3s values; the code uses ONE process-wide monotonic
System.nanoTime origin and append-order display. The third iteration's
milliseconds-only format was still ambiguity-prone; FieldDiag-04 fixes the format.

## Full source audit result (fourth pass — every Blocked producer enumerated)

start() decision table (path: ObservationSessionOrchestrator.start): duplicate
OBSERVING/STOPPING -> Started (idempotent); !preciseLocation -> NeedsRuntimePermission;
!capabilityReady -> Blocked(capabilityIssue); !foregroundEligible ||
!supervisor.isForegroundEligible -> Blocked(NOT_ELIGIBLE, gate); source
ForegroundIneligible -> Blocked(NOT_ELIGIBLE, source re-gate); other source
refusals -> mapRefusal -> NeedsRuntimePermission or Blocked(typed issue).
Presenter tap-time facts are read synchronously in the SAME block the gate reads
(same supervisor instance, same main thread, same instant) — the logged facts
CANNOT diverge from the gate's first check. CONCLUSION (explicit): with all logged
facts true, the trace CANNOT identify which guard returned Blocked — the FieldDiag-03
start_attempt row omitted the typed issue and stage. Root cause NOT proven; the
decisive missing fact is exactly the typed issue + stage, which FieldDiag-04 now
records. No behavioral fix was applied (no proven defect; the previous stale-hint
workaround is NOT repeated).

## FieldDiag-04 changes (DEBUG-only, no policy change)

- Orchestrator StartAttempt.Blocked gains a typed decision STAGE:
  ORCHESTRATOR_GATE (eligibility/capability gates) vs SOURCE_REFUSAL (the source's
  own re-gate/readiness refusal). Pure JVM; UI semantics unchanged.
- start_attempt now logs radio, attemptNo, outcome, the EXACT typed issue, the
  stage, sourceCalled=YES/NO and sourceAccepted. If the reason is unavailable the
  stage is still shown — never an invented reason.
- start_tap logs an anonymous per-radio attempt number; permission-path taps log
  theirs too. MainActivity logs activity_recreated so a recreation boundary is
  visible in the timeline.
- Bounded ring enlarged to the last 40 rows; each row carries a strictly increasing
  process-local sequence "#N" and a seconds-based monotonic elapsed "+8.912s",
  ending the transcription ambiguity.
- Privacy unchanged: typed booleans/enum names only; no MAC/BSSID/payload/SSID/
  location/identifiers; no disk/network; release builds emit and render nothing.

## Tests and gates (fresh, final tree)

New JVM behavior tests: S1 (gate rejection carries ORCHESTRATOR_GATE stage),
S2 (source readiness refusal carries SOURCE_REFUSAL with its OWN distinct typed
issue, not a generic eligibility reason), S3 (successful eligible start reports
Started, never Blocked); Q3 upgraded to assert the SOURCE_REFUSAL stage. Ring
tests updated (capacity 40, marker FieldDiag-04, sequence + seconds format).
Gates: :core:radio:test --rerun-tasks EXIT 0 — 234/234 (231+3), 0 failed;
:app:testDebugUnitTest --rerun-tasks EXIT 0 — 4/4; :app:compileDebugKotlin EXIT 0;
:app:assembleDebug EXIT 0; :app:lintDebug --rerun-tasks EXIT 0 (0 errors, 15
warnings, baseline). Activity/Compose boundary not host-testable (stated openly).

## APK identity (FieldDiag-04 build)

com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1 (unchanged).
/app/radioobserver/app/build/outputs/apk/debug/app-debug.apk; 23526952 bytes;
SHA-256 4ad30042558de733bc0111d93b2c1f4e1372f762dbdad9e5a0a4d5be383ac980.
"FieldDiag-04" CONFIRMED inside the DEX; "FieldDiag-03" ABSENT (old build
distinguishable on the phone via the panel marker).

## Next phone retest (decisive)

1. Install the FieldDiag-04 APK; the collapsed chip MUST read "Tanılama ▸
   (FieldDiag-04)". 2. Enable Bluetooth, Wi-Fi, Location services. 3. Open the app;
   explicit Start per radio (grant OS prompts if asked; return; second Start if
   needed). 4. Expand "Tanılama" and screenshot: the start_attempt rows now show
   the EXACT issue + stage (gate vs source) + sourceCalled — that single typed
   fact will identify the failing guard. 5. Check "Gözlemler" separately; zero
   passive Wi-Fi results does not prove registration failure.

---

# Fifth Field Iteration — PROVEN Source-Level Root-Cause Fix (2026-10-23)

## Authoritative FieldDiag-04 evidence

Xiaomi, FieldDiag-04 APK: header orchestrator=true, source=true; both sessions
IDLE(NOT_ELIGIBLE); tap facts fineLocation=true coarseLocation=true btScan=true
readiness=Ready; second Start rows: start_tap radio=BLE attemptNo=2 eligible=true
-> start_attempt radio=BLE outcome=Blocked issue=NOT_ELIGIBLE stage=SOURCE_REFUSAL
sourceCalled=true sourceAccepted=false — same for Wi-Fi. The typed stage named the
decision point: the SOURCE's own foreground re-gate refused while every caller-side
fact was true.

## Root cause (PROVEN by source inspection)

Kotlin trailing-lambda binding: in MainActivity both sources were constructed as
`AndroidBleObservationSource(applicationContext) { foregroundEligibility.get() }`
(and the Wi-Fi equivalent). A trailing lambda binds to the LAST constructor
parameter — `permanentDenialProvider: (PermissionId) -> Boolean` — NOT to
`foregroundEligibility: () -> Boolean`. A zero-declared-param lambda satisfies
`(PermissionId) -> Boolean` via the unused implicit `it`, so it COMPILED SILENTLY.
Both sources therefore kept the DEFAULT `foregroundEligibility = { false }`:
BleSessionCoordinator.startSession/WifiSessionCoordinator.startObservation re-gate
`foregroundEligible()` FIRST (before readiness), always read false, and refused
`ObservationError.ForegroundIneligible` — mapped by the orchestrator to
`Blocked(NOT_ELIGIBLE, stage=SOURCE_REFUSAL)`, sessions left IDLE+NOT_ELIGIBLE.

Why every previous audit missed it: the debug header's "source=true" comes from the
SUPERVISOR SINK (RadioObservationSupervisor's correctly-bound trailing lambda — the
sink IS its last parameter), which is a DIFFERENT state holder from the sources'
misbound gate provider. Host/JVM tests construct the core coordinators with explicit
arguments, so the defect existed only in the Android-layer wiring, which the host
framework cannot instantiate (stated openly).

Evidence-fit check: matches ALL field iterations — first tap took the permission
path (presenter uses the real gateway), second tap passed the orchestrator gate
(supervisor resumed=true) and was refused by the source re-gate (default false),
identically for both radios, with no session start attempt (sourceCalled=true
means the source METHOD was entered; its foreground guard fired before any scan).

## Fix (smallest safe change, no guard weakened)

1) MainActivity: explicit NAMED argument `foregroundEligibility = { ... }` for both
sources. 2) Structural prevention: `foregroundEligibility` is now a REQUIRED
constructor parameter (default `{ false }` removed) in BOTH sources — any future
positional trailing-lambda call FAILS TO COMPILE; the fail-safe "never
background-safe by default" property is STRENGTHENED (the live gate must now be
supplied). No manifest/permission/version/signing change; legitimate denial is
preserved: when the app truly leaves the foreground the supervisor sets the SAME
live holder false and both sources still refuse with ForegroundIneligible.

## Regression tests (core JVM, fakes only)

W1 (BLE): shared live holder — not resumed -> ForegroundIneligible refusal with
zero engine starts; after resume (sink sets the SAME holder) -> accepted start.
W3 (Wi-Fi): identical independence proof for the Wi-Fi source. W2: documentation of
the phone signature — a DETACHED gate holder (the misbinding's effect) reproduces
the exact FieldDiag-04 refusal while a separate "sink" holder reads true. The
Android-layer misbinding itself is not host-testable (open limitation); the
structural compile-time guard prevents its recurrence. Previous stage tests
(Q1-Q4, R1-R4, S1-S3) unchanged.

## Gates (fresh, final tree, --no-daemon, 15-min timeouts)

:core:radio:test --rerun-tasks EXIT 0 — 237/237 (234+3), 0 failed;
:app:testDebugUnitTest --rerun-tasks EXIT 0 — 4/4; :app:compileDebugKotlin EXIT 0
(also proves the required-parameter change compiles); :app:assembleDebug EXIT 0;
:app:lintDebug --rerun-tasks EXIT 0 — 0 errors, 15 warnings (baseline unchanged).

## APK identity (root-cause-fix build)

com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1 (unchanged).
/app/radioobserver/app/build/outputs/apk/debug/app-debug.apk — size, SHA-256 and
packaging verification in the release notes of this iteration. Real-device repair
remains UNVERIFIED until the phone retest below succeeds.

## Phone retest (safe, decisive)

1. Install this APK (uninstall the old one first; the panel chip still reads
"Tanılama ▸ (FieldDiag-04)" — the fixed build is distinguishable by BEHAVIOR: Start
must now show a running/observing state when eligible). 2. Bluetooth, Wi-Fi and
Location services ON. 3. First Start per radio -> grant the OS prompts if asked;
return to the app; SECOND Start per radio. 4. Expected: the radio cards enter
OBSERVING (not IDLE(NOT_ELIGIBLE)); the panel's start_attempt rows show
outcome=started sourceAccepted=true. 5. If any radio still blocks, screenshot the
panel — the typed issue+stage will name the guard (now it can only be a genuine
foreground/permission/radio state, not this wiring defect).

---

# Sixth Field Iteration — Passive Wi-Fi Zero-Result Diagnosis + Cached-Read Fix (2026-10-23)

## Field evidence and verified execution map

Xiaomi, root-cause-fix build: BLE session Observes (20s bounded), two sanitized
rows, detail works. Wi-Fi session Observes for the 30s window with ZERO entries.
Code map (verified): WifiSessionCoordinator.startObservation gates
(already-active -> foreground -> readiness) then registers the session-scoped
receiver (AndroidWifiResultEngine, RECEIVER_NOT_EXPORTED, system
SCAN_RESULTS_AVAILABLE_ACTION only) and schedules the 30s timeout. BEFORE this
iteration the fetcher (WifiManager.getScanResults, never startScan) was read
ONLY inside onScanResultsBroadcast. Zero results were therefore EXPECTED
whenever no system/other-app scan broadcast arrives within 30s — which is the
common case when the user has just opened the app (throttled system scans may
simply not occur). Observing means "receiver registered", truthfully; it never
guaranteed a scan or results. NOT proven by the phone evidence: cache contents,
permission state at the exact call, or OEM broadcast behavior.

## Fix (proven gap, API-29+ documented behavior)

On an explicit, fully gated Start the coordinator now performs ONE guarded
cached-result read (getScanResults via the existing fetcher; the class still has
NO scan API and never calls startScan/WifiScanner). Non-empty cached rows are
delivered with updatedSinceLastDelivery=false and honest per-record age —
cached data is never presented as newly detected. An empty cache or a failed
cached read NEVER stops the session: the receiver stays armed for later
broadcasts. All gates are unchanged; the read happens only inside an explicit
user-initiated start (no resume/recreation/composition path).

## DEBUG-only Wi-Fi evidence (no adb needed, no identifiers)

Existing FieldDiagnostics rows now include wifi_evidence: fetch (raw cached
count, allowlisted exception class only), broadcast (platform results-updated
flag), receiver registration. These make the next phone check decisive: cache
empty vs rows dropped vs exception. Bounded, in-RAM, DEBUG builds only.

## Regression tests (core JVM)

New: I0 (initial cached read delivers once, NOT-new, honest age + provenance
fields), I1 (empty cache: no delivery, session keeps listening; later broadcast
delivers), I2 (failed cached read swallowed; receiver stays; broadcast path
intact), I3 (refused starts never read the cache). Updated for the intentional
behavior change: broadcast-fetch counts (1 initial read) and delivery counts in
the existing freshness/dedup/late-broadcast tests. Gates: core 241/241 (237+4),
app 4/4, compile OK, assembleDebug OK, lintDebug 0 errors/15 warnings.

## APK identity

com.hjinlabs.radioobserver, versionCode 1, versionName 0.1.0-phase1 (unchanged).
Size 23526952 bytes; SHA-256 a8902566fdf147459a989ef0901203a5dadeb8d82c229113cf829d22ba889436.
"wifi_evidence" and "FieldDiag-04" confirmed in the DEX.

## Phone retest

Install this APK; Wi-Fi + Location ON; explicit Wi-Fi Start. Expected: cached
results (if any exist on the device) appear immediately, labeled not-new with
their age; if none appear, open Tanılama: wifi_evidence rows show the RAW cached
count (0 = device cache genuinely empty at that moment, expected when nobody
scanned; exception = a different, now-visible cause). A successful listening
session with zero results remains possible and is NOT a failed start.
