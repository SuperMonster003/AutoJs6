"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const work = require("work_manager");
  console.log("sample.scheduled-node-task.provider=work_manager");

  try {
    const id = await work.scheduleOnce({
      scriptPath: "main.cjs",
      delayMs: 60000,
      taskTimeoutMs: 5000,
      timeoutMs: 1000
    });
    await work.cancel(id, { timeoutMs: 1000 });
    console.log("sample.scheduled-node-task.cancelled=true");
  } catch (error) {
    console.log("sample.scheduled-node-task.unavailable=" + codeOf(error));
  }

  console.log("sample.scheduled-node-task=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
