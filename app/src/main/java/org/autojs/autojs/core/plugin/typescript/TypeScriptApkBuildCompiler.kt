package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptApkBuildEntry(
    val relativePath: String,
    val bytes: ByteArray,
)

/** In-memory overlay used to keep raw TypeScript out of an APK Builder request. */
internal class TypeScriptApkBuildPlan(
    val mainScriptPath: String,
    val forceDirectorySource: Boolean,
    val requiresEncryptedStaging: Boolean = false,
    entries: List<TypeScriptApkBuildEntry>,
) : Closeable {

    val entries: List<TypeScriptApkBuildEntry> = entries.sortedBy(TypeScriptApkBuildEntry::relativePath)
    val generatedPaths: Set<String> = this.entries.mapTo(linkedSetOf()) { it.relativePath }

    init {
        require(this.entries.isNotEmpty())
        require(generatedPaths.size == this.entries.size)
        require(generatedPaths.map(String::asciiCaseFold).distinct().size == generatedPaths.size)
        this.entries.forEach { entry ->
            requireCanonicalProjectPath(entry.relativePath)
            require(entry.bytes.isNotEmpty())
        }
    }

    fun omitOriginal(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/')
        val lower = normalized.lowercase()
        return lower == PROJECT_CONFIG_PATH ||
            lower == TSCONFIG_PATH ||
            TypeScriptApkBuildCompiler.isTypeScriptPath(normalized)
    }

    override fun close() {
        entries.forEach { entry -> entry.bytes.fill(0) }
    }

    private companion object {
        const val PROJECT_CONFIG_PATH = "project.json"
        const val TSCONFIG_PATH = "tsconfig.json"
    }
}

/** Performs the clean TypeScript type-check/emit phase before the remote APK build starts. */
internal object TypeScriptApkBuildCompiler {

    private const val COMPILATION_MANIFEST_PATH = ".autojs6/typescript-compilation.json"
    internal const val RHINO_LAUNCHER_PATH = ".autojs6/typescript-launcher.js"
    private const val COMPILATION_MANIFEST_SCHEMA = "autojs6.typescript.apk-compilation"
    private const val COMPILATION_MANIFEST_REVISION = 1
    private const val DEFAULT_PACKAGED_MAIN = "main.js"
    private const val TSCONFIG_PATH = "tsconfig.json"
    private val FIXED_EXCLUDED_DIRECTORIES = setOf(
        ".git",
        ".gradle",
        ".idea",
        "build",
        "dist",
        "node_modules",
    )
    private val SOURCE_MAPPING_URL = Regex(
        pattern = "(?m)^\\s*//[#@]\\s*sourceMappingURL=.*$",
    )

    fun prepare(
        context: Context,
        projectConfig: ProjectConfig,
        source: File,
        cancelSignal: AtomicBoolean?,
    ): TypeScriptApkBuildPlan? {
        ensureActive(cancelSignal)
        val canonicalSource = source.canonicalFile
        return if (canonicalSource.isDirectory) {
            prepareDirectory(context, projectConfig, canonicalSource, cancelSignal)
        } else {
            prepareSingleFile(context, projectConfig, canonicalSource, cancelSignal)
        }
    }

    internal fun isTypeScriptPath(path: String): Boolean = classify(path) != SourceKind.OTHER

