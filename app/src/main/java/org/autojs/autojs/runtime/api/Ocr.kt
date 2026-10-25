package org.autojs.autojs.runtime.api

import org.autojs.autojs.runtime.api.modules.ocr.Ocr

class Ocr {

    @JvmField
    @Volatile
    var mode = Ocr.Companion.OcrMode.AUTO

}
