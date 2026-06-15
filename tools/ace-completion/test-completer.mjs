#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import {
  GENERATED_INDEX_PATH,
  PROJECT_ROOT,
  relativeToRoot,
} from "./indices-utils.mjs";

const COMPLETER_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6_completer.js",
);
const BRIDGE_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6_ace_bridge.js",
);
const CSS_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6.css",
);
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
const LANGUAGE_TOOLS_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "src-min-noconflict",
  "ext-language_tools.js",
);
const FALLBACK_INDEX_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs_indices.js",
);

function createSandbox() {
  const sandbox = {
    window: {},
    console,
  };
  sandbox.window.window = sandbox.window;
  vm.createContext(sandbox);
  return sandbox;
}

function runScript(sandbox, filePath) {
  vm.runInContext(fs.readFileSync(filePath, "utf8"), sandbox, { filename: filePath });
}

function installCompleter(sandbox) {
  const languageTools = {
    completers: [],
    addCompleter(completer) {
      this.completers.push(completer);
    },
  };
  const ace = {
    require(id) {
      if (id === "ace/ext/language_tools") {
        return languageTools;
      }
      throw new Error(`Unexpected ace.require(${id})`);
    },
  };
  sandbox.window.AutoJsAceCompleter.install(ace);
  if (languageTools.completers.length !== 1) {
    throw new Error("Expected exactly one AutoJs6 completer");
  }
  return languageTools.completers[0];
}

function complete(completer, line, prefix) {
  const session = {
    getLine(row) {
      return row === 0 ? line : "";
    },
  };
  let completions = null;
  let thrown = null;
  completer.getCompletions(null, session, { row: 0, column: line.length }, prefix, (error, result) => {
    thrown = error;
    completions = result || [];
  });
  if (thrown) {
    throw thrown;
  }
  return completions;
}

function valuesFor(completer, line, prefix) {
  return complete(completer, line, prefix).map((item) => item.value || item.caption);
}

function completionFor(completer, line, prefix, value) {
  return complete(completer, line, prefix)
    .find((item) => (item.value || item.caption) === value);
}

function assertIncludes(actual, expected, label) {
  for (const value of expected) {
    if (!actual.includes(value)) {
      throw new Error(`${label}: missing ${value}; actual=${actual.join(", ")}`);
    }
  }
}

function assertNoThrow(label, block) {
  try {
    block();
  } catch (error) {
    throw new Error(`${label}: ${error.stack || error}`);
  }
}

function assertDocIncludes(completer, line, prefix, value, expected) {
  const completion = completionFor(completer, line, prefix, value);
  if (!completion) {
    throw new Error(`${line}: missing completion ${value}`);
  }
  const tooltip = completer.getDocTooltip(completion);
  const docText = tooltip && tooltip.docText || completion.docText || "";
  if (!docText.includes(expected)) {
    throw new Error(`${line}: completion ${value} is missing doc text ${expected}; actual=${docText}`);
  }
}

function createAutoJs6Completer() {
  const sandbox = createSandbox();
  runScript(sandbox, GENERATED_INDEX_PATH);
  runScript(sandbox, COMPLETER_PATH);
  return installCompleter(sandbox);
}

function createFallbackCompleter() {
  const sandbox = createSandbox();
  runScript(sandbox, FALLBACK_INDEX_PATH);
  runScript(sandbox, COMPLETER_PATH);
  return installCompleter(sandbox);
}

function createMissingIndicesCompleter() {
  const sandbox = createSandbox();
  runScript(sandbox, COMPLETER_PATH);
  return installCompleter(sandbox);
}

function assertLanguageToolsAcceptShim() {
  const languageTools = fs.readFileSync(LANGUAGE_TOOLS_PATH, "utf8");
  if (!languageTools.includes("editor.renderer.scrollCursorIntoView()")) {
    return;
  }
  const bridge = fs.readFileSync(BRIDGE_PATH, "utf8");
  if (!bridge.includes("global.editor = editor;")) {
    throw new Error("ACE language tools completion accept shim is missing");
  }
}

function assertMobileBridgeStabilityHooks() {
  const bridge = fs.readFileSync(BRIDGE_PATH, "utf8");
  for (const expected of ["scheduleResize", "recoverableError", "firstPaint", "requestFirstPaint"]) {
    if (!bridge.includes(expected)) {
      throw new Error(`ACE mobile stability hook is missing from bridge: ${expected}`);
    }
  }

  const css = fs.readFileSync(CSS_PATH, "utf8");
  for (const expected of ["visibility: hidden", "body.ace-ready #editor"]) {
    if (!css.includes(expected)) {
      throw new Error(`ACE first-paint CSS guard is missing: ${expected}`);
    }
  }

  const nativeBridge = fs.readFileSync(ACE_BRIDGE_KT_PATH, "utf8");
  for (const expected of ["fun startActionMode", "fun finishActionMode", "fun performLongPressFeedback"]) {
    if (!nativeBridge.includes(expected)) {
      throw new Error(`ACE native mobile bridge method is missing: ${expected}`);
    }
  }
}

const completer = createAutoJs6Completer();
assertIncludes(valuesFor(completer, "toast", "toast"), ["toast"], "toast");
assertIncludes(valuesFor(completer, "app.la", "la"), ["launch", "launchApp"], "app.la");
assertIncludes(valuesFor(completer, "files.re", "re"), ["read", "rename"], "files.re");
assertIncludes(valuesFor(completer, "images.find", "find"), ["findImage", "findColor"], "images.find");
assertIncludes(valuesFor(completer, "device.getB", "getB"), ["getBattery"], "device.getB");
assertIncludes(valuesFor(completer, "console.lo", "lo"), ["log"], "console.lo");
assertIncludes(valuesFor(completer, "threads.st", "st"), ["start"], "threads.st");
assertIncludes(valuesFor(completer, "engines.my", "my"), ["myEngine"], "engines.my");
assertDocIncludes(
  completer,
  "app.la",
  "la",
  "launch",
  "launch(app: App | App.Alias | App.PackageName): boolean;",
);
assertDocIncludes(
  completer,
  "launch",
  "launch",
  "launch",
  "launch(app: App | App.Alias | App.PackageName): boolean;",
);

const fallbackCompleter = createFallbackCompleter();
assertIncludes(valuesFor(fallbackCompleter, "app.la", "la"), ["launch", "launchApp"], "fallback app.la");

const missingIndicesCompleter = createMissingIndicesCompleter();
assertNoThrow("missing indices should not throw", () => {
  valuesFor(missingIndicesCompleter, "toast", "toast");
  valuesFor(missingIndicesCompleter, "app.la", "la");
});

assertLanguageToolsAcceptShim();
assertMobileBridgeStabilityHooks();

console.log(`AutoJs6 ACE completer tests passed (${relativeToRoot(COMPLETER_PATH)}).`);
