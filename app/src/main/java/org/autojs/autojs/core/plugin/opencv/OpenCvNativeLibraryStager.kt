package org.autojs.autojs.core.plugin.opencv

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object OpenCvNativeLibraryStager {

    private const val MAX_NATIVE_LIBRARY_BYTES = 128L * 1024L * 1024L
    private val nativeEntryRoots = listOf("lib", "libs")
    private val elfMagic = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())

    data class StagedLibrary(
        val file: File,
        val abi: String,
        val apkPath: String,
        val entryName: String,
    )

    fun hasCompatibleLibrary(
        apkPaths: List<String>,
        supportedAbis: List<String>,
        nativeLibraryFileName: String,
    ): Boolean = supportedAbis.any { abi ->
        apkPaths.any { apkPath ->
            runCatching {
                ZipFile(apkPath).use { zip ->
                    nativeEntryRoots.any { root ->
                        zip.getEntry("$root/$abi/$nativeLibraryFileName")?.isDirectory == false
                    }
                }
            }.getOrDefault(false)
        }
    }

    fun stage(
        apkPaths: List<String>,
        destinationRoot: File,
        packageIdentity: String,
        supportedAbis: List<String>,
        nativeLibraryFileName: String,
        makeReadOnly: (File) -> Boolean = File::setReadOnly,
    ): StagedLibrary {
        require(apkPaths.isNotEmpty()) { "OpenCV plugin has no APK code paths" }
        require(supportedAbis.isNotEmpty()) { "Current process does not report a supported ABI" }
        require(nativeLibraryFileName.matches(Regex("lib[A-Za-z0-9_.+-]+\\.so"))) {
            "Unsafe OpenCV native library name: $nativeLibraryFileName"
        }

        val safeIdentity = packageIdentity.replace(Regex("[^A-Za-z0-9._-]"), "_")
        supportedAbis.forEach { abi ->
            require(abi.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe ABI name: $abi" }
            apkPaths.forEach { apkPath ->
                val apkFile = File(apkPath)
                if (!apkFile.isFile) return@forEach
                ZipFile(apkFile).use { zip ->
                    val match = nativeEntryRoots.firstNotNullOfOrNull { root ->
                        val entryName = "$root/$abi/$nativeLibraryFileName"
                        zip.getEntry(entryName)?.takeUnless(ZipEntry::isDirectory)?.let { entryName to it }
                    } ?: return@use

                    val (entryName, entry) = match
                    validateEntry(entry, entryName)
                    val destinationDir = File(destinationRoot, "$safeIdentity/$abi")
                    check(destinationDir.isDirectory || destinationDir.mkdirs()) {
                        "Unable to create OpenCV runtime directory: ${destinationDir.path}"
                    }
                    val destination = File(destinationDir, nativeLibraryFileName)
                    if (!isReusable(destination, entry)) {
                        extractAtomically(zip, entry, destination, makeReadOnly)
                    }
                    ensureReadOnly(destination, makeReadOnly)
                    pruneObsoleteRuntimes(destinationRoot, safeIdentity)
                    return StagedLibrary(destination, abi, apkFile.absolutePath, entryName)
                }
            }
        }
        throw OpenCvPluginLoadException(
            OpenCvPluginLoadError.ABI_NOT_SUPPORTED,
            "OpenCV plugin does not contain $nativeLibraryFileName for any process ABI: ${supportedAbis.joinToString()}",
        )
    }

    private fun ensureReadOnly(file: File, makeReadOnly: (File) -> Boolean) {
        if (!makeReadOnly(file) || file.canWrite()) {
            throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
                "Unable to make staged OpenCV runtime read-only: ${file.path}",
            )
        }
    }

    private fun validateEntry(entry: ZipEntry, entryName: String) {
        val size = entry.size
        if (size <= 0L || size > MAX_NATIVE_LIBRARY_BYTES) {
            throw OpenCvPluginLoadException(
                OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID,
                "Invalid OpenCV native library size ($size bytes): $entryName",
            )
        }
    }

    private fun isReusable(file: File, entry: ZipEntry): Boolean {
        if (!file.isFile || file.length() != entry.size || !hasElfMagic(file)) return false
        if (entry.crc < 0L) return true
        return file.inputStream().buffered().use { input ->
            val crc = CRC32()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                crc.update(buffer, 0, count)
            }
            crc.value == entry.crc
        }
    }

    private fun extractAtomically(
        zip: ZipFile,
        entry: ZipEntry,
        destination: File,
        makeReadOnly: (File) -> Boolean,
    ) {
        val tempFile = File.createTempFile("${destination.name}.", ".tmp", destination.parentFile)
        try {
            val crc = CRC32()
            var copied = 0L
            val prefix = ByteArray(elfMagic.size)
            var prefixCount = 0
            zip.getInputStream(entry).buffered().use { input ->
                FileOutputStream(tempFile, false).use { output ->
                    // Keep only this open descriptor writable until the verified copy is published.
                    // zh-CN: 路径先设为只读, 仅保留当前已打开的描述符写入, 校验后再发布.
                    ensureReadOnly(tempFile, makeReadOnly)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_NATIVE_LIBRARY_BYTES) {
                            throw OpenCvPluginLoadException(
                                OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID,
                                "OpenCV native library exceeds the extraction limit",
                            )
                        }
                        if (prefixCount < prefix.size) {
                            val prefixBytes = minOf(count, prefix.size - prefixCount)
                            buffer.copyInto(prefix, prefixCount, 0, prefixBytes)
                            prefixCount += prefixBytes
                        }
                        crc.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (copied != entry.size || (entry.crc >= 0L && crc.value != entry.crc)) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID,
                    "OpenCV native library failed size or CRC validation",
                )
            }
            if (!prefix.contentEquals(elfMagic)) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID,
                    "OpenCV plugin payload is not an ELF native library",
                )
            }
            tempFile.setReadable(true, true)
            tempFile.setExecutable(true, true)
            if (destination.exists()) {
                destination.setWritable(true, true)
                if (!destination.delete()) {
                    throw OpenCvPluginLoadException(
                        OpenCvPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
                        "Unable to replace staged OpenCV runtime: ${destination.path}",
                    )
                }
            }
            if (!tempFile.renameTo(destination)) {
                throw OpenCvPluginLoadException(
                    OpenCvPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
                    "Unable to publish staged OpenCV runtime: ${destination.path}",
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.setWritable(true, true)
                tempFile.delete()
            }
        }
    }

    private fun hasElfMagic(file: File): Boolean = runCatching {
        file.inputStream().buffered().use { input ->
            val actual = ByteArray(elfMagic.size)
            input.read(actual) == actual.size && actual.contentEquals(elfMagic)
        }
    }.getOrDefault(false)

    private fun pruneObsoleteRuntimes(destinationRoot: File, activeIdentity: String) {
        val canonicalRoot = runCatching { destinationRoot.canonicalFile }.getOrNull() ?: return
        canonicalRoot.listFiles()?.forEach { candidate ->
            if (!candidate.isDirectory || candidate.name == activeIdentity) return@forEach
            val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
            if (canonicalCandidate.parentFile != canonicalRoot) return@forEach
            canonicalCandidate.walkBottomUp().forEach { file -> file.setWritable(true, true) }
            canonicalCandidate.deleteRecursively()
        }
    }
}
