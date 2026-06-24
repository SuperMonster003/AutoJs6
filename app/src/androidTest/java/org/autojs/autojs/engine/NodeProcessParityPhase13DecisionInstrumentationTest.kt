package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeProcessParityPhase13DecisionInstrumentationTest {

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
    fun processParityProfileRecordsSafeSubsetAndStableDenials() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-44 process/env/argv parity decision")

        val workDir = File(context.cacheDir, "node-process-p13-44-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-44 process work dir: $absolutePath" }
        }
        File(workDir, "sub").mkdirs()

        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const assert = require("assert/strict");
                    const processBuiltin = require("process");
                    const nodeProcessBuiltin = require("node:process");
                    const profile = require("autojs6:profile");
                    const processProfile = profile.processParityProfile || {};

                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    function denied(action) {
                      try {
                        action();
                        return "allowed";
                      } catch (error) {
                        return codeOf(error);
                      }
                    }

                    const profileOk =
                      processProfile.status === "safe_process_subset_partial" &&
                      processProfile.defaultEnabled === true &&
                      Array.isArray(processProfile.targetProfiles) &&
                      processProfile.targetProfiles.join(",") === "safe_default,desktop_compat_opt_in" &&
                      processProfile.argv === "entry_and_project_runner_partial" &&
                      processProfile.env === "controlled_string_map" &&
                      processProfile.envMutation === "script_local_mutable" &&
                      processProfile.envSources === "request_map_plus_safe_defaults" &&
                      processProfile.envDefaultKeys.join(",") === "NODE_ENV,AUTOJS6_EMBEDDED_NODE,AUTOJS6_NODE_RUNTIME" &&
                      processProfile.envFilteredKeys.join(",") === "NODE_OPTIONS,NODE_INSPECT_RESUME_ON_START,NODE_COMPILE_CACHE,NODE_DISABLE_COMPILE_CACHE,AUTOJS6_NODE_COMPILE_CACHE_DIR,AUTOJS6_NODE_COMPILE_CACHE_KEY,AUTOJS6_NODE_COMPILE_CACHE_RUNTIME_KEY,AUTOJS6_NODE_COMPILE_CACHE_PROJECT_KEY,AUTOJS6_NODE_COMPILE_CACHE_LOADER" &&
                      processProfile.cwd === "scoped_working_directory" &&
                      processProfile.chdir === "scoped_relative_directory_only" &&
                      processProfile.exit === "exitCode_only_process_exit_denied" &&
                      processProfile.signals === "denied_no_android_signal_delivery" &&
                      processProfile.versions === "partial_node_v8_available" &&
                      processProfile.resourceUsage === "partial_runtime_snapshot" &&
                      processProfile.timing === "partial_uptime_hrtime_cpu_memory" &&
                      processProfile.permission === "autojs6_readonly_capability_query" &&
                      processProfile.report === "getReport_only_write_denied" &&
                      processProfile.stdio === "bounded_non_tty" &&
                      processProfile.moduleAliases.join(",") === "process,node:process" &&
                      processProfile.androidDifferences === "documented_stable" &&
                      processProfile.androidUnsupported.join(",") === "process.kill,process.abort,process.exit,signals,privilege_posix_apis,raw_process_binding,raw_dlopen,report.writeReport,unrestricted_chdir" &&
                      processProfile.packagedBehavior === "existing_project_runner_smokes_partial" &&
                      processProfile.rawDesktopParity === false &&
                      processProfile.rawEnvInheritance === false &&
                      processProfile.rawSignals === false &&
                      processProfile.rawProcessHandles === false &&
                      processProfile.nativeBindings === false &&
                      Object.isFrozen(processProfile) &&
                      Object.isFrozen(processProfile.targetProfiles) &&
                      Object.isFrozen(processProfile.envDefaultKeys) &&
                      Object.isFrozen(processProfile.envFilteredKeys) &&
                      Object.isFrozen(processProfile.moduleAliases) &&
                      Object.isFrozen(processProfile.androidUnsupported);

                    process.env.P13_PROCESS_MUTABLE = "changed";
                    delete process.env.P13_PROCESS_VISIBLE;

                    const argvOk =
                      processBuiltin === process &&
                      nodeProcessBuiltin === process &&
                      Array.isArray(process.argv) &&
                      process.argv[0] === "autojs6-node" &&
                      String(process.argv[1]).includes("node-process-parity-phase13-p13-44") &&
                      Array.isArray(process.execArgv) &&
                      process.execArgv.length === 0 &&
                      typeof process.argv0 === "string" &&
                      process.argv0.length > 0 &&
                      process.title === "autojs6-node";

                    const envOk =
                      process.env.NODE_ENV === "production" &&
                      process.env.AUTOJS6_EMBEDDED_NODE === "1" &&
                      process.env.AUTOJS6_NODE_RUNTIME === "embedded" &&
                      process.env.P13_PROCESS_EMPTY === "" &&
                      process.env.P13_PROCESS_MUTABLE === "changed" &&
                      process.env.P13_PROCESS_VISIBLE === undefined &&
                      process.env.NODE_OPTIONS === undefined &&
                      process.env.NODE_INSPECT_RESUME_ON_START === undefined &&
                      process.env.NODE_COMPILE_CACHE === undefined &&
                      process.env.NODE_DISABLE_COMPILE_CACHE === undefined &&
                      process.env.AUTOJS6_NODE_COMPILE_CACHE_DIR === undefined &&
                      process.env.AUTOJS6_NODE_COMPILE_CACHE_KEY === undefined;

                    const cwdBefore = process.cwd();
                    process.chdir("sub");
                    const cwdSub = process.cwd();
                    process.chdir(".");
                    const cwdAfterDot = process.cwd();
                    process.chdir("..");
                    const cwdBack = process.cwd();
                    const cwdDeniedAbsolute = denied(() => process.chdir("/"));
                    const cwdDeniedEscape = denied(() => process.chdir("../escape"));
                    const cwdOk =
                      cwdBefore.replace(/\\/g, "/").endsWith("${workDir.name}") &&
                      cwdSub.replace(/\\/g, "/").endsWith("${workDir.name}/sub") &&
                      cwdAfterDot === cwdSub &&
                      cwdBack === cwdBefore &&
                      cwdDeniedAbsolute === "EPERM" &&
                      cwdDeniedEscape === "EPERM";

                    process.exitCode = 44;
                    const exitOk =
                      process.exitCode === 44 &&
                      denied(() => process.exit(2)) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process.abort()) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process.kill(process.pid, 0)) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process.reallyExit(2)) === "ERR_AUTOJS6_PROCESS_API_DISABLED";

                    const cpuBefore = process.cpuUsage();
                    const cpuDelta = process.cpuUsage(cpuBefore);
                    const resource = process.resourceUsage();
                    const memory = process.memoryUsage();
                    const hr = process.hrtime();
                    const hrDelta = process.hrtime(hr);
                    const timingOk =
                      typeof process.uptime() === "number" &&
                      Array.isArray(hr) &&
                      hr.length === 2 &&
                      Array.isArray(hrDelta) &&
                      typeof process.hrtime.bigint() === "bigint" &&
                      typeof cpuDelta.user === "number" &&
                      typeof cpuDelta.system === "number" &&
                      typeof resource.userCPUTime === "number" &&
                      typeof resource.systemCPUTime === "number" &&
                      typeof resource.maxRSS === "number" &&
                      typeof resource.signalsCount === "number" &&
                      typeof memory.rss === "number" &&
                      typeof memory.heapUsed === "number";

                    const versionsOk =
                      typeof process.version === "string" &&
                      typeof process.versions === "object" &&
                      typeof process.versions.node === "string" &&
                      typeof process.versions.v8 === "string" &&
                      typeof process.release === "object" &&
                      process.release.name === "node" &&
                      typeof process.platform === "string" &&
                      typeof process.arch === "string" &&
                      typeof process.pid === "number" &&
                      typeof process.ppid === "number";

                    const permissionQuery = process.permission.query();
                    const permissionOk =
                      process.permission.model === "autojs6-readonly-capability-query" &&
                      process.permission.exactNodePermissionModel === false &&
                      process.permission.has("fs.read", "sub") === true &&
                      process.permission.has("fs.read", "../escape") === false &&
                      Object.isFrozen(process.permission) &&
                      Object.isFrozen(permissionQuery) &&
                      permissionQuery.scopes["fs.read"] === true &&
                      permissionQuery.scopes.network === false;

                    const report = process.report.getReport(new Error("p13"));
                    const reportOk =
                      process.report.reportOnSignal === false &&
                      process.report.excludeNetwork === true &&
                      report.header.commandLine[0] === "autojs6-node" &&
                      report.header.cwd === process.cwd() &&
                      report.environmentVariables.P13_PROCESS_MUTABLE === "changed" &&
                      denied(() => process.report.writeReport()) === "ERR_AUTOJS6_PROCESS_API_DISABLED";

                    const stdioOk =
                      process.stdin.isTTY === false &&
                      process.stdin.read() === null &&
                      process.stdout.isTTY === false &&
                      process.stderr.isTTY === false &&
                      process.stdout.hasColors() === false &&
                      process.stderr.getColorDepth() === 1;

                    const dangerousOk =
                      denied(() => process.binding("fs")) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process._linkedBinding("fs")) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process.dlopen({}, "native.node")) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      denied(() => process.getBuiltinModule("fs")) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      typeof process.umask() === "number" &&
                      denied(() => process.umask(0)) === "ERR_AUTOJS6_PROCESS_API_DISABLED" &&
                      process.allowedNodeEnvironmentFlags instanceof Set &&
                      process.allowedNodeEnvironmentFlags.size === 0 &&
                      process.allowedNodeEnvironmentFlags.has("--inspect") === false &&
                      process.allowedNodeEnvironmentFlags.add("--inspect") === process.allowedNodeEnvironmentFlags &&
                      process.allowedNodeEnvironmentFlags.size === 0 &&
                      process.features.inspector === false &&
                      process.features.require_module === false &&
                      process.features.uv === true;

                    console.log("p13process.profile=" + [
                      processProfile.status,
                      processProfile.defaultEnabled,
                      processProfile.targetProfiles.join(","),
                      processProfile.argv,
                      processProfile.env,
                      processProfile.envMutation,
                      processProfile.envSources,
                      processProfile.envDefaultKeys.join(","),
                      processProfile.envFilteredKeys.join(","),
                      processProfile.cwd,
                      processProfile.chdir,
                      processProfile.exit,
                      processProfile.signals,
                      processProfile.versions,
                      processProfile.resourceUsage,
                      processProfile.timing,
                      processProfile.permission,
                      processProfile.report,
                      processProfile.stdio,
                      processProfile.moduleAliases.join(","),
                      processProfile.androidDifferences,
                      processProfile.androidUnsupported.join(","),
                      processProfile.packagedBehavior,
                      processProfile.rawDesktopParity,
                      processProfile.rawEnvInheritance,
                      processProfile.rawSignals,
                      processProfile.rawProcessHandles,
                      processProfile.nativeBindings,
                      Object.isFrozen(processProfile),
                      Object.isFrozen(processProfile.targetProfiles),
                      Object.isFrozen(processProfile.envDefaultKeys),
                      Object.isFrozen(processProfile.envFilteredKeys),
                      Object.isFrozen(processProfile.moduleAliases),
                      Object.isFrozen(processProfile.androidUnsupported)
                    ].join("|"));
                    console.log("p13process.behavior=" + [
                      profileOk,
                      argvOk,
                      envOk,
                      cwdOk,
                      exitOk,
                      timingOk,
                      versionsOk,
                      permissionOk,
                      reportOk,
                      stdioOk,
                      dangerousOk,
                      process.exitCode
                    ].join("|"));
                """.trimIndent(),
                sourceName = "<node-process-parity-phase13-p13-44.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                env = mapOf(
                    "P13_PROCESS_VISIBLE" to "yes",
                    "P13_PROCESS_EMPTY" to "",
                    "NODE_OPTIONS" to "--inspect=0.0.0.0:9229",
                    "NODE_INSPECT_RESUME_ON_START" to "1",
                    "NODE_COMPILE_CACHE" to "/tmp/unsafe",
                    "NODE_DISABLE_COMPILE_CACHE" to "1",
                    "AUTOJS6_NODE_COMPILE_CACHE_DIR" to "/tmp/unsafe",
                    "AUTOJS6_NODE_COMPILE_CACHE_KEY" to "unsafe",
                ),
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-44 process/env/argv parity", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13process.profile=safe_process_subset_partial|true|safe_default,desktop_compat_opt_in|" +
                        "entry_and_project_runner_partial|controlled_string_map|script_local_mutable|" +
                        "request_map_plus_safe_defaults|NODE_ENV,AUTOJS6_EMBEDDED_NODE,AUTOJS6_NODE_RUNTIME|" +
                        "NODE_OPTIONS,NODE_INSPECT_RESUME_ON_START,NODE_COMPILE_CACHE,NODE_DISABLE_COMPILE_CACHE," +
                        "AUTOJS6_NODE_COMPILE_CACHE_DIR,AUTOJS6_NODE_COMPILE_CACHE_KEY," +
                        "AUTOJS6_NODE_COMPILE_CACHE_RUNTIME_KEY,AUTOJS6_NODE_COMPILE_CACHE_PROJECT_KEY," +
                        "AUTOJS6_NODE_COMPILE_CACHE_LOADER|scoped_working_directory|scoped_relative_directory_only|" +
                        "exitCode_only_process_exit_denied|denied_no_android_signal_delivery|partial_node_v8_available|" +
                        "partial_runtime_snapshot|partial_uptime_hrtime_cpu_memory|autojs6_readonly_capability_query|" +
                        "getReport_only_write_denied|bounded_non_tty|process,node:process|documented_stable|" +
                        "process.kill,process.abort,process.exit,signals,privilege_posix_apis,raw_process_binding," +
                        "raw_dlopen,report.writeReport,unrestricted_chdir|existing_project_runner_smokes_partial|" +
                        "false|false|false|false|false|true|true|true|true|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13process.behavior=true|true|true|true|true|true|true|true|true|true|true|44"),
            )
            assertTrue(
                "process.exitCode must stay lifecycle-local and not fail the host result\n${result.stdout}",
                result.exitCode == 0,
            )
        } finally {
            workDir.deleteRecursively()
        }
    }
}
