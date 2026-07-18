package org.autojs.autojs.ui.edit.editor.ace.font

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

interface FontDownloadConnection : Closeable {
    val contentLength: Long
    val inputStream: InputStream
    fun cancel()
}

fun interface FontDownloadSource {
    @Throws(IOException::class)
    fun open(url: String): FontDownloadConnection
}

class OkHttpFontDownloadSource(
    private val client: OkHttpClient = OkHttpClient(),
) : FontDownloadSource {
    override fun open(url: String): FontDownloadConnection {
        val call = client.newCall(Request.Builder().url(url).get().build())
        return OkHttpConnection(call, url)
    }

    private class OkHttpConnection(
        private val call: Call,
        private val url: String,
    ) : FontDownloadConnection {
        private val lock = Any()

        @Volatile
        private var response: Response? = null

        @Volatile
        private var stream: InputStream? = null

        override val contentLength: Long get() = response().body!!.contentLength()

        override val inputStream: InputStream
            get() = stream ?: synchronized(lock) {
                stream ?: response().body!!.byteStream().also { stream = it }
            }

        override fun cancel() {
            call.cancel()
            runCatching { response?.close() }
        }

        override fun close() {
            response?.close() ?: call.cancel()
        }

        private fun response(): Response {
            response?.let { return it }
            return synchronized(lock) {
                response?.let { return@synchronized it }
                val received = try {
                    call.execute()
                } catch (e: Exception) {
                    call.cancel()
                    throw e
                }
                if (!received.isSuccessful) {
                    val code = received.code
                    received.close()
                    throw IOException("HTTP $code while downloading font from $url")
                }
                if (received.body == null) {
                    received.close()
                    throw IOException("Empty font response body from $url")
                }
                received.also { response = it }
            }
        }
    }
}

sealed class FontDownloadEvent {
    data object Queued : FontDownloadEvent()
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : FontDownloadEvent()
    data class Success(val installed: InstalledFont) : FontDownloadEvent()
    data class Failure(val error: Throwable) : FontDownloadEvent()
    data object Cancelled : FontDownloadEvent()
}

fun interface FontDownloadListener {
    fun onEvent(event: FontDownloadEvent)
}

/** Re-checks that an artifact is still present and authorized immediately before durable commit. */
fun interface FontDownloadCommitValidator {
    @Throws(Exception::class)
    fun validate(font: RemoteFont)
}

class FontDownloadSubscription internal constructor(
    val future: CompletableFuture<InstalledFont>,
    private val cancelAction: () -> Boolean,
) {
    fun cancel(): Boolean = cancelAction()
    val isCancelled: Boolean get() = future.isCancelled
}

open class FontDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RevokedFontException(fontId: String) : FontDownloadException("Font '$fontId' is revoked")

class ConcurrentFontVersionException(fontId: String) :
    FontDownloadException("Another version of font '$fontId' is already downloading")

class FontMirrorsExhaustedException(fontId: String, failures: List<Throwable>) :
    FontDownloadException("All download URLs failed for font '$fontId'", failures.lastOrNull()) {
    init {
        failures.dropLast(1).forEach(::addSuppressed)
    }
}

/**
 * Streams WOFF2 files into [FontStore]. Requests for the same id and digest share one transfer;
 * cancellation is per transfer-retaining subscriber and cancels the network call only after the
 * last such subscriber leaves. Non-retaining observers can attach without changing task lifetime.
 */
