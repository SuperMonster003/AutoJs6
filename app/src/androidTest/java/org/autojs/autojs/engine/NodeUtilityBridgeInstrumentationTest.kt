package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeUtilityBridgeInstrumentationTest {

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
    fun jsJsoxMathxPureFacadeBlocksShadowingAndDefaultGlobalMutation() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node utility-heavy Rhino module regression")

        val workDir = File(context.cacheDir, "node-utility-jsox-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create utility bridge working directory: $absolutePath" }
        }
        listOf("jsox", "jsox.mathx").forEach { name ->
            File(workDir, "node_modules/$name").apply {
                check(mkdirs()) { "Failed to create $name shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText("""module.exports = { shadow: true };""")
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (function main() {
              const assert = require("assert");
              const compat = require("autojs6:compat");
              const jsox = require("jsox");
              const mathx = require("jsox.mathx");
              const sample = [2, 4, 4, 4, 5, 5, 7, 9];
              const target = {};
              const targetAll = {};
              const extended = jsox.extend(target, "Mathx");
              const extendedAll = jsox.extendAll(targetAll);
              let blocked = "";
              try {
                jsox.extend("Mathx");
              } catch (error) {
                blocked = String(error && (error.autojs6Code || error.code || ""));
              }
              let unsupported = "";
              try {
                jsox.extend({}, "Numberx");
              } catch (error) {
                unsupported = String(error && (error.autojs6Code || error.code || ""));
              }
              const compatInstall = compat.install({ globals: true });
              assert.strictEqual(extended, target);
              assert.strictEqual(extendedAll, targetAll);
              assert.strictEqual(jsox.mathx, jsox.Mathx);
              assert.strictEqual(jsox.mathx, mathx);
              assert.strictEqual(compat.jsox.mathx, mathx);
              assert.strictEqual(globalThis.jsox, compat.jsox);
              assert(compatInstall.globals.indexOf("jsox") >= 0);
              console.log("jsox.resolve=" + [require.resolve("jsox"), require.resolve("jsox.mathx")].join("|"));
              console.log("jsox.shadow=" + [jsox.shadow === true, mathx.shadow === true].join("|"));
              console.log("jsox.frozen=" + [Object.isFrozen(jsox), Object.isFrozen(mathx), Object.isFrozen(jsox.mathx), Object.isFrozen(compat.jsox), Object.isFrozen(compat.jsox.mathx), Object.isFrozen(jsox.status())].join("|"));
              console.log("jsox.methods=" + [typeof jsox, typeof jsox.extend, typeof jsox.extendAll, typeof jsox.status, typeof mathx.randomInt, typeof mathx["var"]].join("|"));
              console.log("jsox.values=" + [
                mathx.sum(1, 2, 3),
                mathx.mean([1, 2, 3, 4]),
                mathx.avg([1, 2, 3, 4]),
                mathx.median([1, 4, 2, 3]),
                mathx["var"]([1, 2, 3]).toFixed(12),
                mathx.std(sample),
                mathx.cv(sample),
                mathx.mode([3, 1, 3, 2, 2, 3]),
                mathx.dist([0, 0], { x: 3, y: 4 }),
                mathx.dist([0, 0], [3, 4], 2),
                mathx.logMn(2, 8),
                mathx.floorLog(2, 9),
                mathx.ceilLog(2, 9),
                mathx.roundLog(2, 9),
                mathx.floorPow(2, 9),
                mathx.ceilPow(2, 9),
                mathx.roundPow(2, 9),
                mathx.max(1, 7, 3),
                mathx.maxi([1, 7], [3]),
                mathx.min(1, 7, 3),
                mathx.mini([1, 7], [3]),
                target.sum(1, 2, 3),
                targetAll.sum(1, 2, 3),
                compat.jsox.mathx.sum(2, 3),
                globalThis.jsox.mathx.sum(1, 1),
                blocked,
                unsupported,
                compat.status().implementedTasks.indexOf("P13-27") >= 0
              ].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-utility-jsox.js>",
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("jsox mathx utility facade", result)
        assertTrue(result.stdout.contains("jsox.resolve=jsox|jsox.mathx"))
        assertTrue(result.stdout.contains("jsox.shadow=false|false"))
        assertTrue(result.stdout.contains("jsox.frozen=true|true|true|true|true|true"))
        assertTrue(result.stdout.contains("jsox.methods=function|function|function|function|function|function"))
        assertTrue(
            result.stdout.contains(
                "jsox.values=6|2.5|2.5|2.5|0.666666666667|2|0.4|3|5|5|3|3|4|3|8|16|8|7|7|1|1|6|6|5|2|ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|true",
            ),
        )
    }
}
