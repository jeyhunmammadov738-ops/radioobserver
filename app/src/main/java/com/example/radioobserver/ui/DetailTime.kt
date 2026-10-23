package com.example.radioobserver.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MICRO-POLISH (2026-10-23): pure, host-tested clock-time presentation shared by the
 * live feed row and the live detail. The detail's "Son görülme" VALUE must be the bare
 * authoritative time (the label already carries the meaning) — never a duplicated
 * "Son görülme: HH:mm:ss" prefix. The epoch value always comes from the sanitized
 * pipeline; nothing here invents or rewrites timestamps.
 */
object DetailTime {
    fun plainClockText(epochMs: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}
