package com.kevinluo.autoglm.voice

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 语音输入对话框
 *
 * 状态：
 * - RECORDING: 录音中，显示波形 + "正在聆听，请说话..."
 * - RECOGNIZING: 识别中，显示 "正在识别中..."
 * - NO_SPEECH: 未检测到语音，显示 "未检测到语音，请重试"
 * - RESULT: 识别结果，显示结果文字 + 倒计时确认
 */
class VoiceRecordingDialog(
    context: Context,
    private val voiceInputManager: VoiceInputManager,
    private val onResult: (VoiceRecognitionResult) -> Unit,
    private val onError: (VoiceError) -> Unit,
) : Dialog(context, R.style.VoiceRecordingDialogTheme) {
    companion object {
        private const val TAG = "VoiceRecordingDialog"
        private const val AUTO_CONFIRM_SECONDS = 5
    }

    /**
     * 对话框状态
     */
    private enum class State {
        RECORDING, // 录音中
        RECOGNIZING, // 识别中
        NO_SPEECH, // 未检测到语音
        RESULT, // 显示结果
    }

    // Views
    private lateinit var ivMic: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var waveformView: VoiceWaveformView
    private lateinit var progressBar: View
    private lateinit var tvResultText: TextView
    private lateinit var btnLeft: MaterialButton
    private lateinit var btnRight: MaterialButton

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentState = State.RECORDING
    private var countdownJob: Job? = null
    private var pendingResult: VoiceRecognitionResult? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_voice_recording)

        setupWindow()
        initViews()
        setupBackPressedCallback()

        // 开始录音
        setState(State.RECORDING)
        startRecording()
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL)

            val params = attributes
            params.width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.dimAmount = 0.5f
            attributes = params

            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    private fun setupBackPressedCallback() {
        val activity = context as? ComponentActivity ?: return

        backPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cleanup()
                    dismiss()
                }
            }

        activity.onBackPressedDispatcher.addCallback(backPressedCallback!!)
    }

    private fun initViews() {
        ivMic = findViewById(R.id.ivMicIcon)
        tvStatus = findViewById(R.id.tvStatus)
        waveformView = findViewById(R.id.waveformView)
        progressBar = findViewById(R.id.progressBar)
        tvResultText = findViewById(R.id.tvResultText)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)

        btnLeft.setOnClickListener { onLeftButtonClick() }
        btnRight.setOnClickListener { onRightButtonClick() }
    }

    /**
     * 左按钮点击（取消）
     */
    private fun onLeftButtonClick() {
        cleanup()
        dismiss()
    }

    /**
     * 右按钮点击（确认/重试）
     */
    private fun onRightButtonClick() {
        when (currentState) {
            State.RECORDING -> {
                // 停止录音，进入识别
                setState(State.RECOGNIZING)
                voiceInputManager.stopRecording()
            }

            State.NO_SPEECH -> {
                // 重试录音
                setState(State.RECORDING)
                startRecording()
            }

            State.RESULT -> {
                // 确认结果
                confirmResult()
            }

            State.RECOGNIZING -> {
                // 识别中，忽略点击
            }
        }
    }

    /**
     * 设置状态，更新 UI
     */
    private fun setState(state: State) {
        currentState = state

        when (state) {
            State.RECORDING -> {
                tvStatus.text = context.getString(R.string.voice_recording_hint)
                waveformView.visibility = View.VISIBLE
                waveformView.reset()
                progressBar.visibility = View.GONE
                tvResultText.visibility = View.GONE
                btnLeft.isEnabled = true
                btnLeft.text = context.getString(R.string.cancel)
                btnRight.isEnabled = true
                btnRight.text = context.getString(R.string.voice_confirm)
            }

            State.RECOGNIZING -> {
                tvStatus.text = context.getString(R.string.voice_recognizing_hint)
                waveformView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                tvResultText.visibility = View.GONE
                btnLeft.isEnabled = false
                btnRight.isEnabled = false
            }

            State.NO_SPEECH -> {
                tvStatus.text = context.getString(R.string.voice_no_speech_detected)
                waveformView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                tvResultText.visibility = View.GONE
                btnLeft.isEnabled = true
                btnLeft.text = context.getString(R.string.cancel)
                btnRight.isEnabled = true
                btnRight.text = context.getString(R.string.voice_retry)
            }

            State.RESULT -> {
                tvStatus.text = context.getString(R.string.voice_result_hint)
                waveformView.visibility = View.GONE
                progressBar.visibility = View.GONE
                tvResultText.visibility = View.VISIBLE
                setResultText(pendingResult?.text ?: "")
                btnLeft.isEnabled = true
                btnLeft.text = context.getString(R.string.cancel)
                btnRight.isEnabled = true
                // 倒计时会更新按钮文字
            }
        }
    }

    private fun startRecording() {
        voiceInputManager.setListener(
            object : VoiceInputListener {
                override fun onRecordingStarted() {
                    Logger.d(TAG, "Recording started")
                }

                override fun onRecordingStopped() {
                    // 状态由 setState 控制，这里不做处理
                }

                override fun onNoSpeechDetected() {
                    scope.launch {
                        setState(State.NO_SPEECH)
                    }
                }

                override fun onPartialResult(text: String) {
                    // Not used
                }

                override fun onFinalResult(result: VoiceRecognitionResult) {
                    scope.launch {
                        pendingResult = result
                        setState(State.RESULT)
                        startCountdown()
                    }
                }

                override fun onError(error: VoiceError) {
                    scope.launch {
                        cleanup()
                        dismiss()
                        onError(error)
                    }
                }

                override fun onAudioSamples(samples: ShortArray, readSize: Int) {
                    if (currentState == State.RECORDING) {
                        waveformView.updateFromSamples(samples, readSize)
                    }
                }
            },
        )

        voiceInputManager.startRecording(scope)
    }

    private fun startCountdown() {
        var secondsLeft = AUTO_CONFIRM_SECONDS
        updateConfirmButton(secondsLeft)

        countdownJob =
            scope.launch {
                while (isActive && secondsLeft > 0 && currentState == State.RESULT) {
                    delay(1000)
                    secondsLeft--
                    updateConfirmButton(secondsLeft)
                }

                if (isActive && secondsLeft == 0 && currentState == State.RESULT) {
                    confirmResult()
                }
            }
    }

    private fun updateConfirmButton(secondsLeft: Int) {
        btnRight.text =
            if (secondsLeft > 0) {
                context.getString(R.string.voice_confirm_with_countdown, secondsLeft)
            } else {
                context.getString(R.string.voice_confirm)
            }
    }

    /**
     * 设置结果文字，根据行数自动调整对齐方式
     * 单行居中，多行左对齐
     */
    private fun setResultText(text: String) {
        tvResultText.text = text
        tvResultText.post {
            val gravity =
                if (tvResultText.lineCount > 1) {
                    Gravity.START or Gravity.CENTER_VERTICAL
                } else {
                    Gravity.CENTER
                }
            tvResultText.gravity = gravity
        }
    }

    private fun confirmResult() {
        countdownJob?.cancel()
        val result = pendingResult
        pendingResult = null
        dismiss()

        if (result != null) {
            onResult(result)
        }
    }

    private fun cleanup() {
        countdownJob?.cancel()
        voiceInputManager.cancelRecording()
        pendingResult = null
    }

    override fun dismiss() {
        cleanup()
        scope.cancel()
        backPressedCallback?.remove()
        super.dismiss()
    }
}
