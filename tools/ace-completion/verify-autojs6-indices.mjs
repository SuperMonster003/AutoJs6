#!/usr/bin/env node

import fs from "node:fs";
import vm from "node:vm";
import {
  GENERATED_INDEX_PATH,
  buildRuntimeIndices,
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
  ? fs.readFileSync(GENERATED_INDEX_PATH, "utf8")
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

console.log("AutoJs6 ACE completion indices verified.");
