"use strict";

module.exports = async function run() {
  const { AsyncLocalStorage } = require("async_hooks");
  const timers = require("timers/promises");
  const asyncLocalStorage = new AsyncLocalStorage();
  const propagated = await asyncLocalStorage.run({ id: "ctx" }, async () => {
    await timers.setTimeout(1);
    const nested = await Promise.resolve().then(() => asyncLocalStorage.getStore().id);
    return nested + ":" + asyncLocalStorage.getStore().id;
  });
  const cleaned = asyncLocalStorage.getStore() === undefined;
  console.log("moderncore.async-local-storage=PASS|" + [
    typeof AsyncLocalStorage,
    propagated,
    cleaned,
  ].join("|"));
  return "PASS";
};
