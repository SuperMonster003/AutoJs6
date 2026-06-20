"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const fetch = require("fetch");
  console.log("sample.live-fetch.provider=controlled-fetch");
  console.log("sample.live-fetch.limit=" + fetch.policy.defaultMaxResponseBytes);

  try {
    const response = await fetch("https://example.invalid/sample", {
      timeoutMs: 1000,
      maxResponseBytes: 2048
    });
    console.log("sample.live-fetch.status=" + response.status);
  } catch (error) {
    console.log("sample.live-fetch.unavailable=" + codeOf(error));
  }

  console.log("sample.live-fetch=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
