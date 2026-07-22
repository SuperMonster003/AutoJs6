package org.autojs.autojs.core.plugin.imagequant

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object ImageQuantNativeLibraryStager {

    private const val MAX_NATIVE_LIBRARY_BYTES = 16L * 1024L * 1024L
    private const val ELF_HEADER_BYTES = 20
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
    ): StagedLibrary {
        require(apkPaths.isNotEmpty()) { "Image Quantization plugin has no APK code paths" }
        require(supportedAbis.isNotEmpty()) { "Current process does not report a supported ABI" }
        require(nativeLibraryFileName.matches(Regex("lib[A-Za-z0-9_.+-]+\\.so"))) {
            "Unsafe Image Quantization native library name: $nativeLibraryFileName"
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
                        "Unable to create Image Quantization runtime directory: ${destinationDir.path}"
                    }
                    val destination = File(destinationDir, nativeLibraryFileName)
                    if (!isReusable(destination, entry, abi)) {
                        extractAtomically(zip, entry, destination, abi)
                    }
                    destination.setWritable(false, false)
                    pruneObsoleteRuntimes(destinationRoot, safeIdentity)
                    return StagedLibrary(destination, abi, apkFile.absolutePath, entryName)
                }
            }
        }
        throw ImageQuantPluginLoadException(
            ImageQuantPluginLoadError.ABI_NOT_SUPPORTED,
            "Image Quantization plugin does not contain $nativeLibraryFileName for any process ABI: " +
                    supportedAbis.joinToString(),
        )
    }

    private fun validateEntry(entry: ZipEntry, entryName: String) {
        val size = entry.size
        if (size < ELF_HEADER_BYTES || size > MAX_NATIVE_LIBRARY_BYTES) {
            throw ImageQuantPluginLoadException(
                ImageQuantPluginLoadError.NATIVE_LIBRARY_INVALID,
                "Invalid Image Quantization native library size ($size bytes): $entryName",
            )
        }
    }

    private fun isReusable(file: File, entry: ZipEntry, abi: String): Boolean {
        if (!file.isFile || file.length() != entry.size || !hasExpectedElfAbi(file, abi)) return false
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
        abi: String,
    ) {
        val tempFile = File.createTempFile("${destination.name}.", ".tmp", destination.parentFile)
        try {
            val crc = CRC32()
            var copied = 0L
            zip.getInputStream(entry).buffered().use { input ->
                FileOutputStream(tempFile, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_NATIVE_LIBRARY_BYTES) {
                            throw ImageQuantPluginLoadException(
                                ImageQuantPluginLoadError.NATIVE_LIBRARY_INVALID,
                                "Image Quantization native library exceeds the extraction limit",
                            )
                        }
                        crc.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (copied != entry.size || (entry.crc >= 0L && crc.value != entry.crc)) {
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.NATIVE_LIBRARY_INVALID,
                    "Image Quantization native library failed size or CRC validation",
                )
            }
            if (!hasExpectedElfAbi(tempFile, abi)) {
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.NATIVE_LIBRARY_INVALID,
                    "Image Quantization plugin payload is not an ELF library for $abi",
                )
            }
            tempFile.setReadable(true, true)
            tempFile.setExecutable(true, true)
            tempFile.setWritable(false, false)
            if (destination.exists()) {
                destination.setWritable(true, true)
                if (!destination.delete()) {
                    throw ImageQuantPluginLoadException(
                        ImageQuantPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
                        "Unable to replace staged Image Quantization runtime: ${destination.path}",
                    )
                }
            }
            if (!tempFile.renameTo(destination)) {
                throw ImageQuantPluginLoadException(
                    ImageQuantPluginLoadError.NATIVE_LIBRARY_EXTRACTION_FAILED,
                    "Unable to publish staged Image Quantization runtime: ${destination.path}",
                )
            }
            destination.setWritable(false, false)
        } finally {
            if (tempFile.exists()) {
                tempFile.setWritable(true, true)
                tempFile.delete()
            }
        }
    }

    private fun hasExpectedElfAbi(file: File, abi: String): Boolean = runCatching {
        file.inputStream().buffered().use { input ->
            val header = ByteArray(ELF_HEADER_BYTES)
            if (input.read(header) != header.size || !header.copyOfRange(0, elfMagic.size).contentEquals(elfMagic)) {
                return@use false
            }
            val expected = expectedElfIdentity(abi) ?: return@use false
            val elfClass = header[4].toInt() and 0xff
            val dataEncoding = header[5].toInt() and 0xff
            val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
            dataEncoding == 1 && elfClass == expected.first && machine == expected.second
        }
    }.getOrDefault(false)

    private fun expectedElfIdentity(abi: String): Pair<Int, Int>? = when (abi) {
        "armeabi-v7a" -> 1 to 40
        "arm64-v8a" -> 2 to 183
        "x86" -> 1 to 3
        "x86_64" -> 2 to 62
        else -> null
    }

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
