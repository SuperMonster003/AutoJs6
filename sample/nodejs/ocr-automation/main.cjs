"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const image = require("image");
  const ocr = require("ocr");
  console.log("sample.ocr-automation.modules=" + [typeof image, typeof ocr.recognizeText].join("/"));

  try {
    await ocr.recognizeText({ id: "sample-image", width: 1, height: 1 }, { timeoutMs: 1000 });
    console.log("sample.ocr-automation.text=available");
  } catch (error) {
    console.log("sample.ocr-automation.unavailable=" + codeOf(error));
  }

  console.log("sample.ocr-automation=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
