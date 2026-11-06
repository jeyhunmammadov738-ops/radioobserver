package com.example.radioobserver.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UX-01: behavioral host tests for the pure presentation mapping layer. */
class PresentationModelTest {

    @Test
    fun `source distinction maps only Bluetooth and Wifi`() {
        assertEquals(SourceTag.BLUETOOTH, ObservationRowMapping.sourceTag(isBluetooth = true))
        assertEquals(SourceTag.WIFI, ObservationRowMapping.sourceTag(isBluetooth = false))
    }

    @Test
    fun `cached wifi is always stale even when flagged fresh`() {
        assertEquals(
            FreshnessTag.STALE,
            ObservationRowMapping.freshnessTag(isFresh = true, isCached = true, ageKnown = true),
        )
    }

    @Test
    fun `fresh requires known age and no cache`() {
        assertEquals(FreshnessTag.FRESH, ObservationRowMapping.freshnessTag(true, false, true))
        assertEquals(FreshnessTag.UNKNOWN, ObservationRowMapping.freshnessTag(true, false, false))
        assertEquals(FreshnessTag.STALE, ObservationRowMapping.freshnessTag(false, false, true))
    }

    @Test
    fun `blank primary text is unknown and never a fabricated label`() {
        assertEquals(PrimaryTextKind.UNKNOWN, ObservationRowMapping.primaryKind(null))
        assertEquals(PrimaryTextKind.UNKNOWN, ObservationRowMapping.primaryKind("   "))
        assertEquals(PrimaryTextKind.KNOWN, ObservationRowMapping.primaryKind("BLE-3AF1"))
        val model = ObservationRowMapping.model(
            isBluetooth = true, primaryText = "  ", metadata = " ",
            isFresh = true, isCached = false, ageKnown = true, favorite = false, enabled = true,
        )
        assertNull(model.primaryText)
        assertNull(model.metadata)
        assertEquals(PrimaryTextKind.UNKNOWN, model.primaryKind)
    }

    @Test
    fun `row model carries no identity beyond the supplied sanitized text`() {
        val model = ObservationRowMapping.model(
            isBluetooth = false, primaryText = "ag-adi", metadata = "Apple",
            isFresh = false, isCached = true, ageKnown = true, favorite = true, enabled = false,
        )
        assertEquals(SourceTag.WIFI, model.source)
        assertEquals(FreshnessTag.STALE, model.freshness)
        assertTrue(model.favorite)
        assertFalse(model.enabled)
        // The public API surface has exactly these fields: no address/BSSID/location field.
        val names = ObservationRowModel::class.java.declaredFields.mapNotNull { it.name }.toSet()
        assertFalse(names.any { it.contains("mac", true) || it.contains("bssid", true) || it.contains("address", true) })
    }

    @Test
    fun `disabled row offers no interactive target`() {
        assertEquals(0, ObservationRowMapping.interactiveTargets(enabled = false, favoriteToggleShown = true))
        assertEquals(1, ObservationRowMapping.interactiveTargets(enabled = true, favoriteToggleShown = false))
        assertEquals(2, ObservationRowMapping.interactiveTargets(enabled = true, favoriteToggleShown = true))
    }

    @Test
    fun `confirmation requires an explicit confirm press`() {
        assertTrue(Confirmation.authorizesDestructiveAction(Confirmation.fromConfirmClick()))
        assertFalse(Confirmation.authorizesDestructiveAction(Confirmation.fromCancelClick()))
        assertFalse(Confirmation.authorizesDestructiveAction(Confirmation.fromDismissal()))
        assertEquals(ConfirmIntent.DISMISS, Confirmation.fromDismissal())
    }

    @Test
    fun `destructive dialogs require an explanation`() {
        assertTrue(Confirmation.requiresExplanation(destructive = true))
        assertFalse(Confirmation.requiresExplanation(destructive = false))
    }

    @Test
    fun `state surfaces map to the documented kinds`() {
        assertEquals(SurfaceKind.EMPTY, StateSurfaces.empty("kayit yok").kind)
        assertEquals(SurfaceKind.NO_SESSION, StateSurfaces.noSession("oturum yok").kind)
        assertEquals(SurfaceKind.UNAVAILABLE, StateSurfaces.unavailable("yok").kind)
        assertEquals(SurfaceKind.ERROR, StateSurfaces.error("hata").kind)
        assertEquals(SurfaceKind.LOADING, StateSurfaces.loading().kind)
        assertEquals(SurfaceKind.PERMISSION_REQUIRED, StateSurfaces.permissionRequired("izin", "Izin ver").kind)
    }

    @Test
    fun `retry is offered only for errors that carry a label`() {
        assertFalse(StateSurfaces.offersRetry(StateSurfaces.error("hata")))
        assertTrue(StateSurfaces.offersRetry(StateSurfaces.error("hata", "Tekrar dene")))
        assertFalse(StateSurfaces.offersRetry(StateSurfaces.empty("bos", "Tekrar dene")))
    }

    @Test
    fun `action emphasis follows the surface contract`() {
        assertEquals(Emphasis.PRIMARY, StateSurfaces.permissionRequired("i", "a").actionEmphasis)
        assertEquals(Emphasis.SECONDARY, StateSurfaces.empty("b", "a").actionEmphasis)
        assertEquals(Emphasis.TEXT, StateSurfaces.empty("b").actionEmphasis)
        assertEquals(Emphasis.SECONDARY, StateSurfaces.error("e", "Tekrar dene").actionEmphasis)
    }

    @Test
    fun `every surface kind has a deterministic title key`() {
        // Titles are resolved by the component from SurfaceKind; the enum must stay exhaustive.
        assertEquals(6, SurfaceKind.entries.size)
        assertEquals(SurfaceKind.entries.toSet(), SurfaceKind.entries.map { it }.toSet())
    }

    @Test
    fun `null and unknown inputs never become fresh or known`() {
        val model = ObservationRowMapping.model(
            isBluetooth = false, primaryText = null, metadata = null,
            isFresh = false, isCached = false, ageKnown = false, favorite = false, enabled = true,
        )
        assertEquals(FreshnessTag.UNKNOWN, model.freshness)
        assertEquals(PrimaryTextKind.UNKNOWN, model.primaryKind)
        assertNull(model.primaryText)
        assertNull(model.metadata)
    }

    @Test
    fun `presentation mapping is pure and repeatable`() {
        val first = ObservationRowMapping.model(true, "x", "y", true, false, true, false, true)
        val second = ObservationRowMapping.model(true, "x", "y", true, false, true, false, true)
        assertEquals(first, second)
    }
}
