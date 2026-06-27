"nodejs";

const assert = require("assert/strict");
const fs = require("fs");
const fsp = require("fs/promises");

async function runEsmLoader() {
  const module = await import("./snippets/esm-fixture.mjs");
  assert.equal(module.label(), "managed-local-esm");
  assert.equal(module.answer, 42);
  console.log("sample.desktop-parity-suite.esm-loader.label=" + module.label());
  console.log("sample.desktop-parity-suite.esm-loader.answer=" + module.answer);
  console.log("sample.desktop-parity-suite.esm-loader=PASS");
}

function runNpmDependency() {
  const math = require("./vendor/pure-math");
  const sum = math.sum([8, 13, 21]);
  assert.equal(sum, 42);
  console.log("sample.desktop-parity-suite.npm-dependency.sum=" + sum);
  console.log("sample.desktop-parity-suite.npm-dependency=PASS");
}

async function runFsAdvanced() {
  const dir = "./desktop-parity-fs-smoke";
  const file = dir + "/out.txt";
  await fsp.mkdir(dir, { recursive: true });
  const handle = await fsp.open(file, "w+");
  try {
    await handle.writeFile("desktop-fs");
    await handle.sync();
    console.log("sample.desktop-parity-suite.fs-advanced.handle=true");
  } finally {
    await handle.close();
  }

  try {
    const watcher = fs.watch(dir, { persistent: false }, () => {});
    watcher.close();
    console.log("sample.desktop-parity-suite.fs-advanced.watch=true");
  } finally {
    await fsp.rm(dir, { recursive: true, force: true });
  }
  console.log("sample.desktop-parity-suite.fs-advanced=PASS");
}

(async function main() {
  console.log("sample.desktop-parity-suite.smoke.catalog=3");
  await runEsmLoader();
  runNpmDependency();
  await runFsAdvanced();
  console.log("sample.desktop-parity-suite.smoke=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
