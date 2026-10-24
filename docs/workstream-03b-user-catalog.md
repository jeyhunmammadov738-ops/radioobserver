# Workstream 03B — Privacy-Safe Local Classification Catalog (2026-10-24)

User-manageable LOCAL classification catalog + explainable rule management on top of
the WS03A signature engine. The five built-in rules are IMMUTABLE and their
classification behavior is unchanged (JVM-tested). Git commit/push was performed at
the owner's explicit per-message instruction (the prompt files' Git-OFF note is
overridden by the owner's live message, same as WS02/03A). Build Mode ON (DEBUG only).

## 1. Verified sanitized classifier inputs (from actual source)
| Input | Type | Source | Used by user rules? |
|---|---|---|---|
| radio | RadioType enum | ObservationRowUi.radio | YES (BLE / WIFI / any) |
| typeHint | pipeline's own public-format label | BleDeviceClassifier | YES — but ONLY via the 4-token whitelist (below) |
| confidence | ObservationConfidence enum | parser | YES (or any) |
| name / SSID | sanitized display name | user-configurable | NO — not a field of SignatureInput or any rule type |
| manufacturerHint | BT-SIG ecosystem name | parser | NO |
| rssiDbm / signalBucket | qualitative signal | radio | NO |
| freshness / ageMs / lastSeen | computed | pipeline | NO |
| rowId / MAC / BSSID / adv bytes | session-local | pipeline | NO |

