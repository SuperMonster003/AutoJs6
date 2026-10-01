package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.explorer.ExplorerActionHostFileInfoLoader
import org.autojs.autojs.core.plugin.explorer.ExplorerActionHostFileInfo
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs.ui.dialog.InfoDialogItems
import org.autojs.autojs.util.DialogUtils.makeSettingsLaunchable
import org.autojs.autojs.core.plugin.installer.PackageInstallRouter
import org.autojs.autojs6.R
import org.autojs.plugin.packagearchive.AndroidPackageArchive
import org.autojs.plugin.packagearchive.AndroidPackageArchiveInspector
import org.autojs.plugin.packagearchive.AndroidPackageFormat
import org.autojs.plugin.packagearchive.AndroidPackageSubtype
import org.autojs.plugin.packagearchive.ApkSignatureDetector
import org.autojs.plugin.packagearchive.ArchiveInstallationState
import org.autojs.plugin.packagearchive.PackageDeviceSpec

object ApkInfoDialogManager {

    @JvmStatic
    @JvmOverloads
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)? = null,
    ): InfoDialog {
        return showApkInfoDialog(context, apkFile, builderApplier, null)
    }

    @JvmStatic
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
    ): InfoDialog {
        return showPackageInfoDialog(
            context = context,
            packageFile = apkFile,
            builderApplier = builderApplier,
            onDismissListener = onDismissListener,
            installAction = { file, _ -> PackageInstallRouter.install(context, file) },
        )
    }

    @SuppressLint("SetTextI18n")
    internal fun showApkInfoDialogWithActions(
        context: Context,
        packageFile: File,
        archiveActions: Map<String, () -> Unit>,
        onDismissListener: DialogInterface.OnDismissListener?,
    ) = showPackageInfoDialog(
        context, packageFile, null, onDismissListener,
        installAction = { file, _ -> PackageInstallRouter.install(context, file) },
        archiveActions = archiveActions,
    )

    @SuppressLint("SetTextI18n")
    internal fun showPackageInfoDialog(
        context: Context,
        packageFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
        installAction: (File, String?) -> Unit,
        archiveActions: Map<String, () -> Unit> = emptyMap(),
    ): InfoDialog {
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.apk_info_format, id = "format")
            addItem(R.string.text_label_name, id = "labelName")
            addItem(R.string.apk_info_package_name, id = "packageName")
            addItem(R.string.text_version, id = "versionPlaceholder")
            addItem(R.string.apk_info_installed_version, id = "installedVersion").isVisible = false
            addItem(R.string.apk_info_file_size, id = "fileSize")
            addItem(R.string.apk_info_signature_scheme, id = "signatureScheme")
            addItem(R.string.apk_info_min_sdk, id = "minSdk")
            addItem(R.string.apk_info_target_sdk, id = "targetSdk")
            addItem(R.string.apk_info_max_sdk, id = "maxSdk").isVisible = false
            addItem(R.string.apk_info_device_sdk, id = "deviceSdk")
            addItem(R.string.apk_info_exception, id = "installation").apply {
                maxLines = 4
                isVisible = false
            }
        }
        val scope = infoDialog.scope
        var requestedPermissions: List<String>? = null
        var manifestAvailable = false
        var expectedSourceSha256: String? = null
        var activeDialog: MaterialDialog? = null
        var moreInfoDialog: InfoDialog? = null
        var pluginInfoDialog: InfoDialog? = null
        val moreInfo = MutableStateFlow<PackageMoreInfo?>(null)
        val options = archiveActions.map { (label, action) ->
            MaterialDialog.OptionMenuItemSpec(label) {
                activeDialog?.dismiss()
                action()
            }
        }.toMutableList().apply {
            add(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_more_information)) { parent ->
                if (moreInfoDialog?.dialog?.isShowing != true) {
                    moreInfoDialog = showMoreInfoDialog(context, parent, moreInfo)
                }
            })
        }

        val dialog = infoDialog.builder
            .title(packageFile.name)
            .options(options)
            .autoDismiss(false)
            .iconRes(R.drawable.ic_three_dots_outline_small)
            .limitIconToDefaultSize()
            .positiveText(R.string.text_install)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { materialDialog, _ ->
                installAction(packageFile, expectedSourceSha256)
                materialDialog.dismiss()
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .neutralText(R.string.text_manifest)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ ->
                if (manifestAvailable) {
                    DisplayManifestActivity.launch(context, packageFile, requestedPermissions)
                }
            }
            .onNegative { materialDialog, _ -> materialDialog.dismiss() }
            .also { builder -> builderApplier?.invoke(builder) }
            .let {
                infoDialog.show(onDismissListener = { dismissed ->
                    moreInfoDialog?.dialog?.dismiss()
                    pluginInfoDialog?.dialog?.dismiss()
                    onDismissListener?.onDismiss(dismissed)
                })
            }

        activeDialog = dialog

        items["deviceSdk"].setValueIfAbsent(Build.VERSION.SDK_INT.toString())
        items["fileSize"].setValueIfAbsent(PFiles.formatSizeWithUnit(packageFile.length()))
        setButtonAvailability(
            dialog = dialog,
            action = DialogAction.POSITIVE,
            enabled = builderApplier != null,
            enabledColor = R.color.dialog_button_attraction,
        )
        setButtonAvailability(
            dialog = dialog,
            action = DialogAction.NEUTRAL,
            enabled = false,
            enabledColor = R.color.dialog_button_hint,
        )

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    analyzeForDisplay(context, packageFile)
                }
            }
            result.onSuccess { info ->
                val archive = info.archive
                val summary = archive.baseManifest
                val packageName = info.packageName ?: summary?.packageName
                val installedPackageName = packageName?.also { name ->
                    AppUtils.getInstalledVersionInfo(name)?.let { versionInfo ->
                        items["installedVersion"].isVisible = true
                        items["installedVersion"].setValueIfAbsent(
                            context.getString(
                                R.string.text_full_version_info,
                                versionInfo.versionName,
                                versionInfo.versionCode,
                            ),
                        )
                    }
                }
                requestedPermissions = (info.requestedPermissions + summary?.requestedPermissions.orEmpty())
                    .distinct()
                    .sorted()
                manifestAvailable = archive.format == AndroidPackageFormat.AAB || archive.baseApk != null
                expectedSourceSha256 = info.sourceSha256

                items["format"].setValueIfAbsent(formatName(archive))
                items["labelName"].setValueIfAbsent(info.label ?: summary?.applicationLabel)
                items["packageName"].setValueIfAbsent(packageName)
                setVersionValue(dialog, items, info.versionName ?: summary?.versionName, info.versionCode ?: summary?.versionCode)
                items["signatureScheme"].setValueIfAbsent(info.signatureSchemes)
                items["minSdk"].setValueIfAbsent((info.minSdk ?: summary?.minSdk)?.toString())
                items["targetSdk"].setValueIfAbsent((info.targetSdk ?: summary?.targetSdk)?.toString())
                summary?.maxSdk?.let { maxSdk ->
                    items["maxSdk"].isVisible = true
                    items["maxSdk"].setValueIfAbsent(maxSdk.toString())
                }
                installationExceptionText(context, archive)?.let { exceptionText ->
                    items["installation"].isVisible = true
                    items["installation"].setValueIfAbsent(exceptionText)
                }
                val icon = info.icon ?: AppCompatResources.getDrawable(context, R.drawable.ic_packaging)
                dialog.setIcon(icon)
                moreInfo.value = PackageMoreInfo(
                    components = componentsText(context, archive),
                    icon = icon,
                )
                dialog.makeSettingsLaunchable({ it.iconView }, installedPackageName)

                setButtonAvailability(
                    dialog = dialog,
                    action = DialogAction.POSITIVE,
                    enabled = builderApplier != null || archive.canInstall,
                    enabledColor = R.color.dialog_button_attraction,
                )
                setButtonAvailability(
                    dialog = dialog,
                    action = DialogAction.NEUTRAL,
                    enabled = manifestAvailable,
                    enabledColor = R.color.dialog_button_hint,
                )
                scope.launch {
                    val pluginInfo = try {
                        ExplorerActionHostFileInfoLoader.load(
                            context = context,
                            sourceFile = packageFile,
                            expectedSourceSha256 = info.sourceSha256,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    pluginInfo?.let { details ->
                        options.add(options.lastIndex, MaterialDialog.OptionMenuItemSpec(details.title) { parent ->
                            if (pluginInfoDialog?.dialog?.isShowing != true) {
                                pluginInfoDialog = showPluginInfoDialog(context, parent, details)
                            }
                        })
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val extension = packageFile.extension.uppercase(Locale.ROOT).ifBlank { "?" }
                items["format"].setValueIfAbsent(extension)
                items["labelName"].setValueIfAbsent(null)
                items["packageName"].setValueIfAbsent(null)
                items["versionPlaceholder"].setValueIfAbsent(null)
                items["signatureScheme"].setValueIfAbsent(null)
                items["minSdk"].setValueIfAbsent(null)
                items["targetSdk"].setValueIfAbsent(null)
                items["maxSdk"].setValueIfAbsent(null)
                items["installation"].isVisible = true
                items["installation"].setValueIfAbsent(
                    context.getString(
                        R.string.error_android_package_archive_invalid,
                        error.message.orEmpty(),
                    ),
                )
                val icon = AppCompatResources.getDrawable(context, R.drawable.ic_packaging)
                dialog.setIcon(icon)
                moreInfo.value = PackageMoreInfo(components = null, icon = icon)
                setButtonAvailability(
                    dialog = dialog,
                    action = DialogAction.POSITIVE,
                    enabled = builderApplier != null,
                    enabledColor = R.color.dialog_button_attraction,
                )
            }
        }
        return infoDialog
    }

    private fun showPluginInfoDialog(context: Context, parent: MaterialDialog, details: ExplorerActionHostFileInfo): InfoDialog {
        return InfoDialog(context).apply {
            items.addItem(details.title, details.summary, id = "pluginInfo").maxLines = Int.MAX_VALUE
            builder.title(parent.titleView.text)
                .icon(parent.iconView.drawable)
                .limitIconToDefaultSize()
                .negativeText(R.string.dialog_button_dismiss)
                .negativeColorRes(R.color.dialog_button_default)
            show()
        }
    }

    private fun showMoreInfoDialog(context: Context, parent: MaterialDialog, moreInfo: StateFlow<PackageMoreInfo?>): InfoDialog {
        val infoDialog = InfoDialog(context)
        val components = infoDialog.items.addItem(R.string.apk_info_components, id = "components").apply { maxLines = 3 }
        val dialog = infoDialog.builder
            .title(parent.titleView.text)
            .icon(parent.iconView.drawable)
            .limitIconToDefaultSize()
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .let { infoDialog.show() }
        infoDialog.scope.launch {
            moreInfo.collect { info ->
                if (info != null) {
                    components.setValue(info.components)
                    dialog.setIcon(info.icon)
                }
            }
        }
        return infoDialog
    }

    private suspend fun analyzeForDisplay(context: Context, packageFile: File): DisplayInfo {
        val sourceSha256Before = packageFile.sha256Bounded()
        val archive = AndroidPackageArchiveInspector.inspect(
            packageFile,
            PackageDeviceSpec.from(context),
        )
        val displayApk = when (archive.format) {
            AndroidPackageFormat.APK -> packageFile
            AndroidPackageFormat.AAB -> null
            else -> archive.createDisplayApk(context.cacheDir)
        }
        val temporaryDirectory = displayApk
            ?.takeUnless { it == packageFile }
            ?.parentFile
        return try {
            val packageInfo = displayApk?.let { getPackageInfo(context.packageManager, it.absolutePath) }
            val applicationInfo = packageInfo?.applicationInfo?.apply {
                sourceDir = displayApk.absolutePath
                publicSourceDir = displayApk.absolutePath
            }
            val versionCode = packageInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            }
            val label = runCatching {
                applicationInfo?.loadLabel(context.packageManager)?.toString()
            }.getOrNull()
            val icon = runCatching {
                applicationInfo?.loadIcon(context.packageManager)
            }.getOrNull()
            val minSdk = runCatching {
                applicationInfo?.minSdkVersion
            }.getOrNull()?.takeIf { it != 0 }
            val targetSdk = applicationInfo?.targetSdkVersion?.takeIf { it != 0 }
            val requestedPermissions = packageInfo?.requestedPermissions
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.sorted()
                .orEmpty()
            val signatureSchemes = displayApk?.let(::getApkSignatureInfo)
            val sourceSha256After = packageFile.sha256Bounded()
            if (!sourceSha256After.equals(sourceSha256Before, ignoreCase = true)) {
                throw IOException("Package file changed while its details were being read")
            }
            DisplayInfo(
                archive = archive,
                packageName = packageInfo?.packageName,
                versionName = packageInfo?.versionName,
                versionCode = versionCode,
                label = label,
                icon = icon,
                minSdk = minSdk,
                targetSdk = targetSdk,
                requestedPermissions = requestedPermissions,
                signatureSchemes = signatureSchemes,
                sourceSha256 = sourceSha256Before,
            )
        } finally {
            temporaryDirectory?.deleteRecursively()
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, apkFilePath: String): PackageInfo? =
        runCatching {
            packageManager.getPackageArchiveInfo(
                apkFilePath,
                GET_META_DATA or PackageManager.GET_PERMISSIONS,
            )
        }.getOrNull()

    private fun getApkSignatureInfo(apkFile: File): String? =
        runCatching { ApkSignatureDetector.detectSchemes(apkFile) }.getOrNull()

    private suspend fun File.sha256Bounded(): String {
        if (length() > MAX_SOURCE_DIGEST_BYTES) {
            throw IOException("Package file exceeds the inspection size limit")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                currentCoroutineContext().ensureActive()
                if (read < 0) break
                total += read
                if (total > MAX_SOURCE_DIGEST_BYTES) {
                    throw IOException("Package file exceeds the inspection size limit")
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun setVersionValue(
        dialog: MaterialDialog,
        items: InfoDialogItems,
        versionName: String?,
        versionCode: Long?,
    ) {
        when {
            versionName != null -> {
                items["versionPlaceholder"].key = dialog.context.getString(R.string.text_version)
                items["versionPlaceholder"].setValueIfAbsent(
                    dialog.context.getString(R.string.text_full_version_info, versionName, versionCode),
                )
            }
            versionCode != null -> {
                items["versionPlaceholder"].key = dialog.context.getString(R.string.text_version_code)
                items["versionPlaceholder"].setValueIfAbsent(versionCode.toString())
            }
            else -> items["versionPlaceholder"].setValueIfAbsent(null)
        }
    }

    private fun formatName(archive: AndroidPackageArchive): String = when (archive.subtype) {
        AndroidPackageSubtype.SINGLE_APK -> "APK"
        AndroidPackageSubtype.BUNDLETOOL_APKS -> "APKS (bundletool)"
        AndroidPackageSubtype.SAI_APKS -> "APKS (SAI)"
        AndroidPackageSubtype.GENERIC_APKS -> "APKS"
        AndroidPackageSubtype.XAPK -> "XAPK"
        AndroidPackageSubtype.APKM -> "APKM"
        AndroidPackageSubtype.APKZ -> "APKZ"
        AndroidPackageSubtype.ANDROID_APP_BUNDLE -> "AAB"
    }

    private fun componentsText(context: Context, archive: AndroidPackageArchive): String =
        if (archive.format == AndroidPackageFormat.AAB) {
            context.getString(
                R.string.apk_info_components_aab,
                archive.aabModules.size,
            )
        } else {
            context.getString(
                R.string.apk_info_components_apk,
                archive.apkEntryCount,
                archive.selectedApks.size,
                archive.obbEntries.size,
            )
        }

    private fun installationExceptionText(context: Context, archive: AndroidPackageArchive): String? =
        when (archive.installationState) {
            ArchiveInstallationState.READY -> null
            ArchiveInstallationState.READY_WITH_UNDEPLOYED_OBB -> context.getString(
                R.string.apk_info_exception_obb_not_deployed,
                archive.obbEntries.size,
            )
            ArchiveInstallationState.AAB_REQUIRES_CONVERSION ->
                context.getString(R.string.apk_info_installation_aab)
            ArchiveInstallationState.INCOMPATIBLE ->
                context.getString(R.string.apk_info_installation_incompatible)
            ArchiveInstallationState.INVALID ->
                context.getString(R.string.apk_info_installation_invalid)
        }

    private fun setButtonAvailability(
        dialog: MaterialDialog,
        action: DialogAction,
        enabled: Boolean,
        enabledColor: Int,
    ) {
        dialog.getActionButton(action).apply {
            isEnabled = enabled
            setTextColor(
                dialog.context.getColor(
                    if (enabled) enabledColor else R.color.dialog_button_unavailable,
                ),
            )
        }
    }

    private data class DisplayInfo(
        val archive: AndroidPackageArchive,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val label: String?,
        val icon: Drawable?,
        val minSdk: Int?,
        val targetSdk: Int?,
        val requestedPermissions: List<String>,
        val signatureSchemes: String?,
        val sourceSha256: String,
    )

    private data class PackageMoreInfo(val components: String?, val icon: Drawable?)

    private const val MAX_SOURCE_DIGEST_BYTES = 4L * 1024L * 1024L * 1024L
}
