package org.autojs.autojs.runtime.api.modules.http

import org.autojs.autojs.runtime.api.augment.differential.DifferentialCases
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.runtime.api.Http as ApiHttp

/**
 * Roadmap P3.5: `http` on the new script module layer. On the JVM: the obsolete `__http__.js` members exist on the new
 * module, `buildRequest` against the obsolete JS (the only part that runs without Android: the request functions ask
 * Android's Looper whether they run on the UI thread), the surface and `buildRequest` against the current
 * `augment/http/Http`, and the argument errors (D10, raised before any request). The requests themselves run on the
 * device against a local server on the PC (`tools/augment/device_net.py`, cases in
 * `build/script-modules-device-net/HttpModuleTest.json`).
 *
 * zh-CN: P3.5: 新层的 http. JVM 上: 旧 __http__.js 的成员都在新模块上, buildRequest 对照旧 JS (唯一不需要 Android 的部分: 请求函数要经 Android 的 Looper 判断
 * 是否在 UI 线程), 表面与 buildRequest 对照现有 augment/http/Http, 以及参数错误 (D10, 在发出请求前抛出). 请求本身在真机上对 PC 的本地服务运行
 * (tools/augment/device_net.py, 用例在 build/script-modules-device-net/HttpModuleTest.json).
 */
class HttpModuleTest {

