"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const helper = require("test-helper-v2-probe");
  const summary = await helper.run([
    () => true,
    async () => "ok",
  ]);
  assert.equal(summary, "tests:2/0/async");
  console.log("realnpm.test-helper-v2=" + summary);
  return "PASS";
};
