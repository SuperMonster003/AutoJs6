package org.autojs.autojs.external.shortcut

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ShortcutBackupArchiveTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jX1sAAAAASUVORK5CYII=")
    private fun entry(id: String = "copy-1") = ShortcutBackupEntry(
        id, "脚本", "Script label", "script", "/sdcard/scripts/a.py", "python_file_v1", null,
        png, ShortcutBackupEntry.Status.PINNED,
    )
    private fun encode(state: ShortcutBackupState): ByteArray = ByteArrayOutputStream().also { ShortcutBackupArchive.write(state, it) }.toByteArray()
    private fun decode(bytes: ByteArray) = ShortcutBackupArchive.read(ByteArrayInputStream(bytes))

    private fun rewrite(bytes: ByteArray, transform: (String, ByteArray) -> Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { source ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    val (name, data) = transform(entry.name, source.readBytes())
                    target.putNextEntry(ZipEntry(name))
                    target.write(data)
                    target.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    @Test fun roundTripPreservesSeparateIdsNamesIconsAndPythonContext() {
        val state = ShortcutBackupState(listOf(entry(), entry("copy-2")), "generation", "home", "activity")
        val decoded = decode(encode(state))
        assertEquals(state.markerToken, decoded.markerToken)
        assertEquals(2, decoded.entries.size)
        state.entries.zip(decoded.entries).forEach { (before, after) ->
            assertTrue(before.sameDefinition(after))
            assertEquals(before.status, after.status)
        }
    }

    @Test fun writerDoesNotCloseAtomicFileStream() {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        ShortcutBackupArchive.write(ShortcutBackupState(listOf(entry())), output)
        assertFalse(closed)
        assertEquals(1, decode(output.toByteArray()).entries.size)
    }

    @Test fun rejectsPathTraversalAndUnexpectedEntries() {
        val archive = encode(ShortcutBackupState(listOf(entry())))
        val changed = rewrite(archive) { name, bytes -> (if (name.startsWith("icons/")) "../icon.png" else name) to bytes }
        assertThrows(IllegalArgumentException::class.java) { decode(changed) }
    }

    @Test fun rejectsUnsupportedVersion() {
        val archive = encode(ShortcutBackupState(listOf(entry())))
        val changed = rewrite(archive) { name, bytes ->
            name to if (name == "manifest.json") bytes.toString(Charsets.UTF_8).replace("\"version\":1", "\"version\":99").toByteArray() else bytes
        }
        assertThrows(IllegalArgumentException::class.java) { decode(changed) }
    }

    @Test fun rejectsDuplicateIdsWithoutCollapsingSameScriptCopies() {
        assertThrows(IllegalArgumentException::class.java) { encode(ShortcutBackupState(listOf(entry(), entry()))) }
        val archive = encode(ShortcutBackupState(listOf(entry(), entry("copy-2"))))
        val changed = rewrite(archive) { name, bytes ->
            name to if (name == "manifest.json") bytes.toString(Charsets.UTF_8).replace("copy-2", "copy-1").toByteArray() else bytes
        }
        assertThrows(IllegalArgumentException::class.java) { decode(changed) }
    }

    @Test fun rejectsMissingIconBeforeImport() {
        val archive = encode(ShortcutBackupState(listOf(entry())))
        val changed = rewrite(archive) { name, bytes -> (if (name.startsWith("icons/")) "icons/99.png" else name) to bytes }
        assertThrows(IllegalArgumentException::class.java) { decode(changed) }
    }

    @Test fun rejectsCompressedOversizedIconsByInflatedSize() {
        val archive = encode(ShortcutBackupState(listOf(entry())))
        val changed = rewrite(archive) { name, bytes -> name to if (name.startsWith("icons/")) ByteArray(1024 * 1024 + 1) else bytes }
        assertThrows(IllegalArgumentException::class.java) { decode(changed) }
    }

    @Test fun rejectsForeignTargetsAndNonFileScriptPaths() {
        assertThrows(IllegalArgumentException::class.java) { encode(ShortcutBackupState(listOf(entry().copy(target = "arbitrary.component")))) }
        assertThrows(IllegalArgumentException::class.java) { encode(ShortcutBackupState(listOf(entry().copy(path = "content://example/script")))) }
        assertThrows(IllegalArgumentException::class.java) { encode(ShortcutBackupState(listOf(entry().copy(target = "settings")))) }
    }

    @Test fun rejectsIconWithHugeDeclaredDimensions() {
        val icon = png.clone().also { it[16] = 127 }
        assertThrows(IllegalArgumentException::class.java) { encode(ShortcutBackupState(listOf(entry().copy(icon = icon)))) }
    }
}
