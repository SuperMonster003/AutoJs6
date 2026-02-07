package org.autojs.autojs.util

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.huaban.analysis.jieba.CharsDictionaryDatabase
import com.huaban.analysis.jieba.PhrasesDictionaryDatabase
import com.huaban.analysis.jieba.WordDictionaryDatabase
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ReservedForCompatibility
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Modified by SuperMonster003 as of Jan 15, 2026.
 */
object IntentUtils {

    @JvmStatic
    @JvmOverloads
    fun Intent.startSafely(context: Context, printStackTrace: Boolean = false, onFailure: ((Throwable) -> Unit)? = null): Boolean =
        runCatching {
            start(context)
        }.onFailure { t ->
            if (printStackTrace) t.printStackTrace()
            onFailure?.invoke(t)
        }.isSuccess

    @JvmStatic
    @JvmOverloads
    fun Intent.startSafelyWithOptions(context: Context, options: Bundle? = null, printStackTrace: Boolean = false, onFailure: ((Throwable) -> Unit)? = null): Boolean =
        runCatching {
            startWithOptions(context, options)
        }.onFailure { t ->
            if (printStackTrace) t.printStackTrace()
            onFailure?.invoke(t)
        }.isSuccess

    @JvmStatic
    fun Intent.start(context: Context) {
        val activity = context.findActivity()
        if (activity != null) {
            activity.startActivity(this)
        } else {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    @JvmStatic
    fun Intent.startWithOptions(context: Context, options: Bundle? = null) {
        val activity = context.findActivity()
        if (activity != null) {
            activity.startActivity(this, options)
        } else {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this, options)
        }
    }

    @Suppress("SpellCheckingInspection")
    fun chatWithQQ(context: Context, qq: String): Boolean {
        val uri = "mqqwpa://im/chat?chat_type=wpa&uin=$qq".toUri()
        return Intent(Intent.ACTION_VIEW, uri)
            .startSafely(context, true)
    }

    @Suppress("SpellCheckingInspection")
    fun joinQQGroup(context: Context, key: String): Boolean {
        val uri = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key".toUri()
        return Intent(Intent.ACTION_VIEW, uri)
            .startSafely(context, true)
    }

    @JvmOverloads
    fun sendMailTo(context: Context, sendTo: String, title: String? = null, content: String? = null): Boolean {
        val uri = "mailto:$sendTo".toUri()
        val target = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_CC, /* email */ arrayOf(sendTo))
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            content?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
        return Intent.createChooser(target, "").startSafely(context, true)
    }

    @JvmStatic
    @JvmOverloads
    fun browse(context: Context, link: String, exceptionHolder: ExceptionHolder? = null): Boolean =
        Intent(Intent.ACTION_VIEW, link.toUri())
            .startSafely(context, true) {
                exceptionHolder?.show(R.string.error_no_applications_available_for_browsing_this_link)
            }

