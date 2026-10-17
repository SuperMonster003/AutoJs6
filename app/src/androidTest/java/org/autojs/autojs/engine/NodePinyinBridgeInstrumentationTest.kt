package org.autojs.autojs.engine

import android.os.Bundle
import org.autojs.plugin.pinyin.api.PinyinOptionKeys
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePinyinBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Test
    fun androidProviderDispatchesEveryPinyinMethodWithValidatedJsonValues() {
        val backend = RecordingPinyinBackend()
        val provider = AndroidNodeBridgeCapabilityProvider(
            context = context,
            engineInfo = NodeBridgeEngineInfo.fromContext(context, id = "pinyin-provider-test"),
            pinyinBackend = backend,
        )

        val converted = dispatch(
            provider,
            request(
                "convert",
                """["重行",{"style":"TONE2","heteronym":true,"customDictionary":{"重行":[["chóng"],["háng"]]}}]""",
            ),
        )
        assertTrue(converted.toString(), converted.ok)
        assertEquals("[[\"chong2\"],[\"hang2\"]]", converted.resultJson)
        assertEquals(2, backend.lastOptions.getInt(PinyinOptionKeys.STYLE))
        assertTrue(backend.lastOptions.getBoolean(PinyinOptionKeys.HETERONYM))
        assertEquals(
            "{\"重行\":[[\"chóng\"],[\"háng\"]]}",
            backend.lastOptions.getString(PinyinOptionKeys.CUSTOM_DICTIONARY_JSON),
        )
        val numeric = dispatch(provider, request("convert", """["北京",{"style":0.0,"mode":2.0}]"""))
        assertTrue(numeric.toString(), numeric.ok)
        assertEquals(0, backend.lastOptions.getInt(PinyinOptionKeys.STYLE))
        assertEquals(2, backend.lastOptions.getInt(PinyinOptionKeys.MODE))
        assertFalse(dispatch(provider, request("convert", """["北京",{"style":0.5}]""")).ok)

        assertEquals(
            "\"zhongxin\"",
            dispatch(provider, request("simple", """["中心",false,true]""")).resultJson,
        )
        assertTrue(
            dispatch(provider, request("compare", """["马","妈"]""")).resultJson.orEmpty().toInt() > 0,
        )
        assertEquals(1, backend.lastOptions.getInt(PinyinOptionKeys.STYLE))
        assertEquals(
            "[[\"a\",\"1\"],[\"a\",\"2\"],[\"b\",\"1\"],[\"b\",\"2\"]]",
            dispatch(provider, request("compact", """[[["a","b"],["1","2"]]]""")).resultJson,
        )
        assertEquals(
            "\"zhōng,zhòng\"",
            dispatch(provider, request("fromCodePoint", """[20013]""")).resultJson,
        )
        assertEquals(
            "[[\"chóng\"],[\"qìng\"]]",
            dispatch(provider, request("fromPhrase", """["重庆"]""")).resultJson,
        )

        val invalid = dispatch(
            provider,
            request("convert", """["重",{"customDictionary":{"重":[["ZHONG"]]}}]"""),
        )
        assertFalse(invalid.toString(), invalid.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, invalid.error?.code)
        provider.destroy(NodeBridgeError.processDead("pinyin provider test cleanup"))
    }

    @Test
    fun nodeRuntimePublicBridgeCanCallPinyinThroughTheGenericFacade() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node Pinyin bridge facade regression")
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf(NodeBridgePermissionManifest.PINYIN))
        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  let value = null;
                  if (request.method === "convert") value = [["zhōng"], ["xīn"]];
                  else if (request.method === "compare") value = -1;
                  else if (request.method === "compact") value = [["a", "1"], ["b", "1"]];
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result: value
                  })));
                }
              });
              const options = { permissions: ["pinyin"], timeoutMs: 1000 };
              const converted = await bridge.callAutoJs("pinyin", "convert", ["中心", { style: "TONE" }], options);
              const compared = await bridge.callAutoJs("pinyin", "compare", ["阿", "波"], options);
              const compacted = await bridge.callAutoJs("pinyin", "compact", [[['a', 'b'], ['1']]], options);
              console.log("pinyin.node.values=" + [converted[0][0], converted[1][0], compared, compacted.length].join("|"));
              console.log("pinyin.node.requests=" + requests.map(r => r.module + "." + r.method).join("|"));
              console.log("pinyin.node.permissions=" + requests.every(r => r.permissions.join(",") === "pinyin"));
              console.log("pinyin.node.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-pinyin-bridge.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("Node Pinyin generic bridge", result)
        assertTrue(result.stdout, result.stdout.contains("pinyin.node.values=zhōng|xīn|-1|2"))
        assertTrue(result.stdout, result.stdout.contains("pinyin.node.requests=pinyin.convert|pinyin.compare|pinyin.compact"))
        assertTrue(result.stdout, result.stdout.contains("pinyin.node.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("pinyin.node.pending=0"))
    }

    private fun request(method: String, argsJson: String): NodeBridgeRequest = NodeBridgeRequest(
        id = "pinyin-$method",
        module = NodeBridgeModules.PINYIN,
        method = method,
        argsJson = argsJson,
        timeoutMs = 5_000L,
        permissions = listOf(NodeBridgePermissionManifest.PINYIN),
    )

    private fun dispatch(provider: NodeBridgeCapabilityProvider, request: NodeBridgeRequest): NodeBridgeResponse {
        var response: NodeBridgeResponse? = null
        provider.dispatch(request) { response = it }
        return checkNotNull(response) { "Pinyin provider did not reply synchronously" }
    }

    private class RecordingPinyinBackend : NodePinyinBackend {
        var lastOptions = Bundle()

        override fun convert(text: String, options: Bundle): String {
            lastOptions = Bundle(options)
            return when (text) {
                "重行" -> "[[\"chong2\"],[\"hang2\"]]"
                "马" -> "[[\"ma3\"]]"
                "妈" -> "[[\"ma1\"]]"
                else -> JSONArray().toString()
            }
        }

        override fun simple(text: String, enableNumericTone: Boolean, enableSegment: Boolean): String = "zhongxin"

        override fun fromCodePoint(codePoint: Int): String? = "zhōng,zhòng"

        override fun fromPhrase(phrase: String): String = JSONArray()
            .put(JSONArray().put("chóng"))
            .put(JSONArray().put("qìng"))
            .toString()
    }
}
