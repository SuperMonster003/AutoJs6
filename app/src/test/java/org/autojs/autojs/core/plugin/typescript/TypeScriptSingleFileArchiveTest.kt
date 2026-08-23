package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptSingleFileArchiveTest {

    @Test
    fun writesFormalStoredArchiveWithUtf8PathAndBoundAuthorities() {
        val archive = File.createTempFile("typescript-input-test-", ".zip")
        try {
            val source = "const 答案: number = 42;\ntoast(答案);\n"
                .toByteArray(StandardCharsets.UTF_8)
            val prepared = TypeScriptSingleFileArchive.write(
                sourceName = "你好.ts",
                sourceBytes = source,
                requestId = REQUEST_ID,
                projectId = PROJECT_ID,
                authority = authority(),
                destination = archive,
            )

            assertEquals("sources/你好.ts", prepared.entryPath)
            assertEquals(sha256(archive), prepared.inputArchiveSha256)
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(
                    listOf("request-manifest.json", "tsconfig.json", "sources/你好.ts"),
                    entries.map(ZipEntry::getName),
                )
                assertTrue(entries.all { it.method == ZipEntry.STORED })
                assertTrue(entries.all { it.extra == null || it.extra.isEmpty() })

                val manifestBytes = zip.getInputStream(entries.first()).use { it.readBytes() }
                assertEquals(sha256(manifestBytes), prepared.requestManifestSha256)
                val manifest = TypeScriptStrictJson.parseObject(manifestBytes)
                assertEquals(
                    TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA,
                    manifest.getValue("schema"),
                )
                assertEquals(
                    TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
                    manifest.int("revision"),
                )
                assertEquals(
                    TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
                    manifest.int("inputQuotaRevision"),
                )
                assertEquals("sources/你好.ts", manifest.getValue("entryPath"))
                assertEquals(
                    expectedInventoryFingerprint(
                        path = "sources/你好.ts",
                        source = source,
                    ),
                    manifest.getValue("callerInventoryFingerprint"),
                )
                assertEquals(
                    listOf("sources/你好.ts"),
                    (manifest.getValue("rootPaths") as List<*>),
                )
            }

            val localHeaders = readLocalHeaders(archive.readBytes())
            assertEquals(3, localHeaders.size)
            assertEquals(0, localHeaders[0].flags)
            assertEquals(0, localHeaders[1].flags)
            assertEquals(UTF8_FLAG, localHeaders[2].flags)
            assertEquals("sources/你好.ts", localHeaders[2].name)
            assertTrue(localHeaders.all { it.extraLength == 0 })
            assertTrue(localHeaders.all { it.dosTime == 0 && it.dosDate == 0 })
        } finally {
            archive.delete()
        }
    }

    @Test
    fun rejectsMalformedUtf8SourceBeforeWritingArchive() {
        val archive = File.createTempFile("typescript-input-test-", ".zip")
        try {
            val failure = runCatching {
                TypeScriptSingleFileArchive.write(
                    sourceName = "main.ts",
                    sourceBytes = byteArrayOf(0xc3.toByte(), 0x28),
                    requestId = REQUEST_ID,
                    projectId = PROJECT_ID,
                    authority = authority(),
                    destination = archive,
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun writesNodeRevisionTwoArchiveWithoutSyntheticTsConfig() {
        val archive = File.createTempFile("typescript-node-input-test-", ".zip")
        try {
            val source = "const answer: number = 42;\nexport { answer };\n"
                .toByteArray(StandardCharsets.UTF_8)
            val prepared = TypeScriptSingleFileArchive.write(
                sourceName = "入口.mts",
                sourceBytes = source,
                requestId = REQUEST_ID,
                projectId = PROJECT_ID,
                authority = nodeAuthority(),
                destination = archive,
            )

            val namespace = "p-" + sha256(
                PROJECT_ID.toByteArray(StandardCharsets.UTF_8),
            )
            assertEquals("sources/入口.mts", prepared.entryPath)
            assertEquals(
                "autojs://source/$namespace/sources/%E5%85%A5%E5%8F%A3.mts",
                prepared.entrySourceUri,
            )
            assertNull(prepared.configByteLength)
            assertNull(prepared.configSha256)
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(
                    listOf("request-manifest.json", "sources/入口.mts"),
                    entries.map(ZipEntry::getName),
                )
                val manifestBytes = zip.getInputStream(entries.first()).use { it.readBytes() }
                val manifest = TypeScriptStrictJson.parseObject(manifestBytes)
                assertEquals(TypeScriptCompilerContract.PROFILE_NODE, manifest["targetProfile"])
                assertEquals(TypeScriptR2Contract.CONFIG_KIND_NONE, manifest["configKind"])
                assertFalse(manifest.containsKey("config"))
                assertEquals(
                    expectedInventoryFingerprint(
                        path = "sources/入口.mts",
                        source = source,
                        includeConfig = false,
                    ),
                    manifest["callerInventoryFingerprint"],
                )
            }
        } finally {
            archive.delete()
        }
    }

    private fun authority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = "1".repeat(64),
        declarationsFingerprint = "2".repeat(64),
        declarationProfileFingerprint = "3".repeat(64),
        runtimeCapabilitiesFingerprint = "4".repeat(64),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 1,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = "5".repeat(64),
    )

    private fun nodeAuthority() = TypeScriptCompilerAuthority(
        targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = "1".repeat(64),
        declarationsFingerprint = "2".repeat(64),
        declarationProfileFingerprint = "3".repeat(64),
        runtimeCapabilitiesFingerprint = "4".repeat(64),
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = 2,
        configSchemaRevision = TypeScriptCompilerAuthority.CONFIG_NONE_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_NONE,
        packageMetadataSchemaRevision = 1,
        packageMetadataPolicyRevision = 1,
        packageMetadataPolicyFingerprint = PACKAGE_METADATA_POLICY_FINGERPRINT,
    )

    private fun expectedInventoryFingerprint(
        path: String,
        source: ByteArray,
        includeConfig: Boolean = true,
    ): String {
        val config = "{}".toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("autojs6.typescript.input-inventory.v1\n".toByteArray(StandardCharsets.US_ASCII))
        digest.updateRecord("source", "/" + path, source)
        if (includeConfig) digest.updateRecord("config", "tsconfig.json", config)
        return digest.digest().toLowerHex()
    }

    private fun MessageDigest.updateRecord(domain: String, path: String, bytes: ByteArray) {
        update(domain.toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(path.toByteArray(StandardCharsets.UTF_8))
        update(0)
        update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
        update(0)
        update(sha256(bytes).toByteArray(StandardCharsets.US_ASCII))
        update('\n'.code.toByte())
    }

    private fun Map<String, Any?>.int(key: String): Int =
        (getValue(key) as TypeScriptStrictJson.NumberToken).source.toInt()

    private fun readLocalHeaders(bytes: ByteArray): List<LocalHeader> {
        val result = mutableListOf<LocalHeader>()
        var offset = 0
        while (offset + 4 <= bytes.size && bytes.u32(offset) == LOCAL_HEADER_SIGNATURE) {
            val flags = bytes.u16(offset + 6)
            val dosTime = bytes.u16(offset + 10)
            val dosDate = bytes.u16(offset + 12)
            val payloadLength = bytes.u32(offset + 18).toInt()
            val nameLength = bytes.u16(offset + 26)
            val extraLength = bytes.u16(offset + 28)
            val nameOffset = offset + 30
            val name = bytes.copyOfRange(nameOffset, nameOffset + nameLength)
                .toString(StandardCharsets.UTF_8)
            result += LocalHeader(name, flags, dosTime, dosDate, extraLength)
            offset = nameOffset + nameLength + extraLength + payloadLength
        }
        assertFalse(result.isEmpty())
        return result
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

    private data class LocalHeader(
        val name: String,
        val flags: Int,
        val dosTime: Int,
        val dosDate: Int,
        val extraLength: Int,
    )

    private companion object {
        const val REQUEST_ID = "host-test-request"
        const val PROJECT_ID = "single-file-test-project"
        const val UTF8_FLAG = 1 shl 11
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
        const val PACKAGE_METADATA_POLICY_FINGERPRINT =
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97"
    }
}
