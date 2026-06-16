#!/usr/bin/env node

import fs from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const STATIC_ONLY = process.argv.includes("--static-only");

const FILES = {
  roadmap: "Roadmap.md",
  releaseStatus: "docs/dev/ace-release-gate-status.md",
  sonyReport: "docs/dev/ace-release-device-report-sony-xq-at72.md",
  publishingCleanup: "docs/dev/ace-release-publishing-cleanup.md",
  integrationDoc: "docs/dev/ace-editor-integration.md",
  keyboardViewportPlan: "docs/dev/ace-keyboard-viewport-test-plan.md",
  visualInvestigation: "docs/dev/visual-editor-ace-investigation.md",
  strings: "app/src/main/res/values/strings.xml",
  stringsZh: "app/src/main/res/values-zh/strings.xml",
  arrays: "app/src/main/res/values/arrays.xml",
  fragmentPreferences: "app/src/main/res/xml/fragment_preferences.xml",
  editorEngineType: "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEngineType.kt",
  editorEnginePreferences: "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEnginePreferences.kt",
  editorEngineFactory: "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEngineFactory.kt",
  policyFactory: "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/EditorViewportPolicyFactory.kt",
  acePolicy: "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/AceEditorViewportPolicy.kt",
  aceCodeEditor: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceCodeEditor.kt",
  aceBridge: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceBridge.kt",
  aceBridgeAsset: "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_ace_bridge.js",
  aceTestHooks: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceEditorTestHooks.kt",
  aceJsErrorClassifier: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/health/AceJsErrorClassifier.kt",
  aceFallbackPolicy: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/health/AceFallbackPolicy.kt",
  aceLspServerManager: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/lsp/AceLspServerManager.kt",
  aceLspClient: "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_lsp_client.js",
  editorView: "app/src/main/java/org/autojs/autojs/ui/edit/EditorView.kt",
};

function read(relativePath) {
  return fs.readFileSync(path.join(PROJECT_ROOT, relativePath), "utf8").replaceAll("\r\n", "\n");
}

function assertIncludes(file, text, label = text) {
  const content = read(file);
  if (!content.includes(text)) {
    throw new Error(`${file}: missing ${label}`);
  }
}

function assertNotIncludes(file, text, label = text) {
  const content = read(file);
  if (content.includes(text)) {
    throw new Error(`${file}: must not contain ${label}`);
  }
}

function assertMatch(file, regex, label = regex.toString()) {
  const content = read(file);
  if (!regex.test(content)) {
    throw new Error(`${file}: missing ${label}`);
  }
}

function assertOrder(file, items, label) {
  const content = read(file);
  let lastIndex = -1;
  for (const item of items) {
    const index = content.indexOf(item);
    if (index < 0) {
      throw new Error(`${file}: ${label}: missing ${item}`);
    }
    if (index <= lastIndex) {
      throw new Error(`${file}: ${label}: ${item} appears out of order`);
    }
    lastIndex = index;
  }
}

function listTextFiles(rootRelativePath) {
  const root = path.join(PROJECT_ROOT, rootRelativePath);
  const result = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      result.push(...listTextFiles(path.relative(PROJECT_ROOT, fullPath)));
    } else if (/\.(kt|java|xml|js|mjs|kts|gradle)$/.test(entry.name)) {
      result.push(fullPath);
    }
  }
  return result;
}

function relativeToRoot(filePath) {
  return path.relative(PROJECT_ROOT, filePath).replaceAll(path.sep, "/");
}

function assertTextAbsentInRoots(text, rootRelativePaths) {
  const offenders = [];
  for (const root of rootRelativePaths) {
    for (const filePath of listTextFiles(root)) {
      if (fs.readFileSync(filePath, "utf8").includes(text)) {
        offenders.push(relativeToRoot(filePath));
      }
    }
  }
  if (offenders.length) {
    throw new Error(`${text} must stay absent from release-gated code/resources; found in ${offenders.join(", ")}`);
  }
}

function runNodeScript(label, script) {
  console.log(`\n[ACE release gate] ${label}`);
  const result = spawnSync(process.execPath, [script], {
    cwd: PROJECT_ROOT,
    stdio: "inherit",
  });
  if (result.error) {
    throw new Error(`Failed to start ${script}: ${result.error.message}`);
  }
  if (result.status !== 0) {
    process.exit(result.status || 1);
  }
}

