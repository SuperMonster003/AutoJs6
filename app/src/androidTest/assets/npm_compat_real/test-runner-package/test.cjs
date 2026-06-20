"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const runner = require("mini-test-runner-probe");

  const suite = runner.createSuite();
  suite.test("sync add", () => assert.equal(2 + 2, 4));
  suite.test("match", () => assert.match("autojs6", /^auto/));
  suite.test("async", async () => {
    const value = await Promise.resolve("node");
    assert.equal(value, "node");
  });

  const result = await suite.run();

  assert.equal(result.passed, 3);
  assert.equal(result.failed, 0);
  console.log("realnpm.test-runner-package=tests:" + result.passed + "/" + result.total);
  return "PASS";
};
