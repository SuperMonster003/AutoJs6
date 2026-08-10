package org.autojs.autojs.core.plugin.lua

import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import org.autojs.plugin.lua.runtime.api.ILuaExecutionCallback
import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityBroker
import org.autojs.plugin.lua.runtime.api.ILuaHostCapabilityCallback
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test-only exact-component client that bypasses the engine's valid source snapshot builder.
 * It exists solely to send a raw, deliberately inconsistent source PFD across Binder.
 */
internal class RawLuaRuntimeProviderClient private constructor(
    private val context: Context,
    private val binding: LuaRuntimeProviderBinding,
) : Closeable {

    fun submitExpectingSourceRejection(
        request: LuaExecutionRequest,
        actualSource: ByteArray,
    ): RawLuaSourceRejectionObservation {
        val sourceFile = File.createTempFile("lua-raw-source-", ".lua", context.cacheDir)
        try {
            sourceFile.outputStream().use { output -> output.write(actualSource) }
        } catch (error: Throwable) {
            runCatching { sourceFile.delete() }
            throw error
        }
        val sourceDescriptor = try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: Throwable) {
            runCatching { sourceFile.delete() }
            throw error
        }
        val rawFileDescriptor = sourceDescriptor.fileDescriptor
        val callbacks = CountingExecutionCallback()
        val broker = CountingHostBroker()
        var returnedSession: ILuaExecutionSession? = null
        var remoteFailure: Throwable? = null
        var callerDescriptorOpenAfterRemote = false
        var callerDescriptorClosedByOwner = false
        try {
            try {
                returnedSession = binding.provider.createExecution(
                    LuaRuntimeCodec.encodeExecutionRequest(request),
                    sourceDescriptor,
                    callbacks,
                    broker,
                )
            } catch (error: Throwable) {
                remoteFailure = error
            }
            callerDescriptorOpenAfterRemote = rawFileDescriptor.valid()
        } finally {
            try {
                sourceDescriptor.close()
            } finally {
                callerDescriptorClosedByOwner = !rawFileDescriptor.valid()
                runCatching { sourceFile.delete() }
            }
        }

        returnedSession?.let { session ->
            runCatching { session.cancel() }
            runCatching { session.close() }
            error("Raw hostile Lua source unexpectedly created an execution session")
        }
        val failure = checkNotNull(remoteFailure) {
            "Raw hostile Lua source unexpectedly returned a null session without rejection"
        }
        return RawLuaSourceRejectionObservation(
            failure = failure,
            callerDescriptorOpenAfterRemote = callerDescriptorOpenAfterRemote,
            callerDescriptorClosedByOwner = callerDescriptorClosedByOwner,
            callbackCalls = callbacks.calls.get(),
            brokerCalls = broker.calls.get(),
        )
    }

    override fun close() = binding.close()

    private class CountingExecutionCallback : ILuaExecutionCallback.Stub() {
        val calls = AtomicInteger(0)

        override fun onStarted(metadata: ByteArray?) {
            calls.incrementAndGet()
        }

        override fun onOutput(output: ByteArray?) {
            calls.incrementAndGet()
        }

        override fun onCompleted(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
            closeRawDescriptors(descriptors)
            calls.incrementAndGet()
        }

        override fun onFailed(error: ByteArray?) {
            calls.incrementAndGet()
        }

        override fun onCancelled(cancellation: ByteArray?) {
            calls.incrementAndGet()
        }
    }

    private class CountingHostBroker : ILuaHostCapabilityBroker.Stub() {
        val calls = AtomicInteger(0)

        override fun invoke(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor?>?,
            callback: ILuaHostCapabilityCallback?,
        ) {
            closeRawDescriptors(descriptors)
            calls.incrementAndGet()
        }
    }

    companion object {
        private const val BIND_TIMEOUT_MILLIS = 5_000L
        private const val NORMAL_RUNTIME_SERVICE =
            "${FakeLuaConformanceControlClient.FAKE_PACKAGE}.service.NormalLuaRuntimeService"

        fun bind(context: Context): RawLuaRuntimeProviderClient {
            val applicationContext = context.applicationContext ?: context
            val component = ComponentName(
                FakeLuaConformanceControlClient.FAKE_PACKAGE,
                NORMAL_RUNTIME_SERVICE,
            )
            val binding = AndroidLuaRuntimeProviderTransport(
                applicationContext,
                bindTimeoutMillis = BIND_TIMEOUT_MILLIS,
            ).bindExact(
                LuaRuntimeProviderComponent(component.packageName, component.className),
                BIND_TIMEOUT_MILLIS,
            )
            return RawLuaRuntimeProviderClient(applicationContext, binding)
        }

    }
}

internal data class RawLuaSourceRejectionObservation(
    val failure: Throwable,
    val callerDescriptorOpenAfterRemote: Boolean,
    val callerDescriptorClosedByOwner: Boolean,
    val callbackCalls: Int,
    val brokerCalls: Int,
)

private fun closeRawDescriptors(descriptors: Array<out ParcelFileDescriptor?>?) {
    descriptors?.forEach { descriptor -> runCatching { descriptor?.close() } }
}
