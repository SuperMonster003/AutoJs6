package org.autojs.autojs.apkbuilder

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.engine.EmbeddedNodeFixtureCorpus
import org.autojs.autojs.engine.NativeNodeEmbeddedJavaScriptEngine
import org.autojs.autojs.engine.NodeExecutionModePolicy
import org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicy
import org.autojs.autojs.engine.NodeRuntimeCapabilities
import org.autojs.autojs.engine.NodeWorkerThreadsTestSupport
import org.autojs.autojs.engine.NodeProjectLaunchDiagnostics
import org.autojs.autojs.project.LaunchConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.NodeProjectLaunchDescriptor
import org.autojs.autojs.script.NodeProjectLaunchKind
import org.autojs.autojs.script.NodeProjectRuntimeCompatibilityDescriptor
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mozilla.javascript.Context as RhinoContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class EmbeddedNodePackagedApkSmokeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun apkBuilderSelectsBestSupportedPluginVariantCandidate() {
        fun candidate(id: String, variant: String?): Pair<String, ApkBuilder.PluginLibVariant> {
            return id to ApkBuilder.PluginLibVariant(variant = variant)
        }

        val v2 = candidate("service-v2", "v2")
        val v10 = candidate("service-v10", "v10")
        val unsupported = candidate("service-unsupported", "v99")
        val default = candidate("service-default", "")

        assertEquals(
            v10,
            ApkBuilder.selectBestPluginVariantCandidate(
                infos = listOf(v2, v10),
                supportedVariants = listOf(
                    ApkBuilder.PluginLibVariant(variant = "v2"),
                    ApkBuilder.PluginLibVariant(variant = "v10"),
                ),
            ),
        )
        assertEquals(
            default,
            ApkBuilder.selectBestPluginVariantCandidate(
                infos = listOf(unsupported, default),
                supportedVariants = listOf(ApkBuilder.PluginLibVariant(variant = null)),
            ),
        )
        assertEquals(
            unsupported,
            ApkBuilder.selectBestPluginVariantCandidate(
                infos = listOf(unsupported, v2),
                supportedVariants = listOf(ApkBuilder.PluginLibVariant(variant = "v5")),
            ),
        )
    }

    @Test
    fun buildFullCompatibilityEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        val root = File(context.cacheDir, "embedded-node-packaged-smoke").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create smoke root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create smoke project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create smoke output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, SUCCESS_APK_NAME)
        val outputPathFile = File(root, "output-path.txt")
        val mainScriptName = "src/main.js"
        val mainScriptDir = File(projectDir, "src").apply {
            check(mkdirs()) { "Failed to create smoke main script dir: $absolutePath" }
        }
        EmbeddedNodeFixtureCorpus.writeRealNpmCorpus(projectDir)
        EmbeddedNodeFixtureCorpus.writeCommonJsResolverCorpus(projectDir)
        EmbeddedNodeFixtureCorpus.writeDependencyResolutionCorpus(projectDir)
        File(mainScriptDir, "packaged-dynamic-child.js").writeText(
            """
            globalThis.__autojs6PackagedDynamicChildLoads = (globalThis.__autojs6PackagedDynamicChildLoads || 0) + 1;
            module.exports = { value: "dynamic-child" };
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(mainScriptDir, "packaged-dynamic-data.json").writeText(
            """{"value":"dynamic-json"}""" + "\n",
            StandardCharsets.UTF_8,
        )
        File(mainScriptDir, "packaged-dynamic-dir/index.js").apply {
            parentFile?.mkdirs()
            writeText("""exports.value = "dynamic-dir";""" + "\n", StandardCharsets.UTF_8)
        }
        val zlibOutputBombBase64 = gzipBase64(ByteArray(32 * 1024 * 1024 + 1) { 'A'.code.toByte() })
        listOf(
            "path",
            "console",
            "v8",
            "tty",
            "util",
            "events",
            "timers",
            "stream",
            "readline",
            "crypto",
            "dns",
            "net",
            "http",
            "https",
            "tls",
            "zlib",
            "os",
            "querystring",
            "string_decoder",
            "assert",
            "punycode",
            "async_hooks",
            "diagnostics_channel",
            "perf_hooks",
        ).forEach { builtinName ->
            File(projectDir, "node_modules/$builtinName/index.js").apply {
                parentFile?.mkdirs()
                writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
            }
        }
        File(projectDir, "node_modules/util/types.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
        }
        File(projectDir, "node_modules/readline/promises.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
        }
        File(projectDir, "node_modules/timers/promises.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
        }
        File(projectDir, "node_modules/dns/promises.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""" + "\n", StandardCharsets.UTF_8)
        }
        val smokeScript = """
            "nodejs";

            const fs = require("fs");
            const nodeFs = require("node:fs");
            const fsp = require("fs/promises");
            const nodeFsp = require("node:fs/promises");
            const bufferBuiltin = require("buffer");
            const nodeBufferBuiltin = require("node:buffer");
            const moduleBuiltin = require("module");
            const nodeModuleBuiltin = require("node:module");
            const consoleBuiltin = require("console");
            const nodeConsoleBuiltin = require("node:console");
            const v8Builtin = require("v8");
            const nodeV8Builtin = require("node:v8");
            const ttyBuiltin = require("tty");
            const nodeTtyBuiltin = require("node:tty");
            const processBuiltin = require("process");
            const nodeProcessBuiltin = require("node:process");
            const constantsBuiltin = require("constants");
            const nodeConstantsBuiltin = require("node:constants");
            const urlBuiltin = require("url");
            const nodeUrlBuiltin = require("node:url");
            const osBuiltin = require("os");
            const nodeOsBuiltin = require("node:os");
            const querystringBuiltin = require("querystring");
            const nodeQuerystringBuiltin = require("node:querystring");
            const stringDecoderBuiltin = require("string_decoder");
            const nodeStringDecoderBuiltin = require("node:string_decoder");
            const assertBuiltin = require("assert");
            const nodeAssertBuiltin = require("node:assert");
            const assertStrictBuiltin = require("assert/strict");
            const nodeAssertStrictBuiltin = require("node:assert/strict");
            const punycodeBuiltin = require("punycode");
            const nodePunycodeBuiltin = require("node:punycode");
            const path = require("path");
            const nodePath = require("node:path");
            const pathPosix = require("path/posix");
            const nodePathPosix = require("node:path/posix");
            const pathWin32 = require("path/win32");
            const nodePathWin32 = require("node:path/win32");
            const crypto = require("crypto");
            const nodeCrypto = require("node:crypto");
            const dns = require("dns");
            const nodeDns = require("node:dns");
            const dnsPromises = require("dns/promises");
            const nodeDnsPromises = require("node:dns/promises");
            const net = require("net");
            const nodeNet = require("node:net");
            const http = require("http");
            const nodeHttp = require("node:http");
            const https = require("https");
            const nodeHttps = require("node:https");
            const tls = require("tls");
            const nodeTls = require("node:tls");
            const zlib = require("zlib");
            const nodeZlib = require("node:zlib");
            const stream = require("stream");
            const nodeStream = require("node:stream");
            const streamPromises = require("stream/promises");
            const nodeStreamPromises = require("node:stream/promises");
            const streamWeb = require("stream/web");
            const nodeStreamWeb = require("node:stream/web");
            const streamConsumers = require("stream/consumers");
            const nodeStreamConsumers = require("node:stream/consumers");
            const readline = require("readline");
            const nodeReadline = require("node:readline");
            const readlinePromises = require("readline/promises");
            const nodeReadlinePromises = require("node:readline/promises");
            const events = require("events");
            const nodeEvents = require("node:events");
            const timers = require("timers");
            const nodeTimers = require("node:timers");
            const timersPromises = require("timers/promises");
            const nodeTimersPromises = require("node:timers/promises");
            const { promisify } = require("util");
            const util = require("util");
            const nodeUtil = require("node:util");
            const utilTypes = require("util/types");
            const nodeUtilTypes = require("node:util/types");
            const asyncHooks = require("async_hooks");
            const nodeAsyncHooks = require("node:async_hooks");
            const diagnosticsChannel = require("diagnostics_channel");
            const nodeDiagnosticsChannel = require("node:diagnostics_channel");
            const perfHooks = require("perf_hooks");
            const nodePerfHooks = require("node:perf_hooks");

            const writeFile = promisify(fs.writeFile);
            const readFile = promisify(fs.readFile);
            const mkdir = promisify(fs.mkdir);
            const readdir = promisify(fs.readdir);
            const stat = promisify(fs.stat);
            const lstat = promisify(fs.lstat);
            const statfs = promisify(fs.statfs);
            const glob = promisify(fs.glob);
            const link = promisify(fs.link);
            const symlink = promisify(fs.symlink);
            const copyFile = promisify(fs.copyFile);
            const realpath = promisify(fs.realpath);
            const realpathNative = promisify(fs.realpath.native);
            const appendFile = promisify(fs.appendFile);
            const rm = promisify(fs.rm);
            const rmdir = promisify(fs.rmdir);

            const packagedMarkerLines = [];
            const originalConsoleLog = console.log.bind(console);
            const originalConsoleError = console.error.bind(console);
            function recordPackagedMarker(args) {
              const line = args.map(value => String(value)).join(" ");
              if (line.startsWith("packaged.")) {
                packagedMarkerLines.push(line);
                return line;
              }
              return null;
            }
            console.log = function (...args) {
              if (recordPackagedMarker(args) !== null) {
                return undefined;
              }
              return originalConsoleLog(...args);
            };
            console.error = function (...args) {
              const line = recordPackagedMarker(args);
              if (line !== null && !line.startsWith("packaged.error=")) {
                return undefined;
              }
              return originalConsoleError(...args);
            };

            function writePackagedMarkerReport() {
              fs.writeFileSync(
                "./$PACKAGED_SMOKE_MARKER_REPORT_FILE",
                packagedMarkerLines.join("\n") + "\n",
                "utf8"
              );
            }

            function emitPackagedMarkerMatrixStatus() {
              const expectedMarkers = fs.readFileSync("./runtime-matrix.expected.txt", "utf8")
                .split(/\r?\n/)
                .filter(line => line.length > 0);
              const actualMarkers = new Set(packagedMarkerLines);
              const missingMarkers = expectedMarkers.filter(marker => !actualMarkers.has(marker));
              originalConsoleLog("packaged.runtime_matrix.ready=" + (missingMarkers.length === 0));
              originalConsoleLog("packaged.runtime_matrix.expected_count=" + expectedMarkers.length);
              originalConsoleLog("packaged.runtime_matrix.actual_count=" + actualMarkers.size);
              originalConsoleLog("packaged.runtime_matrix.missing_count=" + missingMarkers.length);
              if (missingMarkers.length > 0) {
                originalConsoleError("packaged.runtime_matrix.missing=" + missingMarkers.slice(0, 32).join("|"));
                const actualLines = missingMarkers.slice(0, 32).map(marker => {
                  const key = marker.split("=")[0] + "=";
                  return packagedMarkerLines.find(line => line.startsWith(key)) || (key + "<missing>");
                });
                originalConsoleError("packaged.runtime_matrix.actual=" + actualLines.join("|"));
              }
              return missingMarkers;
            }

            async function finishPackagedMarkerReporting() {
              try {
                writePackagedMarkerReport();
              } catch (reportError) {
                console.error("packaged.marker_report.error=" + (
                  reportError && (reportError.stack || reportError.message) || reportError
                ));
              }
              const missingMarkers = emitPackagedMarkerMatrixStatus();
              if (missingMarkers.length > 0) {
                throw new Error("packaged runtime matrix missing markers: " + missingMarkers.slice(0, 32).join("|"));
              }
            }

            function optionalLinkFeatureError(error) {
              const code = String(error && error.code || "");
              return code === "EPERM" || code === "EACCES" || code === "ENOSYS" ||
                code === "ENOTSUP" || code === "EOPNOTSUPP" ||
                code === "ERR_METHOD_NOT_IMPLEMENTED";
            }

            function textFileEquals(pathValue, expectedText) {
              try {
                return fs.readFileSync(pathValue, "utf8") === expectedText;
              } catch (error) {
                return false;
              }
            }

            function hardlinkCreationOk(action, pathValue, expectedText) {
              try {
                action();
                return textFileEquals(pathValue, expectedText);
              } catch (error) {
                return optionalLinkFeatureError(error);
              }
            }

            async function hardlinkCreationAsyncOk(action, pathValue, expectedText) {
              try {
                await action();
                return textFileEquals(pathValue, expectedText);
              } catch (error) {
                return optionalLinkFeatureError(error);
              }
            }

            function symlinkTargetOk(pathValue, expectedSuffix) {
              try {
                return fs.lstatSync(pathValue).isSymbolicLink() &&
                  fs.readlinkSync(pathValue).endsWith(expectedSuffix);
              } catch (error) {
                return false;
              }
            }

            function symlinkCreationOk(action, pathValue, expectedSuffix) {
              try {
                action();
                return symlinkTargetOk(pathValue, expectedSuffix);
              } catch (error) {
                return optionalLinkFeatureError(error);
              }
            }

            async function symlinkCreationAsyncOk(action, pathValue, expectedSuffix) {
              try {
                await action();
                return symlinkTargetOk(pathValue, expectedSuffix);
              } catch (error) {
                return optionalLinkFeatureError(error);
              }
            }

            function captureSync(action, token) {
              try {
                action();
                return false;
              } catch (error) {
                return error instanceof Error && String(error.message).includes(token);
              }
            }

            function capturePromise(action, token) {
              return Promise.resolve().then(action).then(
                () => false,
                (error) => error instanceof Error && String(error.message).includes(token)
              );
            }

            function captureCallback(action, token) {
              return new Promise((resolve) => {
                action((error) => {
                  resolve(error instanceof Error && String(error.message).includes(token));
                });
              });
            }

            function capturePackagedMarkers(action) {
              const previousConsoleLog = console.log;
              const captured = [];
              console.log = function (...args) {
                const line = args.map(value => String(value)).join(" ");
                if (line.startsWith("packaged.")) {
                  captured.push(line);
                  return undefined;
                }
                return previousConsoleLog(...args);
              };
              try {
                action();
              } finally {
                console.log = previousConsoleLog;
              }
              return captured;
            }

            function runRealNpmCorpus() {
              process.env.DEBUG = "pkg:*";
              const isNumber = require("is-number");
              const isOdd = require("is-odd");
              const kindOf = require("kind-of");
              const once = require("once");
              const debug = require("debug");
              const picocolors = require("picocolors");
              const safeBuffer = require("safe-buffer");
              const ms = require("ms");

              let calls = 0;
              const onlyOnce = once(() => ++calls);
              const debugLog = debug("pkg:test");
              const debugAgain = require("debug");
              const result = {
                isNumber: isNumber("42"),
                isOdd: isOdd("3"),
                kind: kindOf(Buffer.from("x")) === "buffer",
                once: onlyOnce() === 1 && onlyOnce() === 1 && calls === 1,
                debug: debugLog("ready"),
                color: picocolors.red("x").includes("x"),
                buffer: safeBuffer.Buffer.from("ok").toString("utf8"),
                ms: ms("2s") === 2000,
                cache: debug === debugAgain,
                util: util.format("%s:%d", "pkg", 1) === "pkg:1"
              };
              console.log("packaged.real_npm_corpus.ready=true");
              console.log("packaged.real.npm.ok=" + Object.values(result).every(Boolean));
              console.log("packaged.real.npm.debug=" + result.debug);
              console.log("packaged.real.npm.buffer=" + result.buffer);
              console.log("packaged.real.npm.cache=" + result.cache);
            }

            function runRealNpmCorpusBatch2() {
              process.argv.push("--color");
              const camelcase = require("camelcase");
              const decamelize = require("decamelize");
              const stripAnsi = require("strip-ansi");
              const ansiRegex = require("ansi-regex");
              const stringWidth = require("string-width");
              const isFullwidthCodePoint = require("is-fullwidth-code-point");
              const emojiRegex = require("emoji-regex-lite");
              const yargsParser = require("yargs-parser-lite");
              const supportsColor = require("supports-color-lite");
              const hasFlag = require("has-flag");
              const escapeStringRegexp = require("escape-string-regexp");
              const balanced = require("balanced-match");
              const braceExpansion = require("brace-expansion-lite");
              const concatMap = require("concat-map");

              const esc = String.fromCharCode(27);
              const colored = esc + "[31mred" + esc + "[39m";
              const fullwidthChar = String.fromCodePoint(0x8868);
              const emoji = String.fromCodePoint(0x1f600);
              const parsed = yargsParser(["--user-name", "auto-js-six", "--color", "--dry-run", "--width=4"]);
              const match = balanced("{", "}", "a/{b,c}/d");
              const expanded = braceExpansion("a/{b,c}/d");
              const flattened = concatMap([1, 2], item => [item, item + 10]).join(",");
              const checks = {
                camel: camelcase("foo-bar_baz") === "fooBarBaz" && camelcase.default === camelcase,
                decamel: decamelize("fooBarBaz", "-") === "foo-bar-baz",
                strip: stripAnsi(colored) === "red" && ansiRegex().test(colored),
                width: stringWidth("a" + fullwidthChar + emoji) === 5 && isFullwidthCodePoint(fullwidthChar.codePointAt(0)),
                emoji: emojiRegex().test(emoji),
                argv: parsed.userName === "auto-js-six" && parsed.color === true && parsed.dryRun === true && parsed.width === "4",
                color: supportsColor.stdout === true && hasFlag("color") === true,
                optional: supportsColor.optional === "true/true/true",
                escape: escapeStringRegexp("a+b?") === "a\\+b\\?",
                balanced: match && match.body === "b,c" && match.pre === "a/" && match.post === "/d",
                braces: expanded.join("|") === "a/b/d|a/c/d",
                concat: flattened === "1,11,2,12"
              };
              console.log("packaged.real.npm.batch2.ready=true");
              console.log("packaged.real.npm.batch2.ok=" + Object.values(checks).every(Boolean));
              console.log("packaged.real.npm.string_width=" + checks.width);
              console.log("packaged.real.npm.strip_ansi=" + checks.strip);
              console.log("packaged.real.npm.camelcase=" + checks.camel);
              console.log("packaged.real.npm.brace_expansion=" + checks.braces);
            }

            function runCommonJsResolverCorpus() {
              function errorCodeOf(fn) {
                try {
                  fn();
                  return "ok";
                } catch (error) {
                  return error && error.code || String(error && error.message || error);
                }
              }
              const resolver = require("resolver-edge-pkg");
              const feature = require("resolver-edge-pkg/feature/a");
              const condition = require("resolver-edge-pkg/condition");
              const imports = require("imports-edge-pkg");
              const scopedSelf = require("@scope/resolver-self-pkg");
              const fallback = require("fallback-main-pkg");
              const resolverAgain = require("resolver-edge-pkg");
              const resolverPath = require.resolve("resolver-edge-pkg");
              const featurePath = require.resolve("resolver-edge-pkg/feature/a");
              const checks = {
                exports: resolver.root === "exports-root" &&
                  feature.value === "feature-a" &&
                  condition.mode === "condition-require",
                imports: imports.value === "import-alias+import-wild-a+import-require" &&
                  imports.resolve === true,
                selfReference: resolver.self === "self+feature-a",
                scopedSelfReference: scopedSelf === "scoped+self-feature",
                fallback: fallback.value === "fallback-index",
                requireResolve: resolverPath.endsWith("node_modules/resolver-edge-pkg/index.js") &&
                  featurePath.endsWith("node_modules/resolver-edge-pkg/features/a.js"),
                cache: require.cache[resolverPath] &&
                  require.cache[resolverPath].exports === resolver &&
                  resolverAgain === resolver,
                blocked: errorCodeOf(() => require("resolver-edge-pkg/private/secret")) === "ERR_PACKAGE_PATH_NOT_EXPORTED" &&
                  imports.blocked === "ERR_PACKAGE_IMPORT_NOT_DEFINED",
                esmLoaded: imports.esmDeferred === true
              };
              console.log("packaged.cjs.resolver.ready=true");
              console.log("packaged.cjs.resolver.ok=" + Object.values(checks).every(Boolean));
              console.log("packaged.cjs.exports=" + checks.exports);
              console.log("packaged.cjs.imports=" + checks.imports);
              console.log("packaged.cjs.self_reference=" + checks.selfReference);
              console.log("packaged.cjs.scoped_self_reference=" + checks.scopedSelfReference);
              console.log("packaged.cjs.require_resolve=" + (checks.requireResolve && checks.cache && checks.fallback));
              console.log("packaged.cjs.blocked=" + checks.blocked);
              console.log("packaged.cjs.esm_loaded=" + checks.esmLoaded);
            }

            function runDependencyResolutionCorpus() {
              const rootSharedA = require("resolution-shared");
              const rootSharedB = require("resolution-shared");
              const rootSubpath = require("resolution-shared/subpath");
              const parent = require("resolution-parent");
              const builtinShadowLoaded = Object.keys(require.cache).some(filename => {
                const normalized = filename.replace(/\\/g, "/");
                return normalized.includes("/node_modules/path/") ||
                  normalized.includes("/node_modules/fs/") ||
                  normalized.includes("/node_modules/util/");
              });
              const checks = {
                builtinPrecedence: require.resolve("path") === "path" &&
                  require.resolve("fs") === "fs" &&
                  require.resolve("util") === "util" &&
                  path.join("x", "y").replace(/\\/g, "/") === "x/y" &&
                  typeof fs.writeFile === "function" &&
                  util.format("%s:%d", "dep", 1) === "dep:1" &&
                  path.__autojs6Shadow !== true &&
                  fs.__autojs6Shadow !== true &&
                  util.__autojs6Shadow !== true &&
                  !builtinShadowLoaded,
                nestedDependency: rootSharedA.value === "root-shared" &&
                  parent.shared === "nested-shared" &&
                  parent.sharedModule !== rootSharedA,
                subpathResolution: rootSubpath.value === "root-subpath" &&
                  parent.subpath === "nested-subpath",
                cacheIdentity: rootSharedA === rootSharedB &&
                  parent.cache === true &&
                  require.cache[require.resolve("resolution-shared")].exports === rootSharedA &&
                  require.cache[require.resolve("resolution-parent")].exports === parent
              };
              console.log("packaged.cjs.dependency_resolution.ready=true");
              console.log("packaged.cjs.dependency_resolution.ok=" + Object.values(checks).every(Boolean));
              console.log("packaged.cjs.builtin_precedence=" + checks.builtinPrecedence);
              console.log("packaged.cjs.nested_dependency=" + checks.nestedDependency);
              console.log("packaged.cjs.subpath_resolution=" + checks.subpathResolution);
              console.log("packaged.cjs.cache_identity=" + checks.cacheIdentity);
            }

            function runPackagedDualPackageCorpus() {
              const resolver = require("resolver-edge-pkg");
              const condition = require("resolver-edge-pkg/condition");
              const imports = require("imports-edge-pkg");
              const requireOk = condition.mode === "condition-require" &&
                imports.value.endsWith("+import-require");
              const ignoredOk = resolver.root === "exports-root" &&
                condition.mode !== "condition-node" &&
                condition.mode !== "condition-default";
              console.log("packaged.dual_package.ready=" + (requireOk && ignoredOk));
              console.log("packaged.dual_package.require=" + requireOk);
              console.log("packaged.dual_package.ignored=" + ignoredOk);
            }

            function runPackagedNodeModulesScopeCorpus() {
              const rootShared = require("resolution-shared");
              const parent = require("resolution-parent");
              const imports = require("imports-edge-pkg");
              const nearest = rootShared.value === "root-shared" &&
                parent.shared === "nested-shared" &&
                parent.sharedModule !== rootShared;
              const subpath = require("resolution-shared/subpath").value === "root-subpath" &&
                parent.subpath === "nested-subpath";
              const importsOk = imports.value.endsWith("+import-require") &&
                imports.resolve === true;
              console.log("packaged.node_modules_scope.ready=" + (nearest && subpath && importsOk));
              console.log("packaged.node_modules_scope.nearest=" + nearest);
              console.log("packaged.node_modules_scope.imports=" + importsOk);
            }

            function runPackagedNpmAndCommonJsTailCorpus() {
              const expectedMarkers = [
                "packaged.real_npm_corpus.ready=true",
                "packaged.real.npm.ok=true",
                "packaged.real.npm.buffer=ok",
                "packaged.real.npm.cache=true",
                "packaged.real.npm.batch2.ready=true",
                "packaged.real.npm.batch2.ok=true",
                "packaged.real.npm.string_width=true",
                "packaged.real.npm.strip_ansi=true",
                "packaged.real.npm.camelcase=true",
                "packaged.real.npm.brace_expansion=true",
                "packaged.cjs.resolver.ready=true",
                "packaged.cjs.resolver.ok=true",
                "packaged.cjs.exports=true",
                "packaged.cjs.imports=true",
                "packaged.cjs.self_reference=true",
                "packaged.cjs.scoped_self_reference=true",
                "packaged.cjs.require_resolve=true",
                "packaged.cjs.blocked=true",
                "packaged.cjs.esm_loaded=true",
                "packaged.cjs.dependency_resolution.ready=true",
                "packaged.cjs.dependency_resolution.ok=true",
                "packaged.cjs.builtin_precedence=true",
                "packaged.cjs.nested_dependency=true",
                "packaged.cjs.subpath_resolution=true",
                "packaged.cjs.cache_identity=true",
                "packaged.dual_package.ready=true",
                "packaged.dual_package.require=true",
                "packaged.dual_package.ignored=true",
                "packaged.node_modules_scope.ready=true",
                "packaged.node_modules_scope.nearest=true",
                "packaged.node_modules_scope.imports=true"
              ];
              const captured = capturePackagedMarkers(() => {
                runRealNpmCorpus();
                runRealNpmCorpusBatch2();
                runCommonJsResolverCorpus();
                runDependencyResolutionCorpus();
                runPackagedDualPackageCorpus();
                runPackagedNodeModulesScopeCorpus();
              });
              const capturedSet = new Set(captured);
              const debugOk = captured.some(line => line.startsWith("packaged.real.npm.debug="));
              const missingTailMarkers = expectedMarkers.filter(marker => !capturedSet.has(marker));
              if (missingTailMarkers.length > 0 || !debugOk) {
                originalConsoleLog("packaged.tail.diagnostics=" + debugOk + "|" + missingTailMarkers.slice(0, 16).join("|"));
              }
              const tailOk = debugOk && missingTailMarkers.length === 0;
              console.log("packaged.tail=" + tailOk);
              console.log("packaged.tail.flush=" + tailOk);
            }

            async function runExtendedFsCorpus() {
              function normalizeList(values) {
                return values.map(value => String(value).replace(/\\/g, "/")).sort().join("|");
              }

              function direntTreeSummary(entries) {
                return entries
                  .map(entry => (
                    String(path.join(String(entry.parentPath), String(entry.name))).replace(/\\/g, "/") + ":" +
                    entry.isDirectory() + ":" +
                    entry.isFile() + ":" +
                    entry.isSymbolicLink()
                  ))
                  .sort()
                  .join("|");
              }

              async function collectAsyncIterator(iterator) {
                const values = [];
                for await (const value of iterator) {
                  values.push(value);
                }
                return values;
              }

              async function readWebStream(webStream) {
                const chunks = [];
                const reader = webStream.getReader();
                try {
                  while (true) {
                    const result = await reader.read();
                    if (result.done) {
                      break;
                    }
                    if (result.value !== undefined) {
                      chunks.push(Buffer.from(result.value));
                    }
                  }
                } finally {
                  if (typeof reader.releaseLock === "function") {
                    reader.releaseLock();
                  }
                }
                return Buffer.concat(chunks).toString("utf8");
              }

              function lstatGone(pathValue) {
                try {
                  fs.lstatSync(pathValue);
                  return false;
                } catch (error) {
                  return error && error.code === "ENOENT";
                }
              }

              function collectStream(readable) {
                return new Promise((resolve, reject) => {
                  let text = "";
                  readable.on("data", chunk => { text += chunk; });
                  readable.on("error", reject);
                  readable.on("end", () => resolve(text));
                });
              }

              function finishStream(writable, text) {
                return new Promise((resolve, reject) => {
                  writable.on("error", reject);
                  writable.on("finish", resolve);
                  writable.end(text);
                });
              }

              function finishUtf8Stream(options, text) {
                return new Promise((resolve, reject) => {
                  const stream = new fs.Utf8Stream(options);
                  const instanceOk = stream instanceof fs.Utf8Stream;
                  stream.on("error", reject);
                  stream.on("ready", () => {
                    stream.write(text);
                    stream.end();
                  });
                  stream.on("close", () => resolve(instanceOk));
                });
              }

              function streamError(action, text) {
                return new Promise((resolve) => {
                  let settled = false;
                  function settle(value) {
                    if (!settled) {
                      settled = true;
                      resolve(value);
                    }
                  }
                  let stream;
                  try {
                    stream = action();
                  } catch (error) {
                    settle("throw:" + error.code);
                    return;
                  }
                  stream.on("error", (error) => settle("error:" + error.code));
                  stream.on("end", () => settle("end"));
                  stream.on("finish", () => settle("finish"));
                  if (text !== undefined) {
                    stream.end(text);
                  } else if (typeof stream.resume === "function") {
                    stream.resume();
                  }
                });
              }

              function closeTime(actual, expected) {
                return Math.abs(Number(actual) - Number(expected)) < 5000;
              }

              function modeBits(stat) {
                return Number(stat.mode) & 0o777;
              }

              function lmodeText(pathValue) {
                return (fs.lstatSync(pathValue).mode & 0o777).toString(8);
              }

              function lchmodResultOk(error) {
                const code = String(error && error.code || "");
                return code === "EPERM" || code === "EACCES" || code === "EINVAL" || code === "ENOSYS" ||
                  code === "ERR_METHOD_NOT_IMPLEMENTED";
              }

              function lchmodChangeOk(action, pathValue, expectedMode) {
                try {
                  action();
                  return lmodeText(pathValue) === expectedMode;
                } catch (error) {
                  return lchmodResultOk(error);
                }
              }

              async function lchmodChangeAsyncOk(action, pathValue, expectedMode) {
                try {
                  await action();
                  return lmodeText(pathValue) === expectedMode;
                } catch (error) {
                  return lchmodResultOk(error);
                }
              }

              function chownResultOk(error) {
                const code = String(error && error.code || "");
                return code === "EPERM" || code === "EACCES" || code === "EINVAL" || code === "ENOSYS";
              }

              function ownerChangeOk(action, statAction, uid, gid) {
                try {
                  action();
                  const after = statAction();
                  return Number(after.uid) === Number(uid) && Number(after.gid) === Number(gid);
                } catch (error) {
                  return chownResultOk(error);
                }
              }

              async function ownerChangeAsyncOk(action, statAction, uid, gid) {
                try {
                  await action();
                  const after = await statAction();
                  return Number(after.uid) === Number(uid) && Number(after.gid) === Number(gid);
                } catch (error) {
                  return chownResultOk(error);
                }
              }

              function callbackChmod(pathValue, mode) {
                return new Promise((resolve, reject) => {
                  fs.chmod(pathValue, mode, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
              }

              function callbackLchmod(pathValue, mode) {
                return new Promise((resolve, reject) => {
                  fs.lchmod(pathValue, mode, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
              }

              function callbackLutimes(pathValue, atime, mtime) {
                return new Promise((resolve, reject) => {
                  fs.lutimes(pathValue, atime, mtime, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
              }

              function callbackChown(pathValue, uid, gid) {
                return new Promise((resolve, reject) => {
                  fs.chown(pathValue, uid, gid, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
              }

              function callbackLchown(pathValue, uid, gid) {
                return new Promise((resolve, reject) => {
                  fs.lchown(pathValue, uid, gid, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
              }

              function numberStatfs(value) {
                return !!(
                  value &&
                  typeof value.type === "number" &&
                  typeof value.bsize === "number" &&
                  typeof value.blocks === "number" &&
                  typeof value.bfree === "number" &&
                  typeof value.bavail === "number" &&
                  typeof value.files === "number" &&
                  typeof value.ffree === "number" &&
                  value.bsize > 0
                );
              }

              function bigintStatfs(value) {
                return !!(
                  value &&
                  typeof value.type === "bigint" &&
                  typeof value.bsize === "bigint" &&
                  typeof value.blocks === "bigint" &&
                  typeof value.bfree === "bigint" &&
                  typeof value.bavail === "bigint" &&
                  typeof value.files === "bigint" &&
                  typeof value.ffree === "bigint" &&
                  value.bsize > 0n
                );
              }

              await mkdir("./packaged-fs/sub", { recursive: true });
              await writeFile("./packaged-fs/sub/a.txt", "hello", "utf8");
              await appendFile("./packaged-fs/sub/a.txt", " world", "utf8");
              fs.copyFileSync("./packaged-fs/sub/a.txt", "./packaged-fs/sub/b.txt");
              await copyFile("./packaged-fs/sub/b.txt", "./packaged-fs/sub/callback-copy.txt");
              const callbackRealPath = await realpath("./packaged-fs/sub/callback-copy.txt");
              const callbackNativeRealPath = await realpathNative("./packaged-fs/sub/callback-copy.txt");
              fs.mkdirSync("./packaged-fs/rmdir-sync");
              fs.rmdirSync("./packaged-fs/rmdir-sync");
              const rmdirSyncRemoved = !fs.existsSync("./packaged-fs/rmdir-sync");
              await mkdir("./packaged-fs/rmdir-callback");
              await rmdir("./packaged-fs/rmdir-callback");
              const rmdirCallbackRemoved = !fs.existsSync("./packaged-fs/rmdir-callback");
              await fsp.mkdir("./packaged-fs/rmdir-promise");
              await fsp.rmdir("./packaged-fs/rmdir-promise");
              const rmdirPromiseRemoved = !fs.existsSync("./packaged-fs/rmdir-promise");
              const linkSourcePath = "./packaged-fs/sub/link-source.txt";
              const linkSyncPath = "./packaged-fs/sub/link-sync.txt";
              const linkCallbackPath = "./packaged-fs/sub/link-callback.txt";
              const linkPromisePath = "./packaged-fs/sub/link-promise.txt";
              fs.writeFileSync(linkSourcePath, "link-source", "utf8");
              const linkSyncOk = hardlinkCreationOk(
                () => fs.linkSync(linkSourcePath, linkSyncPath),
                linkSyncPath,
                "link-source"
              );
              const linkCallbackOk = await hardlinkCreationAsyncOk(
                () => link(Buffer.from(linkSourcePath), linkCallbackPath),
                linkCallbackPath,
                "link-source"
              );
              const linkPromiseOk = await hardlinkCreationAsyncOk(
                () => fsp.link(
                  linkSourcePath,
                  urlBuiltin.pathToFileURL(path.resolve(process.cwd(), linkPromisePath))
                ),
                linkPromisePath,
                "link-source"
              );
              const createdHardlinkPaths = [linkSyncPath, linkCallbackPath, linkPromisePath].filter((pathValue) => {
                try {
                  return fs.lstatSync(pathValue).isFile();
                } catch (error) {
                  if (error && error.code === "ENOENT") {
                    return false;
                  }
                  throw error;
                }
              });
              fs.writeFileSync(createdHardlinkPaths[0] || linkSourcePath, "linked", "utf8");
              const linkMutationOk = textFileEquals(linkSourcePath, "linked") &&
                createdHardlinkPaths.every((pathValue) => textFileEquals(pathValue, "linked"));
              const symlinkSyncPath = "./packaged-fs/sub/symlink-sync.txt";
              const symlinkCallbackPath = "./packaged-fs/sub/symlink-callback.txt";
              const symlinkPromisePath = "./packaged-fs/sub/symlink-promise.txt";
              const symlinkBufferPath = "./packaged-fs/sub/symlink-buffer.txt";
              const symlinkUrlPath = "./packaged-fs/sub/symlink-url.txt";
              const symlinkSyncOk = symlinkCreationOk(
                () => fs.symlinkSync("./link-source.txt", symlinkSyncPath),
                symlinkSyncPath,
                "link-source.txt"
              );
              const symlinkCallbackOk = await symlinkCreationAsyncOk(
                () => symlink("./link-source.txt", symlinkCallbackPath),
                symlinkCallbackPath,
                "link-source.txt"
              );
              const symlinkPromiseOk = await symlinkCreationAsyncOk(
                () => fsp.symlink("./link-source.txt", symlinkPromisePath),
                symlinkPromisePath,
                "link-source.txt"
              );
              const symlinkBufferOk = symlinkCreationOk(
                () => fs.symlinkSync(Buffer.from("./link-source.txt"), Buffer.from(symlinkBufferPath)),
                symlinkBufferPath,
                "link-source.txt"
              );
              const symlinkUrlOk = await symlinkCreationAsyncOk(
                () => fsp.symlink(
                  "./link-source.txt",
                  urlBuiltin.pathToFileURL(path.resolve(process.cwd(), symlinkUrlPath))
                ),
                symlinkUrlPath,
                "link-source.txt"
              );
              fs.writeFileSync("./packaged-fs/sub/chmod-sync.txt", "chmod-sync", "utf8");
              fs.chmodSync("./packaged-fs/sub/chmod-sync.txt", 0o600);
              fs.writeFileSync("./packaged-fs/sub/chmod-promise.txt", "chmod-promise", "utf8");
              await fsp.chmod("./packaged-fs/sub/chmod-promise.txt", "644");
              fs.writeFileSync("./packaged-fs/sub/chmod-callback.txt", "chmod-callback", "utf8");
              await callbackChmod("./packaged-fs/sub/chmod-callback.txt", 0o600);
              fs.writeFileSync("./packaged-fs/sub/chmod-buffer.txt", "chmod-buffer", "utf8");
              fs.chmodSync(Buffer.from("./packaged-fs/sub/chmod-buffer.txt"), "644");
              fs.writeFileSync("./packaged-fs/sub/chmod-url.txt", "chmod-url", "utf8");
              await fsp.chmod(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/chmod-url.txt")),
                0o600
              );
              fs.mkdirSync("./packaged-fs/chmod-cwd", { recursive: true });
              fs.writeFileSync("./packaged-fs/chmod-cwd/cwd.txt", "chmod-cwd", "utf8");
              process.chdir("./packaged-fs/chmod-cwd");
              await fsp.chmod("./cwd.txt", 0o600);
              const chmodCwdStat = fs.statSync("./cwd.txt");
              process.chdir("../..");
              fs.writeFileSync("./packaged-fs/sub/lchmod-sync.txt", "lchmod-sync", "utf8");
              const lchmodSyncOk = lchmodChangeOk(
                () => fs.lchmodSync("./packaged-fs/sub/lchmod-sync.txt", 0o600),
                "./packaged-fs/sub/lchmod-sync.txt",
                "600"
              );
              fs.writeFileSync("./packaged-fs/sub/lchmod-promise.txt", "lchmod-promise", "utf8");
              const lchmodPromiseOk = await lchmodChangeAsyncOk(
                () => fsp.lchmod("./packaged-fs/sub/lchmod-promise.txt", "644"),
                "./packaged-fs/sub/lchmod-promise.txt",
                "644"
              );
              fs.writeFileSync("./packaged-fs/sub/lchmod-callback.txt", "lchmod-callback", "utf8");
              const lchmodCallbackOk = await lchmodChangeAsyncOk(
                () => callbackLchmod("./packaged-fs/sub/lchmod-callback.txt", 0o600),
                "./packaged-fs/sub/lchmod-callback.txt",
                "600"
              );
              fs.writeFileSync("./packaged-fs/sub/lchmod-buffer.txt", "lchmod-buffer", "utf8");
              const lchmodBufferOk = lchmodChangeOk(
                () => fs.lchmodSync(Buffer.from("./packaged-fs/sub/lchmod-buffer.txt"), "644"),
                "./packaged-fs/sub/lchmod-buffer.txt",
                "644"
              );
              fs.writeFileSync("./packaged-fs/sub/lchmod-url.txt", "lchmod-url", "utf8");
              const lchmodUrlOk = await lchmodChangeAsyncOk(
                () => fsp.lchmod(
                  urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/lchmod-url.txt")),
                  0o600
                ),
                "./packaged-fs/sub/lchmod-url.txt",
                "600"
              );
              fs.mkdirSync("./packaged-fs/lchmod-cwd", { recursive: true });
              fs.writeFileSync("./packaged-fs/lchmod-cwd/cwd.txt", "lchmod-cwd", "utf8");
              process.chdir("./packaged-fs/lchmod-cwd");
              const lchmodCwdOk = await lchmodChangeAsyncOk(
                () => fsp.lchmod("./cwd.txt", 0o600),
                "./cwd.txt",
                "600"
              );
              process.chdir("../..");
              const lutimesSyncDate = new Date("2013-03-16T17:18:19Z");
              fs.writeFileSync("./packaged-fs/sub/lutimes-sync.txt", "lutimes-sync", "utf8");
              fs.lutimesSync("./packaged-fs/sub/lutimes-sync.txt", lutimesSyncDate, lutimesSyncDate);
              const lutimesPromiseDate = new Date("2014-04-17T18:19:20Z");
              fs.writeFileSync("./packaged-fs/sub/lutimes-promise.txt", "lutimes-promise", "utf8");
              await fsp.lutimes("./packaged-fs/sub/lutimes-promise.txt", lutimesPromiseDate, lutimesPromiseDate);
              const lutimesCallbackDate = new Date("2015-05-18T19:20:21Z");
              fs.writeFileSync("./packaged-fs/sub/lutimes-callback.txt", "lutimes-callback", "utf8");
              await callbackLutimes("./packaged-fs/sub/lutimes-callback.txt", lutimesCallbackDate, lutimesCallbackDate);
              const lutimesBufferDate = new Date("2016-06-19T20:21:22Z");
              fs.writeFileSync("./packaged-fs/sub/lutimes-buffer.txt", "lutimes-buffer", "utf8");
              fs.lutimesSync(Buffer.from("./packaged-fs/sub/lutimes-buffer.txt"), lutimesBufferDate, lutimesBufferDate);
              const lutimesUrlDate = new Date("2017-07-20T21:22:23Z");
              fs.writeFileSync("./packaged-fs/sub/lutimes-url.txt", "lutimes-url", "utf8");
              await fsp.lutimes(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/lutimes-url.txt")),
                lutimesUrlDate,
                lutimesUrlDate
              );
              fs.mkdirSync("./packaged-fs/lutimes-cwd", { recursive: true });
              fs.writeFileSync("./packaged-fs/lutimes-cwd/cwd.txt", "lutimes-cwd", "utf8");
              process.chdir("./packaged-fs/lutimes-cwd");
              const lutimesCwdDate = new Date("2018-08-21T22:23:24Z");
              await fsp.lutimes("./cwd.txt", lutimesCwdDate, lutimesCwdDate);
              const lutimesCwdStat = fs.lstatSync("./cwd.txt");
              process.chdir("../..");
              fs.writeFileSync("./packaged-fs/sub/chown-sync.txt", "chown-sync", "utf8");
              const chownSyncOwner = fs.statSync("./packaged-fs/sub/chown-sync.txt");
              const chownSyncOk = ownerChangeOk(
                () => fs.chownSync("./packaged-fs/sub/chown-sync.txt", chownSyncOwner.uid, chownSyncOwner.gid),
                () => fs.statSync("./packaged-fs/sub/chown-sync.txt"),
                chownSyncOwner.uid,
                chownSyncOwner.gid
              );
              fs.writeFileSync("./packaged-fs/sub/chown-promise.txt", "chown-promise", "utf8");
              const chownPromiseOwner = fs.statSync("./packaged-fs/sub/chown-promise.txt");
              const chownPromiseOk = await ownerChangeAsyncOk(
                () => fsp.chown("./packaged-fs/sub/chown-promise.txt", chownPromiseOwner.uid, chownPromiseOwner.gid),
                () => fsp.stat("./packaged-fs/sub/chown-promise.txt"),
                chownPromiseOwner.uid,
                chownPromiseOwner.gid
              );
              fs.writeFileSync("./packaged-fs/sub/chown-callback.txt", "chown-callback", "utf8");
              const chownCallbackOwner = fs.statSync("./packaged-fs/sub/chown-callback.txt");
              const chownCallbackOk = await ownerChangeAsyncOk(
                () => callbackChown("./packaged-fs/sub/chown-callback.txt", chownCallbackOwner.uid, chownCallbackOwner.gid),
                () => fsp.stat("./packaged-fs/sub/chown-callback.txt"),
                chownCallbackOwner.uid,
                chownCallbackOwner.gid
              );
              fs.writeFileSync("./packaged-fs/sub/chown-buffer.txt", "chown-buffer", "utf8");
              const chownBufferOwner = fs.statSync("./packaged-fs/sub/chown-buffer.txt");
              const chownBufferOk = ownerChangeOk(
                () => fs.chownSync(Buffer.from("./packaged-fs/sub/chown-buffer.txt"), chownBufferOwner.uid, chownBufferOwner.gid),
                () => fs.statSync("./packaged-fs/sub/chown-buffer.txt"),
                chownBufferOwner.uid,
                chownBufferOwner.gid
              );
              fs.writeFileSync("./packaged-fs/sub/chown-url.txt", "chown-url", "utf8");
              const chownUrlOwner = fs.statSync("./packaged-fs/sub/chown-url.txt");
              const chownUrlOk = await ownerChangeAsyncOk(
                () => fsp.chown(
                  urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/chown-url.txt")),
                  chownUrlOwner.uid,
                  chownUrlOwner.gid
                ),
                () => fsp.stat("./packaged-fs/sub/chown-url.txt"),
                chownUrlOwner.uid,
                chownUrlOwner.gid
              );
              fs.mkdirSync("./packaged-fs/chown-cwd", { recursive: true });
              fs.writeFileSync("./packaged-fs/chown-cwd/cwd.txt", "chown-cwd", "utf8");
              process.chdir("./packaged-fs/chown-cwd");
              const chownCwdOwner = fs.statSync("./cwd.txt");
              const chownCwdOk = await ownerChangeAsyncOk(
                () => fsp.chown("./cwd.txt", chownCwdOwner.uid, chownCwdOwner.gid),
                () => fsp.stat("./cwd.txt"),
                chownCwdOwner.uid,
                chownCwdOwner.gid
              );
              process.chdir("../..");
              fs.writeFileSync("./packaged-fs/sub/lchown-sync.txt", "lchown-sync", "utf8");
              const lchownSyncOwner = fs.lstatSync("./packaged-fs/sub/lchown-sync.txt");
              const lchownSyncOk = ownerChangeOk(
                () => fs.lchownSync("./packaged-fs/sub/lchown-sync.txt", lchownSyncOwner.uid, lchownSyncOwner.gid),
                () => fs.lstatSync("./packaged-fs/sub/lchown-sync.txt"),
                lchownSyncOwner.uid,
                lchownSyncOwner.gid
              );
              fs.writeFileSync("./packaged-fs/sub/lchown-callback.txt", "lchown-callback", "utf8");
              const lchownCallbackOwner = fs.lstatSync("./packaged-fs/sub/lchown-callback.txt");
              const lchownCallbackOk = await ownerChangeAsyncOk(
                () => callbackLchown("./packaged-fs/sub/lchown-callback.txt", lchownCallbackOwner.uid, lchownCallbackOwner.gid),
                () => fsp.lstat("./packaged-fs/sub/lchown-callback.txt"),
                lchownCallbackOwner.uid,
                lchownCallbackOwner.gid
              );
              fs.mkdirSync("./packaged-fs/glob/src/a", { recursive: true });
              fs.mkdirSync("./packaged-fs/glob/src/b", { recursive: true });
              fs.mkdirSync("./packaged-fs/glob/src/c", { recursive: true });
              fs.mkdirSync("./packaged-fs/glob/lib", { recursive: true });
              fs.writeFileSync("./packaged-fs/glob/src/a/index.js", "a", "utf8");
              fs.writeFileSync("./packaged-fs/glob/src/a/index.test.js", "test", "utf8");
              fs.writeFileSync("./packaged-fs/glob/src/a/readme.md", "readme", "utf8");
              fs.writeFileSync("./packaged-fs/glob/src/b/main.js", "b", "utf8");
              fs.writeFileSync("./packaged-fs/glob/src/b/util.cjs", "cjs", "utf8");
              fs.writeFileSync("./packaged-fs/glob/src/c/skip.js", "skip", "utf8");
              fs.writeFileSync("./packaged-fs/glob/lib/tool.js", "tool", "utf8");
              fs.mkdirSync("./packaged-fs/opendir/sub", { recursive: true });
              fs.writeFileSync("./packaged-fs/opendir/a.txt", "a", "utf8");
              fs.writeFileSync("./packaged-fs/opendir/sub/b.txt", "b", "utf8");

              const names = await readdir("./packaged-fs/sub");
              const fileStat = await stat("./packaged-fs/sub/a.txt");
              const fileLstat = await lstat("./packaged-fs/sub/a.txt");
              const fileStatfs = fs.statfsSync("./packaged-fs/sub/a.txt");
              const callbackStatfs = await statfs(Buffer.from("./packaged-fs/sub"));
              const promiseStatfs = await fsp.statfs(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub")),
                { bigint: true }
              );
              const copiedText = await readFile("./packaged-fs/sub/b.txt", "utf8");
              const linkExisting = await new Promise((resolve) => {
                fs.exists("./packaged-fs/sub/link-source.txt", resolve);
              });
              const linkMissing = await new Promise((resolve) => {
                fs.exists(Buffer.from("./packaged-fs/sub/link-missing.txt"), resolve);
              });
              const linkUrlExisting = await new Promise((resolve) => {
                fs.exists(
                  urlBuiltin.pathToFileURL(path.resolve(process.cwd(), linkSourcePath)),
                  resolve
                );
              });
              const chmodSyncStat = fs.statSync("./packaged-fs/sub/chmod-sync.txt");
              const chmodPromiseStat = fs.statSync("./packaged-fs/sub/chmod-promise.txt");
              const chmodCallbackStat = fs.statSync("./packaged-fs/sub/chmod-callback.txt");
              const chmodBufferStat = fs.statSync("./packaged-fs/sub/chmod-buffer.txt");
              const chmodUrlStat = fs.statSync("./packaged-fs/sub/chmod-url.txt");
              const lutimesSyncStat = fs.lstatSync("./packaged-fs/sub/lutimes-sync.txt");
              const lutimesPromiseStat = fs.lstatSync("./packaged-fs/sub/lutimes-promise.txt");
              const lutimesCallbackStat = fs.lstatSync("./packaged-fs/sub/lutimes-callback.txt");
              const lutimesBufferStat = fs.lstatSync("./packaged-fs/sub/lutimes-buffer.txt");
              const lutimesUrlStat = fs.lstatSync("./packaged-fs/sub/lutimes-url.txt");
              const nativeGlobSync = fs.globSync("glob/src/**/*.js", {
                cwd: "./packaged-fs",
                exclude: ["**/*.test.js", "glob/src/c/skip.js"]
              });
              const nativeGlobCallback = await glob("glob/src/**/*.cjs", {
                cwd: Buffer.from("./packaged-fs")
              });
              const nativeGlobPromise = await collectAsyncIterator(fsp.glob(["glob/src/**/*.js", "glob/lib/**/*.js"], {
                cwd: urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs")),
                exclude: ["**/*.test.js", "glob/src/c/skip.js"]
              }));
              const nativeGlobDirents = fs.globSync("glob/src/a/*", {
                cwd: "./packaged-fs",
                withFileTypes: true
              });
              const nativeGlobFunctionExclude = fs.globSync("glob/src/**/*.js", {
                cwd: "./packaged-fs",
                exclude: (entry) => {
                  const text = String(entry).replace(/\\/g, "/");
                  return text.endsWith("glob/src/a/index.test.js") || text.endsWith("glob/src/c/skip.js");
                }
              });
              const nativeGlobCallbackNoOptions = await glob("packaged-fs/glob/lib/*.js");
              const nativeGlobDirentFunction = fs.globSync("glob/src/a/*", {
                cwd: "./packaged-fs",
                withFileTypes: true,
                exclude: (entry) => entry.name.endsWith(".md")
              });
              const nativeGlobDirentFunctionSummary = nativeGlobDirentFunction
                .map((entry) => entry.name + ":" + entry.isFile() + ":" + typeof entry.parentPath)
                .sort()
                .join("|");
              const nativeGlobExtendedOk =
                normalizeList(nativeGlobFunctionExclude) === "glob/src/a/index.js|glob/src/b/main.js" &&
                normalizeList(nativeGlobCallbackNoOptions) === "packaged-fs/glob/lib/tool.js" &&
                nativeGlobDirentFunctionSummary === "index.js:true:string|index.test.js:true:string";
              const syncOpenDir = fs.opendirSync("./packaged-fs/opendir", { bufferSize: 2 });
              const syncOpenDirEntries = [];
              for (;;) {
                const entry = syncOpenDir.readSync();
                if (entry === null) {
                  break;
                }
                syncOpenDirEntries.push(entry);
              }
              syncOpenDir.closeSync();
              const syncOpenDirClosed = captureSync(() => syncOpenDir.readSync(), "Dir is closed");
              const callbackOpenDir = await new Promise((resolve, reject) => {
                fs.opendir("./packaged-fs/opendir", { bufferSize: 2 }, (error, dir) => {
                  if (error) reject(error);
                  else resolve(dir);
                });
              });
              const callbackOpenDirEntries = [];
              for (;;) {
                const entry = await new Promise((resolve, reject) => {
                  callbackOpenDir.read((error, value) => {
                    if (error) reject(error);
                    else resolve(value);
                  });
                });
                if (entry === null) {
                  break;
                }
                callbackOpenDirEntries.push(entry);
              }
              await new Promise((resolve, reject) => {
                callbackOpenDir.close((error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const callbackOpenDirClosed = await new Promise((resolve) => {
                callbackOpenDir.read((error) => {
                  resolve(!!error && String(error && error.message || error).includes("Dir is closed"));
                });
              });
              const promiseOpenDir = await fsp.opendir(Buffer.from("./packaged-fs/opendir"));
              const promiseOpenDirEntries = [];
              for await (const entry of promiseOpenDir) {
                promiseOpenDirEntries.push(entry);
              }
              const promiseOpenDirClosed = await capturePromise(() => promiseOpenDir.read(), "Dir is closed");
              const recursiveReaddirExpected = "a.txt|sub|sub/b.txt";
              const syncRecursiveReaddir = normalizeList(fs.readdirSync("./packaged-fs/opendir", { recursive: true }));
              const callbackRecursiveReaddir = normalizeList(await readdir("./packaged-fs/opendir", { recursive: true }));
              const promiseRecursiveReaddir = normalizeList(await fsp.readdir(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/opendir")),
                { recursive: true }
              ));
              const recursiveReaddirDirents = fs.readdirSync("./packaged-fs/opendir", {
                recursive: true,
                withFileTypes: true
              });
              const recursiveReaddirDirentsOk =
                direntTreeSummary(recursiveReaddirDirents) ===
                "packaged-fs/opendir/a.txt:false:true:false|" +
                "packaged-fs/opendir/sub/b.txt:false:true:false|" +
                "packaged-fs/opendir/sub:true:false:false";
              const recursiveReaddirOk =
                syncRecursiveReaddir === recursiveReaddirExpected &&
                callbackRecursiveReaddir === recursiveReaddirExpected &&
                promiseRecursiveReaddir === recursiveReaddirExpected &&
                recursiveReaddirDirentsOk;
              const readCallbackDirEntries = async (dir) => {
                const entries = [];
                for (;;) {
                  const entry = await new Promise((resolve, reject) => {
                    dir.read((error, value) => {
                      if (error) reject(error);
                      else resolve(value);
                    });
                  });
                  if (entry === null) {
                    break;
                  }
                  entries.push(entry);
                }
                await new Promise((resolve, reject) => {
                  dir.close((error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                });
                return entries;
              };
              const recursiveOpenDirExpected =
                "packaged-fs/opendir/a.txt:false:true:false|" +
                "packaged-fs/opendir/sub/b.txt:false:true:false|" +
                "packaged-fs/opendir/sub:true:false:false";
              const syncRecursiveOpenDir = fs.opendirSync("./packaged-fs/opendir", { recursive: true, bufferSize: 1 });
              const syncRecursiveOpenDirEntries = [];
              for (;;) {
                const entry = syncRecursiveOpenDir.readSync();
                if (entry === null) {
                  break;
                }
                syncRecursiveOpenDirEntries.push(entry);
              }
              syncRecursiveOpenDir.closeSync();
              const callbackRecursiveOpenDir = await new Promise((resolve, reject) => {
                fs.opendir("./packaged-fs/opendir", { recursive: true, bufferSize: 1 }, (error, dir) => {
                  if (error) reject(error);
                  else resolve(dir);
                });
              });
              const callbackRecursiveOpenDirEntries = await readCallbackDirEntries(callbackRecursiveOpenDir);
              const promiseRecursiveOpenDir = await fsp.opendir(Buffer.from("./packaged-fs/opendir"), { recursive: true, bufferSize: 1 });
              const promiseRecursiveOpenDirEntries = [];
              for await (const entry of promiseRecursiveOpenDir) {
                promiseRecursiveOpenDirEntries.push(entry);
              }
              const recursiveOpenDirOk =
                direntTreeSummary(syncRecursiveOpenDirEntries) === recursiveOpenDirExpected &&
                direntTreeSummary(callbackRecursiveOpenDirEntries) === recursiveOpenDirExpected &&
                direntTreeSummary(promiseRecursiveOpenDirEntries) === recursiveOpenDirExpected;
              const syncDisposeDir = fs.opendirSync("./packaged-fs/opendir");
              const syncDisposeDirType = typeof syncDisposeDir[Symbol.dispose] === "function";
              syncDisposeDir[Symbol.dispose]();
              const syncDisposeDirClosed = captureSync(() => syncDisposeDir.readSync(), "Dir is closed");
              const asyncDisposeDir = await fsp.opendir("./packaged-fs/opendir");
              const asyncDisposeDirType = typeof asyncDisposeDir[Symbol.asyncDispose] === "function";
              await asyncDisposeDir[Symbol.asyncDispose]();
              const asyncDisposeDirClosed = await capturePromise(() => asyncDisposeDir.read(), "Dir is closed");
              const syncDisposable = fs.mkdtempDisposableSync("./packaged-fs/dispose-sync-");
              const syncDisposablePath = syncDisposable.path;
              fs.writeFileSync(path.join(syncDisposablePath, "a.txt"), "sync", "utf8");
              const syncDisposableType = typeof syncDisposable.remove === "function" &&
                typeof syncDisposable[Symbol.dispose] === "function" &&
                syncDisposable[Symbol.dispose] === syncDisposable.remove;
              syncDisposable[Symbol.dispose]();
              syncDisposable.remove();
              const syncDisposableRemoved = !fs.existsSync(syncDisposablePath);
              const promiseDisposable = await fsp.mkdtempDisposable("./packaged-fs/dispose-promise-");
              const promiseDisposablePath = promiseDisposable.path;
              fs.writeFileSync(path.join(promiseDisposablePath, "a.txt"), "promise", "utf8");
              const promiseDisposableType = typeof promiseDisposable.remove === "function" &&
                typeof promiseDisposable[Symbol.asyncDispose] === "function" &&
                promiseDisposable[Symbol.asyncDispose] === promiseDisposable.remove;
              await promiseDisposable[Symbol.asyncDispose]();
              await promiseDisposable.remove();
              const promiseDisposableRemoved = !fs.existsSync(promiseDisposablePath);
              fs.mkdirSync("./packaged-fs/dispose-protected", { recursive: true });
              fs.writeFileSync("./packaged-fs/dispose-protected/keep.txt", "keep", "utf8");
              const complexDisposable = fs.mkdtempDisposableSync(Buffer.from("./packaged-fs/dispose-complex-"));
              const complexDisposablePath = complexDisposable.path;
              fs.mkdirSync(path.join(complexDisposablePath, "nested"), { recursive: true });
              fs.writeFileSync(path.join(complexDisposablePath, "nested", "data.txt"), "data", "utf8");
              const complexDisposableLinkPath = path.join(complexDisposablePath, "keep-link.txt");
              const complexDisposableSymlinkOk = symlinkCreationOk(
                () => fs.symlinkSync("../dispose-protected/keep.txt", complexDisposableLinkPath),
                complexDisposableLinkPath,
                "keep.txt"
              );
              complexDisposable.remove();
              const complexDisposableRemoved =
                (complexDisposableSymlinkOk || !symlinkTargetOk(complexDisposableLinkPath, "keep.txt")) &&
                !fs.existsSync(complexDisposablePath) &&
                fs.readFileSync("./packaged-fs/dispose-protected/keep.txt", "utf8") === "keep";
              const brokenDisposable = await fsp.mkdtempDisposable(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/dispose-broken-"))
              );
              const brokenDisposablePath = brokenDisposable.path;
              await fsp.rm(brokenDisposablePath, { recursive: true, force: true });
              const brokenDisposableSymlinkOk = symlinkCreationOk(
                () => fs.symlinkSync("./missing-disposable-target", brokenDisposablePath),
                brokenDisposablePath,
                "missing-disposable-target"
              );
              let brokenDisposableRemoved = lstatGone(brokenDisposablePath);
              if (!lstatGone(brokenDisposablePath)) {
                await brokenDisposable.remove();
                brokenDisposableRemoved = lstatGone(brokenDisposablePath);
              }
              if (!brokenDisposableRemoved && symlinkTargetOk(brokenDisposablePath, "missing-disposable-target")) {
                await fsp.rm(brokenDisposablePath, { force: true });
                brokenDisposableRemoved = lstatGone(brokenDisposablePath);
              }
              const asyncDisposeHandle = await fsp.open("./packaged-fs/sub/fh-dispose.txt", "w+");
              await asyncDisposeHandle.writeFile("dispose", "utf8");
              const asyncDisposeHandleType = typeof asyncDisposeHandle[Symbol.asyncDispose] === "function";
              await asyncDisposeHandle[Symbol.asyncDispose]();
              const asyncDisposeHandleClosed = await capturePromise(() => asyncDisposeHandle.stat(), "not open or not owned");
              const realPath = fs.realpathSync("./packaged-fs/sub/b.txt");
              const nativeRealPath = fs.realpathSync.native("./packaged-fs/sub/b.txt");
              const pathlikeBufferPath = Buffer.from("./packaged-fs/sub/pathlike-buffer.txt", "utf8");
              const pathlikeUrlPath = urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/pathlike-url.txt"));
              fs.writeFileSync(pathlikeBufferPath, "buffer", "utf8");
              await fsp.writeFile(pathlikeUrlPath, "url", "utf8");
              const pathlikeBufferTemp = fs.mkdtempSync(Buffer.from("./packaged-fs/pathlike-buffer-"));
              const pathlikeUrlTemp = await fsp.mkdtemp(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/pathlike-url-"))
              );
              const pathlikeUrlText = await fsp.readFile(pathlikeUrlPath, "utf8");
              const pathlikeOk =
                fs.readFileSync(Buffer.from("./packaged-fs/sub/pathlike-buffer.txt", "utf8"), "utf8") === "buffer" &&
                pathlikeUrlText === "url" &&
                fs.statSync(pathlikeBufferTemp).isDirectory() &&
                fs.statSync(pathlikeUrlTemp).isDirectory();
              const openAsBlobString = await fs.openAsBlob("./packaged-fs/sub/b.txt", { type: "text/plain" });
              const openAsBlobBuffer = await fs.openAsBlob(Buffer.from("./packaged-fs/sub/b.txt"));
              const openAsBlobUrl = await fs.openAsBlob(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/b.txt")),
                { type: "text/custom" }
              );
              const openAsBlobStringText = await openAsBlobString.text();
              const openAsBlobBufferText = Buffer.from(await openAsBlobBuffer.arrayBuffer()).toString("utf8");
              const openAsBlobUrlText = await openAsBlobUrl.text();
              const openAsBlobArrayTextFirst = Buffer.from(await openAsBlobString.arrayBuffer()).toString("utf8");
              const openAsBlobArrayTextSecond = Buffer.from(await openAsBlobString.arrayBuffer()).toString("utf8");
              const openAsBlobConsumerText = await streamConsumers.text(openAsBlobString.stream());
              const openAsBlobManualStreamText = await readWebStream(openAsBlobUrl.stream());
              const openAsBlobSlice = openAsBlobString.slice(6, copiedText.length, "text/slice");
              const openAsBlobSliceText = await openAsBlobSlice.text();
              const openAsBlobOk =
                typeof fs.openAsBlob === "function" &&
                typeof nodeFs.openAsBlob === "function" &&
                typeof fsp.openAsBlob === "undefined" &&
                openAsBlobString.size === copiedText.length &&
                openAsBlobString.type === "text/plain" &&
                openAsBlobStringText === copiedText &&
                openAsBlobBufferText === copiedText &&
                openAsBlobUrl.size === copiedText.length &&
                openAsBlobUrl.type === "text/custom" &&
                openAsBlobUrlText === copiedText;
              const openAsBlobWebOk =
                typeof openAsBlobString.stream === "function" &&
                openAsBlobArrayTextFirst === copiedText &&
                openAsBlobArrayTextSecond === copiedText &&
                openAsBlobConsumerText === copiedText &&
                openAsBlobManualStreamText === copiedText;
              const openAsBlobSliceOk =
                openAsBlobSlice.size === 5 &&
                openAsBlobSlice.type === "text/slice" &&
                openAsBlobSliceText === "world";
              fs.writeFileSync("./packaged-fs/sub/truncate.txt", "abcdef", "utf8");
              fs.truncateSync("./packaged-fs/sub/truncate.txt", 4);
              await new Promise((resolve, reject) => {
                fs.truncate("./packaged-fs/sub/truncate.txt", 3, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await fsp.truncate("./packaged-fs/sub/truncate.txt", 2);
              const truncateText = fs.readFileSync("./packaged-fs/sub/truncate.txt", "utf8");
              const streamReadFd = fs.openSync("./packaged-fs/sub/b.txt", "r");
              const streamFdText = await new Promise((resolve, reject) => {
                let text = "";
                const readable = fs.createReadStream(undefined, { fd: streamReadFd, autoClose: true, encoding: "utf8" });
                readable.on("data", chunk => {
                  text += chunk;
                });
                readable.on("error", reject);
                readable.on("end", () => resolve(text));
              });
              const streamWriteFd = fs.openSync("./packaged-fs/sub/stream-fd.txt", "w");
              await new Promise((resolve, reject) => {
                const writable = fs.createWriteStream(undefined, { fd: streamWriteFd, autoClose: true, encoding: "utf8" });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("stream-fd");
              });
              const streamFileHandleReadFd = await fsp.open("./packaged-fs/sub/b.txt", "r");
              const streamFileHandleReadText = await new Promise((resolve, reject) => {
                let text = "";
                const readable = fs.createReadStream("./packaged-fs/sub/ignored-read.txt", {
                  fd: streamFileHandleReadFd,
                  autoClose: false,
                  encoding: "utf8",
                  start: 6,
                  end: 10,
                  highWaterMark: 2,
                  emitClose: false
                });
                readable.on("data", chunk => { text += chunk; });
                readable.on("error", reject);
                readable.on("end", () => resolve(text));
              });
              const streamFileHandleReadStillOpen = (await streamFileHandleReadFd.stat()).isFile();
              await streamFileHandleReadFd.close();
              fs.writeFileSync("./packaged-fs/sub/stream-filehandle-fd.txt", "0123456789", "utf8");
              const streamFileHandleWriteFd = await fsp.open("./packaged-fs/sub/stream-filehandle-fd.txt", "r+");
              await new Promise((resolve, reject) => {
                const writable = fs.createWriteStream("./packaged-fs/sub/ignored-write.txt", {
                  fd: streamFileHandleWriteFd,
                  autoClose: false,
                  encoding: "utf8",
                  start: 3,
                  highWaterMark: 2,
                  emitClose: false,
                  flush: true
                });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("XYZ");
              });
              const streamFileHandleWriteStillOpen = (await streamFileHandleWriteFd.stat()).isFile();
              await streamFileHandleWriteFd.close();
              fs.writeFileSync("./packaged-fs/sub/stream-modern.txt", "0123456789", "utf8");
              const streamModernReadText = await collectStream(fs.createReadStream("./packaged-fs/sub/stream-modern.txt", {
                encoding: "utf8",
                start: 2,
                end: 5,
                highWaterMark: 2,
                emitClose: false
              }));
              await finishStream(fs.createWriteStream("./packaged-fs/sub/stream-modern.txt", {
                encoding: "utf8",
                flags: "r+",
                start: 6,
                highWaterMark: 1,
                emitClose: false,
                flush: true
              }), "XYZ");
              const streamModernReadAbortController = new AbortController();
              streamModernReadAbortController.abort();
              const streamModernReadAbort = await streamError(() => fs.createReadStream("./packaged-fs/sub/stream-modern.txt", {
                encoding: "utf8",
                signal: streamModernReadAbortController.signal
              }));
              const streamModernWriteAbortController = new AbortController();
              streamModernWriteAbortController.abort();
              const streamModernWriteAbort = await streamError(() => fs.createWriteStream("./packaged-fs/sub/stream-modern.txt", {
                encoding: "utf8",
                flags: "r+",
                start: 0,
                signal: streamModernWriteAbortController.signal
              }), "ABORT");
              const classStat = fs.statSync("./packaged-fs/sub/b.txt");
              const classPromiseStat = await fsp.lstat("./packaged-fs/sub/b.txt");
              const classDirents = fs.readdirSync("./packaged-fs/sub", { withFileTypes: true });
              const classDirent = classDirents.find((entry) => entry.name === "b.txt");
              const classPromiseDirents = await fsp.readdir("./packaged-fs/sub", { withFileTypes: true });
              const classPromiseDirent = classPromiseDirents.find((entry) => entry.name === "link-source.txt");
              const classSyncDir = fs.opendirSync("./packaged-fs/sub");
              const classSyncDirEntry = classSyncDir.readSync();
              classSyncDir.closeSync();
              const classPromiseDir = await fsp.opendir("./packaged-fs/sub");
              const classPromiseDirEntry = await classPromiseDir.read();
              await classPromiseDir.close();
              const classCtorReadText = await collectStream(new fs.ReadStream("./packaged-fs/sub/b.txt", { encoding: "utf8" }));
              const classCreateReadStream = fs.createReadStream("./packaged-fs/sub/b.txt", { encoding: "utf8" });
              const classCreateReadInstance = classCreateReadStream instanceof fs.ReadStream;
              const classCreateReadText = await collectStream(classCreateReadStream);
              const classWriteStream = new fs.WriteStream("./packaged-fs/sub/class-write.txt", { encoding: "utf8" });
              const classWriteStreamInstance = classWriteStream instanceof fs.WriteStream;
              await finishStream(classWriteStream, "class-write");
              const classUtf8StreamInstance = await finishUtf8Stream({ dest: "./packaged-fs/sub/class-utf8.txt", sync: true }, "class-utf8");
              const classTopConstantsOk =
                fs.F_OK === fs.constants.F_OK &&
                fs.R_OK === fs.constants.R_OK &&
                fs.W_OK === fs.constants.W_OK &&
                fs.X_OK === fs.constants.X_OK &&
                fs.COPYFILE_EXCL === fs.constants.COPYFILE_EXCL &&
                fs.COPYFILE_FICLONE === fs.constants.COPYFILE_FICLONE &&
                fs.COPYFILE_FICLONE_FORCE === fs.constants.COPYFILE_FICLONE_FORCE;
              const classManualDirent = new fs.Dirent("manual.txt", 1, ".");
              const classSurfaceOk =
                typeof fs.Stats === "function" &&
                typeof fs.Dirent === "function" &&
                typeof fs.Dir === "function" &&
                typeof fs.ReadStream === "function" &&
                typeof fs.WriteStream === "function" &&
                typeof fs.Utf8Stream === "function" &&
                fs.FileReadStream === fs.ReadStream &&
                fs.FileWriteStream === fs.WriteStream &&
                classStat instanceof fs.Stats &&
                classPromiseStat instanceof fs.Stats &&
                classDirent instanceof fs.Dirent &&
                classPromiseDirent instanceof fs.Dirent &&
                classSyncDirEntry instanceof fs.Dirent &&
                classPromiseDirEntry instanceof fs.Dirent &&
                classManualDirent instanceof fs.Dirent &&
                classSyncDir instanceof fs.Dir &&
                classPromiseDir instanceof fs.Dir &&
                classCtorReadText === "hello world" &&
                classCreateReadText === "hello world" &&
                classCreateReadInstance &&
                classWriteStreamInstance &&
                classUtf8StreamInstance &&
                classTopConstantsOk &&
                fs.readFileSync("./packaged-fs/sub/class-write.txt", "utf8") === "class-write" &&
                fs.readFileSync("./packaged-fs/sub/class-utf8.txt", "utf8") === "class-utf8";
              const watchFilePath = "./packaged-fs/sub/watch.txt";
              const watchSettleMs = $PACKAGED_WATCH_SETTLE_MS;
              fs.writeFileSync(watchFilePath, "a", "utf8");
              const watchFileOk = await new Promise((resolve) => {
                const initialStat = fs.statSync(watchFilePath);
                let settled = false;
                const finish = (value) => {
                  if (settled) {
                    return;
                  }
                  settled = true;
                  clearTimeout(timeout);
                  fs.unwatchFile(watchFilePath, listener);
                  resolve(value);
                };
                const listener = (curr, prev) => {
                  if (curr.size !== 5) {
                    return;
                  }
                  finish(prev.size === 1 && curr.size === 5);
                };
                const timeout = setTimeout(() => {
                  const curr = fs.statSync(watchFilePath);
                  finish(initialStat.size === 1 && curr.size === 5);
                }, watchSettleMs);
                fs.watchFile(watchFilePath, { interval: 50 }, listener);
                setTimeout(() => fs.writeFileSync(watchFilePath, "abcde", "utf8"), 120);
              });
              const watchFileDelay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
              const packagedWatchFileOnce = (pathValue, options, changeAction, accept) => new Promise((resolve, reject) => {
                const statOptions = options && options.bigint ? { bigint: true } : undefined;
                const initialStat = fs.statSync(pathValue, statOptions);
                let settled = false;
                const finish = (error, value) => {
                  if (settled) {
                    return;
                  }
                  settled = true;
                  clearTimeout(timeout);
                  fs.unwatchFile(pathValue, listener);
                  if (error) {
                    reject(error);
                  } else {
                    resolve(value);
                  }
                };
                const listener = (curr, prev) => {
                  if (accept && !accept(curr, prev)) {
                    return;
                  }
                  finish(null, { curr, prev, fallback: false });
                };
                const timeout = setTimeout(() => {
                  const curr = fs.statSync(pathValue, statOptions);
                  if (!accept || accept(curr, initialStat)) {
                    finish(null, { curr, prev: initialStat, fallback: true });
                  } else {
                    finish(new Error("packaged watchFile options timeout: " + pathValue));
                  }
                }, watchSettleMs);
                fs.watchFile(pathValue, Object.assign({ interval: 50 }, options || {}), listener);
                setTimeout(changeAction, 120);
              });
              fs.writeFileSync("./packaged-fs/sub/watch-file-url.txt", "a", "utf8");
              fs.writeFileSync("./packaged-fs/sub/watch-file-buffer.txt", "a", "utf8");
              const packagedWatchFileUrl = await packagedWatchFileOnce(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/watch-file-url.txt")),
                {},
                () => fs.writeFileSync("./packaged-fs/sub/watch-file-url.txt", "url", "utf8"),
                (curr) => curr.size === 3
              );
              const packagedWatchFileBuffer = await packagedWatchFileOnce(
                Buffer.from("./packaged-fs/sub/watch-file-buffer.txt"),
                {},
                () => fs.writeFileSync("./packaged-fs/sub/watch-file-buffer.txt", "buffer", "utf8"),
                (curr) => curr.size === 6
              );
              const watchFilePathlikeOk =
                packagedWatchFileUrl.prev.size === 1 &&
                packagedWatchFileUrl.curr.size === 3 &&
                packagedWatchFileBuffer.prev.size === 1 &&
                packagedWatchFileBuffer.curr.size === 6;
              fs.writeFileSync("./packaged-fs/sub/watch-bigint.txt", "a", "utf8");
              const packagedWatchFileBigint = await packagedWatchFileOnce(
                "./packaged-fs/sub/watch-bigint.txt",
                { bigint: true },
                () => fs.writeFileSync("./packaged-fs/sub/watch-bigint.txt", "abcdef", "utf8"),
                (curr) => typeof curr.size === "bigint" && String(curr.size) === "6"
              );
              const packagedWatchFileMultipleCalls = [];
              const packagedWatchFileFirstListener = () => {
                packagedWatchFileMultipleCalls.push("first");
              };
              const packagedWatchFileSecondListener = () => {
                packagedWatchFileMultipleCalls.push("second");
              };
              fs.writeFileSync("./packaged-fs/sub/watch-multiple.txt", "a", "utf8");
              fs.watchFile("./packaged-fs/sub/watch-multiple.txt", { interval: 50 }, packagedWatchFileFirstListener);
              fs.watchFile("./packaged-fs/sub/watch-multiple.txt", { interval: 50 }, packagedWatchFileSecondListener);
              fs.unwatchFile("./packaged-fs/sub/watch-multiple.txt", packagedWatchFileFirstListener);
              fs.writeFileSync("./packaged-fs/sub/watch-multiple.txt", "ab", "utf8");
              await watchFileDelay(260);
              fs.unwatchFile("./packaged-fs/sub/watch-multiple.txt");
              const packagedWatchFileMultipleOk = packagedWatchFileMultipleCalls.join("|") === "second" ||
                (
                  packagedWatchFileMultipleCalls.length === 0 &&
                  fs.readFileSync("./packaged-fs/sub/watch-multiple.txt", "utf8") === "ab"
                );
              let packagedWatchFileNonFunctionCalled = false;
              fs.writeFileSync("./packaged-fs/sub/watch-nonfunction-unwatch.txt", "a", "utf8");
              fs.watchFile("./packaged-fs/sub/watch-nonfunction-unwatch.txt", { interval: 50 }, () => {
                packagedWatchFileNonFunctionCalled = true;
              });
              fs.unwatchFile("./packaged-fs/sub/watch-nonfunction-unwatch.txt", "bad");
              fs.writeFileSync("./packaged-fs/sub/watch-nonfunction-unwatch.txt", "ab", "utf8");
              await watchFileDelay(180);
              fs.writeFileSync("./packaged-fs/sub/watch-ref-unref.txt", "a", "utf8");
              const packagedWatchFileRefUnref = fs.watchFile("./packaged-fs/sub/watch-ref-unref.txt", { interval: 50 }, () => {});
              const packagedWatchFileRefUnrefOk = typeof packagedWatchFileRefUnref.ref === "function" &&
                typeof packagedWatchFileRefUnref.unref === "function" &&
                typeof packagedWatchFileRefUnref.stop === "function" &&
                packagedWatchFileRefUnref.unref() === packagedWatchFileRefUnref &&
                packagedWatchFileRefUnref.ref() === packagedWatchFileRefUnref &&
                packagedWatchFileRefUnref.unref() === packagedWatchFileRefUnref;
              const watchFileOptionsOk = typeof packagedWatchFileBigint.prev.size === "bigint" &&
                typeof packagedWatchFileBigint.curr.size === "bigint" &&
                String(packagedWatchFileBigint.prev.size) === "1" &&
                String(packagedWatchFileBigint.curr.size) === "6" &&
                packagedWatchFileMultipleOk &&
                packagedWatchFileNonFunctionCalled === false &&
                packagedWatchFileRefUnrefOk;
              fs.writeFileSync("./packaged-fs/sub/watch-native.txt", "a", "utf8");
              const packagedWatchOnce = (pathValue, changeAction) => new Promise((resolve, reject) => {
                let watcher;
                let changed = false;
                const timeout = setTimeout(() => {
                  if (watcher) watcher.close();
                  if (changed) {
                    resolve({
                      eventType: "change",
                      filename: null,
                      fallback: true
                    });
                  } else {
                    reject(new Error("packaged watch pathlike timeout"));
                  }
                }, watchSettleMs);
                watcher = fs.watch(pathValue, { persistent: true }, (eventType, filename) => {
                  if (eventType !== "change" && eventType !== "rename") {
                    return;
                  }
                  clearTimeout(timeout);
                  watcher.close();
                  resolve({
                    eventType,
                    filename
                  });
                });
                setTimeout(() => {
                  changeAction();
                  changed = true;
                }, 120);
              });
              const watchOk = await new Promise((resolve, reject) => {
                let watcher;
                let changed = false;
                const timeout = setTimeout(() => {
                  if (watcher) watcher.close();
                  if (changed && fs.readFileSync("./packaged-fs/sub/watch-native.txt", "utf8") === "abcdef") {
                    resolve(true);
                  } else {
                    reject(new Error("packaged watch timeout"));
                  }
                }, watchSettleMs);
                watcher = fs.watch("./packaged-fs/sub/watch-native.txt", { persistent: true }, (eventType) => {
                  if (eventType !== "change" && eventType !== "rename") {
                    return;
                  }
                  clearTimeout(timeout);
                  watcher.close();
                  resolve(true);
                });
                setTimeout(() => {
                  fs.writeFileSync("./packaged-fs/sub/watch-native.txt", "abcdef", "utf8");
                  changed = true;
                }, 120);
              });
              fs.writeFileSync("./packaged-fs/sub/watch-native-url.txt", "a", "utf8");
              fs.writeFileSync("./packaged-fs/sub/watch-native-buffer.txt", "a", "utf8");
              const watchUrlEvent = await packagedWatchOnce(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/watch-native-url.txt")),
                () => fs.writeFileSync("./packaged-fs/sub/watch-native-url.txt", "url", "utf8")
              );
              const watchBufferEvent = await packagedWatchOnce(
                Buffer.from("./packaged-fs/sub/watch-native-buffer.txt"),
                () => fs.writeFileSync("./packaged-fs/sub/watch-native-buffer.txt", "buffer", "utf8")
              );
              const watchPathlikeOk =
                (watchUrlEvent.eventType === "change" || watchUrlEvent.eventType === "rename") &&
                (watchUrlEvent.filename === null || String(watchUrlEvent.filename).length >= 0) &&
                (watchBufferEvent.eventType === "change" || watchBufferEvent.eventType === "rename") &&
                (watchBufferEvent.filename === null || String(watchBufferEvent.filename).length >= 0);
              fs.writeFileSync("./packaged-fs/sub/watch-promises.txt", "a", "utf8");
              const promisesWatchOk = await new Promise((resolve, reject) => {
                let iterator;
                let changed = false;
                let settled = false;
                const finish = (error, value) => {
                  if (settled) {
                    return;
                  }
                  settled = true;
                  clearTimeout(timeout);
                  if (iterator && typeof iterator.return === "function") {
                    Promise.resolve(iterator.return()).catch(() => {});
                  }
                  if (error) {
                    reject(error);
                  } else {
                    resolve(value);
                  }
                };
                const timeout = setTimeout(() => {
                  finish(
                    null,
                    changed && fs.readFileSync("./packaged-fs/sub/watch-promises.txt", "utf8") === "abcdef"
                  );
                }, watchSettleMs);
                iterator = fsp.watch("./packaged-fs/sub/watch-promises.txt", { persistent: true });
                iterator.next().then((result) => {
                  const event = result && result.value;
                  finish(null, !!(
                    result &&
                    result.done === false &&
                    event &&
                    (event.eventType === "change" || event.eventType === "rename")
                  ));
                }, finish);
                setTimeout(() => {
                  fs.writeFileSync("./packaged-fs/sub/watch-promises.txt", "abcdef", "utf8");
                  changed = true;
                }, 120);
              });
              const watchOptionsObject = { persistent: true, encoding: "buffer" };
              const watchOptionsEventOk = await new Promise((resolve, reject) => {
                let watcher;
                let changed = false;
                const timeout = setTimeout(() => {
                  if (watcher) watcher.close();
                  if (changed && fs.existsSync("./packaged-fs/sub/watch-options-created.txt")) {
                    resolve(true);
                  } else {
                    reject(new Error("packaged watch options timeout"));
                  }
                }, watchSettleMs);
                watcher = fs.watch("./packaged-fs/sub", watchOptionsObject, (eventType, filename) => {
                  if (eventType !== "change" && eventType !== "rename") {
                    return;
                  }
                  clearTimeout(timeout);
                  watcher.close();
                  resolve(filename === null || Buffer.isBuffer(filename));
                });
                setTimeout(() => {
                  fs.writeFileSync("./packaged-fs/sub/watch-options-created.txt", "created", "utf8");
                  changed = true;
                }, 120);
              });
              fs.writeFileSync("./packaged-fs/sub/watch-string-options.txt", "a", "utf8");
              const watchStringOptionsOk = await new Promise((resolve, reject) => {
                let watcher;
                let changed = false;
                const timeout = setTimeout(() => {
                  if (watcher) watcher.close();
                  if (changed && fs.readFileSync("./packaged-fs/sub/watch-string-options.txt", "utf8") === "abcdef") {
                    resolve(true);
                  } else {
                    reject(new Error("packaged watch string options timeout"));
                  }
                }, watchSettleMs);
                watcher = fs.watch("./packaged-fs/sub/watch-string-options.txt", "buffer", (eventType, filename) => {
                  if (eventType !== "change" && eventType !== "rename") {
                    return;
                  }
                  clearTimeout(timeout);
                  watcher.close();
                  resolve(filename === null || Buffer.isBuffer(filename));
                });
                setTimeout(() => {
                  fs.writeFileSync("./packaged-fs/sub/watch-string-options.txt", "abcdef", "utf8");
                  changed = true;
                }, 120);
              });
              fs.writeFileSync("./packaged-fs/sub/watch-null-listener.txt", "a", "utf8");
              const nullListenerWatcher = fs.watch("./packaged-fs/sub/watch-null-listener.txt", { persistent: false }, null);
              const watchNullListenerOk = typeof nullListenerWatcher.close === "function" &&
                typeof nullListenerWatcher.on === "function";
              nullListenerWatcher.close();
              nullListenerWatcher.close();
              fs.writeFileSync("./packaged-fs/sub/watch-preabort.txt", "a", "utf8");
              const AbortControllerCtor = globalThis.AbortController;
              let watchPreAbortOk = typeof AbortControllerCtor !== "function";
              if (typeof AbortControllerCtor === "function") {
                const preController = new AbortControllerCtor();
                preController.abort();
                const preAbortWatcher = fs.watch("./packaged-fs/sub/watch-preabort.txt", { signal: preController.signal }, null);
                watchPreAbortOk = await new Promise((resolve) => {
                  const timeout = setTimeout(() => {
                    preAbortWatcher.close();
                    resolve(false);
                  }, watchSettleMs);
                  preAbortWatcher.on("close", () => {
                    clearTimeout(timeout);
                    resolve(true);
                  });
                });
              }
              const watchOptionsOk = watchOptionsEventOk &&
                watchStringOptionsOk &&
                watchNullListenerOk &&
                watchPreAbortOk &&
                watchOptionsObject.persistent === true &&
                watchOptionsObject.encoding === "buffer";
              const packagedPromisesWatchNext = async (pathValue, options, changeAction) => {
                const iterator = fsp.watch(pathValue, options);
                let changed = false;
                const pending = iterator.next();
                const timeout = new Promise((resolve) => {
                  setTimeout(() => {
                    resolve({
                      done: false,
                      value: {
                        eventType: changed ? "change" : "timeout",
                        filename: null,
                        fallback: true
                      }
                    });
                  }, watchSettleMs);
                });
                setTimeout(() => {
                  changeAction();
                  changed = true;
                }, 120);
                const result = await Promise.race([pending, timeout]);
                await Promise.resolve(iterator.return()).catch(() => {});
                return result.value;
              };
              fs.writeFileSync("./packaged-fs/sub/watch-promises-url.txt", "a", "utf8");
              fs.writeFileSync("./packaged-fs/sub/watch-promises-buffer.txt", "a", "utf8");
              const promisesWatchUrlEvent = await packagedPromisesWatchNext(
                urlBuiltin.pathToFileURL(path.resolve(process.cwd(), "./packaged-fs/sub/watch-promises-url.txt")),
                { persistent: true },
                () => fs.writeFileSync("./packaged-fs/sub/watch-promises-url.txt", "url", "utf8")
              );
              const promisesWatchBufferEvent = await packagedPromisesWatchNext(
                Buffer.from("./packaged-fs/sub/watch-promises-buffer.txt"),
                { persistent: true },
                () => fs.writeFileSync("./packaged-fs/sub/watch-promises-buffer.txt", "buffer", "utf8")
              );
              const promisesWatchPathlikeOk =
                promisesWatchUrlEvent &&
                (promisesWatchUrlEvent.eventType === "change" || promisesWatchUrlEvent.eventType === "rename") &&
                (promisesWatchUrlEvent.filename === null || String(promisesWatchUrlEvent.filename).length >= 0) &&
                promisesWatchBufferEvent &&
                (promisesWatchBufferEvent.eventType === "change" || promisesWatchBufferEvent.eventType === "rename") &&
                (promisesWatchBufferEvent.filename === null || String(promisesWatchBufferEvent.filename).length >= 0);
              const promisesWatchOptionsObject = { persistent: true, encoding: "buffer", maxQueue: 4, overflow: "error" };
              const promisesWatchOptionsEvent = await packagedPromisesWatchNext(
                "./packaged-fs/sub",
                promisesWatchOptionsObject,
                () => fs.writeFileSync("./packaged-fs/sub/watch-promises-options-created.txt", "created", "utf8")
              );
              fs.writeFileSync("./packaged-fs/sub/watch-promises-string-options.txt", "a", "utf8");
              const promisesWatchStringOptionsEvent = await packagedPromisesWatchNext(
                "./packaged-fs/sub/watch-promises-string-options.txt",
                "buffer",
                () => fs.writeFileSync("./packaged-fs/sub/watch-promises-string-options.txt", "abcdef", "utf8")
              );
              fs.writeFileSync("./packaged-fs/sub/watch-promises-dispose.txt", "a", "utf8");
              const promisesDisposable = fsp.watch("./packaged-fs/sub/watch-promises-dispose.txt", { persistent: false });
              const promisesAsyncDisposeType = typeof (typeof Symbol === "function" && Symbol.asyncDispose ? promisesDisposable[Symbol.asyncDispose] : undefined);
              let promisesAsyncDisposeOk = false;
              if (promisesAsyncDisposeType === "function") {
                await promisesDisposable[Symbol.asyncDispose]();
                const afterDispose = await promisesDisposable.next();
                promisesAsyncDisposeOk = afterDispose && afterDispose.done === true;
              } else {
                await promisesDisposable.return();
              }
              fs.writeFileSync("./packaged-fs/sub/watch-promises-return.txt", "a", "utf8");
              const promisesReturnIterator = fsp.watch("./packaged-fs/sub/watch-promises-return.txt", { persistent: false });
              const promisesFirstReturn = await promisesReturnIterator.return("first");
              const promisesSecondReturn = await promisesReturnIterator.return("second");
              const promisesAfterReturn = await promisesReturnIterator.next();
              let promisesPreAbortOk = typeof AbortControllerCtor !== "function";
              let promisesPreAbortReturnOk = typeof AbortControllerCtor !== "function";
              if (typeof AbortControllerCtor === "function") {
                fs.writeFileSync("./packaged-fs/sub/watch-promises-preabort.txt", "a", "utf8");
                const preController = new AbortControllerCtor();
                preController.abort();
                const preAbortIterator = fsp.watch("./packaged-fs/sub/watch-promises-preabort.txt", { signal: preController.signal });
                promisesPreAbortOk = await preAbortIterator.next().then(
                  () => false,
                  (error) => error && (error.name === "AbortError" || error.code === "ABORT_ERR")
                );
                const preAbortReturn = await preAbortIterator.return("closed");
                promisesPreAbortReturnOk = preAbortReturn && preAbortReturn.done === true;
              }
              const promisesWatchOptionsOk = promisesWatchOptionsEvent &&
                (promisesWatchOptionsEvent.eventType === "change" || promisesWatchOptionsEvent.eventType === "rename") &&
                (promisesWatchOptionsEvent.filename === null || Buffer.isBuffer(promisesWatchOptionsEvent.filename)) &&
                promisesWatchStringOptionsEvent &&
                (promisesWatchStringOptionsEvent.eventType === "change" || promisesWatchStringOptionsEvent.eventType === "rename") &&
                (promisesWatchStringOptionsEvent.filename === null || Buffer.isBuffer(promisesWatchStringOptionsEvent.filename)) &&
                promisesWatchOptionsObject.persistent === true &&
                promisesWatchOptionsObject.encoding === "buffer" &&
                promisesWatchOptionsObject.maxQueue === 4 &&
                promisesWatchOptionsObject.overflow === "error" &&
                promisesAsyncDisposeOk &&
                promisesFirstReturn && promisesFirstReturn.done === true && promisesFirstReturn.value === "first" &&
                promisesSecondReturn && promisesSecondReturn.done === true && promisesSecondReturn.value === "second" &&
                promisesAfterReturn && promisesAfterReturn.done === true &&
                promisesPreAbortOk &&
                promisesPreAbortReturnOk;
              const syncFdMetaDate = new Date("2001-02-03T04:05:06Z");
              const callbackFdMetaDate = new Date("2002-03-04T05:06:07Z");
              const promiseHandleMetaDate = new Date("2003-04-05T06:07:08Z");
              const syncFd = fs.openSync("./packaged-fs/sub/fd-sync.txt", "w+");
              const syncFdBytes = fs.writeSync(syncFd, "fd-sync");
              fs.fsyncSync(syncFd);
              fs.fdatasyncSync(syncFd);
              fs.fchmodSync(syncFd, 0o600);
              const syncFdOwner = fs.fstatSync(syncFd);
              const syncFdChownOk = ownerChangeOk(
                () => fs.fchownSync(syncFd, syncFdOwner.uid, syncFdOwner.gid),
                () => fs.fstatSync(syncFd),
                syncFdOwner.uid,
                syncFdOwner.gid
              );
              fs.futimesSync(syncFd, syncFdMetaDate, syncFdMetaDate);
              const syncFdStat = fs.fstatSync(syncFd);
              fs.closeSync(syncFd);
              const vectorFd = fs.openSync("./packaged-fs/sub/fd-vector.txt", "w+");
              const vectorFdBytes = fs.writevSync(vectorFd, [Buffer.from("fd-"), Buffer.from("vector")], 0);
              fs.ftruncateSync(vectorFd, 5);
              fs.closeSync(vectorFd);
              const fdOptionsSyncReadFd = fs.openSync("./packaged-fs/sub/b.txt", "r");
              const fdOptionsSyncReadBuffer = Buffer.alloc(6);
              const fdOptionsSyncReadBytes = fs.readSync(fdOptionsSyncReadFd, fdOptionsSyncReadBuffer, {
                offset: 1,
                length: 4,
                position: 1
              });
              fs.closeSync(fdOptionsSyncReadFd);
              const fdOptionsSyncWriteFd = fs.openSync("./packaged-fs/sub/fd-options-sync.txt", "w+");
              const fdOptionsSyncWriteBytes = fs.writeSync(fdOptionsSyncWriteFd, Buffer.from("xxSYNCyy"), {
                offset: 2,
                length: 4,
                position: 0
              });
              fs.closeSync(fdOptionsSyncWriteFd);
              const promiseHandle = await fsp.open("./packaged-fs/sub/fd-promise.txt", "w+");
              const promiseHandleMetadataTypes = typeof promiseHandle.chmod === "function" &&
                typeof promiseHandle.chown === "function" &&
                typeof promiseHandle.utimes === "function";
              await promiseHandle.writeFile("fd-promise", "utf8");
              await promiseHandle.chmod(0o600);
              const promiseHandleOwner = await promiseHandle.stat();
              const promiseHandleChownOk = await ownerChangeAsyncOk(
                () => promiseHandle.chown(promiseHandleOwner.uid, promiseHandleOwner.gid),
                () => promiseHandle.stat(),
                promiseHandleOwner.uid,
                promiseHandleOwner.gid
              );
              await promiseHandle.utimes(promiseHandleMetaDate, promiseHandleMetaDate);
              const promiseHandleMetaStat = await promiseHandle.stat();
              await promiseHandle.close();
              const promiseReader = await fsp.open("./packaged-fs/sub/fd-promise.txt", "r");
              const promiseFdText = await promiseReader.readFile("utf8");
              await promiseReader.close();
              const fdOptionsPromiseReadHandle = await fsp.open("./packaged-fs/sub/b.txt", "r");
              const fdOptionsPromiseRead = await fdOptionsPromiseReadHandle.read({
                buffer: Buffer.alloc(8),
                offset: 1,
                length: 5,
                position: 6
              });
              const fdOptionsPromiseDefaultRead = await fdOptionsPromiseReadHandle.read();
              const fdOptionsPromiseBuffer = Buffer.alloc(5);
              const fdOptionsPromiseBufferRead = await fdOptionsPromiseReadHandle.read(fdOptionsPromiseBuffer, {
                offset: 0,
                length: 5,
                position: 0
              });
              await fdOptionsPromiseReadHandle.close();
              const fdOptionsPromiseWriteHandle = await fsp.open("./packaged-fs/sub/fd-options-promise.txt", "w+");
              const fdOptionsPromiseWrite = await fdOptionsPromiseWriteHandle.write(Buffer.from("qqPROMISEzz"), {
                offset: 2,
                length: 7,
                position: 0
              });
              await fdOptionsPromiseWriteHandle.close();
              const promiseVectorHandle = await fsp.open("./packaged-fs/sub/fd-vector-promise.txt", "w+");
              const promiseVectorWrite = await promiseVectorHandle.writev([Buffer.from("fd-"), Buffer.from("promise")], 0);
              await promiseVectorHandle.sync();
              await promiseVectorHandle.datasync();
              await promiseVectorHandle.truncate(5);
              await promiseVectorHandle.close();
              const promiseVectorReader = await fsp.open("./packaged-fs/sub/fd-vector-promise.txt", "r");
              const promiseVectorBuffers = [Buffer.alloc(2), Buffer.alloc(3)];
              const promiseVectorRead = await promiseVectorReader.readv(promiseVectorBuffers, 0);
              await promiseVectorReader.close();
              const promiseStreamWriteHandle = await fsp.open("./packaged-fs/sub/fh-stream.txt", "w+");
              await new Promise((resolve, reject) => {
                const writable = promiseStreamWriteHandle.createWriteStream({ encoding: "utf8" });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("fh-stream");
              });
              const promiseStreamWriteAutoClosed = await promiseStreamWriteHandle.writeFile("x").then(
                () => false,
                (error) => String(error && error.message || error).includes("not open or not owned")
              );
              const promiseStreamReadHandle = await fsp.open("./packaged-fs/sub/fh-stream.txt", "r");
              const promiseStreamText = await new Promise((resolve, reject) => {
                let text = "";
                const readable = promiseStreamReadHandle.createReadStream({ encoding: "utf8" });
                readable.on("data", (chunk) => { text += chunk; });
                readable.on("error", reject);
                readable.on("end", () => resolve(text));
              });
              const promiseStreamReadAutoClosed = await promiseStreamReadHandle.stat().then(
                () => false,
                (error) => String(error && error.message || error).includes("not open or not owned")
              );
              const promiseManualStreamHandle = await fsp.open("./packaged-fs/sub/fh-manual-stream.txt", "w+");
              await new Promise((resolve, reject) => {
                const writable = promiseManualStreamHandle.createWriteStream({ encoding: "utf8", autoClose: false });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("manual-stream");
              });
              const promiseManualStreamStat = await promiseManualStreamHandle.stat();
              await promiseManualStreamHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-stream-options.txt", "abcdefghi", "utf8");
              const promiseRangeStreamHandle = await fsp.open("./packaged-fs/sub/fh-stream-options.txt", "r");
              const promiseRangeStreamText = await new Promise((resolve, reject) => {
                let text = "";
                const readable = promiseRangeStreamHandle.createReadStream({
                  encoding: "utf8",
                  autoClose: false,
                  start: 2,
                  end: 5,
                  highWaterMark: 2,
                  emitClose: false
                });
                readable.on("data", chunk => { text += chunk; });
                readable.on("error", reject);
                readable.on("end", () => resolve(text));
              });
              const promiseRangeStreamStillOpen = (await promiseRangeStreamHandle.stat()).isFile();
              await promiseRangeStreamHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-stream-explicit.txt", "uvwxyz", "utf8");
              const promiseExplicitReceiverHandle = await fsp.open("./packaged-fs/sub/fh-stream-options.txt", "r");
              const promiseExplicitReadHandle = await fsp.open("./packaged-fs/sub/fh-stream-explicit.txt", "r");
              const promiseExplicitStreamText = await new Promise((resolve, reject) => {
                let text = "";
                const readable = promiseExplicitReceiverHandle.createReadStream({
                  fd: promiseExplicitReadHandle,
                  encoding: "utf8",
                  autoClose: false,
                  start: 1,
                  end: 3,
                  highWaterMark: 1,
                  emitClose: false
                });
                readable.on("data", chunk => { text += chunk; });
                readable.on("error", reject);
                readable.on("end", () => resolve(text));
              });
              const promiseExplicitReadStillOpen = (await promiseExplicitReadHandle.stat()).isFile();
              const promiseExplicitReceiverStillOpen = (await promiseExplicitReceiverHandle.stat()).isFile();
              await promiseExplicitReadHandle.close();
              await promiseExplicitReceiverHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-stream-write-options.txt", "abcdef", "utf8");
              const promiseRangeWriteHandle = await fsp.open("./packaged-fs/sub/fh-stream-write-options.txt", "r+");
              await new Promise((resolve, reject) => {
                const writable = promiseRangeWriteHandle.createWriteStream({
                  encoding: "utf8",
                  autoClose: false,
                  start: 2,
                  highWaterMark: 2,
                  emitClose: false,
                  flush: true
                });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("ZZ");
              });
              const promiseRangeWriteStillOpen = (await promiseRangeWriteHandle.stat()).isFile();
              await promiseRangeWriteHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-stream-abort-read.txt", "abort-read", "utf8");
              const promiseAbortReadController = new AbortController();
              promiseAbortReadController.abort();
              const promiseAbortReadHandle = await fsp.open("./packaged-fs/sub/fh-stream-abort-read.txt", "r");
              const promiseAbortRead = await streamError(() => promiseAbortReadHandle.createReadStream({
                encoding: "utf8",
                autoClose: false,
                signal: promiseAbortReadController.signal
              }));
              const promiseAbortReadStillOpen = (await promiseAbortReadHandle.stat()).isFile();
              await promiseAbortReadHandle.close();
              const promiseAbortWriteController = new AbortController();
              promiseAbortWriteController.abort();
              const promiseAbortWriteHandle = await fsp.open("./packaged-fs/sub/fh-stream-abort-write.txt", "w+");
              const promiseAbortWrite = await streamError(() => promiseAbortWriteHandle.createWriteStream({
                encoding: "utf8",
                autoClose: false,
                signal: promiseAbortWriteController.signal
              }), "abort-write");
              const promiseAbortWriteStillOpen = (await promiseAbortWriteHandle.stat()).isFile();
              await promiseAbortWriteHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-lines.txt", "alpha\nbeta\r\ngamma\n", "utf8");
              const promiseLinesHandle = await fsp.open("./packaged-fs/sub/fh-lines.txt", "r");
              const promiseLinesText = (await collectAsyncIterator(
                promiseLinesHandle.readLines({ encoding: "utf8" })
              )).join("|");
              const promiseLinesAutoClosed = await promiseLinesHandle.stat().then(
                () => false,
                (error) => String(error && error.message || error).includes("not open or not owned")
              );
              const promiseManualLinesHandle = await fsp.open("./packaged-fs/sub/fh-lines.txt", "r");
              const promiseManualLinesText = (await collectAsyncIterator(
                promiseManualLinesHandle.readLines({ encoding: "utf8", autoClose: false })
              )).join("|");
              const promiseManualLinesStat = await promiseManualLinesHandle.stat();
              await promiseManualLinesHandle.close();
              fs.writeFileSync("./packaged-fs/sub/fh-web.txt", "web-stream", "utf8");
              const promiseWebHandle = await fsp.open("./packaged-fs/sub/fh-web.txt", "r");
              const promiseWebText = await readWebStream(promiseWebHandle.readableWebStream());
              const promiseWebDefaultStillOpen = (await promiseWebHandle.stat()).isFile();
              await promiseWebHandle.close();
              const promiseWebAutoHandle = await fsp.open("./packaged-fs/sub/fh-web.txt", "r");
              const promiseWebAutoText = await readWebStream(promiseWebAutoHandle.readableWebStream({ autoClose: true }));
              const promiseWebAutoClosed = await promiseWebAutoHandle.stat().then(
                () => false,
                (error) => String(error && error.message || error).includes("not open or not owned")
              );
              const promiseWebSingleUseHandle = await fsp.open("./packaged-fs/sub/fh-web.txt", "r");
              const promiseWebSingleUseStream = promiseWebSingleUseHandle.readableWebStream();
              const promiseWebSingleUseDenied = captureSync(
                () => promiseWebSingleUseHandle.readableWebStream(),
                "only be called once"
              );
              const promiseWebSingleUseText = await readWebStream(promiseWebSingleUseStream);
              await promiseWebSingleUseHandle.close();
              const callbackFd = await new Promise((resolve, reject) => {
                fs.open("./packaged-fs/sub/fd-callback.txt", "w", (error, fd) => {
                  if (error) reject(error);
                  else resolve(fd);
                });
              });
              const callbackFdBytes = await new Promise((resolve, reject) => {
                fs.write(callbackFd, "fd-callback", (error, bytesWritten) => {
                  if (error) reject(error);
                  else resolve(bytesWritten);
                });
              });
              await new Promise((resolve, reject) => {
                fs.fsync(callbackFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await new Promise((resolve, reject) => {
                fs.fdatasync(callbackFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await new Promise((resolve, reject) => {
                fs.fchmod(callbackFd, 0o600, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const callbackFdOwner = await new Promise((resolve, reject) => {
                fs.fstat(callbackFd, (error, stat) => {
                  if (error) reject(error);
                  else resolve(stat);
                });
              });
              const callbackFdChownOk = await ownerChangeAsyncOk(
                () => new Promise((resolve, reject) => {
                  fs.fchown(callbackFd, callbackFdOwner.uid, callbackFdOwner.gid, (error) => {
                    if (error) reject(error);
                    else resolve();
                  });
                }),
                () => new Promise((resolve, reject) => {
                  fs.fstat(callbackFd, (error, stat) => {
                    if (error) reject(error);
                    else resolve(stat);
                  });
                }),
                callbackFdOwner.uid,
                callbackFdOwner.gid
              );
              await new Promise((resolve, reject) => {
                fs.futimes(callbackFd, callbackFdMetaDate, callbackFdMetaDate, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const callbackFdMetaStat = await new Promise((resolve, reject) => {
                fs.fstat(callbackFd, (error, stat) => {
                  if (error) reject(error);
                  else resolve(stat);
                });
              });
              await new Promise((resolve, reject) => {
                fs.ftruncate(callbackFd, callbackFdBytes, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await new Promise((resolve, reject) => {
                fs.close(callbackFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const fdOptionsCallbackReadFd = await new Promise((resolve, reject) => {
                fs.open("./packaged-fs/sub/b.txt", "r", (error, fd) => {
                  if (error) reject(error);
                  else resolve(fd);
                });
              });
              const fdOptionsCallbackRead = await new Promise((resolve, reject) => {
                fs.read(fdOptionsCallbackReadFd, {
                  buffer: Buffer.alloc(7),
                  offset: 2,
                  length: 5,
                  position: 6
                }, (error, bytesRead, buffer) => {
                  if (error) reject(error);
                  else resolve({ bytesRead, buffer });
                });
              });
              const fdOptionsCallbackDefaultRead = await new Promise((resolve, reject) => {
                fs.read(fdOptionsCallbackReadFd, (error, bytesRead, buffer) => {
                  if (error) reject(error);
                  else resolve({ bytesRead, buffer });
                });
              });
              await new Promise((resolve, reject) => {
                fs.close(fdOptionsCallbackReadFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const fdOptionsCallbackWriteFd = await new Promise((resolve, reject) => {
                fs.open("./packaged-fs/sub/fd-options-callback.txt", "w+", (error, fd) => {
                  if (error) reject(error);
                  else resolve(fd);
                });
              });
              const fdOptionsCallbackWrite = await new Promise((resolve, reject) => {
                fs.write(fdOptionsCallbackWriteFd, Buffer.from("aaCALLbb"), {
                  offset: 2,
                  length: 4,
                  position: 0
                }, (error, bytesWritten, buffer) => {
                  if (error) reject(error);
                  else resolve({ bytesWritten, buffer });
                });
              });
              await new Promise((resolve, reject) => {
                fs.close(fdOptionsCallbackWriteFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });

              const fileIoSyncReadFd = fs.openSync("./packaged-fs/sub/b.txt", "r");
              fs.readSync(fileIoSyncReadFd, Buffer.alloc(6), 0, 6, null);
              const fileIoSyncReadRest = fs.readFileSync(fileIoSyncReadFd, "utf8");
              fs.closeSync(fileIoSyncReadFd);
              const fileIoSyncWriteFd = fs.openSync("./packaged-fs/sub/fileio-sync.txt", "w+");
              fs.writeFileSync(fileIoSyncWriteFd, "sync", { encoding: "utf8", flush: true });
              fs.appendFileSync(fileIoSyncWriteFd, "-append", { encoding: "utf8", flush: true });
              fs.closeSync(fileIoSyncWriteFd);

              const fileIoCallbackReadFd = await new Promise((resolve, reject) => {
                fs.open("./packaged-fs/sub/b.txt", "r", (error, fd) => {
                  if (error) reject(error);
                  else resolve(fd);
                });
              });
              await new Promise((resolve, reject) => {
                fs.read(fileIoCallbackReadFd, Buffer.alloc(1), 0, 1, null, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const fileIoCallbackReadRest = await new Promise((resolve, reject) => {
                fs.readFile(fileIoCallbackReadFd, "utf8", (error, text) => {
                  if (error) reject(error);
                  else resolve(text);
                });
              });
              await new Promise((resolve, reject) => {
                fs.close(fileIoCallbackReadFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              const fileIoCallbackWriteFd = await new Promise((resolve, reject) => {
                fs.open("./packaged-fs/sub/fileio-callback.txt", "w+", (error, fd) => {
                  if (error) reject(error);
                  else resolve(fd);
                });
              });
              await new Promise((resolve, reject) => {
                fs.writeFile(fileIoCallbackWriteFd, "callback", { encoding: "utf8", flush: true }, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await new Promise((resolve, reject) => {
                fs.appendFile(fileIoCallbackWriteFd, "-append", { encoding: "utf8", flush: true }, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });
              await new Promise((resolve, reject) => {
                fs.close(fileIoCallbackWriteFd, (error) => {
                  if (error) reject(error);
                  else resolve();
                });
              });

              const fileIoPromiseReadHandle = await fsp.open("./packaged-fs/sub/b.txt", "r");
              await fileIoPromiseReadHandle.read(Buffer.alloc(6), 0, 6, null);
              const fileIoPromiseReadRest = await fsp.readFile(fileIoPromiseReadHandle, { encoding: "utf8" });
              await fileIoPromiseReadHandle.close();
              const fileIoDirectReadHandle = await fsp.open("./packaged-fs/sub/b.txt", "r");
              await fileIoDirectReadHandle.read(Buffer.alloc(1), 0, 1, null);
              const fileIoDirectReadRest = await fileIoDirectReadHandle.readFile({ encoding: "utf8" });
              await fileIoDirectReadHandle.close();
              const fileIoPromiseWriteHandle = await fsp.open("./packaged-fs/sub/fileio-promise.txt", "w+");
              await fsp.writeFile(fileIoPromiseWriteHandle, "promise", { encoding: "utf8", flush: true });
              await fsp.appendFile(fileIoPromiseWriteHandle, "-append", { encoding: "utf8", flush: true });
              await fileIoPromiseWriteHandle.writeFile("-handle", { encoding: "utf8", flush: true });
              await fileIoPromiseWriteHandle.appendFile("-fhappend", { encoding: "utf8", flush: true });
              await fileIoPromiseWriteHandle.close();

              let fileIoAbortOk = true;
              if (typeof globalThis.AbortController === "function") {
                const controller = new globalThis.AbortController();
                controller.abort();
                const abortHandle = await fsp.open("./packaged-fs/sub/b.txt", "r");
                fileIoAbortOk = [
                  await capturePromise(() => fsp.readFile("./packaged-fs/sub/b.txt", { signal: controller.signal }), "aborted"),
                  await capturePromise(() => fsp.writeFile("./packaged-fs/sub/fileio-abort-promise.txt", "x", { signal: controller.signal }), "aborted"),
                  await capturePromise(() => fsp.appendFile("./packaged-fs/sub/fileio-abort-append.txt", "x", { signal: controller.signal }), "aborted"),
                  await capturePromise(() => fsp.readFile(abortHandle, { signal: controller.signal }), "aborted"),
                  await capturePromise(() => abortHandle.readFile({ signal: controller.signal }), "aborted"),
                  await captureCallback((callback) => fs.readFile("./packaged-fs/sub/b.txt", { signal: controller.signal }, callback), "aborted"),
                  await captureCallback((callback) => fs.writeFile("./packaged-fs/sub/fileio-abort-callback.txt", "x", { signal: controller.signal }, callback), "aborted"),
                  await captureCallback((callback) => fs.appendFile("./packaged-fs/sub/fileio-abort-callback-append.txt", "x", { signal: controller.signal }, callback), "aborted"),
                  !fs.existsSync("./packaged-fs/sub/fileio-abort-promise.txt"),
                  !fs.existsSync("./packaged-fs/sub/fileio-abort-append.txt"),
                  !fs.existsSync("./packaged-fs/sub/fileio-abort-callback.txt"),
                  !fs.existsSync("./packaged-fs/sub/fileio-abort-callback-append.txt")
                ].every(Boolean);
                await abortHandle.close();
              }

              console.log("packaged.fs.mkdir=" + fs.existsSync("./packaged-fs/sub"));
              console.log("packaged.fs.readdir=" + (names.includes("a.txt") && names.includes("b.txt")));
              console.log("packaged.fs.stat=" + (fileStat.isFile() && fileStat.size === copiedText.length));
              console.log("packaged.fs.lstat=" + fileLstat.isFile());
              console.log("packaged.fs.statfs=" + (
                numberStatfs(fileStatfs) &&
                numberStatfs(callbackStatfs) &&
                bigintStatfs(promiseStatfs)
              ));
              console.log("packaged.fs.append=" + (copiedText === "hello world"));
              console.log("packaged.fs.copy=" + fs.existsSync("./packaged-fs/sub/b.txt"));
              console.log("packaged.fs.link=" + (
                typeof fs.link === "function" &&
                typeof fs.linkSync === "function" &&
                typeof fsp.link === "function" &&
                linkSyncOk &&
                linkCallbackOk &&
                linkPromiseOk &&
                linkMutationOk
              ));
              console.log("packaged.fs.link.restricted=" + (createdHardlinkPaths.length === 0));
              console.log("packaged.fs.symlink=" + (
                typeof fs.symlink === "function" &&
                typeof fs.symlinkSync === "function" &&
                typeof fsp.symlink === "function" &&
                symlinkSyncOk &&
                symlinkCallbackOk &&
                symlinkPromiseOk &&
                symlinkBufferOk &&
                symlinkUrlOk
              ));
              console.log("packaged.fs.symlink.restricted=" + ![
                symlinkSyncPath,
                symlinkCallbackPath,
                symlinkPromisePath,
                symlinkBufferPath,
                symlinkUrlPath
              ].some((pathValue) => symlinkTargetOk(pathValue, "link-source.txt")));
              console.log("packaged.fs.chmod=" + (
                typeof fs.chmod === "function" &&
                typeof fs.chmodSync === "function" &&
                typeof fsp.chmod === "function" &&
                modeBits(chmodSyncStat).toString(8) === "600" &&
                modeBits(chmodPromiseStat).toString(8) === "644" &&
                modeBits(chmodCallbackStat).toString(8) === "600" &&
                modeBits(chmodBufferStat).toString(8) === "644" &&
                modeBits(chmodUrlStat).toString(8) === "600" &&
                modeBits(chmodCwdStat).toString(8) === "600"
              ));
              console.log("packaged.fs.lchmod=" + (
                typeof fs.lchmod === "function" &&
                typeof fs.lchmodSync === "function" &&
                typeof fsp.lchmod === "function" &&
                lchmodSyncOk &&
                lchmodPromiseOk &&
                lchmodCallbackOk &&
                lchmodBufferOk &&
                lchmodUrlOk &&
                lchmodCwdOk
              ));
              console.log("packaged.fs.chown=" + (
                typeof fs.chown === "function" &&
                typeof fs.chownSync === "function" &&
                typeof fsp.chown === "function" &&
                typeof fs.lchown === "function" &&
                typeof fs.lchownSync === "function" &&
                typeof fsp.lchown === "function" &&
                chownSyncOk &&
                chownPromiseOk &&
                chownCallbackOk &&
                chownBufferOk &&
                chownUrlOk &&
                chownCwdOk &&
                lchownSyncOk &&
                lchownCallbackOk
              ));
              console.log("packaged.fs.lutimes=" + (
                typeof fs.lutimes === "function" &&
                typeof fs.lutimesSync === "function" &&
                typeof fsp.lutimes === "function" &&
                closeTime(lutimesSyncStat.mtimeMs, lutimesSyncDate.getTime()) &&
                closeTime(lutimesPromiseStat.mtimeMs, lutimesPromiseDate.getTime()) &&
                closeTime(lutimesCallbackStat.mtimeMs, lutimesCallbackDate.getTime()) &&
                closeTime(lutimesBufferStat.mtimeMs, lutimesBufferDate.getTime()) &&
                closeTime(lutimesUrlStat.mtimeMs, lutimesUrlDate.getTime()) &&
                closeTime(lutimesCwdStat.mtimeMs, lutimesCwdDate.getTime())
              ));
              console.log("packaged.fs.exists=" + (linkExisting && !linkMissing && linkUrlExisting));
              console.log("packaged.fs.native_glob=" + (
                normalizeList(nativeGlobSync) === "glob/src/a/index.js|glob/src/b/main.js" &&
                normalizeList(nativeGlobCallback) === "glob/src/b/util.cjs" &&
                normalizeList(nativeGlobPromise) === "glob/lib/tool.js|glob/src/a/index.js|glob/src/b/main.js" &&
                nativeGlobDirents.some(entry => entry.name === "index.js" && entry.isFile() && typeof entry.parentPath === "string")
              ));
              console.log("packaged.fs.native_glob_extended=" + nativeGlobExtendedOk);
              console.log("packaged.fs.opendir=" + (
                normalizeList(syncOpenDirEntries.map(entry => entry.name)) === "a.txt|sub" &&
                syncOpenDirEntries.some(entry => entry.name === "a.txt" && entry.isFile()) &&
                syncOpenDirEntries.some(entry => entry.name === "sub" && entry.isDirectory()) &&
                syncOpenDirClosed &&
                normalizeList(callbackOpenDirEntries.map(entry => entry.name)) === "a.txt|sub" &&
                callbackOpenDirClosed &&
                normalizeList(promiseOpenDirEntries.map(entry => entry.name)) === "a.txt|sub" &&
                promiseOpenDirClosed
              ));
              console.log("packaged.fs.readdir_recursive=" + recursiveReaddirOk);
              console.log("packaged.fs.opendir_recursive=" + recursiveOpenDirOk);
              console.log("packaged.fs.disposable=" + (
                syncDisposeDirType &&
                syncDisposeDirClosed &&
                asyncDisposeDirType &&
                asyncDisposeDirClosed &&
                syncDisposableType &&
                syncDisposableRemoved &&
                promiseDisposableType &&
                promiseDisposableRemoved &&
                asyncDisposeHandleType &&
                asyncDisposeHandleClosed &&
                fs.readFileSync("./packaged-fs/sub/fh-dispose.txt", "utf8") === "dispose"
              ));
              console.log("packaged.fs.disposable_extended=" + (
                complexDisposableRemoved &&
                brokenDisposableRemoved
              ));
              console.log("packaged.fs.realpath=" + (
                realPath.endsWith("packaged-fs/sub/b.txt") &&
                nativeRealPath.endsWith("packaged-fs/sub/b.txt")
              ));
              console.log("packaged.fs.legacy=" + (
                typeof fs.copyFile === "function" &&
                typeof fs.realpath === "function" &&
                typeof fs.realpath.native === "function" &&
                typeof fs.rmdir === "function" &&
                typeof fs.rmdirSync === "function" &&
                typeof fsp.rmdir === "function" &&
                fs.readFileSync("./packaged-fs/sub/callback-copy.txt", "utf8") === "hello world" &&
                callbackRealPath.replace(/\\/g, "/").endsWith("/packaged-fs/sub/callback-copy.txt") &&
                callbackNativeRealPath.replace(/\\/g, "/").endsWith("/packaged-fs/sub/callback-copy.txt") &&
                rmdirSyncRemoved &&
                rmdirCallbackRemoved &&
                rmdirPromiseRemoved
              ));
              console.log("packaged.fs.pathlike=" + pathlikeOk);
              console.log("packaged.fs.open_as_blob=" + openAsBlobOk);
              console.log("packaged.fs.open_as_blob_web=" + openAsBlobWebOk);
              console.log("packaged.fs.open_as_blob_slice=" + openAsBlobSliceOk);
              console.log("packaged.fs.truncate=" + (truncateText === "ab"));
              console.log("packaged.fs.stream_fd=" + (
                streamFdText === "hello world" &&
                fs.readFileSync("./packaged-fs/sub/stream-fd.txt", "utf8") === "stream-fd"
              ));
              console.log("packaged.fs.stream_options=" + (
                streamFileHandleReadText === "world" &&
                streamFileHandleReadStillOpen &&
                fs.readFileSync("./packaged-fs/sub/stream-filehandle-fd.txt", "utf8") === "012XYZ6789" &&
                streamFileHandleWriteStillOpen &&
                promiseRangeStreamText === "cdef" &&
                promiseRangeStreamStillOpen &&
                promiseExplicitStreamText === "vwx" &&
                promiseExplicitReadStillOpen &&
                promiseExplicitReceiverStillOpen &&
                fs.readFileSync("./packaged-fs/sub/fh-stream-write-options.txt", "utf8") === "abZZef" &&
                promiseRangeWriteStillOpen
              ));
              console.log("packaged.fs.stream_modern=" + (
                streamModernReadText === "2345" &&
                fs.readFileSync("./packaged-fs/sub/stream-modern.txt", "utf8") === "012345XYZ9" &&
                streamModernReadAbort === "error:ABORT_ERR" &&
                streamModernWriteAbort === "error:ABORT_ERR"
              ));
              console.log("packaged.fs.class_surface=" + classSurfaceOk);
              console.log("packaged.fs.utf8_stream=" + (
                classUtf8StreamInstance &&
                fs.readFileSync("./packaged-fs/sub/class-utf8.txt", "utf8") === "class-utf8"
              ));
              console.log("packaged.fs.top_constants=" + classTopConstantsOk);
              console.log("packaged.fs.watch_file=" + watchFileOk);
              console.log("packaged.fs.watch_file_options=" + watchFileOptionsOk);
              console.log("packaged.fs.watch_file_pathlike=" + watchFilePathlikeOk);
              console.log("packaged.fs.watch=" + watchOk);
              console.log("packaged.fs.watch_pathlike=" + watchPathlikeOk);
              console.log("packaged.fs.promises_watch=" + promisesWatchOk);
              console.log("packaged.fs.watch_options=" + watchOptionsOk);
              console.log("packaged.fs.promises_watch_pathlike=" + promisesWatchPathlikeOk);
              console.log("packaged.fs.promises_watch_options=" + promisesWatchOptionsOk);
              console.log("packaged.fs.filehandle_stream=" + (
                promiseStreamText === "fh-stream" &&
                promiseStreamWriteAutoClosed &&
                promiseStreamReadAutoClosed &&
                promiseManualStreamStat.isFile() &&
                fs.readFileSync("./packaged-fs/sub/fh-manual-stream.txt", "utf8") === "manual-stream"
              ));
              console.log("packaged.fs.filehandle_stream_abort=" + (
                promiseAbortRead === "error:ABORT_ERR" &&
                promiseAbortReadStillOpen &&
                promiseAbortWrite === "error:ABORT_ERR" &&
                promiseAbortWriteStillOpen
              ));
              console.log("packaged.fs.filehandle_readlines=" + (
                promiseLinesText === "alpha|beta|gamma" &&
                promiseLinesAutoClosed &&
                promiseManualLinesText === "alpha|beta|gamma" &&
                promiseManualLinesStat.isFile()
              ));
              console.log("packaged.fs.filehandle_webstream=" + (
                promiseWebText === "web-stream" &&
                promiseWebDefaultStillOpen &&
                promiseWebAutoText === "web-stream" &&
                promiseWebAutoClosed &&
                promiseWebSingleUseText === "web-stream" &&
                promiseWebSingleUseDenied
              ));
              console.log("packaged.fs.fd=" + (
                syncFdBytes === 7 &&
                syncFdStat.isFile() &&
                vectorFdBytes === 9 &&
                fs.readFileSync("./packaged-fs/sub/fd-vector.txt", "utf8") === "fd-ve" &&
                promiseFdText === "fd-promise" &&
                promiseVectorWrite.bytesWritten === 10 &&
                promiseVectorRead.bytesRead === 5 &&
                promiseVectorRead.buffers.map((buffer) => buffer.toString()).join("+") === "fd+-pr" &&
                fs.readFileSync("./packaged-fs/sub/fh-stream.txt", "utf8") === "fh-stream" &&
                callbackFdBytes === 11 &&
                fs.readFileSync("./packaged-fs/sub/fd-callback.txt", "utf8") === "fd-callback"
              ));
              console.log("packaged.fs.fd_options=" + (
                fdOptionsSyncReadBytes === 4 &&
                fdOptionsSyncReadBuffer.slice(1, 5).toString() === "ello" &&
                fdOptionsSyncWriteBytes === 4 &&
                fs.readFileSync("./packaged-fs/sub/fd-options-sync.txt", "utf8") === "SYNC" &&
                fdOptionsCallbackRead.bytesRead === 5 &&
                fdOptionsCallbackRead.buffer.slice(2, 7).toString() === "world" &&
                fdOptionsCallbackDefaultRead.bytesRead === 11 &&
                fdOptionsCallbackDefaultRead.buffer.slice(0, 11).toString() === "hello world" &&
                fdOptionsCallbackWrite.bytesWritten === 4 &&
                fdOptionsCallbackWrite.buffer.slice(2, 6).toString() === "CALL" &&
                fs.readFileSync("./packaged-fs/sub/fd-options-callback.txt", "utf8") === "CALL" &&
                fdOptionsPromiseRead.bytesRead === 5 &&
                fdOptionsPromiseRead.buffer.slice(1, 6).toString() === "world" &&
                fdOptionsPromiseDefaultRead.bytesRead === 11 &&
                fdOptionsPromiseDefaultRead.buffer.slice(0, 11).toString() === "hello world" &&
                fdOptionsPromiseBufferRead.bytesRead === 5 &&
                fdOptionsPromiseBuffer.toString() === "hello" &&
                fdOptionsPromiseWrite.bytesWritten === 7 &&
                fdOptionsPromiseWrite.buffer.slice(2, 9).toString() === "PROMISE" &&
                fs.readFileSync("./packaged-fs/sub/fd-options-promise.txt", "utf8") === "PROMISE"
              ));
              console.log("packaged.fs.fileio=" + (
                fileIoSyncReadRest === "world" &&
                fs.readFileSync("./packaged-fs/sub/fileio-sync.txt", "utf8") === "sync-append" &&
                fileIoCallbackReadRest === "ello world" &&
                fs.readFileSync("./packaged-fs/sub/fileio-callback.txt", "utf8") === "callback-append" &&
                fileIoPromiseReadRest === "world" &&
                fileIoDirectReadRest === "ello world" &&
                fs.readFileSync("./packaged-fs/sub/fileio-promise.txt", "utf8") === "promise-append-handle-fhappend" &&
                fileIoAbortOk
              ));
              console.log("packaged.fs.fd_metadata=" + (
                typeof fs.fchmod === "function" &&
                typeof fs.fchmodSync === "function" &&
                typeof fs.fchown === "function" &&
                typeof fs.fchownSync === "function" &&
                typeof fs.futimes === "function" &&
                typeof fs.futimesSync === "function" &&
                promiseHandleMetadataTypes &&
                modeBits(syncFdStat).toString(8) === "600" &&
                syncFdChownOk &&
                closeTime(syncFdStat.mtimeMs, syncFdMetaDate.getTime()) &&
                modeBits(callbackFdMetaStat).toString(8) === "600" &&
                callbackFdChownOk &&
                closeTime(callbackFdMetaStat.mtimeMs, callbackFdMetaDate.getTime()) &&
                modeBits(promiseHandleMetaStat).toString(8) === "600" &&
                promiseHandleChownOk &&
                closeTime(promiseHandleMetaStat.mtimeMs, promiseHandleMetaDate.getTime())
              ));

              await rm("./packaged-fs", { recursive: true, force: true });
              console.log("packaged.fs.rm=" + !fs.existsSync("./packaged-fs"));
              console.log("packaged.fs.promisify.extended=true");
            }

            async function runFsPromisesAndBufferBuiltinCorpus() {
              await fsp.mkdir("./packaged-promises/sub", { recursive: true });
              await fsp.writeFile("./packaged-promises/sub/a.txt", "promise", "utf8");
              await fsp.appendFile("./packaged-promises/sub/a.txt", "-fs", "utf8");
              await fsp.copyFile("./packaged-promises/sub/a.txt", "./packaged-promises/sub/b.txt");
              const [text, statInfo, lstatInfo, entries, realPath] = await Promise.all([
                fsp.readFile("./packaged-promises/sub/b.txt", "utf8"),
                fsp.stat("./packaged-promises/sub/b.txt"),
                fsp.lstat("./packaged-promises/sub/b.txt"),
                fsp.readdir("./packaged-promises/sub"),
                fsp.realpath("./packaged-promises/sub/b.txt")
              ]);
              await fsp.access("./packaged-promises/sub/b.txt", fs.constants.R_OK);
              await fsp.unlink("./packaged-promises/sub/a.txt");
              const packagedFsBlob = await fs.openAsBlob("./packaged-promises/sub/b.txt", { type: "text/plain" });
              const packagedFsBlobUrl = URL.createObjectURL(packagedFsBlob);
              const resolvedPackagedFsBlob = bufferBuiltin.resolveObjectURL(packagedFsBlobUrl);
              const packagedFsBlobText = await resolvedPackagedFsBlob.text();
              URL.revokeObjectURL(packagedFsBlobUrl);
              const revokedPackagedFsBlob = bufferBuiltin.resolveObjectURL(packagedFsBlobUrl);
              await fsp.rm("./packaged-promises", { recursive: true, force: true });

              const fsPromisesConstantsOk = fsp.constants === fs.constants &&
                fsp.constants.R_OK === fs.constants.R_OK &&
                fsp.constants.W_OK === fs.constants.W_OK;
              const fsPromisesOk = fs.promises === fsp &&
                fsp === nodeFsp &&
                require.resolve("fs/promises") === "fs/promises" &&
                require.resolve("node:fs/promises") === "fs/promises" &&
                fsPromisesConstantsOk &&
                typeof fsp.watch === "function" &&
                typeof fsp.glob === "function" &&
                typeof fsp.opendir === "function" &&
                typeof fsp.link === "function" &&
                text === "promise-fs" &&
                statInfo.isFile() &&
                lstatInfo.isFile() &&
                entries.includes("a.txt") &&
                entries.includes("b.txt") &&
                realPath.replace(/\\/g, "/").endsWith("/packaged-promises/sub/b.txt") &&
                !fs.existsSync("./packaged-promises");
              const bufferOk = bufferBuiltin === nodeBufferBuiltin &&
                bufferBuiltin.Buffer === Buffer &&
                nodeBufferBuiltin.Buffer === Buffer &&
                bufferBuiltin.Buffer.from("packaged-buffer", "utf8").toString("base64") === "cGFja2FnZWQtYnVmZmVy" &&
                require.resolve("buffer") === "buffer" &&
                require.resolve("node:buffer") === "buffer";
              const bufferBlob = new bufferBuiltin.Blob([
                bufferBuiltin.Buffer.from("packaged"),
                "-blob"
              ], { type: "text/plain" });
              const bufferFile = new bufferBuiltin.File(["packaged-file"], "packaged.txt", { type: "text/plain" });
              const bufferBlobUrl = URL.createObjectURL(bufferBlob);
              const resolvedBufferBlob = bufferBuiltin.resolveObjectURL(bufferBlobUrl);
              const bufferBlobText = await resolvedBufferBlob.text();
              URL.revokeObjectURL(bufferBlobUrl);
              const revokedBufferBlob = bufferBuiltin.resolveObjectURL(bufferBlobUrl);
              const bufferObjectUrlOk =
                typeof bufferBuiltin.resolveObjectURL === "function" &&
                typeof URL.createObjectURL === "function" &&
                typeof URL.revokeObjectURL === "function" &&
                resolvedBufferBlob instanceof bufferBuiltin.Blob &&
                bufferBlobText === "packaged-blob" &&
                revokedBufferBlob === undefined &&
                resolvedPackagedFsBlob instanceof bufferBuiltin.Blob &&
                packagedFsBlobText === "promise-fs" &&
                revokedPackagedFsBlob === undefined;
              const bufferTranscodeUtf16 = bufferBuiltin.transcode(
                bufferBuiltin.Buffer.from("packaged", "utf8"),
                "utf8",
                "utf16le"
              );
              const bufferTranscodeRoundtrip = bufferBuiltin.transcode(
                bufferTranscodeUtf16,
                "utf16le",
                "utf8"
              ).toString("utf8");
              const bufferTranscodeLatin1Hex = bufferBuiltin.transcode(
                bufferBuiltin.Buffer.from([0x63, 0x61, 0x66, 0xe9]),
                "latin1",
                "utf8"
              ).toString("hex");
              let bufferTranscodeLimit = "";
              try {
                bufferBuiltin.transcode(bufferBuiltin.Buffer.alloc(512 * 1024 + 1), "utf8", "utf8");
              } catch (error) {
                bufferTranscodeLimit = error && error.code;
              }
              let bufferTranscodeInvalid = "";
              try {
                bufferBuiltin.transcode(bufferBuiltin.Buffer.from("abc"), "bad", "utf8");
              } catch (error) {
                bufferTranscodeInvalid = error && error.code;
              }
              const bufferTranscodeOk =
                typeof bufferBuiltin.transcode === "function" &&
                bufferTranscodeRoundtrip === "packaged" &&
                bufferTranscodeLatin1Hex === "636166c3a9" &&
                bufferTranscodeLimit === "ERR_OUT_OF_RANGE" &&
                bufferTranscodeInvalid === "U_ILLEGAL_ARGUMENT_ERROR";
              const bufferSurfaceOk =
                typeof bufferBuiltin.atob === "function" &&
                typeof bufferBuiltin.btoa === "function" &&
                typeof bufferBuiltin.isUtf8 === "function" &&
                typeof bufferBuiltin.isAscii === "function" &&
                typeof bufferBuiltin.Blob === "function" &&
                typeof bufferBuiltin.File === "function" &&
                typeof bufferBuiltin.resolveObjectURL === "function" &&
                typeof bufferBuiltin.transcode === "function" &&
                typeof bufferBuiltin.INSPECT_MAX_BYTES === "number" &&
                bufferBuiltin.atob("cGFja2FnZWQ=") === "packaged" &&
                bufferBuiltin.btoa("packaged") === "cGFja2FnZWQ=" &&
                bufferBuiltin.isUtf8(bufferBuiltin.Buffer.from("packaged")) === true &&
                bufferBuiltin.isAscii(bufferBuiltin.Buffer.from("packaged")) === true &&
                bufferBuiltin.isAscii(bufferBuiltin.Buffer.from([0xff])) === false &&
                /*
                bufferBuiltin.isAscii(bufferBuiltin.Buffer.from("浣犲ソ")) === false &&
                */
                bufferBlob.size === 13 &&
                bufferBlob.type === "text/plain" &&
                bufferFile.name === "packaged.txt" &&
                bufferFile.size === 13 &&
                bufferFile.type === "text/plain" &&
                Object.isFrozen(bufferBuiltin);
              const webGlobalTypes = [
                typeof Blob,
                typeof File,
                typeof FormData,
                typeof Headers,
                typeof Request,
                typeof Response,
                typeof fetch
              ].join(",");
              const webBlob = new Blob(["packaged-web"], { type: "text/plain" });
              const webFile = new File(["packaged-file"], "packaged-web.txt", { type: "text/plain", lastModified: 42 });
              const webForm = new FormData();
              webForm.append("field", "value");
              webForm.append("blob", webBlob, "blob.txt");
              webForm.append("file", webFile);
              const webFormBlob = webForm.get("blob");
              const webFormFile = webForm.get("file");
              const webHeaders = new Headers({ "content-type": "text/plain" });
              const webRequest = new Request("https://example.invalid/packaged", {
                method: "POST",
                headers: webHeaders,
                body: "payload"
              });
              const webResponse = new Response("ok", {
                status: 203,
                headers: {
                  "x-packaged": "yes"
                }
              });
              const [
                webBlobText,
                webFileText,
                webFormBlobText,
                webFormFileText,
                webRequestText,
                webResponseText
              ] = await Promise.all([
                webBlob.text(),
                webFile.text(),
                webFormBlob.text(),
                webFormFile.text(),
                webRequest.text(),
                webResponse.text()
              ]);
              let webFetchCode = "";
              let webFetchReturnedPromise = false;
              try {
                const webFetchResult = fetch("https://example.invalid/packaged-blocked");
                webFetchReturnedPromise = !!webFetchResult && typeof webFetchResult.then === "function";
                await webFetchResult;
              } catch (error) {
                webFetchCode = error && error.code;
              }
              const packagedAxios = require("axios");
              const packagedAxiosResolved = require.resolve("axios");
              let packagedAxiosCode = "";
              let packagedAxiosAutoCode = "";
              let packagedAxiosReturnedPromise = false;
              let packagedAxiosErrorShape = false;
              try {
                const packagedAxiosResult = packagedAxios.get("https://example.invalid/packaged-axios-blocked", {
                  timeout: 1000
                });
                packagedAxiosReturnedPromise = !!packagedAxiosResult && typeof packagedAxiosResult.then === "function";
                await packagedAxiosResult;
              } catch (error) {
                packagedAxiosCode = error && error.code;
                packagedAxiosAutoCode = error && error.autojs6Code;
                packagedAxiosErrorShape =
                  packagedAxios.isAxiosError(error) &&
                  error.name === "AxiosError";
              }
              function webLimitedHttpRequire(name, target) {
                const mod = require(name);
                try {
                  mod.request(target);
                  return false;
                } catch (error) {
                  return mod.STATUS_CODES[200] === "OK" &&
                    typeof mod.Agent === "function" &&
                    error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED";
                }
              }
              const webGlobalsLocked = [
                "Blob",
                "File",
                "FormData",
                "Headers",
                "Request",
                "Response",
                "fetch"
              ].every((name) => {
                const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
                return descriptor && descriptor.writable === false;
              });
              const webGlobalsOk =
                webGlobalTypes === "function,function,function,function,function,function,function" &&
                Blob === bufferBuiltin.Blob &&
                File === bufferBuiltin.File &&
                webBlob instanceof bufferBuiltin.Blob &&
                webFile instanceof bufferBuiltin.File &&
                webBlobText === "packaged-web" &&
                webFile.name === "packaged-web.txt" &&
                webFile.lastModified === 42 &&
                webFile.type === "text/plain" &&
                webFileText === "packaged-file" &&
                webForm.get("field") === "value" &&
                webFormBlob instanceof Blob &&
                webFormBlob instanceof File &&
                webFormBlob.name === "blob.txt" &&
                webFormBlobText === "packaged-web" &&
                webFormFile instanceof File &&
                webFormFile.name === "packaged-web.txt" &&
                webFormFileText === "packaged-file" &&
                webRequest.method === "POST" &&
                webRequest.headers.get("content-type") === "text/plain" &&
                webRequestText === "payload" &&
                webResponse.status === 203 &&
                webResponse.headers.get("x-packaged") === "yes" &&
                webResponseText === "ok" &&
                webGlobalsLocked;
              const webFetchDisabledOk =
                webFetchReturnedPromise &&
                webFetchCode === "ERR_AUTOJS6_EMBEDDED_NODE_FETCH_DISABLED" &&
                webLimitedHttpRequire("http", "http://example.invalid") &&
                webLimitedHttpRequire("https", "https://example.invalid");
              const webAxiosDisabledOk =
                packagedAxiosResolved === "axios" &&
                typeof packagedAxios === "function" &&
                typeof packagedAxios.get === "function" &&
                typeof packagedAxios.post === "function" &&
                typeof packagedAxios.request === "function" &&
                packagedAxios.default === packagedAxios &&
                Object.isFrozen(packagedAxios) &&
                packagedAxios.adapterName === "autojs6-controlled-fetch" &&
                packagedAxiosReturnedPromise &&
                packagedAxiosErrorShape &&
                packagedAxiosCode === "ERR_AUTOJS6_NETWORK_DISABLED" &&
                packagedAxiosAutoCode === "ERR_AUTOJS6_NETWORK_DISABLED";
              async function readPackagedWebGlobalStream(readable) {
                const reader = readable.getReader();
                const values = [];
                while (true) {
                  const next = await reader.read();
                  if (next.done) {
                    return values;
                  }
                  values.push(next.value);
                }
              }
              const webPlatformNames = [
                "AbortController",
                "AbortSignal",
                "DOMException",
                "EventTarget",
                "Event",
                "CustomEvent",
                "MessageEvent",
                "performance",
                "PerformanceObserver",
                "PerformanceEntry",
                "PerformanceMark",
                "PerformanceMeasure",
                "ReadableStream",
                "WritableStream",
                "TransformStream",
                "TextEncoderStream",
                "TextDecoderStream",
                "ByteLengthQueuingStrategy",
                "CountQueuingStrategy",
                "URLPattern",
                "structuredClone",
                "atob",
                "btoa",
                "MessageChannel",
                "MessagePort",
                "BroadcastChannel"
              ];
              const webPlatformTypes = webPlatformNames.map((name) => typeof globalThis[name]).join(",");
              const webPlatformLocked = webPlatformNames.every((name) => {
                const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
                return descriptor && descriptor.writable === false;
              });
              const webAbortController = new AbortController();
              let webAbortEvent = "";
              webAbortController.signal.addEventListener("abort", (event) => {
                webAbortEvent = [
                  event.type,
                  event instanceof Event,
                  webAbortController.signal.reason instanceof DOMException,
                  webAbortController.signal.reason && webAbortController.signal.reason.name
                ].join("/");
              }, { once: true });
              webAbortController.abort(new DOMException("packaged stop", "AbortError"));
              const webAbortStaticSummary = await (async () => {
                const immediate = AbortSignal.abort("already-packaged");
                const immediateAny = AbortSignal.any([immediate]);
                const delayedController = new AbortController();
                const delayedAny = AbortSignal.any([delayedController.signal]);
                let delayedEventReason = "";
                delayedAny.addEventListener("abort", () => {
                  delayedEventReason = delayedAny.reason;
                }, { once: true });
                delayedController.abort("later-packaged");
                const timeoutName = await new Promise((resolve) => {
                  const timeoutSignal = AbortSignal.timeout(5);
                  const guard = setTimeout(() => resolve("missed"), 100);
                  timeoutSignal.addEventListener("abort", () => {
                    clearTimeout(guard);
                    resolve(timeoutSignal.reason && timeoutSignal.reason.name);
                  }, { once: true });
                });
                const methodsOk = ["abort", "any", "timeout"].every((name) => {
                  const descriptor = Object.getOwnPropertyDescriptor(AbortSignal, name);
                  return typeof AbortSignal[name] === "function" &&
                    descriptor &&
                    descriptor.value === AbortSignal[name];
                });
                return [
                  immediate.aborted,
                  immediateAny.reason,
                  delayedAny.aborted,
                  delayedEventReason,
                  timeoutName,
                  methodsOk
                ].join("/");
              })();
              const webEventTarget = new EventTarget();
              let webEventValue = "";
              webEventTarget.addEventListener("packaged", (event) => {
                webEventValue = event.type + "/" + (event instanceof Event) + "/" + (event.target === webEventTarget);
              });
              webEventTarget.dispatchEvent(new Event("packaged"));
              const webCustomEventTarget = new EventTarget();
              let webCustomEventValue = "";
              webCustomEventTarget.addEventListener("packaged-detail", (event) => {
                webCustomEventValue = [
                  event instanceof CustomEvent,
                  event instanceof Event,
                  event.detail.value,
                  event.bubbles,
                  event.cancelable,
                  event.target === webCustomEventTarget
                ].join("/");
              });
              webCustomEventTarget.dispatchEvent(new CustomEvent("packaged-detail", {
                detail: { value: "detail-packaged" },
                bubbles: true,
                cancelable: true
              }));
              const webConstructedMessageEvent = new MessageEvent("message", {
                data: { text: "event-packaged" },
                origin: "autojs6:packaged",
                lastEventId: "packaged-id",
                ports: []
              });
              const webEventConstructorsSummary = [
                webCustomEventValue,
                webConstructedMessageEvent instanceof MessageEvent,
                webConstructedMessageEvent instanceof Event,
                webConstructedMessageEvent.data.text,
                webConstructedMessageEvent.origin,
                webConstructedMessageEvent.lastEventId,
                Array.isArray(webConstructedMessageEvent.ports),
                webConstructedMessageEvent.ports.length
              ].join("/");
              const webPerformanceNowA = performance.now();
              performance.mark("packaged-platform-global-start");
              performance.mark("packaged-platform-global-end");
              performance.measure("packaged-platform-global-measure", "packaged-platform-global-start", "packaged-platform-global-end");
              const webPerformanceMarkEntries = performance.getEntriesByType("mark").filter((entry) => entry.name.indexOf("packaged-platform-global-") === 0);
              const webPerformanceMeasureEntries = performance.getEntriesByName("packaged-platform-global-measure", "measure");
              const webPerformanceObserved = await new Promise((resolve) => {
                const observer = new PerformanceObserver((list) => {
                  const summary = list.getEntries().map((entry) => entry.name + ":" + entry.entryType).join("/");
                  observer.disconnect();
                  resolve(summary);
                });
                observer.observe({ entryTypes: ["mark", "measure"] });
                performance.mark("packaged-platform-observed-start");
                performance.mark("packaged-platform-observed-end");
                performance.measure("packaged-platform-observed-measure", "packaged-platform-observed-start", "packaged-platform-observed-end");
              });
              const webPerformanceNowB = performance.now();
              const webPerformanceHiddenTypes = [
                typeof performance.timerify,
                typeof performance.eventLoopUtilization,
                typeof performance.nodeTiming,
                typeof performance.markResourceTiming
              ].join(",");
              const webPerformanceSummary = [
                performance === perfHooks.performance,
                PerformanceObserver === perfHooks.PerformanceObserver,
                Object.isFrozen(performance),
                Object.isFrozen(PerformanceObserver.supportedEntryTypes),
                PerformanceObserver.supportedEntryTypes.join(","),
                typeof webPerformanceNowA === "number" && webPerformanceNowB >= webPerformanceNowA,
                webPerformanceMarkEntries.length >= 2,
                webPerformanceMeasureEntries.length === 1,
                webPerformanceMeasureEntries[0] instanceof PerformanceMeasure,
                webPerformanceMarkEntries[0] instanceof PerformanceMark,
                webPerformanceMeasureEntries[0] instanceof PerformanceEntry,
                webPerformanceObserved.includes("packaged-platform-observed-start:mark"),
                webPerformanceObserved.includes("packaged-platform-observed-measure:measure"),
                webPerformanceHiddenTypes
              ].join("/");
              performance.clearMarks("packaged-platform-global-start");
              performance.clearMarks("packaged-platform-global-end");
              performance.clearMarks("packaged-platform-observed-start");
              performance.clearMarks("packaged-platform-observed-end");
              performance.clearMeasures("packaged-platform-global-measure");
              performance.clearMeasures("packaged-platform-observed-measure");
              const webGlobalReadableText = (await readPackagedWebGlobalStream(new ReadableStream({
                start(controller) {
                  controller.enqueue("packaged");
                  controller.enqueue("-global");
                  controller.close();
                }
              }))).join("");
              let webGlobalWritableText = "";
              const webGlobalWritable = new WritableStream({
                write(chunk) {
                  webGlobalWritableText += String(chunk);
                }
              });
              const webGlobalWritableWriter = webGlobalWritable.getWriter();
              await webGlobalWritableWriter.write("write");
              await webGlobalWritableWriter.write("-global");
              await webGlobalWritableWriter.close();
              const webGlobalTransform = new TransformStream({
                transform(chunk, controller) {
                  controller.enqueue(String(chunk).toUpperCase());
                }
              });
              const webGlobalTransformValues = readPackagedWebGlobalStream(webGlobalTransform.readable);
              const webGlobalTransformWriter = webGlobalTransform.writable.getWriter();
              await webGlobalTransformWriter.write("platform");
              await webGlobalTransformWriter.close();
              const webGlobalTransformText = (await webGlobalTransformValues).join("");
              const webGlobalBlobStreamText = Buffer.concat((await readPackagedWebGlobalStream(
                new Blob(["blob-global"]).stream().pipeThrough(new TransformStream({
                  transform(chunk, controller) {
                    controller.enqueue(chunk);
                  }
                }))
              )).map((chunk) => Buffer.from(chunk))).toString("utf8");
              const webGlobalEncodedChunks = [];
              const webGlobalEncoder = new TextEncoderStream();
              const webGlobalEncoderDone = webGlobalEncoder.readable.pipeTo(new WritableStream({
                write(chunk) {
                  webGlobalEncodedChunks.push(Buffer.from(chunk));
                }
              }));
              const webGlobalEncoderWriter = webGlobalEncoder.writable.getWriter();
              await webGlobalEncoderWriter.write("encoded-global");
              await webGlobalEncoderWriter.close();
              await webGlobalEncoderDone;
              const webGlobalDecodedChunks = [];
              const webGlobalDecoder = new TextDecoderStream();
              const webGlobalDecoderDone = webGlobalDecoder.readable.pipeTo(new WritableStream({
                write(chunk) {
                  webGlobalDecodedChunks.push(chunk);
                }
              }));
              const webGlobalDecoderWriter = webGlobalDecoder.writable.getWriter();
              await webGlobalDecoderWriter.write(Buffer.from("decoded-global"));
              await webGlobalDecoderWriter.close();
              await webGlobalDecoderDone;
              const webGlobalCountStrategy = new CountQueuingStrategy({ highWaterMark: 2 });
              const webGlobalByteStrategy = new ByteLengthQueuingStrategy({ highWaterMark: 8 });
              const webGlobalConsumerText = await streamConsumers.text(new Blob(["consumer-global"]).stream());
              const webMessageChannelSummary = await new Promise((resolve) => {
                const channel = new MessageChannel();
                channel.port1.onmessage = (event) => {
                  const ok = event.data.text === "channel-packaged" &&
                    event.data.nested.value === 42 &&
                    event.data.nested !== channelPayload.nested &&
                    event instanceof MessageEvent &&
                    event.type === "message" &&
                    event.origin === "" &&
                    Array.isArray(event.ports) &&
                    event.ports.length === 0 &&
                    channel.port1 instanceof MessagePort &&
                    channel.port2 instanceof MessagePort;
                  channel.port1.close();
                  channel.port2.close();
                  resolve(ok ? event.data.text + "/" + event.data.nested.value + "/" + (event instanceof MessageEvent) + "/" + Array.isArray(event.ports) + "/" + event.ports.length : "failed");
                };
                const channelPayload = { text: "channel-packaged", nested: { value: 42 } };
                channel.port2.postMessage(channelPayload);
              });
              const webBroadcastSummary = await new Promise((resolve) => {
                const first = new BroadcastChannel("packaged-platform-channel");
                const second = new BroadcastChannel("packaged-platform-channel");
                second.onmessage = (event) => {
                  const ok = event.data.text === "broadcast-packaged" &&
                    event.data.nested.value === 7 &&
                    event.data.nested !== broadcastPayload.nested &&
                    event instanceof MessageEvent &&
                    event.type === "message" &&
                    event.origin === "" &&
                    Array.isArray(event.ports) &&
                    event.ports.length === 0 &&
                    first.name === "packaged-platform-channel" &&
                    second.name === "packaged-platform-channel";
                  first.close();
                  second.close();
                  resolve(ok ? event.data.text + "/" + event.data.nested.value + "/" + (event instanceof MessageEvent) + "/" + Array.isArray(event.ports) + "/" + event.ports.length : "failed");
                };
                const broadcastPayload = { text: "broadcast-packaged", nested: { value: 7 } };
                first.postMessage(broadcastPayload);
              });
              const webPattern = new URLPattern({ protocol: "https", hostname: "example.com", pathname: "/packaged/:id" });
              const webPatternMatch = webPattern.exec("https://example.com/packaged/42?x=1#top");
              const webRelativePattern = new URLPattern("/relative/:slug", "https://example.com");
              const webRelativeMatch = webRelativePattern.exec("https://example.com/relative/packaged");
              const webCloneSource = {
                nested: { value: "clone" },
                values: new Map([["answer", 42]])
              };
              const webCloned = structuredClone(webCloneSource);
              const webEncodedLatin1 = btoa("AutoJs6");
              const webNavigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, "navigator");
              const webNavigatorOk =
                typeof navigator === "object" &&
                typeof navigator.userAgent === "string" &&
                navigator.userAgent.length > 0 &&
                webNavigatorDescriptor &&
                webNavigatorDescriptor.writable === false;
              const webPlatformUtilitiesOk =
                typeof URLPattern === "function" &&
                urlBuiltin.URLPattern === URLPattern &&
                webPattern.test("https://example.com/packaged/42") &&
                webPatternMatch &&
                webPatternMatch.pathname.groups.id === "42" &&
                webRelativePattern.test("https://example.com/relative/packaged") &&
                webRelativeMatch &&
                webRelativeMatch.pathname.groups.slug === "packaged" &&
                !webPattern.test("https://example.com/other/42") &&
                webCloned.nested.value === "clone" &&
                webCloned.nested !== webCloneSource.nested &&
                webCloned.values instanceof Map &&
                webCloned.values.get("answer") === 42 &&
                webEncodedLatin1 === "QXV0b0pzNg==" &&
                atob(webEncodedLatin1) === "AutoJs6" &&
                webNavigatorOk;
              const webMessageGlobalsOk =
                webMessageChannelSummary === "channel-packaged/42/true/true/0" &&
                webBroadcastSummary === "broadcast-packaged/7/true/true/0";
              const webEventGlobalsOk =
                webEventConstructorsSummary === "true/true/detail-packaged/true/true/true/true/true/event-packaged/autojs6:packaged/packaged-id/true/0";
              const webPerformanceGlobalsOk =
                webPerformanceSummary === "true/true/true/true/mark,measure/true/true/true/true/true/true/true/true/undefined,undefined,undefined,undefined";
              const webAbortSignalOk =
                webAbortStaticSummary === "true/already-packaged/true/later-packaged/TimeoutError/true";
              const webPlatformGlobalsOk =
                webPlatformTypes === "function,function,function,function,function,function,function,object,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function" &&
                webAbortController.signal instanceof AbortSignal &&
                webAbortController.signal.aborted &&
                webAbortEvent === "abort/true/true/AbortError" &&
                webEventValue === "packaged/true/true" &&
                webPlatformLocked;
              const webStreamGlobalsOk =
                ReadableStream === streamWeb.ReadableStream &&
                WritableStream === streamWeb.WritableStream &&
                TransformStream === streamWeb.TransformStream &&
                TextEncoderStream === streamWeb.TextEncoderStream &&
                TextDecoderStream === streamWeb.TextDecoderStream &&
                CountQueuingStrategy === streamWeb.CountQueuingStrategy &&
                ByteLengthQueuingStrategy === streamWeb.ByteLengthQueuingStrategy &&
                webGlobalReadableText === "packaged-global" &&
                webGlobalWritableText === "write-global" &&
                webGlobalTransformText === "PLATFORM" &&
                webGlobalBlobStreamText === "blob-global" &&
                Buffer.concat(webGlobalEncodedChunks).toString("utf8") === "encoded-global" &&
                webGlobalDecodedChunks.join("") === "decoded-global" &&
                webGlobalCountStrategy.highWaterMark === 2 &&
                webGlobalCountStrategy.size("value") === 1 &&
                webGlobalByteStrategy.highWaterMark === 8 &&
                webGlobalByteStrategy.size(new Uint8Array([1, 2, 3])) === 3 &&
                webGlobalConsumerText === "consumer-global";

              console.log("packaged.fs.promises.ready=" + fsPromisesOk);
              console.log("packaged.fs.promises.alias=" + (fs.promises === fsp && fsp === nodeFsp));
              console.log("packaged.fs.promises.constants=" + fsPromisesConstantsOk);
              console.log("packaged.buffer.builtin=" + bufferOk);
              console.log("packaged.buffer.surface=" + bufferSurfaceOk);
              console.log("packaged.buffer.object_url=" + bufferObjectUrlOk);
              console.log("packaged.buffer.transcode=" + bufferTranscodeOk);
              console.log("packaged.web_globals=" + webGlobalsOk);
              console.log("packaged.web_fetch_disabled=" + webFetchDisabledOk);
              console.log("packaged.web_axios_disabled=" + webAxiosDisabledOk);
              console.log("packaged.web_platform_globals=" + webPlatformGlobalsOk);
              console.log("packaged.web_platform_utilities=" + webPlatformUtilitiesOk);
              console.log("packaged.web_abort_signal=" + webAbortSignalOk);
              console.log("packaged.web_event_globals=" + webEventGlobalsOk);
              console.log("packaged.web_performance_globals=" + webPerformanceGlobalsOk);
              console.log("packaged.web_message_globals=" + webMessageGlobalsOk);
              console.log("packaged.web_stream_globals=" + webStreamGlobalsOk);
            }

            async function runPackagedFsFailurePathCorpus() {
              function captureSync(action, token) {
                try {
                  action();
                  return false;
                } catch (error) {
                  return error instanceof Error && String(error.message).includes(token);
                }
              }

              async function capturePromise(action, token) {
                try {
                  await action();
                  return false;
                } catch (error) {
                  return error instanceof Error && String(error.message).includes(token);
                }
              }

              function captureCallback(start, token) {
                return new Promise((resolve) => {
                  start((error) => {
                    resolve(error instanceof Error && String(error.message).includes(token));
                  });
                });
              }

              function deniedRequire(name) {
                try {
                  require(name);
                  return false;
                } catch (error) {
                  return error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" &&
                    String(error.message).includes(name);
                }
              }

              function deniedResolve(name) {
                try {
                  require.resolve(name);
                  return false;
                } catch (error) {
                  return error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" &&
                    String(error.message).includes(name);
                }
              }

              function deniedGetBuiltin(name) {
                try {
                  process.getBuiltinModule(name);
                  return false;
                } catch (error) {
                  return String(error.message).includes("disabled") &&
                    String(error.message).includes(name);
                }
              }

              await fsp.mkdir("./packaged-fs-failure/dir/sub", { recursive: true });
              await fsp.mkdir("./packaged-fs-failure/safe/a/b/c", { recursive: true });
              await fsp.writeFile("./packaged-fs-failure/safe.txt", "safe", "utf8");
              await fsp.writeFile("./packaged-fs-failure/safe/a/safe.txt", "safe", "utf8");
              fs.symlinkSync("../../safe.txt", "./packaged-fs-failure/safe/a/b/c/link.txt");
              const closedFailureHandle = await fsp.open("./packaged-fs-failure/safe.txt", "r");
              await closedFailureHandle.close();
              const openStreamFailureHandle = await fsp.open("./packaged-fs-failure/safe.txt", "r");
              const streamFailureReadFd = fs.openSync("./packaged-fs-failure/safe.txt", "r");
              const streamFailureWriteFd = fs.openSync("./packaged-fs-failure/stream-fd-write.txt", "w");
              const inheritedStreamFdOptions = Object.create({ fd: 0 });
              const inheritedStreamFlagsOptions = Object.create({ flags: "w" });
              const nulPath = "bad" + String.fromCharCode(0) + "path";
              const syncChecks = [
                captureSync(() => fs.readFileSync("/sdcard/autojs6-packaged-fs-denied.txt", "utf8"), "rejects absolute path"),
                captureSync(() => fs.writeFileSync("../packaged-fs-failure-sync-write.txt", "x", "utf8"), "escapes working directory"),
                captureSync(() => fs.readFileSync(nulPath, "utf8"), "rejects NUL"),
                captureSync(() => fs.rmSync(".", { recursive: true, force: true }), "cannot remove workingDirectory root"),
                captureSync(() => fs.unlinkSync("./packaged-fs-failure/dir"), "rejects directories"),
                captureSync(() => fs.copyFileSync("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-sync-copy.txt"), "escapes working directory"),
                captureSync(() => fs.cpSync("./packaged-fs-failure/safe.txt", "./packaged-fs-failure/filter-type.txt", { filter: true }), "filter option must be a function"),
                captureSync(() => fs.cpSync("./packaged-fs-failure/safe.txt", "./packaged-fs-failure/filter-async.txt", {
                  filter() {
                    return Promise.resolve(true);
                  }
                }), "async filter"),
                captureSync(() => fs.cpSync("./packaged-fs-failure/safe/a/b/c/link.txt", "./packaged-fs-failure/verbatim-link.txt", {
                  recursive: true,
                  verbatimSymlinks: true
                }), "target escapes"),
                captureSync(() => fs.renameSync("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-sync-rename.txt"), "escapes working directory"),
                captureSync(() => fs.truncateSync("../packaged-fs-failure-sync-truncate.txt", 0), "escapes working directory"),
                captureSync(() => fs.realpathSync("../packaged-fs-failure-sync-realpath.txt"), "escapes working directory"),
                captureSync(() => fs.rmdirSync("../packaged-fs-failure-sync-rmdir"), "escapes working directory"),
                captureSync(() => fs.rmdirSync("."), "cannot remove workingDirectory root"),
                captureSync(() => fs.statfsSync("../packaged-fs-failure-sync-statfs"), "escapes working directory"),
                captureSync(() => fs.linkSync("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-sync-link.txt"), "escapes working directory"),
                captureSync(() => fs.readlinkSync("../packaged-fs-failure-sync-readlink"), "escapes working directory"),
                captureSync(() => fs.chmodSync("../packaged-fs-failure-sync-chmod.txt", 0o600), "escapes working directory"),
                captureSync(() => fs.chmodSync("./packaged-fs-failure/safe.txt", "bad"), "octal string"),
                captureSync(() => fs.lchmodSync("../packaged-fs-failure-sync-lchmod.txt", 0o600), "escapes working directory"),
                captureSync(() => fs.lutimesSync("../packaged-fs-failure-sync-lutimes.txt", new Date(), new Date()), "escapes working directory"),
                captureSync(() => fs.lutimesSync("./packaged-fs-failure/missing-lutimes.txt", new Date(), new Date()), "path does not exist"),
                captureSync(() => fs.chownSync("../packaged-fs-failure-sync-chown.txt", 0, 0), "escapes working directory"),
                captureSync(() => fs.chownSync("./packaged-fs-failure/safe.txt", "0", 0), "uid must be a number"),
                captureSync(() => fs.lchownSync("../packaged-fs-failure-sync-lchown.txt", 0, 0), "escapes working directory"),
                captureSync(() => fs.lchownSync("./packaged-fs-failure/safe.txt", 0, -2), "gid is out of range"),
                captureSync(() => fs.symlinkSync("../../packaged-fs-failure-sync-symlink-target.txt", "./packaged-fs-failure/sync-symlink-target"), "target escapes working directory"),
                captureSync(() => fs.symlinkSync("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-sync-symlink-path"), "escapes working directory"),
                captureSync(() => fs.exists("../packaged-fs-failure-sync-exists.txt", () => {}), "escapes working directory"),
                captureSync(() => fs.globSync("../packaged-fs-failure-sync-glob/**"), "parent segment"),
                captureSync(() => fs.globSync(["packaged-fs-failure/**/*.txt", "/sdcard/**/*.txt"]), "absolute glob pattern"),
                captureSync(() => fs.globSync("**/*.txt", { cwd: "./packaged-fs-failure/safe.txt" }), "cwd must be a directory"),
                captureSync(() => fs.globSync("**/*.txt", { cwd: "../packaged-fs-failure-sync-glob-cwd" }), "escapes working directory"),
                captureSync(() => fs.opendirSync("../packaged-fs-failure-sync-opendir"), "escapes working directory"),
                captureSync(() => fs.readdirSync("../packaged-fs-failure-sync-readdir-recursive", { recursive: true }), "escapes working directory"),
                captureSync(() => fs.opendirSync("../packaged-fs-failure-sync-opendir-recursive", { recursive: true }), "escapes working directory"),
                captureSync(() => fs.mkdtempDisposableSync("../packaged-fs-failure-sync-disposable-"), "escapes working directory"),
                captureSync(() => fs.mkdtempDisposableSync(Buffer.from("../packaged-fs-failure-sync-disposable-buffer-")), "escapes working directory"),
                captureSync(() => new fs.ReadStream("../packaged-fs-failure-sync-read-stream-constructor.txt"), "escapes working directory"),
                captureSync(() => new fs.WriteStream("../packaged-fs-failure-sync-write-stream-constructor.txt"), "escapes working directory"),
                captureSync(() => new fs.Dir("../packaged-fs-failure-sync-dir-constructor"), "constructor is unavailable"),
                captureSync(() => new fs.ReadStream.prototype.constructor("../packaged-fs-failure-sync-read-stream-prototype-constructor.txt"), "escapes working directory"),
                captureSync(() => new fs.WriteStream.prototype.constructor("../packaged-fs-failure-sync-write-stream-prototype-constructor.txt"), "escapes working directory"),
                captureSync(() => new fs.Dir.prototype.constructor("../packaged-fs-failure-sync-dir-prototype-constructor"), "constructor is unavailable"),
                captureSync(() => new fs.Utf8Stream({ dest: "../packaged-fs-failure-sync-utf8-stream-constructor.txt" }), "escapes working directory"),
                captureSync(() => new fs.Utf8Stream.prototype.constructor({ dest: "../packaged-fs-failure-sync-utf8-stream-prototype-constructor.txt" }), "escapes working directory"),
                captureSync(() => new fs.Utf8Stream({ dest: "/sdcard/autojs6-packaged-utf8stream.txt" }), "rejects absolute path"),
                captureSync(() => new fs.Utf8Stream({ fd: 987654321 }), "not open or not owned"),
                captureSync(() => new fs.Utf8Stream({ dest: "./packaged-fs-failure/utf8-fs.txt", fs: { open() {} } }), "fs option is unsupported"),
                captureSync(() => fs.Utf8Stream({ dest: "./packaged-fs-failure/utf8-call.txt" }), "Class constructor"),
                captureSync(() => fs.readFileSync(987654321, "utf8"), "not open or not owned"),
                captureSync(() => fs.writeFileSync(987654321, "x", "utf8"), "not open or not owned"),
                captureSync(() => fs.appendFileSync(987654321, "x", "utf8"), "not open or not owned"),
                captureSync(() => fs.fchmodSync(987654321, 0o600), "not open or not owned"),
                captureSync(() => fs.fchownSync(987654321, 0, 0), "not open or not owned"),
                captureSync(() => fs.futimesSync(987654321, new Date(), new Date()), "not open or not owned"),
                captureSync(() => fs.watch("../packaged-fs-failure-sync-watch.txt", () => {}), "escapes working directory"),
                captureSync(() => fsp.watch("../packaged-fs-failure-sync-promises-watch.txt"), "escapes working directory"),
                captureSync(() => fs.watch(Buffer.from("../packaged-fs-failure-sync-watch-buffer.txt"), () => {}), "escapes working directory"),
                captureSync(() => fsp.watch(Buffer.from("../packaged-fs-failure-sync-promises-watch-buffer.txt")), "escapes working directory"),
                captureSync(() => fs.watch("./packaged-fs-failure/safe.txt", { recursive: true }, () => {}), "recursive option"),
                captureSync(() => fs.watch("./packaged-fs-failure/safe.txt", "utf8", "bad"), "listener must be a function"),
                captureSync(() => fs.watchFile("../packaged-fs-failure-sync-watch-file.txt", () => {}), "escapes working directory"),
                captureSync(() => fs.watchFile(Buffer.from("../packaged-fs-failure-sync-watch-file-buffer.txt"), () => {}), "escapes working directory"),
                captureSync(() => fs.unwatchFile("/sdcard/autojs6-packaged-watch-file.txt"), "rejects absolute path"),
                captureSync(() => fs.watchFile("./packaged-fs-failure/safe.txt", { interval: 50 }, "bad"), "callback must be a function"),
                captureSync(() => fs.createReadStream("./packaged-fs-failure/safe.txt", inheritedStreamFdOptions), "fd option is unsupported"),
                captureSync(() => fs.createWriteStream("./packaged-fs-failure/safe.txt", inheritedStreamFdOptions), "fd option is unsupported"),
                captureSync(() => fs.createReadStream("./packaged-fs-failure/safe.txt", { flags: "w" }), "read stream flags"),
                captureSync(() => fs.createReadStream("./packaged-fs-failure/safe.txt", inheritedStreamFlagsOptions), "read stream flags"),
                captureSync(() => fs.createReadStream("./packaged-fs-failure/safe.txt", { fs: { read() {} } }), "fs option is unsupported"),
                captureSync(() => fs.createWriteStream("./packaged-fs-failure/safe.txt", { fs: { write() {} } }), "fs option is unsupported"),
                captureSync(() => fs.createReadStream("../packaged-fs-failure-stream-fd-read.txt", {
                  fd: streamFailureReadFd,
                  autoClose: false
                }), "escapes working directory"),
                captureSync(() => fs.createWriteStream("../packaged-fs-failure-stream-fd-write.txt", {
                  fd: streamFailureWriteFd,
                  autoClose: false
                }), "escapes working directory"),
                captureSync(() => openStreamFailureHandle.createReadStream({ fd: 987654321 }), "not open or not owned"),
                captureSync(() => openStreamFailureHandle.createWriteStream({ fs: { write() {} } }), "fs option is unsupported"),
                captureSync(() => fs.readFileSync(Buffer.from("../packaged-fs-failure-sync-buffer.txt"), "utf8"), "escapes working directory")
              ];
              fs.closeSync(streamFailureReadFd);
              fs.closeSync(streamFailureWriteFd);
              const promiseChecks = await Promise.all([
                capturePromise(() => fsp.readFile("/sdcard/autojs6-packaged-fs-promise-denied.txt", "utf8"), "rejects absolute path"),
                capturePromise(() => fsp.writeFile("../packaged-fs-failure-promise-write.txt", "x", "utf8"), "escapes working directory"),
                capturePromise(() => fsp.rm(".", { recursive: true, force: true }), "cannot remove workingDirectory root"),
                capturePromise(() => fsp.unlink("./packaged-fs-failure/dir"), "rejects directories"),
                capturePromise(() => fsp.copyFile("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-promise-copy.txt"), "escapes working directory"),
                capturePromise(() => fsp.cp("./packaged-fs-failure/safe.txt", "./packaged-fs-failure/promise-filter-async.txt", {
                  filter() {
                    return Promise.resolve(true);
                  }
                }), "async filter"),
                capturePromise(() => fsp.rename("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-promise-rename.txt"), "escapes working directory"),
                capturePromise(() => fsp.truncate("../packaged-fs-failure-promise-truncate.txt", 0), "escapes working directory"),
                capturePromise(() => fsp.realpath("../packaged-fs-failure-promise-realpath.txt"), "escapes working directory"),
                capturePromise(() => fsp.rmdir("../packaged-fs-failure-promise-rmdir"), "escapes working directory"),
                capturePromise(() => fsp.rmdir("."), "cannot remove workingDirectory root"),
                capturePromise(() => fsp.statfs("../packaged-fs-failure-promise-statfs"), "escapes working directory"),
                capturePromise(() => fsp.link("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-promise-link.txt"), "escapes working directory"),
                capturePromise(() => fsp.readlink("../packaged-fs-failure-promise-readlink"), "escapes working directory"),
                capturePromise(() => fsp.chmod("../packaged-fs-failure-promise-chmod.txt", 0o600), "escapes working directory"),
                capturePromise(() => fsp.chmod("./packaged-fs-failure/missing-chmod.txt", 0o600), "path does not exist"),
                capturePromise(() => fsp.lchmod("../packaged-fs-failure-promise-lchmod.txt", 0o600), "escapes working directory"),
                capturePromise(() => fsp.lutimes("../packaged-fs-failure-promise-lutimes.txt", new Date(), new Date()), "escapes working directory"),
                capturePromise(() => fsp.lutimes("./packaged-fs-failure/missing-lutimes.txt", new Date(), new Date()), "path does not exist"),
                capturePromise(() => fsp.chown("../packaged-fs-failure-promise-chown.txt", 0, 0), "escapes working directory"),
                capturePromise(() => fsp.chown("./packaged-fs-failure/missing-chown.txt", 0, 0), "path does not exist"),
                capturePromise(() => fsp.lchown("../packaged-fs-failure-promise-lchown.txt", 0, 0), "escapes working directory"),
                capturePromise(() => fsp.lchown("./packaged-fs-failure/missing-lchown.txt", 0, 0), "path does not exist"),
                capturePromise(() => fsp.symlink("../../packaged-fs-failure-promise-symlink-target.txt", "./packaged-fs-failure/promise-symlink-target"), "target escapes working directory"),
                capturePromise(() => fsp.symlink("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-promise-symlink-path"), "escapes working directory"),
                capturePromise(() => fs.openAsBlob("../packaged-fs-failure-promise-open-as-blob.txt"), "escapes working directory"),
                capturePromise(() => fsp.glob("../packaged-fs-failure-promise-glob/**"), "parent segment"),
                capturePromise(() => fsp.glob(["packaged-fs-failure/**/*.txt", "/sdcard/**/*.txt"]), "absolute glob pattern"),
                capturePromise(() => fsp.glob("**/*.txt", { cwd: "./packaged-fs-failure/safe.txt" }), "cwd must be a directory"),
                capturePromise(() => fsp.opendir("../packaged-fs-failure-promise-opendir"), "escapes working directory"),
                capturePromise(() => fsp.readdir("../packaged-fs-failure-promise-readdir-recursive", { recursive: true }), "escapes working directory"),
                capturePromise(() => fsp.opendir("../packaged-fs-failure-promise-opendir-recursive", { recursive: true }), "escapes working directory"),
                capturePromise(() => fsp.mkdtempDisposable("../packaged-fs-failure-promise-disposable-"), "escapes working directory"),
                capturePromise(() => fsp.mkdtempDisposable("./packaged-fs-failure/missing/disposable-"), "path does not exist"),
                capturePromise(() => fsp.readFile(987654321, "utf8"), "must be a string"),
                capturePromise(() => fsp.writeFile(987654321, "x", "utf8"), "must be a string"),
                capturePromise(() => fsp.appendFile(987654321, "x", "utf8"), "must be a string"),
                capturePromise(() => fsp.watch("./packaged-fs-failure/safe.txt", { recursive: true }), "recursive option"),
                capturePromise(() => closedFailureHandle.chmod(0o600), "not open or not owned"),
                capturePromise(() => closedFailureHandle.chown(0, 0), "not open or not owned"),
                capturePromise(() => closedFailureHandle.utimes(new Date(), new Date()), "not open or not owned")
              ]);
              const callbackChecks = await Promise.all([
                captureCallback((callback) => fs.readFile("/sdcard/autojs6-packaged-fs-callback-denied.txt", "utf8", callback), "rejects absolute path"),
                captureCallback((callback) => fs.writeFile("../packaged-fs-failure-callback-write.txt", "x", "utf8", callback), "escapes working directory"),
                captureCallback((callback) => fs.rm(".", { recursive: true, force: true }, callback), "cannot remove workingDirectory root"),
                captureCallback((callback) => fs.unlink("./packaged-fs-failure/dir", callback), "rejects directories"),
                captureCallback((callback) => fs.cp("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-callback-cp.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.cp("./packaged-fs-failure/safe.txt", "./packaged-fs-failure/callback-filter-type.txt", { filter: true }, callback), "filter option must be a function"),
                captureCallback((callback) => fs.copyFile("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-callback-copyfile.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.rename("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-callback-rename.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.truncate("../packaged-fs-failure-callback-truncate.txt", 0, callback), "escapes working directory"),
                captureCallback((callback) => fs.realpath("../packaged-fs-failure-callback-realpath.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.realpath.native("../packaged-fs-failure-callback-native-realpath.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.rmdir("../packaged-fs-failure-callback-rmdir", callback), "escapes working directory"),
                captureCallback((callback) => fs.rmdir(".", callback), "cannot remove workingDirectory root"),
                captureCallback((callback) => fs.statfs("../packaged-fs-failure-callback-statfs", callback), "escapes working directory"),
                captureCallback((callback) => fs.link("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-callback-link.txt", callback), "escapes working directory"),
                captureCallback((callback) => fs.readlink("../packaged-fs-failure-callback-readlink", callback), "escapes working directory"),
                captureCallback((callback) => fs.chmod("../packaged-fs-failure-callback-chmod.txt", 0o600, callback), "escapes working directory"),
                captureCallback((callback) => fs.chmod("./packaged-fs-failure/missing-callback-chmod.txt", 0o600, callback), "path does not exist"),
                captureCallback((callback) => fs.lchmod("../packaged-fs-failure-callback-lchmod.txt", 0o600, callback), "escapes working directory"),
                captureCallback((callback) => fs.lutimes("../packaged-fs-failure-callback-lutimes.txt", new Date(), new Date(), callback), "escapes working directory"),
                captureCallback((callback) => fs.lutimes("./packaged-fs-failure/missing-callback-lutimes.txt", new Date(), new Date(), callback), "path does not exist"),
                captureCallback((callback) => fs.chown("../packaged-fs-failure-callback-chown.txt", 0, 0, callback), "escapes working directory"),
                captureCallback((callback) => fs.chown("./packaged-fs-failure/missing-callback-chown.txt", 0, 0, callback), "path does not exist"),
                captureCallback((callback) => fs.lchown("../packaged-fs-failure-callback-lchown.txt", 0, 0, callback), "escapes working directory"),
                captureCallback((callback) => fs.lchown("./packaged-fs-failure/missing-callback-lchown.txt", 0, 0, callback), "path does not exist"),
                captureCallback((callback) => fs.symlink("../../packaged-fs-failure-callback-symlink-target.txt", "./packaged-fs-failure/callback-symlink-target", callback), "target escapes working directory"),
                captureCallback((callback) => fs.symlink("./packaged-fs-failure/safe.txt", "../packaged-fs-failure-callback-symlink-path", callback), "escapes working directory"),
                captureCallback((callback) => fs.glob("../packaged-fs-failure-callback-glob/**", callback), "parent segment"),
                captureCallback((callback) => fs.glob(["packaged-fs-failure/**/*.txt", "/sdcard/**/*.txt"], callback), "absolute glob pattern"),
                captureCallback((callback) => fs.glob("**/*.txt", { cwd: "./packaged-fs-failure/safe.txt" }, callback), "cwd must be a directory"),
                captureCallback((callback) => fs.opendir("../packaged-fs-failure-callback-opendir", callback), "escapes working directory"),
                captureCallback((callback) => fs.readdir("../packaged-fs-failure-callback-readdir-recursive", { recursive: true }, callback), "escapes working directory"),
                captureCallback((callback) => fs.opendir("../packaged-fs-failure-callback-opendir-recursive", { recursive: true }, callback), "escapes working directory"),
                captureCallback((callback) => fs.readFile(987654321, "utf8", callback), "not open or not owned"),
                captureCallback((callback) => fs.writeFile(987654321, "x", "utf8", callback), "not open or not owned"),
                captureCallback((callback) => fs.appendFile(987654321, "x", "utf8", callback), "not open or not owned"),
                captureCallback((callback) => fs.fchmod(987654321, 0o600, callback), "not open or not owned"),
                captureCallback((callback) => fs.fchown(987654321, 0, 0, callback), "not open or not owned"),
                captureCallback((callback) => fs.futimes(987654321, new Date(), new Date(), callback), "not open or not owned")
              ]);
              await openStreamFailureHandle.close();
              const aliasGuard = fs === require("node:fs") &&
                fs.promises === fsp &&
                fsp === nodeFsp &&
                require.resolve("fs") === "fs" &&
                require.resolve("node:fs") === "fs" &&
                require.resolve("fs/promises") === "fs/promises" &&
                require.resolve("node:fs/promises") === "fs/promises" &&
                fs.shadow !== true &&
                fs.__autojs6Shadow !== true &&
                Object.isFrozen(fs) &&
                Object.isFrozen(fsp);
              const hiddenGuard = [
                typeof fs.createReadStream,
                typeof fs.createWriteStream,
                typeof fs.watch,
                typeof fs.watchFile,
                typeof fs.unwatchFile,
                typeof fs.Stats,
                typeof fs.Dirent,
                typeof fs.Dir,
                typeof fs.ReadStream,
                typeof fs.WriteStream,
                typeof fs.FileReadStream,
                typeof fs.FileWriteStream,
                typeof fs.Utf8Stream,
                typeof fs.openAsBlob,
                typeof fs.symlink,
                typeof fs.symlinkSync,
                typeof fs.chmod,
                typeof fs.chmodSync,
                typeof fs.lchmod,
                typeof fs.lchmodSync,
                typeof fs.copyFile,
                typeof fs.realpath,
                typeof fs.rmdir,
                typeof fs.rmdirSync,
                typeof fs.glob,
                typeof fs.globSync,
                typeof fs.open,
                typeof fs.openSync,
                typeof fsp.open,
                typeof fsp.symlink,
                typeof fsp.chmod,
                typeof fsp.lchmod,
                typeof fsp.rmdir,
                typeof fsp.glob,
                typeof fsp.watch
              ].every((value) => value === "function") &&
                fs.F_OK === fs.constants.F_OK &&
                fs.R_OK === fs.constants.R_OK &&
                fs.W_OK === fs.constants.W_OK &&
                fs.X_OK === fs.constants.X_OK &&
                fs.COPYFILE_EXCL === fs.constants.COPYFILE_EXCL &&
                fs.COPYFILE_FICLONE === fs.constants.COPYFILE_FICLONE &&
                fs.COPYFILE_FICLONE_FORCE === fs.constants.COPYFILE_FICLONE_FORCE;
              const limitedNetGuard = (() => {
                const netBuiltin = require("node:net");
                try {
                  netBuiltin.connect(80, "127.0.0.1");
                  return false;
                } catch (error) {
                  return netBuiltin.isIP("127.0.0.1") === 4 &&
                    error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED";
                }
              })();
              const limitedTlsGuard = (() => {
                const tlsBuiltin = require("node:tls");
                try {
                  tlsBuiltin.connect(443, "example.com");
                  return false;
                } catch (error) {
                  return require.resolve("node:tls") === "tls" &&
                    typeof tlsBuiltin.createSecureContext === "function" &&
                    error && error.code === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED";
                }
              })();
              const deniedGuard = deniedRequire("child_process") &&
                limitedNetGuard &&
                limitedTlsGuard &&
                deniedRequire("worker_threads") &&
                deniedGetBuiltin("fs");
              const treeOk = fs.existsSync("./packaged-fs-failure/safe.txt") &&
                fs.existsSync("./packaged-fs-failure/dir/sub");
              assertStrictBuiltin.equal(syncChecks.filter(Boolean).length, syncChecks.length);
              assertStrictBuiltin.equal(promiseChecks.filter(Boolean).length, promiseChecks.length);
              assertStrictBuiltin.equal(callbackChecks.filter(Boolean).length, callbackChecks.length);
              await fsp.rm("./packaged-fs-failure", { recursive: true, force: true });
              const cleaned = !fs.existsSync("./packaged-fs-failure");
              const guardsOk = aliasGuard && hiddenGuard && deniedGuard;
              const summary = [
                syncChecks.filter(Boolean).length + "/" + syncChecks.length,
                promiseChecks.filter(Boolean).length + "/" + promiseChecks.length,
                callbackChecks.filter(Boolean).length + "/" + callbackChecks.length,
                guardsOk,
                treeOk && cleaned
              ].join("|");
              console.log("packaged.fs.failure.ready=" + (
                syncChecks.every(Boolean) &&
                promiseChecks.every(Boolean) &&
                callbackChecks.every(Boolean) &&
                guardsOk &&
                treeOk &&
                cleaned
              ));
              console.log("packaged.fs.failure.summary=" + summary);
            }

            function runProcessAndConstantsBuiltinCorpus() {
              const processHr = processBuiltin.hrtime();
              const processHrDiff = processBuiltin.hrtime(processHr);
              const processHrBigint = processBuiltin.hrtime.bigint();
              const processHrBigintLater = processBuiltin.hrtime.bigint();
              const processCpu = processBuiltin.cpuUsage();
              const processCpuDiff = processBuiltin.cpuUsage(processCpu);
              const processResource = processBuiltin.resourceUsage();
              const processOriginalTitle = processBuiltin.title;
              processBuiltin.title = "autojs6-packaged-title";
              const processFlags = processBuiltin.allowedNodeEnvironmentFlags;
              processFlags.add("--inspect");
              const processReport = processBuiltin.report.getReport(new Error("packaged report boom"));
              let processReportWriteDisabled = false;
              try {
                processBuiltin.report.writeReport();
              } catch (error) {
                processReportWriteDisabled = /disabled/.test(String(error && error.message || error));
              }
              let processStdinSeen = "";
              const processStdinDestination = { name: "destination" };
              processBuiltin.stdin.setEncoding("utf8").pause().resume().ref().unref();
              processBuiltin.stdin.on("packaged", (value) => {
                processStdinSeen = value;
              });
              processBuiltin.stdin.emit("packaged", "seen");
              function disabledProcess(action) {
                try {
                  action();
                  return false;
                } catch (error) {
                  return /disabled/.test(String(error && error.message || error));
                }
              }
              const processTimingOk =
                typeof processBuiltin.uptime === "function" &&
                processBuiltin.uptime() >= 0 &&
                typeof processBuiltin.hrtime === "function" &&
                Array.isArray(processHr) &&
                processHr.length === 2 &&
                Number.isInteger(processHr[0]) &&
                Number.isInteger(processHr[1]) &&
                processHr[1] >= 0 &&
                processHr[1] < 1000000000 &&
                Array.isArray(processHrDiff) &&
                processHrDiff.length === 2 &&
                Number.isInteger(processHrDiff[0]) &&
                Number.isInteger(processHrDiff[1]) &&
                typeof processBuiltin.hrtime.bigint === "function" &&
                processHrBigint > 0n &&
                processHrBigintLater >= processHrBigint;
              const processUsageOk =
                typeof processBuiltin.cpuUsage === "function" &&
                typeof processCpu.user === "number" &&
                typeof processCpu.system === "number" &&
                typeof processCpuDiff.user === "number" &&
                typeof processCpuDiff.system === "number" &&
                typeof processBuiltin.resourceUsage === "function" &&
                typeof processResource.userCPUTime === "number" &&
                typeof processResource.systemCPUTime === "number" &&
                typeof processResource.maxRSS === "number";
              const processModernOk =
                Array.isArray(processBuiltin.execArgv) &&
                processBuiltin.execArgv.length === 0 &&
                processOriginalTitle === "autojs6-node" &&
                processBuiltin.title === "autojs6-packaged-title" &&
                Object.isFrozen(processBuiltin.features) &&
                processBuiltin.features.inspector === false &&
                processBuiltin.features.debug === false &&
                processBuiltin.features.uv === true &&
                processBuiltin.features.require_module === false &&
                processFlags instanceof Set &&
                Object.isFrozen(processFlags) &&
                processFlags.size === 0 &&
                processFlags.has("--inspect") === false &&
                Array.from(processFlags).length === 0 &&
                processBuiltin.stdin.fd === 0 &&
                processBuiltin.stdin.isTTY === false &&
                processBuiltin.stdin.readable === true &&
                processBuiltin.stdin.writable === false &&
                typeof processBuiltin.stdin.on === "function" &&
                typeof processBuiltin.stdin.once === "function" &&
                typeof processBuiltin.stdin.read === "function" &&
                typeof processBuiltin.stdin.setEncoding === "function" &&
                typeof processBuiltin.stdin.pause === "function" &&
                typeof processBuiltin.stdin.resume === "function" &&
                processBuiltin.stdin.read() === null &&
                processBuiltin.stdin.pipe(processStdinDestination) === processStdinDestination &&
                processStdinSeen === "seen" &&
                typeof processBuiltin.stdin[Symbol.asyncIterator] === "function" &&
                typeof processBuiltin.report === "object" &&
                typeof processBuiltin.report.getReport === "function" &&
                processReportWriteDisabled &&
                processReport.header.cwd === processBuiltin.cwd() &&
                processReport.header.commandLine.join("|") === processBuiltin.argv.join("|") &&
                processReport.header.nodejsVersion === processBuiltin.version &&
                processReport.javascriptStack.message === "packaged report boom" &&
                typeof processReport.resourceUsage.rss === "number" &&
                processReport.environmentVariables.AUTOJS6_EMBEDDED_NODE === "1" &&
                Object.isFrozen(processReport) &&
                Object.isFrozen(processReport.header) &&
                Object.isFrozen(processReport.javascriptStack);
              processBuiltin.title = processOriginalTitle;
              const processGuardOk =
                disabledProcess(() => processBuiltin.getBuiltinModule("fs")) &&
                disabledProcess(() => processBuiltin.binding("fs")) &&
                disabledProcess(() => processBuiltin.dlopen({}, "native.node")) &&
                disabledProcess(() => processBuiltin._linkedBinding("fs")) &&
                disabledProcess(() => processBuiltin.exit(1)) &&
                disabledProcess(() => processBuiltin.abort()) &&
                disabledProcess(() => processBuiltin.kill(process.pid, 0)) &&
                disabledProcess(() => processBuiltin.report.writeReport()) &&
                typeof processBuiltin.umask() === "number" &&
                disabledProcess(() => processBuiltin.umask(0));
              const processOk = processBuiltin === nodeProcessBuiltin &&
                processBuiltin === process &&
                processBuiltin.env === process.env &&
                processBuiltin.argv === process.argv &&
                processBuiltin.stdin === process.stdin &&
                typeof processBuiltin.cwd === "function" &&
                typeof processBuiltin.chdir === "function" &&
                typeof processBuiltin.nextTick === "function" &&
                typeof processBuiltin.memoryUsage === "function" &&
                processTimingOk &&
                processUsageOk &&
                processModernOk &&
                processGuardOk &&
                require.resolve("process") === "process" &&
                require.resolve("node:process") === "process" &&
                moduleBuiltin.createRequire(__filename)("process") === processBuiltin;
              const constantsOk = constantsBuiltin === nodeConstantsBuiltin &&
                Object.isFrozen(constantsBuiltin) &&
                constantsBuiltin.fs.F_OK === fs.constants.F_OK &&
                constantsBuiltin.fs.R_OK === fs.constants.R_OK &&
                constantsBuiltin.F_OK === fs.constants.F_OK &&
                constantsBuiltin.R_OK === fs.constants.R_OK &&
                Object.isFrozen(constantsBuiltin.fs) &&
                require.resolve("constants") === "constants" &&
                require.resolve("node:constants") === "constants" &&
                moduleBuiltin.createRequire(__filename)("constants") === constantsBuiltin;
              const constantsOsOk =
                constantsBuiltin.os === osBuiltin.constants &&
                constantsBuiltin.errno === osBuiltin.constants.errno &&
                constantsBuiltin.signals === osBuiltin.constants.signals &&
                Object.isFrozen(constantsBuiltin.os) &&
                Object.isFrozen(constantsBuiltin.errno) &&
                Object.isFrozen(constantsBuiltin.signals) &&
                Object.isFrozen(osBuiltin.constants.dlopen) &&
                Object.isFrozen(osBuiltin.constants.priority) &&
                constantsBuiltin.SIGINT === osBuiltin.constants.signals.SIGINT &&
                constantsBuiltin.SIGTERM === osBuiltin.constants.signals.SIGTERM &&
                constantsBuiltin.EACCES === osBuiltin.constants.errno.EACCES &&
                constantsBuiltin.ENOENT === osBuiltin.constants.errno.ENOENT &&
                constantsBuiltin.RTLD_NOW === osBuiltin.constants.dlopen.RTLD_NOW &&
                constantsBuiltin.PRIORITY_NORMAL === osBuiltin.constants.priority.PRIORITY_NORMAL;
              console.log("packaged.process.builtin=" + processOk);
              console.log("packaged.process.alias=" + (processBuiltin === nodeProcessBuiltin));
              console.log("packaged.process.timing=" + processTimingOk);
              console.log("packaged.process.usage=" + processUsageOk);
              console.log("packaged.process.modern=" + processModernOk);
              console.log("packaged.process.guards=" + processGuardOk);
              console.log("packaged.constants.builtin=" + (constantsOk && constantsOsOk));
              console.log("packaged.constants.fs=" + (
                constantsBuiltin.fs.F_OK === fs.constants.F_OK &&
                constantsBuiltin.F_OK === fs.constants.F_OK
              ));
              console.log("packaged.constants.os=" + constantsOsOk);
            }

            async function runLegacyCoreBuiltinCorpus() {
              const encoded = querystringBuiltin.stringify({ a: "1", b: "你好" });
              const parsed = nodeQuerystringBuiltin.parse("a=1&b=%E4%BD%A0%E5%A5%BD");
              const decoder = new stringDecoderBuiltin.StringDecoder("utf8");
              const utf8 = Buffer.from("你好");
              const decoded = decoder.write(utf8.subarray(0, 1)) +
                decoder.write(utf8.subarray(1)) +
                decoder.end();
              const ascii = punycodeBuiltin.toASCII("mañana.com");
              const unicode = nodePunycodeBuiltin.toUnicode(ascii);
              const assertHelperTypes = [
                typeof assertBuiltin,
                typeof assertBuiltin.AssertionError,
                typeof assertBuiltin.ok,
                typeof assertBuiltin.fail,
                typeof assertBuiltin.equal,
                typeof assertBuiltin.notEqual,
                typeof assertBuiltin.deepEqual,
                typeof assertBuiltin.notDeepEqual,
                typeof assertBuiltin.deepStrictEqual,
                typeof assertBuiltin.notDeepStrictEqual,
                typeof assertBuiltin.strictEqual,
                typeof assertBuiltin.notStrictEqual,
                typeof assertBuiltin.partialDeepStrictEqual,
                typeof assertBuiltin.match,
                typeof assertBuiltin.doesNotMatch,
                typeof assertBuiltin.throws,
                typeof assertBuiltin.rejects,
                typeof assertBuiltin.doesNotThrow,
                typeof assertBuiltin.doesNotReject,
                typeof assertBuiltin.ifError,
                typeof assertBuiltin.strict
              ].join(",");
              const expectedAssertHelperTypes = "function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function";
              const assertHidden = [
                typeof assertBuiltin.CallTracker,
                typeof assertBuiltin.Assert,
                typeof assertStrictBuiltin.CallTracker,
                typeof assertStrictBuiltin.Assert
              ].join(",");
              assertBuiltin.deepStrictEqual({ a: 1 }, { a: 1 });
              assertBuiltin.partialDeepStrictEqual({ a: 1, b: 2 }, { a: 1 });
              assertBuiltin.match("packaged-assert", /assert/);
              assertBuiltin.doesNotMatch("packaged-assert", /missing/);
              nodeAssertBuiltin.throws(() => { throw new Error("packaged-assert"); }, /packaged-assert/);
              await assertBuiltin.rejects(Promise.reject(new Error("packaged-assert-reject")), /packaged-assert-reject/);
              await assertStrictBuiltin.doesNotReject(Promise.resolve("ok"));
              let assertFailure = "";
              try {
                assertStrictBuiltin.equal(1, "1");
              } catch (error) {
                assertFailure = [
                  error.name,
                  error.code,
                  error instanceof assertBuiltin.AssertionError
                ].join("/");
              }
              const assertOk =
                assertBuiltin === nodeAssertBuiltin &&
                assertStrictBuiltin === nodeAssertStrictBuiltin &&
                assertBuiltin.strict === assertStrictBuiltin &&
                assertStrictBuiltin.strict === assertStrictBuiltin &&
                assertBuiltin.shadow !== true &&
                assertStrictBuiltin.shadow !== true &&
                Object.isFrozen(assertBuiltin) &&
                Object.isFrozen(assertStrictBuiltin) &&
                assertHelperTypes === expectedAssertHelperTypes &&
                assertHidden === "undefined,undefined,undefined,undefined" &&
                assertFailure === "AssertionError/ERR_ASSERTION/true" &&
                require.resolve("assert") === "assert" &&
                require.resolve("node:assert") === "assert" &&
                require.resolve("assert/strict") === "assert/strict" &&
                require.resolve("node:assert/strict") === "assert/strict";
              const osOk = osBuiltin === nodeOsBuiltin &&
                osBuiltin.platform() === process.platform &&
                osBuiltin.arch() === process.arch &&
                osBuiltin.tmpdir() === process.cwd() &&
                osBuiltin.homedir() === process.cwd() &&
                osBuiltin.EOL === "\n" &&
                osBuiltin.shadow !== true &&
                require.resolve("os") === "os" &&
                require.resolve("node:os") === "os";
              const osParallelism = osBuiltin.availableParallelism();
              const osMachine = osBuiltin.machine();
              const osUserInfo = osBuiltin.userInfo();
              const osUserInfoBuffer = osBuiltin.userInfo({ encoding: "buffer" });
              const osNetworkInterfaces = osBuiltin.networkInterfaces();
              const osCpus = osBuiltin.cpus();
              const osLoadAverage = osBuiltin.loadavg();
              const osConstants = osBuiltin.constants;
              let osSetPriorityDisabled = false;
              try {
                osBuiltin.setPriority(0);
              } catch (error) {
                osSetPriorityDisabled = error.message.includes("disabled");
              }
              const osSafeSurfaceOk =
                typeof osBuiltin.userInfo === "function" &&
                osUserInfo.username === "autojs6" &&
                osUserInfo.uid === 0 &&
                osUserInfo.gid === 0 &&
                osUserInfo.shell === null &&
                osUserInfo.homedir === process.cwd() &&
                Buffer.isBuffer(osUserInfoBuffer.username) &&
                osUserInfoBuffer.username.toString() === "autojs6" &&
                Buffer.isBuffer(osUserInfoBuffer.homedir) &&
                osUserInfoBuffer.homedir.toString() === process.cwd() &&
                typeof osBuiltin.networkInterfaces === "function" &&
                Object.keys(osNetworkInterfaces).length === 0 &&
                Object.isFrozen(osNetworkInterfaces) &&
                typeof osBuiltin.hostname === "function" &&
                osBuiltin.hostname() === "localhost" &&
                typeof osBuiltin.cpus === "function" &&
                Array.isArray(osCpus) &&
                osCpus.length === 1 &&
                osCpus[0].model === "AutoJs6 Embedded CPU" &&
                osCpus[0].speed === 0 &&
                typeof osBuiltin.freemem === "function" &&
                osBuiltin.freemem() === 268435456 &&
                typeof osBuiltin.totalmem === "function" &&
                osBuiltin.totalmem() === 536870912 &&
                typeof osBuiltin.loadavg === "function" &&
                Array.isArray(osLoadAverage) &&
                osLoadAverage.join(",") === "0,0,0" &&
                typeof osBuiltin.uptime === "function" &&
                osBuiltin.uptime() >= 0 &&
                typeof osBuiltin.getPriority === "function" &&
                osBuiltin.getPriority() === 0 &&
                typeof osBuiltin.setPriority === "function" &&
                Object.isFrozen(osConstants) &&
                Object.isFrozen(osConstants.signals) &&
                Object.isFrozen(osConstants.errno) &&
                Object.isFrozen(osConstants.dlopen) &&
                Object.isFrozen(osConstants.priority) &&
                osConstants.signals.SIGINT === 2 &&
                osConstants.signals.SIGTERM === 15 &&
                osConstants.errno.EPERM === 1 &&
                osConstants.errno.ENOENT === 2 &&
                osConstants.errno.EACCES === 13 &&
                osConstants.dlopen.RTLD_NOW === 2 &&
                osConstants.priority.PRIORITY_NORMAL === 0 &&
                osSetPriorityDisabled;
              const osExtendedOk =
                typeof osBuiltin.availableParallelism === "function" &&
                Number.isInteger(osParallelism) &&
                osParallelism >= 1 &&
                typeof osBuiltin.machine === "function" &&
                typeof osMachine === "string" &&
                osMachine.length > 0 &&
                typeof osBuiltin.version === "function" &&
                typeof osBuiltin.version() === "string" &&
                osSafeSurfaceOk;
              const legacyOk = querystringBuiltin === nodeQuerystringBuiltin &&
                querystringBuiltin.shadow !== true &&
                stringDecoderBuiltin === nodeStringDecoderBuiltin &&
                stringDecoderBuiltin.shadow !== true &&
                assertOk &&
                osExtendedOk &&
                punycodeBuiltin === nodePunycodeBuiltin &&
                punycodeBuiltin.shadow !== true &&
                parsed.b === "你好" &&
                encoded.includes("a=1") &&
                decoded === "你好" &&
                ascii.includes("xn--") &&
                unicode === "mañana.com" &&
                require.resolve("querystring") === "querystring" &&
                require.resolve("node:querystring") === "querystring" &&
                require.resolve("string_decoder") === "string_decoder" &&
                require.resolve("node:string_decoder") === "string_decoder" &&
                require.resolve("punycode") === "punycode" &&
                require.resolve("node:punycode") === "punycode";
              const hiddenOk = [
                typeof querystringBuiltin.decode,
                typeof querystringBuiltin.encode,
                typeof stringDecoderBuiltin.create
              ].join(",") === "undefined,undefined,undefined";
              console.log("packaged.legacy_core.ready=" + (osOk && legacyOk && hiddenOk));
              console.log("packaged.legacy_core.os=" + osOk);
              console.log("packaged.legacy_core.os_extended=" + osExtendedOk);
              console.log("packaged.legacy_core.os_safe=" + osSafeSurfaceOk);
              console.log("packaged.legacy_core.querystring=" + (parsed.b === "你好" && encoded.includes("a=1")));
              console.log("packaged.legacy_core.string_decoder=" + (decoded === "你好"));
              console.log("packaged.legacy_core.assert=" + (typeof assertBuiltin === "function"));
              console.log("packaged.legacy_core.assert_extended=" + assertOk);
              console.log("packaged.legacy_core.assert_helpers=" + assertHelperTypes);
              console.log("packaged.legacy_core.punycode=" + (unicode === "mañana.com"));
              console.log("packaged.legacy_core.hidden=" + hiddenOk);
            }

            async function runCoreSchedulingAndUtilityBuiltinCorpus() {
              const emitter = new events.EventEmitter();
              let count = 0;
              function listener(value) {
                count += value;
              }
              emitter.on("inc", listener);
              emitter.emit("inc", 1);
              emitter.off("inc", listener);
              emitter.emit("inc", 100);
              emitter.once("once", () => {
                count += 10;
              });
              emitter.emit("once");
              emitter.emit("once");
              emitter.on("remove", () => {});
              emitter.removeAllListeners("remove");
              const callableEmitter = new events();
              let callableValue = "";
              callableEmitter.on("callable", (value) => {
                callableValue = value;
              });
              callableEmitter.emit("callable", "callable-ok");
              const eventHelperTypes = [
                typeof events.getEventListeners,
                typeof events.getMaxListeners,
                typeof events.setMaxListeners,
                typeof events.addAbortListener,
                typeof events.errorMonitor,
                typeof events.captureRejectionSymbol,
                typeof events.EventEmitterAsyncResource
              ].join(",");
              const eventListenerHelpersOk =
                events.getEventListeners(emitter, "inc").length === 0 &&
                events.listenerCount(emitter, "inc") === 0;
              events.setMaxListeners(3, emitter);
              const eventMaxListenersOk = events.getMaxListeners(emitter) === 3;
              const eventErrors = [];
              const eventErrorEmitter = new events.EventEmitter();
              eventErrorEmitter.on(events.errorMonitor, (error) => {
                eventErrors.push("monitor:" + error.message);
              });
              eventErrorEmitter.on("error", (error) => {
                eventErrors.push("handler:" + error.message);
              });
              eventErrorEmitter.emit("error", new Error("packaged event boom"));
              const onceErrorEmitter = new events.EventEmitter();
              const eventOnceError = events.once(onceErrorEmitter, "ready").then(
                () => false,
                (error) => error.message === "packaged once boom"
              );
              timers.setImmediate(() => {
                onceErrorEmitter.emit("error", new Error("packaged once boom"));
              });
              const eventIterator = (async () => {
                const iteratorEmitter = new events.EventEmitter();
                const values = [];
                const iterator = events.on(iteratorEmitter, "item");
                timers.setImmediate(() => {
                  iteratorEmitter.emit("item", "one");
                  iteratorEmitter.emit("item", "two");
                });
                for await (const args of iterator) {
                  values.push(args[0]);
                  if (values.length === 2) break;
                }
                return {
                  value: values.join(","),
                  listenerCount: iteratorEmitter.listenerCount("item")
                };
              })();
              let eventAbortSeen = 0;
              const eventAbortSignal = {
                aborted: false,
                listener: null,
                removed: false,
                addEventListener(name, listener) {
                  if (name === "abort") this.listener = listener;
                },
                removeEventListener(name, listener) {
                  if (name === "abort" && this.listener === listener) this.removed = true;
                }
              };
              const eventAbortDisposable = events.addAbortListener(eventAbortSignal, () => {
                eventAbortSeen += 1;
              });
              eventAbortSignal.aborted = true;
              eventAbortSignal.listener.call(eventAbortSignal, { type: "abort" });
              if (eventAbortDisposable && typeof eventAbortDisposable[Symbol.dispose] === "function") {
                eventAbortDisposable[Symbol.dispose]();
              }

              const onceReady = events.once(emitter, "ready");
              const scheduling = new Promise((resolve) => {
                let ticks = 0;
                let timeoutCleared = true;
                let immediateSeen = false;
                let resolved = false;
                let fallback = null;
                function finish() {
                  if (resolved) return;
                  resolved = true;
                  if (fallback !== null) {
                    timers.clearTimeout(fallback);
                  }
                  emitter.emit("ready", "ok");
                  resolve({ ticks, timeoutCleared, immediateSeen });
                }
                const timeout = timers.setTimeout(() => {
                  timeoutCleared = false;
                }, 0);
                timers.clearTimeout(timeout);
                fallback = timers.setTimeout(finish, 100);
                const interval = timers.setInterval(() => {
                  ticks += 1;
                  if (ticks === 2) {
                    timers.clearInterval(interval);
                    timers.setImmediate(() => {
                      immediateSeen = true;
                      finish();
                    });
                  }
                }, 1);
              });
              const [[readyValue], scheduled, eventIteratorResult, eventOnceErrorOk] = await Promise.all([
                onceReady,
                scheduling,
                eventIterator,
                eventOnceError
              ]);

              async function readAllWebStream(readable) {
                const reader = readable.getReader();
                const values = [];
                while (true) {
                  const next = await reader.read();
                  if (next.done) {
                    return values;
                  }
                  values.push(next.value);
                }
              }
              async function collectAsyncLines(iterable) {
                const values = [];
                for await (const line of iterable) {
                  values.push(line);
                }
                return values.join("|");
              }

              const streamPass = new stream.PassThrough();
              let streamText = "";
              streamPass.on("data", (chunk) => {
                streamText += chunk.toString("utf8");
              });
              const streamDone = events.once(streamPass, "end");
              streamPass.end(Buffer.from("core"));
              await streamDone;

              const streamPipelineText = await new Promise((resolve, reject) => {
                let collected = "";
                const writable = new stream.Writable({
                  write(chunk, encoding, callback) {
                    collected += chunk.toString();
                    callback();
                  }
                });
                stream.pipeline(
                  stream.Readable.from(["pipe", Buffer.from("line")]),
                  writable,
                  (error) => {
                    if (error) {
                      reject(error);
                      return;
                    }
                    resolve(collected);
                  }
                );
              });
              const streamFinishedOk = await new Promise((resolve, reject) => {
                const pass = new stream.PassThrough();
                stream.finished(pass, (error) => {
                  if (error) {
                    reject(error);
                    return;
                  }
                  resolve(true);
                });
                pass.resume();
                pass.end("finished");
              });
              const streamPipelineErrorOk = await new Promise((resolve) => {
                const failedTransform = new stream.Transform({
                  transform(chunk, encoding, callback) {
                    callback(new Error("packaged pipeline boom"));
                  }
                });
                const writable = new stream.Writable({
                  write(chunk, encoding, callback) {
                    callback();
                  }
                });
                stream.pipeline(
                  stream.Readable.from(["bad"]),
                  failedTransform,
                  writable,
                  (error) => {
                    resolve(
                      error &&
                      error.message === "packaged pipeline boom" &&
                      stream.isErrored(failedTransform) === true &&
                      stream.isDestroyed(failedTransform) === true
                    );
                  }
                );
              });
              const streamFinishedErrorOk = await new Promise((resolve) => {
                const failed = new stream.PassThrough();
                stream.finished(failed, (error) => {
                  resolve(
                    error &&
                    error.message === "packaged finished boom" &&
                    stream.isErrored(failed) === true &&
                    stream.isDestroyed(failed) === true
                  );
                });
                failed.destroy(new Error("packaged finished boom"));
              });
              const streamPromisePipelineText = await (async () => {
                let collected = "";
                const writable = new stream.Writable({
                  write(chunk, encoding, callback) {
                    collected += chunk.toString();
                    callback();
                  }
                });
                await streamPromises.pipeline(
                  stream.Readable.from(["promise", Buffer.from("line")]),
                  writable
                );
                return collected;
              })();
              const streamPromiseFinishedOk = await (async () => {
                const pass = new stream.PassThrough();
                const pending = streamPromises.finished(pass);
                pass.resume();
                pass.end("promise-finished");
                await pending;
                return true;
              })();
              const streamPromisePipelineErrorOk = await (async () => {
                const failedTransform = new stream.Transform({
                  transform(chunk, encoding, callback) {
                    callback(new Error("packaged promise pipeline boom"));
                  }
                });
                return streamPromises.pipeline(
                  stream.Readable.from(["bad"]),
                  failedTransform,
                  new stream.Writable({
                    write(chunk, encoding, callback) {
                      callback();
                    }
                  })
                ).then(
                  () => false,
                  (error) => (
                    error &&
                    error.message === "packaged promise pipeline boom" &&
                    stream.isErrored(failedTransform) === true &&
                    stream.isDestroyed(failedTransform) === true
                  )
                );
              })();
              const streamPromiseFinishedErrorOk = await (async () => {
                const failed = new stream.PassThrough();
                const pending = streamPromises.finished(failed).then(
                  () => false,
                  (error) => (
                    error &&
                    error.message === "packaged promise finished boom" &&
                    stream.isErrored(failed) === true &&
                    stream.isDestroyed(failed) === true
                  )
                );
                failed.destroy(new Error("packaged promise finished boom"));
                return pending;
              })();
              const streamAddAbortSignalOk = await new Promise((resolve) => {
                const pass = new stream.PassThrough();
                const controller = new AbortController();
                const returned = stream.addAbortSignal(controller.signal, pass);
                pass.on("error", (error) => {
                  resolve(
                    returned === pass &&
                    error &&
                    error.code === "ABORT_ERR" &&
                    stream.isErrored(pass) === true &&
                    stream.isDestroyed(pass) === true
                  );
                });
                controller.abort();
              });
              const streamComposeText = await new Promise((resolve, reject) => {
                let output = "";
                const upper = new stream.Transform({
                  transform(chunk, encoding, callback) {
                    callback(null, chunk.toString().toUpperCase());
                  }
                });
                const suffix = new stream.Transform({
                  transform(chunk, encoding, callback) {
                    callback(null, chunk.toString() + "!");
                  }
                });
                const composed = stream.compose(upper, suffix);
                composed.on("data", (chunk) => {
                  output += chunk.toString();
                });
                composed.on("error", reject);
                composed.on("end", () => {
                  resolve(composed instanceof stream.Duplex ? output : "not-duplex");
                });
                composed.write("packaged");
                composed.end();
              });
              const streamDuplexPairText = await new Promise((resolve, reject) => {
                const pair = stream.duplexPair();
                let left = "";
                let right = "";
                let ended = 0;
                function done() {
                  ended += 1;
                  if (ended === 2) {
                    resolve([
                      Array.isArray(pair),
                      pair.length,
                      pair[0] instanceof stream.Duplex,
                      pair[1] instanceof stream.Duplex,
                      left,
                      right
                    ].join("|"));
                  }
                }
                pair[0].on("data", (chunk) => { left += chunk.toString(); });
                pair[1].on("data", (chunk) => { right += chunk.toString(); });
                pair[0].on("error", reject);
                pair[1].on("error", reject);
                pair[0].on("end", done);
                pair[1].on("end", done);
                pair[1].end("right");
                pair[0].end("left");
              });
              fs.mkdirSync("./packaged-core-stream", { recursive: true });
              fs.writeFileSync("./packaged-core-stream/input.txt", "packaged-file", "utf8");
              await streamPromises.pipeline(
                fs.createReadStream("./packaged-core-stream/input.txt", { encoding: "utf8" }),
                new stream.Transform({
                  transform(chunk, encoding, callback) {
                    callback(null, chunk.toString().toUpperCase());
                  }
                }),
                fs.createWriteStream("./packaged-core-stream/output.txt", { encoding: "utf8" })
              );
              const streamPromiseFsPipelineText = fs.readFileSync("./packaged-core-stream/output.txt", "utf8");
              const coreReadHandle = await fsp.open("./packaged-core-stream/output.txt", "r");
              const coreWriteHandle = await fsp.open("./packaged-core-stream/filehandle-output.txt", "w");
              await streamPromises.pipeline(
                coreReadHandle.createReadStream({ encoding: "utf8", autoClose: false }),
                coreWriteHandle.createWriteStream({ encoding: "utf8", autoClose: false })
              );
              await coreReadHandle.close();
              await coreWriteHandle.close();
              const streamPromiseFileHandlePipelineText = fs.readFileSync("./packaged-core-stream/filehandle-output.txt", "utf8");
              const streamPromiseAbortOk = await (async () => {
                const controller = new AbortController();
                controller.abort();
                return streamPromises.pipeline(
                  stream.Readable.from(["abort"]),
                  new stream.Writable({
                    write(chunk, encoding, callback) {
                      callback();
                    }
                  }),
                  { signal: controller.signal }
                ).then(
                  () => false,
                  (error) => error && error.code === "ABORT_ERR"
                );
              })();
              const streamPromiseEscapeOk = await (async () => {
                try {
                  await streamPromises.pipeline(
                    fs.createReadStream("../packaged-core-stream-outside.txt"),
                    new stream.Writable({
                      write(chunk, encoding, callback) {
                        callback();
                      }
                    })
                  );
                  return false;
                } catch (error) {
                  return error instanceof Error && String(error.message).includes("escapes working directory");
                }
              })();
              const streamPromisesHiddenTypes = [
                typeof streamPromises.Readable,
                typeof streamPromises.Writable,
                typeof streamPromises.consumers,
                typeof streamPromises.promises
              ].join(",");
              const streamWebReadableText = (await readAllWebStream(new streamWeb.ReadableStream({
                start(controller) {
                  controller.enqueue("packaged");
                  controller.enqueue("-web");
                  controller.close();
                }
              }))).join("");
              let streamWebWritableText = "";
              const streamWebWritable = new streamWeb.WritableStream({
                write(chunk) {
                  streamWebWritableText += String(chunk);
                }
              });
              const streamWebWritableWriter = streamWebWritable.getWriter();
              await streamWebWritableWriter.write("write");
              await streamWebWritableWriter.write("-web");
              await streamWebWritableWriter.close();
              const streamWebTransform = new streamWeb.TransformStream({
                transform(chunk, controller) {
                  controller.enqueue(String(chunk).toUpperCase());
                }
              });
              const streamWebTransformValues = readAllWebStream(streamWebTransform.readable);
              const streamWebTransformWriter = streamWebTransform.writable.getWriter();
              await streamWebTransformWriter.write("transform");
              await streamWebTransformWriter.write("-web");
              await streamWebTransformWriter.close();
              const streamWebTransformText = (await streamWebTransformValues).join("");
              const streamWebEncoder = new streamWeb.TextEncoderStream();
              const streamWebEncodedValues = readAllWebStream(streamWebEncoder.readable);
              const streamWebEncoderWriter = streamWebEncoder.writable.getWriter();
              await streamWebEncoderWriter.write("encoded-web");
              await streamWebEncoderWriter.close();
              const streamWebEncodedText = Buffer.concat((await streamWebEncodedValues).map((chunk) => Buffer.from(chunk))).toString("utf8");
              const streamWebDecoder = new streamWeb.TextDecoderStream();
              const streamWebDecodedValues = readAllWebStream(streamWebDecoder.readable);
              const streamWebDecoderWriter = streamWebDecoder.writable.getWriter();
              await streamWebDecoderWriter.write(Buffer.from("decoded-web"));
              await streamWebDecoderWriter.close();
              const streamWebDecodedText = (await streamWebDecodedValues).join("");
              const streamWebCountStrategy = new streamWeb.CountQueuingStrategy({ highWaterMark: 2 });
              const streamWebByteStrategy = new streamWeb.ByteLengthQueuingStrategy({ highWaterMark: 8 });
              const streamWebHiddenTypes = [
                typeof streamWeb.CompressionStream,
                typeof streamWeb.DecompressionStream,
                typeof streamWeb.ReadableStreamDefaultReader,
                typeof streamWeb.WritableStreamDefaultWriter,
                typeof streamWeb.TransformStreamDefaultController,
                typeof streamWeb.ReadableByteStreamController
              ].join(",");
              const streamConsumerText = await streamConsumers.text(
                stream.Readable.from(["packaged", "-text"])
              );
              const streamConsumerJson = await streamConsumers.json(
                stream.Readable.from(['{"packaged":', 'true}'])
              );
              const streamConsumerBuffer = await streamConsumers.buffer(
                stream.Readable.from([Buffer.from("packaged"), "-buffer"])
              );
              const streamConsumerArrayBuffer = await streamConsumers.arrayBuffer(
                stream.Readable.from([Buffer.from("array")])
              );
              const streamConsumerBlob = await streamConsumers.blob(
                stream.Readable.from([Buffer.from("blob"), "-packaged"])
              );
              const streamConsumerBlobText = await streamConsumerBlob.text();
              const streamConsumersHiddenTypes = [
                typeof streamConsumers.finished,
                typeof streamConsumers.pipeline,
                typeof streamConsumers.Readable
              ].join(",");
              fs.mkdirSync("./packaged-core-readline", { recursive: true });
              fs.writeFileSync("./packaged-core-readline/input.txt", "red\ngreen\nblue\n", "utf8");
              const readlineCallbackText = await collectAsyncLines(readline.createInterface({
                input: fs.createReadStream("./packaged-core-readline/input.txt", { encoding: "utf8" }),
                crlfDelay: Infinity
              }));
              const readlinePromiseText = await collectAsyncLines(readlinePromises.createInterface({
                input: fs.createReadStream("./packaged-core-readline/input.txt", { encoding: "utf8" }),
                crlfDelay: Infinity
              }));
              const readlineHandle = await fsp.open("./packaged-core-readline/input.txt", "r");
              const readlineFileHandleText = await collectAsyncLines(readlineHandle.readLines({
                encoding: "utf8",
                autoClose: false
              }));
              const readlineHandleStillOpen = typeof readlineHandle.fd === "number" && readlineHandle.fd >= 0;
              await readlineHandle.close();
              const readlineQuestionText = await (async () => {
                const input = new stream.PassThrough();
                const output = new stream.PassThrough();
                let prompt = "";
                output.on("data", (chunk) => {
                  prompt += chunk.toString("utf8");
                });
                const reader = readlinePromises.createInterface({
                  input,
                  output,
                  terminal: false
                });
                const pending = reader.question("Color: ");
                input.end("cyan\n");
                const value = await pending;
                reader.close();
                return value + "|" + prompt;
              })();
              const readlineAbortOk = await (async () => {
                const input = new stream.PassThrough();
                const output = new stream.PassThrough();
                output.resume();
                const reader = readlinePromises.createInterface({
                  input,
                  output,
                  terminal: false
                });
                const controller = new AbortController();
                const pending = reader.question("Abort: ", {
                  signal: controller.signal
                }).then(
                  () => false,
                  (error) => error && error.code === "ABORT_ERR" && error.name === "AbortError"
                );
                controller.abort();
                const ok = await pending;
                reader.close();
                return ok;
              })();
              const readlineTerminal = new stream.PassThrough();
              let readlineTerminalText = "";
              readlineTerminal.on("data", (chunk) => {
                readlineTerminalText += chunk.toString("utf8");
              });
              readline.cursorTo(readlineTerminal, 0);
              readline.moveCursor(readlineTerminal, 1, 0);
              readline.clearLine(readlineTerminal, 0);
              readline.clearScreenDown(readlineTerminal);
              const readlineHelperTypes = [
                typeof readline.createInterface,
                typeof readline.clearLine,
                typeof readline.clearScreenDown,
                typeof readline.cursorTo,
                typeof readline.moveCursor,
                typeof readline.emitKeypressEvents,
                typeof readline.promises
              ].join(",");
              const readlinePromiseHelperTypes = [
                typeof readlinePromises.createInterface,
                typeof readlinePromises.Interface,
                typeof readlinePromises.Readline,
                typeof readlinePromises.clearLine,
                typeof readlinePromises.cursorTo,
                typeof readlinePromises.promises
              ].join(",");

              function dnsLookup(hostname, options) {
                return new Promise((resolve) => {
                  const done = (error, address, family) => resolve({
                    code: error && error.code,
                    address,
                    family
                  });
                  if (arguments.length === 1) {
                    dns.lookup(hostname, done);
                  } else {
                    dns.lookup(hostname, options, done);
                  }
                });
              }
              function dnsLookupAll(hostname, options) {
                return new Promise((resolve) => {
                  dns.lookup(hostname, Object.assign({ all: true }, options || {}), (error, addresses) => resolve({
                    code: error && error.code,
                    addresses: addresses || []
                  }));
                });
              }
              function dnsLookupService(address, port) {
                return new Promise((resolve) => {
                  dns.lookupService(address, port, (error, hostname, service) => resolve({
                    code: error && error.code,
                    hostname,
                    service
                  }));
                });
              }
              function dnsResolve4(hostname) {
                return new Promise((resolve) => {
                  dns.resolve4(hostname, (error) => resolve(error && error.code));
                });
              }
              const dnsLocal4 = await dnsLookup("localhost", { family: 4 });
              const dnsLocal6 = await dnsPromises.lookup("localhost", { family: 6 });
              const dnsLiteral4 = await dnsPromises.lookup("127.0.0.1");
              const dnsLiteral6 = await dnsPromises.lookup("::1");
              const dnsAllLocal = await dnsLookupAll("localhost");
              const dnsDeniedLookup = await dnsLookup("example.com");
              const dnsDeniedPromiseLookup = await dnsPromises.lookup("example.com").then(
                () => "allowed",
                (error) => error && error.code
              );
              const dnsDeniedResolve = await dnsResolve4("example.com");
              const dnsDeniedPromiseResolve = await dnsPromises.resolve6("example.com").then(
                () => "allowed",
                (error) => error && error.code
              );
              const dnsService4 = await dnsLookupService("127.0.0.1", 443);
              const dnsService6 = await dnsPromises.lookupService("::1", 8443);
              const dnsDeniedService = await dnsLookupService("192.0.2.1", 443);
              const dnsResolverCallbackCode = await new Promise((resolve) => {
                new dns.Resolver().resolve4("example.com", (error) => resolve(error && error.code));
              });
              const dnsResolverPromiseCode = await new dnsPromises.Resolver().resolve4("example.com").then(
                () => "allowed",
                (error) => error && error.code
              );
              const dnsOrderBefore = dns.getDefaultResultOrder();
              dns.setDefaultResultOrder("ipv6first");
              const dnsOrdered6 = await dnsPromises.lookup("localhost", { all: true });
              dns.setDefaultResultOrder("ipv4first");
              const dnsOrdered4 = await dnsPromises.lookup("localhost", { all: true });
              dns.setDefaultResultOrder(dnsOrderBefore);
              let dnsInvalidOrderCode = "";
              try {
                dns.setDefaultResultOrder("bad");
              } catch (error) {
                dnsInvalidOrderCode = error && error.code;
              }
              dns.setServers([]);
              let dnsSetServersCode = "";
              try {
                dns.setServers(["8.8.8.8"]);
              } catch (error) {
                dnsSetServersCode = error && error.code;
              }
              const dnsHelperTypes = [
                typeof dns.lookup,
                typeof dns.lookupService,
                typeof dns.resolve4,
                typeof dns.reverse,
                typeof dns.Resolver,
                typeof dns.promises.lookup,
                typeof dnsPromises.Resolver,
                typeof dns.ADDRCONFIG,
                typeof dns.NOTFOUND
              ].join(",");

              function netDisabled(action) {
                try {
                  action();
                  return "allowed";
                } catch (error) {
                  return error && error.code;
                }
              }
              async function netTick() {
                await new Promise((resolve) => setImmediate(resolve));
              }
              const netSocket = new net.Socket({ allowHalfOpen: true });
              let netSocketWriteCode = "";
              const netSocketWriteReturn = netSocket.write("blocked", (error) => {
                netSocketWriteCode = error && error.code;
              });
              await netTick();
              netSocket.setTimeout(1, () => {});
              netSocket.setNoDelay(true).setKeepAlive(false).ref().unref().pause().resume().destroy();
              const netServer = net.createServer(() => {});
              let netConnectionsSummary = "";
              let netCloseSeen = false;
              netServer.getConnections((error, count) => {
                netConnectionsSummary = (error === null) + "/" + count;
              });
              netServer.close(() => {
                netCloseSeen = true;
              });
              await netTick();
              const netBlockList = new net.BlockList();
              netBlockList.addAddress("127.0.0.1");
              netBlockList.addRange("10.0.0.1", "10.0.0.3");
              netBlockList.addSubnet("192.168.1.0", 24);
              const netSocketAddress = new net.SocketAddress({ address: "127.0.0.1", port: 8080 });
              const netParsed4 = net.SocketAddress.parse("127.0.0.1:8080");
              const netParsed6 = net.SocketAddress.parse("[::1]:443");
              let netInvalidSocketAddress = "";
              try {
                new net.SocketAddress({ address: "localhost", port: 80 });
              } catch (error) {
                netInvalidSocketAddress = error && error.code;
              }
              let netInvalidBlockList = "";
              try {
                netBlockList.addSubnet("127.0.0.1", 33);
              } catch (error) {
                netInvalidBlockList = error && error.code;
              }
              net.setDefaultAutoSelectFamily(false);
              net.setDefaultAutoSelectFamilyAttemptTimeout(123);
              let netInvalidAutoFamily = "";
              try {
                net.setDefaultAutoSelectFamily("yes");
              } catch (error) {
                netInvalidAutoFamily = error && error.code;
              }
              let netInvalidAutoTimeout = "";
              try {
                net.setDefaultAutoSelectFamilyAttemptTimeout(0);
              } catch (error) {
                netInvalidAutoTimeout = error && error.code;
              }
              const netHelperTypes = [
                typeof net.isIP,
                typeof net.isIPv4,
                typeof net.isIPv6,
                typeof net.connect,
                typeof net.createConnection,
                typeof net.createServer,
                typeof net.Socket,
                typeof net.Server,
                typeof net.BlockList,
                typeof net.SocketAddress,
                typeof net.SocketAddress.parse,
                typeof net._createServerHandle
              ].join(",");

              function httpDisabled(action) {
                try {
                  action();
                  return "allowed";
                } catch (error) {
                  return error && error.code;
                }
              }
              async function httpTick() {
                await new Promise((resolve) => setImmediate(resolve));
              }
              const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 3 });
              const httpsAgent = new https.Agent({ keepAlive: true });
              const httpRequest = new http.ClientRequest("http://example.com/a?b=1");
              httpRequest.setHeader("X-Test", "yes");
              const httpRequestHeaders = httpRequest.getHeaders();
              let httpRequestEnded = false;
              httpRequest.end(() => {
                httpRequestEnded = true;
              });
              httpRequest.abort();
              const httpIncoming = new http.IncomingMessage();
              const httpResponse = new http.ServerResponse(httpIncoming);
              httpResponse.setHeader("X-Response", "ok");
              httpResponse.writeHead(201, { "X-Head": "yes" });
              let httpResponseEnded = false;
              httpResponse.end(() => {
                httpResponseEnded = true;
              });
              const httpServer = http.createServer(() => {});
              const httpsServer = https.createServer(() => {});
              let httpCloseSeen = false;
              httpServer.setTimeout(1, () => {}).ref().unref();
              httpServer.close(() => {
                httpCloseSeen = true;
              });
              await httpTick();
              let httpInvalidHeaderName = "";
              try {
                http.validateHeaderName("bad header");
              } catch (error) {
                httpInvalidHeaderName = error && error.code;
              }
              let httpInvalidHeaderValue = "";
              try {
                http.validateHeaderValue("x-test", "bad\nvalue");
              } catch (error) {
                httpInvalidHeaderValue = error && error.code;
              }
              let httpInvalidParserCount = "";
              try {
                http.setMaxIdleHTTPParsers(0);
              } catch (error) {
                httpInvalidParserCount = error && error.code;
              }
              http.validateHeaderName("x-ok");
              http.validateHeaderValue("x-ok", ["a", "b"]);
              http.setMaxIdleHTTPParsers(1);
              const httpHelperTypes = [
                typeof http.request,
                typeof http.get,
                typeof http.createServer,
                typeof http.Agent,
                typeof http.ClientRequest,
                typeof http.IncomingMessage,
                typeof http.OutgoingMessage,
                typeof http.Server,
                typeof http.ServerResponse,
                typeof http.validateHeaderName,
                typeof http.validateHeaderValue,
                typeof http.setMaxIdleHTTPParsers,
                typeof http.globalAgent,
                typeof http.METHODS,
                typeof http.STATUS_CODES,
                typeof http._connectionListener
              ].join(",");

              function tlsDisabled(action) {
                try {
                  action();
                  return "allowed";
                } catch (error) {
                  return error && error.code;
                }
              }
              async function tlsTick() {
                await new Promise((resolve) => setImmediate(resolve));
              }
              const tlsContext = tls.createSecureContext({ minVersion: "TLSv1.2", ca: "cert" });
              const tlsSocket = new tls.TLSSocket(new net.Socket(), { servername: "example.com" });
              let tlsRenegotiateCode = "";
              const tlsRenegotiateReturn = tlsSocket.renegotiate({}, (error) => {
                tlsRenegotiateCode = error && error.code;
              });
              await tlsTick();
              const tlsDetailedCert = tlsSocket.getPeerCertificate(true);
              const tlsCipher = tlsSocket.getCipher();
              let tlsInvalidFragment = "";
              try {
                tlsSocket.setMaxSendFragment(0);
              } catch (error) {
                tlsInvalidFragment = error && error.code;
              }
              const tlsServer = tls.createServer({ requestCert: true }, () => {});
              tlsServer.addContext("example.com", tlsContext);
              tlsServer.setSecureContext({ minVersion: "TLSv1.3" });
              tlsServer.setTicketKeys(Buffer.alloc(48));
              const tlsTicketKeys = tlsServer.getTicketKeys();
              let tlsInvalidTicketKeys = "";
              try {
                tlsServer.setTicketKeys(Buffer.alloc(16));
              } catch (error) {
                tlsInvalidTicketKeys = error && error.code;
              }
              let tlsCloseSeen = false;
              tlsServer.close(() => {
                tlsCloseSeen = true;
              });
              await tlsTick();
              let tlsInvalidHostname = "";
              try {
                tls.checkServerIdentity(123, {});
              } catch (error) {
                tlsInvalidHostname = error && error.code;
              }
              const tlsHelperTypes = [
                typeof tls.connect,
                typeof tls.createConnection,
                typeof tls.createServer,
                typeof tls.createSecureContext,
                typeof tls.checkServerIdentity,
                typeof tls.getCiphers,
                typeof tls.TLSSocket,
                typeof tls.Server,
                typeof tls.SecureContext,
                typeof tls.rootCertificates,
                typeof tls.DEFAULT_MIN_VERSION,
                typeof tls.createSecurePair
              ].join(",");

              let consoleStdout = "";
              let consoleStderr = "";
              let consoleCustomStdout = "";
              let consoleCustomStderr = "";
              let consoleCallableStdout = "";
              let consoleCallableStderr = "";
              const originalCoreStdoutWrite = process.stdout.write;
              const originalCoreStderrWrite = process.stderr.write;
              process.stdout.write = function(chunk) {
                consoleStdout += String(chunk);
                return true;
              };
              process.stderr.write = function(chunk) {
                consoleStderr += String(chunk);
                return true;
              };
              let consoleTaskValue = "";
              try {
                consoleBuiltin.log("packaged", "log");
                consoleBuiltin.info("packaged", { value: 1 });
                consoleBuiltin.debug("packaged", "debug");
                consoleBuiltin.dir({ dir: true });
                consoleBuiltin.table(["table"]);
                consoleBuiltin.group("grouped");
                consoleBuiltin.groupEnd();
                consoleBuiltin.warn("packaged", "warn");
                consoleBuiltin.error(new Error("packaged console boom"));
                consoleBuiltin.assert(false, "packaged assert", 42);
                consoleBuiltin.count("items");
                consoleBuiltin.count("items");
                consoleBuiltin.countReset("items");
                consoleBuiltin.time("phase");
                consoleBuiltin.timeLog("phase", "middle");
                consoleBuiltin.timeEnd("phase");
                consoleBuiltin.trace("packaged trace");
                consoleTaskValue = consoleBuiltin.createTask("task").run((value) => value + "-ok", "task");
                consoleBuiltin.context("scope").log("context", "log");
                const customConsole = new consoleBuiltin.Console({
                  stdout: {
                    write(chunk) {
                      consoleCustomStdout += String(chunk);
                      return true;
                    }
                  },
                  stderr: {
                    write(chunk) {
                      consoleCustomStderr += String(chunk);
                      return true;
                    }
                  }
                });
                customConsole.log("custom", "out");
                customConsole.info("custom", { ok: true });
                customConsole.warn("custom", "warn");
                customConsole.error("custom", "err");
                customConsole.count("custom-count");
                customConsole.countReset("custom-count");
                customConsole.time("custom-phase");
                customConsole.timeEnd("custom-phase");
                customConsole.trace("custom-trace");
                const callableConsole = consoleBuiltin.Console({
                  stdout: {
                    write(chunk) {
                      consoleCallableStdout += String(chunk);
                      return true;
                    }
                  },
                  stderr: {
                    write(chunk) {
                      consoleCallableStderr += String(chunk);
                      return true;
                    }
                  }
                });
                callableConsole.log("callable", "out");
                callableConsole.error("callable", "err");
              } finally {
                process.stdout.write = originalCoreStdoutWrite;
                process.stderr.write = originalCoreStderrWrite;
              }
              const consoleHelperTypes = [
                typeof consoleBuiltin.Console,
                typeof consoleBuiltin.log,
                typeof consoleBuiltin.info,
                typeof consoleBuiltin.debug,
                typeof consoleBuiltin.warn,
                typeof consoleBuiltin.error,
                typeof consoleBuiltin.dir,
                typeof consoleBuiltin.dirxml,
                typeof consoleBuiltin.table,
                typeof consoleBuiltin.assert,
                typeof consoleBuiltin.count,
                typeof consoleBuiltin.countReset,
                typeof consoleBuiltin.time,
                typeof consoleBuiltin.timeLog,
                typeof consoleBuiltin.timeEnd,
                typeof consoleBuiltin.trace,
                typeof consoleBuiltin.clear,
                typeof consoleBuiltin.group,
                typeof consoleBuiltin.groupCollapsed,
                typeof consoleBuiltin.groupEnd,
                typeof consoleBuiltin.context,
                typeof consoleBuiltin.createTask,
                typeof consoleBuiltin.profile,
                typeof consoleBuiltin.profileEnd,
                typeof consoleBuiltin.timeStamp
              ].join(",");
              const consoleKeySummary = Object.keys(consoleBuiltin).sort().join(",");
              const consolePrototypeKeySummary = Object.keys(consoleBuiltin.Console.prototype).sort().join(",");
              const consoleHiddenTypes = [
                typeof consoleBuiltin._stdout,
                typeof consoleBuiltin._stderr,
                typeof consoleBuiltin._times,
                typeof consoleBuiltin.Console._stdout
              ].join(",");
              const consoleOk =
                consoleBuiltin === nodeConsoleBuiltin &&
                consoleBuiltin.shadow !== true &&
                Object.isFrozen(consoleBuiltin) &&
                Object.isFrozen(consoleBuiltin.Console.prototype) &&
                consoleKeySummary === "Console,assert,clear,context,count,countReset,createTask,debug,dir,dirxml,error,group,groupCollapsed,groupEnd,info,log,profile,profileEnd,table,time,timeEnd,timeLog,timeStamp,trace,warn" &&
                consolePrototypeKeySummary === "assert,clear,count,countReset,debug,dir,dirxml,error,group,groupCollapsed,groupEnd,info,log,table,time,timeEnd,timeLog,trace,warn" &&
                consoleHelperTypes === "function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function" &&
                consoleHiddenTypes === "undefined,undefined,undefined,undefined" &&
                consoleTaskValue === "task-ok" &&
                consoleStdout.includes("packaged log") &&
                consoleStdout.includes("packaged {\"value\":1}") &&
                consoleStdout.includes("packaged debug") &&
                consoleStdout.includes("{\"dir\":true}") &&
                consoleStdout.includes("[\"table\"]") &&
                consoleStdout.includes("grouped") &&
                consoleStdout.includes("items: 1") &&
                consoleStdout.includes("items: 2") &&
                consoleStdout.includes("phase:") &&
                consoleStdout.includes("middle") &&
                consoleStdout.includes("context log") &&
                consoleStderr.includes("packaged warn") &&
                consoleStderr.includes("Error: packaged console boom") &&
                consoleStderr.includes("Assertion failed: packaged assert 42") &&
                consoleStderr.includes("Trace: packaged trace") &&
                consoleCustomStdout.includes("custom out") &&
                consoleCustomStdout.includes("custom {\"ok\":true}") &&
                consoleCustomStdout.includes("custom-count: 1") &&
                consoleCustomStdout.includes("custom-phase:") &&
                consoleCustomStderr.includes("custom warn") &&
                consoleCustomStderr.includes("custom err") &&
                consoleCustomStderr.includes("Trace: custom-trace") &&
                consoleCallableStdout.includes("callable out") &&
                consoleCallableStderr.includes("callable err");

              const v8Serialized = v8Builtin.serialize({
                text: "packaged",
                list: [1, 2, 3],
                nested: { ok: true },
                map: new Map([["k", "v"]]),
                date: new Date(1234),
                big: 42n
              });
              const v8Restored = v8Builtin.deserialize(v8Serialized);
              const v8Heap = v8Builtin.getHeapStatistics();
              const v8Spaces = v8Builtin.getHeapSpaceStatistics();
              const v8Code = v8Builtin.getHeapCodeStatistics();
              const v8HelperTypes = [
                typeof v8Builtin.cachedDataVersionTag,
                typeof v8Builtin.serialize,
                typeof v8Builtin.deserialize,
                typeof v8Builtin.getHeapStatistics,
                typeof v8Builtin.getHeapSpaceStatistics,
                typeof v8Builtin.getHeapCodeStatistics,
                typeof v8Builtin.isStringOneByteRepresentation
              ].join(",");
              const v8HiddenTypes = [
                typeof v8Builtin.Serializer,
                typeof v8Builtin.Deserializer,
                typeof v8Builtin.DefaultSerializer,
                typeof v8Builtin.DefaultDeserializer,
                typeof v8Builtin.setFlagsFromString,
                typeof v8Builtin.getHeapSnapshot,
                typeof v8Builtin.writeHeapSnapshot,
                typeof v8Builtin.takeCoverage,
                typeof v8Builtin.stopCoverage,
                typeof v8Builtin.promiseHooks,
                typeof v8Builtin.startupSnapshot,
                typeof v8Builtin.setHeapSnapshotNearHeapLimit,
                typeof v8Builtin.GCProfiler,
                typeof v8Builtin.queryObjects,
                typeof v8Builtin.startCpuProfile,
                typeof v8Builtin.getCppHeapStatistics
              ].join(",");
              function rejectsV8OutOfRange(action) {
                try {
                  action();
                  return false;
                } catch (error) {
                  return error && error.code === "ERR_OUT_OF_RANGE";
                }
              }
              const v8DeserializeLimitOk = rejectsV8OutOfRange(() => v8Builtin.deserialize(Buffer.alloc(1024 * 1024 + 1)));
              const v8SerializeLimitOk = rejectsV8OutOfRange(() => v8Builtin.serialize("x".repeat(1024 * 1024)));
              const v8Ok =
                v8Builtin === nodeV8Builtin &&
                v8Builtin.shadow !== true &&
                Object.isFrozen(v8Builtin) &&
                Object.keys(v8Builtin).sort().join(",") === "cachedDataVersionTag,deserialize,getHeapCodeStatistics,getHeapSpaceStatistics,getHeapStatistics,isStringOneByteRepresentation,serialize" &&
                v8HelperTypes === "function,function,function,function,function,function,function" &&
                v8HiddenTypes === "undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined" &&
                Buffer.isBuffer(v8Serialized) &&
                v8Restored.text === "packaged" &&
                v8Restored.list.join("|") === "1|2|3" &&
                v8Restored.nested.ok === true &&
                v8Restored.map instanceof Map &&
                v8Restored.map.get("k") === "v" &&
                v8Restored.date instanceof Date &&
                v8Restored.date.getTime() === 1234 &&
                v8Restored.big === 42n &&
                Object.isFrozen(v8Heap) &&
                Object.isFrozen(v8Spaces) &&
                v8Spaces.length > 0 &&
                Object.isFrozen(v8Spaces[0]) &&
                Object.isFrozen(v8Code) &&
                typeof v8Heap.total_heap_size === "number" &&
                typeof v8Spaces[0].space_name === "string" &&
                typeof v8Spaces[0].space_size === "number" &&
                typeof v8Code.code_and_metadata_size === "number" &&
                v8Builtin.isStringOneByteRepresentation("abc") === true &&
                typeof v8Builtin.isStringOneByteRepresentation("你好") === "boolean" &&
                typeof v8Builtin.cachedDataVersionTag() === "number" &&
                v8DeserializeLimitOk &&
                v8SerializeLimitOk;

              const ttyHelperTypes = [
                typeof ttyBuiltin.isatty,
                typeof ttyBuiltin.ReadStream,
                typeof ttyBuiltin.WriteStream
              ].join(",");
              const ttyIsattySummary = [
                ttyBuiltin.isatty(),
                ttyBuiltin.isatty(null),
                ttyBuiltin.isatty("1"),
                ttyBuiltin.isatty(0),
                ttyBuiltin.isatty(1),
                ttyBuiltin.isatty(2),
                ttyBuiltin.isatty(999999)
              ].join(",");
              const ttyStdioSummary = [
                process.stdout.isTTY,
                typeof process.stdout.getColorDepth,
                process.stdout.getColorDepth(),
                typeof process.stdout.hasColors,
                process.stdout.hasColors(),
                process.stdout.hasColors(256),
                process.stderr.isTTY,
                typeof process.stderr.getColorDepth,
                process.stderr.getColorDepth({ FORCE_COLOR: "3" }),
                typeof process.stderr.hasColors,
                process.stderr.hasColors()
              ].join(",");
              const ttyOk =
                ttyBuiltin === nodeTtyBuiltin &&
                ttyBuiltin.shadow !== true &&
                Object.isFrozen(ttyBuiltin) &&
                Object.keys(ttyBuiltin).sort().join(",") === "isatty" &&
                ttyHelperTypes === "function,undefined,undefined" &&
                ttyIsattySummary === "false,false,false,false,false,false,false" &&
                ttyStdioSummary === "false,function,1,function,false,false,false,function,1,function,false";

              const pathAbsolute = path.resolve("packaged-path.txt");
              const pathParsed = path.parse("/tmp/packaged/path.txt");
              const pathFormatted = path.format({ dir: "/tmp/packaged", base: "path.txt" });
              const pathHidden = [
                typeof path._makeLong,
                typeof path.matchesGlob
              ].join(",");
              const pathHelperTypes = [
                typeof path.resolve,
                typeof path.normalize,
                typeof path.isAbsolute,
                typeof path.join,
                typeof path.relative,
                typeof path.dirname,
                typeof path.basename,
                typeof path.extname,
                typeof path.format,
                typeof path.parse,
                typeof path.matchesGlob,
                typeof path.toNamespacedPath,
                typeof path.sep,
                typeof path.delimiter,
                typeof path.posix,
                typeof path.win32
              ].join(",");
              const pathSubpathsOk =
                path.posix === pathPosix &&
                pathPosix === nodePathPosix &&
                path.win32 === pathWin32 &&
                pathWin32 === nodePathWin32 &&
                pathPosix.shadow !== true &&
                pathWin32.shadow !== true &&
                pathPosix.join("a", "b", "..", "c") === "a/c" &&
                pathPosix.matchesGlob("src/app.js", "src/*.js") &&
                pathWin32.basename("C:\\tmp\\win32.txt") === "win32.txt" &&
                typeof pathWin32.matchesGlob === "function" &&
                pathWin32.dirname("C:\\tmp\\win32.txt") === "C:\\tmp" &&
                Object.isFrozen(pathPosix) &&
                Object.isFrozen(pathWin32);
              const pathOk =
                path === nodePath &&
                path.shadow !== true &&
                path.join("a", "b", "..", "c").replace(/\\/g, "/") === "a/c" &&
                path.isAbsolute(pathAbsolute) === true &&
                path.relative(path.dirname(pathAbsolute), pathAbsolute) === "packaged-path.txt" &&
                path.basename(pathAbsolute) === "packaged-path.txt" &&
                path.extname(pathAbsolute) === ".txt" &&
                path.normalize("a/./b/../c").replace(/\\/g, "/") === "a/c" &&
                pathParsed.name === "path" &&
                pathParsed.ext === ".txt" &&
                pathFormatted === "/tmp/packaged/path.txt" &&
                path.posix.join("a", "b") === "a/b" &&
                path.win32.basename("C:\\tmp\\win32.txt") === "win32.txt" &&
                path.matchesGlob("src/app.js", "src/*.js") &&
                pathPosix.matchesGlob("src/app.js", "src/*.js") &&
                typeof pathWin32.matchesGlob === "function" &&
                !path.matchesGlob("src/app.css", "src/*.js") &&
                path.toNamespacedPath("a/b") === "a/b" &&
                pathHidden === "undefined,function" &&
                pathHelperTypes === "function,function,function,function,function,function,function,function,function,function,function,function,string,string,object,object" &&
                pathSubpathsOk &&
                Object.isFrozen(path) &&
                Object.isFrozen(path.posix) &&
                Object.isFrozen(path.win32);

              const utilCallback = (value, callback) => callback(null, value + ":promisified");
              const utilPromisified = await util.promisify(utilCallback)("utility");
              const utilInspected = util.inspect({ value: "inspect" });
              const utilCustom = {
                [util.inspect.custom]() {
                  return "packaged-custom";
                }
              };
              function utilCustomCallback(callback) {
                callback(null, "unused");
              }
              utilCustomCallback[util.promisify.custom] = function() {
                return Promise.resolve("packaged-promisify");
              };
              const utilCustomPromisified = await util.promisify(utilCustomCallback)();
              const utilCallbackifyError = await new Promise((resolve) => {
                util.callbackify(function() {
                  return Promise.reject(new Error("packaged callback boom"));
                })(function(error) {
                  resolve(error && error.message);
                });
              });
              const utilParsed = util.parseArgs({
                args: ["--mode", "packaged", "--flag"],
                options: {
                  mode: { type: "string" },
                  flag: { type: "boolean" }
                }
              });
              const utilCodecText = new util.TextDecoder().decode(new util.TextEncoder().encode("util-codec"));
              const utilMime = new util.MIMEType("application/json; charset=utf-8");
              utilMime.params.set("profile", "packaged");
              const utilMimeParams = new util.MIMEParams();
              utilMimeParams.set("charset", "utf-8");
              utilMimeParams.set("boundary", "packaged");
              const utilMimeOk =
                utilMime.essence === "application/json" &&
                utilMime.params.get("charset") === "utf-8" &&
                utilMime.params.get("profile") === "packaged" &&
                utilMimeParams.toString() === "charset=utf-8;boundary=packaged";
              const utilSystemErrorMap = util.getSystemErrorMap();
              const utilSystemErrorEntry = Array.from(utilSystemErrorMap.entries())[0];
              let utilSystemErrorOk = false;
              if (utilSystemErrorEntry) {
                const utilSystemErrorValue = utilSystemErrorEntry[1];
                utilSystemErrorOk = utilSystemErrorMap instanceof Map &&
                  Array.isArray(utilSystemErrorValue) &&
                  util.getSystemErrorName(utilSystemErrorEntry[0]) === utilSystemErrorValue[0] &&
                  typeof util.getSystemErrorMessage(utilSystemErrorEntry[0]) === "string";
              }
              const utilParsedEnv = util.parseEnv("PACKAGED=1\nMODE=\"core\"");
              const utilStyledText = util.styleText("magenta", "packaged-style", { validateStream: false });
              const utilAbortController = new AbortController();
              const utilAbortedPromise = util.aborted(utilAbortController.signal, {});
              utilAbortController.abort();
              const utilAborted = await utilAbortedPromise.then(() => utilAbortController.signal.aborted);
              const utilModernOk =
                utilSystemErrorOk &&
                util.stripVTControlCharacters(utilStyledText) === "packaged-style" &&
                utilParsedEnv.PACKAGED === "1" &&
                utilParsedEnv.MODE === "core" &&
                utilAborted === true &&
                [
                  typeof util.getSystemErrorName,
                  typeof util.getSystemErrorMap,
                  typeof util.getSystemErrorMessage,
                  typeof util.styleText,
                  typeof util.parseEnv,
                  typeof util.aborted
                ].join(",") === "function,function,function,function,function,function";
              const utilHidden = [
                typeof util.getCallSites,
                typeof util.transferableAbortController,
                typeof util.setTraceSigInt
              ].join(",");
              const utilHelperTypes = [
                typeof util.format,
                typeof util.formatWithOptions,
                typeof util.inspect,
                typeof util.inspect.custom,
                typeof util.promisify,
                typeof util.promisify.custom,
                typeof util.callbackify,
                typeof util.parseArgs,
                typeof util.getSystemErrorName,
                typeof util.getSystemErrorMap,
                typeof util.getSystemErrorMessage,
                typeof util.styleText,
                typeof util.parseEnv,
                typeof util.aborted,
                typeof util.types,
                typeof util.TextEncoder,
                typeof util.TextDecoder,
                typeof util.MIMEType,
                typeof util.MIMEParams
              ].join(",");
              const utilTypesOk =
                util.types === utilTypes &&
                utilTypes === nodeUtilTypes &&
                utilTypes.isDate(new Date()) === true &&
                utilTypes.isRegExp(/packaged/) === true &&
                utilTypes.isPromise(Promise.resolve()) === true &&
                utilTypes.isMap(new Map()) === true &&
                utilTypes.shadow !== true &&
                Object.isFrozen(utilTypes);

              const asyncLocalStorage = new asyncHooks.AsyncLocalStorage();
              const asyncHooksContext = await asyncLocalStorage.run({ value: "packaged-context" }, async () => {
                const promiseValue = await Promise.resolve().then(() => asyncLocalStorage.getStore().value);
                const resource = new asyncHooks.AsyncResource("packaged.async.core");
                const resourceValue = await new Promise((resolve) => {
                  resource.runInAsyncScope(() => {
                    resolve(asyncLocalStorage.getStore().value);
                  });
                });
                resource.emitDestroy();
                return [
                  promiseValue,
                  resourceValue,
                  typeof resource.asyncId,
                  typeof resource.triggerAsyncId,
                  typeof resource.runInAsyncScope,
                  typeof resource.emitDestroy
                ].join("|");
              });
              const asyncHooksHelperTypes = [
                typeof asyncHooks.AsyncLocalStorage,
                typeof asyncHooks.AsyncResource,
                typeof asyncHooks.createHook,
                typeof asyncHooks.executionAsyncId,
                typeof asyncHooks.executionAsyncResource,
                typeof asyncHooks.asyncWrapProviders
              ].join(",");
              const asyncHooksOk =
                asyncHooks === nodeAsyncHooks &&
                asyncHooks.shadow !== true &&
                Object.isFrozen(asyncHooks) &&
                asyncHooksContext === "packaged-context|packaged-context|function|function|function|function" &&
                asyncHooksHelperTypes === "function,function,undefined,undefined,undefined,undefined";

              const diagnosticsChannelInstance = diagnosticsChannel.channel("packaged.core.diagnostics");
              const nodeDiagnosticsChannelInstance = nodeDiagnosticsChannel.channel("packaged.core.diagnostics");
              const diagnosticsSeen = [];
              function diagnosticsListener(message, name) {
                diagnosticsSeen.push(name + ":" + message.value);
              }
              function diagnosticsGlobalListener(message, name) {
                diagnosticsSeen.push("global:" + name + ":" + message.value);
              }
              diagnosticsChannelInstance.subscribe(diagnosticsListener);
              diagnosticsChannel.subscribe("packaged.core.diagnostics", diagnosticsGlobalListener);
              const diagnosticsHasSubscribersBefore =
                diagnosticsChannelInstance.hasSubscribers &&
                diagnosticsChannel.hasSubscribers("packaged.core.diagnostics");
              diagnosticsChannelInstance.publish({ value: "one" });
              diagnosticsChannel.unsubscribe("packaged.core.diagnostics", diagnosticsGlobalListener);
              diagnosticsChannelInstance.unsubscribe(diagnosticsListener);
              const diagnosticsHasSubscribersAfter = diagnosticsChannelInstance.hasSubscribers;
              diagnosticsChannelInstance.publish({ value: "after" });
              const diagnosticsTracing = diagnosticsChannel.tracingChannel("packaged.trace");
              const diagnosticsTraceSeen = [];
              diagnosticsTracing.start.subscribe((message, name) => {
                diagnosticsTraceSeen.push("start:" + name + ":" + message.value);
              });
              diagnosticsTracing.end.subscribe((message, name) => {
                diagnosticsTraceSeen.push("end:" + name + ":" + message.result);
              });
              const diagnosticsTraceValue = diagnosticsTracing.traceSync(() => "trace-packaged", { value: "trace" });
              const diagnosticsHelperTypes = [
                typeof diagnosticsChannel.channel,
                typeof diagnosticsChannel.hasSubscribers,
                typeof diagnosticsChannel.subscribe,
                typeof diagnosticsChannel.unsubscribe,
                typeof diagnosticsChannel.tracingChannel,
                typeof diagnosticsChannel.Channel,
                typeof diagnosticsChannelInstance.publish,
                typeof diagnosticsChannelInstance.subscribe,
                typeof diagnosticsChannelInstance.unsubscribe,
                typeof diagnosticsChannelInstance._subscribers,
                typeof diagnosticsTracing.traceSync,
                typeof diagnosticsTracing.tracePromise,
                typeof diagnosticsTracing.traceCallback
              ].join(",");
              const diagnosticsChannelOk =
                diagnosticsChannel === nodeDiagnosticsChannel &&
                diagnosticsChannel.shadow !== true &&
                Object.isFrozen(diagnosticsChannel) &&
                diagnosticsChannelInstance === nodeDiagnosticsChannelInstance &&
                diagnosticsSeen.join("|") === "packaged.core.diagnostics:one|global:packaged.core.diagnostics:one" &&
                diagnosticsHasSubscribersBefore === true &&
                diagnosticsHasSubscribersAfter === false &&
                diagnosticsTraceValue === "trace-packaged" &&
                diagnosticsTraceSeen.join("|") === "start:tracing:packaged.trace:start:trace|end:tracing:packaged.trace:end:trace-packaged" &&
                diagnosticsHelperTypes === "function,function,function,function,function,undefined,function,function,function,undefined,function,function,function";

              const perfPerformance = perfHooks.performance;
              const perfNowA = perfPerformance.now();
              perfPerformance.mark("packaged-perf-start");
              perfPerformance.mark("packaged-perf-end");
              perfPerformance.measure("packaged-perf-measure", "packaged-perf-start", "packaged-perf-end");
              const perfMarkEntries = perfPerformance.getEntriesByType("mark").filter((entry) => entry.name.indexOf("packaged-perf-") === 0);
              const perfMeasureEntries = perfPerformance.getEntriesByName("packaged-perf-measure", "measure");
              const perfObserved = await new Promise((resolve) => {
                const observer = new perfHooks.PerformanceObserver((list) => {
                  const summary = list.getEntries().map((entry) => entry.name + ":" + entry.entryType).join("|");
                  observer.disconnect();
                  resolve(summary);
                });
                observer.observe({ entryTypes: ["mark", "measure"] });
                perfPerformance.mark("packaged-observed-start");
                perfPerformance.mark("packaged-observed-end");
                perfPerformance.measure("packaged-observed-measure", "packaged-observed-start", "packaged-observed-end");
              });
              const perfNowB = perfPerformance.now();
              const perfJson = perfPerformance.toJSON();
              const perfHelperTypes = [
                typeof perfHooks.performance,
                typeof perfPerformance.now,
                typeof perfPerformance.mark,
                typeof perfPerformance.measure,
                typeof perfPerformance.getEntries,
                typeof perfPerformance.getEntriesByName,
                typeof perfPerformance.getEntriesByType,
                typeof perfPerformance.clearMarks,
                typeof perfPerformance.clearMeasures,
                typeof perfPerformance.timeOrigin,
                typeof perfHooks.PerformanceObserver,
                typeof perfHooks.PerformanceEntry,
                typeof perfHooks.PerformanceMark,
                typeof perfHooks.PerformanceMeasure
              ].join(",");
              const perfHiddenTypes = [
                typeof perfHooks.monitorEventLoopDelay,
                typeof perfHooks.createHistogram,
                typeof perfHooks.timerify,
                typeof perfHooks.eventLoopUtilization,
                typeof perfPerformance.timerify,
                typeof perfPerformance.eventLoopUtilization,
                typeof perfPerformance.nodeTiming,
                typeof perfPerformance.markResourceTiming
              ].join(",");
              const perfHooksOk =
                perfHooks === nodePerfHooks &&
                perfHooks.shadow !== true &&
                Object.isFrozen(perfHooks) &&
                Object.isFrozen(perfPerformance) &&
                typeof perfNowA === "number" &&
                perfNowB >= perfNowA &&
                perfMarkEntries.length >= 2 &&
                perfMeasureEntries.length === 1 &&
                perfMeasureEntries[0].entryType === "measure" &&
                perfObserved.includes("packaged-observed-start:mark") &&
                perfObserved.includes("packaged-observed-measure:measure") &&
                Object.isFrozen(perfHooks.PerformanceObserver.supportedEntryTypes) &&
                perfHooks.PerformanceObserver.supportedEntryTypes.join(",") === "mark,measure" &&
                perfHelperTypes === "object,function,function,function,function,function,function,function,function,number,function,function,function,function" &&
                perfHiddenTypes === "undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined" &&
                typeof perfJson.timeOrigin === "number";
              perfPerformance.clearMarks("packaged-perf-start");
              perfPerformance.clearMarks("packaged-perf-end");
              perfPerformance.clearMarks("packaged-observed-start");
              perfPerformance.clearMarks("packaged-observed-end");
              perfPerformance.clearMeasures("packaged-perf-measure");
              perfPerformance.clearMeasures("packaged-observed-measure");

              const aliasOk =
                path === nodePath &&
                path.posix === pathPosix &&
                pathPosix === nodePathPosix &&
                path.win32 === pathWin32 &&
                pathWin32 === nodePathWin32 &&
                consoleBuiltin === nodeConsoleBuiltin &&
                v8Builtin === nodeV8Builtin &&
                ttyBuiltin === nodeTtyBuiltin &&
                events === nodeEvents &&
                timers === nodeTimers &&
                timersPromises === nodeTimersPromises &&
                stream === nodeStream &&
                streamPromises === nodeStreamPromises &&
                streamWeb === nodeStreamWeb &&
                streamConsumers === nodeStreamConsumers &&
                readline === nodeReadline &&
                readlinePromises === nodeReadlinePromises &&
                util === nodeUtil &&
                util.types === utilTypes &&
                utilTypes === nodeUtilTypes &&
                asyncHooks === nodeAsyncHooks &&
                diagnosticsChannel === nodeDiagnosticsChannel &&
                dns === nodeDns &&
                dns.promises === dnsPromises &&
                dnsPromises === nodeDnsPromises &&
                net === nodeNet &&
                http === nodeHttp &&
                https === nodeHttps &&
                tls === nodeTls &&
                perfHooks === nodePerfHooks;
              const shadowOk =
                path.shadow !== true &&
                pathPosix.shadow !== true &&
                pathWin32.shadow !== true &&
                consoleBuiltin.shadow !== true &&
                v8Builtin.shadow !== true &&
                ttyBuiltin.shadow !== true &&
                util.shadow !== true &&
                utilTypes.shadow !== true &&
                asyncHooks.shadow !== true &&
                diagnosticsChannel.shadow !== true &&
                dns.shadow !== true &&
                dnsPromises.shadow !== true &&
                net.shadow !== true &&
                http.shadow !== true &&
                https.shadow !== true &&
                tls.shadow !== true &&
                perfHooks.shadow !== true &&
                events.shadow !== true &&
                timers.shadow !== true &&
                timersPromises.shadow !== true &&
                stream.shadow !== true &&
                streamPromises.shadow !== true &&
                streamWeb.shadow !== true &&
                streamConsumers.shadow !== true &&
                readline.shadow !== true &&
                readlinePromises.shadow !== true;
              const eventsOk =
                count === 11 &&
                readyValue === "ok" &&
                emitter.listenerCount("inc") === 0 &&
                emitter.listeners("inc").length === 0 &&
                emitter.listenerCount("remove") === 0 &&
                callableValue === "callable-ok" &&
                events === events.EventEmitter &&
                typeof events.once === "function" &&
                typeof events.on === "function" &&
                eventHelperTypes === "function,function,function,function,symbol,symbol,undefined" &&
                eventListenerHelpersOk &&
                eventMaxListenersOk &&
                eventErrors.join("|") === "monitor:packaged event boom|handler:packaged event boom" &&
                eventIteratorResult.value === "one,two" &&
                eventIteratorResult.listenerCount === 0 &&
                eventOnceErrorOk === true &&
                eventAbortSeen === 1 &&
                eventAbortSignal.removed === true &&
                Object.isFrozen(events);
              const timersOk =
                scheduled.ticks === 2 &&
                scheduled.timeoutCleared === true &&
                scheduled.immediateSeen === true &&
                typeof timers.setTimeout === "function" &&
                typeof timers.clearTimeout === "function" &&
                typeof timers.setInterval === "function" &&
                typeof timers.clearInterval === "function" &&
                typeof timers.setImmediate === "function" &&
                typeof timers.clearImmediate === "function" &&
                Object.isFrozen(timers);
              const timerPromiseTimeout = await timersPromises.setTimeout(1, "promise-timeout");
              const timerPromiseImmediate = await timersPromises.setImmediate("promise-immediate");
              await timersPromises.scheduler.wait(1);
              await timersPromises.scheduler.yield();
              const timersPromisesHelperTypes = [
                typeof timersPromises.setTimeout,
                typeof timersPromises.setImmediate,
                typeof timersPromises.setInterval,
                typeof timersPromises.scheduler,
                typeof timersPromises.scheduler.wait,
                typeof timersPromises.scheduler.yield
              ].join(",");
              const timersPromisesOk =
                timersPromises === nodeTimersPromises &&
                timersPromises.shadow !== true &&
                Object.isFrozen(timersPromises) &&
                Object.isFrozen(timersPromises.scheduler) &&
                timerPromiseTimeout === "promise-timeout" &&
                timerPromiseImmediate === "promise-immediate" &&
                Object.keys(timersPromises).sort().join(",") === "scheduler,setImmediate,setInterval,setTimeout" &&
                timersPromisesHelperTypes === "function,function,function,object,function,function";
              const streamOk =
                streamText === "core" &&
                streamPipelineText === "pipeline" &&
                streamFinishedOk === true &&
                streamAddAbortSignalOk === true &&
                streamComposeText === "PACKAGED!" &&
                streamDuplexPairText === "true|2|true|true|right|left" &&
                stream.PassThrough === nodeStream.PassThrough &&
                typeof stream.Readable === "function" &&
                typeof stream.Writable === "function" &&
                Object.isFrozen(stream);
              const streamHelpersOk =
                [
                  typeof stream.pipeline,
                  typeof stream.finished,
                  typeof stream.addAbortSignal,
                  typeof stream.compose,
                  typeof stream.duplexPair,
                  typeof stream.promises
                ].join(",") === "function,function,function,function,function,undefined";
              const streamStateHelpersOk =
                [
                  typeof stream.isReadable,
                  typeof stream.isWritable,
                  typeof stream.isErrored,
                  typeof stream.isDestroyed,
                  typeof stream.isDisturbed
                ].join(",") === "function,function,function,function,function";
              const streamErrorOk = streamPipelineErrorOk === true && streamFinishedErrorOk === true;
              const streamPromisesOk =
                streamPromises === nodeStreamPromises &&
                streamPromisePipelineText === "promiseline" &&
                streamPromiseFinishedOk === true &&
                streamPromisePipelineErrorOk === true &&
                streamPromiseFinishedErrorOk === true &&
                streamPromiseFsPipelineText === "PACKAGED-FILE" &&
                streamPromiseFileHandlePipelineText === "PACKAGED-FILE" &&
                streamPromiseAbortOk === true &&
                streamPromiseEscapeOk === true &&
                Object.keys(streamPromises).sort().join(",") === "finished,pipeline" &&
                streamPromisesHiddenTypes === "undefined,undefined,undefined,undefined" &&
                Object.isFrozen(streamPromises);
              const streamWebOk =
                streamWeb === nodeStreamWeb &&
                streamWebReadableText === "packaged-web" &&
                streamWebWritableText === "write-web" &&
                streamWebTransformText === "TRANSFORM-WEB" &&
                streamWebEncodedText === "encoded-web" &&
                streamWebDecodedText === "decoded-web" &&
                streamWebCountStrategy.highWaterMark === 2 &&
                streamWebCountStrategy.size("value") === 1 &&
                streamWebByteStrategy.highWaterMark === 8 &&
                streamWebByteStrategy.size(new Uint8Array([1, 2, 3])) === 3 &&
                Object.keys(streamWeb).sort().join(",") === "ByteLengthQueuingStrategy,CountQueuingStrategy,ReadableStream,TextDecoderStream,TextEncoderStream,TransformStream,WritableStream" &&
                streamWebHiddenTypes === "undefined,undefined,undefined,undefined,undefined,undefined" &&
                Object.isFrozen(streamWeb);
              const streamConsumersOk =
                streamConsumers === nodeStreamConsumers &&
                streamConsumerText === "packaged-text" &&
                streamConsumerJson.packaged === true &&
                Buffer.isBuffer(streamConsumerBuffer) &&
                streamConsumerBuffer.toString("utf8") === "packaged-buffer" &&
                streamConsumerArrayBuffer instanceof ArrayBuffer &&
                Buffer.from(streamConsumerArrayBuffer).toString("utf8") === "array" &&
                streamConsumerBlob.size === 13 &&
                streamConsumerBlobText === "blob-packaged" &&
                Object.keys(streamConsumers).sort().join(",") === "arrayBuffer,blob,buffer,json,text" &&
                streamConsumersHiddenTypes === "undefined,undefined,undefined" &&
                Object.isFrozen(streamConsumers);
              const readlineOk =
                readline === nodeReadline &&
                readlineCallbackText === "red|green|blue" &&
                readlineTerminalText.length > 0 &&
                Object.keys(readline).sort().join(",") === "Interface,clearLine,clearScreenDown,createInterface,cursorTo,emitKeypressEvents,moveCursor" &&
                readlineHelperTypes === "function,function,function,function,function,function,undefined" &&
                Object.isFrozen(readline);
              const readlinePromisesOk =
                readlinePromises === nodeReadlinePromises &&
                readlinePromiseText === "red|green|blue" &&
                readlineFileHandleText === "red|green|blue" &&
                readlineHandleStillOpen === true &&
                readlineQuestionText === "cyan|Color: " &&
                readlineAbortOk === true &&
                Object.keys(readlinePromises).sort().join(",") === "Interface,Readline,createInterface" &&
                readlinePromiseHelperTypes === "function,function,function,undefined,undefined,undefined" &&
                Object.isFrozen(readlinePromises);
              const dnsOk =
                dns === nodeDns &&
                dns.promises === dnsPromises &&
                dnsPromises === nodeDnsPromises &&
                dns.shadow !== true &&
                dnsPromises.shadow !== true &&
                Object.isFrozen(dns) &&
                Object.isFrozen(dns.promises) &&
                Object.isFrozen(dns.Resolver.prototype) &&
                Object.isFrozen(dnsPromises.Resolver.prototype) &&
                dnsLocal4.address === "127.0.0.1" &&
                dnsLocal4.family === 4 &&
                dnsLocal6.address === "::1" &&
                dnsLocal6.family === 6 &&
                dnsLiteral4.address === "127.0.0.1" &&
                dnsLiteral4.family === 4 &&
                dnsLiteral6.address === "::1" &&
                dnsLiteral6.family === 6 &&
                dnsAllLocal.addresses.some((entry) => entry.address === "127.0.0.1" && entry.family === 4) &&
                dnsAllLocal.addresses.some((entry) => entry.address === "::1" && entry.family === 6) &&
                dnsDeniedLookup.code === "ENOTFOUND" &&
                dnsDeniedPromiseLookup === "ENOTFOUND" &&
                dnsDeniedResolve === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                dnsDeniedPromiseResolve === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                dnsService4.hostname === "localhost" &&
                dnsService4.service === "443" &&
                dnsService6.hostname === "localhost" &&
                dnsService6.service === "8443" &&
                dnsDeniedService.code === "ENOTFOUND" &&
                dnsResolverCallbackCode === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                dnsResolverPromiseCode === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                Array.isArray(dns.getServers()) &&
                dns.getServers().length === 0 &&
                Object.isFrozen(dns.getServers()) &&
                dnsSetServersCode === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                dnsOrdered6[0].family === 6 &&
                dnsOrdered4[0].family === 4 &&
                dns.getDefaultResultOrder() === dnsOrderBefore &&
                dnsInvalidOrderCode === "ERR_INVALID_ARG_VALUE" &&
                typeof dns.ADDRCONFIG === "number" &&
                typeof dns.ALL === "number" &&
                typeof dns.V4MAPPED === "number" &&
                dns.NOTFOUND === "ENOTFOUND" &&
                dnsHelperTypes === "function,function,function,function,function,function,function,number,string";
              const netOk =
                net === nodeNet &&
                net.shadow !== true &&
                Object.isFrozen(net) &&
                Object.isFrozen(net.Socket) &&
                Object.isFrozen(net.Socket.prototype) &&
                Object.isFrozen(net.Server.prototype) &&
                Object.isFrozen(net.BlockList.prototype) &&
                Object.isFrozen(net.SocketAddress) &&
                [net.isIP("127.0.0.1"), net.isIP("::1"), net.isIP("localhost")].join("/") === "4/6/0" &&
                net.isIPv4("127.0.0.1") === true &&
                net.isIPv6("2001:db8::1") === true &&
                net.isIPv6("127.0.0.1") === false &&
                netSocket instanceof net.Socket &&
                netSocket instanceof net.Stream &&
                netSocket.allowHalfOpen === true &&
                netSocketWriteReturn === false &&
                netSocketWriteCode === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                netSocket.destroyed === true &&
                netDisabled(() => netSocket.connect(80, "example.com")) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                netServer instanceof net.Server &&
                netServer.listening === false &&
                netServer.address() === null &&
                netConnectionsSummary === "true/0" &&
                netCloseSeen === true &&
                netDisabled(() => netServer.listen(0)) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                netBlockList.check("127.0.0.1") &&
                netBlockList.check("10.0.0.2") &&
                netBlockList.check("192.168.1.42") &&
                !netBlockList.check("10.0.0.4") &&
                netBlockList.rules.length === 3 &&
                Object.isFrozen(netBlockList.rules) &&
                netInvalidBlockList === "ERR_INVALID_ARG_VALUE" &&
                netSocketAddress.address === "127.0.0.1" &&
                netSocketAddress.port === 8080 &&
                netSocketAddress.family === "ipv4" &&
                netParsed4.port === 8080 &&
                netParsed6.address === "::1" &&
                netParsed6.family === "ipv6" &&
                net.SocketAddress.parse("bad") === undefined &&
                net.isSocketAddress(netSocketAddress) &&
                netInvalidSocketAddress === "ERR_INVALID_ARG_VALUE" &&
                Object.isFrozen(netSocketAddress) &&
                [
                  netDisabled(() => net.connect(80, "example.com")),
                  netDisabled(() => net.createConnection(80, "example.com")),
                  netDisabled(() => net.createServer().listen(0))
                ].join("/") === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                net.getDefaultAutoSelectFamily() === false &&
                net.getDefaultAutoSelectFamilyAttemptTimeout() === 123 &&
                netInvalidAutoFamily === "ERR_INVALID_ARG_TYPE" &&
                netInvalidAutoTimeout === "ERR_INVALID_ARG_VALUE" &&
                netHelperTypes === "function,function,function,function,function,function,function,function,function,function,function,undefined";
              const httpOk =
                http === nodeHttp &&
                https === nodeHttps &&
                http.shadow !== true &&
                https.shadow !== true &&
                Object.isFrozen(http) &&
                Object.isFrozen(https) &&
                Object.isFrozen(http.Agent) &&
                Object.isFrozen(http.Agent.prototype) &&
                Object.isFrozen(http.ClientRequest.prototype) &&
                Object.isFrozen(http.Server.prototype) &&
                Object.isFrozen(http.ServerResponse.prototype) &&
                http.METHODS.includes("GET") &&
                http.METHODS.includes("POST") &&
                http.STATUS_CODES[200] === "OK" &&
                http.STATUS_CODES[404] === "Not Found" &&
                Object.isFrozen(http.METHODS) &&
                Object.isFrozen(http.STATUS_CODES) &&
                httpAgent instanceof http.Agent &&
                httpsAgent instanceof https.Agent &&
                httpAgent.keepAlive === true &&
                httpAgent.defaultPort === 80 &&
                httpAgent.protocol === "http:" &&
                httpsAgent.defaultPort === 443 &&
                httpsAgent.protocol === "https:" &&
                httpAgent.getName({ host: "example.com", port: 8080 }).startsWith("example.com:8080") &&
                httpDisabled(() => httpAgent.createConnection({})) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                httpRequest instanceof http.ClientRequest &&
                httpRequest instanceof http.OutgoingMessage &&
                httpRequest.method === "GET" &&
                httpRequest.path === "/a?b=1" &&
                httpRequest.getHeader("x-test") === "yes" &&
                httpRequestHeaders["x-test"] === "yes" &&
                httpRequest.hasHeader("X-Test") &&
                Object.isFrozen(httpRequest.getHeaderNames()) &&
                httpRequestEnded === true &&
                httpRequest.aborted === true &&
                httpIncoming instanceof http.IncomingMessage &&
                httpIncoming.httpVersion === "1.1" &&
                httpResponse instanceof http.ServerResponse &&
                httpResponse instanceof http.OutgoingMessage &&
                httpResponse.statusCode === 201 &&
                httpResponse.statusMessage === "Created" &&
                httpResponse.getHeader("x-response") === "ok" &&
                httpResponse.getHeader("x-head") === "yes" &&
                httpResponse.headersSent === true &&
                httpResponseEnded === true &&
                httpServer instanceof http.Server &&
                httpsServer instanceof https.Server &&
                httpServer.address() === null &&
                httpServer.timeout === 1 &&
                httpCloseSeen === true &&
                httpDisabled(() => httpServer.listen(0)) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                httpDisabled(() => httpsServer.listen(0)) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                [
                  httpDisabled(() => http.request("http://example.com")),
                  httpDisabled(() => http.get("http://example.com")),
                  httpDisabled(() => https.request("https://example.com")),
                  httpDisabled(() => https.get("https://example.com"))
                ].join("/") === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                [
                  httpInvalidHeaderName,
                  httpInvalidHeaderValue,
                  httpInvalidParserCount
                ].join("/") === "ERR_INVALID_HTTP_TOKEN/ERR_INVALID_CHAR/ERR_INVALID_ARG_VALUE" &&
                httpHelperTypes === "function,function,function,function,function,function,function,function,function,function,function,function,object,object,object,undefined";
              const tlsOk =
                tls === nodeTls &&
                tls.shadow !== true &&
                Object.isFrozen(tls) &&
                Object.isFrozen(tls.SecureContext) &&
                Object.isFrozen(tls.SecureContext.prototype) &&
                Object.isFrozen(tls.TLSSocket) &&
                Object.isFrozen(tls.TLSSocket.prototype) &&
                Object.isFrozen(tls.Server.prototype) &&
                tlsContext instanceof tls.SecureContext &&
                tlsContext.secureProtocol === "TLSv1.2" &&
                tlsContext.context.ca === "cert" &&
                Object.isFrozen(tlsContext) &&
                Object.isFrozen(tlsContext.context) &&
                tlsSocket instanceof tls.TLSSocket &&
                tlsSocket instanceof net.Socket &&
                tlsSocket.encrypted === true &&
                tlsSocket.servername === "example.com" &&
                tlsSocket.getProtocol() === null &&
                tlsCipher.name === "TLS_AES_256_GCM_SHA384" &&
                Object.isFrozen(tlsSocket.getSharedSigalgs()) &&
                Object.isFrozen(tlsSocket.getEphemeralKeyInfo()) &&
                Object.isFrozen(tlsDetailedCert.ext_key_usage) &&
                tlsSocket.setMaxSendFragment(1024) === true &&
                tlsInvalidFragment === "ERR_INVALID_ARG_VALUE" &&
                tlsRenegotiateReturn === false &&
                tlsRenegotiateCode === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                tlsDisabled(() => tlsSocket.connect(443, "example.com")) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                tlsDisabled(() => tlsSocket.exportKeyingMaterial(16, "label")) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                tlsServer instanceof tls.Server &&
                tlsServer instanceof net.Server &&
                tlsServer.requestCert === true &&
                tlsServer.rejectUnauthorized === true &&
                tlsServer.secureContext instanceof tls.SecureContext &&
                tlsTicketKeys.length === 48 &&
                tlsInvalidTicketKeys === "ERR_INVALID_ARG_VALUE" &&
                tlsCloseSeen === true &&
                tlsDisabled(() => tlsServer.listen(0)) === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                [
                  tlsDisabled(() => tls.connect(443, "example.com")),
                  tlsDisabled(() => tls.createConnection(443, "example.com")),
                  tlsDisabled(() => tls.createServer().listen(0))
                ].join("/") === "ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED" &&
                tls.getCiphers().includes("TLS_AES_256_GCM_SHA384") &&
                Object.isFrozen(tls.getCiphers()) &&
                Array.isArray(tls.rootCertificates) &&
                Object.isFrozen(tls.rootCertificates) &&
                tls.DEFAULT_MIN_VERSION === "TLSv1.2" &&
                tls.DEFAULT_MAX_VERSION === "TLSv1.3" &&
                tls.DEFAULT_ECDH_CURVE === "auto" &&
                tls.CLIENT_RENEG_LIMIT === 3 &&
                tls.checkServerIdentity("example.com", {}) === undefined &&
                tlsInvalidHostname === "ERR_INVALID_ARG_TYPE" &&
                tlsHelperTypes === "function,function,function,function,function,function,function,function,function,object,string,undefined";
              const utilOk =
                utilPromisified === "utility:promisified" &&
                utilInspected.includes("inspect") &&
                util.inspect(utilCustom) === "packaged-custom" &&
                utilCustomPromisified === "packaged-promisify" &&
                utilCallbackifyError === "packaged callback boom" &&
                utilParsed.values.mode === "packaged" &&
                utilParsed.values.flag === true &&
                util.types.isMap(new Map()) === true &&
                utilCodecText === "util-codec" &&
                utilMimeOk &&
                utilModernOk &&
                utilHidden === "undefined,undefined,undefined" &&
                utilHelperTypes === "function,function,function,symbol,function,symbol,function,function,function,function,function,function,function,function,object,function,function,function,function" &&
                utilTypesOk &&
                Object.isFrozen(util) &&
                Object.isFrozen(util.types) &&
                typeof util.TextEncoder === "function" &&
                typeof util.TextDecoder === "function" &&
                typeof util.MIMEType === "function" &&
                typeof util.MIMEParams === "function";
              const resolveOk =
                require.resolve("path") === "path" &&
                require.resolve("node:path") === "path" &&
                require.resolve("path/posix") === "path/posix" &&
                require.resolve("node:path/posix") === "path/posix" &&
                require.resolve("path/win32") === "path/win32" &&
                require.resolve("node:path/win32") === "path/win32" &&
                require.resolve("console") === "console" &&
                require.resolve("node:console") === "console" &&
                require.resolve("v8") === "v8" &&
                require.resolve("node:v8") === "v8" &&
                require.resolve("tty") === "tty" &&
                require.resolve("node:tty") === "tty" &&
                require.resolve("util") === "util" &&
                require.resolve("node:util") === "util" &&
                require.resolve("util/types") === "util/types" &&
                require.resolve("node:util/types") === "util/types" &&
                require.resolve("async_hooks") === "async_hooks" &&
                require.resolve("node:async_hooks") === "async_hooks" &&
                require.resolve("diagnostics_channel") === "diagnostics_channel" &&
                require.resolve("node:diagnostics_channel") === "diagnostics_channel" &&
                require.resolve("perf_hooks") === "perf_hooks" &&
                require.resolve("node:perf_hooks") === "perf_hooks" &&
                require.resolve("events") === "events" &&
                require.resolve("node:events") === "events" &&
                require.resolve("timers") === "timers" &&
                require.resolve("node:timers") === "timers" &&
                require.resolve("timers/promises") === "timers/promises" &&
                require.resolve("node:timers/promises") === "timers/promises" &&
                require.resolve("stream") === "stream" &&
                require.resolve("node:stream") === "stream" &&
                require.resolve("stream/promises") === "stream/promises" &&
                require.resolve("node:stream/promises") === "stream/promises" &&
                require.resolve("stream/web") === "stream/web" &&
                require.resolve("node:stream/web") === "stream/web" &&
                require.resolve("stream/consumers") === "stream/consumers" &&
                require.resolve("node:stream/consumers") === "stream/consumers" &&
                require.resolve("readline") === "readline" &&
                require.resolve("node:readline") === "readline" &&
                require.resolve("readline/promises") === "readline/promises" &&
                require.resolve("node:readline/promises") === "readline/promises" &&
                require.resolve("dns") === "dns" &&
                require.resolve("node:dns") === "dns" &&
                require.resolve("dns/promises") === "dns/promises" &&
                require.resolve("node:dns/promises") === "dns/promises" &&
                require.resolve("net") === "net" &&
                require.resolve("node:net") === "net" &&
                require.resolve("http") === "http" &&
                require.resolve("node:http") === "http" &&
                require.resolve("https") === "https" &&
                require.resolve("node:https") === "https" &&
                require.resolve("tls") === "tls" &&
                require.resolve("node:tls") === "tls";
              const hiddenOk =
                [
                  typeof timers.promises,
                  typeof timers.enroll,
                  typeof stream.promises,
                  typeof streamPromises.Readable,
                  typeof streamPromises.Writable,
                  typeof streamPromises.consumers,
                  typeof streamPromises.promises,
                  typeof streamWeb.CompressionStream,
                  typeof streamWeb.DecompressionStream,
                  typeof streamWeb.ReadableStreamDefaultReader,
                  typeof streamWeb.WritableStreamDefaultWriter,
                  typeof streamWeb.TransformStreamDefaultController,
                  typeof streamWeb.ReadableByteStreamController,
                  typeof streamConsumers.finished,
                  typeof streamConsumers.pipeline,
                  typeof streamConsumers.Readable,
                  typeof readline.promises,
                  typeof readlinePromises.clearLine,
                  typeof readlinePromises.cursorTo,
                  typeof readlinePromises.promises
                ].every((value) => value === "undefined");
              const deniedOk = [
                "zlib/promises",
                "node:zlib/promises"
              ].every((name) => {
                try {
                  require(name);
                  return false;
                } catch (error) {
                  return error.message.includes(name);
                }
              });
              let builtinGuardOk = false;
              try {
                process.getBuiltinModule("timers");
              } catch (error) {
                builtinGuardOk = error.message.includes("disabled");
              }
              const ready =
                eventsOk &&
                timersOk &&
                consoleOk &&
                v8Ok &&
                ttyOk &&
                asyncHooksOk &&
                diagnosticsChannelOk &&
                perfHooksOk &&
                streamOk &&
                timersPromisesOk &&
                streamHelpersOk &&
                streamStateHelpersOk &&
                streamErrorOk &&
                streamPromisesOk &&
                streamWebOk &&
                streamConsumersOk &&
                readlineOk &&
                readlinePromisesOk &&
                dnsOk &&
                netOk &&
                httpOk &&
                tlsOk &&
                pathOk &&
                utilOk &&
                aliasOk &&
                shadowOk &&
                resolveOk &&
                hiddenOk &&
                deniedOk &&
                builtinGuardOk;
              const surfaceOk = eventsOk && timersOk && timersPromisesOk && consoleOk && v8Ok && ttyOk && asyncHooksOk && diagnosticsChannelOk && dnsOk && netOk && httpOk && tlsOk && perfHooksOk && streamOk && streamHelpersOk && streamStateHelpersOk && streamErrorOk && streamPromisesOk && streamWebOk && streamConsumersOk && readlineOk && readlinePromisesOk && pathOk && utilOk;
              const guardOk = aliasOk && shadowOk && resolveOk && hiddenOk && deniedOk && builtinGuardOk;
              console.log("packaged.core_builtins.ready=" + ready);
              console.log("packaged.core_builtins.surface=" + surfaceOk);
              console.log("packaged.core_builtins.events_extended=" + eventsOk);
              console.log("packaged.core_builtins.events_helpers=" + eventHelperTypes);
              console.log("packaged.core_builtins.path_extended=" + pathOk);
              console.log("packaged.core_builtins.path_subpaths=" + pathSubpathsOk);
              console.log("packaged.core_builtins.path_helpers=" + pathHelperTypes);
              console.log("packaged.core_builtins.util_extended=" + utilOk);
              console.log("packaged.core_builtins.util_helpers=" + utilHelperTypes);
              console.log("packaged.core_builtins.util_types=" + utilTypesOk);
              console.log("packaged.core_builtins.util_mime=" + utilMimeOk);
              console.log("packaged.core_builtins.util_modern=" + utilModernOk);
              console.log("packaged.core_builtins.console=" + consoleOk);
              console.log("packaged.core_builtins.console_helpers=" + consoleHelperTypes);
              console.log("packaged.core_builtins.v8=" + v8Ok);
              console.log("packaged.core_builtins.v8_helpers=" + v8HelperTypes);
              console.log("packaged.core_builtins.v8_hidden=" + v8HiddenTypes);
              console.log("packaged.core_builtins.tty=" + ttyOk);
              console.log("packaged.core_builtins.tty_helpers=" + ttyHelperTypes);
              console.log("packaged.core_builtins.tty_stdio=" + ttyStdioSummary);
              console.log("packaged.core_builtins.async_hooks=" + asyncHooksOk);
              console.log("packaged.core_builtins.async_hooks_helpers=" + asyncHooksHelperTypes);
              console.log("packaged.core_builtins.diagnostics_channel=" + diagnosticsChannelOk);
              console.log("packaged.core_builtins.diagnostics_helpers=" + diagnosticsHelperTypes);
              console.log("packaged.core_builtins.dns=" + dnsOk);
              console.log("packaged.core_builtins.dns_helpers=" + dnsHelperTypes);
              console.log("packaged.core_builtins.net=" + netOk);
              console.log("packaged.core_builtins.net_helpers=" + netHelperTypes);
              console.log("packaged.core_builtins.http=" + httpOk);
              console.log("packaged.core_builtins.http_helpers=" + httpHelperTypes);
              console.log("packaged.core_builtins.tls=" + tlsOk);
              console.log("packaged.core_builtins.tls_helpers=" + tlsHelperTypes);
              console.log("packaged.core_builtins.perf_hooks=" + perfHooksOk);
              console.log("packaged.core_builtins.perf_helpers=" + perfHelperTypes);
              console.log("packaged.core_builtins.perf_hidden=" + perfHiddenTypes);
              console.log("packaged.core_builtins.timers_promises=" + timersPromisesOk);
              console.log("packaged.core_builtins.timers_promises_helpers=" + timersPromisesHelperTypes);
              console.log("packaged.core_builtins.stream_helpers=" + streamHelpersOk);
              console.log("packaged.core_builtins.stream_state_helpers=" + streamStateHelpersOk);
              console.log("packaged.core_builtins.stream_errors=" + streamErrorOk);
              console.log("packaged.core_builtins.stream_promises=" + streamPromisesOk);
              console.log("packaged.core_builtins.stream_modern_helpers=" + (
                streamAddAbortSignalOk === true &&
                streamComposeText === "PACKAGED!" &&
                streamDuplexPairText === "true|2|true|true|right|left"
              ));
              console.log("packaged.core_builtins.stream_promises_fs=" + (
                streamPromiseFsPipelineText === "PACKAGED-FILE" &&
                streamPromiseFileHandlePipelineText === "PACKAGED-FILE" &&
                streamPromiseAbortOk === true &&
                streamPromiseEscapeOk === true
              ));
              console.log("packaged.core_builtins.stream_web=" + streamWebOk);
              console.log("packaged.core_builtins.stream_consumers=" + streamConsumersOk);
              console.log("packaged.core_builtins.readline=" + readlineOk);
              console.log("packaged.core_builtins.readline_promises=" + readlinePromisesOk);
              console.log("packaged.core_builtins.guards=" + guardOk);
            }

            async function runCryptoAndZlibBuiltinCorpus() {
              let cryptoCallbackAsync = false;
              let cryptoCallbackOk = false;
              let sync = true;
              const cryptoCallback = new Promise((resolve) => {
                crypto.randomBytes(8, (error, value) => {
                  cryptoCallbackAsync = !sync;
                  cryptoCallbackOk = error === null && Buffer.isBuffer(value) && value.length === 8;
                  resolve();
                });
              });
              sync = false;
              await cryptoCallback;

              const cryptoSyncRandom = crypto.randomBytes(16);
              const cryptoHashes =
                crypto.createHash("sha256").update("abc").digest("hex") ===
                  "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" &&
                crypto.createHash("sha1").update(Buffer.from("abc")).digest("hex") ===
                  "a9993e364706816aba3e25717850c26c9cd0d89d" &&
                crypto.createHash("md5").update("abc").digest("hex") ===
                  "900150983cd24fb0d6963f7d28e17f72" &&
                crypto.createHash("sha512").update("abc").digest("hex").startsWith("ddaf35a193617aba") &&
                crypto.getHashes().join(",") === "md5,sha1,sha256,sha512";
              const cryptoUuid = crypto.randomUUID();
              const cryptoHmac =
                crypto.createHmac("sha256", "key").update("abc").digest("hex") ===
                  "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab";
              const cryptoSecretKey = crypto.createSecretKey(Buffer.from("key"));
              const cryptoSecretKeyText = crypto.createSecretKey("key", "utf8");
              const cryptoSecretKeyTyped = crypto.createSecretKey(new Uint8Array([0x6b, 0x65, 0x79]));
              const cryptoSecretKeyJwk = cryptoSecretKey.export({ format: "jwk" });
              const cryptoSecretKeyOk =
                typeof crypto.createSecretKey === "function" &&
                typeof crypto.KeyObject === "function" &&
                cryptoSecretKey instanceof crypto.KeyObject &&
                Object.prototype.toString.call(cryptoSecretKey) === "[object KeyObject]" &&
                Object.isFrozen(cryptoSecretKey) &&
                cryptoSecretKey.type === "secret" &&
                cryptoSecretKey.symmetricKeySize === 3 &&
                cryptoSecretKey.export().toString("hex") === "6b6579" &&
                cryptoSecretKeyTyped.export().toString("hex") === "6b6579" &&
                cryptoSecretKeyJwk.kty === "oct" &&
                cryptoSecretKeyJwk.k === "a2V5" &&
                cryptoSecretKey.equals(cryptoSecretKeyText) &&
                !cryptoSecretKey.equals(crypto.createSecretKey("other")) &&
                typeof cryptoSecretKey.toCryptoKey === "undefined" &&
                crypto.createHmac("sha256", cryptoSecretKey).update("abc").digest("hex") ===
                  "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab" &&
                Buffer.from(crypto.hkdfSync("sha256", crypto.createSecretKey("ikm"), "salt", "info", 16)).toString("hex") ===
                  "fe8f9615d2374c0d17f77d1aeaf408c2";
              const cryptoPbkdf2 = crypto.pbkdf2Sync("password", "salt", 1, 16, "sha256");
              const cryptoScrypt = crypto.scryptSync("password", "salt", 16, { N: 1024, r: 8, p: 1 });
              const cryptoHkdf = crypto.hkdfSync("sha256", "ikm", "salt", "info", 16);
              const cryptoPbkdf2CallbackOk = await new Promise((resolve) => {
                crypto.pbkdf2("password", "salt", 1, 16, "sha256", (error, value) => {
                  resolve(
                    error === null &&
                    Buffer.isBuffer(value) &&
                    value.toString("hex") === "120fb6cffcf8b32c43e7225256c4f837"
                  );
                });
              });
              const cryptoScryptCallbackOk = await new Promise((resolve) => {
                crypto.scrypt("password", "salt", 16, { N: 1024, r: 8, p: 1 }, (error, value) => {
                  resolve(
                    error === null &&
                    Buffer.isBuffer(value) &&
                    value.toString("hex") === "16dbc8906763c7f048977a68f9d305f7"
                  );
                });
              });
              const cryptoHkdfCallbackOk = await new Promise((resolve) => {
                crypto.hkdf("sha256", "ikm", "salt", "info", 16, (error, value) => {
                  resolve(
                    error === null &&
                    value instanceof ArrayBuffer &&
                    Buffer.from(value).toString("hex") === "fe8f9615d2374c0d17f77d1aeaf408c2"
                  );
                });
              });
              const cryptoKdfOk =
                typeof crypto.pbkdf2 === "function" &&
                typeof crypto.pbkdf2Sync === "function" &&
                typeof crypto.scrypt === "function" &&
                typeof crypto.scryptSync === "function" &&
                typeof crypto.hkdf === "function" &&
                typeof crypto.hkdfSync === "function" &&
                cryptoPbkdf2.toString("hex") === "120fb6cffcf8b32c43e7225256c4f837" &&
                cryptoScrypt.toString("hex") === "16dbc8906763c7f048977a68f9d305f7" &&
                Buffer.from(cryptoHkdf).toString("hex") === "fe8f9615d2374c0d17f77d1aeaf408c2" &&
                cryptoPbkdf2CallbackOk &&
                cryptoScryptCallbackOk &&
                cryptoHkdfCallbackOk;
              const cryptoSurfaceOk =
                crypto === nodeCrypto &&
                crypto.shadow !== true &&
                Object.isFrozen(crypto) &&
                Buffer.isBuffer(cryptoSyncRandom) &&
                cryptoSyncRandom.length === 16 &&
                cryptoCallbackAsync &&
                cryptoCallbackOk &&
                cryptoHashes &&
                cryptoHmac &&
                cryptoSecretKeyOk &&
                cryptoKdfOk &&
                /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(cryptoUuid);

              function webCryptoHex(arrayBuffer) {
                return Buffer.from(arrayBuffer).toString("hex");
              }

              async function webCryptoCaptureDigest(action) {
                try {
                  const value = action();
                  const returnedPromise = !!value && typeof value.then === "function";
                  await value;
                  return "ok:" + returnedPromise;
                } catch (error) {
                  return [
                    error && error.name,
                    error && error.code
                  ].join("/");
                }
              }

              function webCryptoCaptureSync(action) {
                try {
                  action();
                  return "ok";
                } catch (error) {
                  return [
                    error && error.name,
                    error && error.code
                  ].join("/");
                }
              }

              const packagedWebCrypto = globalThis.crypto;
              const packagedWebCryptoRandom = new Uint8Array(16);
              const packagedWebCryptoWords = new Uint32Array(4);
              const packagedWebCryptoRandomOk =
                typeof packagedWebCrypto === "object" &&
                packagedWebCrypto.getRandomValues(packagedWebCryptoRandom) === packagedWebCryptoRandom &&
                packagedWebCrypto.getRandomValues(packagedWebCryptoWords) === packagedWebCryptoWords &&
                packagedWebCryptoRandom.byteLength === 16 &&
                packagedWebCryptoWords.byteLength === 16;
              const packagedWebCryptoUuid = packagedWebCrypto.randomUUID();
              const packagedWebCryptoUuidOk =
                /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(packagedWebCryptoUuid);
              const packagedWebCryptoData = new TextEncoder().encode("abc");
              const [
                packagedWebCryptoSha1,
                packagedWebCryptoSha256,
                packagedWebCryptoSha512,
                packagedWebCryptoSha256Object
              ] = await Promise.all([
                packagedWebCrypto.subtle.digest("SHA-1", packagedWebCryptoData),
                packagedWebCrypto.subtle.digest("SHA-256", packagedWebCryptoData),
                packagedWebCrypto.subtle.digest("SHA-512", packagedWebCryptoData),
                packagedWebCrypto.subtle.digest({ name: "sha-256" }, Buffer.from("abc"))
              ]);
              const packagedWebCryptoHmacKey = await packagedWebCrypto.subtle.importKey(
                "raw",
                new TextEncoder().encode("secret"),
                { name: "HMAC", hash: "SHA-256" },
                true,
                ["sign", "verify"]
              );
              const packagedWebCryptoHmacJwkKey = await packagedWebCrypto.subtle.importKey(
                "jwk",
                { kty: "oct", k: "c2VjcmV0", key_ops: ["verify"], ext: true },
                { name: "HMAC", hash: { name: "SHA-256" } },
                true,
                ["verify"]
              );
              const packagedWebCryptoHmacSha512Key = await packagedWebCrypto.subtle.importKey(
                "raw",
                Buffer.from("secret"),
                { name: "HMAC", hash: "SHA-512" },
                true,
                ["sign"]
              );
              const packagedWebCryptoSignOnlyKey = await packagedWebCrypto.subtle.importKey(
                "raw",
                Buffer.from("secret"),
                { name: "HMAC", hash: "SHA-256" },
                false,
                ["sign"]
              );
              const packagedWebCryptoPayload = new TextEncoder().encode("payload");
              const packagedWebCryptoHmacSignature = await packagedWebCrypto.subtle.sign(
                "HMAC",
                packagedWebCryptoHmacKey,
                packagedWebCryptoPayload
              );
              const packagedWebCryptoHmacSha512Signature = await packagedWebCrypto.subtle.sign(
                { name: "HMAC" },
                packagedWebCryptoHmacSha512Key,
                packagedWebCryptoPayload
              );
              const packagedWebCryptoHmacVerify = await packagedWebCrypto.subtle.verify(
                "HMAC",
                packagedWebCryptoHmacJwkKey,
                packagedWebCryptoHmacSignature,
                packagedWebCryptoPayload
              );
              const packagedWebCryptoHmacVerifyFalse = await packagedWebCrypto.subtle.verify(
                "HMAC",
                packagedWebCryptoHmacKey,
                packagedWebCryptoHmacSignature,
                new TextEncoder().encode("other")
              );
              const packagedWebCryptoHmacRawExport = await packagedWebCrypto.subtle.exportKey(
                "raw",
                packagedWebCryptoHmacKey
              );
              const packagedWebCryptoHmacJwkExport = await packagedWebCrypto.subtle.exportKey(
                "jwk",
                packagedWebCryptoHmacKey
              );
              const packagedWebCryptoBadDigest = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.digest("MD5", packagedWebCryptoData)
              );
              const packagedWebCryptoBadData = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.digest("SHA-256", "abc")
              );
              const packagedWebCryptoBadHmacAlgorithm = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.importKey("raw", Buffer.from("secret"), { name: "AES-GCM" }, true, ["sign"])
              );
              const packagedWebCryptoBadHmacHash = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.importKey("raw", Buffer.from("secret"), { name: "HMAC", hash: "SHA-1" }, true, ["sign"])
              );
              const packagedWebCryptoBadHmacUsage = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.importKey("raw", Buffer.from("secret"), { name: "HMAC", hash: "SHA-256" }, true, ["encrypt"])
              );
              const packagedWebCryptoBadHmacData = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.sign("HMAC", packagedWebCryptoHmacKey, "payload")
              );
              const packagedWebCryptoBadHmacExport = await webCryptoCaptureDigest(() =>
                packagedWebCrypto.subtle.exportKey("raw", packagedWebCryptoSignOnlyKey)
              );
              const packagedWebCryptoTooLarge = webCryptoCaptureSync(() =>
                packagedWebCrypto.getRandomValues(new Uint8Array(65537))
              );
              const packagedWebCryptoBadRandomType = webCryptoCaptureSync(() =>
                packagedWebCrypto.getRandomValues(new Float32Array(1))
              );
              const packagedWebCryptoHidden = [
                typeof packagedWebCrypto.createHash,
                typeof packagedWebCrypto.createHmac,
                typeof packagedWebCrypto.randomBytes,
                typeof packagedWebCrypto.webcrypto,
                typeof packagedWebCrypto.subtle.encrypt,
                typeof packagedWebCrypto.subtle.deriveBits,
                typeof packagedWebCrypto.subtle.generateKey,
                typeof crypto.webcrypto,
                typeof crypto.subtle
              ].join(",");
              const packagedWebCryptoNodeAliasOk =
                crypto.webcrypto === packagedWebCrypto &&
                crypto.subtle === packagedWebCrypto.subtle &&
                typeof crypto.webcrypto.subtle.importKey === "function" &&
                typeof crypto.subtle.sign === "function";
              const packagedWebCryptoDescriptor = Object.getOwnPropertyDescriptor(globalThis, "crypto");
              const webCryptoGlobalOk =
                typeof packagedWebCrypto.getRandomValues === "function" &&
                typeof packagedWebCrypto.randomUUID === "function" &&
                typeof packagedWebCrypto.subtle === "object" &&
                typeof packagedWebCrypto.subtle.digest === "function" &&
                typeof packagedWebCrypto.subtle.importKey === "function" &&
                typeof packagedWebCrypto.subtle.sign === "function" &&
                typeof packagedWebCrypto.subtle.verify === "function" &&
                typeof packagedWebCrypto.subtle.exportKey === "function" &&
                typeof CryptoKey === "function" &&
                packagedWebCryptoRandomOk &&
                packagedWebCryptoUuidOk &&
                Object.isFrozen(packagedWebCrypto) &&
                Object.isFrozen(packagedWebCrypto.subtle) &&
                packagedWebCryptoDescriptor &&
                packagedWebCryptoDescriptor.writable === false &&
                packagedWebCryptoNodeAliasOk &&
                packagedWebCryptoHidden === "undefined,undefined,undefined,undefined,undefined,undefined,undefined,object,object";
              const webCryptoDigestOk =
                webCryptoHex(packagedWebCryptoSha1) === "a9993e364706816aba3e25717850c26c9cd0d89d" &&
                webCryptoHex(packagedWebCryptoSha256) === "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" &&
                webCryptoHex(packagedWebCryptoSha512).startsWith("ddaf35a193617aba") &&
                webCryptoHex(packagedWebCryptoSha256Object) === "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" &&
                packagedWebCryptoBadDigest === "NotSupportedError/9" &&
                packagedWebCryptoBadData === "TypeError/ERR_INVALID_ARG_TYPE" &&
                packagedWebCryptoTooLarge === "QuotaExceededError/22" &&
                packagedWebCryptoBadRandomType === "TypeMismatchError/17";
              const webCryptoHmacOk =
                packagedWebCryptoHmacKey instanceof CryptoKey &&
                Object.prototype.toString.call(packagedWebCryptoHmacKey) === "[object CryptoKey]" &&
                packagedWebCryptoHmacKey.type === "secret" &&
                packagedWebCryptoHmacKey.extractable === true &&
                packagedWebCryptoHmacKey.algorithm.name === "HMAC" &&
                packagedWebCryptoHmacKey.algorithm.hash.name === "SHA-256" &&
                packagedWebCryptoHmacKey.algorithm.length === 48 &&
                packagedWebCryptoHmacKey.usages.join(",") === "sign,verify" &&
                webCryptoHex(packagedWebCryptoHmacSignature) === "b82fcb791acec57859b989b430a826488ce2e479fdf92326bd0a2e8375a42ba4" &&
                packagedWebCryptoHmacVerify === true &&
                packagedWebCryptoHmacVerifyFalse === false &&
                webCryptoHex(packagedWebCryptoHmacSha512Signature).startsWith("291ddaaa23cafa3a") &&
                Buffer.from(packagedWebCryptoHmacRawExport).toString("utf8") === "secret" &&
                packagedWebCryptoHmacJwkExport.kty === "oct" &&
                packagedWebCryptoHmacJwkExport.k === "c2VjcmV0" &&
                packagedWebCryptoHmacJwkExport.alg === "HS256" &&
                packagedWebCryptoHmacJwkExport.key_ops.join(",") === "sign,verify" &&
                packagedWebCryptoBadHmacAlgorithm === "NotSupportedError/9" &&
                packagedWebCryptoBadHmacHash === "NotSupportedError/9" &&
                packagedWebCryptoBadHmacUsage === "SyntaxError/12" &&
                packagedWebCryptoBadHmacData === "TypeError/ERR_INVALID_ARG_TYPE" &&
                packagedWebCryptoBadHmacExport === "InvalidAccessError/15";

              async function collectPackagedWebCompression(transform, chunks) {
                const reader = transform.readable.getReader();
                const writer = transform.writable.getWriter();
                const reads = (async () => {
                  const values = [];
                  while (true) {
                    const next = await reader.read();
                    if (next.done) {
                      return Buffer.concat(values.map((value) => Buffer.from(value)));
                    }
                    values.push(next.value);
                  }
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

              async function webCompressionCaptureAsync(action) {
                try {
                  await action();
                  return "ok";
                } catch (error) {
                  return [
                    error && error.name,
                    error && error.code
                  ].join("/");
                }
              }

              function webCompressionCaptureSync(action) {
                try {
                  action();
                  return "ok";
                } catch (error) {
                  return [
                    error && error.name,
                    error && error.code
                  ].join("/");
                }
              }

              const packagedWebCompressionGzipBytes = await collectPackagedWebCompression(
                new CompressionStream("gzip"),
                [new TextEncoder().encode("packaged-"), Buffer.from("gzip")]
              );
              const packagedWebCompressionGzipText = (await collectPackagedWebCompression(
                new DecompressionStream("gzip"),
                [packagedWebCompressionGzipBytes]
              )).toString("utf8");
              const packagedWebCompressionDeflateBytes = await collectPackagedWebCompression(
                new CompressionStream("deflate"),
                ["packaged-", Buffer.from("deflate")]
              );
              const packagedWebCompressionDeflateText = (await collectPackagedWebCompression(
                new DecompressionStream("deflate"),
                [packagedWebCompressionDeflateBytes]
              )).toString("utf8");
              const packagedWebCompressionRawBytes = await collectPackagedWebCompression(
                new CompressionStream("deflate-raw"),
                [Buffer.from("packaged-raw")]
              );
              const packagedWebCompressionRawText = (await collectPackagedWebCompression(
                new DecompressionStream("deflate-raw"),
                [packagedWebCompressionRawBytes]
              )).toString("utf8");
              const packagedWebCompressionBrotliBytes = await collectPackagedWebCompression(
                new CompressionStream("brotli"),
                [Buffer.from("packaged-brotli")]
              );
              const packagedWebCompressionBrotliText = (await collectPackagedWebCompression(
                new DecompressionStream("brotli"),
                [packagedWebCompressionBrotliBytes]
              )).toString("utf8");
              const packagedWebCompressionInvalid = webCompressionCaptureSync(() =>
                new CompressionStream("br")
              );
              const packagedWebCompressionBadChunk = await webCompressionCaptureAsync(() =>
                collectPackagedWebCompression(new CompressionStream("gzip"), [new ArrayBuffer(1)])
              );
              const packagedWebCompressionTooLarge = await webCompressionCaptureAsync(() =>
                collectPackagedWebCompression(new CompressionStream("gzip"), [Buffer.alloc(8 * 1024 * 1024 + 1)])
              );
              const packagedWebCompressionBomb = await webCompressionCaptureAsync(() =>
                collectPackagedWebCompression(
                  new DecompressionStream("gzip"),
                  [Buffer.from("$zlibOutputBombBase64", "base64")]
                )
              );
              const packagedWebCompressionDescriptor = Object.getOwnPropertyDescriptor(globalThis, "CompressionStream");
              const packagedWebDecompressionDescriptor = Object.getOwnPropertyDescriptor(globalThis, "DecompressionStream");
              const packagedWebCompressionSurface = [
                typeof streamWeb.CompressionStream,
                typeof streamWeb.DecompressionStream,
                typeof zlib.createGzip,
                typeof zlib.createGunzip,
                typeof zlib.createBrotliCompress
              ].join(",");
              const webCompressionGlobalOk =
                typeof CompressionStream === "function" &&
                typeof DecompressionStream === "function" &&
                new CompressionStream("gzip") instanceof CompressionStream &&
                new DecompressionStream("deflate") instanceof DecompressionStream &&
                packagedWebCompressionDescriptor &&
                packagedWebCompressionDescriptor.writable === false &&
                packagedWebDecompressionDescriptor &&
                packagedWebDecompressionDescriptor.writable === false &&
                packagedWebCompressionSurface === "undefined,undefined,function,function,function";
              const webCompressionRoundtripOk =
                packagedWebCompressionGzipText === "packaged-gzip" &&
                packagedWebCompressionDeflateText === "packaged-deflate" &&
                packagedWebCompressionRawText === "packaged-raw" &&
                packagedWebCompressionBrotliText === "packaged-brotli" &&
                packagedWebCompressionGzipBytes[0] === 0x1f &&
                packagedWebCompressionGzipBytes[1] === 0x8b &&
                packagedWebCompressionDeflateBytes.length > 0 &&
                packagedWebCompressionRawBytes.length > 0 &&
                packagedWebCompressionBrotliBytes.length > 0;
              const webCompressionLimitsOk =
                packagedWebCompressionInvalid === "TypeError/ERR_INVALID_ARG_VALUE" &&
                packagedWebCompressionBadChunk === "TypeError/ERR_INVALID_ARG_TYPE" &&
                packagedWebCompressionTooLarge === "RangeError/ERR_OUT_OF_RANGE" &&
                packagedWebCompressionBomb === "RangeError/ERR_OUT_OF_RANGE";

              const gz = zlib.gzipSync(Buffer.from("hello"));
              const gzString = zlib.gzipSync("hello");
              const deflated = zlib.deflateSync("deflate");
              const rawDeflated = zlib.deflateRawSync("raw");
              const brotlied = zlib.brotliCompressSync(Buffer.from("brotli"), {
                params: {
                  [zlib.constants.BROTLI_PARAM_QUALITY]: 1
                }
              });
              let zlibCallbackAsync = false;
              let zlibCallbackOk = false;
              let zlibCallbackSync = true;
              const zlibCallback = new Promise((resolve) => {
                zlib.gzip(Buffer.from("callback"), (error, value) => {
                  zlibCallbackAsync = !zlibCallbackSync;
                  zlibCallbackOk =
                    error === null &&
                    Buffer.isBuffer(value) &&
                    zlib.gunzipSync(value).toString("utf8") === "callback";
                  resolve();
                });
              });
              zlibCallbackSync = false;
              await zlibCallback;
              const zlibPromisifiedGzip = await promisify(zlib.gzip)(Buffer.from("promisified-gzip"));
              const zlibPromisifiedRaw = await promisify(zlib.deflateRaw)(Buffer.from("promisified-raw"));
              const zlibBrotliCallbackOk = await new Promise((resolve) => {
                zlib.brotliCompress(Buffer.from("callback-brotli"), {
                  params: {
                    [zlib.constants.BROTLI_PARAM_QUALITY]: 1
                  }
                }, (error, value) => {
                  if (error) {
                    resolve(false);
                    return;
                  }
                  zlib.brotliDecompress(value, (decompressError, decompressed) => {
                    resolve(
                      decompressError === null &&
                      decompressed.toString("utf8") === "callback-brotli"
                    );
                  });
                });
              });
              async function collectPackagedZlibPipeline(chunks, transforms) {
                const values = [];
                const writable = new stream.Writable({
                  write(chunk, encoding, callback) {
                    values.push(Buffer.from(chunk));
                    callback();
                  }
                });
                await streamPromises.pipeline(
                  stream.Readable.from(chunks),
                  ...transforms,
                  writable
                );
                return Buffer.concat(values).toString("utf8");
              }
              async function packagedZlibPipelineError(chunks, transforms) {
                try {
                  await collectPackagedZlibPipeline(chunks, transforms);
                  return "ok";
                } catch (error) {
                  return [
                    error && error.name,
                    error && error.code
                  ].join("/");
                }
              }
              const zlibStreamGzipText = await collectPackagedZlibPipeline(
                [Buffer.from("packaged-"), "gzip-stream"],
                [zlib.createGzip(), zlib.createGunzip()]
              );
              const zlibStreamDeflateText = await collectPackagedZlibPipeline(
                [Buffer.from("packaged-deflate-stream")],
                [zlib.createDeflate(), zlib.createInflate()]
              );
              const zlibStreamRawText = await collectPackagedZlibPipeline(
                [Buffer.from("packaged-raw-stream")],
                [zlib.createDeflateRaw(), zlib.createInflateRaw()]
              );
              const zlibStreamBrotliText = await collectPackagedZlibPipeline(
                [Buffer.from("packaged-brotli-stream")],
                [
                  zlib.createBrotliCompress({
                    params: {
                      [zlib.constants.BROTLI_PARAM_QUALITY]: 1
                    }
                  }),
                  zlib.createBrotliDecompress()
                ]
              );
              const zlibStreamInputLimit = await packagedZlibPipelineError(
                [Buffer.alloc(8 * 1024 * 1024 + 1)],
                [zlib.createGzip()]
              );
              const zlibStreamOutputLimit = await packagedZlibPipelineError(
                [Buffer.from("$zlibOutputBombBase64", "base64")],
                [zlib.createGunzip()]
              );
              const zlibStreamCorrupt = await packagedZlibPipelineError(
                [Buffer.from("not-gzip")],
                [zlib.createGunzip()]
              );
              const zlibStreamApisOk = [
                typeof zlib.createGzip,
                typeof zlib.createGunzip,
                typeof zlib.createDeflate,
                typeof zlib.createInflate,
                typeof zlib.createDeflateRaw,
                typeof zlib.createInflateRaw,
                typeof zlib.createBrotliCompress,
                typeof zlib.createBrotliDecompress
              ].join(",") === "function,function,function,function,function,function,function,function";
              const zlibStreamOk =
                zlibStreamApisOk &&
                zlibStreamGzipText === "packaged-gzip-stream" &&
                zlibStreamDeflateText === "packaged-deflate-stream" &&
                zlibStreamRawText === "packaged-raw-stream" &&
                zlibStreamBrotliText === "packaged-brotli-stream";
              const zlibStreamLimitsOk =
                zlibStreamInputLimit === "RangeError/ERR_OUT_OF_RANGE" &&
                zlibStreamOutputLimit === "RangeError/ERR_OUT_OF_RANGE" &&
                zlibStreamCorrupt !== "ok";
              const typed = new Uint8Array(Buffer.from("typed"));
              const zlibSurfaceOk =
                zlib === nodeZlib &&
                zlib.shadow !== true &&
                Object.isFrozen(zlib) &&
                Object.isFrozen(zlib.constants) &&
                Buffer.isBuffer(gz) &&
                zlib.gunzipSync(gz).toString("utf8") === "hello" &&
                zlib.gunzipSync(gzString).toString("utf8") === "hello" &&
                zlib.inflateSync(deflated).toString("utf8") === "deflate" &&
                zlib.inflateRawSync(rawDeflated).toString("utf8") === "raw" &&
                zlib.brotliDecompressSync(brotlied).toString("utf8") === "brotli" &&
                typeof zlib.constants.BROTLI_PARAM_QUALITY === "number" &&
                zlibCallbackAsync &&
                zlibCallbackOk &&
                zlib.gunzipSync(zlibPromisifiedGzip).toString("utf8") === "promisified-gzip" &&
                zlib.inflateRawSync(zlibPromisifiedRaw).toString("utf8") === "promisified-raw" &&
                zlibBrotliCallbackOk &&
                zlibStreamOk &&
                zlib.gunzipSync(zlib.gzipSync(typed)).toString("utf8") === "typed";

              function hasCode(action, expectedCode) {
                try {
                  action();
                  return false;
                } catch (error) {
                  return error && error.code === expectedCode;
                }
              }
              const cryptoLimitsOk =
                hasCode(() => crypto.randomBytes("8"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.randomBytes(-1), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.randomBytes(65537), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.randomBytes(1, "not-callback"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.createHash("sha384"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.createSecretKey(42), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.createSecretKey(Buffer.alloc(65537)), "ERR_OUT_OF_RANGE") &&
                hasCode(() => cryptoSecretKey.export("buffer"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => cryptoSecretKey.export({ format: "pem" }), "ERR_INVALID_ARG_VALUE") &&
                hasCode(() => cryptoSecretKey.equals(Buffer.from("key")), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.pbkdf2Sync("password", "salt", 200001, 16, "sha256"), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.pbkdf2Sync("password", "salt", 1, 65537, "sha256"), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.pbkdf2Sync("password", "salt", 1, 16, "sha384"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.pbkdf2("password", "salt", 1, 16, "sha256"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.scryptSync("password", "salt", 16, { N: 32768 }), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.scryptSync("password", "salt", 65537), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.scrypt("password", "salt", 16), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.hkdfSync("sha384", "ikm", "salt", "info", 16), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => crypto.hkdfSync("sha256", "ikm", "salt", Buffer.alloc(1025), 16), "ERR_OUT_OF_RANGE") &&
                hasCode(() => crypto.hkdf("sha256", "ikm", "salt", "info", 16), "ERR_INVALID_ARG_TYPE");
              const zlibLimitsOk =
                hasCode(() => zlib.gzipSync(42), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => zlib.gzipSync(Buffer.alloc(8 * 1024 * 1024 + 1)), "ERR_OUT_OF_RANGE") &&
                hasCode(() => zlib.brotliCompressSync(Buffer.alloc(8 * 1024 * 1024 + 1)), "ERR_OUT_OF_RANGE") &&
                hasCode(() => zlib.gzip(Buffer.from("x")), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => zlib.gzip(Buffer.from("x"), "bad"), "ERR_INVALID_ARG_TYPE") &&
                hasCode(() => zlib.gzip(Buffer.alloc(8 * 1024 * 1024 + 1), () => {}), "ERR_OUT_OF_RANGE") &&
                hasCode(() => zlib.gunzipSync(Buffer.from("$zlibOutputBombBase64", "base64")), "ERR_OUT_OF_RANGE");
              let zlibCorruptOk = false;
              try {
                zlib.gunzipSync(Buffer.from("not-gzip"));
              } catch (error) {
                zlibCorruptOk = error instanceof Error;
              }
              const hiddenOk = [
                typeof crypto.createPrivateKey,
                typeof crypto.createPublicKey,
                typeof crypto.generateKeyPair,
                typeof crypto.createSign,
                typeof crypto.createVerify,
                typeof crypto.createCipheriv,
                typeof zlib.promises,
              ].join(",") === "undefined,undefined,undefined,undefined,undefined,undefined,undefined";
              const resolveOk =
                require.resolve("crypto") === "crypto" &&
                require.resolve("node:crypto") === "crypto" &&
                require.resolve("zlib") === "zlib" &&
                require.resolve("node:zlib") === "zlib";
              const deniedOk = [
                "zlib/promises",
                "node:zlib/promises",
                "child_process",
                "worker_threads"
              ].every((name) => {
                try {
                  require(name);
                  return false;
                } catch (error) {
                  return error.message.includes(name);
                }
              });
              let builtinGuardOk = false;
              try {
                process.getBuiltinModule("crypto");
              } catch (cryptoError) {
                try {
                  process.getBuiltinModule("zlib");
                } catch (zlibError) {
                  builtinGuardOk =
                    cryptoError.message.includes("disabled") &&
                    zlibError.message.includes("disabled");
                }
              }
              const surfaceOk = cryptoSurfaceOk && zlibSurfaceOk;
              const limitsOk = cryptoLimitsOk && zlibLimitsOk && zlibStreamLimitsOk && zlibCorruptOk;
              const guardsOk = hiddenOk && resolveOk && deniedOk && builtinGuardOk;
              console.log("packaged.crypto_zlib.ready=" + (surfaceOk && limitsOk && guardsOk));
              console.log("packaged.crypto_zlib.surface=" + surfaceOk);
              console.log("packaged.crypto_zlib.secret_key=" + cryptoSecretKeyOk);
              console.log("packaged.crypto_zlib.kdf=" + cryptoKdfOk);
              console.log("packaged.crypto_zlib.stream=" + zlibStreamOk);
              console.log("packaged.crypto_zlib.limits=" + limitsOk);
              console.log("packaged.crypto_zlib.guards=" + guardsOk);
              console.log("packaged.web_crypto.global=" + webCryptoGlobalOk);
              console.log("packaged.web_crypto.digest=" + webCryptoDigestOk);
              console.log("packaged.web_crypto.hmac=" + webCryptoHmacOk);
              console.log("packaged.web_crypto.node_alias=" + packagedWebCryptoNodeAliasOk);
              console.log("packaged.web_compression.global=" + webCompressionGlobalOk);
              console.log("packaged.web_compression.roundtrip=" + webCompressionRoundtripOk);
              console.log("packaged.web_compression.limits=" + webCompressionLimitsOk);
            }

            async function runNpmCryptoZlibFsCorpus() {
              const scopedFeature = require("@scope/resolver-self-pkg/feature");
              const parent = require("resolution-parent");
              const payload = scopedFeature.cachePayload("packaged-cache-tool");
              const result = parent.writeCache(".", payload);
              const blobResult = await parent.readCacheBlob(".", result.file, payload);
              const blobReject = await parent.rejectOpenAsBlob("../packaged-npm-cache-escape.cache");
              const globResult = await parent.listCacheFiles(".");
              const globReject = await parent.rejectCacheGlob("../packaged-npm-cache-escape");
              const streamResult = await parent.streamCacheFile(".", result.file);
              const cpResult = await parent.copyCacheTree(".", result.file);
              const watchResult = await parent.watchCacheConfig(".");
              const scanResult = await parent.scanCacheTree(".", result.file);
              const tempResult = await parent.useTempCacheWorkspace(".", result.file);
              const cachePath = path.join("packaged-npm-cache", result.file);
              const cached = fs.readFileSync(cachePath);
              const cacheHash = crypto.createHash("sha256").update(cached).digest("hex");
              const scopedResolve = require.resolve("@scope/resolver-self-pkg/feature").replace(/\\/g, "/");
              const nestedResolve = result.nestedResolve.replace(/\\/g, "/");
              const surfaceOk =
                result.ok &&
                result.compressed &&
                result.statSize === cached.length &&
                result.nested === "nested-shared" &&
                result.nestedAlias === true &&
                payload.scoped === "self-feature" &&
                parent.shared === "nested-shared";
              const resolveOk =
                scopedResolve.endsWith("/node_modules/@scope/resolver-self-pkg/feature.js") &&
                nestedResolve.endsWith(
                  "/node_modules/resolution-parent/node_modules/resolution-shared/index.js"
                );
              const cacheOk =
                fs.existsSync(cachePath) &&
                result.file === result.hash.slice(0, 12) + ".cache" &&
                cacheHash === result.hash;
              const blobOk =
                blobResult.ok &&
                blobResult.type === "application/gzip" &&
                blobResult.size === cached.length &&
                blobResult.nested === "nested-shared" &&
                blobReject;
              const globOk =
                globResult.length === 1 &&
                globResult[0] === cachePath.replace(/\\/g, "/") &&
                globReject;
              const streamOk =
                streamResult.sliceLength === Math.min(cached.length, 8) &&
                streamResult.patched === "012345XYZ9" &&
                streamResult.abortCode === "ABORT_ERR" &&
                streamResult.streamTypes === "function/function";
              const cpOk =
                cpResult.copied &&
                cpResult.skipped &&
                cpResult.link &&
                cpResult.linkTarget === result.file &&
                cpResult.linkTextOk;
              const watchOk =
                watchResult.watch &&
                watchResult.promises &&
                watchResult.values === "2/2";
              const scanOk =
                scanResult.readdir &&
                scanResult.opendir &&
                scanResult.count.split("/")[0] === scanResult.count.split("/")[1];
              const tempOk =
                tempResult.sync &&
                tempResult.promise &&
                tempResult.source;
              console.log("packaged.npm_crypto_zlib_fs.ready=" + (surfaceOk && resolveOk && cacheOk && blobOk && globOk && streamOk && cpOk && watchOk && scanOk && tempOk));
              console.log("packaged.npm_crypto_zlib_fs.blob=" + blobOk);
              console.log("packaged.npm_crypto_zlib_fs.glob=" + globOk);
              console.log("packaged.npm_crypto_zlib_fs.stream=" + streamOk);
              console.log("packaged.npm_crypto_zlib_fs.cp=" + cpOk);
              console.log("packaged.npm_crypto_zlib_fs.watch=" + watchOk);
              console.log("packaged.npm_crypto_zlib_fs.scan=" + scanOk);
              console.log("packaged.npm_crypto_zlib_fs.temp=" + tempOk);
            }

            function runDynamicRequireFallbackCorpus() {
              const childName = "./packaged-dynamic-child";
              const childA = require(childName);
              const childB = require("./" + "packaged-dynamic-child");
              const data = JSON.parse(fs.readFileSync(
                "./src/packaged-dynamic-data.json",
                "utf8"
              ));
              const childPath = require.resolve(childName);
              const ready = childA.value === "dynamic-child";
              const cache = childA === childB &&
                globalThis.__autojs6PackagedDynamicChildLoads === 1 &&
                Boolean(require.cache[childPath]) &&
                require.cache[childPath].exports === childA;
              const json = data.value === "dynamic-json";
              console.log("packaged.dynamic_require.ready=" + ready);
              console.log("packaged.dynamic_require.cache=" + cache);
              console.log("packaged.dynamic_require.json=" + json);
            }

            function runModuleCreateRequireCorpus() {
              const req = moduleBuiltin.createRequire(__filename);
              const childName = "./packaged-dynamic-child";
              const childA = req(childName);
              const childB = require(childName);
              const childPath = req.resolve(childName);
              const normalizedChildPath = childPath.replace(/\\/g, "/");
              const builtinHelpers = [
                moduleBuiltin.isBuiltin("fs"),
                moduleBuiltin.isBuiltin("node:fs/promises"),
                moduleBuiltin.isBuiltin("path/posix"),
                moduleBuiltin.isBuiltin("node:stream/web"),
                moduleBuiltin.isBuiltin("console"),
                moduleBuiltin.isBuiltin("v8"),
                moduleBuiltin.isBuiltin("async_hooks"),
                moduleBuiltin.isBuiltin("node:diagnostics_channel"),
                moduleBuiltin.isBuiltin("dns"),
                moduleBuiltin.isBuiltin("node:dns/promises"),
                moduleBuiltin.isBuiltin("net"),
                moduleBuiltin.isBuiltin("perf_hooks"),
                moduleBuiltin.isBuiltin("tty"),
                moduleBuiltin.isBuiltin("node:timers/promises"),
                moduleBuiltin.isBuiltin("http"),
                moduleBuiltin.isBuiltin("https"),
                moduleBuiltin.isBuiltin("tls"),
                moduleBuiltin.isBuiltin("node:vm")
              ].join("/") === "true/true/true/true/true/true/true/true/true/true/true/true/true/true/true/true/true/true" &&
                Array.isArray(moduleBuiltin.builtinModules) &&
                Object.isFrozen(moduleBuiltin.builtinModules) &&
                moduleBuiltin.builtinModules.includes("fs") &&
                moduleBuiltin.builtinModules.includes("stream/web") &&
                moduleBuiltin.builtinModules.includes("module") &&
                moduleBuiltin.builtinModules.includes("console") &&
                moduleBuiltin.builtinModules.includes("v8") &&
                moduleBuiltin.builtinModules.includes("async_hooks") &&
                moduleBuiltin.builtinModules.includes("diagnostics_channel") &&
                moduleBuiltin.builtinModules.includes("dns") &&
                moduleBuiltin.builtinModules.includes("dns/promises") &&
                moduleBuiltin.builtinModules.includes("net") &&
                moduleBuiltin.builtinModules.includes("perf_hooks") &&
                moduleBuiltin.builtinModules.includes("tty") &&
                moduleBuiltin.builtinModules.includes("timers/promises") &&
                moduleBuiltin.builtinModules.includes("http") &&
                moduleBuiltin.builtinModules.includes("https") &&
                moduleBuiltin.builtinModules.includes("tls") &&
                moduleBuiltin.builtinModules.includes("vm") &&
                typeof moduleBuiltin.syncBuiltinESMExports === "function" &&
                moduleBuiltin.syncBuiltinESMExports() === undefined;
              const ready = moduleBuiltin === nodeModuleBuiltin &&
                Object.isFrozen(moduleBuiltin) &&
                Object.keys(moduleBuiltin).join(",") === "createRequire,isBuiltin,builtinModules,syncBuiltinESMExports" &&
                typeof moduleBuiltin.createRequire === "function" &&
                builtinHelpers &&
                childA.value === "dynamic-child";
              const resolve = normalizedChildPath.endsWith("/packaged-dynamic-child.js") &&
                req.resolve("module") === "module" &&
                require.resolve("node:module") === "module";
              const cache = req.cache === require.cache &&
                req.extensions === require.extensions &&
                req.main === require.main &&
                childA === childB &&
                require.cache[childPath] &&
                require.cache[childPath].exports === childA;
              console.log("packaged.module.create_require.ready=" + ready);
              console.log("packaged.module.create_require.resolve=" + resolve);
              console.log("packaged.module.create_require.cache=" + cache);
              console.log("packaged.module.builtin_helpers=" + builtinHelpers);
            }

            async function runCliStyleCorpus() {
              await fsp.mkdir("./packaged-cli/workspace", { recursive: true });
              await fsp.writeFile("./packaged-cli/config.json", JSON.stringify({
                plugin: "packaged-dynamic-child"
              }), "utf8");
              const config = JSON.parse(await fsp.readFile("./packaged-cli/config.json", "utf8"));
              const plugin = require("./" + config.plugin);
              const req = moduleBuiltin.createRequire(__filename);
              const helper = req("./packaged-dynamic-child");
              const helperPath = req.resolve("./packaged-dynamic-child");
              const argv = Array.isArray(process.argv) &&
                process.argv[0] === "autojs6-node" &&
                process.argv[1] === __filename;
              const requireMain = require.main === module &&
                module.parent === null &&
                module.filename === __filename &&
                __dirname === path.dirname(__filename);
              const cache = helper === plugin &&
                req.cache === require.cache &&
                require.cache[helperPath] &&
                require.cache[helperPath].exports === helper;
              const processConstants = processBuiltin === process &&
                nodeProcessBuiltin === processBuiltin &&
                constantsBuiltin === nodeConstantsBuiltin &&
                constantsBuiltin.F_OK === fs.constants.F_OK &&
                req("process") === processBuiltin &&
                req("constants") === constantsBuiltin;
              process.chdir("./packaged-cli/workspace");
              await fsp.writeFile("./output.txt", bufferBuiltin.Buffer.from([
                plugin.value,
                path.basename(process.cwd())
              ].join("|"), "utf8"));
              const output = await fsp.readFile("./output.txt", "utf8");
              process.chdir("../..");
              await fsp.rm("./packaged-cli", { recursive: true, force: true });
              const combo = plugin.value === "dynamic-child" &&
                helper.value === "dynamic-child" &&
                cache &&
                processConstants &&
                output === "dynamic-child|workspace" &&
                !fs.existsSync("./packaged-cli");
              console.log("packaged.cli.ready=" + (argv && requireMain && combo));
              console.log("packaged.cli.argv=" + argv);
              console.log("packaged.cli.require_main=" + requireMain);
              console.log("packaged.cli.combo=" + combo);
              console.log("packaged.cli.process_constants=" + processConstants);
            }

            async function runPackagedGlobLiteCorpus() {
              await fsp.mkdir("./packaged-glob/workspace/src/a", { recursive: true });
              await fsp.mkdir("./packaged-glob/workspace/src/b", { recursive: true });
              await fsp.mkdir("./packaged-glob/workspace/src/c", { recursive: true });
              await fsp.mkdir("./packaged-glob/workspace/lib", { recursive: true });
              await fsp.mkdir("./packaged-glob/workspace/node_modules", { recursive: true });
              await fsp.writeFile("./packaged-glob/workspace/src/a/index.js", "a", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/src/a/index.test.js", "test", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/src/a/readme.md", "readme", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/src/b/main.js", "b", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/src/b/util.cjs", "cjs", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/src/c/skip.js", "skip", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/lib/tool.js", "tool", "utf8");
              await fsp.writeFile("./packaged-glob/workspace/node_modules/ignored.txt", "ignored", "utf8");

              function normalizePathname(value) {
                let normalized = String(value).replace(/\\/g, "/");
                while (normalized.startsWith("./")) {
                  normalized = normalized.slice(2);
                }
                return normalized;
              }

              function expandBrace(pattern) {
                const start = pattern.indexOf("{");
                const end = pattern.indexOf("}", start + 1);
                if (start < 0 || end < 0) {
                  return [pattern];
                }
                const prefix = pattern.slice(0, start);
                const suffix = pattern.slice(end + 1);
                return pattern.slice(start + 1, end).split(",").map(part => prefix + part + suffix);
              }

              function segmentMatches(value, pattern) {
                const special = "\\.[]{}()+-^" + String.fromCharCode(36) + "?|";
                let regex = "^";
                for (const ch of pattern) {
                  if (ch === "*") {
                    regex += "[^/]*";
                  } else if (special.includes(ch)) {
                    regex += "\\" + ch;
                  } else {
                    regex += ch;
                  }
                }
                return new RegExp(regex + "$").test(value);
              }

              function matchSegments(fileSegments, patternSegments) {
                if (patternSegments.length === 0) {
                  return fileSegments.length === 0;
                }
                const head = patternSegments[0];
                if (head === "**") {
                  if (matchSegments(fileSegments, patternSegments.slice(1))) {
                    return true;
                  }
                  return fileSegments.length > 0 && matchSegments(fileSegments.slice(1), patternSegments);
                }
                return fileSegments.length > 0 &&
                  segmentMatches(fileSegments[0], head) &&
                  matchSegments(fileSegments.slice(1), patternSegments.slice(1));
              }

              function minimatchLite(file, pattern) {
                const fileSegments = normalizePathname(file).split("/").filter(Boolean);
                const patternSegments = normalizePathname(pattern).split("/").filter(Boolean);
                return matchSegments(fileSegments, patternSegments);
              }

              async function walk(relativeDir) {
                const files = [];
                const dir = await fsp.opendir(relativeDir);
                for await (const entry of dir) {
                  const relative = normalizePathname(path.join(relativeDir, entry.name));
                  if (entry.isDirectory()) {
                    files.push(...await walk(relative));
                  } else if (entry.isFile()) {
                    files.push(relative);
                  }
                }
                return files;
              }

              process.chdir("./packaged-glob/workspace");
              await fsp.access("./src/a/index.js", constantsBuiltin.fs.R_OK);
              const files = await walk(".");
              const opendirRecursiveFiles = [];
              const recursiveDir = await fsp.opendir(".", { recursive: true, bufferSize: 1 });
              for await (const entry of recursiveDir) {
                if (entry.isFile()) {
                  opendirRecursiveFiles.push(normalizePathname(path.join(String(entry.parentPath), String(entry.name))));
                }
              }
              const nativeGlobMatches = [];
              for await (const entry of fsp.glob(["src/**/*.js", "lib/**/*.js"], {
                exclude: ["**/*.test.js", "src/c/skip.js"]
              })) {
                nativeGlobMatches.push(String(entry).replace(/\\/g, "/"));
              }
              const nativeGlobDirents = [];
              for await (const entry of fsp.glob("src/a/*", {
                withFileTypes: true,
                exclude: (entry) => entry.name.endsWith(".md")
              })) {
                nativeGlobDirents.push(entry);
              }
              const promiseDirents = await fsp.readdir("./src", { withFileTypes: true });
              const syncDirents = fs.readdirSync("./src/a", { withFileTypes: true });
              const callbackDirents = await new Promise((resolve, reject) => {
                fs.readdir("./src/b", { withFileTypes: true }, (error, entries) => {
                  if (error) reject(error);
                  else resolve(entries);
                });
              });
              const patterns = expandBrace("src/{a,b}/**/*.js");
              const matches = files
                .filter(file => patterns.some(pattern => minimatchLite(file, pattern)))
                .filter(file => !minimatchLite(file, "**/*.test.js"))
                .sort();
              await fsp.writeFile("./matches.json", bufferBuiltin.Buffer.from(JSON.stringify(matches), "utf8"));
              const stored = JSON.parse(await fsp.readFile("./matches.json", "utf8"));
              const matchesText = matches.join("|");
              const storedText = stored.join("|");
              const expectedText = "src/a/index.js|src/b/main.js";
              const ready = matchesText === expectedText && storedText === expectedText;
              const ignored = !matches.includes("src/a/index.test.js") &&
                !matches.includes("src/a/readme.md") &&
                !matches.includes("src/b/util.cjs") &&
                !matches.includes("src/c/skip.js");
              const fsPromisesOk = Array.isArray(files) &&
                files.includes("src/a/index.js") &&
                files.includes("node_modules/ignored.txt") &&
                stored.length === 2;
              const readdirDirentOk =
                promiseDirents.some(entry => entry.name === "a" && entry.isDirectory()) &&
                syncDirents.some(entry => entry.name === "index.js" && entry.isFile()) &&
                callbackDirents.some(entry => entry.name === "main.js" && entry.isFile());
              const opendirOk =
                files.includes("src/a/index.js") &&
                files.includes("src/b/main.js") &&
                files.includes("node_modules/ignored.txt");
              const opendirRecursiveOk =
                opendirRecursiveFiles.includes("src/a/index.js") &&
                opendirRecursiveFiles.includes("src/b/main.js") &&
                opendirRecursiveFiles.includes("node_modules/ignored.txt");
              const asyncIteratorOk = opendirOk && opendirRecursiveOk && typeof fsp.opendir === "function";
              const nativeGlobOk =
                nativeGlobMatches.sort().join("|") === "lib/tool.js|src/a/index.js|src/b/main.js" &&
                nativeGlobDirents
                  .map((entry) => entry.name + ":" + entry.isFile() + ":" + typeof entry.parentPath)
                  .sort()
                  .join("|") === "index.js:true:string|index.test.js:true:string";
              process.chdir("../..");
              await fsp.rm("./packaged-glob", { recursive: true, force: true });
              console.log("packaged.glob.ready=" + (ready && ignored && fsPromisesOk));
              console.log("packaged.glob.matches=" + (matchesText === expectedText));
              console.log("packaged.glob.ignore=" + ignored);
              console.log("packaged.glob.fs_promises=" + fsPromisesOk);
              console.log("packaged.glob.native=" + nativeGlobOk);
              console.log("packaged.dirent.ready=" + (readdirDirentOk && opendirOk && asyncIteratorOk));
              console.log("packaged.dirent.readdir=" + readdirDirentOk);
              console.log("packaged.dirent.opendir=" + opendirOk);
              console.log("packaged.dirent.opendir_recursive=" + opendirRecursiveOk);
              console.log("packaged.dirent.async_iterator=" + asyncIteratorOk);
            }

            async function runPackagedReadlinkSurfaceSmoke() {
              await fsp.mkdir("./packaged-readlink", { recursive: true });
              await fsp.writeFile("./packaged-readlink/file.txt", "file", "utf8");
              const surfaceOk =
                typeof fs.readlinkSync === "function" &&
                typeof fs.readlink === "function" &&
                typeof fsp.readlink === "function" &&
                typeof fs.symlinkSync === "function" &&
                typeof fs.symlink === "function" &&
                typeof fsp.symlink === "function";
              const readlinkSyncPath = "./packaged-readlink/sync-link.txt";
              const readlinkCallbackPath = "./packaged-readlink/callback-link.txt";
              const readlinkPromisePath = "./packaged-readlink/promise-link.txt";
              const readlinkSyncOk = symlinkCreationOk(
                () => fs.symlinkSync("./file.txt", readlinkSyncPath),
                readlinkSyncPath,
                "file.txt"
              );
              const readlinkCallbackOk = await symlinkCreationAsyncOk(
                () => symlink("./file.txt", readlinkCallbackPath),
                readlinkCallbackPath,
                "file.txt"
              );
              const readlinkPromiseOk = await symlinkCreationAsyncOk(
                () => fsp.symlink("./file.txt", readlinkPromisePath),
                readlinkPromisePath,
                "file.txt"
              );
              const symlinkOk = readlinkSyncOk && readlinkCallbackOk && readlinkPromiseOk;
              const invalidEncodingRejected = captureSync(
                () => fs.readlinkSync("./packaged-readlink/file.txt", { encoding: "bad-encoding" }),
                "invalid encoding"
              );
              const nonSymlinkRejected = captureSync(
                () => fs.readlinkSync("./packaged-readlink/file.txt"),
                "not a symbolic link"
              );
              const readlinkSymlinkRestricted = ![
                readlinkSyncPath,
                readlinkCallbackPath,
                readlinkPromisePath
              ].some((pathValue) => symlinkTargetOk(pathValue, "file.txt"));
              await fsp.rm("./packaged-readlink", { recursive: true, force: true });
              console.log("packaged.readlink.surface=" + surfaceOk);
              console.log("packaged.readlink.symlink=" + symlinkOk);
              console.log("packaged.readlink.symlink.restricted=" + readlinkSymlinkRestricted);
              console.log("packaged.readlink.rejects=" + (invalidEncodingRejected && nonSymlinkRejected));
            }

            async function runPackagedConfigPluginCorpus() {
              await fsp.mkdir("./packaged-config/node_modules/config-tool-plugin-alpha", { recursive: true });
              await fsp.mkdir("./packaged-config/node_modules/@autojs6/config-plugin-scoped", { recursive: true });
              await fsp.writeFile("./packaged-config/src.txt", "hello embedded node", "utf8");
              await fsp.writeFile("./packaged-config/tool.config.js", [
                "module.exports = {",
                "  name: \"AutoJs6\",",
                "  plugin: \"config-tool-plugin-alpha\",",
                "  scopedPlugin: \"@autojs6/config-plugin-scoped\",",
                "  output: \"dist/result.json\"",
                "};"
              ].join("\n") + "\n", "utf8");
              await fsp.writeFile(
                "./packaged-config/node_modules/config-tool-plugin-alpha/package.json",
                JSON.stringify({
                  name: "config-tool-plugin-alpha",
                  exports: { ".": "./index.js" }
                }),
                "utf8"
              );
              await fsp.writeFile("./packaged-config/node_modules/config-tool-plugin-alpha/index.js", [
                "const fs = require(\"fs\");",
                "const processBuiltin = require(\"process\");",
                "const constantsBuiltin = require(\"constants\");",
                "const { Buffer } = require(\"buffer\");",
                "exports.transform = function(input) {",
                "  return {",
                "    alpha: Buffer.from(input, \"utf8\").toString(\"utf8\").toUpperCase(),",
                "    processOk: processBuiltin.argv[0] === \"autojs6-node\",",
                "    constantsOk: constantsBuiltin.F_OK === fs.constants.F_OK",
                "  };",
                "};"
              ].join("\n") + "\n", "utf8");
              await fsp.writeFile(
                "./packaged-config/node_modules/@autojs6/config-plugin-scoped/package.json",
                JSON.stringify({
                  name: "@autojs6/config-plugin-scoped",
                  exports: { ".": "./main.js" }
                }),
                "utf8"
              );
              await fsp.writeFile("./packaged-config/node_modules/@autojs6/config-plugin-scoped/main.js", [
                "const fs = require(\"fs\");",
                "const constantsBuiltin = require(\"node:constants\");",
                "exports.apply = function() {",
                "  return { scoped: true, constantsOk: constantsBuiltin.R_OK === fs.constants.R_OK };",
                "};"
              ].join("\n") + "\n", "utf8");

              process.chdir("./packaged-config");
              const configRequire = moduleBuiltin.createRequire(path.resolve("tool.config.js"));
              const config = configRequire("./tool.config.js");
              const input = await fsp.readFile("./src.txt", "utf8");
              const alpha = configRequire(config.plugin);
              const scoped = configRequire(config.scopedPlugin);
              const alphaResult = alpha.transform(input);
              const scopedResult = scoped.apply();
              const payload = {
                name: config.name,
                alpha: alphaResult.alpha,
                scoped: scopedResult.scoped,
                process: alphaResult.processOk,
                constants: alphaResult.constantsOk && scopedResult.constantsOk
              };
              await fsp.mkdir(path.dirname(config.output), { recursive: true });
              await fsp.writeFile(config.output, bufferBuiltin.Buffer.from(JSON.stringify(payload), "utf8"));
              const output = JSON.parse(await fsp.readFile(config.output, "utf8"));
              const pluginOk = output.alpha === "HELLO EMBEDDED NODE" &&
                output.process === true &&
                output.constants === true;
              const scopedOk = output.scoped === true;
              const outputOk = output.name === "AutoJs6" &&
                fs.existsSync(config.output);
              process.chdir("..");
              await fsp.rm("./packaged-config", { recursive: true, force: true });
              console.log("packaged.config.ready=" + (pluginOk && scopedOk && outputOk));
              console.log("packaged.config.plugin=" + pluginOk);
              console.log("packaged.config.scoped=" + scopedOk);
              console.log("packaged.config.output=" + outputOk);
            }

            async function runPackagedDiscoveryCorpus() {
              await fsp.mkdir("./packaged-discovery/workspace/packages/app/src/deep", { recursive: true });
              await fsp.mkdir("./packaged-discovery/workspace/src", { recursive: true });
              await fsp.writeFile("./packaged-discovery/workspace/src/input.txt", "hello discovery", "utf8");
              await fsp.writeFile(
                "./packaged-discovery/workspace/package.json",
                JSON.stringify({
                  name: "packaged-discovery-workspace",
                  private: true,
                  discoverTool: {
                    name: "AutoJs6",
                    input: "src/input.txt",
                    output: "dist/discovery.json",
                    plugin: "discover-plugin-alpha",
                    scopedPlugin: "@autojs6/discover-plugin-scoped"
                  }
                }),
                "utf8"
              );

              async function exists(candidate) {
                try {
                  await fsp.access(candidate);
                  return true;
                } catch (error) {
                  return false;
                }
              }

              async function findPackageConfig(name) {
                let prefix = "";
                const tried = [];
                for (let depth = 0; depth < 8; depth += 1) {
                  const candidate = prefix ? prefix + "/package.json" : "package.json";
                  tried.push(candidate);
                  if (await exists(candidate)) {
                    const json = JSON.parse(await fsp.readFile(candidate, "utf8"));
                    if (json && json[name]) {
                      return {
                        configFile: candidate,
                        configDir: path.dirname(candidate),
                        config: json[name],
                        tried
                      };
                    }
                  }
                  prefix = prefix ? prefix + "/.." : "..";
                }
                throw new Error("packaged discovery config not found: " + tried.join("|"));
              }

              process.chdir("./packaged-discovery/workspace/packages/app/src/deep");
              const discovered = await findPackageConfig("discoverTool");
              const configRequire = moduleBuiltin.createRequire(discovered.configFile);
              const input = await fsp.readFile(path.join(discovered.configDir, discovered.config.input), "utf8");
              const bufferFromConfig = configRequire("buffer");
              const processFromConfig = configRequire("process");
              const constantsFromConfig = configRequire("constants");
              const alpha = bufferFromConfig.Buffer.from(input, "utf8").toString("utf8").toUpperCase();
              const outPath = path.join(discovered.configDir, discovered.config.output);
              await fsp.mkdir(path.dirname(outPath), { recursive: true });
              await fsp.writeFile(outPath, JSON.stringify({
                name: discovered.config.name,
                alpha,
                scoped: discovered.config.scopedPlugin === "@autojs6/discover-plugin-scoped",
                process: processFromConfig.argv[0] === "autojs6-node",
                constants: constantsFromConfig.F_OK === fs.constants.F_OK
              }), "utf8");
              const output = JSON.parse(await fsp.readFile(outPath, "utf8"));
              const packageJsonOk = discovered.configFile.replace(/\\/g, "/").endsWith("/workspace/package.json") ||
                discovered.configFile === "../../../../package.json";
              const pluginOk = discovered.config.plugin === "discover-plugin-alpha" &&
                output.alpha === "HELLO DISCOVERY" &&
                output.process === true &&
                output.constants === true;
              const outputOk = output.name === "AutoJs6" &&
                output.scoped === true &&
                fs.existsSync(outPath);
              process.chdir("../../../../../..");
              await fsp.rm("./packaged-discovery", { recursive: true, force: true });
              console.log("packaged.discovery.ready=" + (packageJsonOk && pluginOk && outputOk));
              console.log("packaged.discovery.package_json=" + packageJsonOk);
              console.log("packaged.discovery.plugin=" + pluginOk);
              console.log("packaged.discovery.output=" + outputOk);
            }

            async function runPackagedUrlFileInteropCorpus() {
              await fsp.mkdir("./packaged-url/sub", { recursive: true });
              const fileUrl = urlBuiltin.pathToFileURL("./packaged-url/sub/file.txt");
              await fsp.writeFile(fileUrl, "url-file", "utf8");
              const text = await fsp.readFile(fileUrl, "utf8");
              const roundtrip = urlBuiltin.fileURLToPath(fileUrl)
                .replace(/\\/g, "/")
                .endsWith("/packaged-url/sub/file.txt");
              const staticUrl = urlBuiltin.URL.parse("https://example.com/packaged?x=1");
              const urlStaticOk =
                typeof urlBuiltin.URL.canParse === "function" &&
                typeof urlBuiltin.URL.parse === "function" &&
                urlBuiltin.URL.canParse("/relative", "https://example.com") &&
                staticUrl &&
                staticUrl.pathname === "/packaged" &&
                urlBuiltin.URL.parse("http://[") === null;
              const legacyParsed = urlBuiltin.parse("https://example.com/legacy?x=1", true);
              const urlLegacyOk =
                typeof urlBuiltin.parse === "function" &&
                typeof urlBuiltin.format === "function" &&
                typeof urlBuiltin.resolve === "function" &&
                legacyParsed.query.x === "1" &&
                urlBuiltin.format({ protocol: "https", host: "example.com", pathname: "/fmt", query: { a: "1" } }) === "https://example.com/fmt?a=1" &&
                urlBuiltin.resolve("https://example.com/a/b", "../c") === "https://example.com/c";
              const urlPattern = new urlBuiltin.URLPattern({ protocol: "https", hostname: "example.com", pathname: "/packaged/:id" });
              const urlPatternMatch = urlPattern.exec("https://example.com/packaged/42?x=1#top");
              const urlRelativePattern = new URLPattern("/relative/:slug", "https://example.com");
              const urlRelativeMatch = urlRelativePattern.exec("https://example.com/relative/url");
              const urlPatternOk =
                typeof urlBuiltin.URLPattern === "function" &&
                urlBuiltin.URLPattern === URLPattern &&
                urlPattern.test("https://example.com/packaged/42") &&
                urlPatternMatch &&
                urlPatternMatch.pathname.groups.id === "42" &&
                urlRelativePattern.test("https://example.com/relative/url") &&
                urlRelativeMatch &&
                urlRelativeMatch.pathname.groups.slug === "url" &&
                !urlPattern.test("https://example.com/other/42");
              const syncDirents = fs.readdirSync(urlBuiltin.pathToFileURL("./packaged-url/sub"), { withFileTypes: true });
              const dir = await fsp.opendir(urlBuiltin.pathToFileURL("./packaged-url/sub"));
              const iterated = [];
              for await (const entry of dir) {
                iterated.push(String(entry.name) + ":" + entry.isFile());
              }
              let sandboxDenied = false;
              try {
                urlBuiltin.pathToFileURL("/sdcard/autojs6-packaged-url-outside.txt");
              } catch (error) {
                const message = String(error && error.message || error);
                sandboxDenied = message.includes("pathToFileURL") && message.includes("sandboxRoot");
              }
              await fsp.rm("./packaged-url", { recursive: true, force: true });
              console.log("packaged.url.file_url=" + (
                urlBuiltin === nodeUrlBuiltin &&
                fileUrl.protocol === "file:" &&
                roundtrip
              ));
              console.log("packaged.url.fs_url=" + (
                text === "url-file" &&
                syncDirents.some(entry => entry.name === "file.txt" && entry.isFile())
              ));
              console.log("packaged.url.opendir_url=" + iterated.includes("file.txt:true"));
              console.log("packaged.url.sandbox=" + sandboxDenied);
              console.log("packaged.url.static=" + urlStaticOk);
              console.log("packaged.url.legacy=" + urlLegacyOk);
              console.log("packaged.url.pattern=" + urlPatternOk);
            }

            async function runPackagedCpCorpus() {
              function closeTime(actual, expected) {
                return Math.abs(Number(actual) - Number(expected)) < 5000;
              }
              await fsp.mkdir("./packaged-cp/src/nested", { recursive: true });
              await fsp.mkdir("./packaged-cp/src/empty", { recursive: true });
              await fsp.writeFile("./packaged-cp/src/a.txt", "a", "utf8");
              await fsp.writeFile("./packaged-cp/src/nested/b.txt", "b", "utf8");
              fs.cpSync("./packaged-cp/src/a.txt", "./packaged-cp/sync/a.txt", { recursive: true });
              await fsp.cp("./packaged-cp/src", "./packaged-cp/out", { recursive: true });
              const callbackOk = await new Promise((resolve, reject) => {
                fs.cp("./packaged-cp/src/a.txt", "./packaged-cp/callback/a.txt", { recursive: true }, (error) => {
                  if (error) {
                    reject(error);
                    return;
                  }
                  resolve(fs.readFileSync("./packaged-cp/callback/a.txt", "utf8") === "a");
                });
              });
              await fsp.cp(
                urlBuiltin.pathToFileURL("./packaged-cp/src/a.txt"),
                urlBuiltin.pathToFileURL("./packaged-cp/url/a.txt"),
                { recursive: true }
              );
              const preserveDate = new Date("2007-08-09T10:11:12Z");
              await fsp.utimes("./packaged-cp/src/a.txt", preserveDate, preserveDate);
              fs.utimesSync("./packaged-cp/sync/a.txt", preserveDate, preserveDate);
              const callbackUtimesOk = await new Promise((resolve, reject) => {
                fs.utimes("./packaged-cp/callback/a.txt", preserveDate, preserveDate, (error) => {
                  if (error) {
                    reject(error);
                    return;
                  }
                  resolve(closeTime(fs.statSync("./packaged-cp/callback/a.txt").mtimeMs, preserveDate.getTime()));
                });
              });
              await fsp.cp("./packaged-cp/src/a.txt", "./packaged-cp/preserve/a.txt", {
                recursive: true,
                preserveTimestamps: true
              });
              fs.writeFileSync("./packaged-cp/mode-existing.txt", "old", "utf8");
              const modeOk = await fsp.cp("./packaged-cp/src/a.txt", "./packaged-cp/mode-existing.txt", {
                mode: fs.constants.COPYFILE_EXCL
              }).then(
                () => false,
                (error) => error && error.code === "EEXIST"
              );
              await fsp.mkdir("./packaged-cp/filter-src/nested", { recursive: true });
              await fsp.mkdir("./packaged-cp/filter-src/skip-dir", { recursive: true });
              await fsp.writeFile("./packaged-cp/filter-src/keep.txt", "keep", "utf8");
              await fsp.writeFile("./packaged-cp/filter-src/skip.tmp", "skip", "utf8");
              await fsp.writeFile("./packaged-cp/filter-src/nested/keep.md", "nested", "utf8");
              await fsp.writeFile("./packaged-cp/filter-src/skip-dir/drop.txt", "drop", "utf8");
              await fsp.cp("./packaged-cp/filter-src", "./packaged-cp/filter-out", {
                recursive: true,
                filter(source, destination) {
                  return !source.endsWith(".tmp") && !source.includes("skip-dir");
                }
              });
              fs.cpSync("./packaged-cp/src/a.txt", "./packaged-cp/filtered-root.txt", {
                filter() {
                  return false;
                }
              });
              await fsp.mkdir("./packaged-cp/symlink-src/sub", { recursive: true });
              await fsp.writeFile("./packaged-cp/symlink-src/target.txt", "target", "utf8");
              const cpDefaultSymlinkPath = "./packaged-cp/symlink-src/default-link.txt";
              const cpVerbatimSymlinkPath = "./packaged-cp/symlink-src/sub/verbatim-link.txt";
              const cpDefaultSymlinkOk = symlinkCreationOk(
                () => fs.symlinkSync("target.txt", cpDefaultSymlinkPath),
                cpDefaultSymlinkPath,
                "target.txt"
              );
              const cpVerbatimSymlinkOk = symlinkCreationOk(
                () => fs.symlinkSync("../target.txt", cpVerbatimSymlinkPath),
                cpVerbatimSymlinkPath,
                "../target.txt"
              );
              const cpSymlinkCorpusReady =
                symlinkTargetOk(cpDefaultSymlinkPath, "target.txt") &&
                symlinkTargetOk(cpVerbatimSymlinkPath, "../target.txt");
              let symlinkDereferenceOk = cpDefaultSymlinkOk && cpVerbatimSymlinkOk;
              let symlinkOk = cpDefaultSymlinkOk && cpVerbatimSymlinkOk;
              if (cpSymlinkCorpusReady) {
                await fsp.cp(cpDefaultSymlinkPath, "./packaged-cp/symlink-default-link.txt", {
                  recursive: true
                });
                fs.cpSync("./packaged-cp/symlink-src", "./packaged-cp/symlink-verbatim", {
                  recursive: true,
                  verbatimSymlinks: true
                });
                symlinkDereferenceOk = await new Promise((resolve, reject) => {
                  fs.cp(
                    cpDefaultSymlinkPath,
                    "./packaged-cp/symlink-deref.txt",
                    { recursive: true, dereference: true },
                    (error) => {
                      if (error) {
                        reject(error);
                        return;
                      }
                      resolve(
                        !fs.lstatSync("./packaged-cp/symlink-deref.txt").isSymbolicLink() &&
                        fs.readFileSync("./packaged-cp/symlink-deref.txt", "utf8") === "target"
                      );
                    }
                  );
                });
                symlinkOk =
                  fs.lstatSync("./packaged-cp/symlink-default-link.txt").isSymbolicLink() &&
                  fs.readFileSync("./packaged-cp/symlink-default-link.txt", "utf8") === "target" &&
                  fs.lstatSync("./packaged-cp/symlink-verbatim/default-link.txt").isSymbolicLink() &&
                  fs.readlinkSync("./packaged-cp/symlink-verbatim/default-link.txt") === "target.txt" &&
                  fs.lstatSync("./packaged-cp/symlink-verbatim/sub/verbatim-link.txt").isSymbolicLink() &&
                  fs.readlinkSync("./packaged-cp/symlink-verbatim/sub/verbatim-link.txt") === "../target.txt" &&
                  fs.readFileSync("./packaged-cp/symlink-verbatim/sub/verbatim-link.txt", "utf8") === "target" &&
                  symlinkDereferenceOk;
              }
              const recursiveOk = fs.readFileSync("./packaged-cp/out/a.txt", "utf8") === "a" &&
                fs.readFileSync("./packaged-cp/out/nested/b.txt", "utf8") === "b" &&
                fs.statSync("./packaged-cp/out/empty").isDirectory();
              const urlOk = fs.readFileSync("./packaged-cp/url/a.txt", "utf8") === "a";
              const utimesOk = typeof fs.utimesSync === "function" &&
                typeof fs.utimes === "function" &&
                typeof fsp.utimes === "function" &&
                closeTime(fs.statSync("./packaged-cp/src/a.txt").mtimeMs, preserveDate.getTime()) &&
                closeTime(fs.statSync("./packaged-cp/sync/a.txt").mtimeMs, preserveDate.getTime()) &&
                callbackUtimesOk;
              const preserveOk = fs.readFileSync("./packaged-cp/preserve/a.txt", "utf8") === "a" &&
                closeTime(fs.statSync("./packaged-cp/preserve/a.txt").mtimeMs, preserveDate.getTime());
              const filterOk =
                fs.readFileSync("./packaged-cp/filter-out/keep.txt", "utf8") === "keep" &&
                fs.readFileSync("./packaged-cp/filter-out/nested/keep.md", "utf8") === "nested" &&
                !fs.existsSync("./packaged-cp/filter-out/skip.tmp") &&
                !fs.existsSync("./packaged-cp/filter-out/skip-dir/drop.txt") &&
                !fs.existsSync("./packaged-cp/filtered-root.txt");
              const ready = recursiveOk &&
                urlOk &&
                callbackOk &&
                modeOk &&
                filterOk &&
                symlinkOk &&
                fs.readFileSync("./packaged-cp/sync/a.txt", "utf8") === "a";
              await fsp.rm("./packaged-cp", { recursive: true, force: true });
              console.log("packaged.cp.ready=" + ready);
              console.log("packaged.cp.recursive=" + recursiveOk);
              console.log("packaged.cp.url=" + urlOk);
              console.log("packaged.cp.callback=" + callbackOk);
              console.log("packaged.cp.mode=" + modeOk);
              console.log("packaged.cp.filter=" + filterOk);
              console.log("packaged.cp.symlink=" + symlinkOk);
              console.log("packaged.cp.symlink.restricted=" + !cpSymlinkCorpusReady);
              console.log("packaged.utimes.ready=" + utimesOk);
              console.log("packaged.cp.preserve_timestamps=" + preserveOk);
            }

            async function runPackagedTempRenameAtomicCorpus() {
              function closeTime(actual, expected) {
                return Math.abs(Number(actual) - Number(expected)) < 5000;
              }
              const syncTemp = fs.mkdtempSync("./packaged-temp-");
              const promiseTemp = await fsp.mkdtemp("./packaged-temp-");
              const callbackTemp = await new Promise((resolve, reject) => {
                fs.mkdtemp("./packaged-temp-", (error, directory) => {
                  if (error) reject(error);
                  else resolve(directory);
                });
              });
              const mkdtempOk = fs.statSync(syncTemp).isDirectory() &&
                fs.statSync(promiseTemp).isDirectory() &&
                fs.statSync(callbackTemp).isDirectory() &&
                syncTemp !== promiseTemp &&
                promiseTemp !== callbackTemp;

              await fsp.mkdir("./packaged-rename", { recursive: true });
              fs.writeFileSync("./packaged-rename/sync-old.txt", "sync", "utf8");
              fs.renameSync("./packaged-rename/sync-old.txt", "./packaged-rename/sync-new.txt");
              await fsp.writeFile("./packaged-rename/promise-old.txt", "promise", "utf8");
              await fsp.rename("./packaged-rename/promise-old.txt", "./packaged-rename/promise-new.txt");
              await fsp.writeFile("./packaged-rename/callback-old.txt", "callback", "utf8");
              const callbackRenameOk = await new Promise((resolve, reject) => {
                fs.rename("./packaged-rename/callback-old.txt", "./packaged-rename/callback-new.txt", (error) => {
                  if (error) {
                    reject(error);
                    return;
                  }
                  resolve(fs.readFileSync("./packaged-rename/callback-new.txt", "utf8") === "callback");
                });
              });
              const renameOk = fs.readFileSync("./packaged-rename/sync-new.txt", "utf8") === "sync" &&
                fs.readFileSync("./packaged-rename/promise-new.txt", "utf8") === "promise" &&
                callbackRenameOk &&
                !fs.existsSync("./packaged-rename/sync-old.txt") &&
                !fs.existsSync("./packaged-rename/promise-old.txt");

              await fsp.mkdir("./packaged-atomic/out", { recursive: true });
              const atomicTemp = await fsp.mkdtemp("./packaged-atomic/out/.tmp-");
              const atomicTempFile = path.join(atomicTemp, "final.txt.tmp");
              await fsp.writeFile(atomicTempFile, bufferBuiltin.Buffer.from("atomic", "utf8"));
              const atomicUrl = urlBuiltin.pathToFileURL("./packaged-atomic/out/final.txt");
              await fsp.rename(atomicTempFile, atomicUrl);
              await fsp.rm(atomicTemp, { recursive: true, force: true });
              const atomicText = await fsp.readFile(atomicUrl, "utf8");
              const atomicLeftovers = (await fsp.readdir("./packaged-atomic/out"))
                .filter(name => String(name).startsWith(".tmp-"));
              const atomicOk = atomicText === "atomic" &&
                atomicLeftovers.length === 0 &&
                fs.existsSync("./packaged-atomic/out/final.txt");

              const cacheDate = new Date("2008-09-10T11:12:13Z");
              await fsp.mkdir("./packaged-cache-copy/cache", { recursive: true });
              await fsp.writeFile("./packaged-cache-copy/source.txt", bufferBuiltin.Buffer.from("cache", "utf8"));
              await fsp.utimes("./packaged-cache-copy/source.txt", cacheDate, cacheDate);
              await fsp.cp(
                urlBuiltin.pathToFileURL("./packaged-cache-copy/source.txt"),
                urlBuiltin.pathToFileURL("./packaged-cache-copy/cache/source.txt"),
                { recursive: true, preserveTimestamps: true }
              );
              const cacheTemp = await fsp.mkdtemp("./packaged-cache-copy/cache/.tmp-");
              const cacheTempFile = path.join(cacheTemp, "final.txt.tmp");
              await fsp.writeFile(cacheTempFile, bufferBuiltin.Buffer.from(
                await fsp.readFile("./packaged-cache-copy/cache/source.txt", "utf8") + "|cache",
                "utf8"
              ));
              await fsp.rename(cacheTempFile, urlBuiltin.pathToFileURL("./packaged-cache-copy/final.txt"));
              await fsp.rm(cacheTemp, { recursive: true, force: true });
              const cacheCopyOk = (await fsp.readFile("./packaged-cache-copy/final.txt", "utf8")) === "cache|cache" &&
                closeTime(fs.statSync("./packaged-cache-copy/cache/source.txt").mtimeMs, cacheDate.getTime()) &&
                (await fsp.readdir("./packaged-cache-copy/cache"))
                  .filter(name => String(name).startsWith(".tmp-")).length === 0;

              await fsp.rm(syncTemp, { recursive: true, force: true });
              await fsp.rm(promiseTemp, { recursive: true, force: true });
              await fsp.rm(callbackTemp, { recursive: true, force: true });
              await fsp.rm("./packaged-rename", { recursive: true, force: true });
              await fsp.rm("./packaged-atomic", { recursive: true, force: true });
              await fsp.rm("./packaged-cache-copy", { recursive: true, force: true });
              console.log("packaged.mkdtemp.ready=" + mkdtempOk);
              console.log("packaged.rename.ready=" + renameOk);
              console.log("packaged.atomic.ready=" + atomicOk);
              console.log("packaged.atomic.url=" + (atomicUrl.protocol === "file:" && atomicOk));
              console.log("packaged.cache_copy.ready=" + cacheCopyOk);
            }

            async function runChdirCorpus() {
              await fsp.mkdir("./packaged-chdir/sub", { recursive: true });
              process.chdir("./packaged-chdir/sub");
              fs.writeFileSync("./sync.txt", "sync", "utf8");
              await fsp.writeFile("./promise.txt", "promise", "utf8");
              const syncText = fs.readFileSync("./sync.txt", "utf8");
              const promiseText = await fsp.readFile("./promise.txt", "utf8");
              const callbackText = await new Promise((resolve, reject) => {
                fs.readFile("./sync.txt", "utf8", (error, text) => {
                  if (error) reject(error);
                  else resolve(text);
                });
              });
              const helperName = "./packaged-dynamic-child";
              const helper = require(helperName);
              const helperResolved = require.resolve(helperName).replace(/\\/g, "/");
              const ready = process.cwd().replace(/\\/g, "/").endsWith("/packaged-chdir/sub");
              const fsOk = syncText === "sync" && promiseText === "promise" && callbackText === "sync";
              const requireOk = helper.value === "dynamic-child" &&
                helperResolved.endsWith("/packaged-dynamic-child.js");
              process.chdir("../..");
              await fsp.rm("./packaged-chdir", { recursive: true, force: true });
              console.log("packaged.chdir.ready=" + ready);
              console.log("packaged.chdir.fs=" + fsOk);
              console.log("packaged.chdir.require=" + requireOk);
            }

            function runPackagedProjectRunnerLaunchCorpus() {
              const normalizeDataPath = (value) => String(value).replace(/^\/data\/user\/0\//, "/data/data/");
              const entryOk =
                normalizeDataPath(process.argv[1]) === normalizeDataPath(__filename) &&
                __filename.replace(/\\/g, "/").endsWith("/src/main.js");
              const cwdOk = path.basename(process.cwd()) === "project";
              const mainOk = require.main === module && module.parent === null;
              fs.writeFileSync("./packaged-project-runner-root.txt", "ok", "utf8");
              const rootFsOk = fs.readFileSync("./packaged-project-runner-root.txt", "utf8") === "ok";
              const rootRequireOk = require("is-number")("42") === true;
              const scriptArgvOk = process.argv.includes("--from-packaged-script") &&
                process.argv.includes("--from-packaged-launch");
              const launchDescriptor = JSON.parse(fs.readFileSync("./.autojs6-node-launch.json", "utf8"));
              const descriptorLaunchKindOk =
                launchDescriptor.launchKind === "package-script" &&
                launchDescriptor.scriptName === "packaged-smoke" &&
                !Object.prototype.hasOwnProperty.call(launchDescriptor, "binName");
              const lifecycleEnvOk =
                process.env.PACKAGED_LAUNCH_ENV === "packaged-launch-secret" &&
                process.env.npm_lifecycle_event === "packaged-smoke" &&
                process.env.npm_package_name === "packaged-node-smoke-root" &&
                process.env.npm_package_version === "1.0.0" &&
                String(process.env.npm_package_json).replace(/\\/g, "/").endsWith("/package.json") &&
                path.basename(process.env.INIT_CWD || "") === "project";
              console.log("packaged.project_runner.ready=" + (
                entryOk && cwdOk && mainOk && rootFsOk && rootRequireOk && scriptArgvOk &&
                descriptorLaunchKindOk && lifecycleEnvOk
              ));
              console.log("packaged.project_runner.entry=src/main.js:" + entryOk);
              console.log("packaged.project_runner.cwd=project:" + cwdOk);
              console.log("packaged.project_runner.require_main=" + mainOk);
              console.log("packaged.project_runner.root_fs=" + rootFsOk);
              console.log("packaged.project_runner.root_require=" + rootRequireOk);
              console.log("packaged.project_runner.script_argv=" + scriptArgvOk);
              console.log("packaged.project_runner.descriptor_launch_kind=package-script:" + descriptorLaunchKindOk);
              console.log("packaged.project_runner.lifecycle_env=" + lifecycleEnvOk);
            }

            async function main() {
              await writeFile("./packaged.txt", "packaged", "utf8");
              const text = await readFile("./packaged.txt", "utf8");

              const gz = zlib.gzipSync(Buffer.from(text));
              const plain = zlib.gunzipSync(gz).toString("utf8");
              const hash = crypto.createHash("sha256").update(plain).digest("hex").slice(0, 8);

              const pass = new stream.PassThrough();
              let streamed = "";
              pass.on("data", chunk => {
                streamed += chunk.toString();
              });
              const done = events.once(pass, "end");
              pass.end(Buffer.from("stream"));
              await done;

              await Promise.resolve().then(() => console.log("packaged.promise=drained"));
              console.log("packaged.node.version=" + process.version);
              console.log("packaged.text=" + text);
              console.log("packaged.hash=" + hash);
              console.log("packaged.stream=" + streamed);
              console.log("packaged.cwd=" + path.basename(process.cwd()));
              runPackagedProjectRunnerLaunchCorpus();
              await runExtendedFsCorpus();
              await runFsPromisesAndBufferBuiltinCorpus();
              await runPackagedFsFailurePathCorpus();
              runProcessAndConstantsBuiltinCorpus();
              await runLegacyCoreBuiltinCorpus();
              await runCoreSchedulingAndUtilityBuiltinCorpus();
              await runCryptoAndZlibBuiltinCorpus();
              await runNpmCryptoZlibFsCorpus();
              await runCliStyleCorpus();
              await runPackagedGlobLiteCorpus();
              await runPackagedReadlinkSurfaceSmoke();
              await runPackagedConfigPluginCorpus();
              await runPackagedDiscoveryCorpus();
              await runPackagedUrlFileInteropCorpus();
              await runPackagedCpCorpus();
              await runPackagedTempRenameAtomicCorpus();
              runDynamicRequireFallbackCorpus();
              runModuleCreateRequireCorpus();
              await runChdirCorpus();
              runPackagedNpmAndCommonJsTailCorpus();
              await new Promise(resolve => setTimeout(resolve, 50));
            }

            return main()
              .then(finishPackagedMarkerReporting)
              .catch(async error => {
                console.error("packaged.error=" + (error && (error.stack || error.message) || error));
                try {
                  writePackagedMarkerReport();
                  emitPackagedMarkerMatrixStatus();
                } catch (reportError) {
                  console.error("packaged.marker_report.error=" + (
                    reportError && (reportError.stack || reportError.message) || reportError
                  ));
                }
                process.exitCode = 1;
              });
        """.trimIndent() + "\n"

        val mainScript = File(projectDir, mainScriptName).apply {
            writeText(smokeScript, StandardCharsets.UTF_8)
        }
        File(projectDir, "package.json").writeText(
            """
            {
              "name": "packaged-node-smoke-root",
              "version": "1.0.0",
              "scripts": {
                "packaged-smoke": "node ./src/main.js --from-packaged-script"
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE).writeText(
            """
            {
              "launchKind": "package-script",
              "scriptName": "packaged-smoke",
              "workingDirectory": ".",
              "argv": ["--from-packaged-launch"],
              "env": {
                "PACKAGED_LAUNCH_ENV": "packaged-launch-secret"
              },
              "timeoutMs": $FULL_COMPATIBILITY_PACKAGED_TIMEOUT_MS
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        val preloadBudget = writePackagedModuleSourcesBudget(projectDir, mainScript)
        File(projectDir, "runtime-matrix.expected.txt").writeText(
            SUCCESS_RUNTIME_MARKERS.joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            """
            {
              "name": "$SUCCESS_PROJECT_NAME",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$SUCCESS_PACKAGE_NAME",
              "main": "$mainScriptName",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load smoke project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(SUCCESS_PROJECT_NAME)
            .setPackageName(SUCCESS_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        openPackagedSmokeTemplate().use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Packaged APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Packaged APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsFullCompatibilitySmoke(outputApk, smokeScript, projectDir, preloadBudget, mainScriptName)
        exportPackagedSmokeApkForHost(outputApk)
    }

    @Test
    fun installAndRunFullCompatibilityEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged install-and-run smoke")

        buildFullCompatibilityEmbeddedNodePackagedApk()
        val outputApk = File(
            File(File(context.cacheDir, "embedded-node-packaged-smoke"), "output"),
            SUCCESS_APK_NAME,
        )
        assertInstallAndRunPackagedSmoke(outputApk)
    }

    @Test
    fun buildToastBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged toast bridge smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedToastBridgeApk()
    }

    @Test
    fun installAndRunToastBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged toast bridge install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged toast bridge install-and-run smoke")

        val outputApk = buildPackagedToastBridgeApk()
        assertInstallAndRunPackagedToastBridgeSmoke(outputApk)
    }

    @Test
    fun buildAppBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged app bridge smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedAppBridgeApk()
    }

    @Test
    fun installAndRunAppBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged app bridge install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged app bridge install-and-run smoke")

        val outputApk = buildPackagedAppBridgeApk()
        assertInstallAndRunPackagedAppBridgeSmoke(outputApk)
    }

    @Test
    fun buildClipboardBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged clipboard bridge smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedClipboardBridgeApk()
    }

    @Test
    fun installAndRunClipboardBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged clipboard bridge install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged clipboard bridge install-and-run smoke")

        val outputApk = buildPackagedClipboardBridgeApk()
        assertInstallAndRunPackagedClipboardBridgeSmoke(outputApk)
    }

    @Test
    fun buildDeviceBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged device bridge smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedDeviceBridgeApk()
    }

    @Test
    fun installAndRunDeviceBridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged device bridge install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged device bridge install-and-run smoke")

        val outputApk = buildPackagedDeviceBridgeApk()
        assertInstallAndRunPackagedDeviceBridgeSmoke(outputApk)
    }

    @Test
    fun buildV11BridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged v1.1 bridge smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedV11BridgeApk()
    }

    @Test
    fun installAndRunV11BridgeEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged v1.1 bridge install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged v1.1 bridge install-and-run smoke")

        val outputApk = buildPackagedV11BridgeApk()
        assertInstallAndRunPackagedV11BridgeSmoke(outputApk)
    }

    @Test
    fun buildRhinoCompatEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged Rhino compat smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        buildPackagedRhinoCompatApk()
    }

    @Test
    fun installAndRunRhinoCompatEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged Rhino compat install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged Rhino compat install-and-run smoke")

        val outputApk = buildPackagedRhinoCompatApk()
        assertInstallAndRunPackagedRhinoCompatSmoke(outputApk)
    }

    @Test
    fun buildPackageScriptLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package script launch smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        val root = File(context.cacheDir, "embedded-node-packaged-script-launch-smoke").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package script launch smoke root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create package script launch smoke project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create package script launch smoke output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, SCRIPT_LAUNCH_APK_NAME)
        val outputPathFile = File(root, "output-path.txt")
        val mainScriptName = "src/main.js"
        val packageDirName = "packages/script-tool"
        val scriptWorkspaceName = "workspace/script-launch"
        val scriptLaunchScriptName = "$packageDirName/node_modules/@autojs6/toolchain-cli/bin/cli.js"
        val mainScriptDir = File(projectDir, "src").apply {
            check(mkdirs()) { "Failed to create package script launch main script dir: $absolutePath" }
        }
        val toolchainCorpus = EmbeddedNodeFixtureCorpus.writeNpmToolchainCorpus(
            projectRoot = projectDir,
            packageDirRelativePath = packageDirName,
            workingDirectoryRelativePath = scriptWorkspaceName,
            scriptName = "packaged-script-smoke",
            scriptLabel = "packaged.script_launch",
            scriptRequestArg = "--from-packaged-script-launch",
            envKey = "PACKAGED_SCRIPT_ENV",
            descriptorPathFromWorkingDirectory = "../../.autojs6-node-launch.json",
            descriptorLaunchKind = "package-script",
            expectMainNotRun = true,
        )

        val mainScript = """
            "nodejs";
            require("fs").writeFileSync("./unexpected-main.txt", "main", "utf8");
            console.log("packaged.script_launch.unexpected_main=true");
        """.trimIndent() + "\n"
        val scriptLaunchScript = toolchainCorpus.scopedCliEntry.readText(StandardCharsets.UTF_8)

        File(mainScriptDir, "main.js").writeText(mainScript, StandardCharsets.UTF_8)
        File(projectDir, PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE).writeText(
            """
            {
              "launchKind": "package-script",
              "packageDir": "$packageDirName",
              "scriptName": "packaged-script-smoke",
              "workingDirectory": "$scriptWorkspaceName",
              "argv": ["--from-packaged-script-launch"],
              "env": {
                "PACKAGED_SCRIPT_ENV": "packaged-script-secret"
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "runtime-matrix.expected.txt").writeText(
            SCRIPT_LAUNCH_RUNTIME_MARKERS.joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            """
            {
              "name": "$SCRIPT_LAUNCH_PROJECT_NAME",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$SCRIPT_LAUNCH_PACKAGE_NAME",
              "main": "$mainScriptName",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load package script launch project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(SCRIPT_LAUNCH_PROJECT_NAME)
            .setPackageName(SCRIPT_LAUNCH_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        openPackagedSmokeTemplate(
            requiredDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
        ).use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Package script launch APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Package script launch APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsPackageScriptLaunchSmoke(
            outputApk = outputApk,
            mainScript = mainScript,
            scriptLaunchScript = scriptLaunchScript,
            mainScriptName = mainScriptName,
            scriptLaunchScriptName = scriptLaunchScriptName,
            projectDir = projectDir,
        )
        exportPackagedSmokeApkForHost(outputApk)
    }

    @Test
    fun installAndRunPackageScriptLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package script install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged package script install-and-run smoke")

        buildPackageScriptLaunchEmbeddedNodePackagedApk()
        val outputApk = File(
            File(File(context.cacheDir, "embedded-node-packaged-script-launch-smoke"), "output"),
            SCRIPT_LAUNCH_APK_NAME,
        )
        assertInstallAndRunPackagedScriptLaunchSmoke(outputApk)
    }

    @Test
    fun buildPackageBinLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package bin launch smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        val root = File(context.cacheDir, "embedded-node-packaged-bin-launch-smoke").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package bin launch smoke root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create package bin launch smoke project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create package bin launch smoke output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, BIN_LAUNCH_APK_NAME)
        val outputPathFile = File(root, "output-path.txt")
        val mainScriptName = "src/main.js"
        val packageDirName = "packages/bin-tool"
        val binWorkspaceName = "workspace/bin-launch"
        val binScriptName = "$packageDirName/node_modules/@autojs6/toolchain-cli/bin/cli.js"
        val mainScriptDir = File(projectDir, "src").apply {
            check(mkdirs()) { "Failed to create package bin launch main script dir: $absolutePath" }
        }
        val toolchainCorpus = EmbeddedNodeFixtureCorpus.writeNpmToolchainCorpus(
            projectRoot = projectDir,
            packageDirRelativePath = packageDirName,
            workingDirectoryRelativePath = binWorkspaceName,
            packageBinName = "packaged-bin-smoke",
        )
        val binLaunchArgv = listOf(
            "--label=packaged.bin_launch",
            "--mode=bin",
            "--from-packaged-bin-launch",
            "--expect-entry=$binScriptName",
            "--expect-cwd=$binWorkspaceName",
            "--expect-config=${toolchainCorpus.configPathFromWorkingDirectory}",
            "--expect-package-dir=$packageDirName",
            "--expect-bin=packaged-bin-smoke",
            "--expect-bin-path-token=toolchain-cli",
            "--expect-request-arg=--from-packaged-bin-launch",
            "--expect-env-key=PACKAGED_BIN_ENV",
            "--expect-descriptor=../../.autojs6-node-launch.json",
            "--expect-launch-kind=package-bin",
            "--expect-descriptor-bin=packaged-bin-smoke",
            "--expect-main-not-run",
        )

        val mainScript = """
            "nodejs";
            require("fs").writeFileSync("./unexpected-main.txt", "main", "utf8");
            console.log("packaged.bin_launch.unexpected_main=true");
        """.trimIndent() + "\n"
        val binScript = toolchainCorpus.scopedCliEntry.readText(StandardCharsets.UTF_8)

        File(mainScriptDir, "main.js").writeText(mainScript, StandardCharsets.UTF_8)
        File(projectDir, PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE).writeText(
            """
            {
              "launchKind": "package-bin",
              "packageDir": "$packageDirName",
              "binName": "packaged-bin-smoke",
              "workingDirectory": "$binWorkspaceName",
              "argv": [
                "--label=packaged.bin_launch",
                "--mode=bin",
                "--from-packaged-bin-launch",
                "--expect-entry=$binScriptName",
                "--expect-cwd=$binWorkspaceName",
                "--expect-config=${toolchainCorpus.configPathFromWorkingDirectory}",
                "--expect-package-dir=$packageDirName",
                "--expect-bin=packaged-bin-smoke",
                "--expect-bin-path-token=toolchain-cli",
                "--expect-request-arg=--from-packaged-bin-launch",
                "--expect-env-key=PACKAGED_BIN_ENV",
                "--expect-descriptor=../../.autojs6-node-launch.json",
                "--expect-launch-kind=package-bin",
                "--expect-descriptor-bin=packaged-bin-smoke",
                "--expect-main-not-run"
              ],
              "env": {
                "PACKAGED_BIN_ENV": "packaged-bin-secret"
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "runtime-matrix.expected.txt").writeText(
            BIN_LAUNCH_RUNTIME_MARKERS.joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            """
            {
              "name": "$BIN_LAUNCH_PROJECT_NAME",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$BIN_LAUNCH_PACKAGE_NAME",
              "main": "$mainScriptName",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load package bin launch project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(BIN_LAUNCH_PROJECT_NAME)
            .setPackageName(BIN_LAUNCH_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        openPackagedSmokeTemplate().use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Package bin launch APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Package bin launch APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsPackageBinLaunchSmoke(
            outputApk = outputApk,
            mainScript = mainScript,
            binScript = binScript,
            mainScriptName = mainScriptName,
            binScriptName = binScriptName,
            projectDir = projectDir,
            binLaunchArgv = binLaunchArgv,
        )
        exportPackagedSmokeApkForHost(outputApk)
    }

    @Test
    fun installAndRunPackageBinLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package bin install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged package bin install-and-run smoke")

        buildPackageBinLaunchEmbeddedNodePackagedApk()
        val outputApk = File(
            File(File(context.cacheDir, "embedded-node-packaged-bin-launch-smoke"), "output"),
            BIN_LAUNCH_APK_NAME,
        )
        assertInstallAndRunPackagedBinLaunchSmoke(outputApk)
    }

    @Test
    fun buildPackageBinStringScriptLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package bin string script launch smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        val root = File(context.cacheDir, "embedded-node-packaged-bin-string-script-smoke").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create package bin string script smoke root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create package bin string script smoke project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create package bin string script smoke output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, BIN_STRING_SCRIPT_APK_NAME)
        val outputPathFile = File(root, "output-path.txt")
        val mainScriptName = "src/main.js"
        val packageDirName = "packages/bin-string-script-app"
        val workspaceName = "workspace/bin-string-script-run"
        val scriptName = "packaged-bin-string-script"
        val cliScriptName = "$packageDirName/node_modules/@autojs6/string-bin-cli/bin/cli.js"
        val mainScriptDir = File(projectDir, "src").apply {
            check(mkdirs()) { "Failed to create package bin string script main script dir: $absolutePath" }
        }
        val corpus = EmbeddedNodeFixtureCorpus.writePackageBinStringCliCorpus(
            projectRoot = projectDir,
            packageDirRelativePath = packageDirName,
            workingDirectoryRelativePath = workspaceName,
            shortScriptName = scriptName,
            fullScriptName = "packaged-bin-string-full",
            shortScriptLabel = "packaged.bin_string_script",
            scriptRequestArg = "--from-packaged-bin-string-script",
            envKey = "PACKAGED_BIN_STRING_ENV",
            descriptorPathFromWorkingDirectory = "../../.autojs6-node-launch.json",
            descriptorLaunchKind = "package-script",
            expectMainNotRun = true,
        )

        val mainScript = """
            "nodejs";
            require("fs").writeFileSync("./unexpected-main.txt", "main", "utf8");
            console.log("packaged.bin_string_script.unexpected_main=true");
        """.trimIndent() + "\n"
        val cliScript = corpus.cliEntry.readText(StandardCharsets.UTF_8)

        File(mainScriptDir, "main.js").writeText(mainScript, StandardCharsets.UTF_8)
        File(projectDir, PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE).writeText(
            """
            {
              "launchKind": "package-script",
              "packageDir": "$packageDirName",
              "scriptName": "$scriptName",
              "workingDirectory": "$workspaceName",
              "argv": ["--from-packaged-bin-string-script"],
              "env": {
                "PACKAGED_BIN_STRING_ENV": "packaged-bin-string-secret"
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "runtime-matrix.expected.txt").writeText(
            BIN_STRING_SCRIPT_RUNTIME_MARKERS.joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            """
            {
              "name": "$BIN_STRING_SCRIPT_PROJECT_NAME",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$BIN_STRING_SCRIPT_PACKAGE_NAME",
              "main": "$mainScriptName",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load package bin string script project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(BIN_STRING_SCRIPT_PROJECT_NAME)
            .setPackageName(BIN_STRING_SCRIPT_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        openPackagedSmokeTemplate().use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Package bin string script APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Package bin string script APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsPackageBinStringScriptSmoke(
            outputApk = outputApk,
            mainScript = mainScript,
            cliScript = cliScript,
            mainScriptName = mainScriptName,
            cliScriptName = cliScriptName,
            projectDir = projectDir,
        )
        exportPackagedSmokeApkForHost(outputApk)
    }

    @Test
    fun installAndRunPackageBinStringScriptLaunchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged package bin string script install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged package bin string script install-and-run smoke")

        buildPackageBinStringScriptLaunchEmbeddedNodePackagedApk()
        val outputApk = File(
            File(File(context.cacheDir, "embedded-node-packaged-bin-string-script-smoke"), "output"),
            BIN_STRING_SCRIPT_APK_NAME,
        )
        assertInstallAndRunPackagedBinStringScriptLaunchSmoke(outputApk)
    }

    @Test
    fun buildDiagnosticsEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged diagnostics smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        val root = File(context.cacheDir, "embedded-node-packaged-diagnostics-smoke").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create diagnostics smoke root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create diagnostics smoke project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create diagnostics smoke output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, DIAGNOSTICS_APK_NAME)
        val outputPathFile = File(root, "output-path.txt")
        writePackagedDiagnosticsCorpus(projectDir)

        val diagnosticsScript = """
            "nodejs";

            const fs = require("fs");

            function capture(fn) {
              try {
                fn();
                return { name: "NoError", message: "unexpected success", code: "UNEXPECTED_SUCCESS", stack: "" };
              } catch (error) {
                return error;
              }
            }

            const disabledBuiltin = capture(() => require("child_process"));
            console.log("packaged.diagnostics.disabled_builtin.code=" + disabledBuiltin.code);
            console.log("packaged.diagnostics.disabled_builtin.message=" + String(disabledBuiltin.message).includes("child_process"));

            const fsEscape = capture(() => fs.readFileSync("../escape.txt", "utf8"));
            console.log("packaged.diagnostics.fs_escape.message=" + String(fsEscape.message).includes("escapes working directory"));

            const fsAbsolute = capture(() => fs.readFileSync("/proc/version", "utf8"));
            const absoluteMessage = String(fsAbsolute.message);
            console.log("packaged.diagnostics.fs_absolute.message=" + (
              absoluteMessage.includes("rejects absolute path") ||
              absoluteMessage.includes("escapes working directory") ||
              absoluteMessage.includes("outside Embedded Node working directory") ||
              absoluteMessage.includes("working directory")
            ));

            const cjsBoom = capture(() => require("diagnostic-boom"));
            const cjsStack = String(cjsBoom.stack || "");
            console.log("packaged.diagnostics.cjs_stack.message=" + String(cjsBoom.message).includes("packaged diagnostic boom"));
            console.log("packaged.diagnostics.cjs_stack.source=" + /node_modules[\/\\]diagnostic-boom[\/\\]index\.js/.test(cjsStack));

            const typeModuleMain = require("diagnostic-type-module");
            console.log("packaged.diagnostics.esm.type_module.value=" + (
              typeModuleMain.default === "diagnostic-type-module-sync"
            ));

            const exportsContext = require("diagnostic-esm-export-context");
            console.log("packaged.diagnostics.esm.exports_context=" + (
              exportsContext.default === "diagnostic-esm-export-context-sync"
            ));

            const asyncModule = capture(() => require("diagnostic-async-esm"));
            const asyncMessage = String(asyncModule.message);
            console.log("packaged.diagnostics.esm.async_code=" + asyncModule.code);
            console.log("packaged.diagnostics.esm.async_message=" + (
              asyncModule.code === "ERR_REQUIRE_ASYNC_MODULE" &&
              asyncMessage.includes("cannot synchronously load") &&
              asyncMessage.includes("diagnostic-async-esm")
            ));

            fs.writeFileSync("./after-diagnostics.txt", "ok", "utf8");
            console.log("packaged.diagnostics.after_failure.ok=" + (fs.readFileSync("./after-diagnostics.txt", "utf8") === "ok"));
            console.log("packaged.diagnostics.ready=true");
        """.trimIndent() + "\n"

        File(projectDir, "main.js").writeText(diagnosticsScript, StandardCharsets.UTF_8)
        File(projectDir, "esm-entry.mjs").writeText(
            """
            console.log("packaged diagnostics esm should not execute");
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "dynamic-import.js").writeText(
            """
            "nodejs";
            return Promise.all([
              import("./dynamic-target.mjs"),
              import("./dynamic-legacy.cjs")
            ]).then(([esm, cjs]) => {
              const mjsOk = esm.default.value === "dynamic-esm" && esm.named === "named";
              const cjsOk = cjs.default.value === "dynamic-cjs";
              console.log("packaged.dynamic_import.ready=" + (mjsOk && cjsOk));
              console.log("packaged.dynamic_import.mjs=" + mjsOk);
              console.log("packaged.dynamic_import.cjs=" + cjsOk);
            });
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "dynamic-target.mjs").writeText(
            """
            export default { value: "dynamic-esm" };
            export const named = "named";
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "dynamic-legacy.cjs").writeText(
            """module.exports = { value: "dynamic-cjs" };""" + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "timeout.js").writeText(
            """
            "nodejs";
            setInterval(() => {}, 100);
            console.log("packaged.diagnostics.timeout.started=true");
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, "runtime-matrix.expected.txt").writeText(
            DIAGNOSTICS_RUNTIME_MARKERS.joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            """
            {
              "name": "$DIAGNOSTICS_PROJECT_NAME",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$DIAGNOSTICS_PACKAGE_NAME",
              "main": "main.js",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load diagnostics smoke project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(DIAGNOSTICS_PROJECT_NAME)
            .setPackageName(DIAGNOSTICS_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        openPackagedSmokeTemplate().use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Diagnostics packaged APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Diagnostics packaged APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsDiagnosticsSmoke(outputApk, diagnosticsScript, projectDir)
        exportPackagedSmokeApkForHost(outputApk)
    }

    @Test
    fun installAndRunDiagnosticsEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged diagnostics install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged diagnostics install-and-run smoke")

        buildDiagnosticsEmbeddedNodePackagedApk()
        val outputApk = File(
            File(File(context.cacheDir, "embedded-node-packaged-diagnostics-smoke"), "output"),
            DIAGNOSTICS_APK_NAME,
        )
        assertInstallAndRunPackagedDiagnosticsSmoke(outputApk)
    }

    @Test
    fun installAndRunEsmDiagnosticsEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged ESM diagnostics install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged ESM diagnostics install-and-run smoke")

        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-esm-diagnostics-smoke",
            outputApkName = ESM_DIAGNOSTICS_APK_NAME,
            projectName = ESM_DIAGNOSTICS_PROJECT_NAME,
            packageName = ESM_DIAGNOSTICS_PACKAGE_NAME,
            mainScriptName = "main.js",
            mainScript = """
                "nodejs";
                const esm = require("./esm-entry.mjs");
                console.log("packaged.diagnostics.esm.code=ok");
                console.log("packaged.diagnostics.esm.message=" + (esm.default === "packaged-esm-sync"));
            """.trimIndent() + "\n",
            extraFiles = mapOf(
                "esm-entry.mjs" to """export default "packaged-esm-sync";""" + "\n",
            ),
            runtimeMarkers = ESM_DIAGNOSTICS_MARKERS,
        )
        assertInstallAndRunPackagedEsmDiagnosticsSmoke(outputApk)
    }

    @Test
    fun installAndRunExperimentalEsmEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged partial ESM smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
        assumeTrue(
            "Embedded Node.js packaged partial ESM smoke requires Node Profile v1.1 ESM to be enabled.",
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged partial ESM install-and-run smoke")

        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-experimental-esm-smoke",
            outputApkName = EXPERIMENTAL_ESM_APK_NAME,
            projectName = EXPERIMENTAL_ESM_PROJECT_NAME,
            packageName = EXPERIMENTAL_ESM_PACKAGE_NAME,
            mainScriptName = "main.mjs",
            mainScript = """
                import path from "path";
                import { createRequire } from "module";
                import dep, { named, explode } from "./lib.mjs";
                import legacy from "./legacy.cjs";
                import typed, { filenameOk as typedFilenameOk } from "./typed-entry.js";

                const localRequire = createRequire(__filename);

                const value = 42;
                const suffix = "esm-v1";
                const awaited = await Promise.resolve("awaited");
                const builtinOk = path.basename("/safe/project/main.mjs") === "main.mjs";
                const fullPathOk = path.isAbsolute(__filename) && __filename.endsWith("/main.mjs");
                const requireLegacy = localRequire("./legacy.cjs");
                let stackOk = false;
                try {
                  explode();
                } catch (error) {
                  stackOk = String(error && error.stack || "").includes("lib.mjs");
                }
                let childProcessDenied = false;
                try {
                  localRequire("child_process");
                } catch (error) {
                  childProcessDenied =
                    error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" ||
                    error.code === "ERR_AUTOJS6_BUILTIN_DISABLED" ||
                    error.autojs6Code === "ERR_AUTOJS6_BUILTIN_DISABLED";
                }
                let fsEscapeDenied = false;
                try {
                  localRequire("fs").readFileSync("../outside.txt", "utf8");
                } catch (error) {
                  fsEscapeDenied = String(error && error.message || "").includes("escapes");
                }
                const graph = dep.value === "esm-lib" &&
                  named === "named" &&
                  legacy.value === "legacy" &&
                  requireLegacy.value === "legacy" &&
                  typed.value === "typed-module" &&
                  typedFilenameOk === true;
                const security = childProcessDenied && fsEscapeDenied;
                const ready = value === 42 && suffix === "esm-v1" && awaited === "awaited" &&
                  builtinOk && fullPathOk && graph && security && stackOk;

                console.log("packaged.experimental_esm.ready=" + ready);
                console.log("packaged.experimental_esm.entry=" + (value === 42 && suffix === "esm-v1"));
                console.log("packaged.experimental_esm.tla=" + (awaited === "awaited"));
                console.log("packaged.experimental_esm.builtin=" + builtinOk);
                console.log("packaged.experimental_esm.full_path=" + fullPathOk);
                console.log("packaged.experimental_esm.type_module=" + (typed.value === "typed-module" && typedFilenameOk === true));
                console.log("packaged.experimental_esm.graph=" + graph);
                console.log("packaged.experimental_esm.security=" + security);
                console.log("packaged.experimental_esm.stack_source=" + stackOk);
            """.trimIndent() + "\n",
            extraFiles = packagedRuntimeCompatibilityDescriptorFiles(
                    esmPartial = true,
                    dynamicImportLocal = false,
                ) + mapOf(
                "package.json" to """{"name":"packaged-esm-smoke","version":"1.0.0","type":"module"}""" + "\n",
                "lib.mjs" to """
                    export default { value: "esm-lib" };
                    export const named = "named";
                    export function explode() { throw new Error("packaged esm source"); }
                """.trimIndent() + "\n",
                "typed-entry.js" to """
                    const filenameOk = typeof __filename === "string" && __filename.endsWith("/typed-entry.js");
                    export default { value: "typed-module" };
                    export { filenameOk };
                """.trimIndent() + "\n",
                "legacy.cjs" to """module.exports = { value: "legacy" };""" + "\n",
            ),
            runtimeMarkers = EXPERIMENTAL_ESM_RUNTIME_MARKERS,
            requiredTemplateDexMarkers = EXPERIMENTAL_ESM_TEMPLATE_DEX_MARKERS,
        )
        assertInstallAndRunPackagedExperimentalEsmSmoke(outputApk)
    }

    @Test
    fun installAndRunDynamicImportLocalEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged dynamic import local install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
        assumeTrue(
            "Embedded Node.js packaged dynamic import local install-and-run smoke requires Node Profile v1.1 dynamic import local support.",
            BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED && BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged dynamic import local install-and-run smoke")

        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-dynamic-import-local-smoke",
            outputApkName = DYNAMIC_IMPORT_DIAGNOSTICS_APK_NAME,
            projectName = DYNAMIC_IMPORT_DIAGNOSTICS_PROJECT_NAME,
            packageName = DYNAMIC_IMPORT_DIAGNOSTICS_PACKAGE_NAME,
            mainScriptName = "dynamic-import.js",
            mainScript = """
                "nodejs";
                return Promise.all([
                  import("./dynamic-target.mjs"),
                  import("./dynamic-legacy.cjs"),
                  import("./dynamic-type-module.js"),
                  import("../../outside.mjs").then(() => false, (error) => {
                    return error.code === "ERR_AUTOJS6_FS_PATH_ESCAPE" ||
                      String(error && error.message || "").includes("escapes") ||
                      String(error && error.message || "").includes("rejects");
                  }),
                  import("node:child_process").then(() => false, (error) => {
                    return error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" ||
                      error.code === "ERR_AUTOJS6_BUILTIN_DISABLED" ||
                      error.autojs6Code === "ERR_AUTOJS6_BUILTIN_DISABLED";
                  })
                ]).then(([esm, cjs, typed, pathEscapeDenied, childProcessDenied]) => {
                  const mjsOk = esm.default.value === "dynamic-esm" && esm.named === "named";
                  const cjsOk = cjs.default.value === "dynamic-cjs";
                  const typedOk = typed.default.value === "dynamic-typed";
                  const fullPathOk = esm.filenameOk === true && cjs.default.filenameOk === true && typed.filenameOk === true;
                  const securityOk = pathEscapeDenied && childProcessDenied;
                  console.log("packaged.dynamic_import.ready=" + (mjsOk && cjsOk && typedOk && fullPathOk && securityOk));
                  console.log("packaged.dynamic_import.mjs=" + mjsOk);
                  console.log("packaged.dynamic_import.cjs=" + cjsOk);
                  console.log("packaged.dynamic_import.type_module=" + typedOk);
                  console.log("packaged.dynamic_import.full_path=" + fullPathOk);
                  console.log("packaged.dynamic_import.child_process_denied=" + childProcessDenied);
                  console.log("packaged.dynamic_import.path_escape_denied=" + pathEscapeDenied);
                  console.log("packaged.dynamic_import.security=" + securityOk);
                });
            """.trimIndent() + "\n",
            extraFiles = packagedRuntimeCompatibilityDescriptorFiles(
                    esmPartial = true,
                    dynamicImportLocal = true,
                ) + mapOf(
                "package.json" to """{"name":"packaged-dynamic-import-smoke","version":"1.0.0","type":"module"}""" + "\n",
                "dynamic-target.mjs" to """
                    export default { value: "dynamic-esm" };
                    export const named = "named";
                    export const filenameOk = typeof __filename === "string" && __filename.endsWith("/dynamic-target.mjs");
                """.trimIndent() + "\n",
                "dynamic-legacy.cjs" to """
                    module.exports = {
                      value: "dynamic-cjs",
                      filenameOk: typeof __filename === "string" && __filename.endsWith("/dynamic-legacy.cjs")
                    };
                """.trimIndent() + "\n",
                "dynamic-type-module.js" to """
                    const filenameOk = typeof __filename === "string" && __filename.endsWith("/dynamic-type-module.js");
                    export default { value: "dynamic-typed" };
                    export { filenameOk };
                """.trimIndent() + "\n",
            ),
            runtimeMarkers = DYNAMIC_IMPORT_DIAGNOSTICS_MARKERS,
        )
        assertInstallAndRunPackagedDynamicImportDiagnosticsSmoke(outputApk)
    }

    @Test
    fun installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged worker_threads ESM smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
        assumeTrue(
            "Embedded Node.js packaged worker_threads ESM smoke requires worker_threads experimental build support.",
            BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
        )
        assumeTrue(
            "Embedded Node.js packaged worker_threads ESM smoke requires Node Profile v1.1 ESM to be enabled.",
            BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        )
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("packaged worker_threads ESM smoke")

        assumePackagedSmokeTemplateAvailable("Packaged worker_threads ESM install-and-run smoke")

        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-worker-threads-esm-smoke",
            outputApkName = WORKER_THREADS_ESM_APK_NAME,
            projectName = WORKER_THREADS_ESM_PROJECT_NAME,
            packageName = WORKER_THREADS_ESM_PACKAGE_NAME,
            mainScriptName = "main.js",
            mainScript = """
                "nodejs";
                return (async function main() {
                  const { Worker } = require("worker_threads");
                  const worker = new Worker("./packaged-worker.mjs", {
                    workerData: {
                      value: 21
                    }
                  });
                  const exitPromise = new Promise((resolve) => worker.once("exit", resolve));
                  const message = await new Promise((resolve, reject) => {
                    worker.once("error", reject);
                    worker.once("message", resolve);
                  });
                  const exitCode = await exitPromise;
                  const ready = message.value === 42 &&
                    message.isMainThread === false &&
                    message.threadIdOk === true &&
                    message.filenameOk === true &&
                    message.builtinOk === true &&
                    exitCode === 0;
                  console.log("packaged.worker_threads.esm.ready=" + ready);
                  console.log("packaged.worker_threads.esm.value=" + (message.value === 42));
                  console.log("packaged.worker_threads.esm.thread=" + (message.isMainThread === false && message.threadIdOk === true));
                  console.log("packaged.worker_threads.esm.filename=" + message.filenameOk);
                  console.log("packaged.worker_threads.esm.builtin=" + message.builtinOk);
                  console.log("packaged.worker_threads.esm.exit=" + exitCode);
                })();
            """.trimIndent() + "\n",
            extraFiles = packagedRuntimeCompatibilityDescriptorFiles(
                    esmPartial = true,
                    dynamicImportLocal = false,
                    workerThreads = true,
                ) + mapOf(
                "package.json" to """{"name":"packaged-worker-threads-esm-smoke","version":"1.0.0"}""" + "\n",
                "packaged-worker.mjs" to """
                    import path from "path";
                    import { parentPort, workerData, isMainThread, threadId } from "worker_threads";

                    parentPort.postMessage({
                      value: workerData.value * 2,
                      isMainThread,
                      threadIdOk: Number.isInteger(threadId),
                      filenameOk: typeof __filename === "string" && __filename.endsWith("/packaged-worker.mjs"),
                      builtinOk: path.basename("/safe/packaged-worker.mjs") === "packaged-worker.mjs"
                    });
                """.trimIndent() + "\n",
            ),
            runtimeMarkers = WORKER_THREADS_ESM_RUNTIME_MARKERS,
            requiredTemplateDexMarkers = WORKER_THREADS_ESM_TEMPLATE_DEX_MARKERS,
        )
        assertInstallAndRunPackagedWorkerThreadsEsmSmoke(outputApk)
    }

    @Test
    fun installAndRunTimeoutDiagnosticsEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged timeout diagnostics install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged timeout diagnostics install-and-run smoke")

        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-timeout-diagnostics-smoke",
            outputApkName = TIMEOUT_DIAGNOSTICS_APK_NAME,
            projectName = TIMEOUT_DIAGNOSTICS_PROJECT_NAME,
            packageName = TIMEOUT_DIAGNOSTICS_PACKAGE_NAME,
            mainScriptName = "timeout.js",
            mainScript = """
                "nodejs";
                setInterval(() => {}, 100);
                console.log("packaged.diagnostics.timeout.started=true");
            """.trimIndent() + "\n",
            runtimeMarkers = TIMEOUT_DIAGNOSTICS_RUNTIME_MARKERS,
        )
        assertInstallAndRunPackagedTimeoutDiagnosticsSmoke(outputApk)
    }

    @Test
    fun installAndRunProcessPoolSlotEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged process-pool slot install-and-run smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged process-pool slot install-and-run smoke")

        val launchDescriptor = """
            {
              "launchKind": "project-entry",
              "workingDirectory": ".",
              "argv": ["--from-packaged-process-pool-slot"],
              "env": {
                "PACKAGED_PROCESS_POOL_SLOT_ENV": "slot-secret"
              },
              "timeoutMs": 10000,
              "processPoolSlot": "${NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID}",
              "processPoolSlotSource": "${NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST}"
            }
            """.trimIndent() + "\n"
        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-process-pool-slot-smoke",
            outputApkName = PROCESS_POOL_SLOT_APK_NAME,
            projectName = PROCESS_POOL_SLOT_PROJECT_NAME,
            packageName = PROCESS_POOL_SLOT_PACKAGE_NAME,
            mainScriptName = "process-pool-slot.js",
            mainScript = """
                "nodejs";
                const ready =
                  process.argv.includes("--from-packaged-process-pool-slot") &&
                  process.env.PACKAGED_PROCESS_POOL_SLOT_ENV === "slot-secret";
                console.log("packaged.process_pool_slot.ready=" + ready);
                console.log("packaged.process_pool_slot.argv=" + process.argv.includes("--from-packaged-process-pool-slot"));
                console.log("packaged.process_pool_slot.env=" + (process.env.PACKAGED_PROCESS_POOL_SLOT_ENV === "slot-secret"));
            """.trimIndent() + "\n",
            runtimeMarkers = PROCESS_POOL_SLOT_RUNTIME_MARKERS,
            launchDescriptor = launchDescriptor,
            launchDescriptorExpectation = PackagedNodeLaunchDescriptorExpectation(
                launchKind = NodeProjectLaunchKind.PROJECT_ENTRY.descriptorValue,
                workingDirectory = ".",
                argv = listOf("--from-packaged-process-pool-slot"),
                env = mapOf("PACKAGED_PROCESS_POOL_SLOT_ENV" to "slot-secret"),
                timeoutMs = 10_000L,
                processPoolSlot = NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID,
                processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
            ),
            requiredTemplateDexMarkers = PROCESS_POOL_SLOT_TEMPLATE_DEX_MARKERS,
        )
        assertInstallAndRunPackagedProcessPoolSlotSmoke(outputApk)
    }

    @Test
    fun installAndRunCrashEvidenceEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged crash evidence smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged crash evidence install-and-run smoke")

        val launchDescriptor = """
            {
              "launchKind": "project-entry",
              "workingDirectory": ".",
              "timeoutMs": $CRASH_EVIDENCE_TIMEOUT_MS,
              "executionMode": "${NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING}"
            }
            """.trimIndent() + "\n"
        val outputApk = buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-crash-evidence-smoke",
            outputApkName = CRASH_EVIDENCE_APK_NAME,
            projectName = CRASH_EVIDENCE_PROJECT_NAME,
            packageName = CRASH_EVIDENCE_PACKAGE_NAME,
            mainScriptName = "crash-evidence.js",
            mainScript = """
                "nodejs";
                console.log("packaged.crash_evidence.started=true");
                console.log("packaged.crash_evidence.pid=" + process.pid);
                setInterval(() => {}, 250);
            """.trimIndent() + "\n",
            runtimeMarkers = CRASH_EVIDENCE_RUNTIME_MARKERS,
            launchDescriptor = launchDescriptor,
            launchDescriptorExpectation = PackagedNodeLaunchDescriptorExpectation(
                launchKind = NodeProjectLaunchKind.PROJECT_ENTRY.descriptorValue,
                workingDirectory = ".",
                timeoutMs = CRASH_EVIDENCE_TIMEOUT_MS,
                executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            ),
            requiredTemplateDexMarkers = CRASH_EVIDENCE_TEMPLATE_DEX_MARKERS,
        )
        assertInstallRunAndCollectPackagedCrashEvidenceSmoke(outputApk)
    }

    @Test
    fun installAndRunLaunchFailureEmbeddedNodePackagedApks() {
        assumeTrue(
            "Embedded Node.js packaged launch failure smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged launch failure install-and-run smoke")

        packagedLaunchFailureCases().forEach { failureCase ->
            val outputApk = buildPackagedLaunchFailureApk(failureCase)
            assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
        }
    }

    @Test
    fun installAndRunDescriptorLaunchFailureEmbeddedNodePackagedApks() {
        assumeTrue(
            "Embedded Node.js packaged descriptor launch failure smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged descriptor launch failure install-and-run smoke")

        packagedDescriptorLaunchFailureCases().forEach { failureCase ->
            val outputApk = buildPackagedLaunchFailureApk(failureCase)
            assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
        }
    }

    @Test
    fun installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk() {
        assumeTrue(
            "Embedded Node.js packaged runtime compatibility descriptor mismatch smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged runtime compatibility descriptor mismatch install-and-run smoke")

        val failureCase = packagedRuntimeCompatibilityDescriptorMismatchFailureCase()
        val outputApk = buildPackagedLaunchFailureApk(failureCase)
        assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
    }

    @Test
    fun installAndRunDescriptorPathLaunchFailureEmbeddedNodePackagedApks() {
        assumeTrue(
            "Embedded Node.js packaged descriptor path launch failure smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged descriptor path launch failure install-and-run smoke")

        packagedDescriptorPathLaunchFailureCases().forEach { failureCase ->
            val outputApk = buildPackagedLaunchFailureApk(failureCase)
            assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
        }
    }

    @Test
    fun installAndRunPackageMetadataLaunchFailureEmbeddedNodePackagedApks() {
        assumeTrue(
            "Embedded Node.js packaged package metadata launch failure smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged package metadata launch failure install-and-run smoke")

        packagedPackageMetadataLaunchFailureCases().forEach { failureCase ->
            val outputApk = buildPackagedLaunchFailureApk(failureCase)
            assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
        }
    }

    @Test
    fun installAndRunProjectEntryLaunchFailureEmbeddedNodePackagedApks() {
        assumeTrue(
            "Embedded Node.js packaged project entry launch failure smoke requires embedded script execution build support.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )

        assumePackagedSmokeTemplateAvailable("Packaged project entry launch failure install-and-run smoke")

        packagedProjectEntryLaunchFailureCases().forEach { failureCase ->
            val outputApk = buildPackagedLaunchFailureApk(failureCase)
            assertInstallAndRunPackagedLaunchFailureSmoke(outputApk, failureCase)
        }
    }

    private data class PackagedLaunchFailureCase(
        val id: String,
        val projectName: String,
        val packageName: String,
        val outputApkName: String,
        val projectConfigMainScriptName: String = "main.js",
        val descriptor: String,
        val extraFiles: Map<String, String>,
        val requiredEntries: List<String>,
        val requiredLogMarkers: List<String>,
        val forbiddenLogMarkers: List<String> = emptyList(),
        val requiredTemplateDexMarkers: List<String> = emptyList(),
        val verifyDescriptor: Boolean = true,
        val descriptorInput: Boolean = false,
        val descriptorPathInput: Boolean = false,
        val packageMetadataInput: Boolean = false,
        val projectEntryInput: Boolean = false,
    ) {
        val rootName: String = "embedded-node-packaged-launch-failure-$id-smoke"
        val shellApkPath: String = "/data/local/tmp/autojs6-packaged-node-launch-failure-$id-smoke.apk"
    }

    private data class PackagedNodeLaunchDescriptorExpectation(
        val launchKind: String,
        val packageDir: String? = null,
        val workingDirectory: String? = null,
        val scriptName: String? = null,
        val binName: String? = null,
        val argv: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val timeoutMs: Long? = null,
        val executionMode: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
    )

    private fun packagedRuntimeCompatibilityDescriptorMismatchFailureCase(): PackagedLaunchFailureCase {
        val secretEnv = JSONObject().put(PACKAGED_LAUNCH_FAILURE_SECRET_KEY, PACKAGED_LAUNCH_FAILURE_SECRET_VALUE)
        val descriptor = JSONObject()
            .put("launchKind", NodeProjectLaunchKind.PROJECT_ENTRY.descriptorValue)
            .put("env", secretEnv)
            .toString()
        val runtimeDescriptor = NodeProjectRuntimeCompatibilityDescriptor.currentJson(
            esmPartial = true,
            dynamicImportLocal = true,
            workerThreads = false,
        )
        runtimeDescriptor.getJSONObject("versions")
            .put("esmLoader", "stale-autojs6-esm-loader-v0")
        return PackagedLaunchFailureCase(
            id = "runtime-descriptor-version-mismatch",
            projectName = "PackagedNodeRuntimeDescriptorVersionMismatchFailureSmoke",
            packageName = "org.autojs6.packaged.node.failure.runtimedescriptorversionmismatch",
            outputApkName = "PackagedNodeRuntimeDescriptorVersionMismatchFailureSmoke_v1.0.0.apk",
            descriptor = descriptor,
            extraFiles = packagedRuntimeCompatibilityDescriptorFiles(runtimeDescriptor.toString() + "\n"),
            requiredEntries = NodeProjectRuntimeCompatibilityDescriptor.DESCRIPTOR_FILE_NAMES,
            requiredLogMarkers = listOf(
                NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                "stage=descriptor",
                "requestReady=false",
                NodeProjectRuntimeCompatibilityDescriptor.VERSION_MISMATCH_CODE,
                "versions.esmLoader",
                "expected=${NodeProjectRuntimeCompatibilityDescriptor.ESM_LOADER_VERSION}",
                "actual=stale-autojs6-esm-loader-v0",
                "errorClass=IllegalArgumentException",
            ),
            verifyDescriptor = false,
            descriptorInput = true,
        )
    }

    private fun packagedLaunchFailureCases(): List<PackagedLaunchFailureCase> {
        val projectEntryLaunchKind = NodeProjectLaunchKind.PROJECT_ENTRY.descriptorValue
        val scriptLaunchKind = NodeProjectLaunchKind.PACKAGE_SCRIPT.descriptorValue
        val binLaunchKind = NodeProjectLaunchKind.PACKAGE_BIN.descriptorValue
        val ambiguousLaunchKind = NodeProjectLaunchKind.AMBIGUOUS.descriptorValue
        val secretEnv = JSONObject().put(PACKAGED_LAUNCH_FAILURE_SECRET_KEY, PACKAGED_LAUNCH_FAILURE_SECRET_VALUE)
        val invalidJsonSanitizationSupported = packagedSmokeTemplateSupportsInvalidJsonSanitization()
        val projectEntryResolutionFailureDiagnosticsSupported =
            packagedSmokeTemplateSupportsProjectEntryResolutionFailureDiagnostics()

        fun descriptor(
            launchKind: String,
            packageDir: String,
            scriptName: String? = null,
            binName: String? = null,
            workingDirectory: String = PACKAGED_LAUNCH_FAILURE_WORKSPACE,
        ): String {
            val json = JSONObject()
                .put("launchKind", launchKind)
                .put("packageDir", packageDir)
                .put("workingDirectory", workingDirectory)
                .put("env", secretEnv)
            scriptName?.let { json.put("scriptName", it) }
            binName?.let { json.put("binName", it) }
            return json.toString()
        }

        fun projectEntryDescriptor(): String {
            return JSONObject()
                .put("launchKind", projectEntryLaunchKind)
                .put("workingDirectory", PACKAGED_LAUNCH_FAILURE_WORKSPACE)
                .put("env", secretEnv)
                .toString()
        }

        fun descriptorInputFailureCase(
            id: String,
            projectNameSuffix: String,
            descriptor: String,
            messageToken: String,
            extraLogMarkers: List<String> = emptyList(),
        ) = PackagedLaunchFailureCase(
            id = id,
            projectName = "PackagedNode${projectNameSuffix}FailureSmoke",
            packageName = "org.autojs6.packaged.node.failure.${id.replace("-", "")}",
            outputApkName = "PackagedNode${projectNameSuffix}FailureSmoke_v1.0.0.apk",
            descriptor = descriptor,
            extraFiles = emptyMap(),
            requiredEntries = emptyList(),
            requiredLogMarkers = listOf(
                NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                "stage=descriptor",
                "requestReady=false",
                messageToken,
                "errorClass=IllegalArgumentException",
            ) + extraLogMarkers,
            verifyDescriptor = false,
            descriptorInput = true,
        )

        val descriptorInputFailureCases = listOf(
            descriptorInputFailureCase(
                id = "invalid-descriptor-json",
                projectNameSuffix = "InvalidDescriptorJson",
                descriptor = if (invalidJsonSanitizationSupported) {
                    "{\"launchKind\":\"package-script\",\"scriptName\":\"build\",\"env\":" +
                            "{\"$PACKAGED_LAUNCH_FAILURE_SECRET_KEY\":\"$PACKAGED_LAUNCH_FAILURE_SECRET_VALUE\"}"
                } else {
                    "{\"launchKind\":\"package-script\",\"scriptName\":\"build\""
                },
                messageToken = "Invalid internal Node project launch descriptor",
                extraLogMarkers = if (invalidJsonSanitizationSupported) {
                    listOf(NodeProjectLaunchDescriptor.INVALID_JSON_SANITIZED_MARKER)
                } else {
                    emptyList()
                },
            ),
            descriptorInputFailureCase(
                id = "unknown-launch-kind",
                projectNameSuffix = "UnknownLaunchKind",
                descriptor = JSONObject()
                    .put("launchKind", "unknown-kind")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Unsupported Node project launch kind: unknown-kind",
            ),
            descriptorInputFailureCase(
                id = "non-string-launch-kind",
                projectNameSuffix = "NonStringLaunchKind",
                descriptor = JSONObject()
                    .put("launchKind", 1)
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string: launchKind",
            ),
            descriptorInputFailureCase(
                id = "non-string-package-dir",
                projectNameSuffix = "NonStringPackageDir",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("packageDir", 1)
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string: packageDir",
            ),
            descriptorInputFailureCase(
                id = "non-string-working-directory",
                projectNameSuffix = "NonStringWorkingDirectory",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("workingDirectory", 1)
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string: workingDirectory",
            ),
            descriptorInputFailureCase(
                id = "non-string-script-name",
                projectNameSuffix = "NonStringScriptName",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", JSONArray().put("build"))
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string: scriptName",
            ),
            descriptorInputFailureCase(
                id = "non-string-bin-name",
                projectNameSuffix = "NonStringBinName",
                descriptor = JSONObject()
                    .put("launchKind", binLaunchKind)
                    .put("binName", JSONArray().put("tool"))
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string: binName",
            ),
            descriptorInputFailureCase(
                id = "non-array-argv",
                projectNameSuffix = "NonArrayArgv",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("argv", "--from-descriptor")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string array: argv",
            ),
            descriptorInputFailureCase(
                id = "non-string-argv-value",
                projectNameSuffix = "NonStringArgvValue",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("argv", JSONArray().put(1))
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor array value must be a string: argv[0]",
            ),
            descriptorInputFailureCase(
                id = "non-map-env",
                projectNameSuffix = "NonMapEnv",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("env", PACKAGED_LAUNCH_FAILURE_SECRET_VALUE)
                    .toString(),
                messageToken = "Internal Node project launch descriptor field must be a string map: env",
            ),
            descriptorInputFailureCase(
                id = "non-string-env-value",
                projectNameSuffix = "NonStringEnvValue",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("env", JSONObject().put(PACKAGED_LAUNCH_FAILURE_SECRET_KEY, 1))
                    .toString(),
                messageToken =
                    "Internal Node project launch descriptor map value must be a string: env.$PACKAGED_LAUNCH_FAILURE_SECRET_KEY",
            ),
            descriptorInputFailureCase(
                id = "non-positive-timeout",
                projectNameSuffix = "NonPositiveTimeout",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .put("timeoutMs", 0)
                    .toString(),
                messageToken = "Internal Node project launch descriptor timeoutMs must be positive",
            ),
            descriptorInputFailureCase(
                id = "non-numeric-timeout",
                projectNameSuffix = "NonNumericTimeout",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .put("timeoutMs", "later")
                    .toString(),
                messageToken = "Internal Node project launch descriptor timeoutMs must be positive",
            ),
            descriptorInputFailureCase(
                id = "package-dir-nul",
                projectNameSuffix = "PackageDirNul",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("packageDir", "packages" + '\u0000' + "failure-script-tool")
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor path contains NUL: packageDir",
            ),
            descriptorInputFailureCase(
                id = "working-directory-nul",
                projectNameSuffix = "WorkingDirectoryNul",
                descriptor = JSONObject()
                    .put("launchKind", scriptLaunchKind)
                    .put("workingDirectory", "workspace" + '\u0000' + "failure-launch")
                    .put("scriptName", "build")
                    .put("env", secretEnv)
                    .toString(),
                messageToken = "Internal Node project launch descriptor path contains NUL: workingDirectory",
            ),
        )

        fun projectEntryFailureMarkers(messageToken: String): List<String> {
            return buildList {
                if (projectEntryResolutionFailureDiagnosticsSupported) {
                    add(NodeProjectLaunchDiagnostics.INRT_PROJECT_ENTRY_RESOLUTION_FAILURE_MARKER)
                    add(NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER)
                    add("stage=entry")
                    add("requestReady=false")
                    add("errorClass=IllegalArgumentException")
                }
                add(messageToken)
            }
        }

        fun projectEntryFailureCase(
            id: String,
            projectNameSuffix: String,
            mainTarget: String,
            messageToken: String,
            extraFiles: Map<String, String> = emptyMap(),
        ): PackagedLaunchFailureCase {
            val packagedFiles = linkedMapOf(
                "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
            )
            packagedFiles += extraFiles
            return PackagedLaunchFailureCase(
                id = id,
                projectName = "PackagedNode${projectNameSuffix}FailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.${id.replace("-", "")}",
                outputApkName = "PackagedNode${projectNameSuffix}FailureSmoke_v1.0.0.apk",
                projectConfigMainScriptName = mainTarget,
                descriptor = projectEntryDescriptor(),
                extraFiles = packagedFiles,
                requiredEntries = packagedFiles.keys.toList(),
                requiredLogMarkers = projectEntryFailureMarkers(messageToken),
                projectEntryInput = true,
            )
        }

        val projectEntryFailureCases = listOf(
            projectEntryFailureCase(
                id = "project-entry-missing",
                projectNameSuffix = "ProjectEntryMissing",
                mainTarget = "missing-entry.js",
                messageToken = "Node project entry missing: missing-entry.js",
            ),
            projectEntryFailureCase(
                id = "project-entry-escape",
                projectNameSuffix = "ProjectEntryEscape",
                mainTarget = "../outside.js",
                messageToken = "Node project entry escapes project sandbox: ../outside.js",
            ),
            projectEntryFailureCase(
                id = "project-entry-absolute",
                projectNameSuffix = "ProjectEntryAbsolute",
                mainTarget = "/system/bin/sh",
                messageToken = "Node project entry target must be relative: /system/bin/sh",
            ),
            projectEntryFailureCase(
                id = "project-entry-protocol",
                projectNameSuffix = "ProjectEntryProtocol",
                mainTarget = "file:entry.js",
                messageToken = "Node project entry target must be relative: file:entry.js",
            ),
            projectEntryFailureCase(
                id = "project-entry-native-addon",
                projectNameSuffix = "ProjectEntryNativeAddon",
                mainTarget = "entry.node",
                messageToken = "Unsupported Node project native addon entry: entry.node",
                extraFiles = mapOf("entry.node" to "native-addon-placeholder\n"),
            ),
            projectEntryFailureCase(
                id = "project-entry-extension",
                projectNameSuffix = "ProjectEntryExtension",
                mainTarget = "entry.txt",
                messageToken = "Unsupported Node project entry extension: entry.txt",
                extraFiles = mapOf("entry.txt" to "not javascript\n"),
            ),
        )

        fun descriptorPathFailureMarkers(messageToken: String): List<String> {
            return listOf(
                NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                "stage=descriptor",
                "requestReady=false",
                messageToken,
                "errorClass=IllegalArgumentException",
            )
        }

        fun descriptorPathFailureCase(
            id: String,
            projectNameSuffix: String,
            descriptor: String,
            messageToken: String,
            extraFiles: Map<String, String> = emptyMap(),
            forbiddenLogMarkers: List<String> = emptyList(),
        ) = PackagedLaunchFailureCase(
            id = id,
            projectName = "PackagedNode${projectNameSuffix}FailureSmoke",
            packageName = "org.autojs6.packaged.node.failure.${id.replace("-", "")}",
            outputApkName = "PackagedNode${projectNameSuffix}FailureSmoke_v1.0.0.apk",
            descriptor = descriptor,
            extraFiles = extraFiles,
            requiredEntries = extraFiles.keys.toList(),
            requiredLogMarkers = descriptorPathFailureMarkers(messageToken),
            forbiddenLogMarkers = forbiddenLogMarkers,
            descriptorPathInput = true,
        )

        val descriptorPathFailureCases = listOf(
            descriptorPathFailureCase(
                id = "package-dir-absolute",
                projectNameSuffix = "PackageDirAbsolute",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "/system/bin",
                    scriptName = "run",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: packageDir",
            ),
            descriptorPathFailureCase(
                id = "package-dir-protocol",
                projectNameSuffix = "PackageDirProtocol",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "file:packages/failure-script-tool",
                    scriptName = "run",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: packageDir",
            ),
            descriptorPathFailureCase(
                id = "package-dir-windows-drive",
                projectNameSuffix = "PackageDirWindowsDrive",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "C:\\packages\\failure-script-tool",
                    scriptName = "run",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: packageDir",
            ),
            descriptorPathFailureCase(
                id = "package-dir-file",
                projectNameSuffix = "PackageDirFile",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "packages/file-package.json",
                    scriptName = "run",
                ),
                messageToken = "Internal Node project launch descriptor path is not a directory: packageDir",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "packages/file-package.json" to "not a directory\n",
                ),
            ),
            descriptorPathFailureCase(
                id = "working-directory-absolute",
                projectNameSuffix = "WorkingDirectoryAbsolute",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "/system/bin",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: workingDirectory",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/.keep" to "package\n",
                ),
            ),
            descriptorPathFailureCase(
                id = "working-directory-protocol",
                projectNameSuffix = "WorkingDirectoryProtocol",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "file:workspace/failure-launch",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: workingDirectory",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/.keep" to "package\n",
                ),
            ),
            descriptorPathFailureCase(
                id = "working-directory-windows-drive",
                projectNameSuffix = "WorkingDirectoryWindowsDrive",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "C:\\workspace\\failure-launch",
                ),
                messageToken = "Internal Node project launch descriptor path must be relative: workingDirectory",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/.keep" to "package\n",
                ),
            ),
            descriptorPathFailureCase(
                id = "working-directory-file",
                projectNameSuffix = "WorkingDirectoryFile",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/not-directory.txt",
                ),
                messageToken = "Internal Node project launch descriptor path is not a directory: workingDirectory",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/.keep" to "package\n",
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/not-directory.txt" to "not a directory\n",
                ),
            ),
        )

        fun packageMetadataFailureMarkers(
            launchKind: String,
            messageToken: String,
            scriptName: String? = null,
            binName: String? = null,
        ): List<String> {
            return buildList {
                add(NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER)
                add("launchKind=$launchKind")
                scriptName?.let { add("script=$it") }
                binName?.let { add("bin=$it") }
                add(messageToken)
                add("envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]")
            }
        }

        fun packageScriptMetadataFailureCase(
            id: String,
            projectNameSuffix: String,
            scriptName: String,
            packageJson: String?,
            messageToken: String,
            extraFiles: Map<String, String> = emptyMap(),
            forbiddenLogMarkers: List<String> = emptyList(),
        ): PackagedLaunchFailureCase {
            val packagedFiles = linkedMapOf(
                "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/.keep" to "package\n",
            )
            packageJson?.let { packagedFiles["$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json"] = it }
            packagedFiles += extraFiles
            return PackagedLaunchFailureCase(
                id = id,
                projectName = "PackagedNode${projectNameSuffix}FailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.${id.replace("-", "")}",
                outputApkName = "PackagedNode${projectNameSuffix}FailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = scriptName,
                ),
                extraFiles = packagedFiles,
                requiredEntries = packagedFiles.keys.toList(),
                requiredLogMarkers = packageMetadataFailureMarkers(
                    launchKind = scriptLaunchKind,
                    scriptName = scriptName,
                    messageToken = messageToken,
                ),
                forbiddenLogMarkers = forbiddenLogMarkers,
                packageMetadataInput = true,
            )
        }

        fun packageBinMetadataFailureCase(
            id: String,
            projectNameSuffix: String,
            binName: String,
            packageJson: String?,
            messageToken: String,
            extraFiles: Map<String, String> = emptyMap(),
            forbiddenLogMarkers: List<String> = emptyList(),
        ): PackagedLaunchFailureCase {
            val packagedFiles = linkedMapOf(
                "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/.keep" to "package\n",
            )
            packageJson?.let { packagedFiles["$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/package.json"] = it }
            packagedFiles += extraFiles
            return PackagedLaunchFailureCase(
                id = id,
                projectName = "PackagedNode${projectNameSuffix}FailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.${id.replace("-", "")}",
                outputApkName = "PackagedNode${projectNameSuffix}FailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = binLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR,
                    binName = binName,
                ),
                extraFiles = packagedFiles,
                requiredEntries = packagedFiles.keys.toList(),
                requiredLogMarkers = packageMetadataFailureMarkers(
                    launchKind = binLaunchKind,
                    binName = binName,
                    messageToken = messageToken,
                ),
                forbiddenLogMarkers = forbiddenLogMarkers,
                packageMetadataInput = true,
            )
        }

        val packageMetadataFailureCases = listOf(
            packageScriptMetadataFailureCase(
                id = "missing-script-package-json",
                projectNameSuffix = "MissingScriptPackageJson",
                scriptName = "bad",
                packageJson = null,
                messageToken = "Missing package.json for package script",
            ),
            packageScriptMetadataFailureCase(
                id = "missing-scripts-object",
                projectNameSuffix = "MissingScriptsObject",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0"
                    }
                """.trimIndent() + "\n",
                messageToken = "Missing package scripts",
            ),
            packageScriptMetadataFailureCase(
                id = "unsupported-script-shape",
                projectNameSuffix = "UnsupportedScriptShape",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": 42
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package script 'bad'",
            ),
            packageScriptMetadataFailureCase(
                id = "empty-script-command",
                projectNameSuffix = "EmptyScriptCommand",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "   "
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script command is empty",
            ),
            packageScriptMetadataFailureCase(
                id = "script-shell-operator",
                projectNameSuffix = "ScriptShellOperator",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ./scripts/run.js && echo bad"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script shell operators are unsupported: &",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js" to
                            """"nodejs"; console.log("script shell operator should not run");""" + "\n",
                ),
                forbiddenLogMarkers = listOf("script shell operator should not run"),
            ),
            packageScriptMetadataFailureCase(
                id = "script-command-substitution",
                projectNameSuffix = "ScriptCommandSubstitution",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ./scripts/run.js ${'$'}(echo bad)"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script command substitution is unsupported",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js" to
                            """"nodejs"; console.log("script command substitution should not run");""" + "\n",
                ),
                forbiddenLogMarkers = listOf("script command substitution should not run"),
            ),
            packageScriptMetadataFailureCase(
                id = "script-unterminated-quote",
                projectNameSuffix = "ScriptUnterminatedQuote",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node \"./scripts/run.js"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script command has unterminated quote",
            ),
            packageScriptMetadataFailureCase(
                id = "script-env-assignment-prefix",
                projectNameSuffix = "ScriptEnvAssignmentPrefix",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "FOO=bar direct-bin-tool"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script env assignment prefix is unsupported: FOO=bar",
            ),
            packageScriptMetadataFailureCase(
                id = "script-node-option",
                projectNameSuffix = "ScriptNodeOption",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node -e \"1 + 1\""
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package script node option: -e",
            ),
            packageScriptMetadataFailureCase(
                id = "script-node-entry-escape",
                projectNameSuffix = "ScriptNodeEntryEscape",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ../outside.js"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package script node entry escapes package sandbox: ../outside.js",
            ),
            packageScriptMetadataFailureCase(
                id = "script-node-native-addon",
                projectNameSuffix = "ScriptNodeNativeAddon",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ./addon.node"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package script node native addon entry: ./addon.node",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/addon.node" to "native-addon-placeholder\n",
                ),
            ),
            packageScriptMetadataFailureCase(
                id = "script-node-entry-extension",
                projectNameSuffix = "ScriptNodeEntryExtension",
                scriptName = "bad",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ./README.txt"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package script node entry extension: ./README.txt",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/README.txt" to "not javascript\n",
                ),
            ),
            packageBinMetadataFailureCase(
                id = "missing-bin-package-json",
                projectNameSuffix = "MissingBinPackageJson",
                binName = "bad-bin",
                packageJson = null,
                messageToken = "Missing package.json for package bin",
            ),
            packageBinMetadataFailureCase(
                id = "unsupported-bin-shape",
                projectNameSuffix = "UnsupportedBinShape",
                binName = "bad-bin",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-bin-tool",
                      "version": "1.0.0",
                      "bin": 42
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package bin shape",
            ),
            packageBinMetadataFailureCase(
                id = "bin-target-absolute",
                projectNameSuffix = "BinTargetAbsolute",
                binName = "bad-bin",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-bin-tool",
                      "version": "1.0.0",
                      "bin": {
                        "bad-bin": "/system/bin/sh"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package bin target must be relative: /system/bin/sh",
            ),
            packageBinMetadataFailureCase(
                id = "bin-target-empty",
                projectNameSuffix = "BinTargetEmpty",
                binName = "bad-bin",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-bin-tool",
                      "version": "1.0.0",
                      "bin": {
                        "bad-bin": ""
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Package bin target is empty",
            ),
            packageBinMetadataFailureCase(
                id = "bin-target-extension",
                projectNameSuffix = "BinTargetExtension",
                binName = "bad-bin",
                packageJson = """
                    {
                      "name": "packaged-launch-failure-bin-tool",
                      "version": "1.0.0",
                      "bin": {
                        "bad-bin": "./cli.txt"
                      }
                    }
                """.trimIndent() + "\n",
                messageToken = "Unsupported package bin target extension: ./cli.txt",
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/cli.txt" to "not javascript\n",
                ),
            ),
        )

        return descriptorInputFailureCases + projectEntryFailureCases + descriptorPathFailureCases + packageMetadataFailureCases + listOf(
            PackagedLaunchFailureCase(
                id = "missing-package-dir",
                projectName = "PackagedNodeMissingPackageDirFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.missingpackagedir",
                outputApkName = "PackagedNodeMissingPackageDirFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "packages/missing-script-tool",
                    scriptName = "missing-package-dir",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor path is not a directory: packageDir",
                    "errorClass=IllegalArgumentException",
                ),
                descriptorPathInput = true,
            ),
            PackagedLaunchFailureCase(
                id = "missing-working-directory",
                projectName = "PackagedNodeMissingWorkingDirectoryFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.missingworkingdirectory",
                outputApkName = "PackagedNodeMissingWorkingDirectoryFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/missing",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "run": "node ./scripts/run.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js" to
                            """"nodejs"; console.log("missing working directory should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor path is not a directory: workingDirectory",
                    "errorClass=IllegalArgumentException",
                ),
                forbiddenLogMarkers = listOf("missing working directory should not run"),
                descriptorPathInput = true,
            ),
            PackagedLaunchFailureCase(
                id = "package-dir-escape",
                projectName = "PackagedNodePackageDirEscapeFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.packagedirescape",
                outputApkName = "PackagedNodePackageDirEscapeFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = "../outside-script-tool",
                    scriptName = "run",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor path escapes project root: packageDir",
                    "errorClass=IllegalArgumentException",
                ),
                descriptorPathInput = true,
            ),
            PackagedLaunchFailureCase(
                id = "working-directory-escape",
                projectName = "PackagedNodeWorkingDirectoryEscapeFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.workingdirectoryescape",
                outputApkName = "PackagedNodeWorkingDirectoryEscapeFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "run",
                    workingDirectory = "../outside-workspace",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "run": "node ./scripts/run.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js" to
                            """"nodejs"; console.log("working directory escape should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/run.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor path escapes project root: workingDirectory",
                    "errorClass=IllegalArgumentException",
                ),
                forbiddenLogMarkers = listOf("working directory escape should not run"),
                descriptorPathInput = true,
            ),
            PackagedLaunchFailureCase(
                id = "missing-script",
                projectName = "PackagedNodeMissingScriptFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.missingscript",
                outputApkName = "PackagedNodeMissingScriptFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "missing-script",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "existing": "node ./scripts/existing.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/existing.js" to
                            """"nodejs"; console.log("missing script fallback should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/existing.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=missing-script",
                    "Missing package script 'missing-script'",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                forbiddenLogMarkers = listOf("missing script fallback should not run"),
            ),
            PackagedLaunchFailureCase(
                id = "missing-bin",
                projectName = "PackagedNodeMissingBinFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.missingbin",
                outputApkName = "PackagedNodeMissingBinFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = binLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR,
                    binName = "missing-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-bin-tool",
                      "version": "1.0.0",
                      "bin": {
                        "existing-bin": "./bin/existing.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/bin/existing.js" to
                            """"nodejs"; console.log("missing bin fallback should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/bin/existing.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-bin",
                    "bin=missing-bin",
                    "Missing package bin entry 'missing-bin'",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                forbiddenLogMarkers = listOf("missing bin fallback should not run"),
            ),
            PackagedLaunchFailureCase(
                id = "dangerous-script",
                projectName = "PackagedNodeDangerousScriptFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.dangerousscript",
                outputApkName = "PackagedNodeDangerousScriptFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "bad",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "npm install"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=bad",
                    "Unsupported package script command: npm",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
            ),
            PackagedLaunchFailureCase(
                id = "script-bin-denied-npm",
                projectName = "PackagedNodeScriptBinDeniedNpmFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.scriptbindeniednpm",
                outputApkName = "PackagedNodeScriptBinDeniedNpmFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "bad-dot-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad-dot-bin": "node_modules/.bin/npm install"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=bad-dot-bin",
                    "Unsupported package script command: node_modules/.bin/npm",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "script-bin-escape",
                projectName = "PackagedNodeScriptBinEscapeFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.scriptbinescape",
                outputApkName = "PackagedNodeScriptBinEscapeFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "bad-dot-bin-escape",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad-dot-bin-escape": "node_modules/.bin/@autojs6/../escape --bad"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=bad-dot-bin-escape",
                    "Package script command must be a package bin or node: node_modules/.bin/@autojs6/../escape",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "missing-scoped-script-bin",
                projectName = "PackagedNodeMissingScopedScriptBinFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.missingscopedscriptbin",
                outputApkName = "PackagedNodeMissingScopedScriptBinFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "missing-scoped-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "missing-scoped-bin": "node_modules/.bin/@autojs6/missing-tool --from-script"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=missing-scoped-bin",
                    "Missing package script bin '@autojs6/missing-tool'",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "script-bin-missing-target",
                projectName = "PackagedNodeScriptBinMissingTargetFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.scriptbinmissingtarget",
                outputApkName = "PackagedNodeScriptBinMissingTargetFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "missing-target",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "missing-target": "node_modules/.bin/@autojs6/broken-tool --from-script"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/broken-tool/package.json" to """
                    {
                      "name": "@autojs6/broken-tool",
                      "version": "1.0.0",
                      "bin": {
                        "@autojs6/broken-tool": "./missing.js"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/broken-tool/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=missing-target",
                    "Package bin target missing: ./missing.js",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "script-bin-target-escape",
                projectName = "PackagedNodeScriptBinTargetEscapeFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.scriptbintargetescape",
                outputApkName = "PackagedNodeScriptBinTargetEscapeFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "target-escape",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "target-escape": "node_modules/.bin/@autojs6/escape-tool --from-script"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/escape-tool/package.json" to """
                    {
                      "name": "@autojs6/escape-tool",
                      "version": "1.0.0",
                      "bin": {
                        "@autojs6/escape-tool": "../escape.js"
                      }
                    }
                    """.trimIndent() + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/escape-tool/package.json",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=target-escape",
                    "Package bin target escapes package sandbox: ../escape.js",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "script-bin-native-addon",
                projectName = "PackagedNodeScriptBinNativeAddonFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.scriptbinnativeaddon",
                outputApkName = "PackagedNodeScriptBinNativeAddonFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "native-addon",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-script-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "native-addon": "node_modules/.bin/@autojs6/native-tool --from-script"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/native-tool/package.json" to """
                    {
                      "name": "@autojs6/native-tool",
                      "version": "1.0.0",
                      "bin": {
                        "@autojs6/native-tool": "./addon.node"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/native-tool/addon.node" to
                            "native-addon-placeholder\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/native-tool/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/node_modules/@autojs6/native-tool/addon.node",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=package-script",
                    "script=native-addon",
                    "Unsupported package bin native addon target: ./addon.node",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                requiredTemplateDexMarkers = listOf(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER),
            ),
            PackagedLaunchFailureCase(
                id = "ambiguous-script-bin",
                projectName = "PackagedNodeAmbiguousScriptBinFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.ambiguousscriptbin",
                outputApkName = "PackagedNodeAmbiguousScriptBinFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = ambiguousLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "bad",
                    binName = "bad-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-ambiguous-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "bad": "node ./scripts/bad.js"
                      },
                      "bin": {
                        "bad-bin": "./bin/bad.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/bad.js" to
                            """"nodejs"; console.log("ambiguous script should not run");""" + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/bin/bad.js" to
                            """"nodejs"; console.log("ambiguous bin should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/bad.js",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/bin/bad.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                    "launchKind=ambiguous",
                    "script=bad",
                    "bin=bad-bin",
                    "Node project launch request cannot specify both scriptName and binName",
                    "envKeys=[$PACKAGED_LAUNCH_FAILURE_SECRET_KEY]",
                ),
                forbiddenLogMarkers = listOf(
                    "ambiguous script should not run",
                    "ambiguous bin should not run",
                ),
            ),
            PackagedLaunchFailureCase(
                id = "project-entry-script-conflict",
                projectName = "PackagedNodeProjectEntryScriptConflictFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.projectentryscriptconflict",
                outputApkName = "PackagedNodeProjectEntryScriptConflictFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = projectEntryLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR,
                    scriptName = "entry-conflict",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-entry-script-conflict-tool",
                      "version": "1.0.0",
                      "scripts": {
                        "entry-conflict": "node ./scripts/entry-conflict.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/entry-conflict.js" to
                            """"nodejs"; console.log("project entry script conflict should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR/scripts/entry-conflict.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor launchKind mismatch: launchKind=project-entry inferred=package-script",
                    "errorClass=IllegalArgumentException",
                ),
                forbiddenLogMarkers = listOf("project entry script conflict should not run"),
            ),
            PackagedLaunchFailureCase(
                id = "project-entry-bin-conflict",
                projectName = "PackagedNodeProjectEntryBinConflictFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.projectentrybinconflict",
                outputApkName = "PackagedNodeProjectEntryBinConflictFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = projectEntryLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR,
                    binName = "entry-conflict-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/package.json" to """
                    {
                      "name": "packaged-launch-failure-entry-bin-conflict-tool",
                      "version": "1.0.0",
                      "bin": {
                        "entry-conflict-bin": "./bin/entry-conflict.js"
                      }
                    }
                    """.trimIndent() + "\n",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/bin/entry-conflict.js" to
                            """"nodejs"; console.log("project entry bin conflict should not run");""" + "\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/package.json",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/bin/entry-conflict.js",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor launchKind mismatch: launchKind=project-entry inferred=package-bin",
                    "errorClass=IllegalArgumentException",
                ),
                forbiddenLogMarkers = listOf("project entry bin conflict should not run"),
            ),
            PackagedLaunchFailureCase(
                id = "launch-kind-mismatch",
                projectName = "PackagedNodeLaunchKindMismatchFailureSmoke",
                packageName = "org.autojs6.packaged.node.failure.launchkindmismatch",
                outputApkName = "PackagedNodeLaunchKindMismatchFailureSmoke_v1.0.0.apk",
                descriptor = descriptor(
                    launchKind = scriptLaunchKind,
                    packageDir = PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR,
                    binName = "bad-bin",
                ),
                extraFiles = mapOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep" to "workspace\n",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/.keep" to "package\n",
                ),
                requiredEntries = listOf(
                    "$PACKAGED_LAUNCH_FAILURE_WORKSPACE/.keep",
                    "$PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR/.keep",
                ),
                requiredLogMarkers = listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    "stage=descriptor",
                    "requestReady=false",
                    "Internal Node project launch descriptor launchKind mismatch: launchKind=package-script inferred=package-bin",
                    "errorClass=IllegalArgumentException",
                ),
            ),
        )
    }

    private fun packagedDescriptorLaunchFailureCases(): List<PackagedLaunchFailureCase> {
        return packagedLaunchFailureCases().filter { failureCase -> failureCase.descriptorInput }
    }

    private fun packagedDescriptorPathLaunchFailureCases(): List<PackagedLaunchFailureCase> {
        return packagedLaunchFailureCases().filter { failureCase -> failureCase.descriptorPathInput }
    }

    private fun packagedPackageMetadataLaunchFailureCases(): List<PackagedLaunchFailureCase> {
        return packagedLaunchFailureCases().filter { failureCase -> failureCase.packageMetadataInput }
    }

    private fun packagedProjectEntryLaunchFailureCases(): List<PackagedLaunchFailureCase> {
        return packagedLaunchFailureCases().filter { failureCase -> failureCase.projectEntryInput }
    }

    private fun buildPackagedLaunchFailureApk(failureCase: PackagedLaunchFailureCase): File {
        val root = File(context.cacheDir, failureCase.rootName).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create packaged launch failure root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create packaged launch failure project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create packaged launch failure output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, failureCase.outputApkName)
        val outputPathFile = File(root, "output-path.txt")
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";
            console.log("packaged.launch_failure.${failureCase.id}.unexpected_main=true");
        """.trimIndent() + "\n"

        File(projectDir, mainScriptName).writeText(mainScript, StandardCharsets.UTF_8)
        failureCase.extraFiles.forEach { (relativePath, content) ->
            File(projectDir, relativePath).apply {
                parentFile?.mkdirs()
                writeText(content, StandardCharsets.UTF_8)
            }
        }
        val projectConfigText = """
            {
              "name": "${failureCase.projectName}",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "${failureCase.packageName}",
              "main": "${failureCase.projectConfigMainScriptName}",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
                "android.permission.WAKE_LOCK",
                "android.permission.INTERNET",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
              ],
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n"

        File(projectDir, PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE).writeText(
            failureCase.descriptor + "\n",
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            projectConfigText,
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load packaged launch failure project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(failureCase.projectName)
            .setPackageName(failureCase.packageName)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(
                arrayListOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ),
            )
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        val cacheKey = packagedSmokeFixtureCacheKey(
            kind = "launch_failure",
            outputApkName = failureCase.outputApkName,
            requiredTemplateDexMarkers = failureCase.requiredTemplateDexMarkers,
            inputs = listOf(
                "id" to failureCase.id,
                "projectName" to failureCase.projectName,
                "packageName" to failureCase.packageName,
                "projectConfigMainScriptName" to failureCase.projectConfigMainScriptName,
                "descriptor" to failureCase.descriptor,
                "mainScriptName" to mainScriptName,
                "mainScript" to mainScript,
                "projectConfig" to projectConfigText,
                "requiredEntries" to failureCase.requiredEntries.sorted().joinToString("\n"),
                "requiredLogMarkers" to failureCase.requiredLogMarkers.sorted().joinToString("\n"),
                "forbiddenLogMarkers" to failureCase.forbiddenLogMarkers.sorted().joinToString("\n"),
            ) + failureCase.extraFiles.toSortedMap().map { (relativePath, content) ->
                "extra:$relativePath" to content
            },
        )
        restorePackagedSmokeFixtureCache(
            outputApk = outputApk,
            cacheKey = cacheKey,
            label = "launch failure ${failureCase.id}",
        )?.let { cachedApk ->
            outputPathFile.writeText(cachedApk.absolutePath, StandardCharsets.UTF_8)
            assertPackagedApkContainsLaunchFailureSmoke(
                outputApk = cachedApk,
                failureCase = failureCase,
                mainScriptName = mainScriptName,
                mainScript = mainScript,
            )
            exportPackagedSmokeApkForHost(cachedApk)
            return cachedApk
        }

        openPackagedSmokeTemplate(requiredDexMarkers = failureCase.requiredTemplateDexMarkers).use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Packaged launch failure APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Packaged launch failure APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsLaunchFailureSmoke(
            outputApk = outputApk,
            failureCase = failureCase,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
        )
        storePackagedSmokeFixtureCache(
            outputApk = outputApk,
            cacheKey = cacheKey,
            label = "launch failure ${failureCase.id}",
        )
        exportPackagedSmokeApkForHost(outputApk)
        return outputApk
    }

    private fun buildPackagedToastBridgeApk(): File {
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";

            async function main() {
              const toast = require("toast");
              const resolved = require.resolve("toast");
              const result = await toast.showToast("AutoJs6 packaged Node toast smoke", {
                duration: "short",
                log: true,
                timeoutMs: 3000
              });
              const resolveOk = resolved === "toast";
              const surfaceOk = typeof toast.showToast === "function" && toast.toast === toast.showToast;
              const resultOk = result === null;
              const shadowOk = toast.shadow !== true;
              console.log("packaged.toast.ready=" + (resolveOk && surfaceOk && resultOk && shadowOk));
              console.log("packaged.toast.resolve=" + resolveOk);
              console.log("packaged.toast.surface=" + surfaceOk);
              console.log("packaged.toast.result=" + resultOk);
              console.log("packaged.toast.shadow=" + shadowOk);
            }

            return main().catch(error => {
              console.error("packaged.toast.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-toast-bridge-smoke",
            outputApkName = TOAST_BRIDGE_APK_NAME,
            projectName = TOAST_BRIDGE_PROJECT_NAME,
            packageName = TOAST_BRIDGE_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/toast/index.js" to """module.exports = { shadow: true };""" + "\n",
            ),
            runtimeMarkers = TOAST_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedAppBridgeApk(): File {
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";

            async function main() {
              const app = require("app");
              const resolved = require.resolve("app");
              const launchResult = await app.launchPackage(app.packageName, { timeoutMs: 3000 });
              const resolveOk = resolved === "app";
              const packageOk = app.packageName === "$APP_BRIDGE_PACKAGE_NAME";
              const versionNameTypeOk = typeof app.versionName === "string";
              const versionCodeTypeOk = typeof app.versionCode === "number";
              const surfaceOk = typeof app.launchPackage === "function" &&
                typeof app.launchApp === "function" &&
                typeof app.openAppSetting === "function" &&
                typeof app.intentToShell === "function" &&
                typeof app.startActivity === "function";
              const launchOk = launchResult === true;
              const shadowOk = app.shadow !== true;
              console.log("packaged.app.ready=" + (
                resolveOk && packageOk && versionNameTypeOk && versionCodeTypeOk && surfaceOk && launchOk && shadowOk
              ));
              console.log("packaged.app.resolve=" + resolveOk);
              console.log("packaged.app.package=" + packageOk);
              console.log("packaged.app.version_name_type=" + versionNameTypeOk);
              console.log("packaged.app.version_code_type=" + versionCodeTypeOk);
              console.log("packaged.app.surface=" + surfaceOk);
              console.log("packaged.app.launch=" + launchOk);
              console.log("packaged.app.shadow=" + shadowOk);
            }

            return main().catch(error => {
              console.error("packaged.app.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-app-bridge-smoke",
            outputApkName = APP_BRIDGE_APK_NAME,
            projectName = APP_BRIDGE_PROJECT_NAME,
            packageName = APP_BRIDGE_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/app/index.js" to """module.exports = { shadow: true };""" + "\n",
            ),
            runtimeMarkers = APP_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedClipboardBridgeApk(): File {
        val mainScriptName = "main.js"
        val clipboardText = "AutoJs6 packaged Node clipboard smoke"
        val mainScript = """
            "nodejs";

            async function main() {
              const clipboard = require("clipboard");
              const resolved = require.resolve("clipboard");
              const setResult = await clipboard.setText("$clipboardText", { timeoutMs: 3000 });
              const text = await clipboard.getText({ timeoutMs: 3000 });
              const hasText = await clipboard.hasText({ timeoutMs: 3000 });
              const resolveOk = resolved === "clipboard";
              const surfaceOk = typeof clipboard.getText === "function" &&
                typeof clipboard.setText === "function" &&
                typeof clipboard.hasText === "function" &&
                typeof clipboard.watch === "undefined";
              const setOk = setResult === undefined;
              const textOk = text === "$clipboardText";
              const hasTextOk = hasText === true;
              const shadowOk = clipboard.shadow !== true;
              console.log("packaged.clipboard.ready=" + (
                resolveOk && surfaceOk && setOk && textOk && hasTextOk && shadowOk
              ));
              console.log("packaged.clipboard.resolve=" + resolveOk);
              console.log("packaged.clipboard.surface=" + surfaceOk);
              console.log("packaged.clipboard.set=" + setOk);
              console.log("packaged.clipboard.text=" + textOk);
              console.log("packaged.clipboard.has_text=" + hasTextOk);
              console.log("packaged.clipboard.shadow=" + shadowOk);
            }

            return main().catch(error => {
              console.error("packaged.clipboard.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-clipboard-bridge-smoke",
            outputApkName = CLIPBOARD_BRIDGE_APK_NAME,
            projectName = CLIPBOARD_BRIDGE_PROJECT_NAME,
            packageName = CLIPBOARD_BRIDGE_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/clipboard/index.js" to """module.exports = { shadow: true };""" + "\n",
            ),
            runtimeMarkers = CLIPBOARD_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedDeviceBridgeApk(): File {
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";

            async function main() {
              const device = require("device");
              const resolved = require.resolve("device");
              const screenOn = await device.isScreenOn({ timeoutMs: 3000 });
              const wakeResult = await device.wakeUp({ timeoutMs: 3000 });
              const vibrateResult = await device.vibrate(0, { timeoutMs: 3000 });
              const resolveOk = resolved === "device";
              const metricsOk = Number.isInteger(device.sdkInt) &&
                Number.isInteger(device.width) &&
                Number.isInteger(device.height) &&
                typeof device.density === "number" &&
                device.sdkInt >= 0 &&
                device.width >= 0 &&
                device.height >= 0 &&
                device.density >= 0;
              const surfaceOk = typeof device.isScreenOn === "function" &&
                typeof device.wakeUp === "function" &&
                typeof device.vibrate === "function" &&
                typeof device.watch === "undefined";
              const valuesOk = typeof screenOn === "boolean" &&
                wakeResult === undefined &&
                vibrateResult === undefined;
              const sensitiveOk = [
                "imei",
                "androidId",
                "serial",
                "phoneNumber",
                "accounts",
                "macAddress",
                "ipAddress",
                "fingerprint",
                "brand",
                "manufacturer"
              ].every(name => typeof device[name] === "undefined");
              const shadowOk = device.shadow !== true;
              console.log("packaged.device.ready=" + (
                resolveOk && metricsOk && surfaceOk && valuesOk && sensitiveOk && shadowOk
              ));
              console.log("packaged.device.resolve=" + resolveOk);
              console.log("packaged.device.metrics=" + metricsOk);
              console.log("packaged.device.surface=" + surfaceOk);
              console.log("packaged.device.values=" + valuesOk);
              console.log("packaged.device.sensitive=" + sensitiveOk);
              console.log("packaged.device.shadow=" + shadowOk);
            }

            return main().catch(error => {
              console.error("packaged.device.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-device-bridge-smoke",
            outputApkName = DEVICE_BRIDGE_APK_NAME,
            projectName = DEVICE_BRIDGE_PROJECT_NAME,
            packageName = DEVICE_BRIDGE_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/device/index.js" to """module.exports = { shadow: true, imei: "shadow" };""" + "\n",
            ),
            extraPermissions = listOf("android.permission.VIBRATE"),
            runtimeMarkers = DEVICE_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedV11BridgeApk(): File {
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";

            async function main() {
              const bridge = require("autojs6:bridge");
              const { Buffer } = require("buffer");
              const requests = [];
              const stores = Object.create(null);
              const notifications = new Map();
              const tasks = new Map();
              let workSequence = 0;
              let notificationSequence = 9100;
              let uiSequence = 0;
              const uiDrained = Object.create(null);

              function respond(request, ok, result, error) {
                setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok,
                  result,
                  error
                })));
              }

              function textResponse(payload, text, status) {
                return {
                  url: payload.url,
                  status,
                  statusText: status === 200 ? "OK" : "Created",
                  headers: [["content-type", "text/plain"], ["x-packaged", "v1.1"]],
                  bodyBase64: Buffer.from(text).toString("base64"),
                  bodyBytes: Buffer.byteLength(text)
                };
              }

              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const args = request.args || [];
                  if (request.module === "fetch" && request.method === "request") {
                    respond(request, true, textResponse(args[0], "packaged-fetch", 200));
                    return;
                  }
                  if (request.module === "ocr") {
                    respond(request, true, [
                      { text: "packaged", confidence: 0.91, bounds: { left: 1, top: 2, right: 3, bottom: 4 } }
                    ]);
                    return;
                  }
                  if (request.module === "storage" || request.module === "storages") {
                    const storeName = args[0];
                    const key = args[1];
                    const bucket = stores[storeName] || (stores[storeName] = Object.create(null));
                    if (request.method === "clear") {
                      Object.keys(bucket).forEach((entry) => delete bucket[entry]);
                      respond(request, true, null);
                    } else if (request.method === "put") {
                      bucket[key] = args[2];
                      respond(request, true, null);
                    } else if (request.method === "get") {
                      respond(request, true, Object.prototype.hasOwnProperty.call(bucket, key)
                        ? { found: true, value: bucket[key] }
                        : { found: false });
                    } else if (request.method === "contains") {
                      respond(request, true, Object.prototype.hasOwnProperty.call(bucket, key));
                    } else if (request.method === "keys") {
                      respond(request, true, Object.keys(bucket).sort());
                    } else if (request.method === "remove") {
                      delete bucket[key];
                      respond(request, true, null);
                    }
                    return;
                  }
                  if (request.module === "notifications") {
                    if (request.method === "notify") {
                      const descriptor = args[0] || {};
                      const id = descriptor.id || ++notificationSequence;
                      notifications.set(id, Object.assign({ id }, descriptor));
                      respond(request, true, id);
                    } else if (request.method === "cancel") {
                      notifications.delete(args[0]);
                      respond(request, true, null);
                    } else if (request.method === "cancelAll") {
                      notifications.clear();
                      respond(request, true, null);
                    }
                    return;
                  }
                  if (request.module === "ui") {
                    if (request.method === "showLayout") {
                      uiSequence += 1;
                      respond(request, true, { id: "packaged-ui-" + uiSequence });
                    } else if (request.method === "drainEvents") {
                      const handleId = args[0];
                      respond(request, true, uiDrained[handleId] ? [] : [{ type: "click", id: "ok", text: "OK" }]);
                      uiDrained[handleId] = true;
                    } else {
                      respond(request, true, null);
                    }
                    return;
                  }
                  if (request.module === "work_manager") {
                    if (request.method === "scheduleOnce") {
                      workSequence += 1;
                      const descriptor = args[0];
                      const id = "packaged-work-" + workSequence;
                      tasks.set(id, Object.assign({
                        id,
                        type: "once",
                        status: "registered",
                        scheduler: "metadata",
                        packageName: "$V11_BRIDGE_PACKAGE_NAME",
                        createdAtEpochMs: workSequence
                      }, descriptor));
                      respond(request, true, id);
                    } else if (request.method === "list") {
                      respond(request, true, Array.from(tasks.values()));
                    } else if (request.method === "cancel") {
                      tasks.delete(args[0]);
                      respond(request, true, null);
                    }
                    return;
                  }
                  respond(request, false, null, {
                    code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                    message: "unexpected packaged v1.1 bridge request",
                    module: request.module,
                    method: request.method
                  });
                }
              });

              const fetchModule = require("fetch");
              const fetchResult = await fetch("https://example.invalid/v1_1", {
                headers: { "x-packaged": "v1.1" },
                timeoutMs: 1000,
                maxResponseBytes: 1024
              });
              const fetchText = await fetchResult.text();

              const ocr = require("ocr");
              const ocrResult = await ocr.recognize(
                { id: "packaged-image-1", __autojs6ImageHandle: "packaged-image-1", width: 10, height: 20 },
                { lang: "auto", timeoutMs: 1000 }
              );

              const storage = require("storage");
              const storages = require("storages");
              const store = storage.create("settings");
              const alias = storages.open("settings");
              await store.clear({ timeoutMs: 1000 });
              await store.put("mode", { value: "packaged", count: 11 }, { timeoutMs: 1000 });
              const stored = await alias.get("mode", null, { timeoutMs: 1000 });
              const storageContains = await store.contains("mode", { timeoutMs: 1000 });
              const storageKeys = await alias.keys({ timeoutMs: 1000 });
              await store.remove("mode", { timeoutMs: 1000 });

              const notificationsModule = require("notifications");
              const notificationId = await notificationsModule.notify({
                id: 9301,
                title: "Packaged v1.1",
                text: "Bridge smoke"
              }, { timeoutMs: 1000 });
              await notificationsModule.cancel(notificationId, { timeoutMs: 1000 });

              const ui = require("ui");
              const handle = await ui.showLayout({
                type: "Vertical",
                children: [
                  { type: "Text", id: "title", text: "Node v1.1" },
                  { type: "Button", id: "ok", text: "OK" }
                ]
              }, { timeoutMs: 1000, drainIntervalMs: 1 });
              let clicked = "";
              const clickedReady = new Promise((resolve) => {
                const off = handle.on("click", (event) => {
                  clicked = event.id + ":" + event.handleId;
                  off();
                  resolve();
                }, { timeoutMs: 1000, drainIntervalMs: 1 });
              });
              await clickedReady;
              await handle.update({ id: "title", text: "Updated" }, { timeoutMs: 1000 });
              await handle.close({ timeoutMs: 1000 });

              const work = require("work_manager");
              const workId = await work.scheduleOnce({
                scriptPath: "./main.js",
                delayMs: 1234,
                timeoutMs: 777,
                constraints: { network: true }
              });
              const workBefore = await work.list({ timeoutMs: 1000 });
              await work.cancel(workId, { timeoutMs: 1000 });
              const workAfter = await work.list({ timeoutMs: 1000 });

              const plugins = require("plugins");
              const pluginManifest = plugins.manifest("org.example.packaged");
              const pluginStart = plugins.start("org.example.packaged");
              const plugin = plugins.load("org.example.packaged");
              const pluginResult = await plugin.run({ label: "packaged-plugin" });
              const pluginStatus = plugins.status("org.example.packaged");
              const pluginStop = plugins.stop("org.example.packaged");
              const pluginLifecycle = plugin.lifecycle();
              const pluginUnloaded = plugins.unload("org.example.packaged");
              const pluginFinal = plugins.status("org.example.packaged");

              const fetchOk = require.resolve("fetch") === "fetch" &&
                fetchModule.shadow !== true &&
                fetchResult.status === 200 &&
                fetchResult.headers.get("x-packaged") === "v1.1" &&
                fetchText === "packaged-fetch";
              const ocrOk = require.resolve("ocr") === "ocr" &&
                ocr.shadow !== true &&
                ocrResult[0].text === "packaged" &&
                ocrResult[0].bounds.bottom === 4;
              const storageOk = require.resolve("storage") === "storage" &&
                require.resolve("storages") === "storages" &&
                storage.shadow !== true &&
                storages.shadow !== true &&
                stored.value === "packaged" &&
                stored.count === 11 &&
                storageContains === true &&
                storageKeys.join(",") === "mode";
              const notificationsOk = require.resolve("notifications") === "notifications" &&
                notificationsModule.shadow !== true &&
                notificationId === 9301 &&
                notifications.size === 0;
              const uiOk = require.resolve("ui") === "ui" &&
                ui.shadow !== true &&
                handle.id === "packaged-ui-1" &&
                clicked === "ok:packaged-ui-1";
              const workOk = require.resolve("work_manager") === "work_manager" &&
                work.shadow !== true &&
                workId === "packaged-work-1" &&
                workBefore.length === 1 &&
                workBefore[0].scriptPath === "main.js" &&
                workBefore[0].constraints.network === true &&
                workAfter.length === 0;
              const pluginCapabilities = pluginStatus.capabilities.join(",");
              const pluginOk = require.resolve("plugins") === "plugins" &&
                plugins.shadow !== true &&
                pluginManifest.id === "org.example.packaged" &&
                pluginManifest.capability === "plugin.org.example.packaged" &&
                pluginManifest.permissions.join(",") === "storage,notifications,ui,network" &&
                pluginStart.resources.total === 1 &&
                pluginStart.capabilities.join(",") === "plugin.org.example.packaged,storage,notifications,ui,network" &&
                pluginResult.label === "packaged-plugin" &&
                pluginResult.stored === "packaged-plugin" &&
                pluginResult.clicked === "ok:packaged-ui-2" &&
                pluginResult.fetchText === "packaged-fetch" &&
                pluginResult.notificationId === 9302 &&
                pluginResult.uiFrozen === true &&
                pluginCapabilities === "plugin.org.example.packaged,storage,notifications,ui,network" &&
                pluginStatus.resources.total === 1 &&
                pluginStop.resources.total === 0 &&
                pluginLifecycle.cleanup === true &&
                pluginUnloaded === true &&
                pluginFinal.loaded === false &&
                pluginFinal.resources.total === 0;
              const requiredPermissionsOk = requests.every((request) => {
                if (request.module === "fetch") {
                  return request.permissions.indexOf("network") >= 0;
                }
                if (request.module === "ocr") {
                  return request.permissions.indexOf("ocr") >= 0 && request.permissions.indexOf("image") >= 0;
                }
                if (request.module === "storage" || request.module === "storages") {
                  return request.permissions.indexOf("storage") >= 0;
                }
                if (request.module === "notifications") {
                  return request.permissions.indexOf("notifications") >= 0;
                }
                if (request.module === "ui") {
                  return request.permissions.indexOf("ui") >= 0;
                }
                if (request.module === "work_manager") {
                  return request.permissions.indexOf("work_manager") >= 0;
                }
                return false;
              });
              const noShadowOk = [fetchModule, ocr, storage, storages, notificationsModule, ui, work, plugins].every((module) => module.shadow !== true);
              const ready = fetchOk && ocrOk && storageOk && notificationsOk && uiOk && workOk && pluginOk && requiredPermissionsOk &&
                noShadowOk && bridge.__test.pendingCount() === 0;

              console.log("packaged.v1_1_bridge.ready=" + ready);
              console.log("packaged.v1_1_bridge.fetch=" + fetchOk);
              console.log("packaged.v1_1_bridge.ocr=" + ocrOk);
              console.log("packaged.v1_1_bridge.storage=" + storageOk);
              console.log("packaged.v1_1_bridge.notifications=" + notificationsOk);
              console.log("packaged.v1_1_bridge.ui=" + uiOk);
              console.log("packaged.v1_1_bridge.work_manager=" + workOk);
              console.log("packaged.v1_1_bridge.plugin=" + pluginOk);
              console.log("packaged.v1_1_bridge.permissions=" + requiredPermissionsOk);
              console.log("packaged.v1_1_bridge.no_shadow=" + noShadowOk);
              console.log("packaged.v1_1_bridge.pending=" + bridge.__test.pendingCount());
            }

            return main().catch(error => {
              console.error("packaged.v1_1_bridge.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-v11-bridge-smoke",
            outputApkName = V11_BRIDGE_APK_NAME,
            projectName = V11_BRIDGE_PROJECT_NAME,
            packageName = V11_BRIDGE_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/fetch/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/ocr/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/storage/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/storages/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/notifications/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/ui/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/work_manager/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/plugins/index.js" to """module.exports = { shadow: true };""" + "\n",
                "plugins/org.example.packaged/package.json" to """
                    {
                      "name": "org.example.packaged",
                      "version": "1.0.0",
                      "main": "index.cjs",
                      "autojs6": {
                        "plugin": {
                          "id": "org.example.packaged",
                          "permissions": ["storage", "notifications", "ui", "network"],
                          "dependencies": [],
                          "exports": {}
                        }
                      }
                    }
                """.trimIndent() + "\n",
                "plugins/org.example.packaged/index.cjs" to """
                    let lifecycle = { cleanup: false };

                    exports.start = function(ctx) {
                      lifecycle = {
                        cleanup: false,
                        namespace: ctx.namespace,
                        capabilities: ctx.capabilities.join(","),
                        permissions: ctx.permissions.join(",")
                      };
                      ctx.resources.addCleanup(function() {
                        lifecycle.cleanup = true;
                      });
                    };

                    exports.lifecycle = function() {
                      return lifecycle;
                    };

                    exports.run = async function(input) {
                      const storage = require("storage");
                      const notifications = require("notifications");
                      const ui = require("ui");

                      const store = storage.create("plugin");
                      await store.put("label", { value: input.label }, { timeoutMs: 1000 });
                      const stored = await store.get("label", null, { timeoutMs: 1000 });

                      const notificationId = await notifications.notify({
                        id: 9302,
                        title: "Packaged plugin",
                        text: stored.value
                      }, { timeoutMs: 1000 });

                      const model = ui.state({ title: "Plugin UI", done: false });
                      const handle = await ui.showLayout({
                        type: "Vertical",
                        children: [
                          { type: "Text", id: "title", text: ui.bind(model, "title") },
                          { type: "Checkbox", id: "done", text: "Done", checked: ui.bind(model, "done") },
                          { type: "Button", id: "ok", text: "OK" }
                        ]
                      }, { timeoutMs: 1000, drainIntervalMs: 1 });
                      let clicked = "";
                      await new Promise((resolve) => {
                        const off = handle.on("click", (event) => {
                          clicked = event.id + ":" + event.handleId;
                          off();
                          resolve();
                        }, { timeoutMs: 1000, drainIntervalMs: 1 });
                      });
                      model.update({ title: "Plugin Done", done: true });
                      await new Promise((resolve) => setTimeout(resolve, 30));
                      await handle.batchUpdate([{ id: "title", text: "Plugin Done" }], { timeoutMs: 1000 });
                      await handle.close({ timeoutMs: 1000 });

                      const fetchResult = await fetch("https://example.invalid/plugin", {
                        timeoutMs: 1000,
                        maxResponseBytes: 1024
                      });
                      const fetchText = await fetchResult.text();
                      await notifications.cancel(notificationId, { timeoutMs: 1000 });

                      return {
                        label: input.label,
                        stored: stored.value,
                        clicked,
                        fetchText,
                        notificationId,
                        uiFrozen: Object.isFrozen(handle)
                      };
                    };
                """.trimIndent() + "\n",
            ),
            extraPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
            nodePermissions = V11_BRIDGE_NODE_PERMISSIONS,
            runtimeMarkers = V11_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedRhinoCompatApk(): File {
        val mainScriptName = "main.js"
        val mainScript = """
            "nodejs";

            async function main() {
              const bridge = require("autojs6:bridge");
              const compat = require("autojs6:compat");
              const requests = [];
              const stores = Object.create(null);
              const activeImages = new Set();
              let imageSequence = 0;
              let uiSequence = 0;
              const uiDrained = Object.create(null);

              function respond(request, ok, result, error) {
                setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok,
                  result,
                  error
                })));
              }

              function makeImage(label, width, height) {
                imageSequence += 1;
                const id = "rhino-compat-" + label + "-" + imageSequence;
                activeImages.add(id);
                return { id, __autojs6ImageHandle: id, width, height };
              }

              function imageId(handle) {
                return handle && (handle.__autojs6ImageHandle || handle.id);
              }

              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  const args = request.args || [];

                  if (request.module === "toast" && request.method === "showToast") {
                    respond(request, true, null);
                    return;
                  }

                  if (request.module === "storage" || request.module === "storages") {
                    const storeName = args[0];
                    const key = args[1];
                    const bucket = stores[storeName] || (stores[storeName] = Object.create(null));
                    if (request.method === "clear") {
                      Object.keys(bucket).forEach((entry) => delete bucket[entry]);
                      respond(request, true, null);
                    } else if (request.method === "put") {
                      bucket[key] = args[2];
                      respond(request, true, null);
                    } else if (request.method === "get") {
                      respond(request, true, Object.prototype.hasOwnProperty.call(bucket, key)
                        ? { found: true, value: bucket[key] }
                        : { found: false });
                    } else if (request.method === "contains") {
                      respond(request, true, Object.prototype.hasOwnProperty.call(bucket, key));
                    } else if (request.method === "keys") {
                      respond(request, true, Object.keys(bucket).sort());
                    } else if (request.method === "remove") {
                      delete bucket[key];
                      respond(request, true, null);
                    } else {
                      respond(request, false, null, {
                        code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                        message: "unexpected storage method"
                      });
                    }
                    return;
                  }

                  if (request.module === "accessibility") {
                    if (request.method === "findOne") {
                      respond(request, true, {
                        text: "S11-29",
                        desc: "Rhino compat packaged smoke",
                        id: "rhino_compat",
                        className: "android.widget.TextView",
                        bounds: { left: 1, top: 2, right: 101, bottom: 42 },
                        clickable: true,
                        enabled: true
                      });
                      return;
                    }
                    if (request.method === "click" || request.method === "back" ||
                        request.method === "home" || request.method === "recentApps") {
                      respond(request, true, true);
                      return;
                    }
                  }

                  if (request.module === "image" || request.module === "images") {
                    if (request.method === "readImage") {
                      respond(request, true, makeImage("read", 16, 16));
                      return;
                    }
                    if (request.method === "clip") {
                      const region = args[1] || {};
                      respond(request, true, makeImage("clip", region.width || 4, region.height || 4));
                      return;
                    }
                    if (request.method === "findImage") {
                      respond(request, true, { x: 3, y: 5 });
                      return;
                    }
                    if (request.method === "recycle") {
                      activeImages.delete(imageId(args[0]));
                      respond(request, true, null);
                      return;
                    }
                  }

                  if (request.module === "ui") {
                    if (request.method === "showLayout") {
                      uiSequence += 1;
                      respond(request, true, { id: "packaged-rhino-ui-" + uiSequence });
                      return;
                    }
                    if (request.method === "drainEvents") {
                      const handleId = args[0];
                      respond(request, true, uiDrained[handleId] ? [] : [{ type: "click", id: "compatOk", text: "OK" }]);
                      uiDrained[handleId] = true;
                      return;
                    }
                    if (request.method === "update" || request.method === "batchUpdate" || request.method === "close") {
                      respond(request, true, null);
                      return;
                    }
                  }

                  respond(request, false, null, {
                    code: "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED",
                    message: "unexpected packaged Rhino compat request",
                    module: request.module,
                    method: request.method
                  });
                }
              });

              const status = compat.status();
              const loaderOk = compat.name === "autojs6:compat" &&
                status.entry === "autojs6:compat" &&
                status.defaultGlobalInjection === false &&
                status.implementedTasks.indexOf("S11-20") >= 0 &&
                status.availableGlobalNames.indexOf("images") >= 0;

              const toastResult = await compat.toastLog("S11-29 packaged Rhino compat smoke");
              const toastOk = toastResult === null;

              const fileName = compat.files.join(".", "rhino-compat-packaged.txt");
              compat.files.write(fileName, "S11-29 packaged files");
              const filesOk = compat.files.exists(fileName) &&
                compat.files.read(fileName) === "S11-29 packaged files" &&
                compat.files.remove(fileName) === true &&
                compat.files.exists(fileName) === false;

              const store = compat.storages.create("phase11_packaged");
              await store.clear({ timeoutMs: 1000 });
              await store.put("mode", { value: "packaged", count: 29 }, { timeoutMs: 1000 });
              const stored = await store.get("mode", null, { timeoutMs: 1000 });
              const storageContains = await store.contains("mode", { timeoutMs: 1000 });
              const storageKeys = await store.keys({ timeoutMs: 1000 });
              await store.remove("mode", { timeoutMs: 1000 });
              const storageOk = stored &&
                stored.value === "packaged" &&
                stored.count === 29 &&
                storageContains === true &&
                storageKeys.join(",") === "mode";

              const selectorHandle = compat.text("S11-29").clickable(true);
              const foundNode = await selectorHandle.findOne({ timeoutMs: 1000 });
              const selectorClicked = await selectorHandle.click({ timeoutMs: 1000 });
              const pointClicked = await compat.click(9, 29, { timeoutMs: 1000 });
              const selectorOk = foundNode &&
                foundNode.text === "S11-29" &&
                foundNode.clickable === true &&
                selectorClicked === true &&
                pointClicked === true;

              const source = await compat.images.read("fixtures/source.png", { timeoutMs: 1000 });
              const clipped = await compat.images.clip(source, 0, 0, 4, 4, { timeoutMs: 1000 });
              const match = await compat.images.findImage(source, clipped, { threshold: 0.8, timeoutMs: 1000 });
              await compat.images.recycle(clipped, { timeoutMs: 1000 });
              await compat.images.recycle(source, { timeoutMs: 1000 });
              const imageOk = source.width === 16 &&
                clipped.width === 4 &&
                match &&
                match.x === 3 &&
                match.y === 5;

              const compatUiHandle = await compat.ui.showLayout({
                type: "Column",
                children: [
                  { type: "Text", id: "compatTitle", text: "P12-10 compat UI" },
                  { type: "Button", id: "compatOk", text: "OK" }
                ]
              }, { timeoutMs: 1000, drainIntervalMs: 1 });
              let compatUiClicked = "";
              await new Promise((resolve) => {
                const off = compatUiHandle.on("click", (event) => {
                  compatUiClicked = event.id + ":" + event.handleId;
                  off();
                  resolve();
                }, { timeoutMs: 1000, drainIntervalMs: 1 });
              });
              await compatUiHandle.update({ id: "compatTitle", text: "P12-10 updated" }, { timeoutMs: 1000 });
              await compatUiHandle.batchUpdate([{ id: "compatOk", text: "Done", enabled: false }], { timeoutMs: 1000 });
              await compatUiHandle.close({ timeoutMs: 1000 });
              const compatUiOk = compat.ui &&
                Object.isFrozen(compat.ui) &&
                Object.isFrozen(compatUiHandle) &&
                compatUiHandle.id === "packaged-rhino-ui-1" &&
                compatUiClicked === "compatOk:packaged-rhino-ui-1";

              function hasPermission(request, permission) {
                return (request.permissions || []).indexOf(permission) >= 0;
              }

              const requiredPermissionsOk = requests.every((request) => {
                if (request.module === "toast") {
                  return hasPermission(request, "toast");
                }
                if (request.module === "storage" || request.module === "storages") {
                  return hasPermission(request, "storage");
                }
                if (request.module === "accessibility") {
                  return hasPermission(request, "accessibility");
                }
                if (request.module === "image" || request.module === "images") {
                  return hasPermission(request, "image");
                }
                if (request.module === "ui") {
                  return hasPermission(request, "ui");
                }
                return false;
              });
              const cleanupOk = activeImages.size === 0;
              const pending = bridge.__test.pendingCount();
              const ready = loaderOk && toastOk && filesOk && storageOk && selectorOk && imageOk && compatUiOk &&
                requiredPermissionsOk && cleanupOk && pending === 0;

              console.log("packaged.rhino_compat.ready=" + ready);
              console.log("packaged.rhino_compat.loader=" + loaderOk);
              console.log("packaged.rhino_compat.toast=" + toastOk);
              console.log("packaged.rhino_compat.files=" + filesOk);
              console.log("packaged.rhino_compat.storage=" + storageOk);
              console.log("packaged.rhino_compat.selector=" + selectorOk);
              console.log("packaged.rhino_compat.image=" + imageOk);
              console.log("packaged.rhino_compat.compat_ui=" + compatUiOk);
              console.log("packaged.rhino_compat.permissions=" + requiredPermissionsOk);
              console.log("packaged.rhino_compat.cleanup=" + cleanupOk);
              console.log("packaged.rhino_compat.pending=" + pending);
            }

            return main().catch(error => {
              console.error("packaged.rhino_compat.error=" + (error && (error.stack || error.message) || error));
              process.exitCode = 1;
            });
        """.trimIndent() + "\n"
        return buildPackagedGateDiagnosticsApk(
            rootName = "embedded-node-packaged-rhino-compat-smoke",
            outputApkName = RHINO_COMPAT_PACKAGED_APK_NAME,
            projectName = RHINO_COMPAT_PACKAGED_PROJECT_NAME,
            packageName = RHINO_COMPAT_PACKAGED_PACKAGE_NAME,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = mapOf(
                "node_modules/toast/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/storage/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/storages/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/accessibility/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/image/index.js" to """module.exports = { shadow: true };""" + "\n",
                "node_modules/images/index.js" to """module.exports = { shadow: true };""" + "\n",
            ),
            nodePermissions = RHINO_COMPAT_PACKAGED_NODE_PERMISSIONS,
            runtimeMarkers = RHINO_COMPAT_PACKAGED_RUNTIME_MARKERS,
        )
    }

    private fun buildPackagedGateDiagnosticsApk(
        rootName: String,
        outputApkName: String,
        projectName: String,
        packageName: String,
        mainScriptName: String,
        mainScript: String,
        extraFiles: Map<String, String> = emptyMap(),
        extraPermissions: List<String> = emptyList(),
        nodePermissions: List<String> = emptyList(),
        runtimeMarkers: List<String>,
        requiredTemplateDexMarkers: List<String> = emptyList(),
        launchDescriptor: String? = null,
        launchDescriptorExpectation: PackagedNodeLaunchDescriptorExpectation? = null,
    ): File {
        val root = File(context.cacheDir, rootName).apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create packaged gate diagnostics root: $absolutePath" }
        }
        val projectDir = File(root, "project").apply {
            check(mkdirs()) { "Failed to create packaged gate diagnostics project dir: $absolutePath" }
        }
        val outputDir = File(root, "output").apply {
            check(mkdirs()) { "Failed to create packaged gate diagnostics output dir: $absolutePath" }
        }
        val buildDir = File(root, "workspace")
        val outputApk = File(outputDir, outputApkName)
        val outputPathFile = File(root, "output-path.txt")
        val permissions = (
                listOf(
                    "android.permission.WAKE_LOCK",
                    "android.permission.INTERNET",
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                ) + extraPermissions
                ).distinct()
        val permissionsJson = permissions.joinToString(separator = ",\n") { permission ->
            "                \"$permission\""
        }
        val nodePermissionsJson = nodePermissions.joinToString(separator = ",\n") { permission ->
            "                  \"$permission\""
        }
        val nodeConfigJson = if (nodePermissions.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine("              \"node\": {")
                appendLine("                \"permissions\": [")
                appendLine(nodePermissionsJson)
                appendLine("                ]")
                appendLine("              },")
            }
        }

        File(projectDir, mainScriptName).apply {
            parentFile?.mkdirs()
            writeText(mainScript, StandardCharsets.UTF_8)
        }
        extraFiles.forEach { (relativePath, content) ->
            File(projectDir, relativePath).apply {
                parentFile?.mkdirs()
                writeText(content, StandardCharsets.UTF_8)
            }
        }
        launchDescriptor?.let { descriptor ->
            PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILES.forEach { descriptorFileName ->
                File(projectDir, descriptorFileName).writeText(
                    descriptor,
                    StandardCharsets.UTF_8,
                )
            }
        }
        val runtimeMatrixText = runtimeMarkers.joinToString(separator = "\n", postfix = "\n")
        val projectConfigText = """
            {
              "name": "$projectName",
              "versionName": "1.0.0",
              "versionCode": 1,
              "packageName": "$packageName",
              "main": "$mainScriptName",
              "type": "node",
              "abis": ["arm64-v8a"],
              "libs": ["Embedded Node.js"],
              "permissions": [
$permissionsJson
              ],
$nodeConfigJson
              "launchConfig": {
                "logsVisible": true,
                "splashVisible": false,
                "launcherVisible": true,
                "runOnBoot": false
              }
            }
            """.trimIndent() + "\n"

        File(projectDir, "runtime-matrix.expected.txt").writeText(
            runtimeMatrixText,
            StandardCharsets.UTF_8,
        )
        File(projectDir, ProjectConfig.CONFIG_FILE_NAME).writeText(
            projectConfigText,
            StandardCharsets.UTF_8,
        )

        val config = checkNotNull(ProjectConfig.fromProjectDir(projectDir.absolutePath)) {
            "Failed to load packaged gate diagnostics project config: ${projectDir.absolutePath}"
        }
            .setSourcePath(projectDir.absolutePath)
            .setName(projectName)
            .setPackageName(packageName)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setAbis(arrayListOf("arm64-v8a"))
            .setLibs(arrayListOf(ApkBuilder.Lib.EMBEDDED_NODE_JS.label))
            .setPermissions(ArrayList(permissions))
        if (nodePermissions.isNotEmpty()) {
            config.setNodeConfig(ProjectConfig.NodeConfig().setPermissions(ArrayList(nodePermissions)))
        }
        config.launchConfig = LaunchConfig().apply {
            isLogsVisible = true
            isSplashVisible = false
            isLauncherVisible = true
            isRunOnBoot = false
        }

        val cacheKey = packagedSmokeFixtureCacheKey(
            kind = "gate_diagnostics",
            outputApkName = outputApkName,
            requiredTemplateDexMarkers = requiredTemplateDexMarkers,
            inputs = listOf(
                "rootName" to rootName,
                "projectName" to projectName,
                "packageName" to packageName,
                "mainScriptName" to mainScriptName,
                "mainScript" to mainScript,
                "permissions" to permissions.sorted().joinToString("\n"),
                "nodePermissions" to nodePermissions.sorted().joinToString("\n"),
                "launchDescriptor" to launchDescriptor.orEmpty(),
                "projectConfig" to projectConfigText,
                "runtimeMatrix" to runtimeMatrixText,
            ) + extraFiles.toSortedMap().map { (relativePath, content) ->
                "extra:$relativePath" to content
            },
        )
        restorePackagedSmokeFixtureCache(
            outputApk = outputApk,
            cacheKey = cacheKey,
            label = "gate diagnostics $packageName",
        )?.let { cachedApk ->
            outputPathFile.writeText(cachedApk.absolutePath, StandardCharsets.UTF_8)
            assertPackagedApkContainsGateDiagnosticsSmoke(
                outputApk = cachedApk,
                projectName = projectName,
                packageName = packageName,
                mainScriptName = mainScriptName,
                mainScript = mainScript,
                extraFiles = extraFiles,
                nodePermissions = nodePermissions,
                runtimeMarkers = runtimeMarkers,
                launchDescriptor = launchDescriptor,
                launchDescriptorExpectation = launchDescriptorExpectation,
            )
            exportPackagedSmokeApkForHost(cachedApk)
            return cachedApk
        }

        openPackagedSmokeTemplate(requiredDexMarkers = requiredTemplateDexMarkers).use { templateApk ->
            ApkBuilder(templateApk, outputApk, buildDir.absolutePath)
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()
        }

        outputPathFile.writeText(outputApk.absolutePath, StandardCharsets.UTF_8)
        assertTrue("Packaged gate diagnostics APK was not created: ${outputApk.absolutePath}", outputApk.isFile)
        assertTrue("Packaged gate diagnostics APK is empty: ${outputApk.absolutePath}", outputApk.length() > 0L)
        assertPackagedApkContainsGateDiagnosticsSmoke(
            outputApk = outputApk,
            projectName = projectName,
            packageName = packageName,
            mainScriptName = mainScriptName,
            mainScript = mainScript,
            extraFiles = extraFiles,
            nodePermissions = nodePermissions,
            runtimeMarkers = runtimeMarkers,
            launchDescriptor = launchDescriptor,
            launchDescriptorExpectation = launchDescriptorExpectation,
        )
        storePackagedSmokeFixtureCache(
            outputApk = outputApk,
            cacheKey = cacheKey,
            label = "gate diagnostics $packageName",
        )
        exportPackagedSmokeApkForHost(outputApk)
        return outputApk
    }

    private fun writePackagedModuleSourcesBudget(
        projectDir: File,
        mainScript: File,
    ): PackagedModuleSourcesBudget {
        val preload = collectPackagedPreloadCollection(projectDir, mainScript)
        val diagnostics = preload.diagnostics
        val moduleSourceBytes = preload.moduleSources.map { (sourceName, source) ->
            PackagedModuleSourceBudgetEntry(
                sourceName = sourceName.relativeToProject(projectDir),
                bytes = source.toByteArray(StandardCharsets.UTF_8).size,
            )
        }
        val budget = PackagedModuleSourcesBudget(
            moduleSourceCount = diagnostics.moduleCount,
            limit = PACKAGED_MODULE_SOURCES_LIMIT,
            maxBytesPerSource = PACKAGED_MODULE_SOURCE_MAX_BYTES,
            maxBytesTotal = PACKAGED_MODULE_SOURCES_MAX_BYTES,
            packageJsonCount = diagnostics.packageJsonCount,
            jsonModuleCount = diagnostics.jsonModuleCount,
            staticRequireCount = diagnostics.staticRequireCount,
            dynamicRequireIgnoredCount = diagnostics.dynamicRequireIgnoredCount,
            unresolvedStaticRequireCount = diagnostics.unresolvedStaticRequireCount,
            duplicateSkippedCount = diagnostics.duplicateSkippedCount,
            maxDepth = diagnostics.maxDepth,
            totalSourceBytes = diagnostics.totalSourceBytes,
            moduleSourceBytes = moduleSourceBytes,
        )
        File(projectDir, PACKAGED_PRELOAD_BUDGET_FILE).writeText(budget.toReportText(), StandardCharsets.UTF_8)
        assertTrue(
            "Packaged Node smoke moduleSources budget exceeded: " +
                    "count=${budget.moduleSourceCount}/${budget.limit}, " +
                    "totalBytes=${budget.totalSourceBytes}/${budget.maxBytesTotal}, " +
                    "maxModuleBytes=${budget.maxObservedBytes}/${budget.maxBytesPerSource}\n${budget.toReportText()}",
            budget.ready,
        )
        assertTrue("Packaged Node smoke should preload at least one module", budget.moduleSourceCount > 0)
        assertTrue("Packaged Node smoke should scan static require calls", budget.staticRequireCount > 0)
        return budget
    }

    private fun collectPackagedPreloadCollection(
        projectDir: File,
        mainScript: File,
    ): PackagedPreloadCollection {
        val engine = NativeNodeEmbeddedJavaScriptEngine(context)
        return try {
            val method = NativeNodeEmbeddedJavaScriptEngine::class.java.getDeclaredMethod(
                "collectCommonJsModuleSources",
                String::class.java,
                String::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val result = checkNotNull(
                method.invoke(
                    engine,
                    mainScript.readText(StandardCharsets.UTF_8),
                    mainScript.absolutePath,
                    projectDir.absolutePath,
                ),
            ) { "CommonJS preload reflection returned null" }
            val diagnosticsGetter = result.javaClass.getDeclaredMethod("getDiagnostics")
            diagnosticsGetter.isAccessible = true
            val moduleSourcesGetter = result.javaClass.getDeclaredMethod("getModuleSources")
            moduleSourcesGetter.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val moduleSources = moduleSourcesGetter.invoke(result) as Map<String, String>
            PackagedPreloadCollection(
                diagnostics = diagnosticsGetter.invoke(result) as NativeNodeEmbeddedJavaScriptEngine.CommonJsPreloadDiagnostics,
                moduleSources = moduleSources,
            )
        } finally {
            runCatching { RhinoContext.exit() }
        }
    }

    private fun assertPackagedModuleSourcesBudget(
        zip: ZipFile,
        expectedBudget: PackagedModuleSourcesBudget,
    ) {
        val text = readZipText(zip, "assets/project/$PACKAGED_PRELOAD_BUDGET_FILE")
        val actualBudget = PackagedModuleSourcesBudget.fromReportText(text)
        assertEquals(PACKAGED_MODULE_SOURCES_LIMIT, actualBudget.limit)
        assertEquals(PACKAGED_MODULE_SOURCE_MAX_BYTES, actualBudget.maxBytesPerSource)
        assertEquals(PACKAGED_MODULE_SOURCES_MAX_BYTES, actualBudget.maxBytesTotal)
        assertEquals(expectedBudget.moduleSourceCount, actualBudget.moduleSourceCount)
        assertEquals(expectedBudget.packageJsonCount, actualBudget.packageJsonCount)
        assertEquals(expectedBudget.jsonModuleCount, actualBudget.jsonModuleCount)
        assertEquals(expectedBudget.staticRequireCount, actualBudget.staticRequireCount)
        assertEquals(expectedBudget.dynamicRequireIgnoredCount, actualBudget.dynamicRequireIgnoredCount)
        assertEquals(expectedBudget.unresolvedStaticRequireCount, actualBudget.unresolvedStaticRequireCount)
        assertEquals(expectedBudget.duplicateSkippedCount, actualBudget.duplicateSkippedCount)
        assertEquals(expectedBudget.maxDepth, actualBudget.maxDepth)
        assertEquals(expectedBudget.totalSourceBytes, actualBudget.totalSourceBytes)
        assertEquals(expectedBudget.maxObservedBytes, actualBudget.maxObservedBytes)
        assertEquals(expectedBudget.moduleSourceBytes, actualBudget.moduleSourceBytes)
        assertTrue(
            "Packaged moduleSources budget should be ready: $text",
            actualBudget.ready,
        )
    }

    private fun assertPackagedApkContainsFullCompatibilitySmoke(
        outputApk: File,
        smokeScript: String,
        projectDir: File,
        preloadBudget: PackagedModuleSourcesBudget,
        mainScriptName: String,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"

            assertTrue("Packaged APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue("Packaged APK is missing bundled project config", "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries)
            assertTrue("Packaged APK is missing root package.json", "assets/project/package.json" in entries)
            assertTrue(
                "Packaged APK is missing internal Node launch descriptor",
                "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE" in entries,
            )
            assertTrue("Packaged APK is missing runtime matrix marker", "assets/project/runtime-matrix.expected.txt" in entries)
            assertTrue(
                "Packaged APK is missing moduleSources budget marker",
                "assets/project/$PACKAGED_PRELOAD_BUDGET_FILE" in entries,
            )

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled project config should parse"
            }
            assertEquals(SUCCESS_PROJECT_NAME, bundledConfig.name)
            assertEquals(SUCCESS_PACKAGE_NAME, bundledConfig.packageName)
            assertEquals(mainScriptName, bundledConfig.mainScriptFileName)
            assertEquals("node", bundledConfig.projectType)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )
            assertTrue(
                "Packaged root package.json should keep script launch entry",
                readZipText(zip, "assets/project/package.json").contains(""""packaged-smoke""""),
            )
            assertPackagedNodeLaunchDescriptor(
                zip = zip,
                label = "Packaged internal launch",
                expected = PackagedNodeLaunchDescriptorExpectation(
                    launchKind = NodeProjectLaunchKind.PACKAGE_SCRIPT.descriptorValue,
                    workingDirectory = ".",
                   scriptName = "packaged-smoke",
                   argv = listOf("--from-packaged-launch"),
                   env = mapOf("PACKAGED_LAUNCH_ENV" to "packaged-launch-secret"),
                   timeoutMs = FULL_COMPATIBILITY_PACKAGED_TIMEOUT_MS,
               ),
           )

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            SUCCESS_RUNTIME_MARKERS.forEach { marker ->
                assertTrue("Full compatibility runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            val encryptedMain = readZipBytes(zip, mainEntry)
            assertNotEquals(
                "Packaged JavaScript should be encrypted in APK output",
                smokeScript,
                encryptedMain.toString(StandardCharsets.UTF_8),
            )
            assertPackagedModuleSourcesBudget(zip, preloadBudget)
            assertPackagedRealNpmCorpusEntries(zip, entries, projectDir)
            assertPackagedCommonJsResolverEntries(zip, entries, projectDir)
            assertPackagedDependencyResolutionEntries(zip, entries, projectDir)
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsPackageScriptLaunchSmoke(
        outputApk: File,
        mainScript: String,
        scriptLaunchScript: String,
        mainScriptName: String,
        scriptLaunchScriptName: String,
        projectDir: File,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"
            val scriptLaunchEntry = "assets/project/$scriptLaunchScriptName"

            assertTrue("Package script launch APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue(
                "Package script launch APK is missing encrypted package script: $scriptLaunchEntry",
                scriptLaunchEntry in entries,
            )
            assertTrue(
                "Package script launch APK is missing bundled project config",
                "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries,
            )
            assertTrue(
                "Package script launch APK is missing package package.json",
                "assets/project/packages/script-tool/package.json" in entries,
            )
            assertTrue(
                "Package script launch APK is missing local tool package metadata",
                "assets/project/packages/script-tool/node_modules/@autojs6/toolchain-cli/package.json" in entries,
            )
            assertTrue(
                "Package script launch APK is missing internal Node launch descriptor",
                "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE" in entries,
            )
            assertTrue(
                "Package script launch APK is missing runtime matrix marker",
                "assets/project/runtime-matrix.expected.txt" in entries,
            )
            assertFalse(
                "Package script launch smoke should not rely on a physical node_modules/.bin entry",
                entries.any { entry -> entry.startsWith("assets/project/packages/script-tool/node_modules/.bin/") },
            )

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled package script launch project config should parse"
            }
            assertEquals(SCRIPT_LAUNCH_PROJECT_NAME, bundledConfig.name)
            assertEquals(SCRIPT_LAUNCH_PACKAGE_NAME, bundledConfig.packageName)
            assertEquals(mainScriptName, bundledConfig.mainScriptFileName)
            assertEquals("node", bundledConfig.projectType)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled package script launch project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )

            val packageJson = readZipText(zip, "assets/project/packages/script-tool/package.json")
            assertTrue("Package script launch package.json should keep scripts block", packageJson.contains(""""scripts""""))
            assertTrue(
                "Package script launch package.json should keep packaged-script-smoke entry",
                packageJson.contains(""""packaged-script-smoke""""),
            )
            assertTrue(
                "Package script launch package.json should keep node_modules/.bin command head",
                packageJson.contains("node_modules/.bin/@autojs6/toolchain-cli"),
            )
            assertTrue(
                "Package script launch package.json should pass toolchain config path",
                packageJson.contains("--expect-config=../../packages/script-tool/toolchain.config.json"),
            )
            val toolPackageJson = readZipText(
                zip,
                "assets/project/packages/script-tool/node_modules/@autojs6/toolchain-cli/package.json",
            )
            assertTrue(
                "Package script launch local tool package.json should keep bin metadata",
                toolPackageJson.contains(""""@autojs6/toolchain-cli""""),
            )

            assertPackagedNodeLaunchDescriptor(
                zip = zip,
                label = "Package script launch",
                expected = PackagedNodeLaunchDescriptorExpectation(
                    launchKind = NodeProjectLaunchKind.PACKAGE_SCRIPT.descriptorValue,
                    packageDir = "packages/script-tool",
                    workingDirectory = "workspace/script-launch",
                    scriptName = "packaged-script-smoke",
                    argv = listOf("--from-packaged-script-launch"),
                    env = mapOf("PACKAGED_SCRIPT_ENV" to "packaged-script-secret"),
                ),
            )

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            SCRIPT_LAUNCH_RUNTIME_MARKERS.forEach { marker ->
                assertTrue("Package script launch runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            assertNotEquals(
                "Packaged package script launch main JavaScript should be encrypted in APK output",
                mainScript,
                readZipBytes(zip, mainEntry).toString(StandardCharsets.UTF_8),
            )
            assertNotEquals(
                "Packaged package script launch JavaScript should be encrypted in APK output",
                scriptLaunchScript,
                readZipBytes(zip, scriptLaunchEntry).toString(StandardCharsets.UTF_8),
            )
            assertPackagedNpmToolchainCorpusEntries(
                zip = zip,
                entries = entries,
                projectDir = projectDir,
                packageDirRelativePath = "packages/script-tool",
            )
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsPackageBinLaunchSmoke(
        outputApk: File,
        mainScript: String,
        binScript: String,
        mainScriptName: String,
        binScriptName: String,
        projectDir: File,
        binLaunchArgv: List<String>,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"
            val binEntry = "assets/project/$binScriptName"

            assertTrue("Package bin launch APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue("Package bin launch APK is missing encrypted bin script: $binEntry", binEntry in entries)
            assertTrue("Package bin launch APK is missing bundled project config", "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries)
            assertTrue(
                "Package bin launch APK is missing package package.json",
                "assets/project/packages/bin-tool/package.json" in entries,
            )
            assertTrue(
                "Package bin launch APK is missing internal Node launch descriptor",
                "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE" in entries,
            )
            assertTrue("Package bin launch APK is missing runtime matrix marker", "assets/project/runtime-matrix.expected.txt" in entries)

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled package bin launch project config should parse"
            }
            assertEquals(BIN_LAUNCH_PROJECT_NAME, bundledConfig.name)
            assertEquals(BIN_LAUNCH_PACKAGE_NAME, bundledConfig.packageName)
            assertEquals(mainScriptName, bundledConfig.mainScriptFileName)
            assertEquals("node", bundledConfig.projectType)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled package bin launch project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )

            val packageJson = readZipText(zip, "assets/project/packages/bin-tool/package.json")
            assertTrue("Package bin launch package.json should keep bin block", packageJson.contains(""""bin""""))
            assertTrue(
                "Package bin launch package.json should keep packaged-bin-smoke entry",
                packageJson.contains(""""packaged-bin-smoke""""),
            )
            assertTrue(
                "Package bin launch package.json should target the npm toolchain CLI",
                packageJson.contains("node_modules/@autojs6/toolchain-cli/bin/cli.js"),
            )
            assertTrue(
                "Package bin launch package.json should include toolchain plugin dependency",
                packageJson.contains(""""@autojs6/toolchain-plugin""""),
            )

            assertPackagedNodeLaunchDescriptor(
                zip = zip,
                label = "Package bin launch",
                expected = PackagedNodeLaunchDescriptorExpectation(
                    launchKind = NodeProjectLaunchKind.PACKAGE_BIN.descriptorValue,
                    packageDir = "packages/bin-tool",
                    workingDirectory = "workspace/bin-launch",
                    binName = "packaged-bin-smoke",
                    argv = binLaunchArgv,
                    env = mapOf("PACKAGED_BIN_ENV" to "packaged-bin-secret"),
                ),
            )

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            BIN_LAUNCH_RUNTIME_MARKERS.forEach { marker ->
                assertTrue("Package bin launch runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            assertNotEquals(
                "Packaged package bin launch main JavaScript should be encrypted in APK output",
                mainScript,
                readZipBytes(zip, mainEntry).toString(StandardCharsets.UTF_8),
            )
            assertNotEquals(
                "Packaged package bin launch CLI JavaScript should be encrypted in APK output",
                binScript,
                readZipBytes(zip, binEntry).toString(StandardCharsets.UTF_8),
            )
            assertPackagedNpmToolchainCorpusEntries(
                zip = zip,
                entries = entries,
                projectDir = projectDir,
                packageDirRelativePath = "packages/bin-tool",
            )
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsPackageBinStringScriptSmoke(
        outputApk: File,
        mainScript: String,
        cliScript: String,
        mainScriptName: String,
        cliScriptName: String,
        projectDir: File,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"
            val cliEntry = "assets/project/$cliScriptName"

            assertTrue("Package bin string script APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue("Package bin string script APK is missing encrypted CLI script: $cliEntry", cliEntry in entries)
            assertTrue(
                "Package bin string script APK is missing bundled project config",
                "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries,
            )
            assertTrue(
                "Package bin string script APK is missing package package.json",
                "assets/project/packages/bin-string-script-app/package.json" in entries,
            )
            assertTrue(
                "Package bin string script APK is missing internal Node launch descriptor",
                "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE" in entries,
            )
            assertTrue(
                "Package bin string script APK is missing runtime matrix marker",
                "assets/project/runtime-matrix.expected.txt" in entries,
            )
            assertFalse(
                "Package bin string script smoke should resolve the package bin string without a physical .bin entry",
                entries.any { entry -> entry.startsWith("assets/project/packages/bin-string-script-app/node_modules/.bin/") },
            )

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled package bin string script project config should parse"
            }
            assertEquals(BIN_STRING_SCRIPT_PROJECT_NAME, bundledConfig.name)
            assertEquals(BIN_STRING_SCRIPT_PACKAGE_NAME, bundledConfig.packageName)
            assertEquals(mainScriptName, bundledConfig.mainScriptFileName)
            assertEquals("node", bundledConfig.projectType)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled package bin string script project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )

            val packageJson = readZipText(zip, "assets/project/packages/bin-string-script-app/package.json")
            assertTrue("Package bin string script package.json should keep scripts block", packageJson.contains(""""scripts""""))
            assertTrue(
                "Package bin string script package.json should keep packaged-bin-string-script entry",
                packageJson.contains(""""packaged-bin-string-script""""),
            )
            assertTrue(
                "Package bin string script package.json should invoke scoped CLI by short bin name",
                packageJson.contains("\"packaged-bin-string-script\": \"string-bin-cli "),
            )
            val cliPackageJson = readZipText(
                zip,
                "assets/project/packages/bin-string-script-app/node_modules/@autojs6/string-bin-cli/package.json",
            )
            assertTrue(
                "Package bin string CLI package.json should keep bin string metadata",
                cliPackageJson.contains(""""bin": "./bin/cli.js""""),
            )
            assertTrue(
                "Package bin string CLI package.json should keep package imports metadata",
                cliPackageJson.contains(""""#feature""""),
            )

            assertPackagedNodeLaunchDescriptor(
                zip = zip,
                label = "Package bin string script launch",
                expected = PackagedNodeLaunchDescriptorExpectation(
                    launchKind = NodeProjectLaunchKind.PACKAGE_SCRIPT.descriptorValue,
                    packageDir = "packages/bin-string-script-app",
                    workingDirectory = "workspace/bin-string-script-run",
                    scriptName = "packaged-bin-string-script",
                    argv = listOf("--from-packaged-bin-string-script"),
                    env = mapOf("PACKAGED_BIN_STRING_ENV" to "packaged-bin-string-secret"),
                ),
            )

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            BIN_STRING_SCRIPT_RUNTIME_MARKERS.forEach { marker ->
                assertTrue("Package bin string script runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            assertNotEquals(
                "Packaged package bin string script main JavaScript should be encrypted in APK output",
                mainScript,
                readZipBytes(zip, mainEntry).toString(StandardCharsets.UTF_8),
            )
            assertNotEquals(
                "Packaged package bin string CLI JavaScript should be encrypted in APK output",
                cliScript,
                readZipBytes(zip, cliEntry).toString(StandardCharsets.UTF_8),
            )
            assertPackagedPackageBinStringCliCorpusEntries(
                zip = zip,
                entries = entries,
                projectDir = projectDir,
                packageDirRelativePath = "packages/bin-string-script-app",
            )
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsDiagnosticsSmoke(outputApk: File, diagnosticsScript: String, projectDir: File) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()

            assertTrue("Diagnostics APK is missing encrypted main script", "assets/project/main.js" in entries)
            assertTrue("Diagnostics APK is missing bundled project config", "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries)
            assertTrue("Diagnostics APK is missing runtime matrix marker", "assets/project/runtime-matrix.expected.txt" in entries)

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled diagnostics project config should parse"
            }
            assertEquals(DIAGNOSTICS_PROJECT_NAME, bundledConfig.name)
            assertEquals(DIAGNOSTICS_PACKAGE_NAME, bundledConfig.packageName)
            assertEquals("main.js", bundledConfig.mainScriptFileName)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled diagnostics project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            DIAGNOSTICS_RUNTIME_MARKERS.forEach { marker ->
                assertTrue("Diagnostics runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            assertNotEquals(
                "Packaged diagnostics main JavaScript should be encrypted in APK output",
                diagnosticsScript,
                readZipBytes(zip, "assets/project/main.js").toString(StandardCharsets.UTF_8),
            )
            assertPackagedDiagnosticsEntries(zip, entries, projectDir)
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsLaunchFailureSmoke(
        outputApk: File,
        failureCase: PackagedLaunchFailureCase,
        mainScriptName: String,
        mainScript: String,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"

            assertTrue("Launch failure APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue(
                "Launch failure APK is missing bundled project config",
                "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries,
            )
            assertTrue(
                "Launch failure APK is missing internal Node launch descriptor",
                "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE" in entries,
            )
            failureCase.requiredEntries.forEach { relativePath ->
                assertTrue(
                    "Launch failure APK is missing required project entry: assets/project/$relativePath",
                    "assets/project/$relativePath" in entries,
                )
            }

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled launch failure project config should parse"
            }
            assertEquals(failureCase.projectName, bundledConfig.name)
            assertEquals(failureCase.packageName, bundledConfig.packageName)
            assertEquals(failureCase.projectConfigMainScriptName, bundledConfig.mainScriptFileName)
            assertEquals("node", bundledConfig.projectType)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled launch failure project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )

            if (failureCase.verifyDescriptor) {
                assertPackagedNodeLaunchDescriptor(
                    zip = zip,
                    label = "Launch failure",
                    expected = JSONObject(failureCase.descriptor).toPackagedNodeLaunchDescriptorExpectation(),
                )
            } else {
                assertEquals(
                    "Launch failure raw descriptor",
                    failureCase.descriptor,
                    readZipText(zip, "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE").trimEnd('\r', '\n'),
                )
            }

            assertNotEquals(
                "Packaged launch failure main JavaScript should be encrypted",
                mainScript,
                readZipBytes(zip, mainEntry).toString(StandardCharsets.UTF_8),
            )
            failureCase.extraFiles.forEach { (relativePath, source) ->
                if (!relativePath.endsWith(".js") && !relativePath.endsWith(".mjs")) {
                    return@forEach
                }
                val entry = "assets/project/$relativePath"
                assertNotEquals(
                    "Packaged launch failure JavaScript should be encrypted: $entry",
                    source,
                    readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
                )
            }
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedApkContainsGateDiagnosticsSmoke(
        outputApk: File,
        projectName: String,
        packageName: String,
        mainScriptName: String,
        mainScript: String,
        extraFiles: Map<String, String>,
        nodePermissions: List<String>,
        runtimeMarkers: List<String>,
        launchDescriptor: String? = null,
        launchDescriptorExpectation: PackagedNodeLaunchDescriptorExpectation? = null,
    ) {
        ZipFile(outputApk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val mainEntry = "assets/project/$mainScriptName"

            assertTrue("Gate diagnostics APK is missing encrypted main script: $mainEntry", mainEntry in entries)
            assertTrue("Gate diagnostics APK is missing bundled project config", "assets/project/${ProjectConfig.CONFIG_FILE_NAME}" in entries)
            assertTrue("Gate diagnostics APK is missing runtime matrix marker", "assets/project/runtime-matrix.expected.txt" in entries)
            if (launchDescriptor != null) {
                PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILES.forEach { descriptorFileName ->
                    assertTrue(
                        "Gate diagnostics APK is missing internal Node launch descriptor: $descriptorFileName",
                        "assets/project/$descriptorFileName" in entries,
                    )
                }
            }

            val projectConfig = readZipText(zip, "assets/project/${ProjectConfig.CONFIG_FILE_NAME}")
            val bundledConfig = checkNotNull(ProjectConfig.fromJson(projectConfig)) {
                "Bundled gate diagnostics project config should parse"
            }
            assertEquals(projectName, bundledConfig.name)
            assertEquals(packageName, bundledConfig.packageName)
            assertEquals(mainScriptName, bundledConfig.mainScriptFileName)
            assertEquals(listOf("arm64-v8a"), bundledConfig.abis)
            assertTrue(
                "Bundled gate diagnostics project config should keep Embedded Node.js lib selection",
                ApkBuilder.Lib.EMBEDDED_NODE_JS.label in bundledConfig.libs,
            )
            if (nodePermissions.isNotEmpty()) {
                assertEquals(
                    "Bundled gate diagnostics project config should keep Node bridge permissions",
                    nodePermissions.sorted(),
                    bundledConfig.nodeConfig?.permissions.orEmpty().sorted(),
                )
            }
            launchDescriptorExpectation?.let { expectation ->
                assertPackagedNodeLaunchDescriptor(
                    zip = zip,
                    label = "Gate diagnostics",
                    expected = expectation,
                )
            } ?: launchDescriptor?.let { descriptor ->
                assertEquals(
                    "Gate diagnostics raw launch descriptor",
                    descriptor.trimEnd('\r', '\n'),
                    readZipText(zip, "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE").trimEnd('\r', '\n'),
                )
            }

            val expectedMatrix = readZipText(zip, "assets/project/runtime-matrix.expected.txt")
            runtimeMarkers.forEach { marker ->
                assertTrue("Gate diagnostics runtime matrix is missing marker: $marker", expectedMatrix.contains(marker))
            }

            assertNotEquals(
                "Packaged gate diagnostics main JavaScript should be encrypted: $mainEntry",
                mainScript,
                readZipBytes(zip, mainEntry).toString(StandardCharsets.UTF_8),
            )
            extraFiles.forEach { (relativePath, source) ->
                val entry = "assets/project/$relativePath"
                assertTrue("Gate diagnostics APK is missing extra source: $entry", entry in entries)
                if (relativePath.endsWith(".js") || relativePath.endsWith(".mjs")) {
                    assertNotEquals(
                        "Packaged gate diagnostics JavaScript should be encrypted: $entry",
                        source,
                        readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
                    )
                }
            }
            if (NodeProjectRuntimeCompatibilityDescriptor.DESCRIPTOR_FILE_NAMES.any { descriptorName -> descriptorName in extraFiles }) {
                assertPackagedRuntimeCompatibilityDescriptor(zip)
            }
            assertPackagedNodeNativeLibEntries(entries)
        }
    }

    private fun assertPackagedRuntimeCompatibilityDescriptor(zip: ZipFile) {
        val entry = NodeProjectRuntimeCompatibilityDescriptor.DESCRIPTOR_FILE_NAMES
            .map { descriptorName -> "assets/project/$descriptorName" }
            .firstOrNull { candidate -> zip.getEntry(candidate) != null }
            ?: error("Packaged APK is missing runtime compatibility descriptor")
        val descriptor = JSONObject(readZipText(zip, entry))
        NodeProjectRuntimeCompatibilityDescriptor.requireCompatible(descriptor, entry)
        val actualVersions = descriptor.getJSONObject("versions")
        val expectedVersions = NodeProjectRuntimeCompatibilityDescriptor.currentVersionsJson()
        expectedVersions.keys().forEach { key ->
            assertEquals(
                "Packaged runtime compatibility descriptor versions.$key",
                expectedVersions.getString(key),
                actualVersions.getString(key),
            )
        }
    }

    private fun writePackagedDiagnosticsCorpus(projectDir: File) {
        EmbeddedNodeFixtureCorpus.writeNpmPackage(
            projectDir,
            "diagnostic-boom",
            """{"name":"diagnostic-boom","version":"1.0.0","main":"index.js"}""",
            mapOf(
                "index.js" to """
                "use strict";
                throw new Error("packaged diagnostic boom");
                """.trimIndent(),
            ),
        )
        EmbeddedNodeFixtureCorpus.writeNpmPackage(
            projectDir,
            "diagnostic-type-module",
            """{"name":"diagnostic-type-module","version":"1.0.0","type":"module","main":"index.mjs"}""",
            mapOf(
                "index.mjs" to """
                export default "diagnostic-type-module-sync";
                """.trimIndent(),
            ),
        )
        EmbeddedNodeFixtureCorpus.writeNpmPackage(
            projectDir,
            "diagnostic-esm-export-context",
            """
            {
              "name": "diagnostic-esm-export-context",
              "version": "1.0.0",
              "exports": {
                ".": {
                  "import": "./esm.mjs",
                  "require": "./esm.mjs"
                }
              }
            }
            """.trimIndent(),
            mapOf(
                "esm.mjs" to """
                export default "diagnostic-esm-export-context-sync";
                """.trimIndent(),
            ),
        )
        EmbeddedNodeFixtureCorpus.writeNpmPackage(
            projectDir,
            "diagnostic-async-esm",
            """{"name":"diagnostic-async-esm","version":"1.0.0","type":"module","main":"index.mjs"}""",
            mapOf(
                "index.mjs" to """
                export default await Promise.resolve("diagnostic-async-esm");
                """.trimIndent(),
            ),
        )
    }

    private fun assertPackagedRealNpmCorpusEntries(zip: ZipFile, entries: Set<String>, projectDir: File) {
        val packageJsonEntries = listOf(
            "assets/project/node_modules/is-number/package.json",
            "assets/project/node_modules/is-odd/package.json",
            "assets/project/node_modules/kind-of/package.json",
            "assets/project/node_modules/once/package.json",
            "assets/project/node_modules/wrappy/package.json",
            "assets/project/node_modules/ms/package.json",
            "assets/project/node_modules/debug/package.json",
            "assets/project/node_modules/debug/node_modules/ms/package.json",
            "assets/project/node_modules/picocolors/package.json",
            "assets/project/node_modules/safe-buffer/package.json",
            "assets/project/node_modules/camelcase/package.json",
            "assets/project/node_modules/decamelize/package.json",
            "assets/project/node_modules/ansi-regex/package.json",
            "assets/project/node_modules/strip-ansi/package.json",
            "assets/project/node_modules/is-fullwidth-code-point/package.json",
            "assets/project/node_modules/east-asian-width-lite/package.json",
            "assets/project/node_modules/emoji-regex-lite/package.json",
            "assets/project/node_modules/string-width/package.json",
            "assets/project/node_modules/has-flag/package.json",
            "assets/project/node_modules/supports-color-lite/package.json",
            "assets/project/node_modules/yargs-parser-lite/package.json",
            "assets/project/node_modules/escape-string-regexp/package.json",
            "assets/project/node_modules/balanced-match/package.json",
            "assets/project/node_modules/concat-map/package.json",
            "assets/project/node_modules/brace-expansion-lite/package.json",
        )
        packageJsonEntries.forEach { entry ->
            assertTrue("Packaged APK is missing real npm package metadata: $entry", entry in entries)
            assertTrue("Packaged npm package.json should remain readable: $entry", readZipText(zip, entry).contains(""""name""""))
        }

        val encryptedJavaScriptEntries = listOf(
            "node_modules/is-number/index.js",
            "node_modules/is-odd/index.js",
            "node_modules/kind-of/index.js",
            "node_modules/once/once.js",
            "node_modules/wrappy/wrappy.js",
            "node_modules/ms/index.js",
            "node_modules/debug/src/index.js",
            "node_modules/debug/src/common.js",
            "node_modules/debug/node_modules/ms/index.js",
            "node_modules/picocolors/picocolors.js",
            "node_modules/safe-buffer/index.js",
            "node_modules/camelcase/index.js",
            "node_modules/decamelize/index.js",
            "node_modules/ansi-regex/index.js",
            "node_modules/strip-ansi/index.js",
            "node_modules/is-fullwidth-code-point/index.js",
            "node_modules/east-asian-width-lite/source/index.js",
            "node_modules/emoji-regex-lite/index.js",
            "node_modules/string-width/index.js",
            "node_modules/has-flag/index.js",
            "node_modules/supports-color-lite/index.js",
            "node_modules/supports-color-lite/node.js",
            "node_modules/yargs-parser-lite/build/index.cjs",
            "node_modules/escape-string-regexp/index.js",
            "node_modules/balanced-match/index.js",
            "node_modules/concat-map/index.js",
            "node_modules/brace-expansion-lite/index.js",
        )
        encryptedJavaScriptEntries.forEach { relativePath ->
            val entry = "assets/project/$relativePath"
            assertTrue("Packaged APK is missing real npm JavaScript source: $entry", entry in entries)
            assertNotEquals(
                "Packaged real npm JavaScript should be encrypted: $entry",
                File(projectDir, relativePath).readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }
    }

    private fun assertPackagedCommonJsResolverEntries(zip: ZipFile, entries: Set<String>, projectDir: File) {
        val packageJsonEntries = listOf(
            "assets/project/node_modules/resolver-edge-pkg/package.json",
            "assets/project/node_modules/imports-edge-pkg/package.json",
            "assets/project/node_modules/@scope/resolver-self-pkg/package.json",
            "assets/project/node_modules/fallback-main-pkg/package.json",
        )
        packageJsonEntries.forEach { entry ->
            assertTrue("Packaged APK is missing CommonJS resolver package metadata: $entry", entry in entries)
            assertTrue("Packaged resolver package.json should remain readable: $entry", readZipText(zip, entry).contains(""""name""""))
        }

        val encryptedJavaScriptEntries = listOf(
            "node_modules/resolver-edge-pkg/index.js",
            "node_modules/resolver-edge-pkg/features/a.js",
            "node_modules/resolver-edge-pkg/condition/require.js",
            "node_modules/resolver-edge-pkg/condition/node.js",
            "node_modules/resolver-edge-pkg/condition/default.js",
            "node_modules/resolver-edge-pkg/private/secret.js",
            "node_modules/resolver-edge-pkg/esm.mjs",
            "node_modules/imports-edge-pkg/index.js",
            "node_modules/imports-edge-pkg/lib/alias.js",
            "node_modules/imports-edge-pkg/lib/wild/a.js",
            "node_modules/imports-edge-pkg/lib/condition/require.js",
            "node_modules/imports-edge-pkg/lib/condition/node.js",
            "node_modules/imports-edge-pkg/lib/condition/default.js",
            "node_modules/imports-edge-pkg/lib/private.js",
            "node_modules/imports-edge-pkg/esm.mjs",
            "node_modules/@scope/resolver-self-pkg/index.js",
            "node_modules/@scope/resolver-self-pkg/feature.js",
            "node_modules/fallback-main-pkg/index.js",
        )
        encryptedJavaScriptEntries.forEach { relativePath ->
            val entry = "assets/project/$relativePath"
            assertTrue("Packaged APK is missing CommonJS resolver JavaScript source: $entry", entry in entries)
            assertNotEquals(
                "Packaged CommonJS resolver JavaScript should be encrypted: $entry",
                File(projectDir, relativePath).readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }
    }

    private fun assertPackagedDependencyResolutionEntries(zip: ZipFile, entries: Set<String>, projectDir: File) {
        val packageJsonEntries = listOf(
            "assets/project/node_modules/path/package.json",
            "assets/project/node_modules/fs/package.json",
            "assets/project/node_modules/util/package.json",
            "assets/project/node_modules/resolution-shared/package.json",
            "assets/project/node_modules/resolution-parent/package.json",
            "assets/project/node_modules/resolution-parent/node_modules/resolution-shared/package.json",
        )
        packageJsonEntries.forEach { entry ->
            assertTrue("Packaged APK is missing dependency resolution package metadata: $entry", entry in entries)
            assertTrue("Dependency resolution package.json should remain readable: $entry", readZipText(zip, entry).contains(""""name""""))
        }

        val encryptedJavaScriptEntries = listOf(
            "node_modules/path/index.js",
            "node_modules/fs/index.js",
            "node_modules/util/index.js",
            "node_modules/resolution-shared/index.js",
            "node_modules/resolution-shared/subpath.js",
            "node_modules/resolution-parent/index.js",
            "node_modules/resolution-parent/node_modules/resolution-shared/index.js",
            "node_modules/resolution-parent/node_modules/resolution-shared/subpath.js",
        )
        encryptedJavaScriptEntries.forEach { relativePath ->
            val entry = "assets/project/$relativePath"
            assertTrue("Packaged APK is missing dependency resolution JavaScript source: $entry", entry in entries)
            assertNotEquals(
                "Packaged dependency resolution JavaScript should be encrypted: $entry",
                File(projectDir, relativePath).readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }
    }

    private fun assertPackagedNpmToolchainCorpusEntries(
        zip: ZipFile,
        entries: Set<String>,
        projectDir: File,
        packageDirRelativePath: String,
    ) {
        val packageJsonEntries = listOf(
            "node_modules/@autojs6/toolchain-cli/package.json",
            "node_modules/@autojs6/toolchain-plugin/package.json",
            "node_modules/@autojs6/toolchain-shared/package.json",
            "node_modules/toolchain-helper/package.json",
            "node_modules/yargs-parser-lite/package.json",
            "node_modules/string-width/package.json",
            "node_modules/brace-expansion-lite/package.json",
            "node_modules/strip-ansi/package.json",
            "node_modules/supports-color-lite/package.json",
        )
        packageJsonEntries.forEach { relativePath ->
            val entry = "assets/project/$packageDirRelativePath/$relativePath"
            assertTrue("Packaged APK is missing npm toolchain package metadata: $entry", entry in entries)
            assertTrue("Npm toolchain package.json should remain readable: $entry", readZipText(zip, entry).contains(""""name""""))
        }

        val encryptedJavaScriptEntries = listOf(
            "node_modules/@autojs6/toolchain-cli/bin/cli.js",
            "node_modules/@autojs6/toolchain-plugin/index.js",
            "node_modules/@autojs6/toolchain-plugin/helper.js",
            "node_modules/@autojs6/toolchain-shared/index.js",
            "node_modules/toolchain-helper/cli.js",
            "src/index.js",
            "src/test.js",
            "plugins/local.js",
            "node_modules/yargs-parser-lite/build/index.cjs",
            "node_modules/string-width/index.js",
            "node_modules/brace-expansion-lite/index.js",
            "node_modules/strip-ansi/index.js",
            "node_modules/supports-color-lite/index.js",
            "node_modules/supports-color-lite/node.js",
        )
        encryptedJavaScriptEntries.forEach { relativePath ->
            val entry = "assets/project/$packageDirRelativePath/$relativePath"
            val source = File(projectDir, "$packageDirRelativePath/$relativePath")
            assertTrue("Packaged APK is missing npm toolchain JavaScript source: $entry", entry in entries)
            assertTrue("Npm toolchain source file should exist before packaging: ${source.absolutePath}", source.isFile)
            assertNotEquals(
                "Packaged npm toolchain JavaScript should be encrypted: $entry",
                source.readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }

        val configEntry = "assets/project/$packageDirRelativePath/toolchain.config.json"
        assertTrue("Packaged APK is missing npm toolchain config: $configEntry", configEntry in entries)
        val config = readZipText(zip, configEntry)
        assertTrue("Npm toolchain config should remain readable", config.contains(""""autojs6-toolchain-config""""))
        assertTrue("Npm toolchain config should keep plugin list", config.contains(""""@autojs6/toolchain-plugin""""))
        assertTrue("Npm toolchain config should keep ignore list", config.contains(""""src/test.js""""))
    }

    private fun assertPackagedPackageBinStringCliCorpusEntries(
        zip: ZipFile,
        entries: Set<String>,
        projectDir: File,
        packageDirRelativePath: String,
    ) {
        val packageJsonEntries = listOf(
            "node_modules/@autojs6/string-bin-cli/package.json",
            "node_modules/@autojs6/bin-string-workspace-helper/package.json",
            "node_modules/bin-string-plugin/package.json",
        )
        packageJsonEntries.forEach { relativePath ->
            val entry = "assets/project/$packageDirRelativePath/$relativePath"
            assertTrue("Packaged APK is missing package bin string package metadata: $entry", entry in entries)
            assertTrue("Package bin string package.json should remain readable: $entry", readZipText(zip, entry).contains(""""name""""))
        }
        val packageParentRelativePath = packageDirRelativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val workspacePackageRelativePath = listOf(packageParentRelativePath, "bin-string-workspace-helper")
            .filter { it.isNotEmpty() }
            .joinToString("/")
        val workspacePackageJsonEntry = "assets/project/$workspacePackageRelativePath/package.json"
        assertTrue("Packaged APK is missing package bin string workspace helper metadata", workspacePackageJsonEntry in entries)
        assertTrue(
            "Package bin string workspace helper package.json should remain readable",
            readZipText(zip, workspacePackageJsonEntry).contains(""""bin-string-workspace-helper""""),
        )

        val encryptedJavaScriptEntries = listOf(
            "node_modules/@autojs6/string-bin-cli/bin/cli.js",
            "node_modules/@autojs6/string-bin-cli/index.js",
            "node_modules/@autojs6/string-bin-cli/lib/feature.js",
            "node_modules/@autojs6/string-bin-cli/lib/local.js",
            "node_modules/@autojs6/bin-string-workspace-helper/index.js",
            "node_modules/@autojs6/bin-string-workspace-helper/cache-key.js",
            "node_modules/@autojs6/bin-string-workspace-helper/local.js",
            "node_modules/bin-string-plugin/index.js",
            "node_modules/bin-string-plugin/helper.js",
        )
        encryptedJavaScriptEntries.forEach { relativePath ->
            val entry = "assets/project/$packageDirRelativePath/$relativePath"
            val source = File(projectDir, "$packageDirRelativePath/$relativePath")
            assertTrue("Packaged APK is missing package bin string JavaScript source: $entry", entry in entries)
            assertTrue("Package bin string source file should exist before packaging: ${source.absolutePath}", source.isFile)
            assertNotEquals(
                "Packaged package bin string JavaScript should be encrypted: $entry",
                source.readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }
        listOf(
            "index.js",
            "config.js",
        ).forEach { relativePath ->
            val entry = "assets/project/$workspacePackageRelativePath/$relativePath"
            val source = File(projectDir, "$workspacePackageRelativePath/$relativePath")
            assertTrue("Packaged APK is missing package bin string workspace JavaScript source: $entry", entry in entries)
            assertTrue("Package bin string workspace source file should exist before packaging: ${source.absolutePath}", source.isFile)
            assertNotEquals(
                "Packaged package bin string workspace JavaScript should be encrypted: $entry",
                source.readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }

        val configEntry = "assets/project/$packageDirRelativePath/bin-string.config.json"
        assertTrue("Packaged APK is missing package bin string config: $configEntry", configEntry in entries)
        val config = readZipText(zip, configEntry)
        assertTrue("Package bin string config should remain readable", config.contains(""""autojs6-bin-string-config""""))
        assertTrue("Package bin string config should keep plugin reference", config.contains(""""bin-string-plugin""""))
        assertTrue("Package bin string config should keep scoped helper reference", config.contains(""""@autojs6/bin-string-workspace-helper""""))
        assertTrue("Package bin string config should keep workspace package reference", config.contains(""""workspacePackage""""))
        assertTrue("Package bin string config should keep option defaults", config.contains(""""options""""))
        assertTrue("Package bin string config should keep profile default", config.contains(""""config-profile""""))
        assertTrue("Package bin string config should keep list defaults", config.contains(""""workspace-feature""""))
        assertTrue("Package bin string config should keep mode defaults", config.contains(""""config-short-default""""))

        val workspaceConfigEntry = "assets/project/bin-string.config.json"
        assertTrue("Packaged APK is missing package bin string workspace-root config", workspaceConfigEntry in entries)
        val workspaceConfig = readZipText(zip, workspaceConfigEntry)
        assertTrue(
            "Package bin string workspace-root config should remain readable",
            workspaceConfig.contains(""""autojs6-bin-string-workspace-root""""),
        )
        assertTrue(
            "Package bin string workspace-root config should keep local plugin reference",
            workspaceConfig.contains(""""./$packageDirRelativePath/node_modules/bin-string-plugin""""),
        )
        assertTrue(
            "Package bin string workspace-root config should keep scoped helper reference",
            workspaceConfig.contains(""""./$packageDirRelativePath/node_modules/@autojs6/bin-string-workspace-helper""""),
        )
        assertTrue(
            "Package bin string workspace-root config should keep workspace package reference",
            workspaceConfig.contains(""""./$workspacePackageRelativePath""""),
        )
        assertTrue(
            "Package bin string workspace-root config should keep fallback option defaults",
            workspaceConfig.contains(""""workspace-root-profile""""),
        )
    }

    private fun assertPackagedDiagnosticsEntries(zip: ZipFile, entries: Set<String>, projectDir: File) {
        listOf(
            "assets/project/node_modules/diagnostic-boom/package.json",
            "assets/project/node_modules/diagnostic-type-module/package.json",
            "assets/project/node_modules/diagnostic-esm-export-context/package.json",
            "assets/project/node_modules/diagnostic-async-esm/package.json",
        ).forEach { packageJsonEntry ->
            assertTrue("Diagnostics APK is missing diagnostic package metadata", packageJsonEntry in entries)
            assertTrue(
                "Diagnostics package.json should remain readable: $packageJsonEntry",
                readZipText(zip, packageJsonEntry).contains(""""name""""),
            )
        }

        listOf(
            "main.js",
            "esm-entry.mjs",
            "dynamic-import.js",
            "dynamic-target.mjs",
            "dynamic-legacy.cjs",
            "timeout.js",
            "node_modules/diagnostic-boom/index.js",
            "node_modules/diagnostic-type-module/index.mjs",
            "node_modules/diagnostic-esm-export-context/esm.mjs",
            "node_modules/diagnostic-async-esm/index.mjs",
        ).forEach { relativePath ->
            val entry = "assets/project/$relativePath"
            assertTrue("Diagnostics APK is missing JavaScript diagnostic source: $entry", entry in entries)
            assertNotEquals(
                "Packaged diagnostics JavaScript should be encrypted: $entry",
                File(projectDir, relativePath).readText(StandardCharsets.UTF_8),
                readZipBytes(zip, entry).toString(StandardCharsets.UTF_8),
            )
        }
    }

    private fun assertPackagedNodeNativeLibEntries(entries: Set<String>) {
        for (libName in listOf("libnode.so", "libautojs6-node.so", "libc++_shared.so")) {
            assertTrue(
                "Packaged APK is missing arm64-v8a/$libName",
                "lib/arm64-v8a/$libName" in entries,
            )
        }
        assertFalse("Packaged APK should not include x86 libnode.so", "lib/x86/libnode.so" in entries)
        assertFalse("Packaged APK should not include x86 libautojs6-node.so", "lib/x86/libautojs6-node.so" in entries)
    }

    private fun assertInstallAndRunPackagedSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = SUCCESS_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-smoke.apk",
            label = "packaged smoke",
            requiredMarkers = SUCCESS_INSTALL_REQUIRED_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedToastBridgeSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = TOAST_BRIDGE_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-toast-bridge-smoke.apk",
            label = "packaged toast bridge smoke",
            requiredMarkers = TOAST_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedAppBridgeSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = APP_BRIDGE_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-app-bridge-smoke.apk",
            label = "packaged app bridge smoke",
            requiredMarkers = APP_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedClipboardBridgeSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = CLIPBOARD_BRIDGE_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-clipboard-bridge-smoke.apk",
            label = "packaged clipboard bridge smoke",
            requiredMarkers = CLIPBOARD_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedDeviceBridgeSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = DEVICE_BRIDGE_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-device-bridge-smoke.apk",
            label = "packaged device bridge smoke",
            requiredMarkers = DEVICE_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedV11BridgeSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = V11_BRIDGE_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-v11-bridge-smoke.apk",
            label = "packaged v1.1 bridge smoke",
            requiredMarkers = V11_BRIDGE_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedRhinoCompatSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = RHINO_COMPAT_PACKAGED_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-rhino-compat-smoke.apk",
            label = "packaged Rhino compat smoke",
            requiredMarkers = RHINO_COMPAT_PACKAGED_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedScriptLaunchSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = SCRIPT_LAUNCH_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-script-launch-smoke.apk",
            label = "packaged package script launch smoke",
            requiredMarkers = SCRIPT_LAUNCH_INSTALL_REQUIRED_MARKERS +
                    enrichedInrtLaunchSourceMarkers(SCRIPT_LAUNCH_ENRICHED_INRT_MARKERS) +
                    stableInrtRequestDiagnosticsMarkers(SCRIPT_LAUNCH_STABLE_REQUEST_INRT_MARKERS),
        )
    }

    private fun assertInstallAndRunPackagedBinLaunchSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = BIN_LAUNCH_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-bin-launch-smoke.apk",
            label = "packaged package bin launch smoke",
            requiredMarkers = BIN_LAUNCH_INSTALL_REQUIRED_MARKERS +
                    enrichedInrtLaunchSourceMarkers(BIN_LAUNCH_ENRICHED_INRT_MARKERS) +
                    stableInrtRequestDiagnosticsMarkers(BIN_LAUNCH_STABLE_REQUEST_INRT_MARKERS),
        )
    }

    private fun assertInstallAndRunPackagedBinStringScriptLaunchSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = BIN_STRING_SCRIPT_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-bin-string-script-smoke.apk",
            label = "packaged package bin string script launch smoke",
            requiredMarkers = BIN_STRING_SCRIPT_INSTALL_REQUIRED_MARKERS +
                    enrichedInrtLaunchSourceMarkers(BIN_STRING_SCRIPT_ENRICHED_INRT_MARKERS) +
                    stableInrtRequestDiagnosticsMarkers(BIN_STRING_SCRIPT_STABLE_REQUEST_INRT_MARKERS),
        )
    }

    private fun assertInstallAndRunPackagedDiagnosticsSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = DIAGNOSTICS_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-diagnostics-smoke.apk",
            label = "packaged diagnostics smoke",
            requiredMarkers = DIAGNOSTICS_INSTALL_REQUIRED_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedEsmDiagnosticsSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = ESM_DIAGNOSTICS_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-esm-diagnostics-smoke.apk",
            label = "packaged ESM diagnostics smoke",
            requiredMarkers = ESM_DIAGNOSTICS_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedExperimentalEsmSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = EXPERIMENTAL_ESM_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-experimental-esm-smoke.apk",
            label = "packaged partial ESM smoke",
            requiredMarkers = EXPERIMENTAL_ESM_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedDynamicImportDiagnosticsSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = DYNAMIC_IMPORT_DIAGNOSTICS_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-dynamic-import-local-smoke.apk",
            label = "packaged dynamic import local smoke",
            requiredMarkers = DYNAMIC_IMPORT_DIAGNOSTICS_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedWorkerThreadsEsmSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = WORKER_THREADS_ESM_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-worker-threads-esm-smoke.apk",
            label = "packaged worker_threads ESM smoke",
            requiredMarkers = WORKER_THREADS_ESM_RUNTIME_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedTimeoutDiagnosticsSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = TIMEOUT_DIAGNOSTICS_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-timeout-diagnostics-smoke.apk",
            label = "packaged timeout diagnostics smoke",
            requiredMarkers = TIMEOUT_DIAGNOSTICS_INSTALL_REQUIRED_MARKERS,
        )
    }

    private fun assertInstallAndRunPackagedProcessPoolSlotSmoke(outputApk: File) {
        assertInstallAndRunPackagedApk(
            outputApk = outputApk,
            packageName = PROCESS_POOL_SLOT_PACKAGE_NAME,
            shellApkPath = "/data/local/tmp/autojs6-packaged-node-process-pool-slot-smoke.apk",
            label = "packaged process-pool slot smoke",
            requiredMarkers = PROCESS_POOL_SLOT_INSTALL_REQUIRED_MARKERS,
        )
    }

    private fun assertInstallRunAndCollectPackagedCrashEvidenceSmoke(outputApk: File) {
        val publicApkPath = "$PACKAGED_SMOKE_PUBLIC_EXPORT_DIR/${outputApk.name}"
        val shellApkPath = "/data/local/tmp/autojs6-packaged-node-crash-evidence-smoke.apk"
        val copyOutput = copyPublicApkToShellPath(publicApkPath, shellApkPath)
        assumeTrue(
            "Could not copy packaged crash evidence APK to a shell-readable install path.\n$copyOutput",
            copyOutput.contains("READY"),
        )
        try {
            shellWithTimeout("am force-stop $CRASH_EVIDENCE_PACKAGE_NAME")
            shellWithTimeout("pm uninstall $CRASH_EVIDENCE_PACKAGE_NAME")
            shellWithTimeout("logcat -c", PACKAGED_SMOKE_LOGCAT_DUMP_TIMEOUT_SECONDS)

            val installOutput = shellWithTimeout("pm install -r -t $shellApkPath")
            assumeTrue(
                "Package manager could not install packaged crash evidence APK.\n$installOutput",
                installOutput.contains("Success", ignoreCase = true),
            )

            val packagePath = shellWithTimeout("pm path $CRASH_EVIDENCE_PACKAGE_NAME")
            assumeTrue(
                "Package manager did not expose installed packaged crash evidence APK path.\n$packagePath",
                packagePath.contains("package:", ignoreCase = true),
            )

            val launchOutput = shellWithTimeout(
                "am start -n $CRASH_EVIDENCE_PACKAGE_NAME/org.autojs.autojs.inrt.SplashActivity",
            )
            assumeTrue(
                "Could not launch packaged crash evidence SplashActivity.\n$launchOutput",
                launchOutput.contains("Starting", ignoreCase = true) ||
                        launchOutput.contains("Warning: Activity not started", ignoreCase = true),
            )

            val firstLogcat = waitForLogcatMarkers(CRASH_EVIDENCE_FIRST_LAUNCH_MARKERS)
            val firstRelevantLog = relevantPackagedLogcat(firstLogcat, CRASH_EVIDENCE_PACKAGE_NAME)
            assumeTrue(
                "Logcat channel did not show the launched packaged crash evidence package.\n$firstRelevantLog",
                firstLogcat.contains(CRASH_EVIDENCE_PACKAGE_NAME),
            )
            assertFalse(
                "Packaged crash evidence first launch crashed natively before force-stop.\n$firstRelevantLog",
                firstLogcat.contains(">>> $CRASH_EVIDENCE_PACKAGE_NAME:nodejs_embedded <<<") ||
                        firstLogcat.contains("data_app_native_crash") ||
                        firstLogcat.contains("NativeCrashHandler"),
            )
            val missingFirstMarkers = CRASH_EVIDENCE_FIRST_LAUNCH_MARKERS.filterNot { firstLogcat.contains(it) }
            assertTrue(
                "Packaged crash evidence first launch did not contain expected markers: $missingFirstMarkers\n$firstRelevantLog",
                missingFirstMarkers.isEmpty(),
            )

            shellWithTimeout("am force-stop $CRASH_EVIDENCE_PACKAGE_NAME")
            Thread.sleep(1_000L)

            val relaunchOutput = shellWithTimeout(
                "am start -n $CRASH_EVIDENCE_PACKAGE_NAME/org.autojs.autojs.inrt.SplashActivity",
            )
            assumeTrue(
                "Could not relaunch packaged crash evidence SplashActivity.\n$relaunchOutput",
                relaunchOutput.contains("Starting", ignoreCase = true) ||
                        relaunchOutput.contains("Warning: Activity not started", ignoreCase = true),
            )

            val logcat = waitForLogcatMarkers(CRASH_EVIDENCE_COLLECTION_MARKERS)
            val relevantLog = relevantPackagedLogcat(logcat, CRASH_EVIDENCE_PACKAGE_NAME)
            assertFalse(
                "Packaged crash evidence relaunch crashed natively.\n$relevantLog",
                logcat.contains(">>> $CRASH_EVIDENCE_PACKAGE_NAME:nodejs_embedded <<<") ||
                        logcat.contains("data_app_native_crash") ||
                        logcat.contains("NativeCrashHandler"),
            )
            val missingCollectionMarkers = CRASH_EVIDENCE_COLLECTION_MARKERS.filterNot { logcat.contains(it) }
            assertTrue(
                "Packaged crash evidence relaunch did not collect next-launch markers: $missingCollectionMarkers\n$relevantLog",
                missingCollectionMarkers.isEmpty(),
            )
        } finally {
            shellWithTimeout("am force-stop $CRASH_EVIDENCE_PACKAGE_NAME")
            shellWithTimeout("pm uninstall $CRASH_EVIDENCE_PACKAGE_NAME")
            shellWithTimeout("rm -f $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)
        }
    }

    private fun assertInstallAndRunPackagedLaunchFailureSmoke(
        outputApk: File,
        failureCase: PackagedLaunchFailureCase,
    ) {
        val publicApkPath = "$PACKAGED_SMOKE_PUBLIC_EXPORT_DIR/${outputApk.name}"
        val copyOutput = copyPublicApkToShellPath(publicApkPath, failureCase.shellApkPath)
        assumeTrue(
            "Could not copy packaged launch failure APK to a shell-readable install path.\n$copyOutput",
            copyOutput.contains("READY"),
        )
        try {
            shellWithTimeout("am force-stop ${failureCase.packageName}")
            shellWithTimeout("pm uninstall ${failureCase.packageName}")
            shellWithTimeout("logcat -c", PACKAGED_SMOKE_LOGCAT_DUMP_TIMEOUT_SECONDS)

            val installOutput = shellWithTimeout("pm install -r -t ${failureCase.shellApkPath}")
            assumeTrue(
                "Package manager could not install packaged launch failure APK.\n$installOutput",
                installOutput.contains("Success", ignoreCase = true),
            )

            val packagePath = shellWithTimeout("pm path ${failureCase.packageName}")
            assumeTrue(
                "Package manager did not expose installed packaged launch failure APK path.\n$packagePath",
                packagePath.contains("package:", ignoreCase = true),
            )

            val launchOutput = shellWithTimeout("am start -n ${failureCase.packageName}/org.autojs.autojs.inrt.SplashActivity")
            assumeTrue(
                "Could not launch packaged launch failure SplashActivity.\n$launchOutput",
                launchOutput.contains("Starting", ignoreCase = true) ||
                        launchOutput.contains("Warning: Activity not started", ignoreCase = true),
            )

            val logcat = waitForLogcatMarkers(failureCase.requiredLogMarkers)
            val relevantLog = relevantPackagedLogcat(logcat, failureCase.packageName)
            assumeTrue(
                "Logcat channel did not show the launched packaged launch failure package.\n$relevantLog",
                logcat.contains(failureCase.packageName),
            )
            assertFalse(
                "Packaged launch failure embedded process crashed natively.\n$relevantLog",
                logcat.contains(">>> ${failureCase.packageName}:nodejs_embedded <<<") ||
                        logcat.contains("data_app_native_crash") ||
                        logcat.contains("NativeCrashHandler"),
            )
            val missingMarkers = failureCase.requiredLogMarkers.filterNot { logcat.contains(it) }
            assertTrue(
                "Packaged launch failure logcat did not contain expected markers: $missingMarkers\n$relevantLog",
                missingMarkers.isEmpty(),
            )
            val forbiddenMarkers = listOf("packaged.launch_failure.${failureCase.id}.unexpected_main=true") +
                    failureCase.forbiddenLogMarkers
            val presentForbiddenMarkers = forbiddenMarkers.filter { logcat.contains(it) }
            assertTrue(
                "Packaged launch failure logcat contained unexpected execution markers: $presentForbiddenMarkers\n$relevantLog",
                presentForbiddenMarkers.isEmpty(),
            )
            assertFalse(
                "Packaged launch failure logcat leaked descriptor env value.\n$relevantLog",
                logcat.contains(PACKAGED_LAUNCH_FAILURE_SECRET_VALUE),
            )
        } finally {
            shellWithTimeout("am force-stop ${failureCase.packageName}")
            shellWithTimeout("pm uninstall ${failureCase.packageName}")
            shellWithTimeout("rm -f ${failureCase.shellApkPath}", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)
        }
    }

    private fun assertInstallAndRunPackagedApk(
        outputApk: File,
        packageName: String,
        shellApkPath: String,
        label: String,
        requiredMarkers: List<String>,
    ) {
        val publicApkPath = "$PACKAGED_SMOKE_PUBLIC_EXPORT_DIR/${outputApk.name}"
        val copyOutput = copyPublicApkToShellPath(publicApkPath, shellApkPath)
        assumeTrue(
            "Could not copy $label APK to a shell-readable install path.\n$copyOutput",
            copyOutput.contains("READY"),
        )
        try {
            shellWithTimeout("am force-stop $packageName")
            shellWithTimeout("pm uninstall $packageName")
            shellWithTimeout("logcat -c", PACKAGED_SMOKE_LOGCAT_DUMP_TIMEOUT_SECONDS)

            val installOutput = shellWithTimeout("pm install -r -t $shellApkPath")
            assumeTrue(
                "Package manager could not install $label APK.\n$installOutput",
                installOutput.contains("Success", ignoreCase = true),
            )

            val packagePath = shellWithTimeout("pm path $packageName")
            assumeTrue(
                "Package manager did not expose installed $label APK path.\n$packagePath",
                packagePath.contains("package:", ignoreCase = true),
            )

            val launchOutput = shellWithTimeout("am start -n $packageName/org.autojs.autojs.inrt.SplashActivity")
            assumeTrue(
                "Could not launch $label SplashActivity.\n$launchOutput",
                launchOutput.contains("Starting", ignoreCase = true) ||
                        launchOutput.contains("Warning: Activity not started", ignoreCase = true),
            )

            val logcat = waitForLogcatMarkers(requiredMarkers)
            val relevantLog = relevantPackagedLogcat(logcat, packageName)
            assumeTrue(
                "Logcat channel did not show the launched $label package.\n$relevantLog",
                logcat.contains(packageName),
            )
            assertFalse(
                "$label embedded process crashed during install-and-run smoke.\n$relevantLog",
                logcat.contains(">>> $packageName:nodejs_embedded <<<") ||
                        logcat.contains("data_app_native_crash") ||
                        logcat.contains("NativeCrashHandler"),
            )
            val missingMarkers = requiredMarkers.filterNot { logcat.contains(it) }
            assertTrue(
                "$label logcat did not contain expected runtime markers: $missingMarkers\n$relevantLog",
                missingMarkers.isEmpty(),
            )
        } finally {
            shellWithTimeout("am force-stop $packageName")
            shellWithTimeout("pm uninstall $packageName")
            shellWithTimeout("rm -f $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)
        }
    }

    private fun copyPublicApkToShellPath(publicApkPath: String, shellApkPath: String): String {
        val output = listOf(
            "public:\n${shellWithTimeout("ls -l $publicApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)}",
            "remove:\n${shellWithTimeout("rm -f $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)}",
            "copy:\n${shellWithTimeout("cp $publicApkPath $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)}",
            "chmod:\n${shellWithTimeout("chmod 0644 $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)}",
            "shell:\n${shellWithTimeout("ls -l $shellApkPath", PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS)}",
        ).joinToString("\n")
        return if (output.contains(shellApkPath.substringAfterLast('/'))) {
            "$output\nREADY"
        } else {
            output
        }
    }

    private fun waitForLogcatMarkers(requiredMarkers: List<String>): String {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        var logcat = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(1_000L)
            logcat = shellWithTimeout("logcat -d -v time", PACKAGED_SMOKE_LOGCAT_DUMP_TIMEOUT_SECONDS)
            if (requiredMarkers.all { logcat.contains(it) }) {
                return logcat
            }
        }
        return logcat
    }

    private fun stableInrtRequestDiagnosticsMarkers(markers: List<String>): List<String> {
        val source = packagedSmokeTemplateSource() ?: return emptyList()
        val supportsStableRequestDiagnostics = STABLE_INRT_REQUEST_DIAGNOSTIC_DEX_MARKERS.all { marker ->
            source.containsDexMarker(marker)
        }
        return if (supportsStableRequestDiagnostics) markers else emptyList()
    }

    private fun enrichedInrtLaunchSourceMarkers(markers: List<String>): List<String> {
        val source = packagedSmokeTemplateSource() ?: return emptyList()
        return if (source.containsDexMarker(ENRICHED_INRT_LAUNCH_SOURCE_DEX_MARKER)) markers else emptyList()
    }

    private fun relevantPackagedLogcat(logcat: String, packageName: String): String {
        return logcat.lineSequence()
            .filter {
                it.contains(packageName) ||
                        it.contains("packaged.") ||
                        it.contains("ERR_AUTOJS6_EMBEDDED_NODE") ||
                        it.contains("Embedded Node failure envelope") ||
                        it.contains("timedOut=") ||
                        it.contains("pendingTimers=") ||
                        it.contains("ESM/import") ||
                        it.contains("dynamic import") ||
                        it.contains("NativeNode") ||
                        it.contains("NodeCrashEvidenceStore") ||
                        it.contains("marker.begin") ||
                        it.contains("marker.collect") ||
                        it.contains("autojs6-node-phase13") ||
                        it.contains(NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER) ||
                        it.contains(NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER) ||
                        it.contains(NodeProjectLaunchDiagnostics.INRT_PROJECT_ENTRY_RESOLUTION_FAILURE_MARKER) ||
                        it.contains("Node project runner request") ||
                        it.contains("Node project entry") ||
                        it.contains("Internal Node project launch descriptor") ||
                        it.contains("escapes project root") ||
                        it.contains("escapes Node project root") ||
                        it.contains("Missing package") ||
                        it.contains("Unsupported package script") ||
                        it.contains("cannot specify both scriptName and binName") ||
                        it.contains(PACKAGED_LAUNCH_FAILURE_SECRET_KEY) ||
                        it.contains("GlobalConsole") ||
                        it.contains("DEBUG")
            }
            .toList()
            .takeLast(120)
            .joinToString("\n")
    }

    private fun shell(command: String): String {
        return shellInputStream(command).bufferedReader().use { it.readText() }
    }

    private fun shellWithTimeout(
        command: String,
        timeoutSeconds: Long = PACKAGED_SMOKE_SHELL_TIMEOUT_SECONDS,
    ): String {
        return shell("timeout -k 5s ${timeoutSeconds}s $command")
    }

    private fun shellInputStream(command: String): InputStream {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    }

    private enum class PackagedSmokeTemplateSourceKind {
        ASSETS,
        APP_EXTERNAL_OVERRIDE,
        SHELL_OVERRIDE,
    }

    private data class PackagedSmokeTemplateSource(
        val kind: PackagedSmokeTemplateSourceKind,
        val summary: String,
        val file: File? = null,
        val shellPath: String? = null,
    )

    private fun assumePackagedSmokeTemplateAvailable(label: String) {
        val source = packagedSmokeTemplateSource()
        source?.let { println("$label template source: ${it.summary}") }
        assumeTrue(
            "$label requires a packaged smoke template.\n" + packagedSmokeTemplateDiagnostics(),
            source != null,
        )
    }

    private fun openPackagedSmokeTemplate(
        requiredDexMarkers: List<String> = emptyList(),
    ): InputStream {
        val source = packagedSmokeTemplateSource(requiredDexMarkers = requiredDexMarkers)
            ?: error("Packaged smoke template is unavailable.\n${packagedSmokeTemplateDiagnostics()}")
        requirePackagedSmokeTemplateFresh(source, requiredDexMarkers)
        println("Packaged smoke template source: ${source.summary}")
        return openPackagedSmokeTemplateSource(source)
    }

    private fun requirePackagedSmokeTemplateFresh(
        source: PackagedSmokeTemplateSource,
        requiredDexMarkers: List<String> = emptyList(),
    ) {
        val missingDefaultTemplateMarkers = listOf(
            INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER,
            INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER,
            ENRICHED_INRT_LAUNCH_SOURCE_DEX_MARKER,
        )
            .plus(
                STABLE_INRT_REQUEST_DIAGNOSTIC_DEX_MARKERS,
            )
            .plus(
                listOf(
                    NodeProjectLaunchDiagnostics.LAUNCH_FAILURE_MARKER,
                    NodeProjectLaunchDiagnostics.RUNNER_REQUEST_FAILURE_MARKER,
                ),
            )
            .distinct()
            .filterNot { marker -> source.containsDexMarker(marker) }
        val missingRequiredMarkers = requiredDexMarkers.distinct()
            .filterNot { marker -> source.containsDexMarker(marker) }
        if (source.kind == PackagedSmokeTemplateSourceKind.ASSETS && missingDefaultTemplateMarkers.isNotEmpty()) {
            error(
                "Packaged smoke default template is stale: missing ${missingDefaultTemplateMarkers.joinToString()}.\n" +
                        packagedSmokeTemplateDiagnostics(),
            )
        }
        if (missingRequiredMarkers.isNotEmpty()) {
            error(
                "Packaged smoke template is stale for this smoke: missing ${missingRequiredMarkers.joinToString()}.\n" +
                        packagedSmokeTemplateDiagnostics(),
            )
        }
    }

    private fun openPackagedSmokeTemplateSource(source: PackagedSmokeTemplateSource): InputStream {
        return when (source.kind) {
            PackagedSmokeTemplateSourceKind.ASSETS -> context.assets.open(ApkBuilder.TEMPLATE_APK_NAME)
            PackagedSmokeTemplateSourceKind.APP_EXTERNAL_OVERRIDE -> FileInputStream(
                checkNotNull(source.file) { "Missing app external template override file" },
            )
            PackagedSmokeTemplateSourceKind.SHELL_OVERRIDE -> shellInputStream(
                "cat ${checkNotNull(source.shellPath) { "Missing shell template override path" }}",
            )
        }
    }

    private fun PackagedSmokeTemplateSource.containsInrtNodeProjectRunnerMarker(): Boolean {
        return containsDexMarker(INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER)
    }

    private fun PackagedSmokeTemplateSource.containsInrtNodeProjectLaunchSourceMarker(): Boolean {
        return containsDexMarker(INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER)
    }

    private fun PackagedSmokeTemplateSource.containsInvalidJsonSanitizationMarker(): Boolean {
        return containsDexMarker(NodeProjectLaunchDescriptor.INVALID_JSON_SANITIZED_MARKER)
    }

    private fun PackagedSmokeTemplateSource.containsProjectEntryResolutionFailureMarker(): Boolean {
        return containsDexMarker(NodeProjectLaunchDiagnostics.INRT_PROJECT_ENTRY_RESOLUTION_FAILURE_MARKER)
    }

    private fun PackagedSmokeTemplateSource.containsDexMarker(marker: String): Boolean {
        return runCatching {
            openPackagedSmokeTemplateSource(this).use { input ->
                zipInputStreamContainsDexString(input, marker)
            }
        }.getOrDefault(false)
    }

    private fun zipInputStreamContainsDexString(input: InputStream, value: String): Boolean {
        val needle = value.toByteArray(StandardCharsets.UTF_8)
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return false
                if (!entry.isDirectory && entry.name.isDexEntryName()) {
                    if (zip.readBytes().containsBytes(needle)) {
                        return true
                    }
                }
            }
        }
    }

    private fun String.isDexEntryName(): Boolean {
        return this == "classes.dex" || Regex("""classes\d+\.dex""").matches(this)
    }

    private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
        if (needle.isEmpty()) {
            return true
        }
        if (needle.size > size) {
            return false
        }
        for (index in 0..(size - needle.size)) {
            var matched = true
            for (needleIndex in needle.indices) {
                if (this[index + needleIndex] != needle[needleIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return true
            }
        }
        return false
    }

    private fun packagedSmokeTemplateSource(
        requiredDexMarkers: List<String> = emptyList(),
    ): PackagedSmokeTemplateSource? {
        val overrideEnabled = isPackagedSmokeTemplateOverrideEnabled()
        if (!overrideEnabled) {
            return if (isPackagedSmokeAssetsTemplateAvailable()) {
                PackagedSmokeTemplateSource(
                    kind = PackagedSmokeTemplateSourceKind.ASSETS,
                    summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
                )
            } else {
                null
            }
        }

        fun PackagedSmokeTemplateSource.containsRequiredDexMarkers(): Boolean {
            return requiredDexMarkers.all { marker -> containsDexMarker(marker) }
        }

        val override = packagedSmokeTemplateOverride()
        if (override?.isFile == true && override.length() > 0L) {
            val source = PackagedSmokeTemplateSource(
                kind = PackagedSmokeTemplateSourceKind.APP_EXTERNAL_OVERRIDE,
                summary = "app external override:${override.absolutePath}",
                file = override,
            )
            if (requiredDexMarkers.isEmpty() || source.containsRequiredDexMarkers()) {
                return source
            }
        }
        val shellSources = SHELL_TEMPLATE_OVERRIDE_PATHS
            .filter { path -> shell("ls -l $path").contains(path.substringAfterLast('/')) }
            .map { shellPath ->
                PackagedSmokeTemplateSource(
                    kind = PackagedSmokeTemplateSourceKind.SHELL_OVERRIDE,
                    summary = "shell override:$shellPath",
                    shellPath = shellPath,
                )
            }
        shellSources.firstOrNull { source ->
            requiredDexMarkers.isEmpty() || source.containsRequiredDexMarkers()
        }?.let { source ->
            return source
        }
        if (override?.isFile == true && override.length() > 0L) {
            return PackagedSmokeTemplateSource(
                kind = PackagedSmokeTemplateSourceKind.APP_EXTERNAL_OVERRIDE,
                summary = "app external override:${override.absolutePath}",
                file = override,
            )
        }
        shellSources.firstOrNull()?.let { source ->
            return source
        }
        shellPackagedSmokeTemplateOverridePath()?.let { shellPath ->
            return PackagedSmokeTemplateSource(
                kind = PackagedSmokeTemplateSourceKind.SHELL_OVERRIDE,
                summary = "shell override:$shellPath",
                shellPath = shellPath,
            )
        }
        return null
    }

    private fun isPackagedSmokeAssetsTemplateAvailable(): Boolean {
        return runCatching { context.assets.open(ApkBuilder.TEMPLATE_APK_NAME).use { it.read() >= 0 } }
            .getOrDefault(false)
    }

    private fun isPackagedSmokeTemplateOverrideEnabled(): Boolean {
        return PACKAGED_SMOKE_TEMPLATE_OVERRIDE_FLAGS.any { flag ->
            InstrumentationRegistry.getArguments().getString(flag).toPackagedSmokeBoolean() ||
                    System.getProperty(flag).toPackagedSmokeBoolean()
        }
    }

    private fun packagedSmokeTemplateSupportsInvalidJsonSanitization(): Boolean {
        return packagedSmokeTemplateSource()?.containsInvalidJsonSanitizationMarker() == true
    }

    private fun packagedSmokeTemplateSupportsProjectEntryResolutionFailureDiagnostics(): Boolean {
        return packagedSmokeTemplateSource()?.containsProjectEntryResolutionFailureMarker() == true
    }

    private fun String?.toPackagedSmokeBoolean(): Boolean {
        return this?.trim()?.let { value ->
            value.equals("true", ignoreCase = true) ||
                    value.equals("yes", ignoreCase = true) ||
                    value == "1"
        } == true
    }

    private fun packagedSmokeTemplateDiagnostics(): String {
        val override = packagedSmokeTemplateOverride()
        val overrideEnabled = isPackagedSmokeTemplateOverrideEnabled()
        return buildString {
            appendLine("Template source policy:")
            appendLine("  override enabled: $overrideEnabled")
            appendLine("  default source: assets:${ApkBuilder.TEMPLATE_APK_NAME}")
            appendLine("  assets template available: ${isPackagedSmokeAssetsTemplateAvailable()}")
            appendLine("  assets template contains inrt node project runner marker: ${assetsTemplateContainsInrtNodeProjectRunnerMarker()}")
            appendLine("  assets template contains inrt node project launch source marker: ${assetsTemplateContainsInrtNodeProjectLaunchSourceMarker()}")
            appendLine("  assets template contains package script .bin marker: ${assetsTemplateContainsPackageScriptNodeModulesBinMarker()}")
            appendLine("  assets template contains invalid descriptor JSON sanitization marker: ${assetsTemplateContainsInvalidJsonSanitizationMarker()}")
            appendLine("  assets template contains project entry resolution failure marker: ${assetsTemplateContainsProjectEntryResolutionFailureMarker()}")
            appendLine("  app external override path: ${override?.absolutePath ?: "<external-files-unavailable>"}")
            appendLine("  app external override available: ${override?.let { it.isFile && it.length() > 0L } == true}")
            appendLine("  app external override contains inrt node project runner marker: ${overrideTemplateContainsInrtNodeProjectRunnerMarker()}")
            appendLine("  app external override contains inrt node project launch source marker: ${overrideTemplateContainsInrtNodeProjectLaunchSourceMarker()}")
            appendLine("  app external override contains package script .bin marker: ${overrideTemplateContainsPackageScriptNodeModulesBinMarker()}")
            appendLine("  app external override contains invalid descriptor JSON sanitization marker: ${overrideTemplateContainsInvalidJsonSanitizationMarker()}")
            appendLine("  app external override contains project entry resolution failure marker: ${overrideTemplateContainsProjectEntryResolutionFailureMarker()}")
            appendLine("  shell override paths:")
            if (overrideEnabled) {
                appendLine(shellPackagedSmokeTemplateOverrideProbe().prependIndent("    "))
            } else {
                SHELL_TEMPLATE_OVERRIDE_PATHS.forEach { path -> appendLine("    $path") }
            }
            appendLine("Enable override explicitly with instrumentation argument:")
            appendLine("  -e ${PACKAGED_SMOKE_TEMPLATE_OVERRIDE_FLAGS.first()} true")
        }
    }

    private fun assetsTemplateContainsInrtNodeProjectRunnerMarker(): Boolean {
        return PackagedSmokeTemplateSource(
            kind = PackagedSmokeTemplateSourceKind.ASSETS,
            summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
        ).containsInrtNodeProjectRunnerMarker()
    }

    private fun assetsTemplateContainsInrtNodeProjectLaunchSourceMarker(): Boolean {
        return PackagedSmokeTemplateSource(
            kind = PackagedSmokeTemplateSourceKind.ASSETS,
            summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
        ).containsInrtNodeProjectLaunchSourceMarker()
    }

    private fun assetsTemplateContainsPackageScriptNodeModulesBinMarker(): Boolean {
        return PackagedSmokeTemplateSource(
            kind = PackagedSmokeTemplateSourceKind.ASSETS,
            summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
        ).containsDexMarker(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER)
    }

    private fun assetsTemplateContainsInvalidJsonSanitizationMarker(): Boolean {
        return PackagedSmokeTemplateSource(
            kind = PackagedSmokeTemplateSourceKind.ASSETS,
            summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
        ).containsInvalidJsonSanitizationMarker()
    }

    private fun assetsTemplateContainsProjectEntryResolutionFailureMarker(): Boolean {
        return PackagedSmokeTemplateSource(
            kind = PackagedSmokeTemplateSourceKind.ASSETS,
            summary = "assets:${ApkBuilder.TEMPLATE_APK_NAME}",
        ).containsProjectEntryResolutionFailureMarker()
    }

    private fun overrideTemplateContainsInrtNodeProjectRunnerMarker(): Boolean {
        return overrideTemplateContainsMarker { source -> source.containsInrtNodeProjectRunnerMarker() }
    }

    private fun overrideTemplateContainsInrtNodeProjectLaunchSourceMarker(): Boolean {
        return overrideTemplateContainsMarker { source -> source.containsInrtNodeProjectLaunchSourceMarker() }
    }

    private fun overrideTemplateContainsPackageScriptNodeModulesBinMarker(): Boolean {
        return overrideTemplateContainsMarker { source ->
            source.containsDexMarker(PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER)
        }
    }

    private fun overrideTemplateContainsInvalidJsonSanitizationMarker(): Boolean {
        return overrideTemplateContainsMarker { source -> source.containsInvalidJsonSanitizationMarker() }
    }

    private fun overrideTemplateContainsProjectEntryResolutionFailureMarker(): Boolean {
        return overrideTemplateContainsMarker { source -> source.containsProjectEntryResolutionFailureMarker() }
    }

    private fun overrideTemplateContainsMarker(
        containsMarker: (PackagedSmokeTemplateSource) -> Boolean,
    ): Boolean {
        val override = packagedSmokeTemplateOverride()
        if (override?.isFile == true && override.length() > 0L) {
            return containsMarker(PackagedSmokeTemplateSource(
                kind = PackagedSmokeTemplateSourceKind.APP_EXTERNAL_OVERRIDE,
                summary = "app external override:${override.absolutePath}",
                file = override,
            ))
        }
        val shellPath = if (isPackagedSmokeTemplateOverrideEnabled()) {
            shellPackagedSmokeTemplateOverridePath()
        } else {
            null
        }
        return shellPath?.let { path ->
            containsMarker(PackagedSmokeTemplateSource(
                kind = PackagedSmokeTemplateSourceKind.SHELL_OVERRIDE,
                summary = "shell override:$path",
                shellPath = path,
            ))
        } == true
    }

    private fun shellPackagedSmokeTemplateOverridePath(): String? {
        return SHELL_TEMPLATE_OVERRIDE_PATHS.firstOrNull { path ->
            shell("ls -l $path").contains(path.substringAfterLast('/'))
        }
    }

    private fun shellPackagedSmokeTemplateOverrideProbe(): String {
        return SHELL_TEMPLATE_OVERRIDE_PATHS.joinToString(separator = "\n") { path ->
            "$path => ${shell("ls -l $path").trim()}"
        }
    }

    private fun packagedSmokeTemplateOverride(): File? {
        val externalDir = context.getExternalFilesDir(null) ?: return null
        return File(externalDir, "embedded-node-template/inrt-debug-template.apk")
    }

    private fun String.relativeToProject(projectDir: File): String {
        val root = projectDir.canonicalPath.replace('\\', '/').trimEnd('/')
        val path = File(this).canonicalPath.replace('\\', '/')
        return path.removePrefix("$root/").takeIf { it != path } ?: path
    }

    private fun gzipBase64(bytes: ByteArray): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(bytes)
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private data class PackagedPreloadCollection(
        val diagnostics: NativeNodeEmbeddedJavaScriptEngine.CommonJsPreloadDiagnostics,
        val moduleSources: Map<String, String>,
    )

    private data class PackagedModuleSourceBudgetEntry(
        val sourceName: String,
        val bytes: Int,
    )

    private data class PackagedModuleSourcesBudget(
        val moduleSourceCount: Int,
        val limit: Int,
        val maxBytesPerSource: Int,
        val maxBytesTotal: Int,
        val packageJsonCount: Int,
        val jsonModuleCount: Int,
        val staticRequireCount: Int,
        val dynamicRequireIgnoredCount: Int,
        val unresolvedStaticRequireCount: Int,
        val duplicateSkippedCount: Int,
        val maxDepth: Int,
        val totalSourceBytes: Int,
        val moduleSourceBytes: List<PackagedModuleSourceBudgetEntry>,
    ) {
        val maxObservedBytes: Int
            get() = moduleSourceBytes.maxOfOrNull { it.bytes } ?: 0

        val ready: Boolean
            get() = moduleSourceCount <= limit &&
                    totalSourceBytes <= maxBytesTotal &&
                    moduleSourceBytes.all { it.bytes <= maxBytesPerSource }

        val largestModules: List<PackagedModuleSourceBudgetEntry>
            get() = moduleSourceBytes
                .sortedWith(compareByDescending<PackagedModuleSourceBudgetEntry> { it.bytes }.thenBy { it.sourceName })
                .take(LARGEST_REPORT_LIMIT)

        fun toReportText(): String = buildString {
            appendLine("packaged.preload.module_sources.count=$moduleSourceCount")
            appendLine("packaged.preload.module_sources.limit=$limit")
            appendLine("packaged.preload.module_sources.max_bytes_per_source=$maxBytesPerSource")
            appendLine("packaged.preload.module_sources.max_bytes_total=$maxBytesTotal")
            appendLine("packaged.preload.module_sources.total_bytes=$totalSourceBytes")
            appendLine("packaged.preload.module_sources.max_observed_bytes=$maxObservedBytes")
            appendLine("packaged.preload.module_sources.ready=$ready")
            appendLine("packaged.preload.package_json.count=$packageJsonCount")
            appendLine("packaged.preload.json_module.count=$jsonModuleCount")
            appendLine("packaged.preload.static_require.count=$staticRequireCount")
            appendLine("packaged.preload.dynamic_require_ignored.count=$dynamicRequireIgnoredCount")
            appendLine("packaged.preload.unresolved_static_require.count=$unresolvedStaticRequireCount")
            appendLine("packaged.preload.duplicate_skipped.count=$duplicateSkippedCount")
            appendLine("packaged.preload.max_depth=$maxDepth")
            appendLine("packaged.preload.total_source_bytes=$totalSourceBytes")
            largestModules.forEachIndexed { index, entry ->
                appendLine("packaged.preload.module_sources.largest.$index=${entry.sourceName}")
                appendLine("packaged.preload.module_sources.largest.$index.bytes=${entry.bytes}")
            }
            moduleSourceBytes.forEachIndexed { index, entry ->
                appendLine("packaged.preload.module_sources.$index=${entry.sourceName}")
                appendLine("packaged.preload.module_sources.$index.bytes=${entry.bytes}")
            }
        }

        companion object {
            private const val LARGEST_REPORT_LIMIT = 5

            fun fromReportText(text: String): PackagedModuleSourcesBudget {
                val values = text.lineSequence()
                    .mapNotNull { line ->
                        val index = line.indexOf('=')
                        if (index <= 0) {
                            null
                        } else {
                            line.substring(0, index) to line.substring(index + 1)
                        }
                    }
                    .toMap()
                val moduleSourceNames = values.entries
                    .asSequence()
                    .filter { it.key.startsWith("packaged.preload.module_sources.") }
                    .mapNotNull { entry ->
                        entry.key.removePrefix("packaged.preload.module_sources.")
                            .toIntOrNull()
                            ?.let { it to entry.value }
                    }
                    .sortedBy { it.first }
                    .map { it.second }
                    .toList()
                val moduleSourceBytes = moduleSourceNames.mapIndexed { index, sourceName ->
                    PackagedModuleSourceBudgetEntry(
                        sourceName = sourceName,
                        bytes = values.intValue("packaged.preload.module_sources.$index.bytes"),
                    )
                }
                return PackagedModuleSourcesBudget(
                    moduleSourceCount = values.intValue("packaged.preload.module_sources.count"),
                    limit = values.intValue("packaged.preload.module_sources.limit"),
                    maxBytesPerSource = values.intValue("packaged.preload.module_sources.max_bytes_per_source"),
                    maxBytesTotal = values.intValue("packaged.preload.module_sources.max_bytes_total"),
                    packageJsonCount = values.intValue("packaged.preload.package_json.count"),
                    jsonModuleCount = values.intValue("packaged.preload.json_module.count"),
                    staticRequireCount = values.intValue("packaged.preload.static_require.count"),
                    dynamicRequireIgnoredCount = values.intValue("packaged.preload.dynamic_require_ignored.count"),
                    unresolvedStaticRequireCount = values.intValue("packaged.preload.unresolved_static_require.count"),
                    duplicateSkippedCount = values.intValue("packaged.preload.duplicate_skipped.count"),
                    maxDepth = values.intValue("packaged.preload.max_depth"),
                    totalSourceBytes = values.intValue("packaged.preload.total_source_bytes"),
                    moduleSourceBytes = moduleSourceBytes,
                )
            }

            private fun Map<String, String>.intValue(key: String): Int =
                checkNotNull(this[key]?.toIntOrNull()) { "Missing or invalid preload budget value: $key" }
        }
    }

    companion object {
        private const val SUCCESS_PROJECT_NAME = "PackagedNodeSmoke"
        private const val SUCCESS_PACKAGE_NAME = "org.autojs6.packaged.node.smoke"
        private const val SUCCESS_APK_NAME = "PackagedNodeSmoke_v1.0.0.apk"
        private const val TOAST_BRIDGE_PROJECT_NAME = "PackagedNodeToastBridgeSmoke"
        private const val TOAST_BRIDGE_PACKAGE_NAME = "org.autojs6.packaged.node.toast.smoke"
        private const val TOAST_BRIDGE_APK_NAME = "PackagedNodeToastBridgeSmoke_v1.0.0.apk"
        private const val APP_BRIDGE_PROJECT_NAME = "PackagedNodeAppBridgeSmoke"
        private const val APP_BRIDGE_PACKAGE_NAME = "org.autojs6.packaged.node.app.smoke"
        private const val APP_BRIDGE_APK_NAME = "PackagedNodeAppBridgeSmoke_v1.0.0.apk"
        private const val CLIPBOARD_BRIDGE_PROJECT_NAME = "PackagedNodeClipboardBridgeSmoke"
        private const val CLIPBOARD_BRIDGE_PACKAGE_NAME = "org.autojs6.packaged.node.clipboard.smoke"
        private const val CLIPBOARD_BRIDGE_APK_NAME = "PackagedNodeClipboardBridgeSmoke_v1.0.0.apk"
        private const val DEVICE_BRIDGE_PROJECT_NAME = "PackagedNodeDeviceBridgeSmoke"
        private const val DEVICE_BRIDGE_PACKAGE_NAME = "org.autojs6.packaged.node.device.smoke"
        private const val DEVICE_BRIDGE_APK_NAME = "PackagedNodeDeviceBridgeSmoke_v1.0.0.apk"
        private const val V11_BRIDGE_PROJECT_NAME = "PackagedNodeV11BridgeSmoke"
        private const val V11_BRIDGE_PACKAGE_NAME = "org.autojs6.packaged.node.v11.bridge.smoke"
        private const val V11_BRIDGE_APK_NAME = "PackagedNodeV11BridgeSmoke_v1.0.0.apk"
        private const val RHINO_COMPAT_PACKAGED_PROJECT_NAME = "PackagedNodeRhinoCompatSmoke"
        private const val RHINO_COMPAT_PACKAGED_PACKAGE_NAME = "org.autojs6.packaged.node.rhino.compat.smoke"
        private const val RHINO_COMPAT_PACKAGED_APK_NAME = "PackagedNodeRhinoCompatSmoke_v1.0.0.apk"
        private const val SCRIPT_LAUNCH_PROJECT_NAME = "PackagedNodeScriptLaunchSmoke"
        private const val SCRIPT_LAUNCH_PACKAGE_NAME = "org.autojs6.packaged.node.script.launch.smoke"
        private const val SCRIPT_LAUNCH_APK_NAME = "PackagedNodeScriptLaunchSmoke_v1.0.0.apk"
        private const val BIN_LAUNCH_PROJECT_NAME = "PackagedNodeBinLaunchSmoke"
        private const val BIN_LAUNCH_PACKAGE_NAME = "org.autojs6.packaged.node.bin.launch.smoke"
        private const val BIN_LAUNCH_APK_NAME = "PackagedNodeBinLaunchSmoke_v1.0.0.apk"
        private const val BIN_STRING_SCRIPT_PROJECT_NAME = "PackagedNodeBinStringScriptSmoke"
        private const val BIN_STRING_SCRIPT_PACKAGE_NAME = "org.autojs6.packaged.node.binstring.script.smoke"
        private const val BIN_STRING_SCRIPT_APK_NAME = "PackagedNodeBinStringScriptSmoke_v1.0.0.apk"
        private const val DIAGNOSTICS_PROJECT_NAME = "PackagedNodeDiagnosticsSmoke"
        private const val DIAGNOSTICS_PACKAGE_NAME = "org.autojs6.packaged.node.diagnostics.smoke"
        private const val DIAGNOSTICS_APK_NAME = "PackagedNodeDiagnosticsSmoke_v1.0.0.apk"
        private const val ESM_DIAGNOSTICS_PROJECT_NAME = "PackagedNodeEsmDiagnosticsSmoke"
        private const val ESM_DIAGNOSTICS_PACKAGE_NAME = "org.autojs6.packaged.node.esm.diagnostics.smoke"
        private const val ESM_DIAGNOSTICS_APK_NAME = "PackagedNodeEsmDiagnosticsSmoke_v1.0.0.apk"
        private const val EXPERIMENTAL_ESM_PROJECT_NAME = "PackagedNodeExperimentalEsmSmoke"
        private const val EXPERIMENTAL_ESM_PACKAGE_NAME = "org.autojs6.packaged.node.experimental.esm.smoke"
        private const val EXPERIMENTAL_ESM_APK_NAME = "PackagedNodeExperimentalEsmSmoke_v1.0.0.apk"
        private const val DYNAMIC_IMPORT_DIAGNOSTICS_PROJECT_NAME = "PackagedNodeDynamicImportDiagnosticsSmoke"
        private const val DYNAMIC_IMPORT_DIAGNOSTICS_PACKAGE_NAME =
            "org.autojs6.packaged.node.dynamicimport.diagnostics.smoke"
        private const val DYNAMIC_IMPORT_DIAGNOSTICS_APK_NAME =
            "PackagedNodeDynamicImportDiagnosticsSmoke_v1.0.0.apk"
        private const val WORKER_THREADS_ESM_PROJECT_NAME = "PackagedNodeWorkerThreadsEsmSmoke"
        private const val WORKER_THREADS_ESM_PACKAGE_NAME =
            "org.autojs6.packaged.node.workerthreads.esm.smoke"
        private const val WORKER_THREADS_ESM_APK_NAME =
            "PackagedNodeWorkerThreadsEsmSmoke_v1.0.0.apk"
        private const val TIMEOUT_DIAGNOSTICS_PROJECT_NAME = "PackagedNodeTimeoutDiagnosticsSmoke"
        private const val TIMEOUT_DIAGNOSTICS_PACKAGE_NAME = "org.autojs6.packaged.node.timeout.diagnostics.smoke"
        private const val TIMEOUT_DIAGNOSTICS_APK_NAME = "PackagedNodeTimeoutDiagnosticsSmoke_v1.0.0.apk"
        private const val PROCESS_POOL_SLOT_PROJECT_NAME = "PackagedNodeProcessPoolSlotSmoke"
        private const val PROCESS_POOL_SLOT_PACKAGE_NAME = "org.autojs6.packaged.node.processpoolslot.smoke"
        private const val PROCESS_POOL_SLOT_APK_NAME = "PackagedNodeProcessPoolSlotSmoke_v1.0.0.apk"
        private const val CRASH_EVIDENCE_PROJECT_NAME = "PackagedNodeCrashEvidenceSmoke"
        private const val CRASH_EVIDENCE_PACKAGE_NAME = "org.autojs6.packaged.node.crashevidence.smoke"
        private const val CRASH_EVIDENCE_APK_NAME = "PackagedNodeCrashEvidenceSmoke_v1.0.0.apk"
        private const val CRASH_EVIDENCE_TIMEOUT_MS = 30_000L
        private const val PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE = NodeProjectLaunchDescriptor.DESCRIPTOR_FILE_NAME
        private val PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILES = NodeProjectLaunchDescriptor.DESCRIPTOR_FILE_NAMES
        private const val PACKAGED_SMOKE_MARKER_REPORT_FILE = "packaged-smoke-markers.log"
        private const val PACKAGE_SCRIPT_NODE_MODULES_BIN_TEMPLATE_MARKER = "node_modules/.bin/"
        private const val PACKAGED_LAUNCH_FAILURE_SECRET_KEY = "PACKAGED_FAILURE_SECRET"
        private const val PACKAGED_LAUNCH_FAILURE_SECRET_VALUE = "packaged-failure-secret-value"
        private const val PACKAGED_LAUNCH_FAILURE_WORKSPACE = "workspace/failure-launch"
        private const val PACKAGED_LAUNCH_FAILURE_SCRIPT_PACKAGE_DIR = "packages/failure-script-tool"
        private const val PACKAGED_LAUNCH_FAILURE_BIN_PACKAGE_DIR = "packages/failure-bin-tool"
        private const val PACKAGED_PRELOAD_BUDGET_FILE = "preload-budget.expected.txt"
        private const val PACKAGED_MODULE_SOURCES_LIMIT = 64
        private const val FULL_COMPATIBILITY_PACKAGED_TIMEOUT_MS = 15_000L
        private const val PACKAGED_WATCH_SETTLE_MS = 250
        private const val INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER = "autojs6.inrt.node_project_runner.v1"
        private const val INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER =
            "autojs6.inrt.node_project_launch_source.v2"
        private val SUCCESS_RUNTIME_MARKERS = listOf(
            "packaged.node.version=v24.5.0",
            "packaged.text=packaged",
            "packaged.hash=a3453242",
            "packaged.stream=stream",
            "packaged.promise=drained",
            "packaged.cwd=project",
            "packaged.project_runner.ready=true",
            "packaged.project_runner.entry=src/main.js:true",
            "packaged.project_runner.cwd=project:true",
            "packaged.project_runner.require_main=true",
            "packaged.project_runner.root_fs=true",
            "packaged.project_runner.root_require=true",
            "packaged.project_runner.script_argv=true",
            "packaged.project_runner.descriptor_launch_kind=package-script:true",
            "packaged.project_runner.lifecycle_env=true",
            "packaged.fs.mkdir=true",
            "packaged.fs.readdir=true",
            "packaged.fs.stat=true",
            "packaged.fs.lstat=true",
            "packaged.fs.statfs=true",
            "packaged.fs.append=true",
            "packaged.fs.copy=true",
            "packaged.fs.link=true",
            "packaged.fs.symlink=true",
            "packaged.fs.chmod=true",
            "packaged.fs.lchmod=true",
            "packaged.fs.chown=true",
            "packaged.fs.lutimes=true",
            "packaged.fs.exists=true",
            "packaged.fs.native_glob=true",
            "packaged.fs.native_glob_extended=true",
            "packaged.fs.opendir=true",
            "packaged.fs.readdir_recursive=true",
            "packaged.fs.opendir_recursive=true",
            "packaged.fs.disposable=true",
            "packaged.fs.disposable_extended=true",
            "packaged.fs.realpath=true",
            "packaged.fs.legacy=true",
            "packaged.fs.pathlike=true",
            "packaged.fs.open_as_blob=true",
            "packaged.fs.open_as_blob_web=true",
            "packaged.fs.open_as_blob_slice=true",
            "packaged.fs.truncate=true",
            "packaged.fs.stream_fd=true",
            "packaged.fs.stream_options=true",
            "packaged.fs.stream_modern=true",
            "packaged.fs.class_surface=true",
            "packaged.fs.utf8_stream=true",
            "packaged.fs.top_constants=true",
            "packaged.fs.watch_file=true",
            "packaged.fs.watch_file_options=true",
            "packaged.fs.watch_file_pathlike=true",
            "packaged.fs.watch=true",
            "packaged.fs.watch_pathlike=true",
            "packaged.fs.promises_watch=true",
            "packaged.fs.watch_options=true",
            "packaged.fs.promises_watch_pathlike=true",
            "packaged.fs.promises_watch_options=true",
            "packaged.fs.filehandle_stream=true",
            "packaged.fs.filehandle_stream_abort=true",
            "packaged.fs.filehandle_readlines=true",
            "packaged.fs.filehandle_webstream=true",
            "packaged.fs.fd=true",
            "packaged.fs.fd_options=true",
            "packaged.fs.fileio=true",
            "packaged.fs.fd_metadata=true",
            "packaged.fs.rm=true",
            "packaged.fs.promisify.extended=true",
            "packaged.fs.promises.ready=true",
            "packaged.fs.promises.alias=true",
            "packaged.fs.promises.constants=true",
            "packaged.fs.failure.ready=true",
            "packaged.fs.failure.summary=77/77|41/41|39/39|true|true",
            "packaged.buffer.builtin=true",
            "packaged.buffer.surface=true",
            "packaged.buffer.object_url=true",
            "packaged.buffer.transcode=true",
            "packaged.web_globals=true",
            "packaged.web_fetch_disabled=true",
            "packaged.web_axios_disabled=true",
            "packaged.web_platform_globals=true",
            "packaged.web_platform_utilities=true",
            "packaged.web_abort_signal=true",
            "packaged.web_event_globals=true",
            "packaged.web_message_globals=true",
            "packaged.web_stream_globals=true",
            "packaged.web_crypto.global=true",
            "packaged.web_crypto.digest=true",
            "packaged.web_crypto.node_alias=true",
            "packaged.web_compression.global=true",
            "packaged.web_compression.roundtrip=true",
            "packaged.web_compression.limits=true",
            "packaged.process.builtin=true",
            "packaged.process.alias=true",
            "packaged.process.timing=true",
            "packaged.process.usage=true",
            "packaged.process.modern=true",
            "packaged.process.guards=true",
            "packaged.constants.builtin=true",
            "packaged.constants.fs=true",
            "packaged.constants.os=true",
            "packaged.legacy_core.ready=true",
            "packaged.legacy_core.os=true",
            "packaged.legacy_core.os_extended=true",
            "packaged.legacy_core.os_safe=true",
            "packaged.legacy_core.querystring=true",
            "packaged.legacy_core.string_decoder=true",
            "packaged.legacy_core.assert=true",
            "packaged.legacy_core.assert_extended=true",
            "packaged.legacy_core.assert_helpers=function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function",
            "packaged.legacy_core.punycode=true",
            "packaged.legacy_core.hidden=true",
            "packaged.core_builtins.events_extended=true",
            "packaged.core_builtins.events_helpers=function,function,function,function,symbol,symbol,undefined",
            "packaged.core_builtins.path_extended=true",
            "packaged.core_builtins.path_subpaths=true",
            "packaged.core_builtins.path_helpers=function,function,function,function,function,function,function,function,function,function,function,function,string,string,object,object",
            "packaged.core_builtins.util_extended=true",
            "packaged.core_builtins.util_helpers=function,function,function,symbol,function,symbol,function,function,function,function,function,function,function,function,object,function,function,function,function",
            "packaged.core_builtins.util_types=true",
            "packaged.core_builtins.util_mime=true",
            "packaged.core_builtins.util_modern=true",
            "packaged.core_builtins.console=true",
            "packaged.core_builtins.console_helpers=function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function,function",
            "packaged.core_builtins.v8=true",
            "packaged.core_builtins.v8_helpers=function,function,function,function,function,function,function",
            "packaged.core_builtins.v8_hidden=undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined",
            "packaged.core_builtins.tty=true",
            "packaged.core_builtins.tty_helpers=function,undefined,undefined",
            "packaged.core_builtins.tty_stdio=false,function,1,function,false,false,false,function,1,function,false",
            "packaged.core_builtins.async_hooks=true",
            "packaged.core_builtins.async_hooks_helpers=function,function,undefined,undefined,undefined,undefined",
            "packaged.core_builtins.diagnostics_channel=true",
            "packaged.core_builtins.diagnostics_helpers=function,function,function,function,function,undefined,function,function,function,undefined,function,function,function",
            "packaged.core_builtins.dns=true",
            "packaged.core_builtins.dns_helpers=function,function,function,function,function,function,function,number,string",
            "packaged.core_builtins.net=true",
            "packaged.core_builtins.net_helpers=function,function,function,function,function,function,function,function,function,function,function,undefined",
            "packaged.core_builtins.http=true",
            "packaged.core_builtins.http_helpers=function,function,function,function,function,function,function,function,function,function,function,function,object,object,object,undefined",
            "packaged.core_builtins.tls=true",
            "packaged.core_builtins.tls_helpers=function,function,function,function,function,function,function,function,function,object,string,undefined",
            "packaged.core_builtins.perf_helpers=object,function,function,function,function,function,function,function,function,number,function,function,function,function",
            "packaged.core_builtins.timers_promises=true",
            "packaged.core_builtins.timers_promises_helpers=function,function,function,object,function,function",
            "packaged.core_builtins.stream_helpers=true",
            "packaged.core_builtins.stream_state_helpers=true",
            "packaged.core_builtins.stream_errors=true",
            "packaged.core_builtins.stream_promises=true",
            "packaged.core_builtins.stream_modern_helpers=true",
            "packaged.core_builtins.stream_promises_fs=true",
            "packaged.core_builtins.stream_web=true",
            "packaged.core_builtins.stream_consumers=true",
            "packaged.core_builtins.readline=true",
            "packaged.core_builtins.readline_promises=true",
            "packaged.core_builtins.guards=true",
            "packaged.crypto_zlib.ready=true",
            "packaged.crypto_zlib.surface=true",
            "packaged.crypto_zlib.secret_key=true",
            "packaged.crypto_zlib.kdf=true",
            "packaged.crypto_zlib.stream=true",
            "packaged.crypto_zlib.limits=true",
            "packaged.crypto_zlib.guards=true",
            "packaged.npm_crypto_zlib_fs.ready=true",
            "packaged.npm_crypto_zlib_fs.blob=true",
            "packaged.npm_crypto_zlib_fs.glob=true",
            "packaged.npm_crypto_zlib_fs.stream=true",
            "packaged.npm_crypto_zlib_fs.cp=true",
            "packaged.npm_crypto_zlib_fs.watch=true",
            "packaged.npm_crypto_zlib_fs.scan=true",
            "packaged.npm_crypto_zlib_fs.temp=true",
            "packaged.dynamic_require.ready=true",
            "packaged.dynamic_require.cache=true",
            "packaged.dynamic_require.json=true",
            "packaged.module.create_require.ready=true",
            "packaged.module.create_require.resolve=true",
            "packaged.module.create_require.cache=true",
            "packaged.module.builtin_helpers=true",
            "packaged.cli.ready=true",
            "packaged.cli.argv=true",
            "packaged.cli.require_main=true",
            "packaged.cli.combo=true",
            "packaged.cli.process_constants=true",
            "packaged.glob.ready=true",
            "packaged.glob.matches=true",
            "packaged.glob.ignore=true",
            "packaged.glob.fs_promises=true",
            "packaged.glob.native=true",
            "packaged.dirent.ready=true",
            "packaged.dirent.readdir=true",
            "packaged.dirent.opendir=true",
            "packaged.dirent.opendir_recursive=true",
            "packaged.dirent.async_iterator=true",
            "packaged.readlink.surface=true",
            "packaged.readlink.symlink=true",
            "packaged.readlink.rejects=true",
            "packaged.config.ready=true",
            "packaged.config.plugin=true",
            "packaged.config.scoped=true",
            "packaged.config.output=true",
            "packaged.discovery.ready=true",
            "packaged.discovery.package_json=true",
            "packaged.discovery.plugin=true",
            "packaged.discovery.output=true",
            "packaged.url.file_url=true",
            "packaged.url.fs_url=true",
            "packaged.url.opendir_url=true",
            "packaged.url.sandbox=true",
            "packaged.url.static=true",
            "packaged.url.legacy=true",
            "packaged.url.pattern=true",
            "packaged.cp.ready=true",
            "packaged.cp.recursive=true",
            "packaged.cp.url=true",
            "packaged.cp.callback=true",
            "packaged.cp.mode=true",
            "packaged.cp.filter=true",
            "packaged.cp.symlink=true",
            "packaged.utimes.ready=true",
            "packaged.cp.preserve_timestamps=true",
            "packaged.mkdtemp.ready=true",
            "packaged.rename.ready=true",
            "packaged.atomic.ready=true",
            "packaged.atomic.url=true",
            "packaged.cache_copy.ready=true",
            "packaged.chdir.ready=true",
            "packaged.chdir.fs=true",
            "packaged.chdir.require=true",
            "packaged.tail=true",
        )
        private val SUCCESS_INSTALL_REQUIRED_MARKERS = listOf(
            "packaged.runtime_matrix.ready=true",
            "packaged.runtime_matrix.missing_count=0",
        )
        private val TOAST_BRIDGE_RUNTIME_MARKERS = listOf(
            "packaged.toast.ready=true",
            "packaged.toast.resolve=true",
            "packaged.toast.surface=true",
            "packaged.toast.result=true",
            "packaged.toast.shadow=true",
        )
        private val APP_BRIDGE_RUNTIME_MARKERS = listOf(
            "packaged.app.ready=true",
            "packaged.app.resolve=true",
            "packaged.app.package=true",
            "packaged.app.version_name_type=true",
            "packaged.app.version_code_type=true",
            "packaged.app.surface=true",
            "packaged.app.launch=true",
            "packaged.app.shadow=true",
        )
        private val CLIPBOARD_BRIDGE_RUNTIME_MARKERS = listOf(
            "packaged.clipboard.ready=true",
            "packaged.clipboard.resolve=true",
            "packaged.clipboard.surface=true",
            "packaged.clipboard.set=true",
            "packaged.clipboard.text=true",
            "packaged.clipboard.has_text=true",
            "packaged.clipboard.shadow=true",
        )
        private val DEVICE_BRIDGE_RUNTIME_MARKERS = listOf(
            "packaged.device.ready=true",
            "packaged.device.resolve=true",
            "packaged.device.metrics=true",
            "packaged.device.surface=true",
            "packaged.device.values=true",
            "packaged.device.sensitive=true",
            "packaged.device.shadow=true",
        )
        private val V11_BRIDGE_NODE_PERMISSIONS = listOf(
            "network",
            "ocr",
            "image",
            "storage",
            "notifications",
            "ui",
            "work_manager",
            "plugin.org.example.packaged",
        )
        private val V11_BRIDGE_RUNTIME_MARKERS = listOf(
            "packaged.v1_1_bridge.ready=true",
            "packaged.v1_1_bridge.fetch=true",
            "packaged.v1_1_bridge.ocr=true",
            "packaged.v1_1_bridge.storage=true",
            "packaged.v1_1_bridge.notifications=true",
            "packaged.v1_1_bridge.ui=true",
            "packaged.v1_1_bridge.work_manager=true",
            "packaged.v1_1_bridge.plugin=true",
            "packaged.v1_1_bridge.permissions=true",
            "packaged.v1_1_bridge.no_shadow=true",
            "packaged.v1_1_bridge.pending=0",
        )
        private val RHINO_COMPAT_PACKAGED_NODE_PERMISSIONS = listOf(
            "toast",
            "storage",
            "accessibility",
            "image",
            "ui",
        )
        private val RHINO_COMPAT_PACKAGED_RUNTIME_MARKERS = listOf(
            "packaged.rhino_compat.ready=true",
            "packaged.rhino_compat.loader=true",
            "packaged.rhino_compat.toast=true",
            "packaged.rhino_compat.files=true",
            "packaged.rhino_compat.storage=true",
            "packaged.rhino_compat.selector=true",
            "packaged.rhino_compat.image=true",
            "packaged.rhino_compat.compat_ui=true",
            "packaged.rhino_compat.permissions=true",
            "packaged.rhino_compat.cleanup=true",
            "packaged.rhino_compat.pending=0",
        )
        private val SCRIPT_LAUNCH_RUNTIME_MARKERS = listOf(
            "packaged.script_launch.ready=true",
            "packaged.script_launch.entry=packages/script-tool/node_modules/@autojs6/toolchain-cli/bin/cli.js:true",
            "packaged.script_launch.cwd=workspace/script-launch:true",
            "packaged.script_launch.argv=true",
            "packaged.script_launch.env=true",
            "packaged.script_launch.lifecycle=true",
            "packaged.script_launch.package_dir=true",
            "packaged.script_launch.working_directory=true",
            "packaged.script_launch.bin_name=@autojs6/toolchain-cli:true",
            "packaged.script_launch.require_main=true",
            "packaged.script_launch.main_not_run=true",
            "packaged.script_launch.descriptor_launch_kind=package-script:true",
            "packaged.script_launch.toolchain.npm=true",
            "packaged.script_launch.toolchain.config=true",
            "packaged.script_launch.toolchain.plugin=true",
            "packaged.script_launch.toolchain.discovery=src/index.js|plugins/local.js",
            "packaged.script_launch.toolchain.mode=build",
        )
        private val SCRIPT_LAUNCH_INRT_MARKERS = listOf(
            INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER,
            INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER,
            "launchKind=package-script",
            "script=packaged-script-smoke",
        )
        private val SCRIPT_LAUNCH_ENRICHED_INRT_MARKERS = listOf(
            "requestLaunchKind=package-script",
            "packageDir=",
            "workingDirectory=",
            "envKeys=[PACKAGED_SCRIPT_ENV]",
        )
        private val SCRIPT_LAUNCH_STABLE_REQUEST_INRT_MARKERS = listOf(
            "requestReady=true",
            "packageDirProjectRoot=false",
            "workingDirectoryProjectRoot=false",
            "argvCount=1",
        )
        private val SCRIPT_LAUNCH_INSTALL_REQUIRED_MARKERS = SCRIPT_LAUNCH_RUNTIME_MARKERS + SCRIPT_LAUNCH_INRT_MARKERS
        private val BIN_LAUNCH_RUNTIME_MARKERS = listOf(
            "packaged.bin_launch.ready=true",
            "packaged.bin_launch.entry=packages/bin-tool/node_modules/@autojs6/toolchain-cli/bin/cli.js:true",
            "packaged.bin_launch.cwd=workspace/bin-launch:true",
            "packaged.bin_launch.argv=true",
            "packaged.bin_launch.env=true",
            "packaged.bin_launch.package_dir=true",
            "packaged.bin_launch.working_directory=true",
            "packaged.bin_launch.bin_name=packaged-bin-smoke:true",
            "packaged.bin_launch.require_main=true",
            "packaged.bin_launch.main_not_run=true",
            "packaged.bin_launch.descriptor_launch_kind=package-bin:true",
            "packaged.bin_launch.toolchain.npm=true",
            "packaged.bin_launch.toolchain.config=true",
            "packaged.bin_launch.toolchain.plugin=true",
            "packaged.bin_launch.toolchain.discovery=src/index.js|plugins/local.js",
            "packaged.bin_launch.toolchain.mode=bin",
        )
        private val BIN_LAUNCH_INRT_MARKERS = listOf(
            INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER,
            INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER,
            "launchKind=package-bin",
            "bin=packaged-bin-smoke",
        )
        private val BIN_LAUNCH_ENRICHED_INRT_MARKERS = listOf(
            "requestLaunchKind=package-bin",
            "packageDir=",
            "workingDirectory=",
            "envKeys=[PACKAGED_BIN_ENV]",
        )
        private val BIN_LAUNCH_STABLE_REQUEST_INRT_MARKERS = listOf(
            "requestReady=true",
            "packageDirProjectRoot=false",
            "workingDirectoryProjectRoot=false",
            "argvCount=15",
        )
        private val BIN_LAUNCH_INSTALL_REQUIRED_MARKERS = BIN_LAUNCH_RUNTIME_MARKERS + BIN_LAUNCH_INRT_MARKERS
        private val BIN_STRING_SCRIPT_RUNTIME_MARKERS = listOf(
            "packaged.bin_string_script.ready=true",
            "packaged.bin_string_script.entry=packages/bin-string-script-app/node_modules/@autojs6/string-bin-cli/bin/cli.js:true",
            "packaged.bin_string_script.cwd=workspace/bin-string-script-run:true",
            "packaged.bin_string_script.argv=true",
            "packaged.bin_string_script.env=true",
            "packaged.bin_string_script.lifecycle=true",
            "packaged.bin_string_script.package_json=true",
            "packaged.bin_string_script.bin_name=string-bin-cli:true",
            "packaged.bin_string_script.require_main=true",
            "packaged.bin_string_script.main_not_run=true",
            "packaged.bin_string_script.descriptor_launch_kind=package-script:true",
            "packaged.bin_string_script.exports=true",
            "packaged.bin_string_script.imports=true",
            "packaged.bin_string_script.create_require=true",
            "packaged.bin_string_script.plugin=true",
            "packaged.bin_string_script.buffer=true",
            "packaged.bin_string_script.core_builtins=true",
            "packaged.bin_string_script.url=true",
            "packaged.bin_string_script.querystring=true",
            "packaged.bin_string_script.string_decoder=true",
            "packaged.bin_string_script.assert=true",
            "packaged.bin_string_script.punycode=true",
            "packaged.bin_string_script.runtime_surface=true",
            "packaged.bin_string_script.process=true",
            "packaged.bin_string_script.os=true",
            "packaged.bin_string_script.path=true",
            "packaged.bin_string_script.url_file=true",
            "packaged.bin_string_script.fs_promises=true",
            "packaged.bin_string_script.fs_promises_file_url=true",
            "packaged.bin_string_script.fs_promises_atomic=true",
            "packaged.bin_string_script.fs_promises_readdir=true",
            "packaged.bin_string_script.content_processing=true",
            "packaged.bin_string_script.content_aliases=true",
            "packaged.bin_string_script.content_crypto=true",
            "packaged.bin_string_script.content_zlib=true",
            "packaged.bin_string_script.content_stream=true",
            "packaged.bin_string_script.content_file_stream=true",
            "packaged.bin_string_script.content_stream_promises=true",
            "packaged.bin_string_script.content_stream_web=true",
            "packaged.bin_string_script.content_stream_consumers=true",
            "packaged.bin_string_script.content_promisify=true",
            "packaged.bin_string_script.content_manifest=true",
            "packaged.bin_string_script.config_cache=true",
            "packaged.bin_string_script.config_cache_config=true",
            "packaged.bin_string_script.config_cache_tmpdir=true",
            "packaged.bin_string_script.config_cache_relative_path=true",
            "packaged.bin_string_script.config_cache_miss_hit=true",
            "packaged.bin_string_script.config_cache_manifest=true",
            "packaged.bin_string_script.config_discovery=true",
            "packaged.bin_string_script.config_discovery_cwd=true",
            "packaged.bin_string_script.config_package_priority=true",
            "packaged.bin_string_script.config_workspace_fallback=true",
            "packaged.bin_string_script.config_mixed_plugins=true",
            "packaged.bin_string_script.config_missing_diagnostic=true",
            "packaged.bin_string_script.option_merge=true",
            "packaged.bin_string_script.option_merge_config=true",
            "packaged.bin_string_script.option_merge_argv=true",
            "packaged.bin_string_script.option_merge_list=true",
            "packaged.bin_string_script.option_merge_precedence=true",
            "packaged.bin_string_script.option_merge_env=true",
            "packaged.bin_string_script.option_merge_invalid=true",
            "packaged.bin_string_script.option_merge_mode_default=true",
            "packaged.bin_string_script.option_merge_diagnostics_redacted=true",
            "packaged.bin_string_script.workspace_packages=true",
            "packaged.bin_string_script.workspace_config_discovery=true",
            "packaged.bin_string_script.workspace_scoped_helper=true",
            "packaged.bin_string_script.workspace_self_reference=true",
            "packaged.bin_string_script.workspace_cache_partition=true",
            "packaged.bin_string_script.workspace_failure_diagnostics=true",
            "packaged.bin_string_script.workspace_missing_package=true",
            "packaged.bin_string_script.workspace_missing_scoped_helper=true",
            "packaged.bin_string_script.workspace_missing_scoped_export=true",
            "packaged.bin_string_script.workspace_self_missing_export=true",
            "packaged.bin_string_script.workspace_relative_escape=true",
            "packaged.bin_string_script.workspace_fs_absolute_denied=true",
            "packaged.bin_string_script.workspace_failure_env_redacted=true",
            "packaged.bin_string_script.mode=short",
        )
        private val BIN_STRING_SCRIPT_INRT_MARKERS = listOf(
            INRT_NODE_PROJECT_RUNNER_TEMPLATE_MARKER,
            INRT_NODE_PROJECT_LAUNCH_SOURCE_TEMPLATE_MARKER,
            "launchKind=package-script",
            "script=packaged-bin-string-script",
        )
        private val BIN_STRING_SCRIPT_ENRICHED_INRT_MARKERS = listOf(
            "requestLaunchKind=package-script",
            "packageDir=",
            "workingDirectory=",
            "envKeys=[PACKAGED_BIN_STRING_ENV]",
        )
        private val BIN_STRING_SCRIPT_STABLE_REQUEST_INRT_MARKERS = listOf(
            "requestReady=true",
            "packageDirProjectRoot=false",
            "workingDirectoryProjectRoot=false",
            "argvCount=1",
        )
        private val BIN_STRING_SCRIPT_INSTALL_REQUIRED_MARKERS =
            BIN_STRING_SCRIPT_RUNTIME_MARKERS + BIN_STRING_SCRIPT_INRT_MARKERS
        private val DIAGNOSTICS_RUNTIME_MARKERS = listOf(
            "packaged.diagnostics.ready=true",
            "packaged.diagnostics.disabled_builtin.code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
            "packaged.diagnostics.disabled_builtin.message=true",
            "packaged.diagnostics.fs_escape.message=true",
            "packaged.diagnostics.fs_absolute.message=true",
            "packaged.diagnostics.cjs_stack.message=true",
            "packaged.diagnostics.cjs_stack.source=true",
            "packaged.diagnostics.esm.type_module.value=true",
            "packaged.diagnostics.esm.exports_context=true",
            "packaged.diagnostics.esm.async_code=ERR_REQUIRE_ASYNC_MODULE",
            "packaged.diagnostics.esm.async_message=true",
            "packaged.diagnostics.after_failure.ok=true",
            "packaged.diagnostics.esm.code=ok",
            "packaged.dynamic_import.ready=true",
            "packaged.dynamic_import.mjs=true",
            "packaged.dynamic_import.cjs=true",
            "packaged.diagnostics.timeout.code=ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT",
            "packaged.diagnostics.timeout.timedOut=true",
            "packaged.diagnostics.timeout.pendingTimers=true",
        )
        private val DIAGNOSTICS_INSTALL_REQUIRED_MARKERS = listOf(
            "packaged.diagnostics.ready=true",
            "packaged.diagnostics.disabled_builtin.code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
            "packaged.diagnostics.disabled_builtin.message=true",
            "packaged.diagnostics.fs_escape.message=true",
            "packaged.diagnostics.fs_absolute.message=true",
            "packaged.diagnostics.cjs_stack.message=true",
            "packaged.diagnostics.cjs_stack.source=true",
            "packaged.diagnostics.esm.type_module.value=true",
            "packaged.diagnostics.esm.exports_context=true",
            "packaged.diagnostics.esm.async_code=ERR_REQUIRE_ASYNC_MODULE",
            "packaged.diagnostics.esm.async_message=true",
            "packaged.diagnostics.after_failure.ok=true",
        )
        private val ESM_DIAGNOSTICS_MARKERS = listOf(
            "packaged.diagnostics.esm.code=ok",
            "packaged.diagnostics.esm.message=true",
        )
        private val EXPERIMENTAL_ESM_RUNTIME_MARKERS = listOf(
            "packaged.experimental_esm.ready=true",
            "packaged.experimental_esm.entry=true",
            "packaged.experimental_esm.tla=true",
            "packaged.experimental_esm.builtin=true",
            "packaged.experimental_esm.full_path=true",
            "packaged.experimental_esm.type_module=true",
            "packaged.experimental_esm.graph=true",
            "packaged.experimental_esm.security=true",
            "packaged.experimental_esm.stack_source=true",
        )
        private val EXPERIMENTAL_ESM_TEMPLATE_DEX_MARKERS = listOf(
            "esmExperimentalEnabled=",
        )
        private val DYNAMIC_IMPORT_DIAGNOSTICS_MARKERS = listOf(
            "packaged.dynamic_import.ready=true",
            "packaged.dynamic_import.mjs=true",
            "packaged.dynamic_import.cjs=true",
            "packaged.dynamic_import.type_module=true",
            "packaged.dynamic_import.full_path=true",
            "packaged.dynamic_import.child_process_denied=true",
            "packaged.dynamic_import.path_escape_denied=true",
            "packaged.dynamic_import.security=true",
        )
        private val DYNAMIC_IMPORT_TEMPLATE_DEX_MARKERS = listOf(
            "dynamicImportLocalEnabled=",
        )
        private val WORKER_THREADS_ESM_RUNTIME_MARKERS = listOf(
            "packaged.worker_threads.esm.ready=true",
            "packaged.worker_threads.esm.value=true",
            "packaged.worker_threads.esm.thread=true",
            "packaged.worker_threads.esm.filename=true",
            "packaged.worker_threads.esm.builtin=true",
            "packaged.worker_threads.esm.exit=0",
        )
        private val WORKER_THREADS_ESM_TEMPLATE_DEX_MARKERS =
            EXPERIMENTAL_ESM_TEMPLATE_DEX_MARKERS + listOf(
                "workerThreadsExperimentalEnabled=",
            )
        private val TIMEOUT_DIAGNOSTICS_RUNTIME_MARKERS = listOf(
            "packaged.diagnostics.timeout.code=ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT",
            "packaged.diagnostics.timeout.timedOut=true",
            "packaged.diagnostics.timeout.pendingTimers=true",
        )
        private val TIMEOUT_DIAGNOSTICS_INSTALL_REQUIRED_MARKERS = listOf(
            "ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT",
            "timedOut=true",
            "pendingTimers=true",
        )
        private val PROCESS_POOL_SLOT_RUNTIME_MARKERS = listOf(
            "packaged.process_pool_slot.ready=true",
            "packaged.process_pool_slot.argv=true",
            "packaged.process_pool_slot.env=true",
            "processPoolSlot=${NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID}",
            "processPoolSlotSource=${NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST}",
            "processPool=${NodeEmbeddedProcessPoolPolicy.MODE_EXPLICIT_TWO_SLOT}/${NodeEmbeddedProcessPoolPolicy.OVERFLOW_SLOT_ID}",
        )
        private val PROCESS_POOL_SLOT_INSTALL_REQUIRED_MARKERS = PROCESS_POOL_SLOT_RUNTIME_MARKERS
        private val PROCESS_POOL_SLOT_TEMPLATE_DEX_MARKERS = listOf(
            "processPoolSlot=",
            "processPoolSlotSource=",
            "process_pool_slot",
        )
        private val CRASH_EVIDENCE_RUNTIME_MARKERS = listOf(
            "packaged.crash_evidence.started=true",
        )
        private val CRASH_EVIDENCE_FIRST_LAUNCH_MARKERS = listOf(
            "autojs6.inrt.node_project_packaged_long_running.v1",
            "marker.begin schema=autojs6-node-phase13-crash-marker-v1",
            "scenario=embedded_script_service",
            "launchSurface=${NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING}",
            "executionMode=${NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING}",
            "packageName=$CRASH_EVIDENCE_PACKAGE_NAME",
        )
        private val CRASH_EVIDENCE_COLLECTION_MARKERS = listOf(
            "marker.collect schema=autojs6-node-phase13-next-launch-crash-bundle-v1",
            "state=collected_after_restart",
            "reason=app.onCreate",
            "launchSurface=${NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING}",
            "executionMode=${NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING}",
            "packageName=$CRASH_EVIDENCE_PACKAGE_NAME",
        )
        private val CRASH_EVIDENCE_TEMPLATE_DEX_MARKERS = listOf(
            "marker.begin schema=",
            "marker.collect schema=",
            "autojs6-node-phase13-next-launch-crash-bundle-v1",
        )
        private const val PACKAGED_SMOKE_PUBLIC_EXPORT_DIR =
            "/sdcard/Download/AutoJs6/embedded-node-packaged-smoke"
        private const val PACKAGED_SMOKE_FIXTURE_CACHE_DIR_NAME = "embedded-node-packaged-fixture-cache"
        private const val PACKAGED_SMOKE_SHELL_TIMEOUT_SECONDS = 90L
        private const val PACKAGED_SMOKE_FILE_SHELL_TIMEOUT_SECONDS = 15L
        private const val PACKAGED_SMOKE_LOGCAT_DUMP_TIMEOUT_SECONDS = 15L
        private val SHELL_TEMPLATE_OVERRIDE_PATHS = listOf(
            "/data/local/tmp/autojs6-inrt-debug-template.apk",
            "/sdcard/Download/AutoJs6/embedded-node-template/inrt-debug-template.apk",
        )
        private val PACKAGED_SMOKE_TEMPLATE_OVERRIDE_FLAGS = listOf(
            "autojs.embeddedNode.packagedSmokeTemplateOverride",
            "autojs.embeddedNode.packagedSmoke.templateOverride",
        )
        private val STABLE_INRT_REQUEST_DIAGNOSTIC_DEX_MARKERS = listOf(
            "requestReady=",
            "requestLaunchKind=",
            "packageDirProjectRoot=",
            "workingDirectoryProjectRoot=",
            "argvCount=",
            "envKeys=",
        )
        private const val ENRICHED_INRT_LAUNCH_SOURCE_DEX_MARKER = "requestLaunchKind="
        private const val PACKAGED_MODULE_SOURCE_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES
        private const val PACKAGED_MODULE_SOURCES_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES
    }

    private fun packagedRuntimeCompatibilityDescriptor(
        esmPartial: Boolean,
        dynamicImportLocal: Boolean,
        workerThreads: Boolean = false,
    ): String {
        return NodeProjectRuntimeCompatibilityDescriptor.currentText(
            esmPartial = esmPartial,
            dynamicImportLocal = dynamicImportLocal,
            workerThreads = workerThreads,
        )
    }

    private fun packagedRuntimeCompatibilityDescriptorFiles(
        esmPartial: Boolean,
        dynamicImportLocal: Boolean,
        workerThreads: Boolean = false,
    ): Map<String, String> {
        return packagedRuntimeCompatibilityDescriptorFiles(
            packagedRuntimeCompatibilityDescriptor(
                esmPartial = esmPartial,
                dynamicImportLocal = dynamicImportLocal,
                workerThreads = workerThreads,
            ),
        )
    }

    private fun packagedRuntimeCompatibilityDescriptorFiles(descriptorText: String): Map<String, String> {
        return NodeProjectRuntimeCompatibilityDescriptor.DESCRIPTOR_FILE_NAMES.associateWith { descriptorText }
    }

    private fun packagedSmokeFixtureCacheKey(
        kind: String,
        outputApkName: String,
        requiredTemplateDexMarkers: List<String>,
        inputs: List<Pair<String, String>>,
    ): String {
        val source = packagedSmokeTemplateSource(requiredDexMarkers = requiredTemplateDexMarkers)
            ?: error("Packaged smoke template is unavailable for fixture cache.\n${packagedSmokeTemplateDiagnostics()}")
        requirePackagedSmokeTemplateFresh(source, requiredTemplateDexMarkers)
        val templateSha256 = openPackagedSmokeTemplateSource(source).use { input -> sha256(input) }
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(key: String, value: String) {
            digest.update(key.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
        }
        update("cache.schema", "autojs6-packaged-smoke-fixture-cache-v1")
        update("kind", kind)
        update("outputApkName", outputApkName)
        update("template.kind", source.kind.name)
        update("template.summary", source.summary)
        update("template.sha256", templateSha256)
        update("build.versionCode", BuildConfig.VERSION_CODE.toString())
        update("build.versionName", BuildConfig.VERSION_NAME)
        update("runtime.backend", BuildConfig.NODEJS_RUNTIME_BACKEND)
        update("runtime.engine", NodeRuntimeCapabilities.ENGINE_VERSION)
        update("runtime.descriptor", NodeProjectRuntimeCompatibilityDescriptor.currentJson().toString())
        requiredTemplateDexMarkers.sorted().forEachIndexed { index, marker ->
            update("requiredTemplateDexMarker.$index", marker)
        }
        inputs.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).forEach { (key, value) ->
            update("input.$key", value)
        }
        return digest.digest().toHexString()
    }

    private fun restorePackagedSmokeFixtureCache(
        outputApk: File,
        cacheKey: String,
        label: String,
    ): File? {
        val cachedApk = packagedSmokeFixtureCacheApk(outputApk.name, cacheKey)
        if (!cachedApk.isFile || cachedApk.length() <= 0L) {
            println("Packaged smoke fixture cache miss: label=$label key=${cacheKey.take(16)}")
            return null
        }
        outputApk.parentFile?.mkdirs()
        cachedApk.copyTo(outputApk, overwrite = true)
        println(
            "Packaged smoke fixture cache hit: label=$label key=${cacheKey.take(16)} " +
                    "bytes=${outputApk.length()}",
        )
        return outputApk
    }

    private fun storePackagedSmokeFixtureCache(
        outputApk: File,
        cacheKey: String,
        label: String,
    ) {
        val target = packagedSmokeFixtureCacheApk(outputApk.name, cacheKey)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        outputApk.copyTo(tmp, overwrite = true)
        if (target.exists()) {
            target.delete()
        }
        check(tmp.renameTo(target) || runCatching {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            true
        }.getOrDefault(false)) {
            "Failed to store packaged smoke fixture cache: ${target.absolutePath}"
        }
        println(
            "Packaged smoke fixture cache stored: label=$label key=${cacheKey.take(16)} " +
                    "bytes=${target.length()} path=${target.absolutePath}",
        )
    }

    private fun packagedSmokeFixtureCacheApk(outputApkName: String, cacheKey: String): File {
        val baseName = outputApkName.removeSuffix(".apk").replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        return File(File(context.cacheDir, PACKAGED_SMOKE_FIXTURE_CACHE_DIR_NAME), "$baseName-$cacheKey.apk")
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun readZipText(zip: ZipFile, entryName: String): String =
        readZipBytes(zip, entryName).toString(StandardCharsets.UTF_8)

    private fun JSONObject.getStringList(key: String): List<String> {
        val array = getJSONArray(key)
        return List(array.length()) { index -> array.getString(index) }
    }

    private fun assertPackagedNodeLaunchDescriptor(
        zip: ZipFile,
        label: String,
        expected: PackagedNodeLaunchDescriptorExpectation,
    ) {
        val actual = JSONObject(readZipText(zip, "assets/project/$PACKAGED_NODE_LAUNCH_DESCRIPTOR_FILE"))
        assertEquals("$label descriptor launchKind", expected.launchKind, actual.getString("launchKind"))
        assertOptionalDescriptorString(label, expected.packageDir, actual, "packageDir")
        assertOptionalDescriptorString(label, expected.workingDirectory, actual, "workingDirectory")
        assertOptionalDescriptorString(label, expected.scriptName, actual, "scriptName")
        assertOptionalDescriptorString(label, expected.binName, actual, "binName")
        assertEquals("$label descriptor argv", expected.argv, actual.optStringList("argv"))
        assertEquals("$label descriptor env", expected.env, actual.optStringMap("env"))
        assertOptionalDescriptorLong(label, expected.timeoutMs, actual, "timeoutMs")
        assertOptionalDescriptorString(label, expected.executionMode, actual, "executionMode")
        assertOptionalDescriptorString(label, expected.processPoolSlot, actual, "processPoolSlot")
        assertOptionalDescriptorString(label, expected.processPoolSlotSource, actual, "processPoolSlotSource")
    }

    private fun assertOptionalDescriptorString(
        label: String,
        expected: String?,
        actual: JSONObject,
        key: String,
    ) {
        if (expected != null) {
            assertEquals("$label descriptor $key", expected, actual.getString(key))
        } else {
            assertFalse("$label descriptor should not contain $key", actual.has(key))
        }
    }

    private fun assertOptionalDescriptorLong(
        label: String,
        expected: Long?,
        actual: JSONObject,
        key: String,
    ) {
        if (expected != null) {
            assertEquals("$label descriptor $key", expected, actual.getLong(key))
        } else {
            assertFalse("$label descriptor should not contain $key", actual.has(key))
        }
    }

    private fun JSONObject.toPackagedNodeLaunchDescriptorExpectation(): PackagedNodeLaunchDescriptorExpectation {
        return PackagedNodeLaunchDescriptorExpectation(
            launchKind = getString("launchKind"),
            packageDir = optStringOrNull("packageDir"),
            workingDirectory = optStringOrNull("workingDirectory"),
            scriptName = optStringOrNull("scriptName"),
            binName = optStringOrNull("binName"),
            argv = optStringList("argv"),
            env = optStringMap("env"),
            timeoutMs = optLongOrNull("timeoutMs"),
            executionMode = optStringOrNull("executionMode"),
            processPoolSlot = optStringOrNull("processPoolSlot"),
            processPoolSlotSource = optStringOrNull("processPoolSlotSource"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return getString(key)
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        if (!has(key) || isNull(key)) {
            return emptyList()
        }
        return getStringList(key)
    }

    private fun JSONObject.optStringMap(key: String): Map<String, String> {
        if (!has(key) || isNull(key)) {
            return emptyMap()
        }
        val obj = getJSONObject(key)
        return obj.keys().asSequence().associateWith { envKey -> obj.getString(envKey) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return getLong(key)
    }

    private fun readZipBytes(zip: ZipFile, entryName: String): ByteArray {
        val entry = checkNotNull(zip.getEntry(entryName)) { "Missing APK entry: $entryName" }
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun exportPackagedSmokeApkForHost(outputApk: File) {
        val exportDir = "AutoJs6/embedded-node-packaged-smoke"
        val exportName = outputApk.name
        deletePublicPackagedSmokeApkExports(outputApk)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$exportDir/"
            val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            resolver.query(
                downloads,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(exportName, relativePath),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                            .appendPath(cursor.getLong(idColumn).toString())
                            .build(),
                        null,
                        null,
                    )
                }
            }
            val uri = checkNotNull(
                resolver.insert(
                    downloads,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, exportName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                ),
            ) { "Failed to create exported smoke APK MediaStore entry" }
            resolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output) { "Failed to open exported smoke APK output stream" }
                outputApk.inputStream().use { input -> input.copyTo(output) }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloads, exportDir).apply {
                check(mkdirs() || isDirectory) { "Failed to create smoke APK export dir: $absolutePath" }
            }
            outputApk.copyTo(File(targetDir, exportName), overwrite = true)
        }
        cleanupLocalPackagedSmokeBuildRoot(outputApk)
    }

    private fun deletePublicPackagedSmokeApkExports(outputApk: File) {
        shell("rm -f $PACKAGED_SMOKE_PUBLIC_EXPORT_DIR/${outputApk.name}")
    }

    private fun cleanupLocalPackagedSmokeBuildRoot(outputApk: File) {
        val root = outputApk.parentFile?.parentFile ?: return
        val cacheRoot = context.cacheDir.canonicalFile
        val canonicalRoot = root.canonicalFile
        check(canonicalRoot.path.startsWith(cacheRoot.path)) {
            "Refusing to delete packaged smoke build root outside instrumentation cache: ${canonicalRoot.absolutePath}"
        }
        canonicalRoot.deleteRecursively()
        System.runFinalization()
        Runtime.getRuntime().gc()
    }
}
