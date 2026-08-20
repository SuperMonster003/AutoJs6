package org.autojs.autojs.runtime.api.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.autojs.autojs.core.ai.AiProviderDefinition
import org.autojs.autojs.core.ai.AiProviderProfileInspection
import org.autojs.autojs.core.ai.AiProviderProfileSummary
import org.autojs.autojs.core.ai.AiProviderVault
import org.autojs.autojs.core.ai.AiResolvedProviderProfile
import org.autojs.autojs.core.ai.transport.AiHttpErrors
import org.autojs.autojs.core.ai.transport.AiHttpException
import org.autojs.autojs.core.ai.transport.AiJsonResponseReader
import org.autojs.autojs.core.ai.transport.AiProviderConfig
import org.autojs.autojs.core.ai.transport.AiProviderError
import org.autojs.autojs.core.ai.transport.AiRequestFactory
import org.autojs.autojs.core.ai.transport.AiResponse
import org.autojs.autojs.core.ai.transport.AiStreamAccumulator
import org.autojs.autojs.core.ai.transport.AiStreamChunk
import org.autojs.autojs.core.ai.transport.AiStreamParser
import org.autojs.autojs.core.ai.transport.AiStreamProtocolException
import org.autojs.autojs.core.ai.transport.AiToolCall
import org.autojs.autojs.core.ai.transport.AiUsage
import org.autojs.autojs.core.ai.transport.SseReader
import org.autojs.autojs.core.plugin.ai.AiTextPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiTextPluginModelListRequest
import org.autojs.autojs.core.plugin.ai.AiTextPluginModelListRunner
import org.autojs.autojs.core.plugin.ai.AiTextPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiTextProviderComponent
import org.autojs.autojs.core.plugin.ai.AndroidAiTextPluginAskRunner
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.ai.AiStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class AiRequestInput(
    val messages: JsonArray,
    val options: JsonObject,
)

/**
 * Per-script-runtime AI client.
 *
 * Provider credentials are resolved only while constructing a request. Redirects and automatic
 * retries are disabled so a credential-bearing POST cannot be replayed or redirected to another
 * origin. Every Rhino conversion is delegated to [AiAsyncDispatcher].
 */
