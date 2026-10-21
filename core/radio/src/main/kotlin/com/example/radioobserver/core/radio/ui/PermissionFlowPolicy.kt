package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.permissions.PermissionId

/**
 * Phase 3 task 3.3 — Runtime permission & settings flow POLICY (pure Kotlin/JVM).
 *
 * Platform-independent decisions only: deterministic inputs in, typed decisions out.
 * NO android.* imports, no side effects, no dialogs, no scanning, no persistence.
 * The thin Android bridge in :app feeds real platform facts into these functions.
 *
 * Approved product rules encoded here:
 * - A runtime request only ever follows an explicit user Start tap.
 * - API 29-30: request ACCESS_FINE_LOCATION alone when the capability needs it.
 * - API 31+: request ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION TOGETHER in one
 *   runtime request whenever precise location is needed; for BLE also include
 *   BLUETOOTH_SCAN when missing, coordinated in the SAME single request (never
 *   overlapping dialogs).
 * - ACCESS_WIFI_STATE is a manifest-normal permission and is NEVER requested at runtime;
 *   NEARBY_WIFI_DEVICES does not exist in this app's permission vocabulary.
 * - Approximate-only is never PRECISE: FINE-required capabilities stay unavailable.
 * - shouldShowRequestPermissionRationale() == false ALONE never proves permanently
 *   denied: denial strength stays UNCERTAIN unless real evidence exists elsewhere.
 * - A permission result NEVER produces an observation-start effect: the pending Start
 *   tap is consumed (cleared), never replayed; a NEW explicit tap is required (3.4+).
 */

/** Which observation capability the user tapped. */
enum class PermissionFlowScope { BLE, WIFI }

/**
 * Grant snapshot as read by the Android bridge. [bluetoothScan] is true by construction
 * on API < 31 (legacy install-time permissions); the bridge supplies the real value on
 * API 31+.
 */
data class RuntimeGrantSnapshot(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val bluetoothScan: Boolean = true,
)

/** One coordinated runtime request for one scope (never two overlapping launches). */
data class RuntimePermissionRequest(
    val permissions: List<PermissionId>,
    val scope: PermissionFlowScope,
) {
    init {
        require(permissions.isNotEmpty()) { "a request must contain permissions" }
        require(permissions.distinct().size == permissions.size) { "no duplicate permissions" }
        require(PermissionId.ACCESS_WIFI_STATE !in permissions) { "ACCESS_WIFI_STATE is install-time only" }
        require(PermissionId.BLUETOOTH !in permissions && PermissionId.BLUETOOTH_ADMIN !in permissions) {
            "legacy Bluetooth permissions are install-time only"
        }
    }
}

/** Resolves an honest location-access classification from a grant snapshot. */
object LocationAccessResolver {
    /**
     * Honest restoration mapping (3.8): with nothing granted, DENIED is only truthful
     * if a genuine runtime request has actually happened ([LocationAccessUi.DENIED] =
     * "runtime request happened and was not granted"). Without that evidence the
     * honest state is NOT_REQUESTED — a fresh process/first launch must never claim
     * a denial that never occurred. Grants map to PRECISE/APPROXIMATE_ONLY regardless.
     */
    fun resolve(
        snapshot: RuntimeGrantSnapshot,
        runtimeRequestHappened: Boolean,
    ): LocationAccessUi = when {
        snapshot.fineLocation -> LocationAccessUi.PRECISE
        snapshot.coarseLocation -> LocationAccessUi.APPROXIMATE_ONLY
        runtimeRequestHappened -> LocationAccessUi.DENIED
        else -> LocationAccessUi.NOT_REQUESTED
    }
}

/**
 * Denial strength. There is deliberately NO permanently-denied output: this policy never
 * claims permanent denial from rationale flags alone. The enum communicates what the UI
 * may honestly say (rationale first vs. uncertain/settings guidance).
 */
enum class DenialAssessment { NOT_DENIED, RATIONALE_RECOMMENDED, UNCERTAIN }

object DenialClassifier {
    /**
     * @param rationaleShouldShow platform's shouldShowRequestPermissionRationale() value.
     * @param previouslyRequested the app has genuinely requested this permission before.
     */
    fun assess(
        denied: Boolean,
        rationaleShouldShow: Boolean,
        previouslyRequested: Boolean,
    ): DenialAssessment = when {
        !denied -> DenialAssessment.NOT_DENIED
        rationaleShouldShow -> DenialAssessment.RATIONALE_RECOMMENDED
        else -> DenialAssessment.UNCERTAIN
    }
}

/** Builds the API-correct runtime request plan for one scope. */
object PermissionRequestPlanner {
    /** Permissions the runtime vocabulary may legitimately request per API level. */
    private val RUNTIME_REQUESTABLE: Set<PermissionId> = setOf(
        PermissionId.ACCESS_FINE_LOCATION,
        PermissionId.ACCESS_COARSE_LOCATION,
        PermissionId.BLUETOOTH_SCAN,
    )

    fun isRuntimeRequestable(permission: PermissionId): Boolean = permission in RUNTIME_REQUESTABLE

