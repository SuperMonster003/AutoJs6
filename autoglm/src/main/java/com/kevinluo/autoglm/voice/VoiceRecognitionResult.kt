package com.kevinluo.autoglm.voice

/**
 * Result of a voice recognition operation.
 *
 * @property text The recognized text
 * @property confidence Confidence score of the recognition (0.0 to 1.0)
 * @property language Detected language code or "auto" for automatic detection
 * @property durationMs Duration of the recognition process in milliseconds
 */
data class VoiceRecognitionResult(
    val text: String,
    val confidence: Float = 1.0f,
    val language: String = "auto",
    val durationMs: Long = 0,
)
