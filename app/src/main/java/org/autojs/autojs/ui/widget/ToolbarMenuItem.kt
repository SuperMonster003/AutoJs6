package org.autojs.autojs.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.autojs.autojs.util.DrawableUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/1/29.
 */
class ToolbarMenuItem : LinearLayout {

    private val mColorDisabled: Int = ContextCompat.getColor(context, R.color.toolbar_disabled)
    private val mColorEnabled: Int = ContextCompat.getColor(context, R.color.toolbar_text)
    private var mImageView: ImageView
    private var mTextView: TextView
    private var mEnabledDrawable: Drawable? = null
    private var mDisabledDrawable: Drawable? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        inflate(context, R.layout.toolbar_menu_item, this)

        val a = context.obtainStyledAttributes(attrs, R.styleable.ToolbarMenuItem)

        val iconText = a.getString(R.styleable.ToolbarMenuItem_text)
        val iconResId = a.getResourceId(R.styleable.ToolbarMenuItem_icon, 0)
        val iconColor = a.getColor(R.styleable.ToolbarMenuItem_icon_color, Color.TRANSPARENT)

        a.recycle()

        mImageView = findViewById<ImageView?>(R.id.icon).apply {
            setImageResource(iconResId)
            if (iconColor != Color.TRANSPARENT) {
                setImageDrawable(DrawableUtils.filterDrawableColor(drawable, iconColor))
            }
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
            mTextView.setTextColor(if (enabled) mColorEnabled else mColorDisabled)
        }
    }

    private fun ensureDisabledDrawable() {
        mDisabledDrawable = mDisabledDrawable ?: DrawableUtils.filterDrawableColor(mEnabledDrawable, mColorDisabled)
    }

    private fun ensureEnabledDrawable() {
        mEnabledDrawable = mEnabledDrawable ?: mImageView.drawable
    }

}