"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const pool = require("promise-pool-probe");

  const result = await pool([1, 2, 3, 4, 5]);

  assert.equal(result.summary, "concurrency:pool/55/5");
  console.log("realnpm.concurrency-pool-package=" + result.summary);
  return "PASS";
};
