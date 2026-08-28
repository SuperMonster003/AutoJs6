package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class TypeScriptNodeRuntimeProject(
    val moduleSources: Map<String, String>,
    val workspaceModuleSources: Map<String, String>,
    val generatedSourceNames: Map<String, String>,
    val precompiledSourceNames: Set<String>,
    val entryGeneratedSourceName: String,
)

internal class TypeScriptMaterializedRhinoProject private constructor(
    val root: File,
    val entryFile: File,
    val generatedSourceNames: Map<String, String>,
    private val dependencyResolver: TypeScriptRhinoDependencyResolver?,
) : Closeable {

    fun resolveCommonJsDependency(specifier: String, parentUri: java.net.URI?): File? =
        dependencyResolver?.resolve(specifier, parentUri)

    override fun close() {
        root.walkBottomUp().forEach { entry -> runCatching { entry.delete() } }
    }

    companion object {
        fun create(
            context: Context,
            executable: TypeScriptExecutableSource,
            entryPrefix: String = "",
        ): TypeScriptMaterializedRhinoProject {
            val projectRoot = requireNotNull(executable.projectRoot).canonicalFile
            val parent = File(context.cacheDir, RUNTIME_DIRECTORY_NAME).canonicalFile
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Unable to create the Rhino TypeScript project cache.")
            }
            require(parent.isDirectory)
            val runtimeRoot = File(parent, "run-${UUID.randomUUID()}")
            if (!runtimeRoot.mkdir()) {
                throw IOException("Unable to create a Rhino TypeScript runtime project.")
            }
            try {
                val runtimeSupportPaths = executable.projectSources
                    .filter(TypeScriptPreparedSource::isRuntimeSupportSource)
                    .mapTo(HashSet(), TypeScriptPreparedSource::projectRelativePath)
                executable.projectSources.forEach { source ->
                    val bytes = readVerifiedProjectSource(projectRoot, source)
                    try {
                        if (source.isRuntimeSupportSource()) {
                            val payload = if (source.projectRelativePath.endsWith(".json", true)) {
                                val json = TypeScriptUtf8.decode(bytes, "TypeScript project JSON")
                                "module.exports = $json;\n".toByteArray(StandardCharsets.UTF_8)
                            } else {
                                bytes
                            }
                            writeRuntimeFile(runtimeRoot, source.projectRelativePath, payload)
                        }
                    } finally {
                        bytes.fill(0)
                    }
                }

                val dependencyPackageManifests = LinkedHashMap<String, String>()
                val runtimeDependencies = verifiedRuntimeDependencySources(
                    projectRoot,
                    executable.dependencyLayer,
                )
                runtimeDependencies.forEach { dependency ->
                        val payload = if (dependency.runtimeRelativePath.endsWith(".json", true)) {
                            "module.exports = ${dependency.source};\n"
                                .toByteArray(StandardCharsets.UTF_8)
                        } else {
                            dependency.source.toByteArray(StandardCharsets.UTF_8)
                        }
                        try {
                            val target = writeRuntimeFile(
                                runtimeRoot,
                                dependency.runtimeRelativePath,
                                payload,
                            )
                            if (dependency.runtimeRelativePath.endsWith("/package.json", true)) {
                                dependencyPackageManifests[target.canonicalPath] = dependency.source
                            }
                        } finally {
                            payload.fill(0)
                        }
                    }

                val generatedSourceNames = LinkedHashMap<String, String>(executable.modules.size)
                executable.modules.forEach { module ->
                    val relative = executable.runtimeRelativeGeneratedPath(module)
                    requireNoRuntimeSupportCollision(relative, runtimeSupportPaths)
                    val javaScript = if (module.javaScriptPath == executable.entryModulePath) {
                        entryPrefix + module.javaScript
                    } else {
                        module.javaScript
                    }
                    val bytes = javaScript.toByteArray(StandardCharsets.UTF_8)
                    try {
                        val target = writeRuntimeFile(runtimeRoot, relative, bytes)
                        generatedSourceNames[module.javaScriptPath] = target.canonicalPath
                    } finally {
                        bytes.fill(0)
                    }
                }
                val entryPath = requireNotNull(generatedSourceNames[executable.entryModulePath])
                return TypeScriptMaterializedRhinoProject(
                    root = runtimeRoot,
                    entryFile = File(entryPath),
                    generatedSourceNames = generatedSourceNames,
                    dependencyResolver = runtimeDependencies
                        .takeIf { dependencies -> dependencies.isNotEmpty() }
                        ?.let {
                            TypeScriptRhinoDependencyResolver(
                                runtimeRoot,
                                dependencyPackageManifests,
                            )
                        },
                )
            } catch (error: Throwable) {
                runtimeRoot.walkBottomUp().forEach { entry -> runCatching { entry.delete() } }
                throw error
            }
        }

        private const val RUNTIME_DIRECTORY_NAME = "typescript-rhino-projects"
    }
}

