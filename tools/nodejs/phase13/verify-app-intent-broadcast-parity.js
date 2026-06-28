#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

const REPORT_BASENAME = "phase13-app-intent-broadcast-parity";

const FILES = {
  phase13Tasks: "docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md",
  p13Report: "docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md",
  surfaceManifest: "docs/nodejs/phase13/phase13-surface-manifest.json",
  surfaceManifestDoc: "docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md",
  protocol: "app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt",
  permissionManifest: "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
  nodeBridgeSources: "app/src/main/cpp/node_bridge_sources.cpp",
  conformanceTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt",
  autoJsBridgeTest: "app/src/androidTest/java/org/autojs/autojs/engine/NodeAutoJsBridgeInstrumentationTest.kt",
  appRhino: "app/src/main/java/org/autojs/autojs/runtime/api/augment/app/App.kt",
  appTypes: "docs/nodejs/types/autojs6-node/app.d.ts",
  rhinoCompatTypes: "docs/nodejs/types/autojs6-node/rhino_compat.d.ts",
  typesReadme: "docs/nodejs/types/autojs6-node/README.md",
  typeSmoke: "sample/nodejs/typescript-smoke/src/main.ts",
  securityModel: "docs/nodejs/SECURITY_MODEL.md",
  compatibilityProfile: "docs/nodejs/COMPATIBILITY_PROFILE.md",
  testingDoc: "docs/nodejs/TESTING.md",
  ciGatesDoc: "docs/nodejs/CI_GATES.md",
  migrationGuide: "docs/nodejs/MIGRATION_FROM_RHINO.md",
  proComparison: "docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md",
  proAutoxNotes: "docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md",
  capabilityMatrix: "docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md",
  intentBroadcastProviderReport: "docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md",
  broadcastTasksDoc: "docs/nodejs/design/BROADCAST_TASKS.md",
  phase11Tasks: "docs/nodejs/reports/phase11/RHINO_AUGMENT_COMPATIBILITY_TASKS.md",
  statusManifest: "docs/nodejs/rhino-compat/status-manifest.json",
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
  console.log("Phase 13 app/intent/broadcast parity: " + report.decision.status);
  console.log("Phase 13 app/intent/broadcast parity blockers: " + report.blockers.length);
  console.log("Phase 13 app/intent/broadcast parity policy violations: " + report.policyViolations.length);
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
    scenario("node_app_intent_descriptor", "Node app module exposes JSON-safe intent descriptors", [
      token(files.nodeBridgeSources, "function __autojs6_app_normalize_intent_descriptor(options, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_open_url_descriptor(url, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_email_descriptor(value, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_uninstall_descriptor(packageName, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_intent_to_shell(descriptor, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_reject_privileged_intent_options(options, methodName)"),
      token(files.nodeBridgeSources, "function intent(options)"),
      token(files.nodeBridgeSources, "function intentToShell(options)"),
      token(files.nodeBridgeSources, "descriptor.categories = Object.freeze(Array.from(new Set(categories)))"),
      token(files.nodeBridgeSources, "descriptor.flags = flags"),
      token(files.nodeBridgeSources, "descriptor.extras = extras"),
      token(files.nodeBridgeSources, 'intentToShell,'),
      token(files.nodeBridgeSources, 'openUrl,'),
      token(files.nodeBridgeSources, 'sendEmail,'),
      token(files.nodeBridgeSources, 'uninstall,'),
      token(files.conformanceTest, 'const shellIntent = app.intentToShell(viewIntent);'),
      token(files.conformanceTest, 'app.intentToShell must not dispatch bridge requests'),
      token(files.conformanceTest, 'await expectPromise("app.startActivity", app.startActivity(viewIntent'),
      token(files.conformanceTest, 'await expectPromise("app.openUrl", app.openUrl("example.com/docs"'),
      token(files.conformanceTest, 'await expectPromise("app.sendEmail", app.sendEmail({ to: ["dev@example.com"]'),
      token(files.conformanceTest, 'await expectPromise("app.uninstall", app.uninstall("com.example.invalid"'),
      token(files.conformanceTest, 'assert.strictEqual(uninstallRequest.args[0].data, "package:com.example.invalid");'),
      token(files.appTypes, "intent(request: IntentRequest): IntentDescriptor;"),
      token(files.appTypes, "intentToShell(request: IntentRequest | IntentDescriptor): string;"),
      token(files.appTypes, "openUrl(url: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;"),
      token(files.appTypes, "sendEmail(request: SendEmailRequest | EmailAddressList"),
      token(files.appTypes, "uninstall(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<void>;"),
      token(files.appTypes, "category?: string | readonly string[];"),
      token(files.appTypes, "categories?: string | readonly string[];"),
      token(files.appTypes, "flags?: number | readonly number[];"),
      token(files.appTypes, "url?: string;"),
      token(files.appTypes, "readonly root?: never;"),
      token(files.typeSmoke, "const viewIntent: app.IntentDescriptor = app.intent"),
      token(files.typeSmoke, "const shellIntent: string = app.intentToShell(viewIntent);"),
      token(files.typeSmoke, 'await app.openUrl("example.invalid/path"'),
      token(files.typeSmoke, "await app.sendEmail({"),
      token(files.typeSmoke, 'await app.uninstall("com.example.invalid"'),
      token(files.conformanceTest, "app.intent descriptor must be frozen"),
      token(files.conformanceTest, "assert.deepStrictEqual(viewIntent.categories"),
    ], [
      "Add named Rhino activity short forms only after a separate descriptor alias matrix exists.",
    ]),
    scenario("android_start_activity_schema", "Android bridge parses category/categories, flags arrays, and JSON-safe extras", [
      token(files.protocol, "val appMethods = setOf("),
      token(files.protocol, "private fun parseIntentCategories(options: JSONObject): List<String>?"),
      token(files.protocol, "private fun parseIntentFlags(options: JSONObject): Int?"),
      token(files.protocol, "private fun putJsonIntentExtraArray(intent: Intent, key: String, values: JSONArray)"),
      token(files.protocol, 'options.optNonBlankString("packageName") ?: options.optNonBlankString("package")'),
      token(files.protocol, 'options.has("extras") && !options.isNull("extras") && extras == null'),
      token(files.protocol, "categories.forEach(::addCategory)"),
      token(files.protocol, "addFlags(flags)"),
      token(files.protocol, "extras?.let { putJsonIntentExtras(this, it) }"),
      token(files.conformanceTest, 'assert.strictEqual(viewStartRequest.args[0].flags, 0x10000020);'),
      token(files.conformanceTest, 'assert.deepStrictEqual(viewStartRequest.args[0].extras.labels, ["node", "intent"]);'),
      token(files.autoJsBridgeTest, "appStartActivityShortFormsAndNegativeCorpusUseLiveBridge"),
      token(files.autoJsBridgeTest, "app.live.short_forms=true"),
      token(files.autoJsBridgeTest, "app.live.negative.flags=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity"),
      token(files.autoJsBridgeTest, "app.live.negative.extras=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity"),
      token(files.autoJsBridgeTest, "app.live.negative.component=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity"),
      token(files.autoJsBridgeTest, "androidAppBridgeRejectsInvalidStartActivityDescriptors"),
      token(files.autoJsBridgeTest, '"invalid-component"'),
      token(files.autoJsBridgeTest, '"invalid-flags"'),
      token(files.autoJsBridgeTest, '"invalid-extras"'),
      token(files.p13Report, "Current Device Evidence"),
      token(files.testingDoc, "Current P13-23 app.startActivity live provider evidence"),
    ], []),
    scenario("installed_app_query_policy", "Installed-app lookup helpers use explicit app.query capability and JSON-safe return values", [
      token(files.permissionManifest, 'const val APP_QUERY = "app.query"'),
      token(files.permissionManifest, '"getPackageName", "getAppName", "isInstalled" -> listOf(APP_QUERY)'),
      token(files.nodeBridgeSources, 'if (methodName === "getPackageName" || methodName === "getAppName" || methodName === "isInstalled") return ["app.query"];'),
      token(files.nodeBridgeSources, "function getPackageName(appName, options)"),
      token(files.nodeBridgeSources, "function getAppName(packageName, options)"),
      token(files.nodeBridgeSources, "function isInstalled(packageName, options)"),
      token(files.protocol, '"getPackageName",'),
      token(files.protocol, '"getAppName",'),
      token(files.protocol, '"isInstalled",'),
      token(files.protocol, "private fun packageNameForLabel(label: String): String?"),
      token(files.protocol, "private fun appLabelForPackage(packageName: String): String?"),
      token(files.protocol, "private fun isPackageInstalled(packageName: String): Boolean"),
      token(files.appTypes, "getPackageName(appName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string | null>;"),
      token(files.appTypes, "getAppName(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<string | null>;"),
      token(files.appTypes, "isInstalled(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.typeSmoke, 'const packageByName: string | null = await app.getPackageName("AutoJs6"'),
      token(files.typeSmoke, "const nameByPackage: string | null = await app.getAppName(app.packageName"),
      token(files.typeSmoke, "const installed: boolean = await app.isInstalled(app.packageName"),
      token(files.conformanceTest, 'assert.strictEqual(await expectPromise("app.getPackageName"'),
      token(files.conformanceTest, 'assert.strictEqual(await expectPromise("app.isInstalled.missing"'),
      token(files.p13Report, "`app.query`"),
    ], [
      "Keep raw `getInstalledApps()` / `getInstalledPackages()` Java-object list APIs blocked until a redacted JSON schema and disclosure policy exist.",
      "Add packaged privacy disclosure examples for app-name/package lookup before default promotion.",
    ]),
    scenario("scoped_file_activity_helpers", "File view/edit helpers resolve paths inside the scoped working directory", [
      token(files.permissionManifest, '"viewFile", "editFile" -> listOf(APP_ACTIVITY)'),
      token(files.nodeBridgeSources, 'if (methodName === "viewFile" || methodName === "editFile") return ["app.activity"];'),
      token(files.nodeBridgeSources, "function viewFile(path, options)"),
      token(files.nodeBridgeSources, "function editFile(path, options)"),
      token(files.protocol, '"viewFile",'),
      token(files.protocol, '"editFile",'),
      token(files.protocol, "private fun dispatchScopedAppFileAction("),
      token(files.protocol, "private fun resolveScopedAppFile(rawPath: String): File?"),
      token(files.protocol, "IntentUtils.viewFile(appContext, file.absolutePath)"),
      token(files.protocol, "IntentUtils.editFile(appContext, file.absolutePath)"),
      token(files.appTypes, "viewFile(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.appTypes, "editFile(path: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.typeSmoke, 'const viewedFile: boolean = await app.viewFile("README.md"'),
      token(files.typeSmoke, 'const editedFile: boolean = await app.editFile("README.md"'),
      token(files.conformanceTest, 'assert.strictEqual(await expectPromise("app.viewFile"'),
      token(files.conformanceTest, 'assert.strictEqual(await expectPromise("app.editFile"'),
      token(files.p13Report, "`app.viewFile()`"),
      token(files.p13Report, "`app.editFile()`"),
    ], [
      "Add packaged device evidence for scoped file view/edit Activity routing and disclosure.",
    ]),
    scenario("launch_alias_parity", "Rhino-style app.launch is a launchPackage alias without new bridge authority", [
      token(files.nodeBridgeSources, "launch: launchPackage"),
      token(files.appTypes, "launch(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<boolean>;"),
      token(files.typeSmoke, "const launchAlias: boolean = await app.launch(app.packageName"),
      token(files.conformanceTest, 'assert.strictEqual(await expectPromise("app.launch"'),
      token(files.p13Report, "`app.launch()`"),
      token(files.p13Report, "`app.launchPackage()`"),
      token(files.p13Report, "Rhino global `launch()` migration example"),
      token(files.migrationGuide, "Rhino global `launch(\"org.example.app\")` migration"),
      token(files.migrationGuide, 'launch("org.example.app");'),
      token(files.migrationGuide, 'const launched = await app.launch("org.example.app", { timeoutMs: 5000 });'),
      token(files.migrationGuide, "`app.launch()` is a P13-23 alias for `app.launchPackage()`"),
    ], []),
    scenario("rhino_app_helper_aliases", "Remaining Rhino app helper names are either JSON-safe or stable fail-closed stubs", [
      token(files.nodeBridgeSources, "function __autojs6_app_parse_uri_descriptor(value, methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_raw_uri_unavailable_error(methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_privileged_unavailable_error(methodName)"),
      token(files.nodeBridgeSources, "function parseUri(uri)"),
      token(files.nodeBridgeSources, "function getUriForFile(path)"),
      token(files.nodeBridgeSources, "function startDualActivity(options, callOptions)"),
      token(files.nodeBridgeSources, "function launchDualPackage(packageName, options)"),
      token(files.nodeBridgeSources, "function isDualInstalled(packageName, options)"),
      token(files.nodeBridgeSources, "function kill(packageName, options)"),
      token(files.nodeBridgeSources, "parseUri,"),
      token(files.nodeBridgeSources, "getUriForFile,"),
      token(files.nodeBridgeSources, "launchDual: launchDualPackage"),
      token(files.nodeBridgeSources, "killDual"),
      token(files.appTypes, 'readonly schema: "autojs6-node-app-uri-descriptor-v1";'),
      token(files.appTypes, "parseUri(uri: string | null | undefined): UriDescriptor | null;"),
      token(files.appTypes, "getUriForFile(path: string): never;"),
      token(files.appTypes, "startDualActivity(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.appTypes, "kill(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.typeSmoke, "const parsedUri: app.UriDescriptor | null = app.parseUri"),
      token(files.typeSmoke, "const compatParsedUri: app.UriDescriptor | null = rhinoCompat.app.parseUri"),
      token(files.conformanceTest, "app.parseUri descriptor must be frozen"),
      token(files.conformanceTest, 'expectThrowCode("app.getUriForFile"'),
      token(files.conformanceTest, 'await expectRejectCode("app.kill"'),
      token(files.conformanceTest, 'await expectRejectCode("app.startDualActivity"'),
      token(files.conformanceTest, 'await expectRejectCode("app.isDualInstalled"'),
      token(files.p13Report, "`app.parseUri()`"),
      token(files.p13Report, "`app.getUriForFile()`"),
      token(files.p13Report, "`app.kill()`"),
    ]),
    scenario("broadcast_and_privileged_boundary", "Broadcast send and privileged Rhino options remain rejected before bridge dispatch", [
      token(files.nodeBridgeSources, "function __autojs6_app_send_broadcast_unavailable_error()"),
      token(files.nodeBridgeSources, "app.sendBroadcast is not available in the Node.js engine yet"),
      token(files.nodeBridgeSources, '"root", "shizuku", "dual", "user", "userId"'),
      token(files.appTypes, "sendBroadcast(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.typeSmoke, "const broadcastDenied: Promise<never> = app.sendBroadcast"),
      token(files.typeSmoke, "const compatBroadcastDenied: Promise<never> = rhinoCompat.app.sendBroadcast"),
      token(files.conformanceTest, 'await expectRejectCode("app.sendBroadcast"'),
      token(files.conformanceTest, "app raw Uri/kill/dual/sendBroadcast/root rejected stubs must not dispatch bridge requests"),
      token(files.intentBroadcastProviderReport, "Status: partial"),
      token(files.broadcastTasksDoc, "No JavaScript API is implemented yet."),
      token(files.securityModel, "P13-23 records app/intent/broadcast Pro parity as partial"),
    ]),
    scenario("stable_denied_app_tiers", "sendBroadcast, raw URI/FileProvider, dual-user, and root/Shizuku app tiers stay stable-denied", [
      token(files.p13Report, "P13-23 keeps `app.sendBroadcast()`, raw Android `Uri` / `FileProvider`"),
      token(files.p13Report, "handles, dual-user helpers, and root/Shizuku app tiers stable-denied for v1.1"),
      token(files.phase13Tasks, "P13-23 keeps `app.sendBroadcast()`, raw Android `Uri` / `FileProvider`"),
      token(files.nodeBridgeSources, "function __autojs6_app_send_broadcast_unavailable_error()"),
      token(files.nodeBridgeSources, "function __autojs6_app_raw_uri_unavailable_error(methodName)"),
      token(files.nodeBridgeSources, "function __autojs6_app_privileged_unavailable_error(methodName)"),
      token(files.conformanceTest, "app raw Uri/kill/dual/sendBroadcast/root rejected stubs must not dispatch bridge requests"),
      token(files.appTypes, "sendBroadcast(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.appTypes, "getUriForFile(path: string): never;"),
      token(files.appTypes, "startDualActivity(request: IntentRequest | string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
      token(files.appTypes, "kill(packageName: string, options?: AutoJs6Node.BridgeCallOptions): Promise<never>;"),
    ]),
    scenario("rhino_app_gap_inventory", "Rhino app augment remains broader than the Node app surface", [
      token(files.appRhino, "::intent.name"),
      token(files.appRhino, "::sendBroadcast.name to AS_GLOBAL"),
      token(files.appRhino, "::sendEmail.name to AS_GLOBAL"),
      token(files.appRhino, "::startDualActivity.name to AS_GLOBAL"),
      token(files.appRhino, "::intentToShell.name"),
      token(files.appRhino, "::launchDualPackage.name to AS_GLOBAL"),
      token(files.phase11Tasks, "`app.launchPackage`"),
      token(files.statusManifest, 'S11-17 exposes app.launchPackage, app.launchApp, app.openAppSetting, and app.startActivity'),
      token(files.proComparison, "P13-23 records app/intent/broadcast Pro parity as partial"),
      token(files.proAutoxNotes, "P13-23 records app/intent/broadcast Pro parity as partial"),
    ], [
      "Map raw installed-app list APIs and preset app aliases to separate JSON-safe providers or stable rejections.",
    ]),
    scenario("docs_gate", "Phase 13 docs and Gradle gate record P13-23 as partial", [
      token(files.p13Report, "Status: partial"),
      token(files.phase13Tasks, "P13-23 partial app/intent/broadcast Pro parity gate"),
      token(files.gradleGate, "verifyNodePhase13AppIntentBroadcastParity"),
      token(files.testingDoc, "verifyNodePhase13AppIntentBroadcastParity"),
      token(files.ciGatesDoc, "verifyNodePhase13AppIntentBroadcastParity"),
      token(files.compatibilityProfile, "P13-23 records app/intent/broadcast Pro parity as partial"),
      token(files.capabilityMatrix, "P13-23 app/intent/broadcast parity"),
      token(files.typesReadme, "P13-23 records the partial app/intent/broadcast parity gate"),
      token(files.surfaceManifestDoc, "phase13-app-intent-broadcast-parity"),
    ], []),
  ];
  const checks = [
    check("task-listed", files.phase13Tasks.text.includes("P13-23"), "P13-23 must remain listed in the Phase 13 checklist."),
    check("report-present", files.p13Report.exists, "P13-23 report must exist."),
    check("surface-present", Boolean(proSurface), "`pro.tasks_app_shell_media_utils` surface must exist."),
    check(
      "surface-partial",
      proSurface && ["debug_poc", "provider_poc", "design_locked"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must remain partial/poc after P13-23 evidence lands.",
    ),
    check(
      "surface-not-promoted",
      proSurface && !["opt_in_promoted", "default_promoted"].includes(proSurface.status),
      "`pro.tasks_app_shell_media_utils` must not be promoted by the P13-23 partial gate.",
    ),
    check(
      "surface-evidence",
      proSurface && (proSurface.evidence || []).some((entry) => entry.includes("phase13-app-intent-broadcast-parity")),
      "`pro.tasks_app_shell_media_utils` surface must reference P13-23 evidence.",
    ),
    check(
      "broadcast-not-bridge-method",
      files.protocol.text.includes("val appMethods = setOf(") &&
        !files.permissionManifest.text.includes('"sendBroadcast" ->') &&
        !files.protocol.text.includes('"sendBroadcast" -> NodeBridgeResponse.success'),
      "P13-23 must not add sendBroadcast to the Android bridge dispatch table.",
    ),
    check(
      "privileged-options-rejected-in-js",
      files.nodeBridgeSources.text.includes('"root", "shizuku", "dual", "user", "userId"') &&
        files.nodeBridgeSources.text.includes("Use a dedicated shell/root/Shizuku capability instead."),
      "P13-23 must reject root/Shizuku/dual app options before bridge dispatch.",
    ),
  ];
  const policyViolations = checks.filter((entry) => !entry.passed).map((entry) => entry.message);
  const blockers = scenarios.flatMap((entry) => entry.remaining.map((item) => `${entry.id}: ${item}`));
  const finalBlockerClosable = blockers.length === 0 && policyViolations.length === 0;
  return {
    schema: "autojs6-node-phase13-app-intent-broadcast-parity-v1",
    generatedAt: new Date().toISOString(),
    gate: "verifyNodePhase13AppIntentBroadcastParity",
    task: "P13-23",
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
      nodeIntentDescriptorReady: scenarioById(scenarios, "node_app_intent_descriptor").status !== "missing_evidence",
      androidStartActivitySchemaReady: scenarioById(scenarios, "android_start_activity_schema").status !== "missing_evidence",
      androidStartActivityDeviceEvidenceReady: scenarioById(scenarios, "android_start_activity_schema").status === "ready",
      broadcastSendReady: false,
      rootShizukuAppOptionsReady: false,
      stableDeniedAppTiers: scenarioById(scenarios, "stable_denied_app_tiers").status === "ready",
      rhinoAppHelperAliasStubsReady: scenarioById(scenarios, "rhino_app_helper_aliases").status !== "missing_evidence",
      rhinoAppFullParityReady: false,
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
