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
const LSP_CLIENT_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_lsp_client.js");
const TOOLTIP_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_tooltip.js");
const SIGNATURE_HELP_PATH = path.join(ACE_ROOT, "autojs6", "autojs6_signature_help.js");
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
const lspClient = read(LSP_CLIENT_PATH);
const tooltip = read(TOOLTIP_PATH);
const signatureHelp = read(SIGNATURE_HELP_PATH);
const aceCodeEditor = read(ACE_CODE_EDITOR_PATH);
const aceScriptEditorEngine = read(ACE_SCRIPT_EDITOR_ENGINE_PATH);
const editorMenu = read(EDITOR_MENU_PATH);
const editorView = read(EDITOR_VIEW_PATH);
const codeCompletion = read(CODE_COMPLETION_PATH);
const codeCompletionBar = read(CODE_COMPLETION_BAR_PATH);
const aceShortcutPanel = read(ACE_SHORTCUT_PANEL_PATH);
const aceActionHistoryStore = read(ACE_ACTION_HISTORY_STORE_PATH);
const aceActionFavoritesDialog = read(ACE_ACTION_FAVORITES_DIALOG_PATH);
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

assertMatch(css, /html,\s*body,\s*#editor\s*\{[\s\S]*overflow:\s*hidden/, "ACE root overflow guard");
assertMatch(css, /#editor\s*\{[\s\S]*visibility:\s*hidden/, "hidden editor before first paint");
assertMatch(css, /body\.ace-ready #editor\s*\{[\s\S]*visibility:\s*visible/, "visible editor after first paint");
assertMatch(css, /html\s*\{[\s\S]*background:\s*#f7f8fa/, "light first-paint background");
assertMatch(css, /html\.ace-theme-dark,\s*body\.ace-theme-dark,\s*body\.ace-theme-dark #editor\s*\{[\s\S]*background:\s*#1e1e1e/, "dark first-paint background");
assertMatch(css, /#editor,\s*\.ace_editor\s*\{[\s\S]*JetBrains Mono/, "stable ACE font stack");
assertMatch(css, /#editor,\s*\.ace_editor\s*\{[\s\S]*-webkit-user-select:\s*text/, "ACE text selection enabled");
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*radial-gradient/, "breakpoint gutter marker");
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*7px center/, "breakpoint gutter marker anchor");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*position:\s*absolute/, "debug line marker positioning");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*z-index:\s*5/, "debug line marker z-index");
assertMatch(css, /\.ace_gutter,\s*\.ace_gutter-cell\s*\{[\s\S]*user-select:\s*none/, "gutter selection suppression");
assertMatch(css, /\.ace_mobile-menu\s*\{[\s\S]*z-index:\s*20/, "mobile menu remains available");
assertMatch(css, /body\.autojs6-native-action-mode \.ace_mobile-menu\s*\{[\s\S]*display:\s*none !important/, "native action-mode suppresses ACE mobile menu");
assertMatch(css, /body\.autojs6-native-action-mode \.ace_mobile-menu\s*\{[\s\S]*pointer-events:\s*none !important/, "suppressed ACE mobile menu cannot steal touch input");
assertMatch(css, /body\.autojs6-selection-menu-suppressed \.ace_mobile-menu\s*\{[\s\S]*display:\s*none !important/, "stale selection-menu suppression hides ACE mobile menu");
assertMatch(css, /body\.autojs6-selection-menu-suppressed \.ace_mobile-menu\s*\{[\s\S]*pointer-events:\s*none !important/, "stale selection-menu suppression blocks ACE mobile menu touch input");
assertMatch(css, /body\.ace-theme-dark \.ace_marker-layer \.ace_selection\s*\{[\s\S]*rgba\(88,\s*166,\s*255,\s*0\.42\)/, "dark selection contrast");
assertMatch(css, /\.autojs6_hover_tooltip\s*\{[\s\S]*pointer-events:\s*none/, "ACE hover tooltip must not steal touch input");
assertMatch(css, /body\.ace-theme-dark \.autojs6_hover_tooltip\s*\{[\s\S]*background:\s*#20242b/, "ACE hover tooltip dark style");
assertMatch(css, /\.autojs6_signature_tooltip\s*\{[\s\S]*pointer-events:\s*none/, "ACE signature tooltip must not steal touch input");
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
assertIncludes(bridge, "function setNativeActionModeActive(active)", "native action-mode state class helper");
assertIncludes(bridge, 'document.body.classList.toggle("autojs6-native-action-mode", active)', "native action-mode host class toggle");
assertIncludes(bridge, "function suppressSelectionActionModeMenu(reason)", "stale selection menu suppression helper");
assertIncludes(bridge, "function wrapScrollMethod(owner, methodName)", "scroll method hook wrapper");
assertIncludes(bridge, 'wrapScrollMethod(editor.renderer, "scrollToY")', "renderer vertical scroll hook");
assertIncludes(bridge, 'wrapScrollMethod(session, "setScrollTop")', "session vertical scroll hook");
assertIncludes(bridge, 'suppressSelectionActionModeMenu("browse_scroll")', "browse scroll suppresses stale selection menus");
assertIncludes(bridge, 'document.addEventListener("visibilitychange", handleDocumentVisibilityChanged)', "visibility changes suppress stale selection menus");
assertIncludes(bridge, "function getActionModeFallbackRect()", "non-zero action-mode fallback anchor");
assertMatch(bridge, /function afterTwoFrames\(callback\)[\s\S]*raf\(function\(\)[\s\S]*raf\(callback\);[\s\S]*\}\);[\s\S]*\}/, "double requestAnimationFrame first-paint guard");
assertMatch(bridge, /function requestFirstPaint\(reason\)[\s\S]*scheduleResize\(reason \|\| "first_paint"\);[\s\S]*afterTwoFrames\(notifyFirstPaint\);[\s\S]*\}/, "first-paint resize before reveal");
assertIncludes(bridge, "requestFirstPaint: requestFirstPaint", "first-paint public API");
assertIncludes(bridge, "resizeFallbackTimer = setTimeout(function()", "resize fallback timer");
assertIncludes(bridge, 'JSON.stringify({ reason: String(reason || "") })', "resizeDone reason payload");
assertIncludes(bridge, 'editor.on("guttermousedown", function(event)', "gutter mouse handler");
assertIncludes(bridge, 'target.className.indexOf("ace_gutter-cell")', "gutter-cell guard");
assertIncludes(bridge, "getBoundingClientRect().left + 24", "gutter breakpoint tap zone");
assertIncludes(bridge, "event.stop();", "gutter click event stop");
assertIncludes(bridge, 'callBridgeBoolean("isWordWrapEnabled", false)', "ACE word wrap preference bridge");
assertIncludes(bridge, 'callBridgeBoolean("isLineNumbersEnabled", true)', "ACE line number preference bridge");
assertIncludes(bridge, "fixedWidthGutter: true", "fixed-width ACE gutter");
assertIncludes(bridge, "function expandFoldAt(row, column)", "fold placeholder expansion helper");
assertIncludes(bridge, 'editor.on("click", function(event)', "fold placeholder click handler");
assertIncludes(bridge, "setWordWrapEnabled: setWordWrapEnabled", "ACE word wrap runtime API");
assertIncludes(bridge, "setLineNumbersEnabled: setLineNumbersEnabled", "ACE line number runtime API");
assertIncludes(bridge, "function setFontSizeSpKeepingFocus", "ACE focus-preserving zoom helper");
assertIncludes(bridge, "setFontSizeSpKeepingFocus: setFontSizeSpKeepingFocus", "ACE focus-preserving zoom public API");
assertIncludes(bridge, "function insertTab()", "ACE shortcut TAB helper");
assertIncludes(bridge, "function handleEscapeKey()", "ACE shortcut ESC helper");
assertIncludes(bridge, "insertTab: insertTab", "ACE shortcut TAB public API");
assertIncludes(bridge, "handleEscapeKey: handleEscapeKey", "ACE shortcut ESC public API");
assertIncludes(bridge, "var lspClient = null;", "ACE LSP client bridge slot");
assertIncludes(bridge, "global.AutoJsAceLspClient.install", "ACE LSP client install");
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
assertIncludes(lspClient, "completionProvider: serverReady ? \"server\" : COMPLETION_PROVIDER_STATIC", "ACE LSP completion fallback provider");
assertIncludes(lspClient, "hoverProvider: serverReady ? \"server\" : HOVER_PROVIDER_STATIC", "ACE LSP hover fallback provider");
assertIncludes(lspClient, "serverAvailable === true", "ACE LSP server availability gate");
assertIncludes(lspClient, "typeof options.serverUri === \"string\"", "ACE LSP server URI gate");
assertIncludes(lspClient, "getCompletions: getCompletions", "ACE LSP completion hook");
assertIncludes(lspClient, "getHover: getHover", "ACE LSP hover hook");
if (lspClient.includes("WebSocket") || lspClient.includes("new Worker")) {
  throw new Error("LSP client must stay off WebSocket and worker transports in alpha path");
}

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
assertIncludes(aceCodeEditor, 'eventHistory.record("lsp_manager"', "ACE LSP manager event history");
assertIncludes(aceCodeEditor, "ScaleGestureDetector", "ACE pinch zoom detector");
assertIncludes(aceCodeEditor, "PinchZoomStrategy", "ACE pinch zoom strategy model");
assertIncludes(aceCodeEditor, "handlePinchToZoomTouch", "ACE pinch zoom touch handler");
assertIncludes(aceCodeEditor, "scaleViewBy", "ACE scale-view zoom handler");
assertIncludes(aceCodeEditor, "setFontSizeSpKeepingFocus", "ACE focus-preserving zoom native call");
assertIncludes(aceCodeEditor, "fun insertTab()", "native ACE shortcut TAB bridge call");
assertIncludes(aceCodeEditor, "fun handleEscapeKey()", "native ACE shortcut ESC bridge call");
assertIncludes(aceCodeEditor, "handleSoftKeyboardCursorKey", "ACE soft-keyboard cursor key handler");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_LEFT", "ACE soft-keyboard cursor left key");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_RIGHT", "ACE soft-keyboard cursor right key");
assertIncludes(aceCodeEditor, "KeyEvent.FLAG_SOFT_KEYBOARD", "ACE soft-keyboard cursor source guard");

assertIncludes(aceScriptEditorEngine, "notifyPinchToZoomStrategyChanged", "ACE pinch zoom strategy forwarding");
assertIncludes(aceScriptEditorEngine, "onTextSizeSpChangedByGesture", "ACE pinch text-size state callback");
assertIncludes(editorMenu, "currentEditor().getEngineType() == EditorEngineType.ACE", "ACE-only scale-view menu enablement");
assertIncludes(editorMenu, "makeEditorPinchToZoomScaleViewUnderDev", "Native scale-view menu remains disabled");

assertIncludes(editorView, "AceShortcutPanel.firstRowCompletions", "ACE shortcut first row binding");
assertIncludes(editorView, "AceShortcutPanel.secondRowCompletions", "ACE shortcut second row binding");
assertIncludes(editorView, "handleAceShortcutClick", "ACE shortcut click handler");
assertIncludes(editorView, "handleAceShortcutLongClick", "ACE shortcut long-click handler");
assertIncludes(editorView, "showAceActionHistoryDialog", "ACE action history dialog");
assertIncludes(editorView, "repeatLastAceAction", "ACE repeat last action handler");
assertIncludes(editorView, "AceActionFavoritesDialog.show", "ACE action favorites dialog integration");

assertIncludes(codeCompletion, "val iconResId", "code completion optional icon model");
assertIncludes(codeCompletionBar, "getIconResId(position)", "code completion optional icon lookup");
assertIncludes(codeCompletionBar, "setCompoundDrawables(wrappedIcon, null, null, null)", "completion bar icon rendering");
assertIncludes(codeCompletionBar, "DrawableCompat.setTint", "completion bar themed icon tint");

assertIncludes(aceShortcutPanel, 'command("ESC", Command.Escape)', "ACE shortcut ESC key");
assertIncludes(aceShortcutPanel, 'command("TAB", Command.Tab)', "ACE shortcut TAB key");
assertIncludes(aceShortcutPanel, 'command("*", Command.ActionFavorites, R.drawable.ic_star_black_48dp)', "ACE shortcut favorites star entry");
assertIncludes(aceShortcutPanel, 'insert("`")', "ACE shortcut backtick key");
assertIncludes(aceShortcutPanel, "data class Action", "ACE shortcut action model");
assertIncludes(aceShortcutPanel, "fun actionById", "ACE shortcut stable action lookup");

assertIncludes(aceActionHistoryStore, "fun record(", "ACE action history record API");
assertIncludes(aceActionHistoryStore, "fun getRankedEntries", "ACE action history ranking API");
assertIncludes(aceActionHistoryStore, "fun getLastActionId", "ACE action history last action API");
assertIncludes(aceActionHistoryStore, "fun applyFavoritesOrder", "ACE action favorites order API");
assertIncludes(aceActionHistoryStore, "usageCount", "ACE action history frequency field");
assertIncludes(aceActionHistoryStore, "lastUsedAt", "ACE action history recency field");
assertIncludes(aceActionHistoryStore, "manualOrder", "ACE action history manual order field");

assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT", "ACE action favorites swipe gestures");
assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.UP or ItemTouchHelper.DOWN", "ACE action favorites drag reorder");
assertIncludes(aceActionFavoritesDialog, "confirmDelete", "ACE action favorites delete confirmation");
assertIncludes(aceActionFavoritesDialog, "confirmClear", "ACE action favorites clear confirmation");
assertIncludes(aceActionFavoritesDialog, "togglePinned", "ACE action favorites pin toggle");
assertIncludes(aceActionFavoritesDialog, "persistOrder", "ACE action favorites order persistence");

assertIncludes(diagnostics, "lastFirstPaintAtUptimeMillis", "first-paint diagnostics field");
assertIncludes(diagnostics, "lastAceResizeScheduledReason", "resize scheduled diagnostics field");
assertIncludes(diagnostics, "lastAceResizeDispatchedReason", "resize dispatched diagnostics field");
assertIncludes(diagnostics, "lastAceResizeCompletedReason", "resize completed diagnostics field");
assertIncludes(diagnostics, "maxBridgeEventWindowCount", "bridge event window diagnostics field");
assertIncludes(diagnostics, "lspTransport=${snapshot.lspTransport.orUnknown()}", "LSP transport diagnostics field");
assertIncludes(diagnostics, "lspDocumentUri=${snapshot.lspDocumentUri.orUnknown()}", "LSP document diagnostics field");
assertIncludes(diagnostics, "lspFallback=${snapshot.lspFallback.orUnknown()}", "LSP fallback diagnostics field");

console.log("ACE editor surface smoke checks passed.");
