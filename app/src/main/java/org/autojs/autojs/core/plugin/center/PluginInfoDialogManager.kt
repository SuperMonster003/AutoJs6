package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.network.UpdateIgnoreStore
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.dialog.InfoDialog
import org.autojs.autojs.ui.dialog.InfoDialogItems
import org.autojs.autojs.ui.settings.DisplayReleaseHistoryActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.makeSettingsLaunchable
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.TimeUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.colorFilterWithDesaturateOrNull
import org.autojs.autojs.util.ViewUtils.toCircular
import org.autojs.autojs6.R

object PluginInfoDialogManager {

    // Hold the current dialog and package name for refreshing on onResume.
    // zh-CN: 持有当前对话框与包名, 便于 onResume 时刷新.
    private var currentDialog: WeakReference<MaterialDialog>? = null
    private var currentPackageName: String? = null
    private val remoteDialogIconCache = object : LruCache<String, Bitmap>(40) {}

    fun refreshIfShowing(context: Context, allItems: List<PluginCenterItem>) {
        val dialog = currentDialog?.get() ?: return
        val pkg = currentPackageName ?: return
        if (!dialog.isShowing) return
        val target = allItems.firstOrNull { it.packageName == pkg } ?: return
        showPluginInfoDialog(context, target)
        dialog.dismiss()
    }

    @JvmStatic
    fun showPluginInfoDialog(context: Context, item: PluginCenterItem): InfoDialog {
        return if (item.isInstalled) {
            showInstalledPluginInfoDialog(context, item)
        } else {
            showInstallablePluginInfoDialog(context, item)
        }
    }

    private fun showInstallablePluginInfoDialog(context: Context, item: PluginCenterItem): InfoDialog {
        val states = listOf(context.getString(R.string.text_installable))
        val info = PluginInfoInstallable(
            item = item,
            states = states,
            lastInstallTime = item.lastInstallTime,
            lastUninstallTime = item.lastUninstallTime,
        )
        return showPluginInfoDialogInternal(context, info) {
            neutralText(R.string.text_install)
            neutralColorRes(R.color.dialog_button_attraction)
            onNeutral { d, _ ->
                d.dismiss()
                val url = info.validateApkUrlAndPrompt(context, d) ?: return@onNeutral
                PluginInstallCompatibilityPrompt.runBeforeDownload(context, info.item) {
                    CoroutineScope(Dispatchers.Main).launch {
                        PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                    }
                }
            }
        }
    }

    private fun showInstalledPluginInfoDialog(context: Context, item: PluginCenterItem): InfoDialog {
        val info = PluginInfoInstalled(
            item = item,
            states = parseStates(context, item),
            updatableVersion = item.updatableVersionSummary,
            firstInstallTime = item.firstInstallTime,
            lastUpdateTime = item.lastUpdateTime,
        )
        return showPluginInfoDialogInternal(context, info) {
            if (item.isUpdatable) {
                neutralText(
                    if (item.requiresDowngradeReinstall) {
                        R.string.dialog_button_view_matched_version
                    } else {
                        R.string.dialog_button_view_update
                    },
                )
                neutralColorRes(R.color.dialog_button_attraction)
                onNeutral { d, _ ->
                    showUpdatablePluginInfoDialog(context, PluginInfoUpdatable(item), d)
                }
            }
        }.apply {
            dialog.makeSettingsLaunchable({ it.iconView }, info.packageName)
        }
    }

