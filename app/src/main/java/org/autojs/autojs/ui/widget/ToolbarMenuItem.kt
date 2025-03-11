package org.autojs.autojs.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.DrawableUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 29, 2017.
 */
class ToolbarMenuItem : LinearLayout {

    private val mColorDisabled: Int
        get() = when (ThemeColorManager.isThemeColorLuminanceLight()) {
            true -> ContextCompat.getColor(context, R.color.toolbar_menu_item_disabled_dark)
            else -> ContextCompat.getColor(context, R.color.toolbar_menu_item_disabled_light)
        }
    private val mColorEnabled: Int
        get() = when (ThemeColorManager.isThemeColorLuminanceLight()) {
            true -> ContextCompat.getColor(context, R.color.toolbar_menu_item_enabled_dark)
            else -> ContextCompat.getColor(context, R.color.toolbar_menu_item_enabled_light)
        }

    private var mImageView: ImageView
    private var mTextView: TextView
    private var mEnabledDrawable: Drawable? = null
    private var mDisabledDrawable: Drawable? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        inflate(context, R.layout.toolbar_menu_item, this)

        val a = context.obtainStyledAttributes(attrs, R.styleable.ToolbarMenuItem)

        val iconText = a.getString(R.styleable.ToolbarMenuItem_text)
        val iconResId = a.getResourceId(R.styleable.ToolbarMenuItem_icon, 0)

        a.recycle()

        mImageView = findViewById<ImageView?>(R.id.icon).apply {
            setImageResource(iconResId)
            imageTintList = ColorStateList.valueOf(mColorEnabled)
        }

        mTextView = findViewById<TextView?>(R.id.text).apply {
            text = iconText
            setTextColor(mColorEnabled)
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    override fun setEnabled(enabled: Boolean) {
        if (enabled != isEnabled) {
            super.setEnabled(enabled)
            ensureEnabledDrawable()
            ensureDisabledDrawable()
            mImageView.setImageDrawable(if (enabled) mEnabledDrawable else mDisabledDrawable)
            mImageView.imageTintList = ColorStateList.valueOf(if (enabled) mColorEnabled else mColorDisabled)
            mTextView.setTextColor(if (enabled) mColorEnabled else mColorDisabled)
        }
    }

    private fun ensureDisabledDrawable() {
        mDisabledDrawable = mDisabledDrawable ?: DrawableUtils.setDrawableColorFilterSrcIn(mEnabledDrawable, mColorDisabled)
    }

    private fun ensureEnabledDrawable() {
        mEnabledDrawable = mEnabledDrawable ?: mImageView.drawable
    }

}