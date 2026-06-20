"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("als-probe");

  const result = await probe();

  assert.deepEqual(result, ["root", "child", "timer", "promise"]);
  console.log("realnpm.async-context=als:" + result.join(":"));
  return "PASS";
};
