#!/usr/bin/env node

import fs from "node:fs";
import vm from "node:vm";
import {
  GENERATED_INDEX_PATH,
  buildRuntimeIndices,
  normalizeLineEndings,
  readSource,
  relativeToRoot,
  renderIndexJs,
  validateSource,
} from "./indices-utils.mjs";

const source = readSource();
const errors = validateSource(source);
if (errors.length > 0) {
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

const expected = renderIndexJs(buildRuntimeIndices(source));
const actual = fs.existsSync(GENERATED_INDEX_PATH)
  ? normalizeLineEndings(fs.readFileSync(GENERATED_INDEX_PATH, "utf8"))
  : "";
if (actual !== expected) {
  console.error(`${relativeToRoot(GENERATED_INDEX_PATH)} is stale. Run node tools/ace-completion/generate-autojs6-indices.mjs.`);
  process.exit(1);
}

const sandbox = {
  window: {},
};
vm.createContext(sandbox);
vm.runInContext(actual, sandbox, { filename: GENERATED_INDEX_PATH });

const indices = sandbox.window.AUTOJS6_INDICES;
const requiredGlobals = ["toast", "app", "files", "images", "device", "console", "threads", "engines"];
const globalNames = new Set((indices.globals || []).map((entry) => entry.name));
for (const name of requiredGlobals) {
  if (!globalNames.has(name)) {
    console.error(`Missing required global completion: ${name}`);
    process.exit(1);
  }
}

const requiredMembers = {
  app: "launch",
  files: "read",
  images: "findImage",
  device: "getBattery",
  console: "log",
  threads: "start",
  engines: "myEngine",
};
for (const [moduleName, memberName] of Object.entries(requiredMembers)) {
  const members = (indices.modules && indices.modules[moduleName] || []).map((entry) => entry.name);
  if (!members.includes(memberName)) {
    console.error(`Missing required member completion: ${moduleName}.${memberName}`);
    process.exit(1);
  }
}

const requiredSignatures = {
  "app.launch": "launch(app: App.Preset | App.Alias | App.PackageName): boolean;",
  "files.read": "read(path: string, encoding?: StandardCharsets): string;",
};
for (const [qualifiedName, signature] of Object.entries(requiredSignatures)) {
  const [moduleName, memberName] = qualifiedName.split(".");
  const member = (indices.modules && indices.modules[moduleName] || [])
    .find((entry) => entry.name === memberName);
  if (!member || member.signature !== signature) {
    console.error(`Missing required member signature: ${qualifiedName} -> ${signature}`);
    process.exit(1);
  }
}

const launchGlobal = (indices.globals || []).find((entry) => entry.name === "launch" && entry.type === "function");
if (!launchGlobal || launchGlobal.signature !== requiredSignatures["app.launch"]) {
  console.error("Global launch completion did not preserve declaration-backed signature.");
  process.exit(1);
}

console.log("AutoJs6 ACE completion indices verified.");
