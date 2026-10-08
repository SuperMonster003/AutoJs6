package org.autojs.autojs.external

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.model.script.PathChecker
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.PersistedScriptLaunchPolicy
import org.autojs.autojs.script.PythonProjectLaunchError
import org.autojs.autojs.script.PythonProjectLaunchException
import org.autojs.autojs.script.PythonProjectLaunchPolicy
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * Created by Stardust on Apr 1, 2017.
 * Modified by SuperMonster003 as of Mar 25, 2023.
 * Transformed by SuperMonster003 on Mar 25, 2023.
 */
object ScriptIntents {

    const val EXTRA_KEY_PATH = "path"
    const val EXTRA_KEY_PRE_EXECUTE_SCRIPT = "script"
    const val EXTRA_KEY_LOOP_TIMES = "loop"
    const val EXTRA_KEY_LOOP_INTERVAL = "interval"
    const val EXTRA_KEY_DELAY = "delay"
    const val EXTRA_KEY_JSON = "json"
    const val EXTRA_KEY_SOURCE_KIND = "source_kind"
    const val EXTRA_KEY_PROJECT_ROOT = "project_root"

    data class PreparedLaunch(
        val launch: ScriptLaunchSourceFactory.Launch,
        val config: ExecutionConfig,
    )

    @JvmStatic
    fun isTaskerJsonObjectValid(json: JSONObject) = json.has(EXTRA_KEY_PATH) || json.has(EXTRA_KEY_PRE_EXECUTE_SCRIPT)

    @JvmStatic
    fun handleIntent(context: Context?, intent: Intent) {
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        prepareLaunch(context, intent)?.let {
            it.config.nodeLaunchStartedAtMs = launchStartedAt
            Scripts.executeLaunch(context ?: GlobalAppContext.get(), it.launch, it.config)
        }
    }

    @JvmStatic
    @VisibleForTesting
    @JvmOverloads
    fun prepareLaunch(context: Context?, intent: Intent, argumentIntent: Intent = intent): PreparedLaunch? {
        enforceExternalUriRequestShape(intent)
        val json = intent.readLaunchJsonOrNull()
        var path = getPath(intent)
        var script = intent.getStringExtra(EXTRA_KEY_PRE_EXECUTE_SCRIPT)
        var sourceKind = intent.getStringExtra(EXTRA_KEY_SOURCE_KIND)
        var projectRoot = intent.getStringExtra(EXTRA_KEY_PROJECT_ROOT)

        json?.let {
            path = it.optIntentString(EXTRA_KEY_PATH) ?: path
            script = it.optIntentString(EXTRA_KEY_PRE_EXECUTE_SCRIPT) ?: script
            sourceKind = it.optIntentString(EXTRA_KEY_SOURCE_KIND) ?: sourceKind
            projectRoot = it.optIntentString(EXTRA_KEY_PROJECT_ROOT) ?: projectRoot
        }
        enforceDefaultOffPythonIntentBoundary(context, intent, path)

        val persistedContextDeclared = intent.hasExtra(EXTRA_KEY_SOURCE_KIND) ||
            intent.hasExtra(EXTRA_KEY_PROJECT_ROOT) ||
            json?.has(EXTRA_KEY_SOURCE_KIND) == true ||
            json?.has(EXTRA_KEY_PROJECT_ROOT) == true
        if (persistedContextDeclared && (sourceKind == null || path == null)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
        }
        if (!persistedContextDeclared) {
            rejectImplicitPythonProjectDescriptor(path)
        }
        if (!persistedContextDeclared || sourceKind == PersistedScriptLaunchPolicy.PYTHON_FILE_V1) {
            rejectUnsupportedPythonPreExecute(path, script)
        }
        val launch = when {
            path == null && script != null -> {
                ScriptLaunchSourceFactory.createScriptLaunch(script, WorkingDirectoryUtils.path)
            }
            path != null && persistedContextDeclared -> {
                ScriptLaunchSourceFactory.createPersistedPathLaunch(
                    path = path,
                    sourceKind = sourceKind,
                    projectRoot = projectRoot,
                    preExecuteScript = script,
                )
            }
            path != null && PathChecker(context).checkAndToastError(path) -> {
                ScriptLaunchSourceFactory.createPersistedPathLaunch(
                    path = path,
                    sourceKind = sourceKind,
                    projectRoot = projectRoot,
                    preExecuteScript = script,
                )
            }
            else -> null
        }
        return launch?.let {
            PreparedLaunch(
                launch = it,
                config = it.createIntentExecutionConfig(argumentIntent),
            )
        }
    }

