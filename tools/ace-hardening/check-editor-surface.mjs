#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const ACE_ROOT = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
);
const HTML_PATH = path.join(ACE_ROOT, "autojs6_editor.html");
const CSS_PATH = path.join(ACE_ROOT, "autojs6", "autojs6.css");
const BRIDGE_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_ace_bridge.js");
const COMPLETER_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_completer.js");
const LSP_CLIENT_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_lsp_client.js");
const TS_SERVICE_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_ts_language_service.js");
const TOOLTIP_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_tooltip.js");
const SIGNATURE_HELP_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_signature_help.js");
const ACE_BRIDGE_KT_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "ace",
  "AceBridge.kt",
);
const ACE_CODE_EDITOR_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "ace",
  "AceCodeEditor.kt",
);
const ACE_LSP_PREFERENCES_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "ace",
  "AceEditorLspPreferences.kt",
);
const ACE_LSP_SERVER_MANAGER_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "ace",
  "lsp",
  "AceLspServerManager.kt",
);
const ACE_SCRIPT_EDITOR_ENGINE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "engine",
  "AceScriptEditorEngine.kt",
);
const NATIVE_CODE_EDITOR_ENGINE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "engine",
  "NativeCodeEditorEngine.kt",
);
const CODE_EDIT_TEXT_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "CodeEditText.kt",
);
const EDITOR_MENU_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "EditorMenu.java",
);
const EDITOR_VIEW_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "EditorView.kt",
);
const FIND_OR_REPLACE_DIALOG_BUILDER_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "FindOrReplaceDialogBuilder.kt",
);
const EDITOR_PINCH_TO_ZOOM_PREFERENCE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "EditorPinchToZoomPreference.kt",
);
const MATERIAL_PREFERENCE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "theme",
  "preference",
  "MaterialPreference.kt",
);
const CODE_COMPLETION_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "model",
  "autocomplete",
  "CodeCompletion.kt",
);
const CODE_COMPLETION_BAR_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "completion",
  "CodeCompletionBar.java",
);
const ACE_SHORTCUT_PANEL_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "AceShortcutPanel.kt",
);
const ACE_ACTION_HISTORY_STORE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "AceActionHistoryStore.kt",
);
const ACE_ACTION_FAVORITES_DIALOG_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "AceActionFavoritesDialog.kt",
);
const FUNCTIONS_KEYBOARD_HELPER_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "FunctionsKeyboardHelper.java",
);
const SYMBOLS_CONFIG_STORE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "SymbolsConfigStore.kt",
);
const SYMBOLS_SETTINGS_ACTIVITY_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "keyboard",
  "SymbolsSettingsActivity.kt",
);
const CODE_EDITOR_SETTINGS_DIALOG_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "CodeEditorSettingsDialog.kt",
);
const CODE_EDITOR_SETTINGS_FRAGMENT_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "CodeEditorSettingsFragment.kt",
);
const ACE_LSP_FILE_TYPES_PREFERENCE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "AceLspFileTypesPreference.kt",
);
const EDITOR_SETTING_ACTION_REGISTRY_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "EditorSettingActionRegistry.kt",
);
const CODE_EDITOR_SETTINGS_XML_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "res",
  "xml",
  "fragment_code_editor_settings.xml",
);
const FRAGMENT_HOST_MATERIAL_DIALOG_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "settings",
  "FragmentHostMaterialDialog.kt",
);
const DIAGNOSTICS_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
  "editor",
  "ace",
  "diagnostics",
  "AceDiagnostics.kt",
);

