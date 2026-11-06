package com.example.radioobserver.ui.theme

import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.ui.components.PillKind
import com.example.radioobserver.ui.components.PillStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UX-01 regression: the design-system foundation must NOT change the historical
 * status-to-visual mapping (a functional/semantic contract, not only cosmetics).
 */
class PillMappingRegressionTest {

    @Test
    fun `historical status to pill mapping is unchanged for every status`() {
        assertEquals(PillKind.ACTIVE, PillStyle.kindFor(RadioSessionUiStatus.OBSERVING))
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.CHECKING))
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.STOPPING))
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.PERMISSION_REQUIRED))
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.BLOCKED))
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.ERROR))
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.TIMED_OUT))
        assertEquals(PillKind.NEUTRAL, PillStyle.kindFor(RadioSessionUiStatus.IDLE))
        assertEquals(PillKind.NEUTRAL, PillStyle.kindFor(RadioSessionUiStatus.STOPPED))
    }
}
