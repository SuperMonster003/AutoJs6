package org.autojs.plugin.paddle.ocr;

parcelable OcrResult {
    String text;
    float confidence;
    android.graphics.Rect bounds;
    android.os.Bundle extras;
}