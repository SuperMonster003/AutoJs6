package org.autojs.autojs.ui.edit.editor.ace.font

import android.content.Context
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.putOrRemove
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap

class FontStore(
    val rootDirectory: File,
) {
    private val fontsDirectory = File(rootDirectory, FONTS_DIRECTORY_NAME)
    private val partsDirectory = File(rootDirectory, PARTS_DIRECTORY_NAME)
    private val storeLockFile = FontIo.storeLockFile(rootDirectory)
    private val verificationCache = ConcurrentHashMap<String, VerifiedFileStamp>()

    init {
        withStoreLock(::cleanupOrphanedPartsLocked)
    }

    constructor(context: Context) : this(File(context.noBackupFilesDir, DIRECTORY_NAME))

    /** Returns the current durable record for [fontId], independently of the latest catalog. */
    fun installedFont(fontId: String): InstalledFont? = withStoreLock {
        installedFontLocked(fontId)
    }

    private fun installedFontLocked(fontId: String): InstalledFont? {
        validateFontId(fontId)
        if (fontId in AceFontIds.reservedRemoteIds) return null
        val directory = fontDirectory(fontId)
        if (!directory.isDirectory) return null

        readMetadata(directory)?.let { record ->
            if (record.fontId == fontId && verifyInstalled(record)) {
                cleanupUnreferencedFontFiles(directory, record.file)
                return record
            }
        }
        return recoverFromFontFiles(fontId, directory)
    }

    fun installedFile(fontId: String): File? = withStoreLock {
        installedFontLocked(fontId)?.file
    }

    /**
     * Resolves only the digest in the current installation manifest. This is the intended WebView
     * virtual-URL entry point; caller-provided paths are never accepted.
     */
    fun installedFile(fontId: String, sha256: String): File? = withStoreLock {
        validateFontId(fontId)
        val normalizedSha = sha256.lowercase()
        if (!AceFontIds.validSha256.matches(normalizedSha)) return@withStoreLock null
        installedFontLocked(fontId)?.takeIf { it.sha256 == normalizedSha }?.file
    }

    /** Opens the verified descriptor before releasing the store lock, closing the File-to-open race. */
    fun openInstalledFont(fontId: String, sha256: String): OpenedInstalledFont? = withStoreLock {
        validateFontId(fontId)
        val normalizedSha = sha256.lowercase()
        if (!AceFontIds.validSha256.matches(normalizedSha)) return@withStoreLock null
        val installed = installedFontLocked(fontId)?.takeIf { it.sha256 == normalizedSha }
            ?: return@withStoreLock null
        val input = try {
            FileInputStream(installed.file)
        } catch (_: Exception) {
            return@withStoreLock null
        }
        try {
            // Re-verify the exact descriptor served to WebView. This prevents the metadata
            // fast-path cache from authorizing same-size, timestamp-preserving tampering.
            FontFileVerifier.verify(input, installed.sizeBytes, installed.sha256)
            input.channel.position(0L)
            OpenedInstalledFont(input, installed.sizeBytes)
        } catch (_: Exception) {
            verificationCache.remove(installed.file.absolutePath)
            runCatching(input::close)
            null
        }
    }

    fun installationState(font: RemoteFont): FontInstallationState = withStoreLock {
        val installed = installedFontLocked(font.id)
        val status = when {
            font.artifact.revoked -> FontInstallationStatus.REVOKED
            installed == null -> FontInstallationStatus.NOT_INSTALLED
            installed.sha256 == font.artifact.sha256 -> FontInstallationStatus.INSTALLED
            else -> FontInstallationStatus.UPDATE_AVAILABLE
        }
        FontInstallationState(status, installed)
    }

    fun installedFonts(): List<InstalledFont> = withStoreLock {
        fontsDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && AceFontIds.validId.matches(it.name) }
            ?.mapNotNull { installedFontLocked(it.name) }
            ?.sortedBy { it.fontId }
            ?.toList()
            .orEmpty()
    }

    /** The bundled and system font IDs are deliberately undeletable through this store. */
    fun delete(fontId: String): Boolean = withStoreLock {
        validateFontId(fontId)
        if (fontId in AceFontIds.reservedRemoteIds) return@withStoreLock false
        deleteDirectoryLocked(fontId)
    }

    /** Atomically deletes only the installation manifest that still names [sha256]. */
    fun deleteIfCurrent(fontId: String, sha256: String): Boolean = withStoreLock {
        validateFontId(fontId)
        val normalizedSha = sha256.lowercase()
        if (fontId in AceFontIds.reservedRemoteIds || !AceFontIds.validSha256.matches(normalizedSha)) {
            return@withStoreLock false
        }
        val installed = installedFontLocked(fontId) ?: return@withStoreLock false
        if (installed.sha256 != normalizedSha) return@withStoreLock false
        deleteDirectoryLocked(fontId)
    }

    private fun deleteDirectoryLocked(fontId: String): Boolean {
        val directory = fontDirectory(fontId)
        if (!directory.exists()) return false
        directory.listFiles()?.forEach { child ->
            if (child.isFile) {
                verificationCache.remove(child.absolutePath)
                if (!child.delete()) return false
            } else {
                return false
            }
        }
        return directory.delete()
    }

    internal fun createPartFile(font: RemoteFont): File = withStoreLock {
        validateRemoteFont(font)
        ensureDirectory(partsDirectory)
        val partFile = File.createTempFile(
            "${font.id}-${font.artifact.sha256.take(12)}-",
            ".part",
            partsDirectory,
        )
        try {
            registerPartLease(partFile)
            partFile
        } catch (e: Exception) {
            partFile.delete()
            throw e
        }
    }

    internal fun installVerifiedPart(font: RemoteFont, partFile: File): InstalledFont = withStoreLock {
        installVerifiedPartLocked(font, partFile)
    }

    private fun installVerifiedPartLocked(font: RemoteFont, partFile: File): InstalledFont {
        validateRemoteFont(font)
        if (font.artifact.revoked) throw FontFileValidationException("Font artifact is revoked")
        requirePartFile(partFile)
        if (!hasPartLease(partFile)) {
            throw FontFileValidationException("Downloaded font no longer owns its temporary file lease")
        }
        FontFileVerifier.verify(partFile, font.artifact.sizeBytes, font.artifact.sha256)

        val directory = fontDirectory(font.id)
        ensureDirectory(directory)
        val previous = installedFontLocked(font.id)
        val target = File(directory, "${font.artifact.sha256}.woff2")
        if (target.isFile && !verifyFile(target, font.artifact.sizeBytes, font.artifact.sha256)) {
            verificationCache.remove(target.absolutePath)
            if (!target.delete()) throw FontFileValidationException("Unable to replace corrupt installed font")
        }
        // Windows cannot rename or delete an open locked file. The store lock prevents a cleaner
        // in another process from observing the gap between releasing the lease and the rename.
        releasePartLease(partFile)
        if (!target.exists() && !partFile.renameTo(target)) {
            throw FontFileValidationException("Unable to atomically install downloaded font")
        }
        if (target.exists() && partFile.exists() && !partFile.delete()) {
            throw FontFileValidationException("Unable to remove redundant partial font")
        }

        val installed = InstalledFont(
            fontId = font.id,
            version = font.version,
            sha256 = font.artifact.sha256,
            sizeBytes = font.artifact.sizeBytes,
            file = target,
            installedAtMillis = System.currentTimeMillis(),
        )
        try {
            writeMetadata(directory, installed)
        } catch (e: Exception) {
            if (previous?.sha256 != installed.sha256) {
                verificationCache.remove(target.absolutePath)
                target.delete()
            }
            throw FontFileValidationException("Unable to persist installed font manifest", e)
        }

        directory.listFiles { file -> file.isFile && file.extension.equals("woff2", true) }
            ?.filterNot { it.name == target.name }
            ?.forEach { old ->
                verificationCache.remove(old.absolutePath)
                old.delete()
            }
        cacheVerified(target, installed.sha256)
        return installed
    }

    internal fun discardPart(partFile: File) {
        withStoreLock {
            runCatching<Unit> {
                requirePartFile(partFile)
                releasePartLease(partFile)
                partFile.delete()
            }
        }
    }

    /** Enforces the global catalog -> store lock order for authorization plus installation. */
    internal fun <T> withCatalogAuthorizationLock(action: () -> T): T =
        FontIo.withFileLock(FontIo.catalogLockFile(rootDirectory), action)

    private fun recoverFromFontFiles(fontId: String, directory: File): InstalledFont? {
        val candidates = directory.listFiles { file ->
            file.isFile && file.name.matches(INSTALLED_FONT_FILE)
        }.orEmpty().sortedByDescending(File::lastModified)
        var recovered: InstalledFont? = null
        candidates.forEach { file ->
            val sha = file.name.removeSuffix(".woff2")
            if (recovered == null && verifyFile(file, file.length(), sha)) {
                recovered = InstalledFont(fontId, null, sha, file.length(), file, file.lastModified())
            } else {
                verificationCache.remove(file.absolutePath)
                file.delete()
            }
        }
        recovered?.let { runCatching { writeMetadata(directory, it) } }
        return recovered
    }

    private fun readMetadata(directory: File): InstalledFont? {
        val metadata = File(directory, METADATA_FILE_NAME)
        FontIo.recoverBackup(metadata)
        if (!metadata.isFile || metadata.length() > MAX_METADATA_BYTES) return null
        val text = runCatching { metadata.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return decodeMetadata(text, directory)
    }

    private fun writeMetadata(directory: File, installed: InstalledFont) {
        FontIo.writeAndReplace(File(directory, METADATA_FILE_NAME)) { output ->
            output.write(encodeMetadata(installed).toByteArray(Charsets.UTF_8))
        }
    }

    private fun verifyInstalled(installed: InstalledFont): Boolean {
        if (!installed.file.isFile || installed.file.parentFile != fontDirectory(installed.fontId)) return false
        return verifyFile(installed.file, installed.sizeBytes, installed.sha256)
    }

    private fun verifyFile(file: File, expectedSize: Long, expectedSha256: String): Boolean {
        if (!file.isFile || file.length() != expectedSize) return false
        val cached = verificationCache[file.absolutePath]
        if (cached != null && cached.length == file.length() && cached.lastModified == file.lastModified() &&
            cached.sha256 == expectedSha256
        ) return true
        return try {
            FontFileVerifier.verify(file, expectedSize, expectedSha256)
            cacheVerified(file, expectedSha256)
            true
        } catch (_: FontFileValidationException) {
            verificationCache.remove(file.absolutePath)
            false
        }
    }

    private fun cacheVerified(file: File, sha256: String) {
        verificationCache[file.absolutePath] = VerifiedFileStamp(file.length(), file.lastModified(), sha256)
    }

    private fun cleanupUnreferencedFontFiles(directory: File, current: File) {
        directory.listFiles { file -> file.isFile && file.name.matches(INSTALLED_FONT_FILE) }
            ?.filterNot { it.name == current.name }
            ?.forEach { orphan ->
                verificationCache.remove(orphan.absolutePath)
                orphan.delete()
            }
    }

    private fun validateFontId(fontId: String) {
        require(AceFontIds.validId.matches(fontId)) { "Invalid font id" }
    }

    private fun validateRemoteFont(font: RemoteFont) {
        validateFontId(font.id)
        require(font.id !in AceFontIds.reservedRemoteIds) { "Font id '${font.id}' is reserved" }
        FontFileVerifier.validateExpectation(font.artifact.sizeBytes, font.artifact.sha256)
    }

    private fun fontDirectory(fontId: String): File = File(fontsDirectory, fontId)

    private fun requirePartFile(file: File) {
        val canonicalParent = runCatching { file.canonicalFile.parentFile }.getOrNull()
        val expectedParent = runCatching { partsDirectory.canonicalFile }.getOrNull()
        require(canonicalParent == expectedParent && file.name.endsWith(".part")) {
            "Temporary font is outside the managed parts directory"
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IllegalStateException("Unable to create font directory ${directory.absolutePath}")
        }
    }

    private fun <T> withStoreLock(action: () -> T): T = FontIo.withFileLock(storeLockFile, action)

    private fun registerPartLease(partFile: File) {
        val key = canonicalPath(partFile)
        val leaseFile = leaseFile(partFile)
        val channel = try {
            RandomAccessFile(leaseFile, "rw").channel
        } catch (e: Exception) {
            throw FontFileValidationException("Unable to create temporary font lease", e)
        }
        val fileLock = try {
            channel.lock()
        } catch (e: Exception) {
            runCatching(channel::close)
            throw FontFileValidationException("Unable to acquire temporary font lease", e)
        }
        val lease = PartLease(leaseFile, channel, fileLock)
        synchronized(PARTS_LOCK) {
            if (ACTIVE_PART_LEASES.putIfAbsent(key, lease) != null) {
                lease.closeQuietly()
                throw FontFileValidationException("Temporary font lease already exists")
            }
        }
    }

    private fun hasPartLease(partFile: File): Boolean = synchronized(PARTS_LOCK) {
        canonicalPath(partFile) in ACTIVE_PART_LEASES
    }

    private fun releasePartLease(partFile: File) {
        val lease = synchronized(PARTS_LOCK) { ACTIVE_PART_LEASES.remove(canonicalPath(partFile)) }
            ?: return
        try {
            lease.close()
        } finally {
            lease.file.delete()
        }
    }

    private fun cleanupOrphanedPartsLocked() {
        val now = System.currentTimeMillis()
        partsDirectory.listFiles { file -> file.isFile && file.name.endsWith(".part") }
            ?.forEach { partFile ->
                if (hasPartLease(partFile)) return@forEach
                val leaseFile = leaseFile(partFile)
                if (!leaseFile.isFile) {
                    if (now - partFile.lastModified() >= LEGACY_PART_GRACE_MILLIS) partFile.delete()
                    return@forEach
                }
                val cleanupLease = tryAcquireCleanupLease(leaseFile) ?: return@forEach
                cleanupLease.closeQuietly()
                partFile.delete()
                leaseFile.delete()
            }
        partsDirectory.listFiles { file -> file.isFile && file.name.endsWith(LEASE_SUFFIX) }
            ?.filter { lease -> !File(lease.parentFile, lease.name.removeSuffix(LEASE_SUFFIX)).exists() }
            ?.forEach { orphanLease ->
                val cleanupLease = tryAcquireCleanupLease(orphanLease) ?: return@forEach
                cleanupLease.closeQuietly()
                orphanLease.delete()
            }
    }

    private fun tryAcquireCleanupLease(leaseFile: File): PartLease? {
        val channel = runCatching { RandomAccessFile(leaseFile, "rw").channel }.getOrNull() ?: return null
        val fileLock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (_: Exception) {
            null
        }
        if (fileLock == null) {
            runCatching(channel::close)
            return null
        }
        return PartLease(leaseFile, channel, fileLock)
    }

    private fun canonicalPath(file: File): String = runCatching { file.canonicalPath }.getOrElse {
        file.absolutePath
    }

    private fun leaseFile(partFile: File): File = File(partFile.parentFile, partFile.name + LEASE_SUFFIX)

    private class PartLease(
        val file: File,
        private val channel: FileChannel,
        private val lock: FileLock,
    ) {
        fun close() {
            try {
                if (lock.isValid) lock.release()
            } finally {
                channel.close()
            }
        }

        fun closeQuietly() {
            runCatching(::close)
        }
    }

    private data class VerifiedFileStamp(
        val length: Long,
        val lastModified: Long,
        val sha256: String,
    )

    companion object {
        const val DIRECTORY_NAME = "ace_fonts"
        private const val FONTS_DIRECTORY_NAME = "fonts"
        private const val PARTS_DIRECTORY_NAME = ".parts"
        private const val METADATA_FILE_NAME = "installed.json"
        private const val METADATA_FORMAT_VERSION = 1
        private const val MAX_METADATA_BYTES = 64L * 1024L
        private const val LEASE_SUFFIX = ".lease"
        private const val LEGACY_PART_GRACE_MILLIS = 24L * 60L * 60L * 1000L
        private val INSTALLED_FONT_FILE = Regex("[0-9a-f]{64}\\.woff2")
        private val PARTS_LOCK = Any()
        private val ACTIVE_PART_LEASES = hashMapOf<String, PartLease>()

        /**
         * Encodes the metadata of an installed font (`installed.json`) with [SettingsJson] (docs/settings/ROADMAP.md
         * P5.3), byte for byte as `Gson().toJson` wrote the former DTO on Android: Gson takes the members from
         * `Class.getDeclaredFields`, which ART returns sorted by name (the dex format sorts the fields of a class), so the
         * members are in name order rather than in declaration order; a null [InstalledFont.version] is omitted and
         * strings are escaped HTML-safe like Gson's default.
         * zh-CN: 以 SettingsJson 编码已安装字体的元数据 (ROADMAP P5.3), 与 Android 上 `Gson().toJson` 写出原 DTO 的结果逐字节相同:
         * Gson 按 `Class.getDeclaredFields` 取成员, ART 按名称排序返回 (dex 格式对类的字段排序), 因此成员按名称排序, 而不是声明
         * 顺序; version 为 null 时省略, 字符串按 Gson 默认的 HTML 安全方式转义.
         */
        internal fun encodeMetadata(installed: InstalledFont): String = SettingsJson.stringify(
            linkedMapOf<String, Any?>(
                "fontId" to installed.fontId,
                "formatVersion" to METADATA_FORMAT_VERSION,
                "installedAtMillis" to installed.installedAtMillis,
                "sha256" to installed.sha256,
                "sizeBytes" to installed.sizeBytes,
            ).putOrRemove("version", installed.version),
            htmlSafe = true,
        )

        /**
         * Decodes the metadata of an installed font in [directory] ([encodeMetadata], and every file that Gson wrote);
         * null for malformed text and for an invalid record. Members are read as Gson read them into the former DTO: a
         * missing member (or a null number) gives the DTO default (`formatVersion` 1, `sizeBytes` and
         * `installedAtMillis` 0), unknown members are ignored and a member of another type makes the record unreadable.
         * Unlike Gson, the text must be strict JSON without duplicate keys, and a number written as a string or a string
         * written as a number is no longer accepted; Gson never wrote either.
         * zh-CN: 解码 [directory] 中已安装字体的元数据 ([encodeMetadata] 以及 Gson 写出的全部文件); 文本格式错误或记录无效时返回
         * null. 成员按 Gson 读入原 DTO 的方式读取: 缺少成员 (或数字为 null) 时取 DTO 的默认值 (formatVersion 为 1, sizeBytes 与
         * installedAtMillis 为 0), 忽略未知成员, 成员类型不符时记录不可读. 与 Gson 不同, 文本须为没有重复键的严格 JSON, 也不再
         * 接受写成字符串的数字或写成数字的字符串; Gson 从未写出这两种形式.
         */
        internal fun decodeMetadata(text: String, directory: File): InstalledFont? = runCatching {
            val raw = SettingsJson.parseMap(text)
            val formatVersion = metadataLong(raw, "formatVersion") ?: METADATA_FORMAT_VERSION.toLong()
            val fontId = metadataString(raw, "fontId")
            val version = metadataString(raw, "version")
            val sha = metadataString(raw, "sha256")?.lowercase()
            val sizeBytes = metadataLong(raw, "sizeBytes") ?: 0L
            val installedAtMillis = metadataLong(raw, "installedAtMillis") ?: 0L
            if (formatVersion != METADATA_FORMAT_VERSION.toLong() ||
                fontId == null || !AceFontIds.validId.matches(fontId) || sha == null || !AceFontIds.validSha256.matches(sha) ||
                sizeBytes !in FontFileVerifier.WOFF2_HEADER_SIZE..FontFileVerifier.MAX_FONT_BYTES
            ) return@runCatching null
            InstalledFont(
                fontId = fontId,
                version = version,
                sha256 = sha,
                sizeBytes = sizeBytes,
                file = File(directory, "$sha.woff2"),
                installedAtMillis = installedAtMillis,
            )
        }.getOrNull()

        // A string member: null when missing or null, like Gson; another type fails.
        private fun metadataString(raw: Map<String, Any?>, key: String): String? {
            val value = raw[key]
            require(value == null || value is String) { "Invalid $key" }
            return value as String?
        }

        // A number member: null when missing or null; Gson's nextLong also took an integral fraction such as 1.0.
        private fun metadataLong(raw: Map<String, Any?>, key: String): Long? {
            val value = raw[key] ?: return null
            return requireNotNull((value as? Number)?.let(SettingsJson::toLong)) { "Invalid $key" }
        }
    }
}
