package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RemoteBuildApkArchivePolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validate_acceptsExpectedRemoteBuildShape() {
        val archive = createArchive("valid.apk", requiredEntries())

        RemoteBuildApkArchivePolicy.validate(archive)
    }

    @Test
    fun validate_rejectsEachMissingRequiredComponent() {
        requiredEntryNames.forEachIndexed { index, missingName ->
            val entries = requiredEntries().filterKeys { name -> name != missingName }
            val archive = createArchive("missing-$index.apk", entries)

            assertThrows(IOException::class.java) {
                RemoteBuildApkArchivePolicy.validate(archive)
            }
        }
    }

    @Test
    fun validate_rejectsEmptyRequiredComponent() {
        val entries = requiredEntries().toMutableMap().apply {
            this["classes.dex"] = byteArrayOf()
        }
        val archive = createArchive("empty-required.apk", entries)

        assertThrows(IOException::class.java) {
            RemoteBuildApkArchivePolicy.validate(archive)
        }
    }

    @Test
    fun validate_rejectsUnsafeAndNonPortablePaths() {
        val unsafeNames = listOf(
            "../outside",
            "assets/../outside",
            "/absolute",
            "C:/absolute",
            "assets\\windows-path",
            "assets//empty-segment",
            "assets/./dot-segment",
        )
        unsafeNames.forEachIndexed { index, unsafeName ->
            val entries = requiredEntries().toMutableMap().apply {
                this[unsafeName] = byteArrayOf(1)
            }
            val archive = createArchive("unsafe-$index.apk", entries)

            assertThrows(IOException::class.java) {
                RemoteBuildApkArchivePolicy.validate(archive)
            }
        }
    }

    @Test
    fun validate_rejectsUnreadableArchive() {
        val archive = temporaryFolder.newFile("not-a-zip.apk").apply {
            writeText("not an apk")
        }

        assertThrows(IOException::class.java) {
            RemoteBuildApkArchivePolicy.validate(archive)
        }
    }

    private fun requiredEntries(): LinkedHashMap<String, ByteArray> = linkedMapOf(
        "AndroidManifest.xml" to byteArrayOf(1),
        "resources.arsc" to byteArrayOf(2),
        "classes.dex" to byteArrayOf(3),
        "assets/project/project.json" to "{}".toByteArray(),
        "assets/project/main.js" to "0".toByteArray(),
    )

    private val requiredEntryNames = listOf(
        "AndroidManifest.xml",
        "resources.arsc",
        "classes.dex",
        "assets/project/project.json",
    )

    private fun createArchive(name: String, entries: Map<String, ByteArray>): File {
        val archive = temporaryFolder.newFile(name)
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (entryName, bytes) ->
                output.putNextEntry(ZipEntry(entryName))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return archive
    }
}
