package org.autojs.autojs.core.console.log

/**
 * Log level with the names and ordering of log4j 1.2's `Level`, so scripts keep writing `Level.DEBUG`,
 * `Level.OFF` and `Level.toLevel("info")` through the `Level` global (APK size roadmap P6.2, D17).
 *
 * zh-CN: 名称与顺序同 log4j 1.2 的 `Level` 的日志级别, 脚本经 `Level` 全局继续使用 `Level.DEBUG`, `Level.OFF`,
 * `Level.toLevel("info")` (APK 体积 roadmap P6.2, D17).
 */
class Level private constructor(private val levelName: String, private val levelInt: Int) : Comparable<Level> {

    fun toInt(): Int = levelInt

    fun isGreaterOrEqual(other: Level): Boolean = levelInt >= other.levelInt

    override fun compareTo(other: Level): Int = levelInt.compareTo(other.levelInt)

    override fun toString(): String = levelName

    companion object {
        @JvmField val OFF = Level("OFF", Int.MAX_VALUE)
        @JvmField val FATAL = Level("FATAL", 50000)
        @JvmField val ERROR = Level("ERROR", 40000)
        @JvmField val WARN = Level("WARN", 30000)
        @JvmField val INFO = Level("INFO", 20000)
        @JvmField val DEBUG = Level("DEBUG", 10000)
        @JvmField val TRACE = Level("TRACE", 5000)
        @JvmField val ALL = Level("ALL", Int.MIN_VALUE)

        private val LEVELS = listOf(OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL)

        /** Case-insensitive lookup that falls back to [defaultLevel], as log4j's `Level.toLevel` does. */
        @JvmStatic
        @JvmOverloads
        fun toLevel(name: String?, defaultLevel: Level = DEBUG): Level = find(name) ?: defaultLevel

        @JvmStatic
        fun toLevel(value: Int): Level = LEVELS.firstOrNull { it.levelInt == value } ?: DEBUG

        /** Strict lookup for the console API: an unknown name is an error, not a silent DEBUG. */
        @JvmStatic
        fun valueOf(name: String): Level = find(name)
            ?: throw IllegalArgumentException("Unknown log level: $name (expected one of ${LEVELS.joinToString(", ")})")

        private fun find(name: String?): Level? {
            val normalized = name?.trim()?.uppercase() ?: return null
            return LEVELS.firstOrNull { it.levelName == normalized }
        }
    }

}
