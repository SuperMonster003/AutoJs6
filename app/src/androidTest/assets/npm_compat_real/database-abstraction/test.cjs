"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const createStore = require("tiny-db-abstraction-probe");

  const db = createStore.memory();
  await db.insert("items", { name: "alpha", value: 1 });
  await db.insert("items", { name: "beta", value: 2 });
  const rows = await db.select("items", (row) => row.value >= 1);
  const beta = await db.first("items", (row) => row.name === "beta");

  assert.equal(rows.length, 2);
  assert.equal(beta.value, 2);
  console.log("realnpm.database-abstraction=db:" + rows.length + "/" + beta.name);
  return "PASS";
};
