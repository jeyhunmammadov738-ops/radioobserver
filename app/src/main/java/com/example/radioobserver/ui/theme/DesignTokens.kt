package com.example.radioobserver.ui.theme

/**
 * UX-01 (2026-11-06, developer-approved UIUX-RM v1.0): the CONTROL DECK visual
 * foundation as PURE KOTLIN data.
 *
 * Why plain ARGB longs instead of Compose [androidx.compose.ui.graphics.Color]?
 * So the token set and its WCAG contrast math stay 100% device-free and are
 * verified by ordinary host JVM tests (no Compose runtime, no Robolectric, no new
 * dependency). Theme.kt is the ONLY place that maps these longs to Compose colors.
 *
 * Every value below is a token, not a scattered literal: screens must consume
 * [ColorRole] / MaterialTheme roles, never these constants directly.
 */
object ControlDeckTokens {

    // --- dark (primary) scheme: deep neutral background, differentiated surfaces,
    // restrained indigo primary, limited teal secondary, clear text ---
    const val DARK_BACKGROUND = 0xFF10131AL
    const val DARK_ON_BACKGROUND = 0xFFE9EDF7L
    const val DARK_SURFACE = 0xFF161A24L
    const val DARK_ON_SURFACE = 0xFFE9EDF7L
    const val DARK_SURFACE_LOW = 0xFF1C2230L
    const val DARK_SURFACE_CONTAINER = 0xFF202836L
    const val DARK_SURFACE_HIGH = 0xFF2A3446L
    const val DARK_SURFACE_HIGHEST = 0xFF303B51L
    const val DARK_ON_SURFACE_VARIANT = 0xFFB6BFD2L
    const val DARK_SURFACE_VARIANT = 0xFF2A3446L

    const val PRIMARY = 0xFFAEBFFFL
    const val ON_PRIMARY = 0xFF16224EL
    const val PRIMARY_CONTAINER = 0xFF2B3B78L
    const val ON_PRIMARY_CONTAINER = 0xFFDCE1FFL

    const val SECONDARY = 0xFF47C3BAL
    const val ON_SECONDARY = 0xFF00332FL
    const val SECONDARY_CONTAINER = 0xFF11463FL
    const val ON_SECONDARY_CONTAINER = 0xFFA8EDE3L

    const val TERTIARY = 0xFFC9C2E8L
    const val ON_TERTIARY = 0xFF2C2949L

    const val ERROR = 0xFFFFB4ABL
    const val ON_ERROR = 0xFF690005L
    const val ERROR_CONTAINER = 0xFF5C1B1BL
    const val ON_ERROR_CONTAINER = 0xFFFFDAD6L

    const val OUTLINE = 0xFF8C97AEL
    const val OUTLINE_VARIANT = 0xFF3A4356L

    const val INVERSE_SURFACE = 0xFFE9EDF7L
    const val INVERSE_ON_SURFACE = 0xFF262B36L

    /** Full orange/amber warning tone used only where a warning is genuinely distinct. */
    const val WARNING = 0xFFFFC46BL
    const val ON_WARNING = 0xFF3D2A00L
    const val WARNING_CONTAINER = 0xFF4A3610L
    const val ON_WARNING_CONTAINER = 0xFFFFDFA8L

    /** Cached/stale marker tone (Wi-Fi "Eski"), always paired with visible text. */
    const val STALE = 0xFF9AA3B5L

    fun argb(argb: Long): Long = argb and 0xFFFFFFFFL
    fun alphaOf(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()
    fun redOf(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()
    fun greenOf(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()
    fun blueOf(argb: Long): Int = (argb and 0xFF).toInt()

    private fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    /** WCAG relative luminance of an opaque ARGB value. */
    fun relativeLuminance(argb: Long): Double =
        0.2126 * channel(redOf(argb)) + 0.7152 * channel(greenOf(argb)) + 0.0722 * channel(blueOf(argb))

    /** WCAG contrast ratio between two opaque ARGB values, (L1 + 0.05) / (L2 + 0.05). */
    fun contrastRatio(a: Long, b: Long): Double {
        val la = relativeLuminance(argb(a))
        val lb = relativeLuminance(argb(b))
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}

/**
 * Semantic dimension tokens (dp expressed as Int so they stay host-testable).
 * The Compose layer converts with `.dp`; nothing here implies a touch target
 * smaller than [MIN_TOUCH_TARGET].
 */
object DeckDimensions {
    const val MIN_TOUCH_TARGET = 48
    const val PRIMARY_ACTION_HEIGHT = 48
    const val COMPACT_ROW_MIN_HEIGHT = 44
    const val BADGE_HEIGHT = 22
    const val ICON_SMALL = 16
    const val ICON_STANDARD = 20
    const val ICON_LARGE = 24
    const val CARD_PADDING = 12
    const val SECTION_GAP = 16
    const val LIST_ITEM_GAP = 8
}

/** 4dp-based spacing scale (Int dp for the same host-testable reason). */
object DeckSpacing {
    const val XS = 4
    const val SM = 8
    const val MD = 12
    const val LG = 16
    const val XL = 24
    const val XXL = 32

    val all: List<Int> = listOf(XS, SM, MD, LG, XL, XXL)
}
