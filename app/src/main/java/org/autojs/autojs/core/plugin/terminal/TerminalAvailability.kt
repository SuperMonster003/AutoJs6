package org.autojs.autojs.core.plugin.terminal

import org.autojs.plugin.terminal.api.TerminalIds

/** User-facing availability of the terminal plugin (plugin roadmap D25): the five states every host entry degrades through. */
enum class TerminalPluginState {
    AVAILABLE,
    NOT_INSTALLED,
    /** Disabled by Android, disabled in Plugin Center, or not authorized. */
    DISABLED,
    /** Contract or minimum host version not satisfied, or the plugin declares no terminal service / entry. */
    INCOMPATIBLE,
    /** Installed and enabled but the service could not be bound or answered with an error. */
    UNAVAILABLE,
}

/** What a host entry does for a [TerminalPluginState] (plugin roadmap D25). */
enum class TerminalRoute {
    /** Start the plugin's `TERMINAL_OPEN` Activity. */
    OPEN,
    /** Plugin Center, official scope, searching for the plugin. */
    PLUGIN_CENTER,
    /** The system app details page: Android disabled the application. */
    APPLICATION_SETTINGS,
    /** The host's plugin settings page: enable or authorize the plugin. */
    PLUGIN_SETTINGS,
    /** Dialog naming the required version, with a Plugin Center button. */
    INCOMPATIBLE_DIALOG,
    /** Toast and log; nothing the user can fix from here. */
    FAILURE_TOAST,
}

/** The package facts the policy decides on; every flag defaults to the happy path so tests name only the deviation. */
data class TerminalPluginFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean = true,
    /** The package declares an exported `org.autojs.plugin.TERMINAL` service behind the plugin permission. */
    val serviceDeclared: Boolean = true,
    /** The package declares an exported `TERMINAL_OPEN` Activity behind the plugin permission. */
    val entryDeclared: Boolean = true,
    /** `requiresHostVersion` meta-data of the terminal service (the largest one when several). */
    val requiredHostVersion: Long = TerminalIds.REQUIRED_HOST_VERSION_CODE,
    /** The running host's `versionCode`. */
    val hostVersion: Long = TerminalIds.REQUIRED_HOST_VERSION_CODE,
    val authorized: Boolean = true,
    val pluginEnabled: Boolean = true,
    /** False once a Binder call failed although the package looked fine. */
    val bindable: Boolean = true,
) {
    val requiresNewerHost: Boolean
        get() = requiredHostVersion > hostVersion
}

/** The decision table of plugin roadmap D25; pure so the JVM tests cover every row. */
object TerminalAvailabilityPolicy {

    fun state(facts: TerminalPluginFacts): TerminalPluginState = when {
        !facts.installed -> TerminalPluginState.NOT_INSTALLED
        !facts.applicationEnabled -> TerminalPluginState.DISABLED
        !facts.serviceDeclared || !facts.entryDeclared || facts.requiresNewerHost -> TerminalPluginState.INCOMPATIBLE
        !facts.authorized || !facts.pluginEnabled -> TerminalPluginState.DISABLED
        !facts.bindable -> TerminalPluginState.UNAVAILABLE
        else -> TerminalPluginState.AVAILABLE
    }

    fun route(facts: TerminalPluginFacts): TerminalRoute = when (state(facts)) {
        TerminalPluginState.AVAILABLE -> TerminalRoute.OPEN
        TerminalPluginState.NOT_INSTALLED -> TerminalRoute.PLUGIN_CENTER
        TerminalPluginState.DISABLED -> if (!facts.applicationEnabled) TerminalRoute.APPLICATION_SETTINGS else TerminalRoute.PLUGIN_SETTINGS
        TerminalPluginState.INCOMPATIBLE -> TerminalRoute.INCOMPATIBLE_DIALOG
        TerminalPluginState.UNAVAILABLE -> TerminalRoute.FAILURE_TOAST
    }
}

/** The outcome of one package inspection: the facts, and the state and route derived from them. */
data class TerminalInspection(val facts: TerminalPluginFacts) {
    val state: TerminalPluginState get() = TerminalAvailabilityPolicy.state(facts)
    val route: TerminalRoute get() = TerminalAvailabilityPolicy.route(facts)
    val available: Boolean get() = state == TerminalPluginState.AVAILABLE
}
