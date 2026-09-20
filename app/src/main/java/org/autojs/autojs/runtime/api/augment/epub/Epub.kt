package org.autojs.autojs.runtime.api.augment.epub

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.epub.EpubBookOperations
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.autojs.runtime.api.epub.EpubService

/**
 * The global `epub` object (plugin roadmap P6.1, appendix A.1 and A.2): `open(path | options)` /
 * `openAsync` -> `EpubBook`, the convenience layer `metadata(path)`, `toc(path)`,
 * `readingOrder(path)`, `text(path, target?, options?)`, `cover(path, outputPath?, options?)`
 * and `search(path, query, options?)` (each opens the book, answers and closes it, with an
 * `Async` form), `isAvailable()` / `isAvailableAsync()` and the `EpubError` constructor. Paths
 * follow the script's working-directory rules (`files.path`); `content://` and other URIs are
 * refused. Argument shapes are checked in [EpubScriptArguments] (pure Kotlin); only the Rhino
 * conversion happens here.
 * zh-CN: 全局 `epub` 对象 (插件路线图 P6.1, 附录 A.1 与 A.2): `open(path | options)` / `openAsync` -> `EpubBook`, 便捷层 `metadata(path)`,
 * `toc(path)`, `readingOrder(path)`, `text(path, target?, options?)`, `cover(path, outputPath?, options?)` 与 `search(path, query, options?)`
 * (各自打开书籍, 应答后关闭, 带 `Async` 形态), `isAvailable()` / `isAvailableAsync()` 与 `EpubError` 构造器. 路径遵循脚本工作目录规则
 * (`files.path`); 拒绝 `content://` 等 URI. 参数形态在 [EpubScriptArguments] (纯 Kotlin) 检查; 这里只做 Rhino 转换.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Epub(
    private val scriptRuntime: ScriptRuntime,
    private val service: EpubService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("epub"))

    override val selfAssignmentFunctions = listOf(
        ::open.name,
        ::openAsync.name,
        ::metadata.name,
        ::metadataAsync.name,
        ::toc.name,
        ::tocAsync.name,
        ::readingOrder.name,
        ::readingOrderAsync.name,
        ::text.name,
        ::textAsync.name,
        ::cover.name,
        ::coverAsync.name,
        ::search.name,
        ::searchAsync.name,
        ::isAvailable.name,
        ::isAvailableAsync.name,
    )

    /** `EpubError` instances thrown by these functions reach the script unwrapped. zh-CN: 这些函数抛出的 `EpubError` 实例不经包装到达脚本. */
    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        listOf<Pair<Any, Any?>>(EpubJsErrors.NAME to EpubJsErrors.constructor(scriptRuntime))
    }

    @RhinoRuntimeFunctionInterface
    fun open(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.syncOf(scriptRuntime) { openWork(raw) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun openAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.asyncOf(scriptRuntime) { openWork(raw) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun metadata(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.syncOf(scriptRuntime) { withBook(raw) { book -> book.metadata() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun metadataAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.asyncOf(scriptRuntime) { withBook(raw) { book -> book.metadata() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun toc(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.syncOf(scriptRuntime) { withBook(raw) { book -> book.toc() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun tocAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.asyncOf(scriptRuntime) { withBook(raw) { book -> book.toc() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun readingOrder(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.syncOf(scriptRuntime) { withBook(raw) { book -> book.readingOrder() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun readingOrderAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsOnlyOne(args) { raw ->
            EpubCalls.asyncOf(scriptRuntime) { withBook(raw) { book -> book.readingOrder() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun text(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..3) { a ->
            EpubCalls.syncOf(scriptRuntime) { textWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun textAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..3) { a ->
            EpubCalls.asyncOf(scriptRuntime) { textWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun cover(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..3) { a ->
            EpubCalls.syncOf(scriptRuntime) { coverWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun coverAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..3) { a ->
            EpubCalls.asyncOf(scriptRuntime) { coverWork(a[0], a.getOrNull(1), a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun search(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            EpubCalls.syncOf(scriptRuntime) { searchWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun searchAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 2..3) { a ->
            EpubCalls.asyncOf(scriptRuntime) { searchWork(a[0], a[1], a.getOrNull(2)) }
        }
    }

    /** Whether a compatible, enabled EPUB plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的 EPUB 插件应答; 从不抛出. */
    @RhinoRuntimeFunctionInterface
    fun isAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            EpubCalls.syncOf(scriptRuntime) { { _ -> service.isAvailable() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun isAvailableAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            EpubCalls.asyncOf(scriptRuntime) { { _ -> service.isAvailable() } }
        }
    }

    /** Host failures and argument guards leave as `EpubError`; script values pass unchanged. zh-CN: 宿主失败与参数守卫以 `EpubError` 离开; 脚本值原样通过. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        EpubJsErrors.throwInto(scriptRuntime, t)
    }

    // ------------------------------------------------------------------ internals

    /** `epub.open`: the book stays open until `close()` or the script's exit. zh-CN: `epub.open`: 书籍保持打开直到 `close()` 或脚本退出. */
    private fun openWork(raw: Any?): EpubWork {
        val request = EpubScriptArguments.open(EpubCalls.json(raw))
        val path = service.resolvePath(request.path)
        return { _ ->
            val client = service.open(path, request.displayName)
            EpubPromises.OnScriptThread { EpubBookNativeObject(scriptRuntime, service, EpubBookOperations(client, path)) }
        }
    }

    /** The convenience layer: open, answer with [block], close. zh-CN: 便捷层: 打开, 用 [block] 应答, 关闭. */
    private fun withBook(raw: Any?, block: suspend (EpubBookOperations) -> Any?): EpubWork {
        val request = EpubScriptArguments.open(EpubCalls.json(raw))
        val path = service.resolvePath(request.path)
        return { _ ->
            val client = service.open(path, request.displayName)
            val book = EpubBookOperations(client, path)
            try {
                block(book)
            } finally {
                service.untrack(client)
                book.closeQuietly()
            }
        }
    }

    private fun textWork(raw: Any?, target: Any?, options: Any?): EpubWork {
        val request = EpubScriptArguments.text(EpubCalls.json(target), EpubCalls.json(options))
        return withBook(raw) { book -> book.text(request) }
    }

    private fun coverWork(raw: Any?, outputPath: Any?, options: Any?): EpubWork {
        val export = EpubScriptArguments.export(EpubCalls.json(options))
        val targetPath = EpubCalls.optionalTargetPath(service, outputPath, "outputPath")
        return withBook(raw) { book ->
            if (targetPath == null) {
                val bitmap = book.coverBitmap()
                EpubPromises.OnScriptThread { ImageWrapper.ofBitmap(scriptRuntime, bitmap) }
            } else {
                book.save(book.coverHref(), targetPath, export.overwrite)
            }
        }
    }

    private fun searchWork(raw: Any?, query: Any?, options: Any?): EpubWork {
        val request = EpubScriptArguments.search(EpubCalls.json(query), EpubCalls.json(options))
        return withBook(raw) { book -> book.search(request) }
    }
}
