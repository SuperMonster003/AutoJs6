"nodejs";

(async function main() {
  const { showToast } = require("toast");
  const app = require("app");

  await showToast("Hello from AutoJs6 Node", {
    duration: "short",
    log: true,
    timeoutMs: 1000
  });

  console.log("sample.toast-app.package=" + app.packageName);
  console.log("sample.toast-app=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
