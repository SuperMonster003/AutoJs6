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
const TOOLTIP_PATH = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "assets",
  "editor",
  "ace-builds-1.4.12",
  "autojs6",
  "autojs6_tooltip.js",
);

function createElement(tagName) {
  return {
    tagName,
    className: "",
    style: {},
    children: [],
    parentNode: null,
    offsetWidth: 280,
    offsetHeight: 96,
    _textContent: "",
    _innerHTML: "",
    appendChild(child) {
      child.parentNode = this;
      this.children.push(child);
      return child;
    },
    removeChild(child) {
      this.children = this.children.filter((item) => item !== child);
      child.parentNode = null;
    },
    set textContent(value) {
      this._textContent = String(value || "");
    },
    get textContent() {
      return this._textContent;
    },
    set innerHTML(value) {
      this._innerHTML = String(value || "");
    },
    get innerHTML() {
      return this._innerHTML;
    },
  };
}

function createSandbox() {
  const body = createElement("body");
  const sandbox = {
    window: {
      innerWidth: 360,
      innerHeight: 240,
      pageXOffset: 0,
      pageYOffset: 0,
      document: {
        body,
        documentElement: {
          clientWidth: 360,
          clientHeight: 240,
        },
        createElement,
      },
    },
    console,
    setTimeout,
    clearTimeout,
  };
  sandbox.window.window = sandbox.window;
  vm.createContext(sandbox);
  return sandbox;
}

function runTooltip(sandbox) {
  vm.runInContext(fs.readFileSync(TOOLTIP_PATH, "utf8"), sandbox, { filename: TOOLTIP_PATH });
  return sandbox.window.AutoJsAceTooltip;
}

function createEditor() {
  const handlers = new Map();
  const container = {
    listeners: new Map(),
    addEventListener(name, handler) {
      this.listeners.set(name, handler);
    },
    removeEventListener(name) {
      this.listeners.delete(name);
    },
  };
  return {
    container,
    renderer: {
      textToScreenCoordinates(row, column) {
        return {
          pageX: 60 + column * 8,
          pageY: 80 + row * 16,
        };
      },
    },
    on(name, handler) {
      handlers.set(name, handler);
    },
    off(name) {
      handlers.delete(name);
    },
    handlers,
  };
}

function createSession() {
  const handlers = new Map();
  return {
    on(name, handler) {
      handlers.set(name, handler);
    },
    off(name) {
      handlers.delete(name);
    },
    handlers,
  };
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

const sandbox = createSandbox();
const tooltip = runTooltip(sandbox);

const rendered = tooltip.renderMarkdown([
  "# Heading",
  "",
  "- uses `code`",
  "- keeps **bold**",
  "",
  "```js",
  "<script>alert(1)</script>",
  "```",
].join("\n"));

assert(rendered.includes("autojs6_hover_tooltip_heading"), "Markdown heading should render");
assert(rendered.includes("<li>uses <code>code</code></li>"), "Markdown list and inline code should render");
assert(rendered.includes("<strong>bold</strong>"), "Markdown bold should render");
assert(rendered.includes("&lt;script&gt;alert(1)&lt;/script&gt;"), "Markdown renderer must escape HTML");
assert(!rendered.includes("<script>"), "Markdown renderer must not emit raw script tags");

const editor = createEditor();
const session = createSession();
const controller = tooltip.createController({
  editor,
  session,
  hoverDelayMs: 1,
  getHover(pos) {
    if (pos.row !== 0) {
      return null;
    }
    return {
      caption: "app.launch",
      docText: "launch(app: App | App.Alias | App.PackageName): boolean;\n\n- static fallback",
      range: {
        start: { row: 0, column: 4 },
        end: { row: 0, column: 10 },
      },
    };
  },
});

assert(controller.getState().attached === true, "Tooltip controller should attach to editor events");
assert(controller.getState().domCreated === false, "Tooltip DOM should be lazy");
assert(controller.getState().markdownLoaded === false, "Markdown renderer should be lazy");

assert(controller.showAtPosition({ row: 0, column: 6 }) === true, "Tooltip should show hover docs");
const stateAfterShow = controller.getState();
assert(stateAfterShow.visible === true, "Tooltip should be visible after show");
assert(stateAfterShow.domCreated === true, "Tooltip DOM should be created after show");
assert(stateAfterShow.markdownRenderer === "safe-subset", "Tooltip should use safe markdown renderer");
assert(sandbox.window.document.body.children.length === 1, "Tooltip should append one DOM element");

const tooltipElement = sandbox.window.document.body.children[0];
assert(tooltipElement.children[0].textContent === "app.launch", "Tooltip caption should use hover caption");
assert(
  tooltipElement.children[1].innerHTML.includes("static fallback"),
  "Tooltip body should render hover markdown content",
);
assert(tooltipElement.style.left.endsWith("px"), "Tooltip should set a clamped left position");
assert(tooltipElement.style.top.endsWith("px"), "Tooltip should set a clamped top position");

controller.hide();
assert(controller.getState().visible === false, "Tooltip should hide");
assert(tooltipElement.style.display === "none", "Tooltip display should be none after hide");

const tooltipSource = fs.readFileSync(TOOLTIP_PATH, "utf8");
assert(!tooltipSource.includes("fetch("), "Tooltip must not fetch markdown resources");
assert(!tooltipSource.includes("XMLHttpRequest"), "Tooltip must not use XHR");
assert(!tooltipSource.includes("innerHTML = hover.docText"), "Tooltip must not assign raw hover docs as HTML");

console.log("ACE tooltip tests passed.");
