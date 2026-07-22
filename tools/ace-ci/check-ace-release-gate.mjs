#!/usr/bin/env node

import fs from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const STATIC_ONLY = process.argv.includes("--static-only");

const CURRENT_DOCS = [
  "docs/dev/ace-editor-integration.md",
  "docs/dev/ace-editor-test-plan.md",
  "docs/dev/ace-keyboard-viewport-test-plan.md",
  "docs/dev/ace-lsp-design.md",
  "docs/dev/ace-release-gate-status.md",
];

function read(relativePath) {
  return fs.readFileSync(path.join(PROJECT_ROOT, relativePath), "utf8").replaceAll("\r\n", "\n");
}

function assertIncludes(relativePath, text, label = text) {
  if (!read(relativePath).includes(text)) {
    throw new Error(`${relativePath}: missing ${label}`);
  }
}

function assertNotIncludes(relativePath, text, label = text) {
  if (read(relativePath).includes(text)) {
    throw new Error(`${relativePath}: obsolete ${label} must be removed`);
  }
}

function runHostGate() {
  const result = spawnSync(process.execPath, ["tools/ace-ci/check-ace-editor-gate.mjs"], {
    cwd: PROJECT_ROOT,
    stdio: "inherit",
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    process.exit(result.status || 1);
  }
}

if (!STATIC_ONLY) {
  runHostGate();
}

for (const doc of CURRENT_DOCS) {
  assertIncludes(doc, "plugin", "external plugin deployment boundary");
  assertNotIncludes(doc, "app/src/main/assets/editor/ace-builds-1.4.12", "host ACE asset path");
  assertNotIncludes(doc, "isolated ACE debug", "removed isolated ACE debug activity");
}

assertIncludes(
  "docs/dev/ace-editor-integration.md",
  "AutoJs6-Plugin-Ace-Editor",
  "separate plugin repository ownership",
);
assertIncludes(
  "docs/dev/ace-editor-test-plan.md",
  "AceEditorPluginHostDeviceTest",
  "cross-APK host smoke test",
);
assertIncludes(
  "docs/dev/ace-release-gate-status.md",
  "Editor API",
  "Editor API release boundary",
);

const englishChangelog = JSON.parse(read(".changelog/lang_en.json"));
const release = englishChangelog.$data?.["v6.8.0"];
if (!release || !/^\d{4}\/\d{2}\/\d{2}$/.test(release.released_date || "")) {
  throw new Error(".changelog/lang_en.json: v6.8.0 must have a YYYY/MM/DD release date");
}
const releaseText = [...(release.feature || []), ...(release.improvement || [])].join("\n");
if (!releaseText.includes("external plugin") || !releaseText.includes("Ace WebView runtime")) {
  throw new Error(".changelog/lang_en.json: missing ACE plugin extraction release notes");
}

console.log("Editor plugin release gate passed.");
