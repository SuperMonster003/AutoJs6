package org.autojs.autojs.engine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryErrorMapper
import org.autojs.autojs.core.plugin.gallery.GalleryEvent
import org.autojs.autojs.core.plugin.gallery.GalleryOutputRequest
import org.autojs.autojs.core.plugin.gallery.GalleryPickPolicy
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.autojs.core.plugin.gallery.GallerySessionClient
import org.autojs.autojs.core.plugin.gallery.GallerySessionSink
import org.autojs.autojs.runtime.api.gallery.GalleryScriptArguments
import org.autojs.autojs.runtime.api.gallery.GalleryScriptValues
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.modules.gallery.GalleryResultWaiter
import org.autojs.autojs.runtime.api.modules.gallery.GalleryViewSession
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host side of the `gallery` script API for the JS runtimes (api-coverage Q81 (a)): the same argument rules, plugin sessions and
 * [GalleryService] ownership as Rhino's `gallery`, Node and Bun sharing it. The picker and the editor wait for the user in an
 * operation of the client; the viewer answers its session and then follows the plugin with events, like an EPUB reader:
 * `open`, `page`, `error` and the terminal `close`. Closing the client (the script or Worker ended) leaves the viewer to the user
 * and finishes waiting pickers and editors, as at Rhino's script exit.
 *
 * zh-CN: JS 运行时的 gallery 脚本 API 宿主侧 (api-coverage Q81 (a)): 参数规则, 插件会话与 [GalleryService] 属主同 Rhino 的 gallery, Node 与 Bun 共用. 选择器与编辑器在客户端的一个操作里等待用户;
 * 查看器应答其会话后像 EPUB 阅读器一样以事件跟随插件: open, page, error 与终结的 close. 关闭客户端 (脚本或 Worker 结束) 时把查看器留给用户并结束等待中的选择器与编辑器, 同 Rhino 的脚本退出.
 */
