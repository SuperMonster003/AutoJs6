package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetricSummary
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadConnection
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSource
import org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontDownloadSource
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

sealed class ModelDownloadEvent {
    data object Queued : ModelDownloadEvent()
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadEvent()
    data class Success(val installed: InstalledModel) : ModelDownloadEvent()
    data class Failure(val error: Throwable) : ModelDownloadEvent()
    data object Cancelled : ModelDownloadEvent()
}

fun interface ModelDownloadListener { fun onEvent(event: ModelDownloadEvent) }

class ModelDownloadSubscription internal constructor(
    val future: CompletableFuture<InstalledModel>,
    private val cancelAction: () -> Boolean,
) {
    fun cancel(): Boolean = cancelAction()
    val isCancelled: Boolean get() = future.isCancelled
}

class ModelDownloadAuthorizationException(message: String) : ModelCatalogException(message)
open class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ModelDownloadIntegrityException(message: String) : ModelDownloadException(message)
class ModelMirrorsExhaustedException(failures: List<Throwable>) :
    ModelDownloadException("All model download URLs failed", failures.lastOrNull()) {
    init { failures.dropLast(1).forEach(::addSuppressed) }
}

/** One subscription/transfer per model version. Cancellation is shared by its observers.
 * Terminal events follow staging cleanup; future cancellation may signal the request earlier.
 * zh-CN: 同一模型版本共享一个订阅和传输, 任一观察者取消即取消该传输.
 * 终止事件在临时文件清理后发出; future 取消可以先表明取消请求已接受. */
