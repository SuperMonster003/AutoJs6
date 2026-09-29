package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds

/** Opens the plugin's existing launcher; the task workbench grows there in roadmap P6. */
object ThreeStoveAgentLauncher {
    @Suppress("DEPRECATION")
    internal fun resolveIntent(context: Context): Intent? {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
        val entries = context.packageManager.queryIntentActivities(query, 0).mapNotNull { it.activityInfo }
            .filter { it.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME && it.exported && it.enabled && it.applicationInfo.enabled }
            .map { ComponentName(it.packageName, it.name) }.distinct()
        return entries.singleOrNull()?.let { query.setComponent(it) }
    }

    fun launch(context: Context) {
        if (runCatching { context.packageManager.getPackageInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, 0) }.isFailure) {
            ThreeStoveAgentGuide.openPluginCenter(context)
            return
        }
        if (resolveIntent(context)?.startSafely(context, true) == true) return
        MaterialDialog.Builder(context).title(R.string.text_three_stove_agent).content(R.string.prompt_three_stove_agent_launcher_unavailable)
            .negativeText(R.string.dialog_button_cancel).positiveText(R.string.text_plugin_center)
            .onPositive { _, _ -> ThreeStoveAgentGuide.openPluginCenter(context) }.show()
    }
}
