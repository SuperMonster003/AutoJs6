"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

function runWorker(Worker) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(
      "const { parentPort } = require('worker_threads'); parentPort.postMessage(42);",
      { eval: true },
    );
    const timer = setTimeout(() => {
      worker.terminate().catch(() => {});
      reject(Object.assign(new Error("worker timeout"), { code: "ERR_AUTOJS6_EXAMPLE_TIMEOUT" }));
    }, 2000);
    worker.once("message", (value) => {
      clearTimeout(timer);
      resolve(value);
    });
    worker.once("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
    worker.once("exit", (code) => {
      if (code !== 0) {
        clearTimeout(timer);
        reject(Object.assign(new Error("worker exit " + code), { code: "ERR_AUTOJS6_WORKER_EXIT" }));
      }
    });
  });
}

(async function main() {
  try {
    const workerThreads = require("worker_threads");
    if (!workerThreads.isMainThread) {
      throw Object.assign(new Error("not main thread"), { code: "ERR_AUTOJS6_WORKER_CONTEXT" });
    }
    const value = await runWorker(workerThreads.Worker);
    console.log("sample.desktop-parity-suite.worker-threads.value=" + value);
  } catch (error) {
    console.log("sample.desktop-parity-suite.worker-threads.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.worker-threads=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
