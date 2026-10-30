package org.autojs.plugin.paddle.ocr.onnx.engine

/** Execution provider selection; `OcrOptions.useOpenCL` maps to NNAPI in the ONNX Runtime build. */
object ExecutionProviders {
    const val CPU = "cpu"
    const val NNAPI = "nnapi"

    /** ONNX Runtime's NNAPI execution provider needs Android 8.1 (API 27). */
    const val NNAPI_MIN_SDK = 27

    fun shouldRequestNnapi(useNnapi: Boolean, sdkInt: Int): Boolean = useNnapi && sdkInt >= NNAPI_MIN_SDK
}
