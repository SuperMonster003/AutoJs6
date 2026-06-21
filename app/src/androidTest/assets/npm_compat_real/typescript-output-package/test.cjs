"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("typescript-output-probe");
  const summary = probe.describe({ phase: 10, target: "commonjs" });
  assert.equal(summary, "ts-output:10/commonjs/declarations");
  console.log("realnpm.typescript-output-package=" + summary);
  return "PASS";
};
