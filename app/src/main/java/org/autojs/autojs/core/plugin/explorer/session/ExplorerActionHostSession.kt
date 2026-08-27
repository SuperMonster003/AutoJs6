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
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionValues
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale
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
    private val expectedUid: Int,
    commonParent: File,
    targets: List<ExplorerActionHostSessionTarget>,
    private val allowCreateOutput: Boolean,
    private val allowReplaceTarget: Boolean,
    private val onOutputCommitted: (File) -> Unit,
    private val onTargetReplaced: (File) -> Unit,
    private val outputTreeStore: ExplorerActionOutputTreeStore,
    private val outputBatchStore: ExplorerActionOutputBatchStore,
    private val clock: () -> Long,
    private val afterBatchMemberPublished: (Int) -> Unit,
) : IExplorerActionHostSession.Stub() {

    private val parent = commonParent.canonicalFile
    private val targetsById = targets.associate { target ->
        require(target.id.isNotBlank() && target.id.length <= ExplorerActionProtocol.MAX_TARGET_ID_LENGTH) {
            "Explorer Action host session target ID is invalid"
        }
        val file = target.file.canonicalFile
        target.id to TargetState(file, TargetIdentity.capture(file))
    }
    private val lock = Any()
    private val outputs = LinkedHashMap<String, OutputTransaction>()
    private val terminalOutputs = LinkedHashMap<String, Bundle>()
    private val outputBatches = LinkedHashMap<String, ExplorerActionOutputBatchRecord>()
    private var clientLease: ClientLease? = null
    private var closed = false

    init {
        require(parent.isDirectory) { "Explorer Action host session parent is not a directory" }
        require(targetsById.isNotEmpty() && targetsById.size == targets.size) {
            "Explorer Action host session targets are empty or duplicated"
        }
        require(targetsById.values.all { target -> target.file.parentFile?.canonicalFile == parent }) {
            "Explorer Action host session targets do not share the declared parent"
        }
    }

    override fun listChildren(targetId: String, relativePath: String, offset: Int, limit: Int): Bundle {
        enforceCaller()
        require(offset >= 0) { "Explorer Action child offset is invalid" }
        require(limit in 1..ExplorerActionProtocol.MAX_SESSION_PAGE_SIZE) {
            "Explorer Action child page size is invalid"
        }
        val root = target(targetId)
        val directory = resolve(root, relativePath)
        require(directory.isDirectory) { "Explorer Action session path is not a directory" }
        val children = directory.listFiles()
            ?.sortedWith(compareBy<File>({ it.name.lowercase(Locale.ROOT) }, File::getName))
            ?: throw IOException("Explorer Action session cannot list the directory")
        require(offset <= children.size) { "Explorer Action child offset exceeds the directory size" }
        val page = children.drop(offset).take(limit)
        val items = ArrayList<Bundle>(page.size)
        page.forEach { child ->
            val childRelativePath = joinRelativePath(relativePath, child.name)
            items += childMetadata(root, child, childRelativePath)
        }
        val nextOffset = offset + page.size
        return Bundle().apply {
            putParcelableArrayList(ExplorerActionHostSessionKeys.ITEMS, items)
            putInt(ExplorerActionHostSessionKeys.NEXT_OFFSET, nextOffset)
            putBoolean(ExplorerActionHostSessionKeys.COMPLETE, nextOffset >= children.size)
        }
    }

    override fun openFile(targetId: String, relativePath: String): ParcelFileDescriptor {
        enforceCaller()
        val file = resolve(target(targetId), relativePath)
        require(file.isFile && file.canRead()) { "Explorer Action session file is not readable" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun prepareOutput(displayName: String, mimeType: String, conflictPolicy: Int): Bundle {
        enforceCaller()
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
                OutputMode.REPLACE -> commitReplacement(transaction)
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

    private fun target(targetId: String): File = targetState(targetId).file

    private fun targetState(targetId: String): TargetState = synchronized(lock) {
        checkOpen()
        targetsById[targetId]
            ?: throw IllegalArgumentException("Explorer Action host session target is unknown")
    }

    private fun resolve(root: File, rawRelativePath: String): File {
        val relativePath = normalizeRelativePath(rawRelativePath)
        if (relativePath.isEmpty()) return root
        require(root.isDirectory) { "Explorer Action file target cannot have a relative path" }
        val unresolved = File(root, relativePath.replace('/', File.separatorChar)).absoluteFile
        val resolved = unresolved.canonicalFile
        require(unresolved == resolved) { "Explorer Action symbolic-link traversal is not allowed" }
        require(isWithinRoot(root, resolved)) { "Explorer Action session path escapes its target" }
        return resolved
    }

    private fun childMetadata(root: File, child: File, relativePath: String): Bundle {
        val absolute = child.absoluteFile
        val canonical = runCatching { absolute.canonicalFile }.getOrNull()
        val symbolicLink = canonical == null || absolute != canonical
        val withinRoot = canonical?.let { isWithinRoot(root, it) } == true
        val kind = if (child.isDirectory) {
            ExplorerActionValues.TARGET_DIRECTORY
        } else {
            ExplorerActionValues.TARGET_FILE
        }
        return Bundle().apply {
            putString(ExplorerActionHostSessionKeys.RELATIVE_PATH, relativePath)
            putString(
                ExplorerActionHostSessionKeys.DISPLAY_NAME,
                child.name.take(ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH),
            )
            putInt(ExplorerActionHostSessionKeys.KIND, kind)
            putString(
                ExplorerActionHostSessionKeys.MIME_TYPE,
                if (kind == ExplorerActionValues.TARGET_DIRECTORY) DIRECTORY_MIME_TYPE else Mime.fromFileOrWildcard(child.path),
            )
            putLong(ExplorerActionHostSessionKeys.SIZE, if (child.isFile) child.length() else SIZE_UNKNOWN)
            putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, child.lastModified())
            putBoolean(ExplorerActionHostSessionKeys.READABLE, child.canRead() && withinRoot && !symbolicLink)
            putBoolean(ExplorerActionHostSessionKeys.SYMBOLIC_LINK, symbolicLink)
        }
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

    private fun commitReplacement(transaction: OutputTransaction) {
        val targetState = requireNotNull(transaction.replacementTarget) {
            "Explorer Action replacement target state is missing"
        }
        val expectedIdentity = requireNotNull(transaction.expectedTargetIdentity) {
            "Explorer Action replacement target identity is missing"
        }
        FileOutputStream(transaction.temp, true).use { output -> output.fd.sync() }
        check(TargetIdentity.capture(transaction.target) == expectedIdentity) {
            "Explorer Action replacement target changed before commit"
        }
        val committedIdentity = TargetIdentity.capture(transaction.temp)
        try {
            Os.rename(transaction.temp.path, transaction.target.path)
        } catch (error: ErrnoException) {
            throw IOException("Explorer Action target could not be replaced atomically", error)
        }
        targetState.identity = committedIdentity
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
        return synchronized(RESERVATION_LOCK) {
            val target = when (conflictPolicy) {
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL -> {
                    File(parent, displayName).also { candidate ->
                        require(!candidate.exists() && candidate.path !in RESERVED_OUTPUT_PATHS) {
                            "Explorer Action output already exists"
                        }
                    }
                }
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME ->
                    findAvailableOutputTarget(displayName)
                else -> throw IllegalArgumentException("Explorer Action output conflict policy is invalid")
            }.canonicalFile
            require(target.parentFile == parent) { "Explorer Action output escapes its parent" }
            check(RESERVED_OUTPUT_PATHS.add(target.path)) { "Explorer Action output is already reserved" }
            target
        }
    }

    private fun reserveOutputTreeTarget(displayName: String, conflictPolicy: Int): File {
        return synchronized(RESERVATION_LOCK) {
            fun existingCollisionKeys(): Set<String> = parent.listFiles()
                ?.mapTo(HashSet()) { outputTreeCollisionKey(it.name) }
                ?: throw IOException("Explorer Action parent directory cannot be inspected")

            fun reserve(candidateName: String): File? {
                val candidate = File(parent, candidateName).canonicalFile
                if (candidate.parentFile != parent || candidate.path in RESERVED_OUTPUT_PATHS) return null
                if (outputTreeCollisionKey(candidateName) in existingCollisionKeys()) return null
                if (!candidate.mkdir()) return null
                val equivalentSibling = parent.listFiles()
                    .orEmpty()
                    .any { sibling ->
                        sibling != candidate &&
                            outputTreeCollisionKey(sibling.name) == outputTreeCollisionKey(candidate.name)
                    }
                if (equivalentSibling) {
                    candidate.delete()
                    return null
                }
                check(RESERVED_OUTPUT_PATHS.add(candidate.path)) {
                    "Explorer Action output tree is already reserved"
                }
                return candidate
            }

            when (conflictPolicy) {
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_FAIL ->
                    reserve(displayName)
                        ?: throw IllegalArgumentException("Explorer Action output already exists")
                ExplorerActionHostSessionValues.OUTPUT_CONFLICT_AUTO_RENAME -> {
                    reserve(displayName)?.let { return@synchronized it }
                    for (index in 2..MAX_AUTO_RENAME_ATTEMPTS) {
                        val suffix = " ($index)"
                        val maximumStemLength =
                            ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH - suffix.length
                        if (maximumStemLength < 1) break
                        reserve(displayName.take(maximumStemLength) + suffix)?.let {
                            return@synchronized it
                        }
                    }
                    throw IOException("Explorer Action could not choose an available output directory")
                }
                else -> throw IllegalArgumentException(
                    "Explorer Action output conflict policy is invalid",
                )
            }
        }
    }

    private fun reserveReplacementTarget(target: File) {
        synchronized(RESERVATION_LOCK) {
            require(target.parentFile == parent && target.isFile) {
                "Explorer Action replacement target is invalid"
            }
            check(RESERVED_OUTPUT_PATHS.add(target.path)) {
                "Explorer Action replacement target is already reserved"
            }
        }
    }

    private fun findAvailableOutputTarget(displayName: String): File {
        fun available(name: String): File? = File(parent, name).canonicalFile.takeIf { candidate ->
            candidate.parentFile == parent && !candidate.exists() && candidate.path !in RESERVED_OUTPUT_PATHS
        }

        available(displayName)?.let { return it }
        val dotIndex = displayName.lastIndexOf('.').takeIf { it > 0 } ?: displayName.length
        val extension = displayName.substring(dotIndex)
        val originalStem = displayName.substring(0, dotIndex)
        for (index in 1..MAX_AUTO_RENAME_ATTEMPTS) {
            val suffix = " ($index)"
            val maxStemLength = ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH - extension.length - suffix.length
            if (maxStemLength < 1) break
            val candidateName = originalStem.take(maxStemLength) + suffix + extension
            available(candidateName)?.let { return it }
        }
        throw IOException("Explorer Action could not choose an available output name")
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
        synchronized(RESERVATION_LOCK) {
            RESERVED_OUTPUT_PATHS.remove(target.path)
        }
    }

    private fun normalizeOutputName(value: String): String {
        require(value.length in 1..ExplorerActionProtocol.MAX_OUTPUT_DISPLAY_NAME_LENGTH) {
            "Explorer Action output name length is invalid"
        }
        require(value == value.trim() && value != "." && value != "..") {
            "Explorer Action output name is invalid"
        }
        require(value.any { it != '.' && !it.isWhitespace() }) {
            "Explorer Action output name is empty"
        }
        require(value.none { it == '/' || it == '\\' || it == '\u0000' || it.code < 0x20 || it.code == 0x7f }) {
            "Explorer Action output name contains an unsafe character"
        }
        return value
    }

    private fun normalizeRelativePath(value: String): String {
        require(value.length <= ExplorerActionProtocol.MAX_SESSION_RELATIVE_PATH_LENGTH) {
            "Explorer Action relative path is too long"
        }
        if (value.isEmpty()) return value
        require(!value.startsWith('/') && !value.endsWith('/')) {
            "Explorer Action relative path is not relative"
        }
        val segments = value.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) {
            "Explorer Action relative path contains an unsafe segment"
        }
        return value
    }

    private fun normalizeOutputRelativePath(value: String): String {
        require(value.length in 1..ExplorerActionProtocol.MAX_SESSION_RELATIVE_PATH_LENGTH) {
            "Explorer Action output relative path length is invalid"
        }
        require(!value.startsWith('/') && !value.endsWith('/')) {
            "Explorer Action output path is not relative"
        }
        val segments = value.split('/')
        require(segments.isNotEmpty()) { "Explorer Action output path is empty" }
        segments.forEach { segment ->
            normalizeOutputName(segment)
            require(!segment.startsWith(OUTPUT_TREE_MARKER_PREFIX)) {
                "Explorer Action output path uses a reserved name"
            }
        }
        return segments.joinToString("/")
    }

    private fun outputTreeCollisionKey(value: String): String = Normalizer.normalize(
        value,
        Normalizer.Form.NFC,
    ).lowercase(Locale.ROOT)

    private fun rememberTerminalOutput(transactionId: String, bundle: Bundle) {
        terminalOutputs[transactionId] = Bundle(bundle)
        while (terminalOutputs.size > ExplorerActionProtocol.MAX_OUTPUT_TRANSACTION_RESULTS) {
            terminalOutputs.remove(terminalOutputs.keys.first())
        }
    }

    private fun joinRelativePath(parentPath: String, childName: String): String {
        val path = if (parentPath.isEmpty()) childName else "$parentPath/$childName"
        return normalizeRelativePath(path)
    }

    private fun isWithinRoot(root: File, candidate: File): Boolean {
        return candidate == root || candidate.path.startsWith(root.path + File.separator)
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

    private data class TargetState(
        val file: File,
        var identity: TargetIdentity,
    )

    private data class TargetIdentity(
        val device: Long,
        val inode: Long,
        val size: Long,
        val lastModifiedMillis: Long,
    ) {
        companion object {
            fun capture(file: File): TargetIdentity {
                val stat = try {
                    Os.stat(file.path)
                } catch (error: ErrnoException) {
                    throw IOException("Explorer Action target identity is unavailable", error)
                }
                return TargetIdentity(
                    device = stat.st_dev,
                    inode = stat.st_ino,
                    size = stat.st_size,
                    lastModifiedMillis = file.lastModified(),
                )
            }

            fun captureRegularFile(file: File): TargetIdentity {
                val stat = try {
                    Os.lstat(file.path)
                } catch (error: ErrnoException) {
                    throw IOException("Explorer Action output identity is unavailable", error)
                }
                require(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                    "Explorer Action output is not a regular file"
                }
                return TargetIdentity(
                    device = stat.st_dev,
                    inode = stat.st_ino,
                    size = stat.st_size,
                    lastModifiedMillis = file.lastModified(),
                )
            }
        }
    }

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

    private data class OutputTreeDeletion(
        val file: File,
        val afterChildren: Boolean = false,
    )

    companion object {
        private const val DIRECTORY_MIME_TYPE = "inode/directory"
        private const val SIZE_UNKNOWN = -1L
        private const val MAX_AUTO_RENAME_ATTEMPTS = 10_000
        private const val OUTPUT_TREE_MARKER_PREFIX = ".autojs6-explorer-transaction-"
        private const val OUTPUT_TREE_TERMINAL_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
        private const val OUTPUT_BATCH_TERMINAL_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
        private val OUTPUT_STAGING_NAME_PATTERN = Regex(
            "\\.autojs6-explorer-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.part",
        )
        private val PROCESS_INSTANCE_ID = UUID.randomUUID().toString()
        private val RESERVATION_LOCK = Any()
        private val RESERVED_OUTPUT_PATHS = HashSet<String>()
        private val ACTIVE_OUTPUT_TREE_IDS = HashSet<String>()
        private val ACTIVE_OUTPUT_BATCH_IDS = HashSet<String>()
        private val OUTPUT_TREE_RECOVERY_LOCK = Any()
        private val OUTPUT_BATCH_RECOVERY_LOCK = Any()
        private val CLIENT_DEATH_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ExplorerActionClientDeath").apply { isDaemon = true }
        }

        fun create(
            context: Context,
            pluginPackageName: String,
            commonParent: File,
            targets: List<ExplorerActionHostSessionTarget>,
            allowCreateOutput: Boolean,
            allowReplaceTarget: Boolean = false,
            onOutputCommitted: (File) -> Unit = ::notifyCreated,
            onTargetReplaced: (File) -> Unit = ::notifyChanged,
            clock: () -> Long = System::currentTimeMillis,
            afterBatchMemberPublished: (Int) -> Unit = {},
        ): ExplorerActionHostSession {
            val canonicalParent = commonParent.canonicalFile
            val applicationContext = context.applicationContext
            val store = ExplorerActionOutputTreeStore(applicationContext)
            synchronized(OUTPUT_TREE_RECOVERY_LOCK) {
                recoverDurableOutputTrees(store, clock())
            }
            val batchStore = ExplorerActionOutputBatchStore(applicationContext)
            synchronized(OUTPUT_BATCH_RECOVERY_LOCK) {
                recoverDurableOutputBatches(batchStore, clock())
            }
            return ExplorerActionHostSession(
                expectedUid = applicationUid(context.packageManager, pluginPackageName),
                commonParent = canonicalParent,
                targets = targets,
                allowCreateOutput = allowCreateOutput,
                allowReplaceTarget = allowReplaceTarget,
                onOutputCommitted = onOutputCommitted,
                onTargetReplaced = onTargetReplaced,
                outputTreeStore = store,
                outputBatchStore = batchStore,
                clock = clock,
                afterBatchMemberPublished = afterBatchMemberPublished,
            )
        }

        private fun recoverDurableOutputTrees(
            store: ExplorerActionOutputTreeStore,
            nowMillis: Long,
        ) {
            store.list().forEach { record ->
                val age = if (nowMillis < record.updatedAtMillis) {
                    Long.MAX_VALUE
                } else {
                    nowMillis - record.updatedAtMillis
                }
                val terminal = record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED ||
                    record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED
                if (terminal) {
                    if (age >= OUTPUT_TREE_TERMINAL_RETENTION_MILLIS) store.remove(record.id)
                    return@forEach
                }
                val activeInThisProcess = synchronized(RESERVATION_LOCK) {
                    record.id in ACTIVE_OUTPUT_TREE_IDS
                }
                if (activeInThisProcess) return@forEach

                val parent = File(record.parentPath)
                val target = File(record.targetPath)
                val parentMatches = runCatching {
                    val identity = TargetIdentity.capture(parent)
                    identity.device == record.parentDevice && identity.inode == record.parentInode
                }.getOrDefault(false)
                if (!parentMatches) {
                    store.write(
                        record.copy(
                            state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                            updatedAtMillis = nowMillis,
                        ),
                    )
                    return@forEach
                }
                if (!target.exists()) {
                    store.write(
                        record.copy(
                            state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                            updatedAtMillis = nowMillis,
                        ),
                    )
                    return@forEach
                }
                if (!outputTreeIdentityMatches(record, target)) {
                    store.write(
                        record.copy(
                            state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                            updatedAtMillis = nowMillis,
                        ),
                    )
                    return@forEach
                }
                val marker = File(target, record.markerName)
                if (!marker.exists()) {
                    val terminalState = if (
                        record.state == ExplorerActionHostSessionValues.OUTPUT_STATE_VERIFYING
                    ) {
                        ExplorerActionHostSessionValues.OUTPUT_STATE_COMMITTED
                    } else {
                        ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED
                    }
                    store.write(record.copy(state = terminalState, updatedAtMillis = nowMillis))
                } else if (safeDeleteTree(target)) {
                    store.write(
                        record.copy(
                            state = ExplorerActionHostSessionValues.OUTPUT_STATE_ABORTED,
                            updatedAtMillis = nowMillis,
                        ),
                    )
                }
            }
        }

        private fun recoverDurableOutputBatches(
            store: ExplorerActionOutputBatchStore,
            nowMillis: Long,
        ) {
            store.list().forEach { record ->
                val age = if (nowMillis < record.updatedAtMillis) {
                    0L
                } else {
                    nowMillis - record.updatedAtMillis
                }
                val terminal = record.state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_COMMITTED ||
                    record.state == ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
                if (terminal) {
                    if (age >= OUTPUT_BATCH_TERMINAL_RETENTION_MILLIS) store.remove(record.id)
                    return@forEach
                }
                val activeInThisProcess = synchronized(RESERVATION_LOCK) {
                    record.id in ACTIVE_OUTPUT_BATCH_IDS
                }
                if (activeInThisProcess) return@forEach

                val failures = mutableListOf<Throwable>()
                val parent = File(record.parentPath)
                val parentMatches = runCatching {
                    val canonicalParent = parent.canonicalFile
                    val identity = TargetIdentity.capture(canonicalParent)
                    canonicalParent == parent &&
                        identity.device == record.parentDevice &&
                        identity.inode == record.parentInode
                }.getOrDefault(false)
                if (!parentMatches) {
                    failures += IOException("Explorer Action output batch recovery parent changed")
                } else {
                    record.members.asReversed().forEach { member ->
                        listOf(File(member.targetPath), File(member.stagingPath)).forEach { file ->
                            val failure = runCatching {
                                val canonical = file.canonicalFile
                                check(canonical == file && canonical.parentFile == parent) {
                                    "Explorer Action output batch recovery path is invalid"
                                }
                                if (file == File(member.stagingPath)) {
                                    check(OUTPUT_STAGING_NAME_PATTERN.matches(file.name)) {
                                        "Explorer Action output batch staging name is invalid"
                                    }
                                }
                                val stat = try {
                                    Os.lstat(file.path)
                                } catch (error: ErrnoException) {
                                    if (error.errno == OsConstants.ENOENT) return@runCatching
                                    throw IOException(
                                        "Explorer Action output batch recovery path is unavailable",
                                        error,
                                    )
                                }
                                check(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                                    "Explorer Action output batch recovery path is not a regular file"
                                }
                                check(
                                    stat.st_dev == member.stagingDevice &&
                                        stat.st_ino == member.stagingInode &&
                                        stat.st_size == member.stagingSize &&
                                        file.lastModified() == member.stagingLastModifiedMillis,
                                ) { "Explorer Action output batch recovery identity changed" }
                                if (!file.delete()) {
                                    throw IOException(
                                        "Explorer Action output batch recovery file could not be removed",
                                    )
                                }
                            }.exceptionOrNull()
                            if (failure != null) failures += failure
                        }
                    }
                    runCatching { syncDirectoryForRecovery(parent) }
                        .exceptionOrNull()
                        ?.let(failures::add)
                }
                val recovered = record.copy(
                    state = if (failures.isEmpty()) {
                        ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_ABORTED
                    } else {
                        ExplorerActionHostSessionValues.OUTPUT_BATCH_STATE_RECOVERY_REQUIRED
                    },
                    updatedAtMillis = nowMillis.coerceAtLeast(record.updatedAtMillis),
                )
                runCatching { store.write(recovered) }
            }
        }

        private fun markOutputTreeActive(transactionId: String, active: Boolean) {
            synchronized(RESERVATION_LOCK) {
                if (active) {
                    ACTIVE_OUTPUT_TREE_IDS += transactionId
                } else {
                    ACTIVE_OUTPUT_TREE_IDS -= transactionId
                }
            }
        }

        private fun markOutputBatchActive(batchId: String, active: Boolean) {
            synchronized(RESERVATION_LOCK) {
                if (active) {
                    ACTIVE_OUTPUT_BATCH_IDS += batchId
                } else {
                    ACTIVE_OUTPUT_BATCH_IDS -= batchId
                }
            }
        }

        private fun syncDirectoryForRecovery(directory: File) {
            var descriptor: java.io.FileDescriptor? = null
            try {
                descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
                Os.fsync(descriptor)
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EINVAL && error.errno != OsConstants.EOPNOTSUPP) {
                    throw IOException(
                        "Explorer Action output batch recovery directory could not be synchronized",
                        error,
                    )
                }
            } finally {
                descriptor?.let { runCatching { Os.close(it) } }
            }
        }

        private fun outputTreeIdentityMatches(
            record: ExplorerActionOutputTreeRecord,
            target: File,
        ): Boolean = runCatching {
            val identity = TargetIdentity.capture(target)
            identity.device == record.targetDevice && identity.inode == record.targetInode
        }.getOrDefault(false)

        private fun safeDeleteTree(root: File): Boolean {
            val rootStat = try {
                Os.lstat(root.path)
            } catch (error: ErrnoException) {
                return error.errno == OsConstants.ENOENT
            }
            if (!OsConstants.S_ISDIR(rootStat.st_mode) || OsConstants.S_ISLNK(rootStat.st_mode)) {
                return false
            }

            val pending = ArrayDeque<OutputTreeDeletion>()
            pending.addLast(OutputTreeDeletion(root))
            while (pending.isNotEmpty()) {
                val current = pending.removeLast()
                if (current.afterChildren) {
                    if (!current.file.delete()) return false
                    continue
                }
                val stat = runCatching { Os.lstat(current.file.path) }.getOrNull() ?: return false
                if (OsConstants.S_ISDIR(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode)) {
                    val children = current.file.listFiles() ?: return false
                    pending.addLast(current.copy(afterChildren = true))
                    children.forEach { child -> pending.addLast(OutputTreeDeletion(child)) }
                } else if (!current.file.delete()) {
                    return false
                }
            }
            return true
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
