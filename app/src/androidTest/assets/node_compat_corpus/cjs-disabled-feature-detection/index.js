module.exports = async function run() {
  const assert = require("assert");
  const profile = require("autojs6:profile");

  function codeOf(error) {
    return error && (error.autojs6Code || error.code || error.name);
  }
  function throwsCode(action, expected) {
    try {
      action();
    } catch (error) {
      return codeOf(error) === expected || error.code === expected || error.legacyCode === expected;
    }
    return false;
  }
  async function rejectsCode(action, expected) {
    try {
      await action();
    } catch (error) {
      return codeOf(error) === expected || error.code === expected || error.legacyCode === expected;
    }
    return false;
  }

  assert.strictEqual(profile.network, false);
  assert.strictEqual(profile.workerThreads, false);
  assert.strictEqual(profile.nativeAddon, false);
  assert.strictEqual(throwsCode(() => require("child_process"), "ERR_AUTOJS6_BUILTIN_DISABLED"), true);
  assert.strictEqual(throwsCode(() => require("worker_threads"), "ERR_AUTOJS6_BUILTIN_DISABLED"), true);
  assert.strictEqual(throwsCode(() => process.binding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED"), true);
  assert.strictEqual(await rejectsCode(() => fetch("https://example.invalid/"), "ERR_AUTOJS6_NETWORK_DISABLED"), true);
  return "PASS";
};
