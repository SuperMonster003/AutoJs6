#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase14-accessibility-lifecycle";
const REQUIRED_NODE_PERMISSIONS = Object.freeze(["accessibility"]);
const REQUIRED_ANDROID_PERMISSIONS = Object.freeze(["android.permission.BIND_ACCESSIBILITY_SERVICE"]);
const REQUIRED_RUNTIME_MARKERS = Object.freeze([
  "packaged.accessibility_lifecycle.ready=true",
  "packaged.accessibility_lifecycle.metadata=true",
  "packaged.accessibility_lifecycle.privacy_policy=true",
  "packaged.accessibility_lifecycle.settings_contract=true",
  "packaged.accessibility_lifecycle.packaged_policy=true",
  "packaged.accessibility_lifecycle.disabled_state=true",
  "packaged.accessibility_lifecycle.denial_paths=true",
  "packaged.accessibility_lifecycle.settings_intent=android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS",
  "packaged.accessibility_lifecycle.raw_globals_absent=true",
  "packaged.accessibility_lifecycle.unsupported_helpers_absent=true",
  "packaged.accessibility_lifecycle.pending=0",
]);
const REQUIRED_DENIED_METHODS = Object.freeze([
  "findOne",
  "findAll",
  "click",
  "longClick",
  "setText",
  "scrollForward",
  "scrollBackward",
]);
const OPEN_BLOCKERS = Object.freeze([
  {
    id: "accessibility_packaged_service_enabled_archive",
    classification: "future_promotion",
    requiredEvidence: "Archived packaged install-run with user-enabled accessibility service and provider availability on Android 12 and Android 15+.",
  },
  {
    id: "accessibility_settings_round_trip_archive",
    classification: "future_promotion",
    requiredEvidence: "Accessibility settings launch guidance and return-to-app state recorded without auto-enabling the service.",
  },
  {
    id: "accessibility_task_removal_relaunch_archive",
    classification: "future_promotion",
    requiredEvidence: "Task removal cleanup and package relaunch behavior for the packaged accessibility lifecycle.",
  },
  {
    id: "accessibility_disconnect_missing_capability_denial_archive",
    classification: "future_promotion",
    requiredEvidence: "Service disconnect cleanup and packaged missing-capability denial with raw selector/service handles absent.",
  },
]);