internal object TypeScriptNodeRuntimeProjectBuilder {

    fun build(executable: TypeScriptExecutableSource): TypeScriptNodeRuntimeProject {
        val projectRoot = requireNotNull(executable.projectRoot).canonicalFile
        val moduleSources = LinkedHashMap<String, String>()
        moduleSources.putAll(verifiedPackageMetadataSources(projectRoot, executable.packageMetadata))
        val workspaceModuleSources = verifiedRuntimeDependencySources(
            projectRoot,
            executable.dependencyLayer,
        ).associateTo(LinkedHashMap()) { dependency ->
            dependency.hostPath to dependency.source
        }
        val runtimeSupportPaths = executable.projectSources
            .filter(TypeScriptPreparedSource::isRuntimeSupportSource)
            .mapTo(HashSet(), TypeScriptPreparedSource::projectRelativePath)
        executable.projectSources.forEach { source ->
            val bytes = readVerifiedProjectSource(projectRoot, source)
            try {
                if (source.isRuntimeSupportSource()) {
                    moduleSources[File(projectRoot, source.projectRelativePath).canonicalPath] =
                        TypeScriptUtf8.decode(bytes, "TypeScript project runtime source")
                }
            } finally {
                bytes.fill(0)
            }
        }

        val generatedSourceNames = LinkedHashMap<String, String>(executable.modules.size)
        val precompiledSourceNames = LinkedHashSet<String>(executable.modules.size)
        executable.modules.forEach { module ->
            val relative = executable.runtimeRelativeGeneratedPath(module)
            requireNoRuntimeSupportCollision(relative, runtimeSupportPaths)
            val generated = File(
                projectRoot,
                relative,
            ).absoluteFile.normalize()
            require(generated.isInsideOrSame(projectRoot)) {
                "TypeScript generated Node module escapes its project root."
            }
            moduleSources[generated.path] = module.javaScript
            generatedSourceNames[module.javaScriptPath] = generated.path
            val original = File(projectRoot, module.source.projectRelativePath)
                .absoluteFile
                .normalize()
            require(original.isInsideOrSame(projectRoot)) {
                "TypeScript source module escapes its project root."
            }
            precompiledSourceNames.add(original.path)
        }
        return TypeScriptNodeRuntimeProject(
            moduleSources = moduleSources,
            workspaceModuleSources = workspaceModuleSources,
            generatedSourceNames = generatedSourceNames,
            precompiledSourceNames = precompiledSourceNames,
            entryGeneratedSourceName = requireNotNull(
                generatedSourceNames[executable.entryModulePath],
            ),
        )
    }

    private fun verifiedPackageMetadataSources(
        projectRoot: File,
        metadata: TypeScriptPreparedPackageMetadata?,
    ): Map<String, String> {
        if (metadata == null) return emptyMap()
        val sources = LinkedHashMap<String, String>(metadata.files.size)
        metadata.files.forEach { descriptor ->
            val packageFile = File(projectRoot, descriptor.archivePath).canonicalFile
            require(packageFile.isInsideOrSame(projectRoot) && packageFile.isFile) {
                "TypeScript package metadata changed after compilation."
            }
            require(packageFile.length() == descriptor.size)
            val bytes = packageFile.readBytes()
            try {
                require(
                    bytes.size.toLong() == descriptor.size && sha256(bytes) == descriptor.sha256,
                ) {
                    "TypeScript package metadata changed after compilation."
                }
                sources[packageFile.path] = TypeScriptUtf8.decode(
                    bytes,
                    "TypeScript package metadata",
                )
            } finally {
                bytes.fill(0)
            }
        }
        return sources
    }
}

internal class TypeScriptRuntimeDependencyException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException("$code: $message", cause)

internal data class TypeScriptRuntimeDependencySource(
    val hostPath: String,
    val runtimeRelativePath: String,
    val source: String,
)

