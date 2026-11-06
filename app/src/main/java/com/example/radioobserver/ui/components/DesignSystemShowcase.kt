package com.example.radioobserver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.ui.theme.DeckNumerals
import com.example.radioobserver.ui.theme.RadioObserverTheme

/**
 * UX-01 PREVIEW DEFINITIONS (source-level, deterministic synthetic data).
 *
 * The project does not depend on `ui-tooling-preview`, and UX-01 is not authorized
 * to add dependencies, so these are plain composables intended to be rendered in
 * Android Studio via a temporary preview wrapper (or inspected in a test activity).
 * They are NOT screenshots and no on-device rendering is claimed. Every sample uses
 * fake labels only: no real SSIDs, MAC/BSSID values, record ids or device data.
 */
@Composable
fun DesignSystemShowcase() {
    RadioObserverTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Kontrol paneli", style = MaterialTheme.typography.headlineSmall)
                Text("Gözlem", style = MaterialTheme.typography.titleMedium)

                // Surface hierarchy
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader("Oturum", description = "Sınırlı süreli, ön planda")
                        StatisticRow("Bluetooth", "Aktif")
                        StatisticRow("Kayıt", "12")
                    }
                }

                // Radio source badges
                ActionRow {
                    RadioSourceBadge(RadioType.BLE)
                    RadioSourceBadge(RadioType.WIFI)
                    StatusPill("Eski", PillKind.STALE)
                }

                // Status banners
                StatusBanner("Hazır", PillKind.NEUTRAL, detail = "Oturum başlatılmadı.")
                StatusBanner("Gözlem sürüyor", PillKind.ACTIVE, detail = "Kalan süre: 08:41")
                StatusBanner("İzin gerekli", PillKind.ATTENTION, detail = "Yakındaki cihazlar izni verilmedi.")
                StatusBanner("Hata", PillKind.ERROR, detail = "Oturum beklenmedik şekilde durdu.")
                StatusBanner("Süre doldu", PillKind.WARNING, detail = "Oturum sınırlı süre sonunda kapandı.")

                // Action hierarchy
                ActionRow {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Başlat") }
                    OutlinedButton(onClick = {}) { Text("Ayarlar") }
                }

                // Statistics
                ActionRow {
                    StatBlock("12", "Kayıt")
                    StatBlock("7", "Bluetooth")
                    StatBlock("5", "Wi-Fi")
                }
                Text("12", style = DeckNumerals.numeric)

                EmptyState("Bu süzgeçle eşleşen kayıt yok.")
            }
        }
    }
}
