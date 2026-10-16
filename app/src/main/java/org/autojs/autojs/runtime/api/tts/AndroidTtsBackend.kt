package org.autojs.autojs.runtime.api.tts

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as AndroidVoice
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AndroidTtsBackendFactory(
    context: Context,
) : TtsBackendFactory {

    private val applicationContext = context.applicationContext ?: context

    override fun create(
        requestedEngine: String?,
        listener: TtsProgressListener,
        onInit: (statusCode: Int) -> Unit,
    ): TtsBackend {
        val normalizedEngine = normalizeTtsEngineName(requestedEngine)?.also {
            requireEngineAvailable(it)
        }
        val initLock = Any()
        var backendReady = false
        var pendingStatus: Int? = null

        val initListener = TextToSpeech.OnInitListener { status ->
            val deliver = synchronized(initLock) {
                if (backendReady) {
                    true
                } else {
                    pendingStatus = status
                    false
                }
            }
            if (deliver) onInit(status)
        }

        val bindingContext = normalizedEngine?.let {
            TtsEngineBindingContext(applicationContext, it)
        }
        val textToSpeech = if (normalizedEngine == null) {
            TextToSpeech(applicationContext, initListener)
        } else {
            TextToSpeech(requireNotNull(bindingContext), initListener, normalizedEngine)
        }
        val backend = try {
            bindingContext?.verifyNoFallback()
            AndroidTtsBackend(
                textToSpeech = textToSpeech,
                requestedEngine = normalizedEngine,
                initialListener = listener,
                requestedEngineVerifier = normalizedEngine?.let { engine ->
                    {
                        requireEngineAvailable(engine)
                        bindingContext?.verifyNoFallback()
                    }
                },
            )
        } catch (error: Throwable) {
            textToSpeech.shutdown()
            throw error
        }
        val status = synchronized(initLock) {
            backendReady = true
            pendingStatus.also {
                pendingStatus = null
            }
        }
        status?.let(onInit)
        return backend
    }

    private fun requireEngineAvailable(packageName: String) {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            .setPackage(packageName)
        @Suppress("DEPRECATION")
        val matches = applicationContext.packageManager.queryIntentServices(
            intent,
            PackageManager.MATCH_ALL,
        )
        require(matches.any { match ->
            match.serviceInfo?.let { info ->
                info.packageName == packageName &&
                    info.enabled &&
                    info.applicationInfo.enabled
            } == true
        }) {
            "TTS engine is not installed or enabled: $packageName"
        }
    }
}

/**
 * API 24-34 may synchronously fall back when binding a requested engine fails. The public
 * TextToSpeech constructor does not expose its useFallback flag, so observe the package used by
 * its legacy bindService path and reject a different successful binding.
 */
private class TtsEngineBindingContext(
    baseContext: Context,
    private val requestedEngine: String,
) : ContextWrapper(baseContext) {

    @Volatile
    private var boundEngine: String? = null

    override fun bindService(
        service: Intent,
        connection: ServiceConnection,
        flags: Int,
    ): Boolean {
        val bound = super.bindService(service, connection, flags)
        if (bound && service.action == TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE) {
            boundEngine = service.`package` ?: service.component?.packageName
        }
        return bound
    }

    fun verifyNoFallback() {
        val observedEngine = boundEngine ?: return
        require(observedEngine == requestedEngine) {
            "Requested TTS engine $requestedEngine fell back to $observedEngine"
        }
    }
}

