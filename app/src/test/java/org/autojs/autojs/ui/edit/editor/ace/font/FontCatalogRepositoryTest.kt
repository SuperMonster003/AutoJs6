package org.autojs.autojs.ui.edit.editor.ace.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FontCatalogRepositoryTest {
    @Test
    fun refreshCachesCatalogAndRejectsPersistentRollback() {
        val directory = Files.createTempDirectory("font-catalog-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val responses = mutableMapOf(CATALOG_URL to remoteV2, SIGNATURE_URL to byteArrayOf(1))
            val repository = repository(directory, bootstrap, responses)

            assertEquals(1L, repository.loadBestAvailable()!!.catalogVersion)
            assertTrue(repository.current()!!.remoteArtifactsAllowed)
            assertEquals(2L, repository.refresh().catalogVersion)

            val reloaded = repository(directory, bootstrap, responses)
            assertEquals(2L, reloaded.loadCached()!!.catalogVersion)

            responses[CATALOG_URL] = bootstrap
            assertThrows(FontCatalogRollbackException::class.java) { reloaded.refresh() }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptVerifiedCacheFallsBackToTrustedBootstrap() {
        val directory = Files.createTempDirectory("font-catalog-fallback-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val responses = mutableMapOf(CATALOG_URL to remoteV2, SIGNATURE_URL to byteArrayOf(1))
            repository(directory, bootstrap, responses).refresh()
            directory.resolve("catalog.cache").writeText("corrupt")

            val fallback = repository(directory, bootstrap, responses).loadBestAvailable()
            assertEquals(1L, fallback!!.catalogVersion)
            assertEquals(false, fallback.remoteArtifactsAllowed)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptRollbackStateDoesNotReauthorizeBootstrapArtifacts() {
        val directory = Files.createTempDirectory("font-catalog-state-corruption-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            directory.resolve("catalog.accepted").writeText("corrupt")

            val fallback = repository(directory, bootstrap, emptyMap()).loadBestAvailable()
            assertEquals(1L, fallback!!.catalogVersion)
            assertEquals(false, fallback.remoteArtifactsAllowed)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedRollbackStateDoesNotReauthorizeBootstrapArtifacts() {
        val directory = Files.createTempDirectory("font-catalog-large-state-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            directory.resolve("catalog.accepted").writeBytes(ByteArray(1024) { '9'.code.toByte() })

            val fallback = repository(directory, bootstrap, emptyMap()).loadBestAvailable()
            assertEquals(1L, fallback!!.catalogVersion)
            assertEquals(false, fallback.remoteArtifactsAllowed)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun currentReloadsCatalogAcceptedByAnotherRepositoryInstance() {
        val directory = Files.createTempDirectory("font-catalog-cross-process-test").toFile()
        try {
            val bytes = FontTestFixtures.validWoff2()
            val digest = FontTestFixtures.sha256(bytes)
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1, bytes = bytes).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(
                catalogVersion = 2,
                bytes = bytes,
                revokedSha256 = setOf(digest),
            ).toByteArray()
            val firstProcess = repository(directory, bootstrap, emptyMap())
            assertEquals(false, firstProcess.loadBestAvailable()!!.fonts.single().artifact.revoked)

            val secondProcess = repository(
                directory,
                bootstrap,
                mapOf(CATALOG_URL to remoteV2, SIGNATURE_URL to byteArrayOf(1)),
            )
            secondProcess.refresh()

            val reloaded = firstProcess.current()!!
            assertEquals(2L, reloaded.catalogVersion)
            assertTrue(reloaded.fonts.single().artifact.revoked)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptAcceptedMarkerKeepsOlderSignedCacheDisplayOnly() {
        val directory = Files.createTempDirectory("font-catalog-corrupt-marker-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val responses = mutableMapOf(
                CATALOG_URL to bootstrap,
                SIGNATURE_URL to byteArrayOf(1),
            )
            val writer = repository(directory, bootstrap, responses)
            writer.refresh()
            val signedV1Cache = directory.resolve("catalog.cache").readBytes()
            responses[CATALOG_URL] = remoteV2
            writer.refresh()

            directory.resolve("catalog.cache").writeBytes(signedV1Cache)
            directory.resolve("catalog.accepted").writeText("corrupt")
            val reader = repository(directory, bootstrap, responses)
            val cached = reader.loadCached()!!

            assertEquals(1L, cached.catalogVersion)
            assertEquals(false, cached.remoteArtifactsAllowed)
            assertEquals("corrupt", directory.resolve("catalog.accepted").readText())
            assertThrows(FontCatalogRollbackException::class.java) { reader.refresh() }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun currentUsesAcceptedGenerationFastPathWithoutRepeatedSignatureChecks() {
        val directory = Files.createTempDirectory("font-catalog-fast-path-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val verifications = AtomicInteger()
            val writer = repository(
                directory,
                bootstrap,
                mapOf(CATALOG_URL to remoteV2, SIGNATURE_URL to byteArrayOf(1)),
            )
            writer.refresh()
            val reader = FontCatalogRepository(
                directory = directory,
                catalogUrl = CATALOG_URL,
                signatureUrl = SIGNATURE_URL,
                hostVersionCode = 5221,
                signatureVerifier = FontCatalogSignatureVerifier { _, _ -> verifications.incrementAndGet() },
                fetcher = FontCatalogFetcher { _, _ -> error("Network is not expected") },
                bootstrapCatalogBytes = bootstrap,
            )

            assertEquals(2L, reader.current()!!.catalogVersion)
            assertEquals(2L, reader.current()!!.catalogVersion)
            assertEquals(2L, reader.current()!!.catalogVersion)
            assertEquals(1, verifications.get())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun interruptedReplacementRecoversCacheAndRollbackStateBackups() {
        val directory = Files.createTempDirectory("font-catalog-recovery-test").toFile()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val responses = mutableMapOf(CATALOG_URL to remoteV2, SIGNATURE_URL to byteArrayOf(1))
            repository(directory, bootstrap, responses).refresh()
            assertTrue(directory.resolve("catalog.cache").renameTo(directory.resolve("catalog.cache.bak")))
            assertTrue(directory.resolve("catalog.accepted").renameTo(directory.resolve("catalog.accepted.bak")))

            val recovered = repository(directory, bootstrap, responses)
            assertEquals(2L, recovered.loadCached()!!.catalogVersion)
            responses[CATALOG_URL] = bootstrap
            assertThrows(FontCatalogRollbackException::class.java) { recovered.refresh() }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun networkRefreshDoesNotHoldCatalogReadLock() {
        val directory = Files.createTempDirectory("font-catalog-lock-test").toFile()
        val releaseNetwork = CountDownLatch(1)
        val catalogFetchStarted = CountDownLatch(1)
        val refreshExecutor = Executors.newSingleThreadExecutor()
        val readExecutor = Executors.newSingleThreadExecutor()
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val repository = FontCatalogRepository(
                directory = directory,
                catalogUrl = CATALOG_URL,
                signatureUrl = SIGNATURE_URL,
                hostVersionCode = 5221,
                signatureVerifier = FontCatalogSignatureVerifier { _, _ -> },
                fetcher = FontCatalogFetcher { url, _ ->
                    if (url == CATALOG_URL) {
                        catalogFetchStarted.countDown()
                        assertTrue(releaseNetwork.await(5, TimeUnit.SECONDS))
                        remoteV2
                    } else {
                        byteArrayOf(1)
                    }
                },
                bootstrapCatalogBytes = bootstrap,
            )
            assertEquals(1L, repository.loadBestAvailable()!!.catalogVersion)
            val refresh = refreshExecutor.submit<FontCatalog> { repository.refresh() }
            assertTrue(catalogFetchStarted.await(5, TimeUnit.SECONDS))

            val concurrentRead = readExecutor.submit<FontCatalog?> { repository.current() }
            assertEquals(1L, concurrentRead.get(500, TimeUnit.MILLISECONDS)!!.catalogVersion)
            releaseNetwork.countDown()
            assertEquals(2L, refresh.get(5, TimeUnit.SECONDS).catalogVersion)
        } finally {
            releaseNetwork.countDown()
            refreshExecutor.shutdownNow()
            readExecutor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun authorizationLockAndConcurrentRefreshDoNotDeadlock() {
        val directory = Files.createTempDirectory("font-catalog-lock-order-test").toFile()
        val lockHeld = CountDownLatch(1)
        val candidateVerified = CountDownLatch(1)
        val callCurrent = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val bootstrap = FontTestFixtures.catalogJson(catalogVersion = 1).toByteArray()
            val remoteV2 = FontTestFixtures.catalogJson(catalogVersion = 2).toByteArray()
            val repository = FontCatalogRepository(
                directory = directory,
                catalogUrl = CATALOG_URL,
                signatureUrl = SIGNATURE_URL,
                hostVersionCode = 5221,
                signatureVerifier = FontCatalogSignatureVerifier { _, _ -> candidateVerified.countDown() },
                fetcher = FontCatalogFetcher { url, _ ->
                    if (url == CATALOG_URL) remoteV2 else byteArrayOf(1)
                },
                bootstrapCatalogBytes = bootstrap,
            )
            repository.loadBestAvailable()
            val store = FontStore(directory)
            val authorization = executor.submit<FontCatalog?> {
                store.withCatalogAuthorizationLock {
                    lockHeld.countDown()
                    assertTrue(callCurrent.await(5, TimeUnit.SECONDS))
                    repository.current()
                }
            }
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS))
            val refresh = executor.submit<FontCatalog> { repository.refresh() }
            assertTrue(candidateVerified.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            callCurrent.countDown()

            assertEquals(1L, authorization.get(5, TimeUnit.SECONDS)!!.catalogVersion)
            assertEquals(2L, refresh.get(5, TimeUnit.SECONDS).catalogVersion)
        } finally {
            callCurrent.countDown()
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    private fun repository(
        directory: java.io.File,
        bootstrap: ByteArray,
        responses: Map<String, ByteArray>,
    ) = FontCatalogRepository(
        directory = directory,
        catalogUrl = CATALOG_URL,
        signatureUrl = SIGNATURE_URL,
        hostVersionCode = 5221,
        signatureVerifier = FontCatalogSignatureVerifier { _, _ -> },
        fetcher = FontCatalogFetcher { url, _ -> responses.getValue(url) },
        bootstrapCatalogBytes = bootstrap,
    )

    companion object {
        private const val CATALOG_URL = "https://example.test/catalog-v1.json"
        private const val SIGNATURE_URL = "https://example.test/catalog-v1.sig.json"
    }
}
