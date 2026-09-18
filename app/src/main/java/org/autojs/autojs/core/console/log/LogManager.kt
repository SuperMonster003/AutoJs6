package org.autojs.autojs.core.console.log

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A named logger; events go to the process-wide [LoggerRepository]. Created through [getLogger], like log4j.
 * zh-CN: 具名日志器; 事件交给进程级 [LoggerRepository]. 与 log4j 一样经 [getLogger] 取得.
 */
class Logger internal constructor(val name: String) {

    /** Per-logger threshold; `null` inherits the root level. zh-CN: 日志器自身阈值; `null` 时继承根级别. */
    @Volatile
    var level: Level? = null

    fun log(level: Level, message: Any?) = LogManager.getLoggerRepository().dispatch(this, level, message?.toString() ?: "null")

    fun trace(message: Any?) = log(Level.TRACE, message)
    fun debug(message: Any?) = log(Level.DEBUG, message)
    fun info(message: Any?) = log(Level.INFO, message)
    fun warn(message: Any?) = log(Level.WARN, message)
    fun error(message: Any?) = log(Level.ERROR, message)
    fun fatal(message: Any?) = log(Level.FATAL, message)

    fun isEnabledFor(level: Level): Boolean = LogManager.getLoggerRepository().isEnabled(this, level)

    companion object {
        @JvmStatic
        fun getLogger(name: String): Logger = LogManager.getLoggerRepository().getLogger(name)

        @JvmStatic
        fun getLogger(clazz: Class<*>): Logger = LogManager.getLoggerRepository().getLogger(clazz.name)

        @JvmStatic
        fun getRootLogger(): Logger = LogManager.getRootLogger()
    }

}

/**
 * Holds the root level, the named loggers and the appenders. [resetConfiguration] closes every appender and restores
 * the defaults, which is what `console.resetGlobalLogConfig` and `LogManager.getLoggerRepository().resetConfiguration()`
 * did with log4j.
 *
 * zh-CN: 持有根级别, 具名日志器与全部 appender. [resetConfiguration] 关闭所有 appender 并恢复默认, 即 log4j 时代
 * `console.resetGlobalLogConfig` 与 `LogManager.getLoggerRepository().resetConfiguration()` 的效果.
 */
class LoggerRepository internal constructor() {

    val rootLogger: Logger = Logger(ROOT_NAME).apply { level = DEFAULT_ROOT_LEVEL }

    private val loggers = ConcurrentHashMap<String, Logger>()
    private val appenders = CopyOnWriteArrayList<Appender>()

    fun getLogger(name: String): Logger = loggers.getOrPut(name) { Logger(name) }

    fun addAppender(appender: Appender) {
        appenders.add(appender)
    }

    fun removeAllAppenders() {
        val closing = appenders.toList()
        appenders.clear()
        closing.forEach { runCatching { it.close() } }
    }

    val appenderCount: Int get() = appenders.size

    fun resetConfiguration() {
        removeAllAppenders()
        rootLogger.level = DEFAULT_ROOT_LEVEL
        loggers.values.forEach { it.level = null }
    }

    fun shutdown() = removeAllAppenders()

    fun isEnabled(logger: Logger, level: Level): Boolean {
        val threshold = logger.level ?: rootLogger.level ?: DEFAULT_ROOT_LEVEL
        return level.isGreaterOrEqual(threshold)
    }

    internal fun dispatch(logger: Logger, level: Level, message: String) {
        if (appenders.isEmpty() || !isEnabled(logger, level)) return
        val event = LogEvent(level, message, logger.name)
        appenders.forEach { appender ->
            runCatching { appender.append(event) }
        }
    }

    companion object {
        const val ROOT_NAME = "root"
        @JvmField
        val DEFAULT_ROOT_LEVEL: Level = Level.DEBUG
    }

}

/**
 * The process-wide entry scripts reach through the `LogManager` global: `LogManager.getLoggerRepository().resetConfiguration()`
 * and `LogManager.getRootLogger()`. zh-CN: 脚本经 `LogManager` 全局访问的进程级入口.
 */
object LogManager {

    private val repository = LoggerRepository()

    @JvmStatic
    fun getLoggerRepository(): LoggerRepository = repository

    @JvmStatic
    fun getRootLogger(): Logger = repository.rootLogger

    @JvmStatic
    fun getLogger(name: String): Logger = repository.getLogger(name)

    @JvmStatic
    fun getLogger(clazz: Class<*>): Logger = repository.getLogger(clazz.name)

    @JvmStatic
    fun resetConfiguration() = repository.resetConfiguration()

    @JvmStatic
    fun shutdown() = repository.shutdown()

}
