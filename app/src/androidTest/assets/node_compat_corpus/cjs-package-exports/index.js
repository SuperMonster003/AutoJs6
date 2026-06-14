async function run() {
  const assert = require("assert");
  const self = require("cjs-package-exports");
  const feature = require("cjs-package-exports/feature");
  let blocked = false;

  try {
    require("cjs-package-exports/private");
  } catch (error) {
    blocked = error && error.code === "ERR_PACKAGE_PATH_NOT_EXPORTED";
  }

  assert.strictEqual(self.rootValue, "exports-root");
  assert.strictEqual(feature.value, "exports-feature");
  assert.strictEqual(blocked, true);
  return "PASS";
}

run.rootValue = "exports-root";
module.exports = run;
