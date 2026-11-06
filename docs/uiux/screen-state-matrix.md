# UX-00 — Screen State Matrix

Only states reachable in the current source are listed. "Transition" names the user action;
nothing here authorizes new behaviour.

## Gözlem
| Screen | State | Trigger | Visual requirement |
|---|---|---|---|
| Dashboard | IDLE | app open / after stop | both radios idle, Başlat primary per radio |
| | CHECKING | Başlat tapped | inline progress text, actions disabled |
| | PERMISSION_REQUIRED | missing grant | recovery block with the exact needed grant |
| | BLOCKED | hardware/service off | typed reason + Ayarlar action |
| | OBSERVING | session running | status pill + Durdur + remaining time |
| | STOPPING/STOPPED/TIMED_OUT | stop or expiry | terminal badge, restart primary |
| | ERROR | failure | error banner + retry guidance |
| Alerts panel | disabled (default) | fresh start | switch off, explanation collapsed |
| | enabled, no session | switch on, no session | honest "oturum yok" note; no alerts |
| | enabled + active, empty queue | session running | "henüz uyarı yok" |
| | enabled + alerts | new qualifying observation | bounded list, dismiss per item/all |

## Gözlemler
| State | Trigger | Requirement |
|---|---|---|
| Empty (session never started) | tab open | honest empty; no "no devices" claim |
| Empty (filtered) | filter excludes all | distinct filtered-empty copy |
| Empty (favorites-only) | favorites toggle | favorites-empty copy |
| Populated Liste / Radar / Zaman tüneli | rows present | same membership across views |
| Stale cached Wi-Fi | CRL/updated=false row | "Eski" pill; never "yeni" |
| Detail | row tap | opens detail; back returns to FEED tab |
| Detail (row gone) | session reset | honest unavailable state |

## Geçmiş
| State | Trigger | Requirement |
|---|---|---|
| Consent off | recording disabled | list empty + pointer to Ayarlar |
| Populated + filters | records exist | ALL/BLE/WIFI + Favoriler independently applicable |
| Notices | corrupt load / fail-closed | one-time notice card with dismiss |
| Detail | row "Ayrıntılar" | read-only facts + annotation card |
| Annotation editing | favorite/label/note actions | validation errors inline (label 40/note 200) |
| Delete confirm | Sil | dialog naming the record; cancel safe |
| Groups empty/list | Geçmiş → groups entry | no groups → create CTA |
| Group detail empty/filled | group with 0 / n members | honest empty; stats recompute |
| Compare | two groups chosen | side-by-side metrics + "kayıt ≠ cihaz" note |
| Export | scope/format chosen | counts, inclusion toggles, disclosure |
| Export saved / cancelled / failed | SAF result | explicit terminal message per outcome |

## Ayarlar
| State | Trigger | Requirement |
|---|---|---|
| Recording on/off | switch | immediate, persisted |
| SSID consent on/off | switch | separate, off by default |
| Retention 7/14/30 | chips | confirmed selection |
| Clear-all confirm | button | separated destructive dialog |
| Catalog built-in | entry | read-only rows |
| Catalog user rules empty/filled | CRUD | errors typed |
| About/privacy expanded/collapsed | tap | disclosure, not permanent wall of text |

## FieldDiag-04
| State | Requirement |
|---|---|
| Hidden (default) | nothing rendered |
| Unlocked + toggled on, debug build | panel visible |
| Release build | never visible |
