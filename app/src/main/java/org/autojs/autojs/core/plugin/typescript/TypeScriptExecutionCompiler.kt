package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import java.io.File
import java.io.Reader
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.TypeScriptFileSource
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
        val output = runBlocking {
            TypeScriptCompilerPluginClient(context).compileSingleFile(
                sourceFile = source.file,
                sourceName = sourceName,
                sourceIdentity = sourceIdentity,
            )
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
                sourceName = sourceName,
                diagnostics = output.diagnostics,
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
        val category = when (diagnostic.category) {
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING -> "warning"
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR -> "error"
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_SUGGESTION -> "suggestion"
            TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE -> "message"
            else -> "diagnostic"
        }
        val location = diagnostic.range?.let { range ->
            "$sourceName:${range.line}:${range.column}"
        } ?: sourceName
        return "$location - $category TS${diagnostic.code}: ${diagnostic.message}"
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
    sourceName: String,
    val diagnostics: List<TypeScriptDiagnostic>,
) : RuntimeException(
    buildString {
        append("TypeScript compilation failed")
        diagnostics.forEach { diagnostic ->
            append('\n')
            append(TypeScriptExecutionCompiler.formatDiagnostic(sourceName, diagnostic))
        }
    },
)
