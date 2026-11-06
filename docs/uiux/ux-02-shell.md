# UX-02 — Global Shell, Four-Tab Navigation, Insets (2026-11-06)

Status: IMPLEMENTED (debug-only). Shell presentation only: no screen content redesign.

## Ownership
- `ui/shell/ShellModel.kt` — PURE chrome projection (`ShellPolicy.chromeFor`), no Compose/android.
- `ui/shell/AppShell.kt` — `DeckTopBar`, `DeckBottomBar`, tab icon/label mapping.
- `MainActivity.kt` — single root Scaffold, `enableEdgeToEdge()`, shell bars; old private
  `MainBottomBar` removed (no duplicate bar implementation).

## Insets
One owner: the ROOT Scaffold. `contentWindowInsets` stays default; `NavigationBar` and
`TopAppBar` consume their own system insets, and the content Column receives
`innerPadding` exactly once. Secondary destinations keep their internal headers and add
no system-bar inset (previously verified: no screen used `safeDrawing`/`systemBars`).

## Bars
- Top bar only on primary tabs (title = existing strings `control_title`, `feed_title`,
  `history_title`, `settings_title`), single line, ellipsis for long Turkish titles.
- Secondary destinations keep their own in-content header + back action, so no second bar
  is stacked (no duplicate titles, no double padding); `chrome.backVisible` is true there.
- Bottom bar: exactly four tabs (Gözlem, Gözlemler, Geçmiş, Ayarlar), Material 3
  selection indicator + always-visible labels, min 48dp targets, no fifth tab and no
  FieldDiag surface. Tab tap only reports `onTabSelected` (existing callback path).

## Behaviour preserved
No session/permission/History effect from shell; destination identity, per-tab
state, restoration and back policy unchanged; hidden developer entry unchanged.

## Gates (final tree)
core 370/370 · app 81/81 (7 new shell tests: four tabs/order, primary chrome,
secondary chrome, title mapping, History ownership, purity, no diagnostic surface) ·
compileDebugKotlin · assembleDebug · lint 0 errors / 15 warnings.

## Deferred to later packages
Screen content redesign (UX-03..08), scroll/divider rhythm, adaptive size classes,
predictive back animation. Device verification: edge-to-edge rendering, gesture vs
3-button navigation, status-bar icon contrast, large-font title truncation = XIAOMI OPEN.
