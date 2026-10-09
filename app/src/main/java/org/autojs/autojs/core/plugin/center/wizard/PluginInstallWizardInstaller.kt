package org.autojs.autojs.core.plugin.center.wizard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.center.PluginCenterProcessLogger
import org.autojs.autojs.core.plugin.center.PluginInstaller
import org.autojs.autojs.core.plugin.installer.InstallerPluginHost
import org.autojs.autojs.ui.wizard.WizardUi
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.applyProgressThemeColorTintLists
import org.autojs.autojs.util.DialogUtils.setProgressNumberFormatByBytes
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Batch installer of the plugin install wizard. Downloads and installations overlap: while one
 * plugin installs, the next one downloads, all in one progress dialog. With the installer plugin
 * the `auto` interaction installs silently whenever a privileged authorizer is usable; with the
 * system installer the "install unknown apps" access is checked once before any download. A fully
 * successful run ends with a snackbar; a summary dialog appears only for failures or cancellations.
 *
 * zh-CN: 插件安装向导的批量安装器. 下载与安装交叠进行: 一个插件安装时下载下一个, 全程使用同一个进度对话框.
 * 使用安装器插件时以 `auto` 交互方式安装, 有可用的特权授权方式时静默安装; 使用系统安装器时,
 * 在下载前检查一次 "安装未知应用" 权限. 全部成功时以 Snackbar 告知, 仅在有失败或取消时显示汇总对话框.
 */
object PluginInstallWizardInstaller {

    private const val CACHE_RESERVE_BYTES = 128L * 1024L * 1024L

    /** How long the system installer may take to cover the wizard before its return is no longer watched. */
    private const val SYSTEM_INSTALLER_LEAVE_TIMEOUT_MILLIS = 10_000L

    /** Time for the package broadcast to arrive after the user came back from the system installer. */
    private const val SYSTEM_INSTALLER_RETURN_GRACE_MILLIS = 3_000L

    private enum class State { INSTALLED, FAILED, CANCELLED }

    private class Outcome(val item: PluginInstallWizardItem, val state: State, val detail: String? = null)

    private class Downloaded(val index: Int, val item: PluginInstallWizardItem, val uri: Uri, val file: File?)

