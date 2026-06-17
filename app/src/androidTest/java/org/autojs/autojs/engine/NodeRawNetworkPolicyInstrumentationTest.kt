package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeRawNetworkPolicyInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun rawNetworkModulesStayDeniedAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("raw network policy regression")
        assertFalse(BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED)

        val workDir = File(context.cacheDir, "node-raw-network-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create raw network policy work dir: $absolutePath" }
        }

        try {
            writeShadowModule(workDir, "http")
            writeShadowModule(workDir, "https")
            writeShadowModule(workDir, "net")
            writeShadowModule(workDir, "tls")
            writeShadowModule(workDir, "dns")
            writeShadowModule(workDir, "dgram")
            writeShadowModule(workDir, "fetch")
            writeShadowModule(workDir, "websocket")
            File(workDir, "node_modules/dns/promises.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true, id: "dns/promises-shadow" };""" + "\n", StandardCharsets.UTF_8)
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }
                      function expectSync(label, action, expectedCode) {
                        try {
                          action();
                        } catch (error) {
                          const actual = codeOf(error);
                          if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                            throw new Error(label + " expected " + expectedCode + " but got " + actual);
                          }
                          console.log("rawnet." + label + "=" + actual);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      async function expectAsync(label, action, expectedCode) {
                        try {
                          await action();
                        } catch (error) {
                          const actual = codeOf(error);
                          if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                            throw new Error(label + " expected " + expectedCode + " but got " + actual);
                          }
                          console.log("rawnet." + label + "=" + actual);
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      async function expectDnsCallback(label, moduleName, expectedCode) {
                        const dns = require(moduleName);
                        const actual = await new Promise((resolve, reject) => {
                          try {
                            dns.resolve4("example.invalid", (error) => {
                              if (!error) {
                                reject(new Error(label + " was unexpectedly allowed"));
                                return;
                              }
                              resolve(codeOf(error));
                            });
                          } catch (error) {
                            resolve(codeOf(error));
                          }
                        });
                        if (actual !== expectedCode) {
                          throw new Error(label + " expected " + expectedCode + " but got " + actual);
                        }
                        console.log("rawnet." + label + "=" + actual);
                      }
                      function isShadowed(moduleName) {
                        const value = require(moduleName);
                        return value && value.shadow === true;
                      }

                      const fetchModule = require("fetch");
                      const websocket = require("websocket");
                      console.log("rawnet.fetch.shadow=" + (fetchModule.shadow === true));
                      console.log("rawnet.websocket.shadow=" + (websocket.shadow === true));

                      const shadowChecks = [
                        "http", "node:http",
                        "https", "node:https",
                        "net", "node:net",
                        "tls", "node:tls",
                        "dns", "node:dns",
                        "dns/promises", "node:dns/promises"
                      ].map((name) => isShadowed(name));
                      console.log("rawnet.shadow=" + shadowChecks.join("|"));
                      console.log("rawnet.resolve=" + [
                        require.resolve("http"),
                        require.resolve("node:http"),
                        require.resolve("https"),
                        require.resolve("node:https"),
                        require.resolve("net"),
                        require.resolve("node:net"),
                        require.resolve("tls"),
                        require.resolve("node:tls"),
                        require.resolve("dns"),
                        require.resolve("node:dns"),
                        require.resolve("dns/promises"),
                        require.resolve("node:dns/promises")
                      ].join("|"));

                      expectSync("http", () => require("http").request("http://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("node_http", () => require("node:http").request("http://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("https", () => require("https").request("https://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("node_https", () => require("node:https").request("https://127.0.0.1/"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("net_connect", () => require("net").connect(80, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("node_net_connect", () => require("node:net").connect(80, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("net_listen", () => require("net").createServer().listen(0), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("tls_connect", () => require("tls").connect(443, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("node_tls_connect", () => require("node:tls").connect(443, "127.0.0.1"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      await expectDnsCallback("dns", "dns", "ERR_AUTOJS6_NETWORK_DISABLED");
                      await expectDnsCallback("node_dns", "node:dns", "ERR_AUTOJS6_NETWORK_DISABLED");
                      await expectAsync("dns_promises", () => require("dns/promises").resolve4("example.invalid"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      await expectAsync("node_dns_promises", () => require("node:dns/promises").resolve4("example.invalid"), "ERR_AUTOJS6_NETWORK_DISABLED");
                      expectSync("dgram", () => require("dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectSync("node_dgram", () => require("node:dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectSync("resolve_dgram", () => require.resolve("dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED");
                      expectSync("resolve_node_dgram", () => require.resolve("node:dgram"), "ERR_AUTOJS6_BUILTIN_DISABLED");

                      console.log("rawnet.ready=true");
                    })().catch((error) => {
                      console.error("rawnet.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-raw-network-policy.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("raw network policy regression", result)
            assertTrue(result.stdout, result.stdout.contains("rawnet.fetch.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("rawnet.websocket.shadow=false"))
            assertTrue(
                result.stdout,
                result.stdout.contains("rawnet.shadow=false|false|false|false|false|false|false|false|false|false|false|false"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("rawnet.resolve=http|http|https|https|net|net|tls|tls|dns|dns|dns/promises|dns/promises"),
            )
            listOf(
                "http" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_http" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "https" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_https" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "net_connect" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_net_connect" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "net_listen" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "tls_connect" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_tls_connect" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "dns" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_dns" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "dns_promises" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "node_dns_promises" to "ERR_AUTOJS6_NETWORK_DISABLED",
                "dgram" to "ERR_AUTOJS6_BUILTIN_DISABLED",
                "node_dgram" to "ERR_AUTOJS6_BUILTIN_DISABLED",
                "resolve_dgram" to "ERR_AUTOJS6_BUILTIN_DISABLED",
                "resolve_node_dgram" to "ERR_AUTOJS6_BUILTIN_DISABLED",
            ).forEach { (label, code) ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing raw network output $label", result),
                    result.stdout.contains("rawnet.$label=$code"),
                )
            }
            assertTrue(result.stdout, result.stdout.contains("rawnet.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeShadowModule(workDir: File, name: String) {
        File(workDir, "node_modules/$name").apply {
            check(mkdirs()) { "Failed to create shadow module directory: $absolutePath" }
            File(this, "index.js").writeText(
                """module.exports = { shadow: true, id: "$name-shadow" };""" + "\n",
                StandardCharsets.UTF_8,
            )
        }
    }
}
