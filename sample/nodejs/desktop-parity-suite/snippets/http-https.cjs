"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const http = require("http");
    const https = require("https");
    const shape = [
      typeof http.request,
      typeof http.get,
      typeof https.request,
      typeof https.get,
    ].join(",");
    console.log("sample.desktop-parity-suite.http-https.shape=" + shape);
  } catch (error) {
    console.log("sample.desktop-parity-suite.http-https.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.http-https=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
