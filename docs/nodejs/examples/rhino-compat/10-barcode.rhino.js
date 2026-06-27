var mixed = barcode.detectAll("fixtures/barcodes.png", {
  formats: ["QR_CODE", "EAN_13"],
  maxResults: 8,
});

var image = images.read("fixtures/qrcode.png");
try {
  var firstText = barcode.recognizeText(image, [0, 0, 500, 500]);
  var qrOnly = qrcode.detectAll(image, {
    maxResults: 4,
  });

  log(JSON.stringify({
    mixedCount: mixed.length,
    firstText: firstText,
    qrOnlyCount: qrOnly.length,
  }));
} finally {
  images.recycle(image);
}
