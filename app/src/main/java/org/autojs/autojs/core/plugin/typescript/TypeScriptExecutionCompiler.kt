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
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs.script.SequenceScriptSource
import org.autojs.autojs.script.TypeScriptFileSource
import org.autojs.autojs.script.TypeScriptStringSource
import org.autojs.autojs6.R
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptExecutableSource(
    val sourceName: String,
    val targetProfile: String,
    val javaScript: String,
    val sourceMapBinding: TypeScriptSourceMapBinding,
    val generatedSourceName: String,
    val diagnostics: List<TypeScriptDiagnostic>,
    val modules: List<TypeScriptEmittedModule>,
    val entryModulePath: String,
    val projectRoot: File?,
    val projectSources: List<TypeScriptPreparedSource>,
    val packageMetadata: TypeScriptPreparedPackageMetadata?,
    val dependencyLayer: TypeScriptPreparedDependencyLayer?,
) {

    val isProjectCompilation: Boolean
        get() = projectRoot != null

    val entryModule: TypeScriptEmittedModule
        get() = modules.single { module -> module.javaScriptPath == entryModulePath }

    fun runtimeRelativeGeneratedPath(module: TypeScriptEmittedModule): String {
        val path = module.generatedMapping?.generatedPath
            ?.removePrefix("/outputs/")
            ?: module.javaScriptPath.removePrefix("modules/")
        require(
            path.isNotBlank() && !path.startsWith('/') && !path.endsWith('/') && '\\' !in path,
        ) { "TypeScript generated module has no canonical runtime path." }
        require(path.split('/').all { segment -> segment.isNotBlank() && segment != "." && segment != ".." })
        return path
    }

    fun sourceMapBindings(
        generatedSourceNames: Map<String, String>,
        entryGeneratedLineOffset: Int = 0,
    ): TypeScriptSourceMapBindings {
        require(generatedSourceNames.keys == modules.mapTo(linkedSetOf(), TypeScriptEmittedModule::javaScriptPath))
        return TypeScriptSourceMapBindings.of(
            modules.map { module ->
                TypeScriptSourceMapBinding.parse(
                    sourceMapText = module.sourceMap,
                    generatedSourceName = requireNotNull(generatedSourceNames[module.javaScriptPath]),
                    expectedGeneratedFileName = module.javaScriptPath.substringAfterLast('/'),
                    originalSourceName = module.source.originalFile?.path
                        ?: if (projectRoot == null && module.javaScriptPath == entryModulePath) {
                            sourceName
                        } else {
                            module.source.projectRelativePath
                        },
                    generatedLineOffset = if (module.javaScriptPath == entryModulePath) {
                        entryGeneratedLineOffset
                    } else {
                        0
                    },
                )
            },
        )
    }
}

internal object TypeScriptExecutionCompiler {

    /** Runs on the script worker thread before an engine is created. */
    @JvmStatic
    fun prepare(context: Context, source: ScriptSource): ScriptSource {
        val candidate = preparationCandidate(source) ?: return source
        val targetProfile = if (candidate.source.engineName == JavaScriptSource.ENGINE_NODEJS) {
            TypeScriptCompilerContract.PROFILE_NODE
        } else {
            TypeScriptCompilerContract.PROFILE_RHINO
        }
        return CompiledTypeScriptSource(
            originalSource = candidate.source,
            executable = compile(context, candidate.source, targetProfile),
            preExecuteScript = candidate.preExecuteScript,
        )
    }

    @JvmStatic
    fun requiresPreparation(source: ScriptSource): Boolean = preparationCandidate(source) != null

    private fun preparationCandidate(source: ScriptSource): PreparationCandidate? {
        if (source is TypeScriptStringSource) {
            return PreparationCandidate(source).unlessBun()
        }
        if (source is JavaScriptFileSource && source.isCompilerBackedTypeScriptSource) {
            return PreparationCandidate(source).unlessBun()
        }
        if (source !is SequenceScriptSource) return null
        if (source.firstScriptSource.engineName == JavaScriptSource.ENGINE_BUN) {
            source.engineName
        }
        val typeScriptSource = when (val candidate = source.secondScriptSource) {
            is TypeScriptStringSource -> candidate
            is JavaScriptFileSource -> candidate.takeIf(JavaScriptFileSource::isCompilerBackedTypeScriptSource)
            else -> null
        } ?: return null
        return PreparationCandidate(
            source = typeScriptSource,
            preExecuteScript = source.firstScriptSource.script,
        ).unlessBun()
    }

    private fun PreparationCandidate.unlessBun(): PreparationCandidate? =
        takeUnless { source.engineName == JavaScriptSource.ENGINE_BUN }

