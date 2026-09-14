package org.autojs.autojs.core.terminal

import org.autojs.autojs.core.terminal.NodeCliMetadata.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeCliMetadataTest {

    private val sha = "0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun valid(): MutableMap<String, Any?> = mutableMapOf(
        NodeCliMetadata.KEY_SCHEMA to 1, // aapt stores "1" as an integer
        NodeCliMetadata.KEY_EXECUTABLE to "libnodexe.so",
        NodeCliMetadata.KEY_COMMANDS to "node, npm,npx ,corepack,yarn,pnpm",
        NodeCliMetadata.KEY_ARCHIVE to "nodejs/cli/node-cli-24.21.0.bin",
        NodeCliMetadata.KEY_ARCHIVE_SHA256 to sha,
        NodeCliMetadata.KEY_ARCHIVE_ROOT to "lib/node_modules/",
        NodeCliMetadata.KEY_ARCHIVE_ENTRY_COUNT to 1699,
        NodeCliMetadata.KEY_ARCHIVE_BYTES to 10068758,
        NodeCliMetadata.KEY_NPM_VERSION to "11.6.0",
        NodeCliMetadata.KEY_COREPACK_VERSION to "0.34.0",
    )

    @Test
    fun parsesAWellFormedDeclaration() {
        val result = NodeCliMetadata.parse(valid()) as ParseResult.Valid
        val descriptor = result.descriptor
        assertEquals("libnodexe.so", descriptor.executableName)
        assertEquals(listOf("node", "npm", "npx", "corepack", "yarn", "pnpm"), descriptor.commands)
        assertEquals("nodejs/cli/node-cli-24.21.0.bin", descriptor.archivePath)
        assertEquals(sha.lowercase(), descriptor.archiveSha256)
        assertEquals("lib/node_modules", descriptor.archiveRoot)
        assertEquals("11.6.0", descriptor.npmVersion)
        assertEquals("0.34.0", descriptor.corepackVersion)
    }

    @Test
    fun declaredLimitsAreParsedAndOptional() {
        val descriptor = (NodeCliMetadata.parse(valid()) as ParseResult.Valid).descriptor
        assertEquals(1699, descriptor.archiveEntryCount)
        assertEquals(10068758L, descriptor.archiveBytes)
        val without = (NodeCliMetadata.parse(valid().apply {
            remove(NodeCliMetadata.KEY_ARCHIVE_ENTRY_COUNT)
            put(NodeCliMetadata.KEY_ARCHIVE_BYTES, "not a number")
        }) as ParseResult.Valid).descriptor
        assertNull(without.archiveEntryCount)
        assertNull(without.archiveBytes)
    }

    @Test
    fun keysMirrorThePluginContract() {
        assertEquals("org.autojs.plugin.nodejs.NODE_CLI_SCHEMA", NodeCliMetadata.KEY_SCHEMA)
        assertEquals("org.autojs.plugin.nodejs.NODE_CLI_ARCHIVE_BYTES", NodeCliMetadata.KEY_ARCHIVE_BYTES)
        assertEquals("1", NodeCliMetadata.SUPPORTED_SCHEMA)
    }

    @Test
    fun archiveRootDefaultsAndVersionsAreOptional() {
        val meta = valid().apply {
            remove(NodeCliMetadata.KEY_ARCHIVE_ROOT)
            remove(NodeCliMetadata.KEY_NPM_VERSION)
            remove(NodeCliMetadata.KEY_COREPACK_VERSION)
        }
        val descriptor = (NodeCliMetadata.parse(meta) as ParseResult.Valid).descriptor
        assertEquals(NodeCliMetadata.DEFAULT_ARCHIVE_ROOT, descriptor.archiveRoot)
        assertNull(descriptor.npmVersion)
        assertNull(descriptor.corepackVersion)
    }

    @Test
    fun pluginsWithoutTheContractAreReportedAsMissing() {
        assertTrue(NodeCliMetadata.parse(null) is ParseResult.Missing)
        assertTrue(NodeCliMetadata.parse(emptyMap()) is ParseResult.Missing)
        assertTrue(NodeCliMetadata.parse(mapOf("org.autojs.plugin.REQUIRES_HOST_VERSION" to 5280)) is ParseResult.Missing)
        val unsupported = NodeCliMetadata.parse(valid().apply { put(NodeCliMetadata.KEY_SCHEMA, "2") }) as ParseResult.Missing
        assertTrue(unsupported.reason.contains("schema"))
    }

    @Test
    fun malformedFieldsAreRejected() {
        fun missing(mutate: MutableMap<String, Any?>.() -> Unit): ParseResult.Missing =
            NodeCliMetadata.parse(valid().apply(mutate)) as ParseResult.Missing

        assertTrue(missing { put(NodeCliMetadata.KEY_EXECUTABLE, "nodexe") }.reason.contains("executable"))
        assertTrue(missing { put(NodeCliMetadata.KEY_EXECUTABLE, "../libnodexe.so") }.reason.contains("executable"))
        assertTrue(missing { put(NodeCliMetadata.KEY_COMMANDS, " , ") }.reason.contains("command"))
        assertTrue(missing { put(NodeCliMetadata.KEY_COMMANDS, "node,np m") }.reason.contains("command"))
        assertTrue(missing { put(NodeCliMetadata.KEY_ARCHIVE, "/abs/path.bin") }.reason.contains("archive path"))
        assertTrue(missing { put(NodeCliMetadata.KEY_ARCHIVE, "nodejs/../x.bin") }.reason.contains("archive path"))
        assertTrue(missing { put(NodeCliMetadata.KEY_ARCHIVE_SHA256, "abc") }.reason.contains("digest"))
        assertTrue(missing { remove(NodeCliMetadata.KEY_ARCHIVE_SHA256) }.reason.contains("digest"))
        assertTrue(missing { put(NodeCliMetadata.KEY_ARCHIVE_ROOT, "../lib") }.reason.contains("archive root"))
    }

    @Test
    fun isElfChecksTheMagicOnly() {
        val dir = Files.createTempDirectory("elf").toFile()
        try {
            val elf = File(dir, "libnodexe.so").apply {
                writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1, 1, 0))
            }
            val script = File(dir, "libnpm.so").apply { writeText("#!/system/bin/sh\nexec node\n") }
            val short = File(dir, "short.so").apply { writeBytes(byteArrayOf(0x7f, 'E'.code.toByte())) }
            assertTrue(NodeCliMetadata.isElf(elf))
            assertFalse(NodeCliMetadata.isElf(script))
            assertFalse(NodeCliMetadata.isElf(short))
            assertFalse(NodeCliMetadata.isElf(File(dir, "missing.so")))
            assertFalse(NodeCliMetadata.isElf(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

}
