package org.autojs.autojs.core.console.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the repository the way `console.setGlobalLogConfig` / `resetGlobalLogConfig` and the `LogConfigurator` /
 * `Level` / `LogManager` script globals do. The logcat appender is disabled because `android.util.Log` is a stub on
 * the JVM.
 */
class LogConfiguratorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        LogManager.resetConfiguration()
        file = File(temp.root, "logs/android-log4j.log")
    }

    @After
    fun tearDown() = LogManager.resetConfiguration()

    private fun configurator(block: LogConfigurator.() -> Unit = {}) = LogConfigurator().apply {
        fileName = file.path
        isUseLogCatAppender = false
        block()
    }

    private fun read(): String = if (file.exists()) file.readText(Charsets.UTF_8) else "<missing>"

    @Test
    fun defaultsMatchAndroidLoggingLog4j() {
        val config = LogConfigurator()
        assertEquals("android-log4j.log", config.fileName)
        assertEquals("%d - [%p::%c::%C] - %m%n", config.filePattern)
        assertEquals("%m%n", config.logCatPattern)
        assertEquals(5, config.maxBackupSize)
        assertEquals(512L * 1024, config.maxFileSize)
        assertSame(Level.DEBUG, config.rootLevel)
        assertTrue(config.isImmediateFlush)
        assertTrue(config.isUseFileAppender)
        assertTrue(config.isUseLogCatAppender)
        assertTrue(config.isResetConfiguration)
        assertFalse(config.isInternalDebugging)
    }

    @Test
    fun configureInstallsFileAppenderWithRootLevelFilter() {
        configurator {
            rootLevel = Level.INFO
            filePattern = "%p %c{1} %m%n"
        }.configure()

        val logger = LogManager.getLogger("org.example.Worker")
        logger.debug("filtered")
        logger.info("kept")
        logger.error("also kept")
        LogManager.getRootLogger().warn("root")
        assertEquals("INFO Worker kept\nERROR Worker also kept\nWARN root root\n", read())
        assertEquals(1, LogManager.getLoggerRepository().appenderCount)
    }

    @Test
    fun consoleStyleConfigWritesPlainLinesAndRollsOver() {
        // What console.setGlobalLogConfig({ file, filePattern: '%m%n', maxFileSize, maxBackupSize, rootLevel: 'all' }) builds.
        configurator {
            filePattern = "%m%n"
            maxFileSize = 12
            maxBackupSize = 1
            isImmediateFlush = true
            isResetConfiguration = true
            rootLevel = Level.valueOf("ALL")
        }.configure()

        val logger = LogManager.getLogger("console")
        logger.trace("trace ok")
        logger.info("second")
        logger.info("third")
        assertEquals("third\n", read())
        assertEquals("trace ok\nsecond\n", File(temp.root, "logs/android-log4j.log.1").readText())
    }

    @Test
    fun offRootLevelSilencesEverything() {
        configurator { rootLevel = Level.OFF }.configure()
        LogManager.getLogger("x").fatal("nothing")
        LogManager.getRootLogger().fatal("nothing")
        assertEquals("", read())
        assertFalse(LogManager.getLogger("x").isEnabledFor(Level.FATAL))
    }

    @Test
    fun resetConfigurationClosesAppendersAndRestoresDefaults() {
        configurator {
            rootLevel = Level.ERROR
            filePattern = "%m%n"
        }.configure()
        LogManager.getLogger("x").level = Level.TRACE
        LogManager.getLogger("x").error("before")

        LogManager.getLoggerRepository().resetConfiguration()

        assertEquals(0, LogManager.getLoggerRepository().appenderCount)
        assertSame(LoggerRepository.DEFAULT_ROOT_LEVEL, LogManager.getRootLogger().level)
        assertNull(LogManager.getLogger("x").level)
        LogManager.getLogger("x").error("after")
        assertEquals("before\n", read())
    }

    @Test
    fun configureWithoutResetKeepsPreviousAppenders() {
        configurator { filePattern = "%m%n" }.configure()
        val second = File(temp.root, "second.log")
        LogConfigurator().apply {
            fileName = second.path
            filePattern = "2:%m%n"
            isUseLogCatAppender = false
            isResetConfiguration = false
        }.configure()

        LogManager.getLogger("x").info("both")
        assertEquals(2, LogManager.getLoggerRepository().appenderCount)
        assertEquals("both\n", read())
        assertEquals("2:both\n", second.readText())
    }

    @Test
    fun perLoggerLevelOverridesRoot() {
        val config = configurator {
            filePattern = "%c %p%n"
            rootLevel = Level.DEBUG
        }
        // Like log4j, configure() with resetConfiguration clears per-logger levels, so setLevel comes after it.
        config.configure()
        config.setLevel("quiet", Level.ERROR)

        LogManager.getLogger("quiet").warn("dropped")
        LogManager.getLogger("quiet").error("kept")
        LogManager.getLogger("loud").debug("kept")
        assertEquals("quiet ERROR\nloud DEBUG\n", read())
    }

    @Test
    fun useFileAppenderFalseInstallsNothingOnTheJvm() {
        configurator { isUseFileAppender = false }.configure()
        LogManager.getLogger("x").info("nowhere")
        assertEquals(0, LogManager.getLoggerRepository().appenderCount)
        assertFalse(file.exists())
    }

    @Test
    fun levelLookupFollowsLog4jRules() {
        assertSame(Level.OFF, Level.valueOf("off"))
        assertSame(Level.ALL, Level.valueOf(" All "))
        assertSame(Level.WARN, Level.toLevel("warn"))
        assertSame(Level.DEBUG, Level.toLevel("nope"))
        assertSame(Level.INFO, Level.toLevel(null, Level.INFO))
        assertSame(Level.ERROR, Level.toLevel(40000))
        try {
            Level.valueOf("verbose")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("verbose"))
        }
        assertTrue(Level.ERROR.isGreaterOrEqual(Level.WARN))
        assertFalse(Level.WARN.isGreaterOrEqual(Level.ERROR))
        assertTrue(Level.OFF > Level.FATAL && Level.FATAL > Level.ERROR && Level.ERROR > Level.WARN)
        assertTrue(Level.WARN > Level.INFO && Level.INFO > Level.DEBUG && Level.DEBUG > Level.TRACE && Level.TRACE > Level.ALL)
        assertEquals("WARN", Level.WARN.toString())
    }

    @Test
    fun loggersAreCachedByName() {
        assertSame(LogManager.getLogger("a.b"), LogManager.getLogger("a.b"))
        assertSame(LogManager.getLogger(LogConfiguratorTest::class.java), Logger.getLogger(LogConfiguratorTest::class.java.name))
        assertSame(LogManager.getRootLogger(), Logger.getRootLogger())
        assertEquals("root", LogManager.getRootLogger().name)
    }

}
