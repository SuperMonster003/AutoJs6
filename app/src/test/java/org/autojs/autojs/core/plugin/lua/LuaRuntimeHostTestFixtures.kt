package org.autojs.autojs.core.plugin.lua

import org.autojs.plugin.lua.runtime.api.ILuaExecutionSession
import org.autojs.plugin.lua.runtime.api.ILuaRuntimeProvider
import org.autojs.plugin.lua.runtime.api.LuaPluginActions
import org.autojs.plugin.lua.runtime.api.LuaPluginIds
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaRuntimeFamily
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeLimits
import java.util.concurrent.atomic.AtomicInteger

internal object LuaRuntimeHostTestFixtures {
    const val SIGNER_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val SIGNER_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    const val CAPABILITY_CONSOLE = "console.log"
    const val CAPABILITY_DEVICE = "device.info"

    fun component(
        packageName: String = "example.lua.provider",
        className: String = "LuaRuntimeService",
    ) = LuaRuntimeProviderComponent(packageName, className)

    fun descriptor(
        component: LuaRuntimeProviderComponent = component(),
        infoAction: String = LuaPluginActions.INFO,
        runtimeAction: String = LuaRuntimeContract.SERVICE_ACTION,
        pluginId: String = LuaPluginIds.ID,
        engine: String = LuaPluginIds.ENGINE,
        variant: String = LuaPluginIds.VARIANT_PUC_LUA_5_4,
        infoVersionCode: Long = 7L,
        infoSupportedAbis: Collection<String> = listOf("arm64-v8a"),
        exported: Boolean = true,
        applicationEnabled: Boolean = true,
        serviceEnabled: Boolean = true,
        requiredPermission: String? = LuaRuntimeContract.PLUGIN_PERMISSION,
        packageUid: Int = 10_001,
        versionCode: Long = 7L,
        lastUpdateTime: Long = 123L,
        signerSha256: Collection<String> = listOf(SIGNER_A),
        trustLevel: LuaRuntimeProviderTrustLevel = LuaRuntimeProviderTrustLevel.OFFICIAL,
        sameSignerAsHost: Boolean = false,
    ) = LuaRuntimeProviderDescriptor(
        component = component,
        infoAction = infoAction,
        runtimeAction = runtimeAction,
        pluginId = pluginId,
        engine = engine,
        variant = variant,
        infoVersionCode = infoVersionCode,
        infoSupportedAbis = infoSupportedAbis,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = requiredPermission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
    )

    fun runtimeInfo(
        protocolMin: LuaProtocolVersion = LuaProtocolVersion(1, 0),
        protocolMax: LuaProtocolVersion = LuaProtocolVersion(1, 0),
        providerId: String = "fixture.lua.provider",
        providerVersionName: String = "1.0.0",
        providerVersionCode: Long = 7L,
        runtimeFamily: LuaRuntimeFamily = LuaRuntimeFamily.PUC_LUA,
        runtimeSlot: String = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
        languageVersion: String = "5.4.8",
        processAbi: String = "arm64-v8a",
        supportedAbis: Collection<String> = listOf(processAbi),
        capabilities: Collection<String> = listOf(CAPABILITY_CONSOLE, CAPABILITY_DEVICE),
        limits: LuaRuntimeLimits = runtimeLimits(),
        minHostVersionCode: Long? = 1L,
        maxHostVersionCode: Long? = null,
    ) = LuaRuntimeInfo(
        protocolMin = protocolMin,
        protocolMax = protocolMax,
        providerId = providerId,
        providerVersionName = providerVersionName,
        providerVersionCode = providerVersionCode,
        runtimeFamily = runtimeFamily,
        runtimeSlot = runtimeSlot,
        languageVersion = languageVersion,
        processAbi = processAbi,
        supportedAbis = supportedAbis,
        capabilities = capabilities,
        limits = limits,
        minHostVersionCode = minHostVersionCode,
        maxHostVersionCode = maxHostVersionCode,
    )

    fun runtimeLimits(
        maxSourceBytes: Long = LuaRuntimeContract.MAX_SOURCE_BYTES,
        maxMemoryBytes: Long = LuaRuntimeContract.MAX_MEMORY_BYTES,
        maxOutputBytes: Long = LuaRuntimeContract.MAX_OUTPUT_BYTES,
        maxExecutionMillis: Long = LuaRuntimeContract.MAX_TIMEOUT_MILLIS,
        maxConcurrentExecutions: Int = LuaRuntimeContract.MAX_CONCURRENT_EXECUTIONS,
    ) = LuaRuntimeLimits(
        maxSourceBytes = maxSourceBytes,
        maxMemoryBytes = maxMemoryBytes,
        maxOutputBytes = maxOutputBytes,
        maxExecutionMillis = maxExecutionMillis,
        maxConcurrentExecutions = maxConcurrentExecutions,
    )

