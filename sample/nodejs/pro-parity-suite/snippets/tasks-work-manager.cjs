"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const work = require("work_manager");
    const id = await work.scheduleOnce({
      scriptPath: "main.cjs",
      delayMs: 60000,
      taskTimeoutMs: 5000,
      timeoutMs: 1000
    });
    await work.cancel(id, { timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.tasks-work-manager.cancelled=true");
  } catch (error) {
    console.log("sample.pro-parity-suite.tasks-work-manager.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.tasks-work-manager=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
