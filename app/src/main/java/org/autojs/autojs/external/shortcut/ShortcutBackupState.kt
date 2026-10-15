package org.autojs.autojs.external.shortcut

/** Each desktop instance owns an ID, including instances which run the same script. */
internal data class ShortcutBackupEntry(
    val id: String,
    val shortLabel: String,
    val longLabel: String,
    val target: String,
    val path: String? = null,
    val sourceKind: String? = null,
    val projectRoot: String? = null,
    val icon: ByteArray,
    val status: Status = Status.PENDING,
    val notified: Boolean = false,
    val requestToken: String? = null,
) {
    enum class Status { PENDING, PINNED, MISSING, SAVED, REMOVED }

    fun sameDefinition(other: ShortcutBackupEntry): Boolean =
        copy(icon = other.icon, status = other.status, notified = other.notified, requestToken = other.requestToken) == other &&
            icon.contentEquals(other.icon)
}

internal data class ShortcutBackupState(
    val entries: List<ShortcutBackupEntry> = emptyList(),
    val markerToken: String? = null,
    val launcher: String? = null,
    val activity: String? = null,
)

internal object ShortcutBackupPolicy {
    /**
     * A published dynamic marker survives unpinning (even removal of every desktop icon),
     * whereas AOSP removes the package's dynamic AND pinned records on disable/hide.
     * No observation means no inference: failed queries and launcher changes must not be
     * interpreted as either a deletion or a freeze. An unobserved deletion immediately
     * followed by a package reset is inherently indistinguishable from that reset.
     */
    fun reconcile(
        state: ShortcutBackupState,
        pinnedIds: Set<String>,
        markerPresent: Boolean,
        comparable: Boolean,
    ): ShortcutBackupState = state.copy(entries = state.entries.map { entry ->
        when {
            entry.id in pinnedIds -> entry.copy(status = ShortcutBackupEntry.Status.PINNED, notified = false, requestToken = null)
            entry.status != ShortcutBackupEntry.Status.PINNED -> entry
            !comparable -> entry.copy(status = ShortcutBackupEntry.Status.SAVED, requestToken = null)
            markerPresent -> entry.copy(status = ShortcutBackupEntry.Status.REMOVED, requestToken = null)
            state.markerToken != null -> entry.copy(status = ShortcutBackupEntry.Status.MISSING, notified = false, requestToken = null)
            else -> entry.copy(status = ShortcutBackupEntry.Status.SAVED, requestToken = null)
        }
    })

    fun merge(local: ShortcutBackupState, incoming: List<ShortcutBackupEntry>): ShortcutBackupState {
        val merged = local.entries.associateByTo(linkedMapOf()) { it.id }
        incoming.forEach { entry ->
            val existing = merged[entry.id]
            require(existing == null || existing.sameDefinition(entry)) { "Conflicting shortcut ID: ${entry.id}" }
            // Reimporting a backup must not resurrect locally removed shortcuts or duplicate IDs.
            if (existing == null) merged[entry.id] = entry.copy(
                status = when (entry.status) {
                    ShortcutBackupEntry.Status.REMOVED -> ShortcutBackupEntry.Status.REMOVED
                    ShortcutBackupEntry.Status.PENDING -> ShortcutBackupEntry.Status.PENDING
                    else -> ShortcutBackupEntry.Status.SAVED
                },
                notified = false,
                requestToken = null,
            )
        }
        return local.copy(entries = merged.values.toList())
    }
}
