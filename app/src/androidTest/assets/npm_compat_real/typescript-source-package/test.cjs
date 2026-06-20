"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("ts-source-probe");

  const result = probe({ label: "autojs", count: 6 });

  assert.equal(result.ok, true);
  assert.equal(result.summary, "ts:source/autojs/6/12");
  console.log("realnpm.typescript-source-package=" + result.summary);
  return "PASS";
};
