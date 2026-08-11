package org.autojs.autojs.ui.python

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.python.PythonRuntimeOfficialTransport
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground-only admission UI for the independently installed Python Runtime plugin.
 *
 * Background launch paths never call this object: they keep returning the stable host error from
 * the execution engine and never start an Activity or show a dialog.
 */
object PythonRuntimePluginUi {

    internal enum class Availability {
        READY,
        MISSING,
        DISABLED,
        INCOMPATIBLE,
    }

    private val dialogShowing = AtomicBoolean(false)

    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(context: Context): Boolean {
        val availability = inspect(context)
        if (availability == Availability.READY) return true
        showUnavailableDialog(context, availability)
        return false
    }

    internal fun decideAvailability(
        installed: Boolean,
        logicallyEnabled: Boolean,
        androidEnabled: Boolean,
        contractEligible: Boolean,
    ): Availability = when {
        !installed -> Availability.MISSING
        !logicallyEnabled || !androidEnabled -> Availability.DISABLED
        !contractEligible -> Availability.INCOMPATIBLE
        else -> Availability.READY
    }

    private fun inspect(context: Context): Availability {
        val installed = runCatching {
            PythonRuntimeOfficialTransport(context).inspect()
        }.getOrNull()
        return decideAvailability(
            installed = installed != null,
            logicallyEnabled = PluginEnableStore.isEnabled(
                context.applicationContext,
                PythonRuntimeOfficialTransport.OFFICIAL_PACKAGE,
            ),
            androidEnabled = installed?.let {
                it.applicationEnabled && it.serviceEnabled
            } ?: false,
            contractEligible = installed?.let {
                it.component == PythonRuntimeOfficialTransport.OFFICIAL_PROVIDER &&
                    it.declaredAction == PythonRuntimeContract.RUNTIME_ACTION &&
                    it.exported &&
                    it.requiredPermission == PythonRuntimeContract.PLUGIN_PERMISSION &&
                    it.sameSignerAsHost
            } ?: false,
        )
    }

    private fun showUnavailableDialog(context: Context, availability: Availability) {
        if (!dialogShowing.compareAndSet(false, true)) return
        val content = when (availability) {
            Availability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                DISPLAY_NAME,
            )
            Availability.DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                DISPLAY_NAME,
            )
            Availability.INCOMPATIBLE -> context.getString(
                R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch,
            )
            Availability.READY -> return
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
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = PythonRuntimeOfficialTransport.OFFICIAL_PACKAGE,
                    )
                }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    private const val DISPLAY_NAME = "Python Runtime"
}
