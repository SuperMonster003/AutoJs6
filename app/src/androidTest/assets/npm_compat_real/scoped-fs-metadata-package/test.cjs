"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("scoped-fs-metadata-probe");
  const summary = probe.summarize(__dirname);

  assert.equal(summary, "fsmeta:scoped:12");
  console.log("realnpm.scoped-fs-metadata=" + summary);
  return "PASS";
};
