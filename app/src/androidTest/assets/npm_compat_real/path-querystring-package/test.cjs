"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("path-querystring-probe");
  const summary = probe.summarize();

  assert.equal(summary, "pathquery:/root/b/file.txt:one two:a+b:true");
  console.log("realnpm.path-querystring=" + summary);
  return "PASS";
};
