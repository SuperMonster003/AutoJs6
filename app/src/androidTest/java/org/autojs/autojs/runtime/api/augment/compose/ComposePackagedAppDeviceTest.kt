package org.autojs.autojs.runtime.api.augment.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.compose.api.ComposeUiIds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Install the producer's real APK first; the driver owns plugin installation/removal. */
@RunWith(AndroidJUnit4::class)
class ComposePackagedAppDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = ComposePackagedApkProducerDeviceTest.OUTPUT_PACKAGE
    private lateinit var automation: UiAutomation

    @Test fun versionOneApplicationUsesTheRuntimeVersionAndReportsMissingPlugins() {
        val mode = InstrumentationRegistry.getArguments().getString("composePackagedMode")
        assumeTrue("Pass -e composePackagedMode present or missing", mode == "present" || mode == "missing")
        assertTrue("Use the isolated debug host", BuildConfig.DEBUG && context.packageName.endsWith(".compose.spike"))
        val app = context.packageManager.getPackageInfo(packageName, 0)
        assertEquals("Use the actual builder output, whose application version differs from the runtime", 1L,
            PackageInfoCompat.getLongVersionCode(app))
        val pluginInstalled = try {
            context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
        assertEquals("The driver must actually install/remove the Compose plugin before this case", mode == "present", pluginInstalled)
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("am force-stop $packageName")
            shell("am start -W -n $packageName/org.autojs.autojs.inrt.SplashActivity")
            val identity = "App: 1; runtime: ${BuildConfig.VERSION_CODE}"
            if (mode == "present") {
                await("The separately installed INRT app mounts the external Compose renderer") { nodeText("count") == "Count: 0" }
                assertEquals(identity, nodeText("identity"))
                assertEquals(packageName, nodeText("package"))
                assertNull(nodeText("error-code"))
                repeat(3) { index ->
                    val button = requireNotNull(node("increment"))
                    try { assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }
                    finally { recycle(button) }
                    await("The packaged application's own Rhino callback updates its view") { nodeText("count") == "Count: ${index + 1}" }
                }
            } else {
                await("Missing plugin produces a typed, localized message in the application's own UI") {
                    nodeText("error-code") == "PLUGIN_UNAVAILABLE"
                }
                assertEquals(identity, nodeText("identity"))
                val message = requireNotNull(nodeText("error-message"))
                assertTrue("The message identifies the plugin to install", message.contains(ComposeUiIds.PLUGIN_PACKAGE_NAME))
                assertTrue("The localized explanation includes more than a code or package name",
                    message.length > ComposeUiIds.PLUGIN_PACKAGE_NAME.length + 5)
                assertNotEquals("PLUGIN_UNAVAILABLE", message)
                assertNull(nodeText("increment"))
            }
            println("compose.p5.packagedRuntime=" + JSONObject().put("packageName", packageName)
                .put("plugin", mode).put("identity", identity).put("count", nodeText("count"))
                .put("errorCode", nodeText("error-code")).put("errorMessage", nodeText("error-message")))
        } finally {
            try { shell("am force-stop $packageName") }
            finally { automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags } }
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        do {
            if (condition()) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("$message; error=${nodeText("error-code")}: ${nodeText("error-message")}")
    }

    private fun nodeText(key: String): String? = node(key)?.let { node ->
        try { node.text?.toString() } finally { recycle(node) }
    }

    private fun node(key: String): AccessibilityNodeInfo? {
        val tag = "compose-inrt-$key"
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        automation.windows.mapNotNull { it.root }.forEach { root ->
            if (root.packageName?.toString() == packageName) queue.add(root) else recycle(root)
        }
        var found: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (found == null && current.refresh()) {
                if (current.viewIdResourceName == tag || current.contentDescription?.toString() == tag) {
                    found = current
                    continue
                }
                for (index in 0 until current.childCount) current.getChild(index)?.let(queue::add)
            }
            recycle(current)
        }
        return found
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) = node.recycle()
}
