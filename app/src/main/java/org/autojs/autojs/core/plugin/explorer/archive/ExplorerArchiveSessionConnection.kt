package org.autojs.autojs.core.plugin.explorer.archive

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionValues
import org.autojs.plugin.explorer.api.IExplorerArchiveSession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal interface ExplorerArchiveSessionClient : Closeable {
    val sourcePath: String
    val info: ExplorerArchiveSessionInfo
    fun listAllChildren(parentId: String): List<ExplorerArchiveItemInfo>
    fun openEntry(entryId: String): ParcelFileDescriptor
}

internal data class ExplorerArchiveSessionInfo(
    val sessionId: String,
    val rootId: String,
    val displayName: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val canOpenEntries: Boolean,
)

internal data class ExplorerArchiveItemInfo(
    val id: String,
    val parentId: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val canExtract: Boolean,
    val canOpen: Boolean = false,
)

internal class ExplorerArchiveSessionConnection private constructor(
    private val remote: IExplorerArchiveSession,
    private val bindingLease: Closeable,
    override val sourcePath: String,
    override val info: ExplorerArchiveSessionInfo,
) : ExplorerArchiveSessionClient {

    private val closed = AtomicBoolean(false)
    private val deathRecipient = IBinder.DeathRecipient { close() }

    init {
        remote.asBinder().linkToDeath(deathRecipient, 0)
    }

    override fun listAllChildren(parentId: String): List<ExplorerArchiveItemInfo> {
        check(!closed.get()) { "Archive session is closed" }
        requireValidId(parentId, "archive parent")
        val result = ArrayList<ExplorerArchiveItemInfo>()
        val seenEntryIds = HashSet<String>()
        var offset = 0
        while (true) {
            val page = remote.listChildren(parentId, offset, ExplorerActionProtocol.MAX_ARCHIVE_PAGE_SIZE)
            val items = BundleCompat.getParcelableArrayList(
                page,
                ExplorerArchiveSessionKeys.ITEMS,
                Bundle::class.java,
            ).orEmpty()
            require(items.size <= ExplorerActionProtocol.MAX_ARCHIVE_PAGE_SIZE) {
                "Archive session returned an oversized page"
            }
            items.forEach { item ->
                val decoded = decodeItem(item, parentId)
                require(seenEntryIds.add(decoded.id)) {
                    "Archive session returned a duplicate entry ID"
                }
                result += decoded
            }
            require(result.size <= ExplorerActionProtocol.MAX_ARCHIVE_ITEMS) {
                "Archive session returned too many entries"
            }

            val nextOffset = page.getInt(ExplorerArchiveSessionKeys.NEXT_OFFSET, -1)
            val complete = page.getBoolean(ExplorerArchiveSessionKeys.COMPLETE, false)
            if (complete) {
                require(nextOffset == result.size) { "Archive session returned an invalid terminal offset" }
                return result
            }
            require(items.isNotEmpty() && nextOffset == offset + items.size) {
                "Archive session pagination did not advance"
            }
            offset = nextOffset
        }
    }

    override fun openEntry(entryId: String): ParcelFileDescriptor {
        check(!closed.get()) { "Archive session is closed" }
        check(info.canOpenEntries) { "Archive session does not support opening entries" }
        requireValidId(entryId, "archive entry")
        val descriptor = requireNotNull(remote.openEntry(entryId)) {
            "Archive session returned no entry descriptor"
        }
        return try {
            require(descriptor.fileDescriptor.valid()) {
                "Archive session returned an invalid entry descriptor"
            }
            descriptor
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { remote.asBinder().unlinkToDeath(deathRecipient, 0) }
        runCatching { remote.close() }
        bindingLease.close()
    }

    private fun decodeItem(bundle: Bundle, expectedParentId: String): ExplorerArchiveItemInfo {
        val id = requireValidId(bundle.getString(ExplorerArchiveSessionKeys.ID), "archive entry")
        val parentId = requireValidId(bundle.getString(ExplorerArchiveSessionKeys.PARENT_ID), "archive parent")
        require(parentId == expectedParentId) { "Archive session returned an entry for another parent" }
        require(id != parentId) { "Archive session returned a self-referencing entry" }
        require(id != info.rootId) { "Archive session reused the archive root ID" }
        val name = sanitizeDisplayName(bundle.getString(ExplorerArchiveSessionKeys.NAME))
        val kind = bundle.getInt(ExplorerArchiveSessionKeys.KIND, 0)
        require(
            kind == ExplorerArchiveSessionValues.KIND_FILE ||
                kind == ExplorerArchiveSessionValues.KIND_DIRECTORY,
        ) { "Archive session returned an invalid entry kind" }
        val size = bundle.getLong(ExplorerArchiveSessionKeys.SIZE, -1L)
        val compressedSize = bundle.getLong(ExplorerArchiveSessionKeys.COMPRESSED_SIZE, -1L)
        require(size >= 0L) { "Archive session returned an invalid entry size" }
        require(compressedSize >= -1L) { "Archive session returned an invalid compressed size" }
        return ExplorerArchiveItemInfo(
            id = id,
            parentId = parentId,
            name = name,
            isDirectory = kind == ExplorerArchiveSessionValues.KIND_DIRECTORY,
            size = size,
            compressedSize = compressedSize,
            lastModified = bundle.getLong(ExplorerArchiveSessionKeys.LAST_MODIFIED, 0L).coerceAtLeast(0L),
            canExtract = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_EXTRACT, false),
        )
    }

    private fun sanitizeDisplayName(value: String?): String {
        val sanitized = value
            ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            ?.trim()
            ?.take(ExplorerActionProtocol.MAX_ARCHIVE_DISPLAY_NAME_LENGTH)
            ?.takeIf(String::isNotEmpty)
        return requireNotNull(sanitized) { "Archive session returned an invalid display name" }
    }

    private fun requireValidId(value: String?, label: String): String {
        return requireNotNull(value?.takeIf {
            it.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_ENTRY_ID_LENGTH &&
                it.none(::isInvalidOpaqueIdCharacter)
        }) { "Archive session returned an invalid $label ID" }
    }

    companion object {
        fun create(
            remote: IExplorerArchiveSession,
            bindingLease: Closeable,
            sourcePath: String,
        ): ExplorerArchiveSessionConnection {
            try {
                val bundle = remote.info
                val sessionId = requireNotNull(bundle.getString(ExplorerArchiveSessionKeys.SESSION_ID)?.takeIf {
                    it.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_SESSION_ID_LENGTH &&
                        it.none(::isInvalidOpaqueIdCharacter)
                }) { "Archive session returned an invalid session ID" }
                val rootId = requireNotNull(bundle.getString(ExplorerArchiveSessionKeys.ROOT_ID)?.takeIf {
                    it.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_ENTRY_ID_LENGTH &&
                        it.none(::isInvalidOpaqueIdCharacter)
                }) { "Archive session returned an invalid root ID" }
                val displayName = bundle.getString(ExplorerArchiveSessionKeys.DISPLAY_NAME)
                    ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
                    ?.trim()
                    ?.take(ExplorerActionProtocol.MAX_ARCHIVE_DISPLAY_NAME_LENGTH)
                    ?.takeIf(String::isNotEmpty)
                    ?: error("Archive session returned an invalid display name")
                val sourceSize = bundle.getLong(ExplorerArchiveSessionKeys.SOURCE_SIZE, -1L)
                require(sourceSize >= 0L) { "Archive session returned an invalid source size" }
                return ExplorerArchiveSessionConnection(
                    remote = remote,
                    bindingLease = bindingLease,
                    sourcePath = sourcePath,
                    info = ExplorerArchiveSessionInfo(
                        sessionId = sessionId,
                        rootId = rootId,
                        displayName = displayName,
                        sourceSize = sourceSize,
                        sourceLastModified = bundle
                            .getLong(ExplorerArchiveSessionKeys.SOURCE_LAST_MODIFIED, 0L)
                            .coerceAtLeast(0L),
                        canOpenEntries = bundle.getBoolean(
                            ExplorerArchiveSessionKeys.CAN_OPEN_ENTRIES,
                            false,
                        ),
                    ),
                )
            } catch (error: Throwable) {
                runCatching { remote.close() }
                bindingLease.close()
                throw error
            }
        }
    }
}

private fun isInvalidOpaqueIdCharacter(character: Char): Boolean =
    character.isWhitespace() || character.code < 0x20 || character.code == 0x7F