    private fun compile(
        context: Context,
        source: JavaScriptSource,
        targetProfile: String,
    ): TypeScriptExecutableSource {
        val fileSource = source as? JavaScriptFileSource
        val sourceName = source.compilationName()
        val sourceIdentity = source.compilationIdentity()
        val projectRoot = fileSource?.typeScriptProjectRoot(targetProfile)
        val output = try {
            runBlocking {
                val client = TypeScriptCompilerPluginClient(context)
                when {
                    source is TypeScriptStringSource -> client.compileSingleSource(
                        source = source.script,
                        sourceName = sourceName,
                        sourceIdentity = sourceIdentity,
                        targetProfile = targetProfile,
                    )
                    projectRoot != null -> client.compileProject(
                        projectRoot = projectRoot,
                        entryFile = requireNotNull(fileSource).file,
                        targetProfile = targetProfile,
                    )
                    else -> client.compileSingleFile(
                        sourceFile = requireNotNull(fileSource).file,
                        sourceName = sourceName,
                        sourceIdentity = sourceIdentity,
                        targetProfile = targetProfile,
                    )
                }
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
            is TypeScriptCompilationOutput.Emitted -> {
                val generatedFileName = output.entryModulePath.substringAfterLast('/')
                val generatedSourceName = if (
                    projectRoot != null
                ) {
                    File(
                        projectRoot,
                        output.entryModule.runtimeRelativeGeneratedPath(),
                    ).path
                } else if (targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
                    NODE_GENERATED_SOURCE_PREFIX + generatedFileName.substringAfterLast('.')
                } else {
                    source.fullPath + GENERATED_SUFFIX
                }
                val entryBinding = TypeScriptSourceMapBinding.parse(
                    sourceMapText = output.sourceMap,
                    generatedSourceName = generatedSourceName,
                    expectedGeneratedFileName = generatedFileName,
                    originalSourceName = if (projectRoot == null) {
                        sourceName
                    } else {
                        output.entryModule.source.originalFile?.path ?: requireNotNull(fileSource).file.path
                    },
                )
                TypeScriptExecutableSource(
                    sourceName = sourceName,
                    targetProfile = targetProfile,
                    javaScript = output.javaScript,
                    sourceMapBinding = entryBinding,
                    generatedSourceName = generatedSourceName,
                    diagnostics = output.diagnostics,
                    modules = output.modules,
                    entryModulePath = output.entryModulePath,
                    projectRoot = projectRoot,
                    projectSources = output.projectSources,
                    packageMetadata = output.packageMetadata,
                    dependencyLayer = output.dependencyLayer,
                )
            }
            is TypeScriptCompilationOutput.Diagnostics -> throw TypeScriptDiagnosticsException(
                message = context.getString(R.string.error_typescript_compilation_failed),
                sourceName = sourceName,
                typeScriptDiagnostics = output.diagnostics,
            )
        }
    }

    private fun TypeScriptEmittedModule.runtimeRelativeGeneratedPath(): String {
        val path = generatedMapping?.generatedPath
            ?.removePrefix("/outputs/")
            ?: javaScriptPath.removePrefix("modules/")
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path)
        return path
    }

    private fun JavaScriptFileSource.typeScriptProjectRoot(targetProfile: String): File? {
        val launchRoot = (this as? NodeProjectLaunchSource)?.nodeProjectRootFile
        if (launchRoot != null) return launchRoot.normalizedCanonicalFile()
        if (targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            nodeProjectRoot?.let { return it.normalizedCanonicalFile() }
        }
        var directory = file.normalizedCanonicalFile().parentFile
        while (directory != null) {
            val hasBoundary = PROJECT_BOUNDARY_NAMES.any { name ->
                File(directory, name).isFile
            } || targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                File(directory, NODE_PACKAGE_BOUNDARY_NAME).isFile
            if (hasBoundary) return directory.normalizedCanonicalFile()
            directory = directory.parentFile
        }
        return null
    }

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun JavaScriptSource.compilationName(): String {
        if (this is TypeScriptStringSource) {
            return compilationSourceName
        }
        val fileSource = this as? JavaScriptFileSource ?: return "main.ts"
        if (fileSource is TypeScriptFileSource) {
            return fileSource.compilationSourceName
        }
        return sequenceOf(
            fileSource.originPath?.let(::File)?.name,
            fileSource.file.name,
        ).filterNotNull()
            .firstOrNull(JavaScriptSource::isCompilerBackedTypeScriptFileName)
            ?: "main.ts"
    }

    private fun JavaScriptSource.compilationIdentity(): String {
        if (this is TypeScriptStringSource) {
            return compilationSourceIdentity
        }
        val fileSource = requireNotNull(this as? JavaScriptFileSource)
        return (fileSource as? TypeScriptFileSource)?.compilationSourceIdentity
            ?: fileSource.originPath?.takeIf(String::isNotBlank)
            ?: fileSource.originUri?.takeIf(String::isNotBlank)
            ?: runCatching { fileSource.file.canonicalPath }
                .getOrElse { fileSource.file.absolutePath }
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
    private const val NODE_GENERATED_SOURCE_PREFIX = "autojs-typescript-entry.generated."
    private const val GENERATED_SUFFIX = ".generated.js"
    private const val NODE_PACKAGE_BOUNDARY_NAME = "package.json"
    private val PROJECT_BOUNDARY_NAMES = listOf("project.json", "tsconfig.json")

    private data class PreparationCandidate(
        val source: JavaScriptSource,
        val preExecuteScript: String? = null,
    )
}

internal class CompiledTypeScriptSource(
    val originalSource: JavaScriptSource,
    val executable: TypeScriptExecutableSource,
    preExecuteScript: String? = null,
) : JavaScriptSource(executable.sourceName.substringBeforeLast('.', executable.sourceName)) {

    val preExecutePrefix: String = preExecuteScript
        ?.takeIf(String::isNotEmpty)
        ?.let { script -> if (script.endsWith('\n')) script else "$script\n" }
        .orEmpty()

    val entryGeneratedLineOffset: Int = preExecutePrefix.count { character -> character == '\n' }

    override val engineName: String
        get() = if (executable.targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            ENGINE_NODEJS
        } else {
            ENGINE
        }

    override fun getScript(): String = prefixedEntrySource(executable.javaScript)

    override fun getScriptReader(): Reader = StringReader(script)

    fun prefixedEntrySource(javaScript: String): String = preExecutePrefix + javaScript

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
