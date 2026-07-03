package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View.MeasureSpec.UNSPECIFIED
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.makeSettingsLaunchable
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.setCopyableTextIfAbsent
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.TimeUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.colorFilterWithDesaturateOrNull
import org.autojs.autojs.util.ViewUtils.toCircular
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginInfoDialogItemsBinding
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

object PluginInfoDialogManager {

    // Hold the current dialog and package name for refreshing on onResume.
    // zh-CN: 持有当前对话框与包名, 便于 onResume 时刷新.
    private var currentDialog: WeakReference<MaterialDialog>? = null
    private var currentPackageName: String? = null
    private val remoteDialogIconCache = object : LruCache<String, Drawable.ConstantState>(40) {}

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
            item = item,
            states = states,
            lastInstallTime = item.lastInstallTime,
            lastUninstallTime = item.lastUninstallTime,
        )
        showPluginInfoDialogInternal(context, info) {
            neutralText(R.string.text_install)
            neutralColorRes(R.color.dialog_button_attraction)
            onNeutral { d, _ ->
                d.dismiss()
                val url = info.validateApkUrlAndPrompt(context, d) ?: return@onNeutral
                PluginAbiCompatibilityPrompt.runBeforeDownload(context, info.item) {
                    CoroutineScope(Dispatchers.Main).launch {
                        PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                    }
                }
            }
        }
    }

    private fun showInstalledPluginInfoDialog(context: Context, item: PluginCenterItem) {
        val info = PluginInfoInstalled(
            item = item,
            states = parseStates(context, item),
            updatableVersion = item.updatableVersionSummary,
            firstInstallTime = item.firstInstallTime,
            lastUpdateTime = item.lastUpdateTime,
        )
        showPluginInfoDialogInternal(context, info) {
            if (item.isUpdatable) {
                neutralText(R.string.dialog_button_view_update)
                neutralColorRes(R.color.dialog_button_attraction)
                onNeutral { d, _ ->
                    showUpdatablePluginInfoDialog(context, PluginInfoUpdatable(item), d)
                }
            }
        }.apply {
            makeSettingsLaunchable({ it.iconView }, info.packageName)
        }
    }

    private fun showPluginInfoDialogInternal(context: Context, info: PluginInfoBase, builderApplier: MaterialDialog.Builder.() -> Unit = {}): MaterialDialog {
        val binding = PluginInfoDialogItemsBinding.inflate(LayoutInflater.from(context))

        val dialog = MaterialDialog.Builder(context)
            .limitIconToDefaultSize()
            .title(info.title)
            .customView(binding.root, false)
            .negativeText(R.string.dialog_button_dismiss)
            .onNegative { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .apply(builderApplier)
            .showAdaptive()
            .apply { makeTextCopyable { titleView } }

        // Hold the current dialog and package name for refreshing on onResume.
        // zh-CN: 记录 "当前对话框" 与包名, 便于 onResume 刷新.
        currentDialog = WeakReference(dialog)
        currentPackageName = info.packageName

        restoreEssentialViews(binding, context, info)
        updateGuidelines(binding)

        when (info.states.size) {
            0 -> {
                binding.stateParent.isVisible = false
            }
            1 -> {
                binding.stateValueFirst.text = info.states[0]

                binding.stateValueFirst.isVisible = true
            }
            2 -> {
                binding.stateValueFirst.text = info.states[0]
                binding.stateValueSecond.text = info.states[1]

                binding.stateValueFirst.isVisible = true
                binding.stateSpliterFirstSecond.isVisible = true
                binding.stateValueSecond.isVisible = true
            }
            else -> {
                binding.stateValueFirst.text = info.states[0]
                binding.stateValueSecond.text = info.states[1]
                binding.stateValueThird.text = info.states[2]

                binding.stateValueFirst.isVisible = true
                binding.stateSpliterFirstSecond.isVisible = true
                binding.stateValueSecond.isVisible = true
                binding.stateSpliterSecondThird.isVisible = true
                binding.stateValueThird.isVisible = true
            }
        }

        dialog.setCopyableTextIfAbsent(binding.packageNameValue, info.packageName)
        dialog.setCopyableTextIfAbsent(binding.mechanismValue, info.mechanism)
        dialog.setCopyableTextIfAbsent(binding.pluginItemInfoSourceValue, resolveSourceText(context, info))
        dialog.setCopyableTextIfAbsent(binding.versionValue, info.version)
        dialog.setCopyableTextIfAbsent(binding.pluginItemInfoAuthorValue, info.author)
        dialog.setCopyableTextIfAbsent(binding.descriptionValue, info.description)
        dialog.setCopyableTextIfAbsent(binding.pluginItemInfoPackageSizeValue, info.packageSize.takeIf { it > 0 }?.let { formatSize(it) })
        dialog.setCopyableTextIfAbsent(
            binding.pluginItemInfoSupportedAbisValue,
            PluginAbiUtils.formatSupportedAbisForDisplay(
                info.supportedAbis,
                context.getString(R.string.text_unknown),
                context.getString(R.string.text_all),
            ),
        )
        bindDialogIcon(context, dialog, info)

        // If the index does not provide size, try to HEAD request to get it, update display after success.
        // zh-CN: 若索引未给 size, 尝试 HEAD 获取, 成功后更新显示.
        if (info.packageSize <= 0) {
            info.apkUrl.takeUnless { it.isNullOrBlank() }?.let { url ->
                CoroutineScope(Dispatchers.IO).launch {
                    val size = PluginInstaller.probeContentLength(url)
                    if (size != null && size > 0 && currentDialog?.get() === dialog) {
                        withContext(Dispatchers.Main) {
                            dialog.setCopyableTextIfAbsent(binding.pluginItemInfoPackageSizeValue, formatSize(size))
                        }
                    }
                }
            }
        }

        return dialog
    }

    private fun resolveSourceText(context: Context, info: PluginInfoBase): String {
        val source = info.item.source.takeIf {
            !info.item.installableApkUrl.isNullOrBlank() ||
                    !info.item.updatableApkUrl.isNullOrBlank() ||
                    !info.item.remoteIconUrl.isNullOrBlank() ||
                    !info.item.instructionMarkdownUrl.isNullOrBlank()
        }
        return when (source) {
            PluginIndexSource.OFFICIAL -> context.getString(R.string.text_plugin_official)
            PluginIndexSource.THIRD_PARTY -> context.getString(R.string.text_plugin_third_party)
            null -> context.getString(R.string.text_unknown)
        }
    }

    private fun resolveInitialDialogIcon(context: Context, info: PluginInfoBase): Drawable {
        info.icon?.let { return toDialogCircularIcon(context, it) }
        val remoteUrl = info.remoteIconUrl?.takeIf { it.isNotBlank() }
        val preferredUrl = PluginIconUrlResolver.resolveForCurrentMode(context, remoteUrl, info.remoteNightIconUrl) ?: remoteUrl
        preferredUrl
            ?.let { url -> getCachedRemoteDialogIcon(context, url) }
            ?.let { return it }
        remoteUrl
            ?.takeIf { it != preferredUrl }
            ?.let { url -> getCachedRemoteDialogIcon(context, url) }
            ?.let { return it }

        val fallbackDrawable = buildDefaultDialogIconDrawable(context)
            ?: AppCompatResources.getDrawable(context, R.mipmap.ic_app_shortcut_plugin_center_adaptive_round)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_three_dots_outline_small)
            ?: error("Dialog icon fallback resource is missing.")
        return toDialogCircularIcon(context, fallbackDrawable)
    }

    private fun bindDialogIcon(context: Context, dialog: MaterialDialog, info: PluginInfoBase) {
        val initialDialogIcon = resolveInitialDialogIcon(context, info)
        dialog.setIcon(initialDialogIcon)
        dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
        if (info.icon != null) return
        val remoteUrl = info.remoteIconUrl?.takeIf { it.isNotBlank() } ?: return
        val preferredUrl = PluginIconUrlResolver.resolveForCurrentMode(context, remoteUrl, info.remoteNightIconUrl) ?: remoteUrl
        val fallbackUrl = listOfNotNull(remoteUrl, info.remoteNightIconUrl)
            .firstOrNull { it.isNotBlank() && it != preferredUrl }

        getCachedRemoteDialogIcon(context, preferredUrl)?.let { cached ->
            dialog.setIcon(cached)
            dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
            return
        }
        val fallbackCached = fallbackUrl?.let { url ->
            getCachedRemoteDialogIcon(context, url)
        }
        if (fallbackCached != null) {
            // Use opposite-mode cache only as temporary placeholder; still fetch preferred mode icon.
            dialog.setIcon(fallbackCached)
            dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
        }

        val requestPlaceholder = fallbackCached ?: initialDialogIcon
        var request = buildDialogRemoteIconRequest(
            context = context,
            dialog = dialog,
            info = info,
            remoteUrl = preferredUrl,
            initialDialogIcon = requestPlaceholder,
        )
        if (fallbackUrl != null) {
            request = request.error(
                buildDialogRemoteIconRequest(
                    context = context,
                    dialog = dialog,
                    info = info,
                    remoteUrl = fallbackUrl,
                    initialDialogIcon = requestPlaceholder,
                ),
            )
        }
        request.into(dialog.iconView)
    }

    private fun buildDialogRemoteIconRequest(
        context: Context,
        dialog: MaterialDialog,
        info: PluginInfoBase,
        remoteUrl: String,
        initialDialogIcon: Drawable,
    ): RequestBuilder<Drawable> {
        return Glide.with(dialog.iconView)
            .load(remoteUrl)
            .placeholder(initialDialogIcon)
            .error(initialDialogIcon)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .dontAnimate()
            .listener(
                object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        dialog.setIcon(initialDialogIcon)
                        dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        val circular = toDialogCircularIcon(context, resource)
                        cacheRemoteDialogIcon(remoteUrl, circular)
                        dialog.setIcon(circular)
                        dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
                        return true
                    }
                },
            )
    }

    private fun getCachedRemoteDialogIcon(context: Context, remoteUrl: String): Drawable? =
        remoteDialogIconCache.get(remoteUrl)?.newDrawable(context.resources)?.mutate()

    private fun cacheRemoteDialogIcon(remoteUrl: String, drawable: Drawable) {
        drawable.constantState?.let { remoteDialogIconCache.put(remoteUrl, it) }
    }

    private fun buildDefaultDialogIconDrawable(context: Context): Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.ic_plugin_center_default)
            ?.mutate()
            ?.also { d ->
                val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
                DrawableCompat.setTint(d, adjustedImageContrastColor)
                DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            }

    private fun toDialogCircularIcon(context: Context, source: Drawable): Drawable =
        source.toCircular(
            context = context,
            sizePx = DisplayUtils.dpToPx(48.0F).roundToInt(),
            borderWidthPx = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_icon_border_width),
            borderColor = context.getColor(R.color.plugin_center_item_icon_border),
        )

    internal fun showUpdatablePluginInfoDialog(context: Context, info: PluginInfoUpdatable, parentDialog: MaterialDialog? = null) {
        val url = info.validateApkUrlAndPrompt(context, parentDialog) ?: return
        parentDialog?.dismiss()

        val ignoreUpdateOption = MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_ignore_current_update)) { updateDialog ->
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.prompt_add_ignored_plugin_version)
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    info.versionCode?.takeIf { it > 0 }?.let {
                        UpdateIgnoreStore.ignoreVersion(info.packageName, it, info.item.versionName)
                    }
                    ViewUtils.showToast(context, R.string.text_done)
                    updateDialog.dismiss()
                }
                .showAdaptive()
        }

        val releaseHistoryOption = MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_release_history)) { d ->
            val changelogUrl = info.changelogUrl
            if (changelogUrl.isNullOrBlank()) {
                ViewUtils.showSnack(d.view, R.string.text_unavailable, false)
            } else {
                IntentUtils.browse(context, changelogUrl, IntentUtils.SnackExceptionHolder(d.view))
            }
        }

        val dialog = MaterialDialog.Builder(context)
            .title(info.version ?: info.title)
            .options(listOf(ignoreUpdateOption, releaseHistoryOption))
            .content(R.string.text_retrieving_release_notes)
            .neutralText(R.string.dialog_button_release_history)
            .neutralColor(
                context.getColor(
                    if (info.changelogUrl.isNullOrBlank()) R.color.dialog_button_unavailable else R.color.dialog_button_hint,
                ),
            )
            .onNeutral { d, _ ->
                val changelogUrl = info.changelogUrl
                if (changelogUrl.isNullOrBlank()) {
                    ViewUtils.showSnack(d.view, R.string.text_unavailable, false)
                } else {
                    IntentUtils.browse(context, changelogUrl, IntentUtils.SnackExceptionHolder(d.view))
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColor(context.getColor(R.color.dialog_button_default))
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(R.string.dialog_button_update_now)
            .positiveColor(context.getColor(R.color.dialog_button_attraction))
            .onPositive { d, _ ->
                d.dismiss()
                PluginAbiCompatibilityPrompt.runBeforeDownload(context, info.item) {
                    CoroutineScope(Dispatchers.Main).launch {
                        PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                    }
                }
            }
            .autoDismiss(false)
            .cancelable(false)
            .showAdaptive()

        CoroutineScope(Dispatchers.Main).launch {
            val releaseNotes = withContext(Dispatchers.IO) {
                val fallback = info.changelogText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.text_no_release_notes)
                val changelogUrl = info.changelogUrl?.takeIf { it.isNotBlank() } ?: return@withContext fallback
                runCatching {
                    fetchReleaseNotesText(changelogUrl)
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
            }
            if (dialog.isShowing) {
                dialog.setContent(releaseNotes)
            }
        }
    }

    private fun parseStates(context: Context, item: PluginCenterItem): List<String> {
        if (!item.isInstalled) {
            return listOf(context.getString(R.string.text_not_installed))
        }

        val states = mutableListOf<String>()

        val authText = when (item.authorizedState) {
            PluginAuthorizedState.OFFICIAL -> context.getString(R.string.text_plugin_official)
            PluginAuthorizedState.TRUSTED -> context.getString(R.string.text_plugin_trusted)
            PluginAuthorizedState.USER_GRANTED -> context.getString(R.string.text_plugin_authorized)
            PluginAuthorizedState.REQUIRED -> context.getString(R.string.text_plugin_authorization_required)
        }
        states += authText

        val enabledText = when (item.enabledState) {
            PluginEnabledState.READY -> context.getString(R.string.text_enabled)
            PluginEnabledState.DISABLED -> context.getString(R.string.text_disabled)
            is PluginEnabledState.ERROR -> context.getString(R.string.text_error)
        }
        states += enabledText

        if (item.isUpdatable) {
            states += context.getString(R.string.text_updatable)
        }

        when (item.activatedState) {
            PluginActivatedState.RECOMMENDED -> states += context.getString(R.string.text_plugin_activation_recommended)
            PluginActivatedState.DONE -> states += context.getString(R.string.text_plugin_activated)
            else -> Unit
        }

        return states.filter { it.isNotBlank() }
    }

    private fun PluginInfoBase.validateApkUrlAndPrompt(context: Context, parentDialog: MaterialDialog?): String? {
        val url = this.apkUrl
        return when {
            url.isNullOrBlank() -> {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.error_no_available_url_provided_for_current_plugin)
                    .positiveText(R.string.dialog_button_dismiss)
                    .showAdaptive()
                parentDialog
                    ?.getActionButton(DialogAction.POSITIVE)
                    ?.setTextColor(context.getColor(R.color.dialog_button_unavailable))
                null
            }
            else -> url
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
            is PluginInfoUpdatable -> {
                /* No additional operations needed. */
            }
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
            binding.mechanismLabel to binding.mechanismGuideline,
            binding.pluginItemInfoSourceLabel to binding.pluginItemInfoSourceGuideline,
            binding.packageNameLabel to binding.packageNameGuideline,
            binding.versionLabel to binding.versionGuideline,
            binding.updatableVersionLabel to binding.updatableVersionGuideline,
            binding.pluginItemInfoAuthorLabel to binding.pluginItemInfoAuthorGuideline,
            binding.pluginItemInfoCollaboratorsFirstLabel to binding.pluginItemInfoCollaboratorsFirstGuideline,
            binding.pluginItemInfoCollaboratorsSecondLabel to binding.pluginItemInfoCollaboratorsSecondGuideline,
            binding.pluginItemInfoCollaboratorsThirdLabel to binding.pluginItemInfoCollaboratorsThirdGuideline,
            binding.descriptionLabel to binding.descriptionGuideline,
            binding.pluginItemInfoPackageSizeLabel to binding.pluginItemInfoPackageSizeGuideline,
            binding.pluginItemInfoSupportedAbisLabel to binding.pluginItemInfoSupportedAbisGuideline,
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

    private fun fetchReleaseNotesText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "text/markdown, text/plain, */*")
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $url")
            }
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText().trim() }
        } finally {
            conn.disconnect()
        }
    }

    private sealed interface PluginInfoBase {
        val item: PluginCenterItem
        val states: List<String> get() = emptyList()
        val isEnabled: Boolean get() = item.isEnabled
        val title: String get() = item.title
        val icon: Drawable? get() = item.icon
        val packageName: String get() = item.packageName
        val mechanism: String get() = item.mechanism.displayName
        val version: String? get() = item.versionSummary
        val versionCode: Long? get() = item.versionCode
        val remoteIconUrl: String? get() = item.remoteIconUrl
        val remoteNightIconUrl: String? get() = item.remoteNightIconUrl
        val author: String? get() = item.author
        val collaborators: List<String> get() = item.collaborators
        val description: String? get() = item.description
        val packageSize: Long
        val supportedAbis: List<String>? get() = item.supportedAbis
        val apkUrl: String? get() = item.installableApkUrl
        val sha256: String? get() = item.installableApkSha256
        val changelogUrl: String? get() = null
        val changelogText: String? get() = null
    }

    internal class PluginInfoUpdatable(
        override val item: PluginCenterItem,
        override val packageSize: Long = item.updatableApkSizeBytes ?: item.installableApkSizeBytes ?: 0L,
        override val version: String? = item.updatableVersionSummary,
        override val versionCode: Long? = item.updatableVersionCode,
        override val apkUrl: String? = item.updatableApkUrl ?: item.installableApkUrl,
        override val sha256: String? = item.updatableApkSha256 ?: item.installableApkSha256,
        override val changelogUrl: String? = item.updatableChangelogUrl,
        override val changelogText: String? = item.updatableChangelogText,
    ) : PluginInfoBase

    private data class PluginInfoInstallable(
        override val item: PluginCenterItem,
        override val states: List<String>,
        override val packageSize: Long = item.installableApkSizeBytes ?: 0L,
        val lastInstallTime: Long?,
        val lastUninstallTime: Long?,
    ) : PluginInfoBase

    private data class PluginInfoInstalled(
        override val item: PluginCenterItem,
        override val states: List<String>,
        override val packageSize: Long = item.packageSize,
        val updatableVersion: String? = null,
        val firstInstallTime: Long?,
        val lastUpdateTime: Long?,
    ) : PluginInfoBase

}
