@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.ViewUtils
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
            ViewUtils.excludeContentViewFromHorizontalNavigationBar(this)
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
                setUpStatusBarIconLightByThemeColor()
            }
        }

        // @Dubious by SuperMonster003 on Oct 28, 2024.
        //  ! Is it property to start a11y service automatically here?
        //  ! zh-CN: 无障碍服务自启动放在这里是否合适?
        AccessibilityTool(this).apply { if (!isServiceRunning()) startService(false) }
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
            setUpStatusBarIconLightByThemeColor()
        }
    }

    override fun onResume() {
        super.onResume()
        LocaleUtils.syncPrefWithCurrentStateIfNeeded(this)
    }

    open fun initThemeColors() {
        /* Empty body. */
    }

    fun setToolbarAsBack(titleRes: Int) = ViewUtils.setToolbarAsBack(this, titleRes)

    fun setToolbarAsBack(title: String?) = ViewUtils.setToolbarAsBack(this, title)

    protected fun setUpStatusBarIconLightByNightMode() {
        ViewUtils.setStatusBarIconLight(this, ViewUtils.isNightModeEnabled)
    }

    protected fun setUpStatusBarIconLightByThemeColor() {
        ThemeColorManager.setStatusBarIconLight(this)
    }

}