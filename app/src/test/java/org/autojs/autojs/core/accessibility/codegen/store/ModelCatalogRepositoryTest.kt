package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogSignatureVerifier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModelCatalogRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private var time = 1_000L

    private class Fetcher : FontCatalogFetcher {
        var catalog = ModelCatalogTestFixtures.bytes(ModelCatalogTestFixtures.catalog())
        var signature = ModelCatalogTestFixtures.signature(catalog)
        var failure: IOException? = null
        val calls = ArrayList<Pair<String, Long>>()
        fun serve(raw: JsonObject) {
            catalog = ModelCatalogTestFixtures.bytes(raw)
            signature = ModelCatalogTestFixtures.signature(catalog)
        }
        override fun fetch(url: String, maxBytes: Long): ByteArray {
            calls += url to maxBytes
            failure?.let { throw it }
            return if (url == "catalog") catalog else signature
        }
    }

    private fun repository(directory: File, fetcher: Fetcher, bootstrap: ByteArray? = null,
                           verifier: FontCatalogSignatureVerifier = ModelCatalogTestFixtures.verifier) = ModelCatalogRepository(
        directory, "catalog", "signature", 100, verifier, fetcher, bootstrap, { time },
    )

    @Test
    fun constructionAndLocalReadsNeverFetchAndRemoteCacheLastsExactlyTwentyFourHours() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        assertNull(repository.current())
        assertEquals(0, fetcher.calls.size)
        assertTrue(repository.refresh().remoteArtifactsAllowed)
        assertEquals(2, fetcher.calls.size)
        assertEquals(ModelCatalogParser.MAX_CATALOG_BYTES.toLong(), fetcher.calls[0].second)
        time += ModelCatalogRepository.CACHE_TTL_MILLIS - 1
        repository.refresh()
        assertEquals(2, fetcher.calls.size)
        time++
        repository.refresh()
        assertEquals(4, fetcher.calls.size)
        repository.refresh(force = true)
        assertEquals(6, fetcher.calls.size)
    }

    @Test
    fun cacheSurvivesRepositoryRecreationAndClockMovingBackwardsDoesNotExtendTtl() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        repository(directory, fetcher).refresh()
        val second = repository(directory, fetcher)
        second.refresh()
        assertEquals(2, fetcher.calls.size)
        time = 0
        second.refresh()
        assertEquals(4, fetcher.calls.size)
    }

    @Test
    fun networkSignatureAndParseFailuresPreserveThePreviousGoodCacheAndHistory() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        repository.refresh()
        val cache = File(directory, ModelCatalogRepository.CACHE_FILE_NAME).readBytes()
        val state = File(directory, ModelCatalogRepository.STATE_FILE_NAME).readBytes()
        fetcher.failure = IOException("offline")
        assertThrows(ModelCatalogNetworkException::class.java) { repository.refresh(force = true) }
        fetcher.failure = null
        fetcher.signature = ModelCatalogTestFixtures.signature("different".toByteArray())
        assertThrows(ModelCatalogSignatureException::class.java) { repository.refresh(force = true) }
        fetcher.catalog = "{bad catalog}".toByteArray()
        fetcher.signature = ModelCatalogTestFixtures.signature(fetcher.catalog)
        assertThrows(ModelCatalogParseException::class.java) { repository.refresh(force = true) }
        assertArrayEquals(cache, File(directory, ModelCatalogRepository.CACHE_FILE_NAME).readBytes())
        assertArrayEquals(state, File(directory, ModelCatalogRepository.STATE_FILE_NAME).readBytes())
        assertTrue(repository.loadCached()!!.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
    }

    @Test
    fun signedBootstrapIsVerifiedButDisplayOnlyAndNeverCreatesRemoteHistory() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher().apply { failure = IOException("offline") }
        val raw = ModelCatalogTestFixtures.bytes(ModelCatalogTestFixtures.catalog())
        var verifications = 0
        val verifier = FontCatalogSignatureVerifier { catalog, signature ->
            verifications++
            assertArrayEquals(raw, catalog)
            ModelCatalogTestFixtures.verifier.verify(catalog, signature)
        }
        val repository = repository(directory, fetcher, ModelCatalogTestFixtures.bootstrap(raw), verifier)
        assertEquals(1, verifications)
        assertFalse(repository.bootstrap()!!.remoteArtifactsAllowed)
        assertFalse(repository.loadBestAvailable()!!.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        assertFalse(File(directory, ModelCatalogRepository.STATE_FILE_NAME).exists())
        assertEquals(0, fetcher.calls.size)
        assertThrows(ModelCatalogNetworkException::class.java) { repository.refresh() }
        assertFalse(repository.current()!!.remoteArtifactsAllowed)
    }

    @Test
    fun emptyBootstrapV1DoesNotBlockFirstDifferentSignedRemoteV1() {
        val directory = temporary.newFolder()
        val bootstrap = ModelCatalogTestFixtures.bootstrap(ModelCatalogTestFixtures.bytes(ModelCatalogTestFixtures.catalog(versions = emptyList())))
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher, bootstrap)
        assertTrue(repository.current()!!.models.isEmpty())
        val accepted = repository.refresh()
        assertEquals(1, accepted.catalogVersion)
        assertTrue(accepted.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        assertTrue(File(directory, ModelCatalogRepository.STATE_FILE_NAME).isFile)
    }

    @Test
    fun unsignedBadSignatureAndNoncanonicalBootstrapEncodingsAreRejected() {
        val fetcher = Fetcher()
        val raw = ModelCatalogTestFixtures.bytes(ModelCatalogTestFixtures.catalog())
        assertThrows(ModelCatalogParseException::class.java) { repository(temporary.newFolder(), fetcher, raw) }
        val bootstrap = JsonParser.parseString(ModelCatalogTestFixtures.bootstrap(raw).toString(Charsets.UTF_8)).asJsonObject
        bootstrap.getAsJsonObject("signature").addProperty("signature", "not-the-signature")
        assertThrows(ModelCatalogSignatureException::class.java) { repository(temporary.newFolder(), fetcher, ModelCatalogTestFixtures.bytes(bootstrap)) }
        bootstrap.addProperty("catalogBase64", "Zh==")
        assertThrows(ModelCatalogParseException::class.java) { repository(temporary.newFolder(), fetcher, ModelCatalogTestFixtures.bytes(bootstrap)) }
    }

    @Test
    fun lowerCatalogAndSameVersionDifferentBytesAreRejected() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher().apply { serve(ModelCatalogTestFixtures.catalog(2)) }
        val repository = repository(directory, fetcher)
        repository.refresh()
        fetcher.serve(ModelCatalogTestFixtures.catalog(1))
        assertThrows(ModelCatalogRollbackException::class.java) { repository.refresh(force = true) }
        fetcher.serve(ModelCatalogTestFixtures.catalog(2))
        fetcher.catalog += byteArrayOf('\n'.code.toByte())
        fetcher.signature = ModelCatalogTestFixtures.signature(fetcher.catalog)
        assertThrows(ModelCatalogRollbackException::class.java) { repository.refresh(force = true) }
        assertEquals(2, repository.loadCached()!!.catalogVersion)
    }

    @Test
    fun previouslyObservedPackageHashAndOtherImmutableMetadataCannotChange() {
        for (changed in listOf(ModelCatalogTestFixtures.version(digest = "b".repeat(64)),
            ModelCatalogTestFixtures.version().apply { addProperty("versionName", "rewritten") })) {
            val fetcher = Fetcher()
            val repository = repository(temporary.newFolder(), fetcher)
            repository.refresh()
            fetcher.serve(ModelCatalogTestFixtures.catalog(2, listOf(changed)))
            assertThrows(ModelCatalogRollbackException::class.java) { repository.refresh(force = true) }
            assertEquals(1, repository.current()!!.catalogVersion)
        }
    }

    @Test
    fun signedZeroMetricsRemainNumericallyEquivalentAcrossCatalogUpdates() {
        val directory = temporary.newFolder()
        fun version(regret: Double) = ModelCatalogTestFixtures.version().apply {
            getAsJsonObject("metrics").getAsJsonObject("test").addProperty("regret", regret)
        }
        val fetcher = Fetcher().apply { serve(ModelCatalogTestFixtures.catalog(1, listOf(version(0.0)))) }
        val repository = repository(directory, fetcher)
        repository.refresh()
        fetcher.serve(ModelCatalogTestFixtures.catalog(2, listOf(version(-0.0))))
        val catalog = repository.refresh(force = true)
        assertEquals(2, catalog.catalogVersion)
        assertTrue(catalog.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(catalog.models.single().versions.single().metrics.test.regret!!))
    }

    @Test
    fun omittedVersionsRemainInHistoryAndCannotReturnWithDifferentContents() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        repository(directory, fetcher).refresh()
        fetcher.serve(ModelCatalogTestFixtures.catalog(2, emptyList()))
        val second = repository(directory, fetcher)
        assertTrue(second.refresh(force = true).models.isEmpty())
        fetcher.serve(ModelCatalogTestFixtures.catalog(3, listOf(ModelCatalogTestFixtures.version(digest = "b".repeat(64)))))
        assertThrows(ModelCatalogRollbackException::class.java) { repository(directory, fetcher).refresh(force = true) }
        fetcher.serve(ModelCatalogTestFixtures.catalog(3))
        assertTrue(repository(directory, fetcher).refresh(force = true).canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
    }

    @Test
    fun withdrawnAndDeprecatedFlagsCannotBeReversedEvenAfterAnOmission() {
        for (deprecated in listOf(false, true)) {
            val directory = temporary.newFolder()
            val fetcher = Fetcher()
            val repository = repository(directory, fetcher)
            repository.refresh()
            fetcher.serve(ModelCatalogTestFixtures.catalog(2, listOf(ModelCatalogTestFixtures.version(revoked = !deprecated, deprecated = deprecated))))
            repository.refresh(force = true)
            fetcher.serve(ModelCatalogTestFixtures.catalog(3, emptyList()))
            repository.refresh(force = true)
            fetcher.serve(ModelCatalogTestFixtures.catalog(4))
            assertThrows(ModelCatalogRollbackException::class.java) { repository(directory, fetcher).refresh(force = true) }
        }
    }

    @Test
    fun anotherInstanceObservesRevocationAndOldBootstrapCannotAuthorizeDownloadsAfterCacheLoss() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val bootstrap = ModelCatalogTestFixtures.bootstrap(fetcher.catalog)
        val first = repository(directory, fetcher, bootstrap)
        first.refresh()
        val stale = repository(directory, fetcher, bootstrap)
        assertTrue(stale.current()!!.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        fetcher.serve(ModelCatalogTestFixtures.catalog(2, listOf(ModelCatalogTestFixtures.version(revoked = true))))
        first.refresh(force = true)
        val observed = repository(directory, fetcher, bootstrap).current()!!
        assertEquals(2, observed.catalogVersion)
        assertTrue(observed.models.single().versions.single().revoked)
        assertTrue(File(directory, ModelCatalogRepository.CACHE_FILE_NAME).delete())
        assertFalse(stale.current()!!.remoteArtifactsAllowed)
        assertFalse(repository(directory, fetcher, bootstrap).current()!!.remoteArtifactsAllowed)
        fetcher.serve(ModelCatalogTestFixtures.catalog(1))
        assertThrows(ModelCatalogRollbackException::class.java) { stale.refresh(force = true) }
    }

    @Test
    fun committedHistoryWithOldCacheIsDisplayOnlyDuringInterruptedReplacement() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        repository.refresh()
        val file = File(directory, ModelCatalogRepository.CACHE_FILE_NAME)
        val old = file.readBytes()
        fetcher.serve(ModelCatalogTestFixtures.catalog(2, listOf(ModelCatalogTestFixtures.version(revoked = true))))
        repository.refresh(force = true)
        file.writeBytes(old)
        val second = repository(directory, fetcher)
        assertFalse(second.loadCached()!!.remoteArtifactsAllowed)
        assertFalse(second.current()!!.canDownload(ModelCatalogTestFixtures.MODEL_ID, 1))
        assertTrue(second.refresh(force = true).models.single().versions.single().revoked)
    }

    @Test
    fun corruptHistoryDisablesAuthorizationAndCannotBeResetByRefresh() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        repository.refresh()
        File(directory, ModelCatalogRepository.STATE_FILE_NAME).writeText("{corrupt")
        assertFalse(repository.current()!!.remoteArtifactsAllowed)
        assertThrows(ModelCatalogRollbackException::class.java) { repository.refresh(force = true) }
        assertFalse(repository(directory, fetcher).loadCached()!!.remoteArtifactsAllowed)
    }

    @Test
    fun memoryFallbackAlsoChecksVersionFingerprintsInStructurallyValidHistory() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        repository.refresh()
        val state = File(directory, ModelCatalogRepository.STATE_FILE_NAME)
        val json = JsonParser.parseString(state.readText()).asJsonObject
        json.getAsJsonArray("versions")[0].asJsonObject.addProperty("fingerprint", "0".repeat(64))
        state.writeText(json.toString())
        assertFalse(repository.current()!!.remoteArtifactsAllowed)
        assertThrows(ModelCatalogRollbackException::class.java) { repository.refresh(force = true) }
    }

    @Test
    fun signedBootstrapRemainsDisplayOnlyWhenCacheDirectoryCannotBeOpened() {
        val fetcher = Fetcher()
        val bootstrap = ModelCatalogTestFixtures.bootstrap(fetcher.catalog)
        val notDirectory = temporary.newFile()
        val repository = repository(notDirectory, fetcher, bootstrap)
        assertEquals(1, repository.current()!!.catalogVersion)
        assertFalse(repository.current()!!.remoteArtifactsAllowed)
        assertEquals(0, fetcher.calls.size)
        assertThrows(ModelCatalogStorageException::class.java) { repository.refresh() }
    }

    @Test
    fun verifiedMemoryRemainsDisplayOnlyWhenTheCatalogLockBecomesInaccessible() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val repository = repository(directory, fetcher)
        repository.refresh()
        val locks = File(directory, ".locks")
        assertTrue(File(locks, "catalog.lock").delete())
        assertTrue(locks.delete())
        locks.writeText("not a directory")
        assertEquals(1, repository.current()!!.catalogVersion)
        assertFalse(repository.current()!!.remoteArtifactsAllowed)
        assertEquals(2, fetcher.calls.size)
    }

    @Test
    fun cachedSignaturesAreRecheckedAndMalformedCacheFallsBackOnlyToSignedDisplayMetadata() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher()
        val bootstrap = ModelCatalogTestFixtures.bootstrap(fetcher.catalog)
        repository(directory, fetcher).refresh()
        val cache = File(directory, ModelCatalogRepository.CACHE_FILE_NAME)
        val bytes = cache.readBytes()
        bytes[bytes.lastIndex - 2] = '!'.code.toByte()
        cache.writeBytes(bytes)
        val second = repository(directory, fetcher, bootstrap)
        assertThrows(ModelCatalogException::class.java) { second.loadCached() }
        assertFalse(second.current()!!.remoteArtifactsAllowed)
        assertEquals(2, fetcher.calls.size)
    }

    @Test
    fun forcedRefreshStillChecksACachedBaselineWhenHistoryFileIsMissing() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher().apply { serve(ModelCatalogTestFixtures.catalog(2)) }
        repository(directory, fetcher).refresh()
        assertTrue(File(directory, ModelCatalogRepository.STATE_FILE_NAME).delete())
        fetcher.serve(ModelCatalogTestFixtures.catalog(1))
        assertThrows(ModelCatalogRollbackException::class.java) { repository(directory, fetcher).refresh(force = true) }
    }

    @Test
    fun forcedRefreshOfTheCachedVersionRestoresMissingDurableHistory() {
        val directory = temporary.newFolder()
        val fetcher = Fetcher().apply { serve(ModelCatalogTestFixtures.catalog(2)) }
        repository(directory, fetcher).refresh()
        val state = File(directory, ModelCatalogRepository.STATE_FILE_NAME)
        val previous = state.readBytes()
        assertTrue(state.delete())
        assertTrue(repository(directory, fetcher).refresh(force = true).remoteArtifactsAllowed)
        assertTrue(state.isFile)
        assertArrayEquals(previous, state.readBytes())
        assertEquals(2, repository(directory, fetcher).current()!!.catalogVersion)
    }

    @Test
    fun duplicateSignatureEnvelopeFieldsAreRejectedBeforeInjectedVerifier() {
        val fetcher = Fetcher()
        val original = fetcher.signature.toString(Charsets.UTF_8)
        fetcher.signature = original.replace("\"keyId\":\"test-only\"", "\"keyId\":\"test-only\",\"keyId\":\"test-only\"").toByteArray()
        var called = false
        val repository = repository(temporary.newFolder(), fetcher, verifier = FontCatalogSignatureVerifier { _, _ -> called = true })
        assertThrows(ModelCatalogSignatureException::class.java) { repository.refresh() }
        assertFalse(called)
    }
}
