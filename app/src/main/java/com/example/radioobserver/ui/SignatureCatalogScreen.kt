package com.example.radioobserver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.Spacing
import com.example.radioobserver.ui.components.StatusPill
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.signature.SignatureCategory
import com.example.radioobserver.core.radio.signature.SignatureCatalog
import com.example.radioobserver.core.radio.signature.UserRuleSelector
import com.example.radioobserver.core.radio.signature.UserRuleTypeToken
import com.example.radioobserver.core.radio.signature.UserSignatureRule
import com.example.radioobserver.core.radio.signature.UserRuleError

/**
 * WORKSTREAM 03B — "Sınıflandırma kataloğu" management screen.
 *
 * Design constraints honored: NO fifth bottom tab (reached from Ayarlar); built-in
 * rules are shown READ-ONLY and can never be edited or deleted here; user rules are
 * bounded (<= 30), categorical-only, with enable/disable, edit, delete-with-
 * confirmation, honest validation errors and the explicit identity caution. Pure
 * management state — nothing here starts/stops observation, touches permissions,
 * or writes anything without an explicit user action (the callbacks map 1:1 to user
 * gestures; the repository never auto-persists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCatalogScreen(
    userRules: List<UserSignatureRule>,
    onCreateRule: (String, UserRuleSelector, SignatureCategory) -> UserRuleError?,
    onUpdateRule: (String, String, UserRuleSelector, SignatureCategory) -> UserRuleError?,
    onSetEnabled: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<UserSignatureRule?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<UserSignatureRule?>(null) }
    val identityCaution = stringResource(R.string.signature_catalog_identity_caution)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.signature_catalog_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.feed_back))
        }
        Text(
            text = stringResource(R.string.signature_catalog_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = identityCaution,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(stringResource(R.string.signature_catalog_builtin_section))
        SignatureCatalog.RULES.forEach { rule ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatusPill(
                            text = stringResource(R.string.signature_catalog_builtin_badge),
                            kind = com.example.radioobserver.ui.components.PillKind.NEUTRAL,
                        )
                    }
                    Text(text = categoryLabel(rule.category), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(
                            R.string.signature_catalog_rule_format, rule.id, SignatureCatalog.VERSION),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rule.rationale.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.signature_catalog_user_section))
        if (userRules.isEmpty()) {
            Text(
                text = stringResource(R.string.signature_catalog_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        userRules.forEach { rule ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            StatusPill(
                                text = stringResource(R.string.signature_catalog_user_badge),
                                kind = com.example.radioobserver.ui.components.PillKind.NEUTRAL,
                            )
                            Text(text = rule.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = selectorLabel(rule.selector),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = categoryLabel(rule.category),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            val toggleA11y = stringResource(R.string.signature_catalog_enabled_a11y)
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onSetEnabled(rule.id, it) },
                                modifier = Modifier.semantics { contentDescription = toggleA11y },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                TextButton(
                                    onClick = { editing = rule; dialogOpen = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.signature_catalog_edit)) }
                                TextButton(
                                    onClick = { deleteCandidate = rule },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.signature_catalog_delete)) }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { editing = null; dialogOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.signature_catalog_create))
        }
    }

    if (dialogOpen) {
        RuleEditorDialog(
            existing = editing,
            onCreate = onCreateRule,
            onUpdate = onUpdateRule,
            onDismiss = { dialogOpen = false; editing = null },
        )
    }

    deleteCandidate?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.signature_catalog_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.signature_catalog_delete_confirm_text, rule.title),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteRule(rule.id); deleteCandidate = null }) {
                    Text(stringResource(R.string.signature_catalog_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.signature_catalog_cancel))
                }
            },
        )
    }
}

@Composable
private fun RuleEditorDialog(
    existing: UserSignatureRule?,
    onCreate: (String, UserRuleSelector, SignatureCategory) -> UserRuleError?,
    onUpdate: (String, String, UserRuleSelector, SignatureCategory) -> UserRuleError?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var radio by remember { mutableStateOf(existing?.selector?.radio ?: RadioType.BLE) }
    var token by remember { mutableStateOf(existing?.selector?.typeToken) }
    var confidence by remember { mutableStateOf(existing?.selector?.confidence) }
    var category by remember { mutableStateOf(existing?.category ?: SignatureCategory.IBEACON_FAMILY) }
    var error by remember { mutableStateOf<UserRuleError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.signature_catalog_dialog_title_create
                    else R.string.signature_catalog_dialog_title_edit),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.signature_catalog_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.signature_catalog_field_radio),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = radio == RadioType.BLE,
                        onClick = { radio = RadioType.BLE },
                        label = { Text(stringResource(R.string.signature_catalog_selector_radio_ble)) },
                    )
                    FilterChip(
                        selected = radio == RadioType.WIFI,
                        onClick = { radio = RadioType.WIFI },
                        label = { Text(stringResource(R.string.signature_catalog_selector_radio_wifi)) },
                    )
                }
                Text(
                    text = stringResource(R.string.signature_catalog_field_token),
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterChip(
                        selected = token == null,
                        onClick = { token = null },
                        label = { Text(stringResource(R.string.signature_catalog_selector_token_any)) },
                    )
                    UserRuleTypeToken.entries.forEach { t ->
                        FilterChip(
                            selected = token == t,
                            onClick = { token = t },
                            label = { Text(t.label) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.signature_catalog_field_confidence),
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterChip(
                        selected = confidence == null,
                        onClick = { confidence = null },
                        label = { Text(stringResource(R.string.signature_catalog_selector_conf_any)) },
                    )
                    ObservationConfidence.entries.forEach { c ->
                        FilterChip(
                            selected = confidence == c,
                            onClick = { confidence = c },
                            label = { Text(c.name) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.signature_catalog_field_category),
                    style = MaterialTheme.typography.labelMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SignatureCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(categoryLabel(cat)) },
                        )
                    }
                }
                error?.let { err ->
                    Text(
                        text = when (err) {
                            UserRuleError.INVALID_TITLE -> stringResource(
                                R.string.signature_catalog_invalid_title)
                            UserRuleError.LIMIT_REACHED -> stringResource(
                                R.string.signature_catalog_limit_reached, 30)
                            UserRuleError.NOT_FOUND -> stringResource(
                                R.string.signature_catalog_invalid_title)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selector = UserRuleSelector(radio, token, confidence)
                    val err = if (existing == null) {
                        onCreate(title, selector, category)
                    } else {
                        onUpdate(existing.id, title, selector, category)
                    }
                    if (err == null) onDismiss() else error = err
                },
            ) { Text(stringResource(R.string.signature_catalog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.signature_catalog_cancel))
            }
        },
    )
}

@Composable
private fun selectorLabel(selector: UserRuleSelector): String {
    val radio = when (selector.radio) {
        RadioType.BLE -> stringResource(R.string.signature_catalog_selector_radio_ble)
        RadioType.WIFI -> stringResource(R.string.signature_catalog_selector_radio_wifi)
        null -> stringResource(R.string.signature_catalog_selector_radio_any)
    }
    val token = selector.typeToken?.label
        ?: stringResource(R.string.signature_catalog_selector_token_any)
    val conf = selector.confidence?.name
        ?: stringResource(R.string.signature_catalog_selector_conf_any)
    return stringResource(R.string.signature_catalog_selector_format, "$radio + $token + $conf")
}

@Composable
private fun categoryLabel(category: SignatureCategory): String = when (category) {
    SignatureCategory.ACCESS_POINT -> stringResource(R.string.signature_category_access_point)
    SignatureCategory.IBEACON_FAMILY -> stringResource(R.string.signature_category_ibeacon_family)
    SignatureCategory.FIND_MY_FAMILY -> stringResource(R.string.signature_category_find_my_family)
}
