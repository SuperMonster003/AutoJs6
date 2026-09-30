package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCenterProcessLogger
import org.autojs.autojs.core.plugin.center.PluginInstaller
import org.autojs.autojs.util.DialogUtils.applyProgressThemeColorTintLists
import org.autojs.autojs.util.DialogUtils.setProgressNumberFormatByBytes
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Batch installer of the plugin install wizard: downloads every selected APK into
 * the plugin cache first, then installs them one after another through the regular
 * package installer, waiting for each system confirmation before starting the next.
 *
 * zh-CN: 插件安装向导的批量安装器: 先将所有选中的 APK 下载到插件缓存,
 * 再依次通过常规安装流程安装, 每个都等待系统确认完成后再开始下一个.
 */
object PluginInstallWizardInstaller {

    private const val CACHE_RESERVE_BYTES = 128L * 1024L * 1024L

    private enum class State { INSTALLED, FAILED, CANCELLED }

    private class Outcome(val item: PluginInstallWizardItem, val state: State, val detail: String? = null)

    private class Downloaded(val item: PluginInstallWizardItem, val uri: Uri, val file: File?)

    private class DownloadPhase(val downloaded: List<Downloaded>, val failures: List<Outcome>)

    fun start(context: Context, scope: CoroutineScope, items: List<PluginInstallWizardItem>) {
        val targets = items.filter { it.isSelectable }
        if (targets.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_plugin_install_wizard_no_selection)
            return
        }
        confirmCompatibility(context, targets) {
            if (!ensureCacheSpace(context, targets)) return@confirmCompatibility
            scope.launch(Dispatchers.Main) { run(context, targets) }
        }
    }

    private fun confirmCompatibility(
        context: Context,
        targets: List<PluginInstallWizardItem>,
        onContinue: () -> Unit,
    ) {
        val flagged = targets.filter { it.isPossiblyIncompatible }
        if (flagged.isEmpty()) {
            onContinue()
            return
        }
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(
                context.getString(
                    R.string.text_plugin_install_wizard_incompatible_confirm,
                    flagged.joinToString("\n") { "- ${it.title}" },
                ),
            )
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_continue)
            .positiveColorRes(R.color.dialog_button_not_recommended)
            .onPositive { _, _ -> onContinue() }
            .cancelable(false)
            .show()
    }

    private fun ensureCacheSpace(context: Context, targets: List<PluginInstallWizardItem>): Boolean {
        val knownSizes = targets.mapNotNull { it.apkSizeBytes?.takeIf { size -> size > 0 } }
        if (knownSizes.isEmpty()) return true
        // The package installer copies each APK once more before committing it.
        // zh-CN: 安装器在提交前会再复制一份 APK.
        val required = knownSizes.sum() + knownSizes.max() + CACHE_RESERVE_BYTES
        val usable = context.cacheDir.usableSpace
        if (usable >= required) return true
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(
                context.getString(
                    R.string.text_plugin_install_wizard_insufficient_space,
                    Formatter.formatFileSize(context, required),
                    Formatter.formatFileSize(context, usable),
                ),
            )
            .positiveText(R.string.dialog_button_dismiss)
            .show()
        return false
    }

    private suspend fun run(context: Context, targets: List<PluginInstallWizardItem>) {
        PluginCenterProcessLogger.i("Plugin install wizard starts: ${targets.size} plugin(s) selected.")
        val phase = downloadAll(context, targets)
        if (phase == null) {
            PluginCenterProcessLogger.i("Plugin install wizard cancelled while downloading.")
            return
        }
        val outcomes = installAll(context, phase)
        PluginCenterProcessLogger.i(
            "Plugin install wizard finished: " +
                    "installed=${outcomes.count { it.state == State.INSTALLED }}, " +
                    "failed=${outcomes.count { it.state == State.FAILED }}, " +
                    "cancelled=${outcomes.count { it.state == State.CANCELLED }}.",
        )
        showSummary(context, targets, outcomes)
    }

    /** Returns null when the user cancelled the download phase. */
    private suspend fun downloadAll(context: Context, targets: List<PluginInstallWizardItem>): DownloadPhase? {
        val cancelFlag = AtomicBoolean(false)
        val total = targets.size
        val dialog = MaterialDialog.Builder(context)
            .title(context.getString(R.string.text_plugin_install_wizard_downloading_with_progress, 1, total))
            .content(targets.first().title)
            .progress(false, 100, true)
            .positiveText(R.string.dialog_button_cancel_download)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { d, _ ->
                cancelFlag.set(true)
                d.getActionButton(DialogAction.POSITIVE).isEnabled = false
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        dialog.applyProgressThemeColorTintLists()

        val downloaded = ArrayList<Downloaded>(total)
        val failures = ArrayList<Outcome>()
        try {
            for ((index, item) in targets.withIndex()) {
                if (cancelFlag.get()) {
                    downloaded.forEach { it.file?.delete() }
                    return null
                }
                withContext(Dispatchers.Main) {
                    dialog.setTitle(context.getString(R.string.text_plugin_install_wizard_downloading_with_progress, index + 1, total))
                    dialog.setContent(item.title)
                    dialog.setProgressNumberFormat(context.getString(R.string.text_half_ellipsis))
                    dialog.setProgress(0)
                }
                val url = item.apkUrl
                if (url.isNullOrBlank()) {
                    failures += Outcome(item, State.FAILED, context.getString(R.string.text_unavailable))
                    continue
                }
                val result = PluginInstaller.downloadToCache(context, url, item.apkSha256, cancelFlag) { bytes, size ->
                    withContext(Dispatchers.Main) {
                        when {
                            size > 0 -> {
                                dialog.setProgressNumberFormatByBytes(bytes, size, true)
                                dialog.setProgress(((bytes * 100f) / size).coerceIn(0f, 100f).roundToInt())
                            }
                            bytes == 0L -> dialog.setProgressNumberFormatByBytes(0, size, true)
                            else -> dialog.setProgress((dialog.currentProgress + 1).coerceAtMost(99))
                        }
                    }
                }
                when (result) {
                    is PluginInstaller.DownloadResult.Success -> {
                        downloaded += Downloaded(item, result.uri, result.file)
                    }
                    is PluginInstaller.DownloadResult.Cancelled -> {
                        downloaded.forEach { it.file?.delete() }
                        return null
                    }
                    is PluginInstaller.DownloadResult.Failure -> {
                        PluginCenterProcessLogger.i("Plugin install wizard download failed: ${item.packageName}: ${result.message}")
                        failures += Outcome(item, State.FAILED, result.message)
                    }
                }
            }
        } finally {
            dialog.dismiss()
        }
        return DownloadPhase(downloaded, failures)
    }

    private suspend fun installAll(context: Context, phase: DownloadPhase): List<Outcome> {
        val outcomes = ArrayList<Outcome>(phase.failures)
        if (phase.downloaded.isEmpty()) return outcomes

        val total = phase.downloaded.size
        val cancelRemaining = AtomicBoolean(false)
        var currentAwait: Job? = null
        val dialog = MaterialDialog.Builder(context)
            .title(context.getString(R.string.text_plugin_install_wizard_installing_with_progress, 1, total))
            .content(installContent(context, phase.downloaded.first().item))
            .progress(true, 0)
            .negativeText(R.string.text_plugin_install_wizard_cancel_remaining)
            .negativeColorRes(R.color.dialog_button_caution)
            .onNegative { d, _ ->
                cancelRemaining.set(true)
                currentAwait?.cancel()
                d.getActionButton(DialogAction.NEGATIVE).isEnabled = false
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()

        try {
            for ((index, entry) in phase.downloaded.withIndex()) {
                if (cancelRemaining.get()) {
                    entry.file?.delete()
                    outcomes += Outcome(entry.item, State.CANCELLED)
                    continue
                }
                withContext(Dispatchers.Main) {
                    dialog.setTitle(context.getString(R.string.text_plugin_install_wizard_installing_with_progress, index + 1, total))
                    dialog.setContent(installContent(context, entry.item))
                }
                PluginCenterProcessLogger.i("Plugin install wizard installs ${entry.item.packageName} (${index + 1}/$total).")
                var result: PluginInstallWizardInstallAwaiter.Result? = null
                try {
                    coroutineScope {
                        val awaitJob = launch {
                            result = PluginInstallWizardInstallAwaiter.install(context, entry.item.packageName, entry.uri)
                        }
                        currentAwait = awaitJob
                        awaitJob.join()
                        currentAwait = null
                    }
                } finally {
                    entry.file?.delete()
                }
                outcomes += when (val r = result) {
                    is PluginInstallWizardInstallAwaiter.Result.Success -> Outcome(entry.item, State.INSTALLED)
                    is PluginInstallWizardInstallAwaiter.Result.Failure -> Outcome(entry.item, State.FAILED, r.detail)
                    null -> Outcome(entry.item, State.CANCELLED)
                }
            }
        } finally {
            dialog.dismiss()
        }
        return outcomes
    }

    private fun installContent(context: Context, item: PluginInstallWizardItem): String =
        "${item.title}\n\n${context.getString(R.string.text_plugin_install_wizard_confirm_in_system_installer)}"

    private fun showSummary(context: Context, targets: List<PluginInstallWizardItem>, outcomes: List<Outcome>) {
        val byPackage = outcomes.associateBy { it.item.packageName }
        val ordered = targets.map { item -> byPackage[item.packageName] ?: Outcome(item, State.CANCELLED) }
        val installed = ordered.filter { it.state == State.INSTALLED }
        val failed = ordered.filter { it.state == State.FAILED }
        val cancelled = ordered.filter { it.state == State.CANCELLED }

        val content = buildString {
            appendLine(context.getString(R.string.text_plugin_install_wizard_summary_installed, installed.size))
            installed.forEach { appendLine("- ${it.item.title}") }
            if (failed.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.text_plugin_install_wizard_summary_failed, failed.size))
                failed.forEach { outcome ->
                    val detail = outcome.detail?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    appendLine("- ${outcome.item.title}$detail")
                }
            }
            if (cancelled.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.text_plugin_install_wizard_summary_cancelled, cancelled.size))
                cancelled.forEach { appendLine("- ${it.item.title}") }
            }
        }.trimEnd()

        MaterialDialog.Builder(context)
            .title(R.string.text_plugin_install_wizard_summary_title)
            .content(content)
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()
    }

}
