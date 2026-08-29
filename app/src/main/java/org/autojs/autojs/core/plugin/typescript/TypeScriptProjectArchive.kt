package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Builds one bounded, immutable snapshot of a local TypeScript project directory. */
internal object TypeScriptProjectArchive {

    fun write(
        projectRoot: File,
        entryFile: File,
        requestId: String,
        projectId: String,
        authority: TypeScriptCompilerAuthority,
        destination: File,
        sourceContentReader: TypeScriptSourceContentReader =
            PlaintextTypeScriptSourceContentReader,
        sourceOverlays: Map<String, ByteArray> = emptyMap(),
    ): TypeScriptPreparedCompilation {
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)

        val root = projectRoot.normalizedCanonicalFile()
        val entry = entryFile.normalizedCanonicalFile()
        require(root.isDirectory) { "TypeScript project root is not a directory." }
        require(entry.isFile && entry.isInside(root)) {
            "TypeScript project entry is outside its project root."
        }

        val sources = collectSources(
            root = root,
            targetProfile = authority.targetProfile,
            sourceContentReader = sourceContentReader,
            sourceOverlays = sourceOverlays,
        )
        val entryRelativePath = entry.relativePathInside(root)
        val entryPath = "sources/$entryRelativePath"
        require(sources.any { source -> source.archivePath == entryPath }) {
            "TypeScript project entry is not an admitted source file."
        }
        val rootPaths = sources.asSequence()
            .filter { source -> isExecutableSource(source.projectRelativePath, authority.targetProfile) }
            .map(ProjectSource::archivePath)
            .sorted()
            .toList()
        require(entryPath in rootPaths) {
            "TypeScript project entry is not executable for ${authority.targetProfile}."
        }

        val configBytes = when (authority.configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> null
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> {
                val config = File(root, CONTROLLED_CONFIG_PATH)
                if (config.isFile) {
                    readBounded(config, TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong())
                } else {
                    EMPTY_TSCONFIG.copyOf()
                }
            }
            else -> error("Unsupported TypeScript config kind: ${authority.configKind}")
        }
        val packageSnapshot = if (
            authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE
        ) {
            collectPackageMetadata(root, authority)
        } else {
            null
        }
        val dependencySnapshot = collectDependencyLayer(root, authority)

