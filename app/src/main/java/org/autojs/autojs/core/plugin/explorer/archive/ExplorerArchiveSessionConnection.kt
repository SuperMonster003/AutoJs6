package org.autojs.autojs.core.plugin.explorer.archive

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import org.autojs.autojs.core.plugin.explorer.session.ExplorerActionHostSession
import org.autojs.autojs.core.plugin.explorer.session.ExplorerActionHostSessionTarget
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerArchiveOperationKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionValues
import org.autojs.plugin.explorer.api.IExplorerArchiveOperationCallback
import org.autojs.plugin.explorer.api.IExplorerArchiveSession
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal interface ExplorerArchiveSessionClient : Closeable {
    val sourcePath: String
    val info: ExplorerArchiveSessionInfo
    fun listAllChildren(parentId: String): List<ExplorerArchiveItemInfo>
    fun openEntry(entryId: String): ParcelFileDescriptor
    fun startExtraction(
        entryIds: List<String>,
        listener: ExplorerArchiveExtractionListener,
    ): String
    fun cancelExtraction(operationId: String)
}

internal data class ExplorerArchiveSessionInfo(
    val sessionId: String,
    val rootId: String,
    val displayName: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val canOpenEntries: Boolean,
    val canExtractEntries: Boolean,
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

internal data class ExplorerArchiveExtractionProgress(
    val phase: Int,
    val currentEntry: String?,
    val completedEntries: Int,
    val totalEntries: Int,
    val bytesWritten: Long,
    val totalBytes: Long,
)

internal data class ExplorerArchiveExtractionResult(
    val outputDisplayName: String,
    val outputDisplayPath: String,
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val entriesSkipped: Int,
    val bytesWritten: Long,
)

internal data class ExplorerArchiveExtractionFailure(
    val code: Int,
    val message: String,
)

internal interface ExplorerArchiveExtractionListener {
    fun onProgress(progress: ExplorerArchiveExtractionProgress)
    fun onCompleted(result: ExplorerArchiveExtractionResult)
    fun onFailed(failure: ExplorerArchiveExtractionFailure)
}

internal class ExplorerArchiveSessionConnection private constructor(
    private val context: Context,
    private val pluginPackageName: String,
    private val pluginUid: Int,
    private val protocolVersion: Int,
    private val remote: IExplorerArchiveSession,
    private val bindingLease: Closeable,
    override val sourcePath: String,
    override val info: ExplorerArchiveSessionInfo,
) : ExplorerArchiveSessionClient {

    private val closed = AtomicBoolean(false)
    private val extractionLock = Any()
    private val activeExtractions = LinkedHashMap<String, ActiveExtraction>()
    private val deathRecipient = IBinder.DeathRecipient {
        DEATH_EXECUTOR.execute { closeInternal(notifyPluginUnavailable = true) }
    }

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

    override fun startExtraction(
        entryIds: List<String>,
        listener: ExplorerArchiveExtractionListener,
    ): String {
        check(!closed.get()) { "Archive session is closed" }
        check(protocolVersion >= EXTRACTION_PROTOCOL_VERSION && info.canExtractEntries) {
            "Archive session does not support host extraction"
        }
        require(entryIds.isNotEmpty() && entryIds.size <= ExplorerActionProtocol.MAX_ARCHIVE_EXTRACTION_TARGETS) {
            "Archive extraction target count is invalid"
        }
        val normalizedEntryIds = entryIds.map { entryId ->
            requireValidId(entryId, "archive extraction entry")
        }
        require(normalizedEntryIds.distinct().size == normalizedEntryIds.size) {
            "Archive extraction contains duplicate entry IDs"
        }

        val source = File(sourcePath).canonicalFile
        require(source.isFile && source.canRead()) { "Archive source is no longer readable" }
        val parent = requireNotNull(source.parentFile?.canonicalFile) {
            "Archive source has no parent directory"
        }
        val outputSession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = pluginPackageName,
            commonParent = parent,
            targets = listOf(ExplorerActionHostSessionTarget(SOURCE_TARGET_ID, source)),
            allowCreateOutput = true,
        )
        val operationId = UUID.randomUUID().toString()
        val active = ActiveExtraction(outputSession, listener)
        val request = Bundle().apply {
            putString(ExplorerArchiveOperationKeys.OPERATION_ID, operationId)
            putStringArrayList(ExplorerArchiveOperationKeys.ENTRY_IDS, ArrayList(normalizedEntryIds))
        }
        try {
            synchronized(extractionLock) {
                check(!closed.get()) { "Archive session is closed" }
                check(activeExtractions.isEmpty()) { "Another archive extraction is already running" }
                activeExtractions[operationId] = active
            }
            remote.extractEntries(request, outputSession, extractionCallback(operationId))
        } catch (error: Throwable) {
            synchronized(extractionLock) {
                if (activeExtractions[operationId] === active) {
                    activeExtractions.remove(operationId)
                }
            }
            runCatching { outputSession.closeFromHost() }
            throw error
        }
        return operationId
    }

    override fun cancelExtraction(operationId: String) {
        check(!closed.get()) { "Archive session is closed" }
        requireCanonicalOperationId(operationId)
        if (synchronized(extractionLock) { operationId in activeExtractions }) {
            remote.cancelExtraction(operationId)
        }
    }

    override fun close() {
        closeInternal(notifyPluginUnavailable = false)
    }

    private fun closeInternal(notifyPluginUnavailable: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        runCatching { remote.asBinder().unlinkToDeath(deathRecipient, 0) }
        val abandoned = synchronized(extractionLock) {
            activeExtractions.values.toList().also { activeExtractions.clear() }
        }
        runCatching { remote.close() }
        abandoned.forEach { extraction ->
            runCatching { extraction.outputSession.closeFromHost() }
            if (notifyPluginUnavailable && extraction.terminal.compareAndSet(false, true)) {
                runCatching {
                    extraction.listener.onFailed(
                        ExplorerArchiveExtractionFailure(
                            code = ExplorerArchiveSessionValues.EXTRACTION_ERROR_PLUGIN_UNAVAILABLE,
                            message = "Archive plugin connection was lost",
                        ),
                    )
                }
            }
        }
        runCatching { bindingLease.close() }
    }

    private fun extractionCallback(operationId: String) =
        object : IExplorerArchiveOperationCallback.Stub() {
            override fun onProgress(update: Bundle) {
                enforcePluginCaller()
                val active = synchronized(extractionLock) { activeExtractions[operationId] } ?: return
                if (active.terminal.get()) return
                runCatching { decodeProgress(update, operationId) }
                    .onSuccess(active.listener::onProgress)
                    .onFailure { error -> finishMalformedCallback(operationId, error) }
            }

            override fun onCompleted(result: Bundle) {
                enforcePluginCaller()
                runCatching { decodeResult(result, operationId) }
                    .onSuccess { decoded -> finishExtraction(operationId) { it.onCompleted(decoded) } }
                    .onFailure { error -> finishMalformedCallback(operationId, error) }
            }

            override fun onFailed(failure: Bundle) {
                enforcePluginCaller()
                runCatching { decodeFailure(failure, operationId) }
                    .onSuccess { decoded -> finishExtraction(operationId) { it.onFailed(decoded) } }
                    .onFailure { error -> finishMalformedCallback(operationId, error) }
            }
        }

    private fun finishMalformedCallback(operationId: String, error: Throwable) {
        finishExtraction(operationId) { listener ->
            listener.onFailed(
                ExplorerArchiveExtractionFailure(
                    code = ExplorerArchiveSessionValues.EXTRACTION_ERROR_UNKNOWN,
                    message = safeFailureMessage(error.message),
                ),
            )
        }
    }

    private fun finishExtraction(
        operationId: String,
        notify: (ExplorerArchiveExtractionListener) -> Unit,
    ) {
        val active = synchronized(extractionLock) { activeExtractions.remove(operationId) } ?: return
        if (!active.terminal.compareAndSet(false, true)) return
        runCatching { active.outputSession.closeFromHost() }
        runCatching { notify(active.listener) }
    }

    private fun decodeProgress(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveExtractionProgress {
        requireOperationId(bundle, expectedOperationId)
        val phase = bundle.getInt(ExplorerArchiveOperationKeys.PHASE, 0)
        require(phase in EXTRACTION_PHASES) { "Archive extraction phase is invalid" }
        val completedEntries = bundle.getInt(ExplorerArchiveOperationKeys.COMPLETED_ENTRIES, -1)
        val totalEntries = bundle.getInt(ExplorerArchiveOperationKeys.TOTAL_ENTRIES, -1)
        val bytesWritten = bundle.getLong(ExplorerArchiveOperationKeys.BYTES_WRITTEN, -1L)
        val totalBytes = bundle.getLong(ExplorerArchiveOperationKeys.TOTAL_BYTES, -1L)
        require(completedEntries >= 0 && totalEntries >= 0 && completedEntries <= totalEntries) {
            "Archive extraction entry progress is invalid"
        }
        require(bytesWritten >= 0L && totalBytes >= 0L && bytesWritten <= totalBytes) {
            "Archive extraction byte progress is invalid"
        }
        return ExplorerArchiveExtractionProgress(
            phase = phase,
            currentEntry = bundle.getString(ExplorerArchiveOperationKeys.CURRENT_ENTRY)
                ?.let(::sanitizeOperationText),
            completedEntries = completedEntries,
            totalEntries = totalEntries,
            bytesWritten = bytesWritten,
            totalBytes = totalBytes,
        )
    }

    private fun decodeResult(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveExtractionResult {
        requireOperationId(bundle, expectedOperationId)
        val displayName = sanitizeOperationText(
            requireNotNull(bundle.getString(ExplorerArchiveOperationKeys.OUTPUT_DISPLAY_NAME)) {
                "Archive extraction output name is missing"
            },
        )
        val displayPath = sanitizeOperationText(
            requireNotNull(bundle.getString(ExplorerArchiveOperationKeys.OUTPUT_DISPLAY_PATH)) {
                "Archive extraction output path is missing"
            },
            ExplorerActionProtocol.MAX_PARENT_DISPLAY_PATH_LENGTH,
        )
        val filesExtracted = bundle.getInt(ExplorerArchiveOperationKeys.FILES_EXTRACTED, -1)
        val directoriesCreated = bundle.getInt(ExplorerArchiveOperationKeys.DIRECTORIES_CREATED, -1)
        val entriesSkipped = bundle.getInt(ExplorerArchiveOperationKeys.ENTRIES_SKIPPED, -1)
        val bytesWritten = bundle.getLong(ExplorerArchiveOperationKeys.BYTES_WRITTEN, -1L)
        require(filesExtracted >= 0 && directoriesCreated >= 0 && entriesSkipped >= 0 && bytesWritten >= 0L) {
            "Archive extraction result statistics are invalid"
        }
        return ExplorerArchiveExtractionResult(
            outputDisplayName = displayName,
            outputDisplayPath = displayPath,
            filesExtracted = filesExtracted,
            directoriesCreated = directoriesCreated,
            entriesSkipped = entriesSkipped,
            bytesWritten = bytesWritten,
        )
    }

    private fun decodeFailure(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveExtractionFailure {
        requireOperationId(bundle, expectedOperationId)
        val code = bundle.getInt(ExplorerArchiveOperationKeys.ERROR_CODE, 0)
        require(code in EXTRACTION_ERROR_CODES) { "Archive extraction error code is invalid" }
        return ExplorerArchiveExtractionFailure(
            code = code,
            message = safeFailureMessage(bundle.getString(ExplorerArchiveOperationKeys.ERROR_MESSAGE)),
        )
    }

    private fun requireOperationId(bundle: Bundle, expectedOperationId: String) {
        require(bundle.getString(ExplorerArchiveOperationKeys.OPERATION_ID) == expectedOperationId) {
            "Archive extraction operation ID changed"
        }
    }

    private fun sanitizeOperationText(
        value: String,
        maxLength: Int = ExplorerActionProtocol.MAX_ARCHIVE_OPERATION_MESSAGE_LENGTH,
    ): String = requireNotNull(
        value.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .trim()
            .take(maxLength)
            .takeIf(String::isNotEmpty),
    ) { "Archive extraction text is invalid" }

    private fun safeFailureMessage(value: String?): String = runCatching {
        sanitizeOperationText(value ?: "Archive extraction failed")
    }.getOrDefault("Archive extraction failed")

    private fun enforcePluginCaller() {
        if (Binder.getCallingUid() != pluginUid) {
            throw SecurityException("Archive extraction callback caller changed")
        }
    }

    private fun requireCanonicalOperationId(value: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        require(parsed?.toString()?.equals(value, ignoreCase = true) == true) {
            "Archive extraction operation ID is invalid"
        }
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

    private data class ActiveExtraction(
        val outputSession: ExplorerActionHostSession,
        val listener: ExplorerArchiveExtractionListener,
        val terminal: AtomicBoolean = AtomicBoolean(false),
    )

    companion object {
        fun create(
            context: Context,
            pluginPackageName: String,
            pluginUid: Int,
            protocolVersion: Int,
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
                    context = context.applicationContext,
                    pluginPackageName = pluginPackageName,
                    pluginUid = pluginUid,
                    protocolVersion = protocolVersion,
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
                        canExtractEntries = protocolVersion >= EXTRACTION_PROTOCOL_VERSION &&
                            bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_EXTRACT_ENTRIES, false),
                    ),
                )
            } catch (error: Throwable) {
                runCatching { remote.close() }
                runCatching { bindingLease.close() }
                throw error
            }
        }

        private const val EXTRACTION_PROTOCOL_VERSION = 10
        private const val SOURCE_TARGET_ID = "archive-source"
        private val DEATH_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ExplorerArchiveSessionDeath").apply { isDaemon = true }
        }
        private val EXTRACTION_PHASES = setOf(
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_PREPARING,
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_EXTRACTING,
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_COMMITTING,
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_CLEANING_UP,
        )
        private val EXTRACTION_ERROR_CODES = setOf(
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_CANCELLED,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_SOURCE_CHANGED,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_UNSUPPORTED,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_OUTPUT,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_INVALID_REQUEST,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_PLUGIN_UNAVAILABLE,
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_UNKNOWN,
        )
    }
}

private fun isInvalidOpaqueIdCharacter(character: Char): Boolean =
    character.isWhitespace() || character.code < 0x20 || character.code == 0x7F
