# UX-05 — History List, Details, Favorites, Labels and Safe Actions (2026-11-08)

Status: IMPLEMENTED (debug-only). Presentation only: stored records, repositories,
filters, labels/notes semantics and the confirm-before-delete contract are unchanged.

## 1. What changed (source)

| Area | Before | After | Source |
|---|---|---|---|
| History toolbar | Count text, filter chip row, two full-width entry buttons and a separate favorites chip row stacked vertically | ONE surface container holding source filters + favorites chip in a single flow, and "Rapor oluştur" / "Gruplar" side by side at equal weight | `ui/HistoryScreens.kt` |
| Filter chips | `HistoryFilterRow` wrapped its own `FlowRow` | `HistoryFilterChips` emits the same three chips with the same callbacks, callable inside the shared toolbar flow | `ui/HistoryScreens.kt` |
| Destructive action | Full-width outlined "Tümünü sil" at the end of the list | Low-emphasis `TextButton` in error color, still guarded by the unchanged explicit confirm dialog and the existing accessibility description | `ui/HistoryScreens.kt` |

Nothing else in the History surface moved: the record row (source badge, metadata
hierarchy, favourite star, delete), the detail screen, the annotation editor, empty /
failed-closed / dropped-record notices and the "Ayarlar'a git" affordance are untouched.

## 2. Preserved contracts

- Saved History records, annotations, labels, notes and favorites: identical repository
  calls and persistence; no migration, no re-keying, no record identity change.
- Filters: ALL / BLUETOOTH / Wi-Fi and favorites-only semantics unchanged (pure, tested).
- Deletion: still one record per explicit tap, and "Tümünü sil" still requires the
  confirmation dialog; no new bulk path, no undo fabrication.
- Empty and unavailable states: same honest copy and same Settings pointer.
- No scanning, permission request, network call or automatic export is triggered from
  any History interaction.

## 3. Evidence

- `:core:radio:test` 370/370 PASS.
- `:app:testDebugUnitTest` 81/81 PASS (History filter, favorites, annotation and
  retention behaviours remain covered).
- `:app:compileDebugKotlin` PASS, `:app:assembleDebug` PASS (debug only).
- `:app:lintDebug` 0 errors / 15 warnings — identical to the accepted baseline.

## 4. Not verified here (device-only)

Row density and wrapping at 2.0x font, the toolbar's real touch targets, the confirm
dialog's rendering, star toggle feedback and scroll performance on the Xiaomi device.
Light theme, TalkBack and 2.0x font checks remain excluded by developer decision.

## 5. Xiaomi acceptance checklist (open)

1. Install over the current build; existing records, labels, notes and favorites intact.
2. Toolbar: three source chips, favorites chip, report and groups buttons reachable and
   correctly laid out at normal and 2.0x font.
3. Open a record: metadata hierarchy readable, label/note editing unchanged.
4. Toggle a favourite; verify the favourites-only filter shows exactly the starred rows.
5. Delete one record after confirming; cancel path leaves data untouched.
6. "Tümünü sil" still requires the confirm dialog (do not run it on real data).
7. Scroll a 200+ record list; no jank, no lost scroll position on return from detail.
