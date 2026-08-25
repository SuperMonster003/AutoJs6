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

function available(label, action) {
  try {
    action();
    console.log("sample.enabled." + label + "=AVAILABLE");
  } catch (error) {
    console.log("sample.enabled." + label + "=" + (error && (error.autojs6Code || error.code || error.name)));
    process.exitCode = 1;
  }
}

available("child_process", () => require("child_process"));
available("worker_threads", () => require("worker_threads"));
available("http", () => require("http"));
blocked("native_addon", () => require("./native.node"));
blocked("process_binding", () => process.binding("fs"));
available("process_chdir", () => process.chdir("/"));

console.log("sample.disabled-features-demo=PASS");
