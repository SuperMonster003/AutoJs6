package org.autojs.plugin.ai.agent.fake

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.ai.agent.api.IAiAgentModelBroker
import org.autojs.plugin.ai.agent.api.IAiAgentModelCallback
import org.autojs.plugin.host.capability.api.IHostCapabilityBroker
import org.autojs.plugin.host.capability.api.IHostCapabilityCallback
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** No permissions for device actions or network. Only the same-signer host can drive this APK. */
class BrokerConformanceService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val fakeAgent = FakeAgentEndpoint(::enforceHost)
    private fun enforceHost() {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if (HOST !in packages || packageManager.checkSignatures(packageName, HOST) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Conformance driver accepts the test host only")
        }
    }

    private fun identity() = PendingIntent.getBroadcast(this, 7,
        Intent("org.autojs.plugin.ai.agent.fake.IDENTITY").setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private val binder = object : IBrokerConformanceDriver.Stub() {
        override fun getPlugin(): IBinder { enforceHost(); return fakeAgent }
        override fun control(operation: String, generation: Int, payload: Bundle): Bundle {
            enforceHost()
            // Do not inherit the host UID through a nested synchronous Binder transaction.
            return worker.submit<Bundle> { fakeAgent.control(operation, generation, payload) }.get(5, TimeUnit.SECONDS)
        }
        override fun getIdentity(): Bundle {
            enforceHost()
            return Bundle().apply {
                putInt("uid", Process.myUid())
                putInt("pid", Process.myPid())
                putParcelable(AiAgentActions.EXTRA_ATTACH_IDENTITY, identity())
            }
        }

        override fun dispatch(brokerBinder: IBinder, request: Bundle, callbackBinder: IBinder) {
            enforceHost()
            val broker = IHostCapabilityBroker.Stub.asInterface(brokerBinder)
            val callback = IHostCapabilityCallback.Stub.asInterface(callbackBinder)
            worker.execute {
                // A worker, rather than a nested Binder call, guarantees this APK's own identity.
                broker.dispatch(request, object : IHostCapabilityCallback.Stub() {
                    override fun onResponse(response: Bundle) { callback.onResponse(response) }
                })
            }
        }

        override fun model(brokerBinder: IBinder, operation: String, request: Bundle, callbackBinder: IBinder) {
            enforceHost()
            val broker = IAiAgentModelBroker.Stub.asInterface(brokerBinder)
            val callback = IAiAgentModelCallback.Stub.asInterface(callbackBinder)
            require(operation in setOf("listTargets", "generate", "cancel", "submitToolResults"))
            worker.execute {
                val remoteCallback = object : IAiAgentModelCallback.Stub() {
                    override fun onEvent(event: Bundle) { callback.onEvent(event) }
                }
                when (operation) {
                    "listTargets" -> broker.listTargets(request, remoteCallback)
                    "generate" -> broker.generate(request, remoteCallback)
                    "cancel" -> broker.cancel(request)
                    "submitToolResults" -> broker.submitToolResults(request)
                }
            }
        }

        override fun probeUnauthorized(capabilityBinder: IBinder, modelBinder: IBinder): Bundle {
            enforceHost()
            val capability = IHostCapabilityBroker.Stub.asInterface(capabilityBinder)
            val model = IAiAgentModelBroker.Stub.asInterface(modelBinder)
            return worker.submit<Bundle> {
                Bundle().apply {
                    fun check(name: String, block: () -> Unit) {
                        putBoolean(name, try { block(); false } catch (_: SecurityException) { true })
                    }
                    check("capability.info") { capability.brokerInfo }
                    check("capability.dispatch") { capability.dispatch(Bundle(), null) }
                    check("capability.destroy") { capability.destroy(Bundle()) }
                    check("model.info") { model.brokerInfo }
                    check("model.listTargets") { model.listTargets(Bundle(), null) }
                    check("model.generate") { model.generate(Bundle(), null) }
                    check("model.cancel") { model.cancel(Bundle()) }
                    check("model.destroy") { model.destroy(Bundle()) }
                    check("model.submitToolResults") { model.submitToolResults(Bundle()) }
                }
            }.get(10, TimeUnit.SECONDS)
        }

        override fun sendAttach(identityMode: String, marker: String, completionBinder: IBinder) {
            enforceHost()
            val completion = IHostCapabilityCallback.Stub.asInterface(completionBinder)
            require(identityMode in setOf("missing", "wrong-type", "foreign"))
            val request = Intent(AiAgentActions.ACTION_ATTACH_REQUEST).setPackage(HOST)
                .setComponent(ComponentName(HOST, "org.autojs.autojs.core.plugin.agent.AiAgentAttachRequestReceiver"))
                // Neither a claimed package nor a well-formed config is identity evidence.
                .putExtra("packageName", AiAgentIds.DEFAULT_PACKAGE_NAME)
                .putExtra(AiAgentContract.KEY_LINK_CONFIG_JSON, "{\"scriptRoots\":[]}")
                .putExtra("untrustedBody", marker)
            when (identityMode) {
                "wrong-type" -> request.putExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY, marker)
                "foreign" -> request.putExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY, identity())
            }
            worker.execute {
                sendOrderedBroadcast(request, null, object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        completion.onResponse(Bundle().apply { putBoolean("delivered", true) })
                    }
                }, null, 0, null, null)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() { fakeAgent.close(); worker.shutdownNow(); super.onDestroy() }

    companion object { private const val HOST = "org.autojs.autojs6" }
}
