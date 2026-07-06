package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SyncFailedException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class RemoteApkBuildWorkspace private constructor(
    val root: File,
    val archiveFile: File,
    val nativeLibrariesArchiveFile: File?,
    val projectRoot: File,
    val sourcePath: File,
    val sourceKind: String,
    val keyStoreFile: File?,
) : Closeable {

    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        deleteRecursivelyBestEffort(root)
    }

    fun toExtras(): Bundle {
        return Bundle().apply {
            putString(ApkBuildRequestExtraKeys.SOURCE_KIND, sourceKind)
            putString(ApkBuildRequestExtraKeys.SOURCE_ROOT_PATH, projectRoot.path)
            putString(ApkBuildRequestExtraKeys.SOURCE_PATH, sourcePath.path)
            nativeLibrariesArchiveFile?.let { putString("nativeLibrariesArchivePath", it.path) }
            keyStoreFile?.let { putString("keyStorePath", it.path) }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 256 * 1024
        private const val EXPECTED_ARCHIVE_FORMAT_VERSION = 1
        private const val TAG = "RemoteApkWorkspace"

        fun prepare(
            context: Context,
            request: ApkBuildRequest,
            cancelSignal: AtomicBoolean? = null,
        ): RemoteApkBuildWorkspace {
            val root = File(context.cacheDir, "remote-apk-build/session-${System.nanoTime()}").apply {
                if (!mkdirs()) {
                    throw IOException("Failed to create remote build workspace: $path")
                }
            }
            try {
                ensureActive(cancelSignal)
                val archiveFile = File(root, "project.zip")
                copyFdToFile(request.projectArchiveFd ?: throw IOException("Project archive fd is missing."), archiveFile, cancelSignal)
                verifyFile(archiveFile, request.projectArchiveSizeBytes, request.projectArchiveSha256, "project archive", cancelSignal)
                ensureActive(cancelSignal)

                val projectRoot = File(root, "unpacked")
                unzipSafely(archiveFile, projectRoot, cancelSignal)
                ensureActive(cancelSignal)

                val nativeLibrariesArchiveFile = request.nativeLibrariesArchiveFd?.let { fd ->
                    File(root, "native-libraries.zip").also { file ->
                        copyFdToFile(fd, file, cancelSignal)
                        verifyFile(
                            file = file,
                            expectedSize = request.nativeLibrariesArchiveSizeBytes,
                            expectedSha256 = request.nativeLibrariesArchiveSha256,
                            label = "native libraries archive",
                            cancelSignal = cancelSignal,
                        )
                    }
                }
                ensureActive(cancelSignal)

                val extras = request.extras ?: throw IOException("Remote build request extras are missing.")
                val archiveVersion = extras.getInt(ApkBuildRequestExtraKeys.ARCHIVE_FORMAT_VERSION, 0)
                if (archiveVersion != EXPECTED_ARCHIVE_FORMAT_VERSION) {
                    throw IOException("Unsupported project archive format version: $archiveVersion")
                }

                val sourceKind = extras.getString(ApkBuildRequestExtraKeys.SOURCE_KIND)
                    ?: throw IOException("Project source kind is missing.")
                if (sourceKind !in setOf(
                        ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY,
                        ApkBuildRequestExtraKeys.SOURCE_KIND_FILE,
                    )
                ) {
                    throw IOException("Unsupported project source kind: $sourceKind")
                }

                val sourcePathValue = extras.getString(ApkBuildRequestExtraKeys.SOURCE_PATH)
                    ?: throw IOException("Project source path is missing.")
                val sourcePath = resolveInside(projectRoot, sourcePathValue)
                when (sourceKind) {
                    ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY -> {
                        if (!sourcePath.isDirectory) {
                            throw IOException("Project source directory does not exist in archive: $sourcePathValue")
                        }
                    }
                    ApkBuildRequestExtraKeys.SOURCE_KIND_FILE -> {
                        if (!sourcePath.isFile) {
                            throw IOException("Project source file does not exist in archive: $sourcePathValue")
                        }
                    }
                }

                val keyStoreFile = request.keyStoreFd?.let { fd ->
                    File(root, "keystore.bin").also { file ->
                        copyFdToFile(fd, file, cancelSignal)
                        verifyFile(file, request.keyStoreSizeBytes, request.keyStoreSha256, "keystore", cancelSignal)
                    }
                }

                return RemoteApkBuildWorkspace(
                    root = root,
                    archiveFile = archiveFile,
                    nativeLibrariesArchiveFile = nativeLibrariesArchiveFile,
                    projectRoot = projectRoot,
                    sourcePath = sourcePath,
                    sourceKind = sourceKind,
                    keyStoreFile = keyStoreFile,
                )
            } catch (t: Throwable) {
                deleteRecursivelyBestEffort(root)
                throw t
            }
        }

        private fun copyFdToFile(
            fd: ParcelFileDescriptor,
            file: File,
            cancelSignal: AtomicBoolean?,
        ) {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                FileOutputStream(file, false).use { output ->
                    copyStreamWithCancel(input, output, cancelSignal)
                    output.flush()
                    syncBestEffort(output)
                }
            }
        }

        private fun syncBestEffort(output: FileOutputStream) {
            try {
                output.fd.sync()
            } catch (e: SyncFailedException) {
                Log.w(TAG, "File descriptor sync failed after copying remote build input; continuing.", e)
            }
        }

        private fun deleteRecursivelyBestEffort(file: File) {
            runCatching {
                deleteRecursively(file)
            }.onFailure {
                Log.w(TAG, "Failed to clean remote build workspace: ${file.path}", it)
            }
        }

        private fun deleteRecursively(file: File) {
            if (!file.exists()) {
                return
            }
            if (file.isDirectory) {
                file.listFiles()?.forEach(::deleteRecursively)
            }
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete remote build workspace file: ${file.path}")
            }
        }

        private fun verifyFile(
            file: File,
            expectedSize: Long,
            expectedSha256: String?,
            label: String,
            cancelSignal: AtomicBoolean?,
        ) {
            ensureActive(cancelSignal)
            if (!file.isFile || file.length() <= 0L) {
                throw IOException("Remote build $label is empty.")
            }
            if (expectedSize > 0L && file.length() != expectedSize) {
                throw IOException("Remote build $label size mismatch: expected=$expectedSize actual=${file.length()}")
            }
            if (!expectedSha256.isNullOrBlank()) {
                val actual = sha256(file, cancelSignal)
                if (!expectedSha256.equals(actual, ignoreCase = true)) {
                    throw IOException("Remote build $label SHA-256 mismatch: expected=$expectedSha256 actual=$actual")
                }
            }
        }

        private fun unzipSafely(
            zipFile: File,
            targetDir: File,
            cancelSignal: AtomicBoolean?,
        ) {
            if (!targetDir.mkdirs()) {
                throw IOException("Failed to create project archive target dir: ${targetDir.path}")
            }
            val canonicalRoot = targetDir.canonicalFile
            ZipInputStream(FileInputStream(zipFile).buffered(BUFFER_SIZE)).use { zip ->
                while (true) {
                    ensureActive(cancelSignal)
                    val entry = zip.nextEntry ?: break
                    extractEntry(zip, entry, canonicalRoot, cancelSignal)
                    zip.closeEntry()
                }
            }
        }

        private fun extractEntry(
            zip: ZipInputStream,
            entry: ZipEntry,
            root: File,
            cancelSignal: AtomicBoolean?,
        ) {
            ensureActive(cancelSignal)
            val name = entry.name.replace('\\', '/')
            if (name.isBlank() || name.startsWith('/') || name.split('/').any { it == ".." }) {
                throw IOException("Unsafe project archive entry: ${entry.name}")
            }
            val out = File(root, name).canonicalFile
            if (!out.path.startsWith(root.path + File.separator) && out.path != root.path) {
                throw IOException("Project archive entry escapes target dir: ${entry.name}")
            }
            if (entry.isDirectory) {
                if (!out.exists() && !out.mkdirs()) {
                    throw IOException("Failed to create archive directory: ${out.path}")
                }
                return
            }
            out.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Failed to create archive parent directory: ${parent.path}")
                }
            }
            FileOutputStream(out, false).use { output ->
                copyStreamWithCancel(zip, output, cancelSignal)
                output.flush()
            }
        }

        private fun resolveInside(root: File, relativePath: String): File {
            val normalized = relativePath.replace('\\', '/')
            if (normalized.isBlank() || normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
                throw IOException("Unsafe project source path: $relativePath")
            }
            val canonicalRoot = root.canonicalFile
            val resolved = File(canonicalRoot, normalized).canonicalFile
            if (!resolved.path.startsWith(canonicalRoot.path + File.separator) && resolved.path != canonicalRoot.path) {
                throw IOException("Project source path escapes workspace: $relativePath")
            }
            return resolved
        }

        private fun sha256(
            file: File,
            cancelSignal: AtomicBoolean?,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    ensureActive(cancelSignal)
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun copyStreamWithCancel(
            input: InputStream,
            output: OutputStream,
            cancelSignal: AtomicBoolean?,
        ) {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                ensureActive(cancelSignal)
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                output.write(buffer, 0, read)
            }
        }

        private fun ensureActive(cancelSignal: AtomicBoolean?) {
            if (cancelSignal?.get() == true || Thread.currentThread().isInterrupted) {
                throw CancellationException("Remote APK build session was cancelled.")
            }
        }
    }
}
