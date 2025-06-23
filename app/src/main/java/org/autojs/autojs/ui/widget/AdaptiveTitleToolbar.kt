package org.autojs.autojs.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.annotation.StyleRes
import androidx.appcompat.widget.Toolbar
import org.autojs.autojs.extension.ViewExtensions.titleView
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on May 30, 2025.
 */
open class AdaptiveTitleToolbar : Toolbar {

    private val defaultTitleAppearance = R.style.TextAppearanceDefaultMainTitle
    private val horizontalWithSubtitleTitleAppearance = R.style.TextAppearanceHorizontalWithSubtitleMainTitle

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        setContentInsetStartWithNavigation(context.resources.getDimensionPixelSize(R.dimen.toolbar_content_inset_start_with_navigation))
        setTitleTextAppearanceKeepColor(defaultTitleAppearance)
        setSubtitleTextAppearance(context, R.style.TextAppearanceMainSubtitle)
        adjustTitleAppearance()
    }

    private fun setTitleTextAppearanceKeepColor(@StyleRes resId: Int) {
        val currentColor = titleView?.currentTextColor
        setTitleTextAppearance(context, resId)
        currentColor?.let { setTitleTextColor(it) }
    }

    override fun setSubtitle(resId: Int) {
        super.setSubtitle(resId)
        adjustTitleAppearance()
    }

    override fun setSubtitle(subtitle: CharSequence?) {
        super.setSubtitle(subtitle)
        adjustTitleAppearance()
    }

    private fun adjustTitleAppearance() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasSubtitle = !subtitle.isNullOrEmpty()

        if (isLandscape && hasSubtitle) {
            setTitleTextAppearanceKeepColor(horizontalWithSubtitleTitleAppearance)
        } else {
            setTitleTextAppearanceKeepColor(defaultTitleAppearance)
        }
    }

}