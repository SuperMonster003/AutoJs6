"use strict";

function codeName(error) {
  return [error && error.name, error && error.code].join(":");
}

module.exports = async function run() {
  const timers = require("timers/promises");
  const timeoutValue = await timers.setTimeout(1, "timeout");
  const immediateValue = await timers.setImmediate("immediate");
  await timers.scheduler.wait(1);
  await timers.scheduler.yield();
  const controller = new AbortController();
  const abortValue = timers
    .setTimeout(50, "late", { signal: controller.signal })
    .then(() => "resolved", codeName);
  controller.abort("stop");
  console.log("moderncore.timers-promises=PASS|" + [
    timeoutValue,
    immediateValue,
    typeof timers.scheduler.wait,
    typeof timers.scheduler.yield,
    await abortValue,
  ].join("|"));
  return "PASS";
};
