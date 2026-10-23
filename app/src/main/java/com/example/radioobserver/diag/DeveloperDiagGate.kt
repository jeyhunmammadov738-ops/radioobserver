package com.example.radioobserver.diag

/**
 * Post-roadmap UI iteration (developer-approved, 2026-10-23): the PURE, host-testable
 * policy for the DEBUG-only, hidden-by-default FieldDiag-04 visibility.
 *
 * - Cold start: diagnostics are ALWAYS hidden (a new instance starts locked+hidden).
 * - Unlock: seven taps on the version label in Ayarlar → Uygulama hakkında.
 * - Visibility: the panel shows ONLY when the build is debuggable AND the developer
 *   section is unlocked AND the user explicitly turned it on.
 * - NOTHING is persisted: process recreation re-locks and re-hides; the diagnostic
 *   ring itself stays in-RAM-only (see FieldDiagnostics).
 */
class DeveloperDiagGate(
    private val requiredTaps: Int = REQUIRED_TAPS,
) {
    private var taps = 0
    private var unlocked = false

    val isUnlocked: Boolean get() = unlocked

    /** Version-label tap. Returns true on the tap that completes the unlock. */
    fun onVersionTap(): Boolean {
        if (unlocked) return false
        taps += 1
        if (taps >= requiredTaps) {
            unlocked = true
            return true
        }
        return false
    }

    /** Reset the tap sequence (e.g. when navigating away mid-sequence). */
    fun resetTaps() {
        if (!unlocked) taps = 0
    }

    companion object {
        const val REQUIRED_TAPS = 7

        /** The single visibility rule; pure and JVM-tested. */
        fun isVisible(isDebugBuild: Boolean, unlocked: Boolean, userToggle: Boolean): Boolean =
            isDebugBuild && unlocked && userToggle
    }
}
