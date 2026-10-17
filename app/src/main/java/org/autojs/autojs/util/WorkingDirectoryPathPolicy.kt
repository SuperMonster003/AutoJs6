package org.autojs.autojs.util

import java.net.URI

/** Untagged values keep the legacy shared-storage-relative meaning, including a leading slash. */
internal object WorkingDirectoryPathPolicy {

    fun isWithinStorage(path: String, storageRoot: String): Boolean {
        val root = normalizedRoot(storageRoot)
        return path == root || path.startsWith(if (root == "/") root else "$root/")
    }

    fun toRelativePath(path: String, storageRoot: String): String {
        val root = normalizedRoot(storageRoot)
        return if (isWithinStorage(path, root)) path.removePrefix(root) else path
    }

    fun encodeAbsolute(path: String, storageRoot: String): String {
        require(path.startsWith('/') && '\u0000' !in path) { "Expected an absolute directory path" }
        if (isWithinStorage(path, storageRoot)) return toRelativePath(path, storageRoot)
        return URI("file", "", path, null, null).toASCIIString()
    }

    fun decodeAbsolute(value: String): String? {
        if (!value.startsWith("file:///")) return null
        return runCatching {
            val uri = URI(value)
            uri.path?.takeIf {
                uri.scheme == "file" && uri.authority.isNullOrEmpty() &&
                    uri.query == null && uri.fragment == null &&
                    it.startsWith('/') && '\u0000' !in it
            }
        }.getOrNull()
    }

    fun resolveStored(value: String, storageRoot: String): String {
        decodeAbsolute(value)?.let { return it }
        val root = normalizedRoot(storageRoot)
        val relative = value.replace(Regex("/+"), "/").trim('/')
        return if (relative.isEmpty()) root else if (root == "/") "/$relative" else "$root/$relative"
    }

    /** Only a file chosen explicitly, or an encoded value, changes the meaning of a leading slash. */
    fun resolveInput(value: String, storageRoot: String, selectedAbsolutePath: String? = null): String {
        selectedAbsolutePath?.let { selected ->
            if (value == toRelativePath(selected, storageRoot)) return selected
            if (!isWithinStorage(selected, storageRoot) && value.startsWith('/')) return value
        }
        decodeAbsolute(value)?.let { return it }
        return if (isWithinStorage(value, storageRoot)) value else resolveStored(value, storageRoot)
    }

    fun displayStored(value: String): String = decodeAbsolute(value) ?: value

    private fun normalizedRoot(storageRoot: String): String = storageRoot.trimEnd('/').ifEmpty { "/" }
}
