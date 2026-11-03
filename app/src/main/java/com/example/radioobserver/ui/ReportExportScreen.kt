package com.example.radioobserver.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.export.ExportFormat
import com.example.radioobserver.core.radio.export.ExportOptions
import com.example.radioobserver.core.radio.export.ExportResult
import com.example.radioobserver.core.radio.export.ReportBuilder
import com.example.radioobserver.core.radio.history.AnalyticalGroup
import com.example.radioobserver.core.radio.history.HistoryAnnotation
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WORKSTREAM 06 — minimal functional export screen (no visual redesign).
 *
 * PURE SAFETY NOTES:
 * - Reads ONLY the already-saved History snapshot + WS05 group metadata that the
 *   caller passes in. Opening this screen dispatches nothing: no scan, no
 *   permission request, no recording consent change, no History mutation.
 * - Sensitive fields (network names, user labels/notes) are OFF by default and
 *   require an explicit switch. A withheld name can never be reconstructed.
 * - Generation runs off the main thread and writes through the user-chosen SAF
 *   destination only; cancel leaves application data untouched.
 */
private enum class ReportScope { GROUP, COMPARE, ALL }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportExportScreen(
    groups: List<AnalyticalGroup>,
    records: List<HistoryRecord>,
    annotations: Map<String, HistoryAnnotation>,
    preselectedGroupId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedScope by remember(preselectedGroupId) {
        mutableStateOf(
            if (preselectedGroupId != null && groups.any { it.id == preselectedGroupId })
                ReportScope.GROUP else ReportScope.GROUP,
        )
    }
    var groupId by remember(preselectedGroupId) {
        mutableStateOf(preselectedGroupId ?: groups.firstOrNull()?.id)
    }
    var leftId by remember { mutableStateOf(groups.firstOrNull()?.id) }
    var rightId by remember { mutableStateOf(groups.drop(1).firstOrNull()?.id) }
    var format by remember { mutableStateOf(ExportFormat.CSV) }
    var includeNames by remember { mutableStateOf(false) }
    var includeAnnotations by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // The generated content waits here until the user picks a destination.
    var pending by remember { mutableStateOf<ExportResult?>(null) }

    val savedText = stringResource(R.string.report_saved, pending?.recordCount ?: 0)
    val cancelledText = stringResource(R.string.report_cancelled)
    val failedText = stringResource(R.string.report_failed)

    fun writeTo(uri: android.net.Uri?) {
        val result = pending
        pending = null
        if (uri == null) {
            status = cancelledText
            error = null
            return
        }
        if (result == null) {
            error = failedText
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: return@withContext false
                    stream.use { it.write(result.content.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (t: Throwable) {
                    // Honest failure state; nothing else is touched.
                    false
                }
            }
            if (ok) {
                status = savedText
                error = null
            } else {
                status = null
                error = failedText
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
    ) { uri -> writeTo(uri) }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType),
    ) { uri -> writeTo(uri) }

    val groupsById = groups.associateBy { it.id }
    val validIds = remember(records) { records.map { it.id }.toSet() }
    fun membersOf(id: String?): List<HistoryRecord> {
        val group = id?.let { groupsById[it] } ?: return emptyList()
        val wanted = group.recordIds.toSet()
        return records.filter { it.id in wanted }
    }

    val currentCount = when (selectedScope) {
        ReportScope.GROUP -> membersOf(groupId).size
        ReportScope.COMPARE -> {
            val l = membersOf(leftId).map { it.id }.toSet()
            val r = membersOf(rightId).map { it.id }.toSet()
            (l + r).size
        }
        ReportScope.ALL -> records.size
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.report_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.feed_back)) }
            }

            SectionHeader(stringResource(R.string.report_scope_section))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = selectedScope == ReportScope.GROUP,
                    onClick = { selectedScope = ReportScope.GROUP },
                    label = { Text(stringResource(R.string.report_scope_group)) },
                )
                FilterChip(
                    selected = selectedScope == ReportScope.COMPARE,
                    onClick = { selectedScope = ReportScope.COMPARE },
                    label = { Text(stringResource(R.string.report_scope_compare)) },
                )
                FilterChip(
                    selected = selectedScope == ReportScope.ALL,
                    onClick = { confirmAll = true },
                    label = { Text(stringResource(R.string.report_scope_all)) },
                )
            }

            when (selectedScope) {
                ReportScope.GROUP -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = groupId == group.id,
                            onClick = { groupId = group.id },
                            label = { Text(group.name) },
                        )
                    }
                }
                ReportScope.COMPARE -> Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.report_group_a),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = leftId == group.id,
                                onClick = { leftId = group.id },
                                label = { Text(group.name) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.report_group_b),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = rightId == group.id,
                                onClick = { rightId = group.id },
                                label = { Text(group.name) },
                            )
                        }
                    }
                }
                ReportScope.ALL -> Unit
            }

            Text(
                text = stringResource(R.string.report_count, currentCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            SectionHeader(stringResource(R.string.report_format_section))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = format == ExportFormat.CSV,
                    onClick = { format = ExportFormat.CSV },
                    label = { Text(stringResource(R.string.report_format_csv)) },
                )
                FilterChip(
                    selected = format == ExportFormat.JSON,
                    onClick = { format = ExportFormat.JSON },
                    label = { Text(stringResource(R.string.report_format_json)) },
                )
            }

            SectionHeader(stringResource(R.string.report_fields_section))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.report_include_names),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = includeNames, onCheckedChange = { includeNames = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.report_include_annotations),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = includeAnnotations,
                    onCheckedChange = { includeAnnotations = it },
                )
            }
            Text(
                text = stringResource(R.string.report_names_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.report_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.report_external_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val compareInvalid = selectedScope == ReportScope.COMPARE &&
                (leftId == null || rightId == null || leftId == rightId)
            if (compareInvalid) {
                Text(
                    text = stringResource(R.string.report_pick_groups),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (currentCount == 0 && selectedScope != ReportScope.COMPARE) {
                Text(
                    text = stringResource(R.string.report_empty_scope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = {
                    status = null
                    error = null
                    val options = ExportOptions(
                        includeNames = includeNames,
                        includeUserAnnotations = includeAnnotations,
                    )
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val nowMs = System.currentTimeMillis()
                                when (selectedScope) {
                                    ReportScope.GROUP -> {
                                        val group = groupId?.let { groupsById[it] }
                                        if (group == null) null else
                                            ReportBuilder.groupSummaryReport(
                                                group.name, group.recordIds, records, annotations,
                                                options, format, nowMs,
                                            )
                                    }
                                    ReportScope.COMPARE -> {
                                        val left = leftId?.let { groupsById[it] }
                                        val right = rightId?.let { groupsById[it] }
                                        if (left == null || right == null || left.id == right.id) null
                                        else ReportBuilder.comparisonReport(
                                            left.name, left.recordIds, right.name, right.recordIds,
                                            records, options, format, nowMs,
                                        )
                                    }
                                    ReportScope.ALL -> ReportBuilder.recordsReport(
                                        records, annotations, options, format,
                                        System.currentTimeMillis(),
                                    )
                                }
                            } catch (t: Throwable) {
                                null
                            }
                        }
                        if (result == null) {
                            error = failedText
                        } else {
                            pending = result
                            when (format) {
                                ExportFormat.CSV -> csvLauncher.launch(result.suggestedFileName)
                                ExportFormat.JSON -> jsonLauncher.launch(result.suggestedFileName)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.report_save)) }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.report_scope_all)) },
            text = { Text(stringResource(R.string.report_scope_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    selectedScope = ReportScope.ALL
                    confirmAll = false
                }) { Text(stringResource(R.string.value_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
