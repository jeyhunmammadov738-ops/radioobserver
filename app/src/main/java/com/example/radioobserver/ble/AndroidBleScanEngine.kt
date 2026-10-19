package com.example.radioobserver.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.example.radioobserver.core.radio.boundary.BleScanEngine
import com.example.radioobserver.core.radio.boundary.EngineAdvertisement
import com.example.radioobserver.core.radio.boundary.EngineCallback
import com.example.radioobserver.core.radio.boundary.EngineStartOutcome
import com.example.radioobserver.core.radio.boundary.ScanFailureReason

/**
 * Thin platform shell over [BluetoothLeScanner] (task 2.3). All session/gating/dedup logic
 * lives in the pure-JVM BleSessionCoordinator; this class only starts/stops the platform
 * scan and forwards raw events.
 *
 * Honesty notes (documented platform limitations):
 * - No logging of raw Bluetooth addresses or manufacturer payload bytes, ever.
 * - Timestamps forwarded are ARRIVAL wall-clock ms. The platform's timestampNanos is
 *   elapsed-realtime since boot, not wall-clock; we do not present it as epoch time.
 * - Real devices may throttle scans (TOO_FREQUENT), unfiltered scans can pause on
 *   screen-off, and callback delivery is not guaranteed.
 * - Scan settings: all matches, no filters (observation coverage priority), balanced mode.
 */
class AndroidBleScanEngine(
    private val adapterProvider: () -> BluetoothAdapter?,
) : BleScanEngine {

    private var started = false
    private var scannerRef: android.bluetooth.le.BluetoothLeScanner? = null
    private var platformCallback: ScanCallback? = null

    override fun rawStart(callback: EngineCallback): EngineStartOutcome {
        if (started) return EngineStartOutcome.Failed(ScanFailureReason.ALREADY_STARTED, 1)
        val adapter = adapterProvider()
            ?: return EngineStartOutcome.Failed(ScanFailureReason.NO_SCANNER)
        if (!adapter.isEnabled) {
            // Defensive: the coordinator's readiness gate should already refuse this.
            return EngineStartOutcome.Failed(ScanFailureReason.NO_SCANNER)
        }
        val scanner = adapter.bluetoothLeScanner
            ?: return EngineStartOutcome.Failed(ScanFailureReason.NO_SCANNER)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val platformCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!started) return // late callback after stop: dropped, never revives
                try {
                    callback.onAdvertisement(
                        address = result.device?.address,
                        rssiDbm = result.rssi,
                        bytes = result.scanRecord?.bytes,
                        timestampMs = System.currentTimeMillis(),
                        displayName = result.scanRecord?.deviceName,
                    )
                } catch (unexpected: RuntimeException) {
                    // Defensive boundary: malformed records must never crash the app.
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                if (!started || results == null) return
                val ads = mutableListOf<EngineAdvertisement>()
                for (result in results) {
                    try {
                        ads += EngineAdvertisement(
                            address = result.device?.address,
                            rssiDbm = result.rssi,
                            bytes = result.scanRecord?.bytes,
                            timestampMs = System.currentTimeMillis(),
                            displayName = result.scanRecord?.deviceName,
                        )
                    } catch (unexpected: RuntimeException) {
                        // skip malformed batch entry
                    }
                }
                if (ads.isNotEmpty()) callback.onBatch(ads)
            }

            override fun onScanFailed(errorCode: Int) {
                if (!started) return
                callback.onEngineFailure(ScanFailureReason.fromPlatformCode(errorCode), errorCode)
            }
        }

        return try {
            scanner.startScan(null, settings, platformCallback)
            started = true
            scannerRef = scanner
            this.platformCallback = platformCallback
            EngineStartOutcome.Started
        } catch (security: SecurityException) {
            // A required permission is actually missing at runtime: typed refusal, never silent.
            EngineStartOutcome.Failed(ScanFailureReason.SECURITY_EXCEPTION)
        }
    }

    override fun rawStop(): Boolean {
        if (!started) return false
        val scanner = scannerRef
        val callback = platformCallback
        started = false
        scannerRef = null
        platformCallback = null
        if (scanner == null || callback == null) return false
        return try {
            scanner.stopScan(callback)
            true
        } catch (security: SecurityException) {
            false // cleanup failure reported by the coordinator, not hidden
        }
    }
}