internal class AndroidTtsBackend(
    private val textToSpeech: TextToSpeech,
    private val requestedEngine: String?,
    initialListener: TtsProgressListener,
    private val requestedEngineVerifier: (() -> Unit)? = null,
) : TtsBackend {

    private val lock = Any()
    private val shutdownState = AtomicBoolean(false)
    private val listener = AtomicReference(initialListener)
    private var configuration = TtsConfiguration()
    private var configurationApplied = false
    private var fallbackDefaultVoiceName: String? = null
    private var effectiveEngineName: String? = requestedEngine

    private val platformProgressListener = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String) {
            notifyListener { it.onStart(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            notifyListener { it.onDone(utteranceId) }
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            notifyListener { it.onStop(utteranceId, interrupted) }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String) {
            notifyListener { it.onError(utteranceId, TtsBackendStatus.ERROR) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            notifyListener { it.onError(utteranceId, errorCode) }
        }

        override fun onRangeStart(
            utteranceId: String,
            start: Int,
            end: Int,
            frame: Int,
        ) {
            notifyListener { it.onRangeStart(utteranceId, start, end, frame) }
        }

        override fun onBeginSynthesis(
            utteranceId: String,
            sampleRateInHz: Int,
            audioFormat: Int,
            channelCount: Int,
        ) {
            val format = runCatching {
                TtsSynthesisFormat(
                    sampleRateInHz = sampleRateInHz,
                    audioEncoding = audioFormat.toTtsAudioEncoding(),
                    channelCount = channelCount,
                )
            }.getOrElse {
                notifyListener { listener ->
                    listener.onError(utteranceId, TtsBackendStatus.ERROR)
                }
                return
            }
            notifyListener { it.onBeginSynthesis(utteranceId, format) }
        }

        override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
            // Android documents this callback value as a copy. The service either drops it or
            // transfers it to its serial command lane, so another defensive copy is unnecessary.
            notifyListener { it.onAudioAvailable(utteranceId, audio) }
        }
    }

    init {
        ensureSuccess(
            operation = "attachProgressListener",
            statusCode = textToSpeech.setOnUtteranceProgressListener(
                platformProgressListener,
            ),
        )
    }

    override fun engineName(): String? = synchronized(lock) {
        checkOpen()
        verifyRequestedEngine()
        effectiveEngineName
            ?: textToSpeech.defaultEngine?.also { effectiveEngineName = it }
    }

    override fun attachProgressListener(listener: TtsProgressListener) = synchronized(lock) {
        checkOpen()
        verifyRequestedEngine()
        this.listener.set(listener)
    }

    override fun configure(configuration: TtsConfiguration): TtsConfigurationResult =
        synchronized(lock) {
            checkOpen()
            verifyRequestedEngine()
            applyConfiguration(configuration).also {
                verifyRequestedEngine()
                this.configuration = configuration
                configurationApplied = true
            }
        }

    override fun speak(
        text: String,
        queueMode: TtsQueueMode,
        utteranceId: String,
        parameters: TtsRequestParameters,
    ): Int = synchronized(lock) {
        checkOpen()
        validateText(text)
        validateUtteranceId(utteranceId)
        ensureConfigurationApplied()
        validateEngineParameters(parameters)
        textToSpeech.speak(
            text,
            queueMode.toPlatformQueueMode(),
            parameters.toBundle(),
            utteranceId,
        ).also { verifyRequestedEngine() }
    }

    override fun playSilence(
        durationMillis: Long,
        queueMode: TtsQueueMode,
        utteranceId: String,
    ): Int = synchronized(lock) {
        checkOpen()
        require(durationMillis >= 0L) { "TTS silence duration must not be negative" }
        validateUtteranceId(utteranceId)
        ensureConfigurationApplied()
        textToSpeech.playSilentUtterance(
            durationMillis,
            queueMode.toPlatformQueueMode(),
            utteranceId,
        ).also { verifyRequestedEngine() }
    }

    override fun synthesizeToFile(
        text: String,
        file: File,
        utteranceId: String,
        parameters: TtsRequestParameters,
    ): Int = synchronized(lock) {
        checkOpen()
        validateText(text)
        validateUtteranceId(utteranceId)
        require(!file.isDirectory) {
            "TTS synthesis output path points to a directory: ${file.absolutePath}"
        }
        require(file.parentFile?.isDirectory == true) {
            "TTS synthesis output parent directory does not exist: ${file.absolutePath}"
        }
        ensureConfigurationApplied()
        validateEngineParameters(parameters)
        textToSpeech.synthesizeToFile(
            text,
            parameters.toBundle(),
            file,
            utteranceId,
        ).also { verifyRequestedEngine() }
    }

    override fun stop(): Int = synchronized(lock) {
        checkOpen()
        textToSpeech.stop()
    }

    override fun deactivate() {
        if (shutdownState.compareAndSet(false, true)) {
            listener.set(NO_OP_PROGRESS_LISTENER)
        }
    }

    override fun shutdown() {
        deactivate()
        // Never hold [lock] while entering TextToSpeech: another call may be permanently stuck
        // in an engine Binder implementation. The service invokes this method only from its
        // bounded native-call runner.
        textToSpeech.shutdown()
    }

    override fun isSpeaking(): Boolean = synchronized(lock) {
        if (shutdownState.get()) false else textToSpeech.isSpeaking
    }

    override fun engines(): List<TtsEngine> = synchronized(lock) {
        checkOpen()
        verifyRequestedEngine()
        val defaultEngine = textToSpeech.defaultEngine
        textToSpeech.engines
            .orEmpty()
            .asSequence()
            .mapNotNull { engine ->
                val name = engine.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                TtsEngine(
                    name = name,
                    label = engine.label.orEmpty(),
                    isDefault = name == defaultEngine,
                    isRequested = name == requestedEngine,
                )
            }
            .distinctBy(TtsEngine::name)
            .sortedBy(TtsEngine::name)
            .toList()
    }

    override fun voices(): List<TtsVoice> = synchronized(lock) {
        checkOpen()
        verifyRequestedEngine()
        platformVoices()
            .map(AndroidVoice::toTtsVoice)
            .sortedWith(compareBy(TtsVoice::languageTag, TtsVoice::name))
    }

    override fun languages(): List<String> = synchronized(lock) {
        checkOpen()
        verifyRequestedEngine()
        textToSpeech.availableLanguages
            .orEmpty()
            .asSequence()
            .map(Locale::toLanguageTag)
            .filter { it.isNotBlank() && it != "und" }
            .distinct()
            .sorted()
            .toList()
    }

    override fun isLanguageAvailable(languageTag: String): TtsLanguageAvailability =
        synchronized(lock) {
            checkOpen()
            verifyRequestedEngine()
            val normalizedTag = normalizeTtsLanguageTag(languageTag)
            val statusCode = textToSpeech.isLanguageAvailable(
                Locale.forLanguageTag(normalizedTag),
            )
            TtsLanguageAvailability(
                languageTag = normalizedTag,
                support = statusCode.toTtsLanguageSupport(),
                statusCode = statusCode,
            )
        }

    private fun applyConfiguration(
        configuration: TtsConfiguration,
    ): TtsConfigurationResult {
        captureEngineDefaultVoice()
        val selectedVoice = if (!configuration.applySpeechProperties) {
            textToSpeech.voice
        } else when (val selection = configuration.selection) {
            TtsSelection.Default -> selectDefault(configuration.offlineOnly)
            is TtsSelection.Language -> selectLanguage(selection.languageTag, configuration.offlineOnly)
            is TtsSelection.PreferredLanguage -> {
                val available = if (configuration.offlineOnly) {
                    selectOfflineTtsVoice(platformVoices().map(AndroidVoice::toTtsVoice), selection.languageTag) != null
                } else {
                    textToSpeech.isLanguageAvailable(Locale.forLanguageTag(selection.languageTag)) >= TextToSpeech.LANG_AVAILABLE
                }
                if (available) {
                    selectLanguage(selection.languageTag, configuration.offlineOnly)
                } else {
                    selectDefault(configuration.offlineOnly)
                }
            }
            is TtsSelection.Voice -> {
                val voice = platformVoices()
                    .firstOrNull { it.name == selection.name }
                    ?: throw TtsBackendException(
                        operation = "setVoice",
                        statusCode = TtsBackendStatus.ERROR,
                        message = "TTS voice is not available: ${selection.name}",
                    )
                ensureUsable(voice, configuration)
                setVoice(voice)
                voice
            }
            is TtsSelection.ModelVoice -> {
                val voices = platformVoices()
                when (val choice = resolveTtsModelVoice(voices.map(AndroidVoice::toTtsVoice), selection)) {
                    is TtsModelVoiceChoice.Listed -> {
                        val voice = voices.first { it.name == choice.voice.name }
                        ensureUsable(voice, configuration)
                        setVoice(voice)
                        voice
                    }
                    is TtsModelVoiceChoice.Named -> {
                        // The engine lists numbered speakers only; it resolves a speaker name when the voice is set.
                        val voice = AndroidVoice(
                            choice.name,
                            Locale.forLanguageTag(choice.template.languageTag),
                            choice.template.quality,
                            choice.template.latency,
                            false,
                            emptySet(),
                        )
                        if (textToSpeech.setVoice(voice) != TextToSpeech.SUCCESS) {
                            throw TtsBackendException(
                                operation = "setVoice",
                                statusCode = TtsBackendStatus.ERROR,
                                message = "TTS voice is not available: ${choice.name} " +
                                    "(the engine knows no speaker named \"${selection.speaker}\" in ${selection.model})",
                            )
                        }
                        voice
                    }
                }
            }
        }

        if (
            configuration.applySpeechProperties &&
            configuration.offlineOnly &&
            selectedVoice != null
        ) {
            val model = selectedVoice.toTtsVoice()
            check(model.isOffline) {
                "Offline-only TTS configuration selected a network or uninstalled voice"
            }
        }

        if (configuration.applySpeechProperties) {
            ensureSuccess(
                operation = "setSpeechRate",
                statusCode = textToSpeech.setSpeechRate(configuration.speechRate),
            )
            ensureSuccess(
                operation = "setPitch",
                statusCode = textToSpeech.setPitch(configuration.pitch),
            )
        }
        val effectiveUsage = configuration.audioUsage.effectiveForPlatform()
        ensureSuccess(
            operation = "setAudioAttributes",
            statusCode = textToSpeech.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(effectiveUsage.toPlatformUsage())
                    .build(),
            ),
        )

        val actualVoice = selectedVoice ?: textToSpeech.voice
        return TtsConfigurationResult(
            configuration = configuration,
            engineName = engineName(),
            selectedVoice = actualVoice?.toTtsVoice(),
            languageTag = actualVoice?.locale?.toLanguageTag()
                ?: selectedVoice?.locale?.toLanguageTag(),
            effectiveAudioUsage = effectiveUsage,
        )
    }

    private fun ensureConfigurationApplied() {
        if (!configurationApplied) {
            applyConfiguration(configuration)
            configurationApplied = true
        }
    }

    /**
     * Some OEM engines return null from getDefaultVoice even though getVoice and getVoices are
     * usable. Capture that initial voice once, before any task changes it, so later tasks can
     * still reset configuration without inheriting the preceding task's selection.
     */
    private fun captureEngineDefaultVoice() {
        if (fallbackDefaultVoiceName == null) {
            fallbackDefaultVoiceName =
                (textToSpeech.defaultVoice ?: textToSpeech.voice)?.name
        }
    }

    private fun resolveEngineDefaultVoice(): AndroidVoice {
        textToSpeech.defaultVoice?.let { voice ->
            fallbackDefaultVoiceName = voice.name
            return voice
        }

        val voices = platformVoices()
        fallbackDefaultVoiceName?.let { name ->
            voices.firstOrNull { it.name == name }?.let { return it }
        }
        val fallback = textToSpeech.voice
            ?: throw TtsBackendException(
                operation = "defaultVoice",
                statusCode = TtsBackendStatus.ERROR,
                message = "The TTS engine did not provide any usable voice",
            )
        fallbackDefaultVoiceName = fallback.name
        return fallback
    }

    private fun selectOfflineVoice(
        languageTag: String,
        preferredVoiceName: String? = null,
    ): AndroidVoice {
        val voicesByName = platformVoices().associateBy(AndroidVoice::getName)
        val selected = selectOfflineTtsVoice(
            voices = voicesByName.values.map(AndroidVoice::toTtsVoice),
            preferredLanguageTag = languageTag,
            preferredVoiceName = preferredVoiceName,
        ) ?: throw TtsBackendException(
            operation = "selectOfflineVoice",
            statusCode = TextToSpeech.ERROR_NOT_INSTALLED_YET,
            message = "No installed offline TTS voice is available for " +
                normalizeTtsLanguageTag(languageTag),
        )
        val voice = requireNotNull(voicesByName[selected.name]) {
            "Selected TTS voice disappeared while configuring the engine"
        }
        setVoice(voice)
        return voice
    }

    private fun selectDefault(offlineOnly: Boolean): AndroidVoice? {
        val defaultVoice = resolveEngineDefaultVoice()
        setVoice(defaultVoice, "resetDefaultVoice")
        return if (offlineOnly) {
            selectOfflineVoice(
                languageTag = defaultVoice.locale.toLanguageTag(),
                preferredVoiceName = defaultVoice.name,
            )
        } else {
            defaultVoice
        }
    }

    private fun selectLanguage(languageTag: String, offlineOnly: Boolean): AndroidVoice? {
        if (offlineOnly) return selectOfflineVoice(languageTag)
        ensureLanguageAvailable(
            textToSpeech.setLanguage(Locale.forLanguageTag(languageTag)),
            languageTag,
        )
        return textToSpeech.voice
    }

    private fun ensureUsable(voice: AndroidVoice, configuration: TtsConfiguration) {
        val model = voice.toTtsVoice()
        if (model.notInstalled) {
            throw TtsBackendException(
                operation = "setVoice",
                statusCode = TextToSpeech.ERROR_NOT_INSTALLED_YET,
                message = "TTS voice is not installed: ${voice.name}",
            )
        }
        if (configuration.offlineOnly && model.networkRequired) {
            throw TtsBackendException(
                operation = "setVoice",
                statusCode = TextToSpeech.ERROR_NETWORK,
                message = "TTS voice requires a network connection: ${voice.name}",
            )
        }
    }

    private fun setVoice(
        voice: AndroidVoice,
        operation: String = "setVoice",
    ) {
        ensureSuccess(operation, textToSpeech.setVoice(voice))
    }

    private fun platformVoices(): List<AndroidVoice> =
        textToSpeech.voices.orEmpty().toList()

    private fun checkOpen() {
        check(!shutdownState.get()) { "TTS backend has already been shut down" }
    }

    private fun verifyRequestedEngine() {
        requestedEngineVerifier?.invoke()
    }

    private inline fun notifyListener(action: (TtsProgressListener) -> Unit) {
        runCatching { action(listener.get()) }
    }

    private fun validateText(text: String) {
        require(text.length < TextToSpeech.getMaxSpeechInputLength()) {
            "TTS text chunk must be shorter than " +
                "${TextToSpeech.getMaxSpeechInputLength()} UTF-16 code units"
        }
    }

    private fun validateUtteranceId(utteranceId: String) {
        require(utteranceId.isNotBlank()) { "TTS utterance ID must not be empty" }
        require(utteranceId.length <= MAX_UTTERANCE_ID_LENGTH) {
            "TTS utterance ID is too long"
        }
        require('\u0000' !in utteranceId) { "TTS utterance ID must not contain NUL" }
    }

    private fun validateEngineParameters(parameters: TtsRequestParameters) {
        if (parameters.engineParameters.isEmpty()) return
        val engineName = effectiveEngineName
            ?: textToSpeech.defaultEngine?.also { effectiveEngineName = it }
            ?: throw TtsBackendException(
                operation = "engineParameters",
                statusCode = TtsBackendStatus.ERROR,
                message = "The active TTS engine could not be identified",
            )
        validateTtsEngineParameterPrefixes(parameters.engineParameters.keys, engineName)
    }

    private fun ensureLanguageAvailable(
        statusCode: Int,
        languageTag: String,
    ) {
        if (statusCode >= TextToSpeech.LANG_AVAILABLE) return
        throw TtsBackendException(
            operation = "setLanguage",
            statusCode = statusCode,
            message = when (statusCode) {
                TextToSpeech.LANG_MISSING_DATA ->
                    "TTS language data is not installed: $languageTag"
                TextToSpeech.LANG_NOT_SUPPORTED ->
                    "TTS language is not supported: $languageTag"
                else -> "Unable to select TTS language $languageTag (status $statusCode)"
            },
        )
    }

    private fun ensureSuccess(
        operation: String,
        statusCode: Int,
    ) {
        if (statusCode == TextToSpeech.SUCCESS) return
        throw TtsBackendException(
            operation = operation,
            statusCode = statusCode,
            message = "TTS operation $operation failed with status $statusCode",
        )
    }

    private companion object {

        const val MAX_UTTERANCE_ID_LENGTH = 512

        val NO_OP_PROGRESS_LISTENER = object : TtsProgressListener {

            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) = Unit

            override fun onStop(utteranceId: String, interrupted: Boolean) = Unit

            override fun onError(utteranceId: String, errorCode: Int) = Unit

            override fun onRangeStart(
                utteranceId: String,
                start: Int,
                end: Int,
                frame: Int,
            ) = Unit

            override fun onBeginSynthesis(
                utteranceId: String,
                format: TtsSynthesisFormat,
            ) = Unit

            override fun onAudioAvailable(utteranceId: String, audio: ByteArray) = Unit
        }
    }
}

