package org.autojs.autojs.runtime

import android.os.SystemClock
import org.autojs.autojs.extension.NumberExtensions.jsString
import org.autojs.autojs.runtime.api.augment.console.Console
import java.util.concurrent.ConcurrentHashMap

class ConsoleTimeTable(private val scriptRuntime: ScriptRuntime) {

    private val mData = ConcurrentHashMap<String, Double>()

    @Synchronized
    fun save(label: String? = null) {
        mData[parseLabel(label)] = SystemClock.uptimeMillis().toDouble()
    }

    @Synchronized
    fun print(label: String? = null) {
        val gap = SystemClock.uptimeMillis() - (mData[parseLabel(label)] ?: Double.NaN)
        Console.log(scriptRuntime, arrayOf("${parseLabel(label)}: ${gap.jsString}ms"))
        mData.remove(parseLabel(label))
    }

    companion object {

        private const val DEFAULT_LABEL = "default"

        private fun parseLabel(label: String?) = label ?: DEFAULT_LABEL

    }

}
