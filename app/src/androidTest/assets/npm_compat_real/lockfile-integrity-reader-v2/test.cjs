"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("lockfile-integrity-v2-probe");
  const summary = probe.summarize(__dirname);

  assert.equal(summary, "lockv2:3:1:8");
  console.log("realnpm.lockfile-integrity-v2=" + summary);
  return "PASS";
};
