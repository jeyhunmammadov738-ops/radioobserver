package com.example.radioobserver.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Post-roadmap UI iteration (2026-10-23): JVM tests for the DEBUG-only, hidden-by-
 * default FieldDiag-04 visibility policy.
 *
 *  - Cold start: ALWAYS locked and hidden (a new gate instance starts fresh).
 *  - Unlock: exactly SEVEN intentional taps on the version label.
 *  - Visibility: debug build AND unlocked AND explicit user toggle — nothing else.
 *  - No persistence: recreation (a new instance) re-locks and re-hides.
 */
class DeveloperDiagGateTest {

    @Test
    fun `cold start is locked`() {
        val gate = DeveloperDiagGate()
        assertFalse(gate.isUnlocked)
        // Even if a stale toggle value survived, visibility must stay off.
        assertFalse(DeveloperDiagGate.isVisible(isDebugBuild = true, unlocked = false, userToggle = true))
    }

    @Test
    fun `six taps do not unlock`() {
        val gate = DeveloperDiagGate()
        repeat(6) { assertFalse(gate.onVersionTap()) }
        assertFalse(gate.isUnlocked)
    }

    @Test
    fun `the seventh tap completes the unlock exactly once`() {
        val gate = DeveloperDiagGate()
        repeat(6) { assertFalse(gate.onVersionTap()) }
        assertTrue(gate.onVersionTap())
        assertTrue(gate.isUnlocked)
        // Already unlocked: further taps report nothing new.
        assertFalse(gate.onVersionTap())
        assertTrue(gate.isUnlocked)
    }

    @Test
    fun `tap reset discards an incomplete sequence`() {
        val gate = DeveloperDiagGate()
        repeat(5) { gate.onVersionTap() }
        gate.resetTaps()
        // After the reset the SAME seven deliberate taps are required again.
        repeat(6) { assertFalse(gate.onVersionTap()) }
        assertTrue(gate.onVersionTap())
    }

    @Test
    fun `tap reset never re-locks an already unlocked gate`() {
        val gate = DeveloperDiagGate()
        repeat(7) { gate.onVersionTap() }
        gate.resetTaps()
        assertTrue(gate.isUnlocked)
    }

    @Test
    fun `process recreation re-locks and re-hides (nothing persisted)`() {
        val old = DeveloperDiagGate()
        repeat(7) { old.onVersionTap() }
        val recreated = DeveloperDiagGate()
        assertFalse(recreated.isUnlocked)
        assertFalse(
            DeveloperDiagGate.isVisible(
                isDebugBuild = true, unlocked = recreated.isUnlocked, userToggle = true))
    }

    @Test
    fun `visibility requires debug build AND unlock AND explicit user toggle`() {
        val gate = DeveloperDiagGate()
        repeat(7) { gate.onVersionTap() }
        assertTrue(DeveloperDiagGate.isVisible(true, gate.isUnlocked, true))
        // Release build: never visible regardless of unlock/toggle.
        assertFalse(DeveloperDiagGate.isVisible(false, gate.isUnlocked, true))
        // Unlock without the explicit switch: still hidden.
        assertFalse(DeveloperDiagGate.isVisible(true, gate.isUnlocked, false))
        // Switch on without unlock: still hidden.
        assertFalse(DeveloperDiagGate.isVisible(true, false, true))
    }

    @Test
    fun `required tap count is seven`() {
        assertEquals(7, DeveloperDiagGate.REQUIRED_TAPS)
    }
}
