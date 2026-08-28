package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.autojs.plugin.editor.api.EditorPluginProjectSnapshot
import org.autojs.plugin.editor.api.EditorPluginProjectSnapshotContract
import org.autojs.plugin.editor.api.EditorPluginProjectSnapshotProvider
import org.autojs.plugin.editor.api.EditorPluginProjectSource

/** Supplies Ace contract 2 with the same admitted source inventory used before compilation. */
internal object TypeScriptEditorProjectSnapshotProvider : EditorPluginProjectSnapshotProvider {

    override fun capture(documentPath: String): EditorPluginProjectSnapshot? =
        runCatching { captureRequired(documentPath) }.getOrNull()

    internal fun captureRequired(documentPath: String): EditorPluginProjectSnapshot? {
        val requestedDocument = localDocumentFile(documentPath) ?: return null
        val document = requestedDocument.normalizedCanonicalFile()
        if (!document.isFile || requestedDocument.absoluteFile.normalize().path != document.path) {
            return null
        }
        val targetProfile = targetProfile(document)
        val root = findProjectRoot(document, targetProfile) ?: return null
        if (!document.isInside(root)) return null
        val documentRelativePath = document.relativePathInside(root)
        val sources = collectSources(root, targetProfile)
        if (sources.none { source -> source.relativePath == documentRelativePath }) return null
        val snapshot = EditorPluginProjectSnapshot(
            schemaRevision = EditorPluginProjectSnapshotContract.SCHEMA_REVISION,
            projectRootPath = root.path,
            documentPath = document.path,
            documentRelativePath = documentRelativePath,
            targetProfile = targetProfile,
            sourceFiles = sources,
            sourceInventoryFingerprint = inventoryFingerprint(sources),
            sourceFileCount = sources.size,
            sourceByteLength = sources.sumOf(EditorPluginProjectSource::utf8ByteLength),
        )
        require(EditorPluginProjectSnapshotContract.isSupported(snapshot)) {
            "TypeScript editor project snapshot violates editor contract 2."
        }
        return snapshot
    }

    private fun collectSources(
        root: File,
        targetProfile: String,
    ): List<EditorPluginProjectSource> {
        val sources = ArrayList<EditorPluginProjectSource>()
        var totalBytes = 0L
        root.walkTopDown()
            .onEnter { directory ->
                TypeScriptProjectArchive.shouldEnterSourceDirectory(root, directory)
            }
            .filter(File::isFile)
            .forEach { candidate ->
                val file = candidate.normalizedCanonicalFile()
                require(file.isInside(root)) {
                    "TypeScript editor project source escapes its project root."
                }
                require(candidate.absoluteFile.normalize().path == file.path) {
                    "TypeScript editor project sources may not traverse symbolic links."
                }
                val relativePath = file.relativePathInside(root)
                if (!TypeScriptProjectArchive.isSnapshotSource(relativePath, targetProfile)) {
                    return@forEach
                }
                require(sources.size < EditorPluginProjectSnapshotContract.MAX_SOURCE_FILES) {
                    "TypeScript editor project exceeds its source file-count limit."
                }
                require(
                    file.length() in 0..EditorPluginProjectSnapshotContract.MAX_SINGLE_SOURCE_BYTES,
                ) {
                    "TypeScript editor project source exceeds its byte limit: $relativePath"
                }
                val bytes = file.readBytes()
                try {
                    require(
                        bytes.size.toLong() <=
                            EditorPluginProjectSnapshotContract.MAX_SINGLE_SOURCE_BYTES,
                    ) {
                        "TypeScript editor project source changed beyond its byte limit: $relativePath"
                    }
                    totalBytes = Math.addExact(totalBytes, bytes.size.toLong())
                    require(totalBytes <= EditorPluginProjectSnapshotContract.MAX_SOURCE_BYTES) {
                        "TypeScript editor project exceeds its total source byte limit."
                    }
                    sources += EditorPluginProjectSource(
                        relativePath = relativePath,
                        text = TypeScriptUtf8.decode(
                            bytes,
                            "TypeScript editor project source $relativePath",
                        ),
                        utf8ByteLength = bytes.size.toLong(),
                        sha256 = sha256(bytes),
                    )
                } finally {
                    bytes.fill(0)
                }
            }
        sources.sortBy(EditorPluginProjectSource::relativePath)
        require(sources.isNotEmpty()) {
            "TypeScript editor project contains no admitted source files."
        }
        return sources
    }

