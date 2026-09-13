package org.autojs.autojs.ui.main.scripts

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks
import org.autojs.autojs6.R
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

/** Persists callback state and offers a user-driven route back to a pending confirmation. */
internal object PackageInstallStatusCoordinator {
    private const val ACTION_CALLBACK = "org.autojs.autojs.action.PACKAGE_INSTALL_STATUS"
    private const val EXTRA_EXPECTED_ID = "org.autojs.autojs.extra.EXPECTED_PACKAGE_INSTALL_SESSION_ID"
    private const val EXTRA_CONFIRMATION_TOKEN = "org.autojs.autojs.extra.PACKAGE_INSTALL_CONFIRMATION_TOKEN"
    private const val CHANNEL = "package-installation-status"
    private const val TAG = "PackageInstallStatus"
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Int) -> Unit>()
    private val prompted = mutableSetOf<Int>()
    private var foreground = WeakReference<Activity>(null)
    private var prompt: MaterialDialog? = null
    private var promptSession: Int? = null
    private var promptGeneration = 0
    private var statusStore: PackageInstallStatusStore? = null

    @Synchronized
    private fun store(context: Context): PackageInstallStatusStore = statusStore
        ?: PackageInstallStatusStore(File(context.noBackupFilesDir, "package-install-status.json"))
            .also { statusStore = it }

    fun initialize(application: Application) {
        application.registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {
            private val started = mutableSetOf<Activity>()

            override fun onActivityStarted(activity: Activity) { started += activity }

            override fun onActivityResumed(activity: Activity) {
                foreground = WeakReference(activity)
                main.post { offerRecovery(activity) }
            }

            override fun onActivityPaused(activity: Activity) {
                if (foreground.get() === activity) {
                    foreground.clear()
                    promptSession?.let(prompted::remove)
                    dismissPrompt()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                started -= activity
                if (started.isEmpty()) prompted.clear()
            }

            override fun onActivityDestroyed(activity: Activity) { started -= activity }
        })
    }

    fun register(context: Context, sessionId: Int, packageName: String, appLabel: String = packageName) =
        store(context).register(sessionId, packageName, appLabel)
    fun records(context: Context): List<PackageInstallStatusRecord> = store(context).records()
    fun record(context: Context, sessionId: Int) = records(context).find { it.sessionId == sessionId }
    fun addListener(listener: (Int) -> Unit) { listeners += listener }
    fun removeListener(listener: (Int) -> Unit) { listeners -= listener }

    fun callbackIntent(context: Context, sessionId: Int) = Intent(context, PackageInstallStatusReceiver::class.java).apply {
        action = ACTION_CALLBACK
        data = Uri.Builder().scheme("autojs6").authority("package-install-status").appendPath(sessionId.toString()).build()
        putExtra(EXTRA_EXPECTED_ID, sessionId)
    }

    fun receive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_EXPECTED_ID, -1)
        if (id < 0 || intent.action != ACTION_CALLBACK || intent.data != callbackIntent(context, id).data ||
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != id) {
            Log.w(TAG, "Ignoring installation callback with mismatched identity")
            return
        }
        val prior = record(context, id)
        if (prior == null || prior.isTerminal) {
            Log.w(TAG, "Ignoring unknown or already completed installation: $id")
            return
        }
        var status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        var detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        var confirmationKey: String? = null
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirmation == null) {
                status = PackageInstaller.STATUS_FAILURE
                detail = context.getString(R.string.error_package_installation_confirmation_missing)
            } else {
                val launch = Intent(confirmation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmationKey = launch.cloneFilter().toUri(Intent.URI_INTENT_SCHEME)
                val token = PendingIntent.getActivity(context, id, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                // The system's active PackageInstaller session retains this broadcast token.
                // Retain the confirmation token inside it as well: an unreferenced token can
                // otherwise be collected after our process dies, even with its identity on disk.
                PendingIntent.getBroadcast(context, id, callbackIntent(context, id)
                    .putExtra(EXTRA_CONFIRMATION_TOKEN, token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }
        }
        val updated = store(context).update(id, status, detail, confirmationKey) ?: return
        Log.i(TAG, "Installation callback recorded: session=$id, status=$status")
        if (prior.confirmationKey != null && prior.confirmationKey != updated.confirmationKey) {
            confirmationToken(context, prior)?.cancel()
        }
        publish(context, updated)
    }

    fun confirmationToken(context: Context, record: PackageInstallStatusRecord): PendingIntent? = record.confirmationKey?.let {
        PendingIntent.getActivity(context, record.sessionId, Intent.parseUri(it, Intent.URI_INTENT_SCHEME),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    }

    fun confirmationUnavailable(context: Context, sessionId: Int) {
        val prior = record(context, sessionId) ?: return
        if (prior.isTerminal) return
        val updated = store(context).update(sessionId, PackageInstaller.STATUS_FAILURE,
            context.getString(R.string.error_package_installation_confirmation_expired), null) ?: return
        confirmationToken(context, prior)?.cancel()
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        publish(context, updated)
    }

    fun acknowledge(context: Context, sessionId: Int) {
        val prior = record(context, sessionId) ?: return
        store(context).remove(sessionId)
        confirmationToken(context, prior)?.cancel()
        context.getSystemService(NotificationManager::class.java).cancel(CHANNEL, sessionId)
        PendingIntent.getActivity(context, sessionId, PackageInstallStatusActivity.showIntent(context, sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.cancel()
        PendingIntent.getBroadcast(context, sessionId, callbackIntent(context, sessionId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE)?.cancel()
    }

    private fun publish(context: Context, record: PackageInstallStatusRecord) {
        // A notification failure must not prevent delivery to an already visible UI.
        runCatching { notifyUser(context, record) }
            .onFailure { Log.w(TAG, "Unable to post installation status notification", it) }
        main.post {
            if (promptSession == record.sessionId) {
                prompted -= record.sessionId
                dismissPrompt()
            }
            listeners.forEach { it(record.sessionId) }
            foreground.get()?.let(::offerRecovery)
        }
    }

    private fun notifyUser(context: Context, record: PackageInstallStatusRecord) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.text_package_installation), NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val content = when (record.status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> context.getString(R.string.text_package_installation_confirmation_pending)
            PackageInstaller.STATUS_SUCCESS -> context.getString(R.string.text_package_installation_succeeded)
            else -> context.getString(R.string.error_android_package_installation_failed, record.detail)
        }
        val entry = PendingIntent.getActivity(context, record.sessionId,
            PackageInstallStatusActivity.showIntent(context, record.sessionId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle(record.appLabel)
            .setSubText(record.packageName)
            .setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(entry).setAutoCancel(false).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        manager.notify(CHANNEL, record.sessionId, notification)
    }

    private fun offerRecovery(activity: Activity) {
        if (foreground.get() !== activity || activity.isFinishing || activity.isDestroyed || prompt != null ||
            activity is PackageInstallStatusActivity || activity is PackageInstallerActivity || activity is PackageInstallerEntryActivity) return
        val next = runCatching { records(activity).firstOrNull {
            it.sessionId !in prompted && it.status != PackageInstallStatusRecord.INSTALLING
        } }
            .onFailure { Log.w(TAG, "Unable to load pending installations", it) }.getOrNull() ?: return
        prompted += next.sessionId
        promptSession = next.sessionId
        val showingGeneration = ++promptGeneration
        prompt = MaterialDialog.Builder(activity)
            .title(R.string.text_package_installation)
            .content(activity.getString(if (next.isTerminal) R.string.text_package_installation_result_available
                else R.string.text_package_installation_confirmation_pending) + "\n\n" + next.appLabel)
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_dismiss)
            .onPositive { _, _ -> activity.startActivity(PackageInstallStatusActivity.showIntent(activity, next.sessionId)) }
            .dismissListener {
                if (showingGeneration == promptGeneration) {
                    prompt = null
                    promptSession = null
                }
            }
            .show()
    }

    private fun dismissPrompt() {
        ++promptGeneration
        prompt?.dismiss()
        prompt = null
        promptSession = null
    }
}
