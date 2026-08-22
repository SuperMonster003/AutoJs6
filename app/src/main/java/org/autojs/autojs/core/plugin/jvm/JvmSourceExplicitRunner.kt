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

    internal fun createAuthorizedSource(file: File): JvmFileSource {
        JvmSourceR1JavaPolicy.requireSupportedFileName(file.name)
        return ExplicitJvmFileSource(file, authorization).also { source ->
            require(source.sourceKind == JvmSourceKind.JAVA) { "R1 supports Java source only" }
        }
    }

    internal fun isAuthorized(source: JvmFileSource): Boolean =
        source is ExplicitJvmFileSource && source.hasAuthorization(authorization)

    private class ExplicitJvmFileSource(
        file: File,
        private val authorization: Any,
    ) : JvmFileSource(file) {
        fun hasAuthorization(expected: Any): Boolean = authorization === expected
    }

}
