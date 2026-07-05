package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.autojs.apkbuilder.keystore.AESUtils
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildRequest
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplateProtocol
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RemoteApkBuildRequestPreparer {

    private const val ARCHIVE_FORMAT_VERSION = 1
    private const val SOURCE_ROOT_ENTRY = "project"
    private const val REMOTE_ICON_ENTRY = "__autojs6_remote__/icon.png"
    private const val BUFFER_SIZE = 256 * 1024

    data class Prepared(
        val request: ApkBuildRequest,
        val archiveFile: File,
    ) : Closeable {
        override fun close() {
            runCatching { request.projectArchiveFd?.close() }
            runCatching { request.keyStoreFd?.close() }
        }

        fun cleanup() {
            close()
            if (archiveFile.exists()) {
                archiveFile.delete()
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun prepare(
        context: Context,
        projectConfig: ProjectConfig,
        outputFileName: String,
    ): Prepared {
        val appContext = context.applicationContext
        val source = File(projectConfig.sourcePath ?: throw IOException("Project source path is not set."))
        if (!source.exists()) {
            throw IOException("Project source path does not exist: ${source.path}")
        }

        val cacheDir = File(appContext.cacheDir, "apk-remote-build-input").apply {
            mkdirs()
            listFiles()?.forEach { PFiles.deleteRecursively(it) }
        }
        val archiveFile = File(cacheDir, "project-${System.nanoTime()}.zip")
        val remoteConfig = cloneForRemoteRequest(projectConfig)
        val sourcePathInArchive = writeProjectArchive(archiveFile, source, projectConfig, remoteConfig)

        val keyStore = projectConfig.keyStore
        val keyStoreFile = keyStore?.absolutePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        if (keyStoreFile != null && !keyStoreFile.isFile) {
            throw IOException("KeyStore file does not exist: ${keyStoreFile.path}")
        }

        val request = ApkBuildRequest(
            hostPackageName = appContext.packageName,
            hostVersionName = BuildConfig.VERSION_NAME,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requiredProtocolVersion = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
            projectArchiveFd = ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY),
            projectArchiveSizeBytes = archiveFile.length(),
            projectArchiveSha256 = sha256(archiveFile),
            projectConfigJson = remoteConfig.toJson(false),
            keyStoreFd = keyStoreFile?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) },
            keyStoreSizeBytes = keyStoreFile?.length() ?: 0L,
            keyStoreSha256 = keyStoreFile?.let(::sha256),
            keyStorePassword = keyStore?.password?.takeIf { it.isNotBlank() }?.let(AESUtils::decrypt),
            keyAlias = keyStore?.alias,
            keyAliasPassword = keyStore?.aliasPassword?.takeIf { it.isNotBlank() }?.let(AESUtils::decrypt),
            outputFileName = outputFileName,
            allowRiskyBuild = true,
            extras = Bundle().apply {
                putInt(ApkBuildRequestExtraKeys.ARCHIVE_FORMAT_VERSION, ARCHIVE_FORMAT_VERSION)
                putString(
                    ApkBuildRequestExtraKeys.SOURCE_KIND,
                    if (source.isDirectory) {
                        ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY
                    } else {
                        ApkBuildRequestExtraKeys.SOURCE_KIND_FILE
                    },
                )
                putString(ApkBuildRequestExtraKeys.SOURCE_ROOT_PATH, SOURCE_ROOT_ENTRY)
                putString(ApkBuildRequestExtraKeys.SOURCE_PATH, sourcePathInArchive)
                if (remoteConfig.iconPath == REMOTE_ICON_ENTRY) {
                    putString(ApkBuildRequestExtraKeys.ICON_PATH, REMOTE_ICON_ENTRY)
                }
                putString(ApkBuildRequestExtraKeys.HOST_OUTPUT_FILE_NAME, outputFileName)
            },
        )

        return Prepared(request, archiveFile)
    }

    private fun cloneForRemoteRequest(projectConfig: ProjectConfig): ProjectConfig {
        val json = projectConfig.toJson(false)
        return ProjectConfig.fromJson(json)
            ?.setIconPath(null)
            ?: throw IOException("Failed to clone project config for remote build request.")
    }

    private fun writeProjectArchive(
        archiveFile: File,
        source: File,
        projectConfig: ProjectConfig,
        remoteConfig: ProjectConfig,
    ): String {
        var sourcePathInArchive = SOURCE_ROOT_ENTRY
        FileOutputStream(archiveFile, false).use { fileOutput ->
            ZipOutputStream(fileOutput.buffered(BUFFER_SIZE)).use { zip ->
                if (source.isDirectory) {
                    addDirectoryTree(
                        zip = zip,
                        root = source.canonicalFile,
                        entryRoot = SOURCE_ROOT_ENTRY,
                        excludedDirs = projectConfig.excludedDirs.mapNotNull { runCatching { it.canonicalFile }.getOrNull() },
                    )
                } else {
                    sourcePathInArchive = "$SOURCE_ROOT_ENTRY/${sanitizeEntrySegment(source.name)}"
                    addFile(zip, source, sourcePathInArchive)
                }
                if (writeIconEntryIfAvailable(zip, projectConfig)) {
                    remoteConfig.setIconPath(REMOTE_ICON_ENTRY)
                }
            }
            fileOutput.fd.sync()
        }
        return sourcePathInArchive
    }

    private fun addDirectoryTree(
        zip: ZipOutputStream,
        root: File,
        entryRoot: String,
        excludedDirs: List<File>,
    ) {
        addDirectoryEntry(zip, "$entryRoot/")
        root.walkTopDown()
            .onEnter { dir -> !isExcluded(dir, excludedDirs) }
            .filter { it != root }
            .filterNot { isExcluded(it, excludedDirs) }
            .forEach { file ->
                val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
                val entryName = "$entryRoot/${sanitizeEntryPath(relative)}"
                if (file.isDirectory) {
                    addDirectoryEntry(zip, "$entryName/")
                } else if (file.isFile) {
                    addFile(zip, file, entryName)
                }
            }
    }

    private fun writeIconEntryIfAvailable(
        zip: ZipOutputStream,
        projectConfig: ProjectConfig,
    ): Boolean {
        val bitmap = projectConfig.iconBitmapGetter?.call() ?: return false
        zip.putNextEntry(ZipEntry(REMOTE_ICON_ENTRY))
        val written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
        zip.closeEntry()
        if (!written) {
            throw IOException("Failed to encode project icon for remote build request.")
        }
        return true
    }

    private fun addDirectoryEntry(zip: ZipOutputStream, entryName: String) {
        val safeName = sanitizeEntryPath(entryName).let { if (it.endsWith('/')) it else "$it/" }
        zip.putNextEntry(ZipEntry(safeName))
        zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(sanitizeEntryPath(entryName)))
        FileInputStream(file).use { input ->
            input.copyTo(zip, BUFFER_SIZE)
        }
        zip.closeEntry()
    }

    private fun isExcluded(file: File, excludedDirs: List<File>): Boolean {
        if (!file.isDirectory || excludedDirs.isEmpty()) {
            return false
        }
        val path = runCatching { file.canonicalFile.path }.getOrDefault(file.absolutePath)
        return excludedDirs.any { excluded ->
            val excludedPath = excluded.path
            path == excludedPath || path.startsWith("$excludedPath${File.separator}")
        }
    }

    private fun sanitizeEntrySegment(value: String): String {
        return value.replace('\\', '/')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: throw IOException("Invalid archive entry name: $value")
    }

    private fun sanitizeEntryPath(value: String): String {
        val normalized = value.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        require(normalized.none { it == ".." }) {
            "Invalid archive entry path: $value"
        }
        return normalized.joinToString("/")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
