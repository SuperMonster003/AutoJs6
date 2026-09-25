package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Re-captures the bounded dependency inventory used to authorize editor definition targets. */
internal object TypeScriptEditorDependencySnapshotProvider {

    data class Snapshot(
        val projectRootPath: String,
        val inventoryFingerprint: String,
        val contentSha256ByRelativePath: Map<String, String>,
    )

    fun capture(projectRootPath: String): Snapshot? =
        runCatching { captureRequired(projectRootPath) }.getOrNull()

    internal fun captureRequired(projectRootPath: String): Snapshot? {
        val requestedRoot = File(projectRootPath)
        if (!requestedRoot.isAbsolute) return null
        val root = requestedRoot.normalizedCanonicalFile()
        if (!root.isDirectory || requestedRoot.absoluteFile.normalize().path != root.path) return null
        val requestedDependencyRoot = File(root, NODE_MODULES_DIRECTORY)
        if (!requestedDependencyRoot.exists()) return null
        val dependencyRoot = requestedDependencyRoot.normalizedCanonicalFile()
        require(dependencyRoot.isDirectory && dependencyRoot.isInside(root))
        require(requestedDependencyRoot.absoluteFile.normalize().path == dependencyRoot.path)

        val descriptors = ArrayList<Descriptor>()
        var totalBytes = 0L
        var totalPathBytes = 0L
        dependencyRoot.walkTopDown()
            .onEnter { directory ->
                if (directory == dependencyRoot) {
                    true
                } else {
                    require(
                        directory.absoluteFile.normalize().path ==
                            directory.normalizedCanonicalFile().path,
                    )
                    directory.name.lowercase(Locale.ROOT) !in EXCLUDED_DIRECTORY_NAMES
                }
            }
            .filter(File::isFile)
            .forEach { candidate ->
                val file = candidate.normalizedCanonicalFile()
                require(file.isInside(dependencyRoot))
                require(candidate.absoluteFile.normalize().path == file.path)
                val relativePath = file.relativePathInside(dependencyRoot)
                if (!isCompilationRelevantDependencyPath(relativePath)) return@forEach
                val archivePath = DEPENDENCY_ARCHIVE_PREFIX + relativePath
                val bytes = readBoundedUtf8(file, MAX_SINGLE_FILE_BYTES)
                try {
                    totalBytes = Math.addExact(totalBytes, bytes.size.toLong())
                    totalPathBytes = Math.addExact(
                        totalPathBytes,
                        archivePath.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                    )
                    require(descriptors.size < MAX_FILES)
                    require(totalBytes <= MAX_TOTAL_BYTES)
                    require(totalPathBytes <= MAX_PATH_BYTES)
                    descriptors += Descriptor(
                        archivePath = archivePath,
                        projectRelativePath = "$NODE_MODULES_DIRECTORY/$relativePath",
                        size = bytes.size.toLong(),
                        sha256 = sha256(bytes),
                    )
                } finally {
                    bytes.fill(0)
                }
            }
        if (descriptors.isEmpty()) return null

        dependencyIdentity(root)?.let { identity ->
            totalBytes = Math.addExact(totalBytes, identity.size)
            totalPathBytes = Math.addExact(
                totalPathBytes,
                identity.archivePath.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            )
            require(descriptors.size < MAX_FILES)
            require(totalBytes <= MAX_TOTAL_BYTES)
            require(totalPathBytes <= MAX_PATH_BYTES)
            descriptors += identity
        }
        descriptors.sortBy(Descriptor::archivePath)
        require(descriptors.map(Descriptor::archivePath).distinct().size == descriptors.size)
        require(
            descriptors.map { descriptor -> descriptor.archivePath.lowercase(Locale.ROOT) }
                .distinct().size == descriptors.size,
        )
        val contentSha256ByRelativePath = descriptors.mapNotNull { descriptor ->
            descriptor.projectRelativePath?.let { relativePath -> relativePath to descriptor.sha256 }
        }.toMap()
        return Snapshot(
            projectRootPath = root.path,
            inventoryFingerprint = inventoryFingerprint(descriptors),
            contentSha256ByRelativePath = contentSha256ByRelativePath,
        )
    }

