module.exports = async function run() {
  const assert = require("assert");
  const first = require("first-dep");

  assert.strictEqual(first.value(), "first:second");
  return "PASS";
};
