"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const lifecycle = require("autojs6:lifecycle");
    lifecycle.onStop(async (reason) => {
      await lifecycle.checkpoint({ stopped: true, reason }, { reason: "sample.stop", timeoutMs: 1000 });
    });
    console.log("sample.long-running-service.policy=" + lifecycle.policy.restartPolicy);
  } catch (error) {
    console.log("sample.long-running-service.lifecycle=" + codeOf(error));
  }

  await new Promise((resolve) => setTimeout(resolve, 25));
  console.log("sample.long-running-service=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
