package org.autojs.plugin.mcp.server.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.IMcpServerCallback
import org.autojs.plugin.mcp.server.api.IMcpServerPlugin
import org.autojs.plugin.mcp.server.api.IMcpServerSession
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.autojs.plugin.mcp.server.fake.FakeMcpServerScenario
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * `IMcpServerPlugin` fixture. One service class per scenario, all in the same process except the
 * death scenario; the service refuses to start when its manifest meta-data names another scenario.
 */
abstract class FakeMcpServerService : Service() {

    protected abstract val scenario: FakeMcpServerScenario

    private lateinit var verifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private val sessions = ConcurrentHashMap.newKeySet<RemoteFakeMcpServerSession>()
    private val activeSession = AtomicReference<RemoteFakeMcpServerSession?>(null)

    override fun onCreate() {
        super.onCreate()
        verifyManifestScenario()
        verifier = HostCallerVerifier(this)
        worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mcp-fake-worker").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.toList().forEach(RemoteFakeMcpServerSession::serviceDestroyed)
        sessions.clear()
        activeSession.set(null)
        worker.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IMcpServerPlugin.Stub() {

        override fun getInfo(): PluginInfo {
            verifier.enforceAllowedCaller()
            return FakeMcpBundles.pluginInfo(scenario)
        }

        override fun getCapabilities(): Bundle {
            verifier.enforceAllowedCaller()
            return FakeMcpBundles.capabilities(scenario)
        }

        override fun openServer(
            config: Bundle?,
            broker: IMcpHostCapabilityBroker?,
            callback: IMcpServerCallback?,
        ): IMcpServerSession {
            val ownerUid = verifier.enforceAllowedCaller()
            check(scenario != FakeMcpServerScenario.WRONG_CONTRACT_VERSION) {
                "${McpServerContract.ERROR_UNSUPPORTED_CONTRACT}: the host opened a server on a contract version it does not support"
            }
            if (broker == null || callback == null) {
                throw IllegalArgumentException("${McpServerContract.ERROR_INVALID_CONFIG}: a capability broker and a callback are required")
            }
            val port = FakeMcpBundles.validatedPort(config)
            val session = RemoteFakeMcpServerSession(
                ownerUid = ownerUid,
                port = port,
                bindScope = FakeMcpBundles.bindScope(config),
                protocolMode = FakeMcpBundles.protocolMode(config),
                broker = broker,
                callback = callback,
                scenario = scenario,
                verifier = verifier,
                worker = worker,
                callbackLane = callbackLane,
                onFinished = { finished ->
                    activeSession.compareAndSet(finished, null)
                    sessions.remove(finished)
                },
            )
            if (!activeSession.compareAndSet(null, session)) {
                throw IllegalStateException("${McpServerContract.ERROR_ALREADY_OPEN}: the fixture serves one session at a time")
            }
            sessions += session
            session.start()
            return session
        }

        override fun openServerV2(
            config: Bundle?,
            broker: IHostCapabilityBroker?,
            callback: IMcpServerCallback?,
        ): IMcpServerSession {
            verifier.enforceAllowedCaller()
            requireNotNull(broker) { "A shared capability broker is required" }
            // Exercise the same hostile scenarios through the new shared transport.
            val adapter = object : IMcpHostCapabilityBroker.Stub() {
                override fun getBrokerInfo(): Bundle = broker.brokerInfo
                override fun dispatch(request: Bundle?, response: IMcpHostCapabilityCallback?) {
                    broker.dispatch(request, response?.let { target -> object : IHostCapabilityCallback.Stub() {
                        override fun onResponse(result: Bundle?) = target.onResponse(result)
                    } })
                }
                override fun destroy(reason: Bundle?) = broker.destroy(reason)
            }
            return openServer(config, adapter, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyManifestScenario() {
        val info = packageManager.getServiceInfo(
            ComponentName(this, javaClass),
            PackageManager.GET_META_DATA,
        )
        val declaredValue = info.metaData?.getString(FakeMcpServerScenario.METADATA_KEY)
            ?: error("MCP Server fixture service is missing scenario metadata")
        check(FakeMcpServerScenario.fromMetadata(declaredValue) == scenario) {
            "MCP Server fixture service scenario does not match its manifest metadata"
        }
    }
}

class NormalMcpServerService : FakeMcpServerService() {
    override val scenario = FakeMcpServerScenario.NORMAL
}

class WrongContractVersionMcpServerService : FakeMcpServerService() {
    override val scenario = FakeMcpServerScenario.WRONG_CONTRACT_VERSION
}

class OversizedBundleMcpServerService : FakeMcpServerService() {
    override val scenario = FakeMcpServerScenario.OVERSIZED_BUNDLE
}

class DuplicateCallbackMcpServerService : FakeMcpServerService() {
    override val scenario = FakeMcpServerScenario.DUPLICATE_CALLBACK
}

class DeathCallbackMcpServerService : FakeMcpServerService() {
    override val scenario = FakeMcpServerScenario.DEATH_CALLBACK
}

/** `org.autojs.plugin.INFO` provider of the fixture, answering with the normal scenario's metadata. */
class FakeMcpServerInfoService : Service() {

    private lateinit var verifier: HostCallerVerifier

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo(): PluginInfo {
            verifier.enforceAllowedCaller()
            return FakeMcpBundles.pluginInfo(FakeMcpServerScenario.NORMAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        verifier = HostCallerVerifier(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
