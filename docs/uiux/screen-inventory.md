# UX-00 — Complete Screen Inventory (2026-11-06)

Evidence labels: **[S]** CONFIRMED FROM SOURCE · **[V]** OBSERVED IN DEVELOPER SCREENSHOT ·
**[I]** INFERRED · **[U]** NOT YET VERIFIED. Paths are relative to the repo root; "tab" is the
bottom-navigation owner (`app/.../navigation/AppNavigation.kt`, `AppTab`).

Screenshots: the seven developer captures were supplied in chat, **not** added to this
repository, so they are not re-inspectable here; statements marked **[V]** come from the
developer's own written observations (UX-00 §5 concern list) plus the roadmap's §B record.

## A. Gözlem (tab OBSERVATION)

| Item | Source | Entry | Turkish title | States | Nav/back | Components | Actions | UX concerns | Change risk | Target |
|---|---|---|---|---|---|---|---|---|---|---|
| Operations dashboard | `ui/ControlScreen.kt:71` `RadioObserverControlScreen` | Tab tap | Gözlem | per session: IDLE/CHECKING/PERMISSION_REQUIRED/BLOCKED/OBSERVING/STOPPING/STOPPED/TIMED_OUT/ERROR (`RadioSessionUiStatus`) | root, back = system | `SummaryStrip`, `RadioControlCard`, `StatusPill`, `SectionHeader` | Başlat/Durdur (BLE, Wi-Fi), ayarlara/katalog | **[V]** oversized cards, excessive vertical spacing, weak primary-action hierarchy | MEDIUM: callbacks are the only action channel | UX-03 |
| Readiness/permission state | `ControlScreen.kt:370` `RecoverySection` | inline in card | — | typed `SessionIssueKey` → Turkish copy | inline | buttons (48dp) | İzin ver / Ayarlar / Yeniden dene / Konum | **[V]** many competing buttons | MEDIUM | UX-03 |
| Privacy disclosure | `ControlScreen.kt:328` `InfoDisclosure` | inline | Gizlilik ve sınırlar | collapsed/expanded | inline | `ExpandableText`-style | expand/collapse | **[V]** long in-place explanations | LOW | UX-03/UX-08 |
| Session alerts panel | `ControlScreen.kt:471` `SessionAlertsPanel` | inline | Oturum uyarıları | kapalı/etkin, tetikleyici çoklu seçim, boş/dolu kuyruk, oturum yok | inline | `Switch`, FilterChip, compact rows | etkinleştir, tetikleyici seç, kapat, tümünü kapat | **[V]** seven oversized trigger controls | MEDIUM: engine lifecycle rules | UX-03 |

## B. Gözlemler (tab FEED)

| Item | Source | States | Components | Concerns | Target |
|---|---|---|---|---|---|
| Feed root + source chips + view switch | `ui/FeedScreens.kt:104` | rows empty/populated; `FeedEmptyKind` taxonomy | FilterChip, Card | **[V]** inconsistent view/filter selection controls | UX-04 |
| Liste rows | `FeedScreens.kt:342` `ObservationRow` | favorite on/off, freshness pill | `SourceIcon`, `StatusPill`, star TextButton | **[V]** oversized record cards | UX-04 |
| Radar (schematic) | `FeedScreens.kt:664` `RadarView` + `:849` `RadarListRow` | cluster summary, illegible-label fallback | custom Canvas/Box layout | **[V]** misleading-realism risk if changed; labeled sınır note must stay | UX-04 |
| Zaman tüneli | `FeedScreens.kt:894` `TimelineView` | entries per bucket, empty | rows | **[V]** density | UX-04 |
| Filter panel + presets | `FeedScreens.kt:975` `FilterPanel`, `:1244` `PresetNameDialog` | advanced criteria, search, preset list, dialogs | FilterChip, dialogs | **[V]** dialect differences vs History filters | UX-04 |
| Live detail | `FeedScreens.kt:450` `ObservationDetailScreen` | row gone (honest empty), present | cards, `SignatureHintSection` | consistent | UX-04 |

## C. Geçmiş (tab HISTORY)

| Item | Source | States | Components | Concerns | Target |
|---|---|---|---|---|---|
| Saved list | `ui/HistoryScreens.kt:72` | consents, notices, count, filter ALL/BLE/WIFI + Favoriler, empty | `HistoryFilterRow`, `HistoryRecordRow` | **[V]** scattered actions, oversized cards | UX-05 |
| Record detail | `HistoryScreens.kt:338` | record missing, annotation editor dialog | `HistoryAnnotationCard`, AlertDialog | **not** in the seven screenshots → **[U]** visually | UX-05 |
| Annotation editor | `HistoryScreens.kt:521` | empty/filled, validation error, delete | AlertDialog, OutlinedTextField | **[U]** | UX-05 |
| Group list | `ui/SessionGroupScreens.kt:66` | empty/list, create/rename/delete dialogs | `GroupNameDialog` | **[U]** | UX-06 |
| Group detail | `SessionGroupScreens.kt:240` | member list, add section, stats, charts | `GroupStatsCard`, `DistributionRows`, `TimeBucketBars` | **[U]** | UX-06 |
| Group compare | `SessionGroupScreens.kt:377` | A/B selection, stats pairs | `GroupPickerRow` | **[U]** | UX-06 |
| Report export | `ui/ReportExportScreen.kt:68` | scope, format, inclusion toggles, busy, saved, cancelled, error, confirm-all | dialog + SAF launchers | **[U]** two launchers intentionally | UX-07 |

## D. Ayarlar (tab SETTINGS)

| Item | Source | States | Components | Concerns | Target |
|---|---|---|---|---|---|
| Settings root | `ui/SettingsScreen.kt:55` | recording on/off, SSID consent on/off, retention 7/14/30, privacy expanders | `ToggleRow`, `RetentionChips`, `ExpandableText` | **[V]** verbose layout ("upper/lower settings") | UX-08 |
| Clear-all confirm | `HistoryScreens.kt` dialog | shown/hidden | AlertDialog | **[V]** destructive actions insufficiently separated | UX-08 |
| Catalog management | `ui/SignatureCatalogScreen.kt` | built-in read-only, user rules CRUD, error text | cards, chips, dialogs | **[U]** | UX-08 |
| About/privacy body | strings `privacy_about_body` etc. | expanded | text | **[V]** long in-place copy | UX-08 |

## E. Hidden developer destination

| Item | Source | Rules | Target |
|---|---|---|---|
| FieldDiag-04 | `ui/FieldDiagnosticsPanel.kt:38` + `DeveloperDiagGate` | debug build AND seven version taps AND explicit toggle; never in release | UX-08 (visual only; gate logic is untouchable) |

## F. Navigation model

`AppDestination` (S*): TabScreen, ObservationDetail, HistoryDetail, SignatureCatalog,
SessionGroups, SessionGroupDetail, SessionGroupCompare, ReportExport(groupId?). Back policy is
JVM-tested (`app/src/test/.../navigation/`); details hide the bottom bar. Four tabs only; UX-02
must keep exactly these destinations and add no fifth tab.