class ModelDownloader(
    private val store: ModelStore,
    private val catalogRepository: ModelCatalogRepository,
    private val source: FontDownloadSource = OkHttpFontDownloadSource(),
    private val workerExecutor: ExecutorService = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "CodegenModelDownloader").apply { isDaemon = true }
    },
    private val callbackExecutor: Executor = Executor(Runnable::run),
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : Closeable {
    private val lock = Any()
    private val active = HashMap<String, Transfer>()
    private var closed = false

    fun enqueue(
        modelId: String,
        version: CatalogModelVersion,
        listener: ModelDownloadListener? = null,
        replaceKey: String? = null,
    ): ModelDownloadSubscription {
        val detached = version.copy(artifact = version.artifact.copy(urls = version.artifact.urls.toList()))
        val id = "$modelId@${version.versionCode}"
        var start = false
        var failure: Throwable? = null
        val task = synchronized(lock) {
            val existing = active[id]
            if (existing != null && existing.version.copy(deprecated = detached.deprecated) == detached && existing.replaceKey == replaceKey) {
                existing.also { attach(it, listener, replay = true) }
            } else {
                Transfer(id, modelId, detached, replaceKey).also {
                    attach(it, listener, replay = false)
                    when {
                        closed -> failure = ModelDownloadException("Model downloader is closed")
                        existing != null -> failure = ModelDownloadException("Another request for this model version is active")
                        else -> { active[id] = it; start = true }
                    }
                }
            }
        }
        task.deliveries.flush()
        if (failure != null) finish(task, ModelDownloadEvent.Failure(requireNotNull(failure)))
        else if (start) {
            try { workerExecutor.execute { run(task) } }
            catch (error: RuntimeException) { finish(task, ModelDownloadEvent.Failure(error)) }
        }
        return task.subscription
    }

    fun isDownloading(modelId: String, versionCode: Int): Boolean = synchronized(lock) {
        active["$modelId@$versionCode"]?.let { !it.cancelled && !it.finished } == true
    }

    override fun close() {
        val tasks = synchronized(lock) {
            if (closed) return
            closed = true
            active.values.toList()
        }
        tasks.forEach(::cancel)
        // Tasks already inside the durable commit gate complete normally.
        workerExecutor.shutdown()
    }

    private fun run(task: Transfer) {
        synchronized(lock) {
            if (task.finished) return
            task.started = true
        }
        var stage: ModelStaging? = null
        var terminal: ModelDownloadEvent
        try {
            checkCancelled(task)
            catalogRepository.withDownloadAuthorization(task.modelId, task.version) { _, _ -> }
            stage = store.beginStaging()
            val failures = ArrayList<Throwable>()
            var downloaded = false
            for (url in task.version.artifact.urls) {
                checkCancelled(task)
                try {
                    stream(task, url, stage.file)
                    downloaded = true
                    break
                } catch (error: Exception) {
                    checkCancelled(task)
                    if (error is CancellationException) throw error
                    failures += error
                }
            }
            if (!downloaded) throw ModelMirrorsExhaustedException(failures)
            checkCancelled(task)
            val downloadedStage = requireNotNull(stage)
            val installed = catalogRepository.withDownloadAuthorization(task.modelId, task.version) { catalogVersion, version ->
                synchronized(lock) {
                    checkCancelled(task)
                    task.committing = true
                }
                store.installStaged(downloadedStage, "catalog:$catalogVersion", replaceKey = task.replaceKey,
                    expectedSha256 = version.artifact.sha256, expectedSizeBytes = version.artifact.size,
                    validateManifest = { validateManifest(it, task.modelId, version) })
            }
            terminal = ModelDownloadEvent.Success(installed)
        } catch (_: CancellationException) {
            terminal = ModelDownloadEvent.Cancelled
        } catch (error: Throwable) {
            terminal = if (synchronized(lock) { task.cancelled }) ModelDownloadEvent.Cancelled else ModelDownloadEvent.Failure(error)
        } finally {
            synchronized(lock) { task.connection = null }
        }
        try { stage?.close() }
        catch (error: Throwable) {
            // A successful durable installation is retained even if unrelated temporary cleanup
            // fails; callers must not be told that an installed package was cancelled.
            if (terminal !is ModelDownloadEvent.Success) terminal = ModelDownloadEvent.Failure(error)
        }
        finish(task, terminal)
    }

    private fun stream(task: Transfer, url: String, file: File) {
        val artifact = task.version.artifact
        val connection = source.open(url)
        synchronized(lock) { task.connection = connection }
        try {
            connection.use {
                checkCancelled(task)
                val declared = connection.contentLength
                if (declared >= 0 && declared != artifact.size) throw ModelDownloadIntegrityException("Model Content-Length differs from catalog")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                val input = connection.inputStream
                reportProgress(task, 0)
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled(task)
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        checkCancelled(task)
                        total += read
                        if (total > artifact.size) throw ModelDownloadIntegrityException("Model exceeds catalog size")
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        if (total < artifact.size) reportProgress(task, total)
                    }
                    output.flush()
                    output.fd.sync()
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                if (total != artifact.size || sha256 != artifact.sha256) throw ModelDownloadIntegrityException("Model size or SHA-256 differs from catalog")
                reportProgress(task, total, complete = true)
            }
        } finally {
            synchronized(lock) { if (task.connection === connection) task.connection = null }
        }
    }

    private fun reportProgress(task: Transfer, received: Long, complete: Boolean = false) {
        synchronized(lock) {
            if (task.cancelled || task.finished) return
            val previous = task.lastProgress
            // A mirror retry cannot move the displayed progress backwards.
            val observed = maxOf(received, previous?.bytesDownloaded ?: 0)
            val now = clockMillis()
            val percentChanged = previous == null || observed * 100 / task.version.artifact.size > previous.bytesDownloaded * 100 / task.version.artifact.size
            val elapsed = now - task.lastProgressAt
            if (previous != null && observed == previous.bytesDownloaded) return
            if (previous != null && !complete && elapsed < 100 && !percentChanged) return
            val event = ModelDownloadEvent.Progress(observed, task.version.artifact.size)
            task.lastProgress = event
            task.lastProgressAt = now
            task.deliveries.offer(task.listeners.toList(), event)
        }
        task.deliveries.flush()
    }

    private fun cancel(task: Transfer): Boolean {
        val connection: FontDownloadConnection?
        val started: Boolean
        synchronized(lock) {
            if (task.finished || task.committing || task.cancelled) return false
            task.cancelled = true
            connection = task.connection
            started = task.started
        }
        runCatching { connection?.cancel() }
        task.future.cancelFromDownloader()
        if (!started) finish(task, ModelDownloadEvent.Cancelled)
        return true
    }

    private fun checkCancelled(task: Transfer) {
        if (synchronized(lock) { task.cancelled } || Thread.currentThread().isInterrupted) throw CancellationException()
    }

    private fun finish(task: Transfer, event: ModelDownloadEvent) {
        val terminal: ModelDownloadEvent
        synchronized(lock) {
            if (task.finished) return
            terminal = if (task.cancelled && event !is ModelDownloadEvent.Success) ModelDownloadEvent.Cancelled else event
            task.finished = true
            if (active[task.id] === task) active.remove(task.id)
            task.deliveries.offer(task.listeners.toList(), terminal)
            task.listeners.clear()
        }
        when (terminal) {
            is ModelDownloadEvent.Success -> task.future.complete(terminal.installed)
            is ModelDownloadEvent.Failure -> task.future.completeExceptionally(terminal.error)
            ModelDownloadEvent.Cancelled -> task.future.cancelFromDownloader()
            else -> error("Expected a terminal download event")
        }
        task.deliveries.flush()
    }

    /** Caller holds lock; callbacks are only scheduled after releasing it. */
    private fun attach(task: Transfer, listener: ModelDownloadListener?, replay: Boolean) {
        if (listener == null) return
        task.listeners += listener
        task.deliveries.offer(listOf(listener), ModelDownloadEvent.Queued)
        if (replay) task.lastProgress?.let { task.deliveries.offer(listOf(listener), it) }
    }

    private inner class Transfer(val id: String, val modelId: String, val version: CatalogModelVersion, val replaceKey: String?) {
        val future = ManagedFuture()
        val subscription = ModelDownloadSubscription(future) { cancel(this) }
        val deliveries = Deliveries()
        val listeners = ArrayList<ModelDownloadListener>()
        var connection: FontDownloadConnection? = null
        var started = false
        var cancelled = false
        var committing = false
        var finished = false
        var lastProgress: ModelDownloadEvent.Progress? = null
        var lastProgressAt = 0L
        init { future.cancellationHandler = { cancel(this) } }
    }

    /** FIFO delivery even with a parallel callback executor, with no callback under transfer lock. */
    private inner class Deliveries {
        private val queue = ArrayDeque<Pair<List<ModelDownloadListener>, ModelDownloadEvent>>()
        private var draining = false
        fun offer(listeners: List<ModelDownloadListener>, event: ModelDownloadEvent) = synchronized(queue) {
            if (listeners.isNotEmpty()) queue.addLast(listeners to event)
            Unit
        }
        fun flush() {
            synchronized(queue) {
                if (draining || queue.isEmpty()) return
                draining = true
            }
            try { callbackExecutor.execute(::drain) }
            catch (_: RuntimeException) { synchronized(queue) { queue.clear(); draining = false } }
        }
        private fun drain() {
            while (true) {
                val next = synchronized(queue) {
                    if (queue.isEmpty()) { draining = false; return }
                    queue.removeFirst()
                }
                next.first.forEach { listener -> runCatching { listener.onEvent(next.second) } }
            }
        }
    }

    private class ManagedFuture : CompletableFuture<InstalledModel>() {
        var cancellationHandler: (() -> Boolean)? = null
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = cancellationHandler?.invoke() ?: super.cancel(false)
        fun cancelFromDownloader(): Boolean = super.cancel(false)
    }

    companion object {
        private fun validateManifest(manifest: ModelManifest, modelId: String, version: CatalogModelVersion) {
            if (manifest.modelId != modelId || manifest.versionCode != version.versionCode || manifest.versionName != version.versionName ||
                manifest.featureSchemaVersion != version.featureSchemaVersion || manifest.featureNamesHash != version.featureNamesHash ||
                manifest.minHostVersionCode != version.minHostVersionCode || manifest.dataset.datasetId != version.datasetId ||
                normalized(manifest.metrics) != normalized(version.metrics) || manifest.license != version.license) {
                throw ModelDownloadIntegrityException("Model manifest differs from the signed catalog")
            }
        }

        // The catalog parser and Python release validator treat +0.0 and -0.0 as the same value.
        private fun normalized(metrics: ModelMetrics) = ModelMetrics(normalized(metrics.test), normalized(metrics.realTest))
        private fun normalized(summary: ModelMetricSummary): ModelMetricSummary {
            fun zero(value: Double?) = value?.let { if (it == 0.0) 0.0 else it }
            return summary.copy(top1Survival = zero(summary.top1Survival), ruleTop1Survival = zero(summary.ruleTop1Survival), regret = zero(summary.regret))
        }
    }
}
