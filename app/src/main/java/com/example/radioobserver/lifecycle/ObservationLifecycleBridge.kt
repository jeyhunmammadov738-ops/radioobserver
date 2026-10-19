package com.example.radioobserver.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.radioobserver.core.radio.lifecycle.RadioObservationSupervisor

/**
 * Thin Activity->supervisor lifecycle bridge (task 2.6). Pure forwarding: the policy
 * itself lives in the pure-JVM RadioObservationSupervisor. One observer per Activity; it
 * holds no Activity references beyond the registration and cannot outlive the Activity
 * (the observer is removed automatically when the Lifecycle is destroyed).
 *
 * NOTE (honest boundary): pure JVM tests validate the SUPERVISOR's reactions; they cannot
 * prove that Android delivers these callbacks on a real device. Real-device lifecycle
 * delivery and OEM behavior remain UNVERIFIED in this device-free iteration.
 */
class ObservationLifecycleBridge(
    lifecycleOwner: LifecycleOwner,
    private val supervisor: RadioObservationSupervisor,
) : DefaultLifecycleObserver {

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        supervisor.onHostResumed()
    }

    override fun onPause(owner: LifecycleOwner) {
        supervisor.onHostPaused()
    }

    override fun onStop(owner: LifecycleOwner) {
        supervisor.onHostStopped()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        supervisor.onHostDestroyed()
    }
}
