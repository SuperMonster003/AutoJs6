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
const TS_RUNTIME_PATH = path.join(ACE_AUTOJS6_ROOT, "typescript", "typescriptServices.js");
const TS_SERVICE_PATH = path.join(ACE_AUTOJS6_ROOT, "autojs6_ts_language_service.js");
const ACE_BUILD_ROOT = path.dirname(ACE_AUTOJS6_ROOT);
const DEFAULT_MAX_DOCUMENT_LENGTH = 512 * 1024;

const LOCAL_SERVICE_OPTIONS = JSON.stringify({
  enabled: true,
  manager: "native-local",
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
  maxDocumentLength: DEFAULT_MAX_DOCUMENT_LENGTH,
});

const JSON_SERVICE_OPTIONS = JSON.stringify({
  ...JSON.parse(LOCAL_SERVICE_OPTIONS),
  documentUri: "file:///autojs6/editor/config.json",
});

function resolveAceAsset(assetUrl) {
  const cleanUrl = String(assetUrl || "").replace(/[?#].*$/, "").replaceAll("\\", "/");
  if (!cleanUrl.startsWith("./")) {
    throw new Error(`Expected a relative ACE asset URL, got ${assetUrl}`);
  }
  const resolved = path.resolve(ACE_BUILD_ROOT, cleanUrl.slice(2));
  const relative = path.relative(ACE_BUILD_ROOT, resolved);
  if (!relative || relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error(`ACE asset URL escapes the asset root: ${assetUrl}`);
  }
  return resolved;
}

function createSandbox({ browserAssets = false } = {}) {
  const scriptLoads = [];
  const bridgeReads = [];
  const sandbox = {
    console,
  };
  sandbox.window = sandbox;
  sandbox.self = sandbox;
  sandbox.globalThis = sandbox;

  if (browserAssets) {
    sandbox.setTimeout = setTimeout;
    sandbox.clearTimeout = clearTimeout;
    sandbox.autojs = {
      read(assetUrl) {
        bridgeReads.push(String(assetUrl || ""));
        try {
          const filePath = resolveAceAsset(assetUrl);
          return JSON.stringify({ ok: true, text: fs.readFileSync(filePath, "utf8") });
        } catch (error) {
          return JSON.stringify({ ok: false, error: String(error && error.message || error) });
        }
      },
    };
    const scriptParent = {
      appendChild(script) {
        try {
          const filePath = resolveAceAsset(script && script.src);
          scriptLoads.push(filePath);
          runScript(sandbox, filePath);
          if (script && typeof script.onload === "function") {
            script.onload();
          }
        } catch (error) {
          if (script && typeof script.onerror === "function") {
            script.onerror(error);
          } else {
            throw error;
          }
        }
        return script;
      },
    };
    sandbox.document = {
      head: scriptParent,
      body: scriptParent,
      documentElement: scriptParent,
      createElement(tagName) {
        return { tagName: String(tagName || "").toUpperCase(), src: "", async: false };
      },
    };
  }

  vm.createContext(sandbox);
  sandbox.__scriptLoads = scriptLoads;
  sandbox.__bridgeReads = bridgeReads;
  return sandbox;
}

function runScript(sandbox, filePath) {
  vm.runInContext(fs.readFileSync(filePath, "utf8"), sandbox, { filename: filePath });
}

function createRuntime({ browserAssets = false } = {}) {
  const sandbox = createSandbox({ browserAssets });
  runScript(sandbox, GENERATED_INDEX_PATH);
  runScript(sandbox, COMPLETER_PATH);
  runScript(sandbox, LSP_CLIENT_PATH);
  return {
    sandbox,
    staticCompleter: installStaticCompleter(sandbox),
    scriptLoads: sandbox.__scriptLoads,
    bridgeReads: sandbox.__bridgeReads,
  };
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
  let text = Array.isArray(lines) ? lines.join("\n") : String(lines || "");
  return {
    getValue() {
      return text;
    },
    getLine(row) {
      return text.split("\n")[row] || "";
    },
    getLength() {
      return text.split("\n").length;
    },
    getDocument() {
      return {
        getNewLineCharacter() {
          return text.includes("\r\n") ? "\r\n" : "\n";
        },
      };
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
    setValue(value, emitChange = true, delta = undefined) {
      text = String(value || "");
      if (emitChange && listeners.has("change")) {
        listeners.get("change")(delta);
      }
    },
    listenerCount(name) {
      return listeners.has(name) ? 1 : 0;
    },
  };
}

function createClient(optionsJson, lines = [""], runtime = createRuntime()) {
  let currentOptions = optionsJson;
  const { sandbox, staticCompleter } = runtime;
  const errors = [];
  const session = sessionFor(lines);
  const client = sandbox.window.AutoJsAceLspClient.install({
    editor: {},
    session,
    getOptions() {
      return typeof currentOptions === "function" ? currentOptions() : currentOptions;
    },
    getStaticCompleter() {
      return staticCompleter;
    },
    notifyError(message, error) {
      errors.push({ message, error });
    },
  });
  return {
    client,
    errors,
    runtime,
    sandbox,
    session,
    setOptions(options) {
      currentOptions = options;
    },
  };
}

function completeSession(client, session, pos, prefix) {
  let completions = null;
  let thrown = null;
  client.getCompletions({}, session, pos, prefix, (error, result) => {
    thrown = error;
    completions = result || [];
  });
  if (thrown) {
    throw thrown;
  }
  return completions;
}

function complete(client, line, prefix) {
  const session = sessionFor([line]);
  return completeSession(client, session, { row: 0, column: line.length }, prefix);
}

function positionAt(text, needle, offset = 0) {
  const index = String(text).indexOf(needle);
  if (index < 0) {
    throw new Error(`Missing position needle: ${needle}`);
  }
  const before = String(text).slice(0, index + offset);
  const rows = before.split("\n");
  return { row: rows.length - 1, column: rows[rows.length - 1].length };
}

function endPosition(text) {
  const rows = String(text).split("\n");
  return { row: rows.length - 1, column: rows[rows.length - 1].length };
}

function optionsFor(documentUri, overrides = {}) {
  return JSON.stringify({
    ...JSON.parse(LOCAL_SERVICE_OPTIONS),
    documentUri,
    ...overrides,
  });
}

function warmUp(client, label) {
  let callbackCalled = false;
  let ready = false;
  let state = null;
  const started = client.warmUp((ok, nextState) => {
    callbackCalled = true;
    ready = ok;
    state = nextState;
  });
  assert(started === true, `${label}: warm-up should start`);
  assert(callbackCalled, `${label}: browser-style script loading should complete warm-up callback`);
  assert(ready, `${label}: TypeScript language service should warm successfully; state=${JSON.stringify(state)}`);
  return state;
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
assert(localServiceState.manager === "native-local", "Local LSP manager should be native-local");
assert(localServiceState.state === "local-language-service", "Local LSP state should report local service");
assert(localServiceState.transport === "in-process", "Local LSP transport should be in-process");
assert(localServiceState.serverAvailable === false, "Local LSP should not report a server");
assert(localServiceState.serverReady === false, "Local LSP server should not be ready");
assert(localServiceState.localServiceReady === false, "Local LSP service should remain configured before warm-up");
assert(localServiceState.serviceStatus === "configured", "Local LSP should distinguish configured from ready");
assert(localServiceState.fallback === "static-completion", "Local LSP should keep static-completion fallback");
assert(localServiceState.completionProvider === "local-index", "Local LSP should use local index completion");
assert(localServiceState.hoverProvider === "local-index", "Local LSP should use local index hover");
assert(localServiceState.diagnosticProvider === "ace-jshint", "Local LSP should use Ace JSHint diagnostics");
assert(
  localServiceState.maxDocumentLength === DEFAULT_MAX_DOCUMENT_LENGTH,
  "Local LSP should expose the configured semantic document limit",
);
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
assert(
  completeSession(validJsonClient.client, validJsonClient.session, { row: 1, column: 2 }, "").length === 0,
  "JSON documents should not receive unrelated AutoJs static completions",
);

const invalidJsonClient = createClient(JSON_SERVICE_OPTIONS, ["{", '  "abis": [', "}"]);
const invalidJsonDiagnostics = invalidJsonClient.client.validateNow();
assert(invalidJsonDiagnostics.length === 1, "Invalid JSON should report one syntax diagnostic");
assert(invalidJsonDiagnostics[0].source === "autojs6-lsp", `Unexpected JSON diagnostic source: ${invalidJsonDiagnostics[0].source}`);
assert(invalidJsonDiagnostics[0].severity === 1, `Unexpected JSON diagnostic severity: ${invalidJsonDiagnostics[0].severity}`);

const browserRuntime = createRuntime({ browserAssets: true });
const inferredText = [
  'const localValue = { alpha: 1, beta: "two" };',
  "localValue.",
].join("\n");
const semanticFixture = createClient(
  optionsFor("file:///autojs6/editor/inference.ts"),
  inferredText,
  browserRuntime,
);
let semanticState = warmUp(semanticFixture.client, "real TypeScript service");
assert(semanticState.tsService.ready === true, `Expected ready TS service: ${JSON.stringify(semanticState.tsService)}`);
assert(semanticState.localServiceReady === true, "Warmed TypeScript service should report ready");
assert(semanticState.serviceStatus === "ready", "Warmed TypeScript service should expose ready status");
assert(semanticState.tsService.version, "Bundled TypeScript service should report its runtime version");
assert(semanticState.completionProvider === "typescript-language-service", "TS completion provider should be active");
assert(semanticState.hoverProvider === "typescript-language-service", "TS hover provider should be active");
assert(semanticState.signatureProvider === "typescript-language-service", "TS signature provider should be active");
assert(
  browserRuntime.scriptLoads.includes(TS_RUNTIME_PATH),
  `Browser loader should load bundled typescriptServices.js; actual=${browserRuntime.scriptLoads.join(", ")}`,
);
assert(
  browserRuntime.scriptLoads.includes(TS_SERVICE_PATH),
  `Browser loader should load bundled TS service; actual=${browserRuntime.scriptLoads.join(", ")}`,
);
assert(
  browserRuntime.bridgeReads.some((url) => url.endsWith("/typescript/lib.es2020.d.ts")),
  "TS service should read the bundled default lib through the native-style bridge",
);
assert(
  browserRuntime.bridgeReads.some((url) => url.endsWith("/types/lib.autojs6.d.ts")),
  "TS service should read AutoJs6 declarations through the native-style bridge",
);

const inferredCompletions = completeSession(
  semanticFixture.client,
  semanticFixture.session,
  endPosition(inferredText),
  "",
);
assertIncludes(valuesFor(inferredCompletions), "alpha", "TS inferred object completion");
assertIncludes(valuesFor(inferredCompletions), "beta", "TS inferred object completion");
const inferredHover = semanticFixture.client.getHover(positionAt(inferredText, "localValue.", 3));
assert(inferredHover, "TS inferred local variable should have hover information");
assert(
  inferredHover.docText.includes("localValue") &&
    inferredHover.docText.includes("alpha") &&
    inferredHover.docText.includes("beta"),
  `Unexpected inferred hover: ${JSON.stringify(inferredHover)}`,
);

const signatureText = [
  "function combine(left: string, right: number): string { return left + right; }",
  'combine("value", ',
].join("\n");
semanticFixture.session.setValue(signatureText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/signature.ts"));
semanticFixture.client.refresh();
const customSignature = semanticFixture.client.getSignatureHelp(endPosition(signatureText));
assert(customSignature, "TS should return signature help for a custom function");
assert(
  customSignature.signature.includes("combine(left: string, right: number): string"),
  `Unexpected custom signature: ${JSON.stringify(customSignature)}`,
);
assert(customSignature.activeParameter === 1, `Unexpected custom signature parameter: ${customSignature.activeParameter}`);

const semanticErrorText = 'const count: number = "not-a-number";';
semanticFixture.session.setValue(semanticErrorText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/semantic-error.ts"));
semanticFixture.client.refresh();
const semanticDiagnostics = semanticFixture.client.validateNow();
assert(
  semanticDiagnostics.some((item) =>
    item.source === "autojs6-ts" && /string.+not assignable.+number/i.test(item.message)),
  `Expected a TypeScript semantic assignment diagnostic: ${JSON.stringify(semanticDiagnostics)}`,
);

const relativeImportText = [
  'import { helper } from "./dep";',
  "helper();",
].join("\n");
semanticFixture.session.setValue(relativeImportText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/import.ts"));
semanticFixture.client.refresh();
const relativeImportDiagnostics = semanticFixture.client.validateNow();
assert(
  !relativeImportDiagnostics.some((item) => item.code === "2307" || /cannot find module/i.test(item.message)),
  `Single-document service must suppress unreliable module-resolution diagnostics: ${JSON.stringify(relativeImportDiagnostics)}`,
);

const jsDocText = [
  "/**",
  " * Formats a display name.",
  " * @param {string} name user display name",
  " */",
  "function greet(name) { return name.toUpperCase(); }",
  'greet("Ada");',
].join("\n");
semanticFixture.session.setValue(jsDocText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/jsdoc.js", { checkJs: true }));
semanticFixture.client.refresh();
const jsDocHover = semanticFixture.client.getHover(positionAt(jsDocText, "name) {", 2));
assert(jsDocHover, "JSDoc parameter should have TypeScript hover information");
assert(
  jsDocHover.docText.includes("@param") && jsDocHover.docText.includes("user display name"),
  `Unexpected JSDoc parameter hover: ${JSON.stringify(jsDocHover)}`,
);

const tsxText = "const view = <div>{1}</div>;";
semanticFixture.session.setValue(tsxText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/component.tsx"));
semanticFixture.client.refresh();
const tsxDiagnostics = semanticFixture.client.validateNow();
assert(
  !tsxDiagnostics.some((item) => item.code === "17004" || /cannot use jsx unless/i.test(item.message)),
  `TSX should use the TSX script kind and preserve JSX: ${JSON.stringify(tsxDiagnostics)}`,
);

for (const extension of ["mts", "cts"]) {
  const moduleText = [
    "const moduleCount: number = 1;",
    "moduleCount.toF",
  ].join("\n");
  semanticFixture.session.setValue(moduleText, false);
  semanticFixture.setOptions(optionsFor(`file:///autojs6/editor/module.${extension}`));
  semanticFixture.client.refresh();
  const moduleCompletions = completeSession(
    semanticFixture.client,
    semanticFixture.session,
    endPosition(moduleText),
    "toF",
  );
  assertIncludes(valuesFor(moduleCompletions), "toFixed", `.${extension} TypeScript script-kind completion`);
  const moduleDiagnostics = semanticFixture.client.validateNow();
  assert(
    !moduleDiagnostics.some((item) => item.code === "8010" || /type annotations can only be used/i.test(item.message)),
    `.${extension} should accept TypeScript annotations: ${JSON.stringify(moduleDiagnostics)}`,
  );
}

const shadowText = [
  "function useLocalFiles() {",
  "  const files = { customOnly: 1 };",
  "  files.",
  "}",
].join("\n");
semanticFixture.session.setValue(shadowText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/shadow.ts"));
semanticFixture.client.refresh();
const shadowCompletions = completeSession(
  semanticFixture.client,
  semanticFixture.session,
  { row: 2, column: "  files.".length },
  "",
);
const shadowValues = valuesFor(shadowCompletions);
assertIncludes(shadowValues, "customOnly", "local files shadow completion");
assert(!shadowValues.includes("read"), `Local files shadow must not mix static files.read: ${shadowValues.join(", ")}`);
assert(!shadowValues.includes("write"), `Local files shadow must not mix static files.write: ${shadowValues.join(", ")}`);

semanticFixture.setOptions("{}");
semanticFixture.client.refresh();
let hotState = semanticFixture.client.getState();
assert(hotState.enabled === false, "Hot disable should disable the semantic service");
assert(hotState.completionProvider === "static-fallback", "Hot disable should restore static completion");
assertIncludes(
  valuesFor(complete(semanticFixture.client, "console.lo", "lo")),
  "log",
  "hot-disabled static fallback",
);

semanticFixture.session.setValue(inferredText, false);
semanticFixture.setOptions(optionsFor("file:///autojs6/editor/hot-enable.ts"));
semanticFixture.client.refresh();
hotState = warmUp(semanticFixture.client, "hot enable");
assert(hotState.enabled === true && hotState.tsService.ready === true, "Hot enable should recreate a ready TS service");
assert(semanticFixture.session.listenerCount("change") === 1, "Enabled client should attach one change listener");
semanticFixture.client.destroy();
const destroyedState = semanticFixture.client.getState();
assert(destroyedState.enabled === false && destroyedState.state === "disabled", "Destroyed client should be disabled");
assert(destroyedState.semanticServiceReason === "destroyed", "Destroyed client should expose its terminal reason");
assert(semanticFixture.session.listenerCount("change") === 0, "Destroy should detach the change listener");
assertIncludes(
  valuesFor(complete(semanticFixture.client, "console.lo", "lo")),
  "log",
  "destroyed static fallback",
);

const largeDocumentText = `${"// padding\n".repeat(20)}files.re`;
const largeFixture = createClient(
  optionsFor("file:///autojs6/editor/large.js", { maxDocumentLength: 64 }),
  largeDocumentText,
  browserRuntime,
);
const largeState = largeFixture.client.getState();
assert(largeState.maxDocumentLength === 64, "Large-document test should expose its configured limit");
assert(largeState.semanticServiceSuppressed === true, "Large documents should suppress the semantic service");
assert(largeState.semanticServiceReason === "document-too-large", "Large-document suppression should report its reason");
assert(largeState.serviceStatus === "degraded", "Large documents should expose degraded service status");
assert(largeState.tsService.ready === false, "Suppressed large documents should not create a TS service");
const largeCompletions = completeSession(
  largeFixture.client,
  largeFixture.session,
  endPosition(largeDocumentText),
  "re",
);
assertIncludes(valuesFor(largeCompletions), "read", "large-document static completion fallback");
assert(largeFixture.client.validateNow().length === 0, "Suppressed large documents should skip semantic diagnostics");
largeFixture.client.destroy();

const crlfLengthFixture = createClient(
  optionsFor("file:///autojs6/editor/crlf.js", { maxDocumentLength: 6 }),
  "a\r\nb",
  browserRuntime,
);
crlfLengthFixture.session.setValue(
  "a\r\nx\r\nb",
  true,
  { action: "insert", lines: ["x", ""] },
);
const crlfLengthState = crlfLengthFixture.client.getState();
assert(crlfLengthState.documentLength === 7, "CRLF delta length should use the session newline width");
assert(crlfLengthState.semanticServiceSuppressed === true, "CRLF delta should enforce the semantic limit without a full rescan");
crlfLengthFixture.client.destroy();

const invalid = createClient("{invalid-json", ["toast"]);
assert(invalid.errors.length === 1, "Invalid options should emit one LSP error");
assert(invalid.client.getState().state === "disabled", "Invalid options should fall back to disabled state");

const lspClientSource = fs.readFileSync(LSP_CLIENT_PATH, "utf8");
assert(!lspClientSource.includes("WebSocket"), "LSP client must not open socket transport");
assert(!lspClientSource.includes("new Worker"), "LSP client must not start worker transport");
assert(lspClientSource.includes("TS_LOAD_TIMEOUT_MS = 5000"), "TS asset loading should have a bounded timeout");

console.log("ACE LSP client tests passed.");