    /** Starts the installation of the selectable [items]; [onFinished] runs once everything has ended. */
    fun start(context: Context, scope: CoroutineScope, items: List<PluginInstallWizardItem>, onFinished: () -> Unit = {}) {
        scope.launch(Dispatchers.Main) {
            try {
                run(context, items.filter { it.isSelectable })
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun run(context: Context, targets: List<PluginInstallWizardItem>) {
        if (targets.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_plugin_install_wizard_no_selection)
            return
        }
        if (!confirmCompatibility(context, targets)) return
        if (!ensureCacheSpace(context, targets)) return

        val viaSystemInstaller = !InstallerPluginHost.isAvailable(context)
        if (viaSystemInstaller) ensureUnknownAppsAllowed(context)

        PluginCenterProcessLogger.i("Plugin install wizard starts: ${targets.size} plugin(s), systemInstaller=$viaSystemInstaller.")
        val outcomes = downloadAndInstall(context, targets, viaSystemInstaller)
        PluginCenterProcessLogger.i(
            "Plugin install wizard finished: " +
                    "installed=${outcomes.count { it.state == State.INSTALLED }}, " +
                    "failed=${outcomes.count { it.state == State.FAILED }}, " +
                    "cancelled=${targets.size - outcomes.count { it.state != State.CANCELLED }}.",
        )
        showResult(context, targets, outcomes)
    }

    private suspend fun confirmCompatibility(context: Context, targets: List<PluginInstallWizardItem>): Boolean {
        val flagged = targets.filter { it.isPossiblyIncompatible }
        if (flagged.isEmpty()) return true
        val action = MaterialDialog.Builder(context)
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
            .cancelable(false)
            .showAndAwait()
        return action == DialogAction.POSITIVE
    }

    private suspend fun ensureCacheSpace(context: Context, targets: List<PluginInstallWizardItem>): Boolean {
        val knownSizes = targets.mapNotNull { it.apkSizeBytes?.takeIf { size -> size > 0 } }
        if (knownSizes.isEmpty()) return true
        // Downloads overlap installations and the package installer copies each APK once more.
        // zh-CN: 下载与安装交叠进行, 且安装器在提交前会再复制一份 APK.
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
            .showAndAwait()
        return false
    }

    /**
     * Asks once for the "install unknown apps" access before downloading, so the system installer
     * does not interrupt the first installation to send the user to the settings.
     */
    private suspend fun ensureUnknownAppsAllowed(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (context.packageManager.canRequestPackageInstalls()) return
        val activity = context.findActivity() as? ComponentActivity ?: return
        ViewUtils.showToast(context, R.string.text_plugin_install_wizard_allow_unknown_apps, true)
        WizardUi.launchAndAwaitReturn(activity) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .startSafely(activity)
        }
    }

    private suspend fun downloadAndInstall(
        context: Context,
        targets: List<PluginInstallWizardItem>,
        viaSystemInstaller: Boolean,
    ): List<Outcome> = coroutineScope {
        val cancelFlag = AtomicBoolean(false)
        var currentInstall: Job? = null
        val progress = Progress(context, targets.size, viaSystemInstaller) {
            cancelFlag.set(true)
            currentInstall?.cancel()
        }
        // Both coroutines below run on the main thread, so the list needs no locking.
        // zh-CN: 下方两个协程均运行在主线程, 因此列表无需加锁.
        val outcomes = ArrayList<Outcome>(targets.size)
        val downloads = Channel<Downloaded>(Channel.UNLIMITED)
        try {
            val downloader = launch(Dispatchers.Main) {
                try {
                    for ((index, item) in targets.withIndex()) {
                        if (cancelFlag.get()) break
                        progress.onDownloadStarted(index, item)
                        val url = item.apkUrl
                        if (url.isNullOrBlank()) {
                            outcomes += Outcome(item, State.FAILED, context.getString(R.string.text_unavailable))
                            continue
                        }
                        val result = PluginInstaller.downloadToCache(context, url, item.apkSha256, cancelFlag) { bytes, size ->
                            withContext(Dispatchers.Main) { progress.onDownloadProgress(bytes, size) }
                        }
                        when (result) {
                            is PluginInstaller.DownloadResult.Success -> downloads.send(Downloaded(index, item, result.uri, result.file))
                            is PluginInstaller.DownloadResult.Cancelled -> break
                            is PluginInstaller.DownloadResult.Failure -> {
                                PluginCenterProcessLogger.i("Plugin install wizard download failed: ${item.packageName}: ${result.message}")
                                outcomes += Outcome(item, State.FAILED, result.message)
                            }
                        }
                    }
                } finally {
                    downloads.close()
                    progress.onDownloadsFinished()
                }
            }

            for (entry in downloads) {
                if (cancelFlag.get()) {
                    entry.file?.delete()
                    continue
                }
                progress.onInstallStarted(entry.index, entry.item)
                PluginCenterProcessLogger.i("Plugin install wizard installs ${entry.item.packageName} (${entry.index + 1}/${targets.size}).")
                val outcome = try {
                    install(context, entry, viaSystemInstaller) { job -> currentInstall = job }
                } finally {
                    currentInstall = null
                    entry.file?.delete()
                }
                outcomes += outcome
                progress.onInstallFinished(outcomes.count { it.state != State.CANCELLED })
            }
            downloader.join()
        } finally {
            progress.dismiss()
        }
        outcomes
    }

    /**
     * Installs one download. On the system installer path the wizard also watches its own screen:
     * once the user is back from the installer and the package has not landed shortly after, the
     * installation counts as not confirmed instead of waiting for the broadcast timeout.
     */
    private suspend fun install(
        context: Context,
        entry: Downloaded,
        viaSystemInstaller: Boolean,
        onJob: (Job) -> Unit,
    ): Outcome = coroutineScope {
        val activity = context.findActivity() as? ComponentActivity
        val installJob = async(start = CoroutineStart.LAZY) {
            PluginInstallWizardInstallAwaiter.install(context, entry.item.packageName, entry.uri)
        }
        onJob(installJob)
        var notConfirmed = false
        val watcher = if (viaSystemInstaller && activity != null) {
            // Undispatched, so the lifecycle observer is in place before the installer appears.
            // zh-CN: 不经调度立即执行, 确保在安装器出现前完成生命周期观察者的注册.
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val result = WizardUi.launchAndAwaitReturn(activity, SYSTEM_INSTALLER_LEAVE_TIMEOUT_MILLIS) { installJob.start(); true }
                    if (result == WizardUi.ReturnResult.RETURNED && !installJob.isCompleted) {
                        delay(SYSTEM_INSTALLER_RETURN_GRACE_MILLIS)
                        if (!installJob.isCompleted) {
                            notConfirmed = true
                            installJob.cancel()
                        }
                    }
                } finally {
                    installJob.start()
                }
            }
        } else {
            installJob.start()
            null
        }
        try {
            val result = installJob.await()
            // A successful broadcast can precede the installer result screen's dismissal.
            // Wait for that screen to close before starting another system installation.
            // zh-CN: 成功广播可能早于安装结果页面的关闭, 需等待返回后再启动下一次系统安装.
            if (result is PluginInstallWizardInstallAwaiter.Result.Success) watcher?.join()
            when (result) {
                is PluginInstallWizardInstallAwaiter.Result.Success -> Outcome(entry.item, State.INSTALLED)
                is PluginInstallWizardInstallAwaiter.Result.Failure -> Outcome(entry.item, State.FAILED, result.detail)
            }
        } catch (e: CancellationException) {
            ensureActive()
            when {
                notConfirmed -> Outcome(entry.item, State.FAILED, context.getString(R.string.text_plugin_install_wizard_install_not_confirmed))
                else -> Outcome(entry.item, State.CANCELLED)
            }
        } finally {
            watcher?.cancel()
        }
    }

    private suspend fun showResult(context: Context, targets: List<PluginInstallWizardItem>, outcomes: List<Outcome>) {
        val byPackage = outcomes.associateBy { it.item.packageName }
        val ordered = targets.map { item -> byPackage[item.packageName] ?: Outcome(item, State.CANCELLED) }
        val installed = ordered.filter { it.state == State.INSTALLED }
        val failed = ordered.filter { it.state == State.FAILED }
        val cancelled = ordered.filter { it.state == State.CANCELLED }

        if (failed.isEmpty() && cancelled.isEmpty()) {
            WizardUi.showSnack(context, context.getString(R.string.text_plugin_install_wizard_all_installed, installed.size))
            return
        }

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
            .showAndAwait()
    }

    /**
     * The single progress dialog of a run: one line for the download, one for the installation and,
     * on the system installer path, a hint to confirm there. The bar follows the current download,
     * then the number of finished installations once every download is done.
     */
    private class Progress(
        private val context: Context,
        private val total: Int,
        private val viaSystemInstaller: Boolean,
        onCancel: () -> Unit,
    ) {

        private var downloadLine = context.getString(R.string.text_plugin_install_wizard_waiting_for_download)
        private var installLine: String? = null
        private var downloadsFinished = false
        private var installing = false

        private val dialog: MaterialDialog = MaterialDialog.Builder(context)
            .title(R.string.text_plugin_install_wizard_installing_plugins)
            .content(downloadLine)
            .progress(false, 100, true)
            .negativeText(R.string.text_plugin_install_wizard_cancel_remaining)
            .negativeColorRes(R.color.dialog_button_caution)
            .onNegative { d, _ ->
                onCancel()
                d.getActionButton(DialogAction.NEGATIVE).isEnabled = false
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
            .also { it.applyProgressThemeColorTintLists() }

        fun onDownloadStarted(index: Int, item: PluginInstallWizardItem) {
            downloadLine = "${context.getString(R.string.text_plugin_install_wizard_downloading_with_progress, index + 1, total)}: ${item.title}"
            render()
            dialog.setProgressNumberFormat(context.getString(R.string.text_half_ellipsis))
            dialog.setProgress(0)
        }

        fun onDownloadProgress(bytes: Long, size: Long) {
            if (downloadsFinished) return
            when {
                size > 0 -> {
                    dialog.setProgressNumberFormatByBytes(bytes, size, true)
                    dialog.setProgress(((bytes * 100f) / size).coerceIn(0f, 100f).roundToInt())
                }
                bytes == 0L -> dialog.setProgressNumberFormatByBytes(0, size, true)
                else -> dialog.setProgress((dialog.currentProgress + 1).coerceAtMost(99))
            }
        }

        fun onDownloadsFinished() {
            downloadsFinished = true
            downloadLine = context.getString(R.string.text_plugin_install_wizard_downloads_finished)
            render()
        }

        fun onInstallStarted(index: Int, item: PluginInstallWizardItem) {
            installing = true
            installLine = "${context.getString(R.string.text_plugin_install_wizard_installing_with_progress, index + 1, total)}: ${item.title}"
            render()
        }

        fun onInstallFinished(finishedCount: Int) {
            installing = false
            render()
            if (downloadsFinished) {
                dialog.setProgressNumberFormat("$finishedCount/$total")
                dialog.setProgress(finishedCount * 100 / total.coerceAtLeast(1))
            }
        }

        private fun render() {
            val lines = listOfNotNull(
                downloadLine,
                installLine,
                context.getString(R.string.text_plugin_install_wizard_confirm_in_system_installer).takeIf { viaSystemInstaller && installing },
            )
            dialog.setContent(lines.joinToString("\n"))
        }

        fun dismiss() {
            runCatching { dialog.dismiss() }
        }

    }

}
