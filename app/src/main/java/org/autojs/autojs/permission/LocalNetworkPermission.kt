package org.autojs.autojs.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.runtime.api.Permissions
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.util.WeakHashMap

/** User-initiated permission requests; never called automatically at app startup. */
class LocalNetworkPermission(override val context: Context) : PermissionItemHelper, AbleToUrge {

    override val isInMainThread = true

    override fun has() = isGranted(context)

    override fun request(): Boolean = has().also { if (!it) request { } }

    fun request(onResult: (Boolean) -> Unit) {
        if (has()) {
            onResult(true)
            return
        }
        val activity = context.findActivity() as? FragmentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onResult(false)
            return
        }
        pending[activity]?.let {
            it.callbacks.add(onResult)
            return
        }
        val request = PendingRequest(onResult)
        pending[activity] = request
        activity.lifecycle.addObserver(request)
        val launch = {
            // Always let PermissionController decide whether another request is possible.
            // A persisted "already asked" bit would defeat Android 17's Nearby Devices
            // denial-counter reset when ACCESS_LOCAL_NETWORK is introduced on upgrade.
            Permissions.getRequestMultiplePermissionsLauncher(activity).launch(arrayOf(PERMISSION))
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)) {
            request.dialog = MaterialDialog.Builder(activity)
                .title(R.string.text_local_network_permission)
                .content(R.string.description_local_network_permission)
                .widgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.dialog_button_continue)
                .onPositive { _, _ -> launch() }
                .onNegative { _, _ -> complete(activity, false) }
                .cancelListener { complete(activity, false) }
                .show()
        } else {
            launch()
        }
    }

    override fun revoke(): Boolean = false.also { if (isRequired(context)) config() }

    fun config() = IntentUtils.launchAppDetailsSettings(context)

    override fun urge() { request() }

    override fun urgeIfNeeded() { requestIfNeeded() }

    companion object {

        const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

        @JvmStatic
        fun isRequired(context: Context) = Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37

        @JvmStatic
        fun isGranted(context: Context) = !isRequired(context) || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

        private val pending = WeakHashMap<FragmentActivity, PendingRequest>()

        internal fun onRequestResult(activity: FragmentActivity, result: Map<String, Boolean>) {
            if (PERMISSION !in result && result.isNotEmpty()) return
            if (activity !in pending) return
            val granted = isGranted(activity)
            complete(activity, granted)
            if (!granted && PERMISSION in result && !activity.isFinishing && !activity.isDestroyed &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)
            ) {
                // Offer a settings link only after a real denied result; never open it automatically.
                MaterialDialog.Builder(activity)
                    .title(R.string.text_local_network_permission)
                    .content(R.string.text_local_network_permission_settings)
                    .widgetThemeColor()
                    .negativeText(R.string.dialog_button_cancel)
                    .positiveText(R.string.dialog_button_system_settings)
                    .onPositive { _, _ -> IntentUtils.launchAppDetailsSettings(activity) }
                    .show()
            }
        }

        private fun complete(activity: FragmentActivity, granted: Boolean) {
            val request = pending.remove(activity) ?: return
            activity.lifecycle.removeObserver(request)
            request.dialog?.dismiss()
            request.callbacks.forEach { it(granted) }
        }

        private class PendingRequest(callback: (Boolean) -> Unit) : DefaultLifecycleObserver {
            val callbacks = mutableListOf(callback)
            var dialog: MaterialDialog? = null

            override fun onDestroy(owner: LifecycleOwner) {
                // A destroyed screen must not resume a queued connection or retain its views.
                pending.remove(owner as FragmentActivity)
                callbacks.clear()
                dialog?.dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
    }
}
