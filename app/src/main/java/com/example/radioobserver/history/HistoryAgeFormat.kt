package com.example.radioobserver.history

/**
 * PREMIUM UI/UX V2 (2026-10-23): pure, host-tested Turkish human-friendly age
 * formatting for HISTORY records. The stored raw millisecond age (e.g. 2,838,035)
 * was technically honest but unreadable on the history detail screen.
 *
 * Rules (all pure, no android imports):
 *  - It is the age AT RECORDING TIME ("Kayıt anındaki yaş") — never the present
 *    elapsed time; the caller labels it accordingly.
 *  - Never fabricates precision: rounds DOWN with "yaklaşık" (approximate).
 *  - Never negative; null/unknown stays the caller's "Bilinmiyor".
 */
object HistoryAgeFormat {

    /** Bounded word forms; Turkish suffix -dır/-dir intentionally omitted for neutral UI. */
    fun formatAgeMs(ageMs: Long): String = when {
        ageMs < MINUTE_MS -> "1 dakikadan az"
        ageMs < HOUR_MS -> "${ageMs / MINUTE_MS} dakika"
        ageMs < DAY_MS -> "${ageMs / HOUR_MS} saat"
        ageMs < 30 * DAY_MS -> "${ageMs / DAY_MS} gün"
        else -> "30 günden fazla"
    }

    /** Same value prefixed with the honest approximation qualifier where rounded. */
    fun formatApproximate(ageMs: Long): String {
        val core = formatAgeMs(ageMs)
        return if (ageMs < MINUTE_MS) core else "yaklaşık $core"
    }

    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L
}
