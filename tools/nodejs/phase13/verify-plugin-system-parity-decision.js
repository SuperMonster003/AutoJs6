#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { resolvePhase13SourceFile } = require("./source-topology");

const REPORT_BASENAME = "phase13-plugin-system-parity-decision";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_46_PLUGIN_SYSTEM_PARITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  phase13GradleGate: "app/node-phase13-gate.gradle.kts",
  p13PluginTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemParityPhase13DecisionInstrumentationTest.kt",
  pluginSystemTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePluginSystemInstrumentationTest.kt",
  pluginDesign: "docs/nodejs/design/PLUGIN_SYSTEM.md",
  phase9PluginPolicy: "docs/nodejs/reports/phase9/PLUGIN_POLICY.md",
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
  console.log("Phase 13 plugin system parity decision: " + report.decision.status);
  console.log("Phase 13 plugin system blockers: " + report.blockers.length);
  console.log("Phase 13 plugin system policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0 || (options.failOnBlocked && !report.decision.finalBlockerClosable)) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot) {
  return Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => {
      const file = resolvePhase13SourceFile(repoRoot, relative);
      return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
    }),
  );
}

function buildReport(files) {
  const surfaceEntry = surface(files.surfaceManifest.text, "packaging.plugins_distribution");
  const profileBlock = block(files.nodeBridgeSources.text, "pluginSystemProfile: Object.freeze({", "      filesystemProfile: Object.freeze({");
  const scenarios = [
    scenario("diagnostic_profile", "pluginSystemProfile records the JS-only local plugin manager boundary", [
      token(files.nodeBridgeSources, "pluginSystemProfile: Object.freeze({"),
      token(files.nodeBridgeSources, 'status: "js_only_local_manager_partial"'),
      token(files.nodeBridgeSources, 'manager: "js_only_local_manager_v1"'),
      token(files.nodeBridgeSources, 'pluginRoot: "project_local_plugins_directory"'),
      token(files.nodeBridgeSources, 'installMode: "already_present_current_execution"'),
      token(files.nodeBridgeSources, 'permissionModel: "exact_plugin_id_plus_requested_permissions"'),
      token(files.nodeBridgeSources, 'hostManifestEnforcement: "required_before_install_load_start"'),
      token(files.nodeBridgeSources, 'overDeclaredPluginCapabilities: "rejected_before_code_execution"'),
      token(files.nodeBridgeSources, 'pluginNamespacePermissions: "rejected_in_manifest"'),
      token(files.nodeBridgeSources, 'androidProviderPlugins: "deferred"'),
      token(files.nodeBridgeSources, 'nativePlugins: "denied"'),
      token(files.nodeBridgeSources, 'remoteMarketplace: "deferred"'),
      token(files.nodeBridgeSources, 'packagedDistribution: "local_checked_files_only"'),
      token(files.nodeBridgeSources, "downloadInstaller: false"),
      token(files.nodeBridgeSources, "nativeAddonLoading: false"),
      token(files.nodeBridgeSources, "androidPluginApkBridge: false"),
      token(files.nodeBridgeSources, "marketplaceInstall: false"),
      token(files.nodeBridgeSources, "grantsHostCapabilities: false"),
      token(files.typesAutojsGlobal, "export interface PluginSystemProfile"),
      token(files.typesAutojsGlobal, "readonly pluginSystemProfile: PluginSystemProfile"),
      token(files.typescriptSmoke, "autojsProfile.pluginSystemProfile.operations"),
      token(files.typescriptSmoke, "autojsProfile.pluginSystemProfile.nativeAddonLoading"),
    ]),
    scenario("host_capability_ceiling", "Plugin capabilities cannot exceed host project/package declarations", [
      token(files.nodeBridgeSources, "__autojs6_plugins_assert_capabilities"),
      token(files.nodeBridgeSources, "__autojs6_plugin_assert_bridge_permission"),
      token(files.nodeBridgeSources, "cannot declare plugin namespace capability"),
      token(files.nodeBridgeSources, 'throw __autojs6_bridge_capability_not_declared_error("plugins", methodName, missing);'),
      token(files.nodeBridgeSources, "__autojs6_plugin_scan_native_files"),
      token(files.nodeBridgeSources, "plugins.reload is debug-only"),
      token(files.p13PluginTest, "NodePluginSystemParityPhase13DecisionInstrumentationTest"),
      token(files.p13PluginTest, "pluginSystemProfileAndHostCapabilityCeilingAreStable"),
      token(files.p13PluginTest, "p13plugin.profile=js_only_local_manager_partial"),
      token(files.p13PluginTest, "p13plugin.denial=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|"),
      token(files.p13PluginTest, "globalThis.__autojs6Phase13PluginExecuted === true"),
      token(files.pluginSystemTest, "pluginPermissionDeniedBeforeCodeExecution"),
      token(files.pluginSystemTest, "pluginNamespaceCapabilityPermissionIsRejectedBeforeCodeExecution"),
      token(files.pluginSystemTest, "pluginNativeAddonFileIsRejected"),
      token(files.pluginSystemTest, "pluginLifecycleResourcesAndDebugReload"),
      token(files.pluginSystemTest, "plugins.reload(\"org.example.lifecycle\", { debug: true })"),
      token(files.pluginDesign, "project metadata must declare exact `plugin.<id>` capability"),
      token(files.pluginDesign, "it cannot declare `plugin` or `plugin.*` namespace capabilities"),
      token(files.phase9PluginPolicy, "project-local JS-only manager"),
      token(files.p13Report, "For P13-46 v1.1, plugin-declared permissions are request metadata only"),
      token(files.p13Report, "cannot promote host bridge authority beyond the exact capabilities"),
      token(files.compatibilityProfile, "the host project/package permission manifest remains the authority ceiling"),
      token(files.securityModel, "declared by the host"),
    ]),
    scenario("docs_gate_and_surface", "Docs, release matrix, surface manifest, and Gradle gate record P13-46", [
      token(files.phase13Tasks, "P13-46 partial landing"),
      token(files.p13Report, "P13-46 records the v1.1 plugin system parity decision as ready"),
      token(files.phase13GradleGate, "verifyNodePhase13PluginSystemParityDecision"),
      token(files.testingDoc, "verifyNodePhase13PluginSystemParityDecision"),
      token(files.ciGatesDoc, "verifyNodePhase13PluginSystemParityDecision"),
      token(files.compatibilityProfile, "P13-46 records plugin system parity as partial"),
      token(files.securityModel, "P13-46 records plugin system parity as partial"),
      token(files.proComparison, "P13-46 records plugin system parity as partial"),
      token(files.proAutoxNotes, "P13-46 records plugin system parity as partial"),
      token(files.typesReadme, "P13-46 extends the `autojs6:profile` declaration"),
      token(files.surfaceManifestDoc, REPORT_BASENAME),
      token(files.surfaceManifest, REPORT_BASENAME),
      token(files.capabilityMatrix, "P13-46 plugin system parity"),
    ], []),
  ];

  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-46"), "P13-46 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-46 report must exist."),
    check("surface-present", Boolean(surfaceEntry), "`packaging.plugins_distribution` surface must exist."),
    check(
      "surface-evidence",
      surfaceEntry && (surfaceEntry.evidence || []).some((entry) => entry.includes(REPORT_BASENAME)),
      "`packaging.plugins_distribution` surface must reference P13-46 evidence.",
    ),
    check(
      "profile-fail-closed",
      profileBlock.includes("downloadInstaller: false") &&
        profileBlock.includes("nativeAddonLoading: false") &&
        profileBlock.includes("androidPluginApkBridge: false") &&
        profileBlock.includes("marketplaceInstall: false") &&
        profileBlock.includes("grantsHostCapabilities: false"),
      "pluginSystemProfile must keep installer, native addon, Android plugin APK bridge, marketplace, and host-capability grant paths fail-closed.",
    ),
    check(
      "host-capability-ceiling",
      files.nodeBridgeSources.text.includes("__autojs6_plugins_assert_capabilities") &&
        files.nodeBridgeSources.text.includes('throw __autojs6_bridge_capability_not_declared_error("plugins", methodName, missing);'),
      "plugins must reject missing host-declared capabilities before plugin code runs.",
    ),
    check(
      "no-unsafe-plugin-promotion",
      !files.nodeBridgeSources.text.includes("downloadInstaller: true") &&
        !files.nodeBridgeSources.text.includes("nativeAddonLoading: true") &&
        !files.nodeBridgeSources.text.includes("androidPluginApkBridge: true") &&
        !files.nodeBridgeSources.text.includes("marketplaceInstall: true") &&
        !files.nodeBridgeSources.text.includes("grantsHostCapabilities: true"),
      "P13-46 must not promote plugin download, native addon, Android plugin APK bridge, marketplace, or host capability grants.",
    ),
  ];

  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-plugin-system-parity-decision-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13PluginSystemParityDecision",
    task: "P13-46",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: surfaceEntry || {
      id: "packaging.plugins_distribution",
      status: "missing",
      evidence: [],
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      diagnosticProfileReady: scenarioById(scenarios, "diagnostic_profile").status === "ready",
      hostCapabilityCeilingReady: scenarioById(scenarios, "host_capability_ceiling").status !== "missing_evidence",
      androidProviderPluginsPromoted: false,
      nativePluginsPromoted: false,
      remoteMarketplacePromoted: false,
      pluginGrantsHostAuthority: false,
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
    "# Phase 13 Plugin System Parity Decision",
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
      console.log("Usage: node verify-plugin-system-parity-decision.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

main();
