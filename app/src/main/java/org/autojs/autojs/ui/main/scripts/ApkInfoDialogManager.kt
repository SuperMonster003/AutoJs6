package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.os.Build
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import org.autojs.autojs.extension.MaterialDialogExtensions.makeSettingsLaunchable
import org.autojs.autojs.extension.MaterialDialogExtensions.makeTextCopyable
import org.autojs.autojs.extension.MaterialDialogExtensions.setCopyableTextIfAbsent
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ApkFileInfoDialogListItemBinding
import java.io.File

object ApkInfoDialogManager {

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showApkInfoDialog(context: Context, apkFile: File) {
        val binding = ApkFileInfoDialogListItemBinding.inflate(LayoutInflater.from(context))

        // Create an independent Scope for the Dialog, bind its lifecycle with the Dialog.
        // zh-CN: 针对 Dialog 独立创建一个 Scope, 生命周期与 Dialog 绑定.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val apkFilePath = apkFile.absolutePath
        val packageManager = context.packageManager

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
            .neutralColorRes(R.color.dialog_button_hint)
            .onNegative { materialDialog, _ -> materialDialog.dismiss() }
            .show()
            .apply {
                setOnDismissListener { scope.cancel() }
                makeTextCopyable { it.titleView }
            }

        scope.launch {
            val apkInfoDeferred = async(Dispatchers.IO) { getApkInfo(apkFile) }
            val packageInfoDeferred = async(Dispatchers.IO) { getPackageInfo(packageManager, apkFilePath) }

            val firstPackageName: String? = select {
                apkInfoDeferred.onAwait { it?.packageName }
                packageInfoDeferred.onAwait { it?.packageName }
            }
            val packageName: String? = firstPackageName ?: when {
                apkInfoDeferred.isCompleted -> packageInfoDeferred.await()?.packageName
                else -> apkInfoDeferred.await()?.packageName
            }

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
            dialog.setCopyableTextIfAbsent(binding.fileSizeValue, this) { PFiles.getHumanReadableSize(apkFile.length()) }
            dialog.setCopyableTextIfAbsent(binding.signatureSchemeValue, this) { getApkSignatureInfo(apkFile) }

            launch(Dispatchers.IO) {
                val apkInfo = apkInfoDeferred.await()

                withContext(Dispatchers.Main) {
                    dialog.setCopyableTextIfAbsent(binding.labelNameValue, apkInfo?.label)
                    dialog.setCopyableTextIfAbsent(binding.minSdkValue, apkInfo?.minSdkVersion?.toString())
                    dialog.setCopyableTextIfAbsent(binding.targetSdkValue, apkInfo?.targetSdkVersion?.toString())

                    if (apkInfo != null) dialog.getActionButton(DialogAction.NEUTRAL).let { neutralButton ->
                        neutralButton.isVisible = true
                        neutralButton.text = "Manifest"
                        neutralButton.setOnClickListener { DisplayManifestActivity.launch(context, apkInfo.manifestXml, apkInfo.usesPermissions) }
                    }
                }
            }

            launch(Dispatchers.IO) {
                val packageInfo = packageInfoDeferred.await()
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

    private fun getApkInfo(apkFile: File): ApkInfo? = runCatching {
        ApkFile(apkFile).use { parser ->
            val meta = runCatching { parser.apkMeta }.getOrNull()
            val label = meta?.label
            val packageName = meta?.packageName
            val minSdkVersion = meta?.minSdkVersion?.toIntOrNull()
            val targetSdkVersion = meta?.targetSdkVersion?.toIntOrNull()
            val usesPermissions = meta?.usesPermissions ?: emptyList()
            val manifestXml = parser.manifestXml
            ApkInfo(label, packageName, minSdkVersion, targetSdkVersion, usesPermissions, manifestXml)
        }
    }.getOrNull()

    private fun getPackageInfo(packageManager: PackageManager, apkFilePath: String): PackageInfo? = runCatching {
        packageManager.getPackageArchiveInfo(apkFilePath, GET_META_DATA)
    }.getOrNull()

    private fun restoreEssentialViews(binding: ApkFileInfoDialogListItemBinding, context: Context) {
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

    private fun updateGuidelines(binding: ApkFileInfoDialogListItemBinding) {
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

    private fun getApkSignatureInfo(apkFile: File): String? = runCatching {
        // ApkVerifier.Builder(apkFile).build().verify().run {
        //     listOfNotNull(
        //         "V1".takeIf { isVerifiedUsingV1Scheme || hasV1Signature(apkFile) },
        //         "V2".takeIf { isVerifiedUsingV2Scheme },
        //         "V3".takeIf { isVerifiedUsingV3Scheme },
        //         "V4".takeIf { isVerifiedUsingV4Scheme },
        //     ).takeUnless { it.isEmpty() }?.joinToString(" + ")
        // }
        ApkSignatureDetector.detectSchemes(apkFile)
    }.getOrNull()

    private data class ApkInfo(
        val label: String?,
        val packageName: String?,
        val minSdkVersion: Int?,
        val targetSdkVersion: Int?,
        val usesPermissions: List<String>,
        val manifestXml: String,
    )

}