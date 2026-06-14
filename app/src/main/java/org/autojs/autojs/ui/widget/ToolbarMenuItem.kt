package org.autojs.autojs.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.DrawableUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ToolbarMenuItemBinding

/**
 * Created by Stardust on Jan 29, 2017.
 */
class ToolbarMenuItem : LinearLayout {

    private var mImageView: ImageView
    private var mTextView: TextView

    private var mEnabledDrawable: Drawable? = null
    private var mDisabledDrawable: Drawable? = null

    private val colorEnabled: Int
        get() = when (ThemeColorManager.isLuminanceLight()) {
            true -> ContextCompat.getColor(context, R.color.toolbar_menu_item_enabled_dark)
            else -> ContextCompat.getColor(context, R.color.toolbar_menu_item_enabled_light)
        }

    private val colorDisabled: Int
        get() = when (ThemeColorManager.isLuminanceLight()) {
            true -> ContextCompat.getColor(context, R.color.toolbar_menu_item_disabled_dark)
            else -> ContextCompat.getColor(context, R.color.toolbar_menu_item_disabled_light)
        }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        val binding = ToolbarMenuItemBinding.inflate(LayoutInflater.from(context), this, true)

        context.obtainStyledAttributes(attrs, R.styleable.ToolbarMenuItem).also { ta ->
            mImageView = binding.icon.apply {
                setImageResource(ta.getResourceId(R.styleable.ToolbarMenuItem_icon, 0))
                imageTintList = ColorStateList.valueOf(colorEnabled)
            }
            mTextView = binding.text.apply {
                text = ta.getString(R.styleable.ToolbarMenuItem_text)
                setTextColor(colorEnabled)
            }
        }.recycle()
        mEnabledDrawable = mImageView.drawable
        applyStyleByCurrentState()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    fun setIconResource(@DrawableRes iconRes: Int) {
        mImageView.setImageResource(iconRes)
        mEnabledDrawable = mImageView.drawable
        mDisabledDrawable = null
        applyStyleByCurrentState()
    }

    fun setTextResource(@StringRes textRes: Int) {
        mTextView.setText(textRes)
    }

    override fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) {
            applyStyleByCurrentState()
            return
        }
        super.setEnabled(enabled)
        applyStyleByCurrentState()
    }

    private fun applyStyleByCurrentState() {
        if (isEnabled) {
            mImageView.setImageDrawable(mEnabledDrawable)
            mImageView.imageTintList = ColorStateList.valueOf(colorEnabled)
            mTextView.setTextColor(colorEnabled)
            return
        }
        val disabled = mDisabledDrawable ?: DrawableUtils.setDrawableColorFilterSrcIn(mEnabledDrawable, colorDisabled).also {
            mDisabledDrawable = it
        }
        mImageView.setImageDrawable(disabled)
        mImageView.imageTintList = ColorStateList.valueOf(colorDisabled)
        mTextView.setTextColor(colorDisabled)
    }
}
