package com.example.radioobserver.wifi

import android.net.wifi.WifiManager
import android.os.SystemClock
import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.boundary.ScanFailureReason
import com.example.radioobserver.core.radio.boundary.WifiAccessPointRecord
import com.example.radioobserver.core.radio.boundary.WifiFetchOutcome
import com.example.radioobserver.core.radio.boundary.WifiResultFetcher
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.PermissionId

/**
 * Reads ONLY the system-cached scan results via [WifiManager.getScanResults] (task 2.4).
 * Never triggers a scan. The platform's ScanResult.timestamp is MICROSECONDS SINCE BOOT;
 * age is computed against the compatible boot-time clock (elapsedRealtime), and unknown /
 * negative / future / implausibly large values stay unknown instead of being fabricated.
 */
class AndroidWifiResultFetcher(
    private val wifiManagerProvider: () -> WifiManager?,
) : WifiResultFetcher {

    override fun fetch(): WifiFetchOutcome {
        val manager = wifiManagerProvider()
            ?: return WifiFetchOutcome.Failed(ObservationError.HardwareUnavailable(RadioType.WIFI))
        val results = try {
            manager.getScanResults()
        } catch (security: SecurityException) {
            // Actual runtime permission loss: fail closed with a typed error, never crash.
            return WifiFetchOutcome.Failed(
                ObservationError.PermissionDenied(PermissionId.ACCESS_FINE_LOCATION, permanent = false, radio = RadioType.WIFI))
        } catch (unexpected: RuntimeException) {
            return WifiFetchOutcome.Failed(
                ObservationError.ScanStartFailed(ScanFailureReason.INTERNAL_ERROR, null, RadioType.WIFI))
        }

        val nowUs = SystemClock.elapsedRealtimeNanos() / 1_000
        val records = results.orEmpty().map { result ->
            val bootTimestampUs = result.timestamp // raw µs since boot, preserved verbatim
            WifiAccessPointRecord(
                bssid = result.BSSID,
                ssid = result.SSID,
                rssiDbm = result.level,
                bootTimestampUs = bootTimestampUs,
                ageMs = computeAgeMs(bootTimestampUs, nowUs),
            )
        }
        return WifiFetchOutcome.Results(records)
    }

    private fun computeAgeMs(timestampUs: Long, nowUs: Long): Long? {
        if (timestampUs <= 0) return null
        val deltaUs = nowUs - timestampUs
        if (deltaUs < 0) return null // future/invalid timestamp: unknown, never fabricated
        val ageMs = deltaUs / 1_000
        return if (ageMs > MAX_TRACKED_AGE_MS) null else ageMs
    }

    companion object {
        /** Beyond this the value is treated as implausible/overflow: unknown. */
        const val MAX_TRACKED_AGE_MS: Long = 24L * 60 * 60 * 1000
    }
}
