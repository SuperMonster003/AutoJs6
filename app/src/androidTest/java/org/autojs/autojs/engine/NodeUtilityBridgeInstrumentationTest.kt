package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeUtilityBridgeInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun jsJsoxMathxPureFacadeBlocksShadowingAndDefaultGlobalMutation() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node utility-heavy Rhino module regression")

        val workDir = File(context.cacheDir, "node-utility-jsox-shadow").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create utility bridge working directory: $absolutePath" }
        }
        // A bare working directory only ships its explicit inputs to the plugin (entry, env paths, module-source
        // overlays), so the planted packages also travel as module-source overlays; otherwise the plugin never
        // sees them and the shadow check would be vacuous.
        val shadowPackageSource = """module.exports = { shadow: true };"""
        val shadowModules = listOf("jsox", "jsox.mathx", "jsox.arrayx", "jsox.numberx")
        shadowModules.forEach { name ->
            File(workDir, "node_modules/$name").apply {
                check(mkdirs()) { "Failed to create $name shadow module directory: $absolutePath" }
            }.resolve("index.js").writeText(shadowPackageSource)
        }

        val result = NodePluginTestHarness.runPluginScript(
            """
            "nodejs";
            (function main() {
              const assert = require("assert");
              const compat = require("autojs6:compat");
              const jsox = require("jsox");
              const mathx = require("jsox.mathx");
              const arrayx = require("jsox.arrayx");
              const numberx = require("jsox.numberx");
              const sample = [2, 4, 4, 4, 5, 5, 7, 9];
              const target = {};
              const targetArray = {};
              const targetNumber = {};
              const targetAll = {};
              const extended = jsox.extend(target, "Mathx");
              const extendedArray = jsox.extend(targetArray, "Arrayx");
              const extendedNumber = jsox.extend(targetNumber, "Numberx");
              const extendedAll = jsox.extendAll(targetAll);
              const arraySortTarget = ["2", "10", "1"];
              const arraySortValue = arrayx.sortBy(arraySortTarget, Number).join(",");
              const arrayDescTarget = [1, 3, 2];
              const arrayDescValue = arrayx.sortDescending(arrayDescTarget).join(",");
              const shuffleTarget = [1, 2, 3];
              const shuffleOk = arrayx.shuffle(shuffleTarget) === shuffleTarget && shuffleTarget.length === 3 && arrayx.sorted(shuffleTarget).join(",") === "1,2,3";
              let blocked = "";
              try {
                jsox.extend("Mathx");
              } catch (error) {
                blocked = String(error && (error.autojs6Code || error.code || ""));
              }
              let unsupported = "";
              try {
                jsox.extend({}, "Zip");
              } catch (error) {
                unsupported = String(error && (error.autojs6Code || error.code || ""));
              }
              const compatInstall = compat.install({ globals: true });
              assert.strictEqual(extended, target);
              assert.strictEqual(extendedArray, targetArray);
              assert.strictEqual(extendedNumber, targetNumber);
              assert.strictEqual(extendedAll, targetAll);
              assert.strictEqual(jsox.mathx, jsox.Mathx);
              assert.strictEqual(jsox.arrayx, jsox.Arrayx);
              assert.strictEqual(jsox.numberx, jsox.Numberx);
              assert.strictEqual(jsox.mathx, mathx);
              assert.strictEqual(jsox.arrayx, arrayx);
              assert.strictEqual(jsox.numberx, numberx);
              assert.strictEqual(compat.jsox.mathx, mathx);
              assert.strictEqual(compat.jsox.arrayx, arrayx);
              assert.strictEqual(compat.jsox.numberx, numberx);
              assert.strictEqual(globalThis.jsox, compat.jsox);
              assert(compatInstall.globals.indexOf("jsox") >= 0);
              console.log("jsox.resolve=" + [require.resolve("jsox"), require.resolve("jsox.mathx"), require.resolve("jsox.arrayx"), require.resolve("jsox.numberx")].join("|"));
              console.log("jsox.shadow=" + [jsox.shadow === true, mathx.shadow === true, arrayx.shadow === true, numberx.shadow === true].join("|"));
              console.log("jsox.frozen=" + [Object.isFrozen(jsox), Object.isFrozen(mathx), Object.isFrozen(arrayx), Object.isFrozen(numberx), Object.isFrozen(jsox.mathx), Object.isFrozen(jsox.arrayx), Object.isFrozen(jsox.numberx), Object.isFrozen(compat.jsox), Object.isFrozen(compat.jsox.mathx), Object.isFrozen(compat.jsox.arrayx), Object.isFrozen(compat.jsox.numberx), Object.isFrozen(jsox.status())].join("|"));
              console.log("jsox.methods=" + [typeof jsox, typeof jsox.extend, typeof jsox.extendAll, typeof jsox.status, typeof mathx.randomInt, typeof mathx["var"], typeof arrayx.sortedBy, typeof numberx.parseAny, typeof numberx.ICU].join("|"));
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
                arrayx.distinct([1, 2, 1, 3]).join(","),
                arrayx.distinctBy(["a", "bb", "c"], function(value) { return value.length; }).join(","),
                arrayx.union([1, 2], [2, 3]).join(","),
                arrayx.intersect([1, 2, 3], [2, 3, 4]).join(","),
                arrayx.subtract([1, 2, 3], [2]).join(","),
                arrayx.differ([1, 2, 3], [2, 4]).join(","),
                arrayx.sorted([80, 70, 9]).join(","),
                arrayx.sortedDescending([1, 2, 3]).join(","),
                arrayx.sortedBy(["aaa", "b", "cc"], function(value) { return value.length; }).join(","),
                arrayx.sortedByDescending(["aaa", "b", "cc"], function(value) { return value.length; }).join(","),
                arraySortValue,
                arrayDescValue,
                targetArray.union([1], [1, 2]).join(","),
                targetAll.clamp(5, [10, 30]),
                targetAll.union([1], [1, 2]).join(","),
                compat.jsox.arrayx.sorted([2, 1]).join(","),
                globalThis.jsox.arrayx.sorted([3, 1]).join(","),
                numberx.ICU,
                numberx.check(1, "<", 2, "<=", 2),
                numberx.clamp(20, [10, 30, 40]),
                numberx.clamp(5, [10, 30]),
                numberx.clampTo(390, [0, 360]),
                numberx.toFixedNum(123.456, 2),
                numberx.padStart(5, 2, 0),
                numberx.padEnd(5, 2, 0),
                numberx.parseFloat("0.8", 16),
                numberx.parsePercent("2%%"),
                numberx.parseRatio("18:9"),
                numberx.parseAny("0xFF"),
                numberx.ensureNumberLike("20%"),
                numberx.ensureNumbersLike(["1", "2%"]).join(","),
                targetNumber.toFixedNum(3.004, 2),
                compat.jsox.numberx.padStart(5, 2, 0),
                globalThis.jsox.numberx.parseRatio("3:2"),
                arrayx.ensureArray([], [1]) === undefined,
                shuffleOk,
                blocked,
                unsupported,
                compat.status().implementedTasks.indexOf("P13-27") >= 0
              ].join("|"));
            })();
            """.trimIndent(),
            "<embedded-autojs-utility-jsox.js>",
            workingDirectory = workDir,
            moduleSources = shadowModules.associate { "node_modules/$it/index.js" to shadowPackageSource },
        )

        NodePluginTestHarness.assertSucceeded("jsox mathx utility facade", result)
        NodePluginTestHarness.assertStdoutContains("jsox mathx utility facade", result, "jsox.resolve=autojs6:jsox|autojs6:jsox.mathx|autojs6:jsox.arrayx|autojs6:jsox.numberx")
        NodePluginTestHarness.assertStdoutContains("jsox mathx utility facade", result, "jsox.shadow=false|false|false|false")
        NodePluginTestHarness.assertStdoutContains("jsox mathx utility facade", result, "jsox.frozen=true|true|true|true|true|true|true|true|true|true|true|true")
        NodePluginTestHarness.assertStdoutContains("jsox mathx utility facade", result, "jsox.methods=function|function|function|function|function|function|function|function|number")
        assertTrue(
            result.stdout.contains(
                "jsox.values=6|2.5|2.5|2.5|0.666666666667|2|0.4|3|5|5|3|3|4|3|8|16|8|7|7|1|1|6|6|5|2|1,2,3|a,bb|1,2,3|2,3|1,3|1,3,4|9,70,80|3,2,1|b,cc,aaa|aaa,cc,b|1,2,10|3,2,1|1,2|10|1,2|1,2|1,3|996|true|20|10|30|123.46|05|50|0.5|0.0002|2|255|0.2|1,0.02|3|05|1.5|true|true|ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN|ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED|true",
            ),
        )
    }
}
