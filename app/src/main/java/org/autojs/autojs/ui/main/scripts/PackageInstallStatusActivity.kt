package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ActivityLaunchOptions
import org.autojs.autojs6.R

/**
 * Private, session-scoped entry opened by a visible installer, notification or recovery prompt.
 *
 * Keeping callbacks outside [PackageInstallerActivity] prevents a completed session from
 * replacing or finishing another package-import flow that happens to be in the same task.
 */
class PackageInstallStatusActivity : BaseActivity() {

    override val handleContentViewFromHorizontalNavigationBarAutomatically = false

    private var sessionId = INVALID_SESSION_ID
    private var launchedGeneration = -1
    private var awaitingConfirmation = false
    private var awaitingStatus = false
    private var resumed = false
    private var dialog: MaterialDialog? = null
    private var dialogGeneration = 0
    private val changed: (Int) -> Unit = { id ->
        if (id == sessionId) {
            closeDialog()
            renderWhenVisible()
        }
    }
    private val confirmationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        awaitingConfirmation = false
        awaitingStatus = true
        if (it.data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true) {
            PackageInstallStatusCoordinator.confirmationUnavailable(this, sessionId)
        }
        renderWhenVisible()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_SHOW_STATUS) {
            // Previously issued Activity tokens can still arrive after an app update.
            if (savedInstanceState == null) handleStatus(intent) else finish()
            return
        }
        sessionId = intent.getIntExtra(EXTRA_EXPECTED_SESSION_ID, INVALID_SESSION_ID)
        launchedGeneration = savedInstanceState?.getInt("launchedGeneration", -1) ?: -1
        awaitingConfirmation = savedInstanceState?.getBoolean("awaitingConfirmation", false) ?: false
        awaitingStatus = savedInstanceState?.getBoolean("awaitingStatus", false) ?: false
        if (sessionId < 0) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != ACTION_SHOW_STATUS) {
            handleStatus(intent)
            return
        }
        setIntent(intent)
        sessionId = intent.getIntExtra(EXTRA_EXPECTED_SESSION_ID, INVALID_SESSION_ID)
        launchedGeneration = -1
        awaitingConfirmation = false
        awaitingStatus = false
        closeDialog()
        renderWhenVisible()
    }

    override fun onStart() {
        super.onStart()
        PackageInstallStatusCoordinator.addListener(changed)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        renderWhenVisible()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        PackageInstallStatusCoordinator.removeListener(changed)
        closeDialog()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("launchedGeneration", launchedGeneration)
        outState.putBoolean("awaitingConfirmation", awaitingConfirmation)
        outState.putBoolean("awaitingStatus", awaitingStatus)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) renderWhenVisible()
    }

    private fun renderWhenVisible() {
        window.decorView.post {
            if (sessionId < 0 || !resumed || !hasWindowFocus() || isFinishing || isDestroyed || dialog != null) return@post
            val record = PackageInstallStatusCoordinator.record(this, sessionId) ?: run { finish(); return@post }
            if (record.isTerminal) {
                if (record.status == PackageInstaller.STATUS_SUCCESS) {
                    PackageInstallStatusCoordinator.acknowledge(this, sessionId)
                    Toast.makeText(this, R.string.text_package_installation_succeeded, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val showingGeneration = ++dialogGeneration
                    dialog = MaterialDialog.Builder(this)
                        .title(R.string.text_failed)
                        .content(getString(R.string.error_android_package_installation_failed,
                            record.detail.ifEmpty { getString(R.string.error_package_installation_status, record.status) }))
                        .positiveText(R.string.dialog_button_dismiss)
                        .dismissListener {
                            if (showingGeneration == dialogGeneration) {
                                PackageInstallStatusCoordinator.acknowledge(this, record.sessionId)
                                finish()
                            }
                        }.show()
                }
                return@post
            }
            if (awaitingConfirmation) return@post
            if (record.status == PackageInstaller.STATUS_PENDING_USER_ACTION && record.generation != launchedGeneration) {
                val token = PackageInstallStatusCoordinator.confirmationToken(this, record)
                if (token == null) {
                    PackageInstallStatusCoordinator.confirmationUnavailable(this, sessionId)
                    return@post
                }
                launchedGeneration = record.generation
                awaitingConfirmation = true
                runCatching {
                    confirmationLauncher.launch(IntentSenderRequest.Builder(token.intentSender).build(),
                        ActivityLaunchOptions.forVisibleSender())
                }.onFailure {
                    Log.w(TAG, "Unable to resume installation confirmation", it)
                    awaitingConfirmation = false
                    PackageInstallStatusCoordinator.confirmationUnavailable(this, sessionId)
                }
                return@post
            }
            val showingGeneration = ++dialogGeneration
            // The Activity result acknowledges the confirmation page, not installation success.
            // Older systems can take a while to deliver the final PackageInstaller callback.
            val waiting = record.status == PackageInstallStatusRecord.INSTALLING || awaitingStatus
            val builder = MaterialDialog.Builder(this).title(R.string.text_package_installation)
                .content(if (waiting) R.string.text_package_installation_waiting
                    else R.string.text_package_installation_confirmation_pending)
                .negativeText(R.string.dialog_button_dismiss)
                .onNegative { _, _ -> finish() }
                .cancelListener { finish() }
                .dismissListener { if (showingGeneration == dialogGeneration) dialog = null }
            if (waiting) builder.progress(true, 0)
            else builder.positiveText(R.string.dialog_button_continue).onPositive { _, _ ->
                launchedGeneration = -1
                renderWhenVisible()
            }
            dialog = builder.show()
        }
    }

    private fun closeDialog() {
        ++dialogGeneration
        dialog?.dismiss()
        dialog = null
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
        private const val ACTION_SHOW_STATUS = "org.autojs.autojs.action.SHOW_PACKAGE_INSTALL_STATUS"
        private const val ACTION_INSTALL_STATUS =
            "org.autojs.autojs.action.PACKAGE_INSTALL_STATUS"
        private const val EXTRA_EXPECTED_SESSION_ID =
            "org.autojs.autojs.extra.EXPECTED_PACKAGE_INSTALL_SESSION_ID"
        private const val INVALID_SESSION_ID = -1
        private const val TAG = "PackageInstallStatus"

        internal fun showIntent(context: Context, sessionId: Int): Intent =
            Intent(context, PackageInstallStatusActivity::class.java).apply {
                action = ACTION_SHOW_STATUS
                data = Uri.parse("autojs6://package-install-recovery/$sessionId")
                putExtra(EXTRA_EXPECTED_SESSION_ID, sessionId)
            }

    }
}
