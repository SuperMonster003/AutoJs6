package org.autojs.autojs.ui.edit.editor.ace.diagnostics

import android.os.SystemClock
import java.util.ArrayDeque

class AceRuntimeEventHistory(
    private val maxEvents: Int = MAX_EVENTS,
) {

    private val events = ArrayDeque<String>()

    fun record(type: String, detail: String? = null, atUptimeMillis: Long = SystemClock.uptimeMillis()) {
        val safeDetail = detail?.takeIf { it.isNotBlank() }
        val entry = if (safeDetail == null) {
            "$atUptimeMillis:$type"
        } else {
            "$atUptimeMillis:$type:$safeDetail"
        }
        events.addLast(entry)
        while (events.size > maxEvents) {
            events.removeFirst()
        }
    }

    fun snapshot(): List<String> = events.toList()

    companion object {
        const val MAX_EVENTS = 30
    }
}