        try {
            val packageMetadata = packageSnapshot?.metadata
            val dependencyLayer = dependencySnapshot?.metadata
            if (
                authority.targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                sources.any { source ->
                    source.projectRelativePath.endsWith(".ts") &&
                        !source.projectRelativePath.endsWith(".d.ts")
                }
            ) {
                requireNotNull(packageMetadata) {
                    "Node .ts project compilation requires a root package.json with an explicit module boundary."
                }
            }

            val configSha256 = configBytes?.let(::sha256)
            val callerFileCount = sources.size +
                (if (configBytes == null) 0 else 1) +
                (packageSnapshot?.files?.size ?: 0)
            require(callerFileCount <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES) {
                "TypeScript project exceeds the ordinary input file-count limit."
            }
            val callerBytes = sources.fold(0L) { total, source ->
                Math.addExact(total, source.bytes.size.toLong())
            }.let { total -> Math.addExact(total, configBytes?.size?.toLong() ?: 0L) }
                .let { total ->
                    packageSnapshot?.files?.fold(total) { current, file ->
                        Math.addExact(current, file.bytes.size.toLong())
                    } ?: total
                }
            require(callerBytes <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES) {
                "TypeScript project exceeds the ordinary input byte limit."
            }

            val inventoryFingerprint = callerInventoryFingerprint(
                sources = sources,
                configBytes = configBytes,
                configSha256 = configSha256,
                packageMetadata = packageMetadata,
            )
            val entrySourceUri = sourceUri(projectId, entryPath)
            val manifest = manifestBytes(
                requestId = requestId,
                projectId = projectId,
                entrySourceUri = entrySourceUri,
                entryPath = entryPath,
                rootPaths = rootPaths,
                sources = sources,
                configBytes = configBytes,
                configSha256 = configSha256,
                packageMetadata = packageMetadata,
                dependencyLayer = dependencyLayer,
                callerInventoryFingerprint = inventoryFingerprint,
                authority = authority,
            )
            require(manifest.size.toLong() <= TypeScriptCompilerContract.MAX_MANIFEST_BYTES) {
                "TypeScript project request manifest exceeds its byte limit."
            }

            val entries = LinkedHashMap<String, ByteArray>(
                sources.size + (dependencySnapshot?.files?.size ?: 0) + 3,
            )
            entries[TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH] = manifest
            configBytes?.let { bytes -> entries[CONTROLLED_CONFIG_PATH] = bytes }
            packageSnapshot?.files?.forEach { file ->
                entries[file.metadata.archivePath] = file.bytes
            }
            sources.forEach { source -> entries[source.archivePath] = source.bytes }
            dependencySnapshot?.files?.forEach { file ->
                entries[file.metadata.archivePath] = file.bytes
            }
            StoredZipV1Writer.write(destination, entries)
            val maximumArchiveBytes = if (dependencyLayer == null) {
                TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES
            } else {
                TypeScriptR2Contract.MAX_INPUT_ARCHIVE_WITH_DEPENDENCIES_BYTES
            }
            require(destination.length() <= maximumArchiveBytes) {
                "TypeScript project input archive exceeds its transport byte limit."
            }

            return TypeScriptPreparedCompilation(
                requestId = requestId,
                projectId = projectId,
                entrySourceUri = entrySourceUri,
                entryPath = entryPath,
                archiveFile = destination,
                inputArchiveSha256 = sha256(destination),
                requestManifestSha256 = sha256(manifest),
                configByteLength = configBytes?.size?.toLong(),
                configSha256 = configSha256,
                authority = authority,
                rootPaths = rootPaths,
                sourceFiles = sources.map { source ->
                    TypeScriptPreparedSource(
                        archivePath = source.archivePath,
                        projectRelativePath = source.projectRelativePath,
                        originalFile = source.file,
                        sha256 = source.sha256,
                    )
                },
                packageMetadata = packageMetadata,
                dependencyLayer = dependencyLayer,
            )
        } finally {
            sources.forEach { source -> source.bytes.fill(0) }
            configBytes?.fill(0)
            packageSnapshot?.files?.forEach { file -> file.bytes.fill(0) }
            dependencySnapshot?.files?.forEach { file -> file.bytes.fill(0) }
        }
    }

    private fun collectSources(
        root: File,
        targetProfile: String,
        sourceContentReader: TypeScriptSourceContentReader,
        sourceOverlays: Map<String, ByteArray>,
    ): List<ProjectSource> {
        val sources = mutableListOf<ProjectSource>()
        try {
            root.walkTopDown()
                .onEnter { directory ->
                    shouldEnterSourceDirectory(root, directory)
                }
                .filter(File::isFile)
                .forEach { candidate ->
                    val file = candidate.normalizedCanonicalFile()
                    require(file.isInside(root)) {
                        "TypeScript project source escapes its project root."
                    }
                    require(candidate.absoluteFile.normalize().path == file.path) {
                        "TypeScript project sources may not traverse symbolic links."
                    }
                    val relativePath = file.relativePathInside(root)
                    if (
                        relativePath.split('/').any { segment ->
                            segment.equals("node_modules", true)
                        }
                    ) {
                        return@forEach
                    }
                    if (!isSnapshotSource(relativePath, targetProfile)) return@forEach
                    requireCanonicalRelativePath(relativePath)
                    val bytes = sourceContentReader.read(
                        file,
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
                    )
                    var retained = false
                    try {
                        require(
                            bytes.size.toLong() <=
                                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
                        ) {
                            "TypeScript project source exceeds its byte limit: $relativePath"
                        }
                        TypeScriptUtf8.decode(bytes, "TypeScript project source $relativePath")
                        sources += ProjectSource(
                            archivePath = "sources/$relativePath",
                            projectRelativePath = relativePath,
                            file = file,
                            bytes = bytes,
                            sha256 = sha256(bytes),
                        )
                        retained = true
                    } finally {
                        if (!retained) bytes.fill(0)
                    }
                }
            sourceOverlays.toSortedMap().forEach { (relativePath, sourceBytes) ->
                requireCanonicalRelativePath(relativePath)
                require(isSnapshotSource(relativePath, targetProfile)) {
                    "TypeScript project overlay is not an admitted source: $relativePath"
                }
                require(
                    sourceBytes.size.toLong() <=
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
                ) {
                    "TypeScript project overlay exceeds its byte limit: $relativePath"
                }
                require(sources.none { source ->
                    source.projectRelativePath.asciiCaseFold() == relativePath.asciiCaseFold()
                }) {
                    "TypeScript project overlay collides with another source: $relativePath"
                }
                val bytes = sourceBytes.copyOf()
                var retained = false
                try {
                    TypeScriptUtf8.decode(bytes, "TypeScript project overlay $relativePath")
                    sources += ProjectSource(
                        archivePath = "sources/$relativePath",
                        projectRelativePath = relativePath,
                        file = null,
                        bytes = bytes,
                        sha256 = sha256(bytes),
                    )
                    retained = true
                } finally {
                    if (!retained) bytes.fill(0)
                }
            }
            sources.sortBy(ProjectSource::archivePath)
            require(sources.isNotEmpty()) {
                "TypeScript project contains no admitted source files."
            }
            require(sources.map(ProjectSource::archivePath).distinct().size == sources.size)
            require(
                sources.map { source -> source.archivePath.asciiCaseFold() }.distinct().size ==
                    sources.size,
            ) {
                "TypeScript project sources contain an ASCII case-fold collision."
            }
            return sources
        } catch (error: Throwable) {
            sources.forEach { source -> source.bytes.fill(0) }
            throw error
        }
    }

    internal fun shouldEnterSourceDirectory(root: File, directory: File): Boolean =
        directory == root || directory.name !in EXCLUDED_DIRECTORY_NAMES

    internal fun isSnapshotSource(relativePath: String, targetProfile: String): Boolean {
        val name = relativePath.substringAfterLast('/')
        if (name in RESERVED_ROOT_FILES || name.equals(PACKAGE_METADATA_PATH, ignoreCase = true)) {
            return false
        }
        if (relativePath.split('/').any { segment -> segment.equals("node_modules", true) }) {
            return false
        }
        if (name.endsWith(".d.mts") || name.endsWith(".d.cts")) {
            return false
        }
        return when {
            targetProfile == TypeScriptCompilerContract.PROFILE_RHINO &&
                name.endsWith(".tsx") -> true
            name.endsWith(".ts") -> true
            targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
                (name.endsWith(".mts") || name.endsWith(".cts")) -> true
            name.endsWith(".json") -> true
            name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs") -> true
            else -> false
        }
    }

    private fun isExecutableSource(relativePath: String, targetProfile: String): Boolean {
        if (relativePath.endsWith(".d.ts")) return false
        return relativePath.endsWith(".ts") ||
            targetProfile == TypeScriptCompilerContract.PROFILE_RHINO &&
            relativePath.endsWith(".tsx") ||
            targetProfile == TypeScriptCompilerContract.PROFILE_NODE &&
            (relativePath.endsWith(".mts") || relativePath.endsWith(".cts"))
    }

    private fun collectPackageMetadata(
        root: File,
        authority: TypeScriptCompilerAuthority,
    ): ProjectPackageMetadataSnapshot? {
        val files = mutableListOf<ProjectPackageMetadataFile>()
        try {
            root.walkTopDown()
                .onEnter { directory ->
                    directory == root || directory.name !in EXCLUDED_DIRECTORY_NAMES
                }
                .filter(File::isFile)
                .filter { candidate ->
                    candidate.name.equals(PACKAGE_METADATA_PATH, ignoreCase = true)
                }
                .forEach { candidate ->
                    val file = candidate.normalizedCanonicalFile()
                    require(file.isInside(root)) {
                        "TypeScript package metadata escapes its project root."
                    }
                    require(candidate.absoluteFile.normalize().path == file.path) {
                        "TypeScript package metadata may not traverse symbolic links."
                    }
                    val relativePath = file.relativePathInside(root)
                    require(candidate.name == PACKAGE_METADATA_PATH) {
                        "TypeScript package metadata must use canonical lowercase package.json: " +
                            relativePath
                    }
                    requirePackageMetadataPath(relativePath)
                    val bytes = readBounded(file, MAX_PACKAGE_METADATA_FILE_BYTES)
                    var retained = false
                    try {
                        files += ProjectPackageMetadataFile(
                            metadata = packageMetadataFile(relativePath, bytes),
                            bytes = bytes,
                        )
                        retained = true
                    } finally {
                        if (!retained) bytes.fill(0)
                    }
                }
            if (files.isEmpty()) return null

            files.sortBy { file -> file.metadata.archivePath }
            require(files.first().metadata.archivePath == PACKAGE_METADATA_PATH) {
                "Nested TypeScript package metadata requires the project package.json."
            }
            require(files.size <= MAX_PACKAGE_METADATA_FILES) {
                "TypeScript package metadata exceeds its file-count limit."
            }
            require(
                files.map { file -> file.metadata.archivePath }.distinct().size == files.size &&
                    files.map { file -> file.metadata.archivePath.asciiCaseFold() }
                        .distinct().size == files.size,
            ) {
                "TypeScript package metadata contains a path collision."
            }
            val dependencyRoots = files.asSequence()
                .map { file -> file.metadata.archivePath }
                .filter { path -> path != PACKAGE_METADATA_PATH }
                .map(::dependencyRootForPackageMetadata)
                .distinct()
                .sorted()
                .toList()
            require(dependencyRoots.size <= MAX_PACKAGE_METADATA_DEPENDENCY_ROOTS) {
                "TypeScript package metadata exceeds its dependency-root limit."
            }
            dependencyRoots.forEach { dependencyRoot ->
                require(files.any { file ->
                    file.metadata.archivePath == "$dependencyRoot/$PACKAGE_METADATA_PATH"
                }) {
                    "TypeScript workspace package root has no package.json: $dependencyRoot"
                }
            }
            val totalBytes = files.fold(0L) { total, file ->
                Math.addExact(total, file.bytes.size.toLong())
            }
            require(totalBytes <= MAX_PACKAGE_METADATA_TOTAL_BYTES) {
                "TypeScript package metadata exceeds its aggregate byte limit."
            }
            val canonicalInventory = buildString {
                append(PACKAGE_METADATA_INVENTORY_DOMAIN)
                dependencyRoots.forEach { rootPath ->
                    append("|root=")
                    append(rootPath)
                }
                files.forEach { file ->
                    append("|file=")
                    append(file.metadata.archivePath)
                    append(',')
                    append(file.metadata.size)
                    append(',')
                    append(file.metadata.sha256)
                }
            }
            require(canonicalInventory.all { character -> character.code in 0x20..0x7e })
            val metadata = TypeScriptPreparedPackageMetadata(
                dependencyRoots = dependencyRoots,
                files = files.map(ProjectPackageMetadataFile::metadata),
                inventoryFingerprint = sha256(
                    canonicalInventory.toByteArray(StandardCharsets.US_ASCII),
                ),
            )
            require(requireNotNull(authority.packageMetadataSchemaRevision) > 0)
            require(requireNotNull(authority.packageMetadataPolicyRevision) > 0)
            requireLowerSha256(
                requireNotNull(authority.packageMetadataPolicyFingerprint),
                "package metadata policy fingerprint",
            )
            return ProjectPackageMetadataSnapshot(metadata = metadata, files = files)
        } catch (error: Throwable) {
            files.forEach { file -> file.bytes.fill(0) }
            throw error
        }
    }

    private fun packageMetadataFile(
        archivePath: String,
        bytes: ByteArray,
    ): TypeScriptPreparedPackageMetadataFile {
        require(bytes.size.toLong() <= MAX_PACKAGE_METADATA_FILE_BYTES)
        val root = TypeScriptStrictJson.parseObject(
            bytes = bytes,
            maximumDepth = MAX_PACKAGE_METADATA_JSON_DEPTH,
            maximumNodes = MAX_PACKAGE_METADATA_JSON_NODES,
        )
        require("scripts" !in root) {
            "TypeScript package metadata may not carry lifecycle scripts."
        }
        val typeState = when {
            "type" !in root -> PACKAGE_TYPE_MISSING
            root["type"] == PACKAGE_TYPE_MODULE -> PACKAGE_TYPE_MODULE
            root["type"] == PACKAGE_TYPE_COMMONJS -> PACKAGE_TYPE_COMMONJS
            else -> error("TypeScript package metadata has an unsupported top-level type value.")
        }
        val digest = sha256(bytes)
        return TypeScriptPreparedPackageMetadataFile(
            archivePath = archivePath,
            size = bytes.size.toLong(),
            sha256 = digest,
            typeState = typeState,
        )
    }

    private fun requirePackageMetadataPath(path: String) {
        require(
            path.length <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES &&
                PACKAGE_METADATA_PATH_PATTERN.matches(path) &&
                path.split('/').none { segment -> segment.equals("node_modules", true) },
        ) {
            "TypeScript package metadata is outside the controlled packages workspace: $path"
        }
        if (path == PACKAGE_METADATA_PATH) return
        require(path.startsWith(PACKAGE_METADATA_DEPENDENCY_PREFIX)) {
            "TypeScript package metadata is outside the controlled packages workspace: $path"
        }
        val segments = path.split('/')
        require(segments.size >= 3 && segments.last() == PACKAGE_METADATA_PATH) {
            "TypeScript package metadata path is not canonical: $path"
        }
    }

    private fun dependencyRootForPackageMetadata(path: String): String {
        val segments = path.split('/')
        val dependencyRoot = if (segments.getOrNull(1)?.startsWith('@') == true) {
            require(segments.size >= 4)
            segments.take(3).joinToString("/")
        } else {
            require(segments.size >= 3)
            segments.take(2).joinToString("/")
        }
        require(PACKAGE_METADATA_DEPENDENCY_ROOT_PATTERN.matches(dependencyRoot)) {
            "TypeScript workspace package root is not canonical: $dependencyRoot"
        }
        return dependencyRoot
    }

    /**
     * Captures node_modules as a separate immutable namespace. Its files are deliberately absent
     * from the caller inventory and caller quotas; only this independent policy is charged.
     */
    private fun collectDependencyLayer(
        root: File,
        authority: TypeScriptCompilerAuthority,
    ): ProjectDependencyLayerSnapshot? {
        val requestedRoot = File(root, DEPENDENCY_SOURCE_DIRECTORY)
        if (!requestedRoot.exists()) return null
        val dependencyRoot = requestedRoot.normalizedCanonicalFile()
        require(dependencyRoot.isDirectory && dependencyRoot.isInside(root)) {
            "TypeScript dependency layer is outside its project root."
        }
        require(requestedRoot.absoluteFile.normalize().path == dependencyRoot.path) {
            "TypeScript dependency root may not be a symbolic link."
        }
        require(
            authority.dependencyLayerSchemaRevision ==
                TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION &&
                authority.dependencyLayerPolicyRevision ==
                TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
        ) { "TypeScript dependency-layer authority is not supported." }
        val policyFingerprint = requireNotNull(authority.dependencyLayerPolicyFingerprint)
        requireLowerSha256(policyFingerprint, "dependency layer policy fingerprint")

        val files = mutableListOf<ProjectDependencyLayerFile>()
        val boundaries = mutableListOf<TypeScriptDependencyBoundary>()
        try {
            dependencyRoot.walkTopDown()
                .onEnter { directory ->
                    directory == dependencyRoot ||
                        directory.name.asciiCaseFold() !in
                        DEPENDENCY_EXCLUDED_DIRECTORY_NAMES
                }
                .filter(File::isFile)
                .forEach { candidate ->
                    val file = candidate.normalizedCanonicalFile()
                    require(file.isInside(dependencyRoot)) {
                        "TypeScript dependency file escapes node_modules."
                    }
                    require(candidate.absoluteFile.normalize().path == file.path) {
                        "TypeScript dependency files may not traverse symbolic links."
                    }
                    val relativePath = file.path
                        .removePrefix(dependencyRoot.path + File.separator)
                        .replace(File.separatorChar, '/')
                    TypeScriptDependencyBoundaryPolicy.inspectPath(relativePath)
                        ?.let(boundaries::add)
                    if (!isCompilationRelevantDependencyPath(relativePath)) return@forEach
                    val archivePath = DEPENDENCY_ARCHIVE_PREFIX + relativePath
                    requireCanonicalDependencyArchivePath(archivePath)
                    val bytes = readBounded(
                        file,
                        TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_SINGLE_FILE_BYTES,
                    )
                    var retained = false
                    try {
                        TypeScriptDependencyBoundaryPolicy.inspectPackageJson(relativePath, bytes)
                            ?.let(boundaries::add)
                        TypeScriptUtf8.decode(
                            bytes,
                            "TypeScript dependency asset $relativePath",
                        )
                        files += ProjectDependencyLayerFile(
                            metadata = TypeScriptPreparedDependencyLayerFile(
                                archivePath = archivePath,
                                size = bytes.size.toLong(),
                                sha256 = sha256(bytes),
                            ),
                            bytes = bytes,
                        )
                        retained = true
                    } finally {
                        if (!retained) bytes.fill(0)
                    }
                }
            TypeScriptDependencyBoundaryPolicy.requireSupported(boundaries)
            if (files.isEmpty()) return null

            val identity = collectDependencyIdentity(root)
            identity?.let(files::add)
            files.sortBy { file -> file.metadata.archivePath }
            require(files.size <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES) {
                "TypeScript dependency layer exceeds its independent file-count limit."
            }
            require(
                files.map { file -> file.metadata.archivePath }.distinct().size == files.size &&
                    files.map { file -> file.metadata.archivePath.asciiCaseFold() }
                        .distinct().size == files.size,
            ) { "TypeScript dependency layer contains a path collision." }

            val totalBytes = files.fold(0L) { total, file ->
                Math.addExact(total, file.metadata.size)
            }
            require(totalBytes <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES) {
                "TypeScript dependency layer exceeds its independent aggregate byte limit."
            }
            val totalPathBytes = files.fold(0L) { total, file ->
                Math.addExact(
                    total,
                    TypeScriptUtf8.encode(
                        file.metadata.archivePath,
                        "dependency layer path",
                    ).size.toLong(),
                )
            }
            require(totalPathBytes <= TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_PATH_BYTES) {
                "TypeScript dependency paths exceed their independent metadata limit."
            }

            val descriptors = files.map(ProjectDependencyLayerFile::metadata)
            val inventoryFingerprint = dependencyInventoryFingerprint(descriptors)
            val identityKind = when (identity?.metadata?.archivePath) {
                DEPENDENCY_PACKAGE_LOCK_ARCHIVE_PATH -> DEPENDENCY_IDENTITY_PACKAGE_LOCK
                DEPENDENCY_PNPM_LOCK_ARCHIVE_PATH -> DEPENDENCY_IDENTITY_PNPM_LOCK
                else -> DEPENDENCY_IDENTITY_CONTENT
            }
            val identityPath = identity?.metadata?.archivePath.orEmpty()
            val identityFingerprint = identity?.metadata?.sha256 ?: inventoryFingerprint
            val fingerprint = dependencyLayerFingerprint(
                identityKind = identityKind,
                identityPath = identityPath,
                identityFingerprint = identityFingerprint,
                inventoryFingerprint = inventoryFingerprint,
                policyFingerprint = policyFingerprint,
            )
            return ProjectDependencyLayerSnapshot(
                metadata = TypeScriptPreparedDependencyLayer(
                    identityKind = identityKind,
                    identityPath = identityPath,
                    identityFingerprint = identityFingerprint,
                    inventoryFingerprint = inventoryFingerprint,
                    fingerprint = fingerprint,
                    files = descriptors,
                    totalBytes = totalBytes,
                    totalPathBytes = totalPathBytes,
                ),
                files = files,
            )
        } catch (error: Throwable) {
            files.forEach { file -> file.bytes.fill(0) }
            throw error
        }
    }

    private fun collectDependencyIdentity(root: File): ProjectDependencyLayerFile? {
        val candidates = listOf(
            Triple(
                DEPENDENCY_PACKAGE_LOCK_FILE,
                DEPENDENCY_PACKAGE_LOCK_ARCHIVE_PATH,
                DEPENDENCY_IDENTITY_PACKAGE_LOCK,
            ),
            Triple(
                DEPENDENCY_PNPM_LOCK_FILE,
                DEPENDENCY_PNPM_LOCK_ARCHIVE_PATH,
                DEPENDENCY_IDENTITY_PNPM_LOCK,
            ),
        )
        val selected = candidates.firstOrNull { candidate -> File(root, candidate.first).isFile }
            ?: return null
        val requestedFile = File(root, selected.first)
        val file = requestedFile.normalizedCanonicalFile()
        require(file.isInside(root) && requestedFile.absoluteFile.normalize().path == file.path) {
            "TypeScript dependency lockfile may not traverse a symbolic link."
        }
        val bytes = readBounded(
            file,
            TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_LOCKFILE_BYTES,
        )
        var retained = false
        try {
            TypeScriptUtf8.decode(bytes, "TypeScript dependency ${selected.first}")
            val result = ProjectDependencyLayerFile(
                metadata = TypeScriptPreparedDependencyLayerFile(
                    archivePath = selected.second,
                    size = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                ),
                bytes = bytes,
            )
            retained = true
            return result
        } finally {
            if (!retained) bytes.fill(0)
        }
    }

    private fun isCompilationRelevantDependencyPath(relativePath: String): Boolean {
        val segments = relativePath.split('/')
        if (segments.size < 2) return false
        val foldedSegments = segments.map { segment -> segment.asciiCaseFold() }
        if (foldedSegments.any(DEPENDENCY_EXCLUDED_DIRECTORY_NAMES::contains)) return false
        val name = foldedSegments.last()
        return DEPENDENCY_EXCLUDED_SUFFIXES.none(name::endsWith) &&
            DEPENDENCY_ALLOWED_SUFFIXES.any(name::endsWith)
    }

    private fun requireCanonicalDependencyArchivePath(path: String) {
        val encoded = TypeScriptUtf8.encode(path, "TypeScript dependency archive path")
        require(
            path.startsWith(DEPENDENCY_ARCHIVE_PREFIX) &&
                encoded.size <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES &&
                '\\' !in path,
        ) { "TypeScript dependency path is not canonical." }
        val segments = path.split('/')
        require(
            segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                segments.all { segment ->
                    segment.isNotEmpty() && segment != "." && segment != ".." &&
                        segment.none { character -> Character.isISOControl(character.code) }
                },
        ) { "TypeScript dependency path is not canonical." }
    }

    private fun dependencyInventoryFingerprint(
        files: List<TypeScriptPreparedDependencyLayerFile>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DEPENDENCY_INVENTORY_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        digest.update('\n'.code.toByte())
        files.forEach { file ->
            digest.update(file.archivePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

    private fun dependencyLayerFingerprint(
        identityKind: String,
        identityPath: String,
        identityFingerprint: String,
        inventoryFingerprint: String,
        policyFingerprint: String,
    ): String {
        val canonical = buildString {
            append(DEPENDENCY_FINGERPRINT_DOMAIN)
            append("|schemaRevision=")
            append(TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION)
            append("|policyRevision=")
            append(TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION)
            append("|policyFingerprint=")
            append(policyFingerprint)
            append("|identityKind=")
            append(identityKind)
            append("|identityPath=")
            append(identityPath)
            append("|identityFingerprint=")
            append(identityFingerprint)
            append("|inventoryFingerprint=")
            append(inventoryFingerprint)
        }
        require(canonical.all { character -> character.code in 0x20..0x7e })
        return sha256(canonical.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun callerInventoryFingerprint(
        sources: List<ProjectSource>,
        configBytes: ByteArray?,
        configSha256: String?,
        packageMetadata: TypeScriptPreparedPackageMetadata?,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INPUT_INVENTORY_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        sources.forEach { source ->
            digest.updateInventoryRecord(
                domain = "source",
                path = "/${source.archivePath}",
                size = source.bytes.size.toLong(),
                sha256 = source.sha256,
            )
        }
        configBytes?.let { bytes ->
            digest.updateInventoryRecord(
                domain = "config",
                path = CONTROLLED_CONFIG_PATH,
                size = bytes.size.toLong(),
                sha256 = requireNotNull(configSha256),
            )
        }
        packageMetadata?.files?.forEach { metadata ->
            digest.updateInventoryRecord(
                domain = "package-metadata",
                path = "/${metadata.archivePath}",
                size = metadata.size,
                sha256 = metadata.sha256,
            )
        }
        return digest.digest().toLowerHex()
    }

    private fun MessageDigest.updateInventoryRecord(
        domain: String,
        path: String,
        size: Long,
        sha256: String,
    ) {
        update(domain.toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(path.toByteArray(StandardCharsets.UTF_8))
        update(0)
        update(size.toString().toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(sha256.toByteArray(StandardCharsets.US_ASCII))
        update('\n'.code.toByte())
    }

    private fun manifestBytes(
        requestId: String,
        projectId: String,
        entrySourceUri: String,
        entryPath: String,
        rootPaths: List<String>,
        sources: List<ProjectSource>,
        configBytes: ByteArray?,
        configSha256: String?,
        packageMetadata: TypeScriptPreparedPackageMetadata?,
        dependencyLayer: TypeScriptPreparedDependencyLayer?,
        callerInventoryFingerprint: String,
        authority: TypeScriptCompilerAuthority,
    ): ByteArray {
        val json = buildString(
            4_096 + sources.size * 160 + (dependencyLayer?.files?.size ?: 0) * 180,
        ) {
            append('{')
            appendJsonField("schema", TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA)
            append(',')
            appendJsonField("revision", TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
            append(',')
            appendJsonField(
                "inputQuotaRevision",
                TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
            )
            append(',')
            appendJsonField("contractVersion", TypeScriptCompilerContract.VERSION)
            append(',')
            appendJsonField("contractHash", TypeScriptCompilerContract.HASH)
            append(',')
            appendJsonField(
                "inputArchiveTransportVersion",
                TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
            )
            append(',')
            appendJsonField("requestId", requestId)
            append(',')
            appendJsonField("projectId", projectId)
            append(',')
            appendJsonField("entrySourceUri", entrySourceUri)
            append(',')
            appendJsonField("entryPath", entryPath)
            append(',')
            appendJsonField("targetProfile", authority.targetProfile)
            append(',')
            appendJsonField("compilationMode", TypeScriptCompilerContract.MODE_DEVELOPMENT)
            append(',')
            appendJsonField("profileSchemaRevision", authority.profileSchemaRevision)
            append(',')
            appendJsonField("profileRevision", authority.profileRevision)
            append(',')
            appendJsonField(
                "runtimeCapabilitySchemaRevision",
                TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            )
            append(',')
            appendJsonField("configSchemaRevision", authority.configSchemaRevision)
            append(',')
            appendJsonField(
                "runtimeCapabilitiesFingerprint",
                authority.runtimeCapabilitiesFingerprint,
            )
            append(',')
            appendJsonField("compilerPackage", TypeScriptPluginIds.ID)
            append(',')
            appendJsonField("compilerVersion", authority.compilerVersion)
            append(',')
            appendJsonField(
                "compilerDistributionFingerprint",
                authority.compilerDistributionFingerprint,
            )
            append(',')
            appendJsonField("declarationsFingerprint", authority.declarationsFingerprint)
            append(',')
            appendJsonField(
                "declarationProfileFingerprint",
                authority.declarationProfileFingerprint,
            )
            append(',')
            appendJsonField("configKind", authority.configKind)
            if (configBytes != null) {
                append(',')
                appendJsonString("config")
                append(':')
                append('{')
                appendJsonField("path", CONTROLLED_CONFIG_PATH)
                append(',')
                appendJsonField("size", configBytes.size.toLong())
                append(',')
                appendJsonField("sha256", requireNotNull(configSha256))
                append(',')
                appendJsonField("policyRevision", requireNotNull(authority.configPolicyRevision))
                append(',')
                appendJsonField(
                    "policyFingerprint",
                    requireNotNull(authority.configPolicyFingerprint),
                )
                append('}')
            }
            if (packageMetadata != null) {
                append(',')
                appendJsonString("packageMetadata")
                append(':')
                append('{')
                appendJsonField(
                    "schemaRevision",
                    requireNotNull(authority.packageMetadataSchemaRevision),
                )
                append(',')
                appendJsonField(
                    "policyRevision",
                    requireNotNull(authority.packageMetadataPolicyRevision),
                )
                append(',')
                appendJsonField(
                    "policyFingerprint",
                    requireNotNull(authority.packageMetadataPolicyFingerprint),
                )
                append(',')
                appendJsonField("inventoryFingerprint", packageMetadata.inventoryFingerprint)
                append(',')
                appendJsonString("dependencyRoots")
                append(':')
                appendJsonStringArray(packageMetadata.dependencyRoots)
                append(',')
                appendJsonString("files")
                append(':')
                append('[')
                packageMetadata.files.forEachIndexed { index, file ->
                    if (index > 0) append(',')
                    append('{')
                    appendJsonField("path", file.archivePath)
                    append(',')
                    appendJsonField("size", file.size)
                    append(',')
                    appendJsonField("sha256", file.sha256)
                    append('}')
                }
                append(']')
                append('}')
            }
            if (dependencyLayer != null) {
                append(',')
                appendJsonString("dependencyLayer")
                append(':')
                append('{')
                appendJsonField(
                    "schemaRevision",
                    requireNotNull(authority.dependencyLayerSchemaRevision),
                )
                append(',')
                appendJsonField(
                    "policyRevision",
                    requireNotNull(authority.dependencyLayerPolicyRevision),
                )
                append(',')
                appendJsonField(
                    "policyFingerprint",
                    requireNotNull(authority.dependencyLayerPolicyFingerprint),
                )
                append(',')
                appendJsonField("identityKind", dependencyLayer.identityKind)
                append(',')
                appendJsonField("identityPath", dependencyLayer.identityPath)
                append(',')
                appendJsonField(
                    "identityFingerprint",
                    dependencyLayer.identityFingerprint,
                )
                append(',')
                appendJsonField(
                    "inventoryFingerprint",
                    dependencyLayer.inventoryFingerprint,
                )
                append(',')
                appendJsonField("fingerprint", dependencyLayer.fingerprint)
                append(',')
                appendJsonField("fileCount", dependencyLayer.files.size)
                append(',')
                appendJsonField("totalBytes", dependencyLayer.totalBytes)
                append(',')
                appendJsonField("totalPathBytes", dependencyLayer.totalPathBytes)
                append(',')
                appendJsonString("files")
                append(':')
                append('[')
                dependencyLayer.files.forEachIndexed { index, file ->
                    if (index > 0) append(',')
                    append('{')
                    appendJsonField("path", file.archivePath)
                    append(',')
                    appendJsonField("size", file.size)
                    append(',')
                    appendJsonField("sha256", file.sha256)
                    append('}')
                }
                append(']')
                append('}')
            }
            append(',')
            appendJsonField("callerInventoryFingerprint", callerInventoryFingerprint)
            append(',')
            appendJsonString("rootPaths")
            append(':')
            appendJsonStringArray(rootPaths)
            append(',')
            appendJsonString("files")
            append(':')
            append('[')
            sources.forEachIndexed { index, source ->
                if (index > 0) append(',')
                append('{')
                appendJsonField("path", source.archivePath)
                append(',')
                appendJsonField("sha256", source.sha256)
                append(',')
                appendJsonField("size", source.bytes.size.toLong())
                append('}')
            }
            append(']')
            append('}')
        }
        return TypeScriptUtf8.encode(json, "TypeScript project request manifest")
    }

    private fun sourceUri(projectId: String, entryPath: String): String {
        val namespace = "p-" + sha256(TypeScriptUtf8.encode(projectId, "TypeScript project ID"))
        val encodedPath = entryPath.split('/').joinToString("/") { segment ->
            percentEncodeSegment(segment)
        }
        return "autojs://source/$namespace/$encodedPath".also { uri ->
            require(uri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS)
        }
    }

    private fun percentEncodeSegment(value: String): String {
        val bytes = TypeScriptUtf8.encode(value, "TypeScript project path segment")
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (
                    unsigned in 'a'.code..'z'.code ||
                    unsigned in 'A'.code..'Z'.code ||
                    unsigned in '0'.code..'9'.code ||
                    unsigned == '-'.code || unsigned == '.'.code ||
                    unsigned == '_'.code || unsigned == '~'.code
                ) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
        }
    }

    private fun readBounded(file: File, maximumBytes: Long): ByteArray {
        require(file.length() in 0..maximumBytes) {
            "TypeScript project file exceeds its byte limit: ${file.path}"
        }
        return file.readBytes().also { bytes ->
            require(bytes.size.toLong() <= maximumBytes)
        }
    }

    private fun requireCanonicalRelativePath(path: String) {
        val encoded = TypeScriptUtf8.encode("sources/$path", "TypeScript project archive path")
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                !path.endsWith('/') &&
                '\\' !in path &&
                encoded.size <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES,
        ) { "TypeScript project path is not canonical." }
        val segments = path.split('/')
        require(
            segments.size + 1 <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                segments.all { segment ->
                    segment.isNotEmpty() && segment != "." && segment != ".." &&
                        segment.none { character ->
                            character.code in 0x00..0x1f || character.code in 0x7f..0x9f
                        }
                },
        ) { "TypeScript project path is not canonical." }
    }

    private fun File.relativePathInside(root: File): String {
        require(isInside(root))
        return path.removePrefix(root.path + File.separator)
            .replace(File.separatorChar, '/')
            .also(::requireCanonicalRelativePath)
    }

    private fun File.isInside(root: File): Boolean =
        path.startsWith(root.path + File.separator)

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(if (character in 'A'..'Z') character + 32 else character)
        }
    }

    private fun StringBuilder.appendJsonField(name: String, value: String) {
        appendJsonString(name)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Int) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonField(name: String, value: Long) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendJsonStringArray(values: List<String>) {
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendJsonString(value)
        }
        append(']')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private data class ProjectSource(
        val archivePath: String,
        val projectRelativePath: String,
        val file: File?,
        val bytes: ByteArray,
        val sha256: String,
    )

    private data class ProjectPackageMetadataFile(
        val metadata: TypeScriptPreparedPackageMetadataFile,
        val bytes: ByteArray,
    )

    private data class ProjectPackageMetadataSnapshot(
        val metadata: TypeScriptPreparedPackageMetadata,
        val files: List<ProjectPackageMetadataFile>,
    )

    private data class ProjectDependencyLayerFile(
        val metadata: TypeScriptPreparedDependencyLayerFile,
        val bytes: ByteArray,
    )

    private data class ProjectDependencyLayerSnapshot(
        val metadata: TypeScriptPreparedDependencyLayer,
        val files: List<ProjectDependencyLayerFile>,
    )

    private val EMPTY_TSCONFIG = "{}".toByteArray(StandardCharsets.UTF_8)
    private val EXCLUDED_DIRECTORY_NAMES = setOf(
        ".git",
        ".gradle",
        ".idea",
        "build",
        "dist",
        "node_modules",
    )
    private val DEPENDENCY_EXCLUDED_DIRECTORY_NAMES = setOf(
        ".bin",
        ".git",
        ".github",
        "bench",
        "benchmark",
        "benchmarks",
        "coverage",
        "doc",
        "docs",
        "example",
        "examples",
        "test",
        "tests",
    )
    private val DEPENDENCY_ALLOWED_SUFFIXES = setOf(
        ".cjs",
        ".cts",
        ".js",
        ".json",
        ".jsx",
        ".mjs",
        ".mts",
        ".ts",
        ".tsx",
    )
    private val DEPENDENCY_EXCLUDED_SUFFIXES = setOf(".map", ".markdown", ".md")
    private val RESERVED_ROOT_FILES = setOf(
        "project.json",
        "tsconfig.json",
        DEPENDENCY_PACKAGE_LOCK_FILE,
        DEPENDENCY_PNPM_LOCK_FILE,
    )
    private const val CONTROLLED_CONFIG_PATH = "tsconfig.json"
    private const val PACKAGE_METADATA_PATH = "package.json"
    private const val PACKAGE_METADATA_DEPENDENCY_PREFIX = "packages/"
    private const val DEPENDENCY_SOURCE_DIRECTORY = "node_modules"
    private const val DEPENDENCY_ARCHIVE_PREFIX = "dependencies/node_modules/"
    private const val DEPENDENCY_PACKAGE_LOCK_FILE = "package-lock.json"
    private const val DEPENDENCY_PNPM_LOCK_FILE = "pnpm-lock.yaml"
    private const val DEPENDENCY_PACKAGE_LOCK_ARCHIVE_PATH =
        "dependencies/lockfiles/package-lock.json"
    private const val DEPENDENCY_PNPM_LOCK_ARCHIVE_PATH =
        "dependencies/lockfiles/pnpm-lock.yaml"
    private const val DEPENDENCY_IDENTITY_PACKAGE_LOCK = "package-lock"
    private const val DEPENDENCY_IDENTITY_PNPM_LOCK = "pnpm-lock"
    private const val DEPENDENCY_IDENTITY_CONTENT = "content"
    private const val DEPENDENCY_INVENTORY_DOMAIN =
        "autojs6.typescript.dependency-layer.inventory.v1"
    private const val DEPENDENCY_FINGERPRINT_DOMAIN =
        "autojs6.typescript.dependency-layer.fingerprint.v1"
    private const val PACKAGE_METADATA_INVENTORY_DOMAIN =
        "autojs6.typescript.package-metadata.inventory.v1"
    private const val PACKAGE_TYPE_MISSING = "missing"
    private const val PACKAGE_TYPE_MODULE = "module"
    private const val PACKAGE_TYPE_COMMONJS = "commonjs"
    private const val MAX_PACKAGE_METADATA_FILE_BYTES = 64L * 1024L
    private const val MAX_PACKAGE_METADATA_TOTAL_BYTES = 1024L * 1024L
    private const val MAX_PACKAGE_METADATA_FILES = 256
    private const val MAX_PACKAGE_METADATA_DEPENDENCY_ROOTS = 128
    private const val MAX_PACKAGE_METADATA_JSON_DEPTH = 16
    private const val MAX_PACKAGE_METADATA_JSON_NODES = 4 * 1024
    private val PACKAGE_METADATA_PATH_PATTERN = Regex(
        "(?:package\\.json|packages/(?:[A-Za-z0-9@._+~-]+/)+package\\.json)",
    )
    private val PACKAGE_METADATA_DEPENDENCY_ROOT_PATTERN = Regex(
        "packages/(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*",
    )
    private const val INPUT_INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n"
    private const val HEX_DIGITS = "0123456789ABCDEF"
}
