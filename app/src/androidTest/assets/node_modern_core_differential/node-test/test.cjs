"use strict";

module.exports = async function run() {
  const nodeTest = require("node:test");
  const summary = [
    typeof nodeTest,
    nodeTest === nodeTest.test,
    typeof nodeTest.describe,
    typeof nodeTest.it,
    typeof nodeTest.before,
    typeof nodeTest.afterEach,
    typeof nodeTest.run,
    typeof nodeTest.mock,
    nodeTest.reporter || "",
    nodeTest.assert && typeof nodeTest.assert.strictEqual,
  ].join("|");
  console.log("moderncore.node-test=PASS|" + summary);
  return "PASS";
};
