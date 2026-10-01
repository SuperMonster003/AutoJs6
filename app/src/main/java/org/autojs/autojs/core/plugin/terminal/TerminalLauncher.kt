package org.autojs.autojs.core.plugin.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginSettingsActivity
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.plugin.terminal.api.TerminalActions

/**
 * The single host entry to the terminal plugin (plugin roadmap D25). Every caller (drawer item,
 * Explorer directory menu, project toolbar, script API later) goes through [launch], which
 * inspects the package and either starts the plugin's exported `TERMINAL_OPEN` Activity or
 * degrades: Plugin Center when not installed, the plugin settings page when disabled or not
 * authorized, the app details page when Android disabled it, a dialog naming the required
 * version when incompatible, and a toast plus log when the plugin cannot answer.
 */
object TerminalLauncher {

    private const val TAG = "TerminalLauncher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun open(context: Context, directory: String? = null): Boolean = launch(context, TerminalOpenRequest.open(directory))

    fun openNew(context: Context, directory: String? = null): Boolean = launch(context, TerminalOpenRequest.openNew(directory))

    fun openSession(context: Context, sessionId: String): Boolean = launch(context, TerminalOpenRequest.session(sessionId))

    fun openManager(context: Context): Boolean = launch(context, TerminalOpenRequest.manager())

    /** Closes every session through the Binder; failures surface as a toast and the monitor is refreshed either way. */
    fun closeAll(context: Context) {
        val applicationContext = context.applicationContext
        scope.launch {
            val closed = withContext(Dispatchers.IO) {
                runCatching { TerminalPluginHost.closeAllSessions(applicationContext) }
            }
            closed.onFailure { error ->
                Log.w(TAG, "closeAllSessions failed: ${error.message}")
                ViewUtils.showToast(applicationContext, R.string.text_terminal_plugin_unavailable, true)
            }
            TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED)
        }
    }

    /** Routes [request] per the inspected state; true when the terminal UI itself was started. */
    fun launch(context: Context, request: TerminalOpenRequest): Boolean {
        request.validate()?.let { reason ->
            Log.w(TAG, "rejected terminal request: $reason")
            return false
        }
        val inspection = TerminalPluginInspector.inspect(context)
        return when (inspection.route) {
            TerminalRoute.OPEN -> startEntry(context, request)
            TerminalRoute.PLUGIN_CENTER -> false.also { openPluginCenter(context) }
            TerminalRoute.APPLICATION_SETTINGS -> false.also { IntentUtils.launchAppDetailsSettings(context, ThreeShellTerminalOfficialPlugin.PACKAGE_NAME) }
            TerminalRoute.PLUGIN_SETTINGS -> false.also { openPluginSettings(context) }
            TerminalRoute.INCOMPATIBLE_DIALOG -> false.also { showIncompatibleDialog(context, inspection) }
            TerminalRoute.FAILURE_TOAST -> false.also { reportUnavailable(context, inspection.facts.toString()) }
        }
    }

    /** The explicit Intent for [component] carrying the extras of [request] (plugin roadmap D19). */
    fun intent(component: ComponentName, request: TerminalOpenRequest): Intent = Intent(TerminalActions.OPEN_TERMINAL).apply {
        this.component = component
        addCategory(Intent.CATEGORY_DEFAULT)
        request.extras().forEach { (key, value) ->
            when (value) {
                is Boolean -> putExtra(key, value)
                else -> putExtra(key, value.toString())
            }
        }
    }

    fun openPluginCenter(context: Context) {
        PluginCenterActivity.launch(context, sourceScope = PluginIndexSource.OFFICIAL, initialQuery = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME)
    }

    fun openPluginSettings(context: Context) {
        PluginSettingsActivity.launch(
            context = context,
            packageName = ThreeShellTerminalOfficialPlugin.PACKAGE_NAME,
            title = ThreeShellTerminalOfficialPlugin.DISPLAY_NAME,
            source = PluginIndexSource.OFFICIAL,
        )
    }

    private fun startEntry(context: Context, request: TerminalOpenRequest): Boolean {
        val component = TerminalPluginInspector.resolveEntry(context)
        if (component == null) {
            showIncompatibleDialog(context, TerminalPluginInspector.inspect(context))
            return false
        }
        val started = intent(component, request).startSafely(context, printStackTrace = true) { error ->
            reportUnavailable(context, "cannot start ${component.flattenToShortString()}: ${error.message}")
        }
        if (!started) return false
        TerminalPluginStateMonitor.refresh(TerminalPluginStateMonitor.Cause.REFRESHED)
        return true
    }

    private fun showIncompatibleDialog(context: Context, inspection: TerminalInspection) {
        val facts = inspection.facts
        val content = if (facts.requiresNewerHost) {
            context.getString(R.string.prompt_terminal_plugin_requires_host, facts.requiredHostVersion)
        } else {
            context.getString(R.string.prompt_terminal_plugin_update_required)
        }
        runCatching {
            MaterialDialog.Builder(context)
                .title(R.string.text_terminal)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ -> openPluginCenter(context) }
                .show()
        }.onFailure {
            Log.w(TAG, "incompatible terminal plugin: $content")
            ViewUtils.showToast(context, content, true)
        }
    }

    private fun reportUnavailable(context: Context, detail: String) {
        Log.w(TAG, "terminal plugin unavailable: $detail")
        ViewUtils.showToast(context, R.string.text_terminal_plugin_unavailable, true)
    }
}
