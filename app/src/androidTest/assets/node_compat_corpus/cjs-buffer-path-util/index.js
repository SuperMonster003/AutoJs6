module.exports = async function run() {
  const assert = require("assert");
  const path = require("path");
  const util = require("util");
  const buffer = Buffer.from("buffer-path-util", "utf8");

  assert.strictEqual(buffer.toString("utf8"), "buffer-path-util");
  assert.strictEqual(path.join("a", "b", "..", "c"), "a/c");
  assert.strictEqual(util.inspect({ ok: true }), "{ ok: true }");
  return "PASS";
};
