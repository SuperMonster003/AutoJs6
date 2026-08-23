package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.runtime.exception.ScriptCompilationDiagnostic
import org.autojs.autojs.runtime.exception.ScriptCompilationDiagnosticSeverity
import org.autojs.autojs.runtime.exception.ScriptCompilationException
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs6.R
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptExecutableSource(
    val sourceName: String,
    val javaScript: String,
    val sourceMap: String,
    val generatedSourceName: String,
    val diagnostics: List<TypeScriptDiagnostic>,
)

internal object TypeScriptExecutionCompiler {

    /** Runs on the script worker thread before an engine is created. */
    @JvmStatic
    fun prepare(context: Context, source: ScriptSource): ScriptSource {
        if (source !is JavaScriptFileSource || !source.isRhinoTypeScriptSource) {
            return source
        }
        return CompiledTypeScriptSource(source, compile(context, source))
    }

    private fun compile(context: Context, source: JavaScriptFileSource): TypeScriptExecutableSource {
        val sourceName = source.compilationName()
        val sourceIdentity = (source as? TypeScriptFileSource)?.compilationSourceIdentity
            ?: source.originPath?.takeIf(String::isNotBlank)
            ?: source.originUri?.takeIf(String::isNotBlank)
            ?: runCatching { source.file.canonicalPath }.getOrElse { source.file.absolutePath }
        val output = try {
            runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(
                    sourceFile = source.file,
                    sourceName = sourceName,
                    sourceIdentity = sourceIdentity,
                )
            }
        } catch (error: TypeScriptCompilerException) {
            if (error.code == TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE) {
                throw TypeScriptExecutionException(
                    context.getString(R.string.error_typescript_compiler_plugin_unavailable),
                    error,
                )
            }
            throw error
        }
        return when (output) {
            is TypeScriptCompilationOutput.Emitted -> TypeScriptExecutableSource(
                sourceName = sourceName,
                javaScript = output.javaScript,
                sourceMap = output.sourceMap,
                generatedSourceName = source.fullPath + GENERATED_SUFFIX,
                diagnostics = output.diagnostics,
            )
            is TypeScriptCompilationOutput.Diagnostics -> throw TypeScriptDiagnosticsException(
                message = context.getString(R.string.error_typescript_compilation_failed),
                sourceName = sourceName,
                typeScriptDiagnostics = output.diagnostics,
            )
        }
    }

    private fun JavaScriptFileSource.compilationName(): String {
        if (this is TypeScriptFileSource) {
            return compilationSourceName
        }
        return sequenceOf(
            originPath?.let(::File)?.name,
            file.name,
        ).filterNotNull()
            .firstOrNull(org.autojs.autojs.script.JavaScriptSource::isRhinoTypeScriptFileName)
            ?: "main.ts"
    }

    fun formatDiagnostic(sourceName: String, diagnostic: TypeScriptDiagnostic): String {
        return diagnostic.toScriptCompilationDiagnostic(sourceName).consoleLine
    }

    fun TypeScriptDiagnostic.toScriptCompilationDiagnostic(
        fallbackSourceName: String,
    ): ScriptCompilationDiagnostic {
        return ScriptCompilationDiagnostic(
            sourceName = sourceName(uri) ?: fallbackSourceName,
            lineNumber = range?.line,
            columnNumber = range?.column,
            severity = compilationSeverity(category),
            code = "TS$code",
            message = message,
            relatedInformation = relatedInformation.map { related ->
                ScriptCompilationDiagnostic(
                    sourceName = sourceName(related.uri) ?: fallbackSourceName,
                    lineNumber = related.range?.line,
                    columnNumber = related.range?.column,
                    severity = compilationSeverity(related.category),
                    code = "TS${related.code}",
                    message = related.message,
                )
            },
        )
    }

    private fun compilationSeverity(category: Int): ScriptCompilationDiagnosticSeverity =
        when (category) {
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING ->
                ScriptCompilationDiagnosticSeverity.WARNING
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR ->
                ScriptCompilationDiagnosticSeverity.ERROR
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_SUGGESTION ->
                ScriptCompilationDiagnosticSeverity.SUGGESTION
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE ->
                ScriptCompilationDiagnosticSeverity.MESSAGE
            else -> ScriptCompilationDiagnosticSeverity.MESSAGE
        }

    private fun sourceName(uri: String?): String? {
        if (uri.isNullOrBlank()) {
            return null
        }
        return runCatching { URI(uri).path }
            .getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
    }

    const val SOURCE_MAP_ENGINE_TAG = "typescript_source_map_v3"
    const val GENERATED_SOURCE_ENGINE_TAG = "typescript_generated_source"
    private const val GENERATED_SUFFIX = ".generated.js"
}

internal class CompiledTypeScriptSource(
    val originalSource: JavaScriptFileSource,
    val executable: TypeScriptExecutableSource,
) : JavaScriptSource(executable.sourceName.substringBeforeLast('.', executable.sourceName)) {

    override val engineName: String
        get() = ENGINE

    override fun getScript(): String = executable.javaScript

    override fun getScriptReader(): Reader = StringReader(executable.javaScript)

    override fun toString(): String = executable.generatedSourceName
}

internal class TypeScriptDiagnosticsException(
    message: String,
    sourceName: String,
    typeScriptDiagnostics: List<TypeScriptDiagnostic>,
) : ScriptCompilationException(
    message = message,
    diagnostics = typeScriptDiagnostics.map { diagnostic ->
        with(TypeScriptExecutionCompiler) {
            diagnostic.toScriptCompilationDiagnostic(sourceName)
        }
    },
) {
    val typeScriptDiagnostics: List<TypeScriptDiagnostic> = typeScriptDiagnostics.toList()
}

internal class TypeScriptExecutionException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
