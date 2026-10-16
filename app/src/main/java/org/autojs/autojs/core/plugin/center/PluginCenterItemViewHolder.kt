package org.autojs.autojs.core.plugin.center

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.hdodenhof.circleimageview.CircleImageView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.colorFilterWithDesaturateOrNull
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

class PluginCenterItemViewHolder(
    private val itemViewBinding: PluginCenterRecyclerViewItemBinding,
    private val listener: PluginCenterItemAdapter.Listener,
) : RecyclerView.ViewHolder(itemViewBinding.root) {

    private val context = itemViewBinding.root.context

    private val themeColor = ThemeColorManager.colorPrimary
    private val adjustedTextContrastColor =
        ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), themeColor, 3.2)
    private val adjustedImageContrastColor =
        ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), themeColor, 2.3)
    private val colorPrimary = context.getColor(R.color.text_color_primary)
    private val colorPrimaryA50 = context.getColor(R.color.text_color_primary_alpha_50)
    private val colorPrimaryA30 = context.getColor(R.color.text_color_primary_alpha_30)
    private val colorPrimaryA20 = context.getColor(R.color.text_color_primary_alpha_20)

    private val iconView: CircleImageView = itemViewBinding.icon

    private val titleView = itemViewBinding.title
    private val versionInfoView = itemViewBinding.versionInfo
    private val authorView = itemViewBinding.author
    private val descriptionView = itemViewBinding.description

    private val switchView = itemViewBinding.sw
    private val downloadButton = itemViewBinding.downloadButton

    private val updatableBadgeView = itemViewBinding.updatableBadge
    private val attentionBadgeView = itemViewBinding.attentionBadge
    private val versionInfoForUpdateView = itemViewBinding.versionInfoForUpdate

    private val btnDeleteView = itemViewBinding.btnDelete
    private val btnUpdateView = itemViewBinding.btnUpdate
    private val btnSettingsView = itemViewBinding.btnSettings
    private val btnDetailsView = itemViewBinding.btnDetails

    private var lastBoundPackageName: String? = null
    private var hasRemoteIconRequest = false
    private lateinit var currentItem: PluginCenterItem

    init {
        val actionIconSize = context.resources.getDimensionPixelSize(R.dimen.plugin_center_action_icon_size)
        listOf(btnDeleteView, btnUpdateView, btnSettingsView, btnDetailsView).forEach { actionView ->
            actionView.resizeStartCompoundDrawable(actionIconSize)
        }
    }

    private fun bindAttentionBadge(attention: PluginCenterAttention?) {
        attentionBadgeView.isVisible = attention != null
        when (attention) {
            null -> Unit
            PluginCenterAttention.NEW_INSTALL -> {
                attentionBadgeView.setText(R.string.text_plugin_attention_new_install)
                attentionBadgeView.setTextColor(adjustedTextContrastColor)
            }
            PluginCenterAttention.ENABLE_FAILED -> {
                attentionBadgeView.setText(R.string.text_plugin_attention_enable_failed)
                attentionBadgeView.setTextColor(context.getColor(R.color.dialog_button_caution))
            }
        }
    }

    fun bind(item: PluginCenterItem) {
        currentItem = item
        iconView.circleBackgroundColor = PluginIconBackgroundPolicy.resolve(
            item.packageName, ViewUtils.isNightModeEnabled, item.iconBackgroundColor,
            item.nightIconBackgroundColor, item.icon?.let(PluginInstalledIconResolver::backgroundForCurrentMode),
        )
        itemView.findViewById<android.widget.TextView>(R.id.native_alignment_badge).isVisible =
            PluginNativeAlignmentPolicy.needsWarning(item.nativePageAlignment, org.autojs.autojs.core.plugin.nativealign.NativePageSize.current())
        val previousPackageName = lastBoundPackageName
        lastBoundPackageName = item.packageName

        val cachedPackageIcon = getCachedRemotePackageIcon(item.packageName)
            ?: getCachedRemotePackageIcon(item.packageName, isNightModeEnabled = !ViewUtils.isNightModeEnabled)
        item.icon?.let {
            clearRemoteIconRequest()
            iconView.setImageDrawable(PluginInstalledIconResolver.forCurrentMode(it))
        } ?: item.remoteIconUrl?.takeIf { it.isNotBlank() }?.let { url ->
            val preferredUrl = PluginIconUrlResolver.resolveForCurrentMode(context, url, item.remoteNightIconUrl) ?: url
            val fallbackUrl = listOfNotNull(url, item.remoteNightIconUrl)
                .firstOrNull { it.isNotBlank() && it != preferredUrl }
            val fallback = getCachedRemoteIcon(preferredUrl)
                ?: cachedPackageIcon
                ?: fallbackUrl?.let(::getCachedRemoteIcon)
                ?: iconView.drawable
                    ?.takeIf { previousPackageName == item.packageName }
                    ?.let(::copyDrawableForIconCache)
                ?: buildFallbackIconDrawable()
            val packageCacheKey = packageIconCacheKey(item.packageName)
            var request = buildRemoteIconRequest(
                url = preferredUrl,
                fallback = fallback,
                cacheKeys = listOf(remoteIconCacheKey(preferredUrl), packageCacheKey),
            )
            if (fallbackUrl != null) {
                request = request.error(
                    buildRemoteIconRequest(
                        url = fallbackUrl,
                        fallback = fallback,
                        cacheKeys = listOf(remoteIconCacheKey(fallbackUrl), packageCacheKey),
                    )
                )
            }
            request.into(iconView)
            hasRemoteIconRequest = true
        } ?: run {
            clearRemoteIconRequest()
            iconView.setImageDrawable(
                cachedPackageIcon
                    ?: iconView.drawable?.takeIf { previousPackageName == item.packageName }
                    ?: buildFallbackIconDrawable()
            )
        }

        iconView.setOnClickListener {
            if (currentItem.isInstalled) {
                IntentUtils.launchAppDetailsSettings(context, currentItem.packageName)
            } else {
                listener.onDetails(currentItem)
            }
        }
        iconView.setOnLongClickListener {
            if (currentItem.isInstalled) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(currentItem.packageName)
                if (launchIntent == null) {
                    ViewUtils.showToast(context, R.string.summary_plugin_no_launch_page, true)
                } else {
                    launchIntent.startSafely(context, true) { error ->
                        ViewUtils.showToast(context, error.message ?: context.getString(R.string.text_unavailable), true)
                    }
                }
            } else {
                listener.onInstall(currentItem)
            }
            true
        }

        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = item.isEnabled
        val showInstallAction = !item.isInstalled
        switchView.isVisible = !showInstallAction
        downloadButton.isVisible = showInstallAction
        if (showInstallAction) {
            downloadButton.setOnClickListener { listener.onInstall(currentItem) }
            ImageViewCompat.setImageTintList(downloadButton, ColorStateList.valueOf(adjustedImageContrastColor))
        } else {
            downloadButton.setOnClickListener(null)
            ImageViewCompat.setImageTintList(downloadButton, ColorStateList.valueOf(context.getColor(R.color.text_color_primary_alpha_50)))
        }

        bindAttentionBadge(item.attention)
        titleView.text = item.title.takeUnless { it.isBlank() }
            ?: "[ ${context.getString(R.string.text_unknown_title_for_plugin)} ]"
        versionInfoView.text = item.versionSummary.takeUnless { it.isBlank() }
            ?: "[ ${context.getString(R.string.text_unknown_version_for_plugin)} ]"
        authorView.text = item.author.takeUnless { it.isNullOrBlank() }
            ?: "[ ${context.getString(R.string.text_unknown_author_for_plugin)} ]"
        descriptionView.text = item.description.takeUnless { it.isNullOrBlank() }
            ?: "[ ${context.getString(R.string.text_unknown_description_for_plugin)} ]"

        if (item.isInstalled) {
            btnDeleteView.setButtonState(true) {
                listener.onUninstall(currentItem)
            }
        } else {
            btnDeleteView.setButtonState(false)
        }

        val showUpdate = item.isInstalled && item.isUpdatable
        updatableBadgeView.isVisible = showUpdate
        versionInfoForUpdateView.isVisible = showUpdate
        updatableBadgeView.setText(
            if (item.requiresDowngradeReinstall) R.string.text_reinstall_required else R.string.text_updatable,
        )
        btnUpdateView.setText(
            if (item.requiresDowngradeReinstall) R.string.text_reinstall else R.string.text_update,
        )
        if (showUpdate) {
            versionInfoForUpdateView.text = item.updatableVersionSummary
            btnUpdateView.setButtonState(true) {
                listener.onUpdate(currentItem)
            }
        } else {
            btnUpdateView.setButtonState(false)
        }

        if (item.settings != null) {
            btnSettingsView.setButtonState(true) {
                listener.onSettings(currentItem)
            }
        } else {
            btnSettingsView.setButtonState(false)
        }

        btnDetailsView.setButtonState(true) {
            listener.onDetails(currentItem)
        }

        listOf(
            itemViewBinding.title,
            itemViewBinding.itemMiddleArea,
            itemViewBinding.description,
        ).forEach {
            it.setOnClickListener { listener.onDetails(currentItem) }
        }

        val visualEnabled = if (item.isInstalled) switchView.isChecked else true
        applyUiBySwitch(visualEnabled, item)

        if (item.isInstalled) {
            switchView.setOnCheckedChangeListener { _, isChecked ->
                listener.onToggleEnable(currentItem, isChecked)
                applyUiBySwitch(isChecked, item)
            }
        } else {
            switchView.setOnCheckedChangeListener(null)
        }
    }

    fun recycle() {
        clearRemoteIconRequest()
        lastBoundPackageName = null
    }

    private fun clearRemoteIconRequest() {
        if (!hasRemoteIconRequest) return
        Glide.with(iconView).clear(iconView)
        hasRemoteIconRequest = false
    }

    private fun TextView.setButtonState(enabled: Boolean, onClickListener: View.OnClickListener? = null) {
        this.isEnabled = enabled
        this.setOnClickListener(onClickListener)
    }

    private fun applyUiBySwitch(isOn: Boolean, item: PluginCenterItem) {
        if (btnDeleteView.isEnabled) {
            btnDeleteView.setActionColors(iconColor = colorPrimaryA50, textColor = colorPrimary)
        } else {
            btnDeleteView.setActionColors(iconColor = colorPrimaryA20, textColor = colorPrimaryA30)
        }

        if (btnUpdateView.isEnabled) {
            if (isOn) {
                btnUpdateView.setActionColors(iconColor = adjustedImageContrastColor, textColor = adjustedTextContrastColor)
            } else {
                btnUpdateView.setActionColors(iconColor = colorPrimaryA50, textColor = colorPrimary)
            }
        } else {
            btnUpdateView.setActionColors(iconColor = colorPrimaryA20, textColor = colorPrimaryA30)
        }

        if (item.settings == null) {
            btnSettingsView.setActionColors(iconColor = colorPrimaryA20, textColor = colorPrimaryA30)
        } else {
            btnSettingsView.setActionColors(iconColor = colorPrimaryA50, textColor = colorPrimary)
        }

        btnDetailsView.setActionColors(iconColor = colorPrimaryA50, textColor = colorPrimary)

        if (isOn) {
            versionInfoForUpdateView.setTextColor(adjustedTextContrastColor)
            updatableBadgeView.setTextColor(adjustedTextContrastColor)
        } else {
            versionInfoForUpdateView.setTextColor(colorPrimary)
            updatableBadgeView.setTextColor(colorPrimary)
        }

        iconView.colorFilterWithDesaturateOrNull(isOn, 0.5F)
    }

    private fun TextView.setActionColors(iconColor: Int, textColor: Int) {
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(iconColor))
        setTextColor(textColor)
    }

    private fun TextView.resizeStartCompoundDrawable(size: Int) {
        val drawables = compoundDrawablesRelative
        // Bounds belong to the Drawable instance, so mutating its shared ConstantState is
        // unnecessary. BitmapDrawable.mutate() allocates a Paint-backed state for every
        // action icon and is especially costly while the cold viewport is being created.
        // zh-CN: bounds 属于 Drawable 实例, 无需复制其共享 ConstantState.
        // BitmapDrawable.mutate() 会为每个操作图标分配含 Paint 的状态,
        // 在冷启动首屏集中创建时开销尤为明显.
        val start = drawables[0]?.apply { setBounds(0, 0, size, size) }
        setCompoundDrawablesRelative(start, drawables[1], drawables[2], drawables[3])
    }

    private fun buildFallbackIconDrawable() =
        AppCompatResources.getDrawable(iconView.context, R.drawable.ic_plugin_center_default)
            ?.mutate()
            ?.also { d ->
                DrawableCompat.setTint(d, adjustedImageContrastColor)
                DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            }
            ?: AppCompatResources.getDrawable(iconView.context, R.mipmap.ic_app_shortcut_plugin_center_adaptive_round)

    private fun buildRemoteIconRequest(
        url: String,
        fallback: Drawable?,
        cacheKeys: List<String>,
    ): RequestBuilder<Drawable> =
        Glide.with(iconView)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .skipMemoryCache(false)
            .placeholder(fallback)
            .error(fallback)
            .listener(
                object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean,
                    ): Boolean = false

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        cacheRemoteItemIcon(cacheKeys, resource)
                        return false
                    }
                },
            )

    private fun getCachedRemoteIcon(url: String): Drawable? =
        getCachedRemoteItemIcon(remoteIconCacheKey(url))

    private fun getCachedRemotePackageIcon(
        packageName: String,
        isNightModeEnabled: Boolean = ViewUtils.isNightModeEnabled,
    ): Drawable? =
        getCachedRemoteItemIcon(packageIconCacheKey(packageName, isNightModeEnabled))

    private fun getCachedRemoteItemIcon(cacheKey: String): Drawable? =
        remoteItemIconCache.get(cacheKey)?.takeUnless { bitmap ->
            bitmap.isRecycled.also { isRecycled ->
                if (isRecycled) {
                    remoteItemIconCache.remove(cacheKey)
                }
            }
        }?.let { bitmap ->
            BitmapDrawable(context.resources, bitmap).mutate()
        }

    private fun cacheRemoteItemIcon(cacheKeys: List<String>, drawable: Drawable) {
        val bitmap = copyBitmapForIconCache(drawable) ?: return
        cacheKeys.distinct().forEach { cacheKey ->
            remoteItemIconCache.put(cacheKey, bitmap)
        }
    }

    private fun copyDrawableForIconCache(drawable: Drawable): Drawable? =
        copyBitmapForIconCache(drawable)?.let { bitmap ->
            BitmapDrawable(context.resources, bitmap).mutate()
        }

    private fun copyBitmapForIconCache(drawable: Drawable): Bitmap? {
        val size = iconCacheSizePx()
        val source = runCatching { drawable.toBitmap(size, size) }.getOrNull()
            ?.takeUnless { it.isRecycled }
            ?: return null
        return runCatching {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
            ?.takeUnless { it.isRecycled }
    }

    private fun iconCacheSizePx(): Int =
        iconView.width.takeIf { it > 0 }
            ?: context.resources.getDimensionPixelSize(R.dimen.plugin_center_item_side_length)

    private fun remoteIconCacheKey(url: String) = "url:$url"

    private fun packageIconCacheKey(
        packageName: String,
        isNightModeEnabled: Boolean = ViewUtils.isNightModeEnabled,
    ) = "package:${if (isNightModeEnabled) "night" else "day"}:$packageName"

    companion object {
        private const val REMOTE_ICON_CACHE_SIZE_KB = 32 * 1024

        private val remoteItemIconCache = object : android.util.LruCache<String, Bitmap>(REMOTE_ICON_CACHE_SIZE_KB) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                if (value.isRecycled) 1 else (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

}

