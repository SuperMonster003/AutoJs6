"use strict";

module.exports = async function run() {
  const mod = await import("./value.mjs");
  const ok =
    mod.default === "esm-default" &&
    mod.named === 41 &&
    mod.tla === "tla-ok" &&
    mod.twice(2) === 4;
  if (!ok) {
    throw new Error("ESM partial probe failed");
  }
  console.log("moderncore.esm=PASS|dynamic-import-local|default,named,tla");
  return "PASS";
};
