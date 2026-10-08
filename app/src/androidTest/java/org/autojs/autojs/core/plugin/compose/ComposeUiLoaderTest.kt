package org.autojs.autojs.core.plugin.compose

import android.content.*
import android.content.res.Configuration
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.floaty.BaseResizableFloatyWindow
import org.autojs.autojs.core.floaty.RawWindow
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.compose.api.*
import org.autojs.plugin.compose.api.loading.*
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.*
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ComposeUiLoaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var host: ComposeUiPluginHost
    private var enabled = true
    private var accessibilityFlags = 0
    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        enabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        host = ComposeUiPluginHost(context)
        instrumentation.uiAutomation.serviceInfo.also {
            accessibilityFlags = it.flags
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            instrumentation.uiAutomation.serviceInfo = it
        }
    }
    @After fun finish() {
        if (::host.isInitialized) { host.close(); PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, enabled) }
        instrumentation.uiAutomation.serviceInfo.also { it.flags = accessibilityFlags; instrumentation.uiAutomation.serviceInfo = it }
    }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }
    private fun activity() = instrumentation.startActivitySync(Intent(context, AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AboutActivity
    private fun counter(count: Int, callback: Int) = UiTree(1, listOf(
        UiNode(1, ComposeUiComponents.COLUMN, children = listOf(2, 3)),
        UiNode(2, ComposeUiComponents.TEXT, "count", mapOf(ComposeUiProps.TEXT to UiValue.Str("Count: $count"))),
        UiNode(3, ComposeUiComponents.BUTTON, "increment", children = listOf(4), callbacks = mapOf(ComposeUiEvents.CLICK to callback)),
        UiNode(4, ComposeUiComponents.TEXT, props = mapOf(ComposeUiProps.TEXT to UiValue.Str("+1"))),
    ))

    @Test fun activityCounterUsesV1TransactionsHostOwnersAndClosesComposition() {
        val activity = activity()
        val loaded = host.load()
        val debugProvider = loaded.pluginContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        lateinit var instance: ComposeUiRenderer
        // This fixture guards the frozen V1 entry, independently of optional interop factories.
        val recording = loaded.copy(androidViewFactory = null, factory = object : ComposeUiRendererFactory by loaded.factory {
            override fun create(environment: ComposeUiHostEnvironment): ComposeUiRenderer = loaded.factory.create(environment).also { instance = it }
        })
        var count = 0
        lateinit var session: ComposeSession
        lateinit var view: View
        val errors = ArrayList<ComposeUiError>()
        try {
            main {
                val adapter = ComposeAndroidRenderer(activity, recording, 1, { view = it; activity.setContentView(it) }, activity, onDestroyed = { session.close() })
                session = ComposeSession(1, ScriptUiDispatcher.main(), ScriptUiDispatcher.mainExecutor(), adapter, loaded.components, errors::add)
                session.open()
                var callback = 0
                callback = session.registerCallback { count++; session.render(counter(count, callback)) }
                session.render(counter(0, callback)); session.attach()
                assertSame(activity, view.findViewTreeLifecycleOwner()); assertSame(activity, view.findViewTreeSavedStateRegistryOwner())
            }
            instrumentation.waitForIdleSync()
            main {
                errors.firstOrNull()?.let { throw AssertionError("Renderer failed: ${it.code}", it) }
                assertEquals("First transaction was committed", 1L, session.generation)
                assertTrue("View attached", view.isAttachedToWindow)
            }
            await("initial counter composed") {
                errors.firstOrNull()?.let { throw AssertionError("Renderer failed: ${it.code}", it) }
                nodeWithText("Count: 0") != null
            }
            val initialIds = session.tree!!.nodes.map { it.nodeId }.toSet()
            var button = requireNotNull(nodeWithText("+1"))
            while (!button.isClickable && button.parent != null) button = button.parent
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("V1 event and SetProps returned") { nodeWithText("Count: 1") != null }
            assertEquals(1, count); assertEquals(2L, session.generation)
            assertEquals(initialIds, session.tree!!.nodes.map { it.nodeId }.toSet())
            assertTrue(errors.toString(), errors.isEmpty())
            main { session.detach(); assertFalse(view.isAttachedToWindow); session.attach() }
            await("counter reattached") { nodeWithText("Count: 1") != null }
        } catch (failure: AssertionError) {
            main {
                android.util.Log.i("ComposeLoaderDiagnostic", "count=$count generation=${session.generation} callbacks=${session.callbackCount} view=${view.width}x${view.height} errors=${errors.map { it.code }}")
            }
            throw failure
        } finally { main { activity.finish() } }
        await("Activity closes session") { session.state == ComposeSession.State.CLOSED }
        main {
            assertFalse(view.isAttachedToWindow)
            if (debugProvider) assertEquals(false, hasComposition(view))
            try { instance.view(); fail("Disposed renderer must reject view creation") }
            catch (e: ComposeUiContractException) { assertEquals(ComposeUiErrorCodes.SESSION_CLOSED, e.code) }
            assertEquals(0, session.callbackCount); assertNull(session.tree)
            session.close()
        }
    }

    @Test fun loaderCachesByIdentityButRechecksEnablementAndSharesParentClasses() {
        val first = host.load()
        assertSame(first, host.load())
        assertEquals(1, first.factory.contractVersion())
        listOf("androidx.lifecycle.Lifecycle", "androidx.savedstate.SavedStateRegistry", "androidx.core.view.ViewCompat",
            "androidx.activity.ComponentActivity", "androidx.appcompat.app.AppCompatActivity", "kotlin.Unit", "kotlinx.coroutines.Job",
            ComposeUiRendererFactory::class.java.name).forEach { name -> assertSame(name, context.classLoader.loadClass(name), first.loader.loadClass(name)) }
        assertSame(first.loader, first.loader.loadClass("androidx.compose.ui.platform.ComposeView").classLoader)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, false)
        assertFalse(host.isAvailable())
        try { host.load(); fail() } catch (e: ComposeUiError) { assertEquals(ComposeUiErrorCodes.PLUGIN_DISABLED, e.code); assertTrue(e.message!!.contains(ComposeUiIds.PLUGIN_PACKAGE_NAME)) }
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        // State changes retain the process code loader, avoiding duplicate JNI ownership.
        assertSame(first.loader, host.load().loader)
        assertTrue(first.nativePath.contains("!/lib/"))
    }

    @Test fun pluginResourcesFollowHostConfigurationAndNativeLibraryInitializes() {
        val loaded = host.load()
        val activity = activity()
        try {
            for (language in listOf("en", "zh", "ar")) {
                val configuration = Configuration(activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language)); densityDpi = 240
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                }
                val wrapper = ComposeHostContext(activity, loaded.pluginContext, loaded.loader, configuration)
                val id = wrapper.resources.getIdentifier("m3c_dialog", "string", ComposeUiIds.PLUGIN_PACKAGE_NAME)
                assertNotEquals(0, id); assertFalse(wrapper.getString(id).isBlank())
                assertEquals(loaded.pluginContext.createConfigurationContext(configuration).getString(id), wrapper.getString(id))
                assertEquals(240, wrapper.resources.configuration.densityDpi)
                assertEquals(language, wrapper.resources.configuration.locales[0].language)
                assertSame(activity.theme, wrapper.theme)
            }
            assertGraphicsPathNativeLibraryInitialization(loaded)
        } finally { main { activity.finish() } }
    }

    @Test fun rawWindowOwnersAttachBeforeCompositionAndReleaseOnClose() {
        val loaded = host.load()
        val activity = activity()
        lateinit var session: ComposeSession
        var started = false
        lateinit var view: View
        lateinit var owners: ComposeFloatyOwners
        var window: RawWindow? = null
        val errors = ArrayList<ComposeUiError>()
        try {
            main { context.startService(Intent(context, FloatyService::class.java)) }
            await("floaty service") { FloatyService.isRunning() }
            main {
                val service = FloatyService.instance
                owners = ComposeFloatyOwners()
                val adapter = ComposeAndroidRenderer(service, loaded, 2, { child ->
                    view = child
                    window = RawWindow(service, object : BaseResizableFloatyWindow.ViewSupplier {
                        override fun inflate(context: Context, parent: ViewGroup?): View = child.also { parent!!.addView(it) }
                    }).also { FloatyService.addWindow(it) }
                }, floatyOwners = owners, onDestroyed = {})
                session = ComposeSession(2, ScriptUiDispatcher.main(), ScriptUiDispatcher.mainExecutor(), adapter, loaded.components, errors::add)
                started = true
                session.open(); val callback = session.registerCallback { }
                session.render(counter(7, callback)); session.attach()
                assertSame(owners, view.findViewTreeLifecycleOwner()); assertSame(owners, view.findViewTreeSavedStateRegistryOwner())
            }
            await("floaty composition") { var composed = false; main { composed = hasComposition(view) == true }; composed }
            assertTrue(errors.toString(), errors.isEmpty())
        } finally { main { if (started) session.close(); window?.close(); activity.finish() } }
        main {
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, owners.lifecycle.currentState)
            assertEquals(false, hasComposition(view)); assertEquals(0, session.callbackCount)
        }
    }

    @Test fun themeBridgePublishesColorAndNightChangesAndStopsAfterClose() {
        main {
            val original = ThemeColorManager.currentThemeColor
            val received = ArrayList<ThemeSpec>()
            val bridge = ComposeThemeBridge(context, received::add)
            try {
                ThemeColorManager.setThemeColor(0xff336699.toInt())
                assertEquals(0xff336699.toInt(), received.last().seedArgb)
                bridge.onConfigurationChanged(Configuration(context.resources.configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_YES })
                assertEquals(true, received.last().dark)
                bridge.close(); val count = received.size
                ThemeColorManager.setThemeColor(original)
                assertEquals(count, received.size)
            } finally { bridge.close(); ThemeColorManager.setThemeColor(original) }
        }
    }

    @Test fun eventsAreQueuedBoundedAndDiscardedAfterGenerationChangeOrClose() {
        lateinit var session: ComposeSession
        var callback = 0
        val delivered = ArrayList<Int>()
        val errors = ArrayList<ComposeUiError>()
        val warnings = ArrayList<String>()
        val renderer = object : ComposeSessionRenderer {
            override fun open(sink: ComposeUiEventSink) = Unit
            override fun attach() = Unit
            override fun detach() = Unit
            override fun apply(batch: UiPatchBatch) = Unit
            override fun execute(command: UiCommand) = Unit
            override fun setTheme(theme: ThemeSpec) = Unit
            override fun dispose() = Unit
        }
        main {
            session = ComposeSession(9, ScriptUiDispatcher.main(), ScriptUiDispatcher.mainExecutor(), renderer,
                setOf(ComposeUiComponents.COLUMN), errors::add, warnings::add)
            session.open()
            callback = session.registerCallback { delivered += it.payload.getInt(ComposeUiEventFields.OFFSET) }
            session.render(UiTree(1, listOf(UiNode(1, ComposeUiComponents.COLUMN, callbacks = mapOf(ComposeUiEvents.SCROLL to callback)))))
        }
        instrumentation.waitForIdleSync()
        fun event(value: Int, generation: Long = session.generation) = UiEvent(9, generation, session.tree!!.rootId,
            ComposeUiEvents.SCROLL, callback, Bundle().apply { putInt(ComposeUiEventFields.OFFSET, value) })
        main {
            repeat(ComposeUiLimits.EVENT_QUEUE_CAPACITY + 2) { session.enqueue(event(it)) }
            assertTrue("No synchronous callback from enqueue", delivered.isEmpty())
        }
        instrumentation.waitForIdleSync()
        assertEquals(ComposeUiLimits.EVENT_QUEUE_CAPACITY, delivered.size)
        assertEquals(2, delivered.first()); assertEquals(1, warnings.size)
        main {
            session.enqueue(UiEvent(9, session.generation, 17, ComposeUiEvents.ERROR, ComposeUiContract.SYSTEM_CALLBACK_ID,
                Bundle().apply { putString(ComposeUiEventFields.CODE, ComposeUiErrorCodes.INVALID_ARGUMENT); putString(ComposeUiEventFields.PROP, ComposeUiProps.VALUE) }))
            assertTrue(errors.isEmpty())
        }
        instrumentation.waitForIdleSync()
        assertEquals(17, errors.single().nodeId)
        assertEquals(ComposeUiProps.VALUE, errors.single().prop)
        assertEquals(ComposeUiErrorCodes.INVALID_ARGUMENT, errors.single().code)
        main {
            session.enqueue(event(-1, 0))
            session.enqueue(event(-2))
            session.close()
        }
        instrumentation.waitForIdleSync()
        assertEquals(ComposeUiLimits.EVENT_QUEUE_CAPACITY, delivered.size)
        assertEquals(0, session.callbackCount); assertEquals(1, errors.size)
    }

    @Test fun standaloneInfoStillRunsWithoutTheHostProvidedApiClasses() {
        val ready = CountDownLatch(1); val binder = AtomicReference<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder.set(service); ready.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val intent = Intent(ComposeUiContract.INFO_ACTION).addCategory(ComposeUiContract.INFO_CATEGORY).setPackage(ComposeUiIds.PLUGIN_PACKAGE_NAME)
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val info = IPluginInfoProvider.Stub.asInterface(binder.get()).info
            assertEquals(ComposeUiIds.PLUGIN_ID, info.id); assertTrue(info.supportedAbis!!.isEmpty())
            assertEquals(setOf(PluginCapabilityKeys.REQUIRES_HOST_VERSION), info.capabilities!!.keySet())
        } finally { context.unbindService(connection) }
    }

    /** Debug-only Compose introspection stays in the test, while release tests use the V1 lifecycle. */
    private fun hasComposition(view: View): Boolean {
        val getter = view.javaClass.methods.firstOrNull { it.name == "getHasComposition" && it.parameterCount == 0 }
        if (getter != null) return getter.invoke(view) == true
        return view is ViewGroup && (0 until view.childCount).any { hasComposition(view.getChildAt(it)) }
    }

    private fun nodeWithText(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // A cached virtual node can outlive the Compose semantics update or an Activity window.
            if (!node.refresh()) return null
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child)?.let { return it } }
            return null
        }
        // A user's floating window can own focus while this test Activity is RESUMED.
        // Observe only our dedicated package; do not close or alter another app's window.
        return instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.mapNotNull(::find).firstOrNull()
    }
}
