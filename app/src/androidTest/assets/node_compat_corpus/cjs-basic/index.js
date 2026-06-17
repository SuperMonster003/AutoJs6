module.exports = async function run() {
  const assert = require("assert");
  const path = require("path");
  const util = require("util");

  assert.strictEqual(path.basename("/tmp/basic.txt"), "basic.txt");
  assert.strictEqual(util.format("%s:%d", "basic", 1), "basic:1");
  return "PASS";
};
