package org.autojs.autojs.core.plugin.center

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PluginCenterProcessLogger {

    private const val MAX_LINES = 300

    private val sdf
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val lines = ArrayDeque<String>()
    private val internalFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    val flow: SharedFlow<String> = internalFlow.asSharedFlow()

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }

    @Synchronized
    fun i(msg: String) {
        val line = "${sdf.format(Date())}: $msg"
        lines += line
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }
        internalFlow.tryEmit(line)
    }
}

