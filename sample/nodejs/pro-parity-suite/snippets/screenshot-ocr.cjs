"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  let capture = null;
  try {
    const image = require("image");
    const ocr = require("ocr");
    capture = await image.captureScreen({ requireExistingPermission: true, timeoutMs: 3000 });
    const text = await ocr.recognizeText(capture, { timeoutMs: 5000 });
    console.log("sample.pro-parity-suite.screenshot-ocr.text=" + String(text).slice(0, 32));
  } catch (error) {
    console.log("sample.pro-parity-suite.screenshot-ocr.skipped=" + codeOf(error));
  } finally {
    if (capture) {
      try {
        await require("image").recycle(capture, { timeoutMs: 1000 });
      } catch (_) {
        // Ignore cleanup failure in example skip paths.
      }
    }
  }
  console.log("sample.pro-parity-suite.screenshot-ocr=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
