package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.autojs.core.plugin.explorer.ExplorerActionCatalogPolicy
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.storage.history.TrashMoveRecoveryRequiredException
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.DialogUtils
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors

internal data class ExplorerActionHostSessionTarget(
    val id: String,
    val file: File,
)

internal fun explorerItemForCreatedOutput(file: File): ExplorerFileItem {
    val parent = ExplorerDirPage(file.parentFile, null)
    return if (file.isDirectory) ExplorerDirPage(file, parent) else ExplorerFileItem(file, parent)
}

/** A request-scoped capability. Every remote call is pinned to the launched plugin UID. */
internal class ExplorerActionHostSession private constructor(
    private val applicationContext: Context,
    private val expectedUid: Int,
    commonParent: File,
    private val selectedOutputDirectory: File?,
    targets: List<ExplorerActionHostSessionTarget>,
    private val allowCreateOutput: Boolean,
    private val allowReplaceTarget: Boolean,
    private val allowReplacementUndo: Boolean,
    private val allowTargetTrash: Boolean,
    private val allowTargetRename: Boolean,
    allowReadSiblings: Boolean,
    allowPlaybackProgress: Boolean,
    relatedFileSuffixes: Set<String>,
    private val onOutputCommitted: (File) -> Unit,
    private val onTargetReplaced: (File) -> Unit,
    private val onSourcesTrashed: () -> Unit,
    private val outputTreeStore: ExplorerActionOutputTreeStore,
    private val outputBatchStore: ExplorerActionOutputBatchStore,
    private val targetTrashBatchStore: ExplorerActionTargetTrashBatchStore,
    private val replacementHistoryStore: ExplorerActionReplacementHistoryStore,
    playbackHistoryStore: ExplorerPlaybackHistoryStore,
    private val clock: () -> Long,
    private val afterBatchMemberPublished: (Int) -> Unit,
    private val afterReplacementPublished: () -> Unit,
) : IExplorerActionHostSession.Stub() {

    private val lock = Any()
    private val inputAccess = ExplorerActionHostInputAccess(
        commonParent = commonParent,
        targets = targets,
        allowReadSiblings = allowReadSiblings,
        allowPlaybackProgress = allowPlaybackProgress,
        relatedFileSuffixes = relatedFileSuffixes,
        playbackHistoryStore = playbackHistoryStore,
        requireSessionOpen = { synchronized(lock) { checkOpen() } },
    )
    private val parent: File = selectedOutputDirectory ?: inputAccess.parent
    private val targetsById: Map<String, ExplorerActionHostTargetState>
        get() = inputAccess.targetsById
    private val outputs = LinkedHashMap<String, OutputTransaction>()
    private val terminalOutputs = LinkedHashMap<String, Bundle>()
    private val committedCreateOutputs = LinkedHashMap<String, CommittedCreateOutput>()
    private val outputBatches = LinkedHashMap<String, ExplorerActionOutputBatchRecord>()
    private var targetTrashResult: Bundle? = null
    private var targetTrashRequest: List<String> = emptyList()
    private val trashedTargetIds = HashSet<String>()
    private var targetTrashRunning = false
    private var clientLease: ClientLease? = null
    private var closed = false

    override fun listChildren(targetId: String, relativePath: String, offset: Int, limit: Int): Bundle {
        enforceCaller()
        return inputAccess.listChildren(targetId, relativePath, offset, limit)
    }

    override fun openFile(targetId: String, relativePath: String): ParcelFileDescriptor {
        enforceCaller()
        return inputAccess.openFile(targetId, relativePath)
    }

    override fun getPlaybackProgress(targetId: String, relativePath: String): Bundle {
        enforceCaller()
        return inputAccess.getPlaybackProgress(targetId, relativePath)
    }

    override fun reportPlaybackProgress(
        targetId: String,
        relativePath: String,
        positionMillis: Long,
        durationMillis: Long,
        reportState: Int,
    ) {
        enforceCaller()
        inputAccess.reportPlaybackProgress(
            targetId = targetId,
            relativePath = relativePath,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            reportState = reportState,
        )
    }

    override fun queryTargetReplacement(targetId: String): Bundle {
        enforceCaller()
        check(allowReplacementUndo) {
            "Explorer Action does not have replacement-history access"
        }
        return synchronized(lock) {
            checkOpen()
            val target = targetState(targetId)
            replacementHistoryBundle(
                replacementHistoryStore.queryLatest(expectedUid, target.file),
            )
        }
    }

    override fun undoTargetReplacement(targetId: String, replacementHistoryId: String): Bundle {
        enforceCaller()
        check(allowReplacementUndo) {
            "Explorer Action does not have replacement-history access"
        }
        val restored = synchronized(lock) {
            checkOpen()
            val target = targetState(targetId)
            val result = replacementHistoryStore.undo(
                pluginUid = expectedUid,
                target = target.file,
                historyId = replacementHistoryId,
            )
            target.identity = result.restoredIdentity
            RestoredReplacement(
                target = target.file,
                result = replacementHistoryBundle(result.record).apply {
                    putLong(ExplorerActionHostSessionKeys.SIZE, target.file.length())
                    putLong(
                        ExplorerActionHostSessionKeys.LAST_MODIFIED,
                        target.file.lastModified(),
                    )
                },
            )
        }
        runCatching { onTargetReplaced(restored.target) }
        return restored.result
    }

    override fun prepareOutput(displayName: String, mimeType: String, conflictPolicy: Int): Bundle {
        enforceCaller()
        check(selectedOutputDirectory == null) { "Extraction sessions only support directory outputs" }
        check(allowCreateOutput) { "Explorer Action does not have create-in-parent access" }
        val normalizedName = normalizeOutputName(displayName)
        val normalizedMimeType = ExplorerActionCatalogPolicy.normalizeMimeType(mimeType)
            ?.takeIf { it.length <= ExplorerActionProtocol.MAX_OUTPUT_MIME_TYPE_LENGTH }
            ?: throw IllegalArgumentException("Explorer Action output MIME type is invalid")
        val transaction = synchronized(lock) {
            checkOpen()
            require(parent.canWrite()) { "Explorer Action parent directory is not writable" }
            val target = reserveOutputTarget(normalizedName, conflictPolicy)
            val temp = File(parent, ".autojs6-explorer-${UUID.randomUUID()}.part")
            try {
                check(temp.createNewFile()) { "Explorer Action output staging file already exists" }
                OutputTransaction(
                    id = UUID.randomUUID().toString(),
                    mimeType = normalizedMimeType,
                    target = target,
                    temp = temp,
                    mode = OutputMode.CREATE,
                ).also { outputs[it.id] = it }
            } catch (error: Throwable) {
                releaseOutputTarget(target)
                temp.delete()
                throw error
            }
        }
        return outputBundle(transaction)
    }

    override fun prepareTargetReplacement(targetId: String): Bundle {
        enforceCaller()
        check(allowReplaceTarget) { "Explorer Action does not have replace-target access" }
        val transaction = synchronized(lock) {
            checkOpen()
            require(parent.canWrite()) { "Explorer Action parent directory is not writable" }
            val targetState = targetState(targetId)
            require(targetState.file.isFile && targetState.file.canRead()) {
                "Explorer Action replacement target is not a readable file"
            }
            val expectedIdentity = targetState.identity
            require(TargetIdentity.capture(targetState.file) == expectedIdentity) {
                "Explorer Action replacement target changed before reservation"
            }
            reserveReplacementTarget(targetState.file)
            val temp = File(parent, ".autojs6-explorer-${UUID.randomUUID()}.part")
            try {
                check(temp.createNewFile()) { "Explorer Action replacement staging file already exists" }
                OutputTransaction(
                    id = UUID.randomUUID().toString(),
                    mimeType = Mime.fromFileOrWildcard(targetState.file.path),
                    target = targetState.file,
                    temp = temp,
                    mode = OutputMode.REPLACE,
                    replacementTarget = targetState,
                    expectedTargetIdentity = expectedIdentity,
                ).also { outputs[it.id] = it }
            } catch (error: Throwable) {
                releaseOutputTarget(targetState.file)
                temp.delete()
                throw error
            }
        }
        return outputBundle(transaction)
    }

    override fun prepareOutputTree(displayName: String, conflictPolicy: Int): Bundle {
        enforceCaller()
        check(allowCreateOutput) { "Explorer Action does not have create-in-parent access" }
        val normalizedName = normalizeOutputName(displayName)
        val transaction = synchronized(lock) {
            checkOpen()
            require(parent.canWrite()) { "Explorer Action parent directory is not writable" }
            val id = UUID.randomUUID().toString()
            val target = reserveOutputTreeTarget(normalizedName, conflictPolicy)
            val marker = File(target, "$OUTPUT_TREE_MARKER_PREFIX$id")
            try {
                check(marker.createNewFile()) { "Explorer Action output tree marker already exists" }
                FileOutputStream(marker).use { output ->
                    output.write(id.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                val parentIdentity = TargetIdentity.capture(parent)
                val targetIdentity = TargetIdentity.capture(target)
                val now = clock()
                val record = ExplorerActionOutputTreeRecord(
                    id = id,
                    pluginUid = expectedUid,
                    processInstanceId = PROCESS_INSTANCE_ID,
                    parentPath = parent.path,
                    parentDevice = parentIdentity.device,
                    parentInode = parentIdentity.inode,
                    targetPath = target.path,
                    targetDevice = targetIdentity.device,
                    targetInode = targetIdentity.inode,
                    displayName = target.name,
                    markerName = marker.name,
                    state = ExplorerActionHostSessionValues.OUTPUT_STATE_PREPARED,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                markOutputTreeActive(id, true)
                outputTreeStore.write(record)
                OutputTransaction(
                    id = id,
                    mimeType = DIRECTORY_MIME_TYPE,
                    target = target,
                    temp = target,
                    mode = OutputMode.CREATE_TREE,
                    outputTreeMarker = marker,
                    outputTreeRecord = record,
                ).also { outputs[it.id] = it }
            } catch (error: Throwable) {
                markOutputTreeActive(id, false)
                safeDeleteTree(target)
                releaseOutputTarget(target)
                outputTreeStore.remove(id)
                throw error
            }
        }
        return outputBundle(transaction)
    }

    override fun createOutputDirectory(transactionId: String, relativePath: String) {
        enforceCaller()
        synchronized(lock) {
            checkOpen()
            val transaction = outputTree(transactionId)
            val normalizedPath = normalizeOutputRelativePath(relativePath)
            requireOutputTreeCapacity(transaction, normalizedPath)
            requireOutputTreeParent(transaction, normalizedPath)
            val directory = resolveOutputTreePath(transaction, normalizedPath)
            check(!directory.exists() && directory.mkdir()) {
                "Explorer Action output directory could not be created"
            }
            transaction.outputTreeNodes[normalizedPath] = OutputTreeNodeKind.DIRECTORY
            markOutputTreeWriting(transaction)
        }
    }

    override fun openOutputFile(transactionId: String, relativePath: String): ParcelFileDescriptor {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val transaction = outputTree(transactionId)
            val normalizedPath = normalizeOutputRelativePath(relativePath)
            requireOutputTreeCapacity(transaction, normalizedPath)
            requireOutputTreeParent(transaction, normalizedPath)
            val file = resolveOutputTreePath(transaction, normalizedPath)
            check(!file.exists() && file.createNewFile()) {
                "Explorer Action output file could not be created"
            }
            try {
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
                ).also {
                    transaction.outputTreeNodes[normalizedPath] = OutputTreeNodeKind.FILE
                    markOutputTreeWriting(transaction)
                }
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }
    }

    override fun queryOutput(transactionId: String): Bundle {
        enforceCaller()
        require(isCanonicalUuid(transactionId)) { "Explorer Action output transaction ID is invalid" }
        return synchronized(lock) {
            checkOpen()
            outputs[transactionId]?.let(::outputBundle)
                ?: terminalOutputs[transactionId]?.let(::Bundle)
                ?: outputTreeStore.read(transactionId)
                    ?.takeIf(::belongsToThisSessionParent)
                    ?.let(::outputTreeRecordBundle)
                ?: Bundle().apply {
                    putString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID, transactionId)
                    putInt(
                        ExplorerActionHostSessionKeys.OUTPUT_STATE,
                        ExplorerActionHostSessionValues.OUTPUT_STATE_UNKNOWN,
                    )
                }
        }
    }

    override fun listOutputs(): Bundle {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val results = LinkedHashMap<String, Bundle>()
            outputTreeStore.list()
                .filter(::belongsToThisSessionParent)
                .takeLast(ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS)
                .forEach { record -> results[record.id] = outputTreeRecordBundle(record) }
            terminalOutputs.forEach { (id, bundle) -> results[id] = Bundle(bundle) }
            outputs.forEach { (id, transaction) -> results[id] = outputBundle(transaction) }
            Bundle().apply {
                putParcelableArrayList(
                    ExplorerActionHostSessionKeys.OUTPUTS,
                    ArrayList(
                        results.values.toList()
                            .takeLast(ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS),
                    ),
                )
            }
        }
    }

    override fun attachClient(clientToken: IBinder) {
        enforceCaller()
        val deathRecipient = IBinder.DeathRecipient {
            CLIENT_DEATH_EXECUTOR.execute { closeInternal(clientToken) }
        }
        val previousLease = synchronized(lock) {
            checkOpen()
            clientToken.linkToDeath(deathRecipient, 0)
            clientLease.also {
                clientLease = ClientLease(clientToken, deathRecipient)
            }
        }
        previousLease?.let { previous ->
            runCatching { previous.token.unlinkToDeath(previous.deathRecipient, 0) }
        }
    }

    override fun prepareOutputBatch(transactionIds: MutableList<String>): Bundle {
        enforceCaller()
        val requestedIds = transactionIds.toList()
        require(requestedIds.size in 2..ExplorerActionProtocol.MAX_OUTPUT_BATCH_ENTRIES) {
            "Explorer Action output batch size is invalid"
        }
        require(requestedIds.distinct().size == requestedIds.size) {
            "Explorer Action output batch contains duplicate transactions"
        }
        return synchronized(lock) {
            checkOpen()
            require(parent.canWrite()) { "Explorer Action parent directory is not writable" }
            val transactions = requestedIds.map(::output)
            transactions.forEach { transaction ->
                check(transaction.mode == OutputMode.CREATE) {
                    "Explorer Action output batch accepts only new file outputs"
                }
                check(transaction.state == OutputState.VERIFYING) {
                    "Explorer Action output batch member has not been verified"
                }
                check(transaction.outputBatchId == null) {
                    "Explorer Action output already belongs to a batch"
                }
                check(!transaction.target.exists() && transaction.temp.isFile) {
                    "Explorer Action output batch member changed before reservation"
                }
            }
            val parentIdentity = TargetIdentity.capture(parent)
            val batchId = UUID.randomUUID().toString()
            val now = clock()
            val record = ExplorerActionOutputBatchRecord(
                id = batchId,
                pluginUid = expectedUid,
                processInstanceId = PROCESS_INSTANCE_ID,
                parentPath = parent.path,
                parentDevice = parentIdentity.device,
                parentInode = parentIdentity.inode,
                state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_PREPARED,
                createdAtMillis = now,
                updatedAtMillis = now,
                members = transactions.map { transaction ->
                    val identity = TargetIdentity.captureRegularFile(transaction.temp)
                    ExplorerActionOutputBatchMemberRecord(
                        transactionId = transaction.id,
                        targetPath = transaction.target.path,
                        stagingPath = transaction.temp.path,
                        stagingDevice = identity.device,
                        stagingInode = identity.inode,
                        stagingSize = identity.size,
                        stagingLastModifiedMillis = identity.lastModifiedMillis,
                    )
                },
            )
            markOutputBatchActive(batchId, true)
            try {
                outputBatchStore.write(record)
            } catch (error: Throwable) {
                markOutputBatchActive(batchId, false)
                throw error
            }
            transactions.forEach { transaction -> transaction.outputBatchId = batchId }
            outputBatches[batchId] = record
            outputBatchBundle(record)
        }
    }

    override fun commitOutputBatch(batchId: String): Bundle {
        enforceCaller()
        val committed = synchronized(lock) {
            checkOpen()
            val record = activeOutputBatch(batchId)
            check(record.state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_PREPARED) {
                "Explorer Action output batch is not ready to commit"
            }
            val transactions = record.members.map { member ->
                output(member.transactionId).also { transaction ->
                    check(transaction.outputBatchId == batchId) {
                        "Explorer Action output batch membership changed"
                    }
                }
            }
            var current = record.copy(
                state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTING,
                updatedAtMillis = outputBatchTimestamp(record),
            )
            outputBatchStore.write(current)
            outputBatches[batchId] = current
            try {
                transactions.forEachIndexed { index, transaction ->
                    val member = current.members[index]
                    requireBatchMemberIdentity(transaction, member)
                    FileOutputStream(transaction.temp, true).use { output -> output.fd.sync() }
                    requireBatchMemberIdentity(transaction, member)
                    check(!transaction.target.exists()) {
                        "Explorer Action output batch target now exists"
                    }
                    try {
                        Os.rename(transaction.temp.path, transaction.target.path)
                    } catch (error: ErrnoException) {
                        throw IOException("Explorer Action output batch member could not be published", error)
                    }
                    afterBatchMemberPublished(index + 1)
                    current = current.copy(
                        updatedAtMillis = outputBatchTimestamp(current),
                        publishedCount = index + 1,
                    )
                    outputBatchStore.write(current)
                    outputBatches[batchId] = current
                }
                syncDirectory(parent)
                current.members.forEach { member ->
                    check(batchMemberIdentityMatches(File(member.targetPath), member)) {
                        "Explorer Action output batch member changed after publication"
                    }
                    check(!File(member.stagingPath).exists()) {
                        "Explorer Action output batch staging file survived publication"
                    }
                }
                current = current.copy(
                    state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED,
                    updatedAtMillis = outputBatchTimestamp(current),
                    publishedCount = current.members.size,
                )
                outputBatchStore.write(current)
                outputBatches[batchId] = current

                val outputResults = ArrayList<Bundle>(transactions.size)
                transactions.forEach { transaction ->
                    outputs.remove(transaction.id)
                    releaseOutputTarget(transaction.target)
                    val result = committedOutputBundle(transaction)
                    rememberTerminalOutput(transaction.id, result)
                    rememberCommittedCreateOutput(transaction)
                    outputResults += result
                }
                markOutputBatchActive(batchId, false)
                CommittedOutputBatch(
                    targets = transactions.map(OutputTransaction::target),
                    result = outputBatchBundle(current).apply {
                        putParcelableArrayList(ExplorerActionHostSessionKeys.OUTPUTS, outputResults)
                    },
                )
            } catch (error: Throwable) {
                val rollback = rollbackOutputBatch(current)
                outputBatches[batchId] = rollback.record
                if (rollback.failures.isEmpty()) {
                    releaseOutputBatchTransactions(current)
                    markOutputBatchActive(batchId, false)
                } else {
                    rollback.failures.forEach(error::addSuppressed)
                }
                throw error
            }
        }
        committed.targets.forEach { target -> runCatching { onOutputCommitted(target) } }
        return committed.result
    }

    override fun abortOutputBatch(batchId: String) {
        enforceCaller()
        synchronized(lock) {
            checkOpen()
            val record = abortableOutputBatch(batchId)
            check(record.state != ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED) {
                "Explorer Action committed output batch cannot be aborted"
            }
            val rollback = rollbackOutputBatch(record)
            outputBatches[batchId] = rollback.record
            if (rollback.failures.isNotEmpty()) {
                val error = IOException("Explorer Action output batch rollback requires recovery")
                rollback.failures.forEach(error::addSuppressed)
                throw error
            }
            releaseOutputBatchTransactions(record)
            markOutputBatchActive(batchId, false)
        }
    }

    override fun queryOutputBatch(batchId: String): Bundle {
        enforceCaller()
        require(isCanonicalUuid(batchId)) { "Explorer Action output batch ID is invalid" }
        return synchronized(lock) {
            checkOpen()
            val record = outputBatches[batchId] ?: outputBatchStore.read(batchId)
            if (record == null || !belongsToThisSessionParent(record)) {
                unknownOutputBatchBundle(batchId)
            } else {
                outputBatchBundle(record)
            }
        }
    }

    override fun listOutputBatches(): Bundle {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val records = LinkedHashMap<String, ExplorerActionOutputBatchRecord>()
            outputBatchStore.list()
                .filter(::belongsToThisSessionParent)
                .takeLast(ExplorerActionProtocol.MAX_OUTPUT_BATCH_RESULTS)
                .forEach { record -> records[record.id] = record }
            outputBatches.forEach { (id, record) -> records[id] = record }
            Bundle().apply {
                putParcelableArrayList(
                    ExplorerActionHostSessionKeys.OUTPUT_BATCHES,
                    ArrayList(
                        records.values.toList()
                            .takeLast(ExplorerActionProtocol.MAX_OUTPUT_BATCH_RESULTS)
                            .map(::outputBatchBundle),
                    ),
                )
            }
        }
    }

    override fun moveTargetsToTrash(
        targetIds: MutableList<String>,
        outputTransactionIds: MutableList<String>,
    ): Bundle {
        enforceCaller()
        check(selectedOutputDirectory == null) { "Extraction sessions cannot trash source targets" }
        val requestedTargetIds = targetIds.toList()
        val requestedOutputIds = outputTransactionIds.toList()
        val repository = TrashRepository(applicationContext)
        val plan = synchronized(lock) {
            checkOpen()
            if (allowTargetTrash) {
                return@synchronized prepareSelectedTargetTrash(requestedTargetIds, requestedOutputIds, repository)
                    ?: return Bundle(requireNotNull(targetTrashResult))
            }
            targetTrashResult?.let { return Bundle(it) }
            check(!targetTrashRunning) { "Explorer Action target Trash operation is already running" }
            require(requestedTargetIds == targetsById.keys.toList()) {
                "Explorer Action target Trash operation must cover the complete request selection"
            }
            require(
                requestedOutputIds.isNotEmpty() &&
                    requestedOutputIds.size <= ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS &&
                    requestedOutputIds.distinct().size == requestedOutputIds.size,
            ) { "Explorer Action target Trash output proof is invalid" }
            require(requestedOutputIds == committedCreateOutputs.keys.toList()) {
                "Explorer Action target Trash output proof is incomplete or reordered"
            }
            committedCreateOutputs.values.forEach(::requireCommittedCreateOutputIdentity)
            targetsById.values.forEach(::requireTargetIdentity)
            val orderedTargets = targetsById.entries.toList()
            val batch = targetTrashBatchStore.prepare(
                pluginUid = expectedUid,
                parent = parent,
                sources = orderedTargets.map { (targetId, target) ->
                    ExplorerActionTargetTrashBatchSource(
                        targetId = targetId,
                        file = target.file,
                        identity = target.identity,
                    )
                },
                repository = repository,
            )
            targetTrashRunning = true
            TargetTrashPlan(
                targetIds = orderedTargets.map { entry -> entry.key },
                targets = orderedTargets.map { entry -> entry.value },
                batch = batch,
            )
        }

        try {
            val trashItemIds = ArrayList<String>(plan.targets.size)
            val movedIndices = HashSet<Int>()
            val recoveryIndices = HashSet<Int>()
            val failedIndices = HashSet<Int>()
            val operationController = DialogUtils.OperationController()
            plan.targets.forEachIndexed { index, target ->
                val historyItem = plan.batch.items[index]
                try {
                    targetTrashBatchStore.updateItem(
                        plan.batch.id,
                        index,
                        ExplorerActionTargetTrashItemState.MOVING,
                    )
                    val item = repository.moveToTrashWithProgress(
                        path = target.file.path,
                        progressCallback = null,
                        fileCallback = null,
                        operationController = operationController,
                        beforeSourceDelete = { requireTargetIdentity(target) },
                        trashId = historyItem.trashId,
                    )
                    check(item.trashId == historyItem.trashId) {
                        "Explorer Action target Trash item ID changed"
                    }
                    trashItemIds += item.trashId
                    movedIndices += index
                    targetTrashBatchStore.updateItem(
                        plan.batch.id,
                        index,
                        ExplorerActionTargetTrashItemState.MOVED,
                    )
                } catch (error: TrashMoveRecoveryRequiredException) {
                    trashItemIds += error.trashItem.trashId
                    recoveryIndices += index
                    if (!target.file.exists()) movedIndices += index
                    runCatching {
                        targetTrashBatchStore.updateItem(
                            plan.batch.id,
                            index,
                            ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED,
                        )
                    }
                } catch (_: Exception) {
                    val durableTrashItem = repository.findTrashItem(historyItem.trashId)
                    if (durableTrashItem != null) {
                        if (durableTrashItem.trashId !in trashItemIds) {
                            trashItemIds += durableTrashItem.trashId
                        }
                        recoveryIndices += index
                        if (!target.file.exists()) movedIndices += index
                        runCatching {
                            targetTrashBatchStore.updateItem(
                                plan.batch.id,
                                index,
                                ExplorerActionTargetTrashItemState.RECOVERY_REQUIRED,
                            )
                        }
                    } else {
                        failedIndices += index
                        runCatching {
                            targetTrashBatchStore.updateItem(
                                plan.batch.id,
                                index,
                                ExplorerActionTargetTrashItemState.NOT_MOVED,
                            )
                        }
                    }
                }
            }
            val movedCount = movedIndices.size
            val recoveryCount = recoveryIndices.size
            val state = when {
                recoveryCount == 0 && failedIndices.isEmpty() && movedCount == plan.targets.size ->
                    ExplorerActionHostSessionValues.TARGET_TRASH_STATE_COMMITTED
                trashItemIds.isNotEmpty() || movedCount > 0 ->
                    ExplorerActionHostSessionValues.TARGET_TRASH_STATE_RECOVERY_REQUIRED
                else -> ExplorerActionHostSessionValues.TARGET_TRASH_STATE_FAILED
            }
            val result = targetTrashBundle(
                state = state,
                trashItemIds = trashItemIds,
                movedCount = movedCount,
                recoveryCount = recoveryCount,
                batchId = plan.batch.id,
            )
            synchronized(lock) {
                targetTrashResult = Bundle(result)
                targetTrashRequest = plan.targetIds
                (movedIndices + recoveryIndices).forEach { index -> trashedTargetIds += plan.targetIds[index] }
            }
            if (movedCount > 0 || recoveryCount > 0) runCatching(onSourcesTrashed)
            return result
        } finally {
            targetTrashBatchStore.finish(plan.batch.id)
            synchronized(lock) {
                targetTrashRunning = false
            }
        }
    }

    override fun queryTargetTrash(): Bundle {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            targetTrashResult?.let(::Bundle) ?: targetTrashBundle(
                state = ExplorerActionHostSessionValues.TARGET_TRASH_STATE_UNKNOWN,
                trashItemIds = emptyList(),
                movedCount = 0,
                recoveryCount = 0,
                batchId = null,
            )
        }
    }

    override fun openRelatedFile(targetId: String, suffix: String): ParcelFileDescriptor? {
        enforceCaller()
        return inputAccess.openRelatedFile(targetId, suffix)
    }

    // v25 target rename (docs/dev/explorer-action-protocol-v25.md). The transactions and their access
    // rule exist now; the executor and the undo journal they delegate to arrive with Batch Rename
    // roadmap P3.1, and until then every call fails instead of guessing.
    // zh-CN: v25 目标重命名. 事务与访问规则已就位; 其委托的执行器与撤销日志随 Batch Rename 路线图 P3.1 落地, 在此之前每次调用都直接失败.

    override fun listRenameParent(offset: Int, limit: Int): Bundle {
        enforceTargetRenameAccess()
        throw UnsupportedOperationException(TARGET_RENAME_UNAVAILABLE)
    }

    override fun renameEntries(request: Bundle): Bundle {
        enforceTargetRenameAccess()
        throw UnsupportedOperationException(TARGET_RENAME_UNAVAILABLE)
    }

    override fun queryRenameBatch(renameBatchId: String): Bundle {
        enforceTargetRenameAccess()
        throw UnsupportedOperationException(TARGET_RENAME_UNAVAILABLE)
    }

    override fun listRenameBatches(): Bundle {
        enforceTargetRenameAccess()
        throw UnsupportedOperationException(TARGET_RENAME_UNAVAILABLE)
    }

    override fun undoRenameBatch(renameBatchId: String): Bundle {
        enforceTargetRenameAccess()
        throw UnsupportedOperationException(TARGET_RENAME_UNAVAILABLE)
    }

    private fun enforceTargetRenameAccess() {
        enforceCaller()
        check(allowTargetRename) { "Explorer Action does not have target rename access" }
        synchronized(lock) { checkOpen() }
    }

    override fun queryTargetTrashBatch(targetTrashBatchId: String): Bundle {
        enforceCaller()
        check(selectedOutputDirectory == null) { "Extraction sessions cannot access source Trash history" }
        require(isCanonicalUuid(targetTrashBatchId)) {
            "Explorer Action target Trash batch ID is invalid"
        }
        check(allowCreateOutput || allowTargetTrash) {
            "Explorer Action does not have target Trash history access"
        }
        return synchronized(lock) {
            checkOpen()
            val repository = TrashRepository(applicationContext)
            val record = targetTrashBatchStore.query(
                pluginUid = expectedUid,
                parent = parent,
                batchId = targetTrashBatchId,
                repository = repository,
            )
            targetTrashBatchBundle(record, targetTrashBatchId)
        }
    }

    override fun listTargetTrashBatches(): Bundle {
        enforceCaller()
        check(selectedOutputDirectory == null) { "Extraction sessions cannot access source Trash history" }
        check(allowCreateOutput || allowTargetTrash) {
            "Explorer Action does not have target Trash history access"
        }
        return synchronized(lock) {
            checkOpen()
            val repository = TrashRepository(applicationContext)
            Bundle().apply {
                putParcelableArrayList(
                    ExplorerActionHostSessionKeys.TARGET_TRASH_BATCHES,
                    ArrayList(
                        targetTrashBatchStore.list(
                            pluginUid = expectedUid,
                            parent = parent,
                            repository = repository,
                        ).map { record -> targetTrashBatchBundle(record, record.id) },
                    ),
                )
            }
        }
    }

    override fun undoTargetTrashBatch(targetTrashBatchId: String): Bundle {
        enforceCaller()
        check(selectedOutputDirectory == null) { "Extraction sessions cannot restore source targets" }
        require(isCanonicalUuid(targetTrashBatchId)) {
            "Explorer Action target Trash batch ID is invalid"
        }
        check(allowCreateOutput || allowTargetTrash) {
            "Explorer Action does not have target Trash history access"
        }
        val before = synchronized(lock) {
            checkOpen()
            check(!targetTrashRunning) {
                "Explorer Action target Trash operation is already running"
            }
            val repository = TrashRepository(applicationContext)
            val record = requireNotNull(
                targetTrashBatchStore.query(
                    pluginUid = expectedUid,
                    parent = parent,
                    batchId = targetTrashBatchId,
                    repository = repository,
                ),
            ) { "Explorer Action target Trash batch is unknown" }
            targetTrashRunning = true
            record
        }
        return try {
            val repository = TrashRepository(applicationContext)
            val restored = targetTrashBatchStore.undo(
                pluginUid = expectedUid,
                parent = parent,
                batchId = targetTrashBatchId,
                repository = repository,
            )
            if (allowTargetTrash) adoptRestoredTargets(before, restored)
            if (restored.restoredCount > before.restoredCount) runCatching(onSourcesTrashed)
            targetTrashBatchBundle(restored, restored.id)
        } finally {
            synchronized(lock) { targetTrashRunning = false }
        }
    }

    override fun openOutput(transactionId: String): ParcelFileDescriptor {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val transaction = output(transactionId)
            check(transaction.mode != OutputMode.CREATE_TREE) {
                "Explorer Action directory output requires a child file path"
            }
            check(transaction.state == OutputState.PREPARED) {
                "Explorer Action output has already been opened"
            }
            val descriptor = ParcelFileDescriptor.open(
                transaction.temp,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
            )
            transaction.state = OutputState.OPENED
            descriptor
        }
    }

    override fun openPendingOutput(transactionId: String): ParcelFileDescriptor {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val transaction = output(transactionId)
            check(transaction.mode != OutputMode.CREATE_TREE) {
                "Explorer Action directory output is verified by the host during commit"
            }
            check(transaction.state == OutputState.OPENED) {
                "Explorer Action pending output is not ready for verification"
            }
            check(transaction.temp.isFile) { "Explorer Action staging output is missing" }
            val descriptor = ParcelFileDescriptor.open(
                transaction.temp,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            transaction.state = OutputState.VERIFYING
            descriptor
        }
    }

    override fun commitOutput(transactionId: String): Bundle {
        enforceCaller()
        val committed = synchronized(lock) {
            checkOpen()
            val transaction = output(transactionId)
            check(transaction.outputBatchId == null) {
                "Explorer Action output belongs to a batch"
            }
            val readyToCommit = when (transaction.mode) {
                OutputMode.CREATE ->
                    transaction.state == OutputState.OPENED ||
                        transaction.state == OutputState.VERIFYING
                OutputMode.REPLACE -> transaction.state == OutputState.VERIFYING
                OutputMode.CREATE_TREE ->
                    transaction.state == OutputState.PREPARED ||
                        transaction.state == OutputState.TREE_WRITING
            }
            check(readyToCommit) {
                "Explorer Action output is not ready to commit"
            }
            check(
                if (transaction.mode == OutputMode.CREATE_TREE) {
                    transaction.target.isDirectory
                } else {
                    transaction.temp.isFile
                },
            ) { "Explorer Action staging output is missing" }
            when (transaction.mode) {
                OutputMode.CREATE -> {
                    check(!transaction.target.exists()) { "Explorer Action output target now exists" }
                    if (!transaction.temp.renameTo(transaction.target)) {
                        throw IOException("Explorer Action output could not be committed atomically")
                    }
                }
                OutputMode.REPLACE ->
                    transaction.replacementHistory = commitReplacement(transaction)
                OutputMode.CREATE_TREE -> commitOutputTree(transaction)
            }
            outputs.remove(transaction.id)
            releaseOutputTarget(transaction.target)
            if (transaction.mode == OutputMode.CREATE_TREE) {
                markOutputTreeActive(transaction.id, false)
            }
            val result = outputBundle(transaction).apply {
                putInt(
                    ExplorerActionHostSessionKeys.OUTPUT_STATE,
                    ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
                )
                if (transaction.mode == OutputMode.CREATE_TREE) {
                    putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, transaction.target.lastModified())
                } else {
                    putLong(ExplorerActionHostSessionKeys.SIZE, transaction.target.length())
                    putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, transaction.target.lastModified())
                }
            }
            rememberTerminalOutput(transaction.id, result)
            if (transaction.mode == OutputMode.CREATE) {
                rememberCommittedCreateOutput(transaction)
            }
            CommittedOutput(
                target = transaction.target,
                mode = transaction.mode,
                result = result,
            )
        }
        runCatching {
            when (committed.mode) {
                OutputMode.CREATE,
                OutputMode.CREATE_TREE,
                -> onOutputCommitted(committed.target)
                OutputMode.REPLACE -> onTargetReplaced(committed.target)
            }
        }
        return committed.result
    }

    override fun abortOutput(transactionId: String) {
        enforceCaller()
        synchronized(lock) {
            checkOpen()
            val transaction = outputs[transactionId] ?: return
            check(transaction.outputBatchId == null) {
                "Explorer Action output batch must be aborted as a group"
            }
            cleanup(transaction)
            outputs.remove(transactionId)
            if (transaction.mode == OutputMode.CREATE_TREE) {
                markOutputTreeActive(transaction.id, false)
            }
            rememberTerminalOutput(
                transaction.id,
                outputBundle(transaction).apply {
                    putInt(
                        ExplorerActionHostSessionKeys.OUTPUT_STATE,
                        ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                    )
                },
            )
        }
    }

    override fun close() {
        enforceCaller()
        closeInternal()
    }

    /** Closes a locally retained session when its plugin call or archive connection terminates. */
    internal fun closeFromHost() {
        closeInternal()
    }

    private fun closeInternal(expectedClientToken: IBinder? = null) {
        val closeState = synchronized(lock) {
            if (expectedClientToken != null && clientLease?.token !== expectedClientToken) return
            if (closed) return
            closed = true
            val lease = clientLease
            clientLease = null
            CloseState(
                abandoned = outputs.values.toList().also { outputs.clear() },
                batches = outputBatches.values.filter { record ->
                    record.state != ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED &&
                        record.state != ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
                },
                clientLease = lease,
            )
        }
        closeState.clientLease?.let { lease ->
            runCatching { lease.token.unlinkToDeath(lease.deathRecipient, 0) }
        }
        closeState.batches.forEach { record ->
            runCatching { rollbackOutputBatch(record) }
            markOutputBatchActive(record.id, false)
        }
        closeState.abandoned.forEach { transaction ->
            try {
                runCatching { cleanup(transaction) }
            } finally {
                releaseOutputTarget(transaction.target)
                if (transaction.mode == OutputMode.CREATE_TREE) {
                    markOutputTreeActive(transaction.id, false)
                }
            }
        }
    }

    private fun targetState(targetId: String): ExplorerActionHostTargetState = synchronized(lock) {
        checkOpen()
        inputAccess.targetState(targetId)
    }

    private fun output(transactionId: String): OutputTransaction {
        require(isCanonicalUuid(transactionId)) { "Explorer Action output transaction ID is invalid" }
        return outputs[transactionId]
            ?: throw IllegalArgumentException("Explorer Action output transaction is unknown")
    }

    private fun outputBundle(transaction: OutputTransaction): Bundle = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID, transaction.id)
        putString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_NAME, transaction.target.name)
        putString(
            ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH,
            transaction.target.path.take(ExplorerActionProtocol.MAX_PARENT_DISPLAY_PATH_LENGTH),
        )
        putString(ExplorerActionHostSessionKeys.MIME_TYPE, transaction.mimeType)
        putInt(ExplorerActionHostSessionKeys.OUTPUT_KIND, transaction.mode.protocolKind)
        putInt(ExplorerActionHostSessionKeys.OUTPUT_STATE, transaction.state.protocolState)
        if (transaction.mode == OutputMode.CREATE_TREE) {
            val fileCount = transaction.outputTreeNodes.values.count { it == OutputTreeNodeKind.FILE }
            val directoryCount = transaction.outputTreeNodes.size - fileCount
            putInt(ExplorerActionHostSessionKeys.OUTPUT_FILE_COUNT, fileCount)
            putInt(ExplorerActionHostSessionKeys.OUTPUT_DIRECTORY_COUNT, directoryCount)
            putLong(
                ExplorerActionHostSessionKeys.OUTPUT_BYTES,
                transaction.outputTreeBytes.takeIf { it >= 0L } ?: 0L,
            )
        }
        transaction.replacementHistory?.let { history ->
            putAll(replacementHistoryBundle(history))
        }
    }

    private fun committedOutputBundle(transaction: OutputTransaction): Bundle =
        outputBundle(transaction).apply {
            putInt(
                ExplorerActionHostSessionKeys.OUTPUT_STATE,
                ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
            )
            putLong(ExplorerActionHostSessionKeys.SIZE, transaction.target.length())
            putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, transaction.target.lastModified())
        }

    private fun outputBatchBundle(record: ExplorerActionOutputBatchRecord): Bundle = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID, record.id)
        putInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE, record.state)
        putStringArrayList(
            ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_IDS,
            ArrayList(record.members.map(ExplorerActionOutputBatchMemberRecord::transactionId)),
        )
        putInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_PUBLISHED_COUNT, record.publishedCount)
    }

    private fun outputBatchTimestamp(record: ExplorerActionOutputBatchRecord): Long =
        clock().coerceAtLeast(record.updatedAtMillis)

    private fun unknownOutputBatchBundle(batchId: String): Bundle = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.OUTPUT_BATCH_ID, batchId)
        putInt(
            ExplorerActionHostSessionKeys.OUTPUT_BATCH_STATE,
            ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_UNKNOWN,
        )
        putStringArrayList(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_IDS, arrayListOf())
        putInt(ExplorerActionHostSessionKeys.OUTPUT_BATCH_PUBLISHED_COUNT, 0)
    }

    private fun activeOutputBatch(batchId: String): ExplorerActionOutputBatchRecord {
        require(isCanonicalUuid(batchId)) { "Explorer Action output batch ID is invalid" }
        return outputBatches[batchId]
            ?: throw IllegalArgumentException("Explorer Action output batch is unknown")
    }

    private fun abortableOutputBatch(batchId: String): ExplorerActionOutputBatchRecord {
        require(isCanonicalUuid(batchId)) { "Explorer Action output batch ID is invalid" }
        return outputBatches[batchId]
            ?: outputBatchStore.read(batchId)?.takeIf(::belongsToThisSessionParent)
            ?: throw IllegalArgumentException("Explorer Action output batch is unknown")
    }

    private fun belongsToThisSessionParent(record: ExplorerActionOutputBatchRecord): Boolean {
        val identity = runCatching { TargetIdentity.capture(parent) }.getOrNull() ?: return false
        return record.pluginUid == expectedUid &&
            record.parentPath == parent.path &&
            record.parentDevice == identity.device &&
            record.parentInode == identity.inode
    }

    private fun requireBatchMemberIdentity(
        transaction: OutputTransaction,
        member: ExplorerActionOutputBatchMemberRecord,
    ) {
        check(transaction.id == member.transactionId)
        check(transaction.target.path == member.targetPath && transaction.temp.path == member.stagingPath) {
            "Explorer Action output batch paths changed"
        }
        check(transaction.target.parentFile == parent && transaction.temp.parentFile == parent) {
            "Explorer Action output batch member left its parent"
        }
        check(batchMemberIdentityMatches(transaction.temp, member)) {
            "Explorer Action output batch staging file changed"
        }
    }

    private fun batchMemberIdentityMatches(
        file: File,
        member: ExplorerActionOutputBatchMemberRecord,
    ): Boolean = runCatching {
        val identity = TargetIdentity.captureRegularFile(file)
        identity.device == member.stagingDevice &&
            identity.inode == member.stagingInode &&
            identity.size == member.stagingSize &&
            identity.lastModifiedMillis == member.stagingLastModifiedMillis
    }.getOrDefault(false)

    private fun rollbackOutputBatch(
        record: ExplorerActionOutputBatchRecord,
    ): OutputBatchRollback {
        val failures = mutableListOf<Throwable>()
        val parentMatches = runCatching {
            val identity = TargetIdentity.capture(parent)
            record.pluginUid == expectedUid &&
                record.parentPath == parent.path &&
                record.parentDevice == identity.device &&
                record.parentInode == identity.inode
        }.getOrDefault(false)
        if (!parentMatches) {
            failures += IOException("Explorer Action output batch parent identity changed")
        } else {
            record.members.asReversed().forEach { member ->
                listOf(File(member.targetPath), File(member.stagingPath)).forEach { file ->
                    val failure = runCatching {
                        val canonical = file.canonicalFile
                        check(canonical == file && canonical.parentFile == parent) {
                            "Explorer Action output batch rollback path is invalid"
                        }
                        val stat = try {
                            Os.lstat(file.path)
                        } catch (error: ErrnoException) {
                            if (error.errno == OsConstants.ENOENT) return@runCatching
                            throw IOException(
                                "Explorer Action output batch rollback path is unavailable",
                                error,
                            )
                        }
                        check(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                            "Explorer Action output batch rollback path is not a regular file"
                        }
                        check(
                            stat.st_dev == member.stagingDevice &&
                                stat.st_ino == member.stagingInode &&
                                stat.st_size == member.stagingSize &&
                                file.lastModified() == member.stagingLastModifiedMillis,
                        ) {
                            "Explorer Action output batch rollback identity changed"
                        }
                        if (!file.delete()) {
                            throw IOException("Explorer Action output batch member could not be removed")
                        }
                    }.exceptionOrNull()
                    if (failure != null) failures += failure
                }
            }
            runCatching { syncDirectory(parent) }.exceptionOrNull()?.let(failures::add)
        }

        var rolledBack = record.copy(
            state = if (failures.isEmpty()) {
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
            } else {
                ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED
            },
            updatedAtMillis = outputBatchTimestamp(record),
        )
        val journalFailure = runCatching { outputBatchStore.write(rolledBack) }.exceptionOrNull()
        if (journalFailure != null) {
            failures += journalFailure
            rolledBack = rolledBack.copy(
                state = ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED,
            )
        }
        return OutputBatchRollback(rolledBack, failures)
    }

    private fun releaseOutputBatchTransactions(record: ExplorerActionOutputBatchRecord) {
        record.members.forEach { member ->
            val transaction = outputs.remove(member.transactionId) ?: return@forEach
            releaseOutputTarget(transaction.target)
            rememberTerminalOutput(
                transaction.id,
                outputBundle(transaction).apply {
                    putInt(
                        ExplorerActionHostSessionKeys.OUTPUT_STATE,
                        ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                    )
                },
            )
        }
    }

    private fun outputTreeRecordBundle(record: ExplorerActionOutputTreeRecord): Bundle = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.OUTPUT_TRANSACTION_ID, record.id)
        putString(ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_NAME, record.displayName)
        putString(
            ExplorerActionHostSessionKeys.OUTPUT_DISPLAY_PATH,
            record.targetPath.take(ExplorerActionProtocol.MAX_PARENT_DISPLAY_PATH_LENGTH),
        )
        putString(ExplorerActionHostSessionKeys.MIME_TYPE, DIRECTORY_MIME_TYPE)
        putInt(
            ExplorerActionHostSessionKeys.OUTPUT_KIND,
            ExplorerActionHostSessionValues.OUTPUT_KIND_DIRECTORY_TREE,
        )
        putInt(ExplorerActionHostSessionKeys.OUTPUT_STATE, record.state)
        putInt(ExplorerActionHostSessionKeys.OUTPUT_FILE_COUNT, record.fileCount)
        putInt(ExplorerActionHostSessionKeys.OUTPUT_DIRECTORY_COUNT, record.directoryCount)
        putLong(ExplorerActionHostSessionKeys.OUTPUT_BYTES, record.outputBytes)
    }

    private fun belongsToThisSessionParent(record: ExplorerActionOutputTreeRecord): Boolean {
        val identity = runCatching { TargetIdentity.capture(parent) }.getOrNull() ?: return false
        return record.pluginUid == expectedUid &&
            record.parentPath == parent.path &&
            record.parentDevice == identity.device &&
            record.parentInode == identity.inode
    }

    private fun commitReplacement(
        transaction: OutputTransaction,
    ): ExplorerActionReplacementHistoryRecord? {
        val targetState = requireNotNull(transaction.replacementTarget) {
            "Explorer Action replacement target state is missing"
        }
        val expectedIdentity = requireNotNull(transaction.expectedTargetIdentity) {
            "Explorer Action replacement target identity is missing"
        }
        FileOutputStream(transaction.temp, true).use { output -> output.fd.sync() }
        check(TargetIdentity.captureRegularFile(transaction.target) == expectedIdentity) {
            "Explorer Action replacement target changed before commit"
        }
        val committedIdentity = TargetIdentity.captureRegularFile(transaction.temp)
        val history = if (allowReplacementUndo) {
            replacementHistoryStore.prepare(
                transactionId = transaction.id,
                pluginUid = expectedUid,
                parent = parent,
                target = transaction.target,
                previousIdentity = expectedIdentity,
                replacementIdentity = committedIdentity,
            )
        } else {
            null
        }
        try {
            Os.rename(transaction.temp.path, transaction.target.path)
        } catch (error: ErrnoException) {
            history?.let { record ->
                runCatching { replacementHistoryStore.discardPrepared(record.id) }
            }
            throw IOException("Explorer Action target could not be replaced atomically", error)
        }
        targetState.identity = committedIdentity
        runCatching { syncDirectory(parent) }
        afterReplacementPublished()
        return history?.let { record ->
            runCatching { replacementHistoryStore.markAvailable(record.id) }.getOrElse { record }
        }
    }

    private fun commitOutputTree(transaction: OutputTransaction) {
        val marker = requireNotNull(transaction.outputTreeMarker) {
            "Explorer Action output tree marker is missing"
        }
        val record = requireNotNull(transaction.outputTreeRecord) {
            "Explorer Action output tree journal is missing"
        }
        check(outputTreeIdentityMatches(record, transaction.target)) {
            "Explorer Action output tree root changed before commit"
        }
        check(marker.isFile && marker.readText(Charsets.UTF_8) == transaction.id) {
            "Explorer Action output tree marker changed before commit"
        }
        val stats = validateAndSyncOutputTree(transaction)
        val verifyingRecord = record.copy(
            state = ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING,
            updatedAtMillis = clock(),
            fileCount = stats.fileCount,
            directoryCount = stats.directoryCount,
            outputBytes = stats.bytes,
        )
        outputTreeStore.write(verifyingRecord)
        transaction.outputTreeRecord = verifyingRecord
        transaction.outputTreeBytes = stats.bytes
        transaction.state = OutputState.VERIFYING
        check(marker.delete()) { "Explorer Action output tree marker could not be removed" }
        runCatching { syncDirectory(transaction.target) }
        runCatching { syncDirectory(parent) }
        val committedRecord = verifyingRecord.copy(
            state = ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED,
            updatedAtMillis = clock(),
        )
        transaction.outputTreeRecord = committedRecord
        runCatching { outputTreeStore.write(committedRecord) }
    }

    private fun validateAndSyncOutputTree(transaction: OutputTransaction): OutputTreeStats {
        val marker = requireNotNull(transaction.outputTreeMarker)
        val expected = transaction.outputTreeNodes
        val discovered = LinkedHashMap<String, OutputTreeNodeKind>()
        var bytes = 0L
        val pending = ArrayDeque<OutputTreeTraversal>()
        pending.addLast(OutputTreeTraversal(transaction.target, ""))
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (current.afterChildren) {
                syncDirectory(current.file)
                continue
            }

            val isRoot = current.relativePath.isEmpty()
            if (!isRoot) {
                normalizeOutputRelativePath(current.relativePath)
                val stat = try {
                    Os.lstat(current.file.path)
                } catch (error: ErrnoException) {
                    throw IOException("Explorer Action output tree entry is unavailable", error)
                }
                check(!OsConstants.S_ISLNK(stat.st_mode)) {
                    "Explorer Action output tree contains a symbolic link"
                }
                val kind = if (OsConstants.S_ISDIR(stat.st_mode)) {
                    OutputTreeNodeKind.DIRECTORY
                } else {
                    check(OsConstants.S_ISREG(stat.st_mode)) {
                        "Explorer Action output tree contains an unsupported node"
                    }
                    OutputTreeNodeKind.FILE
                }
                check(discovered.put(current.relativePath, kind) == null) {
                    "Explorer Action output tree contains a duplicate path"
                }
                if (kind == OutputTreeNodeKind.FILE) {
                    FileOutputStream(current.file, true).use { output -> output.fd.sync() }
                    bytes = Math.addExact(bytes, current.file.length())
                    continue
                }
                pending.addLast(current.copy(afterChildren = true))
            }

            val children = current.file.listFiles()
                ?: throw IOException("Explorer Action output tree cannot be inspected")
            children.sortedByDescending(File::getName).forEach { child ->
                if (child == marker && isRoot) return@forEach
                val relativePath = if (isRoot) {
                    child.name
                } else {
                    "${current.relativePath}/${child.name}"
                }
                pending.addLast(OutputTreeTraversal(child, relativePath))
            }
        }
        check(discovered == expected) {
            "Explorer Action output tree changed outside the negotiated session"
        }
        return OutputTreeStats(
            fileCount = discovered.values.count { it == OutputTreeNodeKind.FILE },
            directoryCount = discovered.values.count { it == OutputTreeNodeKind.DIRECTORY },
            bytes = bytes,
        )
    }

    private fun syncDirectory(directory: File) {
        var descriptor: java.io.FileDescriptor? = null
        try {
            descriptor = Os.open(
                directory.path,
                OsConstants.O_RDONLY,
                0,
            )
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.EINVAL && error.errno != OsConstants.EOPNOTSUPP) {
                throw IOException("Explorer Action output directory could not be synchronized", error)
            }
        } finally {
            descriptor?.let { runCatching { Os.close(it) } }
        }
    }

    private fun outputTree(transactionId: String): OutputTransaction = output(transactionId).also {
        check(it.mode == OutputMode.CREATE_TREE) {
            "Explorer Action output transaction is not a directory tree"
        }
        check(it.state == OutputState.PREPARED || it.state == OutputState.TREE_WRITING) {
            "Explorer Action output tree is not writable"
        }
    }

    private fun requireOutputTreeCapacity(transaction: OutputTransaction, relativePath: String) {
        check(relativePath !in transaction.outputTreeNodes) {
            "Explorer Action output tree path already exists"
        }
        check(transaction.outputTreeNodes.size < ExplorerActionProtocol.MAX_OUTPUT_TREE_ENTRIES) {
            "Explorer Action output tree exceeds the negotiated entry limit"
        }
        val parentPath = relativePath.substringBeforeLast('/', "")
        val collisionKey = outputTreeCollisionKey(relativePath.substringAfterLast('/'))
        check(
            transaction.outputTreeNodes.none { (path, _) ->
                path.substringBeforeLast('/', "") == parentPath &&
                    outputTreeCollisionKey(path.substringAfterLast('/')) == collisionKey
            },
        ) { "Explorer Action output tree contains an equivalent sibling name" }
    }

    private fun requireOutputTreeParent(transaction: OutputTransaction, relativePath: String) {
        val parentPath = relativePath.substringBeforeLast('/', "")
        if (parentPath.isEmpty()) return
        check(transaction.outputTreeNodes[parentPath] == OutputTreeNodeKind.DIRECTORY) {
            "Explorer Action output tree parent directory does not exist"
        }
    }

    private fun resolveOutputTreePath(transaction: OutputTransaction, relativePath: String): File {
        val unresolved = File(
            transaction.target,
            relativePath.replace('/', File.separatorChar),
        ).absoluteFile
        val parentPath = unresolved.parentFile?.canonicalFile
            ?: throw IllegalArgumentException("Explorer Action output tree path has no parent")
        val resolved = File(parentPath, unresolved.name)
        require(isWithinRoot(transaction.target, resolved) && resolved != transaction.target) {
            "Explorer Action output tree path escapes its root"
        }
        return resolved
    }

    private fun markOutputTreeWriting(transaction: OutputTransaction) {
        if (transaction.state == OutputState.TREE_WRITING) return
        transaction.state = OutputState.TREE_WRITING
        val record = requireNotNull(transaction.outputTreeRecord)
        val updated = record.copy(
            state = ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING,
            updatedAtMillis = clock(),
        )
        outputTreeStore.write(updated)
        transaction.outputTreeRecord = updated
    }

    private fun reserveOutputTarget(displayName: String, conflictPolicy: Int): File {
        return ExplorerActionOutputTargets.reserveFile(parent, displayName, conflictPolicy)
    }

    private fun reserveOutputTreeTarget(displayName: String, conflictPolicy: Int): File {
        return ExplorerActionOutputTargets.reserveDirectory(parent, displayName, conflictPolicy)
    }

    private fun reserveReplacementTarget(target: File) {
        ExplorerActionOutputTargets.reserveReplacement(parent, target)
    }
    private fun cleanup(transaction: OutputTransaction) {
        if (transaction.mode == OutputMode.CREATE_TREE) {
            val record = requireNotNull(transaction.outputTreeRecord)
            check(outputTreeIdentityMatches(record, transaction.target)) {
                "Explorer Action output tree changed before cleanup"
            }
            val deleted = safeDeleteTree(transaction.target)
            if (!deleted) {
                throw IOException("Explorer Action output tree could not be removed")
            }
            val aborted = record.copy(
                state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                updatedAtMillis = clock(),
                fileCount = transaction.outputTreeNodes.values.count {
                    it == OutputTreeNodeKind.FILE
                },
                directoryCount = transaction.outputTreeNodes.values.count {
                    it == OutputTreeNodeKind.DIRECTORY
                },
                outputBytes = transaction.outputTreeBytes.coerceAtLeast(0L),
            )
            transaction.outputTreeRecord = aborted
            runCatching { outputTreeStore.write(aborted) }
        } else {
            transaction.temp.delete()
        }
        releaseOutputTarget(transaction.target)
    }

    private fun releaseOutputTarget(target: File) {
        ExplorerActionOutputTargets.release(target)
    }

    private fun normalizeOutputName(value: String): String {
        return ExplorerActionOutputPathPolicy.normalizeName(value)
    }

    private fun normalizeOutputRelativePath(value: String): String {
        return ExplorerActionOutputPathPolicy.normalizeRelativePath(value)
    }

    private fun outputTreeCollisionKey(value: String): String {
        return ExplorerActionOutputPathPolicy.collisionKey(value)
    }
    private fun rememberTerminalOutput(transactionId: String, bundle: Bundle) {
        terminalOutputs[transactionId] = Bundle(bundle)
        while (terminalOutputs.size > ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS) {
            terminalOutputs.remove(terminalOutputs.keys.first())
        }
    }

    private fun rememberCommittedCreateOutput(transaction: OutputTransaction) {
        check(transaction.mode == OutputMode.CREATE)
        committedCreateOutputs[transaction.id] = CommittedCreateOutput(
            transaction.target,
            TargetIdentity.captureRegularFile(transaction.target),
        )
        while (committedCreateOutputs.size > ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS) {
            committedCreateOutputs.remove(committedCreateOutputs.keys.first())
        }
    }

    private fun requireCommittedCreateOutputIdentity(output: CommittedCreateOutput) {
        check(TargetIdentity.captureRegularFile(output.file) == output.identity) {
            "Explorer Action committed output changed before source removal"
        }
    }

    /**
     * v24: validates one deletion of a `targetTrash` action and reserves its batch. Returns null
     * when the request repeats the previous call and that call moved one of its targets, so the
     * stored result answers a client whose response was lost. A failed or since restored target
     * can be requested again. Must be called while holding [lock].
     */
    private fun prepareSelectedTargetTrash(
        requestedTargetIds: List<String>,
        requestedOutputIds: List<String>,
        repository: TrashRepository,
    ): TargetTrashPlan? {
        if (
            targetTrashResult != null &&
            requestedTargetIds == targetTrashRequest &&
            requestedTargetIds.any(trashedTargetIds::contains)
        ) {
            return null
        }
        check(!targetTrashRunning) { "Explorer Action target Trash operation is already running" }
        require(requestedOutputIds.isEmpty()) {
            "Explorer Action read-only target Trash does not take output proof"
        }
        val requestOrder = targetsById.keys.toList()
        val positions = requestedTargetIds.map(requestOrder::indexOf)
        require(
            requestedTargetIds.isNotEmpty() &&
                positions.none { position -> position < 0 } &&
                positions.zipWithNext().all { (previous, next) -> previous < next },
        ) {
            "Explorer Action target Trash must name distinct request targets in request order"
        }
        check(requestedTargetIds.none(trashedTargetIds::contains)) {
            "Explorer Action target was already moved to Trash"
        }
        val selected = requestedTargetIds.map { targetId -> targetId to targetsById.getValue(targetId) }
        selected.forEach { (_, target) -> requireTargetIdentity(target) }
        val batch = targetTrashBatchStore.prepare(
            pluginUid = expectedUid,
            parent = parent,
            sources = selected.map { (targetId, target) ->
                ExplorerActionTargetTrashBatchSource(
                    targetId = targetId,
                    file = target.file,
                    identity = target.identity,
                )
            },
            repository = repository,
            kind = ExplorerActionTargetTrashBatchKind.SELECTED_TARGETS,
        )
        targetTrashRunning = true
        return TargetTrashPlan(
            targetIds = requestedTargetIds,
            targets = selected.map { (_, target) -> target },
            batch = batch,
        )
    }

    /**
     * v24: a target the host restored to its own original path becomes deletable again in this
     * session, under the identity of the restored file.
     */
    private fun adoptRestoredTargets(
        before: ExplorerActionTargetTrashBatchRecord,
        after: ExplorerActionTargetTrashBatchRecord,
    ) = synchronized(lock) {
        after.items.forEachIndexed { index, item ->
            if (
                item.state != ExplorerActionTargetTrashItemState.RESTORED ||
                before.items.getOrNull(index)?.state == ExplorerActionTargetTrashItemState.RESTORED
            ) {
                return@forEachIndexed
            }
            val target = targetsById[item.targetId] ?: return@forEachIndexed
            if (target.file.path != item.originalPath) return@forEachIndexed
            runCatching { ExplorerActionFileIdentity.capture(target.file) }.onSuccess { identity ->
                target.identity = identity
                trashedTargetIds -= item.targetId
            }
        }
    }

    private fun requireTargetIdentity(target: TargetState) {
        inputAccess.requireCurrentIdentity(
            target,
            "Explorer Action source changed before it could be moved to Trash",
        )
    }

    private fun targetTrashBundle(
        state: Int,
        trashItemIds: List<String>,
        movedCount: Int,
        recoveryCount: Int,
        batchId: String?,
    ): Bundle = Bundle().apply {
        putInt(ExplorerActionHostSessionKeys.TARGET_TRASH_STATE, state)
        batchId?.let {
            putString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID, it)
        }
        putStringArrayList(
            ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_IDS,
            ArrayList(trashItemIds),
        )
        putInt(ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT, movedCount)
        putInt(ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT, recoveryCount)
    }

    private fun targetTrashBatchBundle(
        record: ExplorerActionTargetTrashBatchRecord?,
        requestedId: String,
    ): Bundle = Bundle().apply {
        putString(ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ID, requestedId)
        putInt(
            ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_STATE,
            record?.protocolState
                ?: ExplorerActionHostSessionValues.TARGET_TRASH_BATCH_STATE_UNKNOWN,
        )
        putInt(
            ExplorerActionHostSessionKeys.TARGET_TRASH_MOVED_COUNT,
            record?.movedCount ?: 0,
        )
        putInt(
            ExplorerActionHostSessionKeys.TARGET_TRASH_RECOVERY_COUNT,
            record?.recoveryCount ?: 0,
        )
        putInt(
            ExplorerActionHostSessionKeys.TARGET_TRASH_RESTORED_COUNT,
            record?.restoredCount ?: 0,
        )
        putParcelableArrayList(
            ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_ITEMS,
            ArrayList(
                record?.items.orEmpty().map { item ->
                    Bundle().apply {
                        putString(
                            ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_DISPLAY_NAME,
                            item.displayName,
                        )
                        putInt(
                            ExplorerActionHostSessionKeys.TARGET_TRASH_ITEM_STATE,
                            item.protocolState,
                        )
                    }
                },
            ),
        )
        record?.let { history ->
            putLong(
                ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_CREATED_AT,
                history.createdAtMillis,
            )
            putLong(
                ExplorerActionHostSessionKeys.TARGET_TRASH_BATCH_UPDATED_AT,
                history.updatedAtMillis,
            )
        }
    }

    private fun replacementHistoryBundle(
        record: ExplorerActionReplacementHistoryRecord?,
    ): Bundle = Bundle().apply {
        putInt(
            ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_UNDO_STATE,
            record?.protocolState
                ?: ExplorerActionHostSessionValues.TARGET_REPLACEMENT_UNDO_STATE_NONE,
        )
        record?.let { history ->
            putString(
                ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_HISTORY_ID,
                history.id,
            )
            putLong(
                ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_PREVIOUS_SIZE,
                history.previousIdentity.size,
            )
            putLong(
                ExplorerActionHostSessionKeys.TARGET_REPLACEMENT_CREATED_AT,
                history.createdAtMillis,
            )
        }
    }

    private fun isWithinRoot(root: File, candidate: File): Boolean {
        return ExplorerActionInputPathPolicy.isWithinRoot(root, candidate)
    }

    private fun checkOpen() {
        check(!closed) { "Explorer Action host session is closed" }
    }

    private fun enforceCaller() {
        if (Binder.getCallingUid() != expectedUid) {
            throw SecurityException("Explorer Action host session caller UID does not match the launched plugin")
        }
    }

    private data class OutputTransaction(
        val id: String,
        val mimeType: String,
        val target: File,
        val temp: File,
        val mode: OutputMode,
        val replacementTarget: TargetState? = null,
        val expectedTargetIdentity: TargetIdentity? = null,
        val outputTreeMarker: File? = null,
        var outputTreeRecord: ExplorerActionOutputTreeRecord? = null,
        val outputTreeNodes: LinkedHashMap<String, OutputTreeNodeKind> = LinkedHashMap(),
        var outputTreeBytes: Long = -1L,
        var state: OutputState = OutputState.PREPARED,
        var outputBatchId: String? = null,
        var replacementHistory: ExplorerActionReplacementHistoryRecord? = null,
    )

    private data class ClientLease(
        val token: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private data class CloseState(
        val abandoned: List<OutputTransaction>,
        val batches: List<ExplorerActionOutputBatchRecord>,
        val clientLease: ClientLease?,
    )

    private data class CommittedOutputBatch(
        val targets: List<File>,
        val result: Bundle,
    )

    private data class OutputBatchRollback(
        val record: ExplorerActionOutputBatchRecord,
        val failures: List<Throwable>,
    )

    private data class CommittedOutput(
        val target: File,
        val mode: OutputMode,
        val result: Bundle,
    )

    private data class RestoredReplacement(
        val target: File,
        val result: Bundle,
    )

    private data class CommittedCreateOutput(
        val file: File,
        val identity: TargetIdentity,
    )

    private data class TargetTrashPlan(
        val targetIds: List<String>,
        val targets: List<TargetState>,
        val batch: ExplorerActionTargetTrashBatchRecord,
    )

    private enum class OutputMode {
        CREATE,
        REPLACE,
        CREATE_TREE,
        ;

        val protocolKind: Int
            get() = when (this) {
                CREATE -> ExplorerActionHostSessionValues.OUTPUT_KIND_FILE
                REPLACE -> ExplorerActionHostSessionValues.OUTPUT_KIND_TARGET_REPLACEMENT
                CREATE_TREE -> ExplorerActionHostSessionValues.OUTPUT_KIND_DIRECTORY_TREE
            }
    }

    private enum class OutputState {
        PREPARED,
        OPENED,
        VERIFYING,
        TREE_WRITING,
        ;

        val protocolState: Int
            get() = when (this) {
                PREPARED -> ExplorerActionHostSessionValues.OUTPUT_STATE_PREPARED
                OPENED,
                TREE_WRITING,
                -> ExplorerActionHostSessionValues.OUTPUT_STATE_WRITING
                VERIFYING -> ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING
            }
    }

    private enum class OutputTreeNodeKind {
        FILE,
        DIRECTORY,
    }

    private data class OutputTreeStats(
        val fileCount: Int,
        val directoryCount: Int,
        val bytes: Long,
    )

    private data class OutputTreeTraversal(
        val file: File,
        val relativePath: String,
        val afterChildren: Boolean = false,
    )

    companion object {
        private const val DIRECTORY_MIME_TYPE = "inode/directory"
        private const val TARGET_RENAME_UNAVAILABLE =
            "Explorer Action target rename is not available in this AutoJs6 build yet"
        private const val OUTPUT_TREE_MARKER_PREFIX = ExplorerActionOutputPathPolicy.OUTPUT_TREE_MARKER_PREFIX
        private val PROCESS_INSTANCE_ID = UUID.randomUUID().toString()
        private val CLIENT_DEATH_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ExplorerActionClientDeath").apply { isDaemon = true }
        }

        fun create(
            context: Context,
            pluginPackageName: String,
            commonParent: File,
            targets: List<ExplorerActionHostSessionTarget>,
            allowCreateOutput: Boolean,
            selectedOutputDirectory: File? = null,
            allowReplaceTarget: Boolean = false,
            allowReplacementUndo: Boolean = false,
            allowTargetTrash: Boolean = false,
            allowTargetRename: Boolean = false,
            allowReadSiblings: Boolean = false,
            allowPlaybackProgress: Boolean = false,
            relatedFileSuffixes: Set<String> = emptySet(),
            onOutputCommitted: (File) -> Unit = ::notifyCreated,
            onTargetReplaced: (File) -> Unit = ::notifyChanged,
            onSourcesTrashed: () -> Unit = ::notifyWorkspaceChanged,
            clock: () -> Long = System::currentTimeMillis,
            afterBatchMemberPublished: (Int) -> Unit = {},
            afterReplacementPublished: () -> Unit = {},
            afterReplacementRestored: () -> Unit = {},
        ): ExplorerActionHostSession {
            require(
                !allowReplacementUndo ||
                    (allowReplaceTarget && targets.size == 1),
            ) {
                "Explorer Action replacement history requires one replaceable target"
            }
            require(
                !allowTargetTrash ||
                    (!allowCreateOutput && !allowReplaceTarget && selectedOutputDirectory == null),
            ) {
                "Explorer Action target Trash is available only to read-only sessions"
            }
            require(
                !allowTargetRename ||
                    (!allowCreateOutput && !allowReplaceTarget && !allowTargetTrash && selectedOutputDirectory == null),
            ) {
                "Explorer Action target rename is available only to read-only sessions without Trash access"
            }
            val canonicalParent = commonParent.canonicalFile
            val outputDirectory = selectedOutputDirectory?.canonicalFile
            require(outputDirectory == null || (
                allowCreateOutput && !allowReplaceTarget && !allowReadSiblings &&
                    !allowPlaybackProgress && relatedFileSuffixes.isEmpty() &&
                    outputDirectory.isDirectory && outputDirectory.canWrite()
                )) { "Selected extraction destination is unavailable or incompatible with this session" }
            val applicationContext = context.applicationContext
            val store = ExplorerActionOutputTreeStore(applicationContext)
            ExplorerActionOutputRecovery.recoverOutputTrees(store, clock())
            val batchStore = ExplorerActionOutputBatchStore(applicationContext)
            ExplorerActionOutputRecovery.recoverOutputBatches(batchStore, clock())
            val targetTrashBatchStore = ExplorerActionTargetTrashBatchStore(
                applicationContext,
                clock,
            )
            if (allowCreateOutput || allowTargetTrash) {
                targetTrashBatchStore.recoverForNewSession(TrashRepository(applicationContext))
            }
            val replacementHistoryStore = ExplorerActionReplacementHistoryStore(
                applicationContext,
                clock,
                afterReplacementRestored,
            )
            if (allowReplacementUndo) replacementHistoryStore.recover()
            val pluginUid = applicationUid(context.packageManager, pluginPackageName)
            return ExplorerActionHostSession(
                applicationContext = applicationContext,
                expectedUid = pluginUid,
                commonParent = canonicalParent,
                selectedOutputDirectory = outputDirectory,
                targets = targets,
                allowCreateOutput = allowCreateOutput,
                allowReplaceTarget = allowReplaceTarget,
                allowReplacementUndo = allowReplacementUndo,
                allowTargetTrash = allowTargetTrash,
                allowTargetRename = allowTargetRename,
                allowReadSiblings = allowReadSiblings,
                allowPlaybackProgress = allowPlaybackProgress,
                relatedFileSuffixes = relatedFileSuffixes,
                onOutputCommitted = onOutputCommitted,
                onTargetReplaced = onTargetReplaced,
                onSourcesTrashed = onSourcesTrashed,
                outputTreeStore = store,
                outputBatchStore = batchStore,
                targetTrashBatchStore = targetTrashBatchStore,
                replacementHistoryStore = replacementHistoryStore,
                playbackHistoryStore = ExplorerPlaybackHistoryStore(context.applicationContext, clock),
                clock = clock,
                afterBatchMemberPublished = afterBatchMemberPublished,
                afterReplacementPublished = afterReplacementPublished,
            )
        }

        private fun markOutputTreeActive(transactionId: String, active: Boolean) {
            ExplorerActionOutputRecovery.markOutputTreeActive(transactionId, active)
        }

        private fun markOutputBatchActive(batchId: String, active: Boolean) {
            ExplorerActionOutputRecovery.markOutputBatchActive(batchId, active)
        }

        private fun outputTreeIdentityMatches(
            record: ExplorerActionOutputTreeRecord,
            target: File,
        ): Boolean = ExplorerActionOutputRecovery.outputTreeIdentityMatches(record, target)

        private fun safeDeleteTree(root: File): Boolean {
            return ExplorerActionOutputRecovery.safeDeleteTree(root)
        }
        private fun notifyCreated(file: File) {
            val notify = {
                Explorers.workspace().notifyItemCreated(explorerItemForCreatedOutput(file))
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                notify()
            } else {
                Handler(Looper.getMainLooper()).post(notify)
            }
        }

        private fun notifyChanged(file: File) {
            val notify = {
                val item = ExplorerFileItem(file, ExplorerDirPage(file.parentFile, null))
                Explorers.workspace().notifyItemChanged(item, item)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                notify()
            } else {
                Handler(Looper.getMainLooper()).post(notify)
            }
        }

        private fun notifyWorkspaceChanged() {
            val notify = { Explorers.workspace().refreshAll() }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                notify()
            } else {
                Handler(Looper.getMainLooper()).post(notify)
            }
        }

        private fun applicationUid(packageManager: PackageManager, packageName: String): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).uid
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0).uid
            }
        }

        private fun isCanonicalUuid(value: String): Boolean {
            val parsed = runCatching { UUID.fromString(value) }.getOrNull() ?: return false
            return parsed.toString().equals(value, ignoreCase = true)
        }
    }
}
