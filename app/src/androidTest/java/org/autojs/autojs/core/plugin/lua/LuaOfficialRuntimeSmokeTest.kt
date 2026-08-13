package org.autojs.autojs.core.plugin.lua

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.engine.LuaPluginScriptEngine
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.lua.runtime.api.LuaPluginActions
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Reader
import java.util.UUID

/**
 * Opt-in happy-path smoke for the real, independently installed Lua runtime provider.
 *
 * This intentionally does not reuse the hostile fixture or the debug fault service. The provider
 * APK must be built with native execution and production discovery explicitly enabled; both
 * repository defaults remain disabled.
 */
class LuaOfficialRuntimeSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageManager = context.packageManager

    @Test
    fun officialProviderExecutesAndReleasesSequentialSessions() {
        requirePilotEnvironment()

        val infoService = singleEnabledService(LuaPluginActions.INFO)
        val runtimeService = singleEnabledService(LuaRuntimeContract.SERVICE_ACTION)
        assertEquals(OFFICIAL_PROVIDER_PACKAGE, infoService.packageName)
        assertEquals(OFFICIAL_PROVIDER_PACKAGE, runtimeService.packageName)
        assertEquals(EXPECTED_RUNTIME_PROCESS, infoService.processName)
        assertEquals(EXPECTED_RUNTIME_PROCESS, runtimeService.processName)
        assertEquals(LuaRuntimeContract.PLUGIN_PERMISSION, infoService.permission)
        assertEquals(LuaRuntimeContract.PLUGIN_PERMISSION, runtimeService.permission)
        assertTrue("Official Lua INFO service must be exported", infoService.exported)
        assertTrue("Official Lua runtime service must be exported", runtimeService.exported)
        assertEquals(
            "Official Lua provider and host must have the same debug signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, OFFICIAL_PROVIDER_PACKAGE),
        )

        val stdout = "lua-m3-device-info-${UUID.randomUUID()}"
        val engine = LuaPluginScriptEngine(context)
        try {
            engine.init()
            assertEquals(LuaValue.Int64Value(7L), engine.execute(source("return 7", "official-provider-first")))

            val script = """
                local autojs = require("autojs")
                local device = autojs.device.info()
                if type(device.brand) ~= "string"
                    or type(device.manufacturer) ~= "string"
                    or type(device.model) ~= "string"
                    or type(device.device) ~= "string"
                    or type(device.product) ~= "string"
                    or type(device.sdkInt) ~= "number" then
                    error("device.info result mismatch")
                end
                autojs.console.log("$stdout")
                return device.sdkInt
            """.trimIndent()
            assertEquals(
                LuaValue.Int64Value(Build.VERSION.SDK_INT.toLong()),
                engine.execute(source(script, "official-provider-device-info")),
            )
            assertEquals(
                listOf(Log.INFO to stdout),
                consoleEntries(stdout),
            )

            val moduleRoot = context.cacheDir.resolve("lua-module-smoke-${UUID.randomUUID()}")
            check(moduleRoot.mkdir()) { "Cannot create the Lua module smoke directory" }
            try {
                val entry = moduleRoot.resolve("main.lua").apply {
                    writeText(
                        """
                        local first = require("helper")
                        local second = require("helper")
                        if first ~= true or second ~= true or _G.__module_hits ~= 1 then
                            error("controlled module snapshot cache mismatch")
                        end
                        return _G.__module_hits
                        """.trimIndent(),
                    )
                }
                moduleRoot.resolve("main.modules").apply {
                    check(mkdir()) { "Cannot create the Lua module snapshot root" }
                    resolve("helper.lua").writeText(
                        "_G.__module_hits = (_G.__module_hits or 0) + 1",
                    )
                }
                assertEquals(
                    LuaValue.Int64Value(1L),
                    engine.execute(LuaFileSource(entry)),
                )
            } finally {
                moduleRoot.deleteRecursively()
            }
        } finally {
            engine.destroy()
        }
    }

    private fun requirePilotEnvironment() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Official Lua provider smoke is opt-in; set $ARG_ENABLED=true",
            arguments.getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("Official Lua provider smoke requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertTrue("Official Lua provider smoke requires a debuggable host", BuildConfig.DEBUG)
        assertEquals(HOST_PACKAGE, context.packageName)
        assertTrue(
            "Official Lua provider smoke must not run on the Android main thread",
            Looper.myLooper() != Looper.getMainLooper(),
        )
    }

    @Suppress("DEPRECATION")
    private fun singleEnabledService(action: String): ServiceInfo {
        val services = packageManager.queryIntentServices(
            Intent(action).setPackage(OFFICIAL_PROVIDER_PACKAGE),
            0,
        ).map { it.serviceInfo }
        assertEquals("Expected exactly one enabled service for $action", 1, services.size)
        return services.single()
    }

    private fun source(scriptText: String, name: String) = object : LuaScriptSource(name) {
        override val script: String = scriptText
        override val scriptReader: Reader? = null
    }

    private fun consoleEntries(vararg markers: String): List<Pair<Int, String>> {
        val expected = markers.toSet()
        val console = ScriptEngineService.getInstance().globalConsole as? ConsoleImpl
            ?: error("Lua output sink is not backed by the host ConsoleImpl")
        val deadline = System.nanoTime() + 2_000_000_000L
        var matched = emptyList<Pair<Int, String>>()
        do {
            matched = synchronized(console.logEntries) {
                console.logEntries.mapNotNull { entry ->
                    val marker = expected.firstOrNull(entry.content.toString()::endsWith)
                    marker?.let { entry.level to it }
                }
            }
            if (matched.size == expected.size) return matched
            Thread.sleep(10L)
        } while (System.nanoTime() < deadline)
        return matched
    }

    private companion object {
        const val ARG_ENABLED = "autojs.luaRuntime.officialProvider.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val OFFICIAL_PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.lua.runtime"
        const val EXPECTED_RUNTIME_PROCESS = "$OFFICIAL_PROVIDER_PACKAGE:lua_runtime"
    }
}
