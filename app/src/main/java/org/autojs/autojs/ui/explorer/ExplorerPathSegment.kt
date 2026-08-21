package org.autojs.autojs.ui.explorer

internal data class ExplorerPathSegment(
    val label: String,
    val path: String,
)

internal object ExplorerPathSegmentBuilder {

    fun build(
        currentPath: String,
        rootPath: String,
        rootLabel: String,
    ): List<ExplorerPathSegment> {
        val current = normalize(currentPath)
        val root = normalize(rootPath)
        if (current.isEmpty()) return emptyList()
        if (!isSameOrDescendant(current, root)) {
            return listOf(ExplorerPathSegment(labelForPath(current), current))
        }

        val segments = mutableListOf(ExplorerPathSegment(rootLabel, root))
        val relativePath = current.removePrefix(root).trim(PATH_SEPARATOR)
        if (relativePath.isEmpty()) return segments

        var accumulatedPath = root
        relativePath.split(PATH_SEPARATOR)
            .filter(String::isNotEmpty)
            .forEach { name ->
                accumulatedPath = append(accumulatedPath, name)
                segments += ExplorerPathSegment(name, accumulatedPath)
            }
        return segments
    }

    fun isSameOrDescendant(path: String, rootPath: String): Boolean {
        val normalizedPath = normalize(path)
        val normalizedRoot = normalize(rootPath)
        if (normalizedPath.isEmpty() || normalizedRoot.isEmpty()) return false
        return normalizedPath == normalizedRoot || when (normalizedRoot) {
            PATH_SEPARATOR.toString() -> normalizedPath.startsWith(PATH_SEPARATOR)
            else -> normalizedPath.startsWith("$normalizedRoot$PATH_SEPARATOR")
        }
    }

    fun filesystemRoot(path: String): String {
        val normalized = normalize(path)
        return when {
            normalized.startsWith(PATH_SEPARATOR) -> PATH_SEPARATOR.toString()
            normalized.contains(PATH_SEPARATOR) -> normalized.substringBefore(PATH_SEPARATOR)
            else -> normalized
        }
    }

    fun labelForPath(path: String): String {
        val normalized = normalize(path)
        return normalized.substringAfterLast(PATH_SEPARATOR).ifEmpty { normalized }
    }

    private fun append(parent: String, child: String): String = when (parent) {
        PATH_SEPARATOR.toString() -> "$PATH_SEPARATOR$child"
        else -> "$parent$PATH_SEPARATOR$child"
    }

    private fun normalize(path: String): String {
        val normalizedSeparators = path.replace('\\', PATH_SEPARATOR)
        return when {
            normalizedSeparators.length <= 1 -> normalizedSeparators
            else -> normalizedSeparators.trimEnd(PATH_SEPARATOR)
        }
    }

    private const val PATH_SEPARATOR = '/'
}
