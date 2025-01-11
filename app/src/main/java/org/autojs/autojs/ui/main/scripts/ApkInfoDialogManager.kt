package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.GET_META_DATA
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.android.apksig.ApkVerifier
import com.jaredrummler.apkparser.ApkParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ApkFileInfoDialogListItemBinding
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile

object ApkInfoDialogManager {

    @JvmStatic
    @SuppressLint("SetTextI18n")
    fun showApkInfoDialog(context: Context, apkFile: File) {
        val binding = ApkFileInfoDialogListItemBinding.inflate(LayoutInflater.from(context))
        val root = binding.root as ViewGroup

        val apkFilePath = apkFile.absolutePath

        val dialog = MaterialDialog.Builder(context)
            .title(apkFile.name)
            .customView(root, false)
            .autoDismiss(false)
            .iconRes(R.drawable.ic_three_dots_outline_small)
            .limitIconToDefaultSize()
            .positiveText(R.string.text_install)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { materialDialog, _ ->
                materialDialog.dismiss()
                IntentUtils.installApk(context, apkFilePath)
            }
            .negativeText(R.string.text_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNegative { materialDialog, _ -> materialDialog.dismiss() }
            .show()

        val packageManager = context.packageManager

        CoroutineScope(Dispatchers.Main).launch {
            val packageInfo = withContext(Dispatchers.IO) {
                runCatching { packageManager.getPackageArchiveInfo(apkFilePath, GET_META_DATA) }.getOrNull()
            }
            val applicationInfo = packageInfo?.applicationInfo

            val packageName = packageInfo?.packageName
            val versionName = packageInfo?.versionName
            val versionCode = packageInfo?.let {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> it.longVersionCode
                    else -> @Suppress("DEPRECATION") it.versionCode.toLong()
                }
            }

            val fileSize = withContext(Dispatchers.IO) { PFiles.getHumanReadableSize(apkFile.length()) }

            val signatureScheme = withContext(Dispatchers.IO) { getApkSignatureInfo(apkFile) }

            withContext(Dispatchers.Main) {
                // @Hint by SuperMonster003 on Nov 27, 2024.
                //  ! Prioritize handling "installed version" to determine whether to display its content view.
                //  ! This is the only content view that needs to be considered before displaying the dialog.
                //  ! It is now safe to display the dialog immediately as all content views have placeholders.
                //  ! zh-CN:
                //  ! 优先处理 "已安装版本", 决定是否显示其内容视图.
                //  ! 这是唯一一个在显示对话框之前需要考虑的内容视图.
                //  ! 此时可立即安全显示对话框, 因所有内容视图均已完成占位.
                val installedPackageName: String? = packageName?.let { pkg ->
                    AppUtils.getInstalledVersionInfo(pkg)?.let { versionInfo ->
                        binding.installedVersionParent.isVisible = true
                        binding.installedVersionValue.setTextIfAbsent(dialog) { context.getString(R.string.text_full_version_info, versionInfo.versionName, versionInfo.versionCode) }
                        pkg /* returns as installedPackageName */
                    }
                }

                restoreEssentialViews(binding, context)
                updateGuidelines(binding)

                when {
                    versionName != null -> {
                        binding.versionPlaceholderLabel.text = context.getString(R.string.text_version)
                        binding.versionPlaceholderValue.setTextIfAbsent(dialog) { context.getString(R.string.text_full_version_info, versionName, versionCode) }
                    }
                    versionCode != null -> {
                        binding.versionPlaceholderLabel.text = context.getString(R.string.text_version_code)
                        binding.versionPlaceholderValue.setTextIfAbsent(dialog) { "$versionCode" }
                    }
                    else -> binding.versionPlaceholderValue.setTextIfAbsent(dialog) { null }
                }

                binding.packageNameValue.setTextIfAbsent(dialog) { packageName }
                binding.deviceSdkValue.setTextIfAbsent(dialog) { "${Build.VERSION.SDK_INT}" }
                binding.fileSizeValue.setTextIfAbsent(dialog) { fileSize }
                binding.signatureSchemeValue.setTextIfAbsent(dialog) { signatureScheme }

                dialog.setIcon(applicationInfo?.apply {
                    sourceDir = apkFilePath
                    publicSourceDir = apkFilePath
                }?.loadIcon(packageManager) ?: context.getDrawable(R.drawable.ic_packaging))

                dialog.makeSettingsLaunchable({ it.iconView }, installedPackageName)
                dialog.makeTextCopyable { it.titleView }

                val apkInfo = getApkInfo(apkFile)

                binding.labelNameValue.setTextIfAbsent(dialog) { apkInfo?.label }
                binding.packageNameValue.setTextIfAbsent(dialog) { apkInfo?.packageName }
                binding.minSdkValue.setTextIfAbsent(dialog) { apkInfo?.minSdkVersion?.toString() }
                binding.targetSdkValue.setTextIfAbsent(dialog) { apkInfo?.targetSdkVersion?.toString() }

                if (apkInfo != null) dialog.getActionButton(DialogAction.NEUTRAL).let { neutralButton ->
                    neutralButton.isVisible = true
                    neutralButton.text = "Manifest"
                    neutralButton.setOnClickListener { DisplayManifestActivity.launch(context, apkInfo.manifestXml, apkInfo.usesPermissions) }
                }
            }
        }
    }

