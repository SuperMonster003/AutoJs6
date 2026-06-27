"nodejs";

const assert = require("assert/strict");

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

(async function main() {
  console.log("sample.desktop-parity-suite.smoke.catalog=2");
  await runEsmLoader();
  runNpmDependency();
  console.log("sample.desktop-parity-suite.smoke=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
