# UX-00 — Acceptance, Journeys and Known Unknowns

## 1. Definition of Done (criterion → evidence)

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | Complete screen inventory | `docs/uiux/screen-inventory.md` (A–F incl. hidden FieldDiag) | DONE |
| 2 | Complete user-journey inventory | §2 below (21 journeys, source-grounded) | DONE |
| 3 | Component catalog | `docs/uiux/component-catalog.md` (shared + private→target + new) | DONE |
| 4 | Design contract | `docs/uiux/design-contract.md` (tokens, density, states, a11y) | DONE |
| 5 | Screen state matrix | `docs/uiux/screen-state-matrix.md` | DONE |
| 6 | Screen-to-workstream mapping | inventory "Target" column + §3 below | DONE |
| 7 | Known unknowns + visual gaps | §4 below | DONE |
| 8 | No source/runtime change | git diff shows only docs; no Gradle/APK | DONE |

## 2. User journeys (entry → action → state change → feedback → destination)

1. Cold open, no observation: launcher → Gözlem IDLE → no scan → dashboard shows two idle radios.
2. Start BLE: Gözlem → Başlat (BLE) → CHECKING → OBSERVING + pill, Durdur shown; rows may appear in Gözlemler.
3. Start Wi-Fi: Gözlem → Start (Wi-Fi) → passive listener starts; no scan initiated; cached results marked Eski.
4. Permission grant then second Start: PERMISSION_REQUIRED → system dialog → grant → still not running → user taps Start again (two-tap rule).
5. End observation: Durdur → STOPPING → STOPPED; rows cleared from live state; favorites cleared.
6. Browse views: Gözlemler → Liste/Radar/Zaman tüneli switch → same membership, different presentation.
7. Filters: Gözlemler → filter panel → criteria → apply → rows filtered; presets save/apply/rename/delete.
8. Live favorites: row star → session-local set changes → "Yalnız favoriler" filters list only.
9. Alerts: Gözlem → Oturum uyarıları → enable → choose triggers → qualifying new observations alert once → dismiss/all → session end clears.
10. Review History: Geçmiş → list (consent on) → record rows with source/time.
11. Record annotations: detail → star/label/note → save → persisted; delete annotation.
12. Groups: Geçmiş → Analiz grupları → oluştur → kayıt ekle/çıkar → stats/charts; compare two groups.
13. Export CSV: Geçmiş/group → Rapor → scope + CSV + inclusion → Kaydet → SAF → file written → success message with count.
14. Export cancel: SAF back → "veriler değişmedi" message; no partial file claim.
15. Recording consent: Ayarlar → Kayıt switch → persisted; History list respects it.
16. Retention: Ayarlar → 7/14/30 → prune on next load/save.
17. Delete one record: Geçmiş → Sil → confirm dialog → record removed; annotations/group refs reconciled.
18. Destroy-all: Ayarlar → Tümünü sil → separated confirm → cleared; no resurrection this session.
19. Catalog: Ayarlar → Sınıflandırma kataloğu → built-ins read-only; create/edit/toggle/delete user rule.
20. Privacy reading: expanded disclosures on Gözlem/Ayarlar → collapsed by default (target).
21. FieldDiag: debug build → seven taps on version → toggle → panel; release never.

## 3. Screen → workstream map

UX-01 theme/shared components (all screens) · UX-02 shell/insets/tabs · UX-03 Gözlem + alerts ·
UX-04 Gözlemler + filters · UX-05 Geçmiş + detail + annotations · UX-06 groups/charts/compare ·
UX-07 export · UX-08 Ayarlar/catalog/privacy/diagnostics visual · UX-09 integrated QA.

## 4. Known unknowns and evidence gaps

- The seven Xiaomi screenshots are **not** in the repository and were not re-inspectable in this
  run; all visual claims rely on the developer's written concern list (roadmap §B) → treat
  pixel-level questions as OPEN until UX-09 does a device pass.
- History detail, annotation editor, groups, compare, export and catalog screens have **no**
  screenshot baseline **[U]**; their visual assessment is source-only.
- Exact current colour values: `Theme.kt` defines a custom dark scheme but candidate tokens in
  `design-contract.md` are proposals, not measured from the build.
- Motion/animation inventory is trivially "none by default"; no state uses animation besides
  conditional visibility.
- Font metrics under 2.0x scale and TalkBack are developer-declined: not measured, not claimed.

## 5. Roadmap clarification recorded

Developer reported that the functional app passed Xiaomi testing including WS06/WS08 flows. That
is recorded as DEVELOPER-REPORTED REAL-DEVICE ACCEPTANCE (not an OpenCode device run) via a dated
clarification note in `roadmap.md`; historical entries are unchanged.

## 6. UX-01 readiness

Ready: token names, density scale, component targets and screen map exist. Recommended UX-01
sequence: (1) token file + Theme migration, (2) `ScreenScaffold` + app bar + insets, (3)
`ObservationRow`/`SavedRecordRow`/`StatusPill` sizing, (4) `DisclosureCard` + `EmptyState` v2,
(5) host gates after each step. UX-01 must keep all 419 existing host tests green (370 core +
49 app) and add none that require a device.
