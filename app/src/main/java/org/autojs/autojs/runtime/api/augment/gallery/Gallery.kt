package org.autojs.autojs.runtime.api.augment.gallery

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.plugin.gallery.GalleryOutputRequest
import org.autojs.autojs.core.plugin.gallery.GalleryPickPolicy
import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.api.gallery.GalleryScriptArguments
import org.autojs.autojs.runtime.api.gallery.GalleryScriptValues
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.util.RhinoUtils

/**
 * The global `gallery` object (3-Maple Image roadmap M6, 10.1): the picker, viewer, editor and
 * converter of the 3-Maple Image plugin for scripts. `pick(options?)` answers the chosen images or
 * null when the user cancelled, `view(paths, options?)` answers a `GalleryViewSession` as soon as the
 * viewer is launched (`viewAsync` once it is shown), `edit(path, options?)` answers the saved path or
 * null, and `convert(path, options?)` converts without UI and answers the written image; each has an
 * `Async` form, plus `isAvailable()` / `isAvailableAsync()` and the `GalleryError` constructor. Paths
 * follow the script's working-directory rules (`files.path`); `content://` and other URIs are
 * refused. The host reads the sources and writes the outputs, so a script reaches nothing through
 * the plugin that it could not open itself. Argument shapes are checked in [GalleryScriptArguments]
 * (pure Kotlin); only the Rhino conversion happens here.
 * zh-CN: 全局 `gallery` 对象 (3-Maple Image 路线图 M6, 10.1): 供脚本使用 3-Maple Image 插件的选图, 查看器, 编辑器与转换器. `pick(options?)` 应答选中的
 * 图片, 用户取消时为 null; `view(paths, options?)` 在查看器启动后立即应答 `GalleryViewSession` (`viewAsync` 在其显示后应答); `edit(path, options?)`
 * 应答保存的路径或 null; `convert(path, options?)` 无界面转换并应答写出的图片; 各自带 `Async` 形态, 另有 `isAvailable()` / `isAvailableAsync()` 与
 * `GalleryError` 构造器. 路径遵循脚本工作目录规则 (`files.path`); 拒绝 `content://` 等 URI. 源文件由宿主读取, 输出由宿主写入, 因此脚本无法借插件
 * 访问它自己打不开的文件. 参数形态在 [GalleryScriptArguments] (纯 Kotlin) 检查; 这里只做 Rhino 转换.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Gallery(
    private val scriptRuntime: ScriptRuntime,
    private val service: GalleryService,
) : Augmentable(scriptRuntime) {

    override val keys = listOf(AugmentableKey("gallery"))

    override val selfAssignmentFunctions = listOf(
        ::pick.name,
        ::pickAsync.name,
        ::view.name,
        ::viewAsync.name,
        ::edit.name,
        ::editAsync.name,
        ::convert.name,
        ::convertAsync.name,
        ::isAvailable.name,
        ::isAvailableAsync.name,
    )

    /** `GalleryError` instances thrown by these functions reach the script unwrapped. zh-CN: 这些函数抛出的 `GalleryError` 实例不经包装到达脚本. */
    override val scriptErrorPassThroughFunctions: Set<String>
        get() = selfAssignmentFunctions.toSet()

    override val selfAssignmentProperties: List<Pair<Any, Any?>> by lazy {
        listOf<Pair<Any, Any?>>(GalleryJsErrors.NAME to GalleryJsErrors.constructor(scriptRuntime))
    }

    /** `gallery.pick(options?)`: blocks until the user chose images or left the picker. zh-CN: `gallery.pick(options?)`: 阻塞直到用户选好图片或离开选择器. */
    @RhinoRuntimeFunctionInterface
    fun pick(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            GalleryCalls.blockingOf(scriptRuntime, "gallery.pick") { pickWork(a.getOrNull(0)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun pickAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsAtMost(args, 1) { a ->
            GalleryCalls.asyncOf(scriptRuntime) { pickWork(a.getOrNull(0)) }
        }
    }

    /** `gallery.view(paths, options?)`: the session whose events keep the script alive until `close`. zh-CN: `gallery.view(paths, options?)`: 事件让脚本活到 `close` 的会话. */
    @RhinoRuntimeFunctionInterface
    fun view(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.syncOf(scriptRuntime) { viewWork(a[0], a.getOrNull(1), awaitOpen = false) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun viewAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.asyncOf(scriptRuntime) { viewWork(a[0], a.getOrNull(1), awaitOpen = true) }
        }
    }

    /** `gallery.edit(path, options?)`: blocks until the user saved (the output path) or left the editor (null). zh-CN: `gallery.edit(path, options?)`: 阻塞直到用户保存 (输出路径) 或离开编辑器 (null). */
    @RhinoRuntimeFunctionInterface
    fun edit(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.blockingOf(scriptRuntime, "gallery.edit") { editWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun editAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.asyncOf(scriptRuntime) { editWork(a[0], a.getOrNull(1)) }
        }
    }

    /** `gallery.convert(path, options?)`: converts without UI and answers the written image. zh-CN: `gallery.convert(path, options?)`: 无界面转换并应答写出的图片. */
    @RhinoRuntimeFunctionInterface
    fun convert(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.blockingOf(scriptRuntime, "gallery.convert") { convertWork(a[0], a.getOrNull(1)) }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun convertAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsLengthInRange(args, 1..2) { a ->
            GalleryCalls.asyncOf(scriptRuntime) { convertWork(a[0], a.getOrNull(1)) }
        }
    }

    /** Whether a compatible, enabled gallery plugin answers right now; never throws. zh-CN: 当前是否有兼容且启用的相册插件应答; 从不抛出. */
    @RhinoRuntimeFunctionInterface
    fun isAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any? = guarded {
        ensureArgumentsIsEmpty(args) {
            GalleryCalls.syncOf(scriptRuntime) { { service.isAvailable() } }
        }
    }

    @RhinoRuntimeFunctionInterface
    fun isAvailableAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any = guarded {
        ensureArgumentsIsEmpty(args) {
            GalleryCalls.asyncOf(scriptRuntime) { { service.isAvailable() } }
        }
    }

    /** Host failures and argument guards leave as `GalleryError`; script values pass unchanged. zh-CN: 宿主失败与参数守卫以 `GalleryError` 离开; 脚本值原样通过. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        GalleryJsErrors.throwInto(scriptRuntime, t)
    }

    // ------------------------------------------------------------------ internals

    /** `gallery.pick`: the picker's items, kept only where the script can read them. zh-CN: `gallery.pick`: 选择器的条目, 只保留脚本可读的. */
    private fun pickWork(options: Any?): GalleryWork {
        val request = GalleryScriptArguments.pick(GalleryCalls.json(options))
        service.ensureOpen()
        return {
            val waiter = GalleryResultWaiter()
            service.track(waiter)
            try {
                val client = service.host { context -> GalleryPluginHost.openPicker(context, request, waiter) }
                waiter.attach(client)
                waiter.await()?.let { result -> GalleryScriptValues.picked(GalleryPickPolicy.accept(result.itemsJson)) }
            } finally {
                waiter.end()
                service.untrack(waiter)
            }
        }
    }

    /**
     * `gallery.view` / `viewAsync`: the dispatcher and the emitter are created on the script
     * thread, the plugin session opens in the coroutine (the viewer Activity starts from the app
     * context), and a failed open releases the emitter silently.
     * zh-CN: `gallery.view` / `viewAsync`: 分发器与发射器在脚本线程上创建, 插件会话在协程里打开 (查看器 Activity 从应用上下文启动), 打开失败时静默释放发射器.
     */
    private fun viewWork(paths: Any?, options: Any?, awaitOpen: Boolean): GalleryWork {
        val request = GalleryScriptArguments.view(GalleryCalls.json(paths), GalleryCalls.json(options))
        val resolved = request.paths.map(service::resolvePath)
        service.ensureOpen()
        val controller = GalleryViewSession.Controller(
            dispatcher = GalleryAsyncDispatcher(scriptRuntime),
            bridges = scriptRuntime.bridges,
            paths = resolved,
            scriptRuntime = scriptRuntime,
            convertValue = { value -> RhinoUtils.toJsValue(scriptRuntime.topLevelScope, value) },
        )
        val runner = GalleryViewRunner(
            controller,
            convertError = { error -> GalleryJsErrors.newGalleryError(scriptRuntime, error) },
            onFinished = { finished -> service.untrack(finished) },
        )
        try {
            service.track(runner)
        } catch (t: Throwable) {
            runner.cancelSilently()
            throw t
        }
        return {
            try {
                val client = service.host { context -> GalleryPluginHost.openViewer(context, resolved, request.index, runner) }
                runner.attach(client)
            } catch (t: Throwable) {
                service.untrack(runner)
                runner.cancelSilently()
                throw t
            }
            if (awaitOpen) runner.awaitOpen()
            controller.session
        }
    }

    /** `gallery.edit`: the output is staged by the host and published once the editor saved. zh-CN: `gallery.edit`: 输出由宿主暂存, 编辑器保存后发布. */
    private fun editWork(path: Any?, options: Any?): GalleryWork {
        val source = service.resolvePath(GalleryScriptArguments.path(GalleryCalls.json(path), "path"))
        val request = GalleryScriptArguments.edit(GalleryCalls.json(options))
        val output = GalleryOutputRequest(request.output?.let(service::resolvePath), request.overwrite)
        service.ensureOpen()
        return {
            val waiter = GalleryResultWaiter()
            service.track(waiter)
            try {
                val editor = service.host { context -> GalleryPluginHost.openEditor(context, source, output, request.encode, waiter) }
                try {
                    waiter.attach(editor.session)
                    waiter.await()?.let { result -> editor.publish(result).path }
                } finally {
                    // Finishes the editor and deletes the staging file unless it was published.
                    // zh-CN: 结束编辑器, 并删除未发布的暂存文件.
                    editor.close()
                }
            } finally {
                waiter.end()
                service.untrack(waiter)
            }
        }
    }

    /** `gallery.convert`: the host stages, reads back and publishes the converter's output. zh-CN: `gallery.convert`: 宿主暂存, 回读并发布转换器的输出. */
    private fun convertWork(path: Any?, options: Any?): GalleryWork {
        val source = service.resolvePath(GalleryScriptArguments.path(GalleryCalls.json(path), "path"))
        val request = GalleryScriptArguments.convert(GalleryCalls.json(options))
        val output = GalleryOutputRequest(request.output?.let(service::resolvePath), request.overwrite)
        service.ensureOpen()
        return {
            GalleryScriptValues.written(service.host { context -> GalleryPluginHost.convert(context, source, output, request.options) })
        }
    }
}
