package org.autojs.plugin.paddle.ocr.api

object PaddleOcrOptionExtraKeys {
    const val ENGINE_ID = "engineId"
    const val ENGINE = "engine"
    const val VARIANT = "variant"
    const val PROFILE = "profile"

    const val IMAGE_FORMAT = "imageFormat"
    const val IMAGE_QUALITY = "imageQuality"
    const val RAW_IMAGE = "rawImage"
    const val RAW_WIDTH = "rawWidth"
    const val RAW_HEIGHT = "rawHeight"
    const val RAW_STRIDE = "rawStride"
    const val RAW_CONFIG = "rawConfig"

    const val DET_LIMIT_SIDE_LEN = "detLimitSideLen"

    // Boolean, true when absent: whether the text line direction classifier (cls) runs between detection and recognition.
    // zh-CN: 布尔值, 缺省为 true: 是否在检测与识别之间运行文字方向分类 (cls).
    const val USE_CLS = "useCls"
}
