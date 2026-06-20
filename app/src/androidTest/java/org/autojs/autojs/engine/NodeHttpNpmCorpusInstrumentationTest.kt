package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeHttpNpmCorpusInstrumentationTest {

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
    fun httpCorpusMetadataIsCompleteAndSafe() {
        val workDir = copyCorpusToTemp("node-http-npm-metadata")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            assertEquals("unexpected HTTP npm fixture count", EXPECTED_FIXTURE_COUNT, fixtures.size)
            assertRequiredPatternsPresent(fixtures)

            fixtures.forEach { fixture ->
                val fixtureDir = File(corpusRoot, fixture.name)
                assertTrue("missing fixture dir: ${fixture.name}", fixtureDir.isDirectory)
                assertTrue("missing package.json for ${fixture.name}", File(fixtureDir, "package.json").isFile)
                assertTrue("missing test.cjs for ${fixture.name}", File(fixtureDir, "test.cjs").isFile)
                assertTrue("missing expected.txt for ${fixture.name}", File(fixtureDir, "expected.txt").isFile)
                val packageJson = JSONObject(File(fixtureDir, "package.json").readText())
                assertFalse("fixture runner package must stay CommonJS-compatible: ${fixture.name}", packageJson.optString("type") == "module")

                val licenseNote = File(fixtureDir, "LICENSE_NOTE.md")
                assertTrue("missing license note for ${fixture.name}", licenseNote.isFile)
                assertTrue(
                    "license note should mark fixed snapshots for ${fixture.name}",
                    licenseNote.readText().contains("fixed test snapshots"),
                )
            }

            corpusRoot.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(corpusRoot).invariantSeparatorsPath
                assertFalse("native addon must not be vendored: $relativePath", file.name.endsWith(".node"))
                if (file.name == "package.json") {
                    val json = JSONObject(file.readText())
                    val scripts = json.optJSONObject("scripts")
                    if (scripts != null) {
                        FORBIDDEN_PACKAGE_SCRIPTS.forEach { scriptName ->
                            assertFalse(
                                "package install script must not be present in $relativePath: $scriptName",
                                scripts.has(scriptName),
                            )
                        }
                    }
                }
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun httpNpmCorpusUsesControlledClientFacade() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("HTTP npm compatibility corpus")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("network"))
        val workDir = copyCorpusToTemp("node-http-npm-corpus")
        try {
            val corpusRoot = File(workDir, CORPUS_ASSET_DIR)
            val fixtures = readFixtures(corpusRoot)
            val fixtureNames = fixtures.map { it.name }
            val expectedByFixture = fixtures.associate { fixture ->
                fixture.name to File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
            }
            val source = """
                "nodejs";
                return (async () => {
                  const bridge = require("autojs6:bridge");
                  const { Buffer } = require("buffer");
                  const fixtures = ${fixtureNames.joinToString(prefix = "[", postfix = "]") { jsString(it) }};
                  const expected = ${jsObject(expectedByFixture)};
                  const requests = [];

                  function pathOf(url) {
                    const parsed = new URL(url);
                    return parsed.pathname + parsed.search;
                  }

                  function headerValue(headers, name) {
                    const lower = String(name).toLowerCase();
                    const found = (headers || []).find((entry) => String(entry[0]).toLowerCase() === lower);
                    return found ? String(found[1]) : "";
                  }

                  function requestBody(payload) {
                    if (payload.bodyBase64 !== undefined) {
                      return Buffer.from(payload.bodyBase64, "base64").toString("utf8");
                    }
                    return String(payload.bodyText || "");
                  }

                  function responseFor(payload) {
                    const parsed = new URL(payload.url);
                    const path = pathOf(payload.url);
                    const body = requestBody(payload);
                    const headers = [
                      ["content-type", "text/plain"],
                      ["x-method", payload.method],
                      ["x-path", path],
                      ["x-protocol", parsed.protocol],
                      ["x-content-type", headerValue(payload.headers, "content-type")]
                    ];
                    if (path === "/http-get?pkg=1") {
                      return {
                        url: payload.url,
                        status: 200,
                        statusText: "OK",
                        headers,
                        bodyBase64: Buffer.from("http-get-ok").toString("base64")
                      };
                    }
                    if (path === "/https-request") {
                      return {
                        url: payload.url,
                        status: 201,
                        statusText: "Created",
                        headers,
                        bodyBase64: Buffer.from("payload=" + body).toString("base64")
                      };
                    }
                    if (path === "/incoming-stream") {
                      return {
                        url: payload.url,
                        status: 200,
                        statusText: "OK",
                        headers,
                        bodyBase64: Buffer.from("stream-a\nstream-b\n").toString("base64")
                      };
                    }
                    if (path === "/form-post") {
                      return {
                        url: payload.url,
                        status: 202,
                        statusText: "Accepted",
                        headers,
                        bodyBase64: Buffer.from(body).toString("base64")
                      };
                    }
                    if (path === "/agent-http") {
                      return {
                        url: payload.url,
                        status: 200,
                        statusText: "OK",
                        headers,
                        bodyBase64: Buffer.from("agent-http-ok").toString("base64")
                      };
                    }
                    if (path === "/agent-https") {
                      return {
                        url: payload.url,
                        status: 202,
                        statusText: "Accepted",
                        headers,
                        bodyBase64: Buffer.from("agent-https-ok").toString("base64")
                      };
                    }
                    return {
                      url: payload.url,
                      status: 404,
                      statusText: "Not Found",
                      headers,
                      bodyBase64: Buffer.from("missing").toString("base64")
                    };
                  }

                  bridge.__test.setTransport({
                    postMessage(message) {
                      const request = JSON.parse(message);
                      requests.push(request);
                      const payload = request.args[0];
                      if (pathOf(payload.url) === "/abort-during") {
                        return;
                      }
                      setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                        id: request.id,
                        ok: true,
                        result: responseFor(payload)
                      })));
                    }
                  });

                  const fixtureContext = Object.freeze({
                    requestCount() {
                      return requests.length;
                    }
                  });

                  for (const name of fixtures) {
                    const run = require("./$CORPUS_ASSET_DIR/" + name + "/test.cjs");
                    if (typeof run !== "function") {
                      throw new Error("HTTP npm fixture " + name + " does not export a runner");
                    }
                    const output = await run(fixtureContext);
                    if (output !== expected[name]) {
                      throw new Error("HTTP npm fixture " + name + " output mismatch: " + output);
                    }
                    console.log(output);
                    console.log("httpnpm.fixture." + name + "=PASS");
                  }
                  console.log("httpnpm.corpus.total=" + fixtures.length);
                  console.log("httpnpm.requests=" + requests.map((request) => {
                    const payload = request.args[0];
                    const parsed = new URL(payload.url);
                    return payload.method + ":" + parsed.protocol + pathOf(payload.url);
                  }).join("|"));
                  console.log("httpnpm.permissions=" + requests.every((request) => request.permissions.indexOf("network") >= 0));
                  console.log("httpnpm.pending=" + bridge.__test.pendingCount());
                })().catch((error) => {
                  console.error("httpnpm.corpus.error=" + (error && (error.stack || error.message) || error));
                  throw error;
                });
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-http-npm-corpus-s8-26.js>",
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("HTTP npm compatibility corpus", result)
            fixtures.forEach { fixture ->
                val expected = File(corpusRoot, "${fixture.name}/expected.txt").readText().trim()
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing expected output for ${fixture.name}", result),
                    result.stdout.contains(expected),
                )
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing fixture pass marker for ${fixture.name}", result),
                    result.stdout.contains("httpnpm.fixture.${fixture.name}=PASS"),
                )
            }
            assertTrue(result.stdout, result.stdout.contains("httpnpm.corpus.total=${fixtures.size}"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "httpnpm.requests=GET:http:/http-get?pkg=1|POST:https:/https-request|GET:http:/incoming-stream|POST:http:/form-post|GET:http:/abort-during|GET:http:/agent-http|GET:https:/agent-https",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("httpnpm.permissions=true"))
            assertTrue(result.stdout, result.stdout.contains("httpnpm.pending=0"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun copyCorpusToTemp(prefix: String): File {
        val workDir = File(context.cacheDir, "$prefix-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create HTTP npm corpus work dir: $absolutePath" }
        }
        NodeEmbeddedTestHarness.copyAssetTree(CORPUS_ASSET_DIR, File(workDir, CORPUS_ASSET_DIR))
        return workDir
    }

    private fun readFixtures(corpusRoot: File): List<HttpNpmFixture> {
        val manifest = JSONObject(File(corpusRoot, "manifest.json").readText())
        val array = manifest.getJSONArray("fixtures")
        return (0 until array.length()).map { index ->
            val fixture = array.getJSONObject(index)
            val patterns = fixture.optJSONArray("patterns")
            HttpNpmFixture(
                name = fixture.getString("name"),
                patterns = (0 until (patterns?.length() ?: 0)).map { patternIndex -> patterns!!.getString(patternIndex) },
            )
        }
    }

    private fun assertRequiredPatternsPresent(fixtures: List<HttpNpmFixture>) {
        val patterns = fixtures.flatMap { it.patterns }.toSet()
        REQUIRED_PATTERNS.forEach { pattern ->
            assertTrue("missing S8-26 HTTP npm pattern: $pattern in $patterns", pattern in patterns)
        }
    }

    private fun jsString(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun jsObject(values: Map<String, String>): String {
        return values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsString(key)}:${jsString(value)}"
        }
    }

    private data class HttpNpmFixture(val name: String, val patterns: List<String>)

    companion object {
        private const val CORPUS_ASSET_DIR = "http_compat_corpus"
        private const val EXPECTED_FIXTURE_COUNT = 6
        private val FORBIDDEN_PACKAGE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare")
        private val REQUIRED_PATTERNS = setOf(
            "package using http.get",
            "package using https.request",
            "IncomingMessage stream consumer",
            "package posting form data",
            "package expecting AbortSignal",
            "package expecting Agent options",
        )
    }
}
