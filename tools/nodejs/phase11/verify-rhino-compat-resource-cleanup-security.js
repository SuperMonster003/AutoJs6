#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "rhino-compat-resource-cleanup-security-verification";
const REQUIRED_SURFACES = Object.freeze([
  "image",
  "selector",
  "event",
  "ui",
  "floaty",
  "db",
  "websocket_fetch",
]);
const DEVICE_GATE = "verifyNodeRhinoCompatResourceCleanupSecurity";
const HOST_GATE = "verifyNodeRhinoCompatResourceCleanupSecurityHost";
const DEVICE_SELECTORS = Object.freeze([
  "org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest",
  "org.autojs.autojs.engine.NodeBridgeResourceLeakStressInstrumentationTest",
  "org.autojs.autojs.engine.NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing",
  "org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing",
  "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport",
  "org.autojs.autojs.engine.NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
  "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
  "org.autojs.autojs.engine.NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
  "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing",
  "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes",
  "org.autojs.autojs.engine.NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderRegistersAndClosesResources",
  "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled",
  "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#jsFetchPolicyHardeningUsesFakeTransport",
  "org.autojs.autojs.engine.NodeFetchBridgeInstrumentationTest#androidFetchProviderStaysDisabledInNormalBuilds",
  "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled",
  "org.autojs.autojs.engine.NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderStaysDisabledInNormalBuilds",
  "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase5BridgeNetworkUiAndShellBoundariesStayDenied",
  "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7CapabilityBypassDeniesBeforeProviderDispatch",
  "org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest#phase7ProviderPolicyAndPluginEscapesStayDeniedBeforeDispatch",
]);

const FILES = {
  resourceRegistryTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceRegistryInstrumentationTest.kt",
  resourceLeakStressTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeResourceLeakStressInstrumentationTest.kt",
  imageTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeImageBridgeExpansionInstrumentationTest.kt",
  autoJsBridgeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt",
  selectorTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAccessibilitySelectorBridgeInstrumentationTest.kt",
  uiTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeUiBridgeInstrumentationTest.kt",
  databaseTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeDatabaseBridgeInstrumentationTest.kt",
  fetchTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeFetchBridgeInstrumentationTest.kt",
  websocketTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeWebSocketBridgeInstrumentationTest.kt",
  securityTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeSecurityRegressionInstrumentationTest.kt",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
  eventsDesign: "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
  uiDesign: "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md",
  floatyDesign: "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
  appGradle: "app/build.gradle.kts",
  phase11Gradle: "app/node-phase11-gate.gradle.kts",
  reportDoc: "docs/nodejs/reports/phase11/RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
  testing: "docs/nodejs/TESTING.md",
  taskPlan: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
};

