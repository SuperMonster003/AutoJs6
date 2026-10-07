package org.autojs.autojs.ui.floating.codegen

/** Main-thread state: a dialog may only cancel a transfer which is still active. */
internal class ModelTransferState {
    enum class Phase { ACTIVE, SUCCEEDED, FAILED, CLOSED }

    var phase = Phase.ACTIVE
        private set

    val acceptsProgress: Boolean get() = phase == Phase.ACTIVE

    fun succeed(): Boolean = finish(Phase.SUCCEEDED)
    fun fail(): Boolean = finish(Phase.FAILED)

    /** Returns true exactly once if closing the dialog requires cancelling its subscription. */
    fun close(): Boolean {
        val cancel = phase == Phase.ACTIVE
        phase = Phase.CLOSED
        return cancel
    }

    private fun finish(outcome: Phase): Boolean {
        if (phase != Phase.ACTIVE) return false
        phase = outcome
        return true
    }
}
