package org.autojs.autojs.core.plugin.explorer.output

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.text.Charsets.UTF_8

internal data class ExplorerActionOutputPublishPlan(
    val destination: File,
    val temporary: File,
    val mimeType: String,
    val parentIdentity: ExplorerActionFileIdentity,
)

internal data class ExplorerActionOutputPublishResult(
    val destination: File,
    val size: Long,
    val sha256: String,
    val commitMode: ExplorerActionOutputCommitMode = ExplorerActionOutputCommitMode.RECOVERED,
    val directorySync: ExplorerActionOutputDirectorySync = ExplorerActionOutputDirectorySync.SYNCED,
)

internal fun interface ExplorerActionOutputFileOpener {
    fun createNew(destination: File): FileOutputStream
}

internal interface ExplorerActionOutputNoReplaceCommitter {
    fun commit(temporary: File, destination: File): ExplorerActionOutputCommitMode
    fun unlink(file: File)
    fun syncDirectory(directory: File): ExplorerActionOutputDirectorySync
}

internal enum class ExplorerActionOutputCommitMode {
    HARD_LINK,
    ATOMIC_RENAME_FALLBACK,
    RECOVERED,
}

internal enum class ExplorerActionOutputDirectorySync {
    SYNCED,
    UNSUPPORTED,
}

