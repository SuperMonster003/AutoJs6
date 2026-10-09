package org.autojs.autojs.core.plugin.center

/** Plans component overrides without changing a plugin's application or service state. */
internal object PluginLauncherIconPolicy {
    const val DEFAULT = 0
    const val DISABLED = 2

    fun plan(
        show: Boolean,
        current: Map<String, Int>,
        saved: Map<String, Int>?,
        hasVisibleEntry: Boolean,
    ): Map<String, Int> = when {
        !show -> current.mapValues { DISABLED }
        saved != null -> current.mapValues { (name, _) -> saved[name] ?: DEFAULT }
        hasVisibleEntry -> current
        else -> current.mapValues { DEFAULT }
    }

    fun appliesToNewInstall(installedAtStart: Boolean, installedBeforeAttempt: Boolean, succeeded: Boolean): Boolean =
        succeeded && !installedAtStart && !installedBeforeAttempt

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
