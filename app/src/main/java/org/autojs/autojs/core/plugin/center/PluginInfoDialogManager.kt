package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.extension.MaterialDialogExtensions.makeSettingsLaunchable
import org.autojs.autojs.extension.MaterialDialogExtensions.makeTextCopyable
import org.autojs.autojs.extension.MaterialDialogExtensions.setCopyableTextIfAbsent
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.TimeUtils
import org.autojs.autojs.util.ViewUtils.colorFilterWithDesaturateOrNull
import org.autojs.autojs.util.ViewUtils.toCircular
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginInfoDialogItemsBinding
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

object PluginInfoDialogManager {

    // Hold the current dialog and package name for refreshing on onResume.
    // zh-CN: 持有当前对话框与包名, 便于 onResume 时刷新.
    private var currentDialog: WeakReference<MaterialDialog>? = null
    private var currentPackageName: String? = null

    fun refreshIfShowing(context: Context, allItems: List<PluginCenterItem>) {
        val dialog = currentDialog?.get() ?: return
        val pkg = currentPackageName ?: return
        if (!dialog.isShowing) return
        val target = allItems.firstOrNull { it.packageName == pkg } ?: return
        showPluginInfoDialog(context, target)
        dialog.dismiss()
    }

    @JvmStatic
    fun showPluginInfoDialog(context: Context, item: PluginCenterItem) {
        if (item.isInstalled) {
            showInstalledPluginInfoDialog(context, item)
        } else {
            showInstallablePluginInfoDialog(context, item)
        }
    }

    private fun showInstallablePluginInfoDialog(context: Context, item: PluginCenterItem) {
        val states = listOf(context.getString(R.string.text_installable))
        val info = PluginInfoInstallable(
            title = item.title,
            states = states,
            packageName = item.packageName,
            version = item.versionSummary,
            author = item.author,
            collaborators = item.collaborators,
            description = item.description,
            packageSize = item.installableApkSizeBytes ?: 0L,
            lastInstallTime = item.lastInstallTime,
            lastUninstallTime = item.lastUninstallTime,
            apkUrl = item.installableApkUrl,
            sha256 = item.installableApkSha256,
        )
        showPluginInfoDialogInternal(context, item, info)
    }

    private fun showInstalledPluginInfoDialog(context: Context, item: PluginCenterItem) {
        val enabledRes = if (item.isEnabled) R.string.text_enabled else R.string.text_disabled
        val states = mutableListOf(context.getString(enabledRes)).apply {
            if (item.isUpdatable) add(context.getString(R.string.text_updatable))
        }
        val info = PluginInfoInstalled(
            title = item.title,
            states = states,
            packageName = item.packageName,
            version = item.versionSummary,
            author = item.author,
            collaborators = item.collaborators,
            description = item.description,
            packageSize = item.packageSize,
            updatableVersion = item.updatableVersionSummary,
            firstInstallTime = item.firstInstallTime,
            lastUpdateTime = item.lastUpdateTime,
            apkUrl = item.installableApkUrl,
            sha256 = item.installableApkSha256,
        )
        showPluginInfoDialogInternal(context, item, info)
    }

