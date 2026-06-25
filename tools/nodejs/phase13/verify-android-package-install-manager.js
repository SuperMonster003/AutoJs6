#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-android-package-install-manager";

const FILES = {
  blockerRegistry: "docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json",
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  manager: "app/src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt",
  managerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt",
  p13PackageManagerTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt",
  typesPackageManager: "docs/nodejs/types/autojs6-node/package_manager.d.ts",
  typesIndex: "docs/nodejs/types/autojs6-node/index.d.ts",
  packageManagerDoc: "docs/nodejs/design/PACKAGE_MANAGER.md",
  testingDoc: "docs/nodejs/TESTING.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
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
  console.log("Phase 13 Android package install manager: " + report.decision.status);
  console.log("Phase 13 Android package install manager blockers: " + report.blockers.length);
  console.log("Phase 13 Android package install manager policy violations: " + report.policyViolations.length);
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
  const blockerEntry = blocker(files.blockerRegistry.text, "android_side_package_installation");
  const scenarios = [
    scenario("operations", "Android manager exposes required operations", [
      token(files.manager, "class NodeAndroidPackageInstallManager"),
      token(files.manager, "fun install("),
      token(files.manager, "fun update("),
      token(files.manager, "fun remove("),
      token(files.manager, "fun list("),
      token(files.manager, "fun verify("),
      token(files.manager, "fun prune("),
    ]),
    scenario("transaction_lock_cancel", "Operations are transactional, locked, and cancellable", [
      token(files.manager, "transactionDir"),
      token(files.manager, "moveDirectory"),
      token(files.manager, "channel.lock()"),
      token(files.manager, "class CancellationToken"),
      token(files.manager, "ERROR_CANCELLED"),
      token(files.managerTest, "installUpdateListVerifyRemoveAndPruneAreTransactionalAndIntegrityChecked"),
      token(files.managerTest, "ERROR_CANCELLED"),
    ]),
    scenario("integrity_policy", "Packages are integrity checked", [
      token(files.manager, "MessageDigest.getInstance(\"SHA-512\")"),
      token(files.manager, "expectedIntegrity"),
      token(files.manager, "ERROR_INTEGRITY_MISMATCH"),
      token(files.managerTest, "ERROR_INTEGRITY_MISMATCH"),
    ]),
    scenario("safe_boundaries", "Lifecycle scripts, bin entries, native payloads, and path escapes are denied", [
      token(files.manager, "DISABLED_LIFECYCLE_SCRIPTS"),
      token(files.manager, "ERROR_LIFECYCLE_SCRIPT_DISABLED"),
      token(files.manager, "ERROR_NATIVE_PAYLOAD_DISABLED"),
      token(files.manager, "ERROR_BIN_DISABLED"),
      token(files.manager, "ERROR_PATH_ESCAPE"),
      token(files.managerTest, "rejectsLifecycleScriptsNativePayloadsBinEntriesAndCancellation"),
    ]),
    scenario("future_distribution", "Manager records dependency/plugin/project package kinds and a read-maintenance script facade for later distribution", [
      token(files.manager, "KIND_DEPENDENCY"),
      token(files.manager, "KIND_PLUGIN"),
      token(files.manager, "KIND_PROJECT"),
      token(files.manager, "nativeValues"),
      token(files.manager, '"android_package_manager.source_authority" to "local_unpacked_host_managed_only"'),
      token(files.manager, '"android_package_manager.allowed_sources" to "local_unpacked_host_managed"'),
      token(files.manager, '"android_package_manager.raw_registry_authority" to "disabled"'),
      token(files.manager, '"android_package_manager.remote_tarball_install" to "disabled"'),
      token(files.manager, '"android_package_manager.install_authority" to "internal_substrate_only"'),
      token(files.manager, '"android_package_manager.js_bridge" to "read_maintenance_partial"'),
      token(files.nodeBridgeSources, "function __autojs6_limited_package_manager()"),
      token(files.nodeBridgeSources, "function sourcePolicy()"),
      token(files.nodeBridgeSources, "autojs6-node-package-manager-source-policy-v1"),
      token(files.nodeBridgeSources, 'jsBridge: "read_maintenance_partial"'),
      token(files.nodeBridgeSources, 'requirePackageManager: "read_maintenance_partial"'),
      token(files.nodeBridgeSources, "ERR_AUTOJS6_PACKAGE_MANAGER_NOT_PROMOTED"),
      token(files.p13PackageManagerTest, "packageManagerReadMaintenanceFacadeListsVerifiesAndPrunesLocalStore"),
      token(files.typesPackageManager, 'declare module "package_manager"'),
      token(files.typesPackageManager, "sourcePolicy(): SourcePolicy"),
      token(files.typesPackageManager, "list(options?: ListOptions): Promise<readonly PackageRecord[]>"),
      token(files.typesPackageManager, "install(request?: PackageRequest): never"),
      token(files.typesIndex, './package_manager.d.ts'),
    ], [
      "Registry download and npm CLI install remain disabled.",
      "Install/update/remove remain unpromoted from JavaScript until distribution policy and user-visible install authority are proven.",
      "Signed native payload verification is not implemented; native payload execution remains disabled.",
    ]),
    scenario("docs", "Docs record partial Android package install progress", [
      token(files.phase13Tasks, "P13-09"),
      token(files.p13Report, "Status: partial"),
      token(files.packageManagerDoc, "NodeAndroidPackageInstallManager"),
      token(files.testingDoc, "NodeAndroidPackageInstallManagerInstrumentationTest"),
      token(files.securityModel, "NodeAndroidPackageInstallManager"),
      token(files.capabilityMatrix, "NodeAndroidPackageInstallManager"),
    ]),
  ];
  const checks = [
    check("blocker-open", blockerEntry && blockerEntry.status === "open", "`android_side_package_installation` must remain open until runtime exposure and distribution evidence land."),
    check("manager-present", files.manager.exists, "Android package install manager source must exist."),
    check("test-present", files.managerTest.exists, "Android package install manager focused instrumentation must exist."),
    check("report-present", files.p13Report.exists, "P13-09 report must document current progress."),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = [
    ...scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`)),
  ];
  if (blockerEntry && blockerEntry.status === "open") {
    blockers.push(`android_side_package_installation: ${blockerEntry.currentEvidence}`);
  }
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-android-package-install-manager-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13AndroidPackageInstallManager",
    task: "P13-09",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    blockerRegistry: blockerEntry || {
      id: "android_side_package_installation",
      status: "missing",
      currentEvidence: "blocker registry entry is missing",
    },
    scenarios,
    checks,
    blockers,
    policyViolations,
    decision: {
      status: policyViolations.length > 0 ? "failed" : finalBlockerClosable ? "ready" : "partial",
      phase13TaskComplete: finalBlockerClosable,
      finalBlockerClosable,
      managerReady: scenarioById(scenarios, "operations").status === "ready" &&
        scenarioById(scenarios, "transaction_lock_cancel").status === "ready" &&
        scenarioById(scenarios, "integrity_policy").status === "ready" &&
        scenarioById(scenarios, "safe_boundaries").status === "ready",
      runtimeExposureReady: scenarioById(scenarios, "future_distribution").status === "ready",
      diagnosticFacadeReady: scenarioById(scenarios, "future_distribution").missingEvidence === 0,
      safeNodeProfilePreserved: true,
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
    missingEvidence: missing.length,
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

function blocker(text, id) {
  try {
    const registry = JSON.parse(stripBom(text));
    return (registry.blockers || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Android Package Install Manager",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Manager ready: \`${report.decision.managerReady}\``,
    `- Runtime exposure ready: \`${report.decision.runtimeExposureReady}\``,
    `- Final blocker closable: \`${report.decision.finalBlockerClosable}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Blockers: ${report.blockers.length}`,
    "",
    "## Scenarios",
    "",
    "| Scenario | Status | Remaining |",
    "| --- | --- | --- |",
  ];
  report.scenarios.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.status}\` | ${escapeTable(entry.remaining.join("; ") || "None.")} |`);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | ${entry.passed ? "passed" : "failed"} | ${escapeTable(entry.message)} |`);
  });
  lines.push("", "## Blockers", "");
  if (report.blockers.length === 0) {
    lines.push("None.");
  } else {
    report.blockers.forEach((entry) => lines.push(`- ${entry}`));
  }
  return lines.join("\n") + "\n";
}

function read(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "") : "";
}

function stripBom(value) {
  return String(value || "").replace(/^\uFEFF/, "");
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  const booleanArgs = new Set(["fail-on-blocked"]);
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-android-package-install-manager.js [--repo-root dir] [--report-dir dir] [--fail-on-blocked]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    if (booleanArgs.has(rawKey)) {
      options[key] = eq >= 0 ? parseBoolean(arg.slice(eq + 1)) : true;
      continue;
    }
    const value = eq >= 0 ? arg.slice(eq + 1) : argv[index + 1];
    if (eq < 0) {
      index += 1;
    }
    if (!value || value.startsWith("--")) {
      throw new Error("Missing value for " + arg);
    }
    options[key] = value;
  }
  return options;
}

function parseBoolean(value) {
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

main();