    private fun showPluginInfoDialogInternal(context: Context, info: PluginInfoBase, builderApplier: MaterialDialog.Builder.() -> Unit = {}): InfoDialog {
        val infoDialog = InfoDialog(context)
        val items = infoDialog.items.apply {
            addItem(R.string.text_label_state, id = "state").maxLines = 5
            addItem(R.string.plugin_item_info_mechanism, id = "mechanism")
            addItem(R.string.text_source, id = "pluginItemInfoSource")
            addItem(R.string.apk_info_package_name, id = "packageName").maxLines = 5
            addItem(R.string.text_version, id = "version")
            addItem(R.string.plugin_item_info_updatable_version, id = "updatableVersion").isVisible = false
            addItem(R.string.plugin_item_info_author, id = "pluginItemInfoAuthor").maxLines = 5
            addItem(R.string.plugin_item_info_collaborators, id = "pluginItemInfoCollaboratorsFirst").isVisible = false
            addItem(R.string.plugin_item_info_collaborators, id = "pluginItemInfoCollaboratorsSecond").isVisible = false
            addItem(R.string.plugin_item_info_collaborators, id = "pluginItemInfoCollaboratorsThird").isVisible = false
            addItem(R.string.text_description, id = "description").maxLines = 20
            addItem(R.string.plugin_item_info_package_size, id = "pluginItemInfoPackageSize")
            addItem(R.string.plugin_item_info_supported_abis, id = "pluginItemInfoSupportedAbis").maxLines = 3
            addItem(R.string.plugin_item_info_installed_abis, id = "pluginItemInfoInstalledAbis").apply {
                maxLines = 3
                isVisible = false
            }
            addItem(R.string.plugin_item_info_first_install_time, id = "pluginItemInfoFirstInstallTime").isVisible = false
            addItem(R.string.plugin_item_info_last_update_time, id = "pluginItemInfoLastUpdateTime").isVisible = false
            addItem(R.string.plugin_item_info_last_install_time, id = "pluginItemInfoLastInstallTime").isVisible = false
            addItem(R.string.plugin_item_info_last_uninstall_time, id = "pluginItemInfoLastUninstallTime").isVisible = false
        }

        val dialog = infoDialog.builder
            .limitIconToDefaultSize()
            .title(info.title)
            .negativeText(R.string.dialog_button_dismiss)
            .onNegative { d, _ -> d.dismiss() }
            .autoDismiss(false)
            .apply(builderApplier)
            .let { infoDialog.show() }

        // Hold the current dialog and package name for refreshing on onResume.
        // zh-CN: 记录 "当前对话框" 与包名, 便于 onResume 刷新.
        currentDialog = WeakReference(dialog)
        currentPackageName = info.packageName

        restoreEssentialViews(items, context, info)

        if (info.states.isEmpty()) {
            items["state"].isVisible = false
        } else {
            items["state"].setValue(info.states.joinToString(" ${context.getString(R.string.symbol_pipe)} "))
        }

        items["packageName"].setValueIfAbsent(info.packageName)
        items["mechanism"].setValueIfAbsent(info.mechanism)
        items["pluginItemInfoSource"].setValueIfAbsent(resolveSourceText(context, info))
        items["version"].setValueIfAbsent(info.version)
        items["pluginItemInfoAuthor"].setValueIfAbsent(info.author)
        items["description"].setValueIfAbsent(info.description)
        items["pluginItemInfoPackageSize"].setValueIfAbsent(info.packageSize.takeIf { it > 0 }?.let { formatSize(it) })
        items["pluginItemInfoSupportedAbis"].setValueIfAbsent(
            PluginAbiUtils.formatSupportedAbisForDisplay(
                info.supportedAbis,
                context.getString(R.string.text_unknown),
                context.getString(R.string.text_all),
            ),
        )
        // Installed ABIs come from the installed package itself; the row stays hidden for
        // uninstalled plugins and for packages without native code.
        // zh-CN: "已安装 ABI" 取自已安装包本身; 未安装或无原生代码时不显示该行.
        info.installedAbis?.takeIf { it.isNotEmpty() }?.let { installedAbis ->
            items["pluginItemInfoInstalledAbis"].apply {
                setValueIfAbsent(PluginAbiUtils.formatAbiListOrFallback(installedAbis, context.getString(R.string.text_unknown)))
                isVisible = true
            }
        }
        bindDialogIcon(context, dialog, info)

        // If the index does not provide size, try to HEAD request to get it, update display after success.
        // zh-CN: 若索引未给 size, 尝试 HEAD 获取, 成功后更新显示.
        if (info.packageSize <= 0) {
            info.apkUrl.takeUnless { it.isNullOrBlank() }?.let { url ->
                infoDialog.scope.launch {
                    val size = withContext(Dispatchers.IO) { PluginInstaller.probeContentLength(url) }
                    if (size != null && size > 0 && currentDialog?.get() === dialog) {
                        items["pluginItemInfoPackageSize"].setValueIfAbsent(formatSize(size))
                    }
                }
            }
        }

        return infoDialog
    }