const FILES = {
  phase14Tasks: "docs/nodejs/reports/phase14/PHASE14_NODE_V1_2_PROMOTION_TASKS.md",
  phase14Report: "docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md",
  phase14Readme: "docs/nodejs/reports/phase14/README.md",
  planJson: "docs/nodejs/phase14/accessibility-lifecycle-plan.json",
  planDoc: "docs/nodejs/phase14/PHASE14_ACCESSIBILITY_LIFECYCLE.md",
  surfaceManifest: "docs/nodejs/phase14/phase14-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase14/PHASE14_SURFACE_MANIFEST.md",
  promotionContractsJson: "docs/nodejs/phase14/phase14-promotion-contracts.json",
  p13Report: "docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md",
  p13Verifier: "tools/nodejs/phase13/verify-accessibility-selector-promotion.js",
  packagedTest: "app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  providerRegistry: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  accessibilityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  accessibilityTypes: "docs/nodejs/types/autojs6-node/accessibility.d.ts",
  autojsGlobalTypes: "docs/nodejs/types/autojs6-node/autojs_global.d.ts",
  privacyPolicy: "docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md",
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
  console.log("Phase 14 accessibility lifecycle: " + report.decision.status);
  console.log("Phase 14 accessibility lifecycle blockers: " + report.blockers.length);
  console.log("Phase 14 accessibility lifecycle policy violations: " + report.policyViolations.length);
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
    p14PackagedOverlayGeneratedReport: "phase14-packaged-overlay-positive-smoke.json",
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
  const plan = parseJson(files.planJson.text);
  const p13Generated = parseJson(files.p13AccessibilityGeneratedReport.text);
  const p14PackagedOverlayGenerated = parseJson(files.p14PackagedOverlayGeneratedReport.text);
  const proSurface = (manifest.surfaces || []).find((entry) => entry.id === "pro.ui_automation_media") || null;
  const fixture = plan.fixture || {};
  const manual = plan.manualArchiveRequirements || {};
  const p13Decision = p13Generated.decision || {};
  const p14PackagedOverlayDecision = p14PackagedOverlayGenerated.decision || {};
  const scenarios = [
    scenario("packaged_fixture_contract", "Packaged accessibility lifecycle fixture declares capability, service permission, and runtime markers", [
      checkTokenValue("schema", plan.schema === "autojs6-node-phase14-accessibility-lifecycle-v1"),
      checkTokenValue("task", plan.task === "P14-11"),
      checkTokenValue("status", plan.status === "partial"),
      checkTokenValue("promotionState", plan.promotionState === "provider_poc"),
      checkTokenValue("node_permissions", includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS)),
      checkTokenValue("android_permissions", includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS)),
      checkTokenValue("runtime_markers", includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS)),
      checkTokenValue("denied_methods", includesAll(fixture.deniedMethods || [], REQUIRED_DENIED_METHODS)),
      token(files.packagedTest, "buildAccessibilityLifecycleEmbeddedNodePackagedApk"),
      token(files.packagedTest, "buildPackagedAccessibilityLifecycleApk"),
      token(files.packagedTest, "ACCESSIBILITY_LIFECYCLE_NODE_PERMISSIONS"),
      token(files.packagedTest, "ACCESSIBILITY_LIFECYCLE_RUNTIME_MARKERS"),
      token(files.packagedTest, "PackagedNodeAccessibilityLifecycleSmoke_v1.0.0.apk"),
      token(files.packagedTest, 'extraPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE")'),
      token(files.packagedTest, "packaged.accessibility_lifecycle.ready=true"),
      token(files.packagedTest, "packaged.accessibility_lifecycle.denial_paths=true"),
    ]),
    scenario("privacy_settings_contract", "Privacy disclosure policy records the accessibility settings and packaged denial contract", [
      token(files.packagedTest, "privacyDisclosurePolicy"),
      token(files.packagedTest, "android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS"),
      token(files.packagedTest, "accessibility_service_description"),
      token(files.packagedTest, "deny_without_reviewed_disclosure_manifest"),
      token(files.nodeBridgeSources, "accessibility_service_description"),
      token(files.nodeBridgeSources, "android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS"),
      token(files.nodeBridgeSources, 'packagedBehavior: "deny_without_reviewed_disclosure_manifest"'),
      token(files.autojsGlobalTypes, "readonly settingsIntent: string | null;"),
      token(files.privacyPolicy, "defaultAction=deny_without_reviewed_disclosure_manifest"),
    ]),
    scenario("disabled_denial_boundary", "Disabled or unavailable accessibility service leaves selector/action paths fail-closed", [
      token(files.packagedTest, "const enabled = await accessibility.isEnabled"),
      token(files.packagedTest, "const ensured = await accessibility.ensureEnabled({ prompt: false"),
      token(files.packagedTest, "const deniedActions = ["),
      token(files.packagedTest, "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.packagedTest, "bridge.__test.pendingCount()"),
      token(files.accessibilityTest, "androidAccessibilitySelectorV2RejectsUntilProviderIsConnected"),
      token(files.accessibilityTest, "a11y.selector.android.denied=findOne:ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED"),
      token(files.protocol, "AutoJsAccessibilityService.hasInstance()"),
      token(files.providerRegistry, "blocked_until_packaged_accessibility_service_settings_lifecycle_and_disclosure_evidence"),
      checkTokenValue("denied_methods_in_fixture", REQUIRED_DENIED_METHODS.every((method) => files.packagedTest.text.includes(`"${method}"`))),
    ]),
    scenario("device_archives_open", "User-enabled service, settings round trip, task removal, relaunch, disconnect, and missing-capability archives remain future work", [
      checkTokenValue("manual_device_classes", includesAll(manual.requiredDeviceClasses || [], ["android_12_class", "android_15_plus_class"])),
      checkTokenValue("requires_user_enabled_service", manual.requiresUserEnabledAccessibilityService === true),
      checkTokenValue("requires_settings_round_trip", manual.requiresSettingsRoundTrip === true),
      checkTokenValue("must_not_auto_enable_service", manual.mustNotAutoEnableService === true),
      token(files.planDoc, "requiresUserEnabledAccessibilityService"),
      token(files.planDoc, "mustNotAutoEnableService"),
      token(files.phase14Report, "accessibility_packaged_service_enabled_archive"),
      token(files.phase14Report, "accessibility_settings_round_trip_archive"),
      token(files.phase14Report, "accessibility_task_removal_relaunch_archive"),
      token(files.phase14Report, "accessibility_disconnect_missing_capability_denial_archive"),
    ], OPEN_BLOCKERS.map((entry) => entry.id)),
    scenario("phase13_prerequisites_imported", "P13-19 selector bridge and live denial evidence are imported as prerequisites", [
      token(files.p13AccessibilityGeneratedReport, '"directSelectorBridgeReady": true'),
      token(files.p13AccessibilityGeneratedReport, '"liveAccessibilityGateReady": true'),
      token(files.p13AccessibilityGeneratedReport, '"packagedAccessibilityProviderReady": false'),
      token(files.p13Report, "Status: partial"),
      token(files.p13Verifier, "verifyNodePhase13AccessibilitySelectorPromotion"),
      checkTokenValue("p13_status", p13Decision.status === "partial"),
      checkTokenValue("p13_direct_selector", p13Decision.directSelectorBridgeReady === true),
      checkTokenValue("p13_live_gate", p13Decision.liveAccessibilityGateReady === true),
      checkTokenValue("p13_packaged_not_ready", p13Decision.packagedAccessibilityProviderReady === false),
      checkTokenValue("p14_overlay_prereq", p14PackagedOverlayDecision.status === "partial"),
    ]),
    scenario("docs_surface_and_gradle_wiring", "Phase 14 docs, surface manifest, and Gradle expose the P14-11 gate", [
      token(files.gradle14, "verifyNodePhase14AccessibilityLifecycle"),
      token(files.gradle14, "verify-node-phase14-accessibility-lifecycle.js"),
      token(files.phase14Tasks, "Packaged lifecycle fixture deployed:"),
      token(files.phase14Readme, ":app:verifyNodePhase14AccessibilityLifecycle"),
      token(files.testing, ":app:verifyNodePhase14AccessibilityLifecycle"),
      token(files.ciGates, ":app:verifyNodePhase14AccessibilityLifecycle"),
      token(files.surfaceManifestDoc, "P14-11 adds the packaged accessibility lifecycle fixture"),
      token(files.surfaceManifest, "docs/nodejs/phase14/accessibility-lifecycle-plan.json"),
    ]),
  ];
  const disclosure = contracts.packagedHighPrivilegeDisclosure || {};
  const checks = [
    check("phase13-accessibility-generated-report-present", files.p13AccessibilityGeneratedReport.exists, "The P13-19 accessibility selector report must be generated before the P14-11 gate."),
    check("phase14-packaged-overlay-generated-report-present", files.p14PackagedOverlayGeneratedReport.exists, "The P14-10 packaged overlay report must be generated before the P14-11 gate."),
    check("plan-json-present", files.planJson.exists && plan.schema === "autojs6-node-phase14-accessibility-lifecycle-v1", "P14-11 accessibility lifecycle plan JSON must exist."),
    check("plan-doc-present", files.planDoc.exists && files.planDoc.text.includes("Phase 14 Accessibility Lifecycle"), "P14-11 plan documentation must exist."),
    check(
      "fixture-contract",
      includesAll(fixture.requiredNodePermissions || [], REQUIRED_NODE_PERMISSIONS) &&
        includesAll(fixture.requiredAndroidPermissions || [], REQUIRED_ANDROID_PERMISSIONS) &&
        includesAll(fixture.runtimeMarkers || [], REQUIRED_RUNTIME_MARKERS) &&
        includesAll(fixture.deniedMethods || [], REQUIRED_DENIED_METHODS) &&
        files.packagedTest.text.includes("buildPackagedAccessibilityLifecycleApk"),
      "P14-11 fixture must declare accessibility metadata, service permission, runtime markers, denied methods, and a packaged builder.",
    ),
    check(
      "privacy-settings-contract",
      files.packagedTest.text.includes("privacyDisclosurePolicy") &&
        files.packagedTest.text.includes("android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS") &&
        files.nodeBridgeSources.text.includes("accessibility_service_description") &&
        files.nodeBridgeSources.text.includes("android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS"),
      "P14-11 must verify the accessibility privacy disclosure and settings contract.",
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
          "docs/nodejs/phase14/accessibility-lifecycle-plan.json",
          "docs/nodejs/phase14/PHASE14_ACCESSIBILITY_LIFECYCLE.md",
          "docs/nodejs/reports/phase14/P14_11_ACCESSIBILITY_LIFECYCLE.md",
          "tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js",
          ":app:verifyNodePhase14AccessibilityLifecycle",
          "build/reports/nodejs/phase14-accessibility-lifecycle.json",
          "build/reports/nodejs/phase14-accessibility-lifecycle.md",
        ]),
      "`pro.ui_automation_media` evidence must include the P14-11 fixture, docs, verifier, Gradle gate, and output reports.",
    ),
    check(
      "contracts-cover-accessibility-fields",
      includesAll(disclosure.requiredForSurfaces || [], ["accessibility"]) &&
        includesAll(disclosure.requiredFields || [], [
          "manifestMetadata",
          "installReviewCopy",
          "runReviewCopy",
          "permissionDeniedDiagnostic",
          "taskRemovalCleanup",
          "relaunchBehavior",
          "privacyDisclosure",
          "rollbackBehavior",
        ]),
      "Phase 14 promotion contracts must cover accessibility disclosure, diagnostics, cleanup, relaunch, privacy, and rollback fields.",
    ),
    check(
      "task-plan-keeps-p14-11-open",
      files.phase14Tasks.text.includes("- [ ] P14-11:") &&
        files.phase14Tasks.text.includes("Packaged lifecycle fixture deployed:") &&
        OPEN_BLOCKERS.every((entry) => files.phase14Tasks.text.includes(entry.id)),
      "P14-11 must remain unchecked while the fixture records open packaged/device promotion blockers.",
    ),
    check(
      "docs-and-gradle-wiring",
      files.gradle14.text.includes("verifyNodePhase14AccessibilityLifecycle") &&
        files.phase14Readme.text.includes(":app:verifyNodePhase14AccessibilityLifecycle") &&
        files.testing.text.includes(":app:verifyNodePhase14AccessibilityLifecycle") &&
        files.ciGates.text.includes(":app:verifyNodePhase14AccessibilityLifecycle"),
      "Gradle, README, TESTING, and CI_GATES must reference the P14-11 accessibility lifecycle gate.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  return {
    schema: "autojs6-node-phase14-accessibility-lifecycle-report-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase14AccessibilityLifecycle",
    task: "P14-11",
    status: policyViolations.length === 0 ? "partial" : "failed",
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    fixture: {
      projectName: fixture.projectName || "missing",
      packageName: fixture.packageName || "missing",
      apkName: fixture.apkName || "missing",
      requiredNodePermissions: fixture.requiredNodePermissions || [],
      requiredAndroidPermissions: fixture.requiredAndroidPermissions || [],
      runtimeMarkers: fixture.runtimeMarkers || [],
      deniedMethods: fixture.deniedMethods || [],
    },
    scenarios,
    checks,
    policyViolations,
    blockers: OPEN_BLOCKERS.map((entry) => ({ ...entry, status: "open" })),
    decision: {
      status: policyViolations.length === 0 ? "partial" : "failed",
      promotionState: "provider_poc",
      p14TaskComplete: false,
      packagedFixtureReady: scenarioById(scenarios, "packaged_fixture_contract").status === "ready",
      privacySettingsContractReady: scenarioById(scenarios, "privacy_settings_contract").status === "ready",
      disabledDenialBoundaryReady: scenarioById(scenarios, "disabled_denial_boundary").status === "ready",
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

function includesAll(values, requiredValues) {
  if (Array.isArray(values)) {
    return requiredValues.every((value) => values.includes(value));
  }
  return requiredValues.every((value) => String(values).includes(value));
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
    "# Phase 14 Accessibility Lifecycle",
    "",
    `- Gate: \`${report.gate}\``,
    `- Task: \`${report.task}\``,
    `- Status: \`${report.decision.status}\``,
    `- Promotion state: \`${report.decision.promotionState}\``,
    `- Policy violations: ${report.policyViolations.length}`,
    `- Future promotion blockers: ${report.decision.futurePromotionBlockers}`,
    "",
    "## Fixture",
    "",
    `- Project: \`${report.fixture.projectName}\``,
    `- Package: \`${report.fixture.packageName}\``,
    `- APK: \`${report.fixture.apkName}\``,
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
      console.log("Usage: node verify-node-phase14-accessibility-lifecycle.js [--repo-root dir] [--report-dir dir]");
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
