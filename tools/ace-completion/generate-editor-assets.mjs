#!/usr/bin/env node
import path from "node:path";
import { spawnSync } from "node:child_process";
import { EDITOR_PROJECT_ROOT, EDITOR_ASSET_ROOT, PROJECT_ROOT, TYPESCRIPT_RUNTIME } from "./indices-utils.mjs";

const check = process.argv.includes("--check");
if (process.argv.slice(2).some(argument => argument !== "--check")) {
  throw new Error("Usage: node tools/ace-completion/generate-editor-assets.mjs [--check]");
}
function run(script, args = [], cwd = PROJECT_ROOT) {
  const result = spawnSync(process.execPath, [script, ...args], { cwd, stdio: "inherit" });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
if (!check) {
  for (const script of ["generate-autojs6-indices-from-dts", "generate-autojs6-indices", "generate-dts"]) {
    run(`tools/ace-completion/${script}.mjs`);
  }
  run("tools/ace-lsp/generate-declarations.mjs", [
    "--source", path.join(EDITOR_ASSET_ROOT, "types"),
    "--output", path.join(EDITOR_PROJECT_ROOT, "app/build/generated/aceLspAssets"),
    "--typescript", TYPESCRIPT_RUNTIME,
  ], EDITOR_PROJECT_ROOT);
}
for (const script of ["verify-autojs6-api-model", "verify-autojs6-indices", "verify-autojs6-dts", "verify-autojs6-pro-only"]) {
  run(`tools/ace-completion/${script}.mjs`);
}
run("tools/ace-lsp/verify-api-coverage.mjs", [], EDITOR_PROJECT_ROOT);
