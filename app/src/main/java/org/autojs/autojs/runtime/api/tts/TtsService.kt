package org.autojs.autojs.runtime.api.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.google.gson.JsonObject
import org.autojs.autojs.core.tts.TtsTextChunk
import org.autojs.autojs.core.tts.TtsTextChunker
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.tts.TtsUtterance
import java.util.ArrayDeque
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class TtsService(
    context: Context,
    private val client: TtsClient,
    commandScheduler: TtsCommandScheduler? = null,
    backendFactory: TtsBackendFactory? = null,
    private val blockingCallRunner: TtsBlockingCallRunner = AndroidTtsBlockingCallRunner,
    deadlineScheduler: TtsDeadlineScheduler? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    constructor(
        context: Context,
        scriptRuntime: ScriptRuntime,
        commandScheduler: TtsCommandScheduler? = null,
        backendFactory: TtsBackendFactory? = null,
        blockingCallRunner: TtsBlockingCallRunner = AndroidTtsBlockingCallRunner,
        deadlineScheduler: TtsDeadlineScheduler? = null,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(context, RhinoTtsClient(scriptRuntime), commandScheduler, backendFactory, blockingCallRunner, deadlineScheduler, clock)

    private val applicationContext = context.applicationContext ?: context
    private val schedulerDelegate = lazy {
        commandScheduler ?: AndroidTtsCommandScheduler()
    }
    private val scheduler: TtsCommandScheduler
        get() = schedulerDelegate.value
    private val backendFactoryDelegate = lazy {
        backendFactory ?: AndroidTtsBackendFactory(applicationContext)
    }
    private val backendFactory: TtsBackendFactory
        get() = backendFactoryDelegate.value
    private val deadlineSchedulerDelegate = lazy {
        deadlineScheduler ?: DefaultTtsDeadlineScheduler()
    }
    private val deadlineScheduler: TtsDeadlineScheduler
        get() = deadlineSchedulerDelegate.value
    private val lifecycleLock = Any()
    private val accepting = AtomicBoolean(true)
    private val defaults = AtomicReference(TtsOptions())
    private val jobIngress = TtsCapacityLimiter(
        MAX_PENDING_JOB_COMMANDS,
        MAX_PENDING_JOB_CHARACTERS,
    )
    private val queryIngress = TtsCapacityLimiter(MAX_PENDING_QUERIES, MAX_PENDING_QUERIES.toLong())
    private val controlIngress = TtsCapacityLimiter(
        MAX_PENDING_CONTROL_COMMANDS,
        MAX_PENDING_CONTROL_COMMANDS.toLong(),
    )
    private val progressIngress = TtsCapacityLimiter(
        MAX_PENDING_PROGRESS_EVENTS,
        MAX_PENDING_PROGRESS_EVENTS.toLong(),
    )
    private val audioIngress = TtsCapacityLimiter(
        MAX_PENDING_AUDIO_EVENTS,
        MAX_PENDING_AUDIO_BYTES.toLong(),
    )
    private val liveUtterances = ConcurrentHashMap<String, TtsTaskController>()
    private val liveOutputs = Collections.newSetFromMap(
        ConcurrentHashMap<TtsOutputFile, Boolean>(),
    )
    private val liveQueries = Collections.newSetFromMap(
        ConcurrentHashMap<PendingResult, Boolean>(),
    )
    private val backendLease = AtomicReference<TtsBackend?>(null)

    // The fields below are confined to scheduler commands.
    private val queue = ArrayDeque<Job>()
    private var queuedCharacters = 0L
    private var activeJob: Job? = null
    private var backend: TtsBackend? = null
    private var backendState = BackendState.NEW
    private var backendKey: String? = null
    private var backendGeneration = 0L
    private var initTimeout: TtsScheduledTask? = null
    private val initWaiters = mutableListOf<BackendWaiter>()
    private val lastSelection = AtomicReference<TtsConfigurationResult?>(null)
    private var effectiveEngineName: String? = null
    private val nativeCalls = ArrayDeque<NativeCall>()
    private var activeNativeCall: NativeCall? = null
    private var nextNativeCallId = 0L
    private val statusSnapshot = AtomicReference(TtsStatus())
    private val callbackPolicy = AtomicReference<CallbackPolicy?>(null)
    private val terminalCallbackId = AtomicReference<String?>(null)

    private val progressListener = object : TtsProgressListener {

        override fun onStart(utteranceId: String) {
            postProgressEvent(utteranceId) { handleStart(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            postTerminalEvent(utteranceId) { handleDone(utteranceId) }
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            postTerminalEvent(utteranceId) { handleStop(utteranceId, interrupted) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            postTerminalEvent(utteranceId) { handleError(utteranceId, errorCode) }
        }

        override fun onRangeStart(
            utteranceId: String,
            start: Int,
            end: Int,
            frame: Int,
        ) {
            postProgressEvent(utteranceId) {
                handleRange(utteranceId, start, end, frame)
            }
        }

        override fun onBeginSynthesis(
            utteranceId: String,
            format: TtsSynthesisFormat,
        ) {
            postProgressEvent(utteranceId) {
                handleBeginSynthesis(utteranceId, format)
            }
        }

        override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
            postAudioEvent(utteranceId, audio)
        }
    }

    fun defaults(): Map<String, Any?> = defaults.get().toPublicValue()

    fun configure(options: JsonObject): Map<String, Any?> {
        synchronized(lifecycleLock) {
            requireOpen()
            while (true) {
                val current = defaults.get()
                val updated = TtsOptionsParser.merge(current, options).speech
                if (defaults.compareAndSet(current, updated)) {
                    return updated.toPublicValue()
                }
            }
        }
    }

    /**
     * Speaks [text]; [selection] (only `speech.speak`, 3-Macaw Speech roadmap D19) replaces the voice and locale of
     * [options] and of the defaults: always for [TtsSelection.ModelVoice], for [TtsSelection.PreferredLanguage] only
     * when neither names a voice or a locale.
     */
    fun speech(
        text: String,
        options: JsonObject,
        forcedQueueMode: TtsQueueMode? = null,
        selection: TtsSelection? = null,
    ): TtsUtterance = speechTask(text, options, forcedQueueMode, selection).publicValue as TtsUtterance

    fun speechTask(
        text: String,
        options: JsonObject,
        forcedQueueMode: TtsQueueMode? = null,
        selection: TtsSelection? = null,
    ): TtsTask {
        val merged = TtsOptionsParser.merge(defaults.get(), options, forcedQueueMode)
        val applies = selection != null &&
            (selection !is TtsSelection.PreferredLanguage || (merged.speech.locale == null && merged.speech.voice == null))
        val parsed = if (applies) merged.copy(speech = merged.speech.copy(locale = null, voice = null, speechSelection = selection)) else merged
        val chunks = if (parsed.speech.autoSplit) {
            TtsTextChunker.chunk(text)
        } else {
            require(text.length <= TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH) {
                "TTS text exceeds the safe ${TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH} " +
                    "UTF-16 unit limit while autoSplit is disabled"
            }
            TtsTextChunker.chunk(text, TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH)
        }
        return submit(
            operation = Operation.SPEECH,
            text = text,
            chunks = chunks,
            options = parsed.speech,
        )
    }

    fun silence(
        durationMillis: Long,
        options: JsonObject,
        observable: Boolean = false,
    ): Any {
        require(durationMillis in 0L..MAX_SILENCE_MILLIS) {
            "TTS silence duration must be between 0 and $MAX_SILENCE_MILLIS milliseconds"
        }
        val parsed = TtsOptionsParser.merge(defaults.get(), options)
        val utterance = submit(
            operation = Operation.SILENCE,
            text = null,
            chunks = emptyList(),
            options = parsed.speech,
            silenceMillis = durationMillis,
        )
        return if (observable) utterance.publicValue else utterance.result
    }

    fun synthesis(
        text: String,
        path: String,
        options: JsonObject,
        observable: Boolean = false,
    ): Any {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        require(text.length <= TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH) {
            "TTS file synthesis is limited to " +
                "${TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH} UTF-16 units"
        }
        val parsed = TtsOptionsParser.merge(
            defaults.get(),
            options,
            includeFileOptions = true,
        )
        val output = TtsOutputFile.prepare(
            path,
            parsed.file,
            AndroidAtomicTtsFilePublisher,
        )
        val utterance = try {
            submit(
                operation = Operation.FILE,
                text = text,
                chunks = listOf(TtsTextChunk(text, 0, text.length)),
                options = parsed.speech,
                output = output,
            )
        } catch (error: Throwable) {
            discardOutput(output)?.let(error::addSuppressed)
            throw error
        }
        return if (observable) utterance.publicValue else utterance.result
    }

    fun ready(engine: String? = defaults.get().engine): ScriptPromiseAdapter =
        query(engine) { current ->
            readyValue(current, engine)
        }

    fun engines(engine: String? = null): ScriptPromiseAdapter =
        query(engine, independent = {
            TtsEngineDirectory.list(applicationContext, normalizeTtsEngineName(engine)).map { it.toPublicValue() }
        })

    fun voices(
        engine: String? = defaults.get().engine,
        locale: String? = null,
        offlineOnly: Boolean = false,
        installedOnly: Boolean = false,
    ): ScriptPromiseAdapter = query(engine) { current ->
        current.voices()
            .asSequence()
            .filter { voice ->
                locale == null || ttsLocaleMatches(voice.languageTag, locale)
            }
            .filter { voice -> !offlineOnly || voice.isOffline }
            .filter { voice -> !installedOnly || !voice.notInstalled }
            .map { it.toPublicValue() }
            .toList()
    }

    fun languages(engine: String? = defaults.get().engine): ScriptPromiseAdapter =
        query(engine, operation = TtsBackend::languages)

    fun languageAvailability(
        languageTag: String,
        engine: String? = defaults.get().engine,
    ): ScriptPromiseAdapter = query(engine) { current ->
        current.isLanguageAvailable(languageTag).toPublicValue()
    }

    fun isLanguageAvailable(
        languageTag: String,
        engine: String? = defaults.get().engine,
    ): ScriptPromiseAdapter = query(engine) { current ->
        current.isLanguageAvailable(languageTag).isAvailable
    }

    fun stopAll(): Int {
        synchronized(lifecycleLock) {
            requireOpen()
            val count = liveUtterances.size
            val reservation = controlIngress.tryAcquire()
                ?: throw TtsException(
                    "BACKPRESSURE",
                    "Too many TTS control commands are pending",
                )
            try {
                scheduler.post {
                    try {
                        abortAll("stopped", cancelled = true)
                        publishStatus()
                    } finally {
                        reservation.release()
                    }
                }
            } catch (error: Throwable) {
                reservation.release()
                throw error
            }
            return count
        }
    }

    fun reset(): Int {
        synchronized(lifecycleLock) {
            requireOpen()
            defaults.set(TtsOptions())
            val count = liveUtterances.size
            val reservation = controlIngress.tryAcquire()
                ?: throw TtsException(
                    "BACKPRESSURE",
                    "Too many TTS control commands are pending",
                )
            try {
                scheduler.post {
                    try {
                        abortAll("reset", cancelled = true)
                        shutdownBackend()
                        publishStatus()
                    } finally {
                        reservation.release()
                    }
                }
            } catch (error: Throwable) {
                reservation.release()
                throw error
            }
            return count
        }
    }

    fun isSpeaking(): Boolean = statusSnapshot.get().isSpeaking

    fun isBusy(): Boolean = statusSnapshot.get().activeId != null

    fun status(): Map<String, Any?> = statusSnapshot.get().toPublicValue()

    fun openSettings(): Boolean {
        requireOpen()
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        if (intent.resolveActivity(applicationContext.packageManager) == null) return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            applicationContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun installData(engine: String? = defaults.get().engine): Boolean {
        requireOpen()
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            engine?.trim()?.takeIf(String::isNotEmpty)?.let(::setPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(applicationContext.packageManager) == null) return false
        return runCatching {
            applicationContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun close() {
        val leasedBackend = synchronized(lifecycleLock) {
            if (!accepting.compareAndSet(true, false)) return
            backendLease.getAndSet(null)
        }
        leasedBackend?.let(::disposeBackend)
        if (deadlineSchedulerDelegate.isInitialized()) {
            deadlineScheduler.shutdown()
        }

        liveUtterances.values.toList().forEach { controller ->
            runCatching(controller::cancelSilently)
        }
        liveQueries.toList().forEach { pending ->
            runCatching(pending::cancelSilently)
        }
        if (!schedulerDelegate.isInitialized()) {
            liveOutputs.toList().forEach { output ->
                discardOutput(output)
            }
            statusSnapshot.set(TtsStatus(state = "closed"))
            return
        }

        val closeScheduled = runCatching {
            scheduler.post {
                val queuedJobs = queue.toList()
                queue.clear()
                queuedCharacters = 0L
                val current = activeJob
                activeJob = null
                current?.cancelGuards()
                clearPlatformCallback(current)
                shutdownBackend(alreadyDisposed = leasedBackend)
                queuedJobs.forEach { job ->
                    discardOutput(job)
                }
                current?.let { job ->
                    discardOutput(job)
                }
                liveOutputs.toList().forEach { output ->
                    discardOutput(output)
                }
                backendState = BackendState.CLOSED
                publishStatus()
                scheduler.shutdown()
            }
            true
        }.getOrDefault(false)
        if (!closeScheduled) {
            callbackPolicy.set(null)
            terminalCallbackId.set(null)
            runCatching { scheduler.shutdown() }
            liveOutputs.toList().forEach { output ->
                discardOutput(output)
            }
            statusSnapshot.set(TtsStatus(state = "closed"))
        }
    }

    private fun submit(
        operation: Operation,
        text: String?,
        chunks: List<TtsTextChunk>,
        options: TtsOptions,
        silenceMillis: Long? = null,
        output: TtsOutputFile? = null,
    ): TtsTask {
        synchronized(lifecycleLock) {
            requireOpen()
            val characters = text?.length?.toLong() ?: 0L
            val ingressReservation = jobIngress.tryAcquire(characters)
                ?: throw TtsException(
                    "BACKPRESSURE",
                    "Too many TTS operations are waiting to enter the command queue",
                )
            val id = "tts-${UUID.randomUUID()}"
            val controller = try {
                client.createTask(
                    id = id,
                    operation = operation.value,
                    text = text,
                    requestStop = ::stopUtterance,
                    onTerminal = {
                        liveUtterances.remove(id)
                        ingressReservation.release()
                    },
                )
            } catch (error: Throwable) {
                ingressReservation.release()
                throw error
            }
            liveUtterances[id] = controller

            val job = Job(
                controller = controller,
                operation = operation,
                text = text,
                chunks = chunks,
                options = options,
                silenceMillis = silenceMillis,
                output = output,
                submittedAt = clock(),
            )
            output?.let(liveOutputs::add)
            try {
                controller.queued(
                    linkedMapOf(
                        "id" to id,
                        "operation" to operation.value,
                        "queue" to options.queueMode.value,
                        "chunks" to chunks.size.coerceAtLeast(1),
                    ),
                )
                scheduler.post { enqueue(job) }
            } catch (error: Throwable) {
                discardOutput(job)
                liveUtterances.remove(id)
                controller.cancelSilently()
                throw error
            }
            return controller.task
        }
    }

    private fun enqueue(job: Job) {
        if (!accepting.get() || job.controller.isDone) {
            discardOutput(job)
            return
        }
        val characters = job.text?.length?.toLong() ?: 0L
        if (characters > MAX_QUEUED_CHARACTERS) {
            discardOutput(job)
            runCatching {
                job.controller.fail(
                    TtsException("QUEUE_LIMIT", "The TTS operation is too large to queue"),
                )
            }
            return
        }
        if (job.options.queueMode == TtsQueueMode.FLUSH) {
            abortAll("flushed", cancelled = false)
        }
        if (
            queue.size >= MAX_QUEUED_JOBS ||
            characters > MAX_QUEUED_CHARACTERS - queuedCharacters
        ) {
            discardOutput(job)
            runCatching {
                job.controller.fail(
                    TtsException(
                        "QUEUE_LIMIT",
                        "TTS queue exceeded $MAX_QUEUED_JOBS jobs or " +
                            "$MAX_QUEUED_CHARACTERS UTF-16 units",
                    ),
                )
            }
            return
        }
        queue.addLast(job)
        queuedCharacters += characters
        dispatchNext()
        publishStatus()
    }

    private fun dispatchNext() {
        if (activeJob != null || !accepting.get()) return
        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst()
            queuedCharacters -= candidate.text?.length?.toLong() ?: 0L
            if (candidate.controller.isDone) {
                discardOutput(candidate)
                continue
            }
            activeJob = candidate
            try {
                candidate.effectiveTimeoutMillis().takeIf { it > 0L }?.let { timeout ->
                    candidate.timeoutTask = deadlineScheduler.schedule(timeout) {
                        tryPostCallback {
                            if (activeJob === candidate && !candidate.controller.isDone) {
                                failActive(
                                    TtsException(
                                        "TIMEOUT",
                                        "TTS operation timed out after $timeout milliseconds",
                                    ),
                                    retireBackend = true,
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                failActive(error.toTtsException("SCHEDULING"))
                return
            }
            ensureBackend(
                requestedEngine = candidate.options.engine,
                onReady = { current ->
                    if (activeJob !== candidate || candidate.controller.isDone) return@ensureBackend
                    beginActive(current)
                },
                onError = { error ->
                    if (activeJob === candidate) failActive(error)
                },
            )
            publishStatus()
            return
        }
        publishStatus()
    }

    private fun beginActive(current: TtsBackend) {
        val job = activeJob ?: return
        val generation = backendGeneration
        enqueueNativeCall(
            operation = "configure",
            errorCode = "CONFIGURATION",
            generation = generation,
            expectedBackend = current,
            action = {
                current.configure(
                    TtsConfiguration.from(
                        job.options,
                        applySpeechProperties = job.operation != Operation.SILENCE,
                    ),
                )
            },
            onSuccess = { selection ->
                if (
                    activeJob !== job ||
                    job.controller.isDone ||
                    backend !== current ||
                    backendGeneration != generation
                ) {
                    return@enqueueNativeCall
                }
                job.selection = selection
                lastSelection.set(selection)
                effectiveEngineName = selection.engineName
                startActivePlatformOperation(current, job)
                publishStatus()
            },
            onError = { error ->
                if (
                    activeJob === job &&
                    backend === current &&
                    backendGeneration == generation
                ) {
                    failActive(error)
                }
            },
        )
    }

    private fun startActivePlatformOperation(current: TtsBackend, job: Job) {
        if (job.controller.isDone) {
            finishActiveWithoutResult()
            return
        }
        val generation = backendGeneration
        val platformId = "${job.controller.id}:${job.chunkIndex}:${job.platformGeneration++}"
        job.currentPlatformId = platformId
        terminalCallbackId.set(null)
        callbackPolicy.set(
            CallbackPolicy(
                utteranceId = platformId,
                audioEvents = job.options.audioEvents,
            ),
        )
        if (job.operation == Operation.FILE) {
            scheduleOutputSizeGuard(job)
        }
        val requestParameters = runCatching {
            TtsRequestParameters.from(job.options)
        }.getOrElse { error ->
            failActive(error.toTtsException("ENQUEUE"))
            return
        }
        enqueueNativeCall(
            operation = "enqueue",
            errorCode = "ENQUEUE",
            generation = generation,
            expectedBackend = current,
            action = {
                when (job.operation) {
                    Operation.SPEECH -> {
                        val chunk = job.currentChunk()
                        current.speak(
                            chunk.text,
                            TtsQueueMode.FLUSH,
                            platformId,
                            requestParameters,
                        )
                    }
                    Operation.SILENCE -> current.playSilence(
                        requireNotNull(job.silenceMillis),
                        TtsQueueMode.FLUSH,
                        platformId,
                    )
                    Operation.FILE -> current.synthesizeToFile(
                        requireNotNull(job.text),
                        requireNotNull(job.output).stagingFile,
                        platformId,
                        requestParameters,
                    )
                }
            },
            onSuccess = { result ->
                if (
                    activeJob !== job ||
                    job.controller.isDone ||
                    job.currentPlatformId != platformId ||
                    backend !== current ||
                    backendGeneration != generation
                ) {
                    return@enqueueNativeCall
                }
                if (result != TtsBackendStatus.SUCCESS) {
                    failActive(
                        TtsException(
                            "ENQUEUE",
                            "The TTS engine rejected the ${job.operation.value} request",
                            result,
                        ),
                    )
                }
            },
            onError = { error ->
                if (
                    activeJob === job &&
                    job.currentPlatformId == platformId &&
                    backend === current &&
                    backendGeneration == generation
                ) {
                    failActive(error)
                }
            },
        )
    }

    private fun scheduleOutputSizeGuard(job: Job) {
        job.outputGuardTask = deadlineScheduler.schedule(
            OUTPUT_SIZE_CHECK_INTERVAL_MILLIS,
        ) {
            val tooLarge = requireNotNull(job.output).stagingFile.length() >
                MAX_SYNTHESIS_FILE_BYTES
            tryPostCallback {
                if (!accepting.get() || activeJob !== job || job.controller.isDone) {
                    return@tryPostCallback
                }
                if (tooLarge) {
                    failActive(
                        TtsException(
                            "OUTPUT_LIMIT",
                            "TTS synthesis output exceeded $MAX_SYNTHESIS_FILE_BYTES bytes",
                        ),
                        retireBackend = true,
                    )
                } else {
                    try {
                        scheduleOutputSizeGuard(job)
                    } catch (error: Throwable) {
                        if (accepting.get() && activeJob === job) {
                            failActive(
                                error.toTtsException("SCHEDULING"),
                                retireBackend = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopUtterance(id: String) {
        if (!accepting.get()) return
        scheduler.post {
            val current = activeJob
            if (current?.controller?.id == id) {
                activeJob = null
                clearPlatformCallback(current)
                stopJob(current, "cancelled", cancelled = true)
                shutdownBackend()
                dispatchNext()
                return@post
            }
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (job.controller.id != id) continue
                iterator.remove()
                queuedCharacters -= job.text?.length?.toLong() ?: 0L
                stopJob(job, "cancelled", cancelled = true)
                publishStatus()
                return@post
            }
        }
    }

    private fun abortAll(reason: String, cancelled: Boolean) {
        val current = activeJob
        if (current != null) {
            activeJob = null
            clearPlatformCallback(current)
            stopJob(current, reason, cancelled)
        }
        queue.forEach { job -> stopJob(job, reason, cancelled) }
        queue.clear()
        queuedCharacters = 0L
        if (current != null || backendState == BackendState.INITIALIZING) {
            shutdownBackend()
        }
    }

    private fun stopJob(job: Job, reason: String, cancelled: Boolean) {
        job.cancelGuards()
        val interruption = TtsInterruptedException(reason, "TTS operation was $reason")
        discardOutput(job)?.let(interruption::addSuppressed)
        runCatching {
            job.controller.stop(
                linkedMapOf(
                    "id" to job.controller.id,
                    "operation" to job.operation.value,
                    "status" to "stopped",
                    "reason" to reason,
                ),
                interruption,
                cancelled,
            )
        }
    }

    private fun handleStart(utteranceId: String) {
        val job = activeJob.takeIf { it?.currentPlatformId == utteranceId } ?: return
        if (job.startedAt == null) {
            job.startedAt = clock()
            val delivered = deliverActiveEvent(job) {
                job.controller.start(job.startValue())
            }
            if (!delivered) return
        }
        if (job.operation == Operation.SPEECH) {
            val delivered = deliverActiveEvent(job) {
                job.controller.chunk(job.chunkValue())
            }
            if (!delivered) return
        }
        publishStatus()
    }

    private fun handleDone(utteranceId: String) {
        val job = activeJob.takeIf { it?.currentPlatformId == utteranceId } ?: return
        clearPlatformCallback(job, utteranceId)
        if (job.operation == Operation.SPEECH && job.chunkIndex + 1 < job.chunks.size) {
            job.chunkIndex += 1
            startActivePlatformOperation(requireNotNull(backend), job)
            return
        }
        if (job.operation == Operation.FILE) {
            try {
                val output = requireNotNull(job.output)
                if (output.stagingFile.length() > MAX_SYNTHESIS_FILE_BYTES) {
                    throw TtsException(
                        "OUTPUT_LIMIT",
                        "TTS synthesis output exceeded $MAX_SYNTHESIS_FILE_BYTES bytes",
                    )
                }
                synchronized(lifecycleLock) {
                    if (!accepting.get()) {
                        finishActiveWithoutResult()
                        return
                    }
                    val stagingReleased = output.commit()
                    if (stagingReleased) liveOutputs.remove(output)
                }
            } catch (error: Throwable) {
                failActive(
                    if (error is TtsException) error else TtsException(
                        "OUTPUT",
                        "Unable to publish the synthesized TTS file",
                        cause = error,
                    ),
                )
                return
            }
        }
        completeActive()
    }

    private fun handleStop(utteranceId: String, interrupted: Boolean) {
        val job = activeJob.takeIf { it?.currentPlatformId == utteranceId } ?: return
        clearPlatformCallback(job, utteranceId)
        activeJob = null
        stopJob(
            job,
            if (interrupted) "interrupted" else "flushed",
            cancelled = false,
        )
        dispatchNext()
    }

    private fun handleError(utteranceId: String, errorCode: Int) {
        if (activeJob?.currentPlatformId != utteranceId) return
        failActive(ttsPlatformError(errorCode))
    }

    private fun handleRange(
        utteranceId: String,
        start: Int,
        end: Int,
        frame: Int,
    ) {
        val job = activeJob.takeIf {
            it?.currentPlatformId == utteranceId && it.operation == Operation.SPEECH
        } ?: return
        val chunk = job.currentChunk()
        if (start < 0 || end < start || end > chunk.length) return
        deliverActiveEvent(job) {
            job.controller.range(
                linkedMapOf(
                    "id" to job.controller.id,
                    "start" to (chunk.start + start),
                    "end" to (chunk.start + end),
                    "frame" to frame,
                    "chunk" to job.chunkIndex,
                ),
            )
        }
    }

    private fun handleBeginSynthesis(
        utteranceId: String,
        format: TtsSynthesisFormat,
    ) {
        val job = activeJob.takeIf { it?.currentPlatformId == utteranceId } ?: return
        deliverActiveEvent(job) {
            job.controller.synthesis(
                linkedMapOf(
                    "id" to job.controller.id,
                    "sampleRate" to format.sampleRateInHz,
                    "encoding" to format.audioEncoding.name.lowercase(),
                    "channels" to format.channelCount,
                    "chunk" to job.chunkIndex,
                ),
            )
        }
    }

    private fun handleAudio(utteranceId: String, audio: ByteArray) {
        val job = activeJob.takeIf { it?.currentPlatformId == utteranceId } ?: return
        try {
            job.controller.audio(
                linkedMapOf(
                    "id" to job.controller.id,
                    "data" to audio,
                    "chunk" to job.chunkIndex,
                ),
            )
        } catch (error: Throwable) {
            failActive(
                error.toTtsException("AUDIO_BACKPRESSURE"),
                retireBackend = true,
            )
        }
    }

    private fun completeActive() {
        val job = activeJob ?: return
        job.cancelGuards()
        val result = linkedMapOf<String, Any?>(
            "id" to job.controller.id,
            "operation" to job.operation.value,
            "status" to "done",
            "engine" to job.selection?.engineName,
            "requestedEngine" to normalizeEngineKey(backendKey),
            "voice" to job.selection?.selectedVoice?.name,
            "locale" to job.selection?.languageTag,
            "usage" to job.selection?.effectiveAudioUsage?.value,
            "chunks" to job.chunks.size.coerceAtLeast(1),
            "characters" to (job.text?.length ?: 0),
            "duration" to (clock() - (job.startedAt ?: job.submittedAt)).coerceAtLeast(0L),
            "path" to job.output?.targetPath,
        )
        activeJob = null
        clearPlatformCallback(job)
        runCatching { job.controller.complete(result) }
        dispatchNext()
    }

    private fun failActive(
        error: TtsException,
        retireBackend: Boolean = false,
    ) {
        val job = activeJob ?: return
        job.cancelGuards()
        discardOutput(job)?.let(error::addSuppressed)
        activeJob = null
        clearPlatformCallback(job)
        if (retireBackend || backendState == BackendState.INITIALIZING) {
            shutdownBackend()
        }
        runCatching { job.controller.fail(error) }
        dispatchNext()
    }

    private fun finishActiveWithoutResult() {
        val job = activeJob ?: return
        job.cancelGuards()
        discardOutput(job)
        activeJob = null
        clearPlatformCallback(job)
        dispatchNext()
    }

    private fun deliverActiveEvent(
        job: Job,
        action: () -> Unit,
    ): Boolean {
        return try {
            action()
            true
        } catch (error: Throwable) {
            if (activeJob === job) {
                failActive(
                    error.toTtsException("EVENT_BACKPRESSURE"),
                    retireBackend = true,
                )
            }
            false
        }
    }

    private fun discardOutput(job: Job): Throwable? {
        val output = job.output ?: return null
        return discardOutput(output)
    }

    private fun discardOutput(output: TtsOutputFile): Throwable? {
        val failure = runCatching(output::discard).exceptionOrNull()
        if (failure == null) {
            liveOutputs.remove(output)
        }
        return failure
    }

    private fun clearPlatformCallback(
        job: Job?,
        expectedUtteranceId: String? = job?.currentPlatformId,
    ) {
        if (job == null) {
            callbackPolicy.set(null)
            terminalCallbackId.set(null)
            return
        }
        val utteranceId = expectedUtteranceId
        job.currentPlatformId = null
        if (utteranceId != null) {
            callbackPolicy.get()
                ?.takeIf { it.utteranceId == utteranceId }
                ?.let { callbackPolicy.compareAndSet(it, null) }
            terminalCallbackId.compareAndSet(utteranceId, null)
        }
    }

    private fun ensureBackend(
        requestedEngine: String?,
        onReady: (TtsBackend) -> Unit,
        onError: (TtsException) -> Unit,
    ) {
        if (!accepting.get()) {
            onError(TtsException("CLOSED", "TTS service is closed"))
            return
        }
        val key = requestedEngine.normalizeEngine()
        when {
            backendState == BackendState.READY && backendKey == key -> {
                onReady(requireNotNull(backend))
                return
            }
            backendState == BackendState.INITIALIZING && backendKey == key -> {
                initWaiters += BackendWaiter(onReady, onError)
                return
            }
            backendState == BackendState.INITIALIZING -> {
                onError(
                    TtsException(
                        "ENGINE_BUSY",
                        "A different TTS engine is currently initializing",
                    ),
                )
                return
            }
        }

        shutdownBackend()
        backendState = BackendState.INITIALIZING
        backendKey = key
        val generation = ++backendGeneration
        initWaiters += BackendWaiter(onReady, onError)
        val initStatus = AtomicInteger(INIT_STATUS_PENDING)
        val createdReference = AtomicReference<TtsBackend?>(null)

        try {
            initTimeout = deadlineScheduler.schedule(INIT_TIMEOUT_MILLIS) {
                tryPostCallback {
                    if (
                        backendState == BackendState.INITIALIZING &&
                        backendGeneration == generation
                    ) {
                        failInitialization(
                            TtsException(
                                "INITIALIZATION_TIMEOUT",
                                "TTS engine initialization timed out",
                            ),
                        )
                    }
                }
            }
            enqueueNativeCall(
                operation = "initialize",
                errorCode = "INITIALIZATION",
                generation = generation,
                expectedBackend = null,
                action = {
                    backendFactory.create(
                        normalizeEngineKey(key),
                        progressListener,
                    ) { status ->
                        if (initStatus.compareAndSet(INIT_STATUS_PENDING, status)) {
                            val scheduled = tryPostCallback {
                                maybeFinishInitialization(
                                    generation,
                                    initStatus,
                                    createdReference,
                                )
                            }
                            if (!scheduled) {
                                createdReference.get()?.let(::disposeBackend)
                            }
                        }
                    }.also(createdReference::set)
                },
                onSuccess = { created ->
                    val installed = synchronized(lifecycleLock) {
                        if (
                            accepting.get() &&
                            backendState == BackendState.INITIALIZING &&
                            backendGeneration == generation
                        ) {
                            backend = created
                            backendLease.set(created)
                            true
                        } else {
                            false
                        }
                    }
                    if (!installed) {
                        disposeBackend(created)
                        return@enqueueNativeCall
                    }
                    maybeFinishInitialization(
                        generation,
                        initStatus,
                        createdReference,
                    )
                },
                onError = { error ->
                    if (
                        backendState == BackendState.INITIALIZING &&
                        backendGeneration == generation
                    ) {
                        failInitialization(error)
                    }
                },
                onDropped = ::disposeBackend,
            )
        } catch (error: Throwable) {
            if (
                backendState == BackendState.INITIALIZING &&
                backendGeneration == generation
            ) {
                failInitialization(error.toTtsException("SCHEDULING"))
            }
        }
        publishStatus()
    }

    private fun maybeFinishInitialization(
        generation: Long,
        initStatus: AtomicInteger,
        createdReference: AtomicReference<TtsBackend?>,
    ) {
        if (
            backendState != BackendState.INITIALIZING ||
            backendGeneration != generation
        ) {
            createdReference.get()?.let(::disposeBackend)
            return
        }
        val status = initStatus.get().takeUnless { it == INIT_STATUS_PENDING } ?: return
        val current = backend ?: return
        if (createdReference.get() !== current) return
        initTimeout?.cancel()
        initTimeout = null
        if (status != TtsBackendStatus.SUCCESS) {
            failInitialization(
                TtsException(
                    "INITIALIZATION",
                    "TTS engine initialization failed",
                    status,
                ),
            )
            return
        }
        backendState = BackendState.READY
        effectiveEngineName = normalizeEngineKey(backendKey)
        val waiters = initWaiters.toList()
        initWaiters.clear()
        waiters.forEach { waiter ->
            if (
                backend !== current ||
                backendState != BackendState.READY ||
                backendGeneration != generation
            ) {
                runCatching {
                    waiter.onError(
                        TtsException("ABORTED", "TTS engine initialization was replaced"),
                    )
                }
            } else {
                runCatching { waiter.onReady(current) }
                    .onFailure { failure ->
                        runCatching {
                            waiter.onError(failure.toTtsException("INITIALIZATION"))
                        }
                    }
            }
        }
        publishStatus()
    }

    private fun failInitialization(error: TtsException) {
        shutdownBackend(
            waiterError = error,
            terminalState = if (accepting.get()) {
                BackendState.FAILED
            } else {
                BackendState.CLOSED
            },
        )
        publishStatus()
    }

    private fun shutdownBackend(
        alreadyDisposed: TtsBackend? = null,
        waiterError: TtsException = TtsException(
            "ABORTED",
            "TTS engine initialization was replaced",
        ),
        terminalState: BackendState? = null,
    ) {
        initTimeout?.cancel()
        initTimeout = null
        val waiters = initWaiters.toList()
        initWaiters.clear()
        val retiredGeneration = backendGeneration
        val current = backend
        backend = null
        if (current != null) backendLease.compareAndSet(current, null)
        backendKey = null
        lastSelection.set(null)
        effectiveEngineName = null
        // Waiter callbacks may immediately initialize a new generation, so publish this
        // generation's terminal state before abandoning calls or notifying any waiter.
        backendState = terminalState ?: if (backendState == BackendState.CLOSED) {
            BackendState.CLOSED
        } else {
            BackendState.NEW
        }
        backendGeneration += 1L
        abandonNativeCalls(retiredGeneration, waiterError)
        if (current != null && current !== alreadyDisposed) {
            disposeBackend(current)
        }
        if (waiters.isNotEmpty()) {
            waiters.forEach { waiter -> runCatching { waiter.onError(waiterError) } }
        }
    }

    private fun <T> enqueueNativeCall(
        operation: String,
        errorCode: String = operation.uppercase(Locale.ROOT),
        generation: Long,
        expectedBackend: TtsBackend?,
        action: () -> T,
        onSuccess: (T) -> Unit,
        onError: (TtsException) -> Unit,
        onDropped: (T) -> Unit = {},
    ) {
        if (nativeCalls.size >= MAX_PENDING_NATIVE_CALLS) {
            onError(
                TtsException(
                    "NATIVE_BACKPRESSURE",
                    "Too many TTS native calls are waiting for the active engine",
                ),
            )
            return
        }
        @Suppress("UNCHECKED_CAST")
        val call = NativeCall(
            id = ++nextNativeCallId,
            operation = operation,
            errorCode = errorCode,
            generation = generation,
            expectedBackend = expectedBackend,
            action = action,
            onSuccess = { value -> onSuccess(value as T) },
            onError = onError,
            onDropped = { value -> onDropped(value as T) },
        )
        nativeCalls.addLast(call)
        dispatchNativeCall()
    }

    private fun dispatchNativeCall() {
        if (activeNativeCall != null) return
        while (nativeCalls.isNotEmpty()) {
            val call = nativeCalls.removeFirst()
            if (
                !accepting.get() ||
                call.generation != backendGeneration ||
                (
                    call.expectedBackend != null &&
                        backend !== call.expectedBackend
                    )
            ) {
                runCatching {
                    call.onError(TtsException("ABORTED", "TTS native call was replaced"))
                }
                continue
            }
            activeNativeCall = call
            val accepted = blockingCallRunner.execute {
                val result = runCatching(call.action)
                val scheduled = tryPostCallback {
                    finishNativeCall(call, result)
                }
                if (!scheduled) {
                    result.getOrNull()?.let { value ->
                        runCatching { call.onDropped(value) }
                    }
                }
            }
            if (accepted) return

            activeNativeCall = null
            runCatching {
                call.onError(
                    TtsException(
                        "NATIVE_BACKPRESSURE",
                        "The TTS native-call limit of " +
                            "${AndroidTtsBlockingCallRunner.MAX_IN_FLIGHT_CALLS} was reached",
                    ),
                )
            }
        }
    }

    private fun finishNativeCall(
        call: NativeCall,
        result: Result<Any?>,
    ) {
        if (activeNativeCall !== call) {
            result.getOrNull()?.let { value ->
                runCatching { call.onDropped(value) }
            }
            return
        }
        activeNativeCall = null
        if (
            !accepting.get() ||
            call.generation != backendGeneration ||
            (
                call.expectedBackend != null &&
                    backend !== call.expectedBackend
                )
        ) {
            result.getOrNull()?.let { value ->
                runCatching { call.onDropped(value) }
            }
            dispatchNativeCall()
            return
        }
        result.fold(
            onSuccess = { value ->
                try {
                    call.onSuccess(value)
                } catch (error: Throwable) {
                    runCatching {
                        call.onError(error.toTtsException(call.errorCode))
                    }
                }
            },
            onFailure = { error ->
                runCatching {
                    call.onError(error.toTtsException(call.errorCode))
                }
            },
        )
        dispatchNativeCall()
    }

    private fun abandonNativeCalls(
        generation: Long,
        error: TtsException,
    ) {
        val abandoned = mutableListOf<NativeCall>()
        activeNativeCall?.takeIf { it.generation == generation }?.let { call ->
            activeNativeCall = null
            abandoned += call
        }
        val iterator = nativeCalls.iterator()
        while (iterator.hasNext()) {
            val call = iterator.next()
            if (call.generation != generation) continue
            iterator.remove()
            abandoned += call
        }
        abandoned.forEach { call ->
            runCatching { call.onError(error) }
        }
        dispatchNativeCall()
    }

    private fun disposeBackend(current: TtsBackend) {
        runCatching(current::deactivate)
        blockingCallRunner.executeCleanup {
            runCatching(current::shutdown)
        }
    }

    private fun query(
        requestedEngine: String?,
        independent: (() -> Any?)? = null,
        operation: (TtsBackend) -> Any? = { null },
    ): ScriptPromiseAdapter {
        synchronized(lifecycleLock) {
            requireOpen()
            val ingressReservation = queryIngress.tryAcquire()
                ?: throw TtsException(
                    "BACKPRESSURE",
                    "Too many TTS queries are waiting to enter the command queue",
                )
            lateinit var pending: PendingResult
            try {
                pending = PendingResult(client.createResultDispatcher()) {
                    liveQueries.remove(pending)
                    ingressReservation.release()
                }
            } catch (error: Throwable) {
                ingressReservation.release()
                throw error
            }
            liveQueries += pending
            try {
                pending.installTimeout(
                    deadlineScheduler.schedule(QUERY_TIMEOUT_MILLIS) {
                        tryPostCallback {
                            if (pending.isDone) return@tryPostCallback
                            val error = TtsException(
                                "QUERY_TIMEOUT",
                                "TTS query timed out after $QUERY_TIMEOUT_MILLIS milliseconds",
                            )
                            val generation = pending.backendGeneration
                            pending.reject(error)
                            if (
                                generation != null &&
                                backendGeneration == generation
                            ) {
                                if (activeJob != null) {
                                    failActive(
                                        TtsException(
                                            "BACKEND_TIMEOUT",
                                            "The active TTS engine was retired after a query timeout",
                                        ),
                                        retireBackend = true,
                                    )
                                } else {
                                    shutdownBackend()
                                    publishStatus()
                                }
                            }
                        }
                    },
                )
                scheduler.post {
                    if (pending.isDone) return@post
                    if (independent != null) {
                        val accepted = blockingCallRunner.execute {
                            val result = runCatching(independent)
                            tryPostCallback {
                                if (!pending.isDone) result.fold(pending::resolve) { pending.reject(it.toTtsException("QUERY")) }
                            }
                        }
                        if (!accepted) pending.reject(TtsException("NATIVE_BACKPRESSURE", "TTS native-call capacity is exhausted"))
                        return@post
                    }
                    val requestedKey = requestedEngine.normalizeEngine()
                    val currentJob = activeJob
                    if (
                        currentJob != null &&
                        currentJob.options.engine.normalizeEngine() != requestedKey
                    ) {
                        pending.reject(
                            TtsException(
                                "ENGINE_BUSY",
                                "Cannot query a different TTS engine while speech is active",
                            ),
                        )
                        return@post
                    }
                    ensureBackend(
                        requestedEngine,
                        onReady = { current ->
                            if (pending.isDone) return@ensureBackend
                            val generation = backendGeneration
                            pending.backendGeneration = generation
                            enqueueNativeCall(
                                operation = "query",
                                errorCode = "QUERY",
                                generation = generation,
                                expectedBackend = current,
                                action = {
                                    val value = operation(current)
                                    BackendQueryResult(
                                        engineName = current.engineName(),
                                        value = value,
                                    )
                                },
                                onSuccess = { result ->
                                    if (
                                        !pending.isDone &&
                                        backend === current &&
                                        backendGeneration == generation
                                    ) {
                                        effectiveEngineName =
                                            result.engineName ?: effectiveEngineName
                                        pending.resolve(result.value)
                                        publishStatus()
                                    }
                                },
                                onError = { error ->
                                    if (!pending.isDone) pending.reject(error)
                                },
                            )
                        },
                        onError = pending::reject,
                    )
                }
            } catch (error: Throwable) {
                liveQueries.remove(pending)
                pending.cancelSilently()
                throw error
            }
            return pending.adapter
        }
    }

    private fun readyValue(
        current: TtsBackend,
        requestedEngine: String?,
    ): Map<String, Any?> {
        val engines = current.engines()
        val selection = lastSelection.get()
        return linkedMapOf(
            "state" to "ready",
            "engine" to current.engineName(),
            "requestedEngine" to normalizeEngineKey(requestedEngine.normalizeEngine()),
            "defaultEngine" to engines.firstOrNull(TtsEngine::isDefault)?.name,
            "voice" to selection?.selectedVoice?.toPublicValue(),
            "locale" to selection?.languageTag,
        )
    }

    private fun postProgressEvent(
        utteranceId: String,
        action: () -> Unit,
    ) {
        val policy = callbackPolicy.get()
        if (
            !accepting.get() ||
            policy?.utteranceId != utteranceId ||
            terminalCallbackId.get() == utteranceId
        ) {
            return
        }
        val reservation = progressIngress.tryAcquire() ?: return
        val scheduled = tryPostCallback {
            try {
                if (
                    accepting.get() &&
                    callbackPolicy.get()?.utteranceId == utteranceId
                ) {
                    handleCallbackAction(action)
                }
            } finally {
                reservation.release()
            }
        }
        if (!scheduled) reservation.release()
    }

    private fun postAudioEvent(
        utteranceId: String,
        audio: ByteArray,
    ) {
        val policy = callbackPolicy.get()
        if (
            !accepting.get() ||
            policy?.utteranceId != utteranceId ||
            !policy.audioEvents ||
            terminalCallbackId.get() == utteranceId
        ) {
            return
        }
        val reservation = audioIngress.tryAcquire(audio.size.toLong())
        if (reservation == null) {
            postIngressFailure(
                utteranceId,
                TtsException(
                    "AUDIO_BACKPRESSURE",
                    "TTS platform callback ingress exceeded " +
                        "$MAX_PENDING_AUDIO_EVENTS events or $MAX_PENDING_AUDIO_BYTES bytes",
                ),
            )
            return
        }
        val scheduled = tryPostCallback {
            try {
                if (
                    accepting.get() &&
                    callbackPolicy.get()?.utteranceId == utteranceId
                ) {
                    handleCallbackAction { handleAudio(utteranceId, audio) }
                }
            } finally {
                reservation.release()
            }
        }
        if (!scheduled) reservation.release()
    }

    private fun postTerminalEvent(
        utteranceId: String,
        action: () -> Unit,
    ) {
        if (
            !accepting.get() ||
            callbackPolicy.get()?.utteranceId != utteranceId ||
            !terminalCallbackId.compareAndSet(null, utteranceId)
        ) {
            return
        }
        val scheduled = tryPostCallback {
            if (
                accepting.get() &&
                callbackPolicy.get()?.utteranceId == utteranceId
            ) {
                handleCallbackAction(action)
            }
        }
        if (!scheduled) {
            terminalCallbackId.compareAndSet(utteranceId, null)
        }
    }

    private fun postIngressFailure(
        utteranceId: String,
        error: TtsException,
    ) {
        if (
            callbackPolicy.get()?.utteranceId != utteranceId ||
            !terminalCallbackId.compareAndSet(null, utteranceId)
        ) {
            return
        }
        val scheduled = tryPostCallback {
            if (
                accepting.get() &&
                callbackPolicy.get()?.utteranceId == utteranceId
            ) {
                failActive(error, retireBackend = true)
            }
        }
        if (!scheduled) {
            terminalCallbackId.compareAndSet(utteranceId, null)
        }
    }

    private fun tryPostCallback(action: () -> Unit): Boolean {
        if (!accepting.get()) return false
        return runCatching {
            scheduler.post(action)
            true
        }.getOrDefault(false)
    }

    private fun handleCallbackAction(action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            runCatching {
                failActive(error.toTtsException("CALLBACK"))
            }
        }
    }

    private fun publishStatus() {
        val state = when {
            !accepting.get() || backendState == BackendState.CLOSED -> "closed"
            backendState == BackendState.INITIALIZING -> "initializing"
            activeJob != null -> "working"
            backendState == BackendState.READY -> "ready"
            backendState == BackendState.FAILED -> "failed"
            else -> "idle"
        }
        statusSnapshot.set(
            TtsStatus(
                state = state,
                engine = lastSelection.get()?.engineName ?: effectiveEngineName,
                requestedEngine = normalizeEngineKey(backendKey),
                activeId = activeJob?.controller?.id,
                operation = activeJob?.operation?.value,
                queued = queue.size,
                isSpeaking = activeJob?.let {
                    it.startedAt != null && it.operation == Operation.SPEECH
                } == true,
            ),
        )
    }

    private fun requireOpen() {
        check(accepting.get()) { "TTS service is closed" }
    }

    private data class Job(
        val controller: TtsTaskController,
        val operation: Operation,
        val text: String?,
        val chunks: List<TtsTextChunk>,
        val options: TtsOptions,
        val silenceMillis: Long?,
        val output: TtsOutputFile?,
        val submittedAt: Long,
        var startedAt: Long? = null,
        var chunkIndex: Int = 0,
        var platformGeneration: Int = 0,
        var currentPlatformId: String? = null,
        var selection: TtsConfigurationResult? = null,
        var timeoutTask: TtsScheduledTask? = null,
        var outputGuardTask: TtsScheduledTask? = null,
    ) {

        fun currentChunk(): TtsTextChunk = chunks[chunkIndex]

        fun cancelGuards() {
            timeoutTask?.cancel()
            timeoutTask = null
            outputGuardTask?.cancel()
            outputGuardTask = null
        }

        fun effectiveTimeoutMillis(): Long {
            options.timeoutMillis?.let { return it }
            return when (operation) {
                Operation.SPEECH ->
                    scaledTextTimeout(AUTO_SPEECH_MILLIS_PER_CHARACTER)
                Operation.FILE ->
                    scaledTextTimeout(AUTO_FILE_MILLIS_PER_CHARACTER)
                Operation.SILENCE ->
                    requireNotNull(silenceMillis) + AUTO_TIMEOUT_BASE_MILLIS
            }
        }

        private fun scaledTextTimeout(millisecondsPerCharacter: Long): Long {
            val synthesisMillis = (text?.length ?: 0).toDouble() *
                millisecondsPerCharacter.toDouble() /
                options.rate.toDouble()
            return (AUTO_TIMEOUT_BASE_MILLIS.toDouble() + synthesisMillis)
                .coerceAtMost(TtsOptionsParser.MAX_TIMEOUT_MILLIS.toDouble())
                .toLong()
        }

        fun startValue(): Map<String, Any?> = linkedMapOf(
            "id" to controller.id,
            "operation" to operation.value,
            "voice" to selection?.selectedVoice?.name,
            "locale" to selection?.languageTag,
            "chunks" to chunks.size.coerceAtLeast(1),
        )

        fun chunkValue(): Map<String, Any?> {
            val chunk = currentChunk()
            return linkedMapOf(
                "id" to controller.id,
                "index" to chunkIndex,
                "count" to chunks.size,
                "start" to chunk.start,
                "end" to chunk.end,
            )
        }
    }

    private data class BackendWaiter(
        val onReady: (TtsBackend) -> Unit,
        val onError: (TtsException) -> Unit,
    )

    private data class BackendQueryResult(
        val engineName: String?,
        val value: Any?,
    )

    private data class NativeCall(
        val id: Long,
        val operation: String,
        val errorCode: String,
        val generation: Long,
        val expectedBackend: TtsBackend?,
        val action: () -> Any?,
        val onSuccess: (Any?) -> Unit,
        val onError: (TtsException) -> Unit,
        val onDropped: (Any?) -> Unit,
    )

    private data class CallbackPolicy(
        val utteranceId: String,
        val audioEvents: Boolean,
    )

    private class PendingResult(
        private val dispatcher: TtsResultDispatcher,
        private val onTerminal: () -> Unit,
    ) {

        val adapter = ScriptPromiseAdapter()
        private val timeoutTask = AtomicReference<TtsScheduledTask?>(null)
        var backendGeneration: Long? = null

        val isDone: Boolean
            get() = dispatcher.isTerminal

        fun installTimeout(task: TtsScheduledTask) {
            check(timeoutTask.compareAndSet(null, task)) {
                "TTS query timeout was already installed"
            }
            if (isDone) timeoutTask.getAndSet(null)?.cancel()
        }

        fun resolve(value: Any?) {
            dispatcher.resolve(adapter, value, ::terminalCleanup)
        }

        fun reject(error: Throwable) {
            dispatcher.reject(adapter, error, ::terminalCleanup)
        }

        fun cancelSilently() {
            dispatcher.cancelSilently(::terminalCleanup)
        }

        private fun terminalCleanup() {
            timeoutTask.getAndSet(null)?.cancel()
            onTerminal()
        }
    }

    private data class TtsStatus(
        val state: String = "idle",
        val engine: String? = null,
        val requestedEngine: String? = null,
        val activeId: String? = null,
        val operation: String? = null,
        val queued: Int = 0,
        val isSpeaking: Boolean = false,
    ) {

        fun toPublicValue(): Map<String, Any?> = linkedMapOf(
            "state" to state,
            "engine" to engine,
            "requestedEngine" to requestedEngine,
            "activeId" to activeId,
            "operation" to operation,
            "queued" to queued,
            "isSpeaking" to isSpeaking,
            "isBusy" to (activeId != null),
        )
    }

    private enum class Operation(val value: String) {
        SPEECH("speech"),
        SILENCE("silence"),
        FILE("file"),
    }

    private enum class BackendState {
        NEW,
        INITIALIZING,
        READY,
        FAILED,
        CLOSED,
    }

    companion object {

        const val MAX_SILENCE_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_QUEUED_JOBS = 128
        const val MAX_QUEUED_CHARACTERS = 2_000_000L
        const val MAX_PENDING_JOB_COMMANDS = 256
        const val MAX_PENDING_JOB_CHARACTERS = 4_000_000L
        const val MAX_PENDING_QUERIES = 64
        const val MAX_PENDING_NATIVE_CALLS = 128
        const val MAX_PENDING_CONTROL_COMMANDS = 64
        const val MAX_PENDING_PROGRESS_EVENTS = 512
        const val MAX_PENDING_AUDIO_EVENTS = 256
        const val MAX_PENDING_AUDIO_BYTES = 4 * 1024 * 1024
        const val MAX_SYNTHESIS_FILE_BYTES = 512L * 1024L * 1024L
        const val INIT_TIMEOUT_MILLIS = 15_000L
        const val QUERY_TIMEOUT_MILLIS = 30_000L
        const val OUTPUT_SIZE_CHECK_INTERVAL_MILLIS = 250L
        const val AUTO_TIMEOUT_BASE_MILLIS = 60_000L
        const val AUTO_SPEECH_MILLIS_PER_CHARACTER = 250L
        const val AUTO_FILE_MILLIS_PER_CHARACTER = 500L

        private fun String?.normalizeEngine(): String =
            this?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_ENGINE_KEY

        private fun normalizeEngineKey(key: String?): String? =
            key?.takeUnless { it == DEFAULT_ENGINE_KEY }

        private fun TtsEngine.toPublicValue(): Map<String, Any?> = linkedMapOf(
            "name" to name,
            "label" to label,
            "default" to isDefault,
            "requested" to isRequested,
        )

        private fun TtsVoice.toPublicValue(): Map<String, Any?> = linkedMapOf(
            "name" to name,
            "locale" to languageTag,
            "quality" to quality,
            "latency" to latency,
            "networkRequired" to networkRequired,
            "offline" to isOffline,
            "notInstalled" to notInstalled,
            "features" to features.toList(),
        )

        private fun TtsLanguageAvailability.toPublicValue(): Map<String, Any?> =
            linkedMapOf(
                "locale" to languageTag,
                "available" to isAvailable,
                "support" to support.name.lowercase(),
                "statusCode" to statusCode,
            )

        private fun Throwable.toTtsException(defaultCode: String): TtsException = when (this) {
            is TtsException -> this
            is TtsBackendException -> TtsException(
                code = defaultCode,
                message = message ?: "TTS backend operation $operation failed",
                errorCode = statusCode,
                cause = this,
            )
            else -> TtsException(
                code = defaultCode,
                message = message ?: "TTS operation failed",
                cause = this,
            )
        }

        private const val DEFAULT_ENGINE_KEY = "\u0000default"
        private const val INIT_STATUS_PENDING = Int.MIN_VALUE
    }
}
