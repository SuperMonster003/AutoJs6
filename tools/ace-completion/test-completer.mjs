#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import {
  GENERATED_INDEX_PATH,
  EDITOR_ASSET_ROOT,
  relativeToRoot,
} from "./indices-utils.mjs";

const COMPLETER_PATH = path.join(EDITOR_ASSET_ROOT, "autojs6_completer.js");
const BRIDGE_PATH = path.join(EDITOR_ASSET_ROOT, "autojs6_ace_bridge.js");
const LANGUAGE_TOOLS_PATH = path.join(EDITOR_ASSET_ROOT, "../src-min-noconflict/ext-language_tools.js");
const FALLBACK_INDEX_PATH = path.join(EDITOR_ASSET_ROOT, "autojs_indices.js");

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

function captionsFor(completer, line, prefix) {
  return complete(completer, line, prefix).map((item) => item.caption || item.value);
}

function completionFor(completer, line, prefix, value) {
  return complete(completer, line, prefix)
    .find((item) => (item.value || item.caption) === value);
}

function createFakeEditor(line) {
  let text = String(line || "");
  let cursor = { row: 0, column: text.length };
  const insertAtCursor = (value) => {
    value = String(value || "");
    text = `${text.slice(0, cursor.column)}${value}${text.slice(cursor.column)}`;
    cursor = { row: 0, column: cursor.column + value.length };
  };
  return {
    session: {
      getLine(row) {
        return row === 0 ? text : "";
      },
      replace(range, value) {
        if (range.start.row !== 0 || range.end.row !== 0) {
          throw new Error(`Unexpected multi-row replace ${JSON.stringify(range)}`);
        }
        const start = range.start.column;
        const end = range.end.column;
        value = String(value || "");
        text = `${text.slice(0, start)}${value}${text.slice(end)}`;
        cursor = { row: 0, column: start + value.length };
      },
    },
    renderer: {
      scrollCursorIntoView() {},
    },
    selection: {
      moveToPosition(pos) {
        cursor = { row: pos.row, column: pos.column };
      },
    },
    getCursorPosition() {
      return { row: cursor.row, column: cursor.column };
    },
    moveCursorTo(row, column) {
      cursor = { row, column };
    },
    execCommand(name, value) {
      if (name !== "insertstring") {
        throw new Error(`Unexpected command ${name}`);
      }
      insertAtCursor(value);
    },
    insert: insertAtCursor,
    getText() {
      return text;
    },
  };
}

function acceptedTextFor(completer, line, prefix, value) {
  return acceptedStateFor(completer, line, prefix, value).text;
}