class AiService private constructor(
    context: Context,
    private val scriptRuntime: ScriptRuntime,
    sourceClient: OkHttpClient,
    pluginAskRuntimeFactory: () -> AiPluginAskRuntime,
    pluginStreamRuntimeFactory: () -> AiPluginStreamRuntime,
    pluginModelListRuntimeFactory: () -> AiPluginModelListRuntime,
) {

    constructor(
        context: Context,
        scriptRuntime: ScriptRuntime,
        sourceClient: OkHttpClient,
    ) : this(
        context,
        scriptRuntime,
        sourceClient,
        {
            AiPluginAskRuntime(
                runnerProvider = {
                    AndroidAiTextPluginAskRunner.create(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
            )
        },
        {
            AiPluginStreamRuntime(
                runnerProvider = {
                    AndroidAiTextPluginAskRunner.createStream(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
                bridges = scriptRuntime.bridges,
            )
        },
        {
            AiPluginModelListRuntime(
                runnerProvider = {
                    AndroidAiTextPluginAskRunner.createModelList(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
            )
        },
    )

    internal constructor(
        context: Context,
        scriptRuntime: ScriptRuntime,
        sourceClient: OkHttpClient,
        pluginAskRunner: AiTextPluginAskRunner,
        pluginDispatcherFactory: () -> AiAsyncDispatcher,
        pluginStreamRunner: AiTextPluginStreamRunner =
            AndroidAiTextPluginAskRunner.createStream(context.applicationContext),
        pluginModelListRunner: AiTextPluginModelListRunner =
            AndroidAiTextPluginAskRunner.createModelList(context.applicationContext),
    ) : this(
        context,
        scriptRuntime,
        sourceClient,
        {
            AiPluginAskRuntime(
                runnerProvider = { pluginAskRunner },
                dispatcherFactory = pluginDispatcherFactory,
            )
        },
        {
            AiPluginStreamRuntime(
                runnerProvider = { pluginStreamRunner },
                dispatcherFactory = pluginDispatcherFactory,
                bridges = scriptRuntime.bridges,
            )
        },
        {
            AiPluginModelListRuntime(
                runnerProvider = { pluginModelListRunner },
                dispatcherFactory = pluginDispatcherFactory,
            )
        },
    )

    private val vault = AiProviderVault(context)
    private val closed = AtomicBoolean(false)
    private val activePromises =
        ConcurrentHashMap<AiAsyncDispatcher, AtomicReference<Call?>>()
    private val activeStreams = ConcurrentHashMap.newKeySet<AiStream.Controller>()
    private val client = AiHttpClientFactory.create(sourceClient)
    private val pluginAskRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginAskRuntimeFactory()
    }
    private val pluginStreamRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginStreamRuntimeFactory()
    }
    private val pluginModelListRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginModelListRuntimeFactory()
    }

    fun ask(input: AiRequestInput): ScriptPromiseAdapter {
        if (AiPluginAskInputMapper.hasPluginSelection(input)) {
            return enqueuePluginAsk(input)
        }
        return enqueue(input) { response, _ -> response.text }
    }

    fun chat(input: AiRequestInput): ScriptPromiseAdapter {
        if (AiPluginAskInputMapper.hasPluginSelection(input)) {
            return enqueuePluginChat(input)
        }
        return enqueue(input) { response, metadata -> response.toPublicValue(metadata) }
    }

    fun stream(input: AiRequestInput): AiStream {
        if (AiPluginAskInputMapper.hasPluginSelection(input)) {
            return enqueuePluginStream(input)
        }
        var controllerReference: AiStream.Controller? = null
        val controller = AiStream.Controller(scriptRuntime) {
            controllerReference?.let(activeStreams::remove)
        }.also { controllerReference = it }
        val stream = controller.stream
        activeStreams.add(controller)

        val prepared = try {
            prepare(input, streaming = true)
        } catch (error: Exception) {
            controller.fail(error.toPublicError())
            return stream
        }
        if (closed.get()) {
            controller.cancelSilently()
            return stream
        }

        val call = try {
            clientFor(prepared.timeoutMillis, streaming = true)
                .newCall(prepared.request)
        } catch (error: RuntimeException) {
            controller.fail(error.toPublicError())
            return stream
        }
        controller.bindCall(call)
        if (closed.get()) {
            controller.cancelSilently()
            return stream
        }
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (closed.get()) {
                        controller.cancelSilently()
                    } else if (!controller.isDone) {
                        controller.fail(e.toPublicError())
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (closed.get()) {
                            response.close()
                            controller.cancelSilently()
                            return
                        }
                        consumeStream(response, prepared, controller)
                    } catch (error: Exception) {
                        if (closed.get()) {
                            controller.cancelSilently()
                        } else if (!controller.isDone) {
                            controller.fail(error.toPublicError())
                        }
                    }
                }
            })
        } catch (error: RuntimeException) {
            call.cancel()
            if (closed.get()) {
                controller.cancelSilently()
            } else {
                controller.fail(error.toPublicError())
            }
        }
        return stream
    }

    /**
     * Lists models of an explicitly selected local AI plugin. `options.plugin` follows the same
     * shorthand as ask/chat/stream (`true` or a partial selector picks the official plugin).
     */
    fun models(input: AiRequestInput): ScriptPromiseAdapter {
        val control = try {
            AiPluginAskInputMapper.requirePluginControl(input)
        } catch (_: Exception) {
            return pluginModelListRuntime().reject(invalidPluginRequest())
        }
        val selection = requireNotNull(control.pluginSelection)
        val request = try {
            AiTextPluginModelListRequest(
                component = AiTextProviderComponent(selection.packageName, selection.className),
                providerId = selection.providerId,
                timeoutMillis = control.timeoutMillis ?: DEFAULT_CALL_TIMEOUT_MILLIS,
            )
        } catch (_: Exception) {
            return pluginModelListRuntime().reject(invalidPluginRequest())
        }
        return pluginModelListRuntime().list(request)
    }

    fun profiles(): List<Map<String, Any?>> {
        return vault.inspectProfiles().entries.map(AiProviderProfileInspection::toPublicValue)
    }

    fun providers(): List<Map<String, Any?>> {
        return AiProviderDefinition.entries.map { provider ->
            linkedMapOf(
                "id" to provider.id,
                "defaultBaseUrl" to provider.defaultBaseUrl,
            )
        }
    }

    fun defaultProfile(): Map<String, Any?>? {
        return vault.inspectProfiles().entries
            .firstOrNull(AiProviderProfileInspection::isDefault)
            ?.toPublicValue()
    }

    fun isConfigured(): Boolean {
        return vault.listProfiles().any { profile ->
            profile.isDefault &&
                profile.model.isNotBlank() &&
                (
                    profile.hasApiKey ||
                        AiCredentialPolicy.allowsMissingApiKey(
                            profile.providerId,
                            profile.baseUrl,
                        )
                )
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            if (pluginAskRuntimeDelegate.isInitialized()) {
                pluginAskRuntimeDelegate.value.close()
            }
            if (pluginStreamRuntimeDelegate.isInitialized()) {
                pluginStreamRuntimeDelegate.value.close()
            }
            if (pluginModelListRuntimeDelegate.isInitialized()) {
                pluginModelListRuntimeDelegate.value.close()
            }
            activeStreams.toList().forEach(AiStream.Controller::cancelSilently)
            activePromises.entries.toList().forEach { (dispatcher, callReference) ->
                dispatcher.cancelSilently {
                    activePromises.remove(dispatcher, callReference)
                }
                callReference.get()?.cancel()
            }
        } finally {
            activeStreams.clear()
            activePromises.clear()
            AiHttpClientFactory.close(client)
        }
    }

    private fun enqueuePluginAsk(input: AiRequestInput): ScriptPromiseAdapter {
        val request = try {
            AiPluginAskInputMapper.map(input, DEFAULT_CALL_TIMEOUT_MILLIS)
        } catch (_: Exception) {
            return pluginAskRuntime().reject(invalidPluginRequest())
        }
        return pluginAskRuntime().ask(request)
    }

    private fun enqueuePluginChat(input: AiRequestInput): ScriptPromiseAdapter {
        val request = try {
            AiPluginAskInputMapper.map(input, DEFAULT_CALL_TIMEOUT_MILLIS)
        } catch (_: Exception) {
            return pluginAskRuntime().reject(invalidPluginRequest())
        }
        return pluginAskRuntime().chat(request)
    }

    private fun enqueuePluginStream(input: AiRequestInput): AiStream {
        val request = try {
            AiPluginAskInputMapper.map(input, DEFAULT_CALL_TIMEOUT_MILLIS)
        } catch (_: Exception) {
            return pluginStreamRuntime().reject(invalidPluginRequest())
        }
        return pluginStreamRuntime().stream(request)
    }

    private fun pluginAskRuntime(): AiPluginAskRuntime =
        pluginAskRuntimeDelegate.value.also { runtime ->
            if (closed.get()) runtime.close()
        }

    private fun pluginStreamRuntime(): AiPluginStreamRuntime =
        pluginStreamRuntimeDelegate.value.also { runtime ->
            if (closed.get()) runtime.close()
        }

    private fun pluginModelListRuntime(): AiPluginModelListRuntime =
        pluginModelListRuntimeDelegate.value.also { runtime ->
            if (closed.get()) runtime.close()
        }

    private fun invalidPluginRequest(): AiPluginRouteException =
        AiPluginRouteException(
            code = PLUGIN_INVALID_REQUEST,
            message = "AI plugin request is invalid",
        )

    private fun enqueue(
        input: AiRequestInput,
        resultMapper: (AiResponse, ProfileMetadata) -> Any?,
    ): ScriptPromiseAdapter {
        val promiseAdapter = ScriptPromiseAdapter()
        val dispatcher = AiAsyncDispatcher(scriptRuntime)
        val callReference = AtomicReference<Call?>()
        activePromises[dispatcher] = callReference
        val prepared = try {
            prepare(input, streaming = false)
        } catch (error: Exception) {
            rejectPromise(dispatcher, callReference, promiseAdapter, error)
            return promiseAdapter
        }
        if (closed.get()) {
            dispatcher.cancelSilently {
                activePromises.remove(dispatcher, callReference)
            }
            return promiseAdapter
        }

        val call = try {
            clientFor(prepared.timeoutMillis, streaming = false)
                .newCall(prepared.request)
        } catch (error: RuntimeException) {
            rejectPromise(dispatcher, callReference, promiseAdapter, error)
            return promiseAdapter
        }
        callReference.set(call)
        if (closed.get()) {
            call.cancel()
            dispatcher.cancelSilently {
                activePromises.remove(dispatcher, callReference)
            }
            return promiseAdapter
        }
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (closed.get()) {
                        dispatcher.cancelSilently {
                            activePromises.remove(dispatcher, callReference)
                        }
                    } else {
                        rejectPromise(dispatcher, callReference, promiseAdapter, e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (closed.get()) {
                        response.close()
                        dispatcher.cancelSilently {
                            activePromises.remove(dispatcher, callReference)
                        }
                        return
                    }
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) {
                                throw AiHttpErrors.fromResponse(it, prepared.config)
                            }
                            val body = it.body
                                ?: throw IOException("AI provider returned an empty response body")
                            val parsed = AiJsonResponseReader.parse(
                                prepared.config.providerId,
                                body,
                            )
                            parsed.error?.let { error ->
                                throw providerResponseException(prepared.config, error)
                            }
                            resultMapper(parsed, prepared.metadata)
                        }
                        if (closed.get()) {
                            dispatcher.cancelSilently {
                                activePromises.remove(dispatcher, callReference)
                            }
                        } else {
                            resolvePromise(
                                dispatcher,
                                callReference,
                                promiseAdapter,
                                result,
                            )
                        }
                    } catch (error: Exception) {
                        if (closed.get()) {
                            dispatcher.cancelSilently {
                                activePromises.remove(dispatcher, callReference)
                            }
                        } else {
                            rejectPromise(
                                dispatcher,
                                callReference,
                                promiseAdapter,
                                error,
                            )
                        }
                    }
                }
            })
        } catch (error: RuntimeException) {
            call.cancel()
            if (closed.get()) {
                dispatcher.cancelSilently {
                    activePromises.remove(dispatcher, callReference)
                }
            } else {
                rejectPromise(dispatcher, callReference, promiseAdapter, error)
            }
        }
        return promiseAdapter
    }

    private fun resolvePromise(
        dispatcher: AiAsyncDispatcher,
        callReference: AtomicReference<Call?>,
        promiseAdapter: ScriptPromiseAdapter,
        result: Any?,
    ) {
        try {
            dispatcher.resolve(promiseAdapter, result) {
                activePromises.remove(dispatcher, callReference)
            }
        } catch (_: RuntimeException) {
            activePromises.remove(dispatcher, callReference)
        }
    }

    private fun rejectPromise(
        dispatcher: AiAsyncDispatcher,
        callReference: AtomicReference<Call?>,
        promiseAdapter: ScriptPromiseAdapter,
        error: Exception,
    ) {
        try {
            dispatcher.reject(promiseAdapter, error) {
                activePromises.remove(dispatcher, callReference)
            }
        } catch (_: RuntimeException) {
            activePromises.remove(dispatcher, callReference)
        }
    }

    private fun prepare(input: AiRequestInput, streaming: Boolean): PreparedRequest {
        require(input.messages.size() > 0) { "AI messages must not be empty" }
        val providerOptions = input.options.deepCopy()
        val control = AiControlOptions.takeFrom(providerOptions)

        val preparedConfig = when {
            control.canUseStandaloneConfig() -> standaloneConfig(control)
            else -> resolvedProfileConfig(control)
        }
        val request = AiRequestFactory.build(
            config = preparedConfig.config,
            messages = input.messages,
            options = providerOptions,
            stream = streaming,
        )
        return PreparedRequest(
            config = preparedConfig.config,
            metadata = preparedConfig.metadata,
            request = request,
            timeoutMillis = control.timeoutMillis,
        )
    }

    private fun standaloneConfig(control: AiControlOptions): PreparedConfig {
        val provider = AiProviderDefinition.requireById(requireNotNull(control.providerId))
        val baseUrl = control.baseUrl
            ?: provider.defaultBaseUrl
            ?: throw IllegalArgumentException(
                "baseUrl is required for an OpenAI-compatible AI provider",
            )
        val model = requireNotNull(control.model)
        val normalizedBaseUrl = AiCredentialPolicy.normalizeBaseUrl(baseUrl)
        val apiKey = control.apiKey
        require(
            apiKey != null ||
                AiCredentialPolicy.allowsMissingApiKey(provider.id, normalizedBaseUrl),
        ) {
            "An API key is required for AI provider \"${provider.id}\""
        }
        return PreparedConfig(
            config = AiProviderConfig(provider.id, normalizedBaseUrl, model, apiKey),
            metadata = ProfileMetadata(
                id = null,
                name = null,
                providerId = provider.id,
                baseUrl = normalizedBaseUrl,
                model = model,
            ),
        )
    }

    private fun resolvedProfileConfig(control: AiControlOptions): PreparedConfig {
        val selector = control.profileSelector()
        return vault.withResolvedProfile(selector) { resolved ->
            resolved.toPreparedConfig(control)
        }
    }

    private fun AiResolvedProviderProfile.toPreparedConfig(
        control: AiControlOptions,
    ): PreparedConfig {
        val providerId = control.providerId
            ?.let { AiProviderDefinition.requireById(it).id }
            ?: this.providerId
        val baseUrl = AiCredentialPolicy.normalizeBaseUrl(control.baseUrl ?: this.baseUrl)
        val model = control.model ?: this.model
        val explicitApiKey = control.apiKey
        val storedApiKey = this.apiKey?.reveal()
        val apiKey = explicitApiKey ?: storedApiKey
        if (
            apiKey == null &&
            !AiCredentialPolicy.allowsMissingApiKey(providerId, baseUrl)
        ) {
            throw IllegalStateException(
                "No API key is configured for AI provider profile \"$name\"",
            )
        }

        if (explicitApiKey == null && storedApiKey != null) {
            AiCredentialPolicy.requireStoredCredentialDestination(
                storedProviderId = profile.providerId,
                requestedProviderId = providerId,
                storedBaseUrl = profile.baseUrl,
                requestedBaseUrl = baseUrl,
            )
        }
        return PreparedConfig(
            config = AiProviderConfig(providerId, baseUrl, model, apiKey),
            metadata = ProfileMetadata(
                id = id,
                name = name,
                providerId = providerId,
                baseUrl = baseUrl,
                model = model,
            ),
        )
    }

    private fun consumeStream(
        response: Response,
        prepared: PreparedRequest,
        stream: AiStream.Controller,
    ) {
        response.use {
            if (!it.isSuccessful) {
                throw AiHttpErrors.fromResponse(it, prepared.config)
            }
            val body = it.body ?: throw IOException("AI provider returned an empty response body")
            val contentType = body.contentType()
            val type = contentType?.type?.lowercase()
            val subtype = contentType?.subtype?.lowercase()
            when {
                type == "text" && subtype == "event-stream" ->
                    consumeEventStream(body, prepared, stream)

                type == "application" &&
                    (subtype == "json" || subtype?.endsWith("+json") == true) ->
                    consumeJsonStreamFallback(body, prepared, stream)

                else -> throw AiStreamProtocolException(
                    prepared.config.providerId,
                    "unsupported response content type " +
                        "\"${contentType?.toString() ?: "<missing>"}\"",
                )
            }
        }
    }

    private fun consumeEventStream(
        body: okhttp3.ResponseBody,
        prepared: PreparedRequest,
        stream: AiStream.Controller,
    ) {
        stream.open(prepared.metadata.toPublicValue())
        val providerId = prepared.config.providerId
        val parser = AiStreamParser(providerId)
        val accumulator = AiStreamAccumulator(providerId)
        var terminalEventSeen = false
        SseReader(body.source()).use { reader ->
            while (!stream.isDone) {
                val event = reader.readEvent() ?: break
                val chunk = parser.parse(event)
                chunk.error?.let { error ->
                    throw providerResponseException(prepared.config, error)
                }
                accumulator.append(chunk)
                emitChunk(stream, chunk)
                if (chunk.done) {
                    terminalEventSeen = true
                    stream.complete(accumulator.toResponse().toPublicValue(prepared.metadata))
                    break
                }
            }
        }
        if (!terminalEventSeen && !stream.isDone) {
            throw AiStreamProtocolException(
                providerId,
                "connection ended before a terminal event",
            )
        }
    }

    private fun consumeJsonStreamFallback(
        body: okhttp3.ResponseBody,
        prepared: PreparedRequest,
        stream: AiStream.Controller,
    ) {
        stream.open(prepared.metadata.toPublicValue())
        val parsed = AiJsonResponseReader.parse(prepared.config.providerId, body)
        parsed.error?.let { error ->
            throw providerResponseException(prepared.config, error)
        }
        val chunk = parsed.toStreamChunk()
        stream.chunk(chunk.toPublicValue())
        if (parsed.text.isNotEmpty() || parsed.reasoning.isNotEmpty()) {
            stream.delta(parsed.text, chunk.toPublicValue())
        }
        parsed.toolCalls.forEach { stream.toolCall(it.toPublicValue()) }
        parsed.usage?.let { stream.usage(it.toPublicValue()) }
        stream.complete(parsed.toPublicValue(prepared.metadata))
    }

    private fun emitChunk(stream: AiStream.Controller, chunk: AiStreamChunk) {
        val chunkValue = chunk.toPublicValue()
        stream.chunk(chunkValue)
        if (chunk.text.isNotEmpty() || chunk.reasoning.isNotEmpty()) {
            stream.delta(chunk.text, chunkValue)
        }
        chunk.toolCalls.forEach { stream.toolCall(it.toPublicValue()) }
        chunk.usage?.let { usage -> stream.usage(usage.toPublicValue()) }
    }

    private fun clientFor(timeoutMillis: Long?, streaming: Boolean): OkHttpClient {
        val builder = client.newBuilder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        if (streaming) {
            builder.readTimeout(0L, TimeUnit.MILLISECONDS)
            builder.callTimeout(
                timeoutMillis ?: DEFAULT_STREAM_CALL_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        } else {
            val timeout = timeoutMillis ?: DEFAULT_CALL_TIMEOUT_MILLIS
            builder.readTimeout(timeout, TimeUnit.MILLISECONDS)
            builder.callTimeout(timeout, TimeUnit.MILLISECONDS)
        }
        return builder.build()
    }

    private fun providerResponseException(
        config: AiProviderConfig,
        error: AiProviderError,
    ): IOException {
        val type = sanitizeProviderErrorField(error.type, config.apiKey)
        val code = sanitizeProviderErrorField(error.code, config.apiKey)
        val detail = sanitizeProviderErrorField(error.message, config.apiKey)
        return IOException(
            buildString {
                append("AI provider \"")
                append(config.providerId)
                append("\" returned an error")
                listOfNotNull(type, code).takeIf { it.isNotEmpty() }?.let {
                    append(" (")
                    append(it.joinToString("/"))
                    append(')')
                }
                if (detail != null) {
                    append(": ")
                    append(detail)
                }
            },
        )
    }

    private fun AiResponse.toPublicValue(
        metadata: ProfileMetadata,
    ): Map<String, Any?> = linkedMapOf(
        "text" to text,
        "reasoning" to reasoning,
        "toolCalls" to toolCalls.map(AiToolCall::toPublicValue),
        "usage" to usage?.toPublicValue(),
        "finishReason" to finishReason,
        "message" to message?.toPlainValue(),
        "error" to error?.toPublicValue(),
        "raw" to raw.toPlainValue(),
        "profile" to metadata.toPublicValue(),
        "provider" to metadata.providerId,
        "model" to metadata.model,
    )

    private fun Throwable.toPublicError(): Map<String, Any?> {
        val cause = this
        return linkedMapOf<String, Any?>(
            "name" to cause.javaClass.simpleName,
            "message" to cause.message.orEmpty(),
        ).apply {
            if (cause is AiHttpException) {
                put("provider", cause.providerId)
                put("statusCode", cause.statusCode)
                put("code", cause.errorCode)
                put("type", cause.errorType)
                put("requestId", cause.requestId)
            }
            if (cause is AiPluginRouteException) {
                put("route", "plugin")
                put("code", cause.code)
            }
        }
    }

    private fun redact(value: String, apiKey: String?): String {
        val key = apiKey?.trim().orEmpty()
        return if (key.isEmpty()) value else value.replace(key, "<redacted>")
    }

    private fun sanitizeProviderErrorField(value: String?, apiKey: String?): String? {
        return value
            ?.let { redact(it, apiKey) }
            ?.replace(CONTROL_CHARACTERS, " ")
            ?.trim()
            ?.take(MAX_ERROR_MESSAGE_CHARS)
            ?.takeIf(String::isNotEmpty)
    }

    private data class PreparedConfig(
        val config: AiProviderConfig,
        val metadata: ProfileMetadata,
    )

    private data class PreparedRequest(
        val config: AiProviderConfig,
        val metadata: ProfileMetadata,
        val request: okhttp3.Request,
        val timeoutMillis: Long?,
    )

    private data class ProfileMetadata(
        val id: String?,
        val name: String?,
        val providerId: String,
        val baseUrl: String,
        val model: String,
    ) {

        fun toPublicValue(): Map<String, Any?> = linkedMapOf(
            "id" to id,
            "name" to name,
            "provider" to providerId,
            "baseUrl" to baseUrl,
            "model" to model,
        )
    }

    companion object {

        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 30_000L
        private const val DEFAULT_WRITE_TIMEOUT_MILLIS = 60_000L
        private const val DEFAULT_CALL_TIMEOUT_MILLIS = 120_000L
        private const val DEFAULT_STREAM_CALL_TIMEOUT_MILLIS =
            AiControlOptions.MAX_TIMEOUT_MILLIS
        private const val PLUGIN_INVALID_REQUEST = "INVALID_REQUEST"
        private const val MAX_ERROR_MESSAGE_CHARS = 512
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    }
}

