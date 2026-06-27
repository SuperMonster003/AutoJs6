"nodejs";

const assert = require("assert/strict");

async function runUiLayout() {
  const ui = require("ui");
  const handle = await ui.showLayout({
    type: "Column",
    children: [
      { type: "Text", id: "title", text: "AutoJs6 Pro UI" },
      { type: "Button", id: "ok", text: "OK" }
    ]
  }, { timeoutMs: 5000, drainIntervalMs: 50 });
  try {
    assert.equal(typeof handle.close, "function");
    console.log("sample.pro-parity-suite.ui-layout.handle=true");
  } finally {
    await handle.close({ timeoutMs: 5000 });
  }
  console.log("sample.pro-parity-suite.ui-layout.closed=true");
  console.log("sample.pro-parity-suite.ui-layout=PASS");
}

async function runTasksWorkManager() {
  const work = require("work_manager");
  let taskId = null;
  try {
    taskId = await work.scheduleOnce({
      scriptPath: "main.cjs",
      delayMs: 60000,
      taskTimeoutMs: 5000,
      timeoutMs: 5000
    });
    assert.equal(typeof taskId, "string");
    assert.notEqual(taskId.length, 0);
    console.log("sample.pro-parity-suite.tasks-work-manager.scheduled=true");
  } finally {
    if (taskId) {
      await work.cancel(taskId, { timeoutMs: 5000 });
      console.log("sample.pro-parity-suite.tasks-work-manager.cancelled=true");
    }
  }
  console.log("sample.pro-parity-suite.tasks-work-manager=PASS");
}

(async function main() {
  console.log("sample.pro-parity-suite.smoke.catalog=2");
  await runUiLayout();
  await runTasksWorkManager();
  console.log("sample.pro-parity-suite.smoke=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
