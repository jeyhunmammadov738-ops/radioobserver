package com.example.radioobserver.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteConfirmationGateTest {

    @Test
    fun `no pending identity before any request`() {
        assertNull(DeleteConfirmationGate().pending())
    }

    @Test
    fun `request binds exactly the requested record`() {
        val gate = DeleteConfirmationGate()
        gate.request("rec-1")
        assertEquals("rec-1", gate.pending())
    }

    @Test
    fun `confirm returns the bound id exactly once`() {
        val gate = DeleteConfirmationGate()
        gate.request("rec-1")
        assertEquals("rec-1", gate.confirm())
        assertNull(gate.pending())
        assertNull(gate.confirm())
    }

    @Test
    fun `cancel clears without authorizing deletion`() {
        val gate = DeleteConfirmationGate()
        gate.request("rec-1")
        gate.cancel()
        assertNull(gate.pending())
        assertNull(gate.confirm())
    }

    @Test
    fun `a later request replaces the previous identity`() {
        val gate = DeleteConfirmationGate()
        gate.request("rec-1")
        gate.request("rec-2")
        assertEquals("rec-2", gate.confirm())
        assertNull(gate.confirm())
    }

    @Test
    fun `confirm without a request never authorizes a delete`() {
        assertNull(DeleteConfirmationGate().confirm())
    }
}
