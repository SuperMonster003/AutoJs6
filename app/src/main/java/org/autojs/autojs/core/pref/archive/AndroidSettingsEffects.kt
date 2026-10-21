package org.autojs.autojs.core.pref.archive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.archive.SettingsEffects.Action
import org.autojs.autojs.core.pref.archive.SettingsEffects.EffectException
import org.autojs.autojs.core.pref.archive.SettingsEffects.Entry
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.util.LauncherIconComponents
import org.autojs.autojs.util.LocaleUtils
import org.autojs.autojs.util.ViewUtils

/**
 * The Android side of the effect table (docs/settings/ROADMAP.md T12): what each [Entry] does with the values the
 * import has just written, through the entry point the settings pages use. Not loaded by the JVM tests, which give
 * [SettingsEffects.run] fakes; call [actionOf] on the main thread. The settings pages ask before some of these
 * changes (the launcher icon may lose pinned shortcuts, the alarm backend needs the exact alarm permission); an import
 * applies them as the user confirmed the categories, and the result page tells what failed (P7.5).
 *
 * zh-CN: 生效表的 Android 部分 (ROADMAP T12): 每个 [Entry] 经设置页所用的入口应用导入刚写入的值. JVM 测试不加载它, 而是给
 * [SettingsEffects.run] 传入替身; 须在主线程调用 [actionOf]. 设置页对其中一些修改会先提示 (更换启动图标可能丢失已固定的快捷方式,
 * 闹钟后端需要精确闹钟权限); 导入在用户确认分类后直接应用, 失败项由结果页说明 (P7.5).
 */
class AndroidSettingsEffects(context: Context) {

    private val context = context.applicationContext

    fun actionOf(entry: Entry): Action = when (entry) {
        Entry.THEME_COLOR -> Action {
            // A removed theme color (back to its default) applies the default color.
            ThemeColorCoordinator.apply(ThemeColor.fromPreferences() ?: ThemeColorManager.defaultThemeColor, ThemeColorSource.RESTORE)
        }
        Entry.NIGHT_MODE -> Action {
            // The same choice as at app start (App.setUpDefaultNightMode).
            ViewUtils.setDefaultNightMode(
                when {
                    ViewUtils.isAutoNightModeEnabled -> ViewUtils.MODE.FOLLOW
                    AppearanceKeys.NIGHT_MODE_ENABLED.isStored() -> if (ViewUtils.isNightModeEnabled) ViewUtils.MODE.NIGHT else ViewUtils.MODE.DAY
                    else -> ViewUtils.MODE.NULL
                }
            )
        }
        Entry.LANGUAGE -> Action {
            val language = Language.getPrefLanguage()
            when (language.isAuto()) {
                true -> LocaleUtils.setFollowSystem(context)
                else -> LocaleUtils.setLocale(context, language.locale)
            }
        }
        Entry.LAUNCHER_ICON -> Action { LauncherIconComponents.applyStored(context) }
        Entry.TIMED_TASK_BACKEND -> Action { TimedTaskScheduler.init(context) }
        Entry.FOREGROUND_SERVICE -> Action {
            val service = ForegroundService(context)
            val wanted = GeneralKeys.FOREGROUND_SERVICE.get()
            if (wanted && !service.isRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    throw EffectException(EffectException.Reason.NOTIFICATION_PERMISSION, "The foreground service needs the notification permission")
                }
                if (!service.start()) throw IllegalStateException("The foreground service did not start")
            } else if (!wanted && service.isRunning) {
                service.stop()
            }
        }
    }
}
