package org.autojs.autojs.permission

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.runtime.api.Permissions
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.drawer.PermissionItemHelper
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.WeakHashMap

/** User-initiated permission requests; never called automatically at app startup. */
class LocalNetworkPermission(override val context: Context) : PermissionItemHelper {

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

    override fun revoke(): Boolean = false.also {
        if (!isRequired(context) || !has()) return@also
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            config()
            return@also
        }
        // An app cannot drop a granted runtime permission at once: Android revokes it only after
        // every process of the app has ended. App info is not always reachable either (some
        // managed devices block it), so exiting is offered next to the system settings.
        MaterialDialog.Builder(activity)
            .title(R.string.text_local_network_permission)
            .content(R.string.text_local_network_permission_revoke)
            .widgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .neutralText(R.string.dialog_button_system_settings)
            .onNeutral { _, _ -> config() }
            .positiveText(R.string.dialog_button_revoke_and_exit)
            .onPositive { _, _ -> revokeAndExit(activity) }
            .show()
    }

    fun config() = IntentUtils.launchAppDetailsSettings(context)

    @SuppressLint("NewApi")
    private fun revokeAndExit(activity: Activity) {
        // isRequired() above implies API 37, where revokeSelfPermissionOnKill (API 33) exists.
        activity.revokeSelfPermissionOnKill(PERMISSION)
        ViewUtils.showToast(activity, R.string.text_local_network_permission_revoke_exiting)
        // The system registers the revocation asynchronously. Exiting at once outran it on a
        // Samsung Android 17 device (the grant stayed until AutoJs6 later ran in the background),
        // while exiting 3 s later revoked it right away. The exit itself is the drawer's Exit
        // path, which also stops the services that would keep the process alive.
        Handler(Looper.getMainLooper()).postDelayed({
            IntentUtils.App.exit(activity, (activity as? MainActivity)?.let { it::beforeExit })
        }, EXIT_DELAY_AFTER_REVOKE_MILLIS)
    }

    companion object {

        const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

        private const val EXIT_DELAY_AFTER_REVOKE_MILLIS = 3000L

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
