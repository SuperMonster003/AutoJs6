"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");

  const esmOnly = await import("esm-only-small");
  const hybridImport = await import("hybrid-flags");
  const hybridRequire = require("hybrid-flags");

  assert.equal(esmOnly.default.label, "esm-only");
  assert.equal(esmOnly.named, "named");
  assert.equal(hybridImport.default.mode, "esm");
  assert.equal(hybridImport.named, "import");
  assert.equal(hybridRequire.mode, "cjs");
  assert.equal(hybridRequire.named, "require");

  console.log([
    "realnpm.esm-hybrid-packages=" + esmOnly.default.label + "/" + esmOnly.named,
    hybridImport.default.mode + "/" + hybridImport.named,
    hybridRequire.mode + "/" + hybridRequire.named,
  ].join("|"));
  return "PASS";
};
