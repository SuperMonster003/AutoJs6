"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const mixed = await compat.barcode.detectAll("fixtures/barcodes.png", {
    formats: ["QR_CODE", "EAN_13"],
    maxResults: 8,
    timeoutMs: 10_000,
  });

  const image = await compat.images.read("fixtures/qrcode.png", { timeoutMs: 10_000 });
  try {
    const firstText = await compat.barcode.recognizeText(image, {
      region: [0, 0, 500, 500],
      timeoutMs: 10_000,
    });
    const qrOnly = await compat.qrcode.detectAll(image, {
      maxResults: 4,
      timeoutMs: 10_000,
    });

    console.log(JSON.stringify({
      mixedCount: mixed.length,
      firstText,
      qrOnlyCount: qrOnly.length,
      qrFormats: qrOnly.map((item) => item.formatName),
    }));
  } finally {
    await compat.images.recycle(image);
  }
}

if (require.main === module && process.env.AUTOJS6_RUN_BARCODE_EXAMPLE === "1") {
  main().catch((error) => {
    console.error(error && (error.stack || error.message) || error);
    process.exitCode = 1;
  });
}
