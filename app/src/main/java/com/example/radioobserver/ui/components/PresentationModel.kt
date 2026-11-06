package com.example.radioobserver.ui.components

/**
 * UX-01: PURE presentation mapping (no Compose, no android.*, no I/O).
 *
 * Compose composition, clicks and dialogs cannot execute under the existing host
 * infrastructure, so every mapping decision a shared component makes lives here as a
 * pure, deterministic function that host tests can verify behaviorally.
 */

/** Source distinction carried by a compact row: Bluetooth or Wi-Fi, nothing else. */
enum class SourceTag { BLUETOOTH, WIFI }

/**
 * Freshness tag shown to the user. STALE covers every "old/known age" case; UNKNOWN is a
 * literal unknown, never silently shown as fresh.
 */
enum class FreshnessTag { FRESH, STALE, UNKNOWN }

/** What the row knows about its primary label. */
enum class PrimaryTextKind { KNOWN, UNKNOWN }

/** Visual emphasis of an action offered by a shared surface. */
enum class Emphasis { PRIMARY, SECONDARY, TEXT, DESTRUCTIVE }

/**
 * One compact observation row, data-agnostic. There is no device identity field: the row
 * renders whatever sanitized text the caller supplies and nothing more.
 */
data class ObservationRowModel(
    val source: SourceTag,
    val primaryText: String?,
    val primaryKind: PrimaryTextKind,
    val metadata: String?,
    val freshness: FreshnessTag,
    val favorite: Boolean,
    val enabled: Boolean,
)

/** Kinds of standardized non-content surface. No new domain state is invented. */
enum class SurfaceKind { EMPTY, NO_SESSION, PERMISSION_REQUIRED, UNAVAILABLE, ERROR, LOADING }

/** A standardized empty/error/info surface description. */
data class StateSurfaceModel(
    val kind: SurfaceKind,
    val titleText: String?,
    val detailText: String?,
    val actionLabel: String?,
    val actionEmphasis: Emphasis,
    val dismissible: Boolean,
)

/** How a confirmation dialog was answered. DISMISS is never an implicit confirm. */
enum class ConfirmIntent { CONFIRM, CANCEL, DISMISS }

/** Result of pressing one dialog control. */
object Confirmation {
    fun fromConfirmClick(): ConfirmIntent = ConfirmIntent.CONFIRM
    fun fromCancelClick(): ConfirmIntent = ConfirmIntent.CANCEL
    fun fromDismissal(): ConfirmIntent = ConfirmIntent.DISMISS

    /** Only an explicit confirm press authorizes the destructive action. */
    fun authorizesDestructiveAction(intent: ConfirmIntent): Boolean = intent == ConfirmIntent.CONFIRM

    /** A destructive dialog must say what will happen in its explanation. */
    fun requiresExplanation(destructive: Boolean): Boolean = destructive
}

/** Mapping helpers shared by the compact row and its callers. */
object ObservationRowMapping {
    fun sourceTag(isBluetooth: Boolean): SourceTag =
        if (isBluetooth) SourceTag.BLUETOOTH else SourceTag.WIFI

    /**
     * Freshness mapping. A cached Wi-Fi result is STALE regardless of any other signal, and
     * an unknown age stays UNKNOWN - never FRESH.
     */
    fun freshnessTag(isFresh: Boolean, isCached: Boolean, ageKnown: Boolean): FreshnessTag = when {
        isCached -> FreshnessTag.STALE
        !ageKnown -> FreshnessTag.UNKNOWN
        isFresh -> FreshnessTag.FRESH
        else -> FreshnessTag.STALE
    }

    fun primaryKind(text: String?): PrimaryTextKind =
        if (text.isNullOrBlank()) PrimaryTextKind.UNKNOWN else PrimaryTextKind.KNOWN

    /** Compact row: blank text stays null and is labelled by the caller's string resource. */
    fun model(
        isBluetooth: Boolean,
        primaryText: String?,
        metadata: String?,
        isFresh: Boolean,
        isCached: Boolean,
        ageKnown: Boolean,
        favorite: Boolean,
        enabled: Boolean,
    ): ObservationRowModel = ObservationRowModel(
        source = sourceTag(isBluetooth),
        primaryText = primaryText?.takeIf { it.isNotBlank() },
        primaryKind = primaryKind(primaryText),
        metadata = metadata?.takeIf { it.isNotBlank() },
        freshness = freshnessTag(isFresh, isCached, ageKnown),
        favorite = favorite,
        enabled = enabled,
    )

    /**
     * A row carries BOTH an interactive target and an optional favorite toggle; a disabled
     * row offers neither. Callers must not attach click handlers to a disabled row.
     */
    fun interactiveTargets(enabled: Boolean, favoriteToggleShown: Boolean): Int =
        if (!enabled) 0 else if (favoriteToggleShown) 2 else 1
}

/** Standardized surface mapping. */
object StateSurfaces {
    fun empty(detail: String?, actionLabel: String? = null): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.EMPTY, null, detail, actionLabel,
            if (actionLabel == null) Emphasis.TEXT else Emphasis.SECONDARY, dismissible = false)

    fun noSession(detail: String?): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.NO_SESSION, null, detail, null, Emphasis.TEXT, false)

    fun permissionRequired(detail: String?, actionLabel: String?): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.PERMISSION_REQUIRED, null, detail, actionLabel, Emphasis.PRIMARY, false)

    fun unavailable(detail: String?): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.UNAVAILABLE, null, detail, null, Emphasis.TEXT, false)

    fun error(detail: String?, retryLabel: String? = null): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.ERROR, null, detail, retryLabel,
            if (retryLabel == null) Emphasis.TEXT else Emphasis.SECONDARY, dismissible = false)

    fun loading(detail: String? = null): StateSurfaceModel =
        StateSurfaceModel(SurfaceKind.LOADING, null, detail, null, Emphasis.TEXT, false)

    /** A retry action exists exactly when an action label was supplied. */
    fun offersRetry(model: StateSurfaceModel): Boolean =
        model.kind == SurfaceKind.ERROR && model.actionLabel != null
}
