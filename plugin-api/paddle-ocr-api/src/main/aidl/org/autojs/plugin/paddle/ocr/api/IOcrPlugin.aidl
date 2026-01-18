package org.autojs.plugin.paddle.ocr.api;

import org.autojs.plugin.paddle.ocr.api.PluginInfo;
import org.autojs.plugin.paddle.ocr.api.OcrOptions;
import org.autojs.plugin.paddle.ocr.api.OcrResult;
import android.os.ParcelFileDescriptor;

interface IOcrPlugin {

    PluginInfo getInfo();

    List<String> recognizeText(in ParcelFileDescriptor imageFd, in OcrOptions options);

    List<OcrResult> detect(in ParcelFileDescriptor imageFd, in OcrOptions options);

}