    fun environment(
        hostVersionCode: Long = 6_800L,
        supportedProcessAbis: Collection<String> = listOf("arm64-v8a", "x86_64"),
        releaseBuild: Boolean = true,
        allowUserApprovedProviders: Boolean = false,
    ) = LuaRuntimeProviderEnvironment(
        hostVersionCode = hostVersionCode,
        supportedProcessAbis = supportedProcessAbis,
        releaseBuild = releaseBuild,
        allowUserApprovedProviders = allowUserApprovedProviders,
    )

    fun requirement(
        hostCapabilities: Collection<String> = listOf(CAPABILITY_CONSOLE, CAPABILITY_DEVICE),
        requiredCapabilities: Collection<String> = listOf(CAPABILITY_CONSOLE),
    ) = LuaRuntimeProviderRequirement(hostCapabilities, requiredCapabilities)

    class FakeProvider(initialRuntimeInfo: LuaRuntimeInfo = runtimeInfo()) : ILuaRuntimeProvider.Default() {
        private val calls = AtomicInteger()

        @Volatile
        var runtimeInfoBytes: ByteArray = LuaRuntimeCodec.encodeRuntimeInfo(initialRuntimeInfo)

        val getRuntimeInfoCalls: Int
            get() = calls.get()

        override fun getRuntimeInfo(): ByteArray {
            calls.incrementAndGet()
            return runtimeInfoBytes.copyOf()
        }

        fun replaceRuntimeInfo(value: LuaRuntimeInfo) {
            runtimeInfoBytes = LuaRuntimeCodec.encodeRuntimeInfo(value)
        }
    }

    class FakeSession : ILuaExecutionSession.Default() {
        var startCalls = 0
        var grantCalls = 0
        var cancelCalls = 0
        var closeCalls = 0

        override fun start() {
            startCalls += 1
        }

        override fun grantOutputCredits(count: Int) {
            grantCalls += 1
        }

        override fun cancel() {
            cancelCalls += 1
        }

        override fun close() {
            closeCalls += 1
        }
    }

    class FakeBinding(
        override val component: LuaRuntimeProviderComponent,
        override val provider: ILuaRuntimeProvider,
        private val beforeRuntimeInfo: (() -> Unit)? = null,
    ) : LuaRuntimeProviderBinding {
        var closeCalls = 0
        val runtimeInfoTimeouts = ArrayList<Long>()

        override fun readRuntimeInfo(timeoutMillis: Long): ByteArray {
            require(timeoutMillis > 0L)
            runtimeInfoTimeouts += timeoutMillis
            beforeRuntimeInfo?.invoke()
            return provider.getRuntimeInfo()
        }

        override fun close() {
            closeCalls += 1
        }
    }

    class FakeTransport(
        descriptors: Collection<LuaRuntimeProviderDescriptor> = emptyList(),
    ) : LuaRuntimeProviderTransport {
        var descriptors: List<LuaRuntimeProviderDescriptor> = descriptors.toList()
        val providers = LinkedHashMap<LuaRuntimeProviderComponent, FakeProvider>()
        val discoveryActions = ArrayList<String>()
        val discoveryTimeouts = ArrayList<Long>()
        val boundComponents = ArrayList<LuaRuntimeProviderComponent>()
        val bindTimeouts = ArrayList<Long>()
        val bindings = ArrayList<FakeBinding>()
        var discoveryFailure: Throwable? = null
        var bindingFailure: ((LuaRuntimeProviderComponent, Int) -> Throwable?)? = null
        var boundComponent: ((LuaRuntimeProviderComponent, Int) -> LuaRuntimeProviderComponent)? = null
        var afterDiscover: (() -> Unit)? = null
        var afterBind: ((LuaRuntimeProviderComponent, Int) -> Unit)? = null
        var beforeRuntimeInfo: ((LuaRuntimeProviderComponent, Int) -> Unit)? = null

        override fun discover(
            runtimeAction: String,
            timeoutMillis: Long,
        ): Collection<LuaRuntimeProviderDescriptor> {
            require(timeoutMillis > 0L)
            discoveryActions += runtimeAction
            discoveryTimeouts += timeoutMillis
            discoveryFailure?.let { throw it }
            afterDiscover?.invoke()
            return descriptors.toList()
        }

        override fun bindExact(
            component: LuaRuntimeProviderComponent,
            timeoutMillis: Long,
        ): LuaRuntimeProviderBinding {
            require(timeoutMillis > 0L)
            boundComponents += component
            bindTimeouts += timeoutMillis
            val attempt = boundComponents.size
            bindingFailure?.invoke(component, attempt)?.let { throw it }
            afterBind?.invoke(component, attempt)
            val provider = checkNotNull(providers[component]) { "No fake provider for $component" }
            return FakeBinding(
                component = boundComponent?.invoke(component, attempt) ?: component,
                provider = provider,
                beforeRuntimeInfo = { beforeRuntimeInfo?.invoke(component, attempt) },
            ).also(bindings::add)
        }
    }
}
