package com.example.radioobserver.history

import android.os.Handler
import android.os.Looper
import com.example.radioobserver.core.radio.history.AnalyticalGroup
import com.example.radioobserver.core.radio.history.GroupError
import com.example.radioobserver.core.radio.history.GroupResult
import com.example.radioobserver.core.radio.history.SessionGroupPersistence
import com.example.radioobserver.core.radio.history.SessionGroupRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WORKSTREAM 05: UI controller for NAMED ANALYTICAL GROUPS.
 *
 * Same lifecycle-safe pattern as HistoryController: single-thread executor owns
 * the repository, the main handler publishes immutable snapshots, Compose state is
 * updated ONLY via publish. Every operation below is EXPLICIT user action over
 * ALREADY-SAVED records: nothing here starts/stops a radio session, saves live
 * observations, touches recording consent or mutates the History store. Group
 * deletion removes metadata only (History records stay; a combined destructive
 * action does not exist and is not authorized).
 */
class SessionGroupController(
    private val persistence: SessionGroupPersistence,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    data class UiState(
        val loaded: Boolean = false,
        val groups: List<AnalyticalGroup> = emptyList(),
        /** One-time honest notice: corrupt group lines were dropped on load. */
        val droppedNotice: Boolean = false,
        /** Last explicit action error (honest, user-visible, cleared by dismiss). */
        val lastError: GroupError? = null,
    )

    private val repository = SessionGroupRepository(
        persistence,
        clock = { System.currentTimeMillis() },
    )

    /** Compose-observed state; updated ONLY via the main handler. */
    val state: androidx.compose.runtime.MutableState<UiState> =
        androidx.compose.runtime.mutableStateOf(UiState())

    /** Load persisted groups once (background, then publish). */
    fun initialize() = executor.execute {
        publish(droppedNotice = repository.lastLoadIssues > 0)
    }

    /** History-record sync: drops dangling references after deletions/retention. */
    fun syncWithRecords(knownRecordIds: Set<String>) = executor.execute {
        repository.syncWithRecords(knownRecordIds)
        publish()
    }

    fun create(name: String) = executor.execute {
        publishError(repository.create(name))
    }

    fun rename(id: String, name: String) = executor.execute {
        publishError(repository.rename(id, name))
    }

    fun addRecord(id: String, recordId: String, knownRecordIds: Set<String>) =
        executor.execute {
            publishError(repository.addRecord(id, recordId, knownRecordIds))
        }

    fun removeRecord(id: String, recordId: String) = executor.execute {
        publishError(repository.removeRecord(id, recordId))
    }

    /** Confirmed group deletion: metadata ONLY, History records untouched. */
    fun delete(id: String) = executor.execute {
        publishError(repository.delete(id))
    }

    fun dismissNotices() = executor.execute {
        publish(droppedNotice = false, lastError = null)
    }

    private fun publishError(result: GroupResult) {
        publish(
            lastError = (result as? GroupResult.Error)?.reason,
        )
    }

    private fun publish(
        droppedNotice: Boolean? = null,
        lastError: GroupError? = null,
    ) {
        val snapshot = UiState(
            loaded = true,
            groups = repository.list(),
            droppedNotice = droppedNotice ?: (repository.lastLoadIssues > 0),
            lastError = lastError,
        )
        mainHandler.post { state.value = snapshot }
    }

}
