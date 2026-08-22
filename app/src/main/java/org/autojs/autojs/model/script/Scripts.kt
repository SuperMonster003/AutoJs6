package org.autojs.autojs.model.script

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.external.shortcut.Shortcut
import org.autojs.autojs.external.shortcut.ShortcutActivity
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.core.plugin.jvm.JvmSourceExplicitRunner
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.RhinoException
import java.io.File
import java.io.FileFilter

/**
 * Created by Stardust on May 3, 2017.
 */
object Scripts {

    private val globalAppContext: Context = GlobalAppContext.get()

    const val ACTION_ON_EXECUTION_FINISHED = "ACTION_ON_EXECUTION_FINISHED"
    const val EXTRA_EXCEPTION_MESSAGE = "message"
    const val EXTRA_EXCEPTION_LINE_NUMBER = "lineNumber"
    const val EXTRA_EXCEPTION_COLUMN_NUMBER = "columnNumber"

    @JvmField
    val FILE_FILTER = FileFilter { file ->
        file.isDirectory ||
        JavaScriptSource.isJavaScriptFileName(file.name) ||
        file.name.endsWith(FileUtils.TYPE.AUTO.extension, true)
    }

    private val BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER = object : SimpleScriptExecutionListener() {

        override fun onSuccess(execution: ScriptExecution, result: Any?) {
            globalAppContext.sendBroadcast(newExecutionFinishedIntent())
        }

        override fun onException(execution: ScriptExecution, e: Throwable) {
            val rhinoException = getRhinoException(e)
            var line = -1
            var col = 0
            if (rhinoException != null) {
                line = rhinoException.lineNumber()
                col = rhinoException.columnNumber()
            }
            if (ScriptInterruptedException.causedByInterrupt(e)) {
                globalAppContext.sendBroadcast(
                    newExecutionFinishedIntent()
                        .putExtra(EXTRA_EXCEPTION_LINE_NUMBER, line)
                        .putExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, col)
                )
            } else {
                globalAppContext.sendBroadcast(
                    newExecutionFinishedIntent()
                        .putExtra(EXTRA_EXCEPTION_MESSAGE, e.message)
                        .putExtra(EXTRA_EXCEPTION_LINE_NUMBER, line)
                        .putExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, col)
                )
            }
        }

    }

    private fun newExecutionFinishedIntent(): Intent {
        return Intent(ACTION_ON_EXECUTION_FINISHED).setPackage(globalAppContext.packageName)
    }

    @JvmStatic
    fun openByOtherApps(uri: Uri) {
        IntentUtils.viewFile(
            globalAppContext,
            uri,
            Mime.TEXT_PLAIN,
            AppFileProvider.AUTHORITY,
            ToastExceptionHolder(globalAppContext),
        )
    }

    @JvmStatic
    fun openByOtherApps(file: File) {
        openByOtherApps(Uri.fromFile(file))
    }

    fun createShortcut(context: Context, scriptFile: ScriptFile) {
        Shortcut(context).name(scriptFile.simplifiedName)
            .targetClass(ShortcutActivity::class.java)
            .iconRes(R.drawable.ic_node_js_black)
            .extras(Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, scriptFile.path))
            .send()
    }

    @JvmStatic
    fun edit(context: Context, file: ScriptFile) {
        EditActivity.editFile(context, file.simplifiedName, file.path, true)
    }

    @JvmStatic
    fun edit(context: Context, path: String) {
        edit(context, ScriptFile(path))
    }

    @JvmStatic
    fun run(context: Context, file: ScriptFile): ScriptExecution? = try {
        val launch = ScriptLaunchSourceFactory.createFileLaunch(file)
        executeLaunch(context, launch)
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    @JvmStatic
    fun run(context: Context, source: ScriptSource): ScriptExecution? {
        return run(context, source, null)
    }

    @JvmStatic
    fun run(context: Context, source: ScriptSource, workingDirectory: String?): ScriptExecution? = try {
        val launch = ScriptLaunchSourceFactory.createSourceLaunch(source, workingDirectory)
        executeLaunch(context, launch)
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    @JvmStatic
    fun createLaunchExecutionConfig(
        launch: ScriptLaunchSourceFactory.Launch,
        base: ExecutionConfig,
    ): ExecutionConfig = launch.createExecutionConfig(base)

    @JvmStatic
    fun executeLaunch(
        context: Context,
        launch: ScriptLaunchSourceFactory.Launch,
    ): ScriptExecution? = executeLaunch(context, launch, launch.createExecutionConfig())

    @JvmStatic
    fun executeLaunch(
        context: Context,
        launch: ScriptLaunchSourceFactory.Launch,
        config: ExecutionConfig,
    ): ScriptExecution? = try {
        val source = launch.source
        if (source is JvmFileSource) {
            // Explorer, external intents, shortcuts and tasks all funnel through here; JVM sources
            // must be re-minted by the explicit runner because execution is token-gated.
            // zh-CN: 文件列表/外部 intent/快捷方式/任务均汇聚于此; JVM 源码执行受 token 门控, 需经显式 runner 重新签发.
            JvmSourceExplicitRunner.runJvmSourceExplicit(
                file = source.file,
                config = config,
                overriddenFullPath = source.overriddenFullPath,
                originUri = source.originUri,
            )
        } else {
            AutoJs.instance.scriptEngineService.execute(source, config)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    @JvmStatic
    @JvmOverloads
    fun runWithBroadcastSender(file: File, workingDirectory: String?, overriddenFullPath: String?, originUri: String? = null): ScriptExecution? {
        if (JvmSourceKind.fromFileName(file.name) == JvmSourceKind.JAVA) {
            // JVM source execution is token-gated: only the explicit runner can mint an
            // execution-authorized source, so the editor/explorer run path must go through it.
            // zh-CN: JVM 源码执行受 token 门控: 只有显式 runner 能签发授权, 编辑器/文件列表运行必须经由它.
            return JvmSourceExplicitRunner.runJvmSourceExplicit(
                file = file,
                listener = BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER,
                config = ExecutionConfig(workingDirectory = workingDirectory ?: file.parent ?: ""),
                overriddenFullPath = overriddenFullPath,
                originUri = originUri,
            )
        }
        val launch = ScriptLaunchSourceFactory.createFileLaunch(
            file = file,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = file.parent ?: "",
        )
        return AutoJs.instance.scriptEngineService.execute(
            launch.source.apply {
                this.overriddenFullPath = overriddenFullPath
                this.originUri = originUri
            },
            BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER,
            launch.createExecutionConfig()
        )
    }

    @JvmStatic
    fun runRepeatedly(scriptFile: ScriptFile, loopTimes: Int, delay: Long, interval: Long) {
        val launch = ScriptLaunchSourceFactory.createFileLaunch(scriptFile)
        executeLaunch(
            globalAppContext,
            launch,
            launch.createExecutionConfig(
                ExecutionConfig(
                    delay = delay,
                    loopTimes = loopTimes,
                    interval = interval,
                )
            )
        )
    }

    fun getRhinoException(throwable: Throwable?): RhinoException? {
        var e = throwable
        while (e != null) {
            if (e is RhinoException) {
                return e
            }
            e = e.cause
        }
        return null
    }

    @JvmStatic
    fun send(context: Context, file: ScriptFile) {
        IntentUtils.sendFile(
            context,
            file.path,
            AppFileProvider.AUTHORITY,
            ToastExceptionHolder(context),
        )
    }

}