    private fun prepareDirectory(
        context: Context,
        projectConfig: ProjectConfig,
        root: File,
        cancelSignal: AtomicBoolean?,
    ): TypeScriptApkBuildPlan? {
        val excludedDirectories = projectConfig.excludedDirs.mapNotNull { excluded ->
            runCatching { excluded.canonicalFile }.getOrNull()
        }
        val typeScriptSources = root.walkTopDown()
            .onEnter { directory ->
                ensureActive(cancelSignal)
                directory == root ||
                    directory.name !in FIXED_EXCLUDED_DIRECTORIES &&
                    excludedDirectories.none { excluded -> directory.isInsideOrSame(excluded) }
            }
            .filter(File::isFile)
            .map { file -> file.canonicalFile }
            .filter { file -> file.isInsideOrSame(root) }
            .map { file -> file to file.relativeProjectPath(root) }
            .filter { (_, relativePath) -> isTypeScriptPath(relativePath) }
            .sortedBy { (_, relativePath) -> relativePath }
            .toList()
        if (typeScriptSources.isEmpty()) return null

        typeScriptSources.forEach { (_, relativePath) -> requireCanonicalTypeScriptExtension(relativePath) }
        val executable = typeScriptSources.filter { (_, relativePath) ->
            classify(relativePath) != SourceKind.DECLARATION
        }
        if (executable.isEmpty()) {
            return declarationOnlyPlan(projectConfig.mainScriptFileName)
        }
        requireRhinoPackaging(projectConfig, executable.map { it.second })

        val mainPath = normalizeProjectPath(projectConfig.mainScriptFileName)
        val mainKind = classify(mainPath)
        val entry = when (mainKind) {
            SourceKind.RHINO -> {
                val mainFile = File(root, mainPath).canonicalFile
                require(mainFile.isFile && mainFile.isInsideOrSame(root)) {
                    "TypeScript APK entry does not exist inside the project: $mainPath"
                }
                mainFile
            }
            SourceKind.DECLARATION -> throw IOException(
                "A TypeScript declaration file cannot be an APK entry: $mainPath",
            )
            SourceKind.NODE -> throw IOException(
                "Node TypeScript projects cannot be packaged without a Node Runtime: $mainPath",
            )
            SourceKind.OTHER -> executable.first().first
        }
        val sourceReader = ApkBuildTypeScriptSourceReader(projectConfig)
        val output = compileOrThrow {
            runBlocking {
                TypeScriptCompilerPluginClient(context).compileProject(
                    projectRoot = root,
                    entryFile = entry,
                    targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
                    sourceContentReader = sourceReader,
                    usePersistentCache = false,
                )
            }
        }
        ensureActive(cancelSignal)
        verifyProjectSnapshot(root, output, sourceReader)

        val packagedMain = if (mainKind == SourceKind.RHINO) {
            runtimeRelativeGeneratedPath(output.entryModule)
        } else {
            mainPath
        }
        return createPlan(
            output = output,
            mainScriptPath = packagedMain,
            forceDirectorySource = false,
            singleFileTarget = null,
            requiresEncryptedStaging = sourceReader.encounteredEncryptedSource,
        )
    }

    private fun prepareSingleFile(
        context: Context,
        projectConfig: ProjectConfig,
        source: File,
        cancelSignal: AtomicBoolean?,
    ): TypeScriptApkBuildPlan? {
        val kind = classify(source.name)
        if (kind == SourceKind.OTHER) return null
        require(source.isFile) { "APK source is not a regular file: ${source.path}" }
        requireCanonicalTypeScriptExtension(source.name)
        when (kind) {
            SourceKind.DECLARATION -> throw IOException(
                "A TypeScript declaration file cannot be packaged as an APK entry: ${source.name}",
            )
            SourceKind.NODE -> throw IOException(
                "Node TypeScript files cannot be packaged without a Node Runtime: ${source.name}",
            )
            SourceKind.RHINO -> Unit
            SourceKind.OTHER -> return null
        }
        requireRhinoPackaging(projectConfig, listOf(source.name))
        val sourceReader = ApkBuildTypeScriptSourceReader(projectConfig)
        val output = compileOrThrow {
            runBlocking {
                TypeScriptCompilerPluginClient(context).compileSingleFile(
                    sourceFile = source,
                    sourceName = source.name,
                    sourceIdentity = source.canonicalPath,
                    targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
                    sourceContentReader = sourceReader,
                    usePersistentCache = false,
                )
            }
        }
        ensureActive(cancelSignal)
        verifySingleFileSnapshot(source, output, sourceReader)
        return createPlan(
            output = output,
            mainScriptPath = DEFAULT_PACKAGED_MAIN,
            forceDirectorySource = true,
            singleFileTarget = DEFAULT_PACKAGED_MAIN,
            requiresEncryptedStaging = sourceReader.encounteredEncryptedSource,
        )
    }

    private fun declarationOnlyPlan(mainScriptPath: String): TypeScriptApkBuildPlan {
        val normalizedMain = normalizeProjectPath(mainScriptPath)
        val manifest = JsonObject().apply {
            addProperty("schema", COMPILATION_MANIFEST_SCHEMA)
            addProperty("revision", COMPILATION_MANIFEST_REVISION)
            addProperty("outcome", "declarations_stripped")
            addProperty("entryJavaScriptPath", normalizedMain)
            add("modules", JsonArray())
        }
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        return TypeScriptApkBuildPlan(
            mainScriptPath = normalizedMain,
            forceDirectorySource = false,
            entries = listOf(TypeScriptApkBuildEntry(COMPILATION_MANIFEST_PATH, manifest)),
        )
    }

