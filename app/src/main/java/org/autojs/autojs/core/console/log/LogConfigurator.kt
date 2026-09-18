package org.autojs.autojs.core.console.log

import java.io.File
import java.io.IOException

/**
 * Same surface and defaults as android-logging-log4j 1.0.3's `LogConfigurator` (the class scripts reach through the
 * `LogConfigurator` global and `console.setGlobalLogConfig` uses): `fileName` `android-log4j.log`, `filePattern`
 * `%d - [%p::%c::%C] - %m%n`, `logCatPattern` `%m%n`, `maxBackupSize` 5, `maxFileSize` 512 KiB, `immediateFlush`,
 * `useLogCatAppender`, `useFileAppender`, `resetConfiguration` all true, `rootLevel` DEBUG. [configure] resets the
 * repository when asked, installs the file and / or logcat appenders and sets the root level.
 *
 * zh-CN: 与 android-logging-log4j 1.0.3 的 `LogConfigurator` (脚本经 `LogConfigurator` 全局访问, `console.setGlobalLogConfig`
 * 使用的类) 相同的表面与默认值: `fileName` `android-log4j.log`, `filePattern` `%d - [%p::%c::%C] - %m%n`, `logCatPattern`
 * `%m%n`, `maxBackupSize` 5, `maxFileSize` 512 KiB, `immediateFlush`, `useLogCatAppender`, `useFileAppender`,
 * `resetConfiguration` 均为 true, `rootLevel` DEBUG. [configure] 按需重置仓库, 安装文件 / logcat appender 并设置根级别.
 */
class LogConfigurator() {

    var rootLevel: Level = Level.DEBUG
    var filePattern: String = DEFAULT_FILE_PATTERN
    var logCatPattern: String = DEFAULT_LOGCAT_PATTERN
    var fileName: String = DEFAULT_FILE_NAME
    var maxBackupSize: Int = DEFAULT_MAX_BACKUP_SIZE
    var maxFileSize: Long = DEFAULT_MAX_FILE_SIZE
    var isImmediateFlush: Boolean = true
    var isUseLogCatAppender: Boolean = true
    var isUseFileAppender: Boolean = true
    var isResetConfiguration: Boolean = true
    var isInternalDebugging: Boolean = false

    constructor(fileName: String) : this() {
        this.fileName = fileName
    }

    constructor(fileName: String, rootLevel: Level) : this(fileName) {
        this.rootLevel = rootLevel
    }

    constructor(fileName: String, rootLevel: Level, filePattern: String) : this(fileName, rootLevel) {
        this.filePattern = filePattern
    }

    constructor(fileName: String, maxBackupSize: Int, maxFileSize: Long, filePattern: String, rootLevel: Level) : this(fileName, rootLevel, filePattern) {
        this.maxBackupSize = maxBackupSize
        this.maxFileSize = maxFileSize
    }

    fun configure() {
        val repository = LogManager.getLoggerRepository()
        if (isResetConfiguration) repository.resetConfiguration()
        if (isUseFileAppender) repository.addAppender(createFileAppender())
        if (isUseLogCatAppender) repository.addAppender(LogCatAppender(PatternLayout(logCatPattern)))
        repository.rootLogger.level = rootLevel
    }

    /** Level of the logger named [loggerName] (log4j.properties `log4j.logger.<name>=LEVEL`). */
    fun setLevel(loggerName: String, level: Level) {
        LogManager.getLoggerRepository().getLogger(loggerName).level = level
    }

    private fun createFileAppender(): RollingFileAppender = try {
        RollingFileAppender(File(fileName), PatternLayout(filePattern), isImmediateFlush).apply {
            maxBackupIndex = maxBackupSize
            maximumFileSize = maxFileSize
        }
    } catch (e: IOException) {
        throw RuntimeException("Exception configuring log system", e)
    }

    companion object {
        const val DEFAULT_FILE_NAME = "android-log4j.log"
        const val DEFAULT_FILE_PATTERN = "%d - [%p::%c::%C] - %m%n"
        const val DEFAULT_LOGCAT_PATTERN = "%m%n"
        const val DEFAULT_MAX_BACKUP_SIZE = 5
        const val DEFAULT_MAX_FILE_SIZE = 512L * 1024
    }

}
