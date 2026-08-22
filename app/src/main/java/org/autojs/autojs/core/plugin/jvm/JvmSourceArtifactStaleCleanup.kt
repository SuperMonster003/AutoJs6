package org.autojs.autojs.core.plugin.jvm

import java.io.File

/** Filesystem policy kept Android-free so crash-stale cleanup can be exercised on the JVM. */
internal object JvmSourceArtifactStaleCleanup {

    enum class NodeType {
        DIRECTORY,
        REGULAR_FILE,
        SYMBOLIC_LINK,
        OTHER,
        MISSING,
    }

    interface Operations {
        fun typeNoFollow(file: File): NodeType
        fun canonicalFile(file: File): File?
        fun children(directory: File): List<File>?
        fun unlink(file: File): Boolean
        fun deleteDirectory(directory: File): Boolean
    }

    data class Report(
        val eligibleRoots: Int,
        val deletedRoots: Int,
    )

    fun requirePrivateBase(
        expectedParent: File,
        requestedBase: File,
        operations: Operations,
    ): File {
        val parent = requireNotNull(operations.canonicalFile(expectedParent)) {
            "Unable to resolve the JVM source private parent"
        }
        require(operations.typeNoFollow(requestedBase) == NodeType.DIRECTORY) {
            "JVM source private base must be an ordinary directory, not a symbolic link"
        }
        require(isLexicalDirectChild(parent, requestedBase)) {
            "JVM source private base is not a direct child of the expected private parent"
        }
        val canonicalBase = requireNotNull(operations.canonicalFile(requestedBase)) {
            "Unable to resolve the JVM source private base"
        }
        require(canonicalBase.parentFile == parent && canonicalBase.name == requestedBase.name) {
            "JVM source private base escaped its expected private parent"
        }
        return canonicalBase
    }

    fun cleanup(
        canonicalBase: File,
        operations: Operations,
        canDeleteRoot: (File) -> Boolean,
    ): Report {
        require(operations.typeNoFollow(canonicalBase) == NodeType.DIRECTORY)
        val candidates = requireNotNull(operations.children(canonicalBase)) {
            "Unable to enumerate the JVM source private base"
        }
        val roots = candidates.filter { candidate ->
            isEligibleRoot(canonicalBase, candidate, operations)
        }
        var deleted = 0
        roots.forEach { root ->
            if (runCatching { canDeleteRoot(root) }.getOrDefault(false) &&
                runCatching { deleteEligibleRoot(canonicalBase, root, operations) }.getOrDefault(false)
            ) {
                deleted++
            }
        }
        return Report(eligibleRoots = roots.size, deletedRoots = deleted)
    }

    internal fun isManagedRootName(name: String): Boolean = MANAGED_ROOT.matches(name)

    private fun isEligibleRoot(
        canonicalBase: File,
        candidate: File,
        operations: Operations,
    ): Boolean {
        if (!isManagedRootName(candidate.name) || !isLexicalDirectChild(canonicalBase, candidate)) return false
        // Root symlinks are deliberately left untouched; canonicalizing one would follow its target.
        if (operations.typeNoFollow(candidate) != NodeType.DIRECTORY) return false
        val canonicalCandidate = operations.canonicalFile(candidate) ?: return false
        return canonicalCandidate.parentFile == canonicalBase && canonicalCandidate.name == candidate.name
    }

    private fun deleteEligibleRoot(
        canonicalBase: File,
        root: File,
        operations: Operations,
    ): Boolean {
        if (!isEligibleRoot(canonicalBase, root, operations)) return false
        val canonicalRoot = operations.canonicalFile(root) ?: return false
        return deleteNode(
            lexicalRoot = root.absoluteFile,
            canonicalRoot = canonicalRoot,
            node = root.absoluteFile,
            operations = operations,
            rootNode = true,
        )
    }

    private fun deleteNode(
        lexicalRoot: File,
        canonicalRoot: File,
        node: File,
        operations: Operations,
        rootNode: Boolean,
    ): Boolean {
        if (!isLexicallyContained(lexicalRoot, node)) return false
        return when (operations.typeNoFollow(node)) {
            NodeType.MISSING -> true
            NodeType.SYMBOLIC_LINK -> {
                // Only nested links are unlinked. A candidate root link never reaches this method.
                !rootNode && operations.unlink(node)
            }
            NodeType.REGULAR_FILE -> {
                val canonicalNode = operations.canonicalFile(node) ?: return false
                isCanonicallyContained(canonicalRoot, canonicalNode) && operations.unlink(node)
            }
            NodeType.DIRECTORY -> {
                val canonicalNode = operations.canonicalFile(node) ?: return false
                if (!isCanonicallyContained(canonicalRoot, canonicalNode)) return false
                val children = operations.children(node) ?: return false
                if (children.any { !isLexicalDirectChild(node, it) }) return false
                if (children.any {
                        !deleteNode(lexicalRoot, canonicalRoot, it, operations, rootNode = false)
                    }
                ) {
                    return false
                }
                operations.deleteDirectory(node)
            }
            NodeType.OTHER -> false
        }
    }

    private fun isLexicalDirectChild(parent: File, candidate: File): Boolean {
        val absoluteParent = parent.absoluteFile
        val absoluteCandidate = candidate.absoluteFile
        return absoluteCandidate != absoluteParent && absoluteCandidate.parentFile == absoluteParent
    }

    private fun isLexicallyContained(root: File, candidate: File): Boolean =
        isContained(root.absoluteFile, candidate.absoluteFile)

    private fun isCanonicallyContained(root: File, candidate: File): Boolean =
        isContained(root, candidate)

    private fun isContained(root: File, candidate: File): Boolean {
        var cursor: File? = candidate
        while (cursor != null) {
            if (cursor == root) return true
            cursor = cursor.parentFile
        }
        return false
    }

    private val MANAGED_ROOT = Regex(
        "host-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    )
}
