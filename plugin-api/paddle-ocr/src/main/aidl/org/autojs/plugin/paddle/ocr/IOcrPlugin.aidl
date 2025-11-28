package org.autojs.plugin.paddle.ocr;

import org.autojs.plugin.paddle.ocr.PluginInfo;
import org.autojs.plugin.paddle.ocr.OcrOptions;
import org.autojs.plugin.paddle.ocr.OcrResult;
import android.os.ParcelFileDescriptor;

interface IOcrPlugin {

    PluginInfo getInfo();

    List<String> recognizeText(in ParcelFileDescriptor imageFd, in OcrOptions options);

    List<OcrResult> detect(in ParcelFileDescriptor imageFd, in OcrOptions options);

}