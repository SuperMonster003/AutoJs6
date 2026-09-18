package org.autojs.autojs.core.console

import android.util.Log
import org.autojs.autojs.core.console.log.Level
import org.autojs.autojs.core.console.log.Logger
import org.autojs.autojs.tool.UiHandler

/**
 * The process-wide console every runtime console forwards to. Besides retaining the entries it
 * mirrors each line (with the `HH:mm:ss.SSS/D: ` prefix) to the global file logger configured by
 * `console.setGlobalLogConfig` (see [org.autojs.autojs.core.console.log.LogConfigurator]) and to logcat, and
 * returns that line, which the app forwards to the connected VSCode extension.
 *
 * zh-CN: 所有运行时控制台都会转发到的进程级控制台. 除保留条目外, 它还把每一行 (带 `HH:mm:ss.SSS/D: ` 前缀) 镜像到
 * `console.setGlobalLogConfig` 配置的全局文件日志器 (见 [org.autojs.autojs.core.console.log.LogConfigurator]) 与 logcat
 * 并返回该行, 应用会把它转发给已连接的 VSCode 扩展.
 *
 * Created by Stardust on Oct 22, 2017.
 * Transformed by SuperMonster003 on Sep 8, 2026.
 */
open class GlobalConsole(uiHandler: UiHandler) : ConsoleImpl(uiHandler) {

    override fun println(level: Int, charSequence: CharSequence): String {
        val logEntry = appendEntry(level, charSequence)
        val log = ConsoleLogFormat.formatLine(logEntry.timestamp, level, charSequence)
        LOGGER.log(toLogLevel(level), log)
        Log.d(TAG, log)
        return log
    }

    protected fun toLogLevel(level: Int): Level = when (level) {
        Log.VERBOSE, Log.DEBUG -> Level.DEBUG
        Log.INFO -> Level.INFO
        Log.WARN -> Level.WARN
        Log.ERROR -> Level.ERROR
        Log.ASSERT -> Level.FATAL
        else -> throw IllegalArgumentException("${GlobalConsole::class.java.simpleName}.toLogLevel")
    }

    protected fun getLevelChar(level: Int): String = ConsoleLogFormat.levelChar(level)

    companion object {

        private val TAG = GlobalConsole::class.java.simpleName
        private val LOGGER: Logger = Logger.getLogger(GlobalConsole::class.java)

    }

}
