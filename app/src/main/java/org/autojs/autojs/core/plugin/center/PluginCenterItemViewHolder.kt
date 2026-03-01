package org.autojs.autojs.core.plugin.center

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.colorFilterWithDesaturateOrNull
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding

class PluginCenterItemViewHolder(
    private val itemViewBinding: PluginCenterRecyclerViewItemBinding,
    private val listener: PluginCenterItemAdapter.Listener,
) : RecyclerView.ViewHolder(itemViewBinding.root) {

    private val context = itemViewBinding.root.context

    private val themeColor
        get() = ThemeColorManager.colorPrimary
    private val adjustedTextContrastColor
        get() = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), themeColor, 3.2)
    private val adjustedImageContrastColor
        get() = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), themeColor, 2.3)

    private val iconView: CircleImageView = itemViewBinding.icon

    private val titleView = itemViewBinding.title
    private val versionInfoView = itemViewBinding.versionInfo
    private val authorView = itemViewBinding.author
    private val descriptionView = itemViewBinding.description

    private val switchView = itemViewBinding.sw

    private val updatableBadgeView = itemViewBinding.updatableBadge
    private val updatableBadgeTextView = itemViewBinding.updatableBadgeText
    private val versionInfoForUpdateView = itemViewBinding.versionInfoForUpdate

    private val btnDeleteView = itemViewBinding.btnDelete
    private val btnUpdateView = itemViewBinding.btnUpdate
    private val btnSettingsView = itemViewBinding.btnSettings
    private val btnDetailsView = itemViewBinding.btnDetails

    private lateinit var currentItem: PluginCenterItem

    fun bind(item: PluginCenterItem) {
        currentItem = item

        item.icon?.let { iconView.setImageDrawable(it) } ?: AppCompatResources.getDrawable(
            iconView.context,
            R.drawable.ic_plugin_center_default
        )?.mutate()?.let { d ->
            DrawableCompat.setTint(d, adjustedImageContrastColor)
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            iconView.setImageDrawable(d)
        } ?: iconView.setImageResource(R.mipmap.ic_app_shortcut_plugin_center_adaptive_round)

        iconView.setOnClickListener {
            if (item.isInstalled) {
                IntentUtils.launchAppDetailsSettings(context, item.packageName)
            }
        }

        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = item.isEnabled

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
                ViewUtils.showToast(context, R.string.text_under_development)
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

        applyUiBySwitch(switchView.isChecked, item)

        switchView.setOnCheckedChangeListener { _, isChecked ->
            listener.onToggleEnable(currentItem, isChecked)
            applyUiBySwitch(isChecked, item)
        }
    }

    private fun LinearLayout.setButtonState(enabled: Boolean, onClickListener: View.OnClickListener? = null) {
        this.isEnabled = enabled
        this.setOnClickListener(onClickListener)
    }

    private fun applyUiBySwitch(isOn: Boolean, item: PluginCenterItem) {
        val colorPrimary = context.getColor(R.color.text_color_primary)
        val colorPrimaryA50 = context.getColor(R.color.text_color_primary_alpha_50)
        val colorPrimaryA30 = context.getColor(R.color.text_color_primary_alpha_30)
        val colorPrimaryA20 = context.getColor(R.color.text_color_primary_alpha_20)

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
            updatableBadgeTextView.setTextColor(adjustedTextContrastColor)
        } else {
            versionInfoForUpdateView.setTextColor(colorPrimary)
            updatableBadgeTextView.setTextColor(colorPrimary)
        }

        iconView.colorFilterWithDesaturateOrNull(isOn, 0.5F)
    }

    private fun LinearLayout.setActionColors(iconColor: Int, textColor: Int) {
        val imageView = getChildAtOrNull(0) as? ImageView
        val textView = getChildAtOrNull(1) as? TextView
        imageView?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(iconColor)) }
        textView?.setTextColor(textColor)
    }

    private fun LinearLayout.getChildAtOrNull(index: Int) = if (index in 0 until childCount) getChildAt(index) else null

}