private fun AiProviderProfileSummary.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "name" to name,
    "provider" to providerId,
    "baseUrl" to baseUrl,
    "model" to model,
    "hasApiKey" to hasApiKey,
    "isDefault" to isDefault,
    "status" to "available",
    "errorCode" to null,
)

private fun AiProviderProfileInspection.toPublicValue(): Map<String, Any?> = when (this) {
    is AiProviderProfileInspection.Available -> profile.toPublicValue()
    is AiProviderProfileInspection.Unavailable -> linkedMapOf(
        "id" to id,
        "name" to null,
        "provider" to null,
        "baseUrl" to null,
        "model" to null,
        "hasApiKey" to null,
        "isDefault" to isDefault,
        "status" to status.id,
        "errorCode" to errorCode.id,
    )
}

private fun AiStreamChunk.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "text" to text,
    "reasoning" to reasoning,
    "toolCalls" to toolCalls.map(AiToolCall::toPublicValue),
    "usage" to usage?.toPublicValue(),
    "finishReason" to finishReason,
    "done" to done,
    "raw" to raw.toPlainValue(),
)

private fun AiResponse.toStreamChunk(): AiStreamChunk = AiStreamChunk(
    text = text,
    reasoning = reasoning,
    toolCalls = toolCalls,
    usage = usage,
    raw = raw,
    finishReason = finishReason,
    done = true,
    error = error,
)

private fun AiToolCall.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "index" to index,
    "id" to id,
    "name" to name,
    "arguments" to arguments,
    "argumentsAreDelta" to argumentsAreDelta,
    "raw" to raw.toPlainValue(),
)

private fun AiUsage.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "inputTokens" to inputTokens,
    "outputTokens" to outputTokens,
    "totalTokens" to totalTokens,
    "reasoningTokens" to reasoningTokens,
    "cachedInputTokens" to cachedInputTokens,
    "raw" to raw?.toPlainValue(),
)

private fun AiProviderError.toPublicValue(): Map<String, Any?> = linkedMapOf(
    "type" to type,
    "code" to code,
    "message" to message,
)

private fun JsonElement.toPlainValue(): Any? = when {
    isJsonNull -> null
    isJsonArray -> asJsonArray.map(JsonElement::toPlainValue)
    isJsonObject -> asJsonObject.entrySet().associateTo(linkedMapOf()) { (key, value) ->
        key to value.toPlainValue()
    }
    asJsonPrimitive.isBoolean -> asBoolean
    asJsonPrimitive.isNumber -> asDouble
    else -> asString
}
