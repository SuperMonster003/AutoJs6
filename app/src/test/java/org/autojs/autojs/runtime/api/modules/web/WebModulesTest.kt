package org.autojs.autojs.runtime.api.modules.web

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.http.Http
import org.autojs.autojs.runtime.api.augment.web.Web
import org.autojs.autojs.runtime.api.augment.web.WebSocket
import org.autojs.autojs.runtime.api.augment.web.WebSocketFields
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.runtime.api.Http as ApiHttp

/**
 * Roadmap P3.5: `web` and the global constructor `WebSocket` on the new script module layer. On the JVM: the obsolete
 * `__web__.js` members exist on the new module (its WebSocket was a JS constructor defined by the module), the surfaces
 * (module objects and the global names in their order, `http` / `web` / `WebSocket` mounted in `ScriptModuleLayer.ORDER`)
 * against the current `augment/web`, the WebSocket constants against the current `WebSocketFields`, and the argument
 * errors. Creating WebViews and sockets needs Android: those cases run on the device, the socket ones against the local
 * server of `tools/augment/device_net.py`.
 *
 * zh-CN: P3.5: 新层的 web 与全局构造器 WebSocket. JVM 上: 旧 __web__.js 的成员都在新模块上 (其 WebSocket 是模块定义的 JS 构造器), 表面 (模块对象与全局名称及
 * 顺序, http / web / WebSocket 按 ScriptModuleLayer.ORDER 挂载) 对照现有 augment/web, WebSocket 常量对照现有 WebSocketFields, 以及参数错误. 创建 WebView 与
 * 连接需要 Android, 这些用例在真机运行, 连接用例对 tools/augment/device_net.py 的本地服务运行.
 */
class WebModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("http", "web", "WebSocket")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) = HostModuleTestSupport.setField(side.ktRuntime, "http", ApiHttp())

    /** The current http, web and WebSocket modules in their order of `ScriptModuleLayer.ORDER` (consecutive there). */
    private fun mountCurrent(side: DifferentialRhino) {
        val global = side.ktScope.globalThis as ScriptableObject
        Http(side.ktRuntime).augment(global, side.ktRuntime.http)
        Web(side.ktRuntime).augment(global)
        WebSocket(side.ktRuntime).augment(global, WebSocketFields)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        rhino.evalJs("global.__asGlobal__ = function (o, names, scope) { names.forEach(function (n) { scope[n] = o[n].bind(o); }); };")
        host.loadAndDefine("util")
        val jsWeb = host.loadAndDefine("web")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsWeb, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt("web"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        listOf("newInjectableWebView", "newInjectableWebClient", "newWebSocket", "WebSocket").forEach { name ->
            assertEquals(name, "function", rhino.evalJs("typeof $name"))
            assertEquals(name, "function", rhino.evalKt("typeof $name"))
        }
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        HostModuleTestSupport.assertSameSurface(rhino, "web", ::prepare, ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "WebSocket", ::prepare, ::mountCurrent)
        listOf("newInjectableWebView", "newInjectableWebClient", "newWebSocket").forEach { name ->
            HostModuleTestSupport.assertSameSurface(rhino, name, ::prepare, ::mountCurrent)
        }
        // The global names of the three modules and their order, as ScriptRuntime.augment() mounts them.
        val expected = DifferentialRhino().use { current ->
            prepare(current)
            mountCurrent(current)
            current.evalKt(KEYS).toString()
        }
        assertEquals(expected, rhino.evalKt(KEYS).toString())
    }

    /** The constants resolve through the lookup prototype like the current `WebSocketFields`: same names and values, none listed. */
    @Test
    fun webSocketConstantsMatchTheCurrentFields() {
        assertEquals(WebSocketFields.exportedNames.sorted(), WebSocketConstants.VALUES.keys.sorted())
        WebSocketFields.exportedNames.forEach { name ->
            assertEquals(name, WebSocketFields.get(rhino.ktScope.globalThis, name), WebSocketConstants.VALUES[name])
        }
        HostModuleTestSupport.check(rhino, CONSTANTS)
        DEVICE.add(CONSTANTS)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
        NET.add(NETWORK)
    }

    companion object {

        private val DEVICE = DeviceCases("WebModulesTest", "new script module layer (P3.5)")
        private val NET = DeviceCases("WebModulesTest", "new script module layer (P3.5), local server of tools/augment/device_net.py", "build/script-modules-device-net")

        private const val KEYS = "Object.keys(globalThis).filter(function (k) { return /^(\\\$?(http|web)|WebSocket|new[A-Z]\\w+)\$/.test(k); }).join()"

        private val CONSTANTS = listOf(
            "[WebSocket.CODE_CLOSE_NORMAL, WebSocket.CODE_TLS_HANDSHAKE_FAIL, WebSocket.EVENT_OPEN, WebSocket.EVENT_MAX_REBUILDS]" to "[1000,1015,\"open\",\"max_rebuilds\"]",
            "[Object.keys(WebSocket).length, 'CODE_CLOSE_NORMAL' in WebSocket, WebSocket.hasOwnProperty('EVENT_TEXT'), typeof WebSocket.NO_SUCH_CONSTANT]" to "[0,false,false,\"undefined\"]",
            "typeof WebSocket.TAG" to "\"undefined\"",
            "[typeof \$web, typeof \$WebSocket]" to "[\"object\",\"undefined\"]",
        )

        private const val TYPE = "throws TypeError"

        private val ERRORS = listOf(
            "[web.newInjectableWebView.length, web.newInjectableWebClient.length, web.newWebSocket.length, WebSocket.length]" to "[0,0,1,1]",
            "newInjectableWebView(1, 2, 3)" to TYPE,
            "web.newInjectableWebClient(1)" to TYPE,
            "newWebSocket()" to TYPE,
            "web.newWebSocket(null)" to TYPE,
            "newWebSocket('ws://127.0.0.1:9/', 5)" to TYPE,
            "newWebSocket('ws://127.0.0.1:9/', [])" to TYPE,
            "newWebSocket('ws://127.0.0.1:9/', {}, 1)" to TYPE,
            "WebSocket('ws://127.0.0.1:9/')" to TYPE,
            "new WebSocket()" to TYPE,
            "new WebSocket(null)" to TYPE,
            "new WebSocket(http.__okhttp__, undefined)" to TYPE,
            "new WebSocket(http.__okhttp__, 'ws://127.0.0.1:9/', 3)" to TYPE,
            "new WebSocket(http.__okhttp__)" to TYPE,
            "new WebSocket(1)" to TYPE,
            "(function () { try { WebSocket('ws://127.0.0.1:9/'); } catch (e) { return String(e.message).indexOf('WebSocket') >= 0; } })()" to "true",
        )

        /**
         * Creates a WebView with [create] on the UI thread and returns its class name: Chromium binds every WebView of a
         * process to the thread that created the first one, so creating them on the varying script threads of
         * device_eval.py fails from the second run on (IllegalStateException, either implementation).
         */
        private fun onUiThread(create: String) =
            "(function () { var latch = new java.util.concurrent.CountDownLatch(1); var out = 'timeout'; ui.post(function () { " +
                "try { var w = $create; out = String(w.getClass().getName()); w.destroy(); } catch (e) { out = 'error ' + e; } latch.countDown(); }); " +
                "latch.await(10, java.util.concurrent.TimeUnit.SECONDS); return out; })()"

        private val DEVICE_ONLY = listOf(
            "String(newInjectableWebClient().getClass().getName())" to "\"org.autojs.autojs.core.web.InjectableWebClient\"",
            "web.newInjectableWebClient() !== web.newInjectableWebClient()" to "true",
            onUiThread("newInjectableWebView()") to "\"org.autojs.autojs.core.web.InjectableWebView\"",
            onUiThread("web.newInjectableWebView('about:' + 'blank')") to "\"org.autojs.autojs.core.web.InjectableWebView\"",
            onUiThread("newInjectableWebView(context, null)") to "\"org.autojs.autojs.core.web.InjectableWebView\"",
            "(function () { var ws = newWebSocket('ws://127.0.0.1:9/'); var r = [String(ws.getClass().getName()), String(ws.url)]; ws.cancel(); return r; })()" to "[\"org.autojs.autojs.core.web.WebSocket\",\"ws://127.0.0.1:9/\"]",
            "(function () { var ws = new WebSocket('ws://127.0.0.1:9/'); var r = [ws instanceof org.autojs.autojs.core.web.WebSocket, String(ws.url)]; ws.cancel(); return r; })()" to "[true,\"ws://127.0.0.1:9/\"]",
            "(function () { var ws = new WebSocket(http.__okhttp__, 'ws://127.0.0.1:9/'); var r = ws.client === http.__okhttp__; ws.cancel(); return r; })()" to "true",
            "(function () { var ws = new WebSocket({}, 'ws://127.0.0.1:9/'); var r = typeof ws.send; ws.cancel(); return r; })()" to "\"function\"",
        )

        private const val WS = "ws://127.0.0.1:18735/ws"

        /** Waits for one text message after sending [text] on open; the listeners run on OkHttp's threads (no `eventThread`). */
        private fun echo(create: String, text: String) =
            "(function () { var latch = new java.util.concurrent.CountDownLatch(1); var got = []; var ws = $create; " +
                "ws.on('open', function () { got.push('open'); ws.send('$text'); }); ws.on('text', function (t) { got.push(String(t)); latch.countDown(); }); " +
                "latch.await(10, java.util.concurrent.TimeUnit.SECONDS); ws.close(1000, 'done'); return got; })()"

        private val NETWORK = listOf(
            echo("web.newWebSocket('$WS')", "ping") to "[\"open\",\"echo:ping\"]",
            echo("newWebSocket('$WS', { eventThread: 'other' })", "pong") to "[\"open\",\"echo:pong\"]",
            "(function () { var ws = new WebSocket('$WS'); var r = ws instanceof org.autojs.autojs.core.web.WebSocket; ws.close(1000, 'done'); return r; })()" to "true",
        )

    }

}
