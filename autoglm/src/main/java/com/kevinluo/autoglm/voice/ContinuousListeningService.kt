package com.kevinluo.autoglm.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kevinluo.autoglm.MainActivity
import com.kevinluo.autoglm.R
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.util.KeepAliveManager
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 后台持续监听服务
 *
 * 在后台持续监听语音，检测唤醒词后触发相应操作
 *
 * 状态机设计：
 * ```
 * 状态转换图:
 *
 *                    ┌─────────────────────────────────────┐
 *                    │                                     │
 *                    ▼                                     │
 *   ┌──────┐    ┌──────────┐    ┌───────────┐             │
 *   │ IDLE │───▶│ STARTING │───▶│ LISTENING │─────────────┤
 *   └──────┘    └──────────┘    └───────────┘             │
 *       ▲            ▲               │  │                 │
 *       │            │               │  │                 │
 *       │            │    ┌──────────┘  └──────────┐      │
 *       │            │    ▼                        ▼      │
 *       │      ┌─────────────────┐    ┌───────────────────┐
 *       │      │ PAUSED_BY_USER  │    │PAUSED_BY_SCREEN_OFF│
 *       │      └─────────────────┘    └───────────────────┘
 *       │                                                 │
 *       │                                                 │
 *       │         ┌─────────┐                             │
 *       └─────────│ STOPPED │◀────────────────────────────┘
 *                 └─────────┘
 *
 * 转换规则:
 * - IDLE/STOPPED/PAUSED_* -> STARTING: 调用 startListening()
 * - STARTING -> LISTENING: 初始化完成，开始录音
 * - STARTING -> STOPPED: 初始化失败
 * - STARTING -> PAUSED_BY_SCREEN_OFF: 屏幕关闭时启动
 * - LISTENING -> PAUSED_BY_USER: VoiceInputManager 暂停
 * - LISTENING -> PAUSED_BY_SCREEN_OFF: 屏幕关闭
 * - PAUSED_BY_USER -> STARTING: VoiceInputManager 恢复
 * - PAUSED_BY_SCREEN_OFF -> STARTING: 屏幕打开
 * - 任意状态 -> STOPPED: 调用 stopListening()
 * ```
 *
 * 性能优化点：
 * - 电量优化（降低采样率选项、智能休眠）
 * - 优化内存使用
 * - 性能监控日志
 * - 自适应处理间隔
 */
class ContinuousListeningService : Service() {
    /**
     * 服务状态枚举
     */
    enum class State {
        IDLE, // 服务已创建但未开始监听
        STARTING, // 正在初始化（中间状态）
        LISTENING, // 正在监听
        PAUSED_BY_USER, // 被 VoiceInputManager 暂停
        PAUSED_BY_SCREEN_OFF, // 被屏幕关闭暂停
        STOPPED, // 服务已停止
    }

