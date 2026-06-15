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
const aceCodeEditor = read(ACE_CODE_EDITOR_PATH);
const diagnostics = read(DIAGNOSTICS_PATH);

assertOrder(
  html,
  [
    '<link rel="stylesheet" href="./autojs6/autojs6.css">',
    '<div id="editor"></div>',
    '<script src="./src-min-noconflict/ace.js"></script>',
    '<script src="./autojs6/autojs6_completer.js"></script>',
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
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*radial-gradient/, "breakpoint gutter marker");
assertMatch(css, /\.ace_gutter-cell\.ace_breakpoint\s*\{[\s\S]*7px center/, "breakpoint gutter marker anchor");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*position:\s*absolute/, "debug line marker positioning");
assertMatch(css, /\.autojs6_debug_line\s*\{[\s\S]*z-index:\s*5/, "debug line marker z-index");

assertIncludes(bridge, "var firstPaintNotified = false;", "first-paint idempotency flag");
assertIncludes(bridge, 'document.body.classList.add("ace-ready")', "ACE ready class add");
assertIncludes(bridge, 'callBridge("notifyEvent", ["firstPaint", lightweightStateJson()])', "first-paint bridge event");
assertMatch(bridge, /function afterTwoFrames\(callback\)[\s\S]*raf\(function\(\)[\s\S]*raf\(callback\);[\s\S]*\}\);[\s\S]*\}/, "double requestAnimationFrame first-paint guard");
assertMatch(bridge, /function requestFirstPaint\(reason\)[\s\S]*scheduleResize\(reason \|\| "first_paint"\);[\s\S]*afterTwoFrames\(notifyFirstPaint\);[\s\S]*\}/, "first-paint resize before reveal");
assertIncludes(bridge, "requestFirstPaint: requestFirstPaint", "first-paint public API");
assertIncludes(bridge, "resizeFallbackTimer = setTimeout(function()", "resize fallback timer");
assertIncludes(bridge, 'JSON.stringify({ reason: String(reason || "") })', "resizeDone reason payload");
assertIncludes(bridge, 'editor.on("guttermousedown", function(event)', "gutter mouse handler");
assertIncludes(bridge, 'target.className.indexOf("ace_gutter-cell")', "gutter-cell guard");
assertIncludes(bridge, "getBoundingClientRect().left + 24", "gutter breakpoint tap zone");
assertIncludes(bridge, "event.stop();", "gutter click event stop");

assertIncludes(aceCodeEditor, "private fun requestFirstPaintWhenStable()", "native first-paint request hook");
assertIncludes(aceCodeEditor, 'invokeAce("requestFirstPaint", quote("native_ready"))', "native first-paint bridge call");
assertIncludes(aceCodeEditor, "private fun handleFirstPaint()", "native first-paint handler");
assertIncludes(aceCodeEditor, "hideLoadingOverlay()", "first-paint overlay hide");
assertIncludes(aceCodeEditor, '"resizeDone" -> handleResizeDone(payloadJson)', "native resizeDone handler");
assertIncludes(aceCodeEditor, "lastAceResizeCompletedReason = resizeReasonFromPayload", "native resize completion reason");

assertIncludes(diagnostics, "lastFirstPaintAtUptimeMillis", "first-paint diagnostics field");
assertIncludes(diagnostics, "lastAceResizeScheduledReason", "resize scheduled diagnostics field");
assertIncludes(diagnostics, "lastAceResizeDispatchedReason", "resize dispatched diagnostics field");
assertIncludes(diagnostics, "lastAceResizeCompletedReason", "resize completed diagnostics field");

console.log("ACE editor surface smoke checks passed.");
