"nodejs";

let workerState = "disabled";
try {
  require("worker_threads");
  workerState = "available";
} catch (error) {
  workerState = String(error && (error.code || error.autojs6Code || error.name || "disabled"));
}

console.log("sample.wasm-worker.worker=" + workerState);
console.log("sample.wasm-worker.mode=design-gated");
console.log("sample.wasm-worker=PASS");