class FontDownloader(
    private val store: FontStore,
    private val source: FontDownloadSource = OkHttpFontDownloadSource(),
    private val workerExecutor: ExecutorService = defaultWorkerExecutor(),
    private val callbackExecutor: Executor = Executor(Runnable::run),
    private val commitValidator: FontDownloadCommitValidator = FontDownloadCommitValidator { },
) : Closeable {
    private val lock = Any()
    private val nextSubscriberId = AtomicLong()
    private val activeByFontId = hashMapOf<String, SharedTask>()

    @Volatile
    private var closed = false

    @JvmOverloads
    fun enqueue(font: RemoteFont, listener: FontDownloadListener? = null): FontDownloadSubscription {
        val future = ManagedFontFuture()
        val subscriber = Subscriber(
            id = nextSubscriberId.incrementAndGet(),
            future = future,
            listener = listener,
            retainsTransfer = true,
        )
        if (font.artifact.revoked) {
            val error = RevokedFontException(font.id)
            future.completeExceptionally(error)
            dispatch(subscriber, FontDownloadEvent.Failure(error))
            return FontDownloadSubscription(future) { false }
        }

        store.installationState(font).installed
            ?.takeIf { it.sha256 == font.artifact.sha256 }
            ?.let { installed ->
                future.complete(installed)
                dispatch(subscriber, FontDownloadEvent.Success(installed))
                return FontDownloadSubscription(future) { false }
            }

        val task: SharedTask
        var shouldStart = false
        synchronized(lock) {
            if (closed) {
                val error = FontDownloadException("Font downloader is closed")
                future.completeExceptionally(error)
                dispatch(subscriber, FontDownloadEvent.Failure(error))
                return FontDownloadSubscription(future) { false }
            }
            var active = activeByFontId[font.id]
            if (active?.cancelled == true && !active.committing) {
                if (activeByFontId[font.id] === active) activeByFontId.remove(font.id)
                active = null
            }
            if (active != null && active.font.artifact.sha256 != font.artifact.sha256) {
                val error = ConcurrentFontVersionException(font.id)
                future.completeExceptionally(error)
                dispatch(subscriber, FontDownloadEvent.Failure(error))
                return FontDownloadSubscription(future) { false }
            }
            task = active ?: SharedTask(font).also {
                activeByFontId[font.id] = it
                shouldStart = true
            }
            future.cancellationHandler = { cancelSubscriber(task, subscriber.id) }
            registerSubscriberLocked(task, subscriber, replayProgress = active != null)
        }
        if (shouldStart) {
            try {
                workerExecutor.execute { runTask(task) }
            } catch (e: RejectedExecutionException) {
                finishFailure(task, FontDownloadException("Font download worker rejected the task", e))
            }
        }
        return FontDownloadSubscription(future) { future.cancel(false) }
    }

    /**
     * Atomically attaches a non-retaining observer to an existing transfer.
     *
     * This method never creates a task. The observer receives [FontDownloadEvent.Queued] followed
     * by the latest [FontDownloadEvent.Progress], if any, before later task events. Cancelling the
     * returned subscription only detaches this observer and cannot stop the underlying transfer.
     */
    fun observeActive(
        fontId: String,
        listener: FontDownloadListener,
    ): FontDownloadSubscription? {
        val future = ManagedFontFuture()
        val subscriber = Subscriber(
            id = nextSubscriberId.incrementAndGet(),
            future = future,
            listener = listener,
            retainsTransfer = false,
        )
        val task = synchronized(lock) {
            val active = activeByFontId[fontId]
                ?.takeIf { !it.cancelled && it.hasRetainingSubscribers() }
                ?: return@synchronized null
            future.cancellationHandler = { cancelSubscriber(active, subscriber.id) }
            registerSubscriberLocked(active, subscriber, replayProgress = true)
            active
        } ?: return null
        return FontDownloadSubscription(future) { future.cancel(false) }
    }

    /** Atomically cancels every subscriber for [fontId], unless durable commit has begun. */
    fun cancel(fontId: String): Boolean {
        val subscribers: List<Subscriber>
        synchronized(lock) {
            val task = activeByFontId[fontId] ?: return false
            if (task.committing) return false
            task.cancelled = true
            task.connection?.cancel()
            subscribers = task.subscribers.values.toList()
            task.subscribers.clear()
            if (activeByFontId[fontId] === task) activeByFontId.remove(fontId)
        }
        subscribers.forEach { subscriber ->
            subscriber.future.cancelFromDownloader()
            dispatch(subscriber, FontDownloadEvent.Cancelled)
        }
        return true
    }

    fun isDownloading(fontId: String): Boolean = synchronized(lock) {
        activeByFontId[fontId]?.let { !it.cancelled && it.hasRetainingSubscribers() } == true
    }

    override fun close() {
        val subscribers = mutableListOf<Subscriber>()
        synchronized(lock) {
            if (closed) return
            closed = true
            val cancellable = activeByFontId.values.filterNot { it.committing }
            cancellable.forEach { task ->
                task.cancelled = true
                task.connection?.cancel()
                subscribers += task.subscribers.values
                task.subscribers.clear()
                if (activeByFontId[task.font.id] === task) activeByFontId.remove(task.font.id)
            }
        }
        subscribers.forEach { subscriber ->
            subscriber.future.cancelFromDownloader()
            dispatch(subscriber, FontDownloadEvent.Cancelled)
        }
        // A task that crossed the commit gate must finish consistently and publish success/failure.
        workerExecutor.shutdown()
    }

    private fun runTask(task: SharedTask) {
        var partFile: File? = null
        try {
            checkNotCancelled(task)
            partFile = store.createPartFile(task.font)
            val failures = mutableListOf<Throwable>()
            var downloaded = false
            for (url in task.font.artifact.urls) {
                checkNotCancelled(task)
                runCatching {
                    // FileOutputStream truncates the payload for each mirror. The companion lease
                    // remains locked for the transfer's entire lifetime.
                    streamUrl(task, url, partFile)
                }.onSuccess {
                    downloaded = true
                }.onFailure { failure ->
                    if (failure is CancellationException || isCancelled(task)) throw CancellationException()
                    failures += failure
                }
                if (downloaded) break
            }
            if (!downloaded) throw FontMirrorsExhaustedException(task.font.id, failures)
            beginCommit(task)
            val committedPart = requireNotNull(partFile)
            val installed = store.withCatalogAuthorizationLock {
                commitValidator.validate(task.font)
                store.installVerifiedPart(task.font, committedPart)
            }
            partFile = null
            finishSuccess(task, installed)
        } catch (_: CancellationException) {
            finishCancelled(task)
        } catch (e: Throwable) {
            finishFailure(task, e)
        } finally {
            partFile?.let(store::discardPart)
            synchronized(lock) { task.connection = null }
        }
    }

    private fun streamUrl(task: SharedTask, url: String, partFile: File) {
        val artifact = task.font.artifact
        val connection = source.open(url)
        synchronized(lock) {
            task.connection = connection
            if (task.cancelled || !task.hasRetainingSubscribers()) connection.cancel()
        }
        connection.use {
            checkNotCancelled(task)
            if (connection.contentLength >= 0 && connection.contentLength != artifact.sizeBytes) {
                throw FontFileValidationException(
                    "Font Content-Length mismatch: expected ${artifact.sizeBytes}, received ${connection.contentLength}",
                )
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val header = ByteArray(FontFileVerifier.WOFF2_HEADER_SIZE.toInt())
            var headerBytes = 0
            var downloaded = 0L
            var lastReported = 0L
            FileOutputStream(partFile).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                notify(task, FontDownloadEvent.Progress(0, artifact.sizeBytes))
                while (true) {
                    checkNotCancelled(task)
                    val read = connection.inputStream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    downloaded += read
                    if (downloaded > artifact.sizeBytes || downloaded > FontFileVerifier.MAX_FONT_BYTES) {
                        throw FontFileValidationException("Downloaded font exceeds its declared size")
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    if (headerBytes < header.size) {
                        val copy = minOf(read, header.size - headerBytes)
                        buffer.copyInto(header, headerBytes, 0, copy)
                        headerBytes += copy
                    }
                    if (downloaded - lastReported >= PROGRESS_GRANULARITY_BYTES || downloaded == artifact.sizeBytes) {
                        lastReported = downloaded
                        notify(task, FontDownloadEvent.Progress(downloaded, artifact.sizeBytes))
                    }
                }
                output.flush()
                output.fd.sync()
            }
            FontFileVerifier.validateResult(
                header = header,
                headerBytes = headerBytes,
                total = downloaded,
                digest = digest.digest(),
                expectedSize = artifact.sizeBytes,
                expectedSha256 = artifact.sha256,
            )
        }
        synchronized(lock) {
            if (task.connection === connection) task.connection = null
        }
    }

    private fun cancelSubscriber(task: SharedTask, subscriberId: Long): Boolean {
        val subscriber: Subscriber
        synchronized(lock) {
            val current = task.subscribers[subscriberId] ?: return false
            if (task.committing && current.retainsTransfer) return false
            task.subscribers.remove(subscriberId)
            subscriber = current
            if (subscriber.retainsTransfer && !task.hasRetainingSubscribers()) {
                task.cancelled = true
                task.connection?.cancel()
                if (activeByFontId[task.font.id] === task) activeByFontId.remove(task.font.id)
            }
        }
        val cancelled = subscriber.future.cancelFromDownloader()
        dispatch(subscriber, FontDownloadEvent.Cancelled)
        return cancelled
    }

    private fun finishSuccess(task: SharedTask, installed: InstalledFont) {
        finish(task) { subscriber ->
            subscriber.future.complete(installed)
            dispatch(subscriber, FontDownloadEvent.Success(installed))
        }
    }

    private fun finishFailure(task: SharedTask, error: Throwable) {
        finish(task) { subscriber ->
            subscriber.future.completeExceptionally(error)
            dispatch(subscriber, FontDownloadEvent.Failure(error))
        }
    }

    private fun finishCancelled(task: SharedTask) {
        finish(task) { subscriber ->
            subscriber.future.cancelFromDownloader()
            dispatch(subscriber, FontDownloadEvent.Cancelled)
        }
    }

    private fun finish(task: SharedTask, action: (Subscriber) -> Unit) {
        val subscribers = synchronized(lock) {
            if (activeByFontId[task.font.id] === task) activeByFontId.remove(task.font.id)
            task.subscribers.values.toList().also { task.subscribers.clear() }
        }
        subscribers.forEach(action)
    }

    private fun notify(task: SharedTask, event: FontDownloadEvent) {
        val subscribers = synchronized(lock) {
            if (event is FontDownloadEvent.Progress) task.lastProgress = event
            task.subscribers.values.toList()
        }
        subscribers.forEach { dispatch(it, event) }
    }

    /** Must be called with [lock] held so replay cannot race ahead of Queued. */
    private fun registerSubscriberLocked(
        task: SharedTask,
        subscriber: Subscriber,
        replayProgress: Boolean,
    ) {
        task.subscribers[subscriber.id] = subscriber
        dispatch(subscriber, FontDownloadEvent.Queued)
        if (replayProgress && task.subscribers.containsKey(subscriber.id)) {
            task.lastProgress?.let { dispatch(subscriber, it) }
        }
    }

    private fun dispatch(subscriber: Subscriber, event: FontDownloadEvent) {
        val listener = subscriber.listener ?: return
        try {
            callbackExecutor.execute { runCatching { listener.onEvent(event) } }
        } catch (_: RejectedExecutionException) {
            // Download result remains available through the future.
        }
    }

    private fun checkNotCancelled(task: SharedTask) {
        if (isCancelled(task) || Thread.currentThread().isInterrupted) throw CancellationException()
    }

    private fun beginCommit(task: SharedTask) {
        synchronized(lock) {
            if (task.cancelled || !task.hasRetainingSubscribers() || Thread.currentThread().isInterrupted) {
                throw CancellationException()
            }
            task.committing = true
        }
    }

    private fun isCancelled(task: SharedTask): Boolean = synchronized(lock) {
        task.cancelled || !task.hasRetainingSubscribers()
    }

    private class SharedTask(val font: RemoteFont) {
        val subscribers = linkedMapOf<Long, Subscriber>()
        var connection: FontDownloadConnection? = null
        var cancelled: Boolean = false
        var committing: Boolean = false
        var lastProgress: FontDownloadEvent.Progress? = null

        fun hasRetainingSubscribers(): Boolean = subscribers.values.any { it.retainsTransfer }
    }

    private data class Subscriber(
        val id: Long,
        val future: ManagedFontFuture,
        val listener: FontDownloadListener?,
        val retainsTransfer: Boolean,
    )

    private class ManagedFontFuture : CompletableFuture<InstalledFont>() {
        @Volatile
        var cancellationHandler: (() -> Boolean)? = null

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            return cancellationHandler?.invoke() ?: super.cancel(mayInterruptIfRunning)
        }

        fun cancelFromDownloader(): Boolean = super.cancel(false)
    }

    companion object {
        private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_GRANULARITY_BYTES = 64L * 1024L

        private fun defaultWorkerExecutor(): ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "AceFontDownloader").apply { isDaemon = true }
        }
    }
}
