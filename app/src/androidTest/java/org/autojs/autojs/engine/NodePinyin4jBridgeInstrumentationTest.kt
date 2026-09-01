package org.autojs.autojs.engine

import android.os.Bundle
import org.autojs.plugin.pinyin4j.api.Pinyin4jOptionKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePinyin4jBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Test
    fun androidProviderDispatchesEveryPinyin4jMethodWithCanonicalOptions() {
        val backend = RecordingPinyin4jBackend()
        val provider = AndroidNodeBridgeCapabilityProvider(
            context = context,
            engineInfo = NodeBridgeEngineInfo.fromContext(context, id = "pinyin4j-provider-test"),
            pinyin4jBackend = backend,
        )

        val converted = dispatch(
            provider,
            request("of", """["中国",{"separator":"-","tone":"MARK","v":"U"}]"""),
        )
        assertTrue(converted.toString(), converted.ok)
        assertEquals("\"zhōng-guó\"", converted.resultJson)
        assertEquals("-", backend.lastOptions.getString(Pinyin4jOptionKeys.SEPARATOR))
        assertEquals("WITH_TONE_MARK", backend.lastOptions.getString(Pinyin4jOptionKeys.TONE_TYPE))
        assertEquals("WITH_U_UNICODE", backend.lastOptions.getString(Pinyin4jOptionKeys.V_CHAR_TYPE))

        assertEquals(
            "\"zhōng guó\"",
            dispatch(provider, request("as", """["zhong1 guo2"]""")).resultJson,
        )

        val readings = dispatch(
            provider,
            request("readings", """["间A",{"system":"wade-giles"}]"""),
        )
        assertTrue(readings.toString(), readings.ok)
        assertEquals("[[\"chien1\",\"chien4\"],[\"A\"]]", readings.resultJson)
        assertEquals(
            Pinyin4jRomanizationSystems.WADE_GILES,
            backend.lastOptions.getString(Pinyin4jOptionKeys.ROMANIZATION),
        )
        assertFalse(backend.lastOptions.containsKey(Pinyin4jOptionKeys.TONE_TYPE))

        val invalid = dispatch(
            provider,
            request("readings", """["间",{"system":"YALE","tone":"NUMBER"}]"""),
        )
        assertFalse(invalid.toString(), invalid.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST, invalid.error?.code)
        provider.destroy(NodeBridgeError.processDead("pinyin4j provider test cleanup"))
    }

    @Test
    fun nodeRuntimePublicBridgeCanCallPinyin4jThroughTheGenericFacade() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node Pinyin4j bridge facade regression")
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf(NodeBridgePermissionManifest.PINYIN4J))
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
                  if (request.method === "of") value = "zhōng-guó";
                  else if (request.method === "as") value = "zhōng guó";
                  else if (request.method === "readings") value = [["zhong4", "chong2"], ["A"]];
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result: value
                  })));
                }
              });
              const options = { permissions: ["pinyin4j"], timeoutMs: 1000 };
              const converted = await bridge.callAutoJs("pinyin4j", "of", ["中国", "-"], options);
              const toned = await bridge.callAutoJs("pinyin4j", "as", ["zhong1 guo2"], options);
              const readings = await bridge.callAutoJs("pinyin4j", "readings", ["重A", { tone: "NUMBER" }], options);
              console.log("pinyin4j.node.values=" + [converted, toned, readings[0].length, readings[1][0]].join("|"));
              console.log("pinyin4j.node.requests=" + requests.map(r => r.module + "." + r.method).join("|"));
              console.log("pinyin4j.node.permissions=" + requests.every(r => r.permissions.join(",") === "pinyin4j"));
              console.log("pinyin4j.node.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-pinyin4j-bridge.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodePluginTestHarness.assertSucceeded("Node Pinyin4j generic bridge", result)
        assertTrue(result.stdout, result.stdout.contains("pinyin4j.node.values=zhōng-guó|zhōng guó|2|A"))
        assertTrue(result.stdout, result.stdout.contains("pinyin4j.node.requests=pinyin4j.of|pinyin4j.as|pinyin4j.readings"))
        assertTrue(result.stdout, result.stdout.contains("pinyin4j.node.permissions=true"))
        assertTrue(result.stdout, result.stdout.contains("pinyin4j.node.pending=0"))
    }

    private fun request(method: String, argsJson: String): NodeBridgeRequest = NodeBridgeRequest(
        id = "pinyin4j-$method",
        module = NodeBridgeModules.PINYIN4J,
        method = method,
        argsJson = argsJson,
        timeoutMs = 5_000L,
        permissions = listOf(NodeBridgePermissionManifest.PINYIN4J),
    )

    private fun dispatch(provider: NodeBridgeCapabilityProvider, request: NodeBridgeRequest): NodeBridgeResponse {
        var response: NodeBridgeResponse? = null
        provider.dispatch(request) { response = it }
        return checkNotNull(response) { "Pinyin4j provider did not reply synchronously" }
    }

    private class RecordingPinyin4jBackend : NodePinyin4jBackend {
        var lastOptions = Bundle()

        override fun of(text: String, options: Bundle): String {
            lastOptions = Bundle(options)
            return "zhōng-guó"
        }

        override fun asTone(text: String): String = "zhōng guó"

        override fun readings(text: String, options: Bundle): List<List<String>> {
            lastOptions = Bundle(options)
            return listOf(listOf("chien1", "chien4"), listOf("A"))
        }
    }
}
