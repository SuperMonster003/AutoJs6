module.exports = async function run() {
  const assert = require("assert");
  const alias = require("#alias");
  let blocked = false;

  try {
    require("#blocked");
  } catch (error) {
    blocked = error && error.code === "ERR_PACKAGE_IMPORT_NOT_DEFINED";
  }

  assert.strictEqual(alias.value, "imports-alias");
  assert.strictEqual(blocked, true);
  return "PASS";
};
