@file:Suppress("DEPRECATION")

package org.autojs.autojs.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.zeugmasolutions.localehelper.LocaleHelper
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegate
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ViewUtils

/**
 * Created by Stardust on 2017/1/23.
 * Modified by SuperMonster003 as of Feb 18, 2022.
 */
abstract class BaseActivity : AppCompatActivity() {

    private val localeDelegate: LocaleHelperActivityDelegate = LocaleHelperActivityDelegateImpl()

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
        localeDelegate.onCreate(this)
    }

    override fun getDelegate(): AppCompatDelegate {
        return localeDelegate.getAppCompatDelegate(super.getDelegate())
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeDelegate.attachBaseContext(newBase))
    }

    override fun onStart() {
        super.onStart()
        if (window.decorView.systemUiVisibility and SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN == 0) {
            ThemeColorManager.addActivityStatusBar(this)
        }
    }

    override fun onResume() {
        super.onResume()
        localeDelegate.onResumed(this)
    }

    open fun restartAfterPendingIntent(context: Context?) {
        val intent = context?.packageManager?.getLaunchIntentForPackage(context.packageName)
        val componentName: ComponentName? = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)

        context?.startActivity(mainIntent)
        Process.killProcess(Process.myPid())
    }

    override fun onPause() {
        super.onPause()
        localeDelegate.onPaused()
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        val context = super.createConfigurationContext(overrideConfiguration)
        return LocaleHelper.onAttach(context)
    }

    override fun getApplicationContext() = localeDelegate.getApplicationContext(super.getApplicationContext())

    fun setToolbarAsBack(titleRes: Int) = ViewUtils.setToolbarAsBack(this, titleRes)

    fun setToolbarAsBack(title: String?) = ViewUtils.setToolbarAsBack(this, title)

}