package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R

/**
 * Private, session-scoped landing page for PackageInstaller callbacks.
 *
 * Keeping callbacks outside [PackageInstallerActivity] prevents a completed session from
 * replacing or finishing another package-import flow that happens to be in the same task.
 */
class PackageInstallStatusActivity : BaseActivity() {

    override val handleContentViewFromHorizontalNavigationBarAutomatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        handleStatus(intent)
    }

    private fun handleStatus(statusIntent: Intent) {
        if (statusIntent.action != ACTION_INSTALL_STATUS) {
            finish()
            return
        }
        val expectedSessionId = statusIntent.getIntExtra(EXTRA_EXPECTED_SESSION_ID, INVALID_SESSION_ID)
        val actualSessionId = statusIntent.getIntExtra(
            PackageInstaller.EXTRA_SESSION_ID,
            INVALID_SESSION_ID,
        )
        if (expectedSessionId < 0 || actualSessionId != expectedSessionId) {
            Log.w(
                TAG,
                "Ignoring package installation callback with mismatched session IDs: " +
                    "expected=$expectedSessionId, actual=$actualSessionId",
            )
            finish()
            return
        }

        when (val status = statusIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> launchConfirmation(statusIntent)
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(
                    this,
                    R.string.text_package_installation_succeeded,
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
            else -> {
                val detail = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: getString(R.string.error_package_installation_status, status)
                Log.w(TAG, "Package installation failed: status=$status, message=$detail")
                showFailure(detail)
            }
        }
    }

    private fun launchConfirmation(statusIntent: Intent) {
        val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        if (confirmation == null) {
            showFailure(getString(R.string.error_package_installation_confirmation_missing))
            return
        }
        runCatching {
            startActivity(confirmation)
        }.onSuccess {
            finish()
        }.onFailure { error ->
            Log.w(TAG, "Unable to launch package installation confirmation", error)
            showFailure(error.message.orEmpty())
        }
    }

    private fun showFailure(detail: String) {
        if (isFinishing || isDestroyed) return
        MaterialDialog.Builder(this)
            .title(R.string.text_failed)
            .content(getString(R.string.error_android_package_installation_failed, detail))
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .dismissListener { finish() }
            .show()
    }

    companion object {
        private const val ACTION_INSTALL_STATUS =
            "org.autojs.autojs.action.PACKAGE_INSTALL_STATUS"
        private const val EXTRA_EXPECTED_SESSION_ID =
            "org.autojs.autojs.extra.EXPECTED_PACKAGE_INSTALL_SESSION_ID"
        private const val INVALID_SESSION_ID = -1
        private const val TAG = "PackageInstallStatus"

        internal fun createIntent(context: Context, sessionId: Int): Intent =
            Intent(context, PackageInstallStatusActivity::class.java).apply {
                action = ACTION_INSTALL_STATUS
                data = Uri.Builder()
                    .scheme("autojs6")
                    .authority("package-install-status")
                    .appendPath(sessionId.toString())
                    .build()
                putExtra(EXTRA_EXPECTED_SESSION_ID, sessionId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
    }
}