    private fun getPath(intent: Intent): String? {
        val uri = intent.data
        val localUriPath = when {
            uri == null -> null
            uri.scheme.isNullOrBlank() -> uri.path
            uri.scheme.equals("file", ignoreCase = true) -> uri.path
            else -> null
        }
        return localUriPath ?: intent.getStringExtra(EXTRA_KEY_PATH)
    }

    @JvmStatic
    fun putCapturedPythonContext(intent: Intent, path: String): Intent {
        val context = ScriptLaunchSourceFactory.capturePersistedLaunchContext(path)
        context.sourceKind?.let { intent.putExtra(EXTRA_KEY_SOURCE_KIND, it) }
        context.projectRoot?.let { intent.putExtra(EXTRA_KEY_PROJECT_ROOT, it) }
        return intent
    }

    /** Rejects URI request shapes which could hide a different local launch request. */
    @JvmStatic
    fun enforceExternalUriRequestShape(intent: Intent) {
        val uri = intent.data ?: return
        validateExternalUriRequestShape(
            scheme = uri.scheme,
            hasPathExtra = intent.hasExtra(EXTRA_KEY_PATH),
            hasJsonExtra = intent.hasExtra(EXTRA_KEY_JSON),
            hasPreExecuteExtra = intent.hasExtra(EXTRA_KEY_PRE_EXECUTE_SCRIPT),
            hasSourceKindExtra = intent.hasExtra(EXTRA_KEY_SOURCE_KIND),
            hasProjectRootExtra = intent.hasExtra(EXTRA_KEY_PROJECT_ROOT),
        )
    }

    @VisibleForTesting
    internal fun validateExternalUriRequestShape(
        scheme: String?,
        hasPathExtra: Boolean,
        hasJsonExtra: Boolean,
        hasPreExecuteExtra: Boolean,
        hasSourceKindExtra: Boolean,
        hasProjectRootExtra: Boolean,
    ) {
        val normalizedScheme = scheme?.lowercase()
        if (normalizedScheme != "file" && normalizedScheme != "content") {
            throw ScriptIntentBoundaryException(ScriptIntentBoundaryError.URI_SCHEME_UNSUPPORTED)
        }
        if (hasPathExtra || hasJsonExtra) {
            throw ScriptIntentBoundaryException(ScriptIntentBoundaryError.URI_PAYLOAD_CONFLICT)
        }
        if (normalizedScheme == "content" && (hasSourceKindExtra || hasProjectRootExtra)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
        }
        if (normalizedScheme == "content" && hasPreExecuteExtra) {
            throw ScriptIntentBoundaryException(ScriptIntentBoundaryError.URI_PAYLOAD_CONFLICT)
        }
    }

    /** Python content documents remain import-only in R4. */
    @JvmStatic
    fun enforceContentPythonExecutionBoundary(
        displayName: String?,
        uriPath: String?,
        originPath: String?,
        explicitMimeType: String?,
        providerMimeType: String?,
    ) {
        var extension = ScriptLaunchSourceFactory.resolvePythonImportExtension(
            displayName,
            uriPath,
            explicitMimeType,
            providerMimeType,
        )
        if (extension == null && originPath != null) {
            extension = ScriptLaunchSourceFactory.resolvePythonImportExtension(
                File(originPath).name,
                originPath,
                explicitMimeType,
                providerMimeType,
            )
        }
        if (extension != null) {
            ScriptLaunchSourceFactory.enforcePythonMimeBoundary("text/x-python")
        }
    }

