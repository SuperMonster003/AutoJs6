"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("agent-options-wrapper");

  const summary = await client.probe();
  assert.equal(summary, "httpnpm.agent-options=true|2|3|agent.example.invalid:8080::|secure.example.invalid:9443::|200|202");
  return summary;
};
