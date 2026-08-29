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
import org.autojs.plugin.explorer.api.ExplorerArchiveMutationKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveOperationKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionKeys
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionValues
import org.autojs.plugin.explorer.api.IExplorerArchiveOperationCallback
import org.autojs.plugin.explorer.api.IExplorerArchiveSession
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal interface ExplorerArchiveSessionClient : Closeable {
    val sourcePath: String
    val info: ExplorerArchiveSessionInfo
    fun listAllChildren(parentId: String): List<ExplorerArchiveItemInfo>
    fun openEntry(entryId: String): ParcelFileDescriptor
    fun reindexFilenameCharset(filenameCharsetName: String?): ExplorerArchiveSessionInfo
    fun startExtraction(
        entryIds: List<String>,
        options: ExplorerArchiveExtractionRequestOptions,
        listener: ExplorerArchiveExtractionListener,
    ): String
    fun cancelExtraction(operationId: String)
    fun startMutation(
        request: ExplorerArchiveMutationRequest,
        listener: ExplorerArchiveMutationListener,
    ): String
    fun cancelMutation(operationId: String)
}

internal data class ExplorerArchiveSessionInfo(
    val sessionId: String,
    val rootId: String,
    val displayName: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val canOpenEntries: Boolean,
    val canExtractEntries: Boolean,
    val canDeleteEntries: Boolean = false,
    val canRenameEntries: Boolean = false,
    val canCreateDirectory: Boolean = false,
    val canAddEntries: Boolean = false,
    val formatId: String? = null,
    val formatDisplayName: String? = null,
    val displayNameMatchesFormat: Boolean = true,
    val filenameCharsetNames: List<String> = emptyList(),
    val filenameCharsetName: String? = null,
    val filenameCharsetOverride: String? = null,
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
    val canDelete: Boolean = false,
    val canRename: Boolean = false,
    val canCreateChildren: Boolean = false,
    val canAddChildren: Boolean = false,
)

internal sealed interface ExplorerArchiveMutationRequest {
    data class Delete(val entryIds: List<String>) : ExplorerArchiveMutationRequest
    data class Rename(val entryId: String, val newName: String) : ExplorerArchiveMutationRequest
    data class CreateDirectory(
        val parentEntryId: String,
        val newName: String,
    ) : ExplorerArchiveMutationRequest
}

internal data class ExplorerArchiveMutationProgress(
    val phase: Int,
    val currentEntry: String?,
    val completedEntries: Int,
    val totalEntries: Int,
)

internal data class ExplorerArchiveMutationResult(
    val updatedInfo: ExplorerArchiveSessionInfo,
    val mutatedEntries: Int,
)

internal data class ExplorerArchiveMutationFailure(
    val code: Int,
    val message: String,
)

