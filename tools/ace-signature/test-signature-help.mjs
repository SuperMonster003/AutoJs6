#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
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
const editor = {
  session,
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
    return { row: 0, column: "Function.prototype.apply(this, ".length };
  },
};
const controller = api.createController({ editor, session, delayMs: 1 });
assert(controller.showAtPosition({ row: 0, column: "Function.prototype.apply(this, ".length }) === true, "Controller should show apply signature");
const state = controller.getState();
assert(state.visible === true, "Controller state should become visible");
assert(state.signature === "apply(thisArg: any, argArray?: any): any", `Unexpected state signature: ${state.signature}`);
assert(state.activeParameter === 1, `Unexpected active parameter: ${state.activeParameter}`);
controller.hide();
assert(controller.getState().visible === false, "Controller should hide signature help");

const html = fs.readFileSync(HTML_PATH, "utf8");
assert(
  html.indexOf("autojs6_signature_help.js") > html.indexOf("autojs6_tooltip.js") &&
    html.indexOf("autojs6_signature_help.js") < html.indexOf("autojs6_ace_bridge.js"),
  "Signature help should load after tooltip and before bridge",
);

const css = fs.readFileSync(CSS_PATH, "utf8");
assert(css.includes(".autojs6_signature_tooltip"), "Signature tooltip CSS is missing");
assert(css.includes("pointer-events: none"), "Signature tooltip must not steal touch input");

const bridge = fs.readFileSync(BRIDGE_PATH, "utf8");
for (const expected of [
  "var signatureHelpController = null;",
  "global.AutoJsAceSignatureHelp.install",
  "getSignatureHelp: function(row, column)",
  "showSignatureHelp: function(row, column)",
  "getSignatureHelpState: function()",
]) {
  assert(bridge.includes(expected), `Bridge is missing ${expected}`);
}

const sourceCode = fs.readFileSync(SIGNATURE_HELP_PATH, "utf8");
assert(!sourceCode.includes("fetch("), "Signature help must not fetch resources");
assert(!sourceCode.includes("XMLHttpRequest"), "Signature help must not use XHR");
assert(!sourceCode.includes("eval("), "Signature help must not use eval");

console.log("ACE signature help tests passed.");