const SURFACE_EVIDENCE = Object.freeze([
  {
    id: "image",
    status: "verified_zero_after_recycle_or_stop",
    acceptance: "opaque image handles are recycled; screen-capture handles use explicit stop; bridge pending count drains to zero",
    evidence: [
      "NodeImageBridgeExpansionInstrumentationTest#jsImageExpansionUsesFakeTransportAndBlocksShadowing",
      "NodeAutoJsBridgeInstrumentationTest#mediaProjectionAndImageModulesUseFakeTransportAndBlockShadowing",
      "NodeBridgeResourceRegistryInstrumentationTest#directRegistryClosesLeakedBridgeResourcesAndIsIdempotent",
    ],
    residualPolicy: "none for fake/provider-owned handles after recycle, stop, or destroy; live screenshot permission remains fail-closed until provider ownership is available",
  },
  {
    id: "selector",
    status: "verified_pending_zero_after_timeout_cancel",
    acceptance: "selector facade timeout/cancellation paths reject with stable bridge errors and leave pending=0",
    evidence: [
      "NodeAccessibilitySelectorBridgeInstrumentationTest#jsRhinoCompatSelectorFacadeUsesFakeTransport",
      "NodeAccessibilitySelectorBridgeInstrumentationTest#jsAccessibilitySelectorV2HonorsTimeoutAndCancellation",
    ],
    residualPolicy: "no raw AccessibilityNodeInfo crosses into Node; live disabled-provider paths fail closed with pending dispatch count zero",
  },
  {
    id: "event",
    status: "design_gated_no_resource",
    acceptance: "Rhino events/input observer compatibility is not exposed, so no native observer resources can be created by the compat facade",
    evidence: [
      "docs/nodejs/design/RHINO_EVENTS_INPUT_OBSERVER.md",
      "docs/nodejs/rhino-compat/status-manifest.json#events",
    ],
    residualPolicy: "future input_observer promotion must add resource registry ownership and zero-active-resource destroy checks before exposure",
  },
  {
    id: "ui",
    status: "verified_zero_after_close_destroy",
    acceptance: "opaque UiHandle sessions close explicitly, provider destroy removes owned UI sessions, and fake transport drains pending=0",
    evidence: [
      "NodeUiBridgeInstrumentationTest#jsUiModuleUsesFakeTransportForLayoutEventsAndPolicy",
      "NodeUiBridgeInstrumentationTest#androidUiProviderOpensActivityDeliversEventsAndCleansOwnedResource",
      "docs/nodejs/design/RHINO_UI_COMPATIBILITY.md#ui.run",
    ],
    residualPolicy: "Rhino ui.run/ui.post/raw View semantics remain blocked by design and create no Node-owned resources",
  },
  {
    id: "floaty",
    status: "blocked_by_design_no_resource",
    acceptance: "Rhino floaty/overlay compatibility is not exposed, so overlay resources cannot be created through Node compat",
    evidence: [
      "docs/nodejs/design/RHINO_FLOATY_COMPATIBILITY.md",
      "docs/nodejs/rhino-compat/status-manifest.json#floaty.window",
    ],
    residualPolicy: "future overlay provider must use explicit opaque handles, user-visible permission flow, and destroy/closeAll cleanup tests before exposure",
  },
  {
    id: "db",
    status: "verified_zero_after_close",
    acceptance: "database/sqlite handles close through the bridge resource registry and Android diagnostics return active_handle_count=0",
    evidence: [
      "NodeDatabaseBridgeInstrumentationTest#jsDatabaseBridgeUsesFakeTransportAndBlocksShadowing",
      "NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderPersistsTransactionsAndDeniesEscapes",
      "NodeDatabaseBridgeInstrumentationTest#androidDatabaseProviderRegistersAndClosesResources",
    ],
    residualPolicy: "SQL escape paths such as ATTACH and load_extension are denied before persistent external resources can be opened",
  },
  {
    id: "websocket_fetch",
    status: "verified_pending_zero_and_default_denial",
    acceptance: "fetch/WebSocket timeout, abort, service-destroy, invalid policy, and default-disabled Android provider paths leave diagnostics active counts at zero",
    evidence: [
      "NodeFetchBridgeInstrumentationTest#jsFetchModuleUsesFakeTransportAndKeepsRawModulesDisabled",
      "NodeFetchBridgeInstrumentationTest#jsFetchPolicyHardeningUsesFakeTransport",
      "NodeWebSocketBridgeInstrumentationTest#websocketMvpUsesControlledBridgeAndKeepsRawSocketsDisabled",
      "NodeWebSocketBridgeInstrumentationTest#androidWebSocketProviderStaysDisabledInNormalBuilds",
    ],
    residualPolicy: "live OkHttp destroy cleanup is covered by the existing experimental live fetch/WebSocket gates when autojs.nodejs.network.experimental=true",
  },
]);

