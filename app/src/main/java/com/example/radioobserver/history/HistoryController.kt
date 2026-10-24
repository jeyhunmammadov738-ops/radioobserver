package com.example.radioobserver.history

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import com.example.radioobserver.core.radio.history.HistoryAnnotation
import com.example.radioobserver.core.radio.history.HistoryAnnotationPersistence
import com.example.radioobserver.core.radio.history.HistoryAnnotationRepository
import com.example.radioobserver.core.radio.history.HistoryConsent
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.history.LiveRowTracker
import com.example.radioobserver.core.radio.history.ObservationHistory
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The ONLY bridge between the LIVE sanitized feed and the OPTIONAL local history.
 *
 * Direction is one-way (live → history): the controller OBSERVES the orchestrator's
 * published state and never injects anything back. All engine/storage/tracker access
 * is serialized on ONE background executor, so concurrent write/delete/toggle races
 * cannot interleave (a queued write either runs before a Clear All or is checked
 * against the CURRENT consent at execution time when it runs after it).
 *
 * History interactions are INERT with respect to observation: no method here can start,
 * stop, extend or schedule any session, and no radio/permission state is touched.
 */
class HistoryController(
    context: Context,
    annotationPersistence: HistoryAnnotationPersistence? = null,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {

    data class UiState(
        val loaded: Boolean = false,
        val consent: HistoryConsent = HistoryConsent(),
        val records: List<HistoryRecord> = emptyList(),
        /** WORKSTREAM 04: record-bound annotations, always in sync with records. */
        val annotations: Map<String, HistoryAnnotation> = emptyMap(),
        /** One-time honest notice: corrupt store lines were dropped on load. */
        val droppedNotice: Boolean = false,
        val failedClosedNotice: Boolean = false,
    )

    private val persistence = HistoryFileStorage(context)
    // WORKSTREAM 04: RECORD-BOUND annotations (favorite/label/note) for saved
    // records. Passed in from MainActivity (constructed AFTER super.onCreate —
    // WS02 hotfix contract); null keeps the controller annotation-free.
    private val annotations: HistoryAnnotationRepository? =
        annotationPersistence?.let { HistoryAnnotationRepository(it) }
    private val history = ObservationHistory(
        persistence = persistence,
        clock = { System.currentTimeMillis() },
    )
    private val tracker = LiveRowTracker()

    /** Compose-observed state; updated ONLY via the main handler. */
    val state = mutableStateOf(UiState())

    /** Load the persisted store once at startup (background, then publish). */
    fun initialize() {
        executor.execute {
            history.loadIfNeeded()
            publish(
                droppedNotice = history.lastLoadDroppedLines > 0,
                failedClosedNotice = history.lastLoadFailedClosed,
            )
        }
    }

    /**
     * Fed with EVERY orchestrator state publication (NOT with recompositions): the
     * tracker turns the live rows into at-most-one bounded snapshot per session-local
     * observation, honoring the enable-time seed (no backfill) and Clear All (no
     * resurrection). An empty feed resets the session-local dedup (new session).
     */
    fun onLiveRows(rows: List<ObservationRowUi>) {
        executor.execute {
            history.loadIfNeeded()
            tracker.onConsentChanged(history.consent.recordingEnabled)
            tracker.feed(rows).forEach { history.record(it) }
            publish()
        }
    }

    fun setRecordingEnabled(enabled: Boolean) = executor.execute {
        history.setRecordingEnabled(enabled)
        tracker.onConsentChanged(enabled)
        publish()
    }

    fun setWifiNamesEnabled(enabled: Boolean) = executor.execute {
        history.setWifiNamesEnabled(enabled)
        publish()
    }

    fun setRetentionDays(days: Int) = executor.execute {
        history.setRetentionDays(days)
        publish()
    }

    fun deleteOne(id: String) = executor.execute {
        history.deleteOne(id)
        publish()
    }

    /** Confirmed Clear All: the tracker keeps already-seen live rows seen, so the
     *  still-active session cannot re-record its old rows after the wipe. */
    fun clearAll() = executor.execute {
        history.clearAll()
        publish() // publish() drops orphaned annotations via retainAll
    }

    // ---- WORKSTREAM 04: record-bound annotations (explicit user actions only) ----

    /** Creates/updates one record's label/note/favorite. Storage-only work on the
     *  executor: by construction it never dispatches a radio action. */
    fun setAnnotation(recordId: String, label: String?, note: String?, favorite: Boolean) =
        executor.execute {
            annotations?.set(
                recordId, label, note, favorite,
                knownRecordIds = history.snapshot().map { it.id }.toSet(),
            )
            publish()
        }

    /** Removes one record's annotation (label + note + favorite). */
    fun deleteAnnotation(recordId: String) = executor.execute {
        annotations?.delete(recordId)
        publish()
    }

    fun dismissNotices() = executor.execute {
        publish(droppedNotice = false, failedClosedNotice = false)
    }

    private fun publish(
        droppedNotice: Boolean? = null,
        failedClosedNotice: Boolean? = null,
    ) {
        val records = history.snapshot()
        // WORKSTREAM 04: annotations live EXACTLY as long as their records —
        // retention sweeps, capacity cap, single deletes and Clear All flow
        // through this single sync point (orphans dropped, never consulted).
        annotations?.retainAll(records.map { it.id }.toSet())
        val snapshot = UiState(
            loaded = true,
            consent = history.consent,
            records = records,
            annotations = annotations?.list() ?: emptyMap(),
            droppedNotice = droppedNotice ?: (history.lastLoadDroppedLines > 0),
            failedClosedNotice = failedClosedNotice ?: history.lastLoadFailedClosed,
        )
        mainHandler.post { state.value = snapshot }
    }

    /** App-private refresh (history screen re-entry): re-reads from the engine. */
    fun refresh() = executor.execute { publish() }
}
