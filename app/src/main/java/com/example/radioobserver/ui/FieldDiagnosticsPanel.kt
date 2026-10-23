package com.example.radioobserver.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radioobserver.diag.FieldDiagnostics

/**
 * Third field iteration (2026-10-23): DEBUG-ONLY on-screen diagnostics disclosure.
 * Requires NO ADB/PC/root/logging app: the user expands it on the phone and
 * screenshots the bounded typed chronology. Never rendered in release builds
 * (caller gates on [FieldDiagnostics.isDebugBuild]).
 *
 * HONESTY & PRIVACY:
 * - Labeled "DEBUG ONLY - gozlem sonucu degildir"; panel data is NOT observation data.
 * - Only typed booleans/class names; NO MAC/BSSID/payload/identifiers/location/
 *   observation content; nothing persisted, transmitted or auto-shared (in-RAM ring).
 * - The panel NEVER starts a scan, requests permissions, retries or rechecks; it has
 *   no callbacks into control flow. It only reads typed state supplied to it.
 */
@Composable
fun FieldDiagnosticsPanel(
    orchestratorEligible: Boolean,
    sourceEligible: Boolean?,
    sessionSummary: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val rows = FieldDiagnostics.snapshotRows()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = if (expanded) "Tanılama ▾" else "Tanılama ▸ (${FieldDiagnostics.BUILD_MARKER})",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "${FieldDiagnostics.BUILD_MARKER} — DEBUG ONLY — gözlem sonucu değildir",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Yaşam döngüsü: ön plan uygunluğu (orkestratör)=" +
                            orchestratorEligible +
                            (sourceEligible?.let { " | kaynak=$it" } ?: " | kaynak=bilinmiyor"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = sessionSummary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    HorizontalDivider()
                    for (row in rows) {
                        Text(
                            text = row,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}