## 2. Safe selector whitelist (finite, code-owned, structural)
UserRuleTypeToken (the ONLY type-hint tokens a user rule can select; every token is
an exact label the project's own Phase-1 parser emits from public format docs):
- IBEACON_LAYOUT — "iBeacon (Apple layout)"
- IBEACON_LIKE_MISMATCH — "Apple iBeacon-like prefix (length mismatch)"
- FIND_MY_SEPARATED — "Find My separated-style advertisement (Apple layout)"
- FIND_MY_LIKE_MISMATCH — "Apple Find My-like prefix (length mismatch)"
Structural proof (JVM-tested): UserRuleSelector contains NO String field at all —
only RadioType?, UserRuleTypeToken?, ObservationConfidence?. The only Strings on a
UserSignatureRule are the bookkeeping id and the display-only title. Arbitrary
matching text, regex, names or live observation data CANNOT be represented as a rule.

## 3. Trust boundaries / fixed taxonomy
- Built-in (SignatureCatalog, v1, 5 rules): immutable, read-only in UI and engine.
- User (ws03b-user-catalog-v1): bounded to 30 rules, enabled flag, ids "U-01"..,
  sequence-deterministic. Category taxonomy is the EXISTING fixed SignatureCategory
  enum (ACCESS_POINT, IBEACON_FAMILY, FIND_MY_FAMILY) — no new categories, no model/
  manufacturer/identity claims. Title is management display ONLY, validated (trimmed,
  non-blank, <= 40 chars) and NEVER used in matching (JVM-tested).

## 4. Precedence and conflict policy (deterministic, JVM-tested)
1. Built-in evaluation is EXACTLY WS03A classify(): status, category, rule ids and
   rationale are never changed, upgraded or suppressed by user rules.
2. Enabled user rules matching the same sanitized input are reported SEPARATELY as
   unverified suggestions (SignatureHint.userSuggestions, sorted by rule id) with the
   label "Kullanıcı tanımlı ipucu — doğrulanmamış".
3. Contradictory user suggestions are ALL listed — no arbitrary winner, never a
   verified identity; built-in CONFLICT/UNKNOWN outcomes stay exactly as before.
4. One additional bounded scan (<= 30 rules) with exact categorical matching —
   no regex, recursion, reflection, network or dynamic plugins.

## 5. Storage schema and corruption safety
File: app-private "signature_user_catalog_v1.txt" (SignatureUserRuleFileStorage),
COMPLETELY separate from WS02 filter_presets_v1.txt and History storage — neither is
read, written or migrated. Line format:
  ws03b-user-catalog-v1  (version header, must match exactly)
  id|radio|token|confidence|category|title|enabled   ('-' = any/null, title escaped)
Properties: writes ONLY on explicit user actions (create/edit/toggle/delete), atomic
temp+rename (OEM fallback copy+delete), encoding of the COMPLETE next state. Loading
is fail-closed: missing file -> empty; unsupported version -> empty WITHOUT deleting
or rewriting the original; malformed/oversized lines dropped AND COUNTED (exposed as
lastLoadIssues); duplicate ids keep the first; records beyond 30 dropped. Titles
round-trip pipes, backslashes and newlines.

## 6. UI behavior
"Sınıflandırma kataloğu" management screen reached from Ayarlar (NO fifth bottom
tab; new AppDestination.SignatureCatalog owned by the SETTINGS tab, back returns to
Ayarlar, bottom bar hidden like other detail screens). Shows: built-in rules
read-only (badge "Yerleşik", category, rule id + catalog version, rationale), user
rules (badge "Kullanıcı tanımlı", title, selector summary, category, enable Switch,
Düzenle/Sil text buttons), create dialog with FilterChip categorical selectors only,
honest typed validation errors, delete confirmation dialog, the identity caution
"Aygıt türü ipuçları kesin cihaz kimliği değildir." and the truthful empty state.
Observation detail gains a "Kullanıcı tanımlı öneriler" block: each suggestion shows
category + unverified label + rule id/schema version + the categorical-match
explanation ("Kural cihaz adı, MAC/BSSID veya konum ile eşleşmez."). List rows are
NOT extended (no clutter); no category filtering; WS02 preset schema untouched.
No icon dependencies added (existing text-button design language reused).

## 7. MainActivity startup safety (WS02 hotfix contract)
SignatureUserRuleFileStorage + UserSignatureRuleRepository are constructed EXACTLY
ONCE inside onCreate AFTER super.onCreate, next to the existing FilterPresetFileStorage
pattern. NO Activity property initializer touches Context (lateinit + mutableStateOf
only). No !! assertions masking init problems, no swallowed startup exceptions, no
static Activity references, no async init race. Real cold start remains
Xiaomi-verified (host level: repository needs no Android Context — JVM-tested).

## 8. Test matrix (JVM; 13 new core tests in UserSignatureRulesTest; 321/321 total)
A five built-in rules unchanged (ids, categories, SUPPORTED/UNKNOWN outcomes, empty-
user-rules equality) · B safe creation · C whitelist structural proof (no String in
selector; only id+title Strings on the rule; title never used in matching) ·
D enable/disable · E edit/delete/duplicate/NOT_FOUND honesty · F title validation +
30-rule bound · G built-in precedence (never overridden) · H contradictory
suggestions all listed, sorted, unverified · I UNKNOWN preserved, determinism ·
J save/reload round-trip incl. escaped titles · K corrupt/duplicate/oversize/
unsupported-version fail-closed WITHOUT rewrite; missing file no crash no write ·
L classification never persists/creates anything · M separate stores by construction ·
N repository constructs with no Android Context · O membership/filters/disposal
unchanged with user rules attached · P no diagnostics coupling.
Existing suites: WS03A SignatureClassifierTest, WS02 preset/filter/startup-boundary,
navigation, radar, disposal — all green (49/49 app).

## 9. Fresh gates (all --no-daemon, tests/lint --rerun-tasks, exit 0)
:core:radio:test 321/321 · :app:testDebugUnitTest 49/49 · :app:compileDebugKotlin ·
:app:assembleDebug · :app:lintDebug 0 errors/15 warnings (baseline kept: the two new
findings — Typos "adres" and a PluralsCandidate on "%d karakter" — were fixed by
rewording the new strings, not by suppressing). DEBUG APK only:
com.hjinlabs.radioobserver 0.1.0-phase1 (versionCode 1 — NOT bumped). Install-over
safe; NEVER uninstall or clear data.

## 10. Limitations (honest)
- User rules cannot recognize NEW device types beyond the fixed category taxonomy and
  the 4-token whitelist: they only let the user express UNVERIFIED categorical hints
  over evidence the engine already sanitizes. No recognition-coverage claim is made.
- Unknown devices stay UNKNOWN by design; a user suggestion is never shown as verified.
- Android Activity launch/Compose touch behavior is not host-verifiable; Xiaomi
  acceptance remains outstanding (checklist below).

## 11. Xiaomi acceptance checklist
Install over existing app (no uninstall, no data clear) -> cold start + reopen without
crashes -> old History and saved filter presets intact -> Ayarlar > "Sınıflandırma
kataloğunu yönet": built-in rules read-only -> create an allowed rule (e.g. Wi-Fi
kaynağı + herhangi bir ipucu -> Erişim noktası) -> edit, disable, re-enable it ->
delete with confirmation -> restart: saved rules persist -> unsupported observations
remain UNKNOWN -> matching detail shows "Kullanıcı tanımlı ipucu — doğrulanmamış"
with rule provenance -> Radar/Timeline/filters/stale Wi-Fi labels unchanged -> no
automatic scanning -> FieldDiag-04 still hidden, 7-tap unlock unchanged.
