package com.kevinluo.autoglm.voice

/**
 * Listener interface for voice input events.
 *
 * Allows UI components to receive updates about voice recording and recognition progress.
 */
interface VoiceInputListener {
    /** Called when recording starts. */
    fun onRecordingStarted()

    /** Called when recording stops. */
    fun onRecordingStopped()

    /**
     * Called when recording stops and no speech was detected.
     * UI should show retry option instead of "recognizing..."
     */
    fun onNoSpeechDetected() {}

    /** Called with partial recognition results during streaming recognition. */
    fun onPartialResult(text: String)

    /** Called with the final recognition result. */
    fun onFinalResult(result: VoiceRecognitionResult)

    /** Called when an error occurs. */
    fun onError(error: VoiceError)

    /** Called with audio samples for waveform display. */
    fun onAudioSamples(samples: ShortArray, readSize: Int) {}
}

/**
 * Listener interface for voice model download events.
 *
 * Provides callbacks for tracking download progress and completion status.
 */
interface VoiceModelDownloadListener {
    /** Called when download starts. */
    fun onDownloadStarted()

    /**
     * Called to report download progress.
     *
     * @param progress Progress percentage (0-100)
     * @param downloadedBytes Number of bytes downloaded
     * @param totalBytes Total file size in bytes
     */
    fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long)

    /**
     * Called when download completes successfully.
     *
     * @param modelPath Path to the downloaded model directory
     */
    fun onDownloadCompleted(modelPath: String)

    /**
     * Called when download fails.
     *
     * @param error Error message describing the failure
     */
    fun onDownloadFailed(error: String)

    /** Called when download is cancelled by user. */
    fun onDownloadCancelled()
}
