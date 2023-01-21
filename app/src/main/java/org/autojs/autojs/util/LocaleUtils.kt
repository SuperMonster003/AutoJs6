package org.autojs.autojs.util

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import com.zeugmasolutions.localehelper.LocaleHelper
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegate
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import java.util.Locale

object LocaleUtils {

    private val localeDelegate: LocaleHelperActivityDelegate = LocaleHelperActivityDelegateImpl()
    private val localeHelper = LocaleHelper

    private val globalAppContext = GlobalAppContext.get()

    @JvmStatic
    fun setLocale(activity: Activity, locale: Locale?) {
        locale?.let { localeDelegate.setLocale(activity, it) }
        ?: throw IllegalArgumentException(globalAppContext.getString(R.string.error_cannot_be_null, "Locale"))
    }

    @JvmStatic
    fun setFollowSystem(activity: Activity) {
        val systemLocale = localeHelper.systemLocale
        val currentLocale = localeHelper.getLocale(activity)
        localeDelegate.apply {
            if (currentLocale !== systemLocale) {
                setLocale(activity, systemLocale)
            }
            clearLocaleSelection(activity)
        }
    }

    @JvmStatic
    fun getSystemLocale() = localeHelper.getLocale(globalAppContext)

    @JvmStatic
    fun getResources(desiredLocale: Locale?): Resources {
        return globalAppContext.let { context ->
            context.resources.configuration.let { Configuration(it) }.run {
                setLocale(desiredLocale ?: localeHelper.systemLocale)
                context.createConfigurationContext(this).resources
            }
        }
    }

}