    private suspend fun getApkInfo(apkFile: File): ApkInfo? = withContext(Dispatchers.IO) {
        runCatching {
            ApkParser.create(apkFile).use { parser ->
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
    }

    private fun MaterialDialog.makeSettingsLaunchable(viewGetter: (dialog: MaterialDialog) -> View?, packageName: String?) {
        viewGetter(this)?.setOnClickListener {
            packageName?.let { pkg ->
                IntentUtils.goToAppDetailSettings(this.context, pkg)
            } ?: ViewUtils.showSnack(this.view, R.string.error_app_not_installed)
        }
    }

    private fun MaterialDialog.makeTextCopyable(textViewGetter: (dialog: MaterialDialog) -> TextView?) {
        makeTextCopyable(textViewGetter(this))
    }

    private fun MaterialDialog.makeTextCopyable(textView: TextView?, textValue: String? = textView?.text?.toString()) {
        if (textValue != null) {
            val context = this.context
            textView?.setOnClickListener {
                ClipboardUtils.setClip(context, textValue)
                ViewUtils.showSnack(this.view, "${context.getString(R.string.text_already_copied_to_clip)}: $textValue")
            }
        }
    }

    private fun TextView.setTextIfAbsent(dialog: MaterialDialog, f: () -> String?) {
        val textView = this
        val textValue = f.invoke()
        if (textView.text == context.getString(R.string.ellipsis_six)) {
            textView.text = textValue ?: context.getString(R.string.text_unknown)
        }
        dialog.makeTextCopyable(textView, textValue)
    }

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
        ApkVerifier.Builder(apkFile).build().verify().run {
            listOfNotNull(
                "V1".takeIf { isVerifiedUsingV1Scheme || hasV1Signature(apkFile) },
                "V2".takeIf { isVerifiedUsingV2Scheme },
                "V3".takeIf { isVerifiedUsingV3Scheme },
                "V4".takeIf { isVerifiedUsingV4Scheme },
            ).takeUnless { it.isEmpty() }?.joinToString(" + ")
        }
    }.getOrNull()

    private fun hasV1Signature(apkFile: File): Boolean {
        if (!apkFile.isFile) return false
        JarFile(apkFile).use { jar ->
            var hasManifest = false
            var hasSF = false
            var hasRSA = false
            for (entry: JarEntry in jar.entries()) {
                val name = entry.name.uppercase()
                when {
                    name == "META-INF/MANIFEST.MF" -> hasManifest = true
                    name.endsWith(".SF") && name.startsWith("META-INF/") -> hasSF = true
                    name.endsWith(".RSA") && name.startsWith("META-INF/") -> hasRSA = true
                }
                if (hasManifest && hasSF && hasRSA) {
                    return true
                }
            }
            return false
        }
    }

    private data class ApkInfo(
        val label: String?,
        val packageName: String?,
        val minSdkVersion: Int?,
        val targetSdkVersion: Int?,
        val usesPermissions: List<String>,
        val manifestXml: String,
    )

}