package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsIsEmpty
import org.autojs.autojs.rhino.ArgumentGuards.Companion.ensureArgumentsLengthInRange
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.epub.EpubBookOperations
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.mozilla.javascript.NativeObject
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The script-visible `EpubBook` (plugin roadmap P6.1, appendix A.3): `path`, `metadata`, `toc`
 * (tree), `readingOrder`, `positions` and `isClosed` as properties (the documents are fetched on
 * first access and then stable), `text(target?, options?)`, `textAll(options?)`,
 * `cover(outputPath?, options?)` (an `Image` without a path), `resource(href, outputPath,
 * options?)`, `search(query, options?)`, `annotations()` (the reader's highlights and notes, read
 * live; plugin roadmap P9.4, contract version 2) with their `Async` forms, and `close()`.
 * Argument shapes are normalized in [EpubScriptArguments] (pure Kotlin); the work runs in
 * [EpubBookOperations]. The script's exit closes a book that is still open.
 * zh-CN: 脚本可见的 `EpubBook` (插件路线图 P6.1, 附录 A.3): 属性 `path`, `metadata`, `toc` (树), `readingOrder`, `positions` 与 `isClosed` (文档
 * 首次访问时获取, 之后稳定), 方法 `text(target?, options?)`, `textAll(options?)`, `cover(outputPath?, options?)` (无路径时为 `Image`),
 * `resource(href, outputPath, options?)`, `search(query, options?)`, `annotations()` (阅读器的高亮与笔记, 实时读取; 插件路线图 P9.4, 契约版本 2)
 * 及其 `Async` 形态, 以及 `close()`. 参数形态在 [EpubScriptArguments] (纯 Kotlin) 规范化; 工作在 [EpubBookOperations] 里运行. 脚本退出时关闭仍打开的书.
 */
