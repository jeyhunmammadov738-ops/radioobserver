# Radio Observer — Architecture Spec (pure-JVM core)

Radio Observer is a local-first, receive-only Wi-Fi access-point and BLE-advertisement
observer for Android, split into a device-free JVM core and a thin Compose app.

## Layers

- `:core:radio` — pure Kotlin/JVM, zero `android.*` imports: models, parsers,
  classification, signal buckets, session coordinators, pipeline, History,
  annotations, analytical groups, analytics and export report builders. Verified by
  device-free unit tests (370 at the time of writing).
- `:app` — Android/Compose layer: permission flow, foreground session wiring,
  sanitized UI state, Storage Access Framework document saving.

## Privacy contract

- No uploads; no raw MAC/BSSID retention in UI or reports; SSIDs only under a
  separate consent; RSSI stored as conservative categories, never distance.
- History recording is opt-in and deletable; exports are explicit user actions.

## Verification

Host-only Gradle gates: `:core:radio:test`, `:app:testDebugUnitTest`,
`:app:compileDebugKotlin`, `:app:assembleDebug` (DEBUG only), `:app:lintDebug`.
Real-device behavior remains the developer's acceptance step.
