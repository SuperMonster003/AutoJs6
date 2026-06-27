"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const reader = require("lockfile-reader-probe");
  const summary = reader.summarize({
    packages: {
      "node_modules/a": { integrity: "sha512-a" },
      "node_modules/b": { integrity: "sha512-b" },
    },
  });
  assert.equal(summary, "lockfile:2/2");
  console.log("realnpm.lockfile-reader=" + summary);
  return "PASS";
};
