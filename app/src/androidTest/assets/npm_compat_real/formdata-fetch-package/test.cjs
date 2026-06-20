"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("formdata-fetch-probe");

  const result = await probe();

  assert.equal(result.ok, true);
  assert.match(result.summary, /^formdata:fetch\/alpha=1,beta=two\/ERR_AUTOJS6_/);
  console.log("realnpm.formdata-fetch-package=" + result.summary);
  return "PASS";
};
