requestScreenCapture();
var capture = captureScreen();
var rows = ocr.recognizeText(capture, [0, 0, 400, 200]);
log(rows.join("\n"));
images.recycle(capture);
