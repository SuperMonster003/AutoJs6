package org.autojs.autojs.model.script

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.jvm.JvmSourceExplicitRunner
import org.autojs.autojs.core.plugin.lua.AndroidLuaRuntimeFailureMessages
import org.autojs.autojs.core.plugin.python.PythonInteractiveInputAuthorization
import org.autojs.autojs.core.plugin.python.PythonLongRunningExecutionAuthorization
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.external.shortcut.ShortcutActivity
import org.autojs.autojs.external.shortcut.ScriptShortcutIntentFactory
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.AutoJs4ScriptSource
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.autojs4.AutoJs4RuntimePluginUi
import org.autojs.autojs.ui.jvm.JvmSourcePluginUi
import org.autojs.autojs.ui.python.PythonRuntimePluginUi
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
    val FILE_FILTER = FileFilter { file -> ScriptFileFilterPolicy.accepts(file, PythonScriptSource.isPreviewEnabled()) }

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
                        .putExtra(EXTRA_EXCEPTION_MESSAGE, AndroidLuaRuntimeFailureMessages.resolve(globalAppContext, e))
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
        val extras = ScriptIntents.putCapturedPythonContext(
            ScriptShortcutIntentFactory.create(scriptFile.path),
            scriptFile.path,
        )
        org.autojs.autojs.util.ShortcutUtils.requestPinShortcut(
            context,
            "autojs6.script.${java.util.UUID.randomUUID()}",
            extras.setClass(context, ShortcutActivity::class.java).setAction(Intent.ACTION_MAIN),
            scriptFile.simplifiedName,
            scriptFile.simplifiedName,
            R.drawable.ic_node_js_black,
        )
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
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val persisted = ScriptLaunchSourceFactory.capturePersistedLaunchContext(file.path)
        val launch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = file.path,
            sourceKind = persisted.sourceKind,
            projectRoot = persisted.projectRoot,
        )
        executeLaunch(context, launch, launch.createExecutionConfig().apply { nodeLaunchStartedAtMs = launchStartedAt })
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    /**
     * Runs [file] on a fresh background virtual display without touching its JavaScript source.
     * The display is created by the runtime when the script starts and released when it exits.
     *
     * zh-CN: 让 [file] 在新的后台虚拟屏上运行, 无需修改脚本源码. 虚拟屏在脚本启动时创建, 退出时释放.
     */
    @JvmStatic
    fun runOnVirtualScreen(context: Context, file: ScriptFile): ScriptExecution? = try {
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val persisted = ScriptLaunchSourceFactory.capturePersistedLaunchContext(file.path)
        val launch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = file.path,
            sourceKind = persisted.sourceKind,
            projectRoot = persisted.projectRoot,
        )
        val config = launch.createExecutionConfig().apply {
            nodeLaunchStartedAtMs = launchStartedAt
            virtualScreenMode = ExecutionConfig.VIRTUAL_SCREEN_MODE_ON
        }
        executeLaunch(context, launch, config)
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    /** Runs a user-selected file and may show the foreground Python plugin availability prompt. */
    @JvmStatic
    fun runInteractive(context: Context, file: ScriptFile): ScriptExecution? = try {
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val persisted = ScriptLaunchSourceFactory.capturePersistedLaunchContext(file.path)
        val launch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = file.path,
            sourceKind = persisted.sourceKind,
            projectRoot = persisted.projectRoot,
        )
        executeInteractiveLaunch(context, launch, launch.createExecutionConfig().apply { nodeLaunchStartedAtMs = launchStartedAt })
    } catch (e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(context, e.message, true)
        null
    }

    /**
     * Explicit "Run with AutoJs4" entry: the file or project folder is executed by the AutoJs4
     * Runtime plugin through the `autojs4` engine. Never used by the default run paths.
     *
     * zh-CN: 显式的 "使用 AutoJs4 运行" 入口: 文件或项目文件夹经 `autojs4` 引擎由 AutoJs4 Runtime 插件
     * 执行, 默认运行路径不会使用它.
     */
    @JvmStatic
    fun runWithAutoJs4(context: Context, target: File): ScriptExecution? {
        if (!AutoJs4RuntimePluginUi.ensureAvailableForInteractiveLaunch(context)) {
            return null
        }
        return try {
            val source = if (target.isDirectory) {
                AutoJs4ScriptSource.forProject(target)
                    ?: throw IllegalArgumentException(context.getString(R.string.error_autojs4_project_main_missing))
            } else {
                AutoJs4ScriptSource.forFile(target)
            }
            val config = ExecutionConfig(workingDirectory = source.workingDirectory)
            AutoJs.instance.scriptEngineService.execute(source, config)
        } catch (e: Exception) {
            e.printStackTrace()
            ViewUtils.showToast(context, e.message, true)
            null
        }
    }

    @JvmStatic
    fun run(context: Context, source: ScriptSource): ScriptExecution? {
        return run(context, source, null)
    }

    @JvmStatic
    fun run(context: Context, source: ScriptSource, workingDirectory: String?): ScriptExecution? = try {
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val launch = ScriptLaunchSourceFactory.createSourceLaunch(source, workingDirectory)
        executeLaunch(context, launch, launch.createExecutionConfig().apply { nodeLaunchStartedAtMs = launchStartedAt })
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

    /**
     * Foreground-only execution entry. Background callers must keep using [executeLaunch] so they
     * never show a dialog or start Plugin Center.
     */
    @JvmStatic
    fun executeInteractiveLaunch(
        context: Context,
        launch: ScriptLaunchSourceFactory.Launch,
    ): ScriptExecution? = executeInteractiveLaunch(context, launch, launch.createExecutionConfig())

    @JvmStatic
    fun executeInteractiveLaunch(
        context: Context,
        launch: ScriptLaunchSourceFactory.Launch,
        config: ExecutionConfig,
    ): ScriptExecution? {
        if (
            launch.source is JvmFileSource &&
            !JvmSourcePluginUi.ensureAvailableForInteractiveLaunch(context, launch.source.sourceKind)
        ) {
            return null
        }
        if (
            launch.engineName == PythonScriptSource.ENGINE &&
            !PythonRuntimePluginUi.ensureAvailableForInteractiveLaunch(context)
        ) {
            return null
        }
        if (launch.engineName == PythonScriptSource.ENGINE) {
            authorizeInteractivePythonExecution(config, context)
        }
        return executeLaunch(context, launch, config)
    }

    @JvmStatic
    fun authorizeInteractivePythonExecution(config: ExecutionConfig, context: Context) {
        PythonInteractiveInputAuthorization.authorizeForeground(config, context)
        PythonLongRunningExecutionAuthorization.authorizeForeground(config, context)
    }

    @JvmStatic
    @JvmOverloads
    fun runWithBroadcastSender(
        file: File,
        workingDirectory: String?,
        overriddenFullPath: String?,
        originUri: String? = null,
        semanticDisplayName: String? = null,
        originMimeType: String? = null,
    ): ScriptExecution? = runWithBroadcastSenderInternal(
        file = file,
        workingDirectory = workingDirectory,
        overriddenFullPath = overriddenFullPath,
        originUri = originUri,
        semanticDisplayName = semanticDisplayName,
        originMimeType = originMimeType,
        interactiveContext = null,
    )

    /** Editor/user-gesture counterpart which may show the Python plugin availability prompt. */
    @JvmStatic
    @JvmOverloads
    fun runWithBroadcastSenderInteractive(
        context: Context,
        file: File,
        workingDirectory: String?,
        overriddenFullPath: String?,
        originUri: String? = null,
        semanticDisplayName: String? = null,
        originMimeType: String? = null,
    ): ScriptExecution? = runWithBroadcastSenderInternal(
        file = file,
        workingDirectory = workingDirectory,
        overriddenFullPath = overriddenFullPath,
        originUri = originUri,
        semanticDisplayName = semanticDisplayName,
        originMimeType = originMimeType,
        interactiveContext = context,
    )

    private fun runWithBroadcastSenderInternal(
        file: File,
        workingDirectory: String?,
        overriddenFullPath: String?,
        originUri: String?,
        semanticDisplayName: String?,
        originMimeType: String?,
        interactiveContext: Context?,
    ): ScriptExecution? {
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val launch = ScriptLaunchSourceFactory.createSemanticFileLaunch(
            sourceFile = file,
            semanticPath = overriddenFullPath,
            semanticUri = originUri,
            semanticDisplayName = semanticDisplayName,
            semanticMimeType = originMimeType,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = file.parent ?: "",
        )
        if (
            interactiveContext != null &&
            launch.engineName == PythonScriptSource.ENGINE &&
            !PythonRuntimePluginUi.ensureAvailableForInteractiveLaunch(interactiveContext)
        ) {
            return null
        }
        val source = launch.source.apply {
            this.overriddenFullPath = overriddenFullPath
            this.originUri = originUri
        }
        if (
            interactiveContext != null &&
            source is JvmFileSource &&
            !JvmSourcePluginUi.ensureAvailableForInteractiveLaunch(interactiveContext, source.sourceKind)
        ) {
            return null
        }
        val config = launch.createExecutionConfig().apply { nodeLaunchStartedAtMs = launchStartedAt }
        if (interactiveContext != null && launch.engineName == PythonScriptSource.ENGINE) {
            authorizeInteractivePythonExecution(config, interactiveContext)
        }
        return if (source is JvmFileSource) {
            JvmSourceExplicitRunner.runJvmSourceExplicit(
                file = source.file,
                listener = BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER,
                config = config,
                overriddenFullPath = source.overriddenFullPath,
                originUri = source.originUri,
            )
        } else {
            AutoJs.instance.scriptEngineService.execute(
                source,
                BROADCAST_SENDER_SCRIPT_EXECUTION_LISTENER,
                config,
            )
        }
    }

    @JvmStatic
    fun runRepeatedly(scriptFile: ScriptFile, loopTimes: Int, delay: Long, interval: Long) {
        val persisted = ScriptLaunchSourceFactory.capturePersistedLaunchContext(scriptFile.path)
        val launch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = scriptFile.path,
            sourceKind = persisted.sourceKind,
            projectRoot = persisted.projectRoot,
        )
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
