# Radio Observer

Local-first, receive-only Wi-Fi access-point and BLE-advertisement observation app for
Android (work in progress).

**Current status: Phase 1 — Architecture Foundation & Pure JVM Parsers.**
No scanning, no permissions, no data collection. Parsing, classification, and state logic
are pure JVM code verified by device-free unit tests.

Architecturally informed by the public project
[Fieldwatch](https://github.com/OffGridPete/Fieldwatch) (MIT). No source code copied — see
[NOTICE.md](NOTICE.md).

## Modules

- `:core:radio` — pure Kotlin/JVM: domain models, BLE advertisement parser, conservative
  manufacturer/device-type classifier, Wi-Fi AP classifier, RSSI signal buckets, in-memory
  observation store. Contains no `android.*` imports.
- `:app` — minimal Compose entry point (placeholder UI only).

## Verification (host-only, no device)

```
./gradlew :core:radio:test --no-daemon          # device-free JVM unit tests
./gradlew :app:compileDebugKotlin --no-daemon  # compile-only validation
```

## Honest limitations

- `com.example.radioobserver` is a **provisional** application id; it needs developer
  approval before any release.
- Company-id detection (Apple 0x004C, Samsung 0x0075) identifies ecosystem membership only.
  It does NOT prove an AirTag, SmartTag, or that anyone is being followed. RSSI cannot
  prove distance.
- Wi-Fi classification covers infrastructure access points only: client devices are not
  visible to Android's standard Wi-Fi APIs, and no monitor mode is used or claimed.
- Randomized BLE addresses and locally-administered BSSIDs are flagged, never treated as
  stable identity.
