@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import androidx.appcompat.app.AppCompatActivity
import org.autojs.autojs.pref.Language
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.ViewUtils

/**
 * Created by Stardust on 2017/1/23.
 * Modified by SuperMonster003 as of Feb 18, 2022.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ViewUtils.isAutoNightModeEnabled) {
            val isNightModeYes = ViewUtils.isNightModeYes(this)
            if (ViewUtils.isNightModeEnabled != isNightModeYes) {
                ViewUtils.isNightModeEnabled = isNightModeYes
                val mode = when (isNightModeYes) {
                    true -> ViewUtils.MODE.NIGHT
                    else -> ViewUtils.MODE.DAY
                }
                ViewUtils.setDefaultNightMode(mode)
            }
        }

        ViewUtils.makeBarsAdaptToNightMode(this)

        setLocaleForAutoLanguageIfNeeded(this)
    }

    private fun setLocaleForAutoLanguageIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (Language.getPrefLanguage().isAuto()) {
                LocaleUtils.setFollowSystem(context)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (window.decorView.systemUiVisibility and SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN == 0) {
            ThemeColorManager.addActivityStatusBar(this)
        }
    }

    override fun onResume() {
        super.onResume()
        LocaleUtils.syncPrefWithCurrentStateIfNeeded(this)
    }

    fun setToolbarAsBack(titleRes: Int) = ViewUtils.setToolbarAsBack(this, titleRes)

    fun setToolbarAsBack(title: String?) = ViewUtils.setToolbarAsBack(this, title)

}