async function run() {
  const assert = require("assert");
  const self = require("cjs-self-reference");
  const feature = require("cjs-self-reference/feature");

  assert.strictEqual(self.rootValue, "self-root");
  assert.strictEqual(feature.value, "self-feature");
  return "PASS";
}

run.rootValue = "self-root";
module.exports = run;