    companion object {
        private const val TAG = "ContinuousListening"
        private const val CHANNEL_ID = "continuous_listening"
        private const val NOTIFICATION_ID = 2001

        // 标准采样率
        private const val SAMPLE_RATE = 16000

        // 性能优化：低功耗模式采样率
        private const val SAMPLE_RATE_LOW_POWER = 8000

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SECONDS = 2

        // 性能优化：电量阈值
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10

        // 性能优化：智能休眠参数
        private const val IDLE_SLEEP_INTERVAL_MS = 100L
        private const val ACTIVE_SLEEP_INTERVAL_MS = 10L
        private const val LOW_POWER_SLEEP_INTERVAL_MS = 200L

        // Actions
        const val ACTION_START = "com.kevinluo.autoglm.voice.START_LISTENING"
        const val ACTION_STOP = "com.kevinluo.autoglm.voice.STOP_LISTENING"

        // Broadcast actions
        const val ACTION_WAKE_WORD_DETECTED = "com.kevinluo.autoglm.voice.WAKE_WORD_DETECTED"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        const val EXTRA_WAKE_WORD = "wake_word"

        @Volatile
        private var instance: ContinuousListeningService? = null

        fun getInstance(): ContinuousListeningService? = instance

        fun isRunning(): Boolean = instance?.state?.get() == State.LISTENING

        fun getState(): State = instance?.state?.get() ?: State.STOPPED

        /**
         * 暂停监听（释放 AudioRecord，但保持服务运行）
         * 用于在其他组件需要使用麦克风时暂停
         */
        fun pause() {
            instance?.pauseByUser()
        }

        /**
         * 恢复监听
         * 在其他组件释放麦克风后调用
         */
        fun resume() {
            instance?.resumeFromUser()
        }

        fun start(context: Context) {
            val intent =
                Intent(context, ContinuousListeningService::class.java).apply {
                    action = ACTION_START
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, ContinuousListeningService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 状态机：使用单一状态变量管理所有状态
    private val state = AtomicReference(State.IDLE)

    private var audioRecord: AudioRecord? = null
    private var recognizer: SherpaOnnxRecognizer? = null
    private var wakeWordDetector: WakeWordDetector? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var modelManager: VoiceModelManager

    private var listeningJob: Job? = null

    // 性能优化：电量管理
    private var isLowPowerMode = false
    private var currentSampleRate = SAMPLE_RATE

    // 屏幕状态管理
    private var isScreenOn = true
    private var screenStateReceiver: BroadcastReceiver? = null

    // 性能监控
    private var serviceStartTimeMs = 0L
    private var totalAudioProcessedMs = 0L
    private var wakeWordDetectionCount = 0
    private var recognitionCount = 0

    // 性能优化：复用的音频缓冲区
    private var reusableBuffer: ShortArray? = null
    private var reusableFloatSamples: MutableList<Float>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager.getInstance(this)
        modelManager = VoiceModelManager.getInstance(this)
        createNotificationChannel()
        registerScreenStateReceiver()

        // 检查当前屏幕状态
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        KeepAliveManager.acquireListeningWakeLock(this)
        Logger.i(TAG, "[Performance] ContinuousListeningService created, screen on: $isScreenOn")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i(TAG, "[Performance] ContinuousListeningService destroying")
        logPerformanceStats()
        unregisterScreenStateReceiver()
        stopListening()
        KeepAliveManager.releaseListeningWakeLock()
        serviceScope.cancel()

        // 如果设置中仍然开启了持续监听，尝试重启服务
        if (settingsManager.isContinuousListeningEnabled()) {
            Logger.i(TAG, "Service destroyed but continuous listening still enabled, scheduling restart")
            scheduleServiceRestart()
        }

        instance = null
        super.onDestroy()
    }

    /**
     * 安排服务重启
     */
    private fun scheduleServiceRestart() {
        val restartIntent =
            Intent(applicationContext, ContinuousListeningService::class.java).apply {
                action = ACTION_START
            }
        val pendingIntent =
            PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
            // 1秒后重启
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent,
        )
    }

    /**
     * 注册屏幕状态广播接收器
     */
    private fun registerScreenStateReceiver() {
        screenStateReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            Logger.i(TAG, "Screen turned off, pausing listening")
                            isScreenOn = false
                            pauseListeningForScreenOff()
                        }

                        Intent.ACTION_SCREEN_ON -> {
                            Logger.i(TAG, "Screen turned on, resuming listening")
                            isScreenOn = true
                            resumeListeningForScreenOn()
                        }

                        Intent.ACTION_USER_PRESENT -> {
                            // 用户解锁后
                            Logger.i(TAG, "User present (unlocked), ensuring listening is active")
                            val currentState = state.get()
                            if (currentState == State.PAUSED_BY_SCREEN_OFF) {
                                resumeListeningForScreenOn()
                            }
                        }
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        registerReceiver(screenStateReceiver, filter)
        Logger.d(TAG, "Screen state receiver registered")
    }

