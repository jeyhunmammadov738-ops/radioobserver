package com.example.radioobserver.core.radio.pipeline

import com.example.radioobserver.core.radio.boundary.ObservationEvent
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener

/**
 * Feeds session events into the pipeline (task 2.7). Only DATA events are handled:
 * DeviceObserved (BLE) and WifiResultsAvailable observations. Lifecycle events
 * (SessionStarted/Ended, SourceFailed) are deliberately NOT acted on here — session
 * lifecycle, cleanup and foreground policy belong to the coordinators and supervisor
 * (2.3-2.6), so one radio's session ending never corrupts the other radio's data.
 *
 * ACTIVATION STATE (honest note): nothing in production wires a session to this listener
 * yet — an explicit user-start path is Phase 3 scope. The pipeline is inert until then.
 */
class PipelineObservationListener(
    private val pipeline: ObservationPipeline,
) : ObservationSessionListener {

    override fun onObservationEvent(event: ObservationEvent) {
        when (event) {
            is ObservationEvent.DeviceObserved ->
                pipeline.submit(event.device)
            is ObservationEvent.WifiResultsAvailable ->
                event.observations.forEach { pipeline.submit(it) }
            // Non-data events are intentionally ignored (see class doc).
            is ObservationEvent.SessionStarted,
            is ObservationEvent.SessionEnded,
            is ObservationEvent.SourceFailed -> Unit
        }
    }
}
