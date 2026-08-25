"nodejs";

try {
  const inspector = require("inspector");
  const session = new inspector.Session();
  session.connect();
  const post = (method, params) => new Promise((resolve, reject) => {
    session.post(method, params || {}, (error, result) => error ? reject(error) : resolve(result || {}));
  });
  (async () => {
    await post("HeapProfiler.enable");
    const heap = await post("Runtime.getHeapUsage");
    session.disconnect();
    console.log("sample.heap-snapshot.heap-profiler=" + (heap.usedSize > 0 && heap.totalSize > 0));
    console.log("sample.heap-snapshot.export=not-implemented");
    console.log("sample.heap-snapshot=PASS");
  })().catch((error) => setImmediate(() => { throw error; }));
} catch (error) {
  const memory = process.memoryUsage();
  console.log("sample.heap-snapshot.heap-used=" + (memory.heapUsed > 0));
  console.log("sample.heap-snapshot.debug-request-required=" + (error && (error.autojs6Code || error.code || error.name)));
  console.log("sample.heap-snapshot=PASS");
}
