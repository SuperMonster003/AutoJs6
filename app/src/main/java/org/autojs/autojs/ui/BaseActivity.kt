@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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
import org.autojs.autojs6.BuildConfig

/**
 * Created by Stardust on Jan 23, 2017.
 * Modified by SuperMonster003 as of Feb 18, 2022.
 */
abstract class BaseActivity : AppCompatActivity() {

    open val handleStatusBarThemeColorAutomatically = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        ViewUtils.appendSystemUiVisibility(this, SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setApplicationLocale(this)

        ThemeChangeNotifier.themeChanged.observe(this) {
            initThemeColors()
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
        if (handleStatusBarThemeColorAutomatically && !BuildConfig.isInrt) {
            ThemeColorManager.addActivityStatusBar(this)
        }
        setUpStatusBarIconLightByThemeColor()
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
        ViewUtils.setStatusBarIconLight(this, ThemeColorManager.isThemeColorLuminanceDark())
    }

}