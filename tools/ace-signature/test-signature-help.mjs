#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const HOST_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
// The editor assets live in the separately checked out Ace editor plugin; keep legacy host layouts usable.
const PROJECT_ROOT = process.env.AUTOJS6_ACE_EDITOR_ROOT
  ? path.resolve(process.env.AUTOJS6_ACE_EDITOR_ROOT)
  : HOST_ROOT;
const SIGNATURE_HELP_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6_signature_help.js",
);
const HTML_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6_editor.html",
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

function createSandbox() {
  const sandbox = {
    window: {
      document: {
        createElement(tagName) {
          return {
            tagName,
            className: "",
            children: [],
            style: {},
            offsetWidth: 360,
            offsetHeight: 72,
            appendChild(child) {
              this.children.push(child);
              child.parentNode = this;
              return child;
            },
            contains(child) {
              return child === this || this.children.includes(child);
            },
            removeChild(child) {
              this.children = this.children.filter((it) => it !== child);
              child.parentNode = null;
            },
            addEventListener() {},
            removeEventListener() {},
            set textContent(value) {
              this._textContent = String(value);
            },
            get textContent() {
              return this._textContent || "";
            },
            set innerHTML(value) {
              this._innerHTML = String(value);
            },
            get innerHTML() {
              return this._innerHTML || "";
            },
          };
        },
        body: {
          children: [],
          appendChild(child) {
            this.children.push(child);
            child.parentNode = this;
            return child;
          },
          removeChild(child) {
            this.children = this.children.filter((it) => it !== child);
            child.parentNode = null;
          },
        },
        documentElement: {
          clientWidth: 420,
          clientHeight: 640,
        },
      },
      innerWidth: 420,
      innerHeight: 640,
      pageXOffset: 0,
      pageYOffset: 0,
    },
    console,
    setTimeout,
    clearTimeout,
  };
  sandbox.window.window = sandbox.window;
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(SIGNATURE_HELP_PATH, "utf8"), sandbox, { filename: SIGNATURE_HELP_PATH });
  return sandbox;
}