    private fun showPluginInfoDialogInternal(context: Context, item: PluginCenterItem, info: PluginInfoBase) {
        val binding = PluginInfoDialogItemsBinding.inflate(LayoutInflater.from(context))

        val dialog = MaterialDialog.Builder(context)
            .title(info.title)
            .customView(binding.root, false)
            .autoDismiss(false)
            .iconRes(R.drawable.ic_three_dots_outline_small)
            .limitIconToDefaultSize()
            .negativeText(R.string.dialog_button_dismiss)
            .onNegative { d, _ -> d.dismiss() }
            .apply {
                when (info) {
                    is PluginInfoInstallable -> {
                        positiveText(R.string.text_install)
                        positiveColorRes(R.color.dialog_button_attraction)
                        onPositive { d, _ ->
                            val url = info.apkUrl
                            when {
                                url.isNullOrBlank() -> {
                                    MaterialDialog.Builder(context)
                                        .title(R.string.text_failed_to_install)
                                        .content(R.string.error_no_available_url_provided_for_current_plugin)
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .show()
                                    val positiveButton = d.getActionButton(DialogAction.POSITIVE)
                                    positiveButton.setTextColor(d.context.getColor(R.color.dialog_button_unavailable))
                                }
                                else -> {
                                    d.dismiss()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                                    }
                                }
                            }
                        }
                    }
                    is PluginInfoInstalled -> {
                        positiveText(R.string.text_uninstall)
                        positiveColorRes(R.color.dialog_button_warn)
                        onPositive { d, _ -> item.uninstallWithPrompt(context, d) }
                        if (item.isUpdatable) {
                            neutralText(R.string.text_update)
                            neutralColorRes(R.color.dialog_button_attraction)
                            onNeutral { d, _ ->
                                val url = info.apkUrl
                                when {
                                    url.isNullOrBlank() -> {
                                        MaterialDialog.Builder(context)
                                            .title(R.string.text_failed_to_update)
                                            .content(R.string.error_no_available_url_provided_for_current_plugin)
                                            .positiveText(R.string.dialog_button_dismiss)
                                            .show()
                                    }
                                    else -> {
                                        d.dismiss()
                                        CoroutineScope(Dispatchers.Main).launch {
                                            PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .show()
            .apply {
                makeTextCopyable { titleView }
            }

        // Hold the current dialog and package name for refreshing on onResume.
        // zh-CN: 记录 "当前对话框" 与包名, 便于 onResume 刷新.
        currentDialog = WeakReference(dialog)
        currentPackageName = item.packageName

        restoreEssentialViews(binding, context, info)
        updateGuidelines(binding)

        binding.stateValueFirst.text = info.states.getOrNull(0)
        if (info.states.size > 1) {
            binding.stateValueSecond.text = info.states[1]
            binding.stateSpliterFirstSecond.isVisible = true
            binding.stateValueSecond.isVisible = true
        }

        dialog.setCopyableTextIfAbsent(binding.packageNameValue, info.packageName)
        dialog.setCopyableTextIfAbsent(binding.versionValue, info.version)
        dialog.setCopyableTextIfAbsent(binding.pluginItemInfoAuthorValue, info.author)
        dialog.setCopyableTextIfAbsent(binding.descriptionValue, info.description)
        dialog.setCopyableTextIfAbsent(binding.pluginItemInfoPackageSizeValue, info.packageSize.takeIf { it > 0 }?.let { formatSize(it) })

        val dialogIcon = item.icon ?: AppCompatResources.getDrawable(context, R.drawable.ic_plugin_center_default)?.mutate()?.also { d ->
            val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
            DrawableCompat.setTint(d, adjustedImageContrastColor)
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
        } ?: AppCompatResources.getDrawable(context, R.mipmap.ic_app_shortcut_plugin_center_adaptive_round)

        if (dialogIcon != null) {
            dialog.setIcon(
                dialogIcon.toCircular(
                    context = context,
                    sizePx = DisplayUtils.dpToPx(48.0F).roundToInt(),
                    borderWidthPx = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_icon_border_width),
                    borderColor = context.getColor(R.color.plugin_center_item_icon_border),
                )
            )
            dialog.iconView.colorFilterWithDesaturateOrNull(item.isEnabled, 0.5F)
            if (info is PluginInfoInstalled) {
                dialog.makeSettingsLaunchable({ it.iconView }, info.packageName)
            }
        }

        // If the index does not provide size, try to HEAD request to get it, update display after success.
        // zh-CN: 若索引未给 size, 尝试 HEAD 获取, 成功后更新显示.
        if (info.packageSize <= 0) {
            info.apkUrl.takeUnless { it.isNullOrBlank() }?.let { url ->
                CoroutineScope(Dispatchers.IO).launch {
                    val size = PluginInstaller.probeContentLength(url)
                    if (size != null && size > 0 && currentDialog?.get() === dialog) {
                        // TODO 更新当前 item 的 "可安装包大小" 仅用于对话框展示 (持久化可留到 M2).
                        withContext(Dispatchers.Main) {
                            dialog.setCopyableTextIfAbsent(binding.pluginItemInfoPackageSizeValue, formatSize(size))
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun restoreEssentialViews(binding: PluginInfoDialogItemsBinding, context: Context, info: PluginInfoBase) {
        if (info.collaborators.isNotEmpty()) {
            binding.pluginItemInfoCollaboratorsFirstLabel.text = "${context.getString(R.string.plugin_item_info_collaborators)} [1/${info.collaborators.size}]"
            binding.pluginItemInfoCollaboratorsFirstValue.text = info.collaborators[0]
            binding.pluginItemInfoCollaboratorsFirstParent.isVisible = true
        }
        if (info.collaborators.size > 1) {
            binding.pluginItemInfoCollaboratorsSecondLabel.text = "${context.getString(R.string.plugin_item_info_collaborators)} [2/${info.collaborators.size}]"
            binding.pluginItemInfoCollaboratorsSecondValue.text = info.collaborators[1]
            binding.pluginItemInfoCollaboratorsSecondParent.isVisible = true
        }
        if (info.collaborators.size > 2) {
            binding.pluginItemInfoCollaboratorsThirdLabel.text = "${context.getString(R.string.plugin_item_info_collaborators)} [3/${info.collaborators.size}]"
            binding.pluginItemInfoCollaboratorsThirdValue.text = info.collaborators[2]
            binding.pluginItemInfoCollaboratorsThirdParent.isVisible = true
        }
        when (info) {
            is PluginInfoInstalled -> {
                info.updatableVersion?.let {
                    binding.versionLabel.text = context.getString(R.string.plugin_item_info_installed_version)
                    binding.updatableVersionValue.text = it
                    binding.updatableVersionParent.isVisible = true
                }
                info.firstInstallTime?.setupListItemView(binding.pluginItemInfoFirstInstallTimeParent, binding.pluginItemInfoFirstInstallTimeValue)
                info.lastUpdateTime?.setupListItemView(binding.pluginItemInfoLastUpdateTimeParent, binding.pluginItemInfoLastUpdateTimeValue)
            }
            is PluginInfoInstallable -> {
                info.lastInstallTime?.setupListItemView(binding.pluginItemInfoLastInstallTimeParent, binding.pluginItemInfoLastInstallTimeValue)
                info.lastUninstallTime?.setupListItemView(binding.pluginItemInfoLastUninstallTimeParent, binding.pluginItemInfoLastUninstallTimeValue)
            }
        }
    }

    private fun updateGuidelines(binding: PluginInfoDialogItemsBinding) {
        val filteredBindings = listOf(
            binding.stateLabel to binding.stateGuideline,
            binding.packageNameLabel to binding.packageNameGuideline,
            binding.versionLabel to binding.versionGuideline,
            binding.updatableVersionLabel to binding.updatableVersionGuideline,
            binding.pluginItemInfoAuthorLabel to binding.pluginItemInfoAuthorGuideline,
            binding.pluginItemInfoCollaboratorsFirstLabel to binding.pluginItemInfoCollaboratorsFirstGuideline,
            binding.pluginItemInfoCollaboratorsSecondLabel to binding.pluginItemInfoCollaboratorsSecondGuideline,
            binding.pluginItemInfoCollaboratorsThirdLabel to binding.pluginItemInfoCollaboratorsThirdGuideline,
            binding.descriptionLabel to binding.descriptionGuideline,
            binding.pluginItemInfoPackageSizeLabel to binding.pluginItemInfoPackageSizeGuideline,
            binding.pluginItemInfoFirstInstallTimeLabel to binding.pluginItemInfoFirstInstallTimeGuideline,
            binding.pluginItemInfoLastUpdateTimeLabel to binding.pluginItemInfoLastUpdateTimeGuideline,
            binding.pluginItemInfoLastInstallTimeLabel to binding.pluginItemInfoLastInstallTimeGuideline,
            binding.pluginItemInfoLastUninstallTimeLabel to binding.pluginItemInfoLastUninstallTimeGuideline,
        ).filter { (it.first.parent as? ConstraintLayout)?.isVisible == true }

        @Suppress("DuplicatedCode")
        val maxWidth = filteredBindings.maxOfOrNull { it.first.apply { measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth } ?: return

        filteredBindings.forEach { (_, guideline) ->
            guideline.layoutParams = (guideline.layoutParams as ConstraintLayout.LayoutParams).also {
                it.guideBegin = maxWidth
            }
        }
    }

    private fun Long.setupListItemView(parentView: ConstraintLayout, valueView: TextView) {
        this.takeIf { it > 0 }?.let {
            valueView.text = TimeUtils.formatTimestamp(it)
            parentView.isVisible = true
        }
    }

    private fun formatSize(size: Long): String = Bytes.string(
        source = size.toDouble(),
        fromUnit = "B",
        toUnit = "AUTO",
        useIecIdentifier = true,
        useSpace = true,
        fractionDigits = 1,
        trimTrailingZero = false,
        signature = "pluginItemInfo.getPackageSize",
    )

    private sealed interface PluginInfoBase {
        val title: String
        val states: List<String>
        val packageName: String
        val version: String
        val author: String?
        val collaborators: List<String>
        val description: String?
        val packageSize: Long
        val apkUrl: String?
        val sha256: String?
    }

    private data class PluginInfoInstallable(
        override val title: String,
        override val states: List<String>,
        override val packageName: String,
        override val version: String,
        override val author: String?,
        override val collaborators: List<String>,
        override val description: String?,
        override val packageSize: Long,
        override val apkUrl: String?,
        override val sha256: String?,
        val lastInstallTime: Long?,
        val lastUninstallTime: Long?,
    ) : PluginInfoBase

    private data class PluginInfoInstalled(
        override val title: String,
        override val states: List<String>,
        override val packageName: String,
        override val version: String,
        override val author: String?,
        override val collaborators: List<String>,
        override val description: String?,
        override val packageSize: Long,
        override val apkUrl: String?,
        override val sha256: String?,
        val updatableVersion: String? = null,
        val firstInstallTime: Long?,
        val lastUpdateTime: Long?,
    ) : PluginInfoBase

}
