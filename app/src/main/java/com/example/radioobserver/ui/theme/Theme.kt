package com.example.radioobserver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UX-01 (2026-11-06, developer-approved UIUX-RM v1.0) — CONTROL DECK theme.
 *
 * The DARK scheme is the product's primary identity and is built ONLY from
 * [ControlDeckTokens]; the light scheme is the previously shipped one, kept
 * unchanged because the developer excluded light-theme acceptance from scope
 * (documented in docs/uiux/ux-01-design-system.md).
 *
 * Behavioural note: this file is presentation-only. No session, permission,
 * scanning, storage or alert code path is touched by a theme value, and the
 * theme takes NO Android Context (no dynamic/wallpaper color), so the WS02
 * startup-safety contract is unaffected.
 */
private fun Color.Companion.of(token: Long): Color = Color(token)

private val DarkScheme = darkColorScheme(
    primary = Color.of(ControlDeckTokens.PRIMARY),
    onPrimary = Color.of(ControlDeckTokens.ON_PRIMARY),
    primaryContainer = Color.of(ControlDeckTokens.PRIMARY_CONTAINER),
    onPrimaryContainer = Color.of(ControlDeckTokens.ON_PRIMARY_CONTAINER),
    secondary = Color.of(ControlDeckTokens.SECONDARY),
    onSecondary = Color.of(ControlDeckTokens.ON_SECONDARY),
    secondaryContainer = Color.of(ControlDeckTokens.SECONDARY_CONTAINER),
    onSecondaryContainer = Color.of(ControlDeckTokens.ON_SECONDARY_CONTAINER),
    tertiary = Color.of(ControlDeckTokens.TERTIARY),
    onTertiary = Color.of(ControlDeckTokens.ON_TERTIARY),
    background = Color.of(ControlDeckTokens.DARK_BACKGROUND),
    onBackground = Color.of(ControlDeckTokens.DARK_ON_BACKGROUND),
    surface = Color.of(ControlDeckTokens.DARK_SURFACE),
    onSurface = Color.of(ControlDeckTokens.DARK_ON_SURFACE),
    surfaceVariant = Color.of(ControlDeckTokens.DARK_SURFACE_VARIANT),
    onSurfaceVariant = Color.of(ControlDeckTokens.DARK_ON_SURFACE_VARIANT),
    surfaceContainerLowest = Color.of(ControlDeckTokens.DARK_BACKGROUND),
    surfaceContainerLow = Color.of(ControlDeckTokens.DARK_SURFACE_LOW),
    surfaceContainer = Color.of(ControlDeckTokens.DARK_SURFACE_CONTAINER),
    surfaceContainerHigh = Color.of(ControlDeckTokens.DARK_SURFACE_HIGH),
    surfaceContainerHighest = Color.of(ControlDeckTokens.DARK_SURFACE_HIGHEST),
    error = Color.of(ControlDeckTokens.ERROR),
    onError = Color.of(ControlDeckTokens.ON_ERROR),
    errorContainer = Color.of(ControlDeckTokens.ERROR_CONTAINER),
    onErrorContainer = Color.of(ControlDeckTokens.ON_ERROR_CONTAINER),
    outline = Color.of(ControlDeckTokens.OUTLINE),
    outlineVariant = Color.of(ControlDeckTokens.OUTLINE_VARIANT),
    inverseSurface = Color.of(ControlDeckTokens.INVERSE_SURFACE),
    inverseOnSurface = Color.of(ControlDeckTokens.INVERSE_ON_SURFACE),
)

/** Previously shipped light scheme — intentionally unchanged (out of UX-01 scope). */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF171B36),
    tertiary = Color(0xFF7D5268),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9EC),
    onTertiaryContainer = Color(0xFF31111F),
    background = Color(0xFFFCF8FD),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF8FD),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLow = Color(0xFFF6F2F7),
    surfaceContainer = Color(0xFFF0ECF1),
    surfaceContainerHigh = Color(0xFFEAE7EB),
    outline = Color(0xFF767680),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * TYPE SCALE — tuned for dense, technical data. Rules:
 *  - page headings stay compact (no display sizes in ordinary controls),
 *  - record titles are titleMedium (15sp) so rows stay dense,
 *  - sections are titleSmall (13sp, medium weight) with the primary accent,
 *  - metadata/status use labelMedium/labelSmall,
 *  - numbers use [DeckNumerals.numeric] (tabular figures) where alignment matters.
 * Platform default font family only: no new font dependency (Turkish diacritics
 * are fully covered by the system font).
 */
private val DeckTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

/** Shape hierarchy: restrained corners; pills only where semantics justify them. */
private val DeckShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/** Numeric text style: tabular figures so statistics do not jitter. */
object DeckNumerals {
    val numeric: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )
}

@Composable
fun RadioObserverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = DeckTypography,
        shapes = DeckShapes,
        content = content,
    )
}
