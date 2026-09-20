package org.autojs.autojs.runtime.api.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.epub.EpubBookClient
import org.autojs.autojs.core.plugin.epub.EpubErrorMapper
import org.autojs.autojs.core.plugin.epub.EpubOutputSink
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * The operations of one open book behind the script objects (plugin roadmap P6.1, appendix A.3),
 * on top of [EpubBookClient]: the immutable documents (`metadata`, `toc` as a tree,
 * `readingOrder`, `positions`) are fetched once and cached, text is assembled by
 * [EpubTextAssembler], resources are exported through [EpubOutputSink] or decoded into a
 * `Bitmap` for the cover. Every Binder call runs on the IO dispatcher under the contract's call
 * timeout, so a stopped script cancels the waiting side at once. No Rhino here; the script objects
 * convert the plain values and build the `Image` on the script thread.
 * zh-CN: 脚本对象背后一本已打开书籍的操作 (插件路线图 P6.1, 附录 A.3), 基于 [EpubBookClient]: 不变文档 (`metadata`, 树形 `toc`, `readingOrder`,
 * `positions`) 取一次并缓存, 文本由 [EpubTextAssembler] 拼装, 资源经 [EpubOutputSink] 导出或解码为封面 `Bitmap`. 每个 Binder 调用都在 IO 分发器上
 * 并受契约调用超时约束, 因此脚本停止时等待方立即取消. 这里没有 Rhino; 脚本对象在脚本线程上转换普通值并构建 `Image`.
 */
internal class EpubBookOperations(
    internal val client: EpubBookClient,
    val path: String,
) {

    private val metadataCache = AtomicReference<Map<String, Any?>?>()
    private val tocCache = AtomicReference<List<Map<String, Any?>>?>()
    private val readingOrderCache = AtomicReference<List<Any?>?>()
    private val positionsCache = AtomicReference<Int?>()

    val isClosed: Boolean
        get() = client.isClosed

    suspend fun metadata(): Map<String, Any?> =
        metadataCache.get() ?: call { EpubScriptValues.map(client.metadata()) }.also { metadataCache.set(it) }

    /** The `{ title, href, children }` tree of appendix A.2. zh-CN: 附录 A.2 的 `{ title, href, children }` 树. */
    suspend fun toc(): List<Map<String, Any?>> =
        tocCache.get() ?: call { EpubScriptValues.tocTree(client.toc().json) }.also { tocCache.set(it) }

    suspend fun readingOrder(): List<Any?> =
        readingOrderCache.get() ?: call { EpubScriptValues.list(client.readingOrder().json) }.also { readingOrderCache.set(it) }

    suspend fun positions(): Int =
        positionsCache.get() ?: call { client.positions() }.also { positionsCache.set(it) }

    /** One resource or the whole book as text (appendix A.3). zh-CN: 单个资源或全书的文本 (附录 A.3). */
    suspend fun text(request: EpubScriptArguments.Text): String {
        if (request.wholeBook) {
            val count = readingOrder().size
            return call(timeoutMillis = EpubContract.CALL_TIMEOUT_MS * maxOf(1, count)) {
                EpubTextAssembler.book(count, request.maxChars, { index ->
                    EpubTextAssembler.ChunkSource { offset, maxChars -> client.text(index = index, offset = offset, maxChars = maxChars, format = request.format) }
                })
            }
        }
        return call {
            EpubTextAssembler.resource(request.offset, request.maxChars, { offset, maxChars ->
                client.text(href = request.href, index = request.index, offset = offset, maxChars = maxChars, format = request.format)
            })
        }
    }

    suspend fun search(request: EpubScriptArguments.Search): List<Any?> =
        call { EpubScriptValues.list(client.search(request.query, request.offset, request.limit).resultsJson) }

    /** The href of the cover resource; `RESOURCE_NOT_FOUND` when the package declares none. zh-CN: 封面资源的 href; 包未声明时为 `RESOURCE_NOT_FOUND`. */
    suspend fun coverHref(): String =
        (metadata()[EpubContract.FIELD_COVER] as? String)?.takeIf { it.isNotBlank() }
            ?: throw failure(EpubErrorCodes.RESOURCE_NOT_FOUND, "the book has no cover image")

    /** The cover decoded as a bitmap (`cover()` without a path). zh-CN: 解码为位图的封面 (`cover()` 无路径形态). */
    suspend fun coverBitmap(): Bitmap {
        val href = coverHref()
        val bytes = bytes(href)
        return withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?: throw failure(EpubErrorCodes.PARSE_FAILED, "the cover image could not be decoded: $href")
    }

    /**
     * Exports [href] into [targetPath] (a file path, or a directory that receives the resource's
     * file name) and returns the final path.
     * zh-CN: 把 [href] 导出到 [targetPath] (文件路径, 或接收资源文件名的目录) 并返回最终路径.
     */
    suspend fun save(href: String, targetPath: String, overwrite: Boolean): String {
        val descriptor = call { client.resource(href) }
        val target = try {
            EpubOutputSink.resolveTarget(targetPath, fileNameOf(href), overwrite)
        } catch (t: Throwable) {
            runCatching { descriptor.close() }
            throw t
        }
        return EpubOutputSink.save(descriptor, target).path
    }

    /** All bytes of [href], bounded by the contract's resource ceiling. zh-CN: [href] 的全部字节, 受契约资源上限约束. */
    suspend fun bytes(href: String): ByteArray {
        val descriptor = call { client.resource(href) }
        return withContext(Dispatchers.IO) { drain(descriptor, href) }
    }

    /** Closes the plugin book; never throws and survives a cancelled caller. zh-CN: 关闭插件书籍; 从不抛出, 且调用方被取消时仍执行. */
    suspend fun closeQuietly() {
        withContext(NonCancellable + Dispatchers.IO) { runCatching { client.close() } }
    }

    private suspend fun <T> call(timeoutMillis: Long = EpubContract.CALL_TIMEOUT_MS, block: () -> T): T =
        withContext(Dispatchers.IO) { withTimeout(timeoutMillis) { block() } }

    private fun drain(descriptor: ParcelFileDescriptor, href: String): ByteArray {
        try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size().toLong() + n > EpubContract.MAX_RESOURCE_BYTES) {
                        throw failure(EpubErrorCodes.LIMIT_EXCEEDED, "resource exceeds ${EpubContract.MAX_RESOURCE_BYTES} bytes: $href")
                    }
                    out.write(buffer, 0, n)
                }
            }
            try {
                descriptor.checkError()
            } catch (e: IOException) {
                throw failure(EpubErrorCodes.IO, "the plugin ended the resource stream with an error: ${e.message}")
            }
            return out.toByteArray()
        } catch (e: EpubPluginException) {
            throw e
        } catch (e: IOException) {
            throw failure(EpubErrorCodes.IO, "reading $href failed: ${e.message}")
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun fileNameOf(href: String): String =
        href.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')

    private fun failure(code: String, message: String): EpubPluginException =
        EpubPluginException(EpubErrorMapper.normalize(code, message))
}
