"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("pipeline-promise-probe");

  const result = await probe(["auto", "js", "6"]);

  assert.equal(result.text, "AUTO-JS-6");
  assert.equal(result.bytes, 9);
  console.log("realnpm.stream-promises=pipeline:" + result.text + ":" + result.bytes);
  return "PASS";
};