function offsetAt(text, needle, delta = 0) {
  const index = text.indexOf(needle);
  if (index < 0) {
    throw new Error(`Missing needle ${needle}`);
  }
  return index + needle.length + delta;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function createEmitter() {
  const handlers = new Map();
  return {
    on(name, callback) {
      handlers.set(name, [...(handlers.get(name) || []), callback]);
    },
    off(name, callback) {
      handlers.set(name, (handlers.get(name) || []).filter((it) => it !== callback));
    },
    emit(name, event) {
      for (const callback of handlers.get(name) || []) {
        callback(event);
      }
    },
    listenerCount(name) {
      return (handlers.get(name) || []).length;
    },
  };
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function assertSignature(api, text, offset, expected) {
  const help = api.findSignatureHelpFromText(text, offset);
  assert(help, `Missing signature help for ${text} at ${offset}`);
  for (const [key, value] of Object.entries(expected)) {
    assert(
      help[key] === value,
      `Unexpected ${key}: actual=${help[key]}, expected=${value}, help=${JSON.stringify(help)}`,
    );
  }
  return help;
}

const sandbox = createSandbox();
const api = sandbox.window.AutoJsAceSignatureHelp;

assert(api, "AutoJsAceSignatureHelp should be installed");

let source = "Function.prototype.apply(";
let help = assertSignature(api, source, source.length, {
  caption: "Function.prototype.apply",
  signature: "apply(thisArg: any, argArray?: any): any",
  activeParameter: 0,
});
assert(help.parameters.includes("thisArg: any"), "apply signature should include thisArg");
assert(help.parameters.includes("argArray?: any"), "apply signature should include argArray");

source = "Function.prototype.apply(this, ";
assertSignature(api, source, source.length, {
  caption: "Function.prototype.apply",
  activeParameter: 1,
});

source = "Function.prototype.apply(this, [1, 2]);";
assertSignature(api, source, offsetAt(source, "2"), {
  caption: "Function.prototype.apply",
  activeParameter: 1,
});

source = "items.map(";
assertSignature(api, source, source.length, {
  caption: "Array.prototype.map",
  activeParameter: 0,
});

assert(
  api.findSignatureHelpFromText("unknownThing(", "unknownThing(".length) === null,
  "Unknown functions should not produce signature help",
);

const session = {
  value: "Function.prototype.apply(this, args);",
  getValue() {
    return this.value;
  },
  doc: {
    positionToIndex(pos) {
      return pos.column;
    },
  },
};
const commands = createEmitter();
let cursorColumn = "Function.prototype.apply(this, ".length;
const editor = {
  session,
  commands,
  container: {
    addEventListener() {},
    removeEventListener() {},
  },
  renderer: {
    lineHeight: 20,
    textToScreenCoordinates(row, column) {
      return {
        pageX: 16 + column * 8,
        pageY: 40 + row * 20,
      };
    },
  },
  selection: {
    on() {},
    off() {},
  },
  on() {},
  getCursorPosition() {
    return { row: 0, column: cursorColumn };
  },
};
const controller = api.createController({ editor, session, delayMs: 1 });
assert(commands.listenerCount("afterExec") === 1, "Controller should listen for command afterExec");
assert(controller.showAtPosition({ row: 0, column: "Function.prototype.apply(this, ".length }) === true, "Controller should show apply signature");
const state = controller.getState();
assert(state.visible === true, "Controller state should become visible");
assert(state.signature === "apply(thisArg: any, argArray?: any): any", `Unexpected state signature: ${state.signature}`);
assert(state.activeParameter === 1, `Unexpected active parameter: ${state.activeParameter}`);
controller.hide();
assert(controller.getState().visible === false, "Controller should hide signature help");
session.value = "Function.prototype.apply(";
cursorColumn = "Function.prototype.apply(".length;
commands.emit("afterExec", { command: { name: "insertstring" }, args: "(" });
await wait(5);
assert(controller.getState().visible === true, "Controller should show signature help after typing an opening paren");
controller.destroy();
assert(commands.listenerCount("afterExec") === 0, "Controller should remove command afterExec listener");

// Overloads: normalization, nav rendering, cycling, pinned selection and rest-parameter highlight.
const spliceOverloads = {
  caption: "splice",
  signature: "splice(start: number, deleteCount: number, ...items: any[]): any[]",
  parameters: ["start: number", "deleteCount: number", "...items: any[]"],
  activeParameter: 2,
  docText: "Removes and inserts elements.",
  signatures: [
    {
      caption: "splice",
      signature: "splice(start: number, deleteCount?: number): any[]",
      parameters: ["start: number", "deleteCount?: number"],
      parameterDocs: ["Index at which to start.", "Number of elements to remove."],
      docText: "Removes elements.",
    },
    {
      caption: "splice",
      signature: "splice(start: number, deleteCount: number, ...items: any[]): any[]",
      parameters: ["start: number", "deleteCount: number", "...items: any[]"],
      parameterDocs: ["Index at which to start.", "Number of elements to remove.", "Elements to insert."],
      isVariadic: true,
      docText: "Removes and inserts elements.",
    },
  ],
  activeSignature: 1,
  applicableSpanStart: 10,
};
const normalizedOverloads = api.normalizeSignatureHelp(spliceOverloads);
assert(normalizedOverloads.signatures.length === 2, "normalizeSignatureHelp should keep every overload");
assert(normalizedOverloads.activeSignature === 1, "normalizeSignatureHelp should keep the provider's active overload");
assert(normalizedOverloads.activeParameter === 2, "normalizeSignatureHelp should keep the active parameter");
assert(normalizedOverloads.key === "span:10", `Unexpected overload key: ${normalizedOverloads.key}`);
const normalizedSingle = api.normalizeSignatureHelp({
  caption: "apply",
  callee: "fn.apply",
  signature: "apply(a, b)",
  parameters: ["a", "b"],
  activeParameter: 1,
});
assert(
  normalizedSingle.signatures.length === 1 && normalizedSingle.activeSignature === 0 && normalizedSingle.key === "callee:fn.apply",
  `Single signatures should normalize to one overload: ${JSON.stringify(normalizedSingle)}`,
);
assert(api.normalizeSignatureHelp(null) === null, "normalizeSignatureHelp(null) should be null");
assert(api.normalizeSignatureHelp({ caption: "x" }) === null, "Signature-less help should be dropped");

const callSession = {
  getValue() {
    return "foo(bar, baz); qux";
  },
  doc: {
    positionToIndex(pos) {
      return pos.column;
    },
  },
};
assert(api.isInsideCallArguments(callSession, { row: 0, column: 4 }) === true, "Cursor after '(' should be inside call arguments");
assert(
  api.isInsideCallArguments(callSession, { row: 0, column: "foo(bar, baz); q".length }) === false,
  "Cursor after the closing paren should not be inside call arguments",
);
assert(api.isInsideCallArguments(null, { row: 0, column: 0 }) === false, "Missing session should not be inside call arguments");

let providerHelp = spliceOverloads;
const overloadCommands = createEmitter();
const overloadEditor = {
  session: callSession,
  commands: overloadCommands,
  container: {
    addEventListener() {},
    removeEventListener() {},
  },
  renderer: {
    lineHeight: 20,
    textToScreenCoordinates(row, column) {
      return { pageX: 16 + column * 8, pageY: 40 + row * 20 };
    },
  },
  selection: {
    on() {},
    off() {},
  },
  on() {},
  getCursorPosition() {
    return { row: 0, column: 12 };
  },
};
const overloadController = api.createController({
  editor: overloadEditor,
  session: callSession,
  delayMs: 1,
  getSignatureHelp(pos, callback) {
    callback(null, providerHelp);
  },
});
assert(overloadController.isVisible() === false, "Overload controller should start hidden");
assert(overloadController.cycleSignature(1) === false, "Cycling while hidden should be a no-op");
assert(overloadController.showAtPosition({ row: 0, column: 12 }, { force: true }) === true, "Overload controller should show");
const tooltipElements = sandbox.window.document.body.children.filter((it) => it.className === "autojs6_signature_tooltip");
assert(tooltipElements.length === 1, `Expected one live signature tooltip, found ${tooltipElements.length}`);
const tooltipElement = tooltipElements[0];
const headerElement = tooltipElement.children[0];
const signatureElement = tooltipElement.children[1];
const summaryElement = tooltipElement.children[2];
const navElement = headerElement.children[1];
const navCounter = navElement.children[1];
assert(navElement.children[0].textContent === "▴", "Overload nav should start with an up button");
assert(navElement.children[2].textContent === "▾", "Overload nav should end with a down button");
let overloadState = overloadController.getState();
assert(overloadState.signatureCount === 2, `Unexpected signatureCount: ${overloadState.signatureCount}`);
assert(overloadState.activeSignature === 1, `Unexpected activeSignature: ${overloadState.activeSignature}`);
assert(overloadState.pinnedSignature === false, "Provider-selected overload should not count as pinned");
assert(overloadController.getSignatureCount() === 2, "getSignatureCount should report both overloads");
assert(navElement.className.includes("autojs6_signature_tooltip_nav_visible"), "Overload nav should be visible for two signatures");
assert(navCounter.textContent === "2/2", `Unexpected overload counter: ${navCounter.textContent}`);
assert(
  signatureElement.innerHTML.includes('<span class="autojs6_signature_tooltip_active">...items: any[]</span>'),
  `Rest parameter should be highlighted for extra arguments: ${signatureElement.innerHTML}`,
);
assert(summaryElement.textContent === "Elements to insert.", `Parameter documentation should win: ${summaryElement.textContent}`);

assert(overloadController.cycleSignature(1) === true, "Cycling should wrap to the first overload");
overloadState = overloadController.getState();
assert(overloadState.activeSignature === 0 && overloadState.pinnedSignature === true, `Cycling should pin overload 0: ${JSON.stringify(overloadState)}`);
assert(navCounter.textContent === "1/2", `Unexpected overload counter after cycling: ${navCounter.textContent}`);
assert(overloadState.signature === "splice(start: number, deleteCount?: number): any[]", `Unexpected cycled signature: ${overloadState.signature}`);
assert(!signatureElement.innerHTML.includes("autojs6_signature_tooltip_active"), "Non-variadic overload should not highlight an out-of-range parameter");
assert(summaryElement.textContent === "Removes elements.", `Function documentation should be the fallback: ${summaryElement.textContent}`);

assert(overloadController.showAtPosition({ row: 0, column: 13 }) === true, "Re-showing the same call should succeed");
assert(overloadController.getState().activeSignature === 0, "Manual overload choice should survive re-show within the same call");
providerHelp = Object.assign({}, spliceOverloads, { applicableSpanStart: 40 });
assert(overloadController.showAtPosition({ row: 0, column: 44 }) === true, "Showing a different call should succeed");
overloadState = overloadController.getState();
assert(overloadState.activeSignature === 1 && overloadState.pinnedSignature === false, `A new call should reset the pinned overload: ${JSON.stringify(overloadState)}`);
assert(overloadController.cycleSignature(-1) === true && overloadController.getState().activeSignature === 0, "Negative delta should cycle backwards");

providerHelp = { caption: "apply", signature: "apply(thisArg: any, argArray?: any): any", parameters: ["thisArg: any", "argArray?: any"], activeParameter: 0 };
assert(overloadController.showAtPosition({ row: 0, column: 4 }) === true, "Single-signature help should still show");
assert(!navElement.className.includes("autojs6_signature_tooltip_nav_visible"), "Overload nav should hide for a single signature");
assert(overloadController.cycleSignature(1) === false, "Cycling a single signature should be a no-op");
overloadController.hide();
assert(overloadController.isVisible() === false && overloadController.getSignatureCount() === 0, "Hidden controller should report no signatures");
overloadController.destroy();

// Auto-trigger policy (cursor / typing / off) and touch restore (tap restores, scroll does not).
function createDomEmitter() {
  const handlers = new Map();
  return {
    addEventListener(name, callback) {
      handlers.set(name, [...(handlers.get(name) || []), callback]);
    },
    removeEventListener(name, callback) {
      handlers.set(name, (handlers.get(name) || []).filter((it) => it !== callback));
    },
    emit(name, event) {
      for (const callback of handlers.get(name) || []) {
        callback({ type: name, target: null, ...(event || {}) });
      }
    },
    listenerCount(name) {
      return (handlers.get(name) || []).length;
    },
  };
}
const policySession = Object.assign(createEmitter(), {
  getValue() {
    return "foo(bar, baz);";
  },
  doc: {
    positionToIndex(pos) {
      return pos.column;
    },
  },
});
const policySelection = createEmitter();
const policyCommands = createEmitter();
const policyContainer = createDomEmitter();
const policyEditor = {
  session: policySession,
  commands: policyCommands,
  container: policyContainer,
  renderer: {
    lineHeight: 20,
    textToScreenCoordinates(row, column) {
      return { pageX: 16 + column * 8, pageY: 40 + row * 20 };
    },
  },
  selection: policySelection,
  on() {},
  getCursorPosition() {
    return { row: 0, column: 4 };
  },
};
const policyHelp = { caption: "foo", callee: "foo", signature: "foo(a, b)", parameters: ["a", "b"], activeParameter: 0 };
const policyController = api.createController({
  editor: policyEditor,
  session: policySession,
  delayMs: 1,
  autoTrigger: "off",
  getSignatureHelp(pos, callback) {
    callback(null, policyHelp);
  },
});
assert(policyController.getAutoTrigger() === "off", "Controller should accept the initial auto-trigger mode");
assert(policyContainer.listenerCount("touchend") === 1, "Controller should listen for touchend");
policySelection.emit("changeCursor");
await wait(5);
assert(policyController.isVisible() === false, "off: cursor changes must not show the box");
assert(policyController.showAtPosition({ row: 0, column: 4 }, { force: true }) === true, "off: explicit triggers still show the box");
policyController.hide();
assert(policyController.setAutoTrigger("typing") === "typing", "setAutoTrigger should return the normalized mode");
policySelection.emit("changeCursor");
await wait(5);
assert(policyController.isVisible() === false, "typing: cursor changes must not show the box while hidden");
policyCommands.emit("afterExec", { command: { name: "backspace" } });
await wait(5);
assert(policyController.isVisible() === false, "typing: backspace is not a trigger character");
policyCommands.emit("afterExec", { command: { name: "insertstring" }, args: "(" });
await wait(5);
assert(policyController.isVisible() === true, "typing: a trigger character shows the box");
policySelection.emit("changeCursor");
await wait(5);
assert(policyController.isVisible() === true, "typing: the visible box keeps following the caret");
policyController.hide();
assert(policyController.setAutoTrigger("bogus") === "cursor", "Unknown modes fall back to cursor");
policySelection.emit("changeCursor");
await wait(5);
assert(policyController.isVisible() === true, "cursor: cursor changes show the box");
policyContainer.emit("touchstart");
assert(policyController.isVisible() === false, "touchstart hides the box");
policyContainer.emit("touchend");
await wait(5);
assert(policyController.isVisible() === true, "A tap without scrolling restores the box");
policyContainer.emit("touchstart");
policySession.emit("changeScrollTop");
policyContainer.emit("touchend");
await wait(5);
assert(policyController.isVisible() === false, "A scrolling touch must not restore the box");
policySelection.emit("changeCursor");
await wait(5);
assert(policyController.isVisible() === true, "The next cursor change shows the box again");
policyContainer.emit("touchstart");
policyContainer.emit("touchcancel");
await wait(5);
assert(policyController.isVisible() === false, "touchcancel keeps the box hidden");
assert(policyController.getState().autoTrigger === "cursor", "State should expose the auto-trigger mode");
assert(policyController.setAutoTrigger("off") === "off", "Switching to off should succeed");
policyController.showAtPosition({ row: 0, column: 4 }, { force: true });
assert(policyController.isVisible() === true, "Forced show works in off mode");
policyController.setAutoTrigger("off");
policyController.destroy();
assert(policyContainer.listenerCount("touchend") === 0, "Controller should remove the touchend listener");

const html = fs.readFileSync(HTML_PATH, "utf8");
assert(
  html.indexOf("autojs6_signature_help.js") > html.indexOf("autojs6_tooltip.js") &&
    html.indexOf("autojs6_signature_help.js") < html.indexOf("autojs6_ace_bridge.js"),
  "Signature help should load after tooltip and before bridge",
);

const css = fs.readFileSync(CSS_PATH, "utf8");
assert(css.includes(".autojs6_signature_tooltip"), "Signature tooltip CSS is missing");
assert(css.includes("pointer-events: auto"), "Signature tooltip should receive touch input");
assert(css.includes("overflow: auto"), "Signature tooltip should be scrollable");
assert(css.includes("-webkit-overflow-scrolling: touch"), "Signature tooltip should use momentum touch scrolling");
assert(css.includes("autojs6_signature_tooltip_compact"), "Signature tooltip compact fallback CSS is missing");

const bridge = fs.readFileSync(BRIDGE_PATH, "utf8");
for (const expected of [
  "var signatureHelpController = null;",
  "global.AutoJsAceSignatureHelp.install",
  "getSignatureHelp: function(row, column)",
  "showSignatureHelp: function(row, column)",
  "getSignatureHelpState: function()",
  "cycleSignatureHelp: function(delta)",
  "triggerSmartHint: triggerSmartHint,",
  "triggerSignatureHelp: triggerSignatureHelp,",
  "triggerSymbolDocumentation: triggerSymbolDocumentation,",
  'name: "autojs6SmartHint"',
  'name: "autojs6SignatureHelp"',
  'name: "autojs6ShowHover"',
  "signatureHelpVisible: isSignatureHelpVisible(),",
  "signatureCount: getSignatureHelpCount(),",
  "setSmartHintOptions: function(options)",
  "getSmartHintOptions: function()",
  'name: "autojs6OverloadUp"',
  'name: "autojs6OverloadDown"',
  "function scheduleLiveCompletion()",
  "enableLiveAutocompletion: false,",
  "arrowOverloadCycling",
  "signatureHelpController.setAutoTrigger(smartHintOptions.signatureTrigger)",
]) {
  assert(bridge.includes(expected), `Bridge is missing ${expected}`);
}
assert(css.includes(".autojs6_signature_tooltip_nav_button"), "Overload nav button CSS is missing");
assert(css.includes(".autojs6_signature_tooltip_nav_visible"), "Overload nav visibility CSS is missing");

const sourceCode = fs.readFileSync(SIGNATURE_HELP_PATH, "utf8");
assert(!sourceCode.includes("fetch("), "Signature help must not fetch resources");
assert(!sourceCode.includes("XMLHttpRequest"), "Signature help must not use XHR");
assert(!sourceCode.includes("eval("), "Signature help must not use eval");
assert(sourceCode.includes('"afterExec"'), "Signature help should listen to Ace afterExec events");
assert(sourceCode.includes("hideFromEditorPointer"), "Signature help should keep tooltip touch events visible");
assert(sourceCode.includes("isInsideTooltipEvent"), "Signature help should identify tooltip touch events");
assert(sourceCode.includes("autocompletePopupRect"), "Signature help should inspect autocomplete popup geometry");
assert(sourceCode.includes("rectsOverlap"), "Signature help should avoid overlapping autocomplete popup");
assert(sourceCode.includes("setCompactFallback"), "Signature help should support compact collision fallback");

console.log("ACE signature help tests passed.");
