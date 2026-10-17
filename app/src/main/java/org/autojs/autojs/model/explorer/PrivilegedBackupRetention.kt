package org.autojs.autojs.model.explorer

/** Pure retention planning; actual deletion still revalidates content under the remote lease. */
internal object PrivilegedBackupRetention {
    data class Version(val id: String, val source: String, val createdAt: Long, val size: Long, val automatic: Boolean, val restorable: Boolean)

    fun expired(versions: List<Version>, maxVersions: Int, maxBytes: Long): Set<String> {
        require(maxVersions >= 1 && maxBytes >= 1)
        val expired = linkedSetOf<String>()
        versions.groupBy { it.source }.values.forEach { group ->
            val newestFirst = group.sortedWith(compareByDescending<Version> { it.createdAt }.thenByDescending { it.id })
            // An altered recovery copy must not displace the last known unchanged version.
            val protected = newestFirst.firstOrNull { it.restorable && it.automatic }?.id
            var count = group.size
            var bytes = group.sumOf { it.size.coerceAtLeast(0) }
            newestFirst.asReversed().forEach { version ->
                if ((count > maxVersions || bytes > maxBytes) && version.automatic && version.id != protected) {
                    expired += version.id
                    count--
                    bytes -= version.size.coerceAtLeast(0)
                }
            }
        }
        return expired
    }
}