function read(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function assertIncludes(content, text, label = text) {
  if (!content.includes(text)) {
    throw new Error(`Missing ${label}`);
  }
}

function assertMatch(content, regex, label = regex.toString()) {
  if (!regex.test(content)) {
    throw new Error(`Missing ${label}`);
  }
}

function assertOrder(content, items, label) {
  let lastIndex = -1;
  for (const item of items) {
    const index = content.indexOf(item);
    if (index < 0) {
      throw new Error(`${label}: missing ${item}`);
    }
    if (index <= lastIndex) {
      throw new Error(`${label}: ${item} appears out of order`);
    }
    lastIndex = index;
  }
}

const html = read(HTML_PATH);
const css = read(CSS_PATH);
const bridge = read(BRIDGE_PATH);
const completer = read(COMPLETER_PATH);
const lspClient = read(LSP_CLIENT_PATH);
const tsService = read(TS_SERVICE_PATH);
const tooltip = read(TOOLTIP_PATH);
const signatureHelp = read(SIGNATURE_HELP_PATH);
const aceBridgeKt = read(ACE_BRIDGE_KT_PATH);
const aceCodeEditor = read(ACE_CODE_EDITOR_PATH);
const aceLspPreferences = read(ACE_LSP_PREFERENCES_PATH);
const aceLspServerManager = read(ACE_LSP_SERVER_MANAGER_PATH);
const aceScriptEditorEngine = read(ACE_SCRIPT_EDITOR_ENGINE_PATH);
const nativeCodeEditorEngine = read(NATIVE_CODE_EDITOR_ENGINE_PATH);
const codeEditText = read(CODE_EDIT_TEXT_PATH);
const editorMenu = read(EDITOR_MENU_PATH);
const editorView = read(EDITOR_VIEW_PATH);
const findOrReplaceDialogBuilder = read(FIND_OR_REPLACE_DIALOG_BUILDER_PATH);
const editorPinchToZoomPreference = read(EDITOR_PINCH_TO_ZOOM_PREFERENCE_PATH);
const materialPreference = read(MATERIAL_PREFERENCE_PATH);
const codeCompletion = read(CODE_COMPLETION_PATH);
const codeCompletionBar = read(CODE_COMPLETION_BAR_PATH);
const aceShortcutPanel = read(ACE_SHORTCUT_PANEL_PATH);
const aceActionHistoryStore = read(ACE_ACTION_HISTORY_STORE_PATH);
const aceActionFavoritesDialog = read(ACE_ACTION_FAVORITES_DIALOG_PATH);
const functionsKeyboardHelper = read(FUNCTIONS_KEYBOARD_HELPER_PATH);
const symbolsConfigStore = read(SYMBOLS_CONFIG_STORE_PATH);
const symbolsSettingsActivity = read(SYMBOLS_SETTINGS_ACTIVITY_PATH);
const codeEditorSettingsDialog = read(CODE_EDITOR_SETTINGS_DIALOG_PATH);
const codeEditorSettingsFragment = read(CODE_EDITOR_SETTINGS_FRAGMENT_PATH);
const aceLspFileTypesPreference = read(ACE_LSP_FILE_TYPES_PREFERENCE_PATH);
const editorSettingActionRegistry = read(EDITOR_SETTING_ACTION_REGISTRY_PATH);
const codeEditorSettingsXml = read(CODE_EDITOR_SETTINGS_XML_PATH);
const fragmentHostMaterialDialog = read(FRAGMENT_HOST_MATERIAL_DIALOG_PATH);
const diagnostics = read(DIAGNOSTICS_PATH);

assertOrder(
  html,
  [
    '<link rel="stylesheet" href="./autojs6/autojs6.css">',
    '<div id="editor"></div>',
    '<script src="./src-min-noconflict/ace.js"></script>',
    '<script src="./autojs6/autojs6_completer.js"></script>',
    '<script src="./autojs6/autojs6_lsp_client.js"></script>',
    '<script src="./autojs6/autojs6_tooltip.js"></script>',
    '<script src="./autojs6/autojs6_signature_help.js"></script>',
    '<script src="./autojs6/autojs6_ace_bridge.js"></script>',
  ],
  "ACE editor bootstrap order",
);
if (
  html.includes('<script src="./autojs6/typescript/typescriptServices.js"></script>') ||
  html.includes('<script src="./autojs6/autojs6_ts_language_service.js"></script>')
) {
  throw new Error("ACE TypeScript service must be loaded lazily after first paint");
}

assertMatch(css, /html,\s*body,\s*#editor\s*\{[\s\S]*overflow:\s*hidden/, "ACE root overflow guard");
assertMatch(css, /#editor\s*\{[\s\S]*visibility:\s*hidden/, "hidden editor before first paint");
assertMatch(css, /body\.ace-ready #editor\s*\{[\s\S]*visibility:\s*visible/, "visible editor after first paint");
assertMatch(css, /html\s*\{[\s\S]*background:\s*#f7f8fa/, "light first-paint background");
assertMatch(css, /html\.ace-theme-dark,\s*body\.ace-theme-dark,\s*body\.ace-theme-dark #editor\s*\{[\s\S]*background:\s*#1e1e1e/, "dark first-paint background");
assertIncludes(css, '--autojs6-editor-font-family: "Iosevka", "JetBrains Mono", "Source Code Pro", monospace;', "stable ACE font stack variable");
assertMatch(css, /#editor,\s*\.ace_editor\s*\{[\s\S]*font-family:\s*var\(--autojs6-editor-font-family\)/, "stable ACE font stack usage");
assertMatch(css, /#editor,\s*\.ace_editor\s*\{[\s\S]*-webkit-user-select:\s*text/, "ACE text selection enabled");
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*radial-gradient/, "breakpoint gutter marker");
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*7px center/, "breakpoint gutter marker anchor");
assertIncludes(css, "#editor.autojs6-hide-breakpoint-markers .ace_gutter-cell.ace_breakpoint", "breakpoint marker visibility class");
assertIncludes(css, "#editor.autojs6-hide-fold-markers .ace_fold-widget", "fold marker visibility class");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*position:\s*absolute/, "debug line marker positioning");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*z-index:\s*5/, "debug line marker z-index");
assertMatch(css, /\.ace_gutter,\s*\.ace_gutter-cell\s*\{[\s\S]*user-select:\s*none/, "gutter selection suppression");
assertMatch(css, /\.ace_mobile-menu\s*\{[\s\S]*z-index:\s*20/, "mobile menu remains available");
assertMatch(css, /body\.autojs6-native-action-mode \.ace_mobile-menu\s*\{[\s\S]*display:\s*none !important/, "native action-mode suppresses ACE mobile menu");
assertMatch(css, /body\.autojs6-native-action-mode \.ace_mobile-menu\s*\{[\s\S]*pointer-events:\s*none !important/, "suppressed ACE mobile menu cannot steal touch input");
assertMatch(css, /body\.autojs6-selection-menu-suppressed \.ace_mobile-menu\s*\{[\s\S]*display:\s*none !important/, "stale selection-menu suppression hides ACE mobile menu");
assertMatch(css, /body\.autojs6-selection-menu-suppressed \.ace_mobile-menu\s*\{[\s\S]*pointer-events:\s*none !important/, "stale selection-menu suppression blocks ACE mobile menu touch input");
assertMatch(css, /body\.autojs6-host-selection-action-mode \.ace_mobile-menu\s*\{[\s\S]*display:\s*none !important/, "host selection action-mode hides ACE mobile menu by default");
assertMatch(css, /body\.autojs6-host-selection-action-mode \.ace_mobile-menu\s*\{[\s\S]*pointer-events:\s*none !important/, "host selection action-mode blocks ACE mobile menu touch input");
assertMatch(css, /body\.ace-theme-dark \.ace_marker-layer \.ace_selection\s*\{[\s\S]*rgba\(88,\s*166,\s*255,\s*0\.42\)/, "dark selection contrast");
assertMatch(css, /\.ace_marker-layer \.ace_bracket\s*\{[\s\S]*background:\s*rgba\(20,\s*184,\s*166,\s*0\.22\)/, "bracket match contrast");
assertMatch(css, /body\.ace-theme-dark \.ace_marker-layer \.ace_bracket\s*\{[\s\S]*rgba\(45,\s*212,\s*191,\s*0\.28\)/, "dark bracket match contrast");
assertMatch(css, /\.autojs6_hover_tooltip\s*\{[\s\S]*pointer-events:\s*none/, "ACE hover tooltip must not steal touch input");
assertMatch(css, /body\.ace-theme-dark \.autojs6_hover_tooltip\s*\{[\s\S]*background:\s*#20242b/, "ACE hover tooltip dark style");
assertMatch(css, /\.autojs6_signature_tooltip\s*\{[\s\S]*pointer-events:\s*auto/, "ACE signature tooltip receives touch scrolling input");
assertMatch(css, /\.autojs6_signature_tooltip\s*\{[\s\S]*touch-action:\s*pan-x pan-y/, "ACE signature tooltip supports two-axis touch scrolling");
assertIncludes(css, ".autojs6_signature_tooltip_compact", "ACE signature tooltip compact fallback style");
assertMatch(css, /body\.ace-theme-dark \.autojs6_signature_tooltip\s*\{[\s\S]*background:\s*#f7f7f7/, "ACE signature tooltip dark editor contrast");
if (/^\.ace_mobile-menu\s*\{[^}]*display:\s*none/m.test(css)) {
  throw new Error("ACE mobile menu must not be globally hidden");
}

assertIncludes(bridge, "var firstPaintNotified = false;", "first-paint idempotency flag");
assertIncludes(bridge, "function callBridgeBoolean(name, defaultValue)", "bridge boolean option helper");
assertIncludes(bridge, "var STATE_NOTIFY_THROTTLE_MS = 100;", "state event throttle");
assertIncludes(bridge, "var TEXT_NOTIFY_THROTTLE_MS = 100;", "text event throttle");
assertIncludes(bridge, "var CURSOR_NOTIFY_THROTTLE_MS = 50;", "cursor event throttle");
assertIncludes(bridge, "function flushCursorChanged()", "cursor event flush");
assertIncludes(bridge, "function flushStateChanged()", "state event flush");
assertIncludes(bridge, "function flushTextChanged()", "text event flush");
assertIncludes(bridge, 'document.body.classList.add("ace-ready")', "ACE ready class add");
assertIncludes(bridge, 'callBridge("notifyEvent", ["firstPaint", lightweightStateJson()])', "first-paint bridge event");
assertIncludes(bridge, "var nativeActionModeActive = false;", "native action-mode state flag");
assertIncludes(bridge, "var selectionMenuSuppressed = false;", "selection menu suppression state flag");
assertIncludes(bridge, "var textMutationSelectionMenuSuppressUntil = 0;", "text-mutation selection-menu suppression window");
assertIncludes(bridge, "var imeTextInputSelectionWindow = {", "IME hidden textarea selection window state");
assertIncludes(bridge, "var ACTION_MODE_NOTIFY_THROTTLE_MS = 0;", "selection action-mode immediate handoff");
assertIncludes(bridge, "var TEXT_MUTATION_SELECTION_MENU_SUPPRESS_MS = 1200;", "text mutation selection-menu suppression duration");
assertIncludes(bridge, "var IME_TEXT_INPUT_SELECTION_MAX_DELTA = 64;", "IME hidden textarea selection max delta");
assertIncludes(bridge, "function setNativeActionModeActive(active)", "native action-mode state class helper");
assertIncludes(bridge, 'document.body.classList.toggle("autojs6-native-action-mode", active)', "native action-mode host class toggle");
assertIncludes(bridge, "function suppressSelectionActionModeMenu(reason)", "stale selection menu suppression helper");
assertIncludes(bridge, "function markTextMutationSelectionMenuSuppressed(reason)", "text mutation selection-menu suppression marker");
assertIncludes(bridge, "function shouldSuppressSelectionActionModeAfterTextMutation()", "text mutation selection-menu suppression predicate");
assertIncludes(bridge, 'suppressSelectionActionModeMenu("text_mutation_selection")', "text mutation selection-menu suppression use");
assertIncludes(bridge, "markTextMutationSelectionMenuSuppressed(operation)", "bridge text mutations suppress transient selection menus");
assertIncludes(bridge, 'markTextMutationSelectionMenuSuppressed("session_change:"', "session text changes suppress transient selection menus");
assertIncludes(bridge, "suppressSelectionActionModeMenu: suppressSelectionActionModeMenu", "native pointer guard selection menu suppression API");
assertIncludes(bridge, "function wrapScrollMethod(owner, methodName)", "scroll method hook wrapper");
assertIncludes(bridge, 'wrapScrollMethod(editor.renderer, "scrollToY")', "renderer vertical scroll hook");
assertIncludes(bridge, 'wrapScrollMethod(session, "setScrollTop")', "session vertical scroll hook");
assertIncludes(bridge, 'suppressSelectionActionModeMenu("browse_scroll")', "browse scroll suppresses stale selection menus");
assertIncludes(bridge, 'document.addEventListener("visibilitychange", handleDocumentVisibilityChanged)', "visibility changes suppress stale selection menus");
assertIncludes(bridge, "function getActionModeFallbackRect()", "non-zero action-mode fallback anchor");
assertIncludes(bridge, "function installImeTextInputSelectionWindow()", "Android IME hidden textarea selection installer");
assertIncludes(bridge, "function handleImeTextInputSelectionChange()", "Android IME hidden textarea selection handler");
assertIncludes(bridge, 'document.addEventListener("selectionchange", handleImeTextInputSelectionChange, true)', "Android IME selectionchange listener");
assertIncludes(bridge, "textInput.resetSelection();", "Android IME selection window reset uses ACE text input");
assertIncludes(bridge, '"imeTextInputSelectionDropped"', "Android IME large selection delta guard diagnostics");
assertIncludes(bridge, 'resetImeTextInputSelectionWindow("move_cursor")', "Android IME cursor movement resets textarea window");
assertMatch(bridge, /function afterTwoFrames\(callback\)[\s\S]*raf\(function\(\)[\s\S]*raf\(callback\);[\s\S]*\}\);[\s\S]*\}/, "double requestAnimationFrame first-paint guard");
assertMatch(bridge, /function requestFirstPaint\(reason\)[\s\S]*scheduleResize\(reason \|\| "first_paint"\);[\s\S]*requestFirstPaintCheck\(reason \|\| "first_paint"\);[\s\S]*\}/, "first-paint resize before visual readiness check");
assertIncludes(bridge, "requestFirstPaint: requestFirstPaint", "first-paint public API");
assertIncludes(bridge, "resizeFallbackTimer = setTimeout(function()", "resize fallback timer");
assertIncludes(bridge, 'JSON.stringify({ reason: String(reason || "") })', "resizeDone reason payload");
assertIncludes(bridge, 'editor.on("guttermousedown", function(event)', "gutter mouse handler");
assertIncludes(bridge, 'target.className.indexOf("ace_gutter-cell")', "gutter-cell guard");
assertIncludes(bridge, "function isGutterFoldWidgetEvent(target, domEvent)", "gutter fold widget breakpoint guard");
assertIncludes(bridge, "foldMarkersEnabled && isGutterFoldWidgetEvent(target, event.domEvent)", "breakpoint gutter handler preserves fold widget clicks");
assertIncludes(bridge, "getBoundingClientRect().left + 24", "gutter breakpoint tap zone");
assertIncludes(bridge, "event.stop();", "gutter click event stop");
assertIncludes(bridge, 'callBridgeBoolean("isWordWrapEnabled", false)', "ACE word wrap preference bridge");
assertIncludes(bridge, 'callBridgeString("getWordWrapIndentStyle", "continuation")', "ACE word wrap indentation preference bridge");
assertIncludes(bridge, 'callBridgeBoolean("isLineNumbersEnabled", true)', "ACE line number preference bridge");
assertIncludes(bridge, 'callBridgeBoolean("isBreakpointMarkersEnabled", true)', "ACE breakpoint marker preference bridge");
assertIncludes(bridge, 'callBridgeBoolean("isFoldMarkersEnabled", true)', "ACE fold marker preference bridge");
assertIncludes(bridge, 'callBridgeString("getGutterWidthMode", "dynamic")', "ACE gutter width mode preference bridge");
assertIncludes(bridge, 'fixedWidthGutter: gutterWidthMode === "fixed"', "preference-driven ACE gutter width");
assertIncludes(bridge, "function installDynamicWrapGutterWidthPatch()", "ACE dynamic gutter stays dynamic under word wrap");
assertIncludes(bridge, 'gutterWidthMode === "dynamic" && sessionForGutter && sessionForGutter.$useWrapMode', "ACE dynamic gutter patch detects wrapped sessions");
assertIncludes(bridge, "sessionForGutter.$useWrapMode = false", "ACE dynamic gutter patch bypasses wrap-mode fixed-width measurement");
assertIncludes(bridge, "showGutter: hasVisibleGutterContent()", "ACE gutter hides when all gutter content is disabled");
assertIncludes(bridge, 'setEditorContainerClass("autojs6-hide-gutter", !showGutter)', "ACE gutter hide class runtime sync");
assertIncludes(bridge, "indentedSoftWrap: normalizeWordWrapIndentStyle(wordWrapIndentStyle) !== \"classic\"", "ACE word wrap indentation init option");
assertIncludes(bridge, "showFoldWidgets: foldMarkersEnabled", "ACE fold widget preference option");
assertIncludes(bridge, "function expandFoldAt(row, column)", "fold placeholder expansion helper");
assertIncludes(bridge, "session.getFoldLine && session.getFoldLine(row)", "fold placeholder row fallback");
assertIncludes(bridge, 'hasClassToken(target, "ace_fold-widget")', "fold placeholder handler excludes gutter fold widget");
assertIncludes(bridge, "function isFoldPlaceholderTarget(target)", "fold placeholder target helper");
assertIncludes(bridge, "function expandFoldFromDomEvent(event)", "fold placeholder DOM capture expansion");
assertIncludes(bridge, "function handleFoldPlaceholderTouch(event)", "fold placeholder touch capture expansion");
assertIncludes(bridge, 'cancelAceMobileTouchInteraction("fold_placeholder_touch_start")', "fold placeholder touch start cancels stale ACE mobile touch");
assertIncludes(bridge, "if (beginFoldPlaceholderTouch(event))", "fold placeholder touchstart capture consumes fold taps");
assertIncludes(bridge, "if (updateFoldPlaceholderTouch(event))", "fold placeholder touchmove capture preserves fold tap ownership");
assertIncludes(bridge, "foldPlaceholderTouchExpanded", "fold placeholder touch expansion diagnostics");
assertIncludes(bridge, '["touchstart", "touchmove", "touchend", "touchcancel"].forEach(function(type)', "fold placeholder touch listener set");
assertIncludes(bridge, 'editor.container.addEventListener("click", expandFoldFromDomEvent, true)', "fold placeholder click capture listener");
assertIncludes(bridge, 'editor.on("click", function(event)', "fold placeholder click handler");
assertIncludes(bridge, "function cancelAceMobileTouchInteraction(reason)", "ACE mobile touch cancellation helper");
assertIncludes(bridge, "editor.$mouseHandler.isMousePressed = false", "ACE mouse pressed state reset on touch cancel");
assertIncludes(bridge, "editor.renderer.$isMousePressed = false", "ACE renderer pressed state reset on touch cancel");
assertIncludes(bridge, 'document.addEventListener("touchcancel", function(event)', "ACE DOM touchcancel cancellation listener");
assertIncludes(bridge, "cancelTouchInteraction: cancelAceMobileTouchInteraction", "ACE touch cancellation public API");
assertIncludes(bridge, "setWordWrapEnabled: setWordWrapEnabled", "ACE word wrap runtime API");
assertIncludes(bridge, "setWordWrapIndentStyle: setWordWrapIndentStyle", "ACE word wrap indentation runtime API");
assertIncludes(bridge, "setLineNumbersEnabled: setLineNumbersEnabled", "ACE line number runtime API");
assertIncludes(bridge, "setBreakpointMarkersEnabled: setBreakpointMarkersEnabled", "ACE breakpoint marker runtime API");
assertIncludes(bridge, "setFoldMarkersEnabled: setFoldMarkersEnabled", "ACE fold marker runtime API");
assertIncludes(bridge, "setGutterWidthMode: setGutterWidthMode", "ACE gutter width mode runtime API");
assertIncludes(bridge, "function applyFontSizeSp(size, options)", "ACE font-size helper without implicit resize");
assertIncludes(bridge, "function refreshFontMetricsForCoordinateMapping()", "ACE zoom refreshes font metrics for coordinate mapping");
assertIncludes(bridge, "refreshFontMetricsForCoordinateMapping();", "ACE font-size changes refresh coordinate metrics");
assertIncludes(bridge, "renderer.updateFontSize()", "ACE coordinate metric refresh uses renderer font-size update");
assertIncludes(bridge, "function scrollToPosition(scrollLeft, scrollTop)", "ACE programmatic scroll state sync helper");
assertIncludes(bridge, "session.setScrollLeft(nextLeft)", "ACE focus-preserving zoom syncs horizontal session scroll");
assertIncludes(bridge, "session.setScrollTop(nextTop)", "ACE focus-preserving zoom syncs vertical session scroll");
assertIncludes(bridge, "function resizeForFocusPreservingFontSize(reason)", "ACE focus-preserving zoom resize avoids cursor reveal");
assertIncludes(bridge, "function setFontSizeSpKeepingFocus", "ACE focus-preserving zoom helper");
assertIncludes(bridge, 'resizeForFocusPreservingFontSize("font_size_focus_before_scroll")', "ACE focus-preserving zoom resizes before anchor scroll");
assertIncludes(bridge, 'resizeForFocusPreservingFontSize("font_size_focus_after_scroll")', "ACE focus-preserving zoom resizes after anchor scroll");
assertIncludes(bridge, "setFontSizeSpKeepingFocus: setFontSizeSpKeepingFocus", "ACE focus-preserving zoom public API");
const focusZoomFunction = bridge.slice(
  bridge.indexOf("function setFontSizeSpKeepingFocus"),
  bridge.indexOf("function setFontFamily"),
);
if (focusZoomFunction.includes('scheduleResize("font_size_changed")')) {
  throw new Error("ACE focus-preserving zoom must not schedule cursor-revealing font-size resize");
}
assertIncludes(bridge, "function insertTab()", "ACE shortcut TAB helper");
assertIncludes(bridge, "function insertShortcutText(text)", "ACE shortcut text insertion helper");
assertIncludes(bridge, "function skipExistingClosingPair(text)", "ACE shortcut closing-pair skip helper");
assertIncludes(bridge, "shortcut_insert", "ACE shortcut insert suppresses stale IME selection sync");
assertIncludes(bridge, 'resetImeTextInputSelectionWindow("shortcut_insert")', "ACE shortcut insertion resets IME textarea selection window");
assertIncludes(bridge, "function installTouchCursorSelectionGuard()", "ACE touch cursor guard installer");
assertIncludes(bridge, "touch_cursor", "ACE touch cursor guard suppresses stale IME selection sync");
assertIncludes(bridge, "function isCompletionPopupOpen()", "ACE completion popup state helper");
assertIncludes(bridge, "function moveCompletionSelectionOrCursor(delta)", "ACE completion selection shortcut helper");
assertIncludes(bridge, "function acceptCompletionOrInsertTab()", "ACE completion accept shortcut helper");
assertIncludes(bridge, "function handleEscapeKey()", "ACE shortcut ESC helper");
assertIncludes(bridge, "function jumpToOffset(offset)", "ACE absolute cursor offset helper");
assertIncludes(bridge, 'session.remove(new Range(0, 0, lastRow, getLine(lastRow).length))', "ACE clear keeps undo history");
assertIncludes(bridge, "insertTab: insertTab", "ACE shortcut TAB public API");
assertIncludes(bridge, "jumpToOffset: jumpToOffset", "ACE absolute cursor offset public API");
assertIncludes(bridge, "acceptCompletionOrInsertTab: acceptCompletionOrInsertTab", "ACE completion accept public API");
assertIncludes(bridge, "moveCompletionSelectionOrCursor: moveCompletionSelectionOrCursor", "ACE completion selection public API");
assertIncludes(bridge, "handleEscapeKey: handleEscapeKey", "ACE shortcut ESC public API");
assertIncludes(bridge, "var lspClient = null;", "ACE LSP client bridge slot");
assertIncludes(bridge, "var lspCompletionCompleter = null;", "ACE LSP completion proxy slot");
assertIncludes(bridge, "global.AutoJsAceLspClient.install", "ACE LSP client install");
assertIncludes(bridge, "warmUpLspAfterFirstPaint", "ACE LSP first-paint warm-up hook");
assertIncludes(bridge, "function firstPaintReadinessSnapshot()", "ACE first-paint visual readiness check");
assertIncludes(bridge, "FIRST_PAINT_STABLE_FRAME_COUNT", "ACE first-paint stable-frame gate");
assertIncludes(bridge, "isFirstPaintThemeReady", "ACE first-paint waits for theme consistency");
assertIncludes(bridge, "firstPaintForced", "ACE first-paint timeout fallback event");
assertIncludes(bridge, "function installLspCompletionCompleter()", "ACE LSP completion proxy installer");
assertIncludes(bridge, "removeLanguageToolCompleter(staticCompleter)", "ACE LSP proxy replaces standalone static completer");
assertIncludes(bridge, "getLspState: function()", "ACE LSP state public API");
assertIncludes(bridge, "getLspHover: function(row, column)", "ACE LSP hover public API");
assertIncludes(bridge, "global.AutoJsAceTooltip.install", "ACE tooltip install");
assertIncludes(bridge, "showLspTooltip: function(row, column)", "ACE tooltip public show API");
assertIncludes(bridge, "getTooltipState: function()", "ACE tooltip state public API");
assertIncludes(bridge, "var signatureHelpController = null;", "ACE signature help bridge slot");
assertIncludes(bridge, "global.AutoJsAceSignatureHelp.install", "ACE signature help install");
assertIncludes(bridge, "getSignatureHelp: function(row, column)", "ACE signature help public lookup API");
assertIncludes(bridge, "showSignatureHelp: function(row, column)", "ACE signature help public show API");
assertIncludes(bridge, "getSignatureHelpState: function()", "ACE signature help state public API");

assertIncludes(lspClient, "global.AutoJsAceLspClient", "ACE LSP client global");
assertIncludes(lspClient, "COMPLETION_PROVIDER_TYPESCRIPT", "ACE LSP TypeScript completion provider");
assertIncludes(lspClient, "DIAGNOSTIC_PROVIDER_TYPESCRIPT", "ACE LSP TypeScript diagnostic provider");
assertIncludes(lspClient, "TS_RUNTIME_SCRIPT_SRC", "ACE LSP lazy TypeScript runtime script");
assertIncludes(lspClient, "TS_SERVICE_SCRIPT_SRC", "ACE LSP lazy TypeScript service script");
assertIncludes(lspClient, "ensureTsLanguageServiceLoaded", "ACE LSP lazy TypeScript loader");
assertIncludes(lspClient, "global.AutoJsAceTsLanguageService.create", "ACE LSP TypeScript service creation");
assertIncludes(lspClient, "documentUri: state.documentUri", "ACE LSP passes document URI into TypeScript service");
assertIncludes(lspClient, "isJsonDocumentUri", "ACE LSP JSON document detection");
assertIncludes(lspClient, "jsonSyntaxDiagnostics", "ACE LSP JSON syntax diagnostics");
assertIncludes(lspClient, "tsService.setDocumentUri(state.documentUri)", "ACE LSP refresh updates TypeScript document URI");
assertIncludes(lspClient, "getTsServiceState(state.enabled || !!tsService)", "ACE LSP avoids loading TS service while disabled");
assertIncludes(lspClient, "warmUp: warmUp", "ACE LSP exposes lazy warm-up hook");
assertIncludes(lspClient, "getCompletions: getCompletions", "ACE LSP completion hook");
assertIncludes(lspClient, "getHover: getHover", "ACE LSP hover hook");
if (lspClient.includes("WebSocket") || lspClient.includes("new Worker")) {
  throw new Error("LSP client must stay off WebSocket and worker transports in preview path");
}

assertIncludes(tsService, "global.AutoJsAceTsLanguageService", "ACE TypeScript service global");
assertIncludes(tsService, "typescriptServices.js unavailable", "ACE TypeScript service runtime guard");
assertIncludes(tsService, "loadTextFromBridge", "ACE TypeScript service native asset loader");
assertIncludes(tsService, "file:///autojs6/types/lib.autojs6.extra.d.ts", "ACE TypeScript service extra declaration lib");
assertIncludes(tsService, "function scriptKindForFileName", "ACE TypeScript service script-kind selection");
assertIncludes(tsService, "ts.ScriptKind.JSON", "ACE TypeScript service JSON script kind");
assertIncludes(tsService, "getScriptKind: function(fileName)", "ACE TypeScript host exposes script kind");
assertIncludes(tsService, "setDocumentUri: setDocumentUri", "ACE TypeScript service can change current document URI");
assertIncludes(tsService, "getCompletionsAtPosition", "ACE TypeScript service completion API");
assertIncludes(tsService, "getSignatureHelpItems", "ACE TypeScript service signature API");
assertIncludes(tsService, "getSemanticDiagnostics", "ACE TypeScript service diagnostics API");

assertIncludes(aceBridgeKt, "fun read(uri: String?): String", "native ACE bridge asset read hook");
assertIncludes(aceBridgeKt, "isAllowedReadAsset", "native ACE bridge read whitelist");
assertIncludes(aceBridgeKt, '"autojs6/typescript/"', "native ACE bridge permits TypeScript declaration assets only");
assertIncludes(aceBridgeKt, "fun write(uri: String?, text: String?): String", "native ACE bridge write remains explicit");
assertIncludes(aceBridgeKt, "File access is not available from the ACE editor bridge", "native ACE bridge keeps write disabled");

assertIncludes(aceLspPreferences, "KEY_ACE_LSP_FILE_TYPES", "ACE LSP file-type preference key");
assertIncludes(aceLspPreferences, "DEFAULT_FILE_TYPES", "ACE LSP default file-type suffixes");
assertIncludes(aceLspPreferences, "fun getFileTypes()", "ACE LSP file-type getter");
assertIncludes(aceLspPreferences, "fun resetFileTypes()", "ACE LSP file-type default reset");
assertIncludes(aceLspPreferences, "fun isDocumentAllowed(documentPathOrName: String?)", "ACE LSP document type gate");
assertIncludes(aceLspPreferences, "fun normalizeFileTypes", "ACE LSP file-type normalization");
assertIncludes(aceLspPreferences, '".node.js"', "ACE LSP default Node.js suffix");

assertIncludes(aceLspServerManager, "documentAllowedProvider", "ACE LSP manager injectable document-type gate");
assertIncludes(aceLspServerManager, 'MANAGER_NAME = "native-preview"', "ACE LSP manager uses preview identity");
assertIncludes(aceLspServerManager, "fun setDocumentPath(path: String?)", "ACE LSP manager document path setter");
assertIncludes(aceLspServerManager, "fun documentUriForPath(path: String?)", "ACE LSP manager derives document URI from path");
assertIncludes(aceLspServerManager, "encodePathSegment", "ACE LSP manager escapes synthetic document URI file name");
assertIncludes(aceLspServerManager, "STATE_DISABLED_FILE_TYPE", "ACE LSP unsupported file type state");
assertIncludes(aceLspServerManager, "REASON_DISABLED_FILE_TYPE", "ACE LSP unsupported file type reason");

assertIncludes(tooltip, "global.AutoJsAceTooltip", "ACE tooltip global");
assertIncludes(tooltip, "loadMarkdownRenderer: function()", "ACE tooltip lazy markdown renderer");
assertIncludes(tooltip, "escapeHtml", "ACE tooltip HTML escaping");
assertIncludes(tooltip, "pointerType === \"touch\"", "ACE tooltip touch guard");
assertIncludes(tooltip, "domCreated: !!element", "ACE tooltip lazy DOM state");
assertIncludes(tooltip, "markdownRenderer: markdownLoaded ? \"safe-subset\" : \"not-loaded\"", "ACE tooltip markdown state");
if (tooltip.includes("fetch(") || tooltip.includes("XMLHttpRequest") || tooltip.includes("innerHTML = hover.docText")) {
  throw new Error("Tooltip must keep markdown local and escaped");
}

assertIncludes(signatureHelp, "Function.prototype.apply", "ECMAScript apply signature help");
assertIncludes(signatureHelp, "findSignatureHelpFromText", "ACE signature help pure lookup");
assertIncludes(signatureHelp, "global.AutoJsAceSignatureHelp", "ACE signature help global");
if (signatureHelp.includes("fetch(") || signatureHelp.includes("XMLHttpRequest") || signatureHelp.includes("eval(")) {
  throw new Error("Signature help must stay local and parser-only");
}

assertIncludes(completer, "modules: Object.create(null)", "ACE completer module map must avoid prototype inheritance");
assertIncludes(completer, "function membersForModule(source, moduleName)", "ACE completer safe module lookup helper");
assertIncludes(completer, "Object.prototype.hasOwnProperty.call(source.modules, moduleName)", "ACE completer module lookup must ignore inherited prototype keys");
assertIncludes(completer, "var ECMASCRIPT_GLOBALS = [", "ACE completer ECMAScript global completion source");
assertIncludes(completer, "var ECMASCRIPT_MODULES = {", "ACE completer ECMAScript module completion source");
assertIncludes(completer, '"Object.prototype"', "ACE completer Object prototype completion source");
assertIncludes(completer, "hasOwnProperty", "ACE completer Object.prototype.hasOwnProperty completion");
assertIncludes(completer, "function appendEcmascriptBuiltins(source)", "ACE completer merges ECMAScript built-ins");
assertIncludes(completer, "function membersForContext(source, moduleName)", "ACE completer context member lookup");
assertIncludes(completer, 'membersForModule(source, "Object.prototype")', "ACE completer object prototype fallback");

assertIncludes(aceCodeEditor, "private fun requestFirstPaintWhenStable()", "native first-paint request hook");
assertIncludes(aceCodeEditor, 'invokeAce("requestFirstPaint", quote("native_ready"))', "native first-paint bridge call");
assertIncludes(aceCodeEditor, "private fun handleFirstPaint()", "native first-paint handler");
assertIncludes(aceCodeEditor, "hideLoadingOverlay()", "first-paint overlay hide");
assertIncludes(aceCodeEditor, '"resizeDone" -> handleResizeDone(payloadJson)', "native resizeDone handler");
assertIncludes(aceCodeEditor, "lastAceResizeCompletedReason = resizeReasonFromPayload", "native resize completion reason");
assertIncludes(aceCodeEditor, "BRIDGE_EVENT_STORM_THRESHOLD", "bridge event storm threshold");
assertIncludes(aceCodeEditor, "if (bridgeEventWindowCount == BRIDGE_EVENT_STORM_THRESHOLD)", "bridge event storm threshold gate");
assertIncludes(aceCodeEditor, '"bridge_event_storm",', "bridge event storm diagnostics event");
assertIncludes(aceCodeEditor, '"count=$bridgeEventWindowCount,windowMillis=$BRIDGE_EVENT_WINDOW_MS,last=$name"', "bridge event storm diagnostics payload");
assertIncludes(aceCodeEditor, "private val lspServerManager = AceLspServerManager()", "ACE LSP manager");
assertIncludes(aceCodeEditor, "lspServerManager.bridgeOptionsJson()", "ACE LSP bridge options");
assertIncludes(aceCodeEditor, "fun setDocumentPath(path: String?)", "ACE LSP document path bridge");
assertIncludes(aceCodeEditor, "lspServerManager.setDocumentPath(path)", "ACE LSP manager receives document path");
assertIncludes(aceCodeEditor, 'invokeAce("refreshLsp")', "ACE LSP runtime refresh hook");
assertIncludes(aceCodeEditor, 'invokeAce("setWordWrapIndentStyle", quote(EditorDisplayPreferences.wordWrapIndentStyle()))', "native ACE word wrap indentation runtime bridge call");
assertIncludes(aceCodeEditor, "bridgeWordWrapIndentStyle", "native ACE word wrap indentation bridge getter");
assertIncludes(aceCodeEditor, 'eventHistory.record("lsp_manager"', "ACE LSP manager event history");
assertIncludes(aceCodeEditor, "ScaleGestureDetector", "ACE pinch zoom detector");
assertIncludes(aceCodeEditor, "PinchZoomStrategy", "ACE pinch zoom strategy model");
assertIncludes(aceCodeEditor, "handlePinchToZoomTouch", "ACE pinch zoom touch handler");
assertIncludes(aceCodeEditor, "cancelAceTouchInteraction(\"pinch_zoom_begin\")", "ACE pinch begin cancels stale mobile touch state");
assertIncludes(aceCodeEditor, "cancelAceTouchInteraction(\"pinch_zoom_end\")", "ACE pinch end cancels stale mobile touch state");
assertIncludes(aceCodeEditor, "scaleViewBy", "ACE scale-view zoom handler");
assertIncludes(aceCodeEditor, "setFontSizeSpKeepingFocus", "ACE focus-preserving zoom native call");
assertIncludes(aceCodeEditor, "currentScaleViewTextSizeSp", "ACE scale-view effective text-size tracker");
assertIncludes(aceCodeEditor, "commitScaleViewTextSizeGesture", "ACE scale-view final text-size commit");
assertIncludes(aceCodeEditor, "commitScaleViewTextSizeGesture(lastPinchFocusX, lastPinchFocusY)", "ACE scale-view pinch commits on gesture end");
assertIncludes(aceCodeEditor, "onTextSizeSpChangedByGesture?.invoke(committedSize, true)", "ACE scale-view pinch saves committed text size");
assertIncludes(aceCodeEditor, "fun insertTab()", "native ACE shortcut TAB bridge call");
assertIncludes(aceCodeEditor, "fun acceptCompletionOrInsertTab()", "native ACE completion accept bridge call");
assertIncludes(aceCodeEditor, "fun moveCompletionSelectionOrCursor", "native ACE completion navigation bridge call");
assertIncludes(aceCodeEditor, "fun handleEscapeKey()", "native ACE shortcut ESC bridge call");
assertIncludes(aceCodeEditor, "fun onSelectionAction(action: SelectionAction)", "ACE selection action callback");
assertIncludes(aceCodeEditor, "enum class SelectionAction", "ACE selection action model");
assertIncludes(aceCodeEditor, "handleSoftKeyboardCursorKey", "ACE soft-keyboard cursor key handler");
assertIncludes(aceCodeEditor, "handleSoftKeyboardCursorSelection", "ACE soft-keyboard InputConnection cursor selection handler");
assertIncludes(aceCodeEditor, "InputConnectionWrapper", "ACE WebView InputConnection wrapper");
assertIncludes(aceCodeEditor, "override fun setSelection(start: Int, end: Int)", "ACE IME setSelection cursor interception");
assertIncludes(aceCodeEditor, "InputConnection.setSelection.textarea", "ACE IME setSelection passes local textarea selection");
assertIncludes(aceCodeEditor, "mode=textarea_window", "ACE IME selection diagnostics use textarea-window mode");
assertIncludes(aceCodeEditor, "document-absolute fallback moves", "ACE IME setSelection avoids document-absolute fallback moves");
assertIncludes(aceCodeEditor, '"ime_cursor_selection"', "ACE IME setSelection diagnostics event");
assertIncludes(aceCodeEditor, 'invokeAce("cancelTouchInteraction", quote(reason))', "native pointer guard cancels ACE touch interaction");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_LEFT", "ACE soft-keyboard cursor left key");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_RIGHT", "ACE soft-keyboard cursor right key");
assertIncludes(aceCodeEditor, "KeyEvent.FLAG_SOFT_KEYBOARD", "ACE soft-keyboard cursor source guard");
assertIncludes(aceCodeEditor, "startingHostSelectionActionMode", "ACE host-owned action-mode guard");
assertIncludes(aceCodeEditor, "pendingSelectionActionModeRequest", "ACE touch-deferred action mode request");
assertIncludes(aceCodeEditor, "restoreSelectionActionModeRunnable", "ACE selection action-mode restore runnable");
assertIncludes(aceCodeEditor, "lastSelectionActionModeRequest", "ACE remembers last selection action-mode request");
assertIncludes(aceCodeEditor, "selectionActionModeExpectedFinish", "ACE distinguishes expected action-mode finishes");
assertIncludes(aceCodeEditor, "cancelSelectionActionModeRestore", "ACE cancels stale action-mode restore callbacks");
assertIncludes(aceCodeEditor, "stabilizeSelectionActionModeAfterTouch", "ACE post-touch action-mode stabilization");
assertIncludes(aceCodeEditor, "maybeRestoreUnexpectedSelectionActionModeAfterTouch", "ACE unexpected action-mode finish restore guard");
assertIncludes(aceCodeEditor, "restoreUnexpectedSelectionActionModeAfterTouch", "ACE unexpected action-mode restore helper");
assertIncludes(aceCodeEditor, '"action_mode_restore_after_touch"', "ACE action-mode restore diagnostics");
assertIncludes(aceCodeEditor, "ACTION_MODE_POST_TOUCH_STABILIZE_MS", "ACE action-mode post-touch stabilization window");
assertIncludes(aceCodeEditor, "ACTION_MODE_RESTORE_AFTER_TOUCH_DELAY_MS", "ACE action-mode restore delay");
assertIncludes(aceCodeEditor, "hasRecentTextTouchAnchor", "ACE touch-anchored action-mode rect");
assertIncludes(aceCodeEditor, "isProbablyInvalidActionModeRect", "ACE action-mode invalid rect guard");
assertIncludes(aceCodeEditor, "setFallbackSelectionActionModeRect", "ACE action-mode non-zero fallback rect");
assertIncludes(aceCodeEditor, "invalidateContentRect()", "ACE floating action-mode rect invalidation");
assertIncludes(aceCodeEditor, "override fun startActionMode(callback: ActionMode.Callback", "ACE WebView action-mode suppression");
assertIncludes(aceCodeEditor, '"webview_action_mode_suppressed"', "ACE WebView action-mode suppression diagnostics");
assertIncludes(aceCodeEditor, "handleSingleTouchScrollGuard", "ACE single-touch scroll guard");
assertIncludes(aceCodeEditor, "singleTouchScrollCancelSlopPx", "ACE single-touch scroll guard slop");
assertIncludes(aceCodeEditor, 'cancelAceTouchInteraction("single_finger_scroll")', "ACE single-touch scroll cancels stale mobile touch state");

assertIncludes(aceScriptEditorEngine, "notifyPinchToZoomStrategyChanged", "ACE pinch zoom strategy forwarding");
assertIncludes(aceScriptEditorEngine, "onTextSizeSpChangedByGesture", "ACE pinch text-size state callback");
assertIncludes(aceScriptEditorEngine, "var onAceStateChanged", "ACE engine state-change callback");
assertIncludes(aceScriptEditorEngine, "override fun onStateChanged", "ACE engine forwards bridge state changes");
assertIncludes(aceScriptEditorEngine, "override fun setDocumentPath(path: String?)", "ACE engine forwards LSP document path");
assertIncludes(aceScriptEditorEngine, "fun refreshLsp()", "ACE engine exposes LSP refresh");
assertIncludes(nativeCodeEditorEngine, "codeEditor.setWordWrapEnabled(false)", "Native editor keeps word wrap disabled");
assertIncludes(nativeCodeEditorEngine, "gutterWidthMode = EditorDisplayPreferences.GUTTER_WIDTH_MODE_FIXED", "Native editor keeps fixed gutter width");
assertIncludes(codeEditText, "fun setWordWrapEnabled(enabled: Boolean)", "Native word wrap edit text toggle");
assertIncludes(codeEditText, "fun getLogicalLineForVisualLine", "Native wrapped visual-line to logical-line mapping");
assertIncludes(editorPinchToZoomPreference, "current == key(R.string.key_editor_pinch_to_zoom_scale_view)", "scale-view pinch preference is normalized for unsupported engines");
assertIncludes(editorPinchToZoomPreference, "key(R.string.key_editor_pinch_to_zoom_change_text_size)", "unsupported scale-view preference falls back to text-size pinch");

assertIncludes(editorView, "AceShortcutPanel.topRowCompletions", "ACE shortcut top row binding");
assertIncludes(editorView, "AceShortcutPanel.bottomRowCompletions", "ACE shortcut bottom row binding");
assertIncludes(editorView, "SymbolsConfigStore.DefaultSymbolSet.ACE", "ACE shortcut symbol defaults binding");
assertIncludes(symbolsConfigStore, "defaultSymbolSet: DefaultSymbolSet = DefaultSymbolSet.NATIVE", "symbols store default symbol-set parameter");
assertIncludes(symbolsConfigStore, "name == PROFILE_DEFAULT_ID && defaultSymbolSet == DefaultSymbolSet.ACE", "symbols store ACE default profile lookup");
assertIncludes(symbolsSettingsActivity, "EditorEnginePreferences.get()", "symbols settings current editor engine lookup");
assertIncludes(symbolsSettingsActivity, "loadProfileForCurrentEngine", "symbols settings engine-aware default profile load");
assertIncludes(symbolsSettingsActivity, "exportProfileToJson(this, currentProfileName, defaultSymbolSet)", "symbols settings engine-aware default profile export");
assertIncludes(editorView, "mAceSymbolsBar.useFlowLayout()", "ACE shortcut symbol flow layout");
assertIncludes(editorView, "mAceShortcutTopBar.setFixedItemSizeDp(40, 35)", "ACE shortcut fixed top key size");
assertIncludes(editorView, "mAceShortcutBottomBar.setFixedItemSizeDp(40, 35)", "ACE shortcut fixed bottom key size");
assertIncludes(editorView, "handleAceShortcutClick", "ACE shortcut click handler");
assertIncludes(editorView, "handleAceShortcutLongClick", "ACE shortcut long-click handler");
assertIncludes(editorView, "executeAceShortcutAction(it, record = false)", "ACE shortcut short clicks stay out of action history");
assertIncludes(editorView, "jumpWithAceShortcutReturnPoint", "ACE shortcut long-press return point");
assertIncludes(editorView, "EditorDisplayPreferences.KEY_WORD_WRAP_INDENT_STYLE", "editor display preference live listener includes word wrap indentation");
assertIncludes(editorView, "editor.notifyDisplayPreferencesChanged()", "editor display preference changes apply to current engine");
assertIncludes(editorView, "key(R.string.key_ace_editor_font)", "editor font preference changes apply to current ACE editor");
assertIncludes(editorView, "key(R.string.key_editor_text_size)", "editor text-size preference changes apply to current editor");
assertIncludes(editorView, "key(R.string.key_editor_theme)", "editor theme preference changes apply to current editor");
assertIncludes(editorView, "key(R.string.key_editor_engine)", "editor engine preference changes apply to current editor");
assertIncludes(editorView, "key(R.string.key_ace_lsp_file_types)", "LSP file-type preference changes refresh current ACE editor");
assertIncludes(editorView, "themeFontService.applyEditorPreferences()", "editor font/text-size live preference applier");
assertIncludes(editorView, "applyCurrentEditorThemePreference", "editor theme live preference applier");
assertIncludes(editorView, "applyCurrentEditorEnginePreference", "editor engine live preference applier");
assertIncludes(editorView, "switchEditorEngine(EditorEnginePreferences.get())", "editor engine live preference switcher");
assertIncludes(editorView, "currentDocumentPathOrName", "editor tracks current document path for LSP");
assertIncludes(editorView, "syncEditorDocumentPath()", "editor syncs document path into ACE LSP manager");
assertIncludes(editorView, "editor.setDocumentPath(currentDocumentPathOrName())", "editor forwards document path to current engine");
assertIncludes(editorView, "toggleAceShortcutReturnPoint", "ACE shortcut TAB return toggle");
assertIncludes(editorView, "showAceActionHistoryDialog", "ACE action history dialog");
assertIncludes(editorView, "repeatLastAceAction", "ACE repeat last action handler");
assertIncludes(editorView, "AceActionFavoritesDialog.show", "ACE action favorites dialog integration");
assertIncludes(editorView, "fun recordAceActionForMenuItem", "ACE action history menu recording entry point");
assertIncludes(editorView, "aceEditor.onAceStateChanged", "ACE state changes refresh editor toolbar state");
assertIncludes(editorView, "actionForAceSelectionAction", "ACE selection action history mapping");
assertIncludes(editorView, "private fun executeAceHistoryAction", "ACE generic action history executor");
assertIncludes(editorView, "private fun actionForToolbarItem", "ACE toolbar action history mapping");
assertIncludes(editorView, "private fun actionForMenuItem", "ACE menu action history mapping");
assertIncludes(editorView, "fun showFindOrReplaceDialog", "editor action history find/replace dialog launcher");
assertIncludes(editorView, "fun showJumpToLineDialog", "editor action history jump-to-line dialog launcher");
assertIncludes(editorView, "fun startBuildApkActivity", "editor action history build APK launcher");
assertIncludes(editorView, "fun showFileDetailsDialog", "editor action history file details launcher");
assertIncludes(editorView, "fun showJavaClassSearchDialog", "editor action history Java class search launcher");
assertIncludes(editorView, "ACTION_FIND_OR_REPLACE", "editor action history find/replace action id");
assertIncludes(editorView, "ACTION_COPY_SELECTION", "editor action history copy selection action id");
assertIncludes(editorView, "ACTION_SELECT_ALL", "editor action history select-all action id");
assertIncludes(editorView, "ACTION_JUMP_TO_LINE", "editor action history jump-to-line action id");
assertIncludes(editorView, "ACTION_JUMP_TO_PREVIOUS_CURSOR", "editor action history previous cursor action id");
assertIncludes(editorView, "ACTION_BUILD_APK", "editor action history build APK action id");
assertIncludes(editorView, "ACTION_IMPORT_JAVA_CLASS", "editor action history Java class search action id");
assertIncludes(editorView, "ACTION_FILE_DETAILS", "editor action history file details action id");
assertIncludes(editorView, "recordAceFindOrReplaceHistory", "editor action history records find/replace payloads");
assertIncludes(editorView, "recordAceJumpToLineHistory", "editor action history records jump-to-line payloads");
assertIncludes(editorView, "recordAceJavaClassImportHistory", "editor action history records Java import payloads");
assertIncludes(editorView, "isDeferredParameterizedHistoryAction", "editor action history defers dialog parameter recording until confirmation");
assertIncludes(editorView, "PAYLOAD_QUERY", "editor action history query payload key");
assertIncludes(editorView, "PAYLOAD_LINE", "editor action history line payload key");
assertIncludes(editorView, "PAYLOAD_IMPORT_TEXT", "editor action history Java import payload key");
assertIncludes(editorView, "executeEditorSettingHistoryAction", "editor action history open-ended setting action replay");
assertIncludes(editorView, "EditorSettingActionRegistry.preferenceKeyFromActionId", "editor setting action id decoding");
assertIncludes(editorView, "suppressInitialSettingActionRecord = true", "editor setting action replay avoids duplicate recording");
assertIncludes(findOrReplaceDialogBuilder, "recordAceFindOrReplaceHistory", "find/replace dialog records ACE action payload after successful confirmation");
assertIncludes(editorMenu, "mEditorView.recordAceActionForMenuItem(itemId)", "editor overflow menu records ACE actions");
assertIncludes(editorMenu, "mEditorView.showEditorSettingsDialog()", "editor overflow settings menu enables ACE setting-action tracking");
assertIncludes(editorMenu, "mEditorView::showFindOrReplaceDialog", "editor overflow menu shares find/replace action launcher");
assertIncludes(editorMenu, "mEditorView::showJumpToLineDialog", "editor overflow menu shares jump-to-line action launcher");
assertIncludes(editorMenu, "mEditorView::startBuildApkActivity", "editor overflow menu shares build APK launcher");
assertIncludes(editorMenu, "mEditorView::showFileDetailsDialog", "editor overflow menu shares file details launcher");
assertIncludes(editorMenu, "mEditorView::showJavaClassSearchDialog", "editor overflow menu shares Java class search launcher");
assertIncludes(editorMenu, "prepareReadOnlyMenu(menu)", "editor read-only menu preparation hook");
assertIncludes(editorMenu, "item.setVisible(visible)", "editor read-only menu respects intent lock visibility");
assertIncludes(editorMenu, "item.setEnabled(mEditorView.canToggleReadOnlyMode())", "editor read-only menu respects toggle availability");
assertIncludes(editorMenu, "item.setCheckable(true)", "editor read-only menu uses native checked state");
assertIncludes(editorMenu, "item.setChecked(readOnly)", "editor read-only menu syncs checked state");
assertIncludes(functionsKeyboardHelper, "mFunctionsLayout == null", "function keyboard hide tolerates missing layout");
assertIncludes(functionsKeyboardHelper, "mFunctionsLayout.setVisibility(View.GONE)", "function keyboard hide always clears visibility");
assertIncludes(functionsKeyboardHelper, "params != null && params.height != 0", "function keyboard hide clears stale height");
assertIncludes(functionsKeyboardHelper, "showSoftInput && wasVisible", "function keyboard hide preserves soft-input restore guard");

assertIncludes(codeCompletion, "val iconResId", "code completion optional icon model");
assertIncludes(codeCompletionBar, "getIconResId(position)", "code completion optional icon lookup");
assertIncludes(codeCompletionBar, "setForeground(new FixedSizeDrawable(wrappedIcon, iconSize))", "completion bar icon rendering");
assertIncludes(codeCompletionBar, "setForegroundGravity(Gravity.CENTER)", "completion bar centered icon rendering");
assertIncludes(codeCompletionBar, "DrawableCompat.setTint", "completion bar themed icon tint");
assertIncludes(codeCompletionBar, "useFlowLayout()", "completion bar flow layout mode");
assertIncludes(codeCompletionBar, "setFixedItemSizeDp", "completion bar fixed item size mode");

assertIncludes(aceShortcutPanel, 'command("ESC", Command.Escape)', "ACE shortcut ESC key");
assertIncludes(aceShortcutPanel, 'command("TAB", Command.Tab)', "ACE shortcut TAB key");
assertIncludes(aceShortcutPanel, 'command("☆", Command.ActionFavorites, R.drawable.ic_star_border_black_48dp)', "ACE shortcut favorites hollow star entry");
assertIncludes(aceShortcutPanel, 'R.drawable.ic_keyboard_arrow_up_black_48dp', "ACE shortcut arrow-up drawable");
assertIncludes(aceShortcutPanel, 'R.drawable.ic_keyboard_arrow_down_black_48dp', "ACE shortcut arrow-down drawable");
assertIncludes(aceShortcutPanel, 'R.drawable.ic_keyboard_arrow_left_black_48dp', "ACE shortcut arrow-left drawable");
assertIncludes(aceShortcutPanel, 'R.drawable.ic_keyboard_arrow_right_black_48dp', "ACE shortcut arrow-right drawable");
assertIncludes(aceShortcutPanel, "data class Action", "ACE shortcut action model");
assertIncludes(aceShortcutPanel, "fun actionOf(command: Command)", "ACE shortcut command action lookup");
assertIncludes(aceShortcutPanel, "fun actionById", "ACE shortcut stable action lookup");

assertIncludes(aceActionHistoryStore, "fun record(", "ACE action history record API");
assertIncludes(aceActionHistoryStore, "data class Action", "ACE generic action history model");
assertIncludes(aceActionHistoryStore, "fun getRankedEntries", "ACE action history ranking API");
assertIncludes(aceActionHistoryStore, "fun getLastActionId", "ACE action history last action API");
assertIncludes(aceActionHistoryStore, "fun getLastAction", "ACE generic last action lookup");
assertIncludes(aceActionHistoryStore, "values.sortedWith(entryComparator())", "ACE action favorites ranking uses current comparator");
assertIncludes(aceActionHistoryStore, 'LEGACY_SHORTCUT_ACTION_PREFIX = "command:"', "ACE action favorites filters legacy shortcut clicks");
assertIncludes(aceActionHistoryStore, 'DEPRECATED_CANCEL_SEARCH_ACTION_ID = "editor:cancel_search"', "ACE action favorites filters accidental cancel-search action");
assertIncludes(aceActionHistoryStore, "fun applyFavoritesOrder", "ACE action favorites order API");
assertIncludes(aceActionHistoryStore, "mutatesText", "ACE action history mutation metadata");
assertIncludes(aceActionHistoryStore, "payloadJson", "ACE action history payload metadata");
assertIncludes(aceActionHistoryStore, "payloadSummary", "ACE action history payload summary metadata");
assertIncludes(aceActionHistoryStore, "usageCount", "ACE action history frequency field");
assertIncludes(aceActionHistoryStore, "lastUsedAt", "ACE action history recency field");
assertIncludes(aceActionHistoryStore, "manualOrder", "ACE action history manual order field");
assertIncludes(aceActionHistoryStore, "private fun compareUnpinned(left: Entry, right: Entry): Int", "ACE action favorites unpinned comparator");
assertIncludes(aceActionHistoryStore, "compareValues(right.lastUsedAt, left.lastUsedAt)", "ACE action favorites unpinned recency ranking");

assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT", "ACE action favorites swipe gestures");
assertIncludes(aceActionFavoritesDialog, "onActionSelected: (AceActionHistoryStore.Action) -> Unit", "ACE action favorites generic callback");
assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.UP or ItemTouchHelper.DOWN", "ACE action favorites drag reorder");
assertIncludes(aceActionFavoritesDialog, "R.drawable.ic_thumbtacks", "ACE action favorites thumbtack icon");
assertIncludes(aceActionFavoritesDialog, 'holder.usageCount.text = "<${entry.usageCount}>"', "ACE action favorites usage count inline");
assertIncludes(aceActionFavoritesDialog, "payloadSummary", "ACE action favorites show payload summary");
assertIncludes(aceActionFavoritesDialog, "fun finishDrag()", "ACE action favorites drag finalization");
assertIncludes(aceActionFavoritesDialog, "confirmDelete", "ACE action favorites delete confirmation");
assertIncludes(aceActionFavoritesDialog, "confirmClear", "ACE action favorites clear confirmation");
assertIncludes(aceActionFavoritesDialog, "togglePinned", "ACE action favorites pin toggle");
assertIncludes(aceActionFavoritesDialog, "persistOrder", "ACE action favorites order persistence");
if (aceActionFavoritesDialog.includes("notifyItemRangeChanged(0, itemCount)")) {
  throw new Error("ACE action favorites must not rebind the full list while dragging");
}

assertIncludes(codeEditorSettingsDialog, "FragmentHostMaterialDialog.Builder", "editor settings dialog uses fragment-safe MaterialDialog host");
assertIncludes(codeEditorSettingsDialog, ".widgetThemeColor()", "editor settings dialog uses themed MaterialDialog buttons");
assertIncludes(codeEditorSettingsDialog, "initialPreferenceKey", "editor settings dialog can open a requested preference");
assertIncludes(codeEditorSettingsDialog, "recordAceActionFavorites", "editor settings dialog can enable ACE action tracking");
assertIncludes(codeEditorSettingsFragment, "setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected", "editor settings disables word wrap for non-Ace engines");
assertIncludes(codeEditorSettingsFragment, "setPreferenceAvailable(R.string.key_ace_lsp_enabled, aceSelected", "editor settings disables LSP for non-Ace engines");
assertIncludes(codeEditorSettingsFragment, "syncAceLspFileTypesPreferenceVisibility", "editor settings updates LSP file-type preference visibility");
assertIncludes(codeEditorSettingsFragment, "AceEditorLspPreferences.isEnabled()", "editor settings hides LSP file-type preference while LSP is disabled");
assertIncludes(codeEditorSettingsFragment, "setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected", "editor settings disables gutter width mode for non-Ace engines");
assertIncludes(codeEditorSettingsFragment, "unavailablePreferenceKeys.add(preferenceKey)", "editor settings tracks unavailable preferences");
assertIncludes(codeEditorSettingsFragment, "useStaticSummaryProvider(unavailableSummary)", "editor settings shows unavailable summary for list preferences");
assertIncludes(codeEditorSettingsFragment, "isEnabled = available || this !is SwitchPreference", "editor settings keep non-switch unavailable items clickable for feedback");
assertIncludes(codeEditorSettingsFragment, "showUnavailableEditorSettingHint", "editor settings show unavailable-option feedback");
assertIncludes(codeEditorSettingsFragment, "ViewUtils.showSnack(requireView(), message)", "editor settings dialog uses snackbar for unavailable options");
assertIncludes(codeEditorSettingsFragment, "ViewUtils.showToast(requireContext(), message)", "standalone editor settings page uses toast for unavailable options");
assertIncludes(codeEditorSettingsFragment, "recordAceSettingAction", "editor settings records open-ended setting actions");
assertIncludes(codeEditorSettingsFragment, "EditorSettingActionRegistry.actionIdForPreferenceKey", "editor settings records stable setting action ids");
assertIncludes(aceLspFileTypesPreference, "class AceLspFileTypesPreference", "ACE LSP file-type preference widget");
assertIncludes(aceLspFileTypesPreference, "AceEditorLspPreferences.DEFAULT_FILE_TYPES", "ACE LSP file-type dialog can restore defaults");
assertIncludes(aceLspFileTypesPreference, "AceEditorLspPreferences.setFileTypes", "ACE LSP file-type dialog saves suffixes");
assertIncludes(aceLspFileTypesPreference, "itemsCallbackMultiChoice", "ACE LSP file-type dialog uses multi-choice selection");
assertIncludes(codeEditorSettingsXml, "AceLspFileTypesPreference", "editor settings XML includes LSP file-type preference");
assertIncludes(codeEditorSettingsXml, "@string/key_ace_lsp_file_types", "editor settings XML uses LSP file-type key");
assertIncludes(materialPreference, "setAvailableForCurrentContext", "material preference supports contextual unavailable visual state");
assertIncludes(materialPreference, "holder.itemView.alpha = if (availableForCurrentContext)", "material preference dims unavailable non-switch rows");
assertIncludes(materialPreference, "handleUnavailableClickIfNeeded", "material preference intercepts unavailable non-switch clicks");
assertIncludes(editorSettingActionRegistry, "ACTION_ID_PREFIX = \"editor_setting:\"", "editor setting actions use stable id prefix");
assertIncludes(editorSettingActionRegistry, "R.string.key_editor_engine", "editor engine setting action is registered");
assertIncludes(editorSettingActionRegistry, "R.string.key_ace_word_wrap_enabled", "word wrap setting action is registered");
assertIncludes(editorSettingActionRegistry, "R.string.key_ace_lsp_file_types", "LSP file-type setting action is registered");
assertIncludes(editorSettingActionRegistry, "R.string.key_editor_text_size", "font-size setting action is registered");
assertIncludes(editorSettingActionRegistry, "R.string.key_editor_shortcut_input_bar", "shortcut input setting action is registered");
assertIncludes(fragmentHostMaterialDialog, "class FragmentHostMaterialDialog", "fragment-hosting MaterialDialog wrapper");
assertIncludes(fragmentHostMaterialDialog, "override fun setContentView(view: View)", "fragment host intercepts DialogFragment content view");
assertIncludes(fragmentHostMaterialDialog, "customView(", "fragment host uses MaterialDialog custom view container");

assertIncludes(diagnostics, "lastFirstPaintAtUptimeMillis", "first-paint diagnostics field");
assertIncludes(diagnostics, "lastAceResizeScheduledReason", "resize scheduled diagnostics field");
assertIncludes(diagnostics, "lastAceResizeDispatchedReason", "resize dispatched diagnostics field");
assertIncludes(diagnostics, "lastAceResizeCompletedReason", "resize completed diagnostics field");
assertIncludes(diagnostics, "maxBridgeEventWindowCount", "bridge event window diagnostics field");
assertIncludes(diagnostics, "lspTransport=${snapshot.lspTransport.orUnknown()}", "LSP transport diagnostics field");
assertIncludes(diagnostics, "lspDocumentUri=${snapshot.lspDocumentUri.orUnknown()}", "LSP document diagnostics field");
assertIncludes(diagnostics, "lspFallback=${snapshot.lspFallback.orUnknown()}", "LSP fallback diagnostics field");

console.log("ACE editor surface smoke checks passed.");
