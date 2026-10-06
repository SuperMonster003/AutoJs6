package org.autojs.autojs.util

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.BaseTransientBottomBar

object SnackbarUtils {

    /**
     * Lets the bar's background reach under the navigation bar instead of floating above it.
     * A bar normally turns the navigation bar inset into a bottom margin, so the transparent
     * navigation bar keeps showing the content below it. Here the inset becomes padding, so the
     * area takes the bar's background only while the bar is shown. The IME inset is left out,
     * which suits bars shown while no keyboard is open.
     *
     * zh-CN: 使提示条背景延伸到导航栏下方, 而非悬浮在其上方. 提示条默认将导航栏高度作为底部外边距,
     * 透明导航栏因此仍露出下方内容. 此处改为内边距, 使该区域仅在提示条显示期间呈现提示条背景.
     * 未处理输入法高度, 适用于无键盘时显示的提示条.
     */
    @JvmStatic
    fun <B : BaseTransientBottomBar<B>> B.coverNavigationBar(): B = apply {
        val bar = view
        val left = bar.paddingLeft
        val top = bar.paddingTop
        val right = bar.paddingRight
        val bottom = bar.paddingBottom
        setGestureInsetBottomIgnored(true)
        // Replaces the listener that would add the inset as a margin.
        // zh-CN: 替换将导航栏高度加为外边距的监听器.
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(left + bars.left, top, right + bars.right, bottom + bars.bottom)
            insets
        }
    }

}
