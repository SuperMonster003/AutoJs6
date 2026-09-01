package org.autojs.autojs.apkbuilder.template

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Performs bounded, non-extracting structural validation of an APK received from a plugin.
 * Cryptographic verification and manifest identity checks are deliberately handled separately.
 */
internal object RemoteBuildApkArchivePolicy {

    private const val MAX_ENTRY_COUNT = 65_536
    private val WINDOWS_DRIVE_ABSOLUTE = Regex("^[A-Za-z]:($|/|\\\\)")
    private val REQUIRED_ENTRIES = linkedSetOf(
        "AndroidManifest.xml",
        "resources.arsc",
        "classes.dex",
        "assets/project/project.json",
    )

    @Throws(IOException::class)
    fun validate(apkFile: File) {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            throw IOException("Remote output APK is missing or empty.")
        }

        try {
            ZipFile(apkFile).use { archive ->
                val seenNames = HashSet<String>()
                val requiredEntries = REQUIRED_ENTRIES.toMutableSet()
                val entries = archive.entries()
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw ArchivePolicyException(
                            "Remote output APK contains too many archive entries."
                        )
                    }

                    val name = entry.name
                    validateEntryName(name, entry.isDirectory)
                    if (!seenNames.add(name)) {
                        throw ArchivePolicyException(
                            "Remote output APK contains duplicate archive entries."
                        )
                    }
                    if (name in requiredEntries) {
                        if (entry.isDirectory || entry.size <= 0L) {
                            throw ArchivePolicyException(
                                "Remote output APK has an invalid required package component."
                            )
                        }
                        requiredEntries.remove(name)
                    }
                }

                if (requiredEntries.isNotEmpty()) {
                    throw ArchivePolicyException(
                        "Remote output APK is missing required package components."
                    )
                }
            }
        } catch (error: ArchivePolicyException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("Remote output APK is not a readable ZIP archive.", error)
        }
    }

    private fun validateEntryName(name: String, isDirectory: Boolean) {
        if (name.isEmpty() || name.any { character -> character.code < 0x20 || character.code == 0x7f }) {
            throw ArchivePolicyException("Remote output APK contains an invalid archive path.")
        }
        if (
            name.startsWith('/') ||
            name.startsWith('\\') ||
            WINDOWS_DRIVE_ABSOLUTE.containsMatchIn(name) ||
            '\\' in name
        ) {
            throw ArchivePolicyException("Remote output APK contains an absolute or non-portable archive path.")
        }

        val normalizedCandidate = if (isDirectory && name.endsWith('/')) {
            name.dropLast(1)
        } else {
            name
        }
        if (normalizedCandidate.isEmpty() || normalizedCandidate.endsWith('/')) {
            throw ArchivePolicyException("Remote output APK contains an invalid archive path.")
        }
        val segments = normalizedCandidate.split('/')
        if (segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
            throw ArchivePolicyException("Remote output APK contains an unsafe archive path.")
        }
    }

    private class ArchivePolicyException(message: String) : IOException(message)
}
