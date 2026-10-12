package org.autojs.autojs.ui.autojs4

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.plugin.autojs4.runtime.api.AutoJs4PluginActions
import org.autojs.plugin.autojs4.runtime.api.AutoJs4RuntimeContract
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground admission UI for the AutoJs4 Runtime plugin ("Run with AutoJs4" menu entry).
 * Availability is derived from the installed package only, so it is cheap enough to decide
 * which menu item to show; the engine performs the full contract validation when it binds.
 *
 * zh-CN: AutoJs4 Runtime 插件的前台准入界面 ("使用 AutoJs4 运行" 菜单项). 可用性只根据已安装包判断,
 * 开销足以用于决定显示哪个菜单项; 完整的契约校验在引擎绑定时进行.
 */
object AutoJs4RuntimePluginUi {

    const val DISPLAY_NAME = "AutoJs4 Runtime"
    const val OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.autojs4.runtime"

    enum class Availability {
        READY,
        MISSING,
        DISABLED,
        INCOMPATIBLE,
    }

    private val dialogShowing = AtomicBoolean(false)

    @JvmStatic
    fun inspect(context: Context): Availability {
        val appContext = context.applicationContext
        val services = appContext.packageManager.queryIntentServices(
            Intent(AutoJs4PluginActions.RUNTIME).addCategory(AutoJs4PluginActions.CATEGORY),
            PackageManager.GET_META_DATA,
        )
        val service = services.firstOrNull()?.serviceInfo ?: return Availability.MISSING
        val androidEnabled = service.enabled && service.applicationInfo.enabled
        val logicallyEnabled = PluginEnableStore.isEnabled(appContext, service.packageName)
        if (!androidEnabled || !logicallyEnabled) return Availability.DISABLED
        val contractEligible = service.exported && service.permission == AutoJs4RuntimeContract.PLUGIN_PERMISSION
        return if (contractEligible) Availability.READY else Availability.INCOMPATIBLE
    }

    @JvmStatic
    fun isInstalled(context: Context): Boolean = inspect(context) != Availability.MISSING

    /** Returns true when the plugin can be used; otherwise shows the guidance dialog and returns false. */
    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(context: Context): Boolean {
        val availability = inspect(context)
        if (availability == Availability.READY) return true
        showUnavailableDialog(context, availability)
        return false
    }

    @JvmStatic
    fun openPluginCenter(context: Context) {
        PluginCenterActivity.launch(
            context = context,
            sourceScope = PluginIndexSource.OFFICIAL,
            initialQuery = OFFICIAL_PACKAGE,
        )
    }

    private fun showUnavailableDialog(context: Context, availability: Availability) {
        if (!dialogShowing.compareAndSet(false, true)) return
        val content = when (availability) {
            Availability.MISSING -> context.getString(R.string.error_plugin_not_found_in_installed_apps, DISPLAY_NAME)
            Availability.DISABLED -> context.getString(R.string.error_plugin_is_not_enabled_in_plugin_center, DISPLAY_NAME)
            Availability.INCOMPATIBLE -> context.getString(R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch)
            Availability.READY -> {
                dialogShowing.set(false)
                return
            }
        }
        try {
            MaterialDialog.Builder(context)
                .title(R.string.text_plugin_center)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onPositive { _, _ -> openPluginCenter(context) }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }
}
