"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const checker = require("security-policy-checker-probe");
  const summary = checker.describe(["child_process", "node-gyp", "addon.node"], ["fs", "path"]);
  assert.equal(summary, "security:blocked=3/allowed=2");
  console.log("realnpm.security-policy-checker=" + summary);
  return "PASS";
};