function main() {
  const options = parseArgs(process.argv.slice(2));
  const repoRoot = path.resolve(options.repoRoot || path.resolve(__dirname, "../../.."));
  const reportDir = path.resolve(options.reportDir || path.join(repoRoot, "build/reports/nodejs"));
  const files = Object.fromEntries(
    Object.entries(FILES).map(([key, relative]) => [key, {
      relative,
      text: read(path.join(repoRoot, relative)),
    }]),
  );
  const manifestEntries = flattenManifestEntries(JSON.parse(files.statusManifest.text));
  const checks = buildChecks(files, manifestEntries);
  const policyViolations = checks.filter((check) => !check.passed).map((check) => check.message);
  const report = {
    schema: "autojs6-node-rhino-compat-resource-cleanup-security-verification-v1",
    generatedAt: new Date().toISOString(),
    gate: HOST_GATE,
    deviceGate: DEVICE_GATE,
    requiredSurfaces: REQUIRED_SURFACES,
    deviceSelectors: DEVICE_SELECTORS,
    surfaceEvidence: SURFACE_EVIDENCE,
    files: Object.fromEntries(Object.entries(files).map(([key, file]) => [key, file.relative])),
    checks,
    policyViolations,
    decision: {
      status: policyViolations.length === 0 ? "passed" : "failed",
      allSurfacesCovered: checkById(checks, "surface-evidence-covers-required-scope"),
      cleanupEvidencePresent: checkById(checks, "resource-cleanup-evidence"),
      securityBoundaryPresent: checkById(checks, "security-boundary-evidence"),
      hostReportReady: checkById(checks, "phase11-host-task"),
      deviceGateRegistered: checkById(checks, "gradle-device-gate"),
    },
  };

  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.json`), JSON.stringify(report, null, 2) + "\n");
  fs.writeFileSync(path.join(reportDir, `${REPORT_BASENAME}.md`), renderMarkdown(report));
  console.log("Rhino compat resource cleanup/security verification: " + report.decision.status);
  console.log("Rhino compat resource cleanup/security surfaces: " + REQUIRED_SURFACES.join(", "));
  console.log("Rhino compat resource cleanup/security policy violations: " + policyViolations.length);
  if (policyViolations.length > 0) {
    process.exitCode = 1;
  }
}

function buildChecks(files, manifestEntries) {
  const manifest = (id) => manifestEntryText(manifestEntries, id);
  const checks = [
    check(
      "surface-evidence-covers-required-scope",
      sameSet(SURFACE_EVIDENCE.map((surface) => surface.id), REQUIRED_SURFACES) &&
        SURFACE_EVIDENCE.every((surface) => surface.acceptance && surface.residualPolicy && surface.evidence.length > 0),
      "S11-30 report model must cover image, selector, event, ui, floaty, db, and websocket/fetch with acceptance and residual policy",
    ),
    check(
      "image-cleanup-evidence",
      includesAll(files.imageTest.text, [
        "image.expand.recycle.count=1",
        "image.expand.pending=0",
        "image.recycle(",
      ]) &&
        includesAll(files.autoJsBridgeTest.text, [
          "media_projection:requestScreenCapture|media_projection:nextImage",
          "media_projection:stop",
          "media.pending=0",
        ]) &&
        includesAll(files.resourceRegistryTest.text, [
          "screen-capturer-1",
          "image:execution.destroy",
          "embedded_script.bridge_resource.active_count",
          "embedded_script.bridge_resource.close_error_count",
        ]),
      "Image cleanup evidence must cover recycle, screenshot stop, registry destroy cleanup, and zero pending/active resources",
    ),
    check(
      "selector-cleanup-evidence",
      includesAll(files.selectorTest.text, [
        "rhino.compat.selector.pending=0",
        "a11y.selector.timeout_cancel",
        "NodeRuntimeErrorCodes.BRIDGE_TIMEOUT",
        "NodeRuntimeErrorCodes.BRIDGE_CANCELLED",
        "embedded_script.bridge_live_dispatch_pending_count",
      ]),
      "Selector evidence must cover Rhino compat pending=0 plus timeout/cancellation cleanup",
    ),
    check(
      "event-design-gate",
      includesAll(files.eventsDesign.text, [
        "S11-21",
        "design gate only",
        "Future implementation must register every active observer",
        "zero active `input_observer` resources after destroy",
        "blocked_by_design",
      ]) &&
        includesAll(manifest("events"), [
          "events.__module",
          "s11_21_raw_rhino_event_emitter_blocked",
          "node:events",
        ]),
      "Events/input observer compatibility must remain design-gated with no resource-creating facade",
    ),
    check(
      "ui-cleanup-evidence",
      includesAll(files.uiTest.text, [
        "ui.pending=0",
        "ui.no_raw_view=true",
        "NodeBridgeResourceType.UI_SESSION",
        "provider.destroy()",
        "embedded_script.bridge_resource.active_count",
        "active UI session limit",
      ]) &&
        includesAll(files.uiDesign.text, [
          "ui.run(fn)",
          "blocked_by_design",
          "opaque `UiHandle`",
          "Node event loop",
        ]) &&
        includesAll(manifest("ui.run"), [
          "blocked_by_design",
          "Android main looper",
        ]),
      "UI evidence must cover opaque handles, pending=0, close/destroy cleanup, and blocked Rhino ui.run/ui.post semantics",
    ),
    check(
      "floaty-design-gate",
      includesAll(files.floatyDesign.text, [
        "S11-24",
        "Do not add `require(\"floaty\")`",
        "blocked_by_design",
        "floaty.window(xml)",
        "listener cleanup",
      ]) &&
        includesAll(manifest("floaty"), [
          "s11_24_floaty_overlay_design_gate",
          "compat.floaty",
          "global floaty",
        ]) &&
        includesAll(manifest("floaty.window"), [
          "s11_24_xml_window_wrapper_blocked",
          "blocked_by_design",
        ]),
      "Floaty compatibility must remain blocked by design and create no overlay resources",
    ),
    check(
      "database-cleanup-evidence",
      includesAll(files.databaseTest.text, [
        "database.pending=0",
        "database.requests=database.open|database.exec|database.run|database.get|database.all|database.transaction|database.close|sqlite.open|sqlite.close",
        "database_active_handle_count",
        "bridge_resource.active_count",
        "ATTACH DATABASE",
        "load_extension",
        "node:sqlite",
      ]),
      "Database evidence must cover close(), registry active_count=0, node:sqlite denial, and SQL escape denial",
    ),
    check(
      "fetch-websocket-cleanup-evidence",
      includesAll(files.fetchTest.text, [
        "fetch.pending=0",
        "fetch.harden.pending",
        "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD",
        "activeRequestCount",
        "androidFetchProviderStaysDisabledInNormalBuilds",
      ]) &&
        includesAll(files.websocketTest.text, [
          "websocket.pending=0",
          "websocket.diagnostics=0|2|1|3",
          "ERR_AUTOJS6_BRIDGE_PROCESS_DEAD",
          "activeConnectionCount",
          "androidWebSocketProviderStaysDisabledInNormalBuilds",
        ]),
      "Fetch/WebSocket evidence must cover timeout/service-destroy cleanup, active diagnostics zero, and normal-build default denial",
    ),
    check(
      "resource-cleanup-evidence",
      includesAll(files.resourceLeakStressTest.text, [
        "bridge.stress.pending=0",
        "embedded_script.bridge_resource.active_count",
        "embedded_script.bridge_resource.active_types",
        "embedded_script.bridge_resource.close_error_count",
        "embedded_script.websocket_active_connection_count",
        "NodeBridgeResourceType.UI_SESSION",
      ]) &&
        includesAll(files.autoJsBridgeTest.text, [
          "bridge.timeout.pending=0",
          "bridge.abort.pre.pending=0",
          "bridge.abort.during.pending=0",
          "bridge.abort.wrapper.pending=0",
          "bridge.destroy.pending=0",
        ]),
      "Generic bridge lifecycle evidence must prove timeout/abort/destroy drains pending calls and active resource diagnostics",
    ),
    check(
      "security-boundary-evidence",
      includesAll(files.securityTest.text, [
        "security.phase5.fetch_schemes",
        "security.phase5.websocket_scheme",
        "security.phase5.ui_cleanup=true|0",
        "security.phase7.capability_requests=0",
        "security.phase7.provider_policy.no_dispatch=true|true|0",
        "database.attach",
        "database.load_extension",
      ]),
      "Security evidence must cover network/UI/database denial paths and no-dispatch capability bypass protection",
    ),
    check(
      "status-manifest-resource-decisions",
      includesAll(manifest("images.requestScreenCapture"), ["s11_19", "media_projection"]) &&
        includesAll(manifest("images.recycle"), ["s11_18_images_compat_facade", "recycled explicitly"]) &&
        includesAll(manifest("storages.create"), ["s11_13_storage_compat_facade"]) &&
        includesAll(manifest("floaty.window"), ["blocked_by_design"]) &&
        includesAll(manifest("events.__module"), ["s11_21_raw_rhino_event_emitter_blocked"]),
      "Status manifest must preserve the resource-sensitive compat decisions for images, storage, events, and floaty",
    ),
    check(
      "gradle-focused-selectors",
      includesAll(files.appGradle.text, [
        "nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses",
        `"${DEVICE_GATE}" to nodeJsRhinoCompatResourceCleanupSecurityInstrumentationClasses`,
      ]) &&
        DEVICE_SELECTORS.every((selector) => files.appGradle.text.includes(selector)),
      "app/build.gradle.kts must register every S11-30 focused instrumentation selector",
    ),
    check(
      "phase11-host-task",
      includesAll(files.phase11Gradle.text, [
        HOST_GATE,
        "verify-rhino-compat-resource-cleanup-security.js",
        "rhino-compat-resource-cleanup-security-verification.json",
        "rhino-compat-resource-cleanup-security-verification.md",
        "RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
      ]),
      "Phase 11 Gradle script must register the S11-30 host report gate and outputs",
    ),
    check(
      "gradle-device-gate",
      includesAll(files.phase11Gradle.text, [
        `tasks.register("${DEVICE_GATE}")`,
        HOST_GATE,
        "connectedAppDebugAndroidTest",
        "resource cleanup/security gate on a connected device",
      ]),
      "Phase 11 Gradle script must register the connected-device S11-30 gate",
    ),
    check(
      "report-doc-s11-30",
      includesAll(files.reportDoc.text, [
        "S11-30",
        HOST_GATE,
        DEVICE_GATE,
        "autojs6-node-rhino-compat-resource-cleanup-security-verification-v1",
        "image",
        "selector",
        "event",
        "ui",
        "floaty",
        "db",
        "websocket/fetch",
        "active resource",
        "residual policy",
      ]),
      "S11-30 report doc must document commands, schema, surfaces, active resource acceptance, and residual policy",
    ),
    check(
      "testing-doc-s11-30",
      includesAll(files.testing.text, [
        HOST_GATE,
        DEVICE_GATE,
        "RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
        "rhino-compat-resource-cleanup-security-verification.json",
      ]),
      "TESTING.md must document the S11-30 resource cleanup/security gates and report",
    ),
    check(
      "task-plan-s11-30-complete",
      /\[x\]\s+S11-30/.test(files.taskPlan.text) &&
        includesAll(files.taskPlan.text, [
          HOST_GATE,
          DEVICE_GATE,
          "RHINO_COMPAT_RESOURCE_CLEANUP_SECURITY.md",
        ]),
      "Phase 11 task plan must mark S11-30 complete and name the gate/report artifacts",
    ),
  ];
  return checks;
}

