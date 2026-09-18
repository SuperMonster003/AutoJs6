package org.autojs.autojs.core.plugin.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * One streamed download (`messages.raw`, `attachments.download`) end to end (roadmap P3.2): the
 * pipe of the [MailAttachmentSink] travels with the request, the host copy of its write end is
 * closed as soon as the plugin accepted the request, and the read end is drained into the target
 * while the call is in flight (draining afterwards would deadlock on a full pipe). A failed call
 * cancels the drain, the part file goes away with the sink, and the target exists only after
 * both the call and the copy succeeded.
 */
object MailDownloads {

    suspend fun download(
        session: MailSessionClient,
        op: String,
        argsJson: String,
        sink: MailAttachmentSink,
        expectedSize: Long?,
        onProgress: ((MailJson.Progress) -> Unit)? = null,
    ): File = coroutineScope {
        sink.use {
            val drain = async(Dispatchers.IO) { sink.await() }
            try {
                session.call(
                    op,
                    argsJson,
                    descriptors = arrayOf(sink.descriptor),
                    transferBytes = expectedSize,
                    onSubmitted = { sink.closeWriteEnd() },
                    onProgress = onProgress,
                )
            } catch (t: Throwable) {
                sink.cancel()
                drain.cancel()
                throw t
            }
            drain.await()
        }
    }
}
