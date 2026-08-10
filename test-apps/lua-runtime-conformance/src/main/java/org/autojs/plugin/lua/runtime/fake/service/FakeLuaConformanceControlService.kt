package org.autojs.plugin.lua.runtime.fake.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import org.autojs.plugin.lua.runtime.fake.FakeLuaConformanceState
import org.autojs.plugin.lua.runtime.fake.FakeLuaRuntimeScenario
import org.autojs.plugin.lua.runtime.fake.control.IFakeLuaConformanceControl

class FakeLuaConformanceControlService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var componentSelector: FakeLuaComponentSelector
    private val serialControlLock = Any()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        componentSelector = FakeLuaComponentSelector(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IFakeLuaConformanceControl.Stub() {
        override fun reset() = checkedControl {
            componentSelector.reset()
        }

        override fun selectScenario(metadataValue: String?) = checkedControl {
            componentSelector.selectScenario(
                requireNotNull(metadataValue) { "Lua conformance scenario is null" },
            )
        }

        override fun selectRuntimeAmbiguity(
            firstMetadataValue: String?,
            secondMetadataValue: String?,
        ) = checkedControl {
            componentSelector.selectRuntimeAmbiguity(
                requireNotNull(firstMetadataValue) { "First Lua ambiguity scenario is null" },
                requireNotNull(secondMetadataValue) { "Second Lua ambiguity scenario is null" },
            )
        }

        override fun releasePluginInfoStall() = checkedControl {
            FakeLuaConformanceState.releasePluginInfoStall()
        }

        override fun releaseRuntimeInfoStall() = checkedControl {
            FakeLuaConformanceState.releaseRuntimeInfoStall()
        }

        override fun releaseCreateExecutionStall() = checkedControl {
            FakeLuaConformanceState.releaseCreateExecutionStall()
        }

        override fun releaseAllStalls() = checkedControl {
            FakeLuaConformanceState.releaseAllStalls()
        }

        override fun getCounters(): LongArray = checkedControl {
            FakeLuaConformanceState.counterSnapshot()
        }
    }

    private inline fun <T> checkedControl(action: () -> T): T = synchronized(serialControlLock) {
        callerVerifier.enforceAllowedCaller()
        action()
    }
}

private class FakeLuaComponentSelector(context: Context) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val normalInfo: Class<out Service> = FakeLuaPluginInfoService::class.java
    private val stallInfo: Class<out Service> = StallLuaPluginInfoService::class.java
    private val infoComponents: List<Class<out Service>> = listOf(normalInfo, stallInfo)
    private val runtimeComponents: Map<FakeLuaRuntimeScenario, Class<out Service>> = linkedMapOf(
        FakeLuaRuntimeScenario.NORMAL to NormalLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.MALFORMED_RUNTIME_INFO to MalformedRuntimeInfoLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.NULL_BINDING to NullBindingLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.STALL_RUNTIME_INFO to StallRuntimeInfoLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.STALL_CREATE to StallCreateLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.LATE_CREATE_RETURN_SESSION to LateCreateReturnSessionLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.CALLBACK_BEFORE_START to CallbackBeforeStartLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.DUPLICATE_TERMINAL to DuplicateTerminalLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.IGNORE_CANCEL to IgnoreCancelLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.ZERO_CREDIT_OUTPUT to ZeroCreditOutputLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.SEQUENCE_GAP to SequenceGapLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.SEQUENCE_REPLAY to SequenceReplayLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.WRONG_REQUEST_ID to WrongRequestIdLuaRuntimeService::class.java,
        FakeLuaRuntimeScenario.BUSY_SECOND_CREATE to BusySecondCreateLuaRuntimeService::class.java,
    )

    init {
        check(runtimeComponents.keys == FakeLuaRuntimeScenario.values().toSet()) {
            "Lua conformance component allowlist does not cover every runtime scenario"
        }
    }

    fun reset() = applySelection(
        info = normalInfo,
        runtimes = setOf(runtimeComponents.getValue(FakeLuaRuntimeScenario.NORMAL)),
    )

    fun selectScenario(metadataValue: String) {
        val selection = if (metadataValue == PLUGIN_INFO_STALL_SCENARIO) {
            stallInfo to setOf(runtimeComponents.getValue(FakeLuaRuntimeScenario.NORMAL))
        } else {
            val scenario = FakeLuaRuntimeScenario.fromMetadata(metadataValue)
            normalInfo to setOf(runtimeComponents.getValue(scenario))
        }
        applySelection(selection.first, selection.second)
    }

    fun selectRuntimeAmbiguity(firstMetadataValue: String, secondMetadataValue: String) {
        val first = FakeLuaRuntimeScenario.fromMetadata(firstMetadataValue)
        val second = FakeLuaRuntimeScenario.fromMetadata(secondMetadataValue)
        require(first != second) { "Lua ambiguity scenarios must be distinct" }
        require(first in AMBIGUITY_ELIGIBLE && second in AMBIGUITY_ELIGIBLE) {
            "Lua ambiguity scenarios must both advertise valid non-stalling runtime info"
        }
        applySelection(
            info = normalInfo,
            runtimes = setOf(runtimeComponents.getValue(first), runtimeComponents.getValue(second)),
            expectedRuntimeCount = 2,
        )
    }

    private fun applySelection(
        info: Class<out Service>,
        runtimes: Set<Class<out Service>>,
        expectedRuntimeCount: Int = 1,
    ) {
        require(info in infoComponents) { "Lua INFO component is not allowlisted" }
        require(runtimes.isNotEmpty() && runtimes.all(runtimeComponents.values::contains)) {
            "Lua runtime component selection is not allowlisted"
        }
        FakeLuaConformanceState.reset()
        try {
            infoComponents.forEach { component -> setEnabled(component, component == info) }
            runtimeComponents.values.forEach { component -> setEnabled(component, component in runtimes) }
            check(enabledCount(infoComponents) == 1) { "Lua fixture must expose exactly one INFO component" }
            check(enabledCount(runtimeComponents.values) == expectedRuntimeCount) {
                "Lua fixture runtime component count differs from the requested topology"
            }
        } catch (error: Throwable) {
            runCatching {
                infoComponents.forEach { component -> setEnabled(component, component == normalInfo) }
                runtimeComponents.values.forEach { component ->
                    setEnabled(component, component == runtimeComponents.getValue(FakeLuaRuntimeScenario.NORMAL))
                }
            }
            throw error
        }
    }

    private fun setEnabled(componentClass: Class<out Service>, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(applicationContext, componentClass),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun enabledCount(components: Collection<Class<out Service>>): Int = components.count { component ->
        packageManager.getComponentEnabledSetting(ComponentName(applicationContext, component)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    private companion object {
        const val PLUGIN_INFO_STALL_SCENARIO = "stall-plugin-info"
        val AMBIGUITY_ELIGIBLE = setOf(
            FakeLuaRuntimeScenario.NORMAL,
            FakeLuaRuntimeScenario.STALL_CREATE,
            FakeLuaRuntimeScenario.LATE_CREATE_RETURN_SESSION,
            FakeLuaRuntimeScenario.CALLBACK_BEFORE_START,
            FakeLuaRuntimeScenario.DUPLICATE_TERMINAL,
            FakeLuaRuntimeScenario.IGNORE_CANCEL,
        )
    }
}
