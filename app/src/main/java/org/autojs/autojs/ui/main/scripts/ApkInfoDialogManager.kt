package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.os.Build
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.makeSettingsLaunchable
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.setCopyableTextIfAbsent
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ApkFileInfoDialogItemsBinding
import java.io.File

object ApkInfoDialogManager {

    @JvmStatic
    @JvmOverloads
    @SuppressLint("SetTextI18n")
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)? = null,
    ) = showApkInfoDialog(context, apkFile, builderApplier, null)

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showApkInfoDialog(
        context: Context,
        apkFile: File,
        builderApplier: (MaterialDialog.Builder.() -> Unit)?,
        onDismissListener: DialogInterface.OnDismissListener?,
    ) {
        val binding = ApkFileInfoDialogItemsBinding.inflate(LayoutInflater.from(context))

        // Create an independent Scope for the Dialog, bind its lifecycle with the Dialog.
        // zh-CN: 针对 Dialog 独立创建一个 Scope, 生命周期与 Dialog 绑定.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val apkFilePath = apkFile.absolutePath
        val packageManager = context.packageManager
        var requestedPermissions = emptyList<String>()

        val dialog = MaterialDialog.Builder(context)
            .title(apkFile.name)
            .customView(binding.root, false)
            .autoDismiss(false)
            .iconRes(R.drawable.ic_three_dots_outline_small)
            .limitIconToDefaultSize()
            .positiveText(R.string.text_install)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { materialDialog, _ ->
                materialDialog.dismiss()
                IntentUtils.installApk(
                    context = context,
                    path = apkFilePath,
                    fileProviderAuthority = AppFileProvider.AUTHORITY,
                    exceptionHolder = ToastExceptionHolder(context),
                )
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .neutralText(R.string.text_manifest)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ ->
                DisplayManifestActivity.launch(context, apkFile, requestedPermissions)
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


        scope.launch {
            val packageInfoDeferred = async(Dispatchers.IO) { getPackageInfo(packageManager, apkFilePath) }
            val packageInfo = packageInfoDeferred.await()
            val archiveInfoDeferred = async(Dispatchers.IO) {
                packageInfo?.let { buildArchiveInfo(packageManager, apkFilePath, it) }
            }
            val packageName = packageInfo?.packageName

            // @Hint by SuperMonster003 on Nov 27, 2024.
            //  ! Prioritize handling "installed version" to determine whether to display its content view.
            //  ! This is the only content view that needs to be considered before displaying the dialog.
            //  ! It is now safe to display the dialog immediately as all content views have placeholders.
            //  ! zh-CN:
            //  ! 优先处理 "已安装版本", 决定是否显示其内容视图.
            //  ! 这是唯一一个在显示对话框之前需要考虑的内容视图.
            //  ! 此时可立即安全显示对话框, 因所有内容视图均已完成占位.
            val installedPackageName: String? = packageName?.also { pkg ->
                AppUtils.getInstalledVersionInfo(pkg)?.let { versionInfo ->
                    binding.installedVersionParent.isVisible = true
                    dialog.setCopyableTextIfAbsent(
                        binding.installedVersionValue,
                        context.getString(R.string.text_full_version_info, versionInfo.versionName, versionInfo.versionCode),
                    )
                }
            }

            restoreEssentialViews(binding, context)
            updateGuidelines(binding)

            dialog.setCopyableTextIfAbsent(binding.packageNameValue, packageName)
            dialog.setCopyableTextIfAbsent(binding.deviceSdkValue, "${Build.VERSION.SDK_INT}")
            dialog.setCopyableTextIfAbsent(binding.fileSizeValue, this) { PFiles.formatSizeWithUnit(apkFile.length()) }
            dialog.setCopyableTextIfAbsent(binding.signatureSchemeValue, this) {
                getApkSignatureInfo(apkFile)
            }

            withContext(Dispatchers.Main) {
                val info = archiveInfoDeferred.await()
                dialog.setCopyableTextIfAbsent(binding.labelNameValue, info?.label)
                dialog.setCopyableTextIfAbsent(binding.minSdkValue, info?.minSdk?.toString())
                dialog.setCopyableTextIfAbsent(binding.targetSdkValue, info?.targetSdk?.toString())
                requestedPermissions = info?.requestedPermissions.orEmpty()
            }

            launch(Dispatchers.IO) {
                val applicationInfo = packageInfo?.applicationInfo
                val versionName = packageInfo?.versionName
                val versionCode = packageInfo?.let {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> it.longVersionCode
                        else -> @Suppress("DEPRECATION") it.versionCode.toLong()
                    }
                }
                withContext(Dispatchers.Main) {
                    when {
                        versionName != null -> {
                            binding.versionPlaceholderLabel.text = context.getString(R.string.text_version)
                            dialog.setCopyableTextIfAbsent(
                                binding.versionPlaceholderValue,
                                context.getString(R.string.text_full_version_info, versionName, versionCode),
                            )
                        }
                        versionCode != null -> {
                            binding.versionPlaceholderLabel.text = context.getString(R.string.text_version_code)
                            dialog.setCopyableTextIfAbsent(binding.versionPlaceholderValue, "$versionCode")
                        }
                        else -> dialog.setCopyableTextIfAbsent(binding.versionPlaceholderValue, null)
                    }

                    dialog.setIcon(applicationInfo?.apply {
                        sourceDir = apkFilePath
                        publicSourceDir = apkFilePath
                    }?.loadIcon(packageManager) ?: AppCompatResources.getDrawable(context, R.drawable.ic_packaging))
                    dialog.makeSettingsLaunchable({ it.iconView }, installedPackageName)
                }
            }
        }
    }

    private fun getPackageInfo(packageManager: PackageManager, apkFilePath: String): PackageInfo? = runCatching {
        packageManager.getPackageArchiveInfo(
            apkFilePath,
            GET_META_DATA or PackageManager.GET_PERMISSIONS,
        )
    }.getOrNull()

    private fun restoreEssentialViews(binding: ApkFileInfoDialogItemsBinding, context: Context) {
        listOf(
            Triple(binding.labelNameLabel, binding.labelNameColon, binding.labelNameValue) to R.string.text_label_name,
            Triple(binding.packageNameLabel, binding.packageNameColon, binding.packageNameValue) to R.string.apk_info_package_name,
            Triple(binding.versionPlaceholderLabel, binding.versionPlaceholderColon, binding.versionPlaceholderValue) to R.string.text_version,
            Triple(binding.fileSizeLabel, binding.fileSizeColon, binding.fileSizeValue) to R.string.apk_info_file_size,
            Triple(binding.signatureSchemeLabel, binding.signatureSchemeColon, binding.signatureSchemeValue) to R.string.apk_info_signature_scheme,
            Triple(binding.minSdkLabel, binding.minSdkColon, binding.minSdkValue) to R.string.apk_info_min_sdk,
            Triple(binding.targetSdkLabel, binding.targetSdkColon, binding.targetSdkValue) to R.string.apk_info_target_sdk,
            Triple(binding.deviceSdkLabel, binding.deviceSdkColon, binding.deviceSdkValue) to R.string.apk_info_device_sdk,
        ).forEach { pair ->
            val (triple, labelTextRes) = pair
            val (labelView, colonView, valueView) = triple
            labelView.text = context.getString(labelTextRes)
            colonView.isVisible = true
            valueView.isVisible = true
        }
    }

    private fun updateGuidelines(binding: ApkFileInfoDialogItemsBinding) {
        val filteredBindings = listOf(
            binding.labelNameLabel to binding.labelNameGuideline,
            binding.packageNameLabel to binding.packageNameGuideline,
            binding.versionPlaceholderLabel to binding.versionPlaceholderGuideline,
            binding.fileSizeLabel to binding.fileSizeGuideline,
            binding.signatureSchemeLabel to binding.signatureSchemeGuideline,
            binding.minSdkLabel to binding.minSdkGuideline,
            binding.targetSdkLabel to binding.targetSdkGuideline,
            binding.installedVersionLabel to binding.installedVersionGuideline,
            binding.deviceSdkLabel to binding.deviceSdkGuideline,
        ).filter { (it.first.parent as? ConstraintLayout)?.isVisible == true }

        @Suppress("DuplicatedCode")
        val maxWidth = filteredBindings.maxOfOrNull { it.first.apply { measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth } ?: return

        filteredBindings.forEach { (_, guideline) ->
            guideline.layoutParams = (guideline.layoutParams as ConstraintLayout.LayoutParams).also {
                it.guideBegin = maxWidth
            }
        }
    }

    private fun getApkSignatureInfo(apkFile: File): String? =
        runCatching { ApkSignatureDetector.detectSchemes(apkFile) }.getOrNull()

    private fun buildArchiveInfo(
        packageManager: PackageManager,
        apkPath: String,
        packageInfo: PackageInfo,
    ): ArchiveInfo {
        val applicationInfo = packageInfo.applicationInfo?.apply {
            sourceDir = apkPath
            publicSourceDir = apkPath
        }
        val minSdk = runCatching { applicationInfo?.minSdkVersion }.getOrNull()?.takeIf { it != 0 }
        val targetSdk = applicationInfo?.targetSdkVersion?.takeIf { it != 0 }
        val label = runCatching { applicationInfo?.loadLabel(packageManager)?.toString() }.getOrNull()
        val requestedPermissions = packageInfo.requestedPermissions
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        return ArchiveInfo(
            label = label,
            minSdk = minSdk,
            targetSdk = targetSdk,
            requestedPermissions = requestedPermissions,
        )
    }

    private data class ArchiveInfo(
        val label: String?,
        val minSdk: Int?,
        val targetSdk: Int?,
        val requestedPermissions: List<String>,
    )

}
