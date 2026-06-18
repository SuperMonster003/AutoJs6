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
const tooltip = read(TOOLTIP_PATH);
const signatureHelp = read(SIGNATURE_HELP_PATH);
const aceCodeEditor = read(ACE_CODE_EDITOR_PATH);
const aceScriptEditorEngine = read(ACE_SCRIPT_EDITOR_ENGINE_PATH);
const nativeCodeEditorEngine = read(NATIVE_CODE_EDITOR_ENGINE_PATH);
const codeEditText = read(CODE_EDIT_TEXT_PATH);
const editorMenu = read(EDITOR_MENU_PATH);
const editorView = read(EDITOR_VIEW_PATH);
const editorPinchToZoomPreference = read(EDITOR_PINCH_TO_ZOOM_PREFERENCE_PATH);
const codeCompletion = read(CODE_COMPLETION_PATH);
const codeCompletionBar = read(CODE_COMPLETION_BAR_PATH);
const aceShortcutPanel = read(ACE_SHORTCUT_PANEL_PATH);
const aceActionHistoryStore = read(ACE_ACTION_HISTORY_STORE_PATH);
const aceActionFavoritesDialog = read(ACE_ACTION_FAVORITES_DIALOG_PATH);
const codeEditorSettingsDialog = read(CODE_EDITOR_SETTINGS_DIALOG_PATH);
const codeEditorSettingsFragment = read(CODE_EDITOR_SETTINGS_FRAGMENT_PATH);
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

