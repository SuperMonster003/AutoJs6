"nodejs";

const path = require("path");

function cpu(limit) {
  let sum = 0;
  for (let value = 2; value <= limit; value += 1) {
    let prime = true;
    for (let factor = 2; factor * factor <= value; factor += 1) {
      if (value % factor === 0) {
        prime = false;
        break;
      }
    }
    if (prime) sum += value;
  }
  return sum;
}

function runWorker(limit) {
  try {
    const { Worker } = require("worker_threads");
    return new Promise((resolve, reject) => {
      const worker = new Worker(path.join(__dirname, "worker.cjs"), { workerData: { limit } });
      worker.once("message", (message) => resolve({ mode: "worker", sum: message.sum }));
      worker.once("error", reject);
      worker.once("exit", (code) => {
        if (code !== 0) reject(new Error("Worker exited " + code));
      });
    });
  } catch (error) {
    return Promise.resolve({ mode: "fallback", sum: cpu(limit) });
  }
}

(async function main() {
  const result = await runWorker(5000);
  console.log("sample.worker-cpu.mode=" + result.mode);
  console.log("sample.worker-cpu.sum=" + result.sum);
  console.log("sample.worker-cpu=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
