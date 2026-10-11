package org.autojs.autojs.inrt.launch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs6.R
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import org.autojs.autojs.engine.NodeProjectRunner
import org.autojs.autojs.engine.NodeProjectLaunchDiagnostics
import org.autojs.autojs.engine.NodeExecutionModePolicy
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.inrt.LogActivity
import org.autojs.autojs.inrt.InrtToastVisibilityGuard
import org.autojs.autojs.inrt.Pref
import org.autojs.autojs.inrt.InrtStorageAccess
import org.autojs.autojs.inrt.autojs.AutoJs
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeEntryDirective
import org.autojs.autojs.script.NodeProjectLaunchDescriptor
import org.autojs.autojs.script.NodeProjectLaunchKind
import org.autojs.autojs.script.NodeProjectLaunchOptions
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs.script.NodeProjectRuntimeCompatibilityDescriptor
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.util.IntentUtils.startSafely
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Created by Stardust on Jan 24, 2018.
 */
open class AssetsProjectLauncher(private val mAssetsProjectDir: String, private val mActivity: Context) {

    private val mProjectDir: String = File(mActivity.filesDir, "project/").path
    private val mProjectConfig: ProjectConfig = ProjectConfig.fromAssets(mActivity, ProjectConfig.configFileOfDir(mAssetsProjectDir))
    private val mMainScriptFile: File
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private var mScriptExecution: ScriptExecution? = null

    // TODO by SuperMonster003 on Mar 9, 2026.
    //  ! 使其可配置.
    //  ! Make it configurable.
    private val finishDelay = 200L // 2000L

    init {
        prepare()
        mMainScriptFile = resolveMainScriptFile()
    }

    fun launch(activity: Activity) {
        // 如果需要隐藏日志界面, 则直接运行脚本
        if (!mProjectConfig.launchConfig.isLogsVisible || Pref.shouldHideLogs()) {
            runScript(activity)
        } else {
            // 如果不隐藏日志界面
            // 如果当前已经是日志界面则直接运行脚本
            if (activity is LogActivity) {
                runScript(activity, finishActivity = false)
            } else {
                createMainScriptLaunch()
                // 否则显示日志界面并在日志界面中运行脚本
                mHandler.post {
                    Intent(mActivity, LogActivity::class.java)
                        .putExtra(LogActivity.EXTRA_LAUNCH_SCRIPT, true)
                        .startSafely(activity)
                    activity.finish()
                }
            }
        }
    }

    fun launchOnBoot() {
        runScript(null)
    }

    private fun runScript(activity: Activity?, finishActivity: Boolean = true) {
        if (mProjectConfig.launchConfig.requiresSharedStorage && !InrtStorageAccess.has(mActivity)) {
            val message = InrtStorageAccess.explanation(mActivity)
            Log.w(TAG, message)
            AutoJs.instance.globalConsole.error(message)
            Pref.updateLauncherVisibility(mProjectConfig.launchConfig)
            return
        }
        if (mScriptExecution != null && mScriptExecution!!.engine != null &&
            !mScriptExecution!!.engine.isDestroyed
        ) {
            return
        }
        try {
            val launch = createMainScriptLaunch()
            val source = launch.source as JavaScriptSource
            val config = launch.createExecutionConfig()
            if (source.engineName == JavaScriptSource.ENGINE_NODEJS &&
                !NodeJsRuntimePluginHost.isPackagedRuntimeAvailable(mActivity)) {
                showNodeRuntimeRequired(activity)
                return
            }
            if (source.engineName == JavaScriptSource.ENGINE_NODEJS && activity != null) {
                NodeJsRuntimePluginHost.activatePackagedRuntimeIfNeeded(activity)
            }
            check(source.engineName != JavaScriptSource.ENGINE_BUN) {
                "Packaged apps do not support the external Bun Runtime plugin."
            }
            check(source.engineName != JavaScriptSource.ENGINE_QUICKJS) {
                "Packaged apps do not support the external QuickJS Runtime plugin."
            }
            if (source.executionMode and JavaScriptSource.EXECUTION_MODE_UI != 0) {
                config.intentFlags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
            } else {
                if (source.engineName == JavaScriptSource.ENGINE_NODEJS) {
                    InrtToastVisibilityGuard.holdForPotentialNodeBridgeToast()
                }
                if (finishActivity) finishHostActivity(activity)
            }
            mScriptExecution = AutoJs.instance.scriptEngineService.execute(source, config)
        } catch (e: Exception) {
            AutoJs.instance.globalConsole.error(e)
        }
    }

