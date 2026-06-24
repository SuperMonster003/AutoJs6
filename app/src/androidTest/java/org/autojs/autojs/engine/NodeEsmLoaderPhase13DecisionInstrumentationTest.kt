package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeEsmLoaderPhase13DecisionInstrumentationTest {

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
    fun esmLoaderProfileRecordsPartialParityAndDeniedBoundaries() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-41 ESM loader parity decision")

        val workDir = File(context.cacheDir, "node-esm-loader-p13-41-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-41 ESM loader work dir: $absolutePath" }
        }
        try {
            File(workDir, "dep.mjs").writeText("""export default { value: "dep" };""")

            val profileResult = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async () => {
                      const profile = require("autojs6:profile");
                      const esmLoaderProfile = profile.esmLoaderProfile || {};
                      console.log("p13esm.profile=" + [
                        esmLoaderProfile.status,
                        esmLoaderProfile.defaultEnabled,
                        esmLoaderProfile.requestEnabled,
                        esmLoaderProfile.dynamicImportDefaultEnabled,
                        esmLoaderProfile.dynamicImportEnabled,
                        esmLoaderProfile.implementation,
                        esmLoaderProfile.mjsEntry,
                        esmLoaderProfile.packageTypeModuleEntry,
                        esmLoaderProfile.topLevelAwait,
                        esmLoaderProfile.staticImport,
                        esmLoaderProfile.dynamicImport,
                        esmLoaderProfile.packageExports,
                        esmLoaderProfile.packageImports,
                        esmLoaderProfile.jsonImportAttributes,
                        esmLoaderProfile.importMeta,
                        esmLoaderProfile.cjsInterop,
                        esmLoaderProfile.moduleSources,
                        esmLoaderProfile.packagedBehavior,
                        esmLoaderProfile.sourceMaps,
                        esmLoaderProfile.loaderHooks,
                        esmLoaderProfile.rawNodeModuleLoader,
                        esmLoaderProfile.networkImports,
                        esmLoaderProfile.fileUrlImports,
                        esmLoaderProfile.dataUrlImports,
                        esmLoaderProfile.absolutePathImports,
                        esmLoaderProfile.workingDirectoryEscape,
                        esmLoaderProfile.disabledBuiltinImports,
                        esmLoaderProfile.encryptedGraphParity,
                        esmLoaderProfile.pendingEvaluationCleanup,
                        esmLoaderProfile.realEsmCorpus,
                        esmLoaderProfile.rawNodeLoader,
                        esmLoaderProfile.customConditions,
                        Object.isFrozen(esmLoaderProfile)
                      ].join("|"));
                      const mod = await import("./dep.mjs");
                      let networkDenied = false;
                      try {
                        await import("https://example.test/dep.mjs");
                      } catch (error) {
                        networkDenied = error && error.code === "ERR_AUTOJS6_NETWORK_DISABLED";
                      }
                      let fileUrlDenied = false;
                      try {
                        await import("file:///tmp/dep.mjs");
                      } catch (error) {
                        fileUrlDenied = error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED";
                      }
                      console.log("p13esm.dynamic=" + [mod.default.value, networkDenied, fileUrlDenied].join("|"));
                    })().catch((error) => {
                      console.error("p13esm.profile.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = File(workDir, "profile.cjs").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-41 ESM loader profile", profileResult)
            assertTrue(
                profileResult.stdout,
                profileResult.stdout.contains(
                    "p13esm.profile=partial_default|true|true|true|true|autojs6_managed_transform|" +
                        "partial|partial|partial|local_scoped_partial|local_scoped_partial|partial|" +
                        "partial|partial|scoped_partial|sync_esm_only|partial|focused_smoke_partial|" +
                        "sourceURL_stack_only|denied|denied|denied|denied|denied|denied|denied|" +
                        "denied|not_promoted|not_proven|partial_required|false|false|true",
                ),
            )
            assertTrue(profileResult.stdout, profileResult.stdout.contains("p13esm.dynamic=dep|true|true"))

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            File(workDir, "package.json").writeText(
                """
                {
                  "type": "module",
                  "imports": {
                    "#alias": {
                      "import": "./alias.mjs",
                      "default": "./alias.cjs"
                    }
                  }
                }
                """.trimIndent(),
            )
            File(workDir, "alias.mjs").writeText("""export default { value: "alias-esm" };""")
            File(workDir, "alias.cjs").writeText("""module.exports = { value: "alias-cjs" };""")
            File(workDir, "config.json").writeText("""{"name":"json-attr"}""")
            File(workDir, "legacy.cjs").writeText("""module.exports = { value: "legacy-cjs" };""")
            val packageDir = File(workDir, "node_modules/p13-esm-pkg").apply { mkdirs() }
            File(packageDir, "package.json").writeText(
                """
                {
                  "name": "p13-esm-pkg",
                  "exports": {
                    ".": {
                      "import": "./entry.mjs",
                      "default": "./entry.cjs"
                    }
                  }
                }
                """.trimIndent(),
            )
            File(packageDir, "entry.mjs").writeText("""export default { value: "pkg-esm" };""")
            File(packageDir, "entry.cjs").writeText("""module.exports = { value: "pkg-cjs" };""")

            val entryResult = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    import dep from "./dep.mjs";
                    import alias from "#alias";
                    import pkg from "p13-esm-pkg";
                    import config from "./config.json" with { type: "json" };
                    const legacy = await import("./legacy.cjs");
                    const awaited = await Promise.resolve("tla");
                    console.log("p13esm.entry=" + [
                      dep.value,
                      alias.value,
                      pkg.value,
                      config.name,
                      legacy.default.value,
                      awaited,
                      import.meta.main,
                      import.meta.resolve("./dep.mjs").startsWith("file:")
                    ].join("|"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-41 ESM loader entry", entryResult)
            assertTrue(
                entryResult.stdout,
                entryResult.stdout.contains("p13esm.entry=dep|alias-esm|pkg-esm|json-attr|legacy-cjs|tla|true|true"),
            )
            assertTrue(entryResult.nativeValues["embedded_script.esm_enabled"] == "true")
            assertTrue(entryResult.nativeValues["embedded_script.esm_entry"] == "true")
            assertTrue(entryResult.nativeValues["embedded_script.dynamic_import_enabled"] == "true")
            assertTrue(entryResult.nativeValues["embedded_script.package_type_module_policy"].orEmpty().contains("type=module"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
