package org.autojs.plugin.mlkit.barcode.api;

parcelable BarcodeResult {
    String rawValue;
    String displayValue;
    byte[] rawBytes;
    int format;
    String formatName;
    int valueType;
    String valueTypeName;
    android.graphics.Rect boundingBox;
    int[] cornerPoints;
    android.os.Bundle extras;
}
