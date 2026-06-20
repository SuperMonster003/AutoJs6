package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeWebPlatformGlobalsInstrumentationTest {

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
    fun webPlatformGlobalsAlignForControlledNetworkPrelude() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node Web Platform globals alignment")

        val workDir = File(context.cacheDir, "node-web-platform-globals-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Web Platform globals work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    return (async () => {
                      const assert = require("assert");
                      const globals = [
                        "Headers",
                        "Request",
                        "Response",
                        "FormData",
                        "Blob",
                        "File",
                        "URLPattern",
                        "DOMException",
                        "EventTarget",
                        "MessageEvent",
                        "CompressionStream",
                        "DecompressionStream",
                      ];
                      assert.deepStrictEqual(
                        globals.map((name) => typeof globalThis[name]),
                        globals.map(() => "function")
                      );
                      assert.strictEqual(globals.every((name) => {
                        const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
                        return descriptor && descriptor.writable === false;
                      }), true);

                      const headers = new Headers([["X-Test", "one"]]);
                      headers.append("x-test", "two");
                      headers.set("content-type", "text/plain");
                      assert.strictEqual(headers.get("x-test"), "one, two");
                      assert.strictEqual([...headers.keys()].includes("content-type"), true);

                      const blob = new Blob([Buffer.from("blob")], { type: "text/plain" });
                      const file = new File(["file"], "input.txt", { type: "text/plain", lastModified: 7 });
                      assert.strictEqual(await blob.text(), "blob");
                      assert.strictEqual(await file.text(), "file");
                      assert.strictEqual(file.name + "/" + file.lastModified + "/" + file.type, "input.txt/7/text/plain");

                      const form = new FormData();
                      form.append("field", "value");
                      form.append("blob", blob, "blob.txt");
                      assert.strictEqual(form.get("field"), "value");
                      assert.strictEqual(form.get("blob") instanceof File, true);
                      assert.strictEqual(form.get("blob").name, "blob.txt");

                      const request = new Request("https://example.invalid/api", {
                        method: "post",
                        headers,
                        body: "payload",
                      });
                      assert.strictEqual(request.method, "POST");
                      assert.strictEqual(request.headers.get("content-type"), "text/plain");
                      assert.strictEqual(await request.text(), "payload");
                      assert.strictEqual(request.bodyUsed, true);

                      const response = new Response(JSON.stringify({ ok: true }), {
                        status: 202,
                        statusText: "Accepted",
                        headers: { "x-reply": "yes" },
                      });
                      assert.strictEqual(response.status, 202);
                      assert.strictEqual(response.statusText, "Accepted");
                      assert.strictEqual(response.ok, true);
                      assert.strictEqual(response.headers.get("x-reply"), "yes");
                      assert.deepStrictEqual(await response.json(), { ok: true });

                      const pattern = new URLPattern({
                        protocol: "https",
                        hostname: "example.com",
                        pathname: "/users/:id",
                      });
                      const match = pattern.exec("https://example.com/users/42?debug=1");
                      assert.strictEqual(pattern.test("https://example.com/users/42"), true);
                      assert.strictEqual(match.pathname.groups.id, "42");

                      const domError = new DOMException("stop", "AbortError");
                      assert.strictEqual(domError.name, "AbortError");
                      assert.strictEqual(domError.message, "stop");
                      assert.strictEqual(typeof domError.code, "number");

                      const target = new EventTarget();
                      let eventSummary = "";
                      target.addEventListener("ready", {
                        handleEvent(event) {
                          event.preventDefault();
                          eventSummary = [
                            event.type,
                            event.target === target,
                            event.currentTarget === target,
                            event.defaultPrevented,
                          ].join("/");
                        },
                      }, { once: true });
                      assert.strictEqual(target.dispatchEvent(new Event("ready", { cancelable: true })), false);
                      assert.strictEqual(target.dispatchEvent(new Event("ready")), true);
                      assert.strictEqual(eventSummary, "ready/true/true/true");

                      const messageEvent = new MessageEvent("message", {
                        data: { text: "hello" },
                        origin: "autojs6://s8-20",
                        lastEventId: "event-1",
                        ports: [],
                      });
                      assert.strictEqual(messageEvent instanceof Event, true);
                      assert.strictEqual(messageEvent.data.text, "hello");
                      assert.strictEqual(messageEvent.origin, "autojs6://s8-20");
                      assert.strictEqual(messageEvent.lastEventId, "event-1");
                      assert.strictEqual(messageEvent.ports.length, 0);

                      async function collectBytes(stream, chunks) {
                        const reader = stream.readable.getReader();
                        const writer = stream.writable.getWriter();
                        const reads = (async () => {
                          const buffers = [];
                          for (;;) {
                            const item = await reader.read();
                            if (item.done) break;
                            buffers.push(Buffer.from(item.value));
                          }
                          return Buffer.concat(buffers);
                        })();
                        try {
                          for (const chunk of chunks) {
                            await writer.write(chunk);
                          }
                          await writer.close();
                        } catch (error) {
                          await reads.catch(() => {});
                          throw error;
                        }
                        return await reads;
                      }

                      const compressed = await collectBytes(
                        new CompressionStream("gzip"),
                        [new TextEncoder().encode("web-"), Buffer.from("platform")]
                      );
                      const roundTrip = await collectBytes(new DecompressionStream("gzip"), [compressed]);
                      assert.strictEqual(roundTrip.toString("utf8"), "web-platform");
                      let invalidCompression = false;
                      try {
                        new CompressionStream("br");
                      } catch (error) {
                        invalidCompression = error instanceof Error;
                      }
                      assert.strictEqual(invalidCompression, true);

                      let fetchDenied = false;
                      try {
                        await fetch("https://example.invalid/blocked");
                      } catch (error) {
                        fetchDenied = error &&
                          (
                            error.code === "ERR_AUTOJS6_EMBEDDED_NODE_FETCH_DISABLED" ||
                            error.code === "ERR_AUTOJS6_NETWORK_DISABLED" ||
                            error.autojs6Code === "ERR_AUTOJS6_NETWORK_DISABLED"
                          );
                      }
                      assert.strictEqual(fetchDenied, true);

                      console.log("web.platform.s8_20=PASS");
                      console.log("web.platform.globals=" + globals.length);
                      console.log("web.platform.roundtrip=" + roundTrip.toString("utf8"));
                    })().catch((error) => {
                      console.error("web.platform.error=" + (error && (error.stack || error.message) || error));
                      throw error;
                    });
                """.trimIndent(),
                sourceName = "<node-web-platform-globals-s8-20.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node Web Platform globals alignment", result)
            assertTrue(result.stdout, result.stdout.contains("web.platform.s8_20=PASS"))
            assertTrue(result.stdout, result.stdout.contains("web.platform.globals=12"))
            assertTrue(result.stdout, result.stdout.contains("web.platform.roundtrip=web-platform"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