internal class ExplorerActionOutputPublisher(
    private val tokenFactory: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val fileOpener: ExplorerActionOutputFileOpener = ExplorerActionAndroidOutputFileOpener,
    private val committer: ExplorerActionOutputNoReplaceCommitter = ExplorerActionAndroidOutputNoReplaceCommitter,
    private val identityReader: ExplorerActionFileIdentityReader = ExplorerActionAndroidFileIdentityReader,
) {

    fun plan(
        source: File,
        outputNameSuffix: String,
        mimeType: String,
        expectedParentIdentity: ExplorerActionFileIdentity? = null,
    ): ExplorerActionOutputPublishPlan {
        val canonicalSource = source.canonicalFile
        require(canonicalSource.isFile) { "Explorer Action output source is not a file" }
        val directory = canonicalSource.parentFile?.canonicalFile
            ?: throw IllegalArgumentException("Explorer Action output source has no parent directory")
        require(directory.isDirectory && directory.canWrite()) { "Explorer Action output directory is not writable" }
        val parentIdentity = identityReader.stat(directory)
        require(expectedParentIdentity == null || parentIdentity == expectedParentIdentity) {
            "Explorer Action output source directory identity changed"
        }
        require(SAFE_SUFFIX_PATTERN.matches(outputNameSuffix)) { "Explorer Action output suffix is unsafe" }
        val extension = ExplorerActionImageOutputValidator.extensionForMimeType(mimeType)
            ?: throw IllegalArgumentException("Unsupported Explorer Action output MIME type: $mimeType")
        val sourceBaseName = canonicalSource.nameWithoutExtension.ifBlank { canonicalSource.name }
        repeat(MAX_DESTINATION_ATTEMPTS) {
            val destinationToken = nextToken()
            val temporaryToken = nextToken()
            if (destinationToken == temporaryToken) return@repeat
            val fixedSuffix = "_${outputNameSuffix}-$destinationToken.$extension"
            val baseName = truncateUtf8(
                value = sourceBaseName,
                maxBytes = MAX_FILE_NAME_UTF8_BYTES - fixedSuffix.toByteArray(UTF_8).size,
            ).ifBlank { FALLBACK_BASE_NAME }
            val candidate = File(
                directory,
                "$baseName$fixedSuffix",
            ).canonicalFile
            val temporary = File(
                directory,
                "$TEMPORARY_PREFIX$temporaryToken$TEMPORARY_SUFFIX",
            ).canonicalFile
            val result = ExplorerActionOutputPublishPlan(candidate, temporary, mimeType, parentIdentity)
            validatePlan(canonicalSource, result)
            if (!candidate.exists() && !temporary.exists()) return result
        }
        throw IOException("Unable to allocate a unique Explorer Action output destination")
    }

    fun restorePlan(
        source: File,
        destination: File,
        temporary: File,
        mimeType: String,
        expectedParentIdentity: ExplorerActionFileIdentity,
    ): ExplorerActionOutputPublishPlan {
        val plan = ExplorerActionOutputPublishPlan(
            destination = destination.canonicalFile,
            temporary = temporary.canonicalFile,
            mimeType = mimeType,
            parentIdentity = expectedParentIdentity,
        )
        validatePlan(source.canonicalFile, plan)
        require(identityReader.stat(plan.destination.parentFile!!) == expectedParentIdentity) {
            "Explorer Action output source directory identity changed"
        }
        return plan
    }

    fun publish(
        snapshot: File,
        plan: ExplorerActionOutputPublishPlan,
        expectedSha256: String,
        maxOutputBytes: Long,
    ): ExplorerActionOutputPublishResult {
        require(snapshot.isFile) { "Explorer Action output snapshot is missing" }
        require(expectedSha256.matches(SHA256_PATTERN)) { "Explorer Action output snapshot hash is invalid" }
        val size = snapshot.length()
        require(size in 1..maxOutputBytes) { "Explorer Action output snapshot size is invalid" }
        validatePlan(source = null, plan = plan)
        val parent = plan.destination.parentFile!!
        requireParentIdentity(parent, plan.parentIdentity)

        var openedTemporaryIdentity: ExplorerActionFileIdentity? = null
        var committed = false
        var commitMode: ExplorerActionOutputCommitMode? = null
        var directorySync = ExplorerActionOutputDirectorySync.SYNCED
        var copied = 0L
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(snapshot).use { input ->
                fileOpener.createNew(plan.temporary).use { output ->
                    openedTemporaryIdentity = identityReader.fstat(output.fd)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val nextCopied = copied + count
                        if (nextCopied < copied || nextCopied > maxOutputBytes) {
                            throw IOException("Explorer Action output exceeded its size limit")
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied = nextCopied
                    }
                    output.fd.sync()
                    require(identityReader.fstat(output.fd) == openedTemporaryIdentity) {
                        "Explorer Action output temporary file identity changed while writing"
                    }
                }
            }
            val temporaryIdentity = requireNotNull(openedTemporaryIdentity)
            require(identityReader.stat(plan.temporary) == temporaryIdentity) {
                "Explorer Action output temporary path changed after writing"
            }
            requireParentIdentity(parent, plan.parentIdentity)
            val actualSha256 = digest.digest().toHex()
            if (copied != size || actualSha256 != expectedSha256) {
                throw IOException("Explorer Action output changed while it was being published")
            }

            commitMode = committer.commit(plan.temporary, plan.destination)
            committed = true
            if (commitMode == ExplorerActionOutputCommitMode.HARD_LINK) {
                require(identityReader.stat(plan.temporary) == temporaryIdentity) {
                    "Explorer Action output temporary path changed during hard-link commit"
                }
            } else {
                require(!plan.temporary.exists()) {
                    "Explorer Action output temporary path survived atomic-rename commit"
                }
            }
            require(identityReader.stat(plan.destination) == temporaryIdentity) {
                "Explorer Action output destination is not the committed temporary file"
            }
            requireParentIdentity(parent, plan.parentIdentity)
            directorySync = weakerDirectorySync(directorySync, committer.syncDirectory(parent))
            if (!verifyPublishedFile(plan.destination, actualSha256, maxOutputBytes, plan.parentIdentity)) {
                throw IOException("Explorer Action output destination changed while it was being published")
            }
            if (commitMode == ExplorerActionOutputCommitMode.HARD_LINK) {
                removeOwnedTemporary(plan, temporaryIdentity)
            }
            directorySync = weakerDirectorySync(directorySync, committer.syncDirectory(parent))
            requireParentIdentity(parent, plan.parentIdentity)
            if (!verifyPublishedFile(plan.destination, actualSha256, maxOutputBytes, plan.parentIdentity)) {
                throw IOException("Explorer Action output destination changed after publication")
            }
            return ExplorerActionOutputPublishResult(
                destination = plan.destination,
                size = copied,
                sha256 = actualSha256,
                commitMode = requireNotNull(commitMode),
                directorySync = directorySync,
            )
        } catch (error: Throwable) {
            openedTemporaryIdentity?.let { identity ->
                runCatching { removeOwnedTemporary(plan, identity) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            // Once the no-replace link succeeds, the destination is never removed here. The
            // journaled COMMITTING state lets recovery verify and finalize a durable result.
            if (committed) runCatching { committer.syncDirectory(parent) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
    }

    fun cleanupTemporary(plan: ExplorerActionOutputPublishPlan) {
        validatePlan(source = null, plan = plan)
        val parent = plan.destination.parentFile!!
        requireParentIdentity(parent, plan.parentIdentity)
        if (!plan.temporary.exists()) return
        val identity = identityReader.stat(plan.temporary)
        removeOwnedTemporary(plan, identity)
        committer.syncDirectory(parent)
        requireParentIdentity(parent, plan.parentIdentity)
    }

    fun verifyPublishedFile(
        file: File,
        expectedSha256: String,
        maxOutputBytes: Long,
        expectedParentIdentity: ExplorerActionFileIdentity? = null,
    ): Boolean {
        if (!expectedSha256.matches(SHA256_PATTERN) || maxOutputBytes <= 0L) return false
        return runCatching {
            val canonicalFile = file.canonicalFile
            val parent = canonicalFile.parentFile?.canonicalFile ?: return false
            expectedParentIdentity?.let { requireParentIdentity(parent, it) }
            if (!canonicalFile.isFile || canonicalFile.length() !in 1..maxOutputBytes) return false
            val beforePathIdentity = identityReader.stat(canonicalFile)
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val descriptorIdentity = FileInputStream(canonicalFile).use { input ->
                val openedIdentity = identityReader.fstat(input.fd)
                require(openedIdentity == beforePathIdentity) { "Published path changed before verification" }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied in 1..maxOutputBytes) { "Published output exceeds its byte limit" }
                    digest.update(buffer, 0, count)
                }
                require(identityReader.fstat(input.fd) == openedIdentity) {
                    "Published file identity changed during verification"
                }
                openedIdentity
            }
            require(copied == canonicalFile.length()) { "Published output size changed during verification" }
            require(identityReader.stat(canonicalFile) == descriptorIdentity) {
                "Published path changed during verification"
            }
            expectedParentIdentity?.let { requireParentIdentity(parent, it) }
            digest.digest().toHex() == expectedSha256
        }.getOrDefault(false)
    }

    private fun validatePlan(source: File?, plan: ExplorerActionOutputPublishPlan) {
        val destination = plan.destination.canonicalFile
        val temporary = plan.temporary.canonicalFile
        val parent = destination.parentFile?.canonicalFile
            ?: throw IOException("Explorer Action output destination has no parent directory")
        require(temporary.parentFile?.canonicalFile == parent) {
            "Explorer Action output temporary file is not beside its destination"
        }
        require(destination == plan.destination && temporary == plan.temporary) {
            "Explorer Action output publication paths are not canonical"
        }
        require(TEMPORARY_NAME_PATTERN.matches(temporary.name)) {
            "Explorer Action output temporary path is not host-derived"
        }
        require(source == null || destination != source.canonicalFile) {
            "Explorer Action output cannot replace its source"
        }
        require(ExplorerActionImageOutputValidator.extensionForMimeType(plan.mimeType) != null) {
            "Explorer Action output MIME type is unsupported"
        }
    }

    private fun requireParentIdentity(
        parent: File,
        expected: ExplorerActionFileIdentity,
    ) {
        require(parent.isDirectory && parent.canWrite()) { "Explorer Action output directory is not writable" }
        require(identityReader.stat(parent) == expected) {
            "Explorer Action output directory identity changed"
        }
    }

    private fun removeOwnedTemporary(
        plan: ExplorerActionOutputPublishPlan,
        expectedIdentity: ExplorerActionFileIdentity,
    ) {
        if (!plan.temporary.exists()) return
        require(identityReader.stat(plan.temporary) == expectedIdentity) {
            "Explorer Action output temporary path no longer identifies the owned file"
        }
        committer.unlink(plan.temporary)
    }

    private fun nextToken(): String = tokenFactory()
        .filter(Char::isLetterOrDigit)
        .take(COMMIT_TOKEN_LENGTH)
        .also { token ->
            require(token.length == COMMIT_TOKEN_LENGTH) {
                "Explorer Action output commit token is invalid"
            }
        }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        require(maxBytes >= FALLBACK_BASE_NAME.length) {
            "Explorer Action output filename has no base-name budget"
        }
        var index = 0
        var usedBytes = 0
        val result = StringBuilder()
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val chars = Character.toChars(codePoint)
            val encodedBytes = String(chars).toByteArray(UTF_8).size
            if (usedBytes + encodedBytes > maxBytes) break
            result.append(chars)
            usedBytes += encodedBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    private fun weakerDirectorySync(
        first: ExplorerActionOutputDirectorySync,
        second: ExplorerActionOutputDirectorySync,
    ): ExplorerActionOutputDirectorySync = if (
        first == ExplorerActionOutputDirectorySync.UNSUPPORTED ||
        second == ExplorerActionOutputDirectorySync.UNSUPPORTED
    ) {
        ExplorerActionOutputDirectorySync.UNSUPPORTED
    } else {
        ExplorerActionOutputDirectorySync.SYNCED
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    internal class DestinationAlreadyExistsException(
        val destination: File,
    ) : IOException("Explorer Action output destination already exists: ${destination.path}")

    internal class AtomicRenameFallbackException(
        temporary: File,
        destination: File,
        cause: Throwable,
    ) : IOException(
        "Unable to atomically rename the Explorer Action publication temporary: " +
            "${temporary.path} -> ${destination.path}",
        cause,
    )

    companion object {
        private const val COMMIT_TOKEN_LENGTH = 32
        private const val MAX_DESTINATION_ATTEMPTS = 16
        private const val MAX_FILE_NAME_UTF8_BYTES = 255
        private const val FALLBACK_BASE_NAME = "output"
        private const val TEMPORARY_PREFIX = ".autojs6-output-"
        private const val TEMPORARY_SUFFIX = ".part"
        private val SAFE_SUFFIX_PATTERN = Regex("[A-Za-z0-9._+-]{1,128}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val TEMPORARY_NAME_PATTERN = Regex(
            "\\.autojs6-output-[A-Za-z0-9]{$COMMIT_TOKEN_LENGTH}\\.part",
        )
    }
}
