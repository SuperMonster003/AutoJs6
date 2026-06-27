package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWasiPhase13DecisionInstrumentationTest {

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
    fun wasiProfileKeepsRawWasiDeniedAndControlledFacadeDeferred() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-38 controlled WASI decision")

        val workDir = File(context.cacheDir, "node-wasi-p13-38-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-38 WASI work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/wasi").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const wasiProfile = profile.wasiProfile || {};

                    function detailOf(error) {
                      return [
                        error && error.code,
                        error && error.autojs6Code,
                        error && error.legacyCode
                      ].filter(Boolean).join("/");
                    }
                    function expectDenied(label, action) {
                      try {
                        action();
                      } catch (error) {
                        console.log("p13wasi." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13wasi.profile=" + [
                      wasiProfile.status,
                      wasiProfile.defaultEnabled,
                      Array.isArray(wasiProfile.targetProfiles) && wasiProfile.targetProfiles.join(","),
                      wasiProfile.pureWebAssembly,
                      wasiProfile.rawNodeWasi,
                      wasiProfile.controlledFacade,
                      wasiProfile.module,
                      wasiProfile.preopens,
                      wasiProfile.workingDirectoryPreopen,
                      wasiProfile.authorizedRoots,
                      wasiProfile.args,
                      wasiProfile.env,
                      wasiProfile.fd,
                      wasiProfile.stdio,
                      wasiProfile.clock,
                      wasiProfile.random,
                      wasiProfile.network,
                      wasiProfile.processExit,
                      wasiProfile.symlinkEscape,
                      wasiProfile.workerIntegration,
                      wasiProfile.packagedBehavior,
                      wasiProfile.rawHostPaths,
                      wasiProfile.rawFdNumbers,
                      Object.isFrozen(wasiProfile),
                      Object.isFrozen(wasiProfile.targetProfiles)
                    ].join("|"));
                    console.log("p13wasi.boundary=" + [
                      profile.disabledBuiltins.includes("wasi"),
                      typeof globalThis.__autojs6_wasi,
                      typeof globalThis.wasi,
                      typeof WebAssembly,
                      typeof WebAssembly.compile,
                      typeof WebAssembly.instantiate
                    ].join("|"));
                    expectDenied("requireBare", () => require("wasi"));
                    expectDenied("requireNodePrefix", () => require("node:wasi"));
                    expectDenied("resolveBare", () => require.resolve("wasi"));
                    expectDenied("resolveNodePrefix", () => require.resolve("node:wasi"));
                    console.log("p13wasi.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/wasi/");
                    }));
                    console.log("p13wasi.ready=true");
                """.trimIndent(),
                sourceName = "<node-wasi-phase13-p13-38.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-38 controlled WASI decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13wasi.profile=disabled_by_default|false|desktop_compat_opt_in|" +
                        "available|denied|deferred|denied|scoped_or_authorized_roots_required|" +
                        "deferred|required_before_enablement|explicit_only|empty_by_default|" +
                        "virtualized_required|bounded_required|limited_required|limited_required|" +
                        "denied|denied|must_deny|deferred|not_promoted|false|false|true|true",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13wasi.boundary=true|undefined|undefined|object|function|function"))
            assertTrue(
                result.stdout,
                result.stdout.contains("p13wasi.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13wasi.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13wasi.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13wasi.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13wasi.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13wasi.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
