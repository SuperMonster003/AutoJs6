package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Debug
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.compose.api.spike.ComposeUiRenderer
import org.autojs.plugin.compose.api.spike.SpikeContract
import org.autojs.plugin.compose.api.ComposeUiIds
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ComposeUiSpikeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var previousEnabled = true
    @Before fun enableDedicatedTestHostPlugin() {
        assumeTrue("Use -PcomposeUiSpike=true to preserve the user's host", context.packageName.endsWith(".compose.spike"))
        // This preference belongs to the separate .compose.spike host, never the user's AutoJs6.
        previousEnabled = PluginEnableStore.isEnabled(context, SpikeContract.PACKAGE)
        PluginEnableStore.setEnabled(context, SpikeContract.PACKAGE, true)
    }
    @After fun restoreDedicatedTestHostPreference() {
        if (context.packageName.endsWith(".compose.spike")) {
            PluginEnableStore.setEnabled(context, SpikeContract.PACKAGE, previousEnabled)
        }
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }
    private fun start(defer: Boolean = false): ComposeSpikeSession {
        val activity = instrumentation.startActivitySync(
            Intent(context, AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as AboutActivity
        lateinit var session: ComposeSpikeSession
        main {
            session = ComposeSpikeSession(activity)
            if (!defer) session.mount()
        }
        instrumentation.waitForIdleSync()
        return session
    }

    @Test fun activityCounterUsesPluginClassesHostOwnersAndQueuedEvents() {
        val activity = start()
        var renderer: ComposeUiRenderer? = null
        try {
            assertNull(activity.error)
            val loaded = requireNotNull(activity.loaded)
            loaded.factory.classOrigins().forEach { (name, type) ->
                if (name in setOf("compose", "runtime")) assertSame(name, loaded.loader, type.classLoader)
                else if (name == "contract") assertSame(name, org.autojs.plugin.compose.api.spike.ComposeUiRendererFactory::class.java, type)
                else assertSame(name, context.classLoader.loadClass(type.name), type)
            }
            main {
                renderer = requireNotNull(activity.renderer)
                assertSame(activity.activity, renderer!!.view().findViewTreeLifecycleOwner())
                assertSame(activity.activity, renderer!!.view().findViewTreeSavedStateRegistryOwner())
            }
            await("counter accessible", { nodeWithText("Count: 0") != null })
            val button = requireNotNull(nodeWithText("+1"))
            var clickable = button
            while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
            assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("event returned to the host and replacement tree rendered", { nodeWithText("Count: 1") != null })
            assertEquals(1, activity.count)
            assertNull(activity.error)
        } finally { main { activity.finish() } }
        await("Activity destroyed", { activity.isDestroyed })
        await("Recomposer shut down", {
            var result = false
            main {
                val state = renderer!!.diagnostics()
                result = state.getBoolean("closed") && !state.getBoolean("hasComposition") &&
                    state.getInt("compositions") == state.getInt("disposals") && state.getBoolean("recomposerShutdown")
            }
            result
        })
    }

    @Test fun resourceConfigurationAndNativePathAreResolvedInsidePlugin() {
        val activity = start(true)
        try {
            val loaded = ComposeSpikeLoader(context).load()
            assertTrue(loaded.nativePath.contains("!/lib/"))
            for (language in listOf("en", "zh", "ar")) {
                val config = Configuration(activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                    densityDpi = 240
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
                val wrapper = ComposeHostContext(activity.activity, loaded.pluginContext, loaded.loader, config)
                val result = loaded.factory.probe(wrapper)
                assertEquals(3, result.getInt("pathSegments"))
                assertEquals(240, result.getInt("densityDpi"))
                assertEquals(language, result.getString("language"))
                assertEquals(Configuration.UI_MODE_NIGHT_YES, result.getInt("uiMode") and Configuration.UI_MODE_NIGHT_MASK)
                assertFalse(result.getString("dialog").isNullOrBlank())
                assertSame(activity.theme, wrapper.theme)
                // Read the same resource from the plugin package's independently configured resource table.
                val expectedResources = loaded.pluginContext.createConfigurationContext(config).resources
                val id = expectedResources.getIdentifier("m3c_dialog", "string", SpikeContract.PACKAGE)
                assertNotEquals(0, id)
                assertEquals(expectedResources.getString(id), result.getString("dialog"))
            }
        } finally { main { activity.finish() } }
    }

    @Test fun rawWindowGetsExplicitOwnersAndReleasesComposition() {
        val activity = start(true)
        var window: RawWindow? = null
        var renderer: ComposeUiRenderer? = null
        var owners: ComposeFloatyOwners? = null
        try {
            main { context.startService(Intent(context, FloatyService::class.java)) }
            await("floaty service started", { FloatyService.isRunning() })
            main {
                val service = FloatyService.instance
                val loaded = ComposeSpikeLoader(context).load()
                val instance = loaded.factory.create(loaded.context(service)) {}.also { renderer = it }
                instance.apply(ComposeSpikeSession.tree(7))
                val view = instance.view()
                assertNull(view.findViewTreeLifecycleOwner())
                assertNull(view.findViewTreeSavedStateRegistryOwner())
                owners = ComposeFloatyOwners().also { it.attach(view) }
                window = RawWindow(service, object : BaseResizableFloatyWindow.ViewSupplier {
                    override fun inflate(context: Context, parent: ViewGroup?): View = view.also { parent!!.addView(it) }
                }).also { FloatyService.addWindow(it) }
            }
            await("floaty composition created", {
                var result = false
                main { result = renderer!!.diagnostics().getInt("compositions") > 0 }
                result
            })
            main { assertTrue(renderer!!.view().isAttachedToWindow) }
        } finally {
            main { renderer?.dispose(); window?.close(); owners?.destroy(); activity.finish() }
        }
        main {
            val state = renderer!!.diagnostics()
            assertTrue(state.getBoolean("closed"))
            assertEquals(state.getInt("compositions"), state.getInt("disposals"))
        }
    }

    @Test fun loaderFailuresAreDistinguishable() {
        fun fails(code: ComposeSpikeLoader.Error, action: () -> Unit) {
            try { action(); fail("Expected $code") } catch (failure: ComposeSpikeLoader.Failure) { assertEquals(code, failure.code) }
        }
        fails(ComposeSpikeLoader.Error.HOST_TOO_OLD) { ComposeSpikeLoader.checkHostVersion(1, 2) }
        fails(ComposeSpikeLoader.Error.NOT_INSTALLED) { ComposeSpikeLoader.checkEligibility(false, false, false, false) }
        fails(ComposeSpikeLoader.Error.SYSTEM_DISABLED) { ComposeSpikeLoader.checkEligibility(true, false, false, false) }
        fails(ComposeSpikeLoader.Error.NOT_ENABLED) { ComposeSpikeLoader.checkEligibility(true, true, false, false) }
        fails(ComposeSpikeLoader.Error.NOT_AUTHORIZED) { ComposeSpikeLoader.checkEligibility(true, true, true, false) }
        fails(ComposeSpikeLoader.Error.CONTRACT_MISMATCH) { ComposeSpikeLoader.checkContract(1) }
        fails(ComposeSpikeLoader.Error.FACTORY_MISSING) { ComposeSpikeLoader.instantiate("no.such.Factory", context.classLoader) }
        val original = PluginEnableStore.isEnabled(context, SpikeContract.PACKAGE)
        try {
            PluginEnableStore.setEnabled(context, SpikeContract.PACKAGE, false)
            fails(ComposeSpikeLoader.Error.NOT_ENABLED) { ComposeSpikeLoader(context).load() }
        } finally { PluginEnableStore.setEnabled(context, SpikeContract.PACKAGE, original) }
    }

    @Test fun standaloneInfoProcessDoesNotNeedTheHostProvidedV1Classes() {
        // The plugin is NOT instrumented here: its own process must answer with only its APK classpath.
        val service = Intent(SpikeContract.INFO_ACTION).addCategory(SpikeContract.INFO_CATEGORY).setPackage(SpikeContract.PACKAGE)
        val ready = CountDownLatch(1)
        val remote = AtomicReference<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { remote.set(binder); ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(service, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Standalone INFO service did not connect", ready.await(10, TimeUnit.SECONDS))
            val info = IPluginInfoProvider.Stub.asInterface(remote.get()).info
            assertEquals(ComposeUiIds.PLUGIN_ID, info.id)
            assertEquals(ComposeUiIds.ENGINE, info.engine)
            assertEquals(ComposeUiIds.VARIANT, info.variant)
            assertTrue(requireNotNull(info.supportedAbis).isEmpty())
            val capabilities = requireNotNull(info.capabilities)
            assertEquals(setOf(PluginCapabilityKeys.REQUIRES_HOST_VERSION), capabilities.keySet())
            assertEquals(ComposeUiIds.REQUIRED_HOST_VERSION_CODE, capabilities.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION))
        } finally { context.unbindService(connection) }
    }

    @Test fun pssBeforeLoadAfterLoadAndAfterDestroy() {
        val activity = start(true)
        fun pss(): Int { System.gc(); SystemClock.sleep(500); return Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss }
        val before = pss()
        main { activity.mount() }
        await("composition created", {
            var composed = false
            main { composed = activity.renderer?.diagnostics()?.getInt("compositions") == 1 }
            composed
        })
        val loaded = pss()
        main { activity.finish() }
        await("destroyed", { activity.isDestroyed })
        val after = pss()
        android.util.Log.i("ComposeSpikePss", "api=${android.os.Build.VERSION.SDK_INT} beforeKiB=$before loadedKiB=$loaded destroyedKiB=$after deltaKiB=${loaded - before}")
    }

    private fun nodeWithText(text: String): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> search(child)?.let { return it } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::search)
    }
}
