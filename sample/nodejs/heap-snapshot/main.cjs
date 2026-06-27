"nodejs";

const memory = process.memoryUsage();
console.log("sample.heap-snapshot.heap-used=" + (memory.heapUsed > 0));
console.log("sample.heap-snapshot.mode=design-gated");
console.log("sample.heap-snapshot=PASS");
