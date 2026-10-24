# Workstream 04 — Privacy-Safe Favorites, Record-Bound Labels, In-App Session Alerts (2026-10-24)

Session-local live favorites, RECORD-BOUND History annotations (favorite/label/note)
and CONTROLLED IN-APP foreground session alerts on top of WS01/02/03A/03B, whose
architectures are unchanged. The prompt file said Git-OFF; the owner's live message
granted full Git authority for this iteration (same explicit-override pattern as
WS02/03A/03B). Build Mode ON (DEBUG ONLY); versionCode NOT bumped.

## 1. Identity analysis (the design's foundation)
| Concept | Identifier | Scope | May represent a physical device? |
|---|---|---|---|
| Live observation | session-local rowId ordinal | one session | NO — recycled across sessions |
| Saved History record | random UUID generated at save | one stored record | NO — never address-derived |
| Real physical device | — | cross-session | NEVER asserted anywhere |
Cross-session correlation by MAC/BSSID/payload/name/manufacturer hint/classification/
RSSI/fingerprint is structurally impossible in the new code: annotations key on
History record ids only; live favorites key on session-local rowIds only; alerts key
on (trigger, rowId) pairs only. None of the three stores or compares names,
addresses or payloads.

## 2. Session-local LIVE favorites (core: LiveFavorites.kt)
- toggle/isFavorite/retain/rowsFor — pure Set<String> in/out, no write channel at
  all (no History write, no scan dispatch, no persistence; JVM-tested purity).
- Disposal: MainActivity feeds every orchestrator publication through
  LiveFavorites.retain(favorites, currentRowIds); an emptied/disposed session
  clears the set completely (JVM-tested). Nothing survives restart by construction.
- UI: star TextButton (48dp target) in live list rows AND the live detail header;
  "Yalnız favoriler" chip applies favorites-only to the LISTE view only — Radar and
  Zaman tüneli keep their unchanged membership and marker accounting.
- Honest disclosure "Canlı favoriler yalnızca geçerli oturumda tutulur." and the
  favorites-specific empty state "Bu oturumda favori işaretli gözlem yok."

## 3. Saved History favorites + record-bound labels/notes (core: HistoryAnnotations.kt)
- HistoryAnnotation(recordId, label?, note?, favorite) — structural JVM test asserts
  the class has EXACTLY these four fields: no radio type, no address, no name, no
  fingerprint; and the repository has NO match/search method.
- Bounds: label <= 40, note <= 200 chars (validated; honest TOO_LONG /
  RECORD_NOT_FOUND typed errors, no swallowing). Blank input clears a field.
- Storage: app-private history_annotations_v1.txt via HistoryAnnotationFileStorage
  (atomic temp+rename, OEM fallback), constructed ONCE in MainActivity.onCreate
  AFTER super.onCreate (WS02 hotfix contract). Schema "ws04-annotations-v1";
  unsupported version -> empty WITHOUT rewriting the original; malformed/oversized/
  duplicate lines dropped AND counted; missing file -> empty, no write.
  COMPLETELY separate from the History store, WS02 presets and the WS03B catalog.
- Lifecycle sync: HistoryController.publish() calls retainAll(current record ids) —
  retention sweeps, the 500-record cap, single deletes and Clear All all drop
  orphaned annotations; orphaned metadata is never consulted and can NEVER recreate
  a record (JVM-tested). Favoriting writes no History record and starts nothing;
  a saved favorite never exempts a record from expiration/deletion.
