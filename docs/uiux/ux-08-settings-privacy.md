# UX-08 — Settings, Alerts, Catalog and Privacy (2026-11-23)

Status: CODE/HOST COMPLETE (debug-only). Xiaomi visual acceptance OPEN.

Baseline HEAD `5fd1d47`. Presentation only: preferences, persistence, privacy defaults,
radio lifecycle, alert engine and classification semantics are unchanged.

## Requirement matrix (A-G)

| Req | Source | Behavior | Evidence | Status |
|---|---|---|---|---|
| A four sections | `SettingsScreen.kt` | Veri ve Geçmiş / Gizlilik / Sınıflandırma / Hakkında | strings + section order | MET |
| B consents + retention | `SettingsScreen.kt`, `RetentionChips` | two independent toggles; single-choice 7/14/30 | existing consent tests | MET |
| C clear-all separation | `SettingsScreen.kt` | own "Yıkıcı eylem" label, error-coloured action, unchanged confirm dialog | core history tests | MET |
| D seven alert triggers | `ControlScreen.kt`, `AlertTriggerGroups` | 3 readable groups, per-group summary, defaults OFF | 7 new JVM tests | MET |
| E privacy info | `SettingsScreen.kt` | three concise disclosures: saved / not saved / background limits | strings preserved verbatim | MET |
| F catalog flows | `SignatureCatalogScreen.kt` | create/edit/delete with 30-item limit, entry now under Sınıflandırma | existing catalog tests | MET |
| G FieldDiag-04 | `SettingsScreen.kt` | seven-tap unlock, DEBUG only, no navigation surface | unchanged code | MET |

## What changed
- Section titles: "Geçmiş kaydı" -> "Veri ve Geçmiş", "Gizlilik ve çalışma şekli" -> "Gizlilik",
  "Uygulama hakkında" -> "Hakkında"; new "Sınıflandırma" section with a truthful one-line
  description plus the existing catalog entry (moved out of Hakkında).
- Retention keeps the existing single-choice chips and now explains that the period applies
  only to saved history, not to in-memory session state.
- Clear-all is preceded by a "Yıkıcı eylem" label and rendered in the error colour; the
  confirmation dialog and controller path are untouched.
- Privacy copy is split into three progressive disclosures (what is saved, what is never
  saved, background limits) using the exact existing strings.
- The seven alert triggers keep their engine semantics but are grouped as Kaynak (2),
  Sinyal kategorisi (3), Üretici bilgisi (2) with per-group selection counts; all remain
  OFF by default and session-scoped.

## Not changed
Consent flags and defaults, retention contract (7/14/30, max 30), 500-record capacity,
alert engine and lifetime, catalog 30-item limit and validation, FieldDiag access, version
and versionCode, navigation.

## Gates (fresh, final tree)
Core JVM 370/370, App JVM 109/109 (7 new), `compileDebugKotlin`, `assembleDebug`,
lint 0 errors / 15 warnings (baseline). Debug APK only.

## Device-pending
2.0x text reflow of the four sections, disclosure expand/collapse, grouped chip wrapping,
clear-all dialog readability. Not verifiable on the host.