    internal fun createPlan(
        output: TypeScriptCompilationOutput.Emitted,
        mainScriptPath: String,
        forceDirectorySource: Boolean,
        singleFileTarget: String?,
        requiresEncryptedStaging: Boolean = false,
    ): TypeScriptApkBuildPlan {
        val modules = output.modules.sortedBy { module -> runtimeRelativeGeneratedPath(module) }
        val entries = ArrayList<TypeScriptApkBuildEntry>(modules.size * 2 + 1)
        val manifestModules = JsonArray()
        modules.forEach { module ->
            val generatedPath = singleFileTarget ?: runtimeRelativeGeneratedPath(module)
            requireCanonicalProjectPath(generatedPath)
            val mapPath = "$generatedPath.map"
            val mapText = releaseSourceMap(module, generatedPath)
            val javaScript = releaseJavaScript(module.javaScript, mapPath.substringAfterLast('/'))
            val javaScriptBytes = TypeScriptUtf8.encode(javaScript, "packaged JavaScript")
            val sourceMapBytes = TypeScriptUtf8.encode(mapText, "packaged Source Map")
            entries += TypeScriptApkBuildEntry(generatedPath, javaScriptBytes)
            entries += TypeScriptApkBuildEntry(mapPath, sourceMapBytes)
            manifestModules.add(JsonObject().apply {
                addProperty(
                    "sourcePathFingerprint",
                    sha256(module.source.projectRelativePath.toByteArray(StandardCharsets.UTF_8)),
                )
                addProperty("javaScriptPath", generatedPath)
                addProperty("sourceMapPath", mapPath)
                addProperty("javaScriptSha256", sha256(javaScriptBytes))
                addProperty("sourceMapSha256", sha256(sourceMapBytes))
            })
        }
        entries += TypeScriptApkBuildEntry(
            RHINO_LAUNCHER_PATH,
            rhinoLauncher(mainScriptPath),
        )
        val authority = output.authority
        val manifest = JsonObject().apply {
            addProperty("schema", COMPILATION_MANIFEST_SCHEMA)
            addProperty("revision", COMPILATION_MANIFEST_REVISION)
            addProperty("outcome", "emitted")
            addProperty("targetProfile", authority.targetProfile)
            addProperty("entryJavaScriptPath", mainScriptPath)
            addProperty("launchScriptPath", RHINO_LAUNCHER_PATH)
            addProperty("compilerVersion", authority.compilerVersion)
            addProperty("compilerDistributionFingerprint", authority.compilerDistributionFingerprint)
            addProperty("declarationsFingerprint", authority.declarationsFingerprint)
            addProperty("declarationProfileFingerprint", authority.declarationProfileFingerprint)
            addProperty("runtimeCapabilitiesFingerprint", authority.runtimeCapabilitiesFingerprint)
            addProperty("profileSchemaRevision", authority.profileSchemaRevision)
            addProperty("profileRevision", authority.profileRevision)
            addProperty("configSchemaRevision", authority.configSchemaRevision)
            addProperty("configKind", authority.configKind)
            addProperty("sourceMapPolicy", "external_without_sources_content")
            add("modules", manifestModules)
        }
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        entries += TypeScriptApkBuildEntry(COMPILATION_MANIFEST_PATH, manifest)
        return TypeScriptApkBuildPlan(
            mainScriptPath = RHINO_LAUNCHER_PATH,
            forceDirectorySource = forceDirectorySource,
            requiresEncryptedStaging = requiresEncryptedStaging,
            entries = entries,
        )
    }

    private fun rhinoLauncher(entryJavaScriptPath: String): ByteArray {
        requireCanonicalProjectPath(entryJavaScriptPath)
        val pathLiteral = JsonObject().apply {
            addProperty("path", "./$entryJavaScriptPath")
        }.get("path").toString()
        return TypeScriptUtf8.encode(
            "\"use strict\";\nrequire(files.path($pathLiteral));\n",
            "packaged TypeScript launcher",
        )
    }

    private fun releaseJavaScript(javaScript: String, sourceMapName: String): String {
        val replacement = "//# sourceMappingURL=$sourceMapName"
        return if (SOURCE_MAPPING_URL.containsMatchIn(javaScript)) {
            SOURCE_MAPPING_URL.replace(javaScript, replacement)
        } else {
            javaScript.trimEnd() + "\n$replacement\n"
        }
    }

    private fun releaseSourceMap(module: TypeScriptEmittedModule, generatedPath: String): String {
        val map = JsonParser.parseString(module.sourceMap).asJsonObject
        require(map.get("version")?.asInt == 3) { "APK TypeScript Source Map is not v3." }
        map.addProperty("file", generatedPath.substringAfterLast('/'))
        map.addProperty("sourceRoot", "")
        map.add("sources", JsonArray().apply { add(module.source.projectRelativePath) })
        map.remove("sourcesContent")
        return map.toString()
    }

