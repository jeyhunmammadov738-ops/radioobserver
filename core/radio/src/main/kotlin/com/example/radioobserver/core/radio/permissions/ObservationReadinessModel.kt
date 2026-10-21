package com.example.radioobserver.core.radio.permissions

import com.example.radioobserver.core.radio.model.RadioType

/**
 * Phase 2 task 2.5 — pure permission/capability decision model.
 *
 * Pure Kotlin/JVM state machine over an immutable [CapabilitySnapshot]. It models Android
 * permission/capability states ONLY; it never requests, implies, or observes real grants.
 * The Android-side adapters (later, separately authorized) build snapshots from framework
 * calls and consume the resulting [ObservationReadiness].
 *
 * Policy encoded here (developer-approved 2026-10-19; see docs/permission-capability-matrix.md):
 * - BLE neverForLocation is NOT asserted → ACCESS_FINE_LOCATION stays required for BLE
 *   scan results on every supported bucket.
 * - Wi-Fi getScanResults() conservatively requires ACCESS_FINE_LOCATION on every bucket
 *   (per official docs this holds even when targeting API 33+); NEARBY_WIFI_DEVICES is
 *   NOT adopted as a substitute.
 * - Location services enabled is a conservative gate for BOTH radios (documented as a
 *   conservative requirement; see the matrix's Limitations section).
 */

/** Supported device runtime buckets (minSdk 29 → today's 35 in the 33+ bucket). */
enum class AndroidApiBucket {
    API_29_30,
    API_31_32,
    API_33_PLUS;

    companion object {
        fun fromSdkLevel(sdk: Int): AndroidApiBucket = when {
            sdk <= 30 -> API_29_30
            sdk <= 32 -> API_31_32
            else -> API_33_PLUS
        }
    }
}

/**
 * Permission identities this app may need. Deliberately minimal: no BLUETOOTH_CONNECT,
 * no BLUETOOTH_ADVERTISE (app never pairs/connects/makes discoverable), and no
 * NEARBY_WIFI_DEVICES (not adopted for getScanResults()).
 */
enum class PermissionId {
    /** Legacy install-time permissions (API ≤ 30 only). */
    BLUETOOTH,

    /** Legacy install-time permissions (API ≤ 30 only). */
    BLUETOOTH_ADMIN,

    /** Runtime permission on API 31+ for BLE scans. */
    BLUETOOTH_SCAN,

    /** Runtime permission; required for scan results on all supported buckets (no neverForLocation). */
    ACCESS_FINE_LOCATION,

    /**
     * Runtime permission on API 31+ (added in Phase 3.3): Android 12+ requires FINE and
     * COARSE to be requested TOGETHER whenever precise location is requested; the user
     * may grant approximate-only, which never satisfies FINE-required scanning. On
     * API 29-30 it is an optional coarse companion of the legacy FINE request and is
     * deliberately NOT part of [requiredPermissionsFor] (the hard scanning requirement
     * there remains FINE only); it exists for the runtime request planner only.
     */
    ACCESS_COARSE_LOCATION,

    /** Install-time permission for Wi-Fi state access. */
    ACCESS_WIFI_STATE,
}

/** Modeled permission states. "Permanently denied" must route the user to settings. */
enum class PermissionState { GRANTED, DENIED, PERMANENTLY_DENIED }

/** Hardware/toggle facts for one radio, as reported by the (future) Android adapter. */
data class RadioHardwareStatus(
    val hardwarePresent: Boolean,
    val radioEnabled: Boolean,
    val locationServicesEnabled: Boolean,
)

/** Everything the decision model needs, for ONE radio at ONE moment. */
data class CapabilitySnapshot(
    val apiBucket: AndroidApiBucket,
    val radio: RadioType,
    val hardware: RadioHardwareStatus,
    val permissions: Map<PermissionId, PermissionState>,
) {
    init {
        require(hardware.hardwarePresent || !hardware.radioEnabled) {
            "inconsistent snapshot: radio enabled without hardware"
        }
    }
}

