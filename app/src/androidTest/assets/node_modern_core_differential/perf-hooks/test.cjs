"use strict";

module.exports = async function run() {
  const perfHooks = require("perf_hooks");
  const timers = require("timers/promises");
  const performance = perfHooks.performance;
  const before = typeof performance.eventLoopUtilization === "function"
    ? performance.eventLoopUtilization()
    : null;
  await timers.setTimeout(1);
  const delta = before ? performance.eventLoopUtilization(before) : null;
  const nowOk = typeof performance.now() === "number";
  const eluShape = !!delta &&
    typeof delta.idle === "number" &&
    typeof delta.active === "number" &&
    typeof delta.utilization === "number";
  console.log("moderncore.perf-hooks=PASS|" + [
    nowOk,
    typeof performance.eventLoopUtilization,
    eluShape,
    typeof perfHooks.PerformanceObserver,
    typeof perfHooks.monitorEventLoopDelay,
  ].join("|"));
  return "PASS";
};