function acceptedStateFor(completer, line, prefix, value) {
  const completion = completionFor(completer, line, prefix, value);
  if (!completion) {
    throw new Error(`${line}: missing completion ${value}`);
  }
  const editor = createFakeEditor(line);
  const accepted = completer.insertMatch(editor, completion);
  if (accepted === false) {
    throw new Error(`${line}: insertMatch rejected ${value}`);
  }
  return {
    text: editor.getText(),
    cursor: editor.getCursorPosition(),
  };
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

function assertHoverIncludes(completer, line, token, expectedCaption, expectedDoc) {
  const column = line.indexOf(token) + Math.floor(token.length / 2);
  const hover = completer.getHover({
    getLine(row) {
      return row === 0 ? line : "";
    },
  }, { row: 0, column });
  if (!hover) {
    throw new Error(`${line}: missing hover for ${token}`);
  }
  if (hover.caption !== expectedCaption) {
    throw new Error(`${line}: unexpected hover caption ${hover.caption}; expected=${expectedCaption}`);
  }
  if (!hover.docText.includes(expectedDoc)) {
    throw new Error(`${line}: hover ${token} is missing doc text ${expectedDoc}; actual=${hover.docText}`);
  }
}

function sessionFor(line) {
  return {
    getValue() {
      return line;
    },
    getLine(row) {
      return row === 0 ? line : "";
    },
    doc: {
      positionToIndex(pos) {
        return pos.column;
      },
    },
  };
}

function assertSignatureHelp(completer, line, expected) {
  const help = completer.getSignatureHelp(sessionFor(line), { row: 0, column: line.length });
  if (!help) {
    throw new Error(`${line}: missing signature help`);
  }
  for (const [key, value] of Object.entries(expected)) {
    const actualValue = key === "signature" ? help[key].replace(/'/g, '"') : help[key];
    const expectedValue = key === "signature" ? value.replace(/'/g, '"') : value;
    if (actualValue !== expectedValue) {
      throw new Error(`${line}: unexpected ${key}: actual=${help[key]}, expected=${value}`);
    }
  }
  return help;
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

// Mobile and IME behavior is verified by the plugin tools/ace-lsp/verify-ace-bridge.mjs.
const completer = createAutoJs6Completer();
assertIncludes(valuesFor(completer, "toast", "toast"), ["toast"], "toast");
assertIncludes(valuesFor(completer, "app.la", "la"), ["launch", "launchApp"], "app.la");
assertIncludes(valuesFor(completer, "files.re", "re"), ["read", "rename"], "files.re");
assertIncludes(valuesFor(completer, "images.find", "find"), ["findImage", "findColor"], "images.find");
assertIncludes(valuesFor(completer, "device.getB", "getB"), ["getBattery"], "device.getB");
assertIncludes(valuesFor(completer, "console.lo", "lo"), ["log"], "console.lo");
assertIncludes(valuesFor(completer, "threads.st", "st"), ["start"], "threads.st");
assertIncludes(valuesFor(completer, "engines.my", "my"), ["myEngine"], "engines.my");
assertIncludes(valuesFor(completer, "Object.prototype.", ""), ["hasOwnProperty", "toString"], "Object.prototype.");
assertIncludes(valuesFor(completer, "Object.", ""), ["assign", "keys", "hasOwn"], "Object.");
assertIncludes(valuesFor(completer, "app.", ""), ["launch", "autojs", "versionCode"], "app.");
assertIncludes(captionsFor(completer, "app.", ""), ["launch"], "app. caption");
assertIncludes(valuesFor(completer, "obj.has", "has"), ["hasOwnProperty"], "generic object prototype fallback");
assertIncludes(valuesFor(completer, "Array.prototype.ma", "ma"), ["map"], "Array.prototype.ma");
assertIncludes(valuesFor(completer, '"abc".', ""), ["trim", "toUpperCase"], 'literal "abc".');
assertIncludes(captionsFor(completer, '"abc".', ""), ["trim"], 'literal "abc". caption');
assertIncludes(valuesFor(completer, "[].", ""), ["map", "push"], "literal [].");
assertIncludes(valuesFor(completer, "(1).", ""), ["toFixed", "toString"], "literal (1).");
assertIncludes(valuesFor(completer, "({}).", ""), ["hasOwnProperty", "toString"], "literal ({}).");
assertIncludes(valuesFor(completer, "JSON.str", "str"), ["stringify"], "JSON.str");
assertIncludes(valuesFor(completer, "app.autojs.", ""), ["version", "setRootMode", "getRootMode"], "app.autojs.");
assertIncludes(valuesFor(completer, "RootMod", "RootMod"), ["RootMode"], "RootMod");
assertIncludes(captionsFor(completer, "app.laun", "laun"), ["launch"], "app.laun caption");
assertIncludes(
  captionsFor(completer, "Object.prototype.", ""),
  ["hasOwnProperty"],
  "Object.prototype. caption",
);
const launchCompletion = completionFor(completer, "app.laun", "laun", "launch");
if (!launchCompletion || launchCompletion.snippet !== "launch(${1})") {
  throw new Error(`app.laun should insert a call snippet; actual=${JSON.stringify(launchCompletion)}`);
}
const hasOwnPropertyCompletion = completionFor(completer, "Object.prototype.", "", "hasOwnProperty");
if (!hasOwnPropertyCompletion || hasOwnPropertyCompletion.caption !== "hasOwnProperty") {
  throw new Error(
    `Object.prototype. should display a short member caption and insert only the member; ` +
      `actual=${JSON.stringify(hasOwnPropertyCompletion)}`,
  );
}
const objectToStringCompletion = completionFor(completer, "Object.prototype.", "", "toString");
if (!objectToStringCompletion || objectToStringCompletion.snippet !== "toString()") {
  throw new Error(
    `Object.prototype.toString should insert a no-arg call snippet; actual=${JSON.stringify(objectToStringCompletion)}`,
  );
}
if (acceptedTextFor(completer, "app.auto", "auto", "autojs") !== "app.autojs") {
  throw new Error("app.autojs member accept should preserve the receiver");
}
if (acceptedTextFor(completer, "device.base", "base", "baseOS") !== "device.baseOS") {
  throw new Error("device.baseOS member accept should preserve the receiver");
}
if (acceptedTextFor(completer, "Object.prototype.", "", "hasOwnProperty") !== "Object.prototype.hasOwnProperty()") {
  throw new Error("Object.prototype member accept should preserve the full receiver chain");
}
const objectToStringAccepted = acceptedStateFor(completer, "Object.prototype.", "", "toString");
if (objectToStringAccepted.text !== "Object.prototype.toString()") {
  throw new Error("Object.prototype.toString accept should preserve the full receiver chain");
}
if (objectToStringAccepted.cursor.column !== "Object.prototype.toString()".length) {
  throw new Error("No-arg method accept should leave the cursor after the closing paren");
}
if (acceptedTextFor(completer, "app.laun", "laun", "launch") !== "app.launch()") {
  throw new Error("function member accept should insert a call snippet after the receiver");
}
assertDocIncludes(
  completer,
  "app.la",
  "la",
  "launch",
  "launch(app: App.Preset | App.Alias | App.PackageName): boolean;",
);
assertDocIncludes(
  completer,
  "launch",
  "launch",
  "launch",
  "launch(app: App.Preset | App.Alias | App.PackageName): boolean;",
);
assertDocIncludes(
  completer,
  "Object.prototype.has",
  "has",
  "hasOwnProperty",
  "Object.prototype.hasOwnProperty(propertyKey: PropertyKey): boolean;",
);
assertHoverIncludes(
  completer,
  'app.launch("org.autojs.autojs6");',
  "launch",
  "app.launch",
  "launch(app: App.Preset | App.Alias | App.PackageName): boolean;",
);
assertHoverIncludes(
  completer,
  "Object.prototype.hasOwnProperty.call(obj, key);",
  "hasOwnProperty",
  "Object.prototype.hasOwnProperty",
  "Object.prototype.hasOwnProperty(propertyKey: PropertyKey): boolean;",
);
assertSignatureHelp(completer, "app.launch(", {
  caption: "app.launch",
  signature: "launch(app: App.Preset | App.Alias | App.PackageName): boolean",
  activeParameter: 0,
});
assertSignatureHelp(completer, "app.autojs.setRootMode('root', ", {
  caption: "app.autojs.setRootMode",
  signature: "setRootMode(mode: -1 | 0 | 1 | boolean | 'auto' | 'root' | 'non-root', isWriteIntoPreference?: boolean | 'write_into_pref'): void",
  activeParameter: 1,
});
// Declaration-backed overloads reach the runtime index and signature help picks by argument count.
const overloadHelp = assertSignatureHelp(completer, "app.startActivity(", {
  caption: "app.startActivity",
  activeParameter: 0,
  activeSignature: 0,
});
if (!Array.isArray(overloadHelp.signatures) || overloadHelp.signatures.length !== 2) {
  throw new Error(`app.startActivity should expose two overloads, got ${JSON.stringify(overloadHelp.signatures)}`);
}
if (overloadHelp.signatures[0].signature !== overloadHelp.signature) {
  throw new Error("The top-level signature should mirror the active overload");
}
assertSignatureHelp(completer, "app.startActivity(intent, ", {
  caption: "app.startActivity",
  activeParameter: 1,
  activeSignature: 1,
});

const fallbackCompleter = createFallbackCompleter();
assertIncludes(valuesFor(completer, "ai.agent.", ""), ["run", "create", "get", "list", "catalog", "presets", "status", "result", "context"], "Agent module");
assertIncludes(valuesFor(completer, "AgentRun.", ""), ["id", "state", "on", "once", "off", "respond", "confirm", "cancel", "join", "result"], "Agent handle members");
for (const name of ["Agent", "AgentRun", "AgentAssistant"]) {
  if (completer.getGlobalNames().includes(name)) throw new Error(`Instance type leaked as a global: ${name}`);
}
assertIncludes(valuesFor(fallbackCompleter, "app.la", "la"), ["launch", "launchApp"], "fallback app.la");

const missingIndicesCompleter = createMissingIndicesCompleter();
assertNoThrow("missing indices should not throw", () => {
  valuesFor(missingIndicesCompleter, "toast", "toast");
  valuesFor(missingIndicesCompleter, "app.la", "la");
});
assertIncludes(
  valuesFor(missingIndicesCompleter, "Object.prototype.has", "has"),
  ["hasOwnProperty"],
  "missing indices ECMAScript builtins",
);
assertNoThrow("prototype-like member contexts should not throw", () => {
  valuesFor(completer, "xx.__proto__.", "");
  valuesFor(completer, "xx.constructor.", "");
  completer.getHover({
    getLine(row) {
      return row === 0 ? "xx.__proto__.value" : "";
    },
  }, { row: 0, column: "xx.__proto__.value".length });
});

assertLanguageToolsAcceptShim();


console.log(`AutoJs6 ACE completer tests passed (${relativeToRoot(COMPLETER_PATH)}).`);
