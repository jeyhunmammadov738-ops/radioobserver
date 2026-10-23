package com.example.radioobserver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Post-roadmap professional UI iteration (developer-approved, 2026-10-23):
 * ONE deliberate Material 3 visual system for the whole app — calm neutral
 * surfaces with a controlled indigo/violet accent, coherent light and dark
 * schemes. No new dependency, no dynamic color (a stable brand identity on
 * every device), no gradients/heavy shadows/flashy animation. Contrast pairs
 * follow Material 3 tonal roles; the schemes are compile-time constants only.
 *
 * Source-level design only: real on-device contrast rendering, dark mode and
 * large-font presentation are UNVERIFIED until the Xiaomi retest.
 */
private val IndigoPrimary = Color(0xFF4355B9)
private val IndigoOnPrimary = Color(0xFFFFFFFF)
private val IndigoPrimaryContainer = Color(0xFFDEE0FF)
private val IndigoOnPrimaryContainer = Color(0xFF00105C)
private val IndigoSecondary = Color(0xFF5A5D72)

private val LightScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = IndigoSecondary,
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

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF102478),
    primaryContainer = Color(0xFF2B3CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C4DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFEFB8CB),
    onTertiary = Color(0xFF492538),
    tertiaryContainer = Color(0xFF623B4F),
    onTertiaryContainer = Color(0xFFFFD9EC),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F27),
    surfaceContainerHigh = Color(0xFF2A2A31),
    outline = Color(0xFF90909A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun RadioObserverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
