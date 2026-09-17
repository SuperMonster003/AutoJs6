package org.autojs.autojs.core.plugin.text

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginResourceResolver
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.autojs.plugin.opencc.api.OpenccPluginCapabilityKeys
import org.autojs.plugin.opencc.api.OpenccPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Requires an installed official contract-v2 plugin; missing fixtures must fail explicitly. */
class OpenccPluginInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedProviderDiscoversTypesAndMatchesLegacyBatchAndChainResults() = runBlocking {
        val info = OpenccPluginHost.probe(context, PLUGIN_PACKAGE)
        assertTrue(
            info.capabilities!!.getInt(OpenccPluginCapabilityKeys.CONTRACT_VERSION) >=
                    OpenccPluginContract.VERSION_EXTENDED_CONVERSIONS,
        )
        assertTrue(OpenccPluginHost.getSupportedConversionTypes(context).containsAll(OpenccConversionTypes.ALL))
        val sources = listOf("鼠标和软件", "汉字转换测试")
        val expected = sources.map { OpenccPluginHost.convert(context, it, "S2TWP") }
        assertEquals("滑鼠和軟體", expected[0])
        val batch = List(OpenccPluginContract.MAX_BATCH_SIZE) { sources[it % sources.size] }
        assertEquals(
            List(batch.size) { expected[it % expected.size] },
            OpenccPluginHost.convertBatch(context, batch, " s2twp "),
        )
        val stages = listOf("JP2T", "T2S", "S2TWP")
        var sequential = "滑鼠和軟體, 漢字変換"
        for (stage in stages) sequential = OpenccPluginHost.convert(context, sequential, stage)
        assertEquals(sequential, OpenccPluginHost.convertChain(context, "滑鼠和軟體, 漢字変換", stages))
    }

    @Test
    fun documentedAliasesAndAllCompositeMethodsRunThroughThePublicRhinoApi() {
        val script = """
            "rhino";
            function equal(actual, expected, label) {
                if (actual !== expected) throw new Error(label + ': ' + actual + ' != ' + expected);
            }
            var source = '鼠标和软件, 汉字转换';
            equal(opencc(source, 'S2TWI'), '滑鼠和軟體, 漢字轉換', 'documented S2TWI');
            equal(opencc('滑鼠和軟體', 'TWI2S'), '鼠标和软件', 'documented TWI2S');
            equal(opencc.convert(source, 's2twi'), opencc.s2twi(source), 'lowercase alias');
            equal(opencc.convert('滑鼠', 'twi2s'), opencc.twi2s('滑鼠'), 'reverse alias');
            var chains = {
                s2jp: ['s2t', 't2jp'], t2twi: ['t2s', 's2twp'],
                hk2tw: ['hk2t', 't2tw'], hk2twi: ['hk2s', 's2twp'], hk2jp: ['hk2t', 't2jp'],
                tw2hk: ['tw2t', 't2hk'], tw2twi: ['tw2s', 's2twp'], tw2jp: ['tw2t', 't2jp'],
                twi2t: ['tw2sp', 's2t'], twi2hk: ['tw2sp', 's2hk'], twi2tw: ['tw2sp', 's2tw'],
                twi2jp: ['tw2sp', 's2t', 't2jp'], jp2s: ['jp2t', 't2s'],
                jp2hk: ['jp2t', 't2hk'], jp2tw: ['jp2t', 't2tw'], jp2twi: ['jp2t', 't2s', 's2twp']
            };
            Object.keys(chains).forEach(function (name) {
                var expected = chains[name].reduce(function (value, stage) { return opencc[stage](value); }, source);
                equal(opencc[name](source), expected, name + ' method');
                equal(opencc(source, name.toUpperCase()), expected, name + ' identifier');
            });
            'OPENCC_COMPATIBILITY_PASS';
        """.trimIndent()
        val service = AutoJs.instance.scriptEngineService
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val result = AtomicReference<String>()
        val execution = service.execute(
            StringScriptSource("opencc-compatibility.js", script),
            object : SimpleScriptExecutionListener() {
                override fun onSuccess(execution: ScriptExecution, value: Any?) {
                    result.set(value.toString())
                    completed.countDown()
                }

                override fun onException(execution: ScriptExecution, error: Throwable) {
                    failure.set(error)
                    completed.countDown()
                }
            },
            ExecutionConfig(),
        )
        try {
            assertTrue("OpenCC Rhino execution timed out", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("OpenCC Rhino compatibility failed", it) }
            assertEquals("OPENCC_COMPATIBILITY_PASS", result.get())
        } finally {
            execution.engine.forceStop()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (service.engines.any { it.id == execution.id } && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(25)
            }
            assertTrue("OpenCC engine remained registered", service.engines.none { it.id == execution.id })
        }
    }

    @Test
    fun pluginInstructionResolvesLocalAndPackageQualifiedRawResources() = runBlocking {
        val info = OpenccPluginHost.probe(context, PLUGIN_PACKAGE)
        assertEquals("@raw/plugin_instruction", info.instruction)
        val local = PluginResourceResolver.resolveStringValue(context, PLUGIN_PACKAGE, info.instruction)
        assertTrue("Plugin instruction was not resolved", !local.isNullOrBlank() && !local.startsWith("@"))
        assertTrue(local!!.contains("OpenCC"))
        assertEquals(
            local,
            PluginResourceResolver.resolveStringValue(context, context.packageName, "@$PLUGIN_PACKAGE:raw/plugin_instruction"),
        )
        val expectedName = context.packageManager.getResourcesForApplication(PLUGIN_PACKAGE).let { resources ->
            resources.getString(resources.getIdentifier("app_name", "string", PLUGIN_PACKAGE))
        }
        assertEquals(
            expectedName,
            PluginResourceResolver.resolveStringValue(context, PLUGIN_PACKAGE, "@string/app_name"),
        )
    }

    companion object {
        private const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.opencc"
    }
}
