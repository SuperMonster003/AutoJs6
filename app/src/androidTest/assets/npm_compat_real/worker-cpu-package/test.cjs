"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const cpu = require("cpu-worker-probe");

  const result = await cpu(5000);

  assert.equal(result.sum, 238887);
  console.log("realnpm.worker-cpu-package=cpu:" + result.sum);
  return "PASS";
};
