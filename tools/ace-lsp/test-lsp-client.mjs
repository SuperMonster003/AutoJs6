#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const ACE_AUTOJS6_ROOT = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
);
const GENERATED_INDEX_PATH = path.join(ACE_AUTOJS6_ROOT, "autojs6_indices.js");
const COMPLETER_PATH = path.join(ACE_AUTOJS6_ROOT, "autojs6_completer.js");
const LSP_CLIENT_PATH = path.join(ACE_AUTOJS6_ROOT, "autojs6_lsp_client.js");

const NO_SERVER_OPTIONS = JSON.stringify({
  enabled: true,
  manager: "native-alpha",
  state: "configured-no-server",
  transport: "unavailable",
  serverUri: null,
  rootUri: "file:///autojs6/editor",
  documentUri: "file:///autojs6/editor/current.js",
  libraryUris: ["autojs6/types/lib.autojs6.d.ts"],
  fallback: "static-completion",
  startSupported: false,
  serverAvailable: false,
  reason: "no-alpha-server-endpoint",
});

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

function installStaticCompleter(sandbox) {
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
  const completer = sandbox.window.AutoJsAceCompleter.install(ace);
  if (languageTools.completers.length !== 1 || completer !== languageTools.completers[0]) {
    throw new Error("Expected one reusable static completer");
  }
  return completer;
}

function sessionFor(lines) {
  return {
    getLine(row) {
      return lines[row] || "";
    },
  };
}

function createClient(optionsJson, lines = [""]) {
  const sandbox = createSandbox();
  const errors = [];
  runScript(sandbox, GENERATED_INDEX_PATH);
  runScript(sandbox, COMPLETER_PATH);
  runScript(sandbox, LSP_CLIENT_PATH);
  const staticCompleter = installStaticCompleter(sandbox);
  const session = sessionFor(lines);
  const client = sandbox.window.AutoJsAceLspClient.install({
    editor: {},
    session,
    getOptions() {
      return optionsJson;
    },
    getStaticCompleter() {
      return staticCompleter;
    },
    notifyError(message, error) {
      errors.push({ message, error });
    },
  });
  return { client, errors };
}

function complete(client, line, prefix) {
  const session = sessionFor([line]);
  let completions = null;
  let thrown = null;
  client.getCompletions({}, session, { row: 0, column: line.length }, prefix, (error, result) => {
    thrown = error;
    completions = result || [];
  });
  if (thrown) {
    throw thrown;
  }
  return completions;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertIncludes(values, expected, label) {
  if (!values.includes(expected)) {
    throw new Error(`${label}: missing ${expected}; actual=${values.join(", ")}`);
  }
}

function valuesFor(completions) {
  return completions.map((item) => item.value || item.caption);
}

const disabled = createClient("{}", ["console.lo"]);
const disabledState = disabled.client.getState();
assert(disabledState.enabled === false, "Disabled LSP state should not be enabled");
assert(disabledState.state === "disabled", "Disabled LSP state should be disabled");
assert(disabledState.completionProvider === "static-fallback", "Disabled LSP should keep static completion fallback");
assert(disabledState.hoverProvider === "static-fallback", "Disabled LSP should keep static hover fallback");
assertIncludes(valuesFor(complete(disabled.client, "console.lo", "lo")), "log", "disabled console.lo");

const noServer = createClient(NO_SERVER_OPTIONS, ['app.launch("org.autojs.autojs6");']);
const noServerState = noServer.client.getState();
assert(noServerState.enabled === true, "No-server LSP state should be enabled");
assert(noServerState.manager === "native-alpha", "No-server LSP manager should be native-alpha");
assert(noServerState.serverAvailable === false, "No-server LSP should not report a server");
assert(noServerState.serverReady === false, "No-server LSP should not be ready");
assert(noServerState.fallback === "static-completion", "No-server LSP should use static-completion fallback");
assert(noServerState.completionProvider === "static-fallback", "No-server LSP should use static completion provider");
assertIncludes(valuesFor(complete(noServer.client, "app.la", "la")), "launch", "no-server app.la");

const hoverColumn = 'app.launch("org.autojs.autojs6");'.indexOf("launch") + 2;
const hover = noServer.client.getHover({ row: 0, column: hoverColumn });
assert(hover, "No-server LSP hover should return static hover data");
assert(hover.caption === "app.launch", `Unexpected hover caption: ${hover.caption}`);
assert(
  hover.docText.includes("launch(app: App | App.Alias | App.PackageName): boolean;"),
  `Unexpected hover doc: ${hover.docText}`,
);

const invalid = createClient("{invalid-json", ["toast"]);
assert(invalid.errors.length === 1, "Invalid options should emit one LSP error");
assert(invalid.client.getState().state === "disabled", "Invalid options should fall back to disabled state");

const lspClientSource = fs.readFileSync(LSP_CLIENT_PATH, "utf8");
assert(!lspClientSource.includes("WebSocket"), "LSP client must not open WebSocket in alpha path");
assert(!lspClientSource.includes("new Worker"), "LSP client must not start worker transport in alpha path");

console.log("ACE LSP client tests passed.");