    private lateinit var rhino: DifferentialRhino
    private lateinit var jsHttp: Any

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        jsHttp = ObsoleteModuleHost(rhino.context, rhino.jsScope).loadAndDefine("http")!!
        prepare(rhino)
        rhino.mountNewModules("http")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) = HostModuleTestSupport.setField(side.ktRuntime, "http", ApiHttp())

    private fun mountCurrent(side: DifferentialRhino) {
        Http(side.ktRuntime).augment(side.ktScope.globalThis as ScriptableObject, side.ktRuntime.http)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val report = SurfaceDiff.compare(SurfaceDiff.surface(jsHttp, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt("http"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "http", ::prepare, ::mountCurrent)
    }

    @Test
    fun buildRequestAgreesWithTheObsoleteModule() {
        DifferentialCases.run(rhino, BUILD_REQUESTS.map { it.first }, KNOWN_OBSOLETE, exportDir = "build/script-modules-device", exportAll = true)
    }

    @Test
    fun resultsMatchTheCurrentModule() {
        val compared = HostModuleTestSupport.assertSameResults(rhino, BUILD_REQUESTS.map { it.first } + CURRENT_ONLY.map { it.first }, KNOWN_CURRENT, ::prepare, ::mountCurrent)
        assertTrue("compared $compared", compared == BUILD_REQUESTS.size + CURRENT_ONLY.size)
        HostModuleTestSupport.check(rhino, BUILD_REQUESTS + CURRENT_ONLY)
        DEVICE.add(CURRENT_ONLY)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun networkCases() {
        NET.add(NETWORK)
    }

    companion object {

        private val DEVICE = DeviceCases("HttpModuleTest", "new script module layer (P3.5)")
        private val NET = DeviceCases("HttpModuleTest", "new script module layer (P3.5), local server of tools/augment/device_net.py", "build/script-modules-device-net")

        /** The fields of a built okhttp3.Request. */
        private fun request(call: String) =
            "(function (r) { var b = r.body(); return [String(r.method()), String(r.url()), r.header('X-A') === null ? null : String(r.header('X-A')), " +
                "b === null ? null : String(b.contentType()), b === null ? null : Number(b.contentLength())]; })($call)"

        private val BUILD_REQUESTS = listOf(
            request("http.buildRequest('https://example.com/a?b=1', { method: 'GET' })") to "[\"GET\",\"https://example.com/a?b=1\",null,null,null]",
            request("http.buildRequest('example.com/x', { method: 'GET', headers: { 'X-A': 'one' } })") to "[\"GET\",\"http://example.com/x\",\"one\",null,null]",
            request("http.buildRequest('http://example.com/', { method: 'POST', body: 'hello', contentType: 'text/plain' })") to "[\"POST\",\"http://example.com/\",null,\"text/plain; charset=utf-8\",5]",
            request("http.buildRequest('http://example.com/', { method: 'PUT', headers: { 'X-A': ['1', '2'] }, body: 'x', contentType: 'application/json' })") to "[\"PUT\",\"http://example.com/\",\"2\",\"application/json; charset=utf-8\",1]",
            request("http.buildRequest('http://example.com/', { method: 'DELETE' })") to "[\"DELETE\",\"http://example.com/\",null,null,null]",
            "http.buildRequest('http://example.com/', { method: 'POST' })" to "throws JavaException IllegalArgumentException",
            "http.buildRequest('http://example.com/', { method: 'GET', body: 'x' })" to "throws JavaException IllegalArgumentException",
            "http.buildRequest('http://example.com/', { method: 'POST', body: 1 })" to "throws InternalError",
            "http.buildRequest('http:// bad', { method: 'GET' })" to "throws JavaException IllegalArgumentException",
        )

        /** Members the obsolete module did not have (6.7.0), against the current module only. */
        private val CURRENT_ONLY = listOf(
            "typeof http.client()" to "\"object\"",
            "http.client() === http.client()" to "true",
            "String(http.client().getClass().getName()).length > 0" to "true",
            "String(http.__okhttp__.getClass().getName())" to "\"org.autojs.autojs.core.http.MutableOkHttp\"",
            "Object.keys(http).indexOf('__okhttp__')" to "-1",
            "Object.keys(http).length" to "20",
            "[typeof http.del, http.del === http.delete, typeof http.delAsync, http.delAsync === http.deleteAsync]" to "[\"function\",false,\"function\",false]",
        )

        private const val TYPE = "throws TypeError"

        /** Argument errors, raised before any request. */
        private val ERRORS = listOf(
            "[http.buildRequest.length, http.request.length, http.get.length, http.post.length, http.postJson.length, http.postMultipart.length, http.client.length]" to "[2,2,1,1,2,2,0]",
            "http.client(1)" to TYPE,
            "http.buildRequest()" to TYPE,
            "http.buildRequest('http://example.com/')" to TYPE,
            "http.buildRequest(null, { method: 'GET' })" to TYPE,
            "http.buildRequest('http://example.com/', 'GET')" to TYPE,
            "http.buildRequest('http://example.com/', {})" to TYPE,
            "http.buildRequest('http://example.com/', { method: null })" to TYPE,
            "http.buildRequest('http://example.com/', { method: 'GET' }, 1)" to TYPE,
            "http.request('http://127.0.0.1:9/')" to TYPE,
            "http.request('http://127.0.0.1:9/', null)" to TYPE,
            "http.request('http://127.0.0.1:9/', { method: 'GET' }, 'callback')" to TYPE,
            "http.requestAsync('http://127.0.0.1:9/', {})" to TYPE,
            "http.get()" to TYPE,
            "http.get(undefined)" to TYPE,
            "http.get('http://127.0.0.1:9/', 5)" to TYPE,
            "http.get('http://127.0.0.1:9/', [])" to TYPE,
            "http.get('http://127.0.0.1:9/', null, 5)" to TYPE,
            "http.get('http://127.0.0.1:9/', null, null, 1)" to TYPE,
            "http.getAsync('http://127.0.0.1:9/', 'x')" to TYPE,
            "http.head('http://127.0.0.1:9/', null, {})" to TYPE,
            "http.headAsync(null)" to TYPE,
            "http.post('http://127.0.0.1:9/', {}, 1)" to TYPE,
            "http.post('http://127.0.0.1:9/', {}, null, true)" to TYPE,
            "http.postAsync('http://127.0.0.1:9/', {}, null, 'f')" to TYPE,
            "http.postJson('http://127.0.0.1:9/')" to TYPE,
            "http.postJsonAsync('http://127.0.0.1:9/', {}, 2)" to TYPE,
            "http.postMultipart('http://127.0.0.1:9/')" to TYPE,
            "http.postMultipart('http://127.0.0.1:9/', null)" to TYPE,
            "http.postMultipart('http://127.0.0.1:9/', 'a=1')" to TYPE,
            "http.postMultipartAsync('http://127.0.0.1:9/', [])" to TYPE,
            "http.put('http://127.0.0.1:9/', 'x', 'y')" to TYPE,
            "http.putAsync()" to TYPE,
            "http.delete('http://127.0.0.1:9/', null, null, 1)" to TYPE,
            "http.del('http://127.0.0.1:9/', null, [])" to TYPE,
            "http.deleteAsync('http://127.0.0.1:9/', null, null, {})" to TYPE,
            "http.delAsync(null)" to TYPE,
            "http.del('a', 'b', {}, null, 5)" to TYPE,
        )

        private const val BASE = "http://127.0.0.1:18735"

        /** The response fields of [call], its JSON body parsed once (the body can be read once without `cacheBody`). */
        private fun response(call: String) =
            "(function (r) { var j = r.body.json(); return [r.statusCode, String(r.method), j.method, j.path, j.query, j.contentType, j.body, j.header]; })($call)"

        private val NETWORK = listOf(
            response("http.get('$BASE/echo?x=1', { headers: { 'X-A': 'one' } })") to "[200,\"GET\",\"GET\",\"/echo\",\"x=1\",null,\"\",\"one\"]",
            response("http.post('$BASE/echo', { a: 1, b: 'two' })") to "[200,\"POST\",\"POST\",\"/echo\",\"\",\"application/x-www-form-urlencoded\",\"a=1&b=two\",null]",
            response("http.postJson('$BASE/echo', { a: [1, 2] })") to "[200,\"POST\",\"POST\",\"/echo\",\"\",\"application/json; charset=utf-8\",\"{\\\"a\\\":[1,2]}\",null]",
            response("http.put('$BASE/echo', 'raw', { contentType: 'text/plain' })") to "[200,\"PUT\",\"PUT\",\"/echo\",\"\",\"text/plain; charset=utf-8\",\"raw\",null]",
            response("http.delete('$BASE/echo', { k: 'v' })") to "[200,\"DELETE\",\"DELETE\",\"/echo\",\"\",\"application/x-www-form-urlencoded\",\"k=v\",null]",
            response("http.del('$BASE/echo')") to "[200,\"DELETE\",\"DELETE\",\"/echo\",\"\",\"application/x-www-form-urlencoded\",\"\",null]",
            response("http.request('$BASE/echo', { method: 'PATCH', contentType: 'text/plain', body: 'p' })") to "[200,\"PATCH\",\"PATCH\",\"/echo\",\"\",\"text/plain; charset=utf-8\",\"p\",null]",
            "(function (o) { http.get('$BASE/echo', o); return o.method; })({})" to "\"GET\"",
            "(function (r) { return [r.statusCode, String(r.body.string())]; })(http.get('$BASE/status/404'))" to "[404,\"nope\"]",
            "(function (r) { return [r.statusCode, String(r.headers['x-echo'])]; })(http.head('$BASE/echo'))" to "[200,\"head\"]",
            "(function (r) { var j = r.body.json(); return [j.contentType.indexOf('multipart/form-data') === 0, j.body.indexOf('name=\\\"field\\\"') > 0]; })(http.postMultipart('$BASE/echo', { field: 'value' }))" to "[true,true]",
            "http.get('$BASE/slow', { timeout: 500 })" to "throws JavaException SocketTimeoutException",
            "http.get('http://127.0.0.1:9/')" to "throws JavaException ConnectException",
            "(function () { var latch = new java.util.concurrent.CountDownLatch(1); var out = []; var ret = http.get('$BASE/echo?cb=1', null, function (r, e) { out.push(r === null ? null : r.statusCode, e); latch.countDown(); }); latch.await(10, java.util.concurrent.TimeUnit.SECONDS); return [ret === undefined, out[0], out[1]]; })()" to "[true,200,null]",
            "(function () { var latch = new java.util.concurrent.CountDownLatch(1); var out = []; http.get('http://127.0.0.1:9/', null, function (r, e) { out.push(r, e !== null); latch.countDown(); }); latch.await(10, java.util.concurrent.TimeUnit.SECONDS); return out; })()" to "[null,true]",
            "http.getAsync('$BASE/echo') instanceof Promise" to "true",
            HostModuleTestSupport.settled("http.getAsync('$BASE/echo')", "v.statusCode") to "200",
            HostModuleTestSupport.settled("http.postJsonAsync('$BASE/echo', { a: 1 })", "[v.statusCode, String(v.method)]") to "[200,\"POST\"]",
            HostModuleTestSupport.settled("http.headAsync('$BASE/echo')", "v.statusCode") to "200",
            HostModuleTestSupport.settled("http.putAsync('$BASE/echo', 'x', { contentType: 'text/plain' })", "[v.statusCode, String(v.method)]") to "[200,\"PUT\"]",
            HostModuleTestSupport.settled("http.deleteAsync('$BASE/echo')", "[v.statusCode, String(v.method)]") to "[200,\"DELETE\"]",
            HostModuleTestSupport.settled("http.delAsync('$BASE/echo')", "[v.statusCode, String(v.method)]") to "[200,\"DELETE\"]",
            HostModuleTestSupport.settled("http.postAsync('$BASE/echo', { a: 1 })", "[v.statusCode, String(v.method)]") to "[200,\"POST\"]",
            HostModuleTestSupport.settled("http.postMultipartAsync('$BASE/echo', { f: 'v' })", "v.statusCode") to "200",
            HostModuleTestSupport.settled("http.requestAsync('$BASE/echo', { method: 'GET' })", "[v.statusCode, String(v.method)]") to "[200,\"GET\"]",
            // The Promise settles on the UI thread with the body already read on the request thread (appendix C Q25): reading
            // it in then() formerly was network I/O on the main thread (NetworkOnMainThreadException).
            HostModuleTestSupport.settled("http.getAsync('$BASE/echo')", "(function () { try { v.body.string(); return 'read'; } catch (e) { return String(e.javaException.getClass().getSimpleName()); } })()") to "\"read\"",
            HostModuleTestSupport.settled("http.getAsync('$BASE/echo?x=2')", "(function (j) { return [j.method, j.query]; })(v.body.json())") to "[\"GET\",\"x=2\"]",
            HostModuleTestSupport.settled("http.getAsync('$BASE/status/404')", "[v.statusCode, String(v.body.string())]") to "[404,\"nope\"]",
            HostModuleTestSupport.waited("http.getAsync('$BASE/echo?x=3')", "[v.statusCode, v.body.json().query]") to "[200,\"x=3\"]",
            HostModuleTestSupport.settled("http.getAsync('$BASE/echo', null, function (res) { globalThis.aj6P35Got = res.statusCode; })", "[v.statusCode, globalThis.aj6P35Got]") to "[200,200]",
            HostModuleTestSupport.settled("http.getAsync('http://127.0.0.1:9/')", "'resolved'") to "\"rejected\"",
        )

        /**
         * The same WrappedIllegalArgumentException of the current request builder: the JVM renders the current module's
         * error from its printed stack (the class name), the new layer passes the Rhino error through (InternalError, T5).
         */
        private val KNOWN_CURRENT = mapOf(
            "http.buildRequest('http://example.com/', { method: 'POST', body: 1 })" to ("throws InternalError" to "same exception, rendered from the stack on the current side"),
        )

        /** The obsolete module did not convert the body of a POST without body and built requests in the same way otherwise. */
        private val KNOWN_OBSOLETE = listOf(
            DifferentialCases.KnownDifference(
                "http.buildRequest('http://example.com/', { method: 'POST', body: 1 })",
                js = "throws TypeError", kt = "throws InternalError",
                reason = "Unchanged current behavior of the request builder (still the current RequestBuilderHelper, appendix C Q24): an unknown body type is a WrappedIllegalArgumentException (InternalError); the obsolete JS threw TypeError",
            ),
        )

    }

}
