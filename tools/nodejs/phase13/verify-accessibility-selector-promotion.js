#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-accessibility-selector-promotion";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  executionModePolicy: "app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  accessibilityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  phase11SelectorGaps: "docs/nodejs/reports/phase11/RHINO_SELECTOR_DSL_BRIDGE_GAPS.md",
  phase11CleanupSecurity: "docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  deviceMatrix: "docs/nodejs/DEVICE_MATRIX.md",
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
  console.log("Phase 13 accessibility selector promotion: " + report.decision.status);
  console.log("Phase 13 accessibility selector promotion blockers: " + report.blockers.length);
  console.log("Phase 13 accessibility selector promotion policy violations: " + report.policyViolations.length);
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
  const proSurface = surface(files.surfaceManifest.text, "pro.ui_automation_media");
  const scenarios = [
    scenario("selector_json_bridge", "Direct accessibility module exposes JSON selector descriptors and controlled node snapshots", [
      token(files.protocol, 'val accessibilityMethods = setOf('),
      token(files.protocol, '"findOne",'),
      token(files.protocol, '"findAll",'),
      token(files.protocol, '"longClick",'),
      token(files.protocol, '"scrollForward",'),
      token(files.protocol, 'private fun AccessibilityNodeInfo.matchesAccessibilitySelector'),
      token(files.protocol, 'private fun AccessibilityNodeInfo.toAccessibilityJson()'),
      token(files.nodeBridgeSources, 'const __autojs6_accessibility_selector_fields = Object.freeze(['),
      token(files.nodeBridgeSources, 'function findOne(selector, options)'),
      token(files.nodeBridgeSources, 'return Object.freeze(selector);'),
      token(files.accessibilityTypes, 'export interface SelectorDescriptor'),
      token(files.accessibilityTypes, 'findOne(selector: SelectorDescriptor, options?: AutoJs6Node.BridgeCallOptions): Promise<UiNodeSnapshot | null>;'),
      token(files.typeSmoke, 'const selector: accessibility.SelectorDescriptor = accessibility.text("OK");'),
      token(files.typeSmoke, 'const boundedAll = await accessibility.findAll(accessibility.boundsContains({ left: 0, top: 0, right: 100, bottom: 100 }), { timeoutMs: 1000 });'),
    ]),
    scenario("rhino_compat_selector_facade", "Rhino compat exposes an explicit Promise-based selector DSL over the same bridge", [
      token(files.nodeBridgeSources, 'function __autojs6_rhino_selector_handle(descriptor)'),
      token(files.nodeBridgeSources, 'exists: function(options)'),
      token(files.nodeBridgeSources, 'selector.recents = selector.recentApps;'),
      token(files.rhinoCompatTypes, 'export interface RhinoSelectorHandle'),
      token(files.rhinoCompatTypes, 'exists(options?: FindOneOptions): Promise<boolean>;'),
      token(files.typeSmoke, 'const rhinoSelector: rhinoCompat.RhinoSelectorHandle = rhinoCompat.text("OK").descContains("Confirm").clickable();'),
      token(files.typeSmoke, 'const rhinoExists: boolean = await rhinoCompat.selector.id("submit").enabled().exists(1000);'),
      token(files.phase11SelectorGaps, 'S11-16'),
      token(files.phase11CleanupSecurity, 'verified_pending_zero_after_timeout_cancel'),
    ], [
      "Add more Rhino selector aliases and chain helpers beyond the current v2 descriptor subset.",
      "Define exact Rhino waitFor/pickup/detect compatibility semantics or keep them as explicit stable denials.",
    ]),
    scenario("live_accessibility_gate", "Android provider is live only when the AutoJs6 accessibility service is visible", [
      token(files.providerRegistry, '"phase13Promotion" to "P13-19 partial accessibility selector promotion evidence gate"'),
      token(files.providerRegistry, '"liveMethods" to "isEnabled,ensureEnabled,findByText,clickText,findOne,findAll,click,longClick,setText,scrollForward,scrollBackward,back,home,recentApps"'),
      token(files.protocol, 'AutoJsAccessibilityService.hasInstance()'),
      token(files.protocol, 'an enabled accessibility capability provider in the current process'),
      token(files.accessibilityTest, 'androidAccessibilitySelectorV2RejectsUntilProviderIsConnected'),
      token(files.accessibilityTest, 'jsAccessibilitySelectorV2HonorsTimeoutAndCancellation'),
      token(files.testingDoc, 'verifyNodeLiveAccessibilityImageGate'),
      token(files.deviceMatrix, 'profile: `accessibility`'),
    ], [
      "Add packaged APK install-run evidence for an accessibility-service-backed provider lifecycle.",
      "Add controlled service-enabled real-device matrix for selector actions, Back/Home/Recents, and denial paths.",
    ]),
    scenario("security_boundaries", "Raw node objects and privileged gesture/root/Shizuku paths remain separated", [
      token(files.providerRegistry, '"blockedMethods" to "swipe,gesture,powerDialog,waitFor,rawNode,rawUiObject,rootOrShizukuGestures"'),
      token(files.providerRegistry, '"gesturePolicy" to "gesture,root,Shizuku,raw input, and shell fallback paths require separate capabilities and must not be implied by accessibility"'),
      token(files.accessibilityTest, 'assertFalse(NodeBridgeModules.supports("accessibility", "pickup"))'),
      token(files.accessibilityTest, 'a11y.selector.rawLeak=true|true|true|-1'),
      token(files.securityModel, 'No raw `AccessibilityService`, `AccessibilityNodeInfo`, `UiObject`, or live selector object crosses into Node.'),
      token(files.permissionManifest, 'const val ACCESSIBILITY = "accessibility"'),
      token(files.executionModePolicy, '"accessibility"'),
    ], [
      "Design a separate gesture provider for swipe/gesture/powerDialog before exposing those APIs.",
      "Separate root/Shizuku input automation into explicit capabilities rather than silently falling back from accessibility.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-19 as partial", [
      token(files.p13Report, 'Status: partial'),
      token(files.phase13Tasks, 'P13-19 partial accessibility selector promotion gate'),
      token(files.gradleGate, 'verifyNodePhase13AccessibilitySelectorPromotion'),
      token(files.testingDoc, 'verifyNodePhase13AccessibilitySelectorPromotion'),
      token(files.ciGatesDoc, 'verifyNodePhase13AccessibilitySelectorPromotion'),
      token(files.compatibilityProfile, 'P13-19 records the current accessibility selector promotion state'),
      token(files.securityModel, 'P13-19 records accessibility/selector promotion as partial'),
      token(files.proComparison, 'P13-19 records selector/accessibility as partial'),
      token(files.proAutoxNotes, 'P13-19 records selector/accessibility as partial'),
      token(files.capabilityMatrix, 'P13-19 accessibility selector promotion'),
      token(files.typesReadme, 'P13-19 records the partial accessibility selector promotion gate'),
      token(files.deviceMatrix, 'P13-19 keeps optional accessibility smoke manual'),
      token(files.surfaceManifestDoc, 'phase13-accessibility-selector-promotion'),
    ], [
      "Keep P13-19 checklist open until advanced automation, packaged service, and root/Shizuku tier evidence land.",
    ]),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-19"), "P13-19 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-19 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.ui_automation_media` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.ui_automation_media` must show partial/poc state, not planned or promoted.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.ui_automation_media` must not be promoted by the P13-19 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-accessibility-selector-promotion")),
      "`pro.ui_automation_media` surface must reference P13-19 evidence.",
    ),
    check(
      "provider-not-overclaimed",
      files.providerRegistry.text.includes('"blockedMethods" to "swipe,gesture,powerDialog,waitFor,rawNode,rawUiObject,rootOrShizukuGestures"') &&
        files.providerRegistry.text.includes("blocked_until_packaged_accessibility_service_settings_lifecycle_and_disclosure_evidence"),
      "`accessibility` provider must keep advanced automation and packaged service promotion blocked.",
    ),
    check(
      "gesture-not-overclaimed",
      !files.protocol.text.includes('"swipe"') &&
        !files.protocol.text.includes('"gesture"') &&
        !files.protocol.text.includes('"powerDialog"'),
      "P13-19 must not silently expose swipe, gesture, or powerDialog through the current accessibility provider.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-accessibility-selector-promotion-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13AccessibilitySelectorPromotion",
    task: "P13-19",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    surface: proSurface || {
      id: "pro.ui_automation_media",
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
      directSelectorBridgeReady: scenarioById(scenarios, "selector_json_bridge").status === "ready",
      rhinoCompatSelectorReady: scenarioById(scenarios, "rhino_compat_selector_facade").status === "ready",
      liveAccessibilityGateReady: scenarioById(scenarios, "live_accessibility_gate").status !== "missing_evidence",
      advancedGestureProviderReady: false,
      packagedAccessibilityProviderReady: false,
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
  return scenarios.find((entry) => entry.id === id) || { status: "missing" };
}

function surface(text, id) {
  try {
    const manifest = JSON.parse(stripBom(text));
    return (manifest.surfaces || []).find((entry) => entry.id === id) || null;
  } catch (_) {
    return null;
  }
}

function renderMarkdown(report) {
  const lines = [
    "# Phase 13 Accessibility Selector Promotion",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
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

function parseArgs(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--repo-root") {
      options.repoRoot = args[++index];
    } else if (arg === "--report-dir") {
      options.reportDir = args[++index];
    } else if (arg === "--fail-on-blocked") {
      options.failOnBlocked = true;
    }
  }
  return options;
}

function read(file) {
  try {
    return fs.readFileSync(file, "utf8");
  } catch (_) {
    return "";
  }
}

function stripBom(text) {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|");
}

main();
