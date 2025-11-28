package org.autojs.autojs.core.plugin.center

import android.content.res.ColorStateList
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginCenterRecyclerViewItemBinding
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

class PluginCenterItemViewHolder(itemViewBinding: PluginCenterRecyclerViewItemBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

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

    fun bind(item: PluginCenterItem) {
        item.icon?.let { iconView.setImageDrawable(it) } ?: AppCompatResources.getDrawable(
            iconView.context,
            R.drawable.ic_plugin_center_default
        )?.mutate()?.let { d ->
            DrawableCompat.setTint(d, adjustedImageContrastColor)
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN)
            iconView.setImageDrawable(d)
        } ?: iconView.setImageResource(R.mipmap.ic_app_shortcut_plugin_center_adaptive_round)

        switchView.isChecked = item.isEnabled

        titleView.text = item.title
        versionInfoView.text = formatVersionInfo(item.versionName, item.versionCode, item.versionDate)
        authorView.text = item.author
        descriptionView.text = item.description

        btnDeleteView.setButtonState(true) {
            ViewUtils.showToast(context, R.string.text_under_development)
        }
        if (item.isUpdatable) {
            updatableBadgeView.isVisible = true
            versionInfoForUpdateView.isVisible = true
            versionInfoForUpdateView.text = formatVersionInfo(item.versionName, item.versionCode?.let { it + 16 }, item.versionDate?.let {
                DateTime.parse(it).plusDays(3).toString("yyyy-MM-dd")
            })
            btnUpdateView.setButtonState(true) {
                ViewUtils.showToast(context, R.string.text_under_development)
            }
        } else {
            updatableBadgeView.isVisible = false
            versionInfoForUpdateView.isVisible = false
            btnUpdateView.setButtonState(false) {
                ViewUtils.showToast(context, R.string.text_unavailable)
            }
        }
        if (item.settings != null) {
            btnSettingsView.setButtonState(true) {
                ViewUtils.showToast(context, R.string.text_under_development)
            }
        } else {
            btnSettingsView.setButtonState(false) {
                ViewUtils.showToast(context, R.string.text_unavailable)
            }
        }
        btnDetailsView.setButtonState(true) {
            ViewUtils.showToast(context, R.string.text_under_development)
        }

        applyUiBySwitch(switchView.isChecked, item)

        switchView.setOnCheckedChangeListener { _, isChecked ->
            applyUiBySwitch(isChecked, item)
        }
    }

    private fun formatVersionInfo(versionName: String, versionCode: Long?, versionDate: String?): String {
        val code = versionCode?.takeIf { it > 0 }
        val date = versionDate?.runCatching {
            DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.parse(this))
        }?.getOrNull()
        return buildString {
            append(versionName)
            code?.let { append(" ($it)") }
            date?.let { append(" | $it") }
        }
    }

    private fun LinearLayout.setButtonState(enabled: Boolean, onClickListener: View.OnClickListener) {
        isEnabled = enabled
        this.setOnClickListener(onClickListener)
    }

    private fun applyUiBySwitch(isOn: Boolean, item: PluginCenterItem) {
        val colorPrimary = context.getColor(R.color.text_color_primary)
        val colorPrimaryA50 = context.getColor(R.color.text_color_primary_alpha_50)
        val colorPrimaryA30 = context.getColor(R.color.text_color_primary_alpha_30)
        val colorPrimaryA20 = context.getColor(R.color.text_color_primary_alpha_20)

        btnDeleteView.setActionColors(iconColor = colorPrimaryA50, textColor = colorPrimary)

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

        if (isOn) {
            iconView.colorFilter = null
        } else {
            // Construct the desaturation matrix.
            // zh-CN: 构造灰度矩阵.
            val desaturate = ColorMatrix().apply { setSaturation(0f) }
            // Construct the alpha scaling matrix.
            // zh-CN: 构造透明度缩放矩阵.
            val alphaMatrix = ColorMatrix().apply { setScale(1f, 1f, 1f, 0.5f) }
            // Concatenate: first desaturate, then apply alpha.
            // zh-CN: 叠加: 先灰度, 再透明度.
            desaturate.postConcat(alphaMatrix)
            iconView.colorFilter = ColorMatrixColorFilter(desaturate)
        }
    }

    private fun LinearLayout.setActionColors(iconColor: Int, textColor: Int) {
        val imageView = getChildAtOrNull(0) as? ImageView
        val textView = getChildAtOrNull(1) as? TextView
        imageView?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(iconColor)) }
        textView?.setTextColor(textColor)
    }

    private fun LinearLayout.getChildAtOrNull(index: Int) = if (index in 0 until childCount) getChildAt(index) else null

}

