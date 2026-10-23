package com.example.radioobserver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.model.RadioType

/**
 * PREMIUM UI/UX V2 design system (post-roadmap, developer-approved 2026-10-23):
 * ONE coherent Material 3 component language shared by every screen.
 *
 * Tokens: documented 4/8dp spacing scale ([Spacing]), theme shape roles, tonal
 * elevation only (no shadows), status colors derived from the existing indigo/violet
 * scheme. Every component here is a PRESENTATION-only composable: no session,
 * permission, scanning or storage dispatch. Color NEVER carries meaning alone —
 * every pill/section text is also present in semantics strings.
 */
object Spacing {
    /** Documented rhythm: 4/8dp base scale with named steps. */
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Tonal status semantics shared by dashboard pills and detail rows. */
enum class PillKind { NEUTRAL, ACTIVE, ATTENTION, ERROR }

/** Pure, host-testable status→pill mapping (no composable context). */
object PillStyle {
    fun kindFor(status: com.example.radioobserver.core.radio.ui.RadioSessionUiStatus): PillKind =
        when (status) {
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.OBSERVING -> PillKind.ACTIVE
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.CHECKING,
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.STOPPING,
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.PERMISSION_REQUIRED,
            -> PillKind.ATTENTION
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.BLOCKED,
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.ERROR,
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.TIMED_OUT,
            -> PillKind.ERROR
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.IDLE,
            com.example.radioobserver.core.radio.ui.RadioSessionUiStatus.STOPPED,
            -> PillKind.NEUTRAL
        }
}

@Composable
fun pillContainer(kind: PillKind): Color = when (kind) {
    PillKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    PillKind.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
    PillKind.ATTENTION -> MaterialTheme.colorScheme.secondaryContainer
    PillKind.ERROR -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun pillContent(kind: PillKind): Color = when (kind) {
    PillKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PillKind.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    PillKind.ATTENTION -> MaterialTheme.colorScheme.onSecondaryContainer
    PillKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

/**
 * Compact tonal status pill (labelMedium). The WORD carries the meaning; the tonal
 * color only supports it (never color-alone semantics).
 */
@Composable
fun StatusPill(text: String, kind: PillKind, modifier: Modifier = Modifier) {
    Surface(
        color = pillContainer(kind),
        contentColor = pillContent(kind),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** Section heading inside cards/lists; consistent heading semantics. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * One key/value row with a DELIBERATE hierarchy: label (bodySmall, secondary color)
 * left, value (bodyMedium, primary color) right, both wrapping over multiple lines.
 * NO hard-coded heights; long Turkish values never clip.
 */
@Composable
fun CompactInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f),
        )
    }
}

/**
 * Tonal circular source icon for list rows and cards. BLE/Wi-Fi glyphs from bundled
 * vector resources (NO new dependency); the radio type also appears as text in
 * semantics, so the icon is never the only meaning carrier.
 */
@Composable
fun SourceIcon(radio: RadioType, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val res = when (radio) {
        RadioType.BLE -> R.drawable.ic_radio_bluetooth
        RadioType.WIFI -> R.drawable.ic_radio_wifi
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp),
        )
    }
}

/** Restrained empty state: short truthful copy, generous padding, no icon noise. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(Spacing.lg),
    )
}
