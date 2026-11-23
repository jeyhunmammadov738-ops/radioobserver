# UX-09 — Final Integrated UI/UX QA (2026-11-23)

Status: CODE/HOST QA COMPLETE. Overall roadmap NOT accepted: developer Xiaomi sign-off is
still required. Baseline HEAD `36f94d8`.

## 1. Defects found and closed in this stage

| # | Severity | Defect | Source | Fix |
|---|---|---|---|---|
| 1 | P1 | Radar marker touch target was 32dp, below the 48dp accessibility minimum | `FeedScreens.kt` schematic radar dot | 48dp target with the 16dp symbol still centred (offset halved) |
| 2 | P1 | Catalog delete confirmation was visually identical to a normal action | `SignatureCatalogScreen.kt` | confirm text now uses the error colour; dialog and callbacks unchanged |
| 3 | P2 | Catalog capacity (30) was only visible after a failed add | `SignatureCatalogScreen.kt` | persistent "Kendi girişleriniz: N / 30" counter under the subtitle |

No other P0/P1 code-level UI defect was found in the audited screens.

## 2. Screen and state audit

| Screen | States reviewed | Result |
|---|---|---|
| Gözlem (dashboard) | idle, BLE active, Wi-Fi active, stopping, permission, unavailable, error, alerts on/off | consistent hierarchy; status text+tone; 48dp controls |
| Gözlemler | list, radar, timeline, source filter, favourites, advanced panel, empty/no-session/no-match | single toolbar surface; honest empty states |
| Kayıt ayrıntısı | present record, missing record after retention | honest missing state, no fabrication |
| Geçmiş | list, filtered, favourites, labels/notes, detail, single delete confirm, clear-all confirm | destructive actions separated and confirmed |
| Analitik | group list, detail, membership add, create/rename, delete confirm, compare (stacked + >=600dp) | numbers from engine only |
| Rapor | four steps, privacy banner, empty scope, saved/cancelled/failed | explicit defaults, honest outcomes |
| Ayarlar | four sections, consents, retention, destructive label, privacy disclosures, diag unlock | defaults preserved |
| Katalog | builtin list, user rules, create/edit, delete confirm, limit reached, counter | limit visible, delete emphasised |

Cross-cutting checks (source-level): no hardcoded user-facing string was added; all
interactive rows/buttons use `heightIn(min = 48.dp)` or larger targets; headings use
`semantics { heading() }`; colour is never the only carrier of meaning; the shell owns
insets, so screens add no duplicate Scaffold padding.

## 3. Responsive, text and inset risks (DEVICE PENDING)

2.0x font reflow of the new toolbars and step headers, landscape, medium/expanded widths
(only the >=600dp comparison path is code-verified), IME behaviour on the catalog and
annotation editors, gesture-navigation insets, and contrast of the ATTENTION/STALE tones
in both themes. These cannot be verified on the host and stay open.

## 4. Gates (fresh, final tree)

core 370/370 · app 109/109 · `compileDebugKotlin` · `assembleDebug` · lint 0 errors / 15
warnings (baseline). Debug APK only; versionCode 1 / 0.1.0-phase1 unchanged.

## 5. Xiaomi acceptance handoff (developer)

1. Install over the previous build; confirm History, annotations, groups, presets and
   settings survive.
2. Gözlem: start BLE (20 s) and Wi-Fi (30 s) separately; stop early; leave the app and
   confirm the session stops; deny a permission and confirm nothing auto-starts.
3. Gözlemler: switch list / radar / timeline; tap a radar dot (48dp target now); apply
   source, favourite and advanced filters; confirm empty-state copy.
4. Geçmiş: open a record, edit label/note, star it, delete one (confirm dialog), attempt
   clear-all and cancel.
5. Analitik: create a group, add members, rename, compare two groups; rotate to landscape
   and check the >=600dp side-by-side layout on a tablet if available.
6. Rapor: export CSV and JSON through the SAF picker; cancel once; verify the file content
   and that SSID/labels/notes stay excluded unless opted in.
7. Ayarlar: toggle recording, SSID names, retention 7/14/30; expand all three privacy
   disclosures; seven-tap the version to reveal the debug section (DEBUG only).
8. Katalog: add rules up to the visible 30 limit, edit, disable, delete with confirmation.
9. Repeat steps 2-8 at 2.0x font scale and in dark theme.

Overall UI/UX roadmap completion stays OPEN until this sign-off is reported.
