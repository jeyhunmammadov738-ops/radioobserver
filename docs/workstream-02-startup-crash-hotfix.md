# Workstream 02 — Startup Crash Hotfix (2026-10-24, QA)

## Confirmed device crash (user-reported, Xiaomi)
RuntimeException: Unable to instantiate activity
ComponentInfo{com.hjinlabs.radioobserver/com.example.radioobserver.MainActivity}
Caused by: NullPointerException: Attempt to invoke Context.getApplicationContext()
on a null object reference. Crash during Activity instantiation, before any UI.

## Source-level root cause (PROVEN, not hypothesis)
MainActivity had TWO instance property initializers (Workstream 02):

    private val filterPresetRepository =
        FilterPresets.Repository(FilterPresetFileStorage(applicationContext))
    private val filterPresets = mutableStateOf(filterPresetRepository.list())

Kotlin instance property initializers execute during MainActivity construction —
BEFORE Android attaches the base context (attach/onCreate). At that instant
ContextWrapper.mBase == null, so the applicationContext getter dereferences a null
reference: exactly the reported NPE, and Activity instantiation aborts. The second
initializer additionally forces the repository evaluation (list()).

Proof by contrast: HistoryController(applicationContext) and
FieldDiagnostics.initialize(applicationContext) run INSIDE onCreate AFTER
super.onCreate — they never crashed the phone.

## Fix (minimal, lifecycle-correct)
- `filterPresetRepository` → `lateinit var`, constructed EXACTLY ONCE in onCreate
  AFTER super.onCreate (same established pattern as HistoryController), before
  setContent and before any consumer reads presets. Main-thread, synchronous —
  no async races, no static Activity refs, no !!, no swallowed exceptions.
- `filterPresets` starts as an empty-list state and is filled from the repository
  in onCreate. Non-persisted UI-state contract unchanged; filters/presets still
  dispatch nothing (no scan/permission/session action).
- Nothing else changed: no codec rewrite, no History touch, no Manifest/permission/
  version/signing change, no new dependency.

## Regression evidence (host; no activity-construction harness exists and none added)
New app unit suite `FilterPresetStartupBoundaryTest` (4 tests) exercises the exact
statements onCreate now runs (repository construction + list()) for every store
condition: missing file → empty; unreadable/foreign content → fail closed to empty
WITHOUT rewriting the stored bytes; mixed valid+corrupt lines → valid subset loads,
store untouched; startup filter default stays unrestricted. Combined with the core
FilterPresetRepositoryTest codec/corruption coverage, no stored preset content can
crash startup at the repository layer. The property-initializer→onCreate move itself
is host-UNVERIFIED: real Activity launch is only provable on the Xiaomi retest.

## Fresh host gates (all --no-daemon, tests/lint --rerun-tasks, exit 0)
:core:radio:test 299/299 · :app:testDebugUnitTest 49/49 (45+4 new) ·
:app:compileDebugKotlin · :app:assembleDebug · :app:lintDebug 0 errors/15 warnings
(baseline kept). DEBUG APK only: com.hjinlabs.radioobserver 0.1.0-phase1 (code 1),
24,578,558 bytes, SHA-256 86f281d158cf1b3fbc350455d55c8460e86b2c0b5fb036cf85f6179de4210fa1.
Install-over safe (identity/version/signing unchanged); NEVER uninstall or wipe data
— History and presets are preserved by the update.

## Workstream 02 status
NOT phone-accepted: device acceptance remains OPEN until the Xiaomi launch retest
succeeds (install-over, cold open, History/preset verification — see checklist in
the final report). No Phase 4 invented; no Workstream 03 started.
