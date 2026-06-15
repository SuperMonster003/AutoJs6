#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

const CHECKS = [
  {
    label: "Editor boundary checks",
    script: "tools/editor-refactor/check-editor-boundaries.mjs",
  },
  {
    label: "ACE surface hardening checks",
    script: "tools/ace-hardening/check-editor-surface.mjs",
  },
  {
    label: "AutoJs6 API model verification",
    script: "tools/ace-completion/verify-autojs6-api-model.mjs",
  },
  {
    label: "AutoJs6 Pro-only API detection",
    script: "tools/ace-completion/verify-autojs6-pro-only.mjs",
  },
  {
    label: "AutoJs6 completion index verification",
    script: "tools/ace-completion/verify-autojs6-indices.mjs",
  },
  {
    label: "AutoJs6 generated d.ts verification",
    script: "tools/ace-completion/verify-autojs6-dts.mjs",
  },
  {
    label: "ACE completer tests",
    script: "tools/ace-completion/test-completer.mjs",
  },
  {
    label: "ACE LSP client tests",
    script: "tools/ace-lsp/test-lsp-client.mjs",
  },
  {
    label: "ACE tooltip tests",
    script: "tools/ace-tooltip/test-tooltip.mjs",
  },
];

for (const check of CHECKS) {
  console.log(`\n[ACE gate] ${check.label}`);
  const result = spawnSync(process.execPath, [check.script], {
    cwd: PROJECT_ROOT,
    stdio: "inherit",
  });
  if (result.error) {
    console.error(`[ACE gate] Failed to start ${check.script}: ${result.error.message}`);
    process.exit(1);
  }
  if (result.status !== 0) {
    console.error(`[ACE gate] ${check.label} failed with exit code ${result.status ?? "unknown"}.`);
    process.exit(result.status || 1);
  }
}

console.log("\nACE editor CI gate passed.");
