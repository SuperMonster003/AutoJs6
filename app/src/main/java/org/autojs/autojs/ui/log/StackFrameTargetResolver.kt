package org.autojs.autojs.ui.log

import java.io.File
import java.net.URI

object StackFrameTargetResolver {

    fun matchesCurrentFile(sourceName: String?, currentPath: String?): Boolean {
        val source = normalizeSourceName(sourceName) ?: return false
        val current = normalizeSourceName(currentPath) ?: return false
        if (source == current) {
            return true
        }
        if (isAbsolutePath(source)) {
            return canonicalPathOrSelf(source) == canonicalPathOrSelf(current)
        }
        val currentFileName = current.substringAfterLast('/')
        return source == currentFileName || current.endsWith("/$source")
    }

    fun normalizeSourceName(sourceName: String?): String? {
        val trimmed = sourceName
            ?.trim()
            ?.removeSuffix(" [cache]")
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val path = if (trimmed.startsWith("file:", ignoreCase = true)) {
            runCatching { URI(trimmed).path }.getOrNull()?.takeIf { it.isNotBlank() } ?: trimmed
        } else {
            trimmed
        }
        return path.replace('\\', '/')
            .replace(Regex("/{2,}"), "/")
            .removeSuffix("/")
            .takeIf { it.isNotEmpty() }
    }

    private fun isAbsolutePath(path: String): Boolean {
        return path.startsWith("/") || File(path).isAbsolute
    }

    private fun canonicalPathOrSelf(path: String): String {
        return runCatching { File(path).canonicalPath.replace('\\', '/') }
            .getOrElse { path }
    }
}
