package org.autojs.plugin.paddle.ocr.api;

import org.autojs.plugin.paddle.ocr.api.OcrOptions;
import org.autojs.plugin.paddle.ocr.api.OcrResult;
import android.os.ParcelFileDescriptor;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IOcrPlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    List<String> recognizeText(in ParcelFileDescriptor imageFd, in OcrOptions options);

    List<OcrResult> detect(in ParcelFileDescriptor imageFd, in OcrOptions options);
}