    /**
     * 注销屏幕状态广播接收器
     */
    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
                Logger.d(TAG, "Screen state receiver unregistered")
            } catch (e: Exception) {
                Logger.e(TAG, "Error unregistering screen state receiver", e)
            }
        }
        screenStateReceiver = null
    }

    /**
     * 屏幕关闭时暂停监听
     */
    private fun pauseListeningForScreenOff() {
        val currentState = state.get()
        if (currentState != State.LISTENING) {
            Logger.d(TAG, "Cannot pause for screen off: current state is $currentState")
            return
        }

        Logger.i(TAG, "[Performance] Pausing listening due to screen off")

        // 状态转换: LISTENING -> PAUSED_BY_SCREEN_OFF
        if (!state.compareAndSet(State.LISTENING, State.PAUSED_BY_SCREEN_OFF)) {
            Logger.w(TAG, "State changed during pause for screen off")
            return
        }

        // 停止并释放 AudioRecord
        releaseAudioRecord()

        // 释放 WakeLock 节省电量
        KeepAliveManager.releaseListeningWakeLock()
    }

    /**
     * 屏幕打开时恢复监听
     */
    private fun resumeListeningForScreenOn() {
        val currentState = state.get()

        // 只有在屏幕关闭暂停状态下才恢复
        if (currentState != State.PAUSED_BY_SCREEN_OFF) {
            Logger.d(TAG, "Cannot resume for screen on: current state is $currentState")
            return
        }

        Logger.i(TAG, "[Performance] Resuming listening due to screen on")

        // 状态转换: PAUSED_BY_SCREEN_OFF -> STARTING
        if (!state.compareAndSet(State.PAUSED_BY_SCREEN_OFF, State.STARTING)) {
            Logger.w(TAG, "State changed during resume for screen on")
            return
        }

        // 重新获取 WakeLock
        KeepAliveManager.acquireListeningWakeLock(this)

        // 重新启动监听
        listeningJob =
            serviceScope.launch {
                try {
                    if (recognizer == null || !recognizer!!.isInitialized()) {
                        if (!initializeRecognizer()) {
                            Logger.e(TAG, "Failed to reinitialize recognizer on screen on")
                            state.set(State.IDLE)
                            return@launch
                        }
                    }

                    initializeWakeWordDetector()
                    startListeningInternal()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error resuming listening on screen on", e)
                    state.set(State.IDLE)
                }
            }
    }

    private fun startListening() {
        val currentState = state.get()

        // 检查是否可以启动
        when (currentState) {
            State.LISTENING -> {
                Logger.w(TAG, "Already listening, ignoring start request")
                return
            }

            State.STARTING -> {
                Logger.w(TAG, "Already starting, ignoring duplicate start request")
                return
            }

            State.PAUSED_BY_USER, State.PAUSED_BY_SCREEN_OFF -> {
                // 从暂停状态启动，需要先恢复
                Logger.d(TAG, "Starting from paused state: $currentState")
            }

            State.IDLE, State.STOPPED -> {
                // 正常启动
            }
        }

        // 状态转换 -> STARTING
        if (!state.compareAndSet(currentState, State.STARTING)) {
            Logger.w(TAG, "State changed during start, aborting")
            return
        }

        if (!modelManager.isModelDownloaded()) {
            Logger.e(TAG, "Model not downloaded, cannot start listening")
            state.set(State.STOPPED)
            stopSelf()
            return
        }

        // 如果屏幕关闭，不启动监听，但标记为屏幕关闭暂停状态
        if (!isScreenOn) {
            Logger.i(TAG, "Screen is off, not starting listening")
            state.set(State.PAUSED_BY_SCREEN_OFF)
            startForeground(NOTIFICATION_ID, createNotification(getString(R.string.voice_listening_active) + " (屏幕关闭)"))
            return
        }

        serviceStartTimeMs = System.currentTimeMillis()
        checkBatteryStatus()
        startForeground(NOTIFICATION_ID, createNotification())

        listeningJob =
            serviceScope.launch {
                try {
                    val initStartTime = System.currentTimeMillis()
                    if (!initializeRecognizer()) {
                        Logger.e(TAG, "Failed to initialize recognizer")
                        state.set(State.STOPPED)
                        stopSelf()
                        return@launch
                    }
                    Logger.d(
                        TAG,
                        "[Performance] Recognizer initialized in ${System.currentTimeMillis() - initStartTime}ms",
                    )

                    initializeWakeWordDetector()
                    startListeningInternal()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error starting listening", e)
                    state.set(State.STOPPED)
                    stopSelf()
                }
            }
    }

    private fun stopListening() {
        Logger.i(TAG, "[Performance] Stopping continuous listening, current state: ${state.get()}")
        logPerformanceStats()

        state.set(State.STOPPED)
        listeningJob?.cancel()

        releaseAudioRecord()

        recognizer?.release()
        recognizer = null

        reusableBuffer = null
        reusableFloatSamples = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 释放 AudioRecord 资源
     */
    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    /**
     * 被用户（VoiceInputManager）暂停监听
     */
    private fun pauseByUser() {
        val currentState = state.get()
        if (currentState != State.LISTENING) {
            Logger.d(TAG, "Cannot pause by user: current state is $currentState")
            return
        }

        Logger.i(TAG, "[Performance] Pausing continuous listening by user")

        // 状态转换: LISTENING -> PAUSED_BY_USER
        if (!state.compareAndSet(State.LISTENING, State.PAUSED_BY_USER)) {
            Logger.w(TAG, "State changed during pause by user")
            return
        }

        // 停止并释放 AudioRecord，但保持识别器
        releaseAudioRecord()
    }

    /**
     * 从用户暂停状态恢复监听
     */
    private fun resumeFromUser() {
        val currentState = state.get()
        if (currentState != State.PAUSED_BY_USER) {
            Logger.d(TAG, "Cannot resume from user: current state is $currentState")
            return
        }

        Logger.i(TAG, "[Performance] Resuming continuous listening from user pause")

        // 状态转换: PAUSED_BY_USER -> STARTING
        if (!state.compareAndSet(State.PAUSED_BY_USER, State.STARTING)) {
            Logger.w(TAG, "State changed during resume from user")
            return
        }

        // 重新启动监听
        listeningJob =
            serviceScope.launch {
                try {
                    // 识别器应该还在，直接重新开始监听
                    if (recognizer == null || !recognizer!!.isInitialized()) {
                        if (!initializeRecognizer()) {
                            Logger.e(TAG, "Failed to reinitialize recognizer on resume")
                            state.set(State.IDLE)
                            return@launch
                        }
                    }

                    // 重新初始化唤醒词检测器（以防设置有变化）
                    initializeWakeWordDetector()

                    startListeningInternal()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error resuming listening", e)
                    state.set(State.IDLE)
                }
            }
    }

    private fun checkBatteryStatus() {
        try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

            val isCharging =
                batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
                    it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
                } ?: false

            Logger.d(TAG, "[Performance] Battery: $batteryPct%, charging: $isCharging")

            when {
                batteryPct <= CRITICAL_BATTERY_THRESHOLD && !isCharging -> {
                    Logger.w(TAG, "[Performance] Critical battery level, stopping service")
                    stopListening()
                }

                batteryPct <= LOW_BATTERY_THRESHOLD && !isCharging -> {
                    Logger.i(TAG, "[Performance] Low battery, enabling low power mode")
                    enableLowPowerMode()
                }

                else -> {
                    disableLowPowerMode()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking battery status", e)
        }
    }

    private fun enableLowPowerMode() {
        if (!isLowPowerMode) {
            isLowPowerMode = true
            currentSampleRate = SAMPLE_RATE_LOW_POWER
            Logger.i(TAG, "[Performance] Low power mode enabled, sample rate: $currentSampleRate")
        }
    }

    private fun disableLowPowerMode() {
        if (isLowPowerMode) {
            isLowPowerMode = false
            currentSampleRate = SAMPLE_RATE
            Logger.i(TAG, "[Performance] Low power mode disabled, sample rate: $currentSampleRate")
        }
    }

    private suspend fun initializeRecognizer(): Boolean {
        val modelPath = modelManager.getModelPath()
        val vadPath = modelManager.getVadModelPath()

        if (modelPath == null || vadPath == null) {
            Logger.e(TAG, "Model paths not available")
            return false
        }

        recognizer = SherpaOnnxRecognizer(this)
        return recognizer!!.initialize(modelPath, vadPath)
    }

    private fun initializeWakeWordDetector() {
        val wakeWords = settingsManager.getWakeWordsList()
        val sensitivity = settingsManager.getWakeWordSensitivity()
        wakeWordDetector = WakeWordDetector(wakeWords, sensitivity)
        Logger.d(TAG, "[Performance] Wake word detector initialized with words: $wakeWords")
    }

    private suspend fun startListeningInternal() = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(currentSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Logger.e(TAG, "Invalid buffer size: $bufferSize")
            return@withContext
        }

        val optimizedBufferSize = bufferSize * 4
        Logger.d(TAG, "[Performance] Using buffer size: $optimizedBufferSize")

        try {
            audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    currentSampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    optimizedBufferSize,
                )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(TAG, "AudioRecord initialization failed")
                return@withContext
            }

            audioRecord?.startRecording()

            // 状态转换: STARTING/PAUSED_BY_* -> LISTENING
            state.set(State.LISTENING)
            Logger.i(TAG, "[Performance] Continuous listening started at ${currentSampleRate}Hz")

            val buffer = getOrCreateBuffer(bufferSize / 2)
            val audioBuffer = getOrCreateFloatList()
            val maxBufferSamples = currentSampleRate * BUFFER_SIZE_SECONDS

            var silenceCount = 0
            val maxSilenceCount = 30
            var hasSpeech = false

            var batteryCheckCounter = 0
            val batteryCheckInterval = 600

            var currentSleepInterval = IDLE_SLEEP_INTERVAL_MS

            while (state.get() == State.LISTENING) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize <= 0) {
                    delay(currentSleepInterval)
                    continue
                }

                var energySum = 0.0
                for (i in 0 until readSize) {
                    val sample = buffer[i] / 32768.0f
                    audioBuffer.add(sample)
                    energySum += sample * sample
                }
                val energy = energySum / readSize

                val energyThreshold = if (isLowPowerMode) 0.001 else 0.0005
                val isSpeaking = energy > energyThreshold

                if (isSpeaking) {
                    hasSpeech = true
                    silenceCount = 0
                    currentSleepInterval = ACTIVE_SLEEP_INTERVAL_MS

                    if (audioBuffer.size > maxBufferSamples) {
                        val removeCount = audioBuffer.size - maxBufferSamples
                        repeat(removeCount) { audioBuffer.removeAt(0) }
                    }
                } else if (hasSpeech) {
                    silenceCount++

                    if (silenceCount >= maxSilenceCount && audioBuffer.size > currentSampleRate / 2) {
                        val processingStartTime = System.currentTimeMillis()

                        val samplesArray = audioBuffer.toFloatArray()
                        processAudioSegment(samplesArray)

                        val processingTime = System.currentTimeMillis() - processingStartTime
                        totalAudioProcessedMs += (samplesArray.size * 1000L / currentSampleRate)
                        Logger.d(TAG, "[Performance] Audio segment processed in ${processingTime}ms")

                        audioBuffer.clear()
                        hasSpeech = false
                        silenceCount = 0
                    }

                    currentSleepInterval = IDLE_SLEEP_INTERVAL_MS
                } else {
                    currentSleepInterval =
                        if (isLowPowerMode) {
                            LOW_POWER_SLEEP_INTERVAL_MS
                        } else {
                            IDLE_SLEEP_INTERVAL_MS
                        }
                }

                batteryCheckCounter++
                if (batteryCheckCounter >= batteryCheckInterval) {
                    batteryCheckCounter = 0
                    checkBatteryStatus()
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "Security exception", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Listening error", e)
        } finally {
            // 如果是正常退出循环（状态被改变），不要重置状态
            // 只有在异常情况下才重置
            val currentState = state.get()
            if (currentState == State.LISTENING) {
                state.set(State.IDLE)
            }
        }
    }

    private fun getOrCreateBuffer(size: Int): ShortArray {
        val existing = reusableBuffer
        return if (existing != null && existing.size >= size) {
            existing
        } else {
            ShortArray(size).also { reusableBuffer = it }
        }
    }

    private fun getOrCreateFloatList(): MutableList<Float> {
        val existing = reusableFloatSamples
        return if (existing != null) {
            existing.clear()
            existing
        } else {
            ArrayList<Float>(SAMPLE_RATE * BUFFER_SIZE_SECONDS).also { reusableFloatSamples = it }
        }
    }

    private suspend fun processAudioSegment(samples: FloatArray) {
        if (samples.size < currentSampleRate / 4) {
            Logger.d(TAG, "[Performance] Audio segment too short (${samples.size} samples), skipping")
            return
        }

        Logger.d(TAG, "[Performance] Processing audio segment: ${samples.size} samples")
        recognitionCount++

        val result = recognizer?.recognize(samples)
        if (result == null || result.text.isBlank()) {
            Logger.d(TAG, "No speech recognized")
            return
        }

        Logger.d(TAG, "[Performance] Recognized: ${result.text}")

        val detectedWakeWord = wakeWordDetector?.detect(result.text)
        if (detectedWakeWord != null) {
            wakeWordDetectionCount++
            Logger.i(TAG, "[Performance] Wake word detected: $detectedWakeWord (total: $wakeWordDetectionCount)")
            onWakeWordDetected(detectedWakeWord, result.text)
        }
    }

    private fun onWakeWordDetected(wakeWord: String, fullText: String) {
        Logger.i(TAG, "[Performance] Wake word detected: $wakeWord, broadcasting...")

        // 发送广播（如果 Activity 在前台会收到）
        val broadcastIntent =
            Intent(ACTION_WAKE_WORD_DETECTED).apply {
                putExtra(EXTRA_WAKE_WORD, wakeWord)
                putExtra(EXTRA_RECOGNIZED_TEXT, fullText)
                setPackage(packageName)
            }
        sendBroadcast(broadcastIntent)

        // 同时启动 Activity（如果 App 在后台）
        // 使用 FLAG_ACTIVITY_SINGLE_TOP 避免重建已存在的 Activity，保持 Shizuku 连接
        val activityIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WAKE_WORD, wakeWord)
                putExtra(EXTRA_RECOGNIZED_TEXT, fullText)
                action = ACTION_WAKE_WORD_DETECTED
            }
        startActivity(activityIntent)
    }

    private fun logPerformanceStats() {
        if (serviceStartTimeMs > 0) {
            val runningTimeMs = System.currentTimeMillis() - serviceStartTimeMs
            val runningTimeMin = runningTimeMs / 60000.0
            Logger.i(TAG, "[Performance] Service stats:")
            Logger.i(TAG, "  - Running time: %.2f minutes".format(runningTimeMin))
            Logger.i(TAG, "  - Audio processed: ${totalAudioProcessedMs / 1000}s")
            Logger.i(TAG, "  - Recognition count: $recognitionCount")
            Logger.i(TAG, "  - Wake word detections: $wakeWordDetectionCount")
            Logger.i(TAG, "  - Low power mode: $isLowPowerMode")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.voice_continuous_listening),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.voice_continuous_listening_desc)
                    setShowBadge(false)
                }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String = getString(R.string.voice_listening_active)): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_continuous_listening))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
