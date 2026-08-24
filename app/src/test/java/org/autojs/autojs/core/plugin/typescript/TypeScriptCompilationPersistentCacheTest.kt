package org.autojs.autojs.core.plugin.typescript

import java.io.File
import java.nio.file.Files
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilationPersistentCacheTest {

    @Test
    fun publishesRecoversAndReplaysRequestAuthentication() = withCacheRoot { root ->
        val output = File(root.parentFile, "typescript-cache-output.zip")
        output.writeBytes("validated TypeScript output".toByteArray())
        val prepared = prepared("host-first")
        val cacheKey = TypeScriptCompilationCacheKey.sha256(prepared)
        val terminal = terminal(prepared.requestId)
        val cache = cache(root)

        val published = cache.publish(cacheKey, prepared, terminal, output)
        val firstHit = requireNotNull(cache.lookup(cacheKey))
        assertEquals(published, firstHit.entry)
        assertArrayEquals(output.readBytes(), firstHit.outputFile.readBytes())

        val recovered = cache(root)
        val recoveredHit = requireNotNull(recovered.lookup(cacheKey))
        assertEquals(prepared.requestId, recoveredHit.entry.terminal.requestId)
        assertEquals(terminal, recoveredHit.entry.terminal)

        val current = prepared("host-second")
        val replay = recoveredHit.entry.replayPrepared(current)
        assertEquals(current.projectId, replay.projectId)
        assertEquals(current.sourceFiles, replay.sourceFiles)
        assertEquals(prepared.requestId, replay.requestId)
        assertEquals(prepared.inputArchiveSha256, replay.inputArchiveSha256)
        assertEquals(prepared.requestManifestSha256, replay.requestManifestSha256)
        output.delete()
    }

    @Test
    fun rejectsAndEvictsChangedOutputBytes() = withCacheRoot { root ->
        val output = File(root.parentFile, "typescript-cache-tamper.zip")
        output.writeBytes("first output".toByteArray())
        val prepared = prepared()
        val key = TypeScriptCompilationCacheKey.sha256(prepared)
        val cache = cache(root)
        cache.publish(key, prepared, terminal(prepared.requestId), output)
        val hit = requireNotNull(cache.lookup(key))

        hit.outputFile.writeBytes("changed output".toByteArray())

        assertNull(cache.lookup(key))
        assertFalse(hit.outputFile.exists())
        output.delete()
    }

    @Test
    fun corruptManifestIsRejectedDuringRecovery() = withCacheRoot { root ->
        val output = File(root.parentFile, "typescript-cache-index.zip")
        output.writeBytes("output".toByteArray())
        val prepared = prepared()
        val key = TypeScriptCompilationCacheKey.sha256(prepared)
        val cache = cache(root)
        cache.publish(key, prepared, terminal(prepared.requestId), output)
        val manifest = root.listFiles().orEmpty().single { it.name.endsWith(".entry") }
        manifest.appendBytes(byteArrayOf(0))

        val recovered = cache(root)

        assertNull(recovered.lookup(key))
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".entry") })
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".output.zip") })
        output.delete()
    }

    @Test
    fun staleEntryCannotInvalidateReplacementGeneration() = withCacheRoot { root ->
        val output = File(root.parentFile, "typescript-cache-replace.zip")
        val prepared = prepared()
        val key = TypeScriptCompilationCacheKey.sha256(prepared)
        val cache = cache(root)
        output.writeBytes("first".toByteArray())
        val first = cache.publish(key, prepared, terminal(prepared.requestId), output)
        output.writeBytes("second".toByteArray())
        val second = cache.publish(key, prepared, terminal(prepared.requestId), output)

        assertFalse(cache.invalidate(first))
        assertEquals(second, cache.lookup(key)?.entry)
        output.delete()
    }

    @Test
    fun enforcesTotalByteQuotaWithoutEvictingNewPublication() = withCacheRoot { root ->
        var now = 1L
        val limits = TypeScriptCompilationPersistentCache.Limits(
            maximumEntryBytes = 100L,
            maximumTotalBytes = 150L,
            maximumEntries = 2,
        )
        val cache = cache(root, limits) { now++ }
        val firstPrepared = prepared().copy(projectId = "project-first")
        val firstKey = TypeScriptCompilationCacheKey.sha256(firstPrepared)
        val secondPrepared = prepared().copy(projectId = "project-second")
        val secondKey = TypeScriptCompilationCacheKey.sha256(secondPrepared)
        val output = File(root.parentFile, "typescript-cache-quota.zip")

        output.writeBytes(ByteArray(80) { 1 })
        cache.publish(firstKey, firstPrepared, terminal(firstPrepared.requestId), output)
        output.writeBytes(ByteArray(80) { 2 })
        cache.publish(secondKey, secondPrepared, terminal(secondPrepared.requestId), output)

        assertNull(cache.lookup(firstKey))
        assertNotNull(cache.lookup(secondKey))
        output.delete()
    }

    private fun cache(
        root: File,
        limits: TypeScriptCompilationPersistentCache.Limits =
            TypeScriptCompilationPersistentCache.Limits(),
        clock: () -> Long = { 1L },
    ) = TypeScriptCompilationPersistentCache(root, TestFileOps, limits, clock)

    private fun prepared(requestId: String = "host-request") = TypeScriptPreparedCompilation(
        requestId = requestId,
        projectId = "single-file-project",
        entrySourceUri = "autojs://source/single-file-project/main.ts",
        entryPath = "sources/main.ts",
        archiveFile = File("request.zip"),
        inputArchiveSha256 = hash('6'),
        requestManifestSha256 = hash('7'),
        configByteLength = 2L,
        configSha256 = hash('5'),
        authority = TypeScriptCompilerAuthority(
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            compilerVersion = "6.0.3",
            compilerDistributionFingerprint = hash('1'),
            declarationsFingerprint = hash('2'),
            declarationProfileFingerprint = hash('3'),
            runtimeCapabilitiesFingerprint = hash('4'),
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = 1,
            configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            configPolicyFingerprint = hash('5'),
        ),
        rootPaths = listOf("sources/main.ts"),
        sourceFiles = listOf(
            TypeScriptPreparedSource("sources/main.ts", "main.ts", null, hash('1')),
        ),
    )

    private fun terminal(requestId: String) = TypeScriptTerminalResult(
        requestId = requestId,
        outcome = TypeScriptCompilerContract.OUTCOME_EMITTED,
        succeeded = true,
        errorCode = null,
        errorMessage = null,
        diagnosticCount = 0,
        emittedModuleCount = 1,
        outputManifestSha256 = hash('8'),
    )

    private inline fun withCacheRoot(block: (File) -> Unit) {
        val parent = Files.createTempDirectory("typescript-cache-test-").toFile()
        val root = File(parent, "cache").apply { mkdir() }
        try {
            block(root)
        } finally {
            parent.walkBottomUp().forEach(File::delete)
        }
    }

    private fun hash(character: Char) = character.toString().repeat(64)

    private object TestFileOps : TypeScriptCompilationPersistentCache.FileOps {
        override fun makeOwnerReadWrite(file: File) = Unit

        override fun makeOwnerReadOnly(file: File) = Unit

        override fun isOwnerReadOnlyRegularFile(file: File): Boolean = file.isFile

        override fun atomicReplace(source: File, destination: File) {
            if (destination.exists()) check(destination.delete())
            check(source.renameTo(destination))
        }

        override fun syncDirectory(directory: File) = Unit
    }
}
