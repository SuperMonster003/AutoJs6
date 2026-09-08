package org.autojs.autojs.core.console

import android.util.Log
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.Priority
import org.autojs.autojs.tool.UiHandler

/**
 * The process-wide console every runtime console forwards to. Besides retaining the entries it
 * mirrors each line (with the `HH:mm:ss.SSS/D: ` prefix) to log4j and logcat and returns that
 * line, which the app forwards to the connected VSCode extension.
 *
 * zh-CN: 所有运行时控制台都会转发到的进程级控制台. 除保留条目外, 它还把每一行 (带 `HH:mm:ss.SSS/D: ` 前缀) 镜像到 log4j 与 logcat 并返回该行, 应用会把它转发给已连接的 VSCode 扩展.
 *
 * Created by Stardust on Oct 22, 2017.
 * Transformed by SuperMonster003 on Sep 8, 2026.
 */
open class GlobalConsole(uiHandler: UiHandler) : ConsoleImpl(uiHandler) {

    override fun println(level: Int, charSequence: CharSequence): String {
        val logEntry = appendEntry(level, charSequence)
        val log = ConsoleLogFormat.formatLine(logEntry.timestamp, level, charSequence)
        LOGGER.log(toLog4jLevel(level), log)
        Log.d(TAG, log)
        return log
    }

    protected fun toLog4jLevel(level: Int): Priority = when (level) {
        Log.VERBOSE, Log.DEBUG -> Level.DEBUG
        Log.INFO -> Level.INFO
        Log.WARN -> Level.WARN
        Log.ERROR -> Level.ERROR
        Log.ASSERT -> Level.FATAL
        else -> throw IllegalArgumentException("${GlobalConsole::class.java.simpleName}.toLog4jLevel")
    }

    protected fun getLevelChar(level: Int): String = ConsoleLogFormat.levelChar(level)

    companion object {

        private val TAG = GlobalConsole::class.java.simpleName
        private val LOGGER: Logger = Logger.getLogger(GlobalConsole::class.java)

    }

}
