"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const bridge = require("require-esm-bridge");
  const direct = require("sync-esm-namespace");

  const bridged = bridge();

  assert.equal(direct.default.kind, "sync-esm");
  assert.equal(direct.named, "require-esm");
  assert.equal(bridged, "sync-esm/require-esm/cjs-bridge");
  console.log("realnpm.require-esm-hybrid=" + bridged);
  return "PASS";
};
