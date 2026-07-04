package org.autojs.plugin.mlkit.barcode.api;

import org.autojs.plugin.mlkit.barcode.api.BarcodeOptions;
import org.autojs.plugin.mlkit.barcode.api.BarcodeResult;
import android.os.ParcelFileDescriptor;

parcelable org.autojs.plugin.common.api.PluginInfo;

interface IBarcodePlugin {

    org.autojs.plugin.common.api.PluginInfo getInfo();

    List<BarcodeResult> detect(in ParcelFileDescriptor imageFd, in BarcodeOptions options);
}
