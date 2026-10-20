package org.autojs.autojs.core.plugin.epub

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.epub.api.EpubErrorCodes

/**
 * Second step of the reader launch (plugin roadmap P5.4, decision D12): after `openReader`
 * answered with a session, the host resolves the plugin's reader Activity by
 * `EpubActions.READER_ACTIVITY_ACTION` inside the plugin package only and starts it explicitly
 * with the session token as its only extra. The plugin never starts the Activity itself.
 */
object EpubReaderLauncher {
    private fun action(context: Context) = if (EpubPluginHost.isPackaged(context))
        EpubActions.PACKAGED_READER_ACTIVITY_ACTION else EpubActions.READER_ACTIVITY_ACTION

    /** The single Activity of [packageName] that answers the reader action, or `PLUGIN_INCOMPATIBLE`. */
    fun resolve(context: Context, packageName: String): ComponentName {
        val intent = Intent(action(context)).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(packageName)
        }
        val packageManager = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val components = resolved.mapNotNull { info ->
            info.activityInfo?.takeIf { it.exported && it.enabled }?.let { ComponentName(it.packageName, it.name) }
        }.distinct()
        return components.singleOrNull() ?: throw EpubPluginException(
            EpubErrorMapper.normalize(
                EpubErrorCodes.PLUGIN_INCOMPATIBLE,
                if (components.isEmpty()) "$packageName declares no reader Activity for ${EpubActions.READER_ACTIVITY_ACTION}"
                else "$packageName declares ${components.size} reader Activities for ${EpubActions.READER_ACTIVITY_ACTION}",
            ),
        )
    }

    fun intent(context: Context, component: ComponentName, token: String): Intent =
        Intent(action(context)).apply {
            this.component = component
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(EpubActions.EXTRA_SESSION_TOKEN, token)
            // A reader already on top of its task (D32 leaves it there) must receive the new session
            // through onNewIntent; without SINGLE_TOP the activity manager only brings the task to
            // the front and drops the intent, and the session waits unclaimed until its timeout.
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun launch(context: Context, packageName: String, token: String) {
        val intent = intent(context, resolve(context, packageName), token)
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            throw EpubPluginException(EpubErrorMapper.normalize(EpubErrorCodes.PLUGIN_UNAVAILABLE, "cannot start the reader Activity: ${t.message}"), t)
        }
    }
}