internal fun verifiedRuntimeDependencySources(
    projectRoot: File,
    layer: TypeScriptPreparedDependencyLayer?,
): List<TypeScriptRuntimeDependencySource> {
    if (layer == null) return emptyList()
    return layer.files.mapNotNull { descriptor ->
        if (!descriptor.archivePath.startsWith(RUNTIME_DEPENDENCY_ARCHIVE_PREFIX)) {
            return@mapNotNull null
        }
        val runtimeRelativePath = descriptor.archivePath.removePrefix("dependencies/")
        val requested = File(projectRoot, runtimeRelativePath).absoluteFile.normalize()
        val sourceFile = runCatching { requested.canonicalFile }.getOrElse { error ->
            throw TypeScriptRuntimeDependencyException(
                TypeScriptR2Contract.ERROR_RUNTIME_DEPENDENCY_CHANGED,
                "Dependency path cannot be canonicalized: $runtimeRelativePath",
                error,
            )
        }
        if (
            !sourceFile.isInsideOrSame(projectRoot) ||
            requested.path != sourceFile.path ||
            !sourceFile.isFile ||
            sourceFile.length() != descriptor.size
        ) {
            throw TypeScriptRuntimeDependencyException(
                TypeScriptR2Contract.ERROR_RUNTIME_DEPENDENCY_CHANGED,
                "Dependency changed after compilation: $runtimeRelativePath",
            )
        }
        val bytes = sourceFile.readBytes()
        try {
            if (
                bytes.size.toLong() != descriptor.size ||
                sha256(bytes) != descriptor.sha256
            ) {
                throw TypeScriptRuntimeDependencyException(
                    TypeScriptR2Contract.ERROR_RUNTIME_DEPENDENCY_CHANGED,
                    "Dependency content changed after compilation: $runtimeRelativePath",
                )
            }
            TypeScriptRuntimeDependencySource(
                hostPath = sourceFile.path,
                runtimeRelativePath = runtimeRelativePath,
                source = TypeScriptUtf8.decode(
                    bytes,
                    "TypeScript runtime dependency $runtimeRelativePath",
                ),
            )
        } finally {
            bytes.fill(0)
        }
    }
}

private fun TypeScriptPreparedSource.isRuntimeSupportSource(): Boolean =
    projectRelativePath.endsWith(".json", true) ||
        projectRelativePath.endsWith(".js", true) ||
        projectRelativePath.endsWith(".mjs", true) ||
        projectRelativePath.endsWith(".cjs", true)

private fun requireNoRuntimeSupportCollision(
    generatedRelativePath: String,
    runtimeSupportPaths: Set<String>,
) {
    val generatedFolded = generatedRelativePath.asciiCaseFold()
    require(runtimeSupportPaths.none { path -> path.asciiCaseFold() == generatedFolded }) {
        "TypeScript generated module collides with a project runtime support file: " +
            generatedRelativePath
    }
}

private fun String.asciiCaseFold(): String = buildString(length) {
    this@asciiCaseFold.forEach { character ->
        append(if (character in 'A'..'Z') character + 32 else character)
    }
}

private fun readVerifiedProjectSource(
    projectRoot: File,
    source: TypeScriptPreparedSource,
): ByteArray {
    val original = requireNotNull(source.originalFile) {
        "TypeScript project source has no local snapshot identity."
    }
    val expected = File(projectRoot, source.projectRelativePath).canonicalFile
    require(expected.isInsideOrSame(projectRoot) && expected.path == original.canonicalPath) {
        "TypeScript project source changed location after compilation."
    }
    require(
        expected.isFile && expected.length() in
            0..TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
    ) {
        "TypeScript project source changed after compilation."
    }
    return expected.readBytes().also { bytes ->
        require(sha256(bytes) == source.sha256) {
            "TypeScript project source changed after compilation."
        }
    }
}

private fun writeRuntimeFile(root: File, relativePath: String, bytes: ByteArray): File {
    require(
        relativePath.isNotBlank() && !relativePath.startsWith('/') && '\\' !in relativePath &&
            relativePath.split('/').all { segment ->
                segment.isNotBlank() && segment != "." && segment != ".."
            },
    )
    val target = File(root, relativePath).absoluteFile.normalize()
    require(target.isInsideOrSame(root))
    val parent = requireNotNull(target.parentFile)
    if (!parent.exists() && !parent.mkdirs()) {
        throw IOException("Unable to create a TypeScript runtime module directory.")
    }
    FileOutputStream(target, false).use { output -> output.write(bytes) }
    return target
}

private fun File.isInsideOrSame(root: File): Boolean {
    val rootPath = root.absolutePath.trimEnd(File.separatorChar)
    val candidatePath = absolutePath
    return candidatePath == rootPath || candidatePath.startsWith("$rootPath${File.separator}")
}

private const val RUNTIME_DEPENDENCY_ARCHIVE_PREFIX = "dependencies/node_modules/"
