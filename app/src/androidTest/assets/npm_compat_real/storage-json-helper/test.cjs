"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const storage = require("storage-json-helper-probe");
  const summary = storage.describe("phase10", { enabled: true, count: 3 });
  assert.equal(summary, "storage:phase10/26");
  console.log("realnpm.storage-json-helper=" + summary);
  return "PASS";
};
