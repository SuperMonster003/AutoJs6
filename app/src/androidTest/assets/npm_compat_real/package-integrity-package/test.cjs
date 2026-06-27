"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("package-integrity-probe");

  const result = probe();

  assert.equal(result.summary, "integrity:sha256/12/true");
  console.log("realnpm.package-integrity-package=" + result.summary);
  return "PASS";
};
