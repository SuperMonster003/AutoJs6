package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicLong

/** Plugin grants belong to the plugin UID, independently of the host's grant. */
object PluginLocalNetworkPermission {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private val requests = AtomicLong()
    private val packages = setOf(
        "io.github.supermonster003.autojs6.plugin.nodejs",
        "io.github.supermonster003.autojs6.plugin.bun.runtime",
        "io.github.supermonster003.autojs6.plugin.python.runtime",
        "io.github.supermonster003.autojs6.plugin.mcp.server",
    )

    fun isRequired(packageName: String) = Build.VERSION.SDK_INT >= 37 && packageName in packages

    fun isGranted(context: Context, packageName: String): Boolean = !isRequired(packageName) ||
        context.packageManager.checkPermission(PERMISSION, packageName) == PackageManager.PERMISSION_GRANTED

    /** Called only for a manual enable attempt. Automatic consumers use isGranted. */
    fun runBeforeEnable(context: Context, packageName: String, title: String, onAllowed: () -> Unit) {
        if (isGranted(context, packageName)) {
            onAllowed()
            return
        }
        PluginEnableStore.setEnabled(context, packageName, false)
        MaterialDialog.Builder(context)
            .title(R.string.text_local_network_permission)
            .content(context.getString(R.string.plugin_local_network_enable_explanation, title))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_authorize)
            .onPositive { _, _ -> request(context, packageName) { if (it) onAllowed() } }
            .showAdaptive()
    }

    fun summary(context: Context, packageName: String): String = context.getString(
        if (isGranted(context, packageName)) R.string.plugin_local_network_granted
        else R.string.plugin_local_network_denied,
    )

    /** A settings click grants or reviews permission, without enabling the plugin. */
    fun manage(context: Context, packageName: String, onResult: () -> Unit = {}) {
        if (!isRequired(packageName)) return
        if (isGranted(context, packageName)) {
            IntentUtils.launchAppDetailsSettings(context, packageName)
        } else request(context, packageName) { onResult() }
    }

    private fun request(context: Context, packageName: String, onResult: (Boolean) -> Unit) {
        val activity = context.findActivity() as? FragmentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onResult(false)
            return
        }
        if (isGranted(activity, packageName)) {
            onResult(true)
            return
        }
        val component = ComponentName(packageName, "$packageName.LocalNetworkPermissionActivity")
        @Suppress("DEPRECATION")
        val info = runCatching { activity.packageManager.getActivityInfo(component, 0) }.getOrNull()
        val intent = if (info?.exported == true && info.enabled && info.applicationInfo.enabled &&
            info.permission == "org.autojs.permission.PLUGIN"
        ) Intent().setComponent(component) else Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:$packageName"),
        )
        // Register without a LifecycleOwner: this user action happens after STARTED.
        // Unregister on destruction so a recreated screen cannot resume an old enable attempt.
        val request = object : DefaultLifecycleObserver {
            var launcher: ActivityResultLauncher<Intent>? = null
            var completed = false

            fun close() {
                if (completed) return
                completed = true
                launcher?.unregister()
                activity.lifecycle.removeObserver(this)
            }

            override fun onDestroy(owner: LifecycleOwner) = close()
        }
        activity.lifecycle.addObserver(request)
        request.launcher = activity.activityResultRegistry.register(
            "plugin-local-network-${requests.incrementAndGet()}", ActivityResultContracts.StartActivityForResult(),
        ) {
            if (!request.completed) {
                request.close()
                // Re-read PackageManager instead of trusting another activity's result code.
                onResult(isGranted(activity, packageName))
            }
        }
        try {
            request.launcher?.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            request.close()
            onResult(false)
        } catch (_: SecurityException) {
            request.close()
            onResult(false)
        }
    }
}