    /**
     * Returns the single coordinated request needed to observe [scope], or null when every
     * runtime permission for that scope is already granted (no dialog may be shown).
     */
    fun plan(apiLevel: Int, scope: PermissionFlowScope, snapshot: RuntimeGrantSnapshot): RuntimePermissionRequest? {
        require(apiLevel >= 29) { "unsupported api level: $apiLevel" }
        val permissions = buildList {
            if (apiLevel >= 31) {
                // Precise location needed: FINE + COARSE always travel together (Android 12+),
                // so the user can make the approximate-only choice in the SAME dialog.
                if (!snapshot.fineLocation || !snapshot.coarseLocation) {
                    add(PermissionId.ACCESS_FINE_LOCATION)
                    add(PermissionId.ACCESS_COARSE_LOCATION)
                }
                if (scope == PermissionFlowScope.BLE && !snapshot.bluetoothScan) {
                    add(PermissionId.BLUETOOTH_SCAN)
                }
            } else {
                // API 29-30: FINE is the only runtime request this app ever makes.
                if (!snapshot.fineLocation) add(PermissionId.ACCESS_FINE_LOCATION)
            }
        }
        return permissions.takeIf { it.isNotEmpty() }?.let { RuntimePermissionRequest(it, scope) }
    }
}

/** Reducible flow state. Holds no user data; safe to drop and rebuild at any time. */
data class PermissionFlowState(
    /** Scope whose runtime dialog is currently in flight (duplicate-launch guard). */
    val pendingRequestScope: PermissionFlowScope? = null,
    /** Transient: the Start tap that triggered a dialog. Cleared on any result; NEVER replayed. */
    val pendingStartTap: PermissionFlowScope? = null,
    val locationAccess: LocationAccessUi = LocationAccessUi.NOT_REQUESTED,
    /** Scopes for which a genuine runtime request has happened (rationale evidence base). */
    val previouslyRequested: Set<PermissionFlowScope> = emptySet(),
)

/** Inputs to the reducer; supplied by the Android bridge at real event time. */
sealed interface PermissionFlowEvent {
    /** Explicit user Start tap with a FRESH grant snapshot at tap time. */
    data class UserStartTap(val scope: PermissionFlowScope, val snapshot: RuntimeGrantSnapshot) : PermissionFlowEvent

    /** Runtime dialog result, delivered with a fresh post-callback grant snapshot. */
    data class PermissionResult(val scope: PermissionFlowScope, val snapshot: RuntimeGrantSnapshot) : PermissionFlowEvent

    /** User returned from system Settings; readiness refresh only. */
    data class SettingsReturned(val snapshot: RuntimeGrantSnapshot) : PermissionFlowEvent

    /** Activity recreation: transient Start intent is dropped; nothing is replayed. */
    data object ActivityRecreated : PermissionFlowEvent
}

/**
 * The ONLY effects the flow may produce. There is intentionally no start-observation
 * effect: starting anything is task 3.4's explicit user-driven wiring, and a permission
 * grant alone must never trigger it.
 */
sealed interface PermissionFlowEffect {
    data class ShowRuntimeDialog(val request: RuntimePermissionRequest) : PermissionFlowEffect
    data object None : PermissionFlowEffect
}

object PermissionFlowReducer {

    fun reduce(
        state: PermissionFlowState,
        event: PermissionFlowEvent,
        apiLevel: Int,
    ): Pair<PermissionFlowState, PermissionFlowEffect> = when (event) {
        is PermissionFlowEvent.UserStartTap -> {
            if (state.pendingRequestScope != null) {
                // In-flight guard: a second tap while a dialog is up must not stack requests.
                state to PermissionFlowEffect.None
            } else {
                val plan = PermissionRequestPlanner.plan(apiLevel, event.scope, event.snapshot)
                if (plan == null) {
                    // Everything already granted: the flow has nothing to do. Actual
                    // observation start is a 3.4 concern driven by a SEPARATE explicit
                    // decision, never by this reducer.
                    state to PermissionFlowEffect.None
                } else {
                    val next = state.copy(
                        pendingRequestScope = event.scope,
                        pendingStartTap = event.scope,
                        previouslyRequested = state.previouslyRequested + event.scope,
                    )
                    next to PermissionFlowEffect.ShowRuntimeDialog(plan)
                }
            }
        }
        is PermissionFlowEvent.PermissionResult -> {
            // Fresh grants decide the honest location access; the pending Start tap is
            // CONSUMED, not executed: no effect can ever start observation here.
            val next = state.copy(
                pendingRequestScope = null,
                pendingStartTap = null,
                // A dialog genuinely ran here, so DENIED (if still ungranted) is truthful.
                locationAccess = LocationAccessResolver.resolve(event.snapshot, runtimeRequestHappened = true),
            )
            next to PermissionFlowEffect.None
        }
        is PermissionFlowEvent.SettingsReturned -> {
            // Settings return is a read-only refresh: no dialog, no start, no pending set.
            // Honesty (3.8): "denied" requires genuine denial evidence — either a past
            // runtime request OR access that was previously held (a grant later revoked
            // in Settings is a real denial). A fresh process with neither reads
            // NOT_REQUESTED, never a fabricated DENIED.
            val next = state.copy(
                locationAccess = LocationAccessResolver.resolve(
                    event.snapshot,
                    runtimeRequestHappened = state.previouslyRequested.isNotEmpty() ||
                        state.locationAccess != LocationAccessUi.NOT_REQUESTED))
            next to PermissionFlowEffect.None
        }
        PermissionFlowEvent.ActivityRecreated -> {
            // The transient Start intent must not survive recreation; the in-flight
            // guard stays so a (possibly stale) late result is still consumed safely.
            val next = state.copy(pendingStartTap = null)
            next to PermissionFlowEffect.None
        }
    }
}
