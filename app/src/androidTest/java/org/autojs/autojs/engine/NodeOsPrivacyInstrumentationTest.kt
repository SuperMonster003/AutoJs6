package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeOsPrivacyInstrumentationTest {

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
    fun osModuleReturnsPrivacySafeRedactedValues() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node os privacy focused regression")

        val workDir = File(context.cacheDir, "node-os-privacy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create os privacy work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/os/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }

            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const os = require("os");
                const nodeOs = require("node:os");

                return (async () => {
                  assert.strictEqual(os, nodeOs);
                  assert.strictEqual(os.shadow, undefined);
                  assert.strictEqual(require.resolve("os"), "os");
                  assert.strictEqual(require.resolve("node:os"), "os");

                  assert.strictEqual(os.tmpdir(), process.cwd());
                  assert.strictEqual(os.homedir(), process.cwd());
                  assert.strictEqual(os.hostname(), "localhost");
                  assert.deepStrictEqual(os.loadavg(), [0, 0, 0]);
                  assert.strictEqual(os.freemem(), 256 * 1024 * 1024);
                  assert.strictEqual(os.totalmem(), 512 * 1024 * 1024);

                  const interfaces = os.networkInterfaces();
                  assert.strictEqual(Object.getPrototypeOf(interfaces), null);
                  assert.deepStrictEqual(Object.keys(interfaces), []);
                  assert.ok(Object.isFrozen(interfaces));

                  const cpus = os.cpus();
                  assert.strictEqual(cpus.length, 1);
                  assert.strictEqual(cpus[0].model, "AutoJs6 Embedded CPU");
                  assert.strictEqual(cpus[0].speed, 0);
                  assert.deepStrictEqual(cpus[0].times, { user: 0, nice: 0, sys: 0, idle: 0, irq: 0 });
                  assert.ok(Object.isFrozen(cpus));
                  assert.ok(Object.isFrozen(cpus[0]));

                  const user = os.userInfo();
                  assert.strictEqual(user.username, "autojs6");
                  assert.strictEqual(user.uid, 0);
                  assert.strictEqual(user.gid, 0);
                  assert.strictEqual(user.shell, null);
                  assert.strictEqual(user.homedir, process.cwd());
                  assert.ok(Object.isFrozen(user));

                  const userBuffer = os.userInfo({ encoding: "buffer" });
                  assert.ok(Buffer.isBuffer(userBuffer.username));
                  assert.ok(Buffer.isBuffer(userBuffer.homedir));
                  assert.strictEqual(userBuffer.username.toString("utf8"), "autojs6");
                  assert.strictEqual(userBuffer.homedir.toString("utf8"), process.cwd());

                  assert.ok(Number.isInteger(os.availableParallelism()));
                  assert.ok(os.availableParallelism() > 0);
                  assert.ok(os.constants && Object.isFrozen(os.constants));
                  assert.throws(() => os.setPriority(1), /disabled/);

                  console.log("os.privacy.focused.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-os-privacy-focused.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node os privacy focused regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing os privacy ready output", result),
                result.stdout.contains("os.privacy.focused.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }
}
