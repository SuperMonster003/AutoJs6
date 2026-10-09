package org.autojs.autojs.core.plugin.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single entry every host installation goes through (plugin roadmap D21, D3, D4).
 *
 * With an available installer plugin the package is handed to it over Binder with the `dialog`
 * interaction, so the plugin's own confirmation, progress and result dialogs appear. Without the
 * plugin a plain APK goes to the system installer through a `content://` URI, and any other
 * package format shows the guide to install the plugin, because the system installer does not
 * accept split containers.
 *
 * zh-CN: 宿主所有安装请求的唯一入口. 插件可用时经 Binder 交给插件 (插件自己的确认 / 进度 / 结果对话框);
 * 插件不可用时, 普通 APK 交给系统安装器, 其它格式提示安装插件.
 */
object PackageInstallRouter {

    private const val TAG = "PackageInstallRouter"

    /** How long a caller waits for the system installer to land a package before giving up. */
    const val SYSTEM_INSTALL_AWAIT_MILLIS = 5L * 60L * 1000L

    /** Package formats the host recognizes; `AAB` is never installable, the rest need the plugin. */
    enum class PackageFormat(val extension: String, val systemInstallable: Boolean, val installable: Boolean = true) {
        APK("apk", systemInstallable = true),
        APKS("apks", systemInstallable = false),
        XAPK("xapk", systemInstallable = false),
        APKM("apkm", systemInstallable = false),
        APKZ("apkz", systemInstallable = false),
        AAB("aab", systemInstallable = false, installable = false),
        ;

        companion object {
            fun fromDisplayName(displayName: String?): PackageFormat? {
                val extension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return null
                return entries.firstOrNull { it.extension == extension }
            }
        }
    }

    /** Terminal result of [installAndAwait]. */
    sealed interface AwaitResult {
        data object Success : AwaitResult
        data class Failure(val detail: String) : AwaitResult
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hintShown = AtomicBoolean(false)

    /** Installs a file the host owns (a build output, a download, a file-manager item). Returns false only when nothing could be started. */
    @JvmStatic
    fun install(context: Context, packageFile: File): Boolean {
        val format = PackageFormat.fromDisplayName(packageFile.name)
        scope.launch { route(context, format, packageFile.name, { InstallerSource.fromFile(packageFile) }, { AppFileProvider.getUriForFile(context, packageFile) }) }
        return true
    }

    /** Installs a `content://` or `file://` package chosen through the system picker. */
    @JvmStatic
    fun install(context: Context, packageUri: Uri, displayName: String? = null): Boolean {
        val name = displayName ?: packageUri.lastPathSegment
        val format = PackageFormat.fromDisplayName(name)
        scope.launch { route(context, format, name, { InstallerSource.fromUri(context, packageUri, fallbackName = name) }, { packageUri }) }
        return true
    }