    private fun targetProfile(document: File): String {
        val foldedName = document.name.lowercase(Locale.ROOT)
        if (foldedName.endsWith(".mts") || foldedName.endsWith(".cts")) {
            return EditorPluginProjectSnapshotContract.TARGET_PROFILE_NODE
        }
        var directory = document.parentFile
        while (directory != null) {
            val projectConfig = File(directory, PROJECT_CONFIG_NAME)
            if (projectConfig.isFile) {
                val projectType = readProjectType(projectConfig)
                return if (projectType.equals(PROJECT_TYPE_NODE, ignoreCase = true)) {
                    EditorPluginProjectSnapshotContract.TARGET_PROFILE_NODE
                } else {
                    EditorPluginProjectSnapshotContract.TARGET_PROFILE_RHINO
                }
            }
            directory = directory.parentFile
        }
        return EditorPluginProjectSnapshotContract.TARGET_PROFILE_RHINO
    }

    private fun findProjectRoot(document: File, targetProfile: String): File? {
        var directory = document.parentFile ?: return null
        while (true) {
            val hasBoundary = PROJECT_BOUNDARY_NAMES.any { name -> File(directory, name).isFile } ||
                targetProfile == EditorPluginProjectSnapshotContract.TARGET_PROFILE_NODE &&
                File(directory, PACKAGE_METADATA_NAME).isFile
            if (hasBoundary) return directory.normalizedCanonicalFile()
            directory = directory.parentFile ?: return null
        }
    }

    private fun readProjectType(projectConfig: File): String? {
        if (projectConfig.length() !in 0..MAX_PROJECT_CONFIG_BYTES) return null
        val bytes = runCatching { projectConfig.readBytes() }.getOrNull() ?: return null
        return try {
            val root = runCatching {
                TypeScriptStrictJson.parseObject(bytes, maximumDepth = 16, maximumNodes = 4 * 1024)
            }.getOrNull() ?: return null
            sequenceOf("type", "projectType")
                .mapNotNull { key -> (root[key] as? String)?.takeIf(String::isNotBlank) }
                .firstOrNull()
        } finally {
            bytes.fill(0)
        }
    }

    private fun inventoryFingerprint(sources: List<EditorPluginProjectSource>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INVENTORY_DOMAIN.toByteArray(StandardCharsets.US_ASCII))
        digest.update('\n'.code.toByte())
        sources.forEach { source ->
            digest.update(source.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(source.utf8ByteLength.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(source.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

    private fun localDocumentFile(documentPath: String): File? {
        val value = documentPath.substringBefore('?').substringBefore('#').trim()
        if (value.isEmpty()) return null
        if (WINDOWS_ABSOLUTE_PATH.matches(value)) return File(value)
        if (value.startsWith("file:", ignoreCase = true)) {
            return runCatching { File(URI(value)) }.getOrNull()
        }
        if (URI_SCHEME.containsMatchIn(value)) return null
        return File(value).takeIf(File::isAbsolute)
    }

    private fun File.relativePathInside(root: File): String {
        val path = root.toPath().relativize(toPath()).toString().replace(File.separatorChar, '/')
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path)
        require(path.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." &&
                segment.none { character -> Character.isISOControl(character.code) }
        })
        return path
    }

    private fun File.isInside(root: File): Boolean = toPath().startsWith(root.toPath())

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }

    private const val INVENTORY_DOMAIN = "autojs6.editor.project-source.inventory.v1"
    private const val PROJECT_CONFIG_NAME = "project.json"
    private const val PACKAGE_METADATA_NAME = "package.json"
    private const val PROJECT_TYPE_NODE = "node"
    private const val MAX_PROJECT_CONFIG_BYTES = 1024L * 1024L
    private val PROJECT_BOUNDARY_NAMES = setOf(PROJECT_CONFIG_NAME, "tsconfig.json")
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[\\\\/].+")
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
