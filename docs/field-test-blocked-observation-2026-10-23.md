# Post-Handover Field Finding — Blocked Observation Cards on Real Phone (2026-10-23)

Post-roadmap finding: a REAL-DEVICE field defect discovered after the device-free
Phase 3 completion (roadmap 10/10). It is not retrospective proof that prior host
tests were wrong; host gates never claimed real-device lifecycle delivery
(explicitly documented as UNVERIFIED in every phase).

## 1. Field evidence (as provided; not independently verifiable here)

Screenshot: both cards show "Kullanılamıyor" + red "Ekrandan ayrıldığınız için
durduruldu" while the control screen is visible. Filtered MIUI system log excerpts
(NOT full process logs): 14:14:36 displayed; 14:18:23 backgrounded; 14:18:25
OneKeyClean kills process; 14:21:47 relaunch; 14:21:56 backgrounded; 14:21:57
OneKeyClean kills again. No exception/permission/callback lines present; absence in
filtered logs proves nothing. OEM cleanup of a backgrounded task is LEGITIMATE.

## 2. Root cause (proven at source level)

BLOCKED ("Kullanılamıyor") + FOREGROUND_LOST ("Ekrandan ayrıldığınız için
durduruldu") had exactly two producers — both REJECTING A START, never stopping a
session: (1) orchestrator start-boundary foreground gate (old L121-123);
(2) mapRefusal(ForegroundIneligible) (old L342-343). Eligibility is FALSE by design
until the Activity's first RESUMED; on aggressive OEMs (OneKeyClean, overlay
pauses, split-screen) a Start tap can transiently arrive while not RESUMED. The UI
rendered that transient rejection as a permanent-looking ACTIONLESS dead end: it
claimed "unavailable" (false) and "stopped because you left the screen" (nothing was
started), and RecoveryStatusProjection offered NO action for it. The exact
real-device trigger remains UNVERIFIED (no device here); the fix does not depend
on it. OneKeyClean killing a backgrounded process is correct, harmless cleanup.

## 3. Narrow fix (contract preserved)

New typed SessionIssueKey.NOT_ELIGIBLE. Both rejection paths return
Blocked(NOT_ELIGIBLE) and: IDLE radio keeps truthful IDLE + retry hint
"Şu anda başlatılamadı. Uygulama ekranda etkinken Başlat düğmesine tekrar dokunun."
(Start stays enabled); a radio with REAL terminal history keeps it untouched. The
old message remains ONLY for REAL foreground-loss session stops (truthful).
RecoveryStatusProjection: NOT_ELIGIBLE -> null (explicitly total). No change to:
two-tap rule, no-auto-start, independence/idempotence, real foreground-loss
teardown, passive Wi-Fi, sanitization, 200/15-min bounds, permissions/manifest/
dependencies/SDK/applicationId/version.

## 4. Before/after matrix

| Situation | Before | After |
|---|---|---|
| Start tap transiently ineligible, radio IDLE | BLOCKED + "Ekrandan ayrıldığınız için durduruldu" (dead end) | IDLE + "Şu anda başlatılamadı… tekrar dokunun" |
| Same, radio has real STOPPED history | overwritten with dead end | truthful history preserved |
| Real foreground loss during active session | STOPPED + FOREGROUND_LOST (truthful) | unchanged |
| Eligible Start | session starts | unchanged |

## 5. Tests (fresh, this tree)

New: Q1 ineligible start on IDLE never fabricates BLOCKED/FOREGROUND_LOST (other
radio untouched); Q2 preserves real STOPPED+FOREGROUND_LOST history; Q3 source
ForegroundIneligible refusal same transient treatment; Q4 later eligible start
works. Gates (--rerun-tasks/--no-daemon): :core:radio:test EXIT 0, 227/227
(223+4), 0 fail; :app:testDebugUnitTest EXIT 0 (0 tests); :app:assembleDebug
EXIT 0; :app:lintDebug EXIT 0 (0 errors, 15 warnings, baseline). APK identity
unchanged (com.hjinlabs.radioobserver, 1 / 0.1.0-phase1).

## 6. Manual re-test checklist (real device; NOT performed here)

1. Fresh launch of the corrected debug APK. 2. BLE: FIRST Start -> grant
permissions -> SECOND Start -> "Gözlemliyor". 3. Wi-Fi: Start -> "Gözlemliyor";
empty feed is EXPECTED until passive results arrive. 4. Background during a
session -> return: truthful "Durdu + Ekrandan ayrıldığınız için durduruldu"; fresh
Start works, nothing auto-restarts. 5. Rapid taps / after MIUI clean: started
session or transient hint — never a dead end. 6. If a dead end still reproduces,
collect a bounded package-filtered logcat (launch -> tap -> state) and share it.
Device result after fix: UNVERIFIED.
