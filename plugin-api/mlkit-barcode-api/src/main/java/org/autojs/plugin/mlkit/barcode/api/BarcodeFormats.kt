package org.autojs.plugin.mlkit.barcode.api

object BarcodeFormats {
    const val FORMAT_UNKNOWN = -1
    const val FORMAT_ALL_FORMATS = 0
    const val FORMAT_CODE_128 = 1
    const val FORMAT_CODE_39 = 2
    const val FORMAT_CODE_93 = 4
    const val FORMAT_CODABAR = 8
    const val FORMAT_DATA_MATRIX = 16
    const val FORMAT_EAN_13 = 32
    const val FORMAT_EAN_8 = 64
    const val FORMAT_ITF = 128
    const val FORMAT_QR_CODE = 256
    const val FORMAT_UPC_A = 512
    const val FORMAT_UPC_E = 1024
    const val FORMAT_PDF417 = 2048
    const val FORMAT_AZTEC = 4096

    const val FORMAT_NAME_UNKNOWN = "FORMAT_NAME_UNKNOWN"

    val BY_NAME: Map<String, Int> = linkedMapOf(
        "ALL" to FORMAT_ALL_FORMATS,
        "AZTEC" to FORMAT_AZTEC,
        "CODABAR" to FORMAT_CODABAR,
        "CODE_39" to FORMAT_CODE_39,
        "CODE_93" to FORMAT_CODE_93,
        "CODE_128" to FORMAT_CODE_128,
        "DATA_MATRIX" to FORMAT_DATA_MATRIX,
        "EAN_8" to FORMAT_EAN_8,
        "EAN_13" to FORMAT_EAN_13,
        "ITF" to FORMAT_ITF,
        "PDF417" to FORMAT_PDF417,
        "PDF_417" to FORMAT_PDF417,
        "QRCODE" to FORMAT_QR_CODE,
        "QR_CODE" to FORMAT_QR_CODE,
        "ALL_FORMATS" to FORMAT_ALL_FORMATS,
        "UPC_A" to FORMAT_UPC_A,
        "UPC_E" to FORMAT_UPC_E,
    )

    val NAME_BY_VALUE: Map<Int, String> = BY_NAME
        .filterKeys { it != "ALL" }
        .entries
        .associate { (name, value) -> value to name }
}
