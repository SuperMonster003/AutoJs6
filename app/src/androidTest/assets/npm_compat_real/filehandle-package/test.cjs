"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("filehandle-probe");

  const result = await probe("realnpm-filehandle.txt");

  assert.equal(result.ok, true);
  assert.equal(result.summary, "filehandle:open/write/read/close:21");
  console.log("realnpm.filehandle-package=" + result.summary);
  return "PASS";
};