    private fun showNodeRuntimeRequired(activity: Activity?) {
        val message = mActivity.getString(R.string.text_nodejs_runtime_required_for_packaged_app)
        AutoJs.instance.globalConsole.error(message)
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        MaterialDialog.Builder(activity)
            .title("AutoJs6 Node.js Runtime")
            .content(message)
            .positiveText(R.string.text_download)
            .negativeText(R.string.text_exit)
            .onPositive { _, _ ->
                Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases/latest"
                )).startSafely(activity)
            }
            .onNegative { _, _ -> activity.finish() }
            .show()
    }

    private fun finishHostActivity(activity: Activity?) {
        val host = activity ?: return
        val shouldDelayFinish = Pref.shouldHideLogs() || !mProjectConfig.launchConfig.isLogsVisible
        if (!shouldDelayFinish) {
            host.finish()
            return
        }
        mHandler.postDelayed({
            finishHostActivityWhenReady(host)
        }, finishDelay)
    }

    private fun finishHostActivityWhenReady(host: Activity) {
        if (host.isFinishing || host.isDestroyed) {
            return
        }
        val remainingToastHoldMs = InrtToastVisibilityGuard.remainingHoldMs()
        if (remainingToastHoldMs > 0L) {
            mHandler.postDelayed({ finishHostActivityWhenReady(host) }, remainingToastHoldMs)
            return
        }
        host.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            host.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            host.overridePendingTransition(0, 0)
        }
    }

    private fun prepare() {
        val projectConfig = ProjectConfig.fromProjectDir(mProjectDir)
        if (projectConfig != null &&
            TextUtils.equals(projectConfig.buildInfo.buildId, mProjectConfig.buildInfo.buildId) &&
            TextUtils.equals(projectConfig.mainScriptFileName, mProjectConfig.mainScriptFileName) &&
            TextUtils.equals(projectConfig.projectType, mProjectConfig.projectType)
        ) {
            initKey(projectConfig)
            return
        }
        initKey(mProjectConfig)
        PFiles.deleteRecursively(File(mProjectDir))
        try {
            PFiles.copyAssetDir(mActivity.assets, mAssetsProjectDir, mProjectDir, null)
            copyKnownNodeDescriptorAssets()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun copyKnownNodeDescriptorAssets() {
        val descriptorNames = (
                NodeProjectLaunchDescriptor.DESCRIPTOR_FILE_NAMES +
                        NodeProjectRuntimeCompatibilityDescriptor.DESCRIPTOR_FILE_NAMES
        ).distinct()
        descriptorNames.forEach { descriptorName ->
            val assetPath = PFiles.join(mAssetsProjectDir, descriptorName)
            try {
                mActivity.assets.open(assetPath).use { input ->
                    File(mProjectDir, descriptorName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied packaged Node descriptor asset: $descriptorName")
            } catch (_: FileNotFoundException) {
                // Optional descriptor assets are only copied when bundled.
            }
        }
    }

    private fun resolveMainScriptFile(): File {
        val projectRoot = File(mProjectDir)
        return try {
            NodeProjectRunner.resolveProjectEntryFileIfNodeProject(projectRoot)
                ?.also { entryFile ->
                    Log.d(TAG, "$INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER entry=${entryFile.path}")
                }
                ?: File(mProjectDir, mProjectConfig.mainScriptFileName)
        } catch (e: Exception) {
            logNodeProjectLaunchFailure(
                stage = "entry",
                projectRoot = projectRoot,
                entryFile = null,
                launchSource = null,
                request = null,
                error = e,
            )
            throw e
        }
    }

    private fun createMainScriptSource(): JavaScriptFileSource {
        return createMainScriptLaunch().source as JavaScriptFileSource
    }

    private fun createMainScriptLaunch(): ScriptLaunchSourceFactory.Launch {
        val projectRoot = File(mProjectDir)
        if (!NodeProjectRunner.isNodeProjectRoot(projectRoot)) {
            return ScriptLaunchSourceFactory.createPathLaunch(
                path = mMainScriptFile.path,
                workingDirectory = mProjectDir,
                defaultWorkingDirectory = mProjectDir,
            )
        }
        val launchOptions = try {
            NodeProjectRuntimeCompatibilityDescriptor.requireCompatibleAssetOrDefault(
                assets = mActivity.assets,
                assetsProjectDir = mAssetsProjectDir,
            )
            NodeProjectLaunchDescriptor
                .readOptionsOrDefault(projectRoot)
                .withNodeConfigDefaults(mProjectConfig.nodeConfig)
        } catch (e: Exception) {
            logNodeProjectLaunchFailure("descriptor", projectRoot, mMainScriptFile, null, null, e)
            throw e
        }
        val launch = try {
            ScriptLaunchSourceFactory.createNodeProjectLaunch(
                projectRoot = projectRoot,
                entryFile = mMainScriptFile,
                name = "main",
                launchOptions = launchOptions,
                defaultWorkingDirectory = mProjectDir,
            )
        } catch (e: Exception) {
            logNodeProjectLaunchFailure("launch_source", projectRoot, mMainScriptFile, null, null, e)
            throw e
        }
        val launchSource = launch.source as? NodeProjectLaunchSource
        val request = try {
            launch.buildNodeProjectRequestOrNull()
        } catch (e: Exception) {
            logNodeProjectLaunchFailure("project_request", projectRoot, mMainScriptFile, launchSource, null, e)
            throw e
        }
        try {
            request?.let { NodeProjectRunner.resolveProjectLaunchPlanWithDescriptor(it) }
        } catch (e: Exception) {
            logNodeProjectLaunchFailure("project_request", projectRoot, mMainScriptFile, launchSource, request, e)
            throw e
        }
        val requestLaunchKind = request
            ?.let { NodeProjectLaunchKind.from(it.scriptName, it.binName).descriptorValue }
            .orEmpty()
        val packageDirProjectRoot = request?.packageDir?.canonicalFile == projectRoot.canonicalFile
        val workingDirectoryProjectRoot = request?.workingDirectory?.canonicalFile == projectRoot.canonicalFile
        Log.d(
            TAG,
            "$INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER root=${projectRoot.path} entry=${mMainScriptFile.path} " +
                    "requestReady=${request != null} " +
                    "launchKind=${launchSource?.nodeProjectLaunchKind?.descriptorValue.orEmpty()} " +
                    "requestLaunchKind=$requestLaunchKind " +
                    "executionMode=${launchSource?.nodeProjectExecutionMode.orEmpty()} " +
                    "entryDirective=${launchSource?.nodeProjectEntryDirective.orEmpty()} " +
                    "longRunning=${NodeExecutionModePolicy.isLongRunningRequest(launchSource?.nodeProjectExecutionMode)} " +
                    "processPoolSlot=${launchSource?.nodeProjectProcessPoolSlot.orEmpty()} " +
                    "processPoolSlotSource=${launchSource?.nodeProjectProcessPoolSlotSource.orEmpty()} " +
                    "packageDir=${request?.packageDir?.path.orEmpty()} " +
                    "packageDirProjectRoot=$packageDirProjectRoot " +
                    "workingDirectory=${request?.workingDirectory?.path.orEmpty()} " +
                    "workingDirectoryProjectRoot=$workingDirectoryProjectRoot " +
                    "argvCount=${request?.argv?.size ?: 0} " +
                    "script=${launchSource?.nodeProjectScriptName.orEmpty()} " +
                    "bin=${launchSource?.nodeProjectBinName.orEmpty()} " +
                    "envKeys=${launchSource?.nodeProjectEnv?.keys?.sorted()?.joinToString(prefix = "[", postfix = "]").orEmpty()}",
        )
        return launch
    }

    private fun NodeProjectLaunchOptions.withNodeConfigDefaults(
        nodeConfig: ProjectConfig.NodeConfig?,
    ): NodeProjectLaunchOptions {
        nodeConfig ?: return this
        val descriptorMode = executionMode?.takeIf { it.isNotBlank() }
        val projectMode = nodeConfig.executionMode?.takeIf { it.isNotBlank() }
        val descriptorDirective = NodeEntryDirective.normalize(entryDirective)
        val projectDirective = NodeEntryDirective.normalize(nodeConfig.entryDirective)
        if (
            descriptorMode != null &&
            projectMode != null &&
            NodeExecutionModePolicy.normalize(descriptorMode) != NodeExecutionModePolicy.normalize(projectMode)
        ) {
            throw IllegalArgumentException(
                "Packaged Node executionMode mismatch: descriptor=$descriptorMode project=${nodeConfig.executionMode}",
            )
        }
        if (descriptorDirective != null && projectDirective != null && descriptorDirective != projectDirective) {
            throw IllegalArgumentException(
                "Packaged Node entryDirective mismatch: descriptor=$descriptorDirective project=${nodeConfig.entryDirective}",
            )
        }
        return copy(
            timeoutMs = timeoutMs ?: nodeConfig.timeoutMs?.takeIf { it > 0L },
            executionMode = descriptorMode ?: projectMode,
            entryDirective = descriptorDirective ?: projectDirective,
        )
    }

    private fun logNodeProjectLaunchFailure(
        stage: String,
        projectRoot: File,
        entryFile: File?,
        launchSource: NodeProjectLaunchSource?,
        request: NodeProjectRunner.ProjectRequest?,
        error: Throwable,
    ) {
        val marker = NodeProjectLaunchDiagnostics.launchFailureMarker(
            stage = stage,
            projectRoot = projectRoot,
            entryFile = entryFile,
            launchSource = launchSource,
            request = request,
            error = error,
        )
        val message = if (stage == "entry") {
            "${NodeProjectLaunchDiagnostics.INRT_PROJECT_ENTRY_RESOLUTION_FAILURE_MARKER} $marker"
        } else {
            marker
        }
        Log.e(
            TAG,
            message,
            error,
        )
    }

    private fun initKey(projectConfig: ProjectConfig) {
        val key = MD5.md5(projectConfig.packageName + projectConfig.versionName + projectConfig.mainScriptFileName)
        val vec = MD5.md5(projectConfig.buildInfo.buildId + projectConfig.name).substring(0, 16)
        ScriptEncryption.configure(key, vec)
    }

    private companion object {
        private const val TAG = "AssetsProjectLauncher"
        private const val INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER = "autojs6.inrt.node_project_runner.v1"
        private const val INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER =
            "autojs6.inrt.node_project_launch_source.v2"
    }
}