    private fun verifyProjectSnapshot(
        root: File,
        output: TypeScriptCompilationOutput.Emitted,
        sourceReader: TypeScriptSourceContentReader,
    ) {
        output.projectSources.forEach { source ->
            val original = requireNotNull(source.originalFile)
            val expected = File(root, source.projectRelativePath).canonicalFile
            require(
                expected.isFile && expected.isInsideOrSame(root) &&
                    expected.path == original.canonicalPath,
            ) { "TypeScript APK source changed location during compilation." }
            val bytes = sourceReader.read(
                expected,
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            )
            try {
                require(sha256(bytes) == source.sha256) {
                    "TypeScript APK source changed during compilation: ${source.projectRelativePath}"
                }
            } finally {
                bytes.fill(0)
            }
        }
        output.configSha256?.let { expectedSha256 ->
            val config = File(root, TSCONFIG_PATH)
            val bytes = if (config.isFile) {
                config.readBytes()
            } else {
                "{}".toByteArray(StandardCharsets.UTF_8)
            }
            try {
                require(
                    bytes.size.toLong() == output.configByteLength &&
                        sha256(bytes) == expectedSha256,
                ) { "TypeScript APK config changed during compilation." }
            } finally {
                bytes.fill(0)
            }
        }
    }

    private fun verifySingleFileSnapshot(
        source: File,
        output: TypeScriptCompilationOutput.Emitted,
        sourceReader: TypeScriptSourceContentReader,
    ) {
        val expected = output.projectSources.single().sha256
        val bytes = sourceReader.read(
            source,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        )
        try {
            require(sha256(bytes) == expected) {
                "TypeScript APK source changed during compilation: ${source.path}"
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun compileOrThrow(
        compile: () -> TypeScriptCompilationOutput,
    ): TypeScriptCompilationOutput.Emitted {
        val output = try {
            compile()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TypeScriptCompilerException) {
            throw IOException(
                "TypeScript APK compilation failed (${error.code}): ${error.message}",
                error,
            )
        }
        return when (output) {
            is TypeScriptCompilationOutput.Emitted -> output
            is TypeScriptCompilationOutput.Diagnostics -> {
                val details = output.diagnostics.take(MAX_REPORTED_DIAGNOSTICS)
                    .joinToString("\n") { diagnostic ->
                        val position = diagnostic.range?.let { range ->
                            ":${range.line}:${range.column}"
                        }.orEmpty()
                        "${diagnostic.uri.orEmpty()}$position TS${diagnostic.code}: " +
                            diagnostic.message
                    }
                throw IOException(
                    "TypeScript APK compilation produced diagnostics" +
                        details.takeIf(String::isNotBlank)?.let { ":\n$it" }.orEmpty(),
                )
            }
        }
    }

    private fun requireRhinoPackaging(projectConfig: ProjectConfig, paths: List<String>) {
        if (projectConfig.projectType?.trim()?.equals("node", ignoreCase = true) == true) {
            throw IOException(
                "Node TypeScript APK packaging is unavailable because packaged apps do not " +
                    "embed the external Node Runtime: ${paths.first()}",
            )
        }
        paths.firstOrNull { path -> classify(path) == SourceKind.NODE }?.let { path ->
            throw IOException(
                "Node TypeScript files cannot be packaged without a Node Runtime: $path",
            )
        }
    }

    private fun runtimeRelativeGeneratedPath(module: TypeScriptEmittedModule): String {
        val path = module.generatedMapping?.generatedPath
            ?.removePrefix("/outputs/")
            ?: module.javaScriptPath.removePrefix("modules/")
        requireCanonicalProjectPath(path)
        return path
    }

    private fun requireCanonicalTypeScriptExtension(path: String) {
        val kind = classify(path)
        val suffixes = when (kind) {
            SourceKind.DECLARATION -> DECLARATION_SUFFIXES
            SourceKind.RHINO -> RHINO_SUFFIXES
            SourceKind.NODE -> NODE_SUFFIXES
            SourceKind.OTHER -> emptyList()
        }
        require(suffixes.any(path::endsWith)) {
            "TypeScript APK sources require canonical lowercase extensions: $path"
        }
    }

    private fun classify(path: String): SourceKind {
        val lower = path.lowercase()
        return when {
            DECLARATION_SUFFIXES.any(lower::endsWith) -> SourceKind.DECLARATION
            NODE_SUFFIXES.any(lower::endsWith) -> SourceKind.NODE
            lower.endsWith(".tsx") || lower.endsWith(".ts") -> SourceKind.RHINO
            else -> SourceKind.OTHER
        }
    }

    private fun normalizeProjectPath(path: String): String = path.replace('\\', '/').also {
        requireCanonicalProjectPath(it)
    }

    private fun ensureActive(cancelSignal: AtomicBoolean?) {
        if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
            throw CancellationException("TypeScript APK compilation was cancelled.")
        }
    }

    private enum class SourceKind {
        OTHER,
        DECLARATION,
        RHINO,
        NODE,
    }

    private const val MAX_REPORTED_DIAGNOSTICS = 20
    private val DECLARATION_SUFFIXES = listOf(".d.mts", ".d.cts", ".d.ts")
    private val RHINO_SUFFIXES = listOf(".tsx", ".ts")
    private val NODE_SUFFIXES = listOf(".mts", ".cts")
}

/** Decrypts a previously packaged project source only in memory. */
internal class ApkBuildTypeScriptSourceReader(
    private val projectConfig: ProjectConfig,
) : TypeScriptSourceContentReader {

    @Volatile
    var encounteredEncryptedSource: Boolean = false
        private set

    override fun read(file: File, maximumBytes: Long): ByteArray {
        require(file.isFile && file.length() in 0..Math.addExact(maximumBytes, MAX_CIPHER_OVERHEAD)) {
            "TypeScript APK source exceeds its encrypted byte limit: ${file.path}"
        }
        val raw = file.readBytes()
        require(raw.size.toLong() <= Math.addExact(maximumBytes, MAX_CIPHER_OVERHEAD))
        if (
            raw.size < EncryptedScriptFileHeader.BLOCK_SIZE ||
            !EncryptedScriptFileHeader.isValidFile(raw)
        ) {
            require(raw.size.toLong() <= maximumBytes)
            return raw
        }
        encounteredEncryptedSource = true
        try {
            val buildId = projectConfig.buildInfo?.buildId?.takeIf(String::isNotBlank)
                ?: throw IOException(
                    "Encrypted TypeScript APK source has no project build identity: ${file.path}",
                )
            val key = md5(
                projectConfig.packageName +
                    projectConfig.versionName +
                    projectConfig.mainScriptFileName,
            ).toByteArray(StandardCharsets.UTF_8)
            val vector = md5(buildId + projectConfig.name).take(16)
            try {
                val cleartext = AdvancedEncryptionStandard(key, vector).decrypt(
                    raw,
                    EncryptedScriptFileHeader.BLOCK_SIZE,
                    raw.size,
                )
                try {
                    require(cleartext.size.toLong() <= maximumBytes) {
                        "Decrypted TypeScript APK source exceeds its byte limit: ${file.path}"
                    }
                    return cleartext
                } catch (error: Throwable) {
                    cleartext.fill(0)
                    throw error
                }
            } finally {
                key.fill(0)
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Unable to decrypt TypeScript APK source: ${file.path}", error)
        } finally {
            raw.fill(0)
        }
    }

    private companion object {
        const val MAX_CIPHER_OVERHEAD = 32L
    }
}

private fun requireCanonicalProjectPath(path: String) {
    require(
        path.isNotBlank() &&
            !path.startsWith('/') &&
            !path.endsWith('/') &&
            '\\' !in path &&
            path.split('/').all { segment ->
                segment.isNotBlank() && segment != "." && segment != ".." &&
                    segment.none { character ->
                        character.code in 0x00..0x1f || character.code in 0x7f..0x9f
                    }
            },
    ) { "TypeScript APK path is not canonical: $path" }
}

private fun File.relativeProjectPath(root: File): String = canonicalPath
    .removePrefix(root.canonicalPath + File.separator)
    .replace(File.separatorChar, '/')
    .also(::requireCanonicalProjectPath)

private fun File.isInsideOrSame(root: File): Boolean {
    val rootPath = root.canonicalPath.trimEnd(File.separatorChar)
    val candidatePath = canonicalPath
    return candidatePath == rootPath || candidatePath.startsWith("$rootPath${File.separator}")
}

private fun String.asciiCaseFold(): String = buildString(length) {
    this@asciiCaseFold.forEach { character ->
        append(if (character in 'A'..'Z') character + 32 else character)
    }
}

private fun md5(value: String): String = MessageDigest.getInstance("MD5")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
