package org.autojs.autojs.core.plugin.explorer.session

import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

/** Validates plugin-supplied output names and relative paths. */
internal object ExplorerActionOutputPathPolicy {

    const val OUTPUT_TREE_MARKER_PREFIX = ".autojs6-explorer-transaction-"

    fun normalizeName(value: String): String {
        require(value.length in 1..ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH) {
            "Explorer Action output name length is invalid"
        }
        require(value == value.trim() && value != "." && value != "..") {
            "Explorer Action output name is invalid"
        }
        require(value.any { it != '.' && !it.isWhitespace() }) {
            "Explorer Action output name is empty"
        }
        require(value.none { it == '/' || it == '\\' || it == '\u0000' || it.code < 0x20 || it.code == 0x7f }) {
            "Explorer Action output name contains an unsafe character"
        }
        return value
    }

    fun normalizeRelativePath(value: String): String {
        require(value.length in 1..ExplorerActionProtocol.MAX_SESSION_RELATIVE_PATH_LENGTH) {
            "Explorer Action output relative path length is invalid"
        }
        require(!value.startsWith('/') && !value.endsWith('/')) {
            "Explorer Action output path is not relative"
        }
        val segments = value.split('/')
        require(segments.isNotEmpty()) { "Explorer Action output path is empty" }
        segments.forEach { segment ->
            normalizeName(segment)
            require(!segment.startsWith(OUTPUT_TREE_MARKER_PREFIX)) {
                "Explorer Action output path uses a reserved name"
            }
        }
        return segments.joinToString("/")
    }

    fun collisionKey(value: String): String = Normalizer.normalize(
        value,
        Normalizer.Form.NFC,
    ).lowercase(Locale.ROOT)
}

/**
 * Process-wide output target reservation registry.
 *
 * Selection and creation happen under the same lock, so concurrent plugin sessions cannot choose
 * the same path between their existence check and transaction commit.
 */
internal object ExplorerActionOutputTargets {

    private const val MAX_AUTO_RENAME_ATTEMPTS = 10_000
    private val lock = Any()
    private val reservedPaths = HashSet<String>()

    fun reserveFile(parent: File, displayName: String, conflictPolicy: Int): File {
        return synchronized(lock) {
            val target = when (conflictPolicy) {
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL -> {
                    File(parent, displayName).also { candidate ->
                        require(!candidate.exists() && candidate.path !in reservedPaths) {
                            "Explorer Action output already exists"
                        }
                    }
                }
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME ->
                    findAvailableFile(parent, displayName)
                else -> throw IllegalArgumentException("Explorer Action output conflict policy is invalid")
            }.canonicalFile
            require(target.parentFile == parent) { "Explorer Action output escapes its parent" }
            check(reservedPaths.add(target.path)) { "Explorer Action output is already reserved" }
            target
        }
    }

    fun reserveDirectory(parent: File, displayName: String, conflictPolicy: Int): File {
        return synchronized(lock) {
            fun existingCollisionKeys(): Set<String> = parent.listFiles()
                ?.mapTo(HashSet()) { ExplorerActionOutputPathPolicy.collisionKey(it.name) }
                ?: throw IOException("Explorer Action parent directory cannot be inspected")

            fun reserve(candidateName: String): File? {
                val candidate = File(parent, candidateName).canonicalFile
                if (candidate.parentFile != parent || candidate.path in reservedPaths) return null
                if (ExplorerActionOutputPathPolicy.collisionKey(candidateName) in existingCollisionKeys()) return null
                if (!candidate.mkdir()) return null
                val equivalentSibling = parent.listFiles()
                    .orEmpty()
                    .any { sibling ->
                        sibling != candidate &&
                            ExplorerActionOutputPathPolicy.collisionKey(sibling.name) ==
                            ExplorerActionOutputPathPolicy.collisionKey(candidate.name)
                    }
                if (equivalentSibling) {
                    candidate.delete()
                    return null
                }
                check(reservedPaths.add(candidate.path)) {
                    "Explorer Action output tree is already reserved"
                }
                return candidate
            }

            when (conflictPolicy) {
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL ->
                    reserve(displayName)
                        ?: throw IllegalArgumentException("Explorer Action output already exists")
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME -> {
                    reserve(displayName)?.let { return@synchronized it }
                    for (index in 2..MAX_AUTO_RENAME_ATTEMPTS) {
                        val suffix = " ($index)"
                        val maximumStemLength =
                            ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH - suffix.length
                        if (maximumStemLength < 1) break
                        reserve(displayName.take(maximumStemLength) + suffix)?.let {
                            return@synchronized it
                        }
                    }
                    throw IOException("Explorer Action could not choose an available output directory")
                }
                else -> throw IllegalArgumentException(
                    "Explorer Action output conflict policy is invalid",
                )
            }
        }
    }

    fun reserveReplacement(parent: File, target: File) {
        synchronized(lock) {
            require(target.parentFile == parent && target.isFile) {
                "Explorer Action replacement target is invalid"
            }
            check(reservedPaths.add(target.path)) {
                "Explorer Action replacement target is already reserved"
            }
        }
    }

    fun release(target: File) {
        synchronized(lock) {
            reservedPaths.remove(target.path)
        }
    }

    private fun findAvailableFile(parent: File, displayName: String): File {
        fun available(name: String): File? = File(parent, name).canonicalFile.takeIf { candidate ->
            candidate.parentFile == parent && !candidate.exists() && candidate.path !in reservedPaths
        }

        available(displayName)?.let { return it }
        val dotIndex = displayName.lastIndexOf('.').takeIf { it > 0 } ?: displayName.length
        val extension = displayName.substring(dotIndex)
        val originalStem = displayName.substring(0, dotIndex)
        for (index in 1..MAX_AUTO_RENAME_ATTEMPTS) {
            val suffix = " ($index)"
            val maxStemLength = ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH - extension.length - suffix.length
            if (maxStemLength < 1) break
            val candidateName = originalStem.take(maxStemLength) + suffix + extension
            available(candidateName)?.let { return it }
        }
        throw IOException("Explorer Action could not choose an available output name")
    }
}
