package org.autojs.autojs.core.plugin.r8

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Verified R8 DEX ZIP handed to the runtime class-loader boundary. */
internal class R8CompilerLoadableDexZip private constructor(
    private val artifact: R8CompilerStoredArtifact,
    val sizeBytes: Long,
    val sha256: String,
) {
    fun openRead(): InputStream = artifact.openArtifact(R8ArtifactRole.DEX_ZIP)

    companion object {
        fun from(artifact: R8CompilerStoredArtifact): R8CompilerLoadableDexZip {
            val identity = artifact.result.artifactIdentities.single {
                it.role == R8ArtifactRole.DEX_ZIP
            }
            return R8CompilerLoadableDexZip(
                artifact = artifact,
                sizeBytes = identity.sizeBytes,
                sha256 = identity.contentSha256.toByteArray().toHex(),
            )
        }
    }
}

internal class R8CompilerExplicitLoadException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Testable production boundary for `runtime.loadJarWithR8`.
 *
 * The route has no local compiler callback: every selected-provider failure is terminal, and only
 * a verified R8 DEX ZIP can cross into the runtime class loader.
 */
internal object R8CompilerExplicitRuntimeRoute {
    fun <T> dispatch(
        programPath: String,
        keepRulePaths: Array<out String>,
        orderedClasspathPaths: Array<out String>,
        consumerRulePaths: Array<out String>,
        consumerRuleClasspathOrdinals: IntArray,
        minApi: Int,
        exportDirectoryPath: String? = null,
        resolvePath: (String) -> String,
        compile: (R8CompilerHostCompileSpec) -> R8CompilerHostDispatchOutcome,
        loadPublished: (R8CompilerLoadableDexZip) -> T,
    ): T {
        require(keepRulePaths.isNotEmpty()) {
            "Explicit R8 loading requires at least one keep-rule file"
        }
        require(consumerRulePaths.size == consumerRuleClasspathOrdinals.size) {
            "Every R8 consumer-rule file must have exactly one classpath owner ordinal"
        }
        require(consumerRuleClasspathOrdinals.all { it in orderedClasspathPaths.indices }) {
            "R8 consumer-rule owner is outside the ordered classpath"
        }

        val program = File(resolvePath(programPath))
        val classpath = orderedClasspathPaths.map { File(resolvePath(it)) }
        val keepRules = keepRulePaths.map { File(resolvePath(it)) }
        val consumerRules = consumerRulePaths.map { File(resolvePath(it)) }
        val spec = R8CompilerHostCompileSpec(
            inputs = R8CompilerHostInputs(
                program = program.asSource("program JAR"),
                classpath = classpath.mapIndexed { ordinal, file ->
                    file.asSource("classpath JAR $ordinal")
                },
                keepRules = keepRules.mapIndexed { ordinal, file ->
                    file.asSource("keep-rule file $ordinal")
                },
                consumerRules = consumerRules.mapIndexed { ordinal, file ->
                    R8CompilerConsumerRuleSource(
                        ownerClasspathOrdinal = consumerRuleClasspathOrdinals[ordinal],
                        source = file.asSource("consumer-rule file $ordinal"),
                    )
                },
            ),
            minApi = minApi,
        )
        val exportDirectory = exportDirectoryPath?.let { File(resolvePath(it)) }
        return consume(compile(spec), exportDirectory, loadPublished)
    }

    private fun <T> consume(
        outcome: R8CompilerHostDispatchOutcome,
        exportDirectory: File?,
        loadPublished: (R8CompilerLoadableDexZip) -> T,
    ): T = when (outcome) {
        is R8CompilerHostDispatchOutcome.Published -> {
            exportDirectory?.let { directory ->
                R8CompilerArtifactExporter.export(outcome.transaction.artifact, directory)
            }
            loadPublished(R8CompilerLoadableDexZip.from(outcome.transaction.artifact))
        }

        is R8CompilerHostDispatchOutcome.NotSelected -> throw R8CompilerExplicitLoadException(
            "Explicit R8 compiler is disabled or has no exact component selection; " +
                "no D8/dx fallback was attempted",
        )

        is R8CompilerHostDispatchOutcome.Failed -> {
            requireR8Only(outcome.directive)
            if (ScriptInterruptedException.causedByInterrupt(outcome.cause) ||
                Thread.currentThread().isInterrupted
            ) {
                Thread.currentThread().interrupt()
                throw ScriptInterruptedException(outcome.cause)
            }
            throw R8CompilerExplicitLoadException(
                "Explicit R8 compilation failed at ${outcome.stage}; no D8/dx fallback was attempted",
                outcome.cause,
            )
        }

        is R8CompilerHostDispatchOutcome.Cancelled -> {
            requireR8Only(outcome.transaction.directive)
            throw ScriptInterruptedException(
                R8CompilerExplicitLoadException(
                    "Explicit R8 compilation was cancelled; no D8/dx fallback was attempted",
                ),
            )
        }
    }

    private fun requireR8Only(directive: R8CompilerFailureDirective) {
        check(directive.propagateAsR8Failure && !directive.semanticFallbackAllowed) {
            "R8 failure directive attempted to authorize a semantic fallback"
        }
    }

    private fun File.asSource(label: String) = R8CompilerHostSource {
        if (!isFile || !canRead()) {
            throw FileNotFoundException("Explicit R8 $label is not a readable file: $path")
        }
        FileInputStream(this)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