internal class RuntimeGalleryBridge(private val context: Context, private val resolve: (String) -> File?) :
    RuntimeOperationClients<RuntimeGalleryBridge.Session>("gallery", "GALLERY") {

    override val operations = setOf("isAvailable", "pick", "edit", "convert", "view", "viewAsync", "view.close")

    override fun newSession() = Session()

    override fun errorOf(error: Throwable): Pair<String, String> =
        GalleryErrorMapper.fromThrowable(error, GalleryErrorMapper.Phase.CALL).let { it.code to it.message }

    override fun onCancel(client: Client, id: String) {
        client.session.viewers[id]?.close(finish = true)
    }

    /** What one client owns: the [GalleryService] of its sessions and its viewers. zh-CN: 一个客户端拥有的对象: 其会话的 [GalleryService] 与查看器. */
    inner class Session : java.io.Closeable {
        val service = GalleryService(context) { path -> resolve(path)?.path ?: path }
        val viewers = ConcurrentHashMap<String, Viewer>()

        override fun close() = service.close()
    }

    override suspend fun invoke(client: Client, id: String, method: String, args: JSONArray): Any? {
        val service = client.session.service
        return when (method) {
            "isAvailable" -> service.isAvailable()
            "pick" -> pick(service, args)
            "edit" -> edit(service, args)
            "convert" -> convert(service, args)
            "view", "viewAsync" -> view(client, id, args, awaitOpen = method == "viewAsync")
            "view.close" -> {
                val finish = GalleryScriptArguments.close(args.json(1)).finish
                client.session.viewers[args.getString(0)]?.let { viewer ->
                    withContext(Dispatchers.IO) { withTimeout(GalleryContract.CALL_TIMEOUT_MS) { viewer.close(finish) } }
                }
                null
            }
            else -> throw IllegalArgumentException("Unknown gallery operation: $method")
        }
    }

    private suspend fun pick(service: GalleryService, args: JSONArray): Any? {
        val request = GalleryScriptArguments.pick(args.json(0))
        val waiter = GalleryResultWaiter()
        service.track(waiter)
        try {
            val client = service.host { context -> GalleryPluginHost.openPicker(context, request, waiter) }
            waiter.attach(client)
            return waiter.await()?.let { result -> GalleryScriptValues.picked(GalleryPickPolicy.accept(result.itemsJson)) }
        } finally {
            waiter.end()
            service.untrack(waiter)
        }
    }

    private suspend fun edit(service: GalleryService, args: JSONArray): Any? {
        val source = service.resolvePath(GalleryScriptArguments.path(args.json(0), "path"))
        val request = GalleryScriptArguments.edit(args.json(1))
        val output = GalleryOutputRequest(request.output?.let(service::resolvePath), request.overwrite)
        val waiter = GalleryResultWaiter()
        service.track(waiter)
        try {
            val editor = service.host { context -> GalleryPluginHost.openEditor(context, source, output, request.encode, waiter) }
            try {
                waiter.attach(editor.session)
                return waiter.await()?.let { result -> editor.publish(result).path }
            } finally {
                editor.close()
            }
        } finally {
            waiter.end()
            service.untrack(waiter)
        }
    }

    private suspend fun convert(service: GalleryService, args: JSONArray): Any? {
        val source = service.resolvePath(GalleryScriptArguments.path(args.json(0), "path"))
        val request = GalleryScriptArguments.convert(args.json(1))
        val output = GalleryOutputRequest(request.output?.let(service::resolvePath), request.overwrite)
        return GalleryScriptValues.written(service.host { context -> GalleryPluginHost.convert(context, source, output, request.options) })
    }

    private suspend fun view(client: Client, id: String, args: JSONArray, awaitOpen: Boolean): Any? {
        val service = client.session.service
        val request = GalleryScriptArguments.view(args.json(0), args.json(1))
        val resolved = request.paths.map(service::resolvePath)
        val viewer = Viewer(client, id, resolved)
        service.track(viewer)
        client.session.viewers[id] = viewer
        try {
            viewer.attach(service.host { context -> GalleryPluginHost.openViewer(context, resolved, request.index, viewer) })
        } catch (error: Throwable) {
            viewer.cancelSilently()
            throw error
        }
        if (awaitOpen) viewer.awaitOpen()
        return mapOf("id" to id, "paths" to resolved)
    }

    /**
     * One viewer session: the events of [org.autojs.autojs.runtime.api.modules.gallery.GalleryViewRunner], delivered to the
     * operation's event stream. A script that stops draining gets `error(LIMIT_EXCEEDED)` and `close(overflow)`.
     * zh-CN: 一个查看器会话: GalleryViewRunner 的事件, 投递到操作的事件流. 脚本不再取事件时得到 error(LIMIT_EXCEEDED) 与 close(overflow).
     */
    inner class Viewer(private val client: RuntimeOperationClients<Session>.Client, private val id: String, private val paths: List<String>) : GallerySessionSink, GalleryService.Task {

        private val finished = AtomicBoolean(false)
        private val opened = CompletableDeferred<Unit>()

        @Volatile
        private var plugin: GallerySessionClient? = null

        @Volatile
        private var reported: GalleryError? = null

        fun attach(session: GallerySessionClient) {
            plugin = session
            if (finished.get()) runCatching { session.close(finish = false) }
        }

        suspend fun awaitOpen() = opened.await()

        /** Script exit: the plugin session ends, the viewer stays, no event is emitted. zh-CN: 脚本退出: 插件会话结束, 查看器保留, 不发出事件. */
        override fun cancelSilently() {
            if (!finished.compareAndSet(false, true)) return
            opened.cancel()
            runCatching { plugin?.close(finish = false) }
            forget()
        }

        fun close(finish: Boolean) {
            val bound = plugin ?: return
            bound.close(finish)
            end(GalleryContract.REASON_HOST)
        }

        override fun onEvent(event: GalleryEvent) {
            if (finished.get()) return
            if (client.pending(id) >= MAX_PENDING_EVENTS) return overflow()
            when (event.type) {
                GalleryContract.EVENT_OPEN -> {
                    send(linkedMapOf("type" to GalleryContract.EVENT_OPEN, GalleryViewSession.FIELD_COUNT to paths.size))
                    opened.complete(Unit)
                }
                GalleryContract.EVENT_PAGE -> event.index?.takeIf { it in paths.indices }?.let { index ->
                    send(linkedMapOf("type" to GalleryContract.EVENT_PAGE, GalleryViewSession.FIELD_INDEX to index, GalleryViewSession.FIELD_PATH to paths[index]))
                }
                GalleryContract.EVENT_ERROR -> GalleryErrorMapper.normalize(event.errorCode, event.errorMessage).let { error ->
                    reported = error
                    send(errorEvent(error))
                }
                GalleryContract.EVENT_CLOSE -> end(event.reason ?: GalleryContract.REASON_ERROR)
            }
        }

        private fun end(reason: String) {
            if (!finished.compareAndSet(false, true)) return
            send(closeEvent(reason))
            if (!opened.isCompleted) opened.completeExceptionally(GalleryPluginException(GalleryViewSession.closedBeforeOpen(reason, reported)))
            runCatching { plugin?.release() }
            forget()
        }

        private fun overflow() {
            if (!finished.compareAndSet(false, true)) return
            val error = GalleryErrorMapper.normalize(GalleryErrorCodes.LIMIT_EXCEEDED, "the script did not drain the viewer events")
            send(errorEvent(error))
            send(closeEvent(GalleryViewSession.REASON_OVERFLOW))
            if (!opened.isCompleted) opened.completeExceptionally(GalleryPluginException(error))
            runCatching { plugin?.close(finish = false) }
            forget()
        }

        private fun send(value: Map<String, Any?>) = client.emit(id, "event", value)

        private fun errorEvent(error: GalleryError) = linkedMapOf("type" to GalleryContract.EVENT_ERROR, "code" to error.code, "message" to error.message)

        private fun closeEvent(reason: String) = linkedMapOf("type" to GalleryContract.EVENT_CLOSE, GalleryViewSession.FIELD_REASON to reason)

        private fun forget() {
            client.session.viewers.remove(id)
            client.session.service.untrack(this)
        }
    }
}