if (!STATIC_ONLY) {
  runNodeScript("ACE editor CI gate", "tools/ace-ci/check-ace-editor-gate.mjs");
}

assertIncludes(FILES.roadmap, "`node tools/ace-ci/check-ace-release-gate.mjs`");
assertIncludes(FILES.roadmap, "# AutoJs6 ACE Phase 9 Roadmap");
assertIncludes(FILES.roadmap, "## Feature Freeze");
assertIncludes(FILES.roadmap, "Sony XQ-AT72");
assertIncludes(FILES.roadmap, "Automated/static gate:");
assertIncludes(FILES.roadmap, "Manual device gate:");
assertIncludes(FILES.releaseStatus, "# ACE Release Gate Status");
assertIncludes(FILES.releaseStatus, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(FILES.releaseStatus, "docs/dev/ace-release-publishing-cleanup.md");
assertIncludes(FILES.releaseStatus, "## Feature Freeze");
assertIncludes(FILES.sonyReport, "# ACE Release Device Report - Sony XQ-AT72");
assertIncludes(FILES.sonyReport, "Publishing cleanup audit");
assertIncludes(FILES.sonyReport, "## Automated Gate");
assertIncludes(FILES.sonyReport, "## Manual Gate Summary");
assertIncludes(FILES.sonyReport, "Release candidate accepted");
assertIncludes(FILES.publishingCleanup, "# ACE Release Publishing Cleanup");
assertIncludes(FILES.publishingCleanup, "defaultConfig.applicationId");
assertIncludes(FILES.publishingCleanup, "VERSION_BUILD");
assertIncludes(FILES.publishingCleanup, "ACE is now the static default editor");
assertIncludes(FILES.publishingCleanup, "LSP remains default-off");
assertIncludes(FILES.publishingCleanup, "VisualEditorAce has no public enum");
assertIncludes(FILES.integrationDoc, "Run `node tools/ace-ci/check-ace-release-gate.mjs`.");
assertIncludes(FILES.integrationDoc, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(FILES.keyboardViewportPlan, "Sony XQ-AT72");

assertIncludes(
  FILES.strings,
  '<string name="default_key_editor_engine" translatable="false">@string/key_editor_engine_ace</string>',
  "ACE editor resource default",
);
assertIncludes(FILES.strings, '<string name="key_editor_engine_native" translatable="false">native</string>');
assertIncludes(FILES.strings, '<string name="key_editor_engine_ace" translatable="false">ace</string>');
assertIncludes(FILES.stringsZh, 'name="text_editor_engine"', "Simplified Chinese editor engine string");
assertIncludes(FILES.stringsZh, 'name="text_ace_word_wrap_enabled"', "Simplified Chinese ACE word wrap string");
assertIncludes(FILES.stringsZh, 'name="text_ace_line_numbers_enabled"', "Simplified Chinese ACE line number string");
assertOrder(
  FILES.arrays,
  [
    '<string-array name="keys_editor_engine">',
    '<item>@string/key_editor_engine_ace</item>',
    '<item>@string/key_editor_engine_native</item>',
  ],
  "editor engine key order",
);
assertIncludes(FILES.fragmentPreferences, 'app:itemDefaultKey="@string/default_key_editor_engine"');
assertOrder(
  FILES.fragmentPreferences,
  [
    'app:title="@string/text_code_editor"',
    'app:key="@string/key_editor_engine"',
    'app:key="@string/key_editor_auto_show_log_bottom_sheet_on_run"',
    'app:key="@string/key_editor_log_button_behavior"',
    'app:key="@string/key_ace_editor_font"',
    'app:key="@string/key_ace_word_wrap_enabled"',
    'app:key="@string/key_ace_line_numbers_enabled"',
    'app:key="@string/key_ace_lsp_enabled"',
  ],
  "code editor preference order",
);
assertMatch(
  FILES.editorEngineType,
  /enum class EditorEngineType\(val preferenceValue: String\) \{\s*NATIVE\("native"\),\s*ACE\("ace"\);/s,
  "only Native and ordinary ACE engine types",
);
assertIncludes(FILES.editorEngineType, "?: ACE", "invalid preference fallback to ACE");
assertIncludes(
  FILES.editorEnginePreferences,
  "Pref.getString(KEY_EDITOR_ENGINE, EditorEngineType.ACE.preferenceValue)",
  "stored editor preference default to ACE",
);
assertIncludes(
  FILES.editorEngineFactory,
  "fun create(context: Context, preferredType: EditorEngineType = EditorEngineType.ACE)",
  "factory default to ACE",
);

assertIncludes(FILES.policyFactory, "EditorEngineType.NATIVE -> NativeEditorViewportPolicy(editor, hostView)");
assertIncludes(FILES.policyFactory, "EditorEngineType.ACE -> AceEditorViewportPolicy(editor)");
assertIncludes(FILES.acePolicy, "view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)");
assertIncludes(FILES.acePolicy, "editor.onImeInsetsChanged(visible, bottomInset)");
assertNotIncludes(FILES.acePolicy, "requestRectangleOnScreen");
assertNotIncludes(FILES.acePolicy, "ensureCursorVisibleIfPossible");
assertTextAbsentInRoots("key_ace_clean_host_layout", [
  "app/src/main/java/org/autojs/autojs/ui/edit",
  "app/src/main/res/menu",
  "app/src/main/res/xml",
  "app/src/main/res/values",
]);

assertIncludes(FILES.aceCodeEditor, "private val textMirror = AceTextMirror()");
assertIncludes(FILES.aceCodeEditor, "val fallback = quote(textMirror.text)");
assertIncludes(FILES.aceCodeEditor, "textSnapshot = textMirror.text");
assertIncludes(FILES.aceCodeEditor, "runtimeEventHistory = eventHistory.snapshot()");
assertMatch(
  FILES.editorView,
  /latestText = oldEditor\.textSnapshot,\s*persistPreference = false,\s*showSwitchToast = false,/s,
  "ACE fallback switches to Native with the latest TextMirror-backed snapshot",
);

assertIncludes(FILES.aceTestHooks, "var forceRendererGoneFallback: Boolean = false");
assertIncludes(FILES.aceTestHooks, "var forceJsInitThrow: Boolean = false");
assertIncludes(FILES.aceBridge, "fun shouldForceJsInitThrow(): Boolean");
assertIncludes(FILES.aceBridgeAsset, 'callBridge("shouldForceJsInitThrow")');
assertIncludes(FILES.aceCodeEditor, "AceEditorTestHooks.shouldForceRendererGoneFallback()");
assertIncludes(FILES.aceCodeEditor, "AceFailureType.RENDER_PROCESS_GONE");
assertIncludes(FILES.aceCodeEditor, "override fun onRenderProcessGone");
assertIncludes(FILES.aceJsErrorClassifier, "AceFailureType.JS_INIT_ERROR");
assertIncludes(FILES.aceFallbackPolicy, "else -> true");

assertIncludes(FILES.aceLspServerManager, 'STATE_DISABLED = "disabled"');
assertIncludes(FILES.aceLspServerManager, 'STATE_CONFIGURED_NO_SERVER = "configured-no-server"');
assertIncludes(FILES.aceLspServerManager, 'TRANSPORT_UNAVAILABLE = "unavailable"');
assertIncludes(FILES.aceLspServerManager, "startSupported = false");
assertIncludes(FILES.aceLspServerManager, "serverAvailable = false");
assertNotIncludes(FILES.aceLspServerManager, "WebSocket");
assertNotIncludes(FILES.aceLspServerManager, "ProcessBuilder");
assertNotIncludes(FILES.aceLspServerManager, "Runtime.getRuntime()");
assertNotIncludes(FILES.aceLspClient, "WebSocket");
assertNotIncludes(FILES.aceLspClient, "new Worker");
assertNotIncludes(FILES.editorEngineType, "VISUAL_ACE");
assertNotIncludes(FILES.editorEngineFactory, "Visual");
assertNotIncludes(FILES.policyFactory, "VISUAL_ACE");
assertTextAbsentInRoots("visual_ace", [
  "app/src/main/java/org/autojs/autojs/ui/edit",
  "app/src/main/res/menu",
  "app/src/main/res/xml",
  "app/src/main/res/values",
]);
assertIncludes(FILES.visualInvestigation, "Keep `VisualEditorAce` deferred.");

console.log("\nACE release gate automated checks passed.");