assertMatch(css, /html,\s*body,\s*#editor\s*\{[\s\S]*overflow:\s*hidden/, "ACE root overflow guard");
assertMatch(css, /#editor\s*\{[\s\S]*visibility:\s*hidden/, "hidden editor before first paint");
assertMatch(css, /body\.ace-ready #editor\s*\{[\s\S]*visibility:\s*visible/, "visible editor after first paint");
assertMatch(css, /html\s*\{[\s\S]*background:\s*#f7f8fa/, "light first-paint background");
assertMatch(css, /html\.ace-theme-dark,\s*body\.ace-theme-dark,\s*body\.ace-theme-dark #editor\s*\{[\s\S]*background:\s*#1e1e1e/, "dark first-paint background");
assertMatch(css, /#editor,\s*\.ace_editor\s*\{[\s\S]*JetBrains Mono/, "stable ACE font stack");
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
assertIncludes(bridge, "var ACTION_MODE_NOTIFY_THROTTLE_MS = 0;", "selection action-mode immediate handoff");
assertIncludes(bridge, "var RIGHT_EDGE_GESTURE_GUARD_CSS_PX = 24;", "DOM right-edge gesture guard width");
assertIncludes(bridge, "var RIGHT_EDGE_GESTURE_ACTIVATE_DELTA_X = 14;", "DOM right-edge gesture swipe activation threshold");
assertIncludes(bridge, "function setNativeActionModeActive(active)", "native action-mode state class helper");
assertIncludes(bridge, 'document.body.classList.toggle("autojs6-native-action-mode", active)', "native action-mode host class toggle");
assertIncludes(bridge, "function suppressSelectionActionModeMenu(reason)", "stale selection menu suppression helper");
assertIncludes(bridge, "suppressSelectionActionModeMenu: suppressSelectionActionModeMenu", "native pointer guard selection menu suppression API");
assertIncludes(bridge, "function handleRightEdgeGestureGuardTouch(event)", "DOM right-edge gesture guard");
assertIncludes(bridge, "function maybeActivateRightEdgeGestureGuard(event)", "DOM right-edge gesture deferred activation");
assertIncludes(bridge, "dx <= -RIGHT_EDGE_GESTURE_ACTIVATE_DELTA_X", "DOM right-edge guard requires a left swipe");
assertIncludes(bridge, 'document.addEventListener(type, handleRightEdgeGestureGuardTouch, true)', "DOM right-edge gesture capture listener");
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
assertIncludes(bridge, "function setFontSizeSpKeepingFocus", "ACE focus-preserving zoom helper");
assertIncludes(bridge, "setFontSizeSpKeepingFocus: setFontSizeSpKeepingFocus", "ACE focus-preserving zoom public API");
assertIncludes(bridge, "function insertTab()", "ACE shortcut TAB helper");
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

assertIncludes(completer, "modules: Object.create(null)", "ACE completer module map must avoid prototype inheritance");
assertIncludes(completer, "function membersForModule(source, moduleName)", "ACE completer safe module lookup helper");
assertIncludes(completer, "Object.prototype.hasOwnProperty.call(source.modules, moduleName)", "ACE completer module lookup must ignore inherited prototype keys");

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
assertIncludes(aceCodeEditor, 'invokeAce("setWordWrapIndentStyle", quote(EditorDisplayPreferences.wordWrapIndentStyle()))', "native ACE word wrap indentation runtime bridge call");
assertIncludes(aceCodeEditor, "bridgeWordWrapIndentStyle", "native ACE word wrap indentation bridge getter");
assertIncludes(aceCodeEditor, 'eventHistory.record("lsp_manager"', "ACE LSP manager event history");
assertIncludes(aceCodeEditor, "ScaleGestureDetector", "ACE pinch zoom detector");
assertIncludes(aceCodeEditor, "PinchZoomStrategy", "ACE pinch zoom strategy model");
assertIncludes(aceCodeEditor, "handlePinchToZoomTouch", "ACE pinch zoom touch handler");
assertIncludes(aceCodeEditor, "scaleViewBy", "ACE scale-view zoom handler");
assertIncludes(aceCodeEditor, "setFontSizeSpKeepingFocus", "ACE focus-preserving zoom native call");
assertIncludes(aceCodeEditor, "fun insertTab()", "native ACE shortcut TAB bridge call");
assertIncludes(aceCodeEditor, "fun acceptCompletionOrInsertTab()", "native ACE completion accept bridge call");
assertIncludes(aceCodeEditor, "fun moveCompletionSelectionOrCursor", "native ACE completion navigation bridge call");
assertIncludes(aceCodeEditor, "fun handleEscapeKey()", "native ACE shortcut ESC bridge call");
assertIncludes(aceCodeEditor, "fun onSelectionAction(action: SelectionAction)", "ACE selection action callback");
assertIncludes(aceCodeEditor, "enum class SelectionAction", "ACE selection action model");
assertIncludes(aceCodeEditor, "handleSoftKeyboardCursorKey", "ACE soft-keyboard cursor key handler");
assertIncludes(aceCodeEditor, "handleSoftKeyboardCursorSelection", "ACE soft-keyboard InputConnection cursor selection handler");
assertIncludes(aceCodeEditor, "flushPendingImeCursorSelection", "ACE IME cursor offset coalescing");
assertIncludes(aceCodeEditor, "moveCursor(delta)", "ACE IME cursor relative delta dispatch");
assertIncludes(aceCodeEditor, "InputConnectionWrapper", "ACE WebView InputConnection wrapper");
assertIncludes(aceCodeEditor, "override fun setSelection(start: Int, end: Int)", "ACE IME setSelection cursor interception");
assertIncludes(aceCodeEditor, "not document-absolute offsets", "ACE IME setSelection avoids document-absolute offsets");
assertMatch(aceCodeEditor, /not document-absolute offsets[\s\S]*return true[\s\S]*\} else/, "ACE IME setSelection suppresses WebView absolute collapsed selection");
assertIncludes(aceCodeEditor, '"ime_cursor_selection"', "ACE IME setSelection diagnostics event");
assertIncludes(aceCodeEditor, 'invokeAce("cancelTouchInteraction", quote(reason))', "native pointer guard cancels ACE touch interaction");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_LEFT", "ACE soft-keyboard cursor left key");
assertIncludes(aceCodeEditor, "KeyEvent.KEYCODE_DPAD_RIGHT", "ACE soft-keyboard cursor right key");
assertIncludes(aceCodeEditor, "KeyEvent.FLAG_SOFT_KEYBOARD", "ACE soft-keyboard cursor source guard");
assertIncludes(aceCodeEditor, "startingHostSelectionActionMode", "ACE host-owned action-mode guard");
assertIncludes(aceCodeEditor, "pendingSelectionActionModeRequest", "ACE touch-deferred action mode request");
assertIncludes(aceCodeEditor, "hasRecentTextTouchAnchor", "ACE touch-anchored action-mode rect");
assertIncludes(aceCodeEditor, "isProbablyInvalidActionModeRect", "ACE action-mode invalid rect guard");
assertIncludes(aceCodeEditor, "setFallbackSelectionActionModeRect", "ACE action-mode non-zero fallback rect");
assertIncludes(aceCodeEditor, "invalidateContentRect()", "ACE floating action-mode rect invalidation");
assertIncludes(aceCodeEditor, "override fun startActionMode(callback: ActionMode.Callback", "ACE WebView action-mode suppression");
assertIncludes(aceCodeEditor, '"webview_action_mode_suppressed"', "ACE WebView action-mode suppression diagnostics");
assertIncludes(aceCodeEditor, "suppressSelectionActionModeForPointerGuard", "ACE right-edge gesture selection suppression");
assertIncludes(aceCodeEditor, "val insetOrFallback = if (systemGestureInsetRight > 0)", "ACE right-edge gesture fallback width");
assertIncludes(aceCodeEditor, "RIGHT_EDGE_BACK_GESTURE_MIN_GUARD_DP = 24f", "ACE right-edge gesture narrow fallback");

assertIncludes(aceScriptEditorEngine, "notifyPinchToZoomStrategyChanged", "ACE pinch zoom strategy forwarding");
assertIncludes(aceScriptEditorEngine, "onTextSizeSpChangedByGesture", "ACE pinch text-size state callback");
assertIncludes(nativeCodeEditorEngine, "codeEditor.setWordWrapEnabled(false)", "Native editor keeps word wrap disabled");
assertIncludes(nativeCodeEditorEngine, "gutterWidthMode = EditorDisplayPreferences.GUTTER_WIDTH_MODE_FIXED", "Native editor keeps fixed gutter width");
assertIncludes(codeEditText, "fun setWordWrapEnabled(enabled: Boolean)", "Native word wrap edit text toggle");
assertIncludes(codeEditText, "fun getLogicalLineForVisualLine", "Native wrapped visual-line to logical-line mapping");
assertIncludes(editorPinchToZoomPreference, "EditorEnginePreferences.get() == EditorEngineType.ACE", "ACE-only scale-view preference enablement");
assertIncludes(editorPinchToZoomPreference, "indexOf(key(R.string.key_editor_pinch_to_zoom_scale_view))", "Native scale-view preference remains disabled");

assertIncludes(editorView, "AceShortcutPanel.topRowCompletions", "ACE shortcut top row binding");
assertIncludes(editorView, "AceShortcutPanel.bottomRowCompletions", "ACE shortcut bottom row binding");
assertIncludes(editorView, "SymbolsConfigStore.DefaultSymbolSet.ACE", "ACE shortcut symbol defaults binding");
assertIncludes(editorView, "mAceSymbolsBar.useFlowLayout()", "ACE shortcut symbol flow layout");
assertIncludes(editorView, "mAceShortcutTopBar.setFixedItemSizeDp(40, 35)", "ACE shortcut fixed top key size");
assertIncludes(editorView, "mAceShortcutBottomBar.setFixedItemSizeDp(40, 35)", "ACE shortcut fixed bottom key size");
assertIncludes(editorView, "handleAceShortcutClick", "ACE shortcut click handler");
assertIncludes(editorView, "handleAceShortcutLongClick", "ACE shortcut long-click handler");
assertIncludes(editorView, "executeAceShortcutAction(it, record = false)", "ACE shortcut short clicks stay out of action history");
assertIncludes(editorView, "jumpWithAceShortcutReturnPoint", "ACE shortcut long-press return point");
assertIncludes(editorView, "EditorDisplayPreferences.KEY_WORD_WRAP_INDENT_STYLE", "editor display preference live listener includes word wrap indentation");
assertIncludes(editorView, "editor.notifyDisplayPreferencesChanged()", "editor display preference changes apply to current engine");
assertIncludes(editorView, "toggleAceShortcutReturnPoint", "ACE shortcut TAB return toggle");
assertIncludes(editorView, "showAceActionHistoryDialog", "ACE action history dialog");
assertIncludes(editorView, "repeatLastAceAction", "ACE repeat last action handler");
assertIncludes(editorView, "AceActionFavoritesDialog.show", "ACE action favorites dialog integration");
assertIncludes(editorView, "fun recordAceActionForMenuItem", "ACE action history menu recording entry point");
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
assertIncludes(editorMenu, "mEditorView.recordAceActionForMenuItem(itemId)", "editor overflow menu records ACE actions");
assertIncludes(editorMenu, "mEditorView::showFindOrReplaceDialog", "editor overflow menu shares find/replace action launcher");
assertIncludes(editorMenu, "mEditorView::showJumpToLineDialog", "editor overflow menu shares jump-to-line action launcher");
assertIncludes(editorMenu, "mEditorView::startBuildApkActivity", "editor overflow menu shares build APK launcher");
assertIncludes(editorMenu, "mEditorView::showFileDetailsDialog", "editor overflow menu shares file details launcher");
assertIncludes(editorMenu, "mEditorView::showJavaClassSearchDialog", "editor overflow menu shares Java class search launcher");

assertIncludes(codeCompletion, "val iconResId", "code completion optional icon model");
assertIncludes(codeCompletionBar, "getIconResId(position)", "code completion optional icon lookup");
assertIncludes(codeCompletionBar, "setCompoundDrawables(wrappedIcon, null, null, null)", "completion bar icon rendering");
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
assertIncludes(aceActionHistoryStore, 'entryComparator(root.optString("lastActionId"))', "ACE action favorites latest-action ranking");
assertIncludes(aceActionHistoryStore, 'LEGACY_SHORTCUT_ACTION_PREFIX = "command:"', "ACE action favorites filters legacy shortcut clicks");
assertIncludes(aceActionHistoryStore, 'DEPRECATED_CANCEL_SEARCH_ACTION_ID = "editor:cancel_search"', "ACE action favorites filters accidental cancel-search action");
assertIncludes(aceActionHistoryStore, "fun applyFavoritesOrder", "ACE action favorites order API");
assertIncludes(aceActionHistoryStore, "mutatesText", "ACE action history mutation metadata");
assertIncludes(aceActionHistoryStore, "usageCount", "ACE action history frequency field");
assertIncludes(aceActionHistoryStore, "lastUsedAt", "ACE action history recency field");
assertIncludes(aceActionHistoryStore, "manualOrder", "ACE action history manual order field");

assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT", "ACE action favorites swipe gestures");
assertIncludes(aceActionFavoritesDialog, "onActionSelected: (AceActionHistoryStore.Action) -> Unit", "ACE action favorites generic callback");
assertIncludes(aceActionFavoritesDialog, "ItemTouchHelper.UP or ItemTouchHelper.DOWN", "ACE action favorites drag reorder");
assertIncludes(aceActionFavoritesDialog, "R.drawable.ic_thumbtacks", "ACE action favorites thumbtack icon");
assertIncludes(aceActionFavoritesDialog, 'holder.usageCount.text = "<${entry.usageCount}>"', "ACE action favorites usage count inline");
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
assertIncludes(codeEditorSettingsFragment, "setPreferenceAvailable(R.string.key_ace_word_wrap_enabled, aceSelected", "editor settings disables word wrap for non-Ace engines");
assertIncludes(codeEditorSettingsFragment, "setPreferenceAvailable(R.string.key_editor_gutter_width_mode, aceSelected", "editor settings disables gutter width mode for non-Ace engines");
assertIncludes(codeEditorSettingsFragment, "EditorDisplayPreferences.setWordWrapEnabled(false)", "editor settings normalizes native word wrap value");
assertIncludes(codeEditorSettingsFragment, "EditorDisplayPreferences.setGutterWidthMode(EditorDisplayPreferences.GUTTER_WIDTH_MODE_FIXED)", "editor settings normalizes native gutter width value");
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