    @JvmStatic
    fun shareText(context: Context, text: String?) =
        Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, text)
            .setType("text/plain")
            .startSafely(context, true)

    @JvmOverloads
    @ReservedForCompatibility
    @Deprecated("Deprecated in Java", ReplaceWith("launchAppDetailsSettings(Context, String)"))
    fun goToAppDetailSettings(context: Context, packageName: String = context.packageName) =
        launchAppDetailsSettings(context, packageName)

    @JvmStatic
    @JvmOverloads
    fun launchAppDetailsSettings(context: Context, packageName: String = context.packageName) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = "package:$packageName".toUri()
        }.startSafely(context, true)

    @JvmStatic
    @JvmOverloads
    fun installApk(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = Intent(Intent.ACTION_VIEW)
        .setDataAndType(
            getUriOfFile(context, path, fileProviderAuthority),
            Mime.APPLICATION_VND_ANDROID_PACKAGE_ARCHIVE,
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        .startSafely(context, true) {
            exceptionHolder?.show(R.string.error_no_applications_available_for_installing_this_file)
        }

    @JvmStatic
    @JvmOverloads
    fun getUriOfFile(context: Context, path: String?, fileProviderAuthority: String? = AppFileProvider.AUTHORITY): Uri =
        fileProviderAuthority?.let {
            FileProvider.getUriForFile(context, it, File(path ?: ""))
        } ?: "file://$path".toUri()

    @JvmStatic
    fun viewFile(context: Context, path: String, fileProviderAuthority: String = AppFileProvider.AUTHORITY): Boolean =
        viewFile(context, path, Mime.fromFileOr(path, Mime.WILDCARD), fileProviderAuthority)

    @JvmStatic
    @JvmOverloads
    fun viewFile(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ): Boolean = when (uri.scheme) {
        uri.scheme -> {
            uri.path?.let { viewFile(context, it, mimeType, fileProviderAuthority) } ?: false
        }
        else -> launchViewIntent(context, uri, mimeType, exceptionHolder)
    }

    @JvmStatic
    fun viewFile(
        context: Context,
        path: String,
        mimeType: String? = null,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = launchViewIntent(
        context = context,
        uri = getUriOfFile(context, path, fileProviderAuthority),
        mimeType = mimeType ?: Mime.fromFileOrWildcard(path),
        exceptionHolder = exceptionHolder,
    )

    private fun launchViewIntent(
        context: Context,
        uri: Uri,
        mimeType: String?,
        exceptionHolder: ExceptionHolder? = null,
    ) = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        .startSafely(context, true) {
            exceptionHolder?.show(R.string.error_no_applications_available_for_viewing_this_file)
        }

    @JvmStatic
    @JvmOverloads
    fun editFile(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ) = Intent(Intent.ACTION_EDIT)
        .setDataAndType(
            getUriOfFile(context, path, fileProviderAuthority),
            Mime.fromFileOrWildcard(path),
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        .startSafely(context, true) {
            exceptionHolder?.show(R.string.error_no_applications_available_for_editing_this_file)
        }

    @JvmStatic
    @JvmOverloads
    fun sendFile(
        context: Context,
        path: String,
        fileProviderAuthority: String = AppFileProvider.AUTHORITY,
        exceptionHolder: ExceptionHolder? = null,
    ): Boolean {
        val target = Intent(Intent.ACTION_SEND).apply {
            setType("text/plain")
            putExtra(Intent.EXTRA_STREAM, getUriOfFile(context, path, fileProviderAuthority))
        }
        return Intent.createChooser(target, context.getString(R.string.text_send))
            .startSafely(context, true) {
                exceptionHolder?.show(R.string.error_no_applications_available_for_sending_this_file)
            }
    }

    @JvmStatic
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    @JvmStatic
    fun launchDeveloperOptions(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

        return if (intent.resolveActivity(context.packageManager) != null) {
            intent.startSafely(context)
        } else {
            false
        }
    }

    @JvmStatic
    fun launchDeveloperOptionsOrSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )

        val pm = context.packageManager
        intents.firstOrNull { it.resolveActivity(pm) != null }?.startSafely(context)
    }

    fun requestAppUsagePermission(context: Context) =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .startSafely(context, true)

    data class SnackExceptionHolder @JvmOverloads constructor(
        private val view: View,
        private val overriddenMessage: String? = null,
        private val duration: Int = Snackbar.LENGTH_LONG,
    ) : ExceptionHolder {

        @JvmOverloads
        constructor(
            view: View,
            overriddenMessageRes: Int,
            duration: Int = Snackbar.LENGTH_LONG,
        ) : this(
            view = view,
            overriddenMessage = view.context.getString(overriddenMessageRes),
            duration = duration,
        )

        override fun show(message: String?) {
            ViewUtils.showSnack(view, overriddenMessage ?: message ?: "Failed to start an activity", duration)
        }

        override fun show(messageRes: Int) {
            show(view.context.getString(messageRes))
        }
    }

    data class ToastExceptionHolder @JvmOverloads constructor(
        private val context: Context,
        private val overriddenMessage: String? = null,
        private val isLong: Boolean = true,
    ) : ExceptionHolder {

        @JvmOverloads
        constructor(
            context: Context,
            overriddenMessageRes: Int,
            isLong: Boolean = true,
        ) : this(
            context = context,
            overriddenMessage = context.getString(overriddenMessageRes),
            isLong = isLong,
        )

        override fun show(message: String?) {
            ViewUtils.showToast(context, overriddenMessage ?: message ?: "Failed to start an activity", isLong)
        }

        override fun show(messageRes: Int) {
            show(context.getString(messageRes))
        }
    }

    interface ExceptionHolder {
        fun show(message: String? = null)
        fun show(messageRes: Int)
    }

    object App {

        private const val UNIQUE_REQUEST_CODE = 1001

        @JvmStatic
        @JvmOverloads
        fun restart(context: Context, beforeExit: (() -> Unit)? = null, scriptsAfterRestart: List<String>? = null) {
            val launchIntent = getLaunchIntent(context)
                ?: throw RuntimeException("Failed to create launch intent for AutoJs6")

            if (context is AppCompatActivity && Pref.isQuickRestartEnabled) {
                // When clicking the "Restart" button in the main drawer,
                // if "Quick Restart" is enabled, the Intent#startSafely method will be called.
                // zh-CN: 主页抽屉 "重启" 按钮点击时, 如果启用了 "快速重启", 则调用 Intent#startSafely 方法.
                launchIntent.startSafely(context, true)
            } else {
                // All scripts will call the scheduleAppLaunch method.
                // zh-CN: 脚本一律调用 scheduleAppLaunch 方法.
                scheduleAppLaunch(context, launchIntent)
            }

            if (context is AppCompatActivity && !Pref.isQuickRestartEnabled) {
                ViewUtils.showToast(context, R.string.text_app_restarting, true)
            }

            exit(context, beforeExit, scriptsAfterRestart)
        }

        private fun scheduleAppLaunch(context: Context, launchIntent: Intent) {
            val delay = Pref.getInt(R.string.key_scheduled_restart_delay, context.resources.getInteger(R.integer.scheduled_restart_start_delay_default_value)).let {
                TimeUnit.MILLISECONDS.toMillis(it.toLong())
            }
            val triggerAtMillis = SystemClock.elapsedRealtime() + delay
            when (Pref.isScheduledRestartPreferredWorkManager) {
                true -> runCatching {
                    scheduleRestartWorker(delay, context)
                }.onFailure {
                    it.printStackTrace()
                    scheduleRestartAlarm(context, launchIntent, triggerAtMillis)
                }
                else -> runCatching {
                    scheduleRestartAlarm(context, launchIntent, triggerAtMillis)
                }.onFailure {
                    it.printStackTrace()
                    scheduleRestartWorker(delay, context)
                }
            }
        }

        private fun scheduleRestartWorker(delay: Long, context: Context) {
            val request = OneTimeWorkRequestBuilder<RestartWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.NONE)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName = "work-task-restart-autojs6",
                existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                request = request,
            )
        }

        private fun scheduleRestartAlarm(context: Context, launchIntent: Intent, triggerAtMillis: Long) {
            val pendingIntent = PendingIntent.getActivity(
                /* context = */ context,
                /* requestCode = */ UNIQUE_REQUEST_CODE,
                /* intent = */ launchIntent,
                /* flags = */ PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent)
            am.setExactAndAllowWhileIdle(
                /* type = */ AlarmManager.ELAPSED_REALTIME,
                /* triggerAtMillis = */ triggerAtMillis,
                /* operation = */ pendingIntent,
            )
        }

        @JvmStatic
        @JvmOverloads
        fun exit(context: Context, beforeExit: (() -> Unit)? = null, scriptsAfterRestart: List<String>? = null) {
            FloatyWindowManger.hideCircularMenuAndSaveState()

            FloatyService.stopService()
            ScreenCapturerForegroundService.stopService()

            AutoJs.instance.scriptEngineService.stopAll()
            AutoJs.instance.clear()

            org.autojs.autojs.App.app.clear()

            WordDictionaryDatabase.getInstance(context.applicationContext).close()
            CharsDictionaryDatabase.getInstance(context.applicationContext).close()
            PhrasesDictionaryDatabase.getInstance(context.applicationContext).close()

            WrappedShizuku.onDestroy()

            beforeExit?.invoke()

            if (!scriptsAfterRestart.isNullOrEmpty()) {
                Pref.putStringSync(key(R.string.key_scripts_after_app_restart), scriptsAfterRestart.joinToString("\n") { it.trim() })
            }

            if (context is AppCompatActivity && Pref.isQuickRestartEnabled) {
                // When clicking the "Restart" button in the main drawer,
                // if "Quick Restart" is enabled, the finishAllAppTasksSafely method will not be called.
                // zh-CN: 主页抽屉 "重启" 按钮点击时, 如果启用了 "快速重启", 则不调用 finishAllAppTasksSafely 方法.
            } else {
                // All scripts will call the finishAllAppTasksSafely method.
                // zh-CN: 脚本一律调用 finishAllAppTasksSafely 方法.
                finishAllAppTasksSafely(context)
            }

            exitProcess(0)
        }

        private fun getLaunchIntent(context: Context): Intent? {
            val appCtx = context.applicationContext
            val pkgName = appCtx.packageName
            return appCtx.packageManager.getLaunchIntentForPackage(pkgName)?.apply {
                setAction("$pkgName.action.RESTART_UNIQUE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        private fun finishAllAppTasksSafely(context: Context) {
            // Use AppTask API to remove all task stacks of the current application (AutoJs6).
            // zh-CN: 使用 AppTask API 移除当前应用 (AutoJs6) 的所有任务栈.
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.appTasks?.forEach { it.runCatching { finishAndRemoveTask() } }
            }
        }

        @JvmStatic
        fun runAfterRestartIfNeeded(activity: AppCompatActivity) {
            val prefKey = key(R.string.key_scripts_after_app_restart)
            val prefValue = Pref.getStringOrNull(prefKey) ?: return
            val scripts = prefValue.split("\n").filter { it.isNotBlank() }

            val workingDir = WorkingDirectoryUtils.path
            val scriptsFailedToRun = mutableMapOf<String, String>()

            try {
                activity.runOnUiThread {
                    scripts.forEach { script ->
                        try {
                            val file = script.toScriptFile(workingDir)
                            when {
                                file.exists() -> AutoJs.instance.scriptEngineService.execute(
                                    /* source = */ ScriptFile(file.absolutePath).toSource(),
                                    /* config = */ ExecutionConfig(workingDir),
                                )
                                else -> throw FileNotFoundException(file.absolutePath)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            scriptsFailedToRun[script.toScriptFile(workingDir).absolutePath] = e.stackTraceToString()
                        }
                    }
                }
            } finally {
                Pref.remove(prefKey)
            }

            if (scriptsFailedToRun.isEmpty()) return

            val size = scriptsFailedToRun.size
            val summary = activity.resources.getQuantityString(R.plurals.text_items_total_sum_with_colon, size, size)
            val hint = activity.getString(R.string.text_click_item_to_show_details)
            MaterialDialog.Builder(activity)
                .title(R.string.error_failed_to_run_script)
                .content("$summary\n\n$hint")
                .items(scriptsFailedToRun.map { it.key })
                .itemsCallback { _, _, _, text ->
                    val stackTrace = scriptsFailedToRun[text]
                    val content = "$text\n\n$stackTrace"
                    MaterialDialog.Builder(activity)
                        .title(R.string.text_details)
                        .content(content)
                        .neutralText(R.string.dialog_button_copy)
                        .neutralColorRes(R.color.dialog_button_hint)
                        .onNeutral { d, _ ->
                            ClipboardUtils.setClip(activity, content)
                            ViewUtils.showSnack(d.view, R.string.text_already_copied_to_clip, false)
                        }
                        .positiveText(R.string.dialog_button_dismiss)
                        .onPositive { d, _ -> d.dismiss() }
                        .autoDismiss(false)
                        .show()
                }
                .choiceWidgetThemeColor()
                .positiveText(R.string.dialog_button_dismiss)
                .onPositive { dialog, _ -> dialog.dismiss() }
                .cancelable(false)
                .autoDismiss(false)
                .show()
        }

        private fun String.toScriptFile(workingDir: String): File {
            val file = when (this.endsWith(FileUtils.TYPE.JAVASCRIPT.extensionWithDot)) {
                true -> this
                else -> this + FileUtils.TYPE.JAVASCRIPT.extensionWithDot
            }.let { File(it) }
            return when {
                file.exists() -> file
                file.absolutePath.startsWith(workingDir.removeSuffix("/")) -> file
                file.path.startsWith("\$remote/") -> file
                else -> File(workingDir, file.path)
            }
        }

        class RestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
            override fun doWork(): Result {
                val context = applicationContext
                val launchIntent = getLaunchIntent(context) ?: return Result.failure()
                if (!launchIntent.startSafely(context)) return Result.failure()
                return Result.success()
            }
        }

    }

}
