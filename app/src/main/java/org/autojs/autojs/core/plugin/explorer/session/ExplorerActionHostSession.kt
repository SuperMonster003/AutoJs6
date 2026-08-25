package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
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
import java.util.Locale
import java.util.UUID

internal data class ExplorerActionHostSessionTarget(
    val id: String,
    val file: File,
)

/** A request-scoped capability. Every remote call is pinned to the launched plugin UID. */
internal class ExplorerActionHostSession private constructor(
    private val expectedUid: Int,
    commonParent: File,
    targets: List<ExplorerActionHostSessionTarget>,
    private val allowCreateOutput: Boolean,
    private val allowReplaceTarget: Boolean,
    private val onOutputCommitted: (File) -> Unit,
    private val onTargetReplaced: (File) -> Unit,
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

    override fun openOutput(transactionId: String): ParcelFileDescriptor {
        enforceCaller()
        return synchronized(lock) {
            checkOpen()
            val transaction = output(transactionId)
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
            val readyToCommit = when (transaction.mode) {
                OutputMode.CREATE ->
                    transaction.state == OutputState.OPENED ||
                        transaction.state == OutputState.VERIFYING
                OutputMode.REPLACE -> transaction.state == OutputState.VERIFYING
            }
            check(readyToCommit) {
                "Explorer Action output is not ready to commit"
            }
            check(transaction.temp.isFile) { "Explorer Action staging output is missing" }
            when (transaction.mode) {
                OutputMode.CREATE -> {
                    check(!transaction.target.exists()) { "Explorer Action output target now exists" }
                    if (!transaction.temp.renameTo(transaction.target)) {
                        throw IOException("Explorer Action output could not be committed atomically")
                    }
                }
                OutputMode.REPLACE -> commitReplacement(transaction)
            }
            outputs.remove(transaction.id)
            releaseOutputTarget(transaction.target)
            CommittedOutput(
                target = transaction.target,
                mode = transaction.mode,
                result = outputBundle(transaction).apply {
                    putLong(ExplorerActionHostSessionKeys.SIZE, transaction.target.length())
                    putLong(ExplorerActionHostSessionKeys.LAST_MODIFIED, transaction.target.lastModified())
                },
            )
        }
        runCatching {
            when (committed.mode) {
                OutputMode.CREATE -> onOutputCommitted(committed.target)
                OutputMode.REPLACE -> onTargetReplaced(committed.target)
            }
        }
        return committed.result
    }

    override fun abortOutput(transactionId: String) {
        enforceCaller()
        val transaction = synchronized(lock) {
            checkOpen()
            outputs.remove(transactionId)
        } ?: return
        cleanup(transaction)
    }

    override fun close() {
        enforceCaller()
        val abandoned = synchronized(lock) {
            if (closed) return
            closed = true
            outputs.values.toList().also { outputs.clear() }
        }
        abandoned.forEach(::cleanup)
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
        transaction.temp.delete()
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
        var state: OutputState = OutputState.PREPARED,
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
        }
    }

    private enum class OutputMode {
        CREATE,
        REPLACE,
    }

    private enum class OutputState {
        PREPARED,
        OPENED,
        VERIFYING,
    }

    companion object {
        private const val DIRECTORY_MIME_TYPE = "inode/directory"
        private const val SIZE_UNKNOWN = -1L
        private const val MAX_AUTO_RENAME_ATTEMPTS = 10_000
        private val RESERVATION_LOCK = Any()
        private val RESERVED_OUTPUT_PATHS = HashSet<String>()

        fun create(
            context: Context,
            pluginPackageName: String,
            commonParent: File,
            targets: List<ExplorerActionHostSessionTarget>,
            allowCreateOutput: Boolean,
            allowReplaceTarget: Boolean = false,
            onOutputCommitted: (File) -> Unit = ::notifyCreated,
            onTargetReplaced: (File) -> Unit = ::notifyChanged,
        ): ExplorerActionHostSession {
            return ExplorerActionHostSession(
                expectedUid = applicationUid(context.packageManager, pluginPackageName),
                commonParent = commonParent,
                targets = targets,
                allowCreateOutput = allowCreateOutput,
                allowReplaceTarget = allowReplaceTarget,
                onOutputCommitted = onOutputCommitted,
                onTargetReplaced = onTargetReplaced,
            )
        }

        private fun notifyCreated(file: File) {
            val notify = {
                Explorers.workspace().notifyItemCreated(
                    ExplorerFileItem(file, ExplorerDirPage(file.parentFile, null)),
                )
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
