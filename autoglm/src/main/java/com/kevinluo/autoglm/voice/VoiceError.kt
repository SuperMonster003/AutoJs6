package com.kevinluo.autoglm.voice

/**
 * Voice feature error types.
 *
 * Represents various error conditions that can occur during voice input operations.
 */
sealed class VoiceError(val message: String) {
    /** Microphone permission was denied. */
    object PermissionDenied : VoiceError("麦克风权限被拒绝")

    /** Voice model has not been downloaded. */
    object ModelNotDownloaded : VoiceError("语音模型未下载")

    /** Failed to load the voice model. */
    object ModelLoadFailed : VoiceError("语音模型加载失败")

    /** Audio recording failed. */
    object RecordingFailed : VoiceError("录音失败")

    /** Speech recognition failed. */
    object RecognitionFailed : VoiceError("识别失败")

    /** Network error during model download. */
    object NetworkError : VoiceError("网络错误")

    /** Unknown error with custom message. */
    data class Unknown(val errorMessage: String) : VoiceError(errorMessage)
}
