@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.autojs.autojs.core.accessibility.AccessibilityServiceAutoStart
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludeContentViewFromHorizontalNavigationBar
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 23, 2017.
 * Modified by SuperMonster003 as of Feb 18, 2022.
 */
abstract class BaseActivity : AppCompatActivity() {

    open val handleContentViewFromHorizontalNavigationBarAutomatically = true
    open val handleStatusBarThemeColorAutomatically = true
    open val handleNavigationBarContrastEnforcedAutomatically = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewUtils.addWindowFlags(this, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        @Suppress("DEPRECATION")
        ViewUtils.appendSystemUiVisibility(this, SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        if (handleContentViewFromHorizontalNavigationBarAutomatically) {
            this.excludeContentViewFromHorizontalNavigationBar()
        }

        if (handleNavigationBarContrastEnforcedAutomatically) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                ViewUtils.setNavigationBarBackgroundColor(this, getColor(R.color.black_alpha_44))
            }
        }

        setApplicationLocale(this)

        ThemeChangeNotifier.themeChanged.observe(this) {
            initThemeColors()
            if (handleStatusBarThemeColorAutomatically) {
                ThemeColorManager.setStatusBarBackgroundColor(this)
                ThemeColorManager.setStatusBarIconLightByDarkColor(this)
            }
            if (handleNavigationBarContrastEnforcedAutomatically) {
                setUpNavigationBarIconLightByNightMode()
            }
        }

        // @Dubious by SuperMonster003 on Oct 28, 2024.
        //  ! Is it property to start a11y service automatically here?
        //  ! zh-CN: 无障碍服务自启动放在这里是否合适?
        // Root and secure-settings recovery can perform blocking I/O, so keep the policy
        // process-wide while moving its execution away from Activity creation.
        // zh-CN: root 与安全设置恢复可能执行阻塞 I/O, 因此保留进程级策略,
        // 同时将实际操作移出 Activity 创建过程.
        AccessibilityServiceAutoStart.request(applicationContext)
    }

    private fun setApplicationLocale(context: Context) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                LocaleUtils.setApplicationLocale(AppCompatDelegate.getApplicationLocales())
            }
            else -> when (Language.getPrefLanguage().isAuto()) {
                true -> LocaleUtils.setFollowSystem(context)
                else -> LocaleUtils.setApplicationLocale(Language.getPrefLanguageOrNull()?.locale)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        initThemeColors()
        if (handleStatusBarThemeColorAutomatically) {
            ThemeColorManager.addActivityStatusBar(this)
            ThemeColorManager.setStatusBarIconLightByDarkColor(this)
        }
        if (handleNavigationBarContrastEnforcedAutomatically) {
            setUpNavigationBarIconLightByNightMode()
        }
    }

    override fun onResume() {
        super.onResume()
        LocaleUtils.syncPrefWithCurrentStateIfNeeded(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        clearStaleHoverState()
    }

    /**
     * Some touch screens report a lifting finger as a hovering pointer, and the matching HOVER_EXIT
     * may never reach this window once the tap has opened another one, so the tapped list row keeps
     * its hover highlight. A synthetic exit, like the one ViewGroup sends to a removed hovered child,
     * clears every hovered view of this window; a real hovering pointer re-enters on its next move.
     * zh-CN: 部分触摸屏会把抬起中的手指上报为悬停指针, 而点击打开其他窗口后, 与之对应的 HOVER_EXIT
     * 可能不会再送达本窗口, 导致被点击的列表条目一直保留悬停高亮. 此处模拟 ViewGroup 对被移除的悬停子视图
     * 发送的退出事件, 清除本窗口内所有视图的悬停状态; 真实的悬停指针在下一次移动时会重新进入.
     */
    private fun clearStaleHoverState() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_EXIT, 0f, 0f, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            window.superDispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    open fun initThemeColors() {
        /* Empty body. */
    }

    fun setToolbarAsBack(titleRes: Int) = ViewUtils.setToolbarAsBack(this, titleRes)

    fun setToolbarAsBack(title: String?) = ViewUtils.setToolbarAsBack(this, title)

    protected fun setUpStatusBarIconLightByNightMode() {
        ViewUtils.setStatusBarIconLight(this, ViewUtils.isNightModeEnabled)
    }

    protected fun setUpNavigationBarIconLightByNightMode() {
        ViewUtils.setNavigationBarIconLight(this, ViewUtils.isNightModeEnabled)
    }

    protected fun setUpStatusBarIconLightByThemeColor() {
        ThemeColorManager.setStatusBarIconLight(this)
    }

}
