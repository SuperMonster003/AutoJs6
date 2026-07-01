#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-accessibility-selector-action-matrix";
const REQUIRED_DEVICE_CLASSES = Object.freeze(["android_12_class", "android_15_plus_class"]);
const REQUIRED_FIELDS = Object.freeze([
  "deviceSerial",
  "deviceModel",
  "androidVersion",
  "apiLevel",
  "packageIdentity",
  "profile",
  "serviceState",
  "settingsPreparedByUser",
  "capabilities",
  "directSelectorApis",
  "compatSelectorApis",
  "globalActions",
  "timeoutBehavior",
  "missingServiceBehavior",
  "missingCapabilityBehavior",
  "unsupportedActionDiagnostics",
  "rawObjectBoundary",
  "reportPath",
]);
const REQUIRED_SCENARIOS = Object.freeze([
  "direct selector builders and action facade",
  "Rhino compat selector aliases and chain helpers",
  "service-enabled findByText/findOne/click/longClick controlled UI",
  "Back/Home/Recents optional global actions",
  "timeout and cancellation cleanup",
  "missing service denial",
  "missing capability denial",
  "gesture/root/Shizuku unsupported tier diagnostics",
  "raw AccessibilityService/AccessibilityNodeInfo/UiObject absence",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "accessibility_selector_android12_action_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 12 class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior.",
  },
  {
    id: "accessibility_selector_android15_plus_action_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived Android 15+ class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior.",
  },
  {
    id: "accessibility_selector_global_actions_recents_archive",
    classification: "future_promotion",
    requiredEvidence: "Back/Home/Recents global action archive recorded only on devices where those actions are safe to trigger.",
  },
  {
    id: "accessibility_selector_unsupported_tier_diagnostics_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived diagnostics proving gesture/root/Shizuku tiers remain separate, unsupported or explicitly denied, and do not leak raw accessibility objects.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  matrixJson: "docs/nodejs/phase14/accessibility-selector-action-matrix.json",
  matrixDoc: "docs/nodejs/phase14/PHASE14_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p13AccessibilityReport: "docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md",
  p13ShellReport: "docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md",
  p14AccessibilityLifecycleReport: "docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md",
  p13AccessibilityVerifier: "tools/nodejs/phase13/verify-accessibility-selector-promotion.js",
  p14AccessibilityLifecycleVerifier: "tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  accessibilityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  realDeviceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilityRealDeviceSmokeInstrumentationTest.kt",
  smokeActivity: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySmokeActivity.kt",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  shellTypes: "docs/nodejs/types/autojs6-node/shell.d.ts",
  deviceMatrix: "docs/nodejs/DEVICE_MATRIX.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testing: "docs/nodejs/TESTING.md",
  ciGates: "docs/nodejs/CI_GATES.md",
  gradle14: "app/node-phase14-gate.gradle.kts",
};

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = loadFiles(repoRoot, reportDir);
  const report = buildReport(files);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Phase 14 accessibility selector action matrix: " + report.decision.status);
  console.log("Phase 14 accessibility selector action matrix blockers: " + report.blockers.length);
  console.log("Phase 14 accessibility selector action matrix policy violations: " + report.policyViolations.length);
  if (report.policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function loadFiles(repoRoot, reportDir) {
  const entries = Object.entries(FILES).map(([key, relative]) => {
    const file = path.join(repoRoot, relative);
    return [key, { relative, exists: fs.existsSync(file), text: read(file) }];
  });
  const generatedReports = {
    p13AccessibilityGeneratedReport: "phase13-accessibility-selector-promotion.json",
    p13ShellGeneratedReport: "phase13-shell-root-shizuku-capability.json",
    p14AccessibilityLifecycleGeneratedReport: "phase14-accessibility-lifecycle.json",
  };
  Object.entries(generatedReports).forEach(([key, basename]) => {
    const file = path.join(reportDir, basename);
    entries.push([
      key,
      {
        relative: path.relative(repoRoot, file).replace(/\\/g, "/"),
        exists: fs.existsSync(file),
        text: read(file),
      },
    ]);
  });
  return Object.fromEntries(entries);
}

function buildReport(files) {
  const manifest = parseJson(files.surfaceManifest.text);
  const contracts = parseJson(files.promotionContractsJson.text);
  const matrix = parseJson(files.matrixJson.text);
  const rows = Array.isArray(matrix.evidenceRows) ? matrix.evidenceRows : [];
  const p13AccessibilityGenerated = parseJson(files.p13AccessibilityGeneratedReport.text);
  const p13ShellGenerated = parseJson(files.p13ShellGeneratedReport.text);
  const p14AccessibilityLifecycleGenerated = parseJson(files.p14AccessibilityLifecycleGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const contractMatrix = contracts.deviceEvidenceMatrix || {};
  const p13Decision = p13AccessibilityGenerated.decision || {};
  const p14LifecycleDecision = p14AccessibilityLifecycleGenerated.decision || {};
  const p13ShellDecision = p13ShellGenerated.decision || {};
  const scenarios = [
    scenario("matrix_schema_and_required_slots", "Matrix declares required device classes, fields, scenarios, and reserved archive rows", [
      checkTokenValue("schema", matrix.schema === "autojs6-node-phase14-accessibility-selector-action-matrix-v1"),
      checkTokenValue("task", matrix.task === "P14-12"),
      checkTokenValue("status", matrix.status === "partial"),
      checkTokenValue("promotionState", matrix.promotionState === "provider_poc"),
      checkTokenValue("requiredDeviceClasses", sameSet(matrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES)),
      checkTokenValue("requiredFields", includesAll(matrix.requiredFields || [], REQUIRED_FIELDS)),
      checkTokenValue("requiredSelectorActionScenarios", includesAll(matrix.requiredSelectorActionScenarios || [], REQUIRED_SCENARIOS)),
      checkTokenValue("android_12_row", hasRow(rows, "android_12_class", "missing_device_archive")),
      checkTokenValue("android_15_plus_row", hasRow(rows, "android_15_plus_class", "missing_device_archive")),
      checkTokenValue("rows_have_required_fields", rows.every((row) => REQUIRED_FIELDS.every((field) => Object.prototype.hasOwnProperty.call(row, field)))),
    ]),
    scenario("selector_facade_imported", "Direct selector builders, action facade, Rhino aliases, and timeout cleanup are imported from P13-19", [
      token(files.accessibilityTest, "jsAccessibilitySelectorV2UsesFakeTransportAndBlocksShadowing"),
      token(files.accessibilityTest, "jsRhinoCompatSelectorFacadeUsesFakeTransport"),
      token(files.accessibilityTest, "jsAccessibilitySelectorV2HonorsTimeoutAndCancellation"),
      token(files.accessibilityTest, "a11y.selector.requests=findOne|findAll|click|longClick|setText|scrollForward|scrollBackward"),
      token(files.accessibilityTest, "rhino.compat.selector.requests=findOne|findAll|click|click|back|home|recentApps"),
      token(files.accessibilityTypes, "textMatches(value: SelectorPatternInput): SelectorDescriptor;"),
      token(files.accessibilityTypes, "scrollable(value?: boolean): SelectorDescriptor;"),
      token(files.rhinoCompatTypes, "descriptionContains(value: string): RhinoSelectorHandle;"),
      token(files.rhinoCompatTypes, "idContains(value: string): RhinoSelectorHandle;"),
      token(files.rhinoCompatTypes, "classNameContains(value: string): RhinoSelectorHandle;"),
      checkTokenValue("p13_direct_selector", p13Decision.directSelectorBridgeReady === true),
      checkTokenValue("p13_rhino_selector", p13Decision.rhinoCompatSelectorReady === true),
      checkTokenValue("p13_waitfor_denied", p13Decision.waitForPickupDetectStableDenied === true),
    ]),
    scenario("real_device_profile_anchor", "Manual accessibility profile anchors controlled UI actions, longClick, and optional Back/Home/Recents", [
      token(files.realDeviceTest, "providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled"),
      token(files.realDeviceTest, 'method = "longClick"'),
      token(files.realDeviceTest, 'provider.dispatchAccessibilityRequest("recentApps")'),
      token(files.realDeviceTest, "autojs.nodejs.accessibilitySmoke.globalActions"),
      token(files.realDeviceTest, "AutoJsAccessibilityService.waitForOperational"),
      token(files.smokeActivity, "LONG_CLICKED_TEXT"),
      token(files.smokeActivity, "setOnLongClickListener"),
      token(files.deviceMatrix, "deviceProfile=accessibility"),
      token(files.deviceMatrix, "globalActions=true"),
      token(files.testing, "selector `click`"),
    ]),
    scenario("denial_and_unsupported_tiers", "Missing service, missing capability, timeout, and gesture/root/Shizuku boundaries stay fail-closed", [
      token(files.accessibilityTest, "androidAccessibilitySelectorV2RejectsUntilProviderIsConnected"),
      token(files.accessibilityTest, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED/accessibility/findOne"),
      token(files.accessibilityTest, "a11y.selector.timeout_cancel"),
      token(files.permissionManifest, 'const val ACCESSIBILITY = "accessibility"'),
      token(files.providerRegistry, "gesture,root,Shizuku,raw input, and shell fallback paths require separate capabilities"),
      token(files.p13AccessibilityReport, "`swipe`, `gesture`, and `powerDialog` stay stable-denied"),
      token(files.p13AccessibilityReport, "`input.gesture.root` for root/shell-backed input"),
      token(files.p13ShellReport, "Shizuku execution stays stable-denied"),
      token(files.conformanceTest, "ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED"),
      token(files.shellTypes, "execShizuku(command: string"),
      token(files.securityModel, "Reserved future input automation labels split gesture authority away"),
      checkTokenValue("p14_lifecycle_disabled_denial", p14LifecycleDecision.disabledDenialBoundaryReady === true),
      checkTokenValue("p13_shell_status", p13ShellDecision.status === "ready" || p13ShellGenerated.status === "ready"),
    ]),
    scenario("device_archives_open", "Required Android 12/15+, safe global action, and unsupported-tier archives remain future work", [
      token(files.matrixDoc, "Android 12 class and"),
      token(files.phase14Report, "accessibility_selector_android12_action_archive"),
      token(files.phase14Report, "accessibility_selector_android15_plus_action_archive"),
      token(files.phase14Report, "accessibility_selector_global_actions_recents_archive"),
      token(files.phase14Report, "accessibility_selector_unsupported_tier_diagnostics_archive"),
      checkTokenValue("manual_android_12_row", hasRow(rows, "android_12_class", "missing_device_archive")),
      checkTokenValue("manual_android_15_plus_row", hasRow(rows, "android_15_plus_class", "missing_device_archive")),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-12 gate", [
      token(files.gradle14, "verifyNodePhase14AccessibilitySelectorActionMatrix"),
      token(files.gradle14, "verify-node-phase14-accessibility-selector-action-matrix.js"),
      token(files.phase14Tasks, "Selector/action matrix gate deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14AccessibilitySelectorActionMatrix"),
      token(files.testing, ":app:verifyNodePhase14AccessibilitySelectorActionMatrix"),
      token(files.ciGates, ":app:verifyNodePhase14AccessibilitySelectorActionMatrix"),
      token(files.surfaceManifestDoc, "P14-12 adds the selector/action real-device matrix"),
      token(files.surfaceManifest, "docs/nodejs/phase14/accessibility-selector-action-matrix.json"),
    ]),
  ];
  const checks = [
    check("phase13-accessibility-generated-report-present", files.p13AccessibilityGeneratedReport.exists, "The P13-19 accessibility selector report must be generated before the P14-12 gate."),
    check("phase13-shell-generated-report-present", files.p13ShellGeneratedReport.exists, "The P13-24 shell/root/Shizuku report must be generated before the P14-12 gate."),
    check("phase14-accessibility-lifecycle-generated-report-present", files.p14AccessibilityLifecycleGeneratedReport.exists, "The P14-11 accessibility lifecycle report must be generated before the P14-12 gate."),
    check("matrix-json-present", files.matrixJson.exists && matrix.schema === "autojs6-node-phase14-accessibility-selector-action-matrix-v1", "P14-12 selector/action matrix JSON must exist."),
    check("matrix-doc-present", files.matrixDoc.exists && files.matrixDoc.text.includes("Phase 14 Accessibility Selector Action Matrix"), "P14-12 matrix documentation must exist."),
    check(
      "contract-device-classes-imported",
      sameSet(contractMatrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES) &&
        sameSet(matrix.requiredDeviceClasses || [], REQUIRED_DEVICE_CLASSES),
      "P14-12 matrix must import the shared Phase 14 required device classes.",
    ),
    check(
      "manual-smoke-anchor-expanded",
      files.realDeviceTest.text.includes('method = "longClick"') &&
        files.realDeviceTest.text.includes('provider.dispatchAccessibilityRequest("recentApps")') &&
        files.smokeActivity.text.includes("setOnLongClickListener"),
      "The manual accessibility profile must include longClick and optional recentApps anchors.",
    ),
    check(
      "surface-provider-poc-not-promoted",
      proSurface &&
        proSurface.status === "provider_poc" &&
        !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must stay provider_poc, not opt-in or default promoted.",
    ),
    check(
      "surface-evidence",
      proSurface &&
        includesAll(proSurface.evidence || [], [
          "docs/nodejs/phase14/accessibility-selector-action-matrix.json",
          "docs/nodejs/phase14/PHASE14_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md",
          "docs/nodejs/reports/phase14/P14_12_ACCESSIBILITY_SELECTOR_ACTION_MATRIX.md",
          "tools/nodejs/phase14/verify-node-phase14-accessibility-selector-action-matrix.js",
          ":app:verifyNodePhase14AccessibilitySelectorActionMatrix",
          "build/reports/nodejs/phase14-accessibility-selector-action-matrix.json",
          "build/reports/nodejs/phase14-accessibility-selector-action-matrix.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-12 matrix, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "task-plan-keeps-p14-12-open",
      files.phase14Tasks.text.includes("- [ ] P14-12:") &&
        files.phase14Tasks.text.includes("Selector/action matrix gate deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-12 must remain unchecked while the matrix records open device/action promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14AccessibilitySelectorActionMatrix") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14AccessibilitySelectorActionMatrix") &&
        files.testing.text.includes(":app:verifyNodePhase14AccessibilitySelectorActionMatrix") &&
        files.ciGates.text.includes(":app:verifyNodePhase14AccessibilitySelectorActionMatrix"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-12 selector/action matrix gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-accessibility-selector-action-matrix-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14AccessibilitySelectorActionMatrix",
    task: "P14-12",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    matrix: {
      status: matrix.status || "missing",
      promotionState: matrix.promotionState || "missing",
      requiredDeviceClasses: matrix.requiredDeviceClasses || [],
      evidenceRows: rows.map((row) => ({
        id: row.id,
        deviceClass: row.deviceClass,
        status: row.status,
        reportPath: row.reportPath,
      })),
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      matrixReady: scenarioById(scenarios, "matrix_schema_and_required_slots").status === "ready",
      selectorFacadeImported: scenarioById(scenarios, "selector_facade_imported").status === "ready",
      realDeviceProfileAnchorReady: scenarioById(scenarios, "real_device_profile_anchor").status === "ready",
      denialAndUnsupportedTiersReady: scenarioById(scenarios, "denial_and_unsupported_tiers").status === "ready",
      requiredDeviceArchivesReady: false,
      releaseBlockingBlockers: 0,
      futurePromotionBlockers: OPEN_BLOCKERS.length,
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

function checkTokenValue(tokenName, present) {
  return {
    file: "parsed-json",
    token: tokenName,
    present: Boolean(present),
  };
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function scenarioById(scenarios, id) {
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function hasRow(rows, deviceClass, status) {
  return rows.some((row) => row.deviceClass === deviceClass && row.status === status);
}

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
}

function sameSet(values, requiredValues) {
  return Array.isArray(values) &&
    values.length === requiredValues.length &&
    requiredValues.every((value) => values.includes(value));
}

function parseJson(text) {
  try {
    return JSON.parse(stripBom(text || "{}"));
  } catch (_) {
    return {};
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 14 Accessibility Selector Action Matrix",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Matrix",
    "",
    `- Status: \`${report.matrix.status}\``,
    `- Required device classes: ${report.matrix.requiredDeviceClasses.map((entry) => `\`${entry}\``).join(", ")}`,
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
  lines.push("", "## Open Blockers", "", "| Blocker | Classification | Required Evidence |", "| --- | --- | --- |");
  report.blockers.forEach((entry) => {
    lines.push(`| \`${entry.id}\` | \`${entry.classification}\` | ${escapeTable(entry.requiredEvidence)} |`);
  });
  return lines.join("\n") + "\n";
}

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-node-phase14-accessibility-selector-action-matrix.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else {
      throw new Error("Unexpected argument: " + arg);
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

main();