/** Honest user-facing disclosure obligations tied to an observation capability. */
enum class Disclosure {
    BLE_RESULTS_MAY_REVEAL_PROXIMITY,
    WIFI_RESULTS_MAY_REVEAL_LOCATION,
    WIFI_RESULTS_MAY_BE_CACHED_STALE,
    NO_BACKGROUND_OBSERVATION,
}

/** Typed blockers. Multiple blockers may coexist; order is not significant (Set). */
sealed interface ReadinessBlocker {
    data class PermissionMissing(
        val permission: PermissionId,
        val permanent: Boolean,
    ) : ReadinessBlocker

    data class RadioDisabled(val radio: RadioType) : ReadinessBlocker
    data class LocationServicesDisabled(val radio: RadioType) : ReadinessBlocker
    data class HardwareUnavailable(val radio: RadioType) : ReadinessBlocker
}

/** Decision output for one snapshot. Blocked never fabricates device facts. */
sealed interface ObservationReadiness {
    data class Ready(val radio: RadioType, val disclosures: Set<Disclosure>) : ObservationReadiness
    data class Blocked(val radio: RadioType, val blockers: Set<ReadinessBlocker>) : ObservationReadiness
}

/**
 * Deterministic, table-driven decision engine. Pure function of [CapabilitySnapshot];
 * identical inputs always yield identical outputs (no time, no randomness, no Android).
 */
object ObservationReadinessModel {

    /** Permissions required for the given radio on the given runtime bucket (matrix 2.1). */
    fun requiredPermissions(radio: RadioType, bucket: AndroidApiBucket): Set<PermissionId> =
        when (radio) {
            RadioType.BLE ->
                when (bucket) {
                    AndroidApiBucket.API_29_30 ->
                        setOf(PermissionId.BLUETOOTH, PermissionId.BLUETOOTH_ADMIN, PermissionId.ACCESS_FINE_LOCATION)
                    AndroidApiBucket.API_31_32, AndroidApiBucket.API_33_PLUS ->
                        setOf(PermissionId.BLUETOOTH_SCAN, PermissionId.ACCESS_FINE_LOCATION)
                }
            RadioType.WIFI -> setOf(PermissionId.ACCESS_WIFI_STATE, PermissionId.ACCESS_FINE_LOCATION)
        }

    /** Honest disclosure set for the radio (both radios include the no-background promise). */
    fun disclosuresFor(radio: RadioType): Set<Disclosure> =
        when (radio) {
            RadioType.BLE ->
                setOf(
                    Disclosure.BLE_RESULTS_MAY_REVEAL_PROXIMITY,
                    Disclosure.NO_BACKGROUND_OBSERVATION,
                )
            RadioType.WIFI ->
                setOf(
                    Disclosure.WIFI_RESULTS_MAY_REVEAL_LOCATION,
                    Disclosure.WIFI_RESULTS_MAY_BE_CACHED_STALE,
                    Disclosure.NO_BACKGROUND_OBSERVATION,
                )
        }

    /** Evaluate readiness. Accumulates ALL blockers; never crashes on missing map entries. */
    fun evaluate(snapshot: CapabilitySnapshot): ObservationReadiness {
        val blockers = mutableSetOf<ReadinessBlocker>()

        if (!snapshot.hardware.hardwarePresent) {
            blockers += ReadinessBlocker.HardwareUnavailable(snapshot.radio)
        }
        if (!snapshot.hardware.radioEnabled) {
            blockers += ReadinessBlocker.RadioDisabled(snapshot.radio)
        }
        if (!snapshot.hardware.locationServicesEnabled) {
            blockers += ReadinessBlocker.LocationServicesDisabled(snapshot.radio)
        }

        for (permission in requiredPermissions(snapshot.radio, snapshot.apiBucket)) {
            val state = snapshot.permissions[permission] ?: PermissionState.DENIED
            if (state != PermissionState.GRANTED) {
                blockers +=
                    ReadinessBlocker.PermissionMissing(
                        permission = permission,
                        permanent = state == PermissionState.PERMANENTLY_DENIED,
                    )
            }
        }

        return if (blockers.isEmpty()) {
            ObservationReadiness.Ready(snapshot.radio, disclosuresFor(snapshot.radio))
        } else {
            ObservationReadiness.Blocked(snapshot.radio, blockers.toSet())
        }
    }
}
