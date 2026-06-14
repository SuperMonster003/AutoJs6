package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeProcessSweepInstrumentationTest {

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun processCompatibilityAndDangerousApiBoundariesWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node process compatibility sweep")

        val source = """
            "nodejs";
            const assert = require("assert/strict");

            function codeOf(error) {
              return String(error && (error.autojs6Code || error.code || error.name || ""));
            }
            function expectDenied(label, action, expectedCode) {
              try {
                action();
              } catch (error) {
                const actual = codeOf(error);
                if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                  throw new Error(label + " expected " + expectedCode + " but got " + actual);
                }
                console.log("process.sweep." + label + "=" + actual);
                return;
              }
              throw new Error(label + " was unexpectedly allowed");
            }

            return (async () => {
              const order = [];
              process.nextTick(() => order.push("nextTick"));
              Promise.resolve().then(() => order.push("promise"));
              queueMicrotask(() => order.push("queueMicrotask"));
              setTimeout(() => order.push("timeout"), 0);
              await new Promise((resolve) => setTimeout(resolve, 10));
              assert.deepStrictEqual(order, ["nextTick", "promise", "queueMicrotask", "timeout"]);

              const warnings = [];
              function warningListener(warning) {
                warnings.push([warning.name, warning.message, warning.code].join(":"));
              }
              process.on("warning", warningListener);
              process.emitWarning("profile warning", {
                type: "AutoJs6TestWarning",
                code: "AUTOJS6_TEST_WARNING"
              });
              await new Promise((resolve) => setTimeout(resolve, 0));
              process.off("warning", warningListener);
              assert.ok(warnings.some((value) => value.includes("AUTOJS6_TEST_WARNING") && value.includes("profile warning")));

              const onceResult = new Promise((resolve) => process.once("autojs6-once", resolve));
              assert.strictEqual(process.emit("autojs6-once", "seen"), true);
              assert.strictEqual(await onceResult, "seen");

              let offCount = 0;
              function offListener() {
                offCount += 1;
              }
              process.on("autojs6-off", offListener);
              process.off("autojs6-off", offListener);
              assert.strictEqual(process.emit("autojs6-off"), false);
              assert.strictEqual(offCount, 0);

              const flags = process.allowedNodeEnvironmentFlags;
              assert.ok(flags instanceof Set);
              assert.strictEqual(flags.size, 0);
              assert.strictEqual(flags.has("--inspect"), false);
              assert.strictEqual(flags.add("--inspect"), flags);
              assert.strictEqual(flags.size, 0);
              assert.strictEqual(flags.delete("--inspect"), false);

              process.exitCode = 23;
              assert.strictEqual(process.exitCode, 23);

              expectDenied("binding", () => process.binding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("dlopen", () => process.dlopen({}, "native.node"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("_linkedBinding", () => process._linkedBinding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("getBuiltinModule", () => process.getBuiltinModule("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("exit", () => process.exit(2), "ERR_AUTOJS6_PROCESS_API_DISABLED");
              expectDenied("chdir.absolute", () => process.chdir("/"), "EPERM");
              expectDenied("chdir.escape", () => process.chdir(".."), "EPERM");

              console.log("process.sweep.exitCode=" + process.exitCode);
              console.log("process.sweep.focused.ready=true");
            })();
        """.trimIndent()

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = source,
            sourceName = "<node-process-sweep-focused.js>",
            timeoutMs = 10_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node process compatibility sweep", result)
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("missing process sweep ready output", result),
            result.stdout.contains("process.sweep.focused.ready=true"),
        )
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("process exitCode should not bypass success path", result),
            result.stdout.contains("process.sweep.exitCode=23") && result.exitCode == 0,
        )
    }
}