    /** A standalone import cannot preserve a project descriptor's workspace. */
    @JvmStatic
    fun enforceSingleFileImportBoundary(displayName: String?, uriPath: String?) {
        if (listOfNotNull(displayName, uriPath).any(::isProjectDescriptorName)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_REQUIRED)
        }
    }

    private fun enforceDefaultOffPythonIntentBoundary(
        context: Context?,
        intent: Intent,
        finalPath: String?,
    ) {
        val uri = intent.data
        val displayName = if (context != null && uri != null) {
            IntentUtils.getFileName(context, uri)
        } else {
            null
        }
        if (uri != null && uri.scheme.equals("content", ignoreCase = true)) {
            val providerMimeType = if (context != null) {
                context.contentResolver.getType(uri)
            } else {
                null
            }
            enforceContentPythonExecutionBoundary(
                displayName = displayName,
                uriPath = uri.path,
                originPath = null,
                explicitMimeType = intent.type,
                providerMimeType = providerMimeType,
            )
            return
        }
        ScriptLaunchSourceFactory.enforcePythonExternalUriBoundary(
            scheme = uri?.scheme ?: finalPath?.let { "file" },
            uriPath = uri?.path ?: finalPath,
            uriText = uri?.toString(),
            displayName = displayName ?: finalPath?.let { File(it).name },
            explicitMimeType = intent.type,
            providerMimeType = null,
        )
    }

    private fun rejectImplicitPythonProjectDescriptor(path: String?) {
        val descriptor = path?.let(::File)?.let { file ->
            runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        } ?: return
        if (!isProjectDescriptorName(descriptor.name)) return
        val root = descriptor.parentFile ?: return
        val admission = PythonProjectLaunchPolicy.inspect(root, legacyMain = null) ?: return
        check(admission.projectRoot.isDirectory) { "Python project admission is not normalized" }
        throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_REQUIRED)
    }

    private fun rejectUnsupportedPythonPreExecute(path: String?, script: String?) {
        if (path == null || script == null) return
        val pythonExtension = ScriptLaunchSourceFactory.resolvePythonImportExtension(
            File(path).name,
            path,
            null,
            null,
        )
        if (pythonExtension != null) {
            throw ScriptIntentBoundaryException(ScriptIntentBoundaryError.PYTHON_PRE_EXECUTE_UNSUPPORTED)
        }
    }

    private fun Intent.readLaunchJsonOrNull(): JSONObject? {
        if (!hasExtra(EXTRA_KEY_JSON)) return null
        return try {
            JSONObject(getStringExtra(EXTRA_KEY_JSON)!!)
        } catch (e: JSONException) {
            null
        }
    }

    private fun isProjectDescriptorName(location: String): Boolean {
        val name = location.replace('\\', '/').substringAfterLast('/')
        return name.equals("project.json", ignoreCase = true)
    }

    private fun ScriptLaunchSourceFactory.Launch.createIntentExecutionConfig(intent: Intent): ExecutionConfig {
        return createExecutionConfig(ExecutionConfig()).apply {
            delay = intent.getLongExtra(EXTRA_KEY_DELAY, 0)
            loopTimes = intent.getIntExtra(EXTRA_KEY_LOOP_TIMES, 1)
            interval = intent.getLongExtra(EXTRA_KEY_LOOP_INTERVAL, 0)
            if (shouldExposeAndroidIntentArgument(engineName)) {
                setArgument("intent", intent)
            }
        }
    }

    @VisibleForTesting
    internal fun shouldExposeAndroidIntentArgument(engineName: String): Boolean =
        engineName != LuaScriptSource.ENGINE && engineName != JvmFileSource.ENGINE

    private fun JSONObject.optIntentString(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        require(value is String) {
            "Script intent JSON field must be a string: $key"
        }
        return value
    }

}

internal enum class ScriptIntentBoundaryError(
    val stableCode: String,
    val stableMessage: String,
) {
    URI_SCHEME_UNSUPPORTED(
        "SCRIPT_INTENT_URI_SCHEME_UNSUPPORTED",
        "Only file and content script URIs are supported",
    ),
    URI_PAYLOAD_CONFLICT(
        "SCRIPT_INTENT_URI_PAYLOAD_CONFLICT",
        "A script URI cannot be combined with a different path or content launch payload",
    ),
    PYTHON_PRE_EXECUTE_UNSUPPORTED(
        "PYTHON_PRE_EXECUTE_UNSUPPORTED",
        "Python launch does not support a JavaScript pre-execute script",
    ),
}

internal class ScriptIntentBoundaryException(
    val error: ScriptIntentBoundaryError,
) : IllegalArgumentException("${error.stableCode}: ${error.stableMessage}")
