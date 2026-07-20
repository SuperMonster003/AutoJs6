#!/usr/bin/env node

import fs from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const STATIC_ONLY = process.argv.includes("--static-only");

const FILES = {
  roadmap: "docs/dev/ace-editor-ux-roadmap.md",
  lspDesign: "docs/dev/ace-lsp-design.md",
  editorTestPlan: "docs/dev/ace-editor-test-plan.md",
  releaseStatus: "docs/dev/ace-release-gate-status.md",
  sonyReport: "docs/dev/ace-release-device-report-sony-xq-at72.md",
  publishingCleanup: "docs/dev/ace-release-publishing-cleanup.md",
  integrationDoc: "docs/dev/ace-editor-integration.md",
  keyboardViewportPlan: "docs/dev/ace-keyboard-viewport-test-plan.md",
  visualInvestigation: "docs/dev/visual-editor-ace-investigation.md",
  strings: "app/src/main/res/values/strings.xml",
  stringsDoNotTranslate: "app/src/main/res/values/strings_donottranslate.xml",
  stringsZh: "app/src/main/res/values-zh/strings.xml",
  bools: "app/src/main/res/values/bools.xml",
  arrays: "app/src/main/res/values/arrays.xml",
  fragmentPreferences: "app/src/main/res/xml/fragment_preferences.xml",
  codeEditorSettings: "app/src/main/res/xml/fragment_code_editor_settings.xml",
  editorMenu: "app/src/main/res/menu/menu_editor.xml",
  codeEditorSettingsDialog: "app/src/main/java/org/autojs/autojs/ui/settings/CodeEditorSettingsDialog.kt",
  codeEditorSettingsFragment: "app/src/main/java/org/autojs/autojs/ui/settings/CodeEditorSettingsFragment.kt",
  editorMenuHandler: "app/src/main/java/org/autojs/autojs/ui/edit/EditorMenu.java",
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
  aceLspPreferences: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceEditorLspPreferences.kt",
  aceLspServerManager: "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/lsp/AceLspServerManager.kt",
  aceLspClient: "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_lsp_client.js",
  aceTsLanguageService: "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_ts_language_service.js",
  aceLspClientTest: "tools/ace-lsp/test-lsp-client.mjs",
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

assertIncludes(FILES.roadmap, "# ACE Editor UX Roadmap");
assertIncludes(FILES.roadmap, "## Gate UX-4: Completion, Signature Help, And Local TypeScript Semantics");
assertIncludes(FILES.roadmap, "LSP smart completion is default-on and an explicit stored opt-out remains");
assertIncludes(FILES.roadmap, "bundled TypeScript language service");
assertIncludes(FILES.roadmap, "Support hot enable/disable, bounded loader retry, stale-callback guards,");
assertIncludes(FILES.roadmap, "Degrade documents longer than 512 KiB to static behavior and cap");
assertIncludes(FILES.roadmap, "Real bundled-runtime tests cover TypeScript semantic results, lifecycle,");
assertIncludes(FILES.roadmap, "External/server-backed LSP, project-wide indexing, rename, references,");
assertIncludes(FILES.roadmap, "Sony XQ-AT72");
assertNotIncludes(FILES.roadmap, "LSP stays default-off", "obsolete default-off LSP roadmap policy");
assertNotIncludes(FILES.roadmap, "LSP remains disabled by default", "obsolete disabled-by-default LSP roadmap policy");
assertIncludes(FILES.lspDesign, "# ACE LSP Smart Completion Design");
assertIncludes(FILES.lspDesign, "released, default-on editor feature");
assertIncludes(FILES.lspDesign, "manager=native-local");
assertIncludes(FILES.lspDesign, "## Loading And Lifecycle");
assertIncludes(FILES.lspDesign, "## Resource Limits And Degradation");
assertIncludes(FILES.lspDesign, "Documents longer than 512 KiB");
assertIncludes(FILES.lspDesign, "A completion response is capped at 300 items");
assertIncludes(FILES.lspDesign, "## Explicit Opt-Out");
assertIncludes(FILES.lspDesign, "## External Server Boundary");
assertIncludes(FILES.lspDesign, "loads the real bundled TypeScript");
assertIncludes(FILES.releaseStatus, "# ACE Release Gate Status");
assertIncludes(FILES.releaseStatus, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(FILES.releaseStatus, "docs/dev/ace-release-publishing-cleanup.md");
assertIncludes(FILES.releaseStatus, "## Feature Freeze");
assertIncludes(FILES.releaseStatus, "LSP smart completion is default-on");
assertIncludes(FILES.releaseStatus, "manager=native-local");
assertIncludes(FILES.releaseStatus, "real bundled TypeScript runtime");
assertIncludes(FILES.releaseStatus, "512 KiB degradation boundary");
assertIncludes(FILES.releaseStatus, "### LSP promotion result - 2026-07-20");
assertIncludes(FILES.releaseStatus, "Sony XQ-DQ72");
assertIncludes(FILES.releaseStatus, "External/server-backed LSP transport remains outside");
assertNotIncludes(FILES.releaseStatus, "preview path", "obsolete ACE LSP preview-path release status");
assertIncludes(FILES.sonyReport, "# ACE Release Device Report - Sony XQ-AT72");
assertIncludes(FILES.sonyReport, "Publishing cleanup audit");
assertIncludes(FILES.sonyReport, "## Automated Gate");
assertIncludes(FILES.sonyReport, "## Manual Gate Summary");
assertIncludes(FILES.sonyReport, "## LSP Formal-Release Addendum - 2026-07-20");
assertIncludes(FILES.sonyReport, "lspDefaultsOnAndCanBeEnabledWithoutReloadingEditor");
assertIncludes(FILES.sonyReport, "tooltipAndLspFallbackStayStaticAndLocal");
assertIncludes(FILES.sonyReport, "Sony XQ-DQ72");
assertIncludes(FILES.sonyReport, "Release candidate accepted");
assertIncludes(FILES.publishingCleanup, "# ACE Release Publishing Cleanup");
assertIncludes(FILES.publishingCleanup, "defaultConfig.applicationId");
assertIncludes(FILES.publishingCleanup, "VERSION_BUILD");
assertIncludes(FILES.publishingCleanup, "ACE is now the static default editor");
assertIncludes(FILES.publishingCleanup, "LSP smart completion is default-on");
assertIncludes(FILES.publishingCleanup, "DEFAULT_ENABLED=true");
assertIncludes(FILES.publishingCleanup, "manager=native-local");
assertIncludes(FILES.publishingCleanup, "512 KiB semantic-document limit");
assertNotIncludes(FILES.publishingCleanup, "LSP remains default-off", "obsolete default-off LSP publishing policy");
assertIncludes(FILES.publishingCleanup, "VisualEditorAce has no public enum");
assertIncludes(FILES.integrationDoc, "Run `node tools/ace-ci/check-ace-release-gate.mjs`.");
assertIncludes(FILES.integrationDoc, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(FILES.integrationDoc, "ACE LSP smart completion is a stable, default-on feature");
assertIncludes(FILES.integrationDoc, "bundled TypeScript language service");
assertIncludes(FILES.integrationDoc, "native-local");
assertNotIncludes(FILES.integrationDoc, "native alpha manager", "obsolete ACE LSP alpha manager documentation");
assertNotIncludes(FILES.integrationDoc, "LSP remains a separate experimental path", "obsolete experimental LSP documentation");
assertIncludes(FILES.editorTestPlan, "ACE LSP smart completion is default-on");
assertIncludes(FILES.editorTestPlan, "manager=native-local");
assertIncludes(FILES.editorTestPlan, "semanticServiceReason=document-too-large");
assertIncludes(FILES.editorTestPlan, "Re-enable it without reloading the page");
assertIncludes(FILES.keyboardViewportPlan, "Sony XQ-AT72");

assertIncludes(
  FILES.stringsDoNotTranslate,
  '<string name="default_key_editor_engine" translatable="false">@string/key_editor_engine_ace</string>',
  "ACE editor resource default",
);
assertIncludes(FILES.stringsDoNotTranslate, '<string name="key_editor_engine_native" translatable="false">native</string>');
assertIncludes(FILES.stringsDoNotTranslate, '<string name="key_editor_engine_ace" translatable="false">ace</string>');
assertIncludes(FILES.strings, '<string name="entry_editor_engine_ace">Ace</string>');
assertIncludes(FILES.strings, '<string name="text_ace_lsp_enabled">LSP smart completion</string>');
assertIncludes(FILES.strings, '<string name="summary_ace_lsp_enabled">Enable LSP-powered editor assistance</string>');
assertIncludes(FILES.bools, '<bool name="pref_ace_lsp_enabled">true</bool>', "ACE LSP resource default-on value");
assertTextAbsentInRoots("LSP smart completion (experimental)", ["app/src/main/res/values"]);
assertTextAbsentInRoots("Enable experimental LSP configuration", ["app/src/main/res/values"]);
assertIncludes(FILES.strings, 'name="text_editor_gutter_width_mode"', "English editor gutter width mode string");
assertIncludes(FILES.stringsZh, 'name="entry_editor_engine_ace"', "Simplified Chinese ACE editor engine entry");
assertIncludes(FILES.stringsZh, 'name="text_ace_lsp_enabled"', "Simplified Chinese ACE LSP string");
assertIncludes(FILES.stringsZh, 'name="summary_ace_lsp_enabled"', "Simplified Chinese ACE LSP summary");
assertIncludes(FILES.stringsZh, 'name="text_editor_engine"', "Simplified Chinese editor engine string");
assertIncludes(FILES.stringsZh, 'name="text_ace_word_wrap_enabled"', "Simplified Chinese ACE word wrap string");
assertIncludes(FILES.stringsZh, 'name="text_editor_word_wrap_indent_style"', "Simplified Chinese editor word wrap indentation string");
assertIncludes(FILES.stringsZh, 'name="text_editor_gutter_width_mode"', "Simplified Chinese editor gutter width mode string");
assertIncludes(FILES.stringsZh, 'name="text_ace_line_numbers_enabled"', "Simplified Chinese ACE line number string");
assertIncludes(FILES.stringsZh, 'name="text_editor_breakpoint_markers_enabled"', "Simplified Chinese editor breakpoint marker string");
assertIncludes(FILES.stringsZh, 'name="text_editor_fold_markers_enabled"', "Simplified Chinese editor fold marker string");
assertIncludes(FILES.stringsZh, 'name="text_code_editor_settings"', "Simplified Chinese code editor settings string");
assertIncludes(FILES.stringsZh, 'name="text_editor_shortcut_input_bar"', "Simplified Chinese shortcut input bar string");
assertOrder(
  FILES.arrays,
  [
    '<string-array name="keys_editor_engine">',
    '<item>@string/key_editor_engine_ace</item>',
    '<item>@string/key_editor_engine_native</item>',
  ],
  "editor engine key order",
);
assertOrder(
  FILES.arrays,
  [
    '<string-array name="keys_editor_word_wrap_indent_style">',
    '<item>@string/key_editor_word_wrap_indent_style_continuation</item>',
    '<item>@string/key_editor_word_wrap_indent_style_classic</item>',
  ],
  "word wrap indentation key order",
);
assertOrder(
  FILES.arrays,
  [
    '<string-array name="keys_editor_gutter_width_mode">',
    '<item>@string/key_editor_gutter_width_mode_dynamic</item>',
    '<item>@string/key_editor_gutter_width_mode_fixed</item>',
  ],
  "gutter width mode key order",
);
assertIncludes(FILES.fragmentPreferences, 'app:itemDefaultKey="@string/default_key_editor_engine"');
assertOrder(
  FILES.fragmentPreferences,
  [
    'app:title="@string/text_code_editor"',
    'app:key="@string/key_editor_engine"',
    'app:key="@string/key_editor_auto_show_log_bottom_sheet_on_run"',
    'app:key="@string/key_editor_log_button_behavior"',
    'app:title="@string/text_all_settings"',
  ],
  "global code editor preference order",
);
assertOrder(
  FILES.codeEditorSettings,
  [
    'app:title="@string/text_general"',
    'app:key="@string/key_editor_engine"',
    'app:title="@string/text_appearance"',
    'app:key="@string/key_editor_theme"',
    'app:key="@string/key_ace_editor_font"',
    'app:key="@string/key_editor_text_size"',
    'app:key="@string/key_ace_word_wrap_enabled"',
    'app:key="@string/key_editor_word_wrap_indent_style"',
    'app:title="@string/text_log"',
    'app:key="@string/key_editor_log_button_behavior"',
    'app:key="@string/key_editor_auto_show_log_bottom_sheet_on_run"',
    'app:title="@string/text_editor_assistance"',
    'app:title="@string/text_editor_shortcut_input_bar"',
    'app:key="@string/key_ace_lsp_enabled"',
    'app:title="@string/text_editor_gestures"',
    'app:key="@string/key_editor_pinch_to_zoom_change_text_size_enabled"',
    'app:title="@string/text_editor_gutter_markers"',
    'app:key="@string/key_editor_gutter_width_mode"',
    'app:key="@string/key_editor_breakpoint_markers_enabled"',
    'app:key="@string/key_ace_line_numbers_enabled"',
    'app:key="@string/key_editor_fold_markers_enabled"',
  ],
  "dedicated code editor preference order",
);
assertOrder(
  FILES.editorMenu,
  [
    'android:id="@+id/action_force_stop"',
    'android:title="@string/text_edit"',
    'android:id="@+id/action_jump"',
    'android:id="@+id/action_debug"',
    'android:id="@+id/action_build_apk"',
    'android:id="@+id/action_log"',
    'android:id="@+id/action_editor_settings"',
    'android:title="@string/text_more"',
    'android:id="@+id/action_console"',
    'android:id="@+id/action_import_java_class"',
    'android:id="@+id/action_file_details"',
    'android:id="@+id/action_version_history"',
    'android:id="@+id/action_ace_read_only_mode"',
    'android:id="@+id/action_open_by_other_apps"',
  ],
  "editor overflow menu order",
);
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_editor_text_size"', "editor text size menu item moved to settings page");
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_ace_editor_font"', "editor font menu item moved to settings page");
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_editor_pinch_to_zoom"', "editor pinch menu item moved to settings page");
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_fx_keyboard"', "editor shortcut input menu item moved to settings page");
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_editor_theme"', "editor theme menu item moved to settings page");
assertNotIncludes(FILES.editorMenu, 'android:id="@+id/action_editor_engine"', "editor engine menu item moved to settings page");
assertIncludes(
  FILES.codeEditorSettingsFragment,
  "setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected, R.string.summary_ace_word_wrap_enabled)",
  "word wrap disabled outside ACE",
);
assertIncludes(
  FILES.codeEditorSettingsFragment,
  "syncWordWrapIndentPreferenceVisibility(aceSelected)",
  "word wrap indentation style visibility follows ACE and wrap state",
);
assertIncludes(FILES.codeEditorSettingsFragment, "R.string.key_editor_word_wrap_indent_style");
assertIncludes(
  FILES.codeEditorSettingsFragment,
  "setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected, R.string.summary_editor_gutter_width_mode)",
  "dynamic gutter width disabled outside ACE",
);
assertNotIncludes(
  FILES.codeEditorSettingsDialog,
  "MaterialDialog.Builder(requireContext()).build()",
  "MaterialDialog cannot back DialogFragment view content directly",
);
assertIncludes(FILES.codeEditorSettingsDialog, "CodeEditorSettingsFragment.newInstance(", "in-editor settings dialog uses shared settings fragment");
assertIncludes(FILES.codeEditorSettingsDialog, "recordAceActionFavorites", "in-editor settings dialog can track ACE setting actions");
assertIncludes(FILES.editorMenuHandler, "mEditorView.showEditorSettingsDialog()", "editor settings menu opens in-editor settings dialog");
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

assertIncludes(FILES.aceLspPreferences, "const val DEFAULT_ENABLED = true");
assertIncludes(FILES.aceLspPreferences, "Pref.getBoolean(KEY_ACE_LSP_ENABLED, DEFAULT_ENABLED)");
assertIncludes(FILES.aceLspPreferences, "fun resetEnabled()");

assertIncludes(FILES.aceLspServerManager, 'MANAGER_NAME = "native-local"');
assertIncludes(FILES.aceLspServerManager, 'STATE_DISABLED = "disabled"');
assertIncludes(FILES.aceLspServerManager, 'STATE_DISABLED_FILE_TYPE = "disabled-file-type"');
assertIncludes(FILES.aceLspServerManager, 'STATE_LOCAL_LANGUAGE_SERVICE = "local-language-service"');
assertIncludes(FILES.aceLspServerManager, 'TRANSPORT_IN_PROCESS = "in-process"');
assertIncludes(FILES.aceLspServerManager, 'FALLBACK_STATIC_COMPLETION = "static-completion"');
assertIncludes(FILES.aceLspServerManager, 'REASON_LOCAL_LANGUAGE_SERVICE = "bundled-typescript-language-service"');
assertIncludes(FILES.aceLspServerManager, 'COMPLETION_PROVIDER_LOCAL_INDEX = "local-index"');
assertIncludes(FILES.aceLspServerManager, 'HOVER_PROVIDER_LOCAL_INDEX = "local-index"');
assertIncludes(FILES.aceLspServerManager, 'DIAGNOSTIC_PROVIDER_ACE_JSHINT = "ace-jshint"');
assertIncludes(FILES.aceLspServerManager, 'SIGNATURE_PROVIDER_STATIC_LOCAL = "static-local"');
assertIncludes(FILES.aceLspServerManager, "MAX_DOCUMENT_LENGTH = 512 * 1024");
assertIncludes(FILES.aceLspServerManager, "maxDocumentLength = MAX_DOCUMENT_LENGTH");
assertIncludes(FILES.aceLspServerManager, "startSupported = false");
assertIncludes(FILES.aceLspServerManager, "serverAvailable = false");
assertNotIncludes(FILES.aceLspServerManager, "native-preview");
assertNotIncludes(FILES.aceLspServerManager, "configured-no-server");
assertNotIncludes(FILES.aceLspServerManager, "TRANSPORT_UNAVAILABLE");
assertNotIncludes(FILES.aceLspServerManager, "WebSocket");
assertNotIncludes(FILES.aceLspServerManager, "ProcessBuilder");
assertNotIncludes(FILES.aceLspServerManager, "Runtime.getRuntime()");

assertIncludes(FILES.aceLspClient, "DEFAULT_MAX_DOCUMENT_LENGTH = 512 * 1024");
assertIncludes(FILES.aceLspClient, "MAX_COMPLETION_ITEMS = 300");
assertIncludes(FILES.aceLspClient, "TS_LOAD_MAX_ATTEMPTS = 2");
assertIncludes(FILES.aceLspClient, "function scheduleTsLoadRetry(revision)");
assertIncludes(FILES.aceLspClient, "function disposeTsService()");
assertIncludes(FILES.aceLspClient, "semanticServiceSuppressed");
assertIncludes(FILES.aceLspClient, "document-too-large");
assertIncludes(FILES.aceLspClient, "slow-operation-circuit-open");
assertIncludes(FILES.aceLspClient, "destroy: destroy");
assertNotIncludes(FILES.aceLspClient, "WebSocket");
assertNotIncludes(FILES.aceLspClient, "new Worker");

assertIncludes(FILES.aceTsLanguageService, "global.AutoJsAceTsLanguageService");
assertIncludes(FILES.aceTsLanguageService, "jsx: ts.JsxEmit && ts.JsxEmit.Preserve");
assertIncludes(FILES.aceTsLanguageService, "entry.replacementSpan");
assertIncludes(FILES.aceTsLanguageService, "completion.replaceRange");
assertIncludes(FILES.aceTsLanguageService, "dispose: dispose");

assertIncludes(FILES.aceBridgeAsset, "global.requestIdleCallback(run, { timeout: 1500 })");
assertIncludes(FILES.aceBridgeAsset, 'global.addEventListener("pagehide", destroyLspClient, { once: true })');
assertIncludes(FILES.aceBridgeAsset, "function insertLspCompletion(activeEditor, completion)");

assertIncludes(FILES.aceLspClientTest, 'manager: "native-local"');
assertIncludes(FILES.aceLspClientTest, "real TypeScript service");
assertIncludes(FILES.aceLspClientTest, "typescript-language-service");
assertIncludes(FILES.aceLspClientTest, "Hot disable should disable the semantic service");
assertIncludes(FILES.aceLspClientTest, "Hot enable should recreate a ready TS service");
assertIncludes(FILES.aceLspClientTest, "Destroyed client should expose its terminal reason");
assertIncludes(FILES.aceLspClientTest, "document-too-large");
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