- UI: star in History rows, "Favoriler" chip (no new navigation tab), and an
  annotation editor card in the record detail (label/note fields, favorite switch,
  Kaydet/Vazgeç/Ek açıklamayı sil, length error disclosure, privacy note
  "Geçmiş etiketleri yalnızca seçilen kayda aittir; aynı cihazı daha sonra otomatik
  tanımaz.").

## 4. Controlled IN-APP session alerts (core: alerts/SessionAlerts.kt)
- Pure SessionAlertEngine reducer; DISABLED BY DEFAULT, enabled only by an explicit
  Switch action; a fresh engine at every cold start (no persistence, no auto
  re-enablement). No Android permission (no POST_NOTIFICATIONS), no service, no
  vibration/audio, no wake lock, no background receiver, no network. IN-APP ONLY.
- Session gate: MainActivity computes sessionActive = BLE/WiFi status == OBSERVING
  (a genuine user-started foreground session). onRows(rows, sessionActive=false)
  resets the engine (no alerts during/after foreground loss, expiry, Stop).
- Triggers (categorical, sanitized only — no name/MAC/BSSID/fingerprint triggers
  exist): NEW_FRESH_BLE, NEW_FRESH_WIFI (both require FreshnessState.FRESH),
  SIGNAL_IMMEDIATE/NEAR/FAR, MANUFACTURER_KNOWN/UNKNOWN. A cached Wi-Fi result
  (STALE, the "Eski" pill) can NEVER produce a new-device alert (JVM-tested);
  freshness is respected, never invented.
- Dedup and bounds: at most one alert per (trigger, observation) per session; seen
  rowIds per session; stable session-local alert ids A-0001..; bounded queue of 10
  with deterministic FIFO eviction; re-feeding the SAME snapshot emits NOTHING
  (recomposition-safe, JVM-tested); dismiss one / dismiss all; queue, dedup and
  sequence counters all cleared on resetSession.
- Feed point: the engine is fed ONLY from the orchestrator publication callback
  (every state publication, never from Compose recomposition).
- UI: compact "Oturum uyarıları" card on the Kontrol screen (Switch, explanation
  "Uyarılar yalnızca uygulama ön plandayken ve gözlem oturumu etkin olduğunda
  çalışır.", privacy note, trigger chips, current alerts with dismissal,
  "Etkin oturum yok; uyarılar bekletilir." indication, honest empty state).

## 5. Preserved behavior (untouched, re-verified by the full suite)
Manual Start, separate post-permission Start, passive Wi-Fi, 20s/30s session
bounds, RESUMED eligibility, stop on foreground loss, independent sessions,
sanitized pipeline, TTL/capacity/disposal, fresh/old Wi-Fi classification, source
+ advanced filters + saved presets (schema untouched), Liste/Radar/Timeline
membership, radar cluster accounting, History consent/retention, signature
classification + user catalog, hidden FieldDiag-04, 7-tap unlock. Favorites,
notes and alert state never control the scanning engine (alert controls are pure
engine-state changes; star toggles are pure UI state).

## 6. Test matrix (JVM; 19 new core tests; 340/340 total)
LiveFavoritesTest: A toggle · B disposal cleanup · C no History write (no write
channel exists) · K no raw identifiers · J no cross-session matching · R read-only
derivation. SessionAlertEngineTest: L default OFF · M active-session requirement ·
N cached/STALE Wi-Fi never alerts · O per-rule dedup + bounded FIFO queue ·
P foreground-loss/expiry reset · Q same-snapshot re-feed emits nothing ·
R dismiss/dismissAll. HistoryAnnotationsTest: D save/reload · E favorites
filtering · F create/edit/remove + validation · G escaping round-trip · H corrupt +
unsupported schema fail closed without rewrite · I retention/deletion cleanup,
orphans cannot recreate · J/K structural record-id binding, no matching API.
App suite 49/49 (navigation, filters/presets, radar, startup boundary, signature).
Host-unverifiable (explicit limitation): real Activity launch, touch behavior,
Compose recomposition timing and actual radio reception remain Xiaomi-verifiable
only; no ADB/emulator/instrumentation was used. Developer-declined tests (2.0x
font, light theme, TalkBack) remain out of scope per the explicit decision.

## 7. Fresh gates (all --no-daemon, tests/lint --rerun-tasks, exit 0)
:core:radio:test 340/340 · :app:testDebugUnitTest 49/49 · :app:compileDebugKotlin ·
:app:assembleDebug · :app:lintDebug 0 errors/15 warnings (baseline kept: the one
NEW finding — UnusedResources on live_favorites_empty — was fixed by WIRING the
string into the favorites empty state, not by deleting or suppressing).

## 8. Xiaomi acceptance checklist
Install over existing app (no uninstall, no data clear) -> cold start + reopen
without crashes -> old History, filter presets and signature catalog intact ->
Gözlemler: star a live row (row + detail), watch "Yalnız favoriler" filter it,
toggle off/on -> verify favorites vanish after session end/restart (session-local)
-> Geçmiş: star a saved record, "Favoriler" chip filters -> open its Ayrıntılar,
add label + note + favorite, Kaydet -> edit, then blank-clear a field -> remove
annotation -> restart: annotation persists -> delete the record: annotation gone
and cannot recreate it -> Kontrol: "Oturum uyarıları" default OFF -> enable,
select triggers -> start a BLE session: fresh new observations alert once; repeat
row/snapshot produces no repeats -> Wi-Fi session: cached "Eski" rows NEVER alert
-> session expiry/Stop clears alerts and disables emission until re-enabled state
persists -> dismiss single/all -> Liste, Radar, Zaman tüneli, süzgeçler,
Sınıflandırma kataloğu, FieldDiag-04 (gizli + 7 dokunuş) all unchanged.

## 9. Limitations (honest)
- Alerts are categorical only and say nothing about device identity; they are
  advisory UI hints, not detections. A STALE/UNKNOWN-freshness row can never
  trigger freshness-based alerts by design.
- Favorites/labels are local-only; no export, no cloud sync (explicitly excluded).
- One annotation set per record (single combined record), bounded lengths.
- Live favorite ids are ordinals: they can collide with another session's
  ordinals by construction — the UI state never persists or crosses sessions,
  so no collision effect is possible.
