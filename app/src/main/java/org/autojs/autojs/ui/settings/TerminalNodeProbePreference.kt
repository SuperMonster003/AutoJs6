package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.terminal.NodeCliLocator
import org.autojs.autojs.core.terminal.NodeCliLocator.Resolution
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Developer option that re-runs the Node.js launcher check of the terminal and shows the raw result.
 * zh-CN: 重新执行终端的 Node.js 启动器检测并显示原始结果的开发者选项.
 */
class TerminalNodeProbePreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onClick() {
        val progress = MaterialDialog.Builder(context)
            .title(R.string.text_terminal_node_probe)
            .content(R.string.text_terminal_node_probe_running)
            .progress(true, 0)
            .cancelable(false)
            .showAdaptive()
        CoroutineScope(Dispatchers.Main).launch {
            val report = withContext(Dispatchers.IO) {
                runCatching { render(NodeCliLocator.resolve(context.applicationContext, refresh = true)) }
                    .getOrElse { "Result: error\n${it.javaClass.name}: ${it.message.orEmpty()}" }
            }
            progress.dismiss()
            MaterialDialog.Builder(context)
                .title(R.string.text_terminal_node_probe)
                .content(report)
                .neutralText(R.string.text_copy)
                .onNeutral { _, _ ->
                    ClipboardUtils.setClip(context, report)
                    ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
                }
                .positiveText(R.string.dialog_button_confirm)
                .widgetThemeColor()
                .showAdaptive()
        }
    }

    companion object {

        private const val LOGCAT_HINT = "adb logcat -b all -d | grep -E \"avc: .*(nodexe|org.autojs.autojs6)\""

        internal fun render(resolution: Resolution): String = buildString {
            when (resolution) {
                is Resolution.Available -> {
                    appendLine("Result: available")
                    appendLauncher(resolution.launcher)
                    appendLine("Probe: ${resolution.probe.summary()}")
                }
                is Resolution.Unavailable.PluginMissing -> appendLine("Result: plugin missing")
                is Resolution.Unavailable.PluginNotAuthorized -> appendLine("Result: plugin not enabled or not authorized")
                is Resolution.Unavailable.PluginTooOld -> {
                    appendLine("Result: plugin has no terminal launcher")
                    appendLine("Plugin: ${resolution.packageName}")
                    appendLine("Reason: ${resolution.reason}")
                }
                is Resolution.Unavailable.ExecutableMissing -> {
                    appendLine("Result: launcher missing")
                    appendLine("Plugin: ${resolution.packageName}")
                    appendLine("ABI: ${resolution.abi}")
                    appendLine("Expected: ${resolution.executableName}")
                }
                is Resolution.Unavailable.SetupFailed -> {
                    appendLine("Result: npm / corepack setup failed")
                    appendLauncher(resolution.launcher)
                    appendLine("Error: ${resolution.message}")
                }
                is Resolution.Unavailable.ExecDenied -> {
                    appendLine("Result: launcher could not run")
                    appendLauncher(resolution.launcher)
                    appendLine("Probe: ${resolution.probe.summary()}")
                    resolution.probe.stderr.trim().takeIf { it.isNotEmpty() }?.let { appendLine("stderr: ${it.take(600)}") }
                    appendLine()
                    appendLine("Hint: $LOGCAT_HINT")
                }
            }
        }.trimEnd()

        private fun StringBuilder.appendLauncher(launcher: NodeCliLocator.Launcher) {
            appendLine("Plugin: ${launcher.packageName} (${launcher.versionCode})")
            appendLine("Launcher: ${launcher.executable.path}")
            appendLine("Commands: ${launcher.descriptor.commands.joinToString(", ")}")
            launcher.descriptor.npmVersion?.let { appendLine("npm: $it") }
            launcher.descriptor.corepackVersion?.let { appendLine("corepack: $it") }
        }

    }

}
