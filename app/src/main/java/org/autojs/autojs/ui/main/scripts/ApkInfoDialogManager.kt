package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.util.DialogUtils.makeSettingsLaunchable
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.setCopyableTextIfAbsent
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ApkFileInfoDialogItemsBinding
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

object ApkInfoDialogManager {

    @JvmStatic
    @JvmOverloads
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)? = null,
    ) {
        showApkInfoDialog(context, apkFile, builderApplier, null)
    }

    @JvmStatic
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
    ) {
        showPackageInfoDialog(
            context = context,
            packageFile = apkFile,
            builderApplier = builderApplier,
            onDismissListener = onDismissListener,
            installAction = { file, expectedSha256 ->
                PackageInstallerActivity.install(context, file, expectedSha256)
            },
        )
    }

    @SuppressLint("SetTextI18n")
    internal fun showPackageInfoDialog(
        context: Context,
        packageFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
        installAction: (File, String?) -> Unit,
    ) {
        val binding = ApkFileInfoDialogItemsBinding.inflate(LayoutInflater.from(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var requestedPermissions: List<String>? = null
        var manifestAvailable = false
        var expectedSourceSha256: String? = null

        val dialog = MaterialDialog.Builder(context)
            .title(packageFile.name)
            .customView(binding.root, false)
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
            .build()
            .apply {
                makeTextCopyable { titleView }
                setOnDismissListener {
                    scope.cancel()
                    onDismissListener?.onDismiss(this)
                }
            }
            .showAdaptive()

        restoreEssentialViews(binding, context)
        dialog.setCopyableTextIfAbsent(binding.deviceSdkValue, Build.VERSION.SDK_INT.toString())
        dialog.setCopyableTextIfAbsent(binding.fileSizeValue, PFiles.formatSizeWithUnit(packageFile.length()))
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
                        binding.installedVersionParent.isVisible = true
                        dialog.setCopyableTextIfAbsent(
                            binding.installedVersionValue,
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

                dialog.setCopyableTextIfAbsent(binding.formatValue, formatName(archive))
                dialog.setCopyableTextIfAbsent(binding.labelNameValue, info.label ?: summary?.applicationLabel)
                dialog.setCopyableTextIfAbsent(binding.packageNameValue, packageName)
                setVersionValue(dialog, binding, info.versionName ?: summary?.versionName, info.versionCode ?: summary?.versionCode)
                dialog.setCopyableTextIfAbsent(binding.componentsValue, componentsText(context, archive))
                dialog.setCopyableTextIfAbsent(binding.signatureSchemeValue, info.signatureSchemes)
                dialog.setCopyableTextIfAbsent(binding.minSdkValue, (info.minSdk ?: summary?.minSdk)?.toString())
                dialog.setCopyableTextIfAbsent(binding.targetSdkValue, (info.targetSdk ?: summary?.targetSdk)?.toString())
                dialog.setCopyableTextIfAbsent(binding.maxSdkValue, summary?.maxSdk?.toString())
                dialog.setCopyableTextIfAbsent(binding.installationValue, installationText(context, archive))
                dialog.setIcon(
                    info.icon ?: AppCompatResources.getDrawable(context, R.drawable.ic_packaging),
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
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val extension = packageFile.extension.uppercase(Locale.ROOT).ifBlank { "?" }
                dialog.setCopyableTextIfAbsent(binding.formatValue, extension)
                dialog.setCopyableTextIfAbsent(binding.labelNameValue, null)
                dialog.setCopyableTextIfAbsent(binding.packageNameValue, null)
                dialog.setCopyableTextIfAbsent(binding.versionPlaceholderValue, null)
                dialog.setCopyableTextIfAbsent(binding.componentsValue, null)
                dialog.setCopyableTextIfAbsent(binding.signatureSchemeValue, null)
                dialog.setCopyableTextIfAbsent(binding.minSdkValue, null)
                dialog.setCopyableTextIfAbsent(binding.targetSdkValue, null)
                dialog.setCopyableTextIfAbsent(binding.maxSdkValue, null)
                dialog.setCopyableTextIfAbsent(
                    binding.installationValue,
                    context.getString(
                        R.string.error_android_package_archive_invalid,
                        error.message.orEmpty(),
                    ),
                )
                dialog.setIcon(AppCompatResources.getDrawable(context, R.drawable.ic_packaging))
                setButtonAvailability(
                    dialog = dialog,
                    action = DialogAction.POSITIVE,
                    enabled = builderApplier != null,
                    enabledColor = R.color.dialog_button_attraction,
                )
            }
        }
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
        binding: ApkFileInfoDialogItemsBinding,
        versionName: String?,
        versionCode: Long?,
    ) {
        when {
            versionName != null -> {
                binding.versionPlaceholderLabel.setText(R.string.text_version)
                dialog.setCopyableTextIfAbsent(
                    binding.versionPlaceholderValue,
                    dialog.context.getString(R.string.text_full_version_info, versionName, versionCode),
                )
            }
            versionCode != null -> {
                binding.versionPlaceholderLabel.setText(R.string.text_version_code)
                dialog.setCopyableTextIfAbsent(binding.versionPlaceholderValue, versionCode.toString())
            }
            else -> dialog.setCopyableTextIfAbsent(binding.versionPlaceholderValue, null)
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

    private fun installationText(context: Context, archive: AndroidPackageArchive): String =
        when (archive.installationState) {
            ArchiveInstallationState.READY -> context.getString(R.string.apk_info_installation_ready)
            ArchiveInstallationState.READY_WITH_UNDEPLOYED_OBB -> context.getString(
                R.string.apk_info_installation_ready_without_obb,
                archive.obbEntries.size,
            )
            ArchiveInstallationState.AAB_REQUIRES_CONVERSION ->
                context.getString(R.string.apk_info_installation_aab)
            ArchiveInstallationState.INCOMPATIBLE ->
                context.getString(R.string.apk_info_installation_incompatible)
            ArchiveInstallationState.INVALID ->
                context.getString(R.string.apk_info_installation_invalid)
        }

    private fun restoreEssentialViews(
        binding: ApkFileInfoDialogItemsBinding,
        context: Context,
    ) {
        listOf(
            Triple(binding.formatLabel, binding.formatColon, binding.formatValue) to R.string.apk_info_format,
            Triple(binding.labelNameLabel, binding.labelNameColon, binding.labelNameValue) to R.string.text_label_name,
            Triple(binding.packageNameLabel, binding.packageNameColon, binding.packageNameValue) to R.string.apk_info_package_name,
            Triple(binding.versionPlaceholderLabel, binding.versionPlaceholderColon, binding.versionPlaceholderValue) to R.string.text_version,
            Triple(binding.componentsLabel, binding.componentsColon, binding.componentsValue) to R.string.apk_info_components,
            Triple(binding.fileSizeLabel, binding.fileSizeColon, binding.fileSizeValue) to R.string.apk_info_file_size,
            Triple(binding.signatureSchemeLabel, binding.signatureSchemeColon, binding.signatureSchemeValue) to R.string.apk_info_signature_scheme,
            Triple(binding.minSdkLabel, binding.minSdkColon, binding.minSdkValue) to R.string.apk_info_min_sdk,
            Triple(binding.targetSdkLabel, binding.targetSdkColon, binding.targetSdkValue) to R.string.apk_info_target_sdk,
            Triple(binding.maxSdkLabel, binding.maxSdkColon, binding.maxSdkValue) to R.string.apk_info_max_sdk,
            Triple(binding.deviceSdkLabel, binding.deviceSdkColon, binding.deviceSdkValue) to R.string.apk_info_device_sdk,
            Triple(binding.installationLabel, binding.installationColon, binding.installationValue) to R.string.apk_info_installation,
        ).forEach { (views, labelTextRes) ->
            val (labelView, colonView, valueView) = views
            labelView.text = context.getString(labelTextRes)
            colonView.isVisible = true
            valueView.isVisible = true
        }
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

    private const val MAX_SOURCE_DIGEST_BYTES = 4L * 1024L * 1024L * 1024L
}