    private fun resolveSourceText(context: Context, info: PluginInfoBase): String {
        val source = info.item.source.takeIf {
            !info.item.installableApkUrl.isNullOrBlank() ||
                    !info.item.updatableApkUrl.isNullOrBlank() ||
                    !info.item.remoteIconUrl.isNullOrBlank() ||
                    !info.item.instructionMarkdownUrl.isNullOrBlank() ||
                    info.item.isOfficialVerified
        }
        return when (source) {
            PluginIndexSource.OFFICIAL -> context.getString(R.string.text_plugin_official)
            PluginIndexSource.THIRD_PARTY -> context.getString(R.string.text_plugin_third_party)
            null -> context.getString(R.string.text_unknown)
        }
    }

    private fun resolveInitialDialogIcon(context: Context, info: PluginInfoBase): Drawable {
        info.icon?.let { return toDialogCircularIcon(context, PluginInstalledIconResolver.forCurrentMode(it)) }
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
        val background = PluginIconBackgroundPolicy.resolve(
            info.packageName, ViewUtils.isNightModeEnabled,
            info.item.iconBackgroundColor, info.item.nightIconBackgroundColor,
            info.icon?.let(PluginInstalledIconResolver::backgroundForCurrentMode),
        )
        // Keep the surface separate so disabled artwork is desaturated without fading its background.
        // zh-CN: 背景独立绘制, 禁用图稿灰度化时不降低圆形底色的透明度.
        dialog.iconView.background = toDialogCircularIcon(context, ColorDrawable(background), Color.TRANSPARENT)
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
                        cacheRemoteDialogIcon(remoteUrl, resource)
                        dialog.setIcon(circular)
                        dialog.iconView.colorFilterWithDesaturateOrNull(info.isEnabled, 0.5F)
                        return true
                    }
                },
            )
    }

    private fun getCachedRemoteDialogIcon(context: Context, remoteUrl: String): Drawable? =
        remoteDialogIconCache.get(remoteUrl)?.let { BitmapDrawable(context.resources, it) }
            ?.let { toDialogCircularIcon(context, it) }

    private fun cacheRemoteDialogIcon(remoteUrl: String, drawable: Drawable) {
        // Own the pixels independently of Glide, and apply themed borders only when displayed.
        // zh-CN: 独立持有像素, 避免 Glide 回收影响缓存; 描边在显示时按当前主题生成.
        drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)?.let { remoteDialogIconCache.put(remoteUrl, it) }
    }

    private fun buildDefaultDialogIconDrawable(context: Context): Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.ic_plugin_center_default)
            ?.mutate()
            ?.also { d ->
                val adjustedImageContrastColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 2.3)
                DrawableCompat.setTint(d, adjustedImageContrastColor)
                DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            }

    private fun toDialogCircularIcon(
        context: Context,
        source: Drawable,
        borderColor: Int = context.getColor(R.color.plugin_center_item_icon_border),
    ): Drawable =
        source.toCircular(
            context = context,
            sizePx = DisplayUtils.dpToPx(48.0F).roundToInt(),
            borderWidthPx = context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_icon_border_width),
            borderColor = borderColor,
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

        val dialog = MaterialDialog.Builder(context)
            .title(info.version ?: info.title)
            .apply {
                if (!info.item.requiresDowngradeReinstall) {
                    options(listOf(ignoreUpdateOption))
                }
            }
            .content(R.string.text_retrieving_release_notes)
            .neutralText(R.string.dialog_button_release_history)
            .neutralColor(
                context.getColor(
                    if (info.changelogUrl.isNullOrBlank() && info.changelogText.isNullOrBlank()) {
                        R.color.dialog_button_unavailable
                    } else {
                        R.color.dialog_button_hint
                    },
                ),
            )
            .onNeutral { d, _ ->
                if (info.changelogUrl.isNullOrBlank() && info.changelogText.isNullOrBlank()) {
                    ViewUtils.showSnack(d.view, R.string.text_unavailable, false)
                } else {
                    DisplayReleaseHistoryActivity.launchPlugin(
                        context = context,
                        title = info.title,
                        version = info.version,
                        changelogText = info.changelogText,
                        changelogUrl = info.changelogUrl,
                    )
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColor(context.getColor(R.color.dialog_button_default))
            .onNegative { d, _ -> d.dismiss() }
            .positiveText(
                if (info.item.requiresDowngradeReinstall) {
                    R.string.text_uninstall
                } else {
                    R.string.dialog_button_update_now
                },
            )
            .positiveColor(context.getColor(R.color.dialog_button_attraction))
            .onPositive { d, _ ->
                d.dismiss()
                PluginInstallCompatibilityPrompt.runBeforeDownload(context, info.item) {
                    CoroutineScope(Dispatchers.Main).launch {
                        PluginInstaller.installFromUrlWithPrompt(context, url, info.sha256)
                    }
                }
            }
            .autoDismiss(false)
            .showAdaptive()

        CoroutineScope(Dispatchers.Main).launch {
            val releaseNotes = withContext(Dispatchers.IO) {
                info.changelogText?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                val fallback = context.getString(R.string.text_no_release_notes)
                val changelogUrl = info.changelogUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.takeIf(::isPlainTextChangelogUrl)
                    ?: return@withContext fallback
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

        when (item.enabledState) {
            PluginEnabledState.READY -> states += context.getString(R.string.text_enabled)
            PluginEnabledState.DISABLED -> states += context.getString(R.string.text_disabled)
            is PluginEnabledState.ERROR -> {
                states += context.getString(R.string.text_disabled)
                states += context.getString(R.string.text_error)
            }
        }

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
    private fun restoreEssentialViews(items: InfoDialogItems, context: Context, info: PluginInfoBase) {
        if (info.collaborators.isNotEmpty()) {
            items["pluginItemInfoCollaboratorsFirst"].key = "${context.getString(R.string.plugin_item_info_collaborators)} [1/${info.collaborators.size}]"
            items["pluginItemInfoCollaboratorsFirst"].setValue(info.collaborators[0])
            items["pluginItemInfoCollaboratorsFirst"].isVisible = true
        }
        if (info.collaborators.size > 1) {
            items["pluginItemInfoCollaboratorsSecond"].key = "${context.getString(R.string.plugin_item_info_collaborators)} [2/${info.collaborators.size}]"
            items["pluginItemInfoCollaboratorsSecond"].setValue(info.collaborators[1])
            items["pluginItemInfoCollaboratorsSecond"].isVisible = true
        }
        if (info.collaborators.size > 2) {
            items["pluginItemInfoCollaboratorsThird"].key = "${context.getString(R.string.plugin_item_info_collaborators)} [3/${info.collaborators.size}]"
            items["pluginItemInfoCollaboratorsThird"].setValue(info.collaborators[2])
            items["pluginItemInfoCollaboratorsThird"].isVisible = true
        }
        when (info) {
            is PluginInfoUpdatable -> {
                /* No additional operations needed. */
            }
            is PluginInfoInstalled -> {
                info.updatableVersion?.let {
                    items["version"].key = context.getString(R.string.plugin_item_info_installed_version)
                    items["updatableVersion"].key = context.getString(
                        if (info.item.requiresDowngradeReinstall) {
                            R.string.plugin_item_info_matched_version
                        } else {
                            R.string.plugin_item_info_updatable_version
                        },
                    )
                    items["updatableVersion"].setValue(it)
                    items["updatableVersion"].isVisible = true
                }
                info.firstInstallTime?.setupListItemView(items["pluginItemInfoFirstInstallTime"])
                info.lastUpdateTime?.setupListItemView(items["pluginItemInfoLastUpdateTime"])
            }
            is PluginInfoInstallable -> {
                info.lastInstallTime?.setupListItemView(items["pluginItemInfoLastInstallTime"])
                info.lastUninstallTime?.setupListItemView(items["pluginItemInfoLastUninstallTime"])
            }
        }
    }

    private fun Long.setupListItemView(item: InfoDialogItems.Item) {
        this.takeIf { it > 0 }?.let {
            item.setValue(TimeUtils.formatTimestamp(it))
            item.isVisible = true
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

    private fun isPlainTextChangelogUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return "raw.githubusercontent.com" in lowerUrl ||
                "raw=true" in lowerUrl ||
                "plain=1" in lowerUrl ||
                ("github.com/" !in lowerUrl && lowerUrl.substringBefore('#').endsWith(".md"))
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
        val installedAbis: List<String>? get() = item.installedAbis
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
