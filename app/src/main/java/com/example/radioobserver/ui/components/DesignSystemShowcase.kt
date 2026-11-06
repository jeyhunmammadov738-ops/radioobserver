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

/**
 * Preview definitions for the UX-01 completion components. Synthetic Turkish labels only;
 * no device data, no scanning. Rendered only in a preview host (source-level definitions).
 */
@Composable
fun CompactRowPreviewSample() {
    val fresh = ObservationRowMapping.model(
        isBluetooth = true, primaryText = "BLE-4C21", metadata = "Apple · bilinen üretici",
        isFresh = true, isCached = false, ageKnown = true, favorite = true, enabled = true,
    )
    val stale = ObservationRowMapping.model(
        isBluetooth = false, primaryText = null, metadata = "Önbellek sonucu",
        isFresh = true, isCached = true, ageKnown = true, favorite = false, enabled = false,
    )
    RadioObserverTheme {
        Surface {
            Column {
                CompactObservationRow(model = fresh, unknownLabel = "Ad bilinmiyor", onOpen = {}, onToggleFavorite = {})
                CompactObservationRow(model = stale, unknownLabel = "Ad bilinmiyor", onOpen = {})
            }
        }
    }
}

@Composable
fun StateSurfacePreviewSample() {
    RadioObserverTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StateSurface(model = StateSurfaces.empty("Bu görünümde kayıt yok."))
                StateSurface(model = StateSurfaces.permissionRequired("Tarama için izin gerekli.", "İzin ver"))
                StateSurface(model = StateSurfaces.error("Okuma başarısız.", "Tekrar dene"))
                StateSurface(model = StateSurfaces.loading())
            }
        }
    }
}

@Composable
fun ConfirmDialogPreviewSample() {
    RadioObserverTheme {
        ConfirmActionDialog(
            title = "Kaydı sil",
            explanation = "Bu kayıt kalıcı olarak silinecek. Geri alınamaz.",
            confirmLabel = "Sil",
            cancelLabel = "Vazgeç",
            onConfirm = {}, onCancel = {}, onDismissRequest = {},
            destructive = true,
        )
    }
}