class EpubBookNativeObject internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val service: EpubService,
    internal val operations: EpubBookOperations,
) : NativeObject(), Closeable {

    private val closed = AtomicBoolean(false)
    private val metadataJs = AtomicReference<Any?>()
    private val tocJs = AtomicReference<Any?>()
    private val readingOrderJs = AtomicReference<Any?>()
    private val positionsJs = AtomicReference<Any?>()

    init {
        RhinoUtils.initNativeObjectPrototype(this)
        defineProperty("path", operations.path, READONLY or PERMANENT)
        defineProperty("isClosed", { isClosed }, null, READONLY or PERMANENT)
        defineProperty("metadata", { cached(metadataJs) { operations.metadata() } }, null, READONLY or PERMANENT)
        defineProperty("toc", { cached(tocJs) { operations.toc() } }, null, READONLY or PERMANENT)
        defineProperty("readingOrder", { cached(readingOrderJs) { operations.readingOrder() } }, null, READONLY or PERMANENT)
        defineProperty("positions", { cached(positionsJs) { operations.positions() } }, null, READONLY or PERMANENT)

        // Appendix A.3, in table order. zh-CN: 附录 A.3, 按表序.
        defineOp("text", 0..2) { a -> textWork(a.getOrNull(0), a.getOrNull(1)) }
        defineOp("textAll", 0..1) { a -> textWork(null, a.getOrNull(0)) }
        defineOp("cover", 0..2) { a -> coverWork(a.getOrNull(0), a.getOrNull(1)) }
        defineOp("resource", 2..3) { a -> resourceWork(a[0], a[1], a.getOrNull(2)) }
        defineOp("search", 1..2) { a -> searchWork(a[0], a.getOrNull(1)) }
        defineOp("annotations", 0..0) { _ -> annotationsWork() }
        defineFunction("close") { args ->
            ensureArgumentsIsEmpty(args) {
                close()
                UNDEFINED
            }
        }
        defineFunction("toString") { describe() }
    }

    override fun getClassName(): String = "EpubBook"

    val path: String
        get() = operations.path

    val isClosed: Boolean
        get() = closed.get() || operations.isClosed

    fun describe(): String = "EpubBook { path: $path${if (isClosed) ", closed" else ""} }"

    // ------------------------------------------------------------------ work builders

    internal fun textWork(target: Any?, options: Any?): EpubWork {
        val request = EpubScriptArguments.text(EpubCalls.json(target), EpubCalls.json(options))
        return { _ -> operations.text(request) }
    }

    internal fun coverWork(outputPath: Any?, options: Any?): EpubWork {
        val export = EpubScriptArguments.export(EpubCalls.json(options))
        val targetPath = EpubCalls.optionalTargetPath(service, outputPath, "outputPath")
        return { _ ->
            if (targetPath == null) {
                val bitmap = operations.coverBitmap()
                EpubPromises.OnScriptThread { ImageWrapper.ofBitmap(scriptRuntime, bitmap) }
            } else {
                operations.save(operations.coverHref(), targetPath, export.overwrite)
            }
        }
    }

    internal fun resourceWork(href: Any?, outputPath: Any?, options: Any?): EpubWork {
        val resourceHref = EpubScriptArguments.href(EpubCalls.json(href), "href")
        val export = EpubScriptArguments.export(EpubCalls.json(options))
        val targetPath = EpubCalls.targetPath(service, outputPath, "outputPath")
        return { _ -> operations.save(resourceHref, targetPath, export.overwrite) }
    }

    internal fun searchWork(query: Any?, options: Any?): EpubWork {
        val request = EpubScriptArguments.search(EpubCalls.json(query), EpubCalls.json(options))
        return { _ -> operations.search(request) }
    }

    /** `annotations()`: never cached, the reader may change the list while the book is open. zh-CN: `annotations()`: 不缓存, 书打开期间阅读器可能改变列表. */
    internal fun annotationsWork(): EpubWork = { _ -> operations.annotations() }

    // ------------------------------------------------------------------ lifecycle

    /** Closes the plugin book; idempotent. zh-CN: 关闭插件书籍; 幂等. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        service.untrack(operations.client)
        try {
            EpubCalls.sync(scriptRuntime) { _ ->
                operations.closeQuietly()
                UNDEFINED
            }
        } catch (t: Throwable) {
            EpubJsErrors.throwInto(scriptRuntime, t)
        }
    }

    private fun ensureOpen() {
        if (isClosed) throw EpubJsErrors.failure(EpubErrorCodes.SESSION_CLOSED, "the book is closed")
    }

    private fun sync(work: EpubWork): Any? {
        ensureOpen()
        return EpubCalls.sync(scriptRuntime, work)
    }

    private fun async(work: EpubWork): Any {
        runCatching { ensureOpen() }.exceptionOrNull()?.let { return EpubPromises.rejected(scriptRuntime, it) }
        return EpubCalls.async(scriptRuntime, work)
    }

    /** A document fetched once and kept as the same script object afterwards. zh-CN: 取一次并在之后保持为同一脚本对象的文档. */
    private fun cached(holder: AtomicReference<Any?>, fetch: suspend () -> Any?): Any? {
        holder.get()?.let { return it }
        val value = sync { _ -> fetch() }
        holder.compareAndSet(null, value)
        return holder.get()
    }

    // ------------------------------------------------------------------ definition helpers

    /** Defines `name` (sync) and `nameAsync`; argument errors throw or reject as `EpubError`. zh-CN: 定义 `name` (同步) 与 `nameAsync`; 参数错误以 `EpubError` 抛出或拒绝. */
    private fun defineOp(name: String, arity: IntRange, build: (Array<Any?>) -> EpubWork) {
        defineFunction(name) { args -> ensureArgumentsLengthInRange(args, arity) { a -> sync(build(a)) } }
        defineFunction("${name}Async") { args ->
            val work = try {
                ensureArgumentsLengthInRange(args, arity) { a -> build(a) }
            } catch (t: Throwable) {
                return@defineFunction EpubPromises.rejected(scriptRuntime, t)
            }
            async(work)
        }
    }

    private fun defineFunction(name: String, body: (Array<out Any?>) -> Any?) =
        EpubCalls.defineFunction(scriptRuntime, this, name, body)
}
