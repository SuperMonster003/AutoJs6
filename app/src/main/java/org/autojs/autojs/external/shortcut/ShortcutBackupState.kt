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
    val icon: ByteArray?,
    val status: Status = Status.PENDING,
    val notified: Boolean = false,
    val requestToken: String? = null,
    val iconNoticeShown: Boolean = false,
) {
    enum class Status { PENDING, PINNED, MISSING, SAVED, REMOVED }

    fun sameLaunch(other: ShortcutBackupEntry): Boolean =
        copy(icon = other.icon, status = other.status, notified = other.notified,
            requestToken = other.requestToken, iconNoticeShown = other.iconNoticeShown) == other

    fun sameDefinition(other: ShortcutBackupEntry): Boolean = sameLaunch(other) &&
        (if (icon == null) other.icon == null else other.icon != null && icon.contentEquals(other.icon))
}

internal data class ShortcutBackupState(
    val entries: List<ShortcutBackupEntry> = emptyList(),
    val markerToken: String? = null,
    val launcher: String? = null,
    val activity: String? = null,
)

internal object ShortcutBackupPolicy {
    /** Fill only absent icons in the current state; keep every copy and its restoration decisions. */
    fun fillMissingIcons(state: ShortcutBackupState, iconForTarget: (String) -> ByteArray): ShortcutBackupState =
        state.copy(entries = state.entries.map { entry ->
            if (entry.icon != null) entry
            else entry.copy(icon = iconForTarget(entry.target), iconNoticeShown = true)
        })

    /** Keep the system ID, including separate IDs with identical labels, paths and intents. */
    fun adoptPinned(state: ShortcutBackupState, discovered: List<ShortcutBackupEntry>): ShortcutBackupState {
        val entries = state.entries.associateByTo(linkedMapOf()) { it.id }
        discovered.forEach { entry ->
            if (entry.id !in entries && entries.size < ShortcutBackupArchive.MAX_ENTRIES) {
                entries[entry.id] = entry.copy(status = ShortcutBackupEntry.Status.PINNED, notified = false,
                    requestToken = null, iconNoticeShown = false)
            }
        }
        return state.copy(entries = entries.values.toList())
    }

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
            require(existing == null || (existing.sameLaunch(entry) &&
                (existing.icon == null || entry.icon == null || existing.icon.contentEquals(entry.icon)))) {
                "Conflicting shortcut ID: ${entry.id}"
            }
            // Reimporting a backup must not resurrect locally removed shortcuts or duplicate IDs.
            if (existing == null) merged[entry.id] = entry.copy(
                status = when (entry.status) {
                    ShortcutBackupEntry.Status.REMOVED -> ShortcutBackupEntry.Status.REMOVED
                    ShortcutBackupEntry.Status.PENDING -> ShortcutBackupEntry.Status.PENDING
                    else -> ShortcutBackupEntry.Status.SAVED
                },
                notified = false,
                requestToken = null,
                iconNoticeShown = false,
            )
            else if (existing.icon == null && entry.icon != null) {
                // An older complete backup can fill an unreadable legacy icon without undoing
                // a local removal, or replacing a user's subsequently chosen icon.
                merged[entry.id] = existing.copy(icon = entry.icon)
            }
        }
        return local.copy(entries = merged.values.toList())
    }
}
