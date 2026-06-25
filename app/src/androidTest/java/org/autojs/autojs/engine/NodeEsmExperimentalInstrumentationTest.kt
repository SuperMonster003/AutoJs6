package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeEsmExperimentalInstrumentationTest {

    private val context
        get() = NodeEmbeddedTestHarness.targetContext

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun tearDown() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun mjsEntryCanStillRejectWhenEsmIsExplicitlyDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM partial explicit-off regression")
        assertTrue(BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED)

        val workDir = freshWorkDir("esm-default-off")
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """console.log("unexpected");""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = false,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("mjs disabled", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_ESM_UNSUPPORTED, result.errorCode)
        assertEquals("false", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.esm_entry"])
    }

    @Test
    fun partialMjsEntrySupportsLocalStaticImportAndTopLevelAwaitByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM partial bootstrap")
        val workDir = freshWorkDir("esm-local-import")
        val entry = File(workDir, "main.mjs")
        val dependency = File(workDir, "dep.mjs").apply {
            writeText(
                """
                export const value = "dep";
                export function suffix() { return "ok"; }
                """.trimIndent(),
            )
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                import { value, suffix as s } from "./dep.mjs";
                const combined = await Promise.resolve(value + "." + s());
                console.log("esm.local=" + combined);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("partial mjs import", result)
        assertTrue(result.stdout.contains("esm.local=dep.ok"))
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertEquals(entry.absolutePath, result.nativeValues["embedded_script.esm_module_graph_root"])
        assertEquals("2", result.nativeValues["embedded_script.esm_module_graph_size"])
        assertTrue(result.nativeValues["embedded_script.esm_module_graph_modules"].orEmpty().contains(dependency.absolutePath))
    }

    @Test
    fun partialMjsEntrySupportsInjectedModuleSourcesGraph() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM moduleSources graph")
        val workDir = freshWorkDir("esm-module-sources")
        val entry = File(workDir, "main.mjs")
        val dependency = File(workDir, "dep.mjs")
        val legacy = File(workDir, "legacy.cjs")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                import dep, { named, explode } from "./dep.mjs";
                import legacy from "./legacy.cjs";
                let stackOk = false;
                try {
                  explode();
                } catch (error) {
                  stackOk = String(error && error.stack || "").includes("dep.mjs");
                }
                console.log("esm.moduleSources.ready=" + (dep.value === "esm" && named === "named" && legacy.value === "cjs"));
                console.log("esm.moduleSources.stack=" + stackOk);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
            moduleSources = mapOf(
                dependency.absolutePath to """
                    export default { value: "esm" };
                    export const named = "named";
                    export function explode() { throw new Error("moduleSources stack"); }
                """.trimIndent(),
                legacy.absolutePath to """module.exports = { value: "cjs" };""",
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("partial ESM moduleSources graph", result)
        assertTrue(result.stdout.contains("esm.moduleSources.ready=true"))
        assertTrue(result.stdout.contains("esm.moduleSources.stack=true"))
        assertEquals("2", result.nativeValues["embedded_script.esm_module_graph_size"])
        assertTrue(result.nativeValues["embedded_script.esm_module_graph_modules"].orEmpty().contains(dependency.absolutePath))
    }

    @Test
    fun partialPackageTypeModuleEntryRunsAsEsmByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package type=module bootstrap")
        val workDir = freshWorkDir("esm-type-module")
        File(workDir, "package.json").writeText("""{"type":"module"}""")
        val entry = File(workDir, "main.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const value = await Promise.resolve("type-module");
                console.log("esm.type=" + value);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("partial type=module entry", result)
        assertTrue(result.stdout.contains("esm.type=type-module"))
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertEquals("type=module accepted by Embedded Node partial ESM", result.nativeValues["embedded_script.package_type_module_policy"])
    }

    @Test
    fun experimentalLocalStaticImportCannotEscapeWorkingDirectory() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM scoped import guard")
        val parent = freshWorkDir("esm-import-escape-parent")
        val workDir = File(parent, "project").apply { mkdirs() }
        File(parent, "outside.mjs").writeText("""export const value = "outside";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import { value } from "../outside.mjs"; console.log(value);""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("experimental import escape", result), result.succeeded)
        assertEquals("ERR_AUTOJS6_FS_PATH_ESCAPE", result.errorCode)
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertTrue(result.nativeValues["embedded_script.esm_last_denied_reason"].orEmpty().contains("escapes working directory"))
    }

    @Test
    fun experimentalEsmImportsCommonJsDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM importing CommonJS")
        val workDir = freshWorkDir("esm-import-cjs")
        val entry = File(workDir, "main.mjs")
        File(workDir, "bridge.cjs").writeText("""module.exports = { value: "cjs", nested: { ok: true } };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import pkg from "./bridge.cjs";
                console.log("esm.cjs.default=" + pkg.value + "/" + pkg.nested.ok);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental esm imports cjs default", result)
        assertTrue(result.stdout.contains("esm.cjs.default=cjs/true"))
    }

    @Test
    fun experimentalEsmImportsSupportedBuiltins() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM importing supported builtins")
        val workDir = freshWorkDir("esm-import-builtins")
        File(workDir, "node_modules/buffer/index.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true, Buffer: function ShadowBuffer() {} };""")
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import bareBuffer, { Buffer as BareBuffer } from "buffer";
                import nodeBuffer, { Buffer as NodeBuffer, Blob as NodeBlob, atob, btoa } from "node:buffer";
                import path from "node:path";
                import { EventEmitter } from "node:events";
                const events = new EventEmitter();
                let seen = false;
                events.on("ready", () => { seen = true; });
                events.emit("ready");
                const blobText = await new NodeBlob([NodeBuffer.from("blob")]).text();
                const bufferOk =
                  BareBuffer === Buffer &&
                  NodeBuffer === Buffer &&
                  bareBuffer === nodeBuffer &&
                  nodeBuffer.shadow !== true &&
                  NodeBuffer.from("buffer").toString("base64") === "YnVmZmVy" &&
                  atob(btoa("roundtrip")) === "roundtrip" &&
                  blobText === "blob";
                console.log("esm.builtin=" + path.basename("/tmp/entry.txt") + "/" + seen + "/" + bufferOk);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental esm imports builtins", result)
        assertTrue(result.stdout.contains("esm.builtin=entry.txt/true/true"))
    }

    @Test
    fun experimentalEsmImportDisabledBuiltinFails() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM disabled builtin guard")
        val workDir = freshWorkDir("esm-import-disabled-builtin")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import cp from "node:child_process"; console.log(cp);""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("experimental disabled builtin import", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED, result.errorCode)
        assertTrue(result.errorMessage.orEmpty().contains("child_process"))
    }

    @Test
    fun experimentalCommonJsRequireMjsSupportsSyncGraphAndRejectsAsyncGraph() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("CJS require ESM sync boundary")
        val workDir = freshWorkDir("cjs-require-esm")
        File(workDir, "dep.mjs").writeText("""export const value = "esm";""")
        File(workDir, "async-dep.mjs").writeText("""export const value = await Promise.resolve("esm");""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const dep = require("./dep.mjs");
                console.log("cjs.require.esm=" + dep.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs require sync mjs", result)
        assertTrue(result.stdout.contains("cjs.require.esm=esm"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val asyncResult = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """require("./async-dep.mjs");""",
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("cjs require async mjs", asyncResult), asyncResult.succeeded)
        assertEquals("ERR_REQUIRE_ASYNC_MODULE", asyncResult.errorCode)
        assertTrue(asyncResult.errorMessage.orEmpty().contains("cannot synchronously load"))
    }

    @Test
    fun experimentalEsmMultilineStaticImportsWorkForRequireAndStaticImport() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM multiline static imports")
        val workDir = freshWorkDir("esm-multiline-imports")
        File(workDir, "side-effect.mjs").writeText("""globalThis.__autojs6MultilineImportSideEffect = "side";""")
        File(workDir, "dep.mjs").writeText(
            """
            export default { label: "dep" };
            export const first = "one";
            export const second = "two";
            """.trimIndent(),
        )
        File(workDir, "multi.mjs").writeText(
            """
            import"./side-effect.mjs"
            import dep, {
              first,
              second as renamed,
            } from "./dep.mjs"
            export const summary = [globalThis.__autojs6MultilineImportSideEffect, dep.label, first, renamed].join("/");
            """.trimIndent(),
        )
        File(workDir, "compact.mjs").writeText("""import{first as compactFirst}from"./dep.mjs";import{second as compactSecond}from"./dep.mjs"; export const compact = compactFirst + "/" + compactSecond;""")

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const mod = require("./multi.mjs");
                const compact = require("./compact.mjs");
                console.log("cjs.require.esm.multiline=" + mod.summary + "/" + compact.compact);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs require ESM multiline static import", cjs)
        assertTrue(cjs.stdout.contains("cjs.require.esm.multiline=side/dep/one/two/one/two"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import { summary } from "./multi.mjs";
                import{compact}from"./compact.mjs";
                console.log("esm.multiline=" + summary + "/" + compact);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ESM multiline static import", esm)
        assertTrue(esm.stdout.contains("esm.multiline=side/dep/one/two/one/two"))
    }

    @Test
    fun experimentalEsmReExportsWorkForRequireAndStaticImport() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM re-export support")
        val workDir = freshWorkDir("esm-reexports")
        File(workDir, "dep.mjs").writeText(
            """
            export const alpha = "alpha";
            export const hidden = "hidden";
            export default { label: "default" };
            const local = "local";
            export { local as publicName };
            """.trimIndent(),
        )
        File(workDir, "named.mjs").writeText("""export{alpha as alias,default as defaultName}from"./dep.mjs";""")
        File(workDir, "star.mjs").writeText(
            """
            export const alpha = "local-alpha";
            export*from"./dep.mjs";
            """.trimIndent(),
        )
        File(workDir, "namespace.mjs").writeText("""export*as bundle from"./dep.mjs";""")

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const named = require("./named.mjs");
                const star = require("./star.mjs");
                const namespace = require("./namespace.mjs");
                const dep = require("./dep.mjs");
                console.log("cjs.require.esm.reexport=" + [
                  named.alias,
                  named.defaultName.label,
                  star.alpha,
                  star.publicName,
                  Object.prototype.hasOwnProperty.call(star, "default"),
                  namespace.bundle.alpha,
                  namespace.bundle.default.label,
                  dep.publicName
                ].join("|"));
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs require ESM re-exports", cjs)
        assertTrue(cjs.stdout.contains("cjs.require.esm.reexport=alpha|default|local-alpha|local|false|alpha|default|local"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import { alias, defaultName } from "./named.mjs";
                import * as star from "./star.mjs";
                import { bundle } from "./namespace.mjs";
                console.log("esm.reexport.static=" + [
                  alias,
                  defaultName.label,
                  star.alpha,
                  star.publicName,
                  Object.prototype.hasOwnProperty.call(star, "default"),
                  bundle.alpha,
                  bundle.default.label
                ].join("|"));
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ESM static re-exports", esm)
        assertTrue(esm.stdout.contains("esm.reexport.static=alpha|default|local-alpha|local|false|alpha|default"))
    }

    @Test
    fun experimentalEsmJsonImportAttributesUseCommonJsJsonCache() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM JSON import attributes")
        val workDir = freshWorkDir("esm-json-attributes")
        File(workDir, "config.json").writeText("""{"name":"local-json","count":2}""")
        File(workDir, "dynamic.json").writeText("""{"name":"dynamic-json"}""")
        val packageDir = File(workDir, "node_modules/json-config-pkg").apply { mkdirs() }
        File(packageDir, "package.json").writeText(
            """
            {
              "name": "json-config-pkg",
              "exports": {
                ".": {
                  "import": "./config.json",
                  "require": "./config.json"
                }
              }
            }
            """.trimIndent(),
        )
        File(packageDir, "config.json").writeText("""{"name":"package-json"}""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import { createRequire } from "node:module";
                import localConfig from "./config.json" with { type: "json" };
                import packageConfig from "json-config-pkg" with { type: "json" };
                const req = createRequire(__filename);
                const requiredConfig = req("./config.json");
                localConfig.seenFromEsm = true;
                const dynamicConfig = await import("./dynamic.json", { with: { type: "json" } });
                console.log("esm.json.local=" + localConfig.name + "/" + localConfig.count);
                console.log("esm.json.cache=" + (requiredConfig === localConfig && req("./config.json").seenFromEsm === true));
                console.log("esm.json.package=" + packageConfig.name);
                console.log("esm.json.dynamic=" + dynamicConfig.default.name);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ESM JSON import attributes", result)
        assertTrue(result.stdout.contains("esm.json.local=local-json/2"))
        assertTrue(result.stdout.contains("esm.json.cache=true"))
        assertTrue(result.stdout.contains("esm.json.package=package-json"))
        assertTrue(result.stdout.contains("esm.json.dynamic=dynamic-json"))
    }

    @Test
    fun experimentalEsmJsonImportAttributesRejectMissingAndUnknownAttributes() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM JSON import attribute denial")
        val workDir = freshWorkDir("esm-json-attribute-denial")
        File(workDir, "config.json").writeText("""{"name":"local-json"}""")

        fun runDenied(label: String, source: String, messageToken: String) {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = File(workDir, "$label.mjs").absolutePath,
                workingDirectory = workDir,
                esmExperimentalEnabled = true,
            )
            assertFalse(NodeEmbeddedTestHarness.describeResult(label, result), result.succeeded)
            assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_ESM_UNSUPPORTED, result.errorCode)
            assertTrue(result.errorMessage.orEmpty(), result.errorMessage.orEmpty().contains(messageToken))
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }

        runDenied(
            label = "missing-json-attribute",
            source = """import config from "./config.json"; console.log(config);""",
            messageToken = """requires with { type: "json" }""",
        )
        runDenied(
            label = "unknown-json-attribute",
            source = """import config from "./config.json" with { type: "text" }; console.log(config);""",
            messageToken = "Only with",
        )
        runDenied(
            label = "legacy-json-assertion",
            source = """import config from "./config.json" assert { type: "json" }; console.log(config);""",
            messageToken = "import assertions are not supported",
        )
        runDenied(
            label = "dynamic-missing-json-attribute",
            source = """await import("./config.json");""",
            messageToken = """requires with { type: "json" }""",
        )
        runDenied(
            label = "dynamic-unknown-json-attribute",
            source = """await import("./config.json", { with: { type: "text" } });""",
            messageToken = """Only { with: { type: "json" } } is supported""",
        )
    }

    @Test
    fun experimentalImportMetaExposesScopedMetadataAndResolve() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM import.meta")
        val workDir = freshWorkDir("esm-import-meta")
        File(workDir, "dep.mjs").writeText(
            """
            export const depMain = import.meta.main;
            export const depUrl = import.meta.url;
            export const depFilename = import.meta.filename;
            """.trimIndent(),
        )
        val packageDir = File(workDir, "node_modules/meta-pkg").apply { mkdirs() }
        File(packageDir, "package.json").writeText(
            """
            {
              "name": "meta-pkg",
              "exports": {
                ".": "./entry.mjs"
              }
            }
            """.trimIndent(),
        )
        File(packageDir, "entry.mjs").writeText("""export default "meta-pkg";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import path from "node:path";
                import { depMain, depUrl, depFilename } from "./dep.mjs";
                const localResolved = import.meta.resolve("./dep.mjs");
                const packageResolved = import.meta.resolve("meta-pkg");
                const builtinResolved = import.meta.resolve("node:path");
                let denied = false;
                try {
                  import.meta.resolve("node:child_process");
                } catch (error) {
                  denied = error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED";
                }
                console.log("esm.meta.url=" + (import.meta.url.startsWith("file://") && import.meta.url.endsWith("/main.mjs")));
                console.log("esm.meta.filename=" + import.meta.filename.endsWith("/main.mjs"));
                console.log("esm.meta.dirname=" + (import.meta.dirname === path.dirname(import.meta.filename)));
                console.log("esm.meta.main=" + import.meta.main + "/" + depMain);
                console.log("esm.meta.dep=" + (depUrl.startsWith("file://") && depFilename.endsWith("/dep.mjs")));
                console.log("esm.meta.resolve=" + (localResolved.endsWith("/dep.mjs") && packageResolved.endsWith("/entry.mjs") && builtinResolved === "node:path"));
                console.log("esm.meta.denied=" + denied);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("ESM import.meta", result)
        assertTrue(result.stdout.contains("esm.meta.url=true"))
        assertTrue(result.stdout.contains("esm.meta.filename=true"))
        assertTrue(result.stdout.contains("esm.meta.dirname=true"))
        assertTrue(result.stdout.contains("esm.meta.main=true/false"))
        assertTrue(result.stdout.contains("esm.meta.dep=true"))
        assertTrue(result.stdout.contains("esm.meta.resolve=true"))
        assertTrue(result.stdout.contains("esm.meta.denied=true"))
    }

    @Test
    fun experimentalPackageExportsConditionsSplitByImporterMode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package condition selection")
        val workDir = freshWorkDir("esm-package-conditions")
        val packageDir = File(workDir, "node_modules/condition-pkg").apply { mkdirs() }
        File(packageDir, "package.json").writeText(
            """
            {
              "name": "condition-pkg",
              "exports": {
                ".": {
                  "import": "./esm-entry.cjs",
                  "require": "./cjs-entry.cjs",
                  "default": "./default-entry.cjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(packageDir, "esm-entry.cjs").writeText("""module.exports = { value: "import" };""")
        File(packageDir, "cjs-entry.cjs").writeText("""module.exports = { value: "require" };""")
        File(packageDir, "default-entry.cjs").writeText("""module.exports = { value: "default" };""")

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import pkg from "condition-pkg";
                console.log("esm.package.condition=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm package import condition", esm)
        assertTrue(esm.stdout.contains("esm.package.condition=import"))
        assertEquals("import", esm.nativeValues["embedded_script.package_resolution_selected_condition"])
        assertTrue(esm.stderr, esm.stderr.contains("[resolve] exports matched request=\".\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"import\""))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const pkg = require("condition-pkg");
                console.log("cjs.package.condition=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs package require condition", cjs)
        assertTrue(cjs.stdout.contains("cjs.package.condition=require"))
        assertEquals("require", cjs.nativeValues["embedded_script.package_resolution_selected_condition"])
        assertTrue(cjs.stderr, cjs.stderr.contains("[resolve] exports matched request=\".\""))
        assertTrue(cjs.stderr, cjs.stderr.contains("selectedCondition=\"require\""))
    }

    @Test
    fun experimentalPackageExportsAllowMjsTargetsAndDefaultFallback() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package exports .mjs targets")
        val workDir = freshWorkDir("esm-package-exports-mjs")
        val importPackage = File(workDir, "node_modules/import-target-pkg").apply { mkdirs() }
        File(importPackage, "package.json").writeText(
            """
            {
              "name": "import-target-pkg",
              "exports": {
                ".": {
                  "import": "./entry.mjs",
                  "require": "./entry.cjs",
                  "default": "./default.cjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(importPackage, "entry.mjs").writeText("""export default { value: "esm-import-target" };""")
        File(importPackage, "entry.cjs").writeText("""module.exports = { value: "cjs-require-target" };""")
        File(importPackage, "default.cjs").writeText("""module.exports = { value: "default-target" };""")

        val defaultPackage = File(workDir, "node_modules/default-target-pkg").apply { mkdirs() }
        File(defaultPackage, "package.json").writeText(
            """
            {
              "name": "default-target-pkg",
              "exports": {
                ".": {
                  "default": "./entry.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(defaultPackage, "entry.mjs").writeText("""export default { value: "esm-default-target" };""")

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import imported from "import-target-pkg";
                import fallback from "default-target-pkg";
                console.log("esm.exports.targets=" + imported.value + "/" + fallback.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm package exports .mjs target", esm)
        assertTrue(esm.stdout.contains("esm.exports.targets=esm-import-target/esm-default-target"))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"import\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"default\""))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const pkg = require("import-target-pkg");
                console.log("cjs.exports.target=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs package exports require target", cjs)
        assertTrue(cjs.stdout.contains("cjs.exports.target=cjs-require-target"))
        assertTrue(cjs.stderr, cjs.stderr.contains("selectedCondition=\"require\""))
    }

    @Test
    fun experimentalPackageImportsConditionsSplitByImporterMode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package imports condition selection")
        val workDir = freshWorkDir("esm-package-imports")
        File(workDir, "package.json").writeText(
            """
            {
              "imports": {
                "#dep": {
                  "import": "./esm-alias.mjs",
                  "require": "./cjs-alias.cjs",
                  "default": "./default-alias.cjs"
                },
                "#fallback": {
                  "default": "./fallback.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(workDir, "esm-alias.mjs").writeText("""export default { value: "import-alias" };""")
        File(workDir, "cjs-alias.cjs").writeText("""module.exports = { value: "require-alias" };""")
        File(workDir, "default-alias.cjs").writeText("""module.exports = { value: "default-alias" };""")
        File(workDir, "fallback.mjs").writeText("""export default { value: "fallback-alias" };""")

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import dep from "#dep";
                import fallback from "#fallback";
                console.log("esm.imports.conditions=" + dep.value + "/" + fallback.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm package imports conditions", esm)
        assertTrue(esm.stdout.contains("esm.imports.conditions=import-alias/fallback-alias"))
        assertTrue(esm.stderr, esm.stderr.contains("[resolve] imports matched request=\"#dep\""))
        assertTrue(esm.stderr, esm.stderr.contains("[resolve] imports matched request=\"#fallback\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"import\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"default\""))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const dep = require("#dep");
                console.log("cjs.imports.condition=" + dep.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs package imports conditions", cjs)
        assertTrue(cjs.stdout.contains("cjs.imports.condition=require-alias"))
        assertTrue(cjs.stderr, cjs.stderr.contains("[resolve] imports matched request=\"#dep\""))
        assertTrue(cjs.stderr, cjs.stderr.contains("selectedCondition=\"require\""))
    }

    @Test
    fun experimentalPackageAutojs6ConditionPreservesPackageKeyOrderAndPolicy() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package autojs6 condition")
        val workDir = freshWorkDir("esm-package-autojs6-condition")
        File(workDir, "package.json").writeText(
            """
            {
              "imports": {
                "#auto-alias": {
                  "autojs6": "./auto-alias.mjs",
                  "import": "./import-alias.mjs",
                  "default": "./default-alias.mjs"
                },
                "#env-alias": {
                  "development": "./development-alias.mjs",
                  "production": "./production-alias.mjs",
                  "default": "./default-env-alias.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(workDir, "auto-alias.mjs").writeText("""export default { value: "alias-autojs6" };""")
        File(workDir, "import-alias.mjs").writeText("""export default { value: "alias-import" };""")
        File(workDir, "default-alias.mjs").writeText("""export default { value: "alias-default" };""")
        File(workDir, "development-alias.mjs").writeText("""export default { value: "alias-development" };""")
        File(workDir, "production-alias.mjs").writeText("""export default { value: "alias-production" };""")
        File(workDir, "default-env-alias.mjs").writeText("""export default { value: "alias-env-default" };""")

        val autoFirstPackage = File(workDir, "node_modules/auto-first-pkg").apply { mkdirs() }
        File(autoFirstPackage, "package.json").writeText(
            """
            {
              "name": "auto-first-pkg",
              "exports": {
                ".": {
                  "autojs6": "./auto.mjs",
                  "import": "./import.mjs",
                  "default": "./default.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(autoFirstPackage, "auto.mjs").writeText("""export default { value: "pkg-autojs6" };""")
        File(autoFirstPackage, "import.mjs").writeText("""export default { value: "pkg-import" };""")
        File(autoFirstPackage, "default.mjs").writeText("""export default { value: "pkg-default" };""")

        val importFirstPackage = File(workDir, "node_modules/import-first-pkg").apply { mkdirs() }
        File(importFirstPackage, "package.json").writeText(
            """
            {
              "name": "import-first-pkg",
              "exports": {
                ".": {
                  "import": "./import.mjs",
                  "autojs6": "./auto.mjs",
                  "default": "./default.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(importFirstPackage, "auto.mjs").writeText("""export default { value: "pkg-autojs6-late" };""")
        File(importFirstPackage, "import.mjs").writeText("""export default { value: "pkg-import-first" };""")
        File(importFirstPackage, "default.mjs").writeText("""export default { value: "pkg-default" };""")

        val cjsPackage = File(workDir, "node_modules/cjs-auto-first-pkg").apply { mkdirs() }
        File(cjsPackage, "package.json").writeText(
            """
            {
              "name": "cjs-auto-first-pkg",
              "exports": {
                ".": {
                  "autojs6": "./auto.cjs",
                  "require": "./require.cjs",
                  "default": "./default.cjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(cjsPackage, "auto.cjs").writeText("""module.exports = { value: "cjs-autojs6" };""")
        File(cjsPackage, "require.cjs").writeText("""module.exports = { value: "cjs-require" };""")
        File(cjsPackage, "default.cjs").writeText("""module.exports = { value: "cjs-default" };""")

        val nativeAddonPackage = File(workDir, "node_modules/native-addon-auto-pkg").apply { mkdirs() }
        File(nativeAddonPackage, "package.json").writeText(
            """
            {
              "name": "native-addon-auto-pkg",
              "exports": {
                ".": {
                  "autojs6": "./addon.node",
                  "default": "./safe.mjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(nativeAddonPackage, "addon.node").writeText("")
        File(nativeAddonPackage, "safe.mjs").writeText("""export default { value: "safe" };""")

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import autoFirst from "auto-first-pkg";
                import importFirst from "import-first-pkg";
                import autoAlias from "#auto-alias";
                import envAlias from "#env-alias";
                let nativeAddonDenied = false;
                try {
                  await import("native-addon-auto-pkg");
                } catch (error) {
                  nativeAddonDenied = error && error.code === "ERR_AUTOJS6_NATIVE_ADDON_DISABLED";
                }
                console.log("esm.package.autojs6=" + autoFirst.value + "/" + importFirst.value + "/" + autoAlias.value + "/" + envAlias.value);
                console.log("esm.package.autojs6.denied=" + nativeAddonDenied);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm package autojs6 condition", esm)
        assertTrue(esm.stdout.contains("esm.package.autojs6=pkg-autojs6/pkg-import-first/alias-autojs6/alias-env-default"))
        assertTrue(esm.stdout.contains("esm.package.autojs6.denied=true"))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"autojs6\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"import\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"default\""))
        assertTrue(esm.stderr, esm.stderr.contains("ignoredConditions=\"development|production\""))
        assertEquals("autojs6", esm.nativeValues["embedded_script.package_resolution_selected_condition"])

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const pkg = require("cjs-auto-first-pkg");
                console.log("cjs.package.autojs6=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs package autojs6 condition", cjs)
        assertTrue(cjs.stdout.contains("cjs.package.autojs6=cjs-autojs6"))
        assertTrue(cjs.stderr, cjs.stderr.contains("selectedCondition=\"autojs6\""))
        assertEquals("autojs6", cjs.nativeValues["embedded_script.package_resolution_selected_condition"])
    }

    @Test
    fun experimentalNestedPackageImportAndSelfReferenceUseEsmConditions() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM nested package import and self-reference")
        val workDir = freshWorkDir("esm-nested-self-reference")

        val innerPackage = File(workDir, "node_modules/inner-pkg").apply { mkdirs() }
        File(innerPackage, "package.json").writeText(
            """{"name":"inner-pkg","exports":{".":{"import":"./entry.mjs","default":"./entry.cjs"}}}""",
        )
        File(innerPackage, "entry.mjs").writeText("""export default { value: "inner-esm" };""")
        File(innerPackage, "entry.cjs").writeText("""module.exports = { value: "inner-cjs" };""")

        val outerPackage = File(workDir, "node_modules/outer-pkg").apply { mkdirs() }
        File(outerPackage, "package.json").writeText(
            """{"name":"outer-pkg","exports":{".":{"import":"./entry.mjs","default":"./entry.cjs"}}}""",
        )
        File(outerPackage, "entry.mjs").writeText(
            """
            import inner from "inner-pkg";
            export default { value: inner.value + "/outer" };
            """.trimIndent(),
        )
        File(outerPackage, "entry.cjs").writeText("""module.exports = { value: "outer-cjs" };""")

        val selfPackage = File(workDir, "node_modules/self-pkg").apply { mkdirs() }
        File(selfPackage, "package.json").writeText(
            """
            {
              "name": "self-pkg",
              "exports": {
                ".": { "import": "./entry.mjs", "default": "./entry.cjs" },
                "./feature": { "import": "./feature.mjs", "default": "./feature.cjs" }
              }
            }
            """.trimIndent(),
        )
        File(selfPackage, "entry.mjs").writeText(
            """
            import { value as feature } from "self-pkg/feature";
            export default { value: feature + "/self" };
            """.trimIndent(),
        )
        File(selfPackage, "feature.mjs").writeText("""export const value = "self-feature-esm";""")
        File(selfPackage, "entry.cjs").writeText("""module.exports = { value: "self-cjs" };""")
        File(selfPackage, "feature.cjs").writeText("""exports.value = "self-feature-cjs";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import outer from "outer-pkg";
                import self from "self-pkg";
                console.log("esm.nested.self=" + outer.value + "/" + self.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm nested package and self-reference", result)
        assertTrue(result.stdout.contains("esm.nested.self=inner-esm/outer/self-feature-esm/self"))
        assertTrue(result.stderr, result.stderr.contains("[resolve] exports matched request=\"./feature\""))
        assertTrue(result.stderr, result.stderr.contains("/node_modules/self-pkg\" target=\"./feature.mjs\""))
        assertTrue(result.stderr, result.stderr.contains("selectedCondition=\"import\""))
    }

    @Test
    fun experimentalPackageNullBlockingAndUnsafeTargetsFail() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package denial paths")
        val workDir = freshWorkDir("esm-package-denials")

        val nullPackage = File(workDir, "node_modules/null-pkg").apply { mkdirs() }
        File(nullPackage, "package.json").writeText(
            """{"name":"null-pkg","exports":{"./blocked":null}}""",
        )

        val nullBlocked = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import blocked from "null-pkg/blocked"; console.log(blocked);""",
            sourceName = File(workDir, "null-main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("esm null blocking", nullBlocked), nullBlocked.succeeded)
        assertEquals("ERR_PACKAGE_PATH_NOT_EXPORTED", nullBlocked.errorCode)
        assertTrue(nullBlocked.errorMessage.orEmpty().contains("target=null"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val unsafePackage = File(workDir, "node_modules/unsafe-pkg").apply { mkdirs() }
        File(unsafePackage, "package.json").writeText(
            """{"name":"unsafe-pkg","exports":{".":{"import":"./../escape.mjs"}}}""",
        )
        File(workDir, "node_modules/escape.mjs").writeText("""export default { value: "escape" };""")

        val unsafe = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import unsafe from "unsafe-pkg"; console.log(unsafe);""",
            sourceName = File(workDir, "unsafe-main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("esm unsafe target", unsafe), unsafe.succeeded)
        assertEquals("ERR_INVALID_PACKAGE_TARGET", unsafe.errorCode)
        assertTrue(unsafe.errorMessage.orEmpty().contains("escapes Embedded Node package directory"))
    }

    @Test
    fun dynamicImportCanStillRejectWhenExplicitlyDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("dynamic import explicit-off regression")
        assertTrue(BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED)
        assertTrue(BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED)
        val workDir = freshWorkDir("esm-dynamic-default-off")
        File(workDir, "dep.mjs").writeText("""export const value = "unexpected";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """const mod = await import("./dep.mjs"); console.log(mod.value);""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
            dynamicImportExperimentalEnabled = false,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("dynamic import disabled", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_DYNAMIC_IMPORT_UNSUPPORTED, result.errorCode)
        assertTrue(result.errorMessage.orEmpty().contains("dynamic import is disabled"))
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.dynamic_import_enabled"])
    }

    @Test
    fun localDynamicImportSupportsRelativeEsmAndCjsModulesByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("local dynamic import")
        val workDir = freshWorkDir("esm-dynamic-local")
        val dependency = File(workDir, "dep.mjs").apply {
            writeText(
                """
                export const named = "named";
                export default { value: "default" };
                """.trimIndent(),
            )
        }
        File(workDir, "legacy.cjs").writeText("""module.exports = { value: "legacy" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const mod = await import("./dep.mjs");
                const legacy = await import("./legacy.cjs");
                console.log("esm.dynamic=" + mod.default.value + "/" + mod.named + "/" + legacy.default.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("local dynamic import", result)
        assertTrue(result.stdout.contains("esm.dynamic=default/named/legacy"))
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.dynamic_import_enabled"])
        assertTrue(result.nativeValues["embedded_script.esm_module_graph_modules"].orEmpty().contains(dependency.absolutePath))
    }

    @Test
    fun localDynamicImportSupportsAllowedBuiltinsAndDeniesDisabledBuiltins() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("local dynamic import builtins")
        val workDir = freshWorkDir("esm-dynamic-builtins")

        val allowed = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const path = await import("node:path");
                console.log("esm.dynamic.builtin=" + (path.default.basename("/tmp/value.txt") === "value.txt"));
            """.trimIndent(),
            sourceName = File(workDir, "builtin-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("local dynamic import builtin", allowed)
        assertTrue(allowed.stdout.contains("esm.dynamic.builtin=true"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val denied = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """await import("node:child_process");""",
            sourceName = File(workDir, "denied-builtin-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("local dynamic import disabled builtin", denied), denied.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED, denied.errorCode)
        assertTrue(denied.errorMessage.orEmpty().contains("child_process"))
    }

    @Test
    fun localDynamicImportSupportsSafePackageImportCondition() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("local dynamic import package")
        val workDir = freshWorkDir("esm-dynamic-package")
        val packageDir = File(workDir, "node_modules/safe-package").apply { mkdirs() }
        File(packageDir, "package.json").writeText(
            """
            {
              "name": "safe-package",
              "exports": {
                ".": {
                  "import": "./esm.mjs",
                  "require": "./cjs.cjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(packageDir, "esm.mjs").writeText("""export default { value: "esm-condition" };""")
        File(packageDir, "cjs.cjs").writeText("""module.exports = { value: "require-condition" };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const pkg = await import("safe-package");
                console.log("esm.dynamic.package=" + pkg.default.value);
            """.trimIndent(),
            sourceName = File(workDir, "package-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("local dynamic import package", result)
        assertTrue(result.stdout.contains("esm.dynamic.package=esm-condition"))
        assertEquals("true", result.nativeValues["embedded_script.dynamic_import_enabled"])
    }

    @Test
    fun localDynamicImportCannotEscapeWorkingDirectory() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("local dynamic import scoped guard")
        val parent = freshWorkDir("esm-dynamic-escape-parent")
        val workDir = File(parent, "project").apply { mkdirs() }
        File(parent, "outside.mjs").writeText("""export const value = "outside";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """await import("../outside.mjs");""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("local dynamic import escape", result), result.succeeded)
        assertEquals("ERR_AUTOJS6_FS_PATH_ESCAPE", result.errorCode)
        assertEquals("true", result.nativeValues["embedded_script.dynamic_import_enabled"])
        assertTrue(result.nativeValues["embedded_script.esm_last_denied_reason"].orEmpty().contains("escapes working directory"))
    }

    @Test
    fun localDynamicImportRejectsUrlAndAbsoluteSpecifiers() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("local dynamic import restricted specifiers")
        val workDir = freshWorkDir("esm-dynamic-restricted")

        val absolute = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """await import("/tmp/dep.mjs");""",
            sourceName = File(workDir, "absolute-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("local dynamic import absolute", absolute), absolute.succeeded)
        assertEquals("ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED", absolute.errorCode)

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val fileUrl = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """await import("file:///tmp/dep.mjs");""",
            sourceName = File(workDir, "url-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("local dynamic import file URL", fileUrl), fileUrl.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_DYNAMIC_IMPORT_UNSUPPORTED, fileUrl.errorCode)
        assertTrue(fileUrl.errorMessage.orEmpty().contains("URL/protocol imports"))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val networkUrl = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """await import("https://example.test/dep.mjs");""",
            sourceName = File(workDir, "network-main.mjs").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("local dynamic import network URL", networkUrl), networkUrl.succeeded)
        assertEquals("ERR_AUTOJS6_NETWORK_DISABLED", networkUrl.errorCode)
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