private fun AndroidVoice.toTtsVoice(): TtsVoice {
    val featureSet: Set<String>? = features
    val normalizedFeatures = featureSet
        .orEmpty()
        .filter(String::isNotBlank)
        .toSortedSet()
    return TtsVoice(
        name = name,
        languageTag = locale.toLanguageTag(),
        quality = quality,
        latency = latency,
        networkRequired = isNetworkConnectionRequired,
        notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in normalizedFeatures,
        features = normalizedFeatures,
    )
}

private fun TtsQueueMode.toPlatformQueueMode(): Int = when (this) {
    TtsQueueMode.FLUSH -> TextToSpeech.QUEUE_FLUSH
    TtsQueueMode.ADD -> TextToSpeech.QUEUE_ADD
}

private fun TtsRequestParameters.toBundle(): Bundle =
    Bundle(engineParameters.size + 2).apply {
        engineParameters.forEach { (name, value) ->
            when (value) {
                is TtsScalarValue.Text -> putString(name, value.value)
                is TtsScalarValue.BooleanValue -> putBoolean(name, value.value)
                is TtsScalarValue.IntValue -> putInt(name, value.value)
                is TtsScalarValue.LongValue -> putLong(name, value.value)
                is TtsScalarValue.FloatValue -> putFloat(name, value.value)
                is TtsScalarValue.DoubleValue -> putDouble(name, value.value)
            }
        }
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan)
    }

