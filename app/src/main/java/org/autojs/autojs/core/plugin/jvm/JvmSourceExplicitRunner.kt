package org.autojs.autojs.core.plugin.jvm

import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs6.BuildConfig
import java.io.File

class JvmSourceExperimentDisabledException : JvmSourceUserFacingException(
    JvmSourceUserExperiencePolicy.experimentDisabled(),
)

internal object JvmSourceExplicitAdmission {
    fun requireEnabled(featureEnabled: Boolean) {
        if (!featureEnabled) throw JvmSourceExperimentDisabledException()
    }
}

/** The only R1 API that can mint an execution-authorized JVM source. */
object JvmSourceExplicitRunner {
    private val authorization = Any()

    @JvmStatic
    @JvmOverloads
    fun runJvmSourceExplicit(
        file: File,
        listener: ScriptExecutionListener? = null,
        config: ExecutionConfig = ExecutionConfig(),
        overriddenFullPath: String? = null,
        originUri: String? = null,
    ): ScriptExecution = runJvmSourceExplicit(
        ScriptEngineService.getInstance(),
        file,
        listener,
        config,
        overriddenFullPath,
        originUri,
    )

    @JvmStatic
    @JvmOverloads
    fun runJvmSourceExplicit(
        service: ScriptEngineService,
        file: File,
        listener: ScriptExecutionListener? = null,
        config: ExecutionConfig = ExecutionConfig(),
        overriddenFullPath: String? = null,
        originUri: String? = null,
    ): ScriptExecution {
        // Reject synchronously before ScriptEngineService can enqueue a generic EngineNotFound failure.
        JvmSourceExplicitAdmission.requireEnabled(BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED)
        require(config.loopTimes == 1) { "R1 explicit JVM source execution does not support host-side loops" }
        require(config.interval == 0L) { "R1 explicit JVM source execution does not support a loop interval" }
        val source = createAuthorizedSource(file).apply {
            this.overriddenFullPath = overriddenFullPath
            this.originUri = originUri
        }
        return service.execute(source, listener, config)
    }

    /** Runs a canonical Java source tree whose selected entry remains `Main`. */
    @JvmStatic
    @JvmOverloads
    fun runJvmSourcePackageExplicit(
        sourceRoot: File,
        entryFile: File,
        listener: ScriptExecutionListener? = null,
        config: ExecutionConfig = ExecutionConfig(),
        overriddenFullPath: String? = null,
        originUri: String? = null,
    ): ScriptExecution = runJvmSourcePackageExplicit(
        ScriptEngineService.getInstance(),
        sourceRoot,
        entryFile,
        listener,
        config,
        overriddenFullPath,
        originUri,
    )

    @JvmStatic
    @JvmOverloads
    fun runJvmSourcePackageExplicit(
        service: ScriptEngineService,
        sourceRoot: File,
        entryFile: File,
        listener: ScriptExecutionListener? = null,
        config: ExecutionConfig = ExecutionConfig(),
        overriddenFullPath: String? = null,
        originUri: String? = null,
    ): ScriptExecution {
        JvmSourceExplicitAdmission.requireEnabled(BuildConfig.JVM_SOURCE_EXPERIMENTAL_ENABLED)
        require(config.loopTimes == 1) { "R1 explicit JVM source execution does not support host-side loops" }
        require(config.interval == 0L) { "R1 explicit JVM source execution does not support a loop interval" }
        val source = createAuthorizedSourcePackage(sourceRoot, entryFile).apply {
            this.overriddenFullPath = overriddenFullPath
            this.originUri = originUri
        }
        return service.execute(source, listener, config)
    }

    internal fun createAuthorizedSource(file: File): JvmFileSource {
        val source = ExplicitJvmFileSource(file, authorization)
        JvmSourceR1Policy.requireSupportedFileName(source.sourceKind, file.name)
        return source
    }

    internal fun createAuthorizedSourcePackage(sourceRoot: File, entryFile: File): JvmFileSource {
        val canonicalRoot = runCatching { sourceRoot.canonicalFile }
            .getOrElse { throw IllegalArgumentException("Java source package root cannot be resolved", it) }
        require(canonicalRoot.isDirectory) { "Java source package root must be an existing directory" }
        val canonicalEntry = runCatching { entryFile.canonicalFile }
            .getOrElse { throw IllegalArgumentException("Java source package entry cannot be resolved", it) }
        val prefix = canonicalRoot.path + File.separator
        require(canonicalEntry.path.startsWith(prefix) && canonicalEntry.isFile) {
            "Java source package entry must be a regular child of its source root"
        }
        val source = ExplicitJvmFileSource(
            canonicalEntry,
            authorization,
            JvmSourcePackageInput(canonicalRoot, canonicalEntry),
        )
        require(source.sourceKind == JvmSourceKind.JAVA) { "Source packages currently admit Java only" }
        JvmSourceR1Policy.requireSupportedFileName(source.sourceKind, canonicalEntry.name)
        return source
    }

    internal fun isAuthorized(source: JvmFileSource): Boolean =
        source is ExplicitJvmFileSource && source.hasAuthorization(authorization)

    internal fun packageInput(source: JvmFileSource): JvmSourcePackageInput? =
        (source as? ExplicitJvmFileSource)?.takeIf { it.hasAuthorization(authorization) }?.packageInput

    private class ExplicitJvmFileSource(
        file: File,
        private val authorization: Any,
        val packageInput: JvmSourcePackageInput? = null,
    ) : JvmFileSource(file) {
        fun hasAuthorization(expected: Any): Boolean = authorization === expected
    }

}

internal data class JvmSourcePackageInput(
    val sourceRoot: File,
    val entryFile: File,
)
