// Calls of the modules of roadmap P3.1 (images, Canvas, ocr, barcode, qrcode, yolo), appended to bench/calls_prelude.js by
// device_bench.py --calls images. They reach the modules of the script module layer (with its switch off they reached the
// old augment ones until P5.2). The "ok" cases work on an image drawn in the script (no screen capture) or only read
// state; the "fail" cases fail with an argument error on a missing or blank path before any image is read or captured,
// compared with the "ref" cases of the prelude. Nothing is written to the host settings.
let img = (function () {
    let b = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
    new android.graphics.Canvas(b).drawARGB(255, 255, 255, 255);
    return org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, b);
})();
time("ok images.pixel", () => { try { images.pixel(img, 4, 4); } catch (e) { } });
time("ok images.getSize", () => { try { images.getSize(img); } catch (e) { } });
time("ok images.isRecycled", () => { try { images.isRecycled(img); } catch (e) { } });
time("ok images.clip", () => { try { images.clip(img, 0, 0, 8, 8).recycle(); } catch (e) { } });
time("ok images.findColor", () => { try { images.findColor(img, "#ff0000"); } catch (e) { } });
time("ok ocr.mode", () => { try { ocr.mode; } catch (e) { } });
// Q28: the option fields and the region are checked before the current function reads them.
time("ok images.findColor-options", () => { try { images.findColor(img, "#ff0000", { region: [0, 0, 32, 32], threshold: 4 }); } catch (e) { } });
time("fail images.pixel-arity", () => { try { images.pixel(img); } catch (e) { } });
time("fail images.read-range", () => { try { images.read(""); } catch (e) { } });
time("fail images.clip-arity", () => { try { images.clip(img, 0, 0); } catch (e) { } });
time("fail images.threshold-range", () => { try { images.threshold(img, 100, 255, "XYZ"); } catch (e) { } });
time("fail canvas-range", () => { try { new Canvas(-1, 1); } catch (e) { } });
time("fail ocr.tap-arity", () => { try { ocr.tap(); } catch (e) { } });
time("fail ocr.tap-type", () => { try { ocr.tap(5); } catch (e) { } });
time("fail ocr-range", () => { try { ocr(" "); } catch (e) { } });
time("fail barcode-range", () => { try { barcode(""); } catch (e) { } });
time("fail qrcode-type", () => { try { qrcode("aj6-p31-missing.png", 5); } catch (e) { } });
time("fail yolo.load-arity", () => { try { yolo.load(); } catch (e) { } });
img.recycle();
