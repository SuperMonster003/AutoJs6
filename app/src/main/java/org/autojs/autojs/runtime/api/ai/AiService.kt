package org.autojs.autojs.runtime.api.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.ai.AiPluginAskRunner
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRequest
import org.autojs.autojs.core.plugin.ai.AiPluginCatalogRunner
import org.autojs.autojs.core.plugin.ai.AiPluginSessionRunner
import org.autojs.autojs.core.plugin.ai.AiPluginStreamRunner
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.AndroidAiPluginAskRunner
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ai.AiAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.ai.AiSessionNativeObject
import org.autojs.autojs.runtime.api.modules.ai.AiStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class AiRequestInput(
    val messages: JsonArray,
    val options: JsonObject,
)

/** Per-script AI facade whose only execution boundary is an explicitly selected plugin target. */
class AiService private constructor(
    private val scriptRuntime: ScriptRuntime,
    pluginAskRuntimeFactory: () -> AiPluginAskRuntime,
    pluginStreamRuntimeFactory: () -> AiPluginStreamRuntime,
    pluginSessionRuntimeFactory: () -> AiPluginSessionRuntime,
    pluginCatalogRuntimeFactory: () -> AiPluginCatalogRuntime,
) {

    constructor(
        context: Context,
        scriptRuntime: ScriptRuntime,
    ) : this(
        scriptRuntime,
        {
            AiPluginAskRuntime(
                runnerProvider = {
                    AndroidAiPluginAskRunner.create(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
            )
        },
        {
            AiPluginStreamRuntime(
                runnerProvider = {
                    AndroidAiPluginAskRunner.createStream(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
                bridges = scriptRuntime.bridges,
            )
        },
        {
            AiPluginSessionRuntime(
                runnerProvider = {
                    AndroidAiPluginAskRunner.createSession(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
            )
        },
        {
            AiPluginCatalogRuntime(
                runnerProvider = {
                    AndroidAiPluginAskRunner.createCatalog(context.applicationContext)
                },
                dispatcherFactory = { AiAsyncDispatcher(scriptRuntime) },
            )
        },
    )

    internal constructor(
        context: Context,
        scriptRuntime: ScriptRuntime,
        pluginAskRunner: AiPluginAskRunner,
        pluginDispatcherFactory: () -> AiAsyncDispatcher,
        pluginStreamRunner: AiPluginStreamRunner =
            AndroidAiPluginAskRunner.createStream(context.applicationContext),
        pluginSessionRunner: AiPluginSessionRunner =
            AndroidAiPluginAskRunner.createSession(context.applicationContext),
        pluginCatalogRunner: AiPluginCatalogRunner =
            AndroidAiPluginAskRunner.createCatalog(context.applicationContext),
    ) : this(
        scriptRuntime,
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
            AiPluginSessionRuntime(
                runnerProvider = { pluginSessionRunner },
                dispatcherFactory = pluginDispatcherFactory,
            )
        },
        {
            AiPluginCatalogRuntime(
                runnerProvider = { pluginCatalogRunner },
                dispatcherFactory = pluginDispatcherFactory,
            )
        },
    )

    private val closed = AtomicBoolean(false)
    private val agentDelegate = lazy { AiAgentService(scriptRuntime) }
    internal val agent: AiAgentService get() = agentDelegate.value
    private val activePluginSessions = ConcurrentHashMap.newKeySet<AiSessionNativeObject>()
    private val pluginAskRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginAskRuntimeFactory()
    }
    private val pluginStreamRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginStreamRuntimeFactory()
    }
    private val pluginSessionRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginSessionRuntimeFactory()
    }
    private val pluginCatalogRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        pluginCatalogRuntimeFactory()
    }

    fun ask(input: AiRequestInput): ScriptPromiseAdapter = enqueuePluginAsk(input)

    fun chat(input: AiRequestInput): ScriptPromiseAdapter = enqueuePluginChat(input)

    fun stream(input: AiRequestInput): AiStream = enqueuePluginStream(input)

    fun session(input: AiRequestInput): ScriptPromiseAdapter {
        val request = try {
            AiPluginAskInputMapper.map(input, DEFAULT_CALL_TIMEOUT_MILLIS)
        } catch (_: Exception) {
            return rejectPluginSession(invalidPluginRequest())
        }
        val target = AiSessionNativeObject(
            scriptRuntime = scriptRuntime,
            baseRequest = request,
            onClosed = activePluginSessions::remove,
        )
        activePluginSessions.add(target)
        return pluginSessionRuntime().open(request, target)
    }

    /** Lists the complete target catalog of one selected AI plugin. */
    fun catalog(input: AiRequestInput): ScriptPromiseAdapter {
        val control = try {
            AiPluginAskInputMapper.requirePluginControl(input)
        } catch (_: Exception) {
            return pluginCatalogRuntime().reject(invalidPluginRequest())
        }
        val selection = control.pluginSelection
        val request = try {
            require(control.targetId == null) { "AI catalog does not accept a target selector" }
            AiPluginCatalogRequest(
                component = AiProviderComponent(selection.packageName, selection.className),
                providerId = selection.providerId,
                timeoutMillis = control.timeoutMillis ?: DEFAULT_CALL_TIMEOUT_MILLIS,
            )
        } catch (_: Exception) {
            return pluginCatalogRuntime().reject(invalidPluginRequest())
        }
        return pluginCatalogRuntime().list(request)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            if (agentDelegate.isInitialized()) agentDelegate.value.close()
            if (pluginAskRuntimeDelegate.isInitialized()) pluginAskRuntimeDelegate.value.close()
            if (pluginStreamRuntimeDelegate.isInitialized()) pluginStreamRuntimeDelegate.value.close()
            if (pluginSessionRuntimeDelegate.isInitialized()) pluginSessionRuntimeDelegate.value.close()
            if (pluginCatalogRuntimeDelegate.isInitialized()) pluginCatalogRuntimeDelegate.value.close()
            activePluginSessions.toList().forEach(AiSessionNativeObject::close)
        } finally {
            activePluginSessions.clear()
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
        pluginAskRuntimeDelegate.value.also { runtime -> if (closed.get()) runtime.close() }

    private fun pluginStreamRuntime(): AiPluginStreamRuntime =
        pluginStreamRuntimeDelegate.value.also { runtime -> if (closed.get()) runtime.close() }

    private fun pluginSessionRuntime(): AiPluginSessionRuntime =
        pluginSessionRuntimeDelegate.value.also { runtime -> if (closed.get()) runtime.close() }

    private fun pluginCatalogRuntime(): AiPluginCatalogRuntime =
        pluginCatalogRuntimeDelegate.value.also { runtime -> if (closed.get()) runtime.close() }

    private fun invalidPluginRequest() = AiPluginRouteException(
        code = PLUGIN_INVALID_REQUEST,
        message = "AI plugin request is invalid",
    )

    private fun rejectPluginSession(error: AiPluginRouteException): ScriptPromiseAdapter {
        val promise = ScriptPromiseAdapter()
        val dispatcher = AiAsyncDispatcher(scriptRuntime)
        if (closed.get()) {
            dispatcher.cancelSilently()
        } else {
            dispatcher.reject(
                promise,
                linkedMapOf(
                    "name" to error.javaClass.simpleName,
                    "message" to error.message.orEmpty(),
                    "route" to "plugin",
                    "code" to error.code,
                ),
            )
        }
        return promise
    }

    private companion object {
        const val DEFAULT_CALL_TIMEOUT_MILLIS = 120_000L
        const val PLUGIN_INVALID_REQUEST = "INVALID_REQUEST"
    }
}
