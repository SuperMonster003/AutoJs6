#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-native-addon-loading-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  runtimeCapabilities: "app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13NativeAddonTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPhase13DecisionInstrumentationTest.kt",
  nativeAddonPolicyTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeNativeAddonPolicyInstrumentationTest.kt",
  processSweepTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeProcessSweepInstrumentationTest.kt",
  securityRegressionTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  packagedSmokeTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  projectAnalyzer: "tools/nodejs/project/autojs6-node-project.js",
  dependencyManager: "tools/nodejs/phase10/autojs6-pm.js",
  pluginPolicy: "tools/nodejs/plugin/verify-phase9-plugin-policy.js",
  phase9PluginPolicy: "docs/nodejs/reports/phase9/PLUGIN_POLICY.md",
  phase9ChaosSecurity: "docs/nodejs/reports/phase9/CHAOS_SECURITY.md",
  typesAutojsGlobal: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typescriptSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 13 native addon loading decision: " + report.decision.status);
  console.log("Phase 13 native addon blockers: " + report.blockers.length);
  console.log("Phase 13 native addon policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = path.join(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const executionSurface = surface(files.surfaceManifest.text, "desktop.execution_runtime");
  const nativeAddonFeature = block(files.nodeBridgeSources.text, "native_addon: Object.freeze({", "      }),");
  const nativeAddonProfile = block(files.nodeBridgeSources.text, "nativeAddonProfile: Object.freeze({", "      }),");
  const scenarios = [
    scenario("diagnostic_profile_default_denial", "nativeAddonProfile records default denial and signed packaged enablement requirements", [
      token(files.nodeBridgeSources, "nativeAddonProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "disabled_by_default"'),
      token(files.nodeBridgeSources, 'targetProfiles: Object.freeze(["desktop_compat_opt_in"])'),
      token(files.nodeBridgeSources, 'module: "denied"'),
      token(files.nodeBridgeSources, 'requireExtension: "denied"'),
      token(files.nodeBridgeSources, 'processDlopen: "denied"'),
      token(files.nodeBridgeSources, 'signedPackagedOnly: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'abiAllowlist: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'hashAllowlist: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'dependencyScan: "required_before_enablement"'),
      token(files.nodeBridgeSources, 'crashIsolation: "required_before_enablement"'),
      token(files.p13NativeAddonTest, "nativeAddonProfileKeepsNodeFilesAndDlopenDenied"),
      token(files.p13NativeAddonTest, "p13native.profile=disabled_by_default|false|desktop_compat_opt_in"),
      token(files.typesAutojsGlobal, "export interface NativeAddonProfile"),
      token(files.typesAutojsGlobal, "readonly nativeAddonProfile: NativeAddonProfile"),
      token(files.typescriptSmoke, "autojsProfile.nativeAddonProfile.abiAllowlist"),
    ], []),
    scenario("require_resolve_dlopen_denial", ".node require/resolve and process private native APIs stay denied", [
      token(files.p13NativeAddonTest, "p13native.requireRelativeNode=ERR_AUTOJS6_NATIVE_ADDON_DISABLED"),
      token(files.p13NativeAddonTest, "p13native.resolveRelativeNode=ERR_AUTOJS6_NATIVE_ADDON_DISABLED"),
      token(files.p13NativeAddonTest, "p13native.requirePackageMain=ERR_AUTOJS6_NATIVE_ADDON_DISABLED"),
      token(files.p13NativeAddonTest, "p13native.resolvePackageMain=ERR_AUTOJS6_NATIVE_ADDON_DISABLED"),
      token(files.p13NativeAddonTest, "p13native.processDlopen=ERR_AUTOJS6_PROCESS_API_DISABLED"),
      token(files.p13NativeAddonTest, "p13native.loaded=false"),
      token(files.nativeAddonPolicyTest, "nativeAddonEntrypointsRejectWithStableCode"),
      token(files.nativeAddonPolicyTest, "package_exports"),
      token(files.nativeAddonPolicyTest, "package_imports"),
      token(files.processSweepTest, "process.dlopen({}, \"native.node\")"),
      token(files.securityRegressionTest, "process.dlopen"),
      token(files.packagedSmokeTest, "processBuiltin.dlopen"),
    ], []),
    scenario("future_signed_packaged_blockers", "Future opt-in requires ABI, integrity, package-only, dependency scan, and isolation gates", [
      token(files.projectAnalyzer, 'NATIVE_EXTENSIONS = new Set([".node", ".so", ".dll", ".dylib", ".a", ".o"])'),
      token(files.projectAnalyzer, "ENTRY_NATIVE_ADDON_DISABLED"),
      token(files.projectAnalyzer, "NATIVE_ADDON_DISABLED"),
      token(files.projectAnalyzer, "NODE_GYP_ARTIFACT_UNSUPPORTED"),
      token(files.projectAnalyzer, "PROCESS_NATIVE_API_DISABLED"),
      token(files.dependencyManager, "nativeAddonsEnabled: false"),
      token(files.dependencyManager, "ERR_AUTOJS6_PM_NATIVE_ADDON_DISABLED"),
      token(files.pluginPolicy, "Node native addon"),
      token(files.pluginPolicy, "no direct Node `.node` loading into the embedded process"),
      token(files.phase9PluginPolicy, "direct `process.dlopen` authority"),
      token(files.phase9ChaosSecurity, "`process.binding`, `process.dlopen`, signed native plugin archives"),
      token(files.compatibilityProfile, "NATIVE_ADDON_DISABLED"),
      token(files.securityModel, "Runtime npm/native build tooling must not be used as the enablement path"),
      token(files.p13Report, "For P13-39 v1.1, native addon artifacts stay packaged-only future inputs"),
      token(files.p13Report, "Runtime download, npm native build, lifecycle-script execution, postinstall"),
      token(files.phase13Tasks, "For P13-39 v1.1, native addon artifacts stay packaged-only future inputs"),
      token(files.phase13Tasks, "unrestricted native payload extraction stay stable-denied"),
    ], [
      "Define a signed packaged native-addon manifest with per-ABI artifact declarations.",
      "Tie ABI and Node-API/version allowlists to the embedded Node runtime, Android ABI, NDK, and release channel.",
      "Verify hashes and signatures before resolution and reject tampered or unlisted artifacts.",
      "Add dependency scan and APK-builder preflight evidence for every native payload and node-gyp trace.",
      "Add crash isolation or a host-owned native service boundary before allowing code into, or beside, the embedded runtime.",
      "Add packaged positive and hostile-corpus evidence before promotion.",
    ]),
    scenario("docs_gate_and_matrix", "Docs, release matrix, surface manifest, and Gradle gate record P13-39", [
      token(files.phase13Tasks, "P13-39 partial landing"),
      token(files.p13Report, "P13-39 records native addon loading as partial"),
      token(files.phase13GradleGate, "verifyNodePhase13NativeAddonLoadingDecision"),
      token(files.testingDoc, "verifyNodePhase13NativeAddonLoadingDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13NativeAddonLoadingDecision"),
      token(files.compatibilityProfile, "P13-39 records native addon loading as partial"),
      token(files.securityModel, "P13-39 records native addon loading as partial"),
      token(files.proComparison, "P13-39 records native addon loading as partial"),
      token(files.proAutoxNotes, "P13-39 records native addon loading as partial"),
      token(files.typesReadme, "P13-39 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-39 native addon loading"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-39"), "P13-39 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-39 report must exist."),
    check("surface-present", Boolean(executionSurface), "`desktop.execution_runtime` surface must exist."),
    check(
      "surface-not-promoted",
      executionSurface && ["planned", "design_locked", "debug_poc", "provider_poc"].includes(executionSurface.status),
      "`desktop.execution_runtime` must not be opt-in/default promoted by the P13-39 partial gate.",
    ),
    check(
      "surface-evidence",
      executionSurface && (executionSurface.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`desktop.execution_runtime` surface must reference P13-39 evidence.",
    ),
    check(
      "native-addon-feature-stays-disabled",
      nativeAddonFeature.includes("defaultEnabled: false") &&
        nativeAddonFeature.includes("enabled: false") &&
        nativeAddonFeature.includes("projectJsonOverrideAllowed: false") &&
        nativeAddonFeature.includes("packagedApkOverrideAllowed: false") &&
        nativeAddonFeature.includes("releaseForbidden: true") &&
        nativeAddonFeature.includes('status: "unsupported"') &&
        files.runtimeCapabilities.text.includes('id = "native_addon"') &&
        files.runtimeCapabilities.text.includes('status = "unsupported"'),
      "native_addon feature flags and capability metadata must stay disabled/unsupported.",
    ),
    check(
      "native-addon-profile-fail-closed",
      nativeAddonProfile.includes("defaultEnabled: false") &&
        nativeAddonProfile.includes('module: "denied"') &&
        nativeAddonProfile.includes('processDlopen: "denied"') &&
        nativeAddonProfile.includes('downloadedArtifacts: "denied"') &&
        nativeAddonProfile.includes('downloadExec: false'),
      "nativeAddonProfile must report fail-closed module, dlopen, download, and default state.",
    ),
    check(
      "resolver-and-process-denials-present",
      files.nodeBridgeSources.text.includes("__autojs6_throw_if_native_addon_extension") &&
        files.nodeBridgeSources.text.includes("ERR_AUTOJS6_NATIVE_ADDON_DISABLED") &&
        files.nodeBridgeSources.text.includes('__autojs6_define_process_property("dlopen", __autojs6_disabled_process_api("dlopen"), false)'),
      ".node resolver denial and process.dlopen denial must stay present.",
    ),
    check(
      "runtime-download-build-lifecycle-denied",
      nativeAddonProfile.includes('downloadedArtifacts: "denied"') &&
        nativeAddonProfile.includes("downloadExec: false") &&
        files.nodeBridgeSources.text.includes('lifecycleScripts: "denied"') &&
        files.nodeBridgeSources.text.includes('nativeBuild: "denied"') &&
        files.nodeBridgeSources.text.includes('postinstallDownload: "denied"') &&
        files.dependencyManager.text.includes("nativeAddonsEnabled: false") &&
        files.dependencyManager.text.includes("lifecycleScriptsExecuted: false") &&
        files.dependencyManager.text.includes("ERR_AUTOJS6_PM_NATIVE_ADDON_DISABLED") &&
        files.projectAnalyzer.text.includes("DEPENDENCY_LIFECYCLE_SCRIPT_UNSUPPORTED") &&
        files.projectAnalyzer.text.includes("NODE_GYP_ARTIFACT_UNSUPPORTED") &&
        files.p13Report.text.includes("Runtime download, npm native build, lifecycle-script execution, postinstall") &&
        files.p13Report.text.includes("unrestricted native payload extraction remain stable-denied"),
      "P13-39 must keep runtime download, npm native build, lifecycle script, and postinstall execution paths denied.",
    ),
    check(
      "no-node-addon-types-or-runtime-facade",
      !files.typesAutojsGlobal.text.includes('declare module "*.node"') &&
        !files.typesAutojsGlobal.text.includes("declare module '*.node'") &&
        !files.nodeBridgeSources.text.includes("function __autojs6_load_native_addon") &&
        !files.nodeBridgeSources.text.includes("nativeAddonProfile.enabled") &&
        !files.nodeBridgeSources.text.includes("process.dlopen =") &&
        !files.nodeBridgeSources.text.includes("NativeAddonLoader"),
      "P13-39 must not introduce a callable native-addon loader, .node declaration, or dlopen replacement.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-native-addon-loading-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13NativeAddonLoadingDecision",
    task: "P13-39",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: executionSurface || {
      id: "desktop.execution_runtime",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: false,
      finalBlockerClosable,
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile_default_denial").status === "ready",
      defaultDeniedReady: scenarioById(scenarios, "require_resolve_dlopen_denial").status === "ready",
      signedPackagedManifestReady: false,
      abiAllowlistReady: false,
      hashSignatureVerificationReady: false,
      dependencyScanReady: false,
      crashIsolationReady: false,
      runtimeDownloadExecAllowed: false,
      runtimeDownloadBuildLifecycleDenied: checkById(checks, "runtime-download-build-lifecycle-denied").passed,
      desktopSurfacePromoted: false,
    },
  };
}

function scenario(id, title, checks, plannedRemaining = []) {
  const missing = checks.filter((entry) => !entry.present).map((entry) => `missing evidence token: ${entry.token}`);
  const remaining = [...missing, ...plannedRemaining];
  return {
    id,
    title,
    status: remaining.length === 0 ? "ready" : missing.length > 0 ? "missing_evidence" : "partial",
    checks,
    remaining,
  };
}

function token(file, value) {
  return {
    file: file.relative,
    token: value,
    present: file.exists && file.text.includes(value),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  return checks.find((entry) => entry.id === id) || { passed: false };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id);
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(text);
    return (manifest.surfaces || []).find((entry) => entry.id === id);
  } catch (_) {
    return null;
  }
}

function block(text, startToken, endToken) {
  const start = text.indexOf(startToken);
  if (start < 0) return "";
  const end = text.indexOf(endToken, start);
  if (end < 0) return text.slice(start);
  return text.slice(start, end + endToken.length);
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Native Addon Loading Decision",
    "",
    `- Task: ${report.task}`,
    `- Gate: \`${report.gate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Blockers: ${report.blockers.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Scenarios",
    "",
  ];
  for (const scenarioItem of report.scenarios) {
    lines.push(`- \`${scenarioItem.id}\`: \`${scenarioItem.status}\` - ${scenarioItem.title}`);
    for (const remaining of scenarioItem.remaining) {
      lines.push(`  - ${remaining}`);
    }
  }
  lines.push("", "## Checks", "");
  for (const checkItem of report.checks) {
    lines.push(`- ${checkItem.passed ? "[x]" : "[ ]"} \`${checkItem.id}\`: ${checkItem.message}`);
  }
  if (report.policyViolations.length > 0) {
    lines.push("", "## Policy Violations", "");
    for (const violation of report.policyViolations) {
      lines.push(`- ${violation}`);
    }
  }
  lines.push("");
  return lines.join("\n");
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[index + 1];
      index += 1;
    } else if (arg === "--report-dir") {
      options.reportDir = args[index + 1];
      index += 1;
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    } else if (arg === "-h" || arg === "--help") {
      console.log("Usage: node verify-native-addon-loading-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
