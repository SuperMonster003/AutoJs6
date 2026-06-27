#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-shell-root-shizuku-capability";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  shellBridgeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeShellBridgeInstrumentationTest.kt",
  permissionManifestTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgePermissionManifestInstrumentationTest.kt",
  shellTypes: "docs/nodejs/types/autojs6-node/shell.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  appIntentReport: "docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md",
  gradleGate: "app/node-phase13-gate.gradle.kts",
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
  console.log("Phase 13 shell/root/Shizuku capability: " + report.decision.status);
  console.log("Phase 13 shell/root/Shizuku capability blockers: " + report.blockers.length);
  console.log("Phase 13 shell/root/Shizuku capability policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.tasks_app_shell_media_utils");
  const scenarios = [
    scenario("capability_split", "shell, root shell, and Shizuku capability vocabulary are split", [
      token(files.permissionManifest, 'const val SHELL = "shell"'),
      token(files.permissionManifest, 'const val SHELL_ROOT = "shell.root"'),
      token(files.permissionManifest, 'const val SHELL_SHIZUKU = "shell.shizuku"'),
      token(files.permissionManifest, '"shell:root" to SHELL_ROOT'),
      token(files.permissionManifest, '"shell:shizuku" to SHELL_SHIZUKU'),
      token(files.permissionManifest, "required == SHELL_ROOT"),
      token(files.permissionManifest, "required == SHELL_SHIZUKU"),
      token(files.nodeBridgeSources, 'required === "shell.root"'),
      token(files.nodeBridgeSources, 'required === "shell.shizuku"'),
      token(files.nodeBridgeSources, 'const permissions = normalizedOptions.root ? ["shell", "shell.root"] : ["shell"];'),
      token(files.nodeBridgeSources, 'requested.push("shell.shizuku");'),
      token(files.providerRegistry, '"rootCapability" to NodeBridgePermissionManifest.SHELL_ROOT'),
      token(files.providerRegistry, '"shizukuCapability" to NodeBridgePermissionManifest.SHELL_SHIZUKU'),
      token(files.permissionManifestTest, 'assertEquals(listOf("shell.root"), response.error?.missingCapabilities)'),
      token(files.shellBridgeTest, "shell.policy.access=true|false|false|shell.root|shell.shizuku"),
      token(files.shellBridgeTest, "shell.policy.root_denied="),
      token(files.conformanceTest, "bridge.conformance.shell_shizuku_missing=shell|shell.shizuku"),
      token(files.p13Report, "project.json shell.root metadata example"),
      token(files.p13Report, '{"node":{"permissions":["shell","shell.root"]}}'),
      token(files.p13Report, "package.json shell.shizuku metadata example"),
      token(files.p13Report, '{"autojs6":{"node":{"permissions":["shell","shell.shizuku"]}}}'),
      token(files.p13Report, "does not promote Shizuku execution"),
    ]),
    scenario("controlled_shell_exec", "controlled shell execution remains bounded and resource-owned", [
      token(files.protocol, 'val shellMethods = setOf("exec")'),
      token(files.protocol, "private fun dispatchShell"),
      token(files.protocol, "tryAcquireShellCommand()"),
      token(files.protocol, "NodeBridgeError.resourceLimit"),
      token(files.protocol, "parseShellTimeoutMs"),
      token(files.protocol, "resolveShellCwd"),
      token(files.protocol, "parseShellEnv"),
      token(files.protocol, "ProcessBuilder(commandLine)"),
      token(files.protocol, "NodeBridgeResourceType.SHELL_EXECUTION"),
      token(files.protocol, "resource.close(\"timeout:$timeoutMs\")"),
      token(files.protocol, "process.destroyForcibly()"),
      token(files.protocol, "SHELL_OUTPUT_LIMIT_BYTES"),
      token(files.nodeBridgeSources, "function execFile(path, args, options)"),
      token(files.nodeBridgeSources, "__autojs6_shell_normalize_exec_file_path"),
      token(files.nodeBridgeSources, "__autojs6_shell_quote"),
      token(files.shellTypes, "execRoot(command: string"),
      token(files.typeSmoke, "const rootShellResult: shell.ShellResult = await shell.execRoot"),
    ], [
      "Add device root-available and root-unavailable matrix evidence without assuming `su` exists.",
      "Add cancellation-specific shell instrumentation that aborts an in-flight command through AbortSignal.",
    ]),
    scenario("shizuku_stable_denial", "Shizuku execution is explicit but still denied before bridge dispatch", [
      token(files.nodeBridgeSources, "function __autojs6_shell_shizuku_unsupported_error(methodName)"),
      token(files.nodeBridgeSources, "ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED"),
      token(files.nodeBridgeSources, "function execShizuku(command, options)"),
      token(files.nodeBridgeSources, "inputOptions.shizuku === true"),
      token(files.shellTypes, "execShizuku(command: string"),
      token(files.shellTypes, "Promise<never>;"),
      token(files.rhinoCompatTypes, "execShizuku(command: string"),
      token(files.typeSmoke, "const shizukuShellDenied: Promise<never>"),
      token(files.typeSmoke, "const compatShizukuDenied: Promise<never>"),
      token(files.conformanceTest, 'await expectRejectCode("shell.execShizuku"'),
      token(files.conformanceTest, "shell Shizuku rejected stubs must not dispatch bridge requests"),
      token(files.securityModel, "P13-24 records shell/root/Shizuku capability promotion as partial"),
      token(files.appIntentReport, "root/Shizuku/dual-user routes through explicit P13-24 capability surfaces"),
    ], [
      "Implement real Shizuku only after service binding, permission request/denial, process lifecycle, package metadata, and cleanup evidence exist.",
      "Add packaged disclosure and rollback policy before accepting shell.shizuku as executable authority.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-24 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-24 partial shell/root/Shizuku capability gate"),
      token(files.gradleGate, "verifyNodePhase13ShellRootShizukuCapability"),
      token(files.testingDoc, "verifyNodePhase13ShellRootShizukuCapability"),
      token(files.ciGatesDoc, "verifyNodePhase13ShellRootShizukuCapability"),
      token(files.compatibilityProfile, "P13-24 records shell/root/Shizuku capability promotion as partial"),
      token(files.proComparison, "P13-24 records shell/root/Shizuku capability promotion as partial"),
      token(files.proAutoxNotes, "P13-24 records shell/root/Shizuku capability promotion as partial"),
      token(files.capabilityMatrix, "P13-24 shell/root/Shizuku capability"),
      token(files.typesReadme, "P13-24 records the partial shell/root/Shizuku capability gate"),
      token(files.surfaceManifestDoc, "phase13-shell-root-shizuku-capability"),
    ], [
      "Keep P13-24 open until real root/Shizuku device matrix, packaged disclosure, authorization UI, and cancellation evidence land.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-24"), "P13-24 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-24 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-24 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-24 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-shell-root-shizuku-capability")),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-24 evidence.",
    ),
    check(
      "android-bridge-still-exec-only",
      files.protocol.text.includes('val shellMethods = setOf("exec")') &&
        !files.protocol.text.includes('"execShizuku" ->') &&
        !files.protocol.text.includes("WrappedShizuku"),
      "P13-24 must not add a live Android Shizuku dispatch method.",
    ),
    check(
      "no-shizuku-module-exposed",
      !files.nodeBridgeSources.text.includes('if (name === "shizuku")') &&
        !files.protocol.text.includes('const val SHIZUKU = "shizuku"'),
      "P13-24 must not expose require(\"shizuku\") before a dedicated provider lands.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-shell-root-shizuku-capability-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13ShellRootShizukuCapability",
    task: "P13-24",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.tasks_app_shell_media_utils",
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
      shellCapabilitySplitReady: scenarioById(scenarios, "capability_split").status !== "missing_evidence",
      controlledShellReady: scenarioById(scenarios, "controlled_shell_exec").status !== "missing_evidence",
      rootShellPromoted: false,
      shizukuExecutionReady: false,
      packagedShellReady: false,
      proSurfacePromoted: false,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing_evidence" };
}

function surface(text, id) {
  try {
    const parsed = JSON.parse(text);
    return (parsed.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [];
  lines.push(`# ${report.gate}`);
  lines.push("");
  lines.push(`- Task: ${report.task}`);
  lines.push(`- Status: ${report.decision.status}`);
  lines.push(`- Final blocker closable: ${report.decision.finalBlockerClosable}`);
  lines.push(`- Policy violations: ${report.policyViolations.length}`);
  lines.push(`- Blockers: ${report.blockers.length}`);
  lines.push("");
  lines.push("## Scenarios");
  report.scenarios.forEach((entry) => {
    lines.push("");
    lines.push(`### ${entry.id}`);
    lines.push("");
    lines.push(`- ${entry.title}`);
    lines.push(`- Status: ${entry.status}`);
    lines.push(`- Missing/planned remaining: ${entry.remaining.length}`);
    entry.remaining.forEach((item) => lines.push(`  - ${item}`));
  });
  if (report.policyViolations.length > 0) {
    lines.push("");
    lines.push("## Policy Violations");
    report.policyViolations.forEach((item) => lines.push(`- ${item}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function parseArgs(args) {
  const result = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      result.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      result.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      result.failOnBlocked = true;
    }
  }
  return result;
}

main();
