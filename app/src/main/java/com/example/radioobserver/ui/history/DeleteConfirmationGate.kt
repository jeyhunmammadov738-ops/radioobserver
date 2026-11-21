package com.example.radioobserver.ui.history

/**
 * UX-05: transient, presentation-only confirmation gate for ONE saved record.
 *
 * Guarantees, all host-tested: a request binds exactly one stable record id; cancel
 * clears it without deleting; confirm returns the bound id exactly ONCE and clears it,
 * so a double tap or recomposition cannot invoke the delete callback twice or authorize
 * a different record. The gate never touches the repository and never reports success.
 */
class DeleteConfirmationGate {
    private var pendingId: String? = null

    fun request(recordId: String) {
        pendingId = recordId
    }

    fun pending(): String? = pendingId

    fun cancel() {
        pendingId = null
    }

    fun confirm(): String? {
        val id = pendingId
        pendingId = null
        return id
    }
}