internal interface ExplorerArchiveMutationListener {
    fun onProgress(progress: ExplorerArchiveMutationProgress)
    fun onCompleted(result: ExplorerArchiveMutationResult)
    fun onFailed(failure: ExplorerArchiveMutationFailure)
}

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
    val interactionKind: Int = ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_NONE,
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
    private val volumeSource: Closeable?,
    override val sourcePath: String,
    initialInfo: ExplorerArchiveSessionInfo,
) : ExplorerArchiveSessionClient {

    @Volatile
    override var info: ExplorerArchiveSessionInfo = initialInfo
        private set

    private val closed = AtomicBoolean(false)
    private val operationLock = Any()
    private val activeExtractions = LinkedHashMap<String, ActiveExtraction>()
    private val activeMutations = LinkedHashMap<String, ActiveMutation>()
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

    override fun reindexFilenameCharset(filenameCharsetName: String?): ExplorerArchiveSessionInfo {
        check(!closed.get()) { "Archive session is closed" }
        check(protocolVersion >= FILENAME_CHARSET_PROTOCOL_VERSION) {
            "Archive session does not support filename charset overrides"
        }
        val previous = info
        check(previous.filenameCharsetNames.isNotEmpty()) {
            "Archive format does not support filename charset overrides"
        }
        check(synchronized(operationLock) {
            activeExtractions.isEmpty() && activeMutations.isEmpty()
        }) {
            "Another archive operation is still running"
        }
        val canonicalOverride = filenameCharsetName?.let { requested ->
            requireNotNull(previous.filenameCharsetNames.firstOrNull { supported ->
                supported.equals(requested, ignoreCase = true)
            }) { "Archive filename charset is not supported" }
        }
        val bundle = remote.reindexFilenameCharset(canonicalOverride)
        val metadata = decodeFilenameCharsetMetadata(bundle, protocolVersion)
        require(bundle.getString(ExplorerArchiveSessionKeys.SESSION_ID) == previous.sessionId) {
            "Archive session ID changed while rebuilding the filename index"
        }
        require(bundle.getString(ExplorerArchiveSessionKeys.ROOT_ID) == previous.rootId) {
            "Archive root ID changed while rebuilding the filename index"
        }
        require(sanitizeDisplayName(bundle.getString(ExplorerArchiveSessionKeys.DISPLAY_NAME)) == previous.displayName) {
            "Archive display name changed while rebuilding the filename index"
        }
        require(bundle.getLong(ExplorerArchiveSessionKeys.SOURCE_SIZE, -1L) == previous.sourceSize) {
            "Archive source size changed while rebuilding the filename index"
        }
        require(
            bundle.getLong(ExplorerArchiveSessionKeys.SOURCE_LAST_MODIFIED, -1L) ==
                previous.sourceLastModified,
        ) { "Archive source identity changed while rebuilding the filename index" }
        val updated = previous.copy(
            canOpenEntries = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_OPEN_ENTRIES, false),
            canExtractEntries = bundle.getBoolean(
                ExplorerArchiveSessionKeys.CAN_EXTRACT_ENTRIES,
                false,
            ),
            canDeleteEntries = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_DELETE_ENTRIES, false),
            canRenameEntries = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_RENAME_ENTRIES, false),
            canCreateDirectory = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_CREATE_DIRECTORY, false),
            canAddEntries = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_ADD_ENTRIES, false),
            filenameCharsetNames = metadata.supportedNames,
            filenameCharsetName = metadata.effectiveName,
            filenameCharsetOverride = metadata.overrideName,
        )
        info = updated
        return updated
    }

    override fun startExtraction(
        entryIds: List<String>,
        options: ExplorerArchiveExtractionRequestOptions,
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
        require(protocolVersion >= EXTRACTION_RECOVERY_PROTOCOL_VERSION || !options.hasRecoveryOptions) {
            "Archive session does not support recoverable extraction options"
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
        val request = Bundle()
        try {
            request.putString(ExplorerArchiveOperationKeys.OPERATION_ID, operationId)
            request.putStringArrayList(
                ExplorerArchiveOperationKeys.ENTRY_IDS,
                ArrayList(normalizedEntryIds),
            )
            if (protocolVersion >= EXTRACTION_RECOVERY_PROTOCOL_VERSION) {
                options.passwordChars()?.let { password ->
                    request.putCharArray(ExplorerArchiveOperationKeys.PASSWORD, password)
                }
                request.putBoolean(
                    ExplorerArchiveOperationKeys.SKIP_UNSAFE_PATHS,
                    options.skipUnsafePaths,
                )
                request.putBoolean(
                    ExplorerArchiveOperationKeys.ALLOW_RESOURCE_BUDGET_OVERRIDE,
                    options.allowResourceBudgetOverride,
                )
            }
            synchronized(operationLock) {
                check(!closed.get()) { "Archive session is closed" }
                check(activeExtractions.isEmpty() && activeMutations.isEmpty()) {
                    "Another archive operation is already running"
                }
                activeExtractions[operationId] = active
            }
            remote.extractEntries(request, outputSession, extractionCallback(operationId))
        } catch (error: Throwable) {
            synchronized(operationLock) {
                if (activeExtractions[operationId] === active) {
                    activeExtractions.remove(operationId)
                }
            }
            runCatching { outputSession.closeFromHost() }
            throw error
        } finally {
            request.getCharArray(ExplorerArchiveOperationKeys.PASSWORD)?.fill('\u0000')
            request.remove(ExplorerArchiveOperationKeys.PASSWORD)
        }
        return operationId
    }

    override fun cancelExtraction(operationId: String) {
        check(!closed.get()) { "Archive session is closed" }
        requireCanonicalOperationId(operationId)
        if (synchronized(operationLock) { operationId in activeExtractions }) {
            remote.cancelExtraction(operationId)
        }
    }

    override fun startMutation(
        request: ExplorerArchiveMutationRequest,
        listener: ExplorerArchiveMutationListener,
    ): String {
        check(!closed.get()) { "Archive session is closed" }
        check(protocolVersion >= MUTATION_PROTOCOL_VERSION) {
            "Archive session does not support host mutations"
        }
        val source = File(sourcePath).canonicalFile
        require(source.isFile && source.canRead()) { "Archive source is no longer readable" }
        val parent = requireNotNull(source.parentFile?.canonicalFile) {
            "Archive source has no parent directory"
        }
        val requestBundle = Bundle()
        when (request) {
            is ExplorerArchiveMutationRequest.Delete -> {
                check(info.canDeleteEntries) { "Archive session does not support deletion" }
                require(
                    request.entryIds.isNotEmpty() &&
                        request.entryIds.size <= ExplorerActionProtocol.MAX_ARCHIVE_MUTATION_TARGETS,
                ) { "Archive mutation target count is invalid" }
                val entryIds = request.entryIds.map { entryId ->
                    requireValidId(entryId, "archive mutation entry")
                }
                require(entryIds.distinct().size == entryIds.size) {
                    "Archive mutation contains duplicate entry IDs"
                }
                requestBundle.putInt(
                    ExplorerArchiveMutationKeys.OPERATION,
                    ExplorerArchiveSessionValues.MUTATION_DELETE,
                )
                requestBundle.putStringArrayList(
                    ExplorerArchiveMutationKeys.ENTRY_IDS,
                    ArrayList(entryIds),
                )
            }
            is ExplorerArchiveMutationRequest.Rename -> {
                check(info.canRenameEntries) { "Archive session does not support rename" }
                val entryId = requireValidId(request.entryId, "archive mutation entry")
                val newName = requireValidMutationName(request.newName)
                requestBundle.putInt(
                    ExplorerArchiveMutationKeys.OPERATION,
                    ExplorerArchiveSessionValues.MUTATION_RENAME,
                )
                requestBundle.putString(ExplorerArchiveMutationKeys.ENTRY_ID, entryId)
                requestBundle.putString(ExplorerArchiveMutationKeys.NEW_NAME, newName)
            }
            is ExplorerArchiveMutationRequest.CreateDirectory -> {
                check(
                    protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                        info.canCreateDirectory,
                ) { "Archive session does not support directory creation" }
                val parentEntryId = requireValidId(
                    request.parentEntryId,
                    "archive destination directory",
                )
                val newName = requireValidMutationName(request.newName)
                requestBundle.putInt(
                    ExplorerArchiveMutationKeys.OPERATION,
                    ExplorerArchiveSessionValues.MUTATION_CREATE_DIRECTORY,
                )
                requestBundle.putString(
                    ExplorerArchiveMutationKeys.PARENT_ENTRY_ID,
                    parentEntryId,
                )
                requestBundle.putString(ExplorerArchiveMutationKeys.NEW_NAME, newName)
            }
        }
        val outputSession = ExplorerActionHostSession.create(
            context = context,
            pluginPackageName = pluginPackageName,
            commonParent = parent,
            targets = listOf(ExplorerActionHostSessionTarget(SOURCE_TARGET_ID, source)),
            allowCreateOutput = false,
            allowReplaceTarget = true,
            allowReplacementUndo = true,
        )
        val operationId = UUID.randomUUID().toString()
        val active = ActiveMutation(outputSession, listener)
        try {
            requestBundle.putString(ExplorerArchiveMutationKeys.OPERATION_ID, operationId)
            synchronized(operationLock) {
                check(!closed.get()) { "Archive session is closed" }
                check(activeExtractions.isEmpty() && activeMutations.isEmpty()) {
                    "Another archive operation is already running"
                }
                activeMutations[operationId] = active
            }
            remote.mutateEntries(requestBundle, outputSession, mutationCallback(operationId))
        } catch (error: Throwable) {
            synchronized(operationLock) {
                if (activeMutations[operationId] === active) activeMutations.remove(operationId)
            }
            runCatching { outputSession.closeFromHost() }
            throw error
        }
        return operationId
    }

    override fun cancelMutation(operationId: String) {
        check(!closed.get()) { "Archive session is closed" }
        requireCanonicalOperationId(operationId)
        if (synchronized(operationLock) { operationId in activeMutations }) {
            remote.cancelMutation(operationId)
        }
    }

    override fun close() {
        closeInternal(notifyPluginUnavailable = false)
    }

    private fun closeInternal(notifyPluginUnavailable: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        runCatching { remote.asBinder().unlinkToDeath(deathRecipient, 0) }
        val (abandonedExtractions, abandonedMutations) = synchronized(operationLock) {
            val extractions = activeExtractions.values.toList()
            val mutations = activeMutations.values.toList()
            activeExtractions.clear()
            activeMutations.clear()
            extractions to mutations
        }
        runCatching { remote.close() }
        abandonedExtractions.forEach { extraction ->
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
        abandonedMutations.forEach { mutation ->
            runCatching { mutation.outputSession.closeFromHost() }
            if (notifyPluginUnavailable && mutation.terminal.compareAndSet(false, true)) {
                runCatching {
                    mutation.listener.onFailed(
                        ExplorerArchiveMutationFailure(
                            code = ExplorerArchiveSessionValues.MUTATION_ERROR_PLUGIN_UNAVAILABLE,
                            message = "Archive plugin connection was lost",
                        ),
                    )
                }
            }
        }
        runCatching { bindingLease.close() }
        runCatching { volumeSource?.close() }
    }

    private fun extractionCallback(operationId: String) =
        object : IExplorerArchiveOperationCallback.Stub() {
            override fun onProgress(update: Bundle) {
                enforcePluginCaller()
                val active = synchronized(operationLock) { activeExtractions[operationId] } ?: return
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

    private fun mutationCallback(operationId: String) =
        object : IExplorerArchiveOperationCallback.Stub() {
            override fun onProgress(update: Bundle) {
                enforcePluginCaller()
                val active = synchronized(operationLock) { activeMutations[operationId] } ?: return
                if (active.terminal.get()) return
                runCatching { decodeMutationProgress(update, operationId) }
                    .onSuccess(active.listener::onProgress)
                    .onFailure { error -> finishMalformedMutationCallback(operationId, error) }
            }

            override fun onCompleted(result: Bundle) {
                enforcePluginCaller()
                runCatching { decodeMutationResult(result, operationId) }
                    .onSuccess { decoded ->
                        info = decoded.updatedInfo
                        finishMutation(operationId) { it.onCompleted(decoded) }
                    }
                    .onFailure { error -> finishMalformedMutationCallback(operationId, error) }
            }

            override fun onFailed(failure: Bundle) {
                enforcePluginCaller()
                runCatching { decodeMutationFailure(failure, operationId) }
                    .onSuccess { decoded -> finishMutation(operationId) { it.onFailed(decoded) } }
                    .onFailure { error -> finishMalformedMutationCallback(operationId, error) }
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
        val active = synchronized(operationLock) { activeExtractions.remove(operationId) } ?: return
        if (!active.terminal.compareAndSet(false, true)) return
        runCatching { active.outputSession.closeFromHost() }
        runCatching { notify(active.listener) }
    }

    private fun finishMalformedMutationCallback(operationId: String, error: Throwable) {
        finishMutation(operationId) { listener ->
            listener.onFailed(
                ExplorerArchiveMutationFailure(
                    code = ExplorerArchiveSessionValues.MUTATION_ERROR_UNKNOWN,
                    message = safeMutationFailureMessage(error.message),
                ),
            )
        }
    }

    private fun finishMutation(
        operationId: String,
        notify: (ExplorerArchiveMutationListener) -> Unit,
    ) {
        val active = synchronized(operationLock) { activeMutations.remove(operationId) } ?: return
        if (!active.terminal.compareAndSet(false, true)) return
        runCatching { active.outputSession.closeFromHost() }
        runCatching { notify(active.listener) }
    }

    private fun decodeMutationProgress(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveMutationProgress {
        requireMutationOperationId(bundle, expectedOperationId)
        val phase = bundle.getInt(ExplorerArchiveMutationKeys.PHASE, 0)
        require(phase in MUTATION_PHASES) { "Archive mutation phase is invalid" }
        val completedEntries = bundle.getInt(ExplorerArchiveMutationKeys.COMPLETED_ENTRIES, -1)
        val totalEntries = bundle.getInt(ExplorerArchiveMutationKeys.TOTAL_ENTRIES, -1)
        require(completedEntries >= 0 && totalEntries >= 0 && completedEntries <= totalEntries) {
            "Archive mutation entry progress is invalid"
        }
        return ExplorerArchiveMutationProgress(
            phase = phase,
            currentEntry = bundle.getString(ExplorerArchiveMutationKeys.CURRENT_ENTRY)
                ?.let(::sanitizeOperationText),
            completedEntries = completedEntries,
            totalEntries = totalEntries,
        )
    }

    private fun decodeMutationResult(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveMutationResult {
        requireMutationOperationId(bundle, expectedOperationId)
        val updatedBundle = requireNotNull(bundle.getBundle(ExplorerArchiveMutationKeys.SESSION_INFO)) {
            "Archive mutation result has no updated session information"
        }
        val mutatedEntries = bundle.getInt(ExplorerArchiveMutationKeys.MUTATED_ENTRIES, -1)
        require(mutatedEntries in 1..ExplorerActionProtocol.MAX_ARCHIVE_MUTATION_TARGETS) {
            "Archive mutation result count is invalid"
        }
        return ExplorerArchiveMutationResult(
            updatedInfo = decodeUpdatedSessionInfo(updatedBundle),
            mutatedEntries = mutatedEntries,
        )
    }

    private fun decodeMutationFailure(
        bundle: Bundle,
        expectedOperationId: String,
    ): ExplorerArchiveMutationFailure {
        requireMutationOperationId(bundle, expectedOperationId)
        val code = bundle.getInt(ExplorerArchiveMutationKeys.ERROR_CODE, 0)
        require(code in MUTATION_ERROR_CODES) { "Archive mutation error code is invalid" }
        return ExplorerArchiveMutationFailure(
            code = code,
            message = safeMutationFailureMessage(
                bundle.getString(ExplorerArchiveMutationKeys.ERROR_MESSAGE),
            ),
        )
    }

    private fun decodeUpdatedSessionInfo(bundle: Bundle): ExplorerArchiveSessionInfo {
        val previous = info
        require(bundle.getString(ExplorerArchiveSessionKeys.SESSION_ID) == previous.sessionId) {
            "Archive session ID changed after mutation"
        }
        require(bundle.getString(ExplorerArchiveSessionKeys.ROOT_ID) == previous.rootId) {
            "Archive root ID changed after mutation"
        }
        require(sanitizeDisplayName(bundle.getString(ExplorerArchiveSessionKeys.DISPLAY_NAME)) == previous.displayName) {
            "Archive display name changed after mutation"
        }
        val sourceSize = bundle.getLong(ExplorerArchiveSessionKeys.SOURCE_SIZE, -1L)
        val sourceLastModified = bundle.getLong(ExplorerArchiveSessionKeys.SOURCE_LAST_MODIFIED, -1L)
        require(sourceSize >= 0L && sourceLastModified >= 0L) {
            "Archive source identity is invalid after mutation"
        }
        val format = decodeFormatMetadata(bundle, protocolVersion)
        require(format.id == previous.formatId && format.displayName == previous.formatDisplayName) {
            "Archive format changed after mutation"
        }
        val filenameCharsets = decodeFilenameCharsetMetadata(bundle, protocolVersion)
        return previous.copy(
            sourceSize = sourceSize,
            sourceLastModified = sourceLastModified,
            canOpenEntries = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_OPEN_ENTRIES, false),
            canExtractEntries = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_EXTRACT_ENTRIES, false),
            canDeleteEntries = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_DELETE_ENTRIES, false),
            canRenameEntries = bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_RENAME_ENTRIES, false),
            canCreateDirectory = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_CREATE_DIRECTORY, false),
            canAddEntries = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_ADD_ENTRIES, false),
            displayNameMatchesFormat = format.displayNameMatches,
            filenameCharsetNames = filenameCharsets.supportedNames,
            filenameCharsetName = filenameCharsets.effectiveName,
            filenameCharsetOverride = filenameCharsets.overrideName,
        )
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
        val interactionKind = if (
            code == ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED &&
            protocolVersion >= EXTRACTION_RECOVERY_PROTOCOL_VERSION
        ) {
            bundle.getInt(
                ExplorerArchiveOperationKeys.INTERACTION_KIND,
                ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_NONE,
            ).also { kind ->
                require(kind in EXTRACTION_INTERACTION_KINDS) {
                    "Archive extraction interaction kind is invalid"
                }
            }
        } else {
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_NONE
        }
        return ExplorerArchiveExtractionFailure(
            code = code,
            message = safeFailureMessage(bundle.getString(ExplorerArchiveOperationKeys.ERROR_MESSAGE)),
            interactionKind = interactionKind,
        )
    }

    private fun requireOperationId(bundle: Bundle, expectedOperationId: String) {
        require(bundle.getString(ExplorerArchiveOperationKeys.OPERATION_ID) == expectedOperationId) {
            "Archive extraction operation ID changed"
        }
    }

    private fun requireMutationOperationId(bundle: Bundle, expectedOperationId: String) {
        require(bundle.getString(ExplorerArchiveMutationKeys.OPERATION_ID) == expectedOperationId) {
            "Archive mutation operation ID changed"
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
    ) { "Archive operation text is invalid" }

    private fun safeFailureMessage(value: String?): String = runCatching {
        sanitizeOperationText(value ?: "Archive extraction failed")
    }.getOrDefault("Archive extraction failed")

    private fun safeMutationFailureMessage(value: String?): String = runCatching {
        sanitizeOperationText(value ?: "Archive mutation failed")
    }.getOrDefault("Archive mutation failed")

    private fun requireValidMutationName(value: String): String = requireNotNull(
        value.takeIf { name ->
            name.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_MUTATION_NAME_LENGTH &&
                name != "." &&
                name != ".." &&
                name.any { !it.isWhitespace() } &&
                name.none { character ->
                    character == '/' ||
                        character == '\\' ||
                        character == '\u0000' ||
                        character.code < 0x20 ||
                        character.code == 0x7F
                }
        },
    ) { "Archive mutation entry name is invalid" }

    private fun enforcePluginCaller() {
        if (Binder.getCallingUid() != pluginUid) {
            throw SecurityException("Archive operation callback caller changed")
        }
    }

    private fun requireCanonicalOperationId(value: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        require(parsed?.toString()?.equals(value, ignoreCase = true) == true) {
            "Archive operation ID is invalid"
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
            canOpen = if (protocolVersion >= ENTRY_CAPABILITIES_PROTOCOL_VERSION) {
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_OPEN, false)
            } else {
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_EXTRACT, false)
            },
            canDelete = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_DELETE, false),
            canRename = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_RENAME, false),
            canCreateChildren = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_CREATE_CHILDREN, false),
            canAddChildren = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_ADD_CHILDREN, false),
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

    private data class ActiveMutation(
        val outputSession: ExplorerActionHostSession,
        val listener: ExplorerArchiveMutationListener,
        val terminal: AtomicBoolean = AtomicBoolean(false),
    )

    private data class FilenameCharsetMetadata(
        val supportedNames: List<String>,
        val effectiveName: String?,
        val overrideName: String?,
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
            volumeSource: Closeable? = null,
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
                val formatMetadata = decodeFormatMetadata(bundle, protocolVersion)
                val filenameCharsets = decodeFilenameCharsetMetadata(bundle, protocolVersion)
                return ExplorerArchiveSessionConnection(
                    context = context.applicationContext,
                    pluginPackageName = pluginPackageName,
                    pluginUid = pluginUid,
                    protocolVersion = protocolVersion,
                    remote = remote,
                    bindingLease = bindingLease,
                    volumeSource = volumeSource,
                    sourcePath = sourcePath,
                    initialInfo = ExplorerArchiveSessionInfo(
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
                        canDeleteEntries = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                            bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_DELETE_ENTRIES, false),
                        canRenameEntries = protocolVersion >= MUTATION_PROTOCOL_VERSION &&
                            bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_RENAME_ENTRIES, false),
                        canCreateDirectory =
                            protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                                bundle.getBoolean(
                                    ExplorerArchiveSessionKeys.CAN_CREATE_DIRECTORY,
                                    false,
                                ),
                        canAddEntries = protocolVersion >= DIRECTORY_CREATION_PROTOCOL_VERSION &&
                            bundle.getBoolean(ExplorerArchiveSessionKeys.CAN_ADD_ENTRIES, false),
                        formatId = formatMetadata.id,
                        formatDisplayName = formatMetadata.displayName,
                        displayNameMatchesFormat = formatMetadata.displayNameMatches,
                        filenameCharsetNames = filenameCharsets.supportedNames,
                        filenameCharsetName = filenameCharsets.effectiveName,
                        filenameCharsetOverride = filenameCharsets.overrideName,
                    ),
                )
            } catch (error: Throwable) {
                runCatching { remote.close() }
                runCatching { bindingLease.close() }
                runCatching { volumeSource?.close() }
                throw error
            }
        }

        private fun decodeFilenameCharsetMetadata(
            bundle: Bundle,
            protocolVersion: Int,
        ): FilenameCharsetMetadata {
            if (protocolVersion < FILENAME_CHARSET_PROTOCOL_VERSION) {
                return FilenameCharsetMetadata(emptyList(), null, null)
            }
            @Suppress("DEPRECATION")
            val rawSupportedNames = bundle
                .getStringArrayList(ExplorerArchiveSessionKeys.FILENAME_CHARSET_NAMES)
                .orEmpty()
            require(rawSupportedNames.size <= ExplorerActionProtocol.MAX_ARCHIVE_FILENAME_CHARSET_NAMES) {
                "Archive session returned too many filename charsets"
            }
            val supportedNames = rawSupportedNames.map(::requireValidCharsetName)
            require(
                supportedNames.distinctBy { name -> name.lowercase(Locale.ROOT) }.size ==
                    supportedNames.size,
            ) { "Archive session returned duplicate filename charsets" }
            val effectiveName = bundle.getString(ExplorerArchiveSessionKeys.FILENAME_CHARSET_NAME)
                ?.let(::requireValidCharsetName)
                ?.also { effective ->
                    require(supportedNames.any { it.equals(effective, ignoreCase = true) }) {
                        "Archive session returned an unsupported effective filename charset"
                    }
                }
            require(supportedNames.isNotEmpty() || effectiveName == null) {
                "Archive session returned a filename charset for an unsupported format"
            }
            val overrideName = if (
                bundle.containsKey(ExplorerArchiveSessionKeys.FILENAME_CHARSET_OVERRIDE)
            ) {
                val rawOverride = requireNotNull(
                    bundle.getString(ExplorerArchiveSessionKeys.FILENAME_CHARSET_OVERRIDE),
                ) { "Archive session returned an empty filename charset override" }
                val override = requireValidCharsetName(rawOverride)
                require(supportedNames.any { it.equals(override, ignoreCase = true) }) {
                    "Archive session returned an unsupported filename charset override"
                }
                supportedNames.first { it.equals(override, ignoreCase = true) }
            } else {
                null
            }
            return FilenameCharsetMetadata(
                supportedNames = supportedNames,
                effectiveName = effectiveName,
                overrideName = overrideName,
            )
        }

        private fun decodeFormatMetadata(
            bundle: Bundle,
            protocolVersion: Int,
        ): ArchiveFormatMetadata {
            if (protocolVersion < ARCHIVE_FORMAT_PROTOCOL_VERSION) {
                return ArchiveFormatMetadata(null, null, true)
            }
            val id = requireNotNull(bundle.getString(ExplorerArchiveSessionKeys.FORMAT_ID)?.takeIf {
                it.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_FORMAT_ID_LENGTH &&
                    ARCHIVE_FORMAT_ID_PATTERN.matches(it)
            }) { "Archive session returned an invalid format ID" }
            val displayName = requireNotNull(
                bundle.getString(ExplorerArchiveSessionKeys.FORMAT_DISPLAY_NAME)
                    ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
                    ?.trim()
                    ?.take(ExplorerActionProtocol.MAX_ARCHIVE_FORMAT_DISPLAY_NAME_LENGTH)
                    ?.takeIf(String::isNotEmpty),
            ) { "Archive session returned an invalid format display name" }
            require(bundle.containsKey(ExplorerArchiveSessionKeys.DISPLAY_NAME_MATCHES_FORMAT)) {
                "Archive session did not report whether its display name matches the format"
            }
            return ArchiveFormatMetadata(
                id = id,
                displayName = displayName,
                displayNameMatches = bundle.getBoolean(
                    ExplorerArchiveSessionKeys.DISPLAY_NAME_MATCHES_FORMAT,
                ),
            )
        }

        private fun requireValidCharsetName(value: String): String = requireNotNull(
            value.takeIf { name ->
                name.length in 1..ExplorerActionProtocol.MAX_ARCHIVE_FILENAME_CHARSET_NAME_LENGTH &&
                    name == name.trim() &&
                    name.all { character ->
                        character.isLetterOrDigit() || character == '-' ||
                            character == '_' || character == '.'
                    }
            },
        ) { "Archive session returned an invalid filename charset" }

        private const val EXTRACTION_PROTOCOL_VERSION = 10
        private const val EXTRACTION_RECOVERY_PROTOCOL_VERSION = 11
        private const val ENTRY_CAPABILITIES_PROTOCOL_VERSION = 11
        private const val FILENAME_CHARSET_PROTOCOL_VERSION = 13
        private const val ARCHIVE_FORMAT_PROTOCOL_VERSION = 17
        private const val MUTATION_PROTOCOL_VERSION = 19
        private const val DIRECTORY_CREATION_PROTOCOL_VERSION = 20
        private const val SOURCE_TARGET_ID = "archive-source"
        private val ARCHIVE_FORMAT_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")
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
        private val MUTATION_PHASES = setOf(
            ExplorerArchiveSessionValues.MUTATION_PHASE_PREPARING,
            ExplorerArchiveSessionValues.MUTATION_PHASE_WRITING,
            ExplorerArchiveSessionValues.MUTATION_PHASE_VERIFYING,
            ExplorerArchiveSessionValues.MUTATION_PHASE_COMMITTING,
            ExplorerArchiveSessionValues.MUTATION_PHASE_REINDEXING,
        )
        private val MUTATION_ERROR_CODES = setOf(
            ExplorerArchiveSessionValues.MUTATION_ERROR_CANCELLED,
            ExplorerArchiveSessionValues.MUTATION_ERROR_SOURCE_CHANGED,
            ExplorerArchiveSessionValues.MUTATION_ERROR_UNSUPPORTED,
            ExplorerArchiveSessionValues.MUTATION_ERROR_INVALID_REQUEST,
            ExplorerArchiveSessionValues.MUTATION_ERROR_OUTPUT,
            ExplorerArchiveSessionValues.MUTATION_ERROR_PLUGIN_UNAVAILABLE,
            ExplorerArchiveSessionValues.MUTATION_ERROR_UNKNOWN,
        )
        private val EXTRACTION_INTERACTION_KINDS = setOf(
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_PASSWORD_REQUIRED,
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_WRONG_PASSWORD,
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_UNSAFE_PATHS,
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_RESOURCE_BUDGET,
        )
    }

    private data class ArchiveFormatMetadata(
        val id: String?,
        val displayName: String?,
        val displayNameMatches: Boolean,
    )
}

private fun isInvalidOpaqueIdCharacter(character: Char): Boolean =
    character.isWhitespace() || character.code < 0x20 || character.code == 0x7F
