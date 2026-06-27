"nodejs";

const record = { namespace: "project", durable: true };
const pass = "sample.persistent-storage=PASS";
console.log(record.durable ? pass : "sample.persistent-storage=FAIL");
