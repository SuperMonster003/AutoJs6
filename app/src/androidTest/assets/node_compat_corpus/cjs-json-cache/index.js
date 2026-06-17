module.exports = async function run() {
  const assert = require("assert");
  const first = require("./data.json");
  first.mutated = true;
  const second = require("./data.json");

  assert.strictEqual(first, second);
  assert.strictEqual(second.mutated, true);
  assert.deepStrictEqual(second.items, ["json", "cache"]);
  return "PASS";
};
