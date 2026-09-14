package org.autojs.autojs.inrt

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs6.R

/** Registers before STARTED and restores pending permission/settings requests across recreation. */
class InrtLaunchGate(
    private val activity: AppCompatActivity,
    private val config: LaunchConfig,
    private val launch: () -> Unit,
    private val cancel: () -> Unit,
) {
    private var phase = activity.savedStateRegistry.consumeRestoredStateForKey(KEY)?.getString("phase") ?: READY
    private val permissions = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkAccess()
    }
    private val settings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkAccess()
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(KEY) { bundleOf("phase" to phase) }
        activity.supportFragmentManager.setFragmentResultListener(KEY, activity) { _, result ->
            if (result.getBoolean("request")) requestAccess() else {
                phase = CANCELLED
                cancel()
            }
        }
    }

    fun begin() {
        if (phase == READY) checkAccess()
    }

    private fun checkAccess() {
        if (phase == LAUNCHED || phase == CANCELLED || activity.isFinishing || activity.isDestroyed) return
        if (!config.requiresSharedStorage || InrtStorageAccess.has(activity)) {
            phase = LAUNCHED
            Pref.updateLauncherVisibility(config)
            launch()
        } else {
            phase = EXPLAINING
            if (activity.supportFragmentManager.findFragmentByTag(KEY) == null) {
                StorageDialog().apply {
                    arguments = bundleOf(
                        "message" to InrtStorageAccess.explanation(this@InrtLaunchGate.activity),
                        "requestable" to InrtStorageAccess.isDeclared(this@InrtLaunchGate.activity),
                    )
                }.show(activity.supportFragmentManager, KEY)
            }
        }
    }

    private fun requestAccess() {
        if (!InrtStorageAccess.isDeclared(activity)) {
            phase = READY
            activity.window.decorView.post { checkAccess() }
            return
        }
        phase = WAITING
        if (Build.VERSION.SDK_INT < 30 && InrtStorageAccess.legacyPermissions.any {
                activity.shouldShowRequestPermissionRationale(it)
            }) {
            permissions.launch(InrtStorageAccess.legacyPermissions)
        } else if (Build.VERSION.SDK_INT < 30 && !activity.getPreferences(0).getBoolean(REQUESTED, false)) {
            activity.getPreferences(0).edit().putBoolean(REQUESTED, true).apply()
            permissions.launch(InrtStorageAccess.legacyPermissions)
        } else {
            for (intent in InrtStorageAccess.settingsIntents(activity)) {
                try {
                    settings.launch(intent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // Some OEMs only provide the list of apps with special access.
                } catch (_: SecurityException) {
                    // Try the remaining system entry before reporting the unavailable action.
                }
            }
            phase = EXPLAINING
            activity.window.decorView.post {
                StorageDialog().apply {
                    arguments = bundleOf("message" to this@InrtLaunchGate.activity.getString(R.string.text_inrt_storage_settings_unavailable))
                }.show(activity.supportFragmentManager, KEY)
            }
        }
    }

    class StorageDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_launch_requires_shared_storage)
            .setMessage(requireArguments().getString("message"))
            .setNegativeButton(R.string.text_cancel) { _, _ -> result(false) }
            .apply {
                if (requireArguments().getBoolean("requestable")) {
                    setPositiveButton(R.string.text_settings) { _, _ -> result(true) }
                }
            }
            .create()

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)
            result(false)
        }

        private fun result(request: Boolean) {
            parentFragmentManager.setFragmentResult(KEY, bundleOf("request" to request))
        }
    }

    private companion object {
        const val KEY = "inrt.launch.storage"
        const val REQUESTED = "inrt.storage.requested"
        const val READY = "ready"
        const val EXPLAINING = "explaining"
        const val WAITING = "waiting"
        const val LAUNCHED = "launched"
        const val CANCELLED = "cancelled"
    }
}
