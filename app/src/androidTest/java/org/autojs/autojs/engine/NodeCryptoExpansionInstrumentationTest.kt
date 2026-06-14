package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeCryptoExpansionInstrumentationTest {

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
    fun safeCryptoExpansionAndShadowingWork() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node crypto safe expansion focused regression")

        val workDir = File(context.cacheDir, "node-crypto-expansion-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create crypto expansion work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/crypto/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }

            val source = """
                "nodejs";
                const assert = require("assert/strict");
                const crypto = require("crypto");
                const nodeCrypto = require("node:crypto");

                return (async () => {
                  assert.strictEqual(crypto, nodeCrypto);
                  assert.strictEqual(crypto.shadow, undefined);
                  assert.strictEqual(require.resolve("crypto"), "crypto");
                  assert.strictEqual(require.resolve("node:crypto"), "crypto");

                  const uuidA = crypto.randomUUID();
                  const uuidB = crypto.randomUUID();
                  assert.match(uuidA, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
                  assert.match(uuidB, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
                  assert.notStrictEqual(uuidA, uuidB);

                  const hmac = crypto.createHmac("sha256", "key").update("abc").digest("hex");
                  assert.strictEqual(hmac, "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab");

                  assert.strictEqual(
                    crypto.timingSafeEqual(Buffer.from("same"), Buffer.from("same")),
                    true
                  );
                  assert.strictEqual(
                    crypto.timingSafeEqual(Buffer.from("same"), Buffer.from("diff")),
                    false
                  );
                  assert.throws(() => crypto.timingSafeEqual(Buffer.from("a"), Buffer.from("aa")));

                  assert.strictEqual(typeof crypto.createPrivateKey, "undefined");
                  assert.strictEqual(typeof crypto.createPublicKey, "undefined");
                  assert.strictEqual(typeof crypto.generateKeyPair, "undefined");
                  assert.strictEqual(typeof crypto.createSign, "undefined");
                  assert.strictEqual(typeof crypto.createVerify, "undefined");

                  // Current Safe Profile exposes limited WebCrypto; keep it bounded and documented.
                  assert.strictEqual(typeof crypto.webcrypto, "object");
                  assert.strictEqual(crypto.subtle, crypto.webcrypto.subtle);
                  assert.strictEqual(typeof crypto.webcrypto.createHmac, "undefined");
                  assert.strictEqual(typeof crypto.webcrypto.createPrivateKey, "undefined");

                  console.log("crypto.expansion.focused.ready=true");
                })();
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-crypto-expansion-focused.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node crypto safe expansion focused regression", result)
            assertTrue(
                NodeEmbeddedTestHarness.describeResult("missing crypto expansion ready output", result),
                result.stdout.contains("crypto.expansion.focused.ready=true"),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }
}
