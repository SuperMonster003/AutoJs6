package org.autojs.autojs.ui.edit.editor.ace

import android.os.SystemClock

class AceImeController(
    private val coalesceWindowMillis: Long,
) {

    var state: AceImeState = AceImeState.HIDDEN
        private set

    var lastImeHeight: Int = 0
        private set

    var lastImeRequestAtUptimeMillis: Long = 0L
        private set

    var lastShowSoftInputAtUptimeMillis: Long = 0L
        private set

    var lastHideSoftInputAtUptimeMillis: Long = 0L
        private set

    var lastImeTransitionStartedAtUptimeMillis: Long = 0L
        private set

    var lastImeTransitionEndedAtUptimeMillis: Long = 0L
        private set

    var lastIgnoredRequestReason: String? = null
        private set

    private var lastAcceptedRequestAtUptimeMillis: Long = 0L

    val isTransitioning: Boolean
        get() = state == AceImeState.SHOWING || state == AceImeState.HIDING

    fun requestShow(now: Long = SystemClock.uptimeMillis()): Decision {
        lastImeRequestAtUptimeMillis = now
        return when {
            isCoalesced(now) -> ignore(Request.SHOW, "coalesced:${state.name.lowercase()}", now)
            state == AceImeState.VISIBLE -> ignore(Request.SHOW, "already_visible", now)
            state == AceImeState.SHOWING -> ignore(Request.SHOW, "already_showing", now)
            state == AceImeState.HIDING -> ignore(Request.SHOW, "reverse_during_hiding", now)
            else -> accept(Request.SHOW, AceImeState.SHOWING, now)
        }
    }

    fun requestHide(now: Long = SystemClock.uptimeMillis()): Decision {
        lastImeRequestAtUptimeMillis = now
        return when {
            isCoalesced(now) -> ignore(Request.HIDE, "coalesced:${state.name.lowercase()}", now)
            state == AceImeState.HIDDEN -> ignore(Request.HIDE, "already_hidden", now)
            state == AceImeState.HIDING -> ignore(Request.HIDE, "already_hiding", now)
            state == AceImeState.SHOWING -> ignore(Request.HIDE, "reverse_during_showing", now)
            else -> accept(Request.HIDE, AceImeState.HIDING, now)
        }
    }

    fun onHostImeInsetsChanged(visible: Boolean, bottomInset: Int, now: Long = SystemClock.uptimeMillis()) {
        lastImeHeight = if (visible) bottomInset.coerceAtLeast(0) else 0
        val next = if (visible) AceImeState.VISIBLE else AceImeState.HIDDEN
        if (state != next) {
            if (!isTransitioning) {
                lastImeTransitionStartedAtUptimeMillis = now
            }
            state = next
            lastImeTransitionEndedAtUptimeMillis = now
        }
    }

    private fun isCoalesced(now: Long): Boolean {
        val lastAccepted = lastAcceptedRequestAtUptimeMillis
        return lastAccepted > 0L && now - lastAccepted < coalesceWindowMillis
    }

    private fun accept(request: Request, next: AceImeState, now: Long): Decision {
        lastAcceptedRequestAtUptimeMillis = now
        lastIgnoredRequestReason = null
        lastImeTransitionStartedAtUptimeMillis = now
        state = next
        when (request) {
            Request.SHOW -> lastShowSoftInputAtUptimeMillis = now
            Request.HIDE -> lastHideSoftInputAtUptimeMillis = now
        }
        return Decision(request = request, accepted = true, reason = "accepted", atUptimeMillis = now)
    }

    private fun ignore(request: Request, reason: String, now: Long): Decision {
        lastIgnoredRequestReason = reason
        return Decision(request = request, accepted = false, reason = reason, atUptimeMillis = now)
    }

    enum class Request {
        SHOW,
        HIDE,
    }

    data class Decision(
        val request: Request,
        val accepted: Boolean,
        val reason: String,
        val atUptimeMillis: Long,
    )
}
