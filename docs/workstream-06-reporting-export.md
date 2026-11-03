# Workstream 06 — Local Reporting and Explicit Export (2026-11-03)

CSV/JSON exports of ALREADY-SAVED, sanitized History records and WS05 analytical
groups, through the Android Storage Access Framework only. No import/restore, no
backup overwrite, no network, no new permissions, no visual redesign.

## Verified source fields (trust table)

| Field | Source | Export default | Notes |
|---|---|---|---|
| source (BLE/WIFI) | HistoryRecord.radio | included | pipeline provenance |
| saved_at | savedAtEpochMs | included (ISO-8601 UTC) | save time, NOT first-seen |
| observed_at | observedAtEpochMs | included (ISO-8601 UTC) | receipt time from the pipeline |
| freshness | FreshnessState | included | captured at save time; cached Wi-Fi stays STALE |
| signal_category | SignalBucket | included | category, never a distance |
| type_hint / manufacturer_hint | sanitized pipeline hints | included | ecosystem/format hints, never identity |
| age_ms | ageMs | included (null when unknown) | age at save time |
| network_name (SSID) | displayName | EXCLUDED (opt-in) | only exists under the separate Wi-Fi-name consent; never reconstructed |
| user_label / user_note | WS04 annotations | EXCLUDED (opt-in) | user text, displayed as such |
| record id | HistoryRecord.id | NEVER exported | no tracking/identity representation |
| MAC/BSSID/payload/fingerprint/FieldDiag | — | structurally unreachable | no such field exists in the export path |

## Scope and selection

Allowed scopes: one named group (explicit selection), two-group comparison
(explicit), or ALL saved History behind an explicit confirmation dialog. The
History snapshot IS the surviving set: deleted or expired records simply do not
exist, and dangling group ids are reported as `missing_record_count` instead of
being substituted. No transient live row, alert or diagnostic data is reachable.

## CSV

UTF-8 text, CRLF line endings, RFC 4180 quoting (comma, quote, CR, LF; quotes
doubled). Stable header order:

`row,source,saved_at_utc,observed_at_utc,freshness,signal_category,type_hint,manufacturer_hint`
plus `network_name` when names are opted in, then `user_label,user_note` when
annotations are opted in. `row` is a report-local ordinal, not an identity.
Unknown values use the explicit token `unknown`. Ordering is deterministic:
saved_at DESC, observed_at DESC, radio, type_hint (nulls last).

Spreadsheet formula defense: after skipping leading whitespace/control characters,
a text value starting with `=`, `+`, `-`, `@`, TAB or CR is prefixed with a single
apostrophe (the original value is preserved after the prefix). Applied to every
text cell, before quoting is decided. Documented transformation, not a silent one.

## JSON

Versioned (`schema_version: 1`), `report_type` in {`records`, `group-summary`,
`group-comparison`}, `generated_at_utc`, `field_inclusion`, `scope`, typed values
(numbers unquoted, booleans real, unknown = `null`), deterministic arrays, and
honest `notes` stating that record counts are not device counts, that freshness is
save-time provenance, and that the destination controls any external copy.
No locale-dependent numbers or time strings (Locale.ROOT + UTC only).

## Analytics reuse

Group summaries and comparisons call the existing WS05 `SessionAnalytics`
(same stats/buckets engine) over the exact surviving member records, so totals
reconcile by construction. Verification asserts equality with the engine output.
Group metadata is an analytical selection, not a scanning session.

## SAF and error handling

`ActivityResultContracts.CreateDocument` with `text/csv` and `application/json`
(two typed launchers; no wildcard MIME). Generation and stream writing run on
`Dispatchers.IO`; the chosen URI is opened via ContentResolver and closed with
`use`. Cancellation (`uri == null`) shows "İptal edildi; hiçbir veri değişmedi."
and changes nothing. Write/generation failures report a clear Turkish error and
never delete unrelated files. No storage permissions, no app-private export cache,
no manifest provider, no share intent (documented as deliberately save-only).

## Storage / lifecycle

The screen is a READ-ONLY projection: it takes the already-loaded History records,
WS05 groups and WS04 annotations as parameters. It performs no repository
construction, no persistence write, no scan/permission/session call. MainActivity
initialization is unchanged (repositories still constructed in `onCreate` after
`super.onCreate`).

## Tests (host/JVM)

13 new behavioral tests (ExportTest): default exclusion of names/annotations,
opt-in inclusion, CSV quoting with Turkish/Unicode/newlines, formula-injection
neutralization (including leading-whitespace bypass attempts), JSON version/type/
nulls, empty + invalid-timestamp handling, deterministic ordering, group totals and
missing counts, comparison equality with the WS05 engine, deleted-record exclusion,
no source mutation, projection without ids, safe filenames and MIME types.
Phone-only behavior (SAF picker UI, provider quirks, real file encoding on device)
is NOT host-verified.

## Deferred

- Import / restore / backup overwrite: needs a versioned envelope, validation,
  duplicate-id reconciliation, consent re-confirmation, rollback and quota limits;
  deliberately not implemented.
- PDF summary: would need dependency or architectural expansion; deferred.
- WS07/WS08 and the UI/UX redesign: not started.

## Xiaomi acceptance checklist

1. Install OVER the existing app (never uninstall; History/groups must survive).
2. Cold launch, then reopen; confirm saved History, groups, presets, user rules,
   labels/notes and FieldDiag-04 gate are intact.
3. Geçmiş → "Rapor oluştur / Dışa aktar" → pick a group → CSV → save via the system
   picker; verify row count equals the group's surviving records.
4. Export the same group as JSON; verify `schema_version`, `report_type`,
   UTC timestamps and typed values.
5. Confirm SSID/labels/notes are ABSENT by default; toggle inclusion and confirm
   they appear only then (names only if consent recorded them).
6. Cancel an export and confirm nothing changed.
7. Export a two-group comparison (CSV and JSON); totals must match WS05 statistics.
8. Reopen the app: no data loss, no unintended scan.
9. Confirm opening/using the export screen never starts BLE/Wi-Fi observation and
   never requests permissions.
10. Confirm earlier views (live list/Radar/Timeline, filters, favorites, alerts)
    and the seven-tap FieldDiag unlock are unchanged.
