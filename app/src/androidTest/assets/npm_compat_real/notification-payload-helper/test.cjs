"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const notifications = require("notification-payload-helper-probe");
  const summary = notifications.describe({ channel: "node", ongoing: true, permission: "runtime" });
  assert.equal(summary, "notification:node/ongoing/runtime");
  console.log("realnpm.notification-payload-helper=" + summary);
  return "PASS";
};