private fun TtsAudioUsage.effectiveForPlatform(): TtsAudioUsage =
    if (this == TtsAudioUsage.ASSISTANT && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        TtsAudioUsage.MEDIA
    } else {
        this
    }

@SuppressLint("InlinedApi")
private fun TtsAudioUsage.toPlatformUsage(): Int = when (this) {
    TtsAudioUsage.MEDIA -> AudioAttributes.USAGE_MEDIA
    TtsAudioUsage.ACCESSIBILITY -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
    TtsAudioUsage.ASSISTANT -> AudioAttributes.USAGE_ASSISTANT
    TtsAudioUsage.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
    TtsAudioUsage.ALARM -> AudioAttributes.USAGE_ALARM
    TtsAudioUsage.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
    TtsAudioUsage.VOICE_COMMUNICATION -> AudioAttributes.USAGE_VOICE_COMMUNICATION
}

private fun Int.toTtsAudioEncoding(): TtsAudioEncoding = when (this) {
    AudioFormat.ENCODING_PCM_8BIT -> TtsAudioEncoding.PCM_8_BIT
    AudioFormat.ENCODING_PCM_16BIT -> TtsAudioEncoding.PCM_16_BIT
    AudioFormat.ENCODING_PCM_FLOAT -> TtsAudioEncoding.PCM_FLOAT
    else -> TtsAudioEncoding.UNKNOWN
}

private fun Int.toTtsLanguageSupport(): TtsLanguageSupport = when (this) {
    TextToSpeech.LANG_AVAILABLE -> TtsLanguageSupport.LANGUAGE
    TextToSpeech.LANG_COUNTRY_AVAILABLE -> TtsLanguageSupport.LANGUAGE_AND_COUNTRY
    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE ->
        TtsLanguageSupport.LANGUAGE_COUNTRY_AND_VARIANT
    TextToSpeech.LANG_MISSING_DATA -> TtsLanguageSupport.MISSING_DATA
    TextToSpeech.LANG_NOT_SUPPORTED -> TtsLanguageSupport.NOT_SUPPORTED
    else -> TtsLanguageSupport.UNKNOWN
}
