package com.example.radioobserver.ui

/**
 * FINAL POLISH (2026-10-23, developer-approved): presentation-boundary localization
 * of the Wi-Fi classifier's ENGLISH vendor fallbacks.
 *
 * The core classifier (WifiObservationClassifier, UNTOUCHED — its detection logic,
 * identifier parsing and typed models stay exactly as they are, and its JVM tests
 * keep asserting the English sentinel) emits honest English sentinel strings such as
 * "Unknown vendor (locally administered BSSID)". Sanitization passes them through,
 * so the UI previously printed the raw English text on the Xiaomi screenshots.
 *
 * This PURE mapper recognizes EXACTLY those sentinels at the presentation boundary
 * and tells the UI to render natural Turkish instead. Anything else — a real
 * OUI-derived vendor hint — passes through UNCHANGED. It never invents a vendor,
 * never exposes a BSSID, and never alters domain data.
 */
object ManufacturerPresentation {

    /** Sentinel emitted by the classifier for a locally administered (randomized) BSSID. */
    const val FALLBACK_LOCAL = "Unknown vendor (locally administered BSSID)"

    /** Sentinel emitted by the classifier when the OUI prefix is simply unknown. */
    const val FALLBACK_GENERIC = "Unknown vendor"

    /** True when the hint is a classifier sentinel that must be localized for display. */
    fun isKnownFallback(hint: String?): Boolean =
        hint == FALLBACK_LOCAL || hint == FALLBACK_GENERIC

    /** True when the sentinel specifically means a locally administered address. */
    fun isLocallyAdministeredFallback(hint: String?): Boolean = hint == FALLBACK_LOCAL

    /**
     * Display value for the manufacturer field: the localized "Üretici belirlenemedi"
     * for sentinels, the original hint otherwise. The Turkish text itself comes from
     * resources in the composable layer — this function only decides WHICH path.
     */
    fun isLocalizedFallback(hint: String?): Boolean =
        isKnownFallback(hint) || bleUnknownManufacturerId(hint) != null

    /**
     * MICRO-POLISH (2026-10-23): the core BLE classifier emits the honest English
     * sentinel "Unknown manufacturer (id 0x038F)" for company ids outside the Phase 1
     * lookup table. Presentation-only parsing (the classifier itself is untouched and
     * its tests keep asserting the English sentinel): returns the company id when the
     * hint is EXACTLY that sentinel, null otherwise. A code NEVER identifies a
     * manufacturer; the UI shows it only as separate optional context.
     */
    fun bleUnknownManufacturerId(hint: String?): Int? {
        if (hint == null) return null
        val m = BLE_UNKNOWN.matchEntire(hint.trim()) ?: return null
        return m.groupValues[1].toIntOrNull(16)
    }

    /** Known, VERIFIED manufacturer hints (Apple/Samsung...) are never touched. */
    fun isKnownManufacturerHint(hint: String?): Boolean =
        hint != null && !isLocalizedFallback(hint)

    /** Honest display of the raw company id: uppercase, 0x-prefixed, as the classifier formats it. */
    fun formatBleCompanyId(companyId: Int): String = "0x%04X".format(companyId)

    private val BLE_UNKNOWN =
        Regex("Unknown manufacturer \\(id 0x([0-9A-Fa-f]{1,4})\\)$")
}
