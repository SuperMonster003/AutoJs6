package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

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

    @Test
    fun processPermissionReadonlyFacadeReflectsManifestAndScopedFsPolicy() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node process.permission compatibility facade")

        val workDir = File(
            NodeEmbeddedTestHarness.targetContext.cacheDir,
            "node-process-permission-${System.nanoTime()}",
        ).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create process.permission work dir: $absolutePath" }
        }
        File(workDir, "allowed.txt").writeText("allowed")
        File(workDir, "nested").mkdirs()

        val declaredManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            listOf("shell", "accessibility", "screen_capture", "notifications", "sensors", "ocr", "network"),
        )
        val declared = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
            "nodejs";
            const assert = require("assert/strict");
            const bridge = require("autojs6:bridge");
            const profile = require("autojs6:profile");
            const permission = process.permission;
            const expectedWorker = profile.featureFlags.worker_threads.enabled === true;

            function codeOf(error) {
              return String(error && (error.autojs6Code || error.code || error.name || ""));
            }

            assert.strictEqual(typeof permission, "object");
            assert.strictEqual(typeof permission.has, "function");
            assert.strictEqual(typeof permission.query, "function");
            assert.strictEqual(permission.model, "autojs6-readonly-capability-query");
            assert.strictEqual(permission.exactNodePermissionModel, false);

            const descriptor = Object.getOwnPropertyDescriptor(process, "permission");
            assert.strictEqual(descriptor.enumerable, true);
            assert.strictEqual(descriptor.writable, false);
            assert.strictEqual(Object.isFrozen(permission), true);

            assert.strictEqual(permission.has("fs.read"), true);
            assert.strictEqual(permission.has("fs.write"), true);
            assert.strictEqual(permission.has("fs.read", "allowed.txt"), true);
            assert.strictEqual(permission.has("fs.write", "nested/new.txt"), true);
            assert.strictEqual(permission.has("fs.read", "../escape.txt"), false);
            assert.strictEqual(permission.has("fs.write", "../escape.txt"), false);
            assert.strictEqual(permission.has("fs.read", "/"), false);
            assert.strictEqual(permission.has("fs.read", "bad" + String.fromCharCode(0) + "path"), false);

            assert.strictEqual(permission.has("shell"), true);
            assert.strictEqual(permission.has("accessibility"), true);
            assert.strictEqual(permission.has("screen.capture"), true);
            assert.strictEqual(permission.has("notifications"), true);
            assert.strictEqual(permission.has("sensors"), true);
            assert.strictEqual(permission.has("ocr"), true);
            assert.strictEqual(permission.has("network"), false);
            assert.strictEqual(permission.has("worker"), expectedWorker);
            assert.strictEqual(permission.has("child"), false);
            assert.strictEqual(permission.has("child_process"), false);
            assert.strictEqual(permission.has("native_addon"), false);
            assert.strictEqual(permission.has("unknown.scope"), false);

            let invalidCode = "";
            try {
              permission.has(null);
            } catch (error) {
              invalidCode = codeOf(error);
            }
            assert.strictEqual(invalidCode, "ERR_INVALID_ARG_TYPE");

            try {
              permission.has = function() { return true; };
            } catch (_) {}
            assert.strictEqual(process.permission.has("network"), false);

            const query = permission.query();
            assert.strictEqual(Object.isFrozen(query), true);
            assert.strictEqual(Object.isFrozen(query.scopes), true);
            assert.strictEqual(Object.isFrozen(query.scopedFs), true);
            assert.strictEqual(Object.isFrozen(query.manifest), true);
            assert.strictEqual(query.exactNodePermissionModel, false);
            assert.deepStrictEqual(query.scopeNames, [
              "fs.read",
              "fs.write",
              "network",
              "worker",
              "shell",
              "accessibility",
              "screen_capture",
              "notifications",
              "sensors",
              "ocr"
            ]);
            assert.strictEqual(query.scopes["fs.read"], true);
            assert.strictEqual(query.scopes["fs.write"], true);
            assert.strictEqual(query.scopes.shell, true);
            assert.strictEqual(query.scopes.accessibility, true);
            assert.strictEqual(query.scopes.screen_capture, true);
            assert.strictEqual(query.scopes.notifications, true);
            assert.strictEqual(query.scopes.sensors, true);
            assert.strictEqual(query.scopes.ocr, true);
            assert.strictEqual(query.scopes.network, false);
            assert.strictEqual(query.scopes.worker, expectedWorker);
            assert.strictEqual(query.manifest.enforced, true);
            assert.strictEqual(query.manifest.normalizedPermissions.includes("shell"), true);
            assert.strictEqual(profile.processPermission.scopes.shell, true);
            assert.strictEqual(profile.processPermission.scopes.network, false);

            console.log("process.permission.declared=" + [
              query.scopes["fs.read"],
              query.scopes["fs.write"],
              permission.has("shell"),
              permission.has("accessibility"),
              permission.has("screen_capture"),
              permission.has("notifications"),
              permission.has("sensors"),
              permission.has("ocr"),
              permission.has("network"),
              permission.has("worker"),
              permission.has("fs.read", "../escape.txt"),
              invalidCode,
              bridge.__test.pendingCount()
            ].join("|"));
            """.trimIndent(),
            sourceName = "<node-process-permission-declared.js>",
            workingDirectory = workDir,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to declaredManifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("process.permission declared manifest", declared)
        val expectedWorkerPermission = BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED.toString()
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("process.permission declared manifest", declared),
            declared.stdout.contains(
                "process.permission.declared=true|true|true|true|true|true|true|true|false|$expectedWorkerPermission|false|ERR_INVALID_ARG_TYPE|0",
            ),
        )

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val restrictedManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val restricted = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
            "nodejs";
            const assert = require("assert/strict");
            const bridge = require("autojs6:bridge");
            const permission = process.permission;
            const query = permission.query();

            assert.strictEqual(permission.has("fs.read", "allowed.txt"), true);
            assert.strictEqual(permission.has("fs.write", "nested/restricted.txt"), true);
            assert.strictEqual(permission.has("shell"), false);
            assert.strictEqual(permission.has("accessibility"), false);
            assert.strictEqual(permission.has("media_projection"), false);
            assert.strictEqual(permission.has("notifications"), false);
            assert.strictEqual(permission.has("sensors"), false);
            assert.strictEqual(permission.has("ocr"), false);
            assert.strictEqual(permission.has("network"), false);

            console.log("process.permission.restricted=" + [
              query.scopes["fs.read"],
              query.scopes["fs.write"],
              query.scopes.shell,
              query.scopes.accessibility,
              query.scopes.screen_capture,
              query.scopes.notifications,
              query.scopes.sensors,
              query.scopes.ocr,
              query.scopes.network,
              query.manifest.normalizedPermissions.join(","),
              bridge.__test.pendingCount()
            ].join("|"));
            """.trimIndent(),
            sourceName = "<node-process-permission-restricted.js>",
            workingDirectory = workDir,
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to restrictedManifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("process.permission restricted manifest", restricted)
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("process.permission restricted manifest", restricted),
            restricted.stdout.contains(
                "process.permission.restricted=true|true|false|false|false|false|false|false|false|toast|0",
            ),
        )
    }
}
