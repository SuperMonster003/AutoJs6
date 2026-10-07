package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadConnection
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSource
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ModelDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val workers = ArrayList<ExecutorService>()
    private val downloaders = ArrayList<ModelDownloader>()

    @After fun stopWorkers() {
        downloaders.forEach(ModelDownloader::close)
        workers.forEach { it.shutdown(); assertTrue(it.awaitTermination(5, TimeUnit.SECONDS)) }
    }

    @Test fun installsExactBytesAsOfficialAndLimitsMonotonicProgress() {
        val fixture = Fixture()
        val seen = Events()
        val download = downloader(fixture, FontDownloadSource { BytesConnection(fixture.bytes, chunk = 31) })
        val result = download.enqueue(fixture.modelId, fixture.version(), seen).future.get(5, TimeUnit.SECONDS)
        seen.await()
        assertEquals(ModelSource.OFFICIAL, result.source)
        assertEquals("catalog:1", result.origin)
        assertArrayEquals(fixture.bytes, requireNotNull(fixture.store.installedFile(result.key)).readBytes())
        val progress = seen.values.filterIsInstance<ModelDownloadEvent.Progress>()
        assertEquals(0L, progress.first().bytesDownloaded)
        assertEquals(fixture.bytes.size.toLong(), progress.last().bytesDownloaded)
        assertTrue(progress.size <= 102)
        assertEquals(progress.map { it.bytesDownloaded }.sorted(), progress.map { it.bytesDownloaded })
        assertTrue(seen.values.first() is ModelDownloadEvent.Queued)
        assertTrue(seen.values.last() is ModelDownloadEvent.Success)
        fixture.assertNoStaging()
    }

    @Test fun aPartialFirstMirrorFallsBackWithoutRegressingProgress() {
        val fixture = Fixture { root ->
            root.addProperty("catalogVersion", 2)
            root.addProperty("releaseTag", "codegen-models-v2")
            val artifact = jsonVersion(root).getAsJsonObject("artifact")
            artifact.getAsJsonArray("urls").add(artifact["url"].asString.replace("codegen-models-v1/", "codegen-models-v2/"))
        }
        val opened = CopyOnWriteArrayList<String>()
        val download = downloader(fixture, FontDownloadSource { url ->
            opened += url
            BytesConnection(if (opened.size == 1) fixture.bytes.copyOf(fixture.bytes.size / 2) else fixture.bytes,
                declared = -1, chunk = 157)
        })
        val events = Events()
        download.enqueue(fixture.modelId, fixture.version(), events).future.get(5, TimeUnit.SECONDS)
        events.await()
        assertEquals(fixture.version().artifact.urls, opened)
        val progress = events.values.filterIsInstance<ModelDownloadEvent.Progress>().map { it.bytesDownloaded }
        assertEquals(progress.sorted(), progress)
        assertEquals(fixture.bytes.size.toLong(), progress.last())
        fixture.assertNoStaging()
    }

    @Test fun cancellationInterruptsTheConnectionAndCleansBeforeTerminalEvent() {
        val fixture = Fixture()
        val blocked = BlockingConnection(fixture.bytes)
        val download = downloader(fixture, FontDownloadSource { blocked })
        val events = Events()
        val subscription = download.enqueue(fixture.modelId, fixture.version(), events)
        assertTrue(blocked.entered.await(5, TimeUnit.SECONDS))
        assertTrue(subscription.cancel())
        events.await()
        assertTrue(blocked.cancelled.get())
        assertTrue(subscription.future.isCancelled)
        assertTrue(events.values.last() is ModelDownloadEvent.Cancelled)
        assertEquals(1, events.values.count { it is ModelDownloadEvent.Cancelled })
        assertTrue(fixture.store.listInstalled().isEmpty())
        fixture.assertNoStaging()
    }

    @Test fun exactDuplicateRequestsShareSubscriptionAndOneTransfer() {
        val fixture = Fixture()
        val blocked = BlockingConnection(fixture.bytes)
        val opens = AtomicInteger()
        val download = downloader(fixture, FontDownloadSource { opens.incrementAndGet(); blocked })
        val firstEvents = Events()
        val secondEvents = Events()
        val first = download.enqueue(fixture.modelId, fixture.version(), firstEvents)
        assertTrue(blocked.entered.await(5, TimeUnit.SECONDS))
        val second = download.enqueue(fixture.modelId, fixture.version(), secondEvents)
        assertSame(first, second)
        blocked.proceed.countDown()
        first.future.get(5, TimeUnit.SECONDS)
        firstEvents.await(); secondEvents.await()
        assertEquals(1, opens.get())
        assertEquals(1, fixture.store.listInstalled().size)
        assertTrue(secondEvents.values.first() is ModelDownloadEvent.Queued)
    }

    @Test fun deprecationDuringTransferStillSharesTheSameExplicitDownload() {
        val fixture = Fixture()
        val blocked = BlockingConnection(fixture.bytes)
        val download = downloader(fixture, FontDownloadSource { blocked })
        val first = download.enqueue(fixture.modelId, fixture.version())
        assertTrue(blocked.entered.await(5, TimeUnit.SECONDS))
        fixture.changeStatus(deprecated = true)
        val second = download.enqueue(fixture.modelId, fixture.version())
        assertSame(first, second)
        blocked.proceed.countDown()
        first.future.get(5, TimeUnit.SECONDS)
    }

    @Test fun revocationDuringTransferPreventsDurableInstallation() {
        val fixture = Fixture()
        val blocked = BlockingConnection(fixture.bytes)
        val download = downloader(fixture, FontDownloadSource { blocked })
        val events = Events()
        val task = download.enqueue(fixture.modelId, fixture.version(), events)
        assertTrue(blocked.entered.await(5, TimeUnit.SECONDS))
        fixture.changeStatus(revoked = true)
        blocked.proceed.countDown()
        assertTrue(assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }.cause is ModelDownloadAuthorizationException)
        events.await()
        assertTrue(fixture.store.listInstalled().isEmpty())
        fixture.assertNoStaging()
    }

    @Test fun declaredOversizeIsRejectedBeforeReadingTheBody() {
        val fixture = Fixture()
        val bodyOpened = AtomicBoolean()
        val connection = object : BytesConnection(fixture.bytes, declared = fixture.bytes.size + 1L) {
            override val inputStream: InputStream get() { bodyOpened.set(true); return super.inputStream }
        }
        val events = Events()
        val task = downloader(fixture, FontDownloadSource { connection }).enqueue(fixture.modelId, fixture.version(), events)
        assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }
        events.await()
        assertFalse(bodyOpened.get())
        fixture.assertNoStaging()
    }

    @Test fun actualOversizeAndWrongHashLeaveNoInstalledOrTemporaryPackage() {
        for (oversize in listOf(true, false)) {
            val fixture = Fixture()
            val bytes = if (oversize) fixture.bytes + byteArrayOf(1) else fixture.bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            val events = Events()
            val task = downloader(fixture, FontDownloadSource { BytesConnection(bytes, declared = -1) })
                .enqueue(fixture.modelId, fixture.version(), events)
            val failure = assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }
            events.await()
            assertTrue(failure.cause is ModelMirrorsExhaustedException)
            assertTrue(fixture.store.listInstalled().isEmpty())
            fixture.assertNoStaging()
        }
    }

    @Test fun forgedArtifactCannotReuseAnOtherwiseValidFingerprint() {
        val fixture = Fixture()
        val opened = AtomicBoolean()
        val forged = fixture.version().let { it.copy(artifact = it.artifact.copy(urls = listOf("https://untrusted.invalid/model.ajcgm"))) }
        val task = downloader(fixture, FontDownloadSource { opened.set(true); BytesConnection(fixture.bytes) })
            .enqueue(fixture.modelId, forged)
        assertTrue(assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }.cause is ModelDownloadAuthorizationException)
        assertFalse(opened.get())
    }

    @Test fun aSignedBootstrapAloneCannotAuthorizeNetworkAccess() {
        val fixture = Fixture(activate = false)
        val opened = AtomicBoolean()
        val task = downloader(fixture, FontDownloadSource { opened.set(true); BytesConnection(fixture.bytes) })
            .enqueue(fixture.modelId, fixture.version())
        assertTrue(assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }.cause is ModelDownloadAuthorizationException)
        assertFalse(opened.get())
    }

    @Test fun packageMetadataMustAlsoMatchTheSignedCatalog() {
        val fixture = Fixture { jsonVersion(it).addProperty("datasetId", "different-reviewed-dataset") }
        val events = Events()
        val task = downloader(fixture, FontDownloadSource { BytesConnection(fixture.bytes) })
            .enqueue(fixture.modelId, fixture.version(), events)
        assertThrows(ExecutionException::class.java) { task.future.get(5, TimeUnit.SECONDS) }
        events.await()
        assertTrue(fixture.store.listInstalled().isEmpty())
        fixture.assertNoStaging()
    }

    @Test fun negativeZeroInPackageMetricsMatchesTheNormalizedCatalogValue() {
        val fixture = Fixture(packageTransform = ::withNegativeZeroMetric) {
            jsonVersion(it).getAsJsonObject("metrics").getAsJsonObject("test").addProperty("regret", 0.0)
        }
        val task = downloader(fixture, FontDownloadSource { BytesConnection(fixture.bytes) })
            .enqueue(fixture.modelId, fixture.version())
        val installed = task.future.get(5, TimeUnit.SECONDS)
        assertEquals(ModelSource.OFFICIAL, installed.source)
        assertEquals((-0.0).toRawBits(), requireNotNull(installed.manifest.metrics.test.regret).toRawBits())
    }

    @Test fun callbackFailureAndParallelExecutorPreserveEventOrderAndResult() {
        val fixture = Fixture()
        val callbacks = Executors.newFixedThreadPool(3).also(workers::add)
        val events = Events(throwOnProgress = true)
        val task = downloader(fixture, FontDownloadSource { BytesConnection(fixture.bytes, chunk = 29) }, callbacks)
            .enqueue(fixture.modelId, fixture.version(), events)
        task.future.get(5, TimeUnit.SECONDS)
        events.await()
        assertTrue(events.values.first() is ModelDownloadEvent.Queued)
        assertTrue(events.values.last() is ModelDownloadEvent.Success)
        val progress = events.values.filterIsInstance<ModelDownloadEvent.Progress>().map { it.bytesDownloaded }
        assertEquals(progress.sorted(), progress)
    }

    @Test fun listenerCanCancelReentrantlyWithoutHoldingTheTransferLock() {
        val fixture = Fixture()
        val blocked = BlockingConnection(fixture.bytes)
        val download = downloader(fixture, FontDownloadSource { blocked })
        val events = Events()
        val task = download.enqueue(fixture.modelId, fixture.version(), ModelDownloadListener { event ->
            events.onEvent(event)
            if (event == ModelDownloadEvent.Queued) download.close()
        })
        events.await()
        assertTrue(task.future.isCancelled)
        fixture.assertNoStaging()
    }

    private fun downloader(fixture: Fixture, source: FontDownloadSource, callbacks: Executor = Executor(Runnable::run)): ModelDownloader {
        val worker = Executors.newSingleThreadExecutor().also(workers::add)
        return ModelDownloader(fixture.store, fixture.repository, source, worker, callbacks, clockMillis = { 0L }).also(downloaders::add)
    }

    private inner class Fixture(activate: Boolean = true, packageTransform: (ByteArray) -> ByteArray = { it }, mutate: (JsonObject) -> Unit = {}) {
        val bytes = packageTransform(resource("models/tiny-test-v1.ajcgm"))
        val json = JsonParser.parseString(Base64.getDecoder().decode(
            JsonParser.parseString(resource("catalog/codegen_model_catalog_v1.json").toString(Charsets.UTF_8)).asJsonObject["catalogBase64"].asString
        ).toString(Charsets.UTF_8)).asJsonObject.apply {
            val artifact = jsonVersion(this).getAsJsonObject("artifact")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val oldName = artifact["fileName"].asString
            val newName = oldName.replace(artifact["sha256"].asString.take(8), hash.take(8))
            artifact.addProperty("fileName", newName)
            artifact.addProperty("size", bytes.size)
            artifact.addProperty("sha256", hash)
            artifact.addProperty("url", artifact["url"].asString.replace(oldName, newName))
            artifact.add("urls", com.google.gson.JsonArray().apply { add(artifact["url"].asString) })
            mutate(this)
        }
        val modelId = json.getAsJsonArray("models")[0].asJsonObject["modelId"].asString
        val repository = ModelCatalogRepository(temporary.newFolder(), hostVersionCode = Int.MAX_VALUE,
            signatureVerifier = ModelCatalogTestFixtures.verifier,
            expectedRepository = json["repository"].asString,
            bootstrapEnvelopeBytes = ModelCatalogTestFixtures.bootstrap(raw()),
            fetcher = FontCatalogFetcher { url, _ -> if (url.endsWith(".sig.json")) ModelCatalogTestFixtures.signature(raw()) else raw() })
        val selection = object : CurrentModelSelection { override var currentKey: String? = null }
        val store = ModelStore(temporary.newFolder(), ModelPackageReader(Int.MAX_VALUE, temporary.newFolder()),
            verifiedCatalogs = { listOfNotNull(repository.loadBestAvailable(), repository.bootstrap()) }, selection = selection)
        init { if (activate) repository.refresh(force = true) }
        fun version() = requireNotNull(repository.current()).models.single().versions.single()
        fun raw() = json.toString().toByteArray(Charsets.UTF_8)
        fun changeStatus(revoked: Boolean = false, deprecated: Boolean = false) {
            json.addProperty("catalogVersion", json["catalogVersion"].asInt + 1)
            json.addProperty("releaseTag", "codegen-models-v${json["catalogVersion"].asInt}")
            jsonVersion(json).apply { addProperty("revoked", revoked); addProperty("deprecated", deprecated) }
            json.getAsJsonArray("models")[0].asJsonObject.add("recommendedVersionCode", JsonNull.INSTANCE)
            repository.refresh(force = true)
        }
        fun assertNoStaging() { assertTrue(fileTreeFiles(store.rootDirectory.resolve("staging")).isEmpty()) }
    }

    private class Events(private val throwOnProgress: Boolean = false) : ModelDownloadListener {
        val values = CopyOnWriteArrayList<ModelDownloadEvent>()
        private val finished = CountDownLatch(1)
        override fun onEvent(event: ModelDownloadEvent) {
            values += event
            if (event is ModelDownloadEvent.Success || event is ModelDownloadEvent.Failure || event is ModelDownloadEvent.Cancelled) finished.countDown()
            if (throwOnProgress && event is ModelDownloadEvent.Progress) throw IllegalStateException("Observer failure")
        }
        fun await() { assertTrue("No terminal event: $values", finished.await(5, TimeUnit.SECONDS)) }
    }

    private open class BytesConnection(bytes: ByteArray, private val declared: Long = bytes.size.toLong(), chunk: Int = Int.MAX_VALUE) : FontDownloadConnection {
        private val stream = object : ByteArrayInputStream(bytes) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int = super.read(target, offset, minOf(length, chunk))
        }
        override val contentLength: Long get() = declared
        override val inputStream: InputStream get() = stream
        override fun cancel() { stream.close() }
        override fun close() { stream.close() }
    }

    private class BlockingConnection(bytes: ByteArray) : FontDownloadConnection {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        private val stream = object : ByteArrayInputStream(bytes) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                if (!proceed.await(5, TimeUnit.SECONDS)) throw IOException("Test body was not released")
                if (cancelled.get()) throw IOException("Connection cancelled")
                return super.read(target, offset, length)
            }
        }
        override val contentLength = bytes.size.toLong()
        override val inputStream: InputStream get() = stream
        override fun cancel() { cancelled.set(true); proceed.countDown() }
        override fun close() { proceed.countDown(); stream.close() }
    }

    companion object {
        private fun jsonVersion(root: JsonObject) = root.getAsJsonArray("models")[0].asJsonObject.getAsJsonArray("versions")[0].asJsonObject
        private fun resource(name: String) = requireNotNull(ModelDownloaderTest::class.java.getResourceAsStream("/codegen/$name")).use { it.readBytes() }
        private fun fileTreeFiles(root: java.io.File) = if (root.exists()) root.walkTopDown().filter { it.isFile }.toList() else emptyList()
        private fun withNegativeZeroMetric(original: ByteArray): ByteArray {
            val result = ByteArrayOutputStream()
            ZipOutputStream(result).use { output ->
                ZipInputStream(original.inputStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        var content = input.readBytes()
                        if (entry.name == "manifest.json") {
                            val manifest = JsonParser.parseString(content.toString(Charsets.UTF_8)).asJsonObject
                            manifest.getAsJsonObject("metrics").getAsJsonObject("test").addProperty("regret", -0.0)
                            content = manifest.toString().toByteArray(Charsets.UTF_8)
                        }
                        output.putNextEntry(ZipEntry(entry.name))
                        output.write(content)
                        output.closeEntry()
                    }
                }
            }
            return result.toByteArray()
        }
    }
}
