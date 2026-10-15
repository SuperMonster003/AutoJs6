package org.autojs.autojs.core.plugin.gallery

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.autojs.plugin.gallery.api.GalleryActions
import org.autojs.plugin.gallery.api.GalleryErrorCodes

/**
 * Second step of a session launch (3-Maple Image roadmap T17): after an interactive call answered
 * with a session, the host resolves the plugin's session Activity by
 * `GalleryActions.SESSION_ACTIVITY_ACTION` inside the plugin package only and starts it explicitly
 * with the session token as its only extra. The plugin never starts the Activity itself.
 */
object GalleryActivityLauncher {

    /** The single Activity of [packageName] that answers the session action, or `PLUGIN_INCOMPATIBLE`. */
    fun resolve(context: Context, packageName: String): ComponentName {
        val intent = Intent(GalleryActions.SESSION_ACTIVITY_ACTION).apply {
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
        return components.singleOrNull() ?: throw GalleryErrorMapper.failure(
            GalleryErrorCodes.PLUGIN_INCOMPATIBLE,
            if (components.isEmpty()) "$packageName declares no session Activity for ${GalleryActions.SESSION_ACTIVITY_ACTION}"
            else "$packageName declares ${components.size} session Activities for ${GalleryActions.SESSION_ACTIVITY_ACTION}",
        )
    }

    fun intent(context: Context, component: ComponentName, token: String): Intent =
        Intent(GalleryActions.SESSION_ACTIVITY_ACTION).apply {
            this.component = component
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(GalleryActions.EXTRA_SESSION_TOKEN, token)
            // An Activity of a replaced session may still be on top of its task: it must receive the
            // new token through onNewIntent instead of being brought to the front unchanged.
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun launch(context: Context, packageName: String, token: String) {
        val intent = intent(context, resolve(context, packageName), token)
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            throw GalleryErrorMapper.failure(GalleryErrorCodes.PLUGIN_UNAVAILABLE, "cannot start the gallery Activity: ${t.message}", t)
        }
    }
}
