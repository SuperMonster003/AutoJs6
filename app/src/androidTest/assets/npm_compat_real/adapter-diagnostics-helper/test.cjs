"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const diagnostics = require("adapter-diagnostics-helper-probe");
  const summary = diagnostics.describe({
    adapter: "surface_only",
    defaultPath: "legacy_jni",
    code: "ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED",
  });
  assert.equal(summary, "adapter:surface_only/legacy_jni/blocked");
  console.log("realnpm.adapter-diagnostics-helper=" + summary);
  return "PASS";
};