function flattenManifestEntries(value, output = []) {
  if (Array.isArray(value)) {
    value.forEach((item) => flattenManifestEntries(item, output));
  } else if (value && typeof value === "object") {
    if (typeof value.id === "string") {
      output.push(value);
    }
    Object.values(value).forEach((item) => flattenManifestEntries(item, output));
  }
  return output;
}

function manifestEntryText(entries, id) {
  const entry = entries.find((item) => item.id === id);
  return entry ? JSON.stringify(entry) : "";
}

function sameSet(actual, expected) {
  return actual.length === expected.length && expected.every((value) => actual.includes(value));
}

function includesAll(text, values) {
  return values.every((value) => text.includes(value));
}

function check(id, passed, message) {
  return { id, passed: Boolean(passed), message };
}

function checkById(checks, id) {
  const item = checks.find((check) => check.id === id);
  return item ? item.passed : false;
}

function read(file) {
  return fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "");
}

function renderMarkdown(report) {
  const lines = [
    "# Rhino Compat Resource Cleanup/Security Verification",
    "",
    `- Gate: \`${report.gate}\``,
    `- Device gate: \`${report.deviceGate}\``,
    `- Status: \`${report.decision.status}\``,
    `- Required surfaces: ${report.requiredSurfaces.map((name) => `\`${name}\``).join(", ")}`,
    `- Device selectors: ${report.deviceSelectors.length}`,
    `- Policy violations: ${report.policyViolations.length}`,
    "",
    "## Surface Evidence",
    "",
    "| Surface | Status | Acceptance | Residual Policy | Evidence |",
    "| --- | --- | --- | --- | --- |",
  ];
  report.surfaceEvidence.forEach((surface) => {
    lines.push([
      `\`${surface.id}\``,
      `\`${surface.status}\``,
      escapeTable(surface.acceptance),
      escapeTable(surface.residualPolicy),
      surface.evidence.map((item) => `\`${item}\``).join("<br>"),
    ].join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  });
  lines.push("", "## Device Selectors", "");
  report.deviceSelectors.forEach((selector) => {
    lines.push(`- \`${selector}\``);
  });
  lines.push("", "## Checks", "", "| Check | Result | Message |", "| --- | --- | --- |");
  report.checks.forEach((check) => {
    lines.push(`| \`${check.id}\` | ${check.passed ? "passed" : "failed"} | ${escapeTable(check.message)} |`);
  });
  return lines.join("\n") + "\n";
}

function escapeTable(value) {
  return String(value).replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      console.log("Usage: node verify-rhino-compat-resource-cleanup-security.js [--repo-root dir] [--report-dir dir]");
      process.exit(0);
    }
    if (!arg.startsWith("--")) {
      throw new Error("Unexpected positional argument: " + arg);
    }
    const eq = arg.indexOf("=");
    const rawKey = eq >= 0 ? arg.slice(2, eq) : arg.slice(2);
    const key = rawKey.replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
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

main();
