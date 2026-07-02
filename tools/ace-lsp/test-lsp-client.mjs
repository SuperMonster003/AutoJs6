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

const LOCAL_SERVICE_OPTIONS = JSON.stringify({
  enabled: true,
  manager: "native-preview",
  state: "local-language-service",
  transport: "in-process",
  serverUri: null,
  rootUri: "file:///autojs6/editor",
  documentUri: "file:///autojs6/editor/current.js",
  libraryUris: ["autojs6/types/lib.autojs6.d.ts"],
  fallback: "static-completion",
  startSupported: false,
  serverAvailable: false,
  reason: "browser-local-language-service",
  completionProvider: "local-index",
  hoverProvider: "local-index",
  diagnosticProvider: "ace-jshint",
  signatureProvider: "static-local",
  features: ["completion", "hover", "diagnostics", "signatureHelp"],
});

const JSON_SERVICE_OPTIONS = JSON.stringify({
  ...JSON.parse(LOCAL_SERVICE_OPTIONS),
  documentUri: "file:///autojs6/editor/config.json",
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
  const listeners = new Map();
  let annotations = [];
  return {
    getValue() {
      return lines.join("\n");
    },
    getLine(row) {
      return lines[row] || "";
    },
    getLength() {
      return lines.length;
    },
    setAnnotations(value) {
      annotations = value || [];
    },
    getAnnotations() {
      return annotations;
    },
    on(name, listener) {
      listeners.set(name, listener);
    },
    off(name, listener) {
      if (listeners.get(name) === listener) {
        listeners.delete(name);
      }
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

function captionsFor(completions) {
  return completions.map((item) => item.caption || item.value);
}

const disabled = createClient("{}", ["console.lo"]);
const disabledState = disabled.client.getState();
assert(disabledState.enabled === false, "Disabled LSP state should not be enabled");
assert(disabledState.state === "disabled", "Disabled LSP state should be disabled");
assert(disabledState.completionProvider === "static-fallback", "Disabled LSP should keep static completion fallback");
assert(disabledState.hoverProvider === "static-fallback", "Disabled LSP should keep static hover fallback");
assert(disabledState.diagnosticProvider === "disabled", "Disabled LSP should keep diagnostics disabled");
assertIncludes(valuesFor(complete(disabled.client, "console.lo", "lo")), "log", "disabled console.lo");

const localService = createClient(LOCAL_SERVICE_OPTIONS, ['app.launch("org.autojs.autojs6");']);
const localServiceState = localService.client.getState();
assert(localServiceState.enabled === true, "Local LSP state should be enabled");
assert(localServiceState.manager === "native-preview", "Local LSP manager should be native-preview");
assert(localServiceState.state === "local-language-service", "Local LSP state should report local service");
assert(localServiceState.transport === "in-process", "Local LSP transport should be in-process");
assert(localServiceState.serverAvailable === false, "Local LSP should not report a server");
assert(localServiceState.serverReady === false, "Local LSP server should not be ready");
assert(localServiceState.localServiceReady === true, "Local LSP service should be ready");
assert(localServiceState.fallback === "static-completion", "Local LSP should keep static-completion fallback");
assert(localServiceState.completionProvider === "local-index", "Local LSP should use local index completion");
assert(localServiceState.hoverProvider === "local-index", "Local LSP should use local index hover");
assert(localServiceState.diagnosticProvider === "ace-jshint", "Local LSP should use Ace JSHint diagnostics");
assertIncludes(valuesFor(complete(localService.client, "app.la", "la")), "launch", "local-service app.la");
const launchCompletion = complete(localService.client, "app.laun", "laun")
  .find((item) => (item.value || item.caption) === "launch");
assert(launchCompletion && launchCompletion.caption === "launch", "local-service app.laun should display launch");
assert(
  launchCompletion && launchCompletion.snippet === "launch(${1})",
  `local-service app.laun should insert call snippet; actual=${JSON.stringify(launchCompletion)}`,
);
assertIncludes(
  valuesFor(complete(localService.client, "app.autojs.", "")),
  "setRootMode",
  "local-service app.autojs.",
);
assertIncludes(valuesFor(complete(localService.client, "RootMod", "RootMod")), "RootMode", "local-service RootMode");

const objectPrototypeCompletions = complete(localService.client, "Object.proto", "proto");
assertIncludes(
  captionsFor(objectPrototypeCompletions),
  "prototype",
  "local-service Object.proto caption",
);
assertIncludes(
  valuesFor(objectPrototypeCompletions),
  "prototype",
  "local-service Object.proto insert value",
);
const objectPrototypeMembers = complete(localService.client, "Object.prototype.", "");
assertIncludes(captionsFor(objectPrototypeMembers), "hasOwnProperty", "local-service Object.prototype caption");
assertIncludes(valuesFor(objectPrototypeMembers), "hasOwnProperty", "local-service Object.prototype insert value");
assertIncludes(valuesFor(complete(localService.client, '"abc".', ".")), "trim", 'local-service "abc".');
assertIncludes(valuesFor(complete(localService.client, "[].", ".")), "map", "local-service [].");
assertIncludes(valuesFor(complete(localService.client, "(1).", ".")), "toFixed", "local-service (1).");
assertIncludes(valuesFor(complete(localService.client, "({}).", ".")), "hasOwnProperty", "local-service ({}).");

const hoverColumn = 'app.launch("org.autojs.autojs6");'.indexOf("launch") + 2;
const hover = localService.client.getHover({ row: 0, column: hoverColumn });
assert(hover, "Local LSP hover should return static hover data");
assert(hover.caption === "app.launch", `Unexpected hover caption: ${hover.caption}`);
assert(
  hover.docText.includes("launch(app: App | App.Alias | App.PackageName): boolean;"),
  `Unexpected hover doc: ${hover.docText}`,
);

const signatureService = createClient(LOCAL_SERVICE_OPTIONS, ["app.launch("]);
const signature = signatureService.client.getSignatureHelp({ row: 0, column: "app.launch(".length });
assert(signature, "Local LSP should return indexed signature help");
assert(signature.caption === "app.launch", `Unexpected signature caption: ${signature && signature.caption}`);
assert(
  signature.signature === "launch(app: App | App.Alias | App.PackageName): boolean",
  `Unexpected signature: ${signature && signature.signature}`,
);
assert(signature.activeParameter === 0, `Unexpected signature active parameter: ${signature && signature.activeParameter}`);

const secondParameterService = createClient(LOCAL_SERVICE_OPTIONS, ["app.autojs.setRootMode('root', "]);
const secondParameterSignature = secondParameterService.client.getSignatureHelp({
  row: 0,
  column: "app.autojs.setRootMode('root', ".length,
});
assert(secondParameterSignature, "Local LSP should return nested alias signature help");
assert(
  secondParameterSignature.caption === "app.autojs.setRootMode",
  `Unexpected nested signature caption: ${secondParameterSignature && secondParameterSignature.caption}`,
);
assert(
  secondParameterSignature.activeParameter === 1,
  `Unexpected nested active parameter: ${secondParameterSignature && secondParameterSignature.activeParameter}`,
);

const diagnosticClient = createClient(LOCAL_SERVICE_OPTIONS, ["function bad("]);
const diagnostics = diagnosticClient.client.validateNow();
assert(diagnostics.length > 0, "Local LSP should report fallback diagnostics when JSHint is unavailable");
assert(diagnostics[0].source === "autojs6-lsp", `Unexpected diagnostic source: ${diagnostics[0].source}`);
assert(diagnostics[0].severity === 1, `Unexpected diagnostic severity: ${diagnostics[0].severity}`);
assert(
  diagnosticClient.client.getState().diagnosticsCount === diagnostics.length,
  "Diagnostic count should reflect last validation",
);

const validJsonClient = createClient(JSON_SERVICE_OPTIONS, [
  "{",
  '  "abis": [],',
  '  "build": {',
  '    "number": 0,',
  '    "time": 0',
  "  }",
  "}",
]);
const validJsonDiagnostics = validJsonClient.client.validateNow();
assert(
  validJsonDiagnostics.length === 0,
  `Valid JSON should not report JavaScript semicolon diagnostics: ${JSON.stringify(validJsonDiagnostics)}`,
);
assert(validJsonClient.client.getState().diagnosticsReady === true, "JSON diagnostics should be ready");

const invalidJsonClient = createClient(JSON_SERVICE_OPTIONS, ["{", '  "abis": [', "}"]);
const invalidJsonDiagnostics = invalidJsonClient.client.validateNow();
assert(invalidJsonDiagnostics.length === 1, "Invalid JSON should report one syntax diagnostic");
assert(invalidJsonDiagnostics[0].source === "autojs6-lsp", `Unexpected JSON diagnostic source: ${invalidJsonDiagnostics[0].source}`);
assert(invalidJsonDiagnostics[0].severity === 1, `Unexpected JSON diagnostic severity: ${invalidJsonDiagnostics[0].severity}`);

const invalid = createClient("{invalid-json", ["toast"]);
assert(invalid.errors.length === 1, "Invalid options should emit one LSP error");
assert(invalid.client.getState().state === "disabled", "Invalid options should fall back to disabled state");

const lspClientSource = fs.readFileSync(LSP_CLIENT_PATH, "utf8");
assert(!lspClientSource.includes("WebSocket"), "LSP client must not open socket transport");
assert(!lspClientSource.includes("new Worker"), "LSP client must not start worker transport");

console.log("ACE LSP client tests passed.");
