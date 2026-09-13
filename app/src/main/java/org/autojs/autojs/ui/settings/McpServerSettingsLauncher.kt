package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.tool.McpServerTool
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginSettingsActivity
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.plugin.mcp.server.api.McpServerActions
import org.autojs.plugin.mcp.server.api.McpServerIds

/** Both host entry points use the same explicit, permission-checked activity contract. */
object McpServerSettingsLauncher {
    @Suppress("DEPRECATION")
    fun launch(context: Context) {
        if (runCatching { context.packageManager.getPackageInfo(McpServerIds.DEFAULT_PACKAGE_NAME, 0) }.isFailure) {
            McpServerTool.openPluginCenter(context)
            return
        }
        if (!PluginEnableStore.isEnabled(context, McpServerIds.DEFAULT_PACKAGE_NAME, false) ||
            !runCatching { PluginTrustManager.isAuthorized(context, McpServerIds.DEFAULT_PACKAGE_NAME) }.getOrDefault(false)) {
            PluginSettingsActivity.launch(context, McpServerIds.DEFAULT_PACKAGE_NAME, "MCP Server", source = PluginIndexSource.OFFICIAL)
            return
        }
        val query = Intent(McpServerActions.OPEN_SETTINGS).addCategory(Intent.CATEGORY_DEFAULT).setPackage(McpServerIds.DEFAULT_PACKAGE_NAME)
        val entries = context.packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == McpServerIds.DEFAULT_PACKAGE_NAME && it.exported && it.enabled &&
                it.applicationInfo.enabled && it.permission == McpServerActions.PLUGIN_PERMISSION }
            .map { ComponentName(it.packageName, it.name) }.distinct()
        val launched = entries.singleOrNull()?.let { query.setComponent(it).startSafely(context, true) } == true
        if (!launched) MaterialDialog.Builder(context)
            .title(R.string.text_mcp_server)
            .content(R.string.prompt_mcp_server_settings_unavailable)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_plugin_center)
            .onPositive { _, _ -> McpServerTool.openPluginCenter(context) }
            .show()
    }
}
