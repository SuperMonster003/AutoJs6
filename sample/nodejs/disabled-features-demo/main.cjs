"nodejs";

function blocked(label, action) {
  try {
    action();
    console.log("sample.disabled." + label + "=UNEXPECTED_ALLOWED");
    process.exitCode = 1;
  } catch (error) {
    console.log("sample.disabled." + label + "=" + (error && (error.autojs6Code || error.code || error.name)));
  }
}

blocked("child_process", () => require("child_process"));
blocked("worker_threads", () => require("worker_threads"));
blocked("http", () => require("http"));
blocked("native_addon", () => require("./native.node"));
blocked("process_binding", () => process.binding("fs"));
blocked("process_chdir", () => process.chdir("/"));

console.log("sample.disabled-features-demo=PASS");
