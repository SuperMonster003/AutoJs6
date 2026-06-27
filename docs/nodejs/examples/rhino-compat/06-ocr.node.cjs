"nodejs";

const compat = require("autojs6:compat");

async function main() {
  await compat.requestScreenCapture({ orientation: "auto", timeoutMs: 5000 });
  const capture = await compat.captureScreen({ timeoutMs: 5000 });
  try {
    const rows = await compat.ocr.recognizeText(capture, { region: [0, 0, 400, 200] });
    console.log(rows.join("\n"));
  } finally {
    await compat.images.recycle(capture);
  }
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
