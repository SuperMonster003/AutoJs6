package org.autojs.autojs.core.plugin.explorer.output

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal sealed interface ExplorerActionOutputDrainResult {
    data class Completed(val bytes: Long) : ExplorerActionOutputDrainResult
    data class Failed(val error: Throwable) : ExplorerActionOutputDrainResult
    data class Cancelled(val reason: String) : ExplorerActionOutputDrainResult
    data object Missing : ExplorerActionOutputDrainResult
    data object TimedOut : ExplorerActionOutputDrainResult
}

internal object ExplorerActionOutputPipeSessions {

    private val lock = Any()
    private val sessions = mutableMapOf<String, PipeSession>()

    fun hasSession(transactionId: String): Boolean = synchronized(lock) {
        sessions.containsKey(transactionId)
    }

    fun open(
        transactionId: String,
        stagingFile: File,
        maxOutputBytes: Long,
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val session = PipeSession(
            transactionId = transactionId,
            readSide = pipe[0],
            stagingFile = stagingFile,
            policy = ExplorerActionBoundedOutputPolicy(declaredMaxBytes = maxOutputBytes),
        )
        synchronized(lock) {
            if (sessions.containsKey(transactionId)) {
                pipe.forEach { descriptor -> runCatching { descriptor.closeWithError(WRITER_EXISTS_ERROR) } }
                throw SecurityException("Explorer Action output transaction already has a writer")
            }
            sessions[transactionId] = session
        }
        try {
            session.start()
            return pipe[1]
        } catch (error: Throwable) {
            synchronized(lock) { sessions.remove(transactionId, session) }
            pipe.forEach { descriptor -> runCatching { descriptor.closeWithError(SESSION_START_ERROR) } }
            throw error
        }
    }

    fun await(
        transactionId: String,
        timeoutMillis: Long,
    ): ExplorerActionOutputDrainResult {
        require(timeoutMillis > 0L) { "Explorer Action output drain timeout must be positive" }
        val session = synchronized(lock) { sessions[transactionId] }
            ?: return ExplorerActionOutputDrainResult.Missing
        return session.await(timeoutMillis)
    }

    fun releaseCompleted(transactionId: String) {
        synchronized(lock) { sessions.remove(transactionId) }
    }

    fun cancelAndRemove(
        transactionId: String,
        reason: String,
        timeoutMillis: Long = CANCEL_WAIT_MILLIS,
    ): ExplorerActionOutputDrainResult {
        val session = synchronized(lock) { sessions[transactionId] }
            ?: return ExplorerActionOutputDrainResult.Missing
        session.cancel(reason)
        val result = session.await(timeoutMillis)
        synchronized(lock) { sessions.remove(transactionId, session) }
        return result
    }

    private class PipeSession(
        transactionId: String,
        private val readSide: ParcelFileDescriptor,
        private val stagingFile: File,
        private val policy: ExplorerActionBoundedOutputPolicy,
    ) {

        private val stateLock = Any()
        private val finished = CountDownLatch(1)
        private var cancellationReason: String? = null

        @Volatile
        private var result: ExplorerActionOutputDrainResult? = null

        private val worker = Thread(
            ::drain,
            "ExplorerAction-output-${transactionId.take(8)}",
        ).apply { isDaemon = true }

        fun start() = worker.start()

        fun await(timeoutMillis: Long): ExplorerActionOutputDrainResult {
            if (!finished.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return ExplorerActionOutputDrainResult.TimedOut
            }
            return result ?: ExplorerActionOutputDrainResult.Failed(
                IOException("Explorer Action output drain ended without a result"),
            )
        }

        fun cancel(reason: String) {
            val shouldCancel = synchronized(stateLock) {
                if (result != null || cancellationReason != null) {
                    false
                } else {
                    cancellationReason = reason
                    true
                }
            }
            if (shouldCancel) {
                runCatching { readSide.closeWithError(reason.take(MAX_PIPE_ERROR_LENGTH)) }
                worker.interrupt()
            }
        }

        private fun drain() {
            var input: FileInputStream? = null
            try {
                input = FileInputStream(readSide.fileDescriptor)
                val copied = FileOutputStream(stagingFile, false).use { output ->
                    ExplorerActionBoundedOutputCopier(
                        policy = policy,
                        usableBytes = { stagingFile.parentFile?.usableSpace ?: 0L },
                    ).copy(input, output)
                        .also {
                            output.flush()
                            output.fd.sync()
                        }
                }
                finish(ExplorerActionOutputDrainResult.Completed(copied))
            } catch (error: Throwable) {
                val cancellation = synchronized(stateLock) { cancellationReason }
                if (cancellation == null) {
                    runCatching {
                        readSide.closeWithError(pipeErrorMessage(error).take(MAX_PIPE_ERROR_LENGTH))
                    }
                    finish(ExplorerActionOutputDrainResult.Failed(error))
                } else {
                    finish(ExplorerActionOutputDrainResult.Cancelled(cancellation))
                }
            } finally {
                runCatching { input?.close() }
                runCatching { readSide.close() }
                finished.countDown()
            }
        }

        private fun finish(candidate: ExplorerActionOutputDrainResult) {
            synchronized(stateLock) {
                if (result == null) {
                    result = cancellationReason?.let(ExplorerActionOutputDrainResult::Cancelled)
                        ?: candidate
                }
            }
        }

        private fun pipeErrorMessage(error: Throwable): String = when (error) {
            is ExplorerActionOutputCopyException -> error.message ?: COPY_REJECTED_ERROR
            else -> COPY_FAILED_ERROR
        }
    }

    private const val CANCEL_WAIT_MILLIS = 5_000L
    private const val MAX_PIPE_ERROR_LENGTH = 128
    private const val WRITER_EXISTS_ERROR = "Explorer Action output writer already exists"
    private const val SESSION_START_ERROR = "Explorer Action output writer could not start"
    private const val COPY_REJECTED_ERROR = "Explorer Action output was rejected"
    private const val COPY_FAILED_ERROR = "Explorer Action output write failed"
}
