package org.autojs.autojs.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.*

object ProcessLogger {

    private val sdf
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val buffer = StringBuilder()

    /* 用 SharedFlow 推送 "增量行". */
    private val _flow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val flow: SharedFlow<String> = _flow.asSharedFlow()

    @Synchronized
    fun dump(): String = buffer.toString()

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun i(msg: String) {
        val suffix = if (buffer.isNotEmpty()) "\n" else ""
        val line = "$suffix${sdf.format(Date())}: $msg"
        buffer.append(line)
        _flow.tryEmit(line)
    }

}