    /**
     * Installs [packageUri] and suspends until [packageName] landed or the attempt ended (plugin
     * roadmap D22): the plugin path awaits the session outcome, the system path awaits the
     * package broadcast up to [SYSTEM_INSTALL_AWAIT_MILLIS]. [interaction] applies to the plugin
     * path; `auto` installs silently when the plugin has a usable privileged authorizer.
     */
    suspend fun installAndAwait(
        context: Context,
        packageUri: Uri,
        packageName: String,
        displayName: String? = null,
        interaction: String = InstallerContract.INTERACTION_DIALOG,
    ): AwaitResult {
        val name = displayName ?: packageUri.lastPathSegment
        val format = PackageFormat.fromDisplayName(name)
        if (format != null && !format.installable) {
            return AwaitResult.Failure(context.getString(R.string.apk_info_installation_aab))
        }
        if (InstallerPluginHost.isAvailable(context)) {
            val outcome = runCatching {
                val source = withContext(Dispatchers.IO) { InstallerSource.fromUri(context, packageUri, fallbackName = name) }
                val handle = try {
                    InstallerPluginHost.openSession(context, listOf(source), interaction)
                } catch (failure: Throwable) {
                    source.close()
                    throw failure
                }
                handle.use { awaitAndCleanOwnedSources(it, listOf(source)) }
            }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                val error = InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.OPEN)
                if (error.code != InstallerErrorCodes.PLUGIN_UNAVAILABLE) return AwaitResult.Failure(error.message)
                Log.w(TAG, "installer plugin unavailable while awaiting $packageName, using the system installer", failure)
                null
            }
            when (outcome) {
                is InstallerSessionOutcome.Completed -> return if (outcome.completion.allOk) AwaitResult.Success else AwaitResult.Failure(outcome.completion.results.firstNotNullOfOrNull { it.error?.message } ?: outcome.completion.json)
                is InstallerSessionOutcome.Failed -> return AwaitResult.Failure(outcome.error.message)
                null -> Unit
            }
        }
        if (format != null && !format.systemInstallable) {
            withContext(Dispatchers.Main) { InstallerPluginUi.showUnavailableDialog(context, format) }
            return AwaitResult.Failure(context.getString(R.string.error_installer_plugin_required_for_package_format, format.extension.uppercase(Locale.ROOT), ThreeSetupInstallerOfficialPlugin.DISPLAY_NAME))
        }
        return coroutineScope {
            // Register before launching so a fast install cannot outrun its receiver.
            // zh-CN: 启动前先注册接收器, 避免快速安装的广播早于监听注册.
            val installed = async(start = CoroutineStart.UNDISPATCHED) {
                PackageChangeObserver.awaitInstalled(context, packageName, SYSTEM_INSTALL_AWAIT_MILLIS)
            }
            try {
                val launched = withContext(Dispatchers.Main) { launchSystemInstaller(context, packageUri) }
                when {
                    !launched -> AwaitResult.Failure(context.getString(R.string.error_no_applications_available_for_installing_this_file))
                    installed.await() -> AwaitResult.Success
                    else -> AwaitResult.Failure(context.getString(R.string.text_plugin_install_wizard_install_not_started))
                }
            } finally {
                installed.cancel()
            }
        }
    }

    private suspend fun route(
        context: Context,
        format: PackageFormat?,
        displayName: String?,
        openSource: () -> InstallerSource,
        systemUri: () -> Uri,
    ) {
        if (format != null && !format.installable) {
            Toast.makeText(context, R.string.apk_info_installation_aab, Toast.LENGTH_LONG).show()
            return
        }
        if (InstallerPluginHost.isAvailable(context)) {
            val started = runCatching {
                val source = withContext(Dispatchers.IO) { openSource() }
                val handle = try {
                    InstallerPluginHost.openSession(context, listOf(source), InstallerContract.INTERACTION_DIALOG)
                } catch (failure: Throwable) {
                    source.close()
                    throw failure
                }
                scope.launch(Dispatchers.IO) { handle.use { awaitAndCleanOwnedSources(it, listOf(source)) } }
                true
            }.getOrElse { failure ->
                val error = InstallerErrorMapper.fromThrowable(failure, InstallerErrorMapper.Phase.OPEN)
                Log.w(TAG, "installer plugin could not take $displayName: ${error.code}", failure)
                if (error.code != InstallerErrorCodes.PLUGIN_UNAVAILABLE) {
                    Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
                    return
                }
                false
            }
            if (started) return
        }
        if (format != null && !format.systemInstallable) {
            InstallerPluginUi.showUnavailableDialog(context, format)
            return
        }
        launchSystemInstaller(context, systemUri())
    }

    /** Host UI uses the same owned-file rules as scripts, on IO and before closing the handle. */
    internal suspend fun awaitAndCleanOwnedSources(handle: InstallerSessionHandle, sources: List<InstallerSource>): InstallerSessionOutcome {
        val outcome = handle.await()
        if (outcome !is InstallerSessionOutcome.Completed || !outcome.completion.sourceProfilesEnabled) return outcome
        // Once a real terminal success is known, a cancelled UI waiter must not turn that fact
        // into CANCELLED at the dispatcher handoff. Cleanup remains local and closes no handle.
        return withContext(Dispatchers.IO + NonCancellable) {
            val groups = sources.map { it.item }.distinct()
            val owned = sources.map { source -> object : InstallerSourceDeletion.Source {
                override val item = groups.indexOf(source.item)
                override val isOwnedFile = source.ownedFile != null
                override val ownershipKey = source.ownershipKey
                override val retentionKey = source.retentionKey
                override fun deleteIfUnchanged() = source.deleteOwnedFileIfUnchanged()
            } }
            outcome.copy(completion = InstallerSourceDeletion.apply(outcome.completion, owned, requested = false))
        }
    }

    /** Hands an APK to the system installer; the one-time hint names the plugin that would do more. */
    fun launchSystemInstaller(context: Context, packageUri: Uri): Boolean {
        val launched = Intent(Intent.ACTION_VIEW)
            .setDataAndType(packageUri, Mime.APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .startSafely(context, true) {
                Toast.makeText(context, R.string.error_no_applications_available_for_installing_this_file, Toast.LENGTH_LONG).show()
            }
        if (launched && hintShown.compareAndSet(false, true) && !PackageChangeObserver.isPackageInstalled(context, ThreeSetupInstallerOfficialPlugin.PACKAGE_NAME)) {
            Toast.makeText(context, context.getString(R.string.hint_installer_plugin_recommended, ThreeSetupInstallerOfficialPlugin.DISPLAY_NAME), Toast.LENGTH_LONG).show()
        }
        return launched
    }
}
