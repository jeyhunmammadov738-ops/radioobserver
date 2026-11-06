package com.example.radioobserver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.radioobserver.ui.theme.ControlDeckTokens
import com.example.radioobserver.ui.theme.DeckDimensions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
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
    val xxl = 32.dp
}

/** Tonal status semantics shared by dashboard pills and detail rows. */
enum class PillKind {
    NEUTRAL,
    ACTIVE,
    ATTENTION,
    ERROR,
    /** UX-01: genuine warning tone (e.g. bounded duration elapsed), distinct from error. */
    WARNING,
    /** UX-01: cached/stale marker (Wi-Fi "Eski"); always paired with visible text. */
    STALE,
}

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
    PillKind.WARNING -> Color(ControlDeckTokens.WARNING_CONTAINER)
    PillKind.STALE -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
fun pillContent(kind: PillKind): Color = when (kind) {
    PillKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    PillKind.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    PillKind.ATTENTION -> MaterialTheme.colorScheme.onSecondaryContainer
    PillKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    PillKind.WARNING -> Color(ControlDeckTokens.ON_WARNING_CONTAINER)
    PillKind.STALE -> MaterialTheme.colorScheme.onSurfaceVariant
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

// ---------------------------------------------------------------------------
// UX-01 shared foundation additions. Every component below is presentation-only,
// stateless where feasible, and never dispatches a session/permission/storage
// action. Existing call sites keep their current APIs (no breaking changes).
// ---------------------------------------------------------------------------

/** Shared dimension accessors backed by the host-testable [DeckDimensions]. */
object Dimensions {
    val touchTarget = DeckDimensions.MIN_TOUCH_TARGET.dp
    val primaryActionHeight = DeckDimensions.PRIMARY_ACTION_HEIGHT.dp
    val compactRowMin = DeckDimensions.COMPACT_ROW_MIN_HEIGHT.dp
    val badgeHeight = DeckDimensions.BADGE_HEIGHT.dp
    val iconSmall = DeckDimensions.ICON_SMALL.dp
    val iconStandard = DeckDimensions.ICON_STANDARD.dp
    val iconLarge = DeckDimensions.ICON_LARGE.dp
    val cardPadding = DeckDimensions.CARD_PADDING.dp
    val sectionGap = DeckDimensions.SECTION_GAP.dp
    val listItemGap = DeckDimensions.LIST_ITEM_GAP.dp
}

/** BLE / Wi-Fi source badge: shape + text differ, never colour alone. */
@Composable
fun RadioSourceBadge(
    radio: com.example.radioobserver.core.radio.model.RadioType,
    modifier: Modifier = Modifier,
) {
    val label = when (radio) {
        com.example.radioobserver.core.radio.model.RadioType.BLE -> "BLE"
        com.example.radioobserver.core.radio.model.RadioType.WIFI -> "Wi-Fi"
    }
    StatusPill(
        text = label,
        kind = if (radio == com.example.radioobserver.core.radio.model.RadioType.BLE) {
            PillKind.ACTIVE
        } else {
            PillKind.NEUTRAL
        },
        modifier = modifier.semantics { contentDescription = "Kaynak: $label" },
    )
}

/**
 * Full-width status banner. [title] carries the meaning in TEXT (colour is only
 * support), [detail] is optional supporting copy, [actionLabel]/[onAction] is an
 * optional explicit user action (never automatic).
 */
@Composable
fun StatusBanner(
    title: String,
    kind: PillKind,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        color = pillContainer(kind),
        contentColor = pillContent(kind),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.bodySmall)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = Dimensions.touchTarget),
                ) { Text(actionLabel) }
            }
        }
    }
}

/** Compact label/value statistic row with tabular numerals. */
@Composable
fun StatisticRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = com.example.radioobserver.ui.theme.DeckNumerals.numeric.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            ),
        )
    }
}

/** Numbered statistic block for dense analytical summaries. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = com.example.radioobserver.ui.theme.DeckNumerals.numeric.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section heading with optional description and optional trailing action slot. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Visual hierarchy of an action: PRIMARY uses filled, SECONDARY tonal, DESTRUCTIVE error. */
enum class ActionEmphasis { PRIMARY, SECONDARY, DESTRUCTIVE }

/**
 * One-row action group with clear emphasis. [enabled] is forwarded verbatim;
 * callbacks are invoked only by explicit taps (never from composition).
 */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