    private fun dependencyIdentity(projectRoot: File): Descriptor? {
        val selected = listOf(
            PACKAGE_LOCK_NAME to PACKAGE_LOCK_ARCHIVE_PATH,
            PNPM_LOCK_NAME to PNPM_LOCK_ARCHIVE_PATH,
        ).firstOrNull { (name, _) -> File(projectRoot, name).isFile } ?: return null
        val requested = File(projectRoot, selected.first)
        val file = requested.normalizedCanonicalFile()
        require(file.isInside(projectRoot))
        require(requested.absoluteFile.normalize().path == file.path)
        val bytes = readBoundedUtf8(file, MAX_LOCKFILE_BYTES)
        return try {
            Descriptor(
                archivePath = selected.second,
                projectRelativePath = null,
                size = bytes.size.toLong(),
                sha256 = sha256(bytes),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun readBoundedUtf8(file: File, maximumBytes: Long): ByteArray {
        require(file.isFile && file.length() in 0..maximumBytes)
        val bytes = file.readBytes()
        var valid = false
        try {
            require(bytes.size.toLong() <= maximumBytes)
            TypeScriptUtf8.decode(bytes, "TypeScript editor dependency ${file.name}")
            valid = true
            return bytes
        } finally {
            if (!valid) bytes.fill(0)
        }
    }

    private fun isCompilationRelevantDependencyPath(relativePath: String): Boolean {
        val segments = relativePath.split('/')
        if (segments.size < 2) return false
        val foldedSegments = segments.map { segment -> segment.lowercase(Locale.ROOT) }
        if (foldedSegments.any(EXCLUDED_DIRECTORY_NAMES::contains)) return false
        val name = foldedSegments.last()
        return EXCLUDED_SUFFIXES.none(name::endsWith) && ALLOWED_SUFFIXES.any(name::endsWith)
    }

    private fun inventoryFingerprint(descriptors: List<Descriptor>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INVENTORY_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        digest.update('\n'.code.toByte())
        descriptors.forEach { descriptor ->
            digest.update(descriptor.archivePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(descriptor.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(descriptor.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

    private fun File.relativePathInside(root: File): String {
        val path = relativeTo(root).invariantSeparatorsPath
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path)
        require(path.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.none { character -> Character.isISOControl(character.code) }
        })
        return path
    }

    private fun File.isInside(root: File): Boolean = startsWith(root)

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }

    private data class Descriptor(
        val archivePath: String,
        val projectRelativePath: String?,
        val size: Long,
        val sha256: String,
    )

    private const val NODE_MODULES_DIRECTORY = "node_modules"
    private const val DEPENDENCY_ARCHIVE_PREFIX = "dependencies/node_modules/"
    private const val PACKAGE_LOCK_NAME = "package-lock.json"
    private const val PNPM_LOCK_NAME = "pnpm-lock.yaml"
    private const val PACKAGE_LOCK_ARCHIVE_PATH = "dependencies/lockfiles/package-lock.json"
    private const val PNPM_LOCK_ARCHIVE_PATH = "dependencies/lockfiles/pnpm-lock.yaml"
    private const val INVENTORY_DOMAIN = "autojs6.typescript.dependency-layer.inventory.v1"
    private const val MAX_FILES = 8_192
    private const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L
    private const val MAX_LOCKFILE_BYTES = 8L * 1024L * 1024L
    private const val MAX_PATH_BYTES = 1536L * 1024L
    private val EXCLUDED_DIRECTORY_NAMES = setOf(
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
    private val ALLOWED_SUFFIXES = setOf(
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
    private val EXCLUDED_SUFFIXES = setOf(".map", ".markdown", ".md")